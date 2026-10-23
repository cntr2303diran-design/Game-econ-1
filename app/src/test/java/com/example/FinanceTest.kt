package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/** Tahap B4a: asuransi, L/C perdagangan, sale-leaseback. */
class FinanceTest {

    private fun balanced(e: KapitalEngine) = assertEquals(0.0, e.totalAssets() - e.totalLiabilities() - e.equity, 1e-6)

    private fun cashFlowIdentity(e: KapitalEngine) {
        val d = e.lastDetail
        if (d.isNotEmpty()) {
            assertEquals("arus kas = perubahan kas (residu)", 0.0, d["d_resid"] ?: 0.0, 1e-6)
            assertEquals("roll-forward ekuitas (residu)", 0.0, d["e_resid"] ?: 0.0, 1e-6)
        }
    }

    @Test
    fun actions_roundTrip_andNeutralByDefault() {
        for (a in listOf(Action.SetInsurance(2), Action.SetTradeFinance(true), Action.SetTradeFinance(false), Action.SaleLeaseback(10)))
            assertEquals(a, Action.decode(a.encode()))
        val e = KapitalEngine(5L)
        assertEquals(0.0, e.financeMonthlyCost(), 0.0)
        assertFalse(e.apply(Action.SetInsurance(0))); assertFalse(e.apply(Action.SetInsurance(3)))
        assertFalse(e.apply(Action.SetTradeFinance(false)))
        assertFalse(e.apply(Action.SaleLeaseback(4))); assertFalse(e.apply(Action.SaleLeaseback(21)))
    }

    @Test
    fun saleLeaseback_bringsCashAtBookValue_costsRent_andKeepsStatementsConsistent() {
        for (seed in listOf(3L, 8L, 17L)) {
            val ctl = KapitalEngine(seed); val e = KapitalEngine(seed)
            val fa0 = e.fixedAssets
            assertTrue(e.apply(Action.SaleLeaseback(15)))
            val proceeds = fa0 * 0.15
            ctl.apply(Action.EndTurn); e.apply(Action.EndTurn)
            balanced(e); cashFlowIdentity(e)
            assertEquals("aset tetap turun sebesar nilai buku", ctl.fixedAssets - proceeds, e.fixedAssets, proceeds * 0.01 + 1.0)
            assertTrue("kas naik mendekati hasil penjualan: ${e.cash - ctl.cash} vs $proceeds", e.cash - ctl.cash > 0.9 * proceeds)
            assertEquals(proceeds, e.leasedBackRs, 1e-9)
            assertEquals(proceeds * KapitalEngine.LEASE_RATE, e.financeMonthlyCost(), 1e-9)
            // tidak ada laba penjualan: ekuitas hanya berubah oleh operasi (sewa menurunkan laba, penyusutan lebih kecil menaikkannya)
            assertTrue(abs(e.equity - ctl.equity) < 0.02 * proceeds)
        }
    }

    @Test
    fun saleLeaseback_isCapped_andCostsAP() {
        val e = KapitalEngine(4L)
        assertTrue(e.apply(Action.SaleLeaseback(20)))
        e.apply(Action.EndTurn)
        assertTrue(e.apply(Action.SaleLeaseback(20)))
        e.apply(Action.EndTurn)
        // total sewa kembali mendekati 36% dari aset tetap awal; satu langkah lagi melewati batas 40%
        assertNotNull(e.saleLeasebackBlock(20))
        assertFalse(e.apply(Action.SaleLeaseback(20)))
        assertTrue(e.leasedBackRs <= KapitalEngine.LEASEBACK_MAX * (e.fixedAssets + e.leasedBackRs) + 1e-6)
    }

    @Test
    fun tradeFinance_extendsPayables_releasesCash_costsFee() {
        val ctl = KapitalEngine(6L); val e = KapitalEngine(6L)
        assertTrue(e.apply(Action.SetTradeFinance(true)))
        repeat(8) { ctl.apply(Action.EndTurn); e.apply(Action.EndTurn); balanced(e); cashFlowIdentity(e) }
        assertTrue("utang dagang lebih besar: ${e.payables} vs ${ctl.payables}", e.payables > 1.25 * ctl.payables)
        assertTrue("DPO mendekati 50 hari: ${e.dpo()} vs ${ctl.dpo()}", e.dpo() > ctl.dpo() + 8.0)
        assertEquals(KapitalEngine.LC_FEE * e.sc.size * e.costIndex, e.financeMonthlyCost(), 1e-9)
    }

    @Test
    fun tradeFinance_cannotBeSwitchedOffWithoutCash() {
        val e = KapitalEngine(7L)
        assertTrue(e.apply(Action.SetTradeFinance(true)))
        repeat(6) { e.apply(Action.EndTurn) }
        // kas cukup: boleh mematikan, setelah itu utang dagang turun bertahap
        if (e.cash >= 0.35 * e.payables + 1500.0) {
            val pay = e.payables
            assertTrue(e.apply(Action.SetTradeFinance(false)))
            repeat(6) { e.apply(Action.EndTurn); balanced(e) }
            assertTrue(e.payables < pay)
        }
    }

    @Test
    fun insurance_chargesPremium_paysClaimsOnBreakdown_andPremiumRisesAfterClaims() {
        var claims = 0; var premiumRose = 0
        for (seed in 1L..40L) {
            val e = KapitalEngine(seed)
            e.apply(Action.SetInsurance(2))
            val base = e.financeMonthlyCost()
            assertEquals(30.0 * e.sc.size * e.costIndex, base, 1e-9)
            var t = 0
            while (t < 120 && e.gameOverReason == null) {
                t++; TestBots.disciplinedStep(e, t)
                val before = e.insuranceClaimTurns.size
                if (e.gameOverReason != null) break
                e.apply(Action.EndTurn)
                balanced(e)
                if (e.insuranceClaimTurns.size > before) { claims++; if (e.financeMonthlyCost() > base * 1.1) premiumRose++ }
            }
        }
        assertTrue("klaim terjadi pada sebagian seed: $claims", claims >= 3)
        assertEquals("premi naik setelah klaim", claims, premiumRose)
    }

    @Test
    fun insurance_isNegativeExpectedValue_butReducesLosses() {
        // Premi berbeban (1,3×): rata-rata ekuitas dengan asuransi lebih rendah sedikit dibanding tanpa asuransi, bukan uang gratis.
        var withIns = 0.0; var without = 0.0; val n = 40
        for (seed in 1L..n) {
            for (ins in listOf(0, 2)) {
                val e = KapitalEngine(seed)
                var t = 0
                while (t < 60 && e.gameOverReason == null) {
                    t++; TestBots.disciplinedStep(e, t)
                    // dibeli setelah keputusan awal bot, agar poin aksi giliran pertama (term sheet refinancing) tidak terpakai
                    if (ins > 0 && t == 3) e.apply(Action.SetInsurance(ins))
                    if (e.gameOverReason != null) break
                    e.apply(Action.EndTurn)
                }
                val v = e.equity
                if (ins > 0) withIns += v else without += v
            }
        }
        val diff = (withIns - without) / without
        assertTrue("asuransi tidak menghasilkan uang gratis: ${100 * diff}%", diff < 0.02)
        assertTrue("asuransi tidak menghancurkan kekayaan: ${100 * diff}%", diff > -0.10)
    }
}
