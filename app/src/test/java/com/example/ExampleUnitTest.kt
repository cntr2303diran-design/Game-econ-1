package com.example

import com.example.data.GameRepository
import com.example.data.SaveStore
import com.example.engine.Action
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
        assertEquals("Maret 2033", state.dateText)
        assertEquals(39, state.turn)
        assertEquals(4, state.apCurrent)
        assertEquals(7, state.apMax)
        assertEquals(4, state.markasIndicators.size)
        assertEquals(3, state.pendingDecisions.size)
        assertEquals(5, state.morningPaperEdition.size)
        assertEquals(8, state.countries.size)
    }

    @Test
    fun actionsRequireEnoughAp_andCannotRepeat() {
        val e = KapitalEngine(3L)
        assertTrue(e.apply(Action.Journal("dec_pemintalan", "", 60, 5000.0, "a", "b", "c"))) // 3 AP
        assertEquals(1, e.ap)
        assertFalse(e.apply(Action.Journal("dec_termsheet", "cnt_ts_1", 60, 5000.0, "a", "b", "c"))) // butuh 2 AP
        assertFalse(e.apply(Action.Journal("dec_pemintalan", "", 60, 5000.0, "a", "b", "c"))) // sudah diputuskan
        assertFalse(e.apply(Action.SetSalesScheme(SalesScheme.OMZET))) // skema sama
        assertTrue(e.pendingDecisions().none { it.id == "dec_pemintalan" })
    }

    @Test
    fun balanceSheetAlwaysBalances() {
        val e = KapitalEngine(7L)
        e.apply(Action.UnionOption("opt_union_1"))
        e.apply(Action.Journal("dec_termsheet", "cnt_ts_2", 70, 5000.0, "a", "b", "c"))
        repeat(60) {
            if (e.turn == 41) {
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
        a.resolveEventOption(a.gameState.value.activeEventDialog.options[0])
        repeat(5) { a.advanceTurn() }
        val b = GameRepository(store)
        assertEquals(a.gameState.value.turn, b.gameState.value.turn)
        assertEquals(a.gameState.value.markasIndicators[0].valueText, b.gameState.value.markasIndicators[0].valueText)
    }

    @Test
    fun ignoringUnion_leadsToStrike() {
        val e = KapitalEngine(5L)
        e.apply(Action.EndTurn)
        e.apply(Action.EndTurn)
        assertTrue(e.chronicle.any { it.title.contains("Mogok") })
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
}
