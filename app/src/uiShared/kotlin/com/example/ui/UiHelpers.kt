package com.example.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

/**
 * Bantuan uji UI (Robolectric, JVM). Tangkapan layar ditulis ke app/build/ui-shots (direktori kerja uji unit AGP = folder modul)
 * dan diunggah CI sebagai artifact. Kegagalan menangkap gambar tidak menggagalkan uji fungsional, tetapi dicatat di _errors.txt
 * supaya tidak diam-diam.
 */
object UiShots {
    /** Uji instrumented mengisi ini dengan folder penyimpanan aplikasi; uji Robolectric memakai build/ui-shots (folder kerja = modul). */
    @Volatile var baseDir: File? = null
    val dir: File get() = (baseDir ?: File(System.getProperty("kapital.shots") ?: "build/ui-shots")).also { it.mkdirs() }

    fun save(rule: ComposeTestRule, name: String) {
        try {
            rule.waitForIdle()
            val n = rule.onAllNodes(isRoot()).fetchSemanticsNodes().size
            for (i in 0 until n) {
                val bmp = rule.onAllNodes(isRoot())[i].captureToImage().asAndroidBitmap()
                val f = File(dir, if (i == 0) "$name.png" else "${name}_jendela$i.png")
                FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } catch (t: Throwable) {
            File(dir, "_errors.txt").appendText("$name: ${t::class.java.simpleName}: ${t.message?.take(300)}\n")
        }
    }
}

/**
 * Pemeriksaan lunak: setiap kegagalan dicatat dengan labelnya dan uji lanjut, supaya satu putaran CI melaporkan SEMUA masalah UI
 * (bukan hanya yang pertama). Panggil [done] di akhir uji.
 */
class Soft {
    val failures = ArrayList<String>()
    fun check(label: String, block: () -> Unit) {
        try { block() } catch (t: Throwable) {
            val msg = (t.message ?: "").lineSequence().take(3).joinToString(" | ").take(400)
            failures += "$label -> ${t::class.java.simpleName}: $msg"
        }
    }
    fun done() {
        if (failures.isNotEmpty()) throw AssertionError("${failures.size} pemeriksaan UI gagal:\n" + failures.joinToString("\n"))
    }
}

/** Nilai properti semantik, atau null bila belum diset. Memakai anggota contains/get (bukan ekstensi getOrNull) supaya tidak bergantung pada impor tambahan. */
fun <T> SemanticsConfiguration.valueOrNull(key: SemanticsPropertyKey<T>): T? = if (contains(key)) get(key) else null

/** Gulir bila node berada di dalam kontainer yang bisa digulir, lalu ketuk. */
fun ComposeTestRule.tap(tag: String) {
    val node = onNodeWithTag(tag)
    try { node.performScrollTo() } catch (_: AssertionError) { /* tidak berada di kontainer gulir */ }
    node.performClick()
}

fun hasTagPrefix(prefix: String): SemanticsMatcher =
    SemanticsMatcher("testTag diawali $prefix") { it.config.valueOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true }

fun ComposeTestRule.tagExists(tag: String): Boolean =
    onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

fun ComposeTestRule.textOfTag(tag: String): String =
    onNodeWithTag(tag).fetchSemanticsNode().config.valueOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: ""

fun ComposeTestRule.tagsWithPrefix(prefix: String): List<String> =
    onAllNodes(hasTagPrefix(prefix)).fetchSemanticsNodes().mapNotNull { it.config.valueOrNull(SemanticsProperties.TestTag) }

private val TURN_RE = Regex("^Giliran (\\d+) · Era (\\d+)$")

/** (giliran, era) dari bilah atas, atau null bila bilah atas tidak tampil. */
fun ComposeTestRule.turnAndEra(): Pair<Int, Int>? {
    val m = SemanticsMatcher("teks giliran") { n -> n.config.valueOrNull(SemanticsProperties.Text)?.any { TURN_RE.matches(it.text) } == true }
    val node = onAllNodes(m).fetchSemanticsNodes().firstOrNull() ?: return null
    val text = node.config.valueOrNull(SemanticsProperties.Text)?.firstOrNull { TURN_RE.matches(it.text) }?.text ?: return null
    val g = TURN_RE.matchEntire(text)!!.groupValues
    return g[1].toInt() to g[2].toInt()
}

/**
 * Penjelajah acak berbatas: mengetuk simpul yang bisa diketuk dan mengisi kolom teks dengan nilai aneh, seperti `monkey`.
 * Hanya AssertionError (simpul sudah hilang dari pohon) yang diabaikan; pengecualian dari kode aplikasi merambat dan menggagalkan uji.
 * Mengembalikan jumlah aksi yang berhasil.
 */
fun ComposeTestRule.crawl(seed: Long, maxActions: Int, maxMillis: Long): Int {
    val rnd = Random(seed)
    val odd = listOf("", " ", "0", "-1", "abc", "99999999999999999999", "1e9", "12345", "0.5", "0,5", "999999")
    val t0 = System.nanoTime()
    var done = 0
    var stale = 0
    while (done < maxActions && (System.nanoTime() - t0) / 1_000_000 < maxMillis && stale < 50) {
        val textMode = rnd.nextInt(8) == 0
        val matcher = if (textMode) hasSetTextAction() else hasClickAction()
        val count = onAllNodes(matcher).fetchSemanticsNodes().size
        if (count == 0) { stale++; continue }
        val i = rnd.nextInt(count)
        try {
            val node = onAllNodes(matcher)[i]
            try { node.performScrollTo() } catch (_: AssertionError) { }
            if (textMode) node.performTextReplacement(odd[rnd.nextInt(odd.size)]) else node.performClick()
            done++
        } catch (e: AssertionError) {
            stale++
        }
    }
    waitForIdle()
    return done
}
