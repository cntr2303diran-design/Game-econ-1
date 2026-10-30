package com.example.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.GameRepository
import com.example.model.GameState
import com.example.ui.screens.MenuScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Menu pada tiap keadaan pemulihan simpanan, diuji langsung pada komposabel (deterministik, tanpa berlomba dengan thread latar):
 * selama simpanan diputar ulang hanya panel pemuatan yang tampil, supaya pemain tidak memulai permainan baru yang menimpa simpanan;
 * sesudahnya tombol permainan kembali.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-port-xhdpi")
class MenuStateUiTest {
    @get:Rule val rule = createComposeRule()

    private fun base(): GameState = GameRepository(null, 7919L).gameState.value

    @Test
    fun whileRestoring_menuShowsOnlyTheLoadingPanel_andHelpStaysReachable() {
        val soft = Soft()
        val progress = mutableStateOf(0.25f)
        rule.setContent {
            MyApplicationTheme {
                MenuScreen(gameState = base().copy(isRestoring = true), onContinue = {}, onNewGame = {}, onToggleTheme = {}, restoreProgress = progress.value)
            }
        }
        soft.check("panel pemuatan tampil") { rule.onNodeWithTag("menu_loading").assertExists() }
        soft.check("persentase 25% tampil") { rule.onNodeWithText("25%", substring = true).assertExists() }
        for (t in listOf("menu_continue", "menu_new_game", "menu_peran", "menu_slots")) {
            soft.check("tombol $t disembunyikan selama memuat") { rule.onNodeWithTag(t).assertDoesNotExist() }
        }
        soft.check("kemajuan diperbarui menjadi 80%") {
            progress.value = 0.8f
            rule.waitForIdle()
            rule.onNodeWithText("80%", substring = true).assertExists()
        }
        soft.check("Cara bermain tetap bisa dibuka selama memuat") {
            val inDialog = hasAnyAncestor(isDialog())
            rule.tap("menu_how")
            rule.onNode(hasText("Cara bermain") and inDialog).assertExists()
            rule.onNode(hasText("Mengerti") and inDialog).performClick()
        }
        UiShots.save(rule, "menu_state_memuat")
        soft.done()
    }

    @Test
    fun afterRestoring_menuOffersContinueNewGameAndRoles() {
        val soft = Soft()
        // Keadaan tersimpan sungguhan: permainan dengan beberapa giliran.
        val repo = GameRepository(null, 7919L)
        repeat(3) { repo.advanceTurn() }
        val saved = repo.gameState.value
        rule.setContent {
            MyApplicationTheme { MenuScreen(gameState = saved, onContinue = {}, onNewGame = {}, onToggleTheme = {}) }
        }
        soft.check("panel pemuatan tidak tampil") { rule.onNodeWithTag("menu_loading").assertDoesNotExist() }
        for (t in listOf("menu_continue", "menu_new_game", "menu_peran", "menu_how")) {
            soft.check("tombol $t tampil") { rule.onNodeWithTag(t).assertExists() }
        }
        UiShots.save(rule, "menu_state_siap")
        soft.done()
    }

    @Test
    fun freshState_menuOffersStartNotContinue() {
        val soft = Soft()
        rule.setContent {
            MyApplicationTheme { MenuScreen(gameState = base(), onContinue = {}, onNewGame = {}, onToggleTheme = {}) }
        }
        soft.check("tidak ada tombol lanjutkan pada permainan baru") { rule.onNodeWithTag("menu_continue").assertDoesNotExist() }
        soft.check("tombol mulai permainan ada") { rule.onNodeWithTag("menu_new_game").assertExists() }
        soft.done()
    }
}
