package com.example

import com.example.data.Autosave
import com.example.data.GameRepository
import com.example.data.SaveStore
import com.example.data.SlotStore
import com.example.engine.Difficulty
import org.junit.Assert.*
import org.junit.Test

/** Tahap B8: lima slot autosave dan kesulitan terwarisi pada "game baru". */
class SaveSlotsTest {

    private class Mem : SlotStore {
        val data = HashMap<Int, String>()
        override fun readSlot(slot: Int) = data[slot]
        override fun writeSlot(slot: Int, text: String) { data[slot] = text }
    }
    private class Main : SaveStore {
        var text: String? = null
        override fun read() = text
        override fun write(text: String) { this.text = text }
    }

    private fun metaTurn(text: String): Int = text.lines().first { it.startsWith(Autosave.META) }.removePrefix(Autosave.META).split("|")[0].toInt()

    private val t0: Int by lazy { com.example.engine.KapitalEngine(1L).turn }

    /** Seed dan daftar aksi bot disiplin yang bertahan [turns] giliran (agar tes tidak kosong karena permainan berakhir cepat). */
    private val run: Pair<Long, List<String>> by lazy {
        for (k in 1..80) {
            val seed = k * 7919L
            val save = TestBots.recordedSave(seed, t0 + 40) ?: continue
            return@lazy seed to save.lines().drop(1)
        }
        throw AssertionError("tidak ada seed yang bertahan 40 giliran")
    }

    private fun feed(r: GameRepository, lines: List<String>, upToEndTurns: Int = Int.MAX_VALUE) {
        var ends = 0
        for (l in lines) {
            if (ends >= upToEndTurns) break
            r.corporateAction(l)
            if (l == "END") ends++
        }
    }

    @Test
    fun fiveRotatingSlots_holdTheLastFiveAutosaves() {
        val slots = Mem()
        val r = GameRepository(null, run.first, slots)
        feed(r, run.second)
        assertTrue("permainan masih berjalan", r.gameState.value.gameOverReason == null)
        val turns = slots.data.values.map { metaTurn(it) }.sorted()
        val last = (t0 + 40) / Autosave.EVERY * Autosave.EVERY
        assertEquals("lima slot terisi dengan lima autosave terakhir: $turns", (0 until 5).map { last - Autosave.EVERY * (4 - it) }, turns)
        assertTrue(slots.data.keys.all { it in 1..Autosave.SLOTS })
        assertEquals("kelima slot berbeda", 5, slots.data.keys.size)
        val views = r.gameState.value.autosaves
        assertEquals(5, views.size)
        assertEquals("diurutkan terbaru dulu", views.map { it.turn }, views.map { it.turn }.sortedDescending())
        assertTrue(views.all { it.turn > 0 && it.label.contains("giliran") })
    }

    @Test
    fun loadingSlot_restoresExactState_andReplayIsDeterministic() {
        val slots = Mem()
        val r = GameRepository(null, run.first, slots)
        val snap = HashMap<Int, Pair<Double, Double>>()
        val start = r.gameState.value.turn
        for (l in run.second) {
            r.corporateAction(l)
            if (l == "END") r.gameState.value.let { snap[it.turn] = it.cashRs to it.equityRs }
        }
        assertTrue(r.gameState.value.gameOverReason == null)
        val finalState = r.gameState.value.let { Triple(it.turn, it.cashRs, it.equityRs) }
        val target = slots.data.entries.first { metaTurn(it.value) == (t0 + 40) / Autosave.EVERY * Autosave.EVERY - Autosave.EVERY * 2 }
        val tTurn = metaTurn(target.value)
        assertTrue(r.loadSlot(target.key))
        val s = r.gameState.value
        assertEquals(tTurn, s.turn)
        assertEquals(snap.getValue(tTurn).first, s.cashRs, 0.0)
        assertEquals(snap.getValue(tTurn).second, s.equityRs, 0.0)
        // lanjutkan dengan aksi yang sama: harus tiba di keadaan akhir yang sama
        val remaining = run.second.let { all ->
            var e = 0; var idx = 0
            while (idx < all.size && e < tTurn - start) { if (all[idx] == "END") e++; idx++ }
            all.drop(idx)
        }
        for (l in remaining) r.corporateAction(l)
        val after = r.gameState.value
        assertEquals(finalState.first, after.turn)
        assertEquals("kas akhir sama", finalState.second, after.cashRs, 0.0)
        assertEquals("ekuitas akhir sama", finalState.third, after.equityRs, 0.0)
    }

    @Test
    fun mainSave_ignoresMetaLines_andSlotWithoutMetaStillLoads() {
        val slots = Mem(); val main = Main()
        val r = GameRepository(main, 5L, slots)
        repeat(14) { r.advanceTurn() }
        assertFalse("simpanan utama tidak memuat META", main.text!!.contains(Autosave.META))
        val any = slots.data.values.first()
        slots.data[4] = any.lines().filterNot { it.startsWith(Autosave.META) }.joinToString("\n")
        assertTrue(r.loadSlot(4))
        assertTrue("tanpa META tampil sebagai kosong tetapi dapat dimuat", r.gameState.value.turn >= 1)
        // simpanan utama dapat dimuat ulang oleh instans baru
        val r2 = GameRepository(main)
        assertEquals(r.gameState.value.turn, r2.gameState.value.turn)
    }

    @Test
    fun ironman_hasNoAutosave_andCannotLoadWhileRunning() {
        val slots = Mem()
        val r = GameRepository(null, 3L, slots)
        r.advanceTurn(); r.advanceTurn()
        r.advanceTurn(); r.advanceTurn(); r.advanceTurn(); r.advanceTurn()
        assertTrue("kesulitan biasa menulis autosave", slots.data.isNotEmpty())
        val ironSlots = Mem()
        var ok = 0
        val r2 = GameRepository(null, 3L, ironSlots, newSeed = { 99L })
        r2.newGame(Difficulty.KRISIS_ABADI)
        val start2 = r2.gameState.value.turn
        repeat(30) { if (r2.gameState.value.gameOverReason == null) r2.advanceTurn() }
        assertTrue("Ironman melewati ≥ 6 giliran sehingga ujian autosave bermakna: ${r2.gameState.value.turn - start2}", r2.gameState.value.turn - start2 >= Autosave.EVERY)
        assertTrue("Ironman tidak menulis autosave", ironSlots.data.isEmpty())
        if (r2.gameState.value.gameOverReason == null) {
            assertFalse("tak bisa memuat saat Ironman berjalan", r2.loadSlot(1))
            ok++
        }
        assertEquals(Difficulty.KRISIS_ABADI.label, r2.gameState.value.difficultyLabel)
    }

    @Test
    fun newGame_inheritsDifficultyUnlessExplicit() {
        var n = 100L
        val r = GameRepository(null, 1L, null, newSeed = { n++ })
        assertEquals(Difficulty.MANAJER.label, r.gameState.value.difficultyLabel)
        r.newGame(Difficulty.DIREKTUR)
        assertEquals(Difficulty.DIREKTUR.label, r.gameState.value.difficultyLabel)
        r.newGame()
        assertEquals("diwarisi", Difficulty.DIREKTUR.label, r.gameState.value.difficultyLabel)
        r.newGame(Difficulty.MAGANG)
        assertEquals(Difficulty.MAGANG.label, r.gameState.value.difficultyLabel)
        r.newGame()
        assertEquals(Difficulty.MAGANG.label, r.gameState.value.difficultyLabel)
    }

    @Test
    fun newGame_afterGameOver_inheritsDifficultyAndUsesFreshSeed() {
        var n = 500L
        val r = GameRepository(null, 2L, null, newSeed = { n++ })
        r.newGame(Difficulty.DIREKTUR)
        var guard = 0
        while (r.gameState.value.gameOverReason == null && guard++ < 500) r.advanceTurn()
        assertNotNull("permainan tanpa keputusan akhirnya berakhir", r.gameState.value.gameOverReason)
        r.newGame()
        assertTrue(r.gameState.value.gameOverReason == null)
        assertEquals(Difficulty.DIREKTUR.label, r.gameState.value.difficultyLabel)
    }

    // ---- Cap versi mesin pada simpanan dan umpan balik tombol korporasi ----

    @Test
    fun saves_carryEngineVersion_andMismatchIsReportedToThePlayer() {
        val (seed, lines) = run
        val store = Main()
        val r = GameRepository(store, seed)
        r.newGameFromChallenge(com.example.engine.Challenge(com.example.engine.Role.CEO, seed, 480, Difficulty.MANAJER))
        feed(r, lines.take(30))
        val text = store.text!!
        assertTrue("simpanan memuat cap versi", text.lines().any { it == "VER:${com.example.data.ENGINE_VERSION}" })
        assertTrue("SEED tetap baris pertama (kompatibel dengan pembaca lama)", text.lines().first().startsWith("SEED:"))
        // muat ulang dengan versi sama: tanpa peringatan
        val same = Main().also { it.text = text }
        assertTrue("versi sama = tanpa peringatan", GameRepository(same).gameState.value.saveNotice == null)
        // simpanan lama tanpa cap versi: ada peringatan, permainan tetap termuat
        val legacy = Main().also { it.text = text.lines().filter { l -> !l.startsWith("VER:") }.joinToString("\n") }
        val rl = GameRepository(legacy)
        val note = rl.gameState.value.saveNotice
        assertTrue("simpanan tanpa cap versi diberi peringatan: $note", note != null && note.contains("versi mesin 1"))
        assertEquals("permainan tetap sama", r.gameState.value.turn, rl.gameState.value.turn)
        rl.dismissSaveNotice()
        assertTrue("peringatan bisa ditutup", rl.gameState.value.saveNotice == null)
        // tindakan yang tak bisa diputar ulang dilaporkan, bukan hilang diam-diam
        val broken = Main().also { it.text = text + "\nIPO|20|false" }
        val nb = GameRepository(broken).gameState.value.saveNotice
        assertTrue("tindakan tak bisa diputar ulang dilaporkan: $nb", nb != null && nb.contains("1 tindakan"))
    }

    @Test
    fun corporateButtons_alwaysTellThePlayerWhatHappened() {
        val r = GameRepository(Main(), 7919L)
        val unknown = r.corporateActionFeedback("XYZ|nope")
        assertTrue("kode tak dikenal: $unknown", unknown.contains("tidak dikenal"))
        val locked = r.corporateActionFeedback("IPO|20|false")
        assertTrue("terkunci era dijelaskan: $locked", locked.startsWith("Belum bisa"))
        val carbon = r.corporateActionFeedback("CRB|buy|5")
        assertTrue("karbon di Era 1 terkunci: $carbon", carbon.startsWith("Belum bisa"))
        val buyback = r.corporateActionFeedback("BYB|1500")
        assertTrue("buyback butuh emiten: $buyback", buyback.startsWith("Belum bisa"))
        // setiap jawaban berisi alasan, tidak pernah kosong
        for (code in listOf("SUB|tarsis", "TPR|OPTIMAL", "HFX", "BND|3000|36", "SPO|3000", "NCI|tarsis|15", "ACQ|riv_cahaya", "LOB|media", "CBK", "SUC|exec_cfo", "BSB|tarsis|500", "CHA"))
            assertTrue("umpan balik $code tak kosong", r.corporateActionFeedback(code).length > 10)
    }
}
