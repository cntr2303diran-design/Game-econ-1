package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/** Tahap B4c: saham minoritas anak usaha dengan tawar-menawar, hak veto, dan pajak tertahan bagian NCI. */
class MinorityTest {

    private fun withSub(seed: Long): KapitalEngine? {
        val e = TestBots.playUntil(seed * 104729L, 130) ?: return null
        e.apply(Action.OpenSubsidiary("tarsis"))
        var t = 0
        while (e.gameOverReason == null && t < 40) {
            val s = e.subsidiaries["tarsis"]
            if (s != null && s.active && !s.nationalized && s.monthsActive >= 12) return e
            t++
            TestBots.disciplinedStep(e, 200 + t)
            e.apply(Action.EndTurn)
        }
        return null
    }

    @Test
    fun action_roundTrip_andLegacyDecode() {
        assertEquals(Action.SellSubStake("tarsis", 15, 25), Action.decode(Action.SellSubStake("tarsis", 15, 25).encode()))
        assertEquals(Action.SellSubStake("tarsis", 15, 20), Action.decode("NCI|tarsis|15"))
    }

    @Test
    fun blocks_beforeSubsidiaryIsMature_andForBadInputs() {
        val e = KapitalEngine(3L)
        assertNotNull(e.nciSaleBlock("tarsis", 10, 20))
        assertFalse(e.apply(Action.SellSubStake("tarsis", 10, 20)))
        val m = withSub(1L) ?: withSub(2L) ?: withSub(3L)
        assertNotNull("perlu satu seed dengan anak usaha matang", m)
        m!!
        assertNotNull(m.nciSaleBlock("tarsis", 4, 20)); assertNotNull(m.nciSaleBlock("tarsis", 10, 4)); assertNotNull(m.nciSaleBlock("tarsis", 10, 31))
    }

    @Test
    fun lowAsk_isRefused_costsAP_andCooldownBlocksRetry() {
        var checked = 0
        for (seed in 1L..12L) {
            val e = withSub(seed) ?: continue
            val ap0 = e.ap; val cash0 = e.cash; val pct0 = e.subsidiaries.getValue("tarsis").nciPct
            assertTrue("tawaran rendah tetap memakai aksi", e.apply(Action.SellSubStake("tarsis", 10, 5)))
            val s = e.subsidiaries.getValue("tarsis")
            assertEquals("diskon 5% di bawah batas minimum 8% selalu ditolak", pct0, s.nciPct, 0.0)
            assertEquals(cash0, e.cash, 0.0)
            assertEquals(ap0 - 2, e.ap)
            assertNotNull("jeda 3 bulan", e.nciSaleBlock("tarsis", 10, 30))
            assertFalse(e.apply(Action.SellSubStake("tarsis", 10, 30)))
            checked++
        }
        assertTrue("diuji pada beberapa seed: $checked", checked >= 3)
    }

    @Test
    fun acceptedPrice_isBelowFairValue_andHigherAskAcceptsMoreOften() {
        var accLow = 0; var accHigh = 0; var n = 0
        for (seed in 1L..30L) {
            for (ask in listOf(12, 30)) {
                val e = withSub(seed) ?: continue
                val value = e.subValueRs("tarsis")
                val cash0 = e.cash
                val ok = e.apply(Action.SellSubStake("tarsis", 10, ask))
                assertTrue(ok)
                val s = e.subsidiaries.getValue("tarsis")
                if (s.nciPct > 0.0) {
                    val net = e.nciSaleProceedsTotal
                    assertEquals("harga = nilai x porsi x (1-diskon) x 0,97", value * 0.10 * (1.0 - ask / 100.0) * 0.97, net, 1e-6)
                    assertTrue("selalu di bawah nilai wajar", net < value * 0.10 * 0.93)
                    assertTrue(e.cash > cash0)
                    if (ask == 12) accLow++ else accHigh++
                }
                if (ask == 12) n++
            }
        }
        println("MINORITAS penerimaan diskon12=$accLow diskon30=$accHigh dari $n")
        assertTrue("sampel cukup: $n", n >= 5)
        assertTrue("diskon lebih besar lebih sering diterima", accHigh > accLow)
    }

    @Test
    fun vetoAtTwentyPercent_blocksAggressiveTransferPricing_only() {
        var tested = false
        for (seed in 1L..30L) {
            val e = withSub(seed) ?: continue
            // jual dalam dua kali dengan diskon besar sampai mencapai 20% atau lebih
            var guard = 0
            while (e.subsidiaries.getValue("tarsis").nciPct < 0.20 - 1e-9 && guard++ < 30 && e.gameOverReason == null) {
                e.apply(Action.SellSubStake("tarsis", 10, 30))
                TestBots.disciplinedStep(e, 300 + guard); e.apply(Action.EndTurn)
            }
            if (e.subsidiaries.getValue("tarsis").nciPct < 0.20 - 1e-9) continue
            assertNotNull(e.transferPricingVeto(TransferPricingMode.AGRESIF))
            assertFalse(e.apply(Action.SetTransferPricing(TransferPricingMode.AGRESIF)))
            assertEquals(null, e.transferPricingVeto(TransferPricingMode.OPTIMAL))
            assertTrue(e.apply(Action.SetTransferPricing(TransferPricingMode.OPTIMAL)))
            tested = true
            break
        }
        assertTrue("setidaknya satu seed mencapai 20% minoritas", tested)
    }

    @Test
    fun noVeto_whenMinorityBelowTwentyPercent() {
        val e = KapitalEngine(2L)
        assertEquals(null, e.transferPricingVeto(TransferPricingMode.AGRESIF))
    }

    @Test
    fun withheldTaxOnNciShare_isTracked_andGroupIdentitiesHold() {
        var withheld = 0.0; var worst = 0.0; var ran = 0
        for (seed in 1L..25L) {
            val e = withSub(seed) ?: continue
            ran++
            var prev = e.lastConsolidatedBalance["cash"] ?: Double.NaN
            for (t in 1..30) {
                if (e.gameOverReason != null) break
                TestBots.disciplinedStep(e, 400 + t)
                if (t % 3 == 1) e.apply(Action.SellSubStake("tarsis", 5, 30))
                if (t % 3 == 0) e.apply(Action.Repatriate("tarsis"))
                e.apply(Action.EndTurn)
                worst = maxOf(worst, abs(e.lastConsolidatedEquity["resid"] ?: 0.0))
                val cash = e.lastConsolidatedBalance["cash"] ?: 0.0
                val net = e.lastConsolidatedCashFlow["net"] ?: 0.0
                if (!prev.isNaN()) assertEquals("kas grup seed=$seed", cash - prev, net, 1e-6)
                prev = cash
                assertEquals(0.0, e.totalAssets() - e.totalLiabilities() - e.equity, 1e-6)
            }
            withheld += e.nciWithheldRs
        }
        println("MINORITAS pajak tertahan atas bagian NCI total=$withheld seed=$ran residu=$worst")
        assertTrue(ran >= 5)
        assertTrue("residu ekuitas konsolidasi", worst < 1e-6)
        assertTrue("pajak tertahan atas bagian minoritas tercatat", withheld > 0.0)
    }

    @Test
    fun deterministic_sameActionsSameResult() {
        fun run(): Double {
            val e = (4L..14L).firstNotNullOf { withSub(it) } // bukan semua seed bertahan sampai giliran 130 (keseimbangan berubah di C3)
            e.apply(Action.SellSubStake("tarsis", 10, 25))
            repeat(3) { TestBots.disciplinedStep(e, 500 + it); e.apply(Action.EndTurn) }
            e.apply(Action.SellSubStake("tarsis", 10, 30))
            return e.equity + e.cash + 1e3 * e.subsidiaries.getValue("tarsis").nciPct
        }
        assertEquals(run(), run(), 0.0)
    }

    @Test
    fun repositoryView_listsStakes_andStockSeries() {
        val repo = com.example.data.GameRepository(null, 3L)
        val v = repo.gameState.value.operations
        assertTrue(v.stakes.isEmpty())
        assertTrue("tanpa IPO belum ada grafik saham", v.stockSeries.isEmpty())
        assertFalse(repo.sellSubStake("tarsis", 10, 20))
    }
}
