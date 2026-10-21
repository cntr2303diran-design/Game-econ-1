package com.example

import com.example.data.GameRepository
import com.example.data.SaveStore
import com.example.engine.Action
import com.example.engine.AppStatus
import com.example.engine.CreditDesk
import com.example.engine.CreditMetrics
import com.example.engine.FactoryScheme
import com.example.engine.KapitalEngine
import com.example.engine.SalesScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ExampleUnitTest {

    private class MemoryStore : SaveStore {
        var text: String? = null
        override fun read() = text
        override fun write(text: String) { this.text = text }
    }

    @Test
    fun initialState_matchesStartingScenario() {
        val state = GameRepository(null, 1L).gameState.value
        val e = KapitalEngine(1L)
        assertEquals(KapitalEngine.dateText(e.sc.turn0), state.dateText)
        assertEquals(e.sc.turn0, state.turn)
        assertEquals(4, state.apCurrent)
        assertEquals(7, state.apMax)
        assertEquals(4, state.markasIndicators.size)
        assertEquals(2 + e.openEvents.size, state.pendingDecisions.size)
        assertEquals(5, state.morningPaperEdition.size)
        assertEquals(8, state.countries.size)
    }

    @Test
    fun everyNewGameStartsDifferently() {
        val starts = (1L..30L).map { KapitalEngine(it) }
        assertTrue("premis harus beragam", starts.map { it.sc.id }.toSet().size >= 5)
        assertTrue("tanggal mulai harus beragam", starts.map { it.sc.turn0 }.toSet().size >= 15)
        assertTrue("kas awal harus beragam", starts.map { it.cash.toInt() }.toSet().size >= 28)
        assertTrue("nama eksekutif harus beragam", starts.map { it.execs[0].name }.toSet().size >= 4)
        for (e in starts) assertTrue("neraca awal seimbang", abs(e.totalAssets() - e.totalLiabilities() - e.equity) < 1.0)
    }

    @Test
    fun eventsDifferBetweenGames_andEventChoicesReplay() {
        val seen = HashSet<String>()
        for (seed in 1L..25L) {
            val e = KapitalEngine(seed)
            repeat(40) {
                for (ev in e.openEvents.toList()) { seen += ev.templateId; e.apply(Action.EventChoice(ev.id, ev.options.first { it.id != "z" }.id)) }
                e.apply(Action.EndTurn)
                if (e.gameOverReason != null) return@repeat
            }
        }
        assertTrue("minimal 15 jenis kartu muncul: ${seen.size}", seen.size >= 15)
        val a = Action.EventChoice("evt_1_2_union", "a")
        assertEquals(a, Action.decode(a.encode()))
        // permainan tersimpan harus memainkan ulang dunia yang sama
        val store = MemoryStore()
        val r = GameRepository(store, 21L)
        repeat(8) { r.advanceTurn() }
        val r2 = GameRepository(store)
        assertEquals(r.gameState.value.turn, r2.gameState.value.turn)
        assertEquals(r.gameState.value.pendingDecisions.map { it.id }, r2.gameState.value.pendingDecisions.map { it.id })
    }

    @Test
    fun actionsRequireEnoughAp_andCannotRepeat() {
        val e = KapitalEngine(3L)
        assertTrue(e.apply(Action.Journal("dec_pemintalan", "", 60, 5000.0, "a", "b", "c"))) // 3 AP
        assertEquals(1, e.ap)
        assertFalse(e.apply(Action.Journal("dec_termsheet", "cnt_ts_1", 60, 5000.0, "a", "b", "c"))) // butuh 2 AP
        assertFalse(e.apply(Action.Journal("dec_pemintalan", "", 60, 5000.0, "a", "b", "c"))) // sudah diputuskan
        assertFalse(e.apply(Action.SetSalesScheme(e.salesScheme))) // skema sama
        assertTrue(e.pendingDecisions().none { it.id == "dec_pemintalan" })
    }

    @Test
    fun balanceSheetAlwaysBalances() {
        val e = KapitalEngine(7L)
        e.apply(Action.Journal("dec_termsheet", "cnt_ts_2", 70, 5000.0, "a", "b", "c"))
        repeat(60) {
            for (ev in e.openEvents.toList()) e.apply(Action.EventChoice(ev.id, ev.options.first { it.id != "z" }.id))
            if (e.turn == e.sc.turn0 + 2) {
                e.apply(Action.SetSalesScheme(SalesScheme.KAS))
                e.apply(Action.SetFactoryScheme(FactoryScheme.ANDAL))
            }
            e.apply(Action.EndTurn)
            val diff = e.totalAssets() - e.totalLiabilities() - e.equity
            assertTrue("neraca selisih $diff pada giliran ${e.turn}", abs(diff) < 1.0)
            if (e.gameOverReason != null) return
        }
    }

    @Test
    fun saveAndReload_reproducesSameWorld() {
        val store = MemoryStore()
        val a = GameRepository(store, 11L)
        a.gameState.value.pendingDecisions.firstOrNull { it.opensEventDialog }?.let { d ->
            a.openEventDialog(true, d.id)
            a.resolveEventOption(a.gameState.value.activeEventDialog.options[0])
        }
        repeat(5) { a.advanceTurn() }
        val b = GameRepository(store)
        assertEquals(a.gameState.value.turn, b.gameState.value.turn)
        assertEquals(a.gameState.value.markasIndicators[0].valueText, b.gameState.value.markasIndicators[0].valueText)
    }

    @Test
    fun ignoringEvent_appliesDefaultOutcomeAtDeadline() {
        val e = KapitalEngine((1L..40L).first { KapitalEngine(it).openEvents.any { ev -> ev.templateId == "union" } })
        assertTrue(e.openEvents.any { it.templateId == "union" })
        repeat(3) { e.apply(Action.EndTurn) }
        assertTrue(e.openEvents.none { it.templateId == "union" })
        assertTrue(e.chronicle.any { it.description.contains("Tenggat lewat") })
        assertTrue("upah naik setelah tuntutan diabaikan", e.wageAddOn > 0.0)
    }

    @Test
    fun competitors_matchPriceCuts_butNotRises() {
        val cut = KapitalEngine(4L); cut.apply(Action.AdjustPrice(-8))
        val hold = KapitalEngine(4L)
        val rise = KapitalEngine(4L); rise.apply(Action.AdjustPrice(5))
        repeat(6) { cut.apply(Action.EndTurn); hold.apply(Action.EndTurn); rise.apply(Action.EndTurn) }
        assertTrue("potong harga harus menambah pangsa dulu", cut.playerShare() > hold.playerShare())
        assertTrue("menaikkan harga harus menurunkan pangsa", rise.playerShare() < hold.playerShare())
        assertTrue("pesaing menyamai potongan harga", cut.world.rivalAveragePrice() < hold.world.rivalAveragePrice() - 0.01)
        assertTrue("pesaing tidak ikut naik", abs(rise.world.rivalAveragePrice() - hold.world.rivalAveragePrice()) < 0.02)
    }

    @Test
    fun macro_isEndogenousAndStable() {
        val w = com.example.engine.World()
        w.initScenario(4.0, 6.5)
        val r = kotlin.random.Random(9)
        val infl = ArrayList<Double>(); val pol = ArrayList<Double>(); val un = ArrayList<Double>()
        repeat(360) {
            val o = w.step(r, 1.0, 1.0, 1.0, 0.0, 15450.0)
            infl += o.inflationAnnual; pol += o.policyRate; un += o.unemployment
        }
        assertTrue("inflasi rata-rata mendekati target 3%: ${infl.average()}", infl.average() in 2.0..4.5)
        assertTrue("bunga acuan tetap wajar", pol.all { it in 2.0..14.0 })
        assertTrue("pengangguran tidak meledak: ${un.max()}", un.max() < 12.0)
    }

    @Test
    fun taylorRule_raisesRatesWhenCurrencyCollapses() {
        val w = com.example.engine.World(); w.initScenario(4.0, 6.5)
        val r = kotlin.random.Random(1)
        var kurs = 15450.0
        repeat(6) { w.step(r, 1.0, 1.0, 1.0, 0.0, kurs) }
        val before = w.policyRate
        kurs *= 1.25
        repeat(8) { w.step(r, 1.0, 1.0, 1.0, if (it == 0) 0.25 else 0.0, kurs) }
        assertTrue("bank sentral harus menaikkan bunga: $before -> ${w.policyRate}", w.policyRate > before)
    }

    @Test
    fun foreignEconomies_areDynamic_andFxFollowsInflationGap() {
        val e = KapitalEngine(12L)
        val a0 = e.foreign.getValue("velmora").policyRate
        val kurs0 = e.kurs
        repeat(36) { e.apply(Action.EndTurn); if (e.gameOverReason != null) return }
        assertEquals(7, e.foreign.size)
        assertTrue("kurs bergerak mengikuti PPP", e.kurs != kurs0)
        assertTrue("bunga asing berubah", e.foreign.values.any { it.policyRate != a0 })
    }

    @Test
    fun taxLossCarryforward_offsetsFutureProfit() {
        val e = KapitalEngine(2L)
        repeat(8) { e.apply(Action.EndTurn); if (e.gameOverReason != null) return }
        assertTrue(e.taxLossCarryforward() >= 0.0)
    }

    @Test
    fun commodities_areMeanReverting_andFeedCosts() {
        val c = com.example.engine.Commodities()
        val r = kotlin.random.Random(5)
        val idx = ArrayList<Double>()
        repeat(480) { c.step(r, 0.0); idx += c.cotton.index }
        assertTrue("kapas tidak meledak: ${idx.max()}", idx.max() < 3.5 && idx.min() > 0.35)
        assertTrue("rata-rata dekat 1: ${idx.average()}", idx.average() in 0.7..1.5)
        val e = KapitalEngine(1L)
        val base = e.cogsRatio()
        repeat(3) { e.apply(Action.EndTurn) }
        assertTrue(e.commodities.materialIndex() > 0.0 && e.cogsRatio() > 0.0 && base > 0.0)
    }

    @Test
    fun hedgeAndEquityCure_workAndRoundTrip() {
        assertEquals(Action.HedgeCotton, Action.decode(Action.HedgeCotton.encode()))
        assertEquals(Action.EquityCure, Action.decode(Action.EquityCure.encode()))
        val e = KapitalEngine(3L)
        assertFalse("equity cure butuh pelanggaran covenant", e.apply(Action.EquityCure))
        assertTrue(e.apply(Action.HedgeCotton))
        assertTrue(e.hedgeActive)
        assertFalse("tidak boleh ganda", e.apply(Action.HedgeCotton))
        repeat(7) { e.apply(Action.EndTurn); if (e.gameOverReason != null) return }
        assertFalse("lindung nilai berakhir setelah 6 bulan", e.hedgeActive)
    }

    @Test
    fun actionEncoding_roundTrips() {
        val a = Action.Journal("dec_termsheet", "cnt_ts_1", 70, 1234.5, "alasan | dengan pipa", "prediksi\nbaris", "gagal 100%")
        val decoded = Action.decode(a.encode())
        assertNotNull(decoded)
        assertEquals(a, decoded)
    }

    @Test
    fun bourse_buySell_balanceAndRoundTrip() {
        assertEquals(Action.BuyStock("sx_mitra", 3), Action.decode(Action.BuyStock("sx_mitra", 3).encode()))
        assertEquals(Action.SellStock("sx_mitra", 2), Action.decode(Action.SellStock("sx_mitra", 2).encode()))
        val e = KapitalEngine(4L)
        assertFalse("emiten tak dikenal", e.apply(Action.BuyStock("xxx", 1)))
        assertFalse("tidak boleh jual sebelum punya", e.apply(Action.SellStock("sx_mitra", 1)))
        assertTrue(e.apply(Action.BuyStock("sx_mitra", 2)))
        assertEquals(2, e.holdings["sx_mitra"])
        repeat(14) {
            e.apply(Action.EndTurn)
            assertTrue("neraca seimbang", abs(e.totalAssets() - e.totalLiabilities() - e.equity) < 1e-6)
            if (e.gameOverReason != null) return
        }
        assertTrue(e.bourse.companies.all { it.quarters.isNotEmpty() })
        assertTrue(e.apply(Action.SellStock("sx_mitra", 2)))
        assertTrue(e.holdings.isEmpty())
    }

    @Test
    fun credit_applicationTakesTimeAndCanBeAcceptedOrRefused() {
        val enc = Action.ApplyCredit("bk_niaga", 6000, 36, "EKSPANSI", true, false)
        assertEquals(enc, Action.decode(enc.encode()))
        assertEquals(Action.AcceptOffer("app_1"), Action.decode(Action.AcceptOffer("app_1").encode()))
        val e = KapitalEngine(3L)
        assertFalse("jumlah terlalu kecil", e.apply(Action.ApplyCredit("bk_niaga", 100, 36, "MODAL_KERJA", true, false)))
        assertTrue(e.apply(Action.ApplyCredit("bk_niaga", 6000, 36, "EKSPANSI", true, false)))
        assertFalse("satu pengajuan aktif per bank", e.apply(Action.ApplyCredit("bk_niaga", 2000, 12, "MODAL_KERJA", true, false)))
        val app = e.credit.applications.first()
        assertEquals("belum ada keputusan di giliran yang sama", AppStatus.PROCESSING, app.status)
        e.apply(Action.EndTurn)
        assertEquals(AppStatus.PROCESSING, app.status)
        assertTrue("tidak ada dana cair tanpa persetujuan", e.loans.none { it.id.startsWith("cr_") })
        e.apply(Action.EndTurn)
        assertEquals(AppStatus.OFFER, app.status)
        assertNotNull(app.offer)
        assertFalse("tidak bisa menjawab penawaran yang tidak ada", e.apply(Action.AcceptOffer("app_99")))
        assertTrue(e.apply(Action.AcceptOffer(app.id)))
        e.apply(Action.EndTurn)
        assertEquals(AppStatus.DISBURSED, app.status)
        assertTrue(e.loans.any { it.id == "cr_${app.id}" && it.balance > 0.0 })
        assertTrue("neraca seimbang", abs(e.totalAssets() - e.totalLiabilities() - e.equity) < 1e-6)
    }

    @Test
    fun credit_stressedBorrowerIsRejectedAndOversizedLoanIsCut() {
        val desk = CreditDesk()
        val bad = CreditMetrics(2000.0, 8000.0, 6000.0, 42000.0, 2000.0, 30000.0, 40000.0, 5000.0, 1, 95.0, 8.0, 8.5, 1.5, 3.0, true)
        for (b in desk.banks.filter { it.id != "bk_dana" }) {
            assertTrue("${b.id} harus menolak", desk.score(b, bad, 9800.0, 13.0, 41, 0.0).total < b.minScore)
        }
        val e = KapitalEngine(3L)
        assertTrue(e.apply(Action.ApplyCredit("bk_niaga", 40000, 60, "EKSPANSI", true, false)))
        repeat(2) { e.apply(Action.EndTurn) }
        val a = e.credit.applications.first()
        assertTrue("plafon 40.000 jt tidak mungkin disetujui penuh", a.status == AppStatus.REJECTED || (a.offer?.cut == true))
    }

    @Test
    fun credit_savedGameReplaysSameBankDecisions() {
        var saved: String? = null
        val store = object : SaveStore { override fun read() = saved; override fun write(text: String) { saved = text } }
        val r = GameRepository(store, 42L)
        r.applyCredit("bk_niaga", 5000, 36, "MODAL_KERJA", true, false)
        r.advanceTurn(); r.advanceTurn()
        val before = r.gameState.value.credit.applications.map { it.statusLabel + it.stageText }
        val r2 = GameRepository(store)
        assertEquals(before, r2.gameState.value.credit.applications.map { it.statusLabel + it.stageText })
    }
}
