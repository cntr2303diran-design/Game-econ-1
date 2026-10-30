package com.example

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.ENGINE_VERSION
import com.example.engine.Action
import com.example.engine.KapitalEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Putar ulang kampanye penuh di ART (emulator), dibandingkan bit demi bit dengan ringkasan yang dihitung di HotSpot JVM
 * (replay_fixture.txt, dijaga ReplayFixtureTest). Ini bukti pertama bahwa simpanan dan tantangan lintas perangkat tidak menyimpang
 * akibat perbedaan runtime. Yang BELUM dibuktikan: CPU ARM (emulator CI memakai x86_64).
 */
@RunWith(AndroidJUnit4::class)
class ArtReplayTest {
    private fun fixture(): ReplayFixture {
        val inst = InstrumentationRegistry.getInstrumentation()
        val text = inst.context.assets.open("replay_fixture.txt").bufferedReader().use { it.readText() }
        return ReplayFixture.parse(text)
    }

    private fun outFile(name: String): File {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        return File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, name)
    }

    @Test
    fun campaignReplayedOnArt_matchesTheJvmDigestsBitForBit() {
        val fx = fixture()
        assertEquals("fixture dibuat untuk ENGINE_VERSION lain", ENGINE_VERSION, fx.engineVersion)
        val runs = (1..3).map { fx.replay() }
        val first = runs.first()
        val report = buildString {
            appendLine("ART-REPLAY runtime=${System.getProperty("java.vm.name")} ${System.getProperty("java.vm.version")}; sdk=${Build.VERSION.SDK_INT}; abi=${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("ART-REPLAY log=${fx.actions.size} baris; titik periksa=${fx.checks.size}; ditolak=${first.rejected}; giliran akhir=${first.finalTurn}")
            appendLine("ART-REPLAY waktu putar ulang termasuk ringkasan refleksi (ms): ${runs.map { it.elapsedMs }}")
            appendLine("ART-REPLAY ketidakcocokan dengan JVM: ${first.mismatches.size}")
            first.mismatches.take(10).forEach { appendLine("ART-REPLAY   $it") }
        }
        Log.i("KAPITAL-ART", report)
        outFile("art_replay.txt").writeText(report)
        assertEquals("tidak boleh ada aksi ditolak", 0, first.rejected)
        assertTrue("kampanye seharusnya mencapai akhir (giliran ${first.finalTurn})", first.finalTurn >= 481)
        assertTrue("ringkasan di ART berbeda dari JVM:\n$report", first.mismatches.isEmpty())
        for (r in runs) assertEquals("putar ulang berulang di ART harus identik", first.checks[-1]!!.hash, r.checks[-1]!!.hash)
    }

    @Test
    fun pureReplayTimeOfAFullCampaign_isRecorded() {
        val fx = fixture()
        val times = ArrayList<Long>()
        repeat(4) {
            val t0 = System.nanoTime()
            val e = KapitalEngine(fx.seed)
            for (line in fx.actions) Action.decode(line)?.let { e.apply(it) }
            times += (System.nanoTime() - t0) / 1_000_000
        }
        val msg = "ART-LOAD putar ulang murni ${fx.actions.size} baris log (ms, berurutan, pertama = dingin): $times"
        Log.i("KAPITAL-ART", msg)
        outFile("art_load.txt").writeText(msg + "\n")
        assertTrue("putar ulang kampanye penuh di emulator terlalu lambat: $times", times.min() < 60_000)
    }
}
