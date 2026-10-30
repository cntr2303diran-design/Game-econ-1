package com.example

import com.example.data.GameRepository
import com.example.data.SaveStore
import com.example.data.SlotStore
import com.example.engine.ENGINE_VERSION
import com.example.engine.KapitalEngine
import org.junit.Assert.*
import org.junit.Test

/**
 * Pemulihan simpanan di latar belakang. Memutar ulang kampanye panjang di ART yang belum hangat memakan 6 sampai 20 detik, jadi
 * aplikasi memulihkan lewat thread latar: hasilnya harus identik dengan pemuatan sinkron, perintah UI harus ditolak selama
 * pemulihan (agar tidak menimpa simpanan), dan "Bagaimana Jika" harus bisa dihitung di luar thread utama tanpa mengubah hasil.
 */
class AsyncRestoreTest {
    private class Main(var text: String?) : SaveStore {
        override fun read() = text
        override fun write(text: String) { this.text = text }
    }

    private class Mem : SlotStore {
        val data = HashMap<Int, String>()
        override fun readSlot(slot: Int) = data[slot]
        override fun writeSlot(slot: Int, text: String) { data[slot] = text }
    }

    /** Seed dan baris aksi bot disiplin yang bertahan 40 giliran. */
    private val run: Pair<Long, List<String>> by lazy {
        for (k in 1..80) {
            val seed = k * 7919L
            val save = TestBots.recordedSave(seed, KapitalEngine(seed).turn + 40) ?: continue
            return@lazy seed to save.lines().drop(1)
        }
        throw AssertionError("tidak ada seed yang bertahan 40 giliran")
    }

    private val legacyText: String by lazy { (listOf("SEED:${run.first}") + run.second).joinToString("\n") }
    private val currentText: String by lazy { (listOf("SEED:${run.first}", "VER:$ENGINE_VERSION") + run.second).joinToString("\n") }

    private fun feed(r: GameRepository) { for (l in run.second) r.corporateAction(l) }

    private fun sameState(msg: String, a: GameRepository, b: GameRepository) {
        val x = a.gameState.value
        val y = b.gameState.value
        assertEquals("$msg: giliran", x.turn, y.turn)
        assertEquals("$msg: seed", x.seed, y.seed)
        assertEquals("$msg: kas", x.cashRs, y.cashRs, 0.0)
        assertEquals("$msg: ekuitas", x.equityRs, y.equityRs, 0.0)
        assertEquals("$msg: AP", x.actionPoints, y.actionPoints)
        assertEquals("$msg: tersimpan", x.hasSavedGame, y.hasSavedGame)
        assertEquals("$msg: peringatan", x.saveNotice, y.saveNotice)
        assertEquals("$msg: kesulitan", x.difficultyLabel, y.difficultyLabel)
        assertEquals("$msg: berakhir", x.gameOverReason, y.gameOverReason)
    }

    @Test
    fun deferredRestore_rejectsCommandsUntilDone_thenMatchesSynchronousLoad() {
        val store = Main(currentText)
        val sync = GameRepository(Main(currentText))
        val r = GameRepository(store, deferRestore = true)
        assertTrue("ada pemulihan tertunda", r.needsRestore)
        val s0 = r.gameState.value
        assertTrue("keadaan awal menandai sedang memulihkan", s0.isRestoring)
        assertFalse("pengganti sementara belum dianggap permainan tersimpan", s0.hasSavedGame)
        // perintah yang mengubah permainan atau simpanan ditolak selama memulihkan
        assertFalse("aksi ditolak", r.corporateAction("END"))
        r.advanceTurn()
        r.newGame()
        r.dismissSaveNotice()
        r.toggleTheme(false)
        assertEquals("giliran tak berubah", s0.turn, r.gameState.value.turn)
        assertTrue("masih memulihkan", r.gameState.value.isRestoring)
        assertEquals("berkas simpanan tak disentuh", currentText, store.text)

        r.restorePending()
        assertFalse("pemulihan selesai", r.gameState.value.isRestoring)
        assertFalse("tak ada lagi yang tertunda", r.needsRestore)
        assertTrue("simpanan dikenali", r.gameState.value.hasSavedGame)
        sameState("sesudah pemulihan", sync, r)
        assertEquals("pemulihan awal tidak menulis ulang simpanan", currentText, store.text)
        assertEquals("penghitung pemulihan naik satu", 1, r.restoredCount.value)
        assertEquals("kemajuan penuh", 1.0, r.restoreProgress.value.toDouble(), 0.0)

        r.restorePending() // kedua kali: tak berbuat apa pun
        assertEquals(1, r.restoredCount.value)
        // perintah berjalan lagi dan menghasilkan keadaan yang sama dengan pemuatan sinkron
        assertTrue(r.corporateAction("END"))
        assertTrue(sync.corporateAction("END"))
        sameState("sesudah satu giliran lagi", sync, r)
    }

    @Test
    fun restoreOnBackgroundThread_matchesSynchronousLoad() {
        val sync = GameRepository(Main(currentText))
        val r = GameRepository(Main(currentText), deferRestore = true)
        val progress = ArrayList<Float>()
        val t = Thread { r.restorePending() }
        t.start()
        while (t.isAlive) { progress += r.restoreProgress.value; Thread.sleep(1) }
        t.join(120_000)
        assertFalse("thread pemulihan selesai", t.isAlive)
        sameState("sesudah pemulihan di thread latar", sync, r)
        assertTrue("kemajuan tidak pernah turun: $progress", progress.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(1.0, r.restoreProgress.value.toDouble(), 0.0)
    }

    @Test
    fun deferredRestore_keepsVersionNotice_forLegacyAndCurrentSaves() {
        val legacy = GameRepository(Main(legacyText), deferRestore = true)
        legacy.restorePending()
        assertNotNull("simpanan tanpa cap versi memunculkan peringatan", legacy.gameState.value.saveNotice)
        assertTrue(legacy.gameState.value.saveNotice!!.contains("versi mesin"))
        sameState("lama", GameRepository(Main(legacyText)), legacy)
        val current = GameRepository(Main(currentText), deferRestore = true)
        current.restorePending()
        assertNull("simpanan bercap versi saat ini tanpa peringatan", current.gameState.value.saveNotice)
    }

    @Test
    fun withoutSavedGame_deferRestoreStartsFreshAndNeedsNothing() {
        val store = Main(null)
        val r = GameRepository(store, deferRestore = true)
        assertFalse(r.needsRestore)
        assertFalse(r.gameState.value.isRestoring)
        assertFalse(r.gameState.value.hasSavedGame)
        assertNotNull("permainan baru langsung disimpan", store.text)
        r.restorePending() // aman dipanggil tanpa simpanan
        assertEquals(0, r.restoredCount.value)
        assertTrue("permainan baru langsung bisa dimainkan", r.corporateAction("END"))
    }

    private fun playedWithSlots(): Pair<GameRepository, Pair<Main, Mem>> {
        val slots = Mem()
        val main = Main(null)
        val r = GameRepository(main, run.first, slots)
        feed(r)
        return r to (main to slots)
    }

    @Test
    fun deferredSlotLoad_matchesSynchronousLoad_andWritesMainSave() {
        val (a, am) = playedWithSlots()
        val (b, bm) = playedWithSlots()
        assertTrue("autosave tersedia", am.second.data.isNotEmpty())
        val slot = am.second.data.keys.sorted().first()
        assertEquals("kedua repositori identik sebelum memuat", am.first.text, bm.first.text)
        assertTrue(a.loadSlot(slot))

        val before = b.gameState.value
        val mainBefore = bm.first.text
        assertTrue("slot sah dimulai", b.beginLoadSlot(slot))
        assertTrue("menandai sedang memulihkan", b.gameState.value.isRestoring)
        assertEquals("keadaan lama masih tampil sampai selesai", before.turn, b.gameState.value.turn)
        assertFalse("aksi ditolak selama memulihkan", b.corporateAction("END"))
        assertFalse("muat slot kedua ditolak selama memulihkan", b.beginLoadSlot(slot))
        assertEquals("berkas utama belum berubah sebelum pemulihan selesai", mainBefore, bm.first.text)
        b.restorePending()
        assertFalse(b.gameState.value.isRestoring)
        sameState("muat slot", a, b)
        assertEquals("simpanan utama ditulis dari hasil pemulihan sama seperti jalur sinkron", am.first.text, bm.first.text)
        assertEquals(1, b.restoredCount.value)
    }

    @Test
    fun beginLoadSlot_rejectsEmptyOrBrokenSlots_withoutTouchingState() {
        val (r, m) = playedWithSlots()
        val s = r.gameState.value
        assertFalse("slot kosong ditolak", GameRepository(Main(null), 1L, Mem()).beginLoadSlot(3))
        m.second.data[2] = "bukan simpanan\nxyz"
        assertFalse("slot tanpa SEED ditolak", r.beginLoadSlot(2))
        assertFalse("tak menandai memulihkan", r.gameState.value.isRestoring)
        assertEquals(s.turn, r.gameState.value.turn)
        assertFalse(r.needsRestore)
    }

    @Test
    fun backgroundCounterfactual_matchesSynchronous_evenIfTheGameAdvancesMeanwhile() {
        val (a, _) = playedWithSlots()
        val (b, _) = playedWithSlots()
        val index = 3
        a.runCounterfactual(index)
        val expected = a.gameState.value.counterfactualText
        assertNotNull("jalur sinkron memberi teks", expected)
        assertFalse("tidak sibuk sesudah selesai", a.gameState.value.counterfactualBusy)

        val job = b.beginCounterfactual(index)
        assertNotNull(job)
        assertTrue("layar ditandai sibuk", b.gameState.value.counterfactualBusy)
        assertNull("teks lama dibersihkan selama menghitung", b.gameState.value.counterfactualText)
        var text: String? = null
        val t = Thread { text = job!!.run() }
        t.start()
        b.advanceTurn() // permainan terus berjalan di thread utama; hasil dihitung dari keadaan yang dibekukan
        t.join(120_000)
        assertFalse(t.isAlive)
        b.finishCounterfactual(text!!)
        assertFalse("tidak sibuk lagi", b.gameState.value.counterfactualBusy)
        assertEquals("teks identik dengan jalur sinkron", expected, b.gameState.value.counterfactualText)
    }

    @Test
    fun restoringAndIronman_refuseCounterfactual() {
        val r = GameRepository(Main(currentText), deferRestore = true)
        assertNull("sedang memulihkan: ditolak", r.beginCounterfactual(1))
        assertFalse(r.gameState.value.counterfactualBusy)
        r.restorePending()
        assertNotNull("sesudah pulih: boleh", r.beginCounterfactual(1))
        val iron = GameRepository(null, 3L, null, newSeed = { 99L })
        iron.newGame(com.example.engine.Difficulty.KRISIS_ABADI)
        iron.advanceTurn()
        assertNull("Ironman: ditolak", iron.beginCounterfactual(0))
        assertFalse(iron.gameState.value.counterfactualBusy)
    }
}
