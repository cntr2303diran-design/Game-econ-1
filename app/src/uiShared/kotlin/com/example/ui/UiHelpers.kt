package com.example.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.test.SemanticsMatcher
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
import kotlin.random.Random

/**
 * Bantuan uji UI. Di Robolectric, tangkapan gambar lewat captureToImage tidak bisa dipakai pada Compose versi ini (menunggu callback
 * render yang tak pernah datang di JVM; kehabisan waktu 2 detik tiap kali), jadi uji JVM menulis "tangkapan teks": semua teks, tag, dan
 * keterangan konten yang tampil, per jendela (termasuk dialog), ke app/build/ui-shots. Gambar sungguhan diambil job emulator
 * (UiAutomation). Berkas _diag.txt memuat catatan harness (keadaan ViewModel, berkas simpanan) supaya kegagalan bisa dipilah.
 */
object UiShots {
    /** Uji instrumented mengisi ini dengan folder penyimpanan aplikasi; uji Robolectric memakai build/ui-shots (folder kerja = modul). */
    @Volatile var baseDir: File? = null
    val dir: File get() = (baseDir ?: File(System.getProperty("kapital.shots") ?: "build/ui-shots")).also { it.mkdirs() }

    /** Catatan harness satu baris ke _diag.txt (tak pernah menggagalkan uji). */
    @Synchronized fun note(line: String) {
        try { File(dir, "_diag.txt").appendText(line + "\n") } catch (_: Throwable) { }
    }

    /** Simpan "tangkapan teks" layar saat ini sebagai [name].txt. */
    fun save(rule: ComposeTestRule, name: String) {
        try {
            rule.waitForIdle()
            File(dir, "$name.txt").writeText(describeScreen(rule))
        } catch (t: Throwable) {
            File(dir, "_errors.txt").appendText("$name: ${t::class.java.simpleName}: ${t.message?.take(300)}\n")
        }
    }

    /** Pohon semantik yang terlihat: satu baris per simpul bertag/berteks/berketerangan, dikelompokkan per jendela. */
    fun describeScreen(rule: ComposeTestRule): String {
        val sb = StringBuilder()
        val roots = rule.onAllNodes(isRoot()).fetchSemanticsNodes()
        roots.forEachIndexed { i, root ->
            sb.append("== jendela ").append(i).append('\n')
            fun walk(n: SemanticsNode, depth: Int) {
                val cfg = n.config
                val tag = cfg.valueOrNull(SemanticsProperties.TestTag)
                val text = cfg.valueOrNull(SemanticsProperties.Text)?.joinToString(" | ") { it.text }
                val desc = cfg.valueOrNull(SemanticsProperties.ContentDescription)?.joinToString(" | ")
                if (tag != null || text != null || desc != null) {
                    sb.append("  ".repeat(minOf(depth, 8)))
                    if (tag != null) sb.append('#').append(tag).append(' ')
                    if (text != null) sb.append('"').append(text).append("\" ")
                    if (desc != null) sb.append("(ket: ").append(desc).append(") ")
                    if (cfg.contains(SemanticsActions.OnClick)) sb.append("[klik]")
                    sb.append('\n')
                }
                for (c in n.children) walk(c, depth + 1)
            }
            walk(root, 0)
        }
        return if (sb.length > 24_000) sb.substring(0, 24_000) + "\n...(dipotong)\n" else sb.toString()
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
