package com.example.ui

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.MainActivity
import com.example.engine.Difficulty
import com.example.engine.Role
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
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
    private var scenario: ActivityScenario<MainActivity>? = null
    abstract val shot: String

    protected fun launch(saveText: String?) {
        try { scenario?.close() } catch (_: Throwable) { }
        val app = ApplicationProvider.getApplicationContext<Application>()
        val f = File(app.filesDir, "kapital_save.txt")
        if (saveText != null) f.writeText(saveText) else f.delete()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        rule.waitForIdle()
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
        launch(UiSaves.early)
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
        launch(UiSaves.late)
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
