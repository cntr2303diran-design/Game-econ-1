package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test

/** Tahap B9: ActiveBot mewakili pemain cakap (harga, kas minimum, leverage, lindung nilai) dan tetap menjaga pembukuan. */
class ActiveBotTest {

    private fun check(e: KapitalEngine, ctx: String) {
        assertEquals("aset = liabilitas + ekuitas $ctx", 0.0, e.totalAssets() - e.totalLiabilities() - e.equity, 1e-6)
        val d = e.lastDetail
        if (d.isNotEmpty()) {
            assertEquals("arus kas $ctx", 0.0, d["d_resid"] ?: 0.0, 1e-6)
            assertEquals("roll-forward ekuitas $ctx", 0.0, d["e_resid"] ?: 0.0, 1e-6)
        }
    }

    @Test
    fun activeBot_keepsInvariants_andUsesItsHabits() {
        var priceMoves = 0; var derivs = 0; var insured = 0; var runs = 0
        for (seed in 1L..24L) {
            val e = KapitalEngine(seed * 2654435761L % 100000007L)
            var t = 0
            var everDeriv = false
            while (e.gameOverReason == null && t < 160) {
                t++; ActiveBot.step(e, t); e.apply(Action.EndTurn)
                check(e, "seed=$seed t=$t")
                if (e.derivs.isNotEmpty()) everDeriv = true
            }
            runs++
            if (e.priceAdjPct != 0) priceMoves++
            if (everDeriv) derivs++
            if (e.insuranceLevel > 0) insured++
        }
        println("B9 kebiasaan bot: harga diubah $priceMoves/$runs, derivatif dibeli $derivs/$runs, asuransi $insured/$runs")
        assertTrue("bot menyesuaikan harga: $priceMoves/$runs", priceMoves >= runs * 0.7)
        assertTrue("bot melindungi nilai: $derivs/$runs", derivs >= runs * 0.5)
        assertTrue("bot mengasuransikan aset: $insured/$runs", insured >= runs * 0.5)
    }

    @Test
    fun activeBot_outperformsDisciplinedPassiveBot_overFullCampaigns() {
        var winA = 0; var winP = 0; val n = 60
        for (k in 1..n) {
            val seed = k * 2654435761L % 100000007L
            for (active in listOf(false, true)) {
                val e = KapitalEngine(seed)
                var t = 0
                while (e.gameOverReason == null && e.turn < 481) { t++; if (active) ActiveBot.step(e, t) else TestBots.disciplinedStep(e, t); e.apply(Action.EndTurn) }
                if (e.isVictory) { if (active) winA++ else winP++ }
            }
        }
        println("B9 menang kampanye ($n seed): pasif=$winP aktif=$winA")
        assertTrue("bot aktif (cakap) menang lebih sering daripada bot pasif: $winA vs $winP", winA >= winP + n / 10)
        assertTrue("bot aktif cakap tidak mustahil menang: $winA/$n", winA <= n * 0.9)
    }

    @Test
    fun activeBot_isDeterministic() {
        fun run(): String {
            val e = KapitalEngine(77L)
            var t = 0
            while (e.gameOverReason == null && t < 100) { t++; ActiveBot.step(e, t); e.apply(Action.EndTurn) }
            return "${e.turn}|${e.cash}|${e.equity}|${e.priceAdjPct}|${e.derivs.size}"
        }
        assertEquals(run(), run())
    }
}
