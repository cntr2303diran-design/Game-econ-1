package com.example.ui

import android.app.Application
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.MainActivity
import com.example.model.GameState
import com.example.viewmodel.KapitalViewModel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Uji UI sungguhan: MainActivity dijalankan di JVM lewat Robolectric (grafik NATIVE), lalu layar dioperasikan lewat testTag.
 * Menjalankan komposisi penuh, navigasi, ViewModel, dan repositori; bukan sekadar kompilasi. Yang TIDAK dibuktikan: tampilan
 * di perangkat nyata (font, kepadatan, kinerja); itu urusan job emulator dan uji manual.
 */
@RunWith(RobolectricTestRunner::class)
abstract class UiTestBase {
    @get:Rule val rule = createEmptyComposeRule()
    @get:Rule val testName = TestName()
    private var scenario: ActivityScenario<MainActivity>? = null
    abstract val shot: String

    private fun menuVisible(): Boolean = try { rule.tagExists("menu_new_game") } catch (_: AssertionError) { false }

    /**
     * Menyuntikkan berkas simpanan (atau menghapusnya), meluncurkan aplikasi, menunggu menu tampil, dan mengembalikan keadaan
     * permainan yang SEBENARNYA dimuat ViewModel. Pembanding ini memisahkan kegagalan pemuatan simpanan dari kegagalan tampilan;
     * catatan harness ditulis ke _diag.txt.
     */
    protected fun launch(saveText: String?): GameState {
        try { scenario?.close() } catch (_: Throwable) { }
        val app = ApplicationProvider.getApplicationContext<Application>()
        val f = File(app.filesDir, "kapital_save.txt")
        if (saveText != null) f.writeText(saveText) else f.delete()
        val sizeBefore = if (f.exists()) f.length() else -1L
        val t0 = System.nanoTime()
        val sc = ActivityScenario.launch(MainActivity::class.java)
        scenario = sc
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 120_000) { menuVisible() }
        val ms = (System.nanoTime() - t0) / 1_000_000
        var st: GameState? = null
        var actDir = "?"
        var sameApp = false
        sc.onActivity { a ->
            actDir = a.filesDir.absolutePath
            sameApp = a.application === app
            st = ViewModelProvider(a)[KapitalViewModel::class.java].gameState.value
        }
        val s = st ?: throw AssertionError("ViewModel tidak terjangkau dari aktivitas")
        UiShots.note(
            "[${testName.methodName}] simpanan=${saveText?.lines()?.size ?: "tanpa"} baris; berkas sebelum=$sizeBefore setelah=${if (f.exists()) f.length() else -1L}; " +
                "folder uji=${app.filesDir.absolutePath} folder aktivitas=$actDir sama=${actDir == app.filesDir.absolutePath} aplikasiSama=$sameApp; " +
                "VM: giliran=${s.turn} era=${s.eraNumber} tersimpan=${s.hasSavedGame} berakhir=${s.gameOverReason != null} spanduk=${s.saveNotice != null}; " +
                "UI: lanjutkan=${rule.tagExists("menu_continue")}; ${ms} ms"
        )
        return s
    }

    @After fun closeScenario() { try { scenario?.close() } catch (_: Throwable) { } }

    protected fun pressBack() { scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle() }

    protected fun continueGame(soft: Soft) {
        soft.check("menu: tombol lanjutkan ada") { rule.onNodeWithTag("menu_continue").assertExists() }
        soft.check("menu: lanjutkan ke Markas") {
            rule.tap("menu_continue"); rule.waitForIdle()
            rule.onNodeWithTag("end_turn_button").assertExists()
        }
    }

    protected fun endTurn(soft: Soft, label: String) {
        soft.check("$label: akhiri giliran, konfirmasi, laporan bulanan") {
            rule.tap("end_turn_button")
            rule.onNodeWithTag("confirm_end_turn").assertExists()
            rule.tap("confirm_end_turn")
            rule.onNodeWithTag("monthly_report_close").assertExists()
            rule.tap("monthly_report_close")
            rule.waitForIdle()
        }
    }

    protected fun visitTabs(soft: Soft, tag: String) {
        for (route in listOf("peta", "perusahaan", "pasar", "bursa", "dunia", "markas")) {
            soft.check("tab $route") {
                rule.tap("bottom_nav_$route"); rule.waitForIdle()
                rule.onNodeWithTag("bottom_nav_$route").assertIsSelected()
            }
            UiShots.save(rule, "${shot}_${tag}_tab_$route")
        }
    }

    protected fun visitSubtabs(soft: Soft, tag: String) {
        soft.check("buka Perusahaan") { rule.tap("bottom_nav_perusahaan"); rule.waitForIdle() }
        for (i in 0..9) {
            soft.check("subtab Perusahaan $i") {
                rule.tap("perusahaan_subtab_$i"); rule.waitForIdle()
                rule.onNodeWithTag("perusahaan_subtab_$i").assertIsSelected()
            }
            UiShots.save(rule, "${shot}_${tag}_perusahaan_$i")
        }
    }

    // ---------------------------------------------------------------------------------------------------------------

    @Test
    fun savedGame_everyTabAndSubtabRenders() {
        val soft = Soft()
        val st = launch(UiSaves.early)
        soft.check("ViewModel memuat simpanan awal (tersimpan, Era 1)") { assertTrue("$st", st.hasSavedGame && st.eraNumber == 1) }
        UiShots.save(rule, "${shot}_early_00_menu")
        continueGame(soft)
        soft.check("bilah atas menampilkan giliran dan era") { assertTrue("turnAndEra null", rule.turnAndEra() != null) }
        UiShots.save(rule, "${shot}_early_01_markas")
        visitTabs(soft, "early")
        visitSubtabs(soft, "early")
        soft.done()
    }

    @Test
    fun lateGame_everyTabAndSubtabRenders() {
        val soft = Soft()
        val st = launch(UiSaves.late)
        soft.check("ViewModel memuat simpanan akhir permainan (Era 3 atau 4)") { assertTrue("giliran=${st.turn} era=${st.eraNumber}", st.eraNumber >= 3) }
        UiShots.save(rule, "${shot}_late_00_menu")
        continueGame(soft)
        soft.check("bilah atas menampilkan era akhir") {
            val te = rule.turnAndEra()
            assertTrue("turnAndEra null", te != null)
            assertTrue("permainan akhir seharusnya di Era 3 atau 4, ternyata ${te?.second}", (te?.second ?: 0) >= 3)
        }
        UiShots.save(rule, "${shot}_late_01_markas")
        visitTabs(soft, "late")
        visitSubtabs(soft, "late")
        soft.done()
    }

    @Test
    fun lateGame_monkeyWalk_neverCrashes() {
        launch(UiSaves.late)
        val soft = Soft()
        continueGame(soft)
        soft.done()
        val n = rule.crawl(seed = 20261030L, maxActions = 250, maxMillis = 240_000)
        println("UI-MONKEY late: $n aksi")
        assertTrue("penjelajah seharusnya sempat beraksi (n=$n)", n >= 50)
        UiShots.save(rule, "${shot}_late_99_setelah_monkey")
    }

    // ---------------------------------------------------------------------------------------------------------------
}
