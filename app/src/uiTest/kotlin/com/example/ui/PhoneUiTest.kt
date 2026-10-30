package com.example.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.example.engine.Difficulty
import com.example.engine.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Layar ponsel potret (360x760dp): seluruh alur pemain dan jalur kegagalan. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-port-xhdpi")
class PhoneUiTest : UiTestBase() {
    override val shot = "phone"

    @Test
    fun freshInstall_menuToFirstTurn_andBack() {
        val soft = Soft()
        val st0 = launch(null)
        soft.check("ViewModel: pemasangan baru tanpa permainan tersimpan") { assertTrue("$st0", !st0.hasSavedGame) }
        soft.check("menu: mulai, peran, cara bermain ada") {
            rule.onNodeWithTag("menu_new_game").assertExists()
            rule.onNodeWithTag("menu_peran").assertExists()
            rule.onNodeWithTag("menu_how").assertExists()
        }
        UiShots.save(rule, "phone_fresh_00_menu")
        soft.check("dialog cara bermain terbuka dan tertutup") {
            rule.tap("menu_how")
            val inDialog = hasAnyAncestor(isDialog())
            rule.onNode(hasText("Cara bermain") and inDialog).assertExists()
            UiShots.save(rule, "phone_fresh_01_cara_bermain")
            rule.onNode(hasText("Mengerti") and inDialog).performClick()
            rule.waitForIdle()
            rule.onNode(hasText("Cara bermain") and inDialog).assertDoesNotExist()
        }
        soft.check("pilih kesulitan: semua tingkat tersedia, mulai Manajer") {
            rule.tap("menu_new_game")
            for (d in Difficulty.values()) rule.onNodeWithTag("difficulty_${d.name}").assertExists()
            UiShots.save(rule, "phone_fresh_02_kesulitan")
            rule.tap("difficulty_${Difficulty.MANAJER.name}")
            rule.waitForIdle()
            rule.onNodeWithTag("end_turn_button").assertExists()
        }
        UiShots.save(rule, "phone_fresh_03_markas")
        val t0 = rule.turnAndEra()
        soft.check("bilah atas: giliran dan era terbaca") { assertTrue("null", t0 != null) }
        soft.check("permainan baru dimulai di Era 1") { assertEquals(1, t0?.second) }
        endTurn(soft, "giliran pertama")
        UiShots.save(rule, "phone_fresh_04_setelah_giliran")
        soft.check("giliran bertambah satu") { assertEquals((t0?.first ?: -1) + 1, rule.turnAndEra()?.first) }
        soft.check("menu dari bilah atas lalu kembali ke permainan") {
            rule.tap("open_menu_button")
            rule.onNodeWithTag("menu_continue").assertExists()
            rule.tap("menu_continue"); rule.waitForIdle()
            rule.onNodeWithTag("end_turn_button").assertExists()
        }
        soft.check("ganti tema terang/gelap dua kali") {
            rule.tap("toggle_theme_button"); rule.waitForIdle()
            UiShots.save(rule, "phone_fresh_05_tema_lain")
            rule.tap("toggle_theme_button"); rule.waitForIdle()
        }
        for (i in 1..5) endTurn(soft, "bulan $i")
        visitTabs(soft, "fresh")
        soft.done()
    }

    @Test
    fun corporateButtons_inEra1_explainWhyTheyAreRefused() {
        val soft = Soft()
        val st = launch(UiSaves.early)
        soft.check("ViewModel memuat simpanan awal (tersimpan, Era 1)") { assertTrue("$st", st.hasSavedGame && st.eraNumber == 1) }
        continueGame(soft)
        soft.check("permainan awal berada di Era 1") { assertEquals(1, rule.turnAndEra()?.second) }
        soft.check("buka tab Investasi") {
            rule.tap("bottom_nav_perusahaan"); rule.tap("perusahaan_subtab_6"); rule.waitForIdle()
        }
        val locked = listOf(
            "SUB|tarsis" to "Bangun anak usaha Tarsis",
            "IPO|20|false" to "IPO 20% saham",
            "BYB|1500" to "Beli kembali saham",
            "CRB|buy|5" to "Beli 5 kredit karbon",
            "SUC|exec_cfo" to "Tunjuk CFO",
            "BSB|tarsis|500" to "Pinjaman bank lokal anak usaha Tarsis",
            "CBK" to "Dirikan bank dan asuransi grup"
        )
        for ((code, label) in locked) soft.check("tombol $code ditolak dengan alasan") {
            rule.tap("corp_$code")
            val t = rule.textOfTag("corp_feedback")
            assertTrue("umpan balik memuat label '$label': $t", t.startsWith(label))
            assertTrue("umpan balik menjelaskan 'Belum bisa': $t", t.contains("Belum bisa"))
        }
        UiShots.save(rule, "phone_corp_era1_ditolak")
        soft.check("HFX (forward valas) selalu memberi jawaban") {
            rule.tap("corp_HFX")
            val t = rule.textOfTag("corp_feedback")
            assertTrue(t, t.startsWith("Forward valas 6 bulan: ") && t.length > 30)
        }
        soft.done()
    }

    @Test
    fun lateGame_everyCorporateButtonAnswers() {
        val soft = Soft()
        val st = launch(UiSaves.late)
        soft.check("ViewModel memuat simpanan akhir permainan (Era 3 atau 4)") { assertTrue("giliran=${st.turn} era=${st.eraNumber}", st.eraNumber >= 3) }
        continueGame(soft)
        soft.check("buka tab Investasi") {
            rule.tap("bottom_nav_perusahaan"); rule.tap("perusahaan_subtab_6"); rule.waitForIdle()
        }
        val tags = rule.tagsWithPrefix("corp_").filter { it != "corp_feedback" }
        soft.check("tombol korporasi tersedia (${tags.size})") { assertTrue(tags.size >= 30) }
        var executed = 0
        var refused = 0
        for (tag in tags) soft.check("tombol $tag") {
            rule.tap(tag)
            val t = rule.textOfTag("corp_feedback")
            assertTrue("umpan balik tak kosong: '$t'", t.length > 15)
            if (t.contains("Dilaksanakan")) executed++ else refused++
        }
        println("UI-LATE tombol korporasi: ${tags.size}; dilaksanakan=$executed; ditolak atau terkunci=$refused")
        UiShots.save(rule, "phone_corp_late_setelah_semua_tombol")
        soft.check("minimal satu aksi korporasi terlaksana di akhir permainan (executed=$executed)") { assertTrue(executed >= 1) }
        soft.done()
    }

    @Test
    fun legacySave_showsNoticeBanner_andItCanBeDismissed() {
        val soft = Soft()
        val st = launch(UiSaves.legacy)
        soft.check("ViewModel memuat simpanan lama dan menyiapkan peringatan") { assertTrue("$st", st.hasSavedGame && st.saveNotice != null) }
        continueGame(soft)
        val inBanner = hasAnyAncestor(hasTestTag("save_notice"))
        soft.check("spanduk peringatan tampil") { rule.onNodeWithTag("save_notice").assertExists() }
        soft.check("spanduk menyebut versi mesin") { rule.onNode(hasText("versi mesin", substring = true, ignoreCase = true) and inBanner).assertExists() }
        UiShots.save(rule, "phone_savenotice_tampil")
        soft.check("spanduk bisa ditutup dengan Mengerti") {
            rule.onNode(hasText("Mengerti") and inBanner).performClick()
            rule.waitForIdle()
            rule.onNodeWithTag("save_notice").assertDoesNotExist()
        }
        soft.done()
    }

    @Test
    fun currentSave_hasNoNoticeBanner() {
        val soft = Soft()
        val st = launch(UiSaves.early)
        soft.check("ViewModel memuat simpanan tanpa peringatan") { assertTrue("$st", st.hasSavedGame && st.saveNotice == null) }
        continueGame(soft)
        soft.check("simpanan bercap versi saat ini tanpa spanduk") { rule.onNodeWithTag("save_notice").assertDoesNotExist() }
        soft.done()
    }

    @Test
    fun lostGame_showsGameOverPanel_andNewGameWorks() {
        val soft = Soft()
        val st = launch(UiSaves.lost)
        soft.check("ViewModel memuat simpanan yang berakhir kalah") { assertTrue("$st", st.hasSavedGame && st.gameOverReason != null) }
        soft.check("menu menandai permainan berakhir") { rule.onNodeWithText("Permainan berakhir", substring = true).assertExists() }
        UiShots.save(rule, "phone_gameover_00_menu")
        continueGame(soft)
        soft.check("panel akhir permainan punya tombol permainan baru") { rule.onNodeWithTag("new_game_button").assertExists() }
        UiShots.save(rule, "phone_gameover_01_markas")
        visitTabs(soft, "gameover")
        soft.check("kembali ke Markas lalu mulai permainan baru") {
            rule.tap("bottom_nav_markas")
            rule.tap("new_game_button"); rule.waitForIdle()
            rule.onNodeWithTag("end_turn_button").assertIsEnabled()
        }
        soft.done()
    }

    private fun playRole(role: Role, advanceTag: String) {
        val soft = Soft()
        launch(null)
        soft.check("${role.code}: buka layar Peran") { rule.tap("menu_peran"); rule.onNodeWithTag("peran_mulai").assertExists() }
        UiShots.save(rule, "phone_peran_00_pilih")
        soft.check("${role.code}: mulai (seed 7919, 24 bulan)") {
            rule.tap("peran_${role.code}")
            rule.tap("peran_h24")
            rule.onNodeWithTag("peran_seed").performTextReplacement("7919")
            rule.tap("peran_mulai"); rule.waitForIdle()
            rule.onNodeWithTag(advanceTag).assertExists()
        }
        UiShots.save(rule, "phone_peran_${role.code}_main")
        soft.check("${role.code}: main sampai selesai") {
            var guard = 0
            while (rule.tagExists(advanceTag) && guard++ < 60) {
                if (role == Role.DANA && guard == 2) rule.tagsWithPrefix("beli_").firstOrNull()?.let { rule.tap(it) }
                rule.tap(advanceTag)
            }
            rule.onNodeWithTag("salin_tantangan").assertExists()
        }
        UiShots.save(rule, "phone_peran_${role.code}_selesai")
        soft.check("${role.code}: salin kode, isi nama, salin hasil") {
            rule.tap("salin_tantangan")
            rule.onNodeWithTag("peran_nama").performTextReplacement("Penguji")
            rule.tap("salin_hasil")
        }
        soft.check("${role.code}: papan peringkat memuat hasil sendiri yang terverifikasi") {
            rule.onNodeWithText("Papan peringkat (hasil Anda masuk otomatis)").performClick()
            rule.waitForIdle()
            rule.onNodeWithText("1. Penguji", substring = true).assertExists()
        }
        UiShots.save(rule, "phone_peran_${role.code}_papan")
        soft.done()
    }

    @Test fun role_governor_playsToTheEnd() = playRole(Role.GUBERNUR, "gub_lanjut")
    @Test fun role_minister_playsToTheEnd() = playRole(Role.MENTERI, "men_lanjut")
    @Test fun role_fundManager_playsToTheEnd() = playRole(Role.DANA, "dana_lanjut")

    @Test
    fun ceoChallenge_startsFromPeranScreen_withTypedSeed() {
        val soft = Soft()
        launch(null)
        soft.check("buka layar Peran") { rule.tap("menu_peran"); rule.onNodeWithTag("ceo_mulai").assertExists() }
        soft.check("mulai tantangan CEO dengan seed 7919 dan kesulitan Manajer") {
            rule.onNodeWithTag("ceo_seed").performTextReplacement("7919")
            rule.tap("ceo_dif_${Difficulty.MANAJER.name}")
            rule.tap("ceo_mulai"); rule.waitForIdle()
            rule.onNodeWithTag("end_turn_button").assertExists()
        }
        soft.check("kampanye CEO berjalan di Era 1") { assertEquals(1, rule.turnAndEra()?.second) }
        endTurn(soft, "tantangan CEO")
        soft.done()
    }

    @Test
    fun earlyGame_monkeyWalk_neverCrashes() {
        launch(UiSaves.early)
        val soft = Soft()
        continueGame(soft)
        soft.done()
        val n = rule.crawl(seed = 20261031L, maxActions = 250, maxMillis = 240_000)
        println("UI-MONKEY early: $n aksi")
        assertTrue("penjelajah seharusnya sempat beraksi (n=$n)", n >= 50)
        UiShots.save(rule, "phone_early_99_setelah_monkey")
    }

    @Test
    fun fromMenu_monkeyWalk_neverCrashes() {
        launch(null)
        val n = rule.crawl(seed = 20261101L, maxActions = 200, maxMillis = 240_000)
        println("UI-MONKEY menu: $n aksi")
        assertTrue("penjelajah seharusnya sempat beraksi (n=$n)", n >= 30)
        UiShots.save(rule, "phone_menu_99_setelah_monkey")
    }
}
