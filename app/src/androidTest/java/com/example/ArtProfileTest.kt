package com.example

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.engine.Action
import com.example.engine.KapitalEngine
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Profil pengambilan sampel dari putar ulang kampanye penuh di ART (emulator). Memutar ulang di ART terukur belasan detik padahal di
 * HotSpot hanya ±0,3 detik; tes ini mencari TEMPAT waktunya habis: thread lain mengambil tumpukan panggilan thread pemutar ulang
 * tiap beberapa milidetik, lalu mencatat bingkai teratas (waktu sendiri) dan keberadaan di tumpukan (waktu inklusif) beserta
 * pengelompokan per pustaka. Tidak menguji kebenaran; hasilnya masuk art_profile.txt untuk dibaca manusia.
 */
@RunWith(AndroidJUnit4::class)
class ArtProfileTest {
    private fun fixture(): ReplayFixture {
        val inst = InstrumentationRegistry.getInstrumentation()
        val text = inst.context.assets.open("replay_fixture.txt").bufferedReader().use { it.readText() }
        return ReplayFixture.parse(text)
    }

    private fun outFile(name: String): File {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        return File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, name)
    }

    private class Profile {
        val self = HashMap<String, Int>()
        val incl = HashMap<String, Int>()
        var samples = 0
        var elapsedMs = 0L
    }

    private fun profileOneReplay(fx: ReplayFixture): Profile {
        val p = Profile()
        val worker = Thread({
            val e = KapitalEngine(fx.seed)
            for (line in fx.actions) Action.decode(line)?.let { e.apply(it) }
        }, "profil-putar-ulang")
        val t0 = System.nanoTime()
        worker.start()
        while (worker.isAlive) {
            try { Thread.sleep(5) } catch (_: InterruptedException) { }
            val st = worker.stackTrace
            if (st.isEmpty()) continue
            p.samples++
            val top = st[0]
            p.self.merge("${top.className}.${top.methodName}", 1, Int::plus)
            val seen = HashSet<String>()
            for (f in st) { val k = "${f.className}.${f.methodName}"; if (seen.add(k)) p.incl.merge(k, 1, Int::plus) }
        }
        worker.join()
        p.elapsedMs = (System.nanoTime() - t0) / 1_000_000
        return p
    }

    private fun pct(n: Int, total: Int) = if (total == 0) "0.0" else "%.1f".format(100.0 * n / total)

    private fun render(round: Int, p: Profile): String = buildString {
        appendLine("== putaran $round (${if (round == 1) "dingin" else "hangat"}): ${p.elapsedMs} ms, ${p.samples} sampel")
        appendLine("-- waktu SENDIRI (bingkai teratas), 30 teratas")
        for ((k, v) in p.self.entries.sortedByDescending { it.value }.take(30)) appendLine("  ${pct(v, p.samples).padStart(5)}%  $k")
        appendLine("-- waktu INKLUSIF (ada di tumpukan), 40 teratas selain thread dasar")
        for ((k, v) in p.incl.entries.sortedByDescending { it.value }.filter { !it.key.startsWith("java.lang.Thread.") && !it.key.startsWith("com.example.ArtProfileTest") }.take(40)) {
            appendLine("  ${pct(v, p.samples).padStart(5)}%  $k")
        }
        fun group(label: String, match: (String) -> Boolean) {
            val self = p.self.entries.filter { match(it.key) }.sumOf { it.value }
            appendLine("  $label: sendiri ${pct(self, p.samples)}%")
        }
        appendLine("-- kelompok (waktu sendiri)")
        group("StrictMath (JNI)") { it.startsWith("java.lang.StrictMath.") }
        group("java.lang.Math") { it.startsWith("java.lang.Math.") }
        group("DMath (pembungkus)") { it.startsWith("com.example.engine.DMathKt.") }
        group("mesin com.example.engine") { it.startsWith("com.example.engine.") }
        group("java.util.* koleksi") { it.startsWith("java.util.") }
        group("kotlin.*") { it.startsWith("kotlin.") }
        group("java.lang.String/Formatter/StringBuilder") { it.startsWith("java.lang.String") || it.startsWith("java.util.Formatter") }
        group("Random") { it.contains("Random.") }
        val incMath = p.incl.entries.filter { it.key.startsWith("java.lang.StrictMath.") || it.key.startsWith("com.example.engine.DMathKt.") }
            .maxOfOrNull { it.value } ?: 0
        appendLine("  paling banyak ada di tumpukan lewat StrictMath atau DMath: ${pct(incMath, p.samples)}% (batas bawah porsi fungsi matematika)")
    }

    @Volatile private var sink = 0.0

    /** Biaya satu panggilan (ns) dari fungsi yang dipakai DMath, dibanding java.lang.Math dan satu putaran kosong, tiga putaran berurutan. */
    @Test
    fun mathCallCost_isRecorded() {
        val n = 400_000
        fun timed(f: () -> Double): Double {
            val t0 = System.nanoTime()
            sink = f()
            return (System.nanoTime() - t0).toDouble() / n
        }
        val sb = StringBuilder()
        sb.appendLine("ns per panggilan, $n panggilan per putaran, tiga putaran berurutan (dingin, lalu lebih hangat)")
        val benches = listOf<Pair<String, () -> Double>>(
            "putaran kosong" to { var a = 0.0; var x = 1.0001; for (i in 0 until n) { a += x; x += 1e-6 }; a },
            "StrictMath.log" to { var a = 0.0; var x = 1.0001; for (i in 0 until n) { a += StrictMath.log(x); x += 1e-6 }; a },
            "StrictMath.exp" to { var a = 0.0; var x = 0.0001; for (i in 0 until n) { a += StrictMath.exp(x); x += 1e-6 }; a },
            "StrictMath.cos" to { var a = 0.0; var x = 0.0001; for (i in 0 until n) { a += StrictMath.cos(x); x += 1e-5 }; a },
            "StrictMath.tanh" to { var a = 0.0; var x = 0.0001; for (i in 0 until n) { a += StrictMath.tanh(x); x += 1e-6 }; a },
            "StrictMath.pow" to { var a = 0.0; var x = 1.0001; for (i in 0 until n) { a += StrictMath.pow(x, 1.37); x += 1e-6 }; a },
            "Math.log" to { var a = 0.0; var x = 1.0001; for (i in 0 until n) { a += Math.log(x); x += 1e-6 }; a },
            "Math.exp" to { var a = 0.0; var x = 0.0001; for (i in 0 until n) { a += Math.exp(x); x += 1e-6 }; a },
            "Math.pow" to { var a = 0.0; var x = 1.0001; for (i in 0 until n) { a += Math.pow(x, 1.37); x += 1e-6 }; a },
            "Math.sqrt" to { var a = 0.0; var x = 1.0001; for (i in 0 until n) { a += Math.sqrt(x); x += 1e-6 }; a }
        )
        for ((name, f) in benches) {
            val r = (1..3).map { timed(f) }
            sb.appendLine("  ${name.padEnd(16)} ${r.joinToString("  ") { "%.1f".format(it) }}")
        }
        Log.i("KAPITAL-ART", "ART-MATEMATIKA\n$sb")
        outFile("math_cost.txt").writeText(sb.toString())
        assertTrue(sink.isFinite())
    }

    @Test
    fun sampledProfileOfAFullReplay_isRecorded() {
        val fx = fixture()
        val sb = StringBuilder()
        var total = 0
        for (round in 1..2) {
            val p = profileOneReplay(fx)
            total += p.samples
            sb.append(render(round, p))
        }
        Log.i("KAPITAL-ART", "ART-PROFIL selesai, $total sampel; lihat art_profile.txt")
        for (line in sb.lines().take(70)) Log.i("KAPITAL-ART", "ART-PROFIL $line")
        outFile("art_profile.txt").writeText(sb.toString())
        assertTrue("profil tidak mendapat sampel", total > 50)
    }
}
