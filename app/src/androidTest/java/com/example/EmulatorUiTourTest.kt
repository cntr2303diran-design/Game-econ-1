package com.example

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.engine.ENGINE_VERSION
import com.example.engine.Action
import com.example.engine.Difficulty
import com.example.ui.Soft
import com.example.ui.UiShots
import com.example.ui.crawl
import com.example.ui.tagExists
import com.example.ui.tagsWithPrefix
import com.example.ui.tap
import com.example.ui.textOfTag
import com.example.ui.turnAndEra
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Tur UI di emulator (ART, tampilan sungguhan): permainan baru, lalu keadaan akhir kampanye yang dimuat dari fixture. Semua layar,
 * semua sub-tab Perusahaan, tombol korporasi, dan penjelajah acak. Tangkapan layar utuh (termasuk bilah status) ikut disimpan.
 */
@RunWith(AndroidJUnit4::class)
class EmulatorUiTourTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext

    @Before fun setUp() {
        UiShots.baseDir = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "ui-shots")
        clearSaves()
    }

    @After fun tearDown() {
        try { scenario?.close() } catch (_: Throwable) { }
        clearSaves()
    }

    private fun clearSaves() {
        File(ctx.filesDir, "kapital_save.txt").delete()
        for (i in 1..5) File(ctx.filesDir, "kapital_slot_$i.txt").delete()
    }

    private fun menuVisible(): Boolean = try { rule.tagExists("menu_new_game") } catch (_: AssertionError) { false }

    /**
     * Hasil peluncuran: ms sampai aktivitas kembali (jendela pertama), sampai panel pemuatan atau menu tampil, sampai menu tampil
     * (simpanan selesai dimuat), dan [respondMs] = berapa ms dialog "Cara bermain" terbuka saat simpanan MASIH dimuat (-1 bila
     * pemuatan terlalu cepat untuk sempat diuji). Respons cepat membuktikan thread utama tidak membeku selama pemutaran ulang.
     */
    private class Launched(val toActivityMs: Long, val toPanelMs: Long, val toMenuMs: Long, val respondMs: Long) {
        fun describe() = "aktivitas kembali $toActivityMs ms, panel/menu pertama tampil $toPanelMs ms, " +
            (if (respondMs >= 0) "layar tetap responsif saat memuat (dialog terbuka dalam $respondMs ms), " else "pemuatan terlalu cepat untuk uji responsivitas, ") +
            "menu siap $toMenuMs ms"
    }

    private fun launch(saveText: String?): Launched {
        try { scenario?.close() } catch (_: Throwable) { }
        if (saveText != null) File(ctx.filesDir, "kapital_save.txt").writeText(saveText)
        val t0 = SystemClock.elapsedRealtime()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val toActivity = SystemClock.elapsedRealtime() - t0
        var toPanel = -1L
        var respond = -1L
        rule.waitUntil(timeoutMillis = 180_000) {
            val loading = rule.tagExists("menu_loading")
            if (toPanel < 0 && (loading || menuVisible())) toPanel = SystemClock.elapsedRealtime() - t0
            if (respond < 0 && loading) {
                // Saat simpanan masih diputar ulang di latar belakang, layar harus tetap melayani sentuhan.
                val t1 = SystemClock.elapsedRealtime()
                try {
                    val inDialog = hasAnyAncestor(isDialog())
                    rule.tap("menu_how")
                    rule.onNode(hasText("Cara bermain") and inDialog).assertExists()
                    respond = SystemClock.elapsedRealtime() - t1
                    rule.onNode(hasText("Mengerti") and inDialog).performClick()
                    rule.waitForIdle()
                } catch (t: Throwable) {
                    respond = -2L
                    File(UiShots.dir, "_errors.txt").appendText("uji responsivitas saat memuat gagal: ${t::class.java.simpleName}: ${t.message?.take(200)}\n")
                }
            }
            menuVisible()
        }
        return Launched(toActivity, toPanel, SystemClock.elapsedRealtime() - t0, respond)
    }

    /**
     * Tangkapan layar utuh dari tampilan perangkat (UiAutomation), termasuk bilah status dan dialog. Jeda singkat menunggu animasi
     * peralihan layar selesai digambar: waitForIdle hanya menjamin jam uji Compose sudah maju, bukan bingkai terakhir sudah tampil
     * (tangkapan pertama putaran lalu menunjukkan layar sebelumnya dan bingkai setengah pudar).
     */
    private fun fullShot(name: String) {
        try {
            inst.waitForIdleSync()
            SystemClock.sleep(600)
            val bmp = inst.uiAutomation.takeScreenshot() ?: return
            FileOutputStream(File(UiShots.dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (t: Throwable) {
            File(UiShots.dir, "_errors.txt").appendText("$name (layar utuh): ${t::class.java.simpleName}: ${t.message?.take(200)}\n")
        }
    }

    private fun lateSave(): String {
        val text = inst.context.assets.open("replay_fixture.txt").bufferedReader().use { it.readText() }
        val fx = ReplayFixture.parse(text)
        val end = Action.EndTurn.encode()
        val sb = StringBuilder("SEED:${fx.seed}\nVER:$ENGINE_VERSION\n")
        var turns = 0
        for (a in fx.actions) {
            sb.append(a).append('\n')
            if (a == end && ++turns >= 380) break
        }
        return sb.toString()
    }

    private fun endTurn(soft: Soft, label: String) {
        soft.check("$label: akhiri giliran") {
            rule.tap("end_turn_button")
            rule.onNodeWithTag("confirm_end_turn").assertExists()
            rule.tap("confirm_end_turn")
            rule.onNodeWithTag("monthly_report_close").assertExists()
            rule.tap("monthly_report_close")
            rule.waitForIdle()
        }
    }

    private fun tour(soft: Soft, tag: String) {
        for (route in listOf("peta", "perusahaan", "pasar", "bursa", "dunia", "markas")) {
            soft.check("tab $route") {
                rule.tap("bottom_nav_$route"); rule.waitForIdle()
                rule.onNodeWithTag("bottom_nav_$route").assertIsSelected()
            }
            fullShot("emu_${tag}_tab_$route")
        }
        soft.check("buka Perusahaan") { rule.tap("bottom_nav_perusahaan"); rule.waitForIdle() }
        for (i in 0..9) {
            soft.check("subtab $i") {
                rule.tap("perusahaan_subtab_$i"); rule.waitForIdle()
                rule.onNodeWithTag("perusahaan_subtab_$i").assertIsSelected()
            }
            fullShot("emu_${tag}_perusahaan_$i")
        }
    }

    @Test
    fun freshInstall_tour() {
        val soft = Soft()
        val l = launch(null)
        Log.i("KAPITAL-ART", "ART-UI peluncuran pemasangan baru: ${l.describe()}")
        fullShot("emu_fresh_00_menu")
        soft.check("mulai permainan baru") {
            rule.tap("menu_new_game")
            rule.tap("difficulty_${Difficulty.MANAJER.name}"); rule.waitForIdle()
            rule.onNodeWithTag("end_turn_button").assertExists()
        }
        fullShot("emu_fresh_01_markas")
        for (i in 1..3) endTurn(soft, "bulan $i")
        tour(soft, "fresh")
        soft.check("ganti tema") { rule.tap("toggle_theme_button"); rule.waitForIdle() }
        fullShot("emu_fresh_99_tema_lain")
        soft.done()
    }

    @Test
    fun lateGame_loadTime_tour_andMonkey() {
        val soft = Soft()
        val save = lateSave()
        val l = launch(save)
        val msg = "ART-UI memuat simpanan ${save.lines().size} baris (±380 bulan): ${l.describe()}"
        Log.i("KAPITAL-ART", msg)
        File(UiShots.dir, "load_time.txt").writeText(msg + "\n")
        soft.check("aplikasi tetap responsif selama simpanan panjang dimuat (respon=${l.respondMs} ms)") {
            // -1 = pemuatan terlalu cepat untuk diuji (tidak gagal); -2 = sempat diuji tetapi dialog tidak terbuka
            assertTrue("dialog tidak terbuka saat memuat", l.respondMs != -2L)
            assertTrue("dialog baru terbuka ${l.respondMs} ms sesudah diketuk; thread utama diduga membeku", l.respondMs < 0 || l.respondMs < 4_000)
        }
        fullShot("emu_late_00_menu")
        soft.check("lanjutkan ke Markas") { rule.tap("menu_continue"); rule.waitForIdle(); rule.onNodeWithTag("end_turn_button").assertExists() }
        soft.check("era akhir (Era 3 atau 4)") { assertTrue("era=${rule.turnAndEra()?.second}", (rule.turnAndEra()?.second ?: 0) >= 3) }
        tour(soft, "late")
        soft.check("tombol korporasi menjawab") {
            rule.tap("bottom_nav_perusahaan"); rule.tap("perusahaan_subtab_6"); rule.waitForIdle()
            val tags = rule.tagsWithPrefix("corp_").filter { it != "corp_feedback" }
            assertTrue("tombol korporasi ada (${tags.size})", tags.size >= 30)
            var executed = 0
            for (t in tags) {
                rule.tap(t)
                val txt = rule.textOfTag("corp_feedback")
                assertTrue("umpan balik $t kosong", txt.length > 15)
                if (txt.contains("Dilaksanakan")) executed++
            }
            Log.i("KAPITAL-ART", "ART-UI tombol korporasi ${tags.size}, dilaksanakan $executed")
        }
        fullShot("emu_late_90_setelah_tombol_korporasi")
        soft.done()
        val n = rule.crawl(seed = 20261102L, maxActions = 200, maxMillis = 180_000)
        Log.i("KAPITAL-ART", "ART-UI penjelajah acak: $n aksi")
        fullShot("emu_late_99_setelah_monkey")
        assertTrue("penjelajah seharusnya sempat beraksi (n=$n)", n >= 40)
    }
}
