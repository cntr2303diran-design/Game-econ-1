package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test

/** C4: mode Gubernur dan Manajer Dana. */
class RolesTest {
    companion object {
        /** Gubernur yang mengikuti sinyal aturan Taylor, dibatasi laju perubahan. */
        fun taylorGovernor(g: GovernorGame) {
            val target = g.sim.world.taylorStar
            val next = (Math.round(target / 0.25) * 0.25).coerceIn(g.policy - 1.0, g.policy + 1.0).coerceIn(1.0, 16.0)
            g.apply(RoleAction.SetPolicy(next))
            g.apply(RoleAction.NextMonth)
        }
        fun holdGovernor(g: GovernorGame) { g.apply(RoleAction.NextMonth) }
        /** Gubernur ceroboh: selalu memangkas bunga ke batas bawah (mengejar pertumbuhan). */
        fun doveGovernor(g: GovernorGame) {
            g.apply(RoleAction.SetPolicy((g.policy - 1.0).coerceAtLeast(1.0)))
            g.apply(RoleAction.NextMonth)
        }
        fun hawkGovernor(g: GovernorGame) {
            g.apply(RoleAction.SetPolicy((g.policy + 1.0).coerceAtMost(16.0)))
            g.apply(RoleAction.NextMonth)
        }
        private fun fiscalMove(g: MinisterGame, spend: Double, tax: Double) {
            val ds = (spend.coerceIn(MinisterGame.MIN_SPEND, MinisterGame.MAX_SPEND) - g.spending).coerceIn(-1.0, 1.0)
            if (ds != 0.0) g.apply(RoleAction.SetSpending(g.spending + ds))
            val dt = (tax.coerceIn(MinisterGame.MIN_TAX, MinisterGame.MAX_TAX) - g.tax).coerceIn(-1.0, 1.0)
            if (dt != 0.0) g.apply(RoleAction.SetTax(g.tax + dt))
        }
        fun holdMinister(g: MinisterGame) { g.apply(RoleAction.NextMonth) }
        /** Kontra-siklus: belanja naik dan pajak turun saat ekonomi lesu, sebaliknya saat panas. */
        fun counterCyclicalMinister(g: MinisterGame) {
            val gap = g.sim.world.gap * 100.0
            fiscalMove(g, 17.0 - 0.8 * gap, 15.0 + 0.2 * gap); g.apply(RoleAction.NextMonth)
        }
        /** Pro-siklus: kebalikannya. */
        fun proCyclicalMinister(g: MinisterGame) {
            val gap = g.sim.world.gap * 100.0
            fiscalMove(g, 17.0 + 0.8 * gap, 15.0 - 0.2 * gap); g.apply(RoleAction.NextMonth)
        }
        fun spendthriftMinister(g: MinisterGame) { fiscalMove(g, 30.0, 8.0); g.apply(RoleAction.NextMonth) }
        fun cashFund(f: FundGame) { f.apply(RoleAction.NextMonth) }
        /** Beli bobot sama pada semua emiten di bulan pertama lalu tahan. */
        fun indexFund(f: FundGame) {
            if (f.months == 0 && f.holdings.isEmpty()) {
                val cs = f.sim.bourse.companies.filter { it.listed && it.price > 0.0 }
                val each = f.cash * 0.97 / cs.size
                for (c in cs) {
                    val lots = (each / (KapitalEngine.LOT * c.price * (1.0 + KapitalEngine.BROKER_FEE))).toInt()
                    if (lots > 0) f.apply(RoleAction.Buy(c.spec.id, lots))
                }
            }
            f.apply(RoleAction.NextMonth)
        }
        /** Penjudi: memusatkan dana pada satu emiten (batas bobot) lalu berpindah tiap tahun. */
        fun gamblerFund(f: FundGame) {
            if (f.months % 12 == 0) {
                for ((id, lots) in f.holdings.toMap()) f.apply(RoleAction.Sell(id, lots))
                val cs = f.sim.bourse.companies.filter { it.listed && it.price > 0.0 }
                val c = cs[(f.months / 12 + f.seed.toInt().let { Math.abs(it) }) % cs.size]
                val lots = (f.nav() * 0.34 / (KapitalEngine.LOT * c.price * (1.0 + KapitalEngine.BROKER_FEE))).toInt()
                if (lots > 0) f.apply(RoleAction.Buy(c.spec.id, lots))
            }
            f.apply(RoleAction.NextMonth)
        }
    }

    private fun runGov(seed: Long, bot: (GovernorGame) -> Unit, horizon: Int = 120): GovernorGame {
        val g = GovernorGame(seed, horizon)
        while (!g.finished) bot(g)
        return g
    }

    private fun runMin(seed: Long, bot: (MinisterGame) -> Unit, horizon: Int = 120): MinisterGame {
        val g = MinisterGame(seed, horizon)
        while (!g.finished) bot(g)
        return g
    }

    private fun runFund(seed: Long, bot: (FundGame) -> Unit, horizon: Int = 120): FundGame {
        val f = FundGame(seed, horizon)
        while (!f.finished) bot(f)
        return f
    }

    @Test
    fun actionCodec_roundTrip() {
        for (a in listOf(RoleAction.NextMonth, RoleAction.SetPolicy(6.25), RoleAction.Buy("sx_mitra", 12), RoleAction.Sell("sx_cahaya", 3), RoleAction.SetSpending(18.5), RoleAction.SetTax(14.0)))
            assertEquals(a, RoleAction.decode(a.encode()))
        assertTrue(RoleAction.decode("???") == null)
        assertTrue(RoleAction.decode("BUY|x") == null)
    }

    @Test
    fun governor_isDeterministic_andReplayMatches() {
        val a = runGov(5L, ::taylorGovernor, 60)
        val b = runGov(5L, ::taylorGovernor, 60)
        assertEquals(a.score(), b.score()); assertEquals(a.log, b.log)
        val r = RoleGames.replay(Role.GUBERNUR, 5L, 60, a.log) as GovernorGame
        assertEquals(a.avgLoss(), r.avgLoss(), 0.0)
        assertEquals(a.sim.world.inflation, r.sim.world.inflation, 0.0)
    }

    @Test
    fun governor_rulesAreEnforced() {
        val g = GovernorGame(3L, 12)
        assertFalse("lompatan >1,5 poin ditolak", g.apply(RoleAction.SetPolicy(g.policy + 2.0)))
        assertFalse("di bawah 1% ditolak", g.apply(RoleAction.SetPolicy(0.5)))
        assertFalse("aksi dana ditolak", g.apply(RoleAction.Buy("sx_mitra", 1)))
        assertTrue(g.apply(RoleAction.SetPolicy(g.policy + 1.0)))
        repeat(12) { assertTrue(g.apply(RoleAction.NextMonth)) }
        assertTrue(g.finished)
        assertFalse("setelah selesai tak bisa bertindak", g.apply(RoleAction.NextMonth))
    }

    @Test
    fun governor_skillIsRewarded_recklessIsNot() {
        val seeds = 1L..24L
        fun avg(bot: (GovernorGame) -> Unit) = seeds.map { runGov(it * 7919L, bot).score() }.average()
        val taylor = avg(::taylorGovernor); val hold = avg(::holdGovernor); val dove = avg(::doveGovernor); val hawk = avg(::hawkGovernor)
        println("GUBERNUR skor rata-rata: taylor=${"%.1f".format(taylor)} tahan=${"%.1f".format(hold)} dovish=${"%.1f".format(dove)} hawkish=${"%.1f".format(hawk)}")
        assertTrue("mengikuti sinyal lebih baik daripada diam: $taylor vs $hold", taylor > hold + 5.0)
        assertTrue("ceroboh jauh lebih buruk: $taylor vs $dove, $hawk", taylor > dove + 25.0 && taylor > hawk + 25.0)
        assertTrue("skor tetap dalam 0..100", listOf(taylor, hold, dove, hawk).all { it in 0.0..100.0 })
    }

    @Test
    fun minister_isDeterministic_andReplayMatches() {
        val a = runMin(5L, ::counterCyclicalMinister, 60)
        val b = runMin(5L, ::counterCyclicalMinister, 60)
        assertEquals(a.score(), b.score()); assertEquals(a.log, b.log)
        val r = RoleGames.replay(Role.MENTERI, 5L, 60, a.log) as MinisterGame
        assertEquals(a.debt, r.debt, 0.0)
        assertEquals(a.avgLoss(), r.avgLoss(), 0.0)
        assertTrue("log berisi aksi fiskal", a.log.any { it.startsWith("SPD|") } && a.log.any { it.startsWith("TAX|") })
    }

    @Test
    fun minister_rulesAreEnforced() {
        val g = MinisterGame(3L, 12)
        assertFalse("lompatan belanja >1 poin ditolak", g.apply(RoleAction.SetSpending(g.spending + 2.0)))
        assertFalse("pajak di atas batas ditolak", g.apply(RoleAction.SetTax(40.0)))
        assertFalse("aksi suku bunga bukan wewenang Menteri", g.apply(RoleAction.SetPolicy(6.0)))
        assertFalse("aksi dana ditolak", g.apply(RoleAction.Buy("sx_mitra", 1)))
        assertTrue(g.apply(RoleAction.SetSpending(g.spending + 1.0)))
        assertTrue(g.apply(RoleAction.SetTax(g.tax - 1.0)))
        repeat(12) { assertTrue(g.apply(RoleAction.NextMonth)) }
        assertTrue(g.finished)
        assertFalse("setelah selesai tak bisa bertindak", g.apply(RoleAction.NextMonth))
        assertFalse("Gubernur tidak menerima aksi fiskal", GovernorGame(3L, 12).apply(RoleAction.SetSpending(18.0)))
    }

    @Test
    fun minister_skillIsRewarded_recklessIsNot_andDebtCrisisEndsTheGame() {
        val seeds = 1L..24L
        fun avg(bot: (MinisterGame) -> Unit) = seeds.map { runMin(it * 7919L, bot).score() }.average()
        val counter = avg(::counterCyclicalMinister); val hold = avg(::holdMinister); val pro = avg(::proCyclicalMinister); val spend = avg(::spendthriftMinister)
        println("MENTERI skor rata-rata: kontra-siklus=${"%.1f".format(counter)} tahan=${"%.1f".format(hold)} pro-siklus=${"%.1f".format(pro)} boros=${"%.1f".format(spend)}")
        assertTrue("kontra-siklus lebih baik daripada diam: $counter vs $hold", counter > hold + 5.0)
        assertTrue("pro-siklus dan boros jauh lebih buruk: $counter vs $pro, $spend", counter > pro + 25.0 && counter > spend + 25.0)
        val g = runMin(9L, ::spendthriftMinister)
        assertTrue("belanja maksimum dan pajak minimum berujung gagal bayar", g.defaulted && g.finished && g.months < 120)
        assertTrue("gagal bayar membatasi skor", g.score() <= 10)
    }

    @Test
    fun fund_ledgerIsConsistent_andTradingCostsMoney() {
        val f = FundGame(7L, 12)
        val c = f.sim.bourse.companies.first { it.listed }
        assertTrue(f.apply(RoleAction.Buy(c.spec.id, 5)))
        val navAfterBuy = f.nav()
        assertTrue("fee dibayar: NAV turun setelah beli", navAfterBuy < FundGame.START_CASH)
        assertTrue(f.apply(RoleAction.Sell(c.spec.id, 5)))
        assertTrue("beli lalu jual langsung selalu rugi biaya", f.nav() < FundGame.START_CASH)
        assertEquals(f.nav(), f.cash, 1e-9)
        assertFalse("tak bisa jual yang tak dimiliki", f.apply(RoleAction.Sell(c.spec.id, 1)))
        assertFalse("tak bisa beli melebihi kas", f.apply(RoleAction.Buy(c.spec.id, 10_000_000)))
        assertFalse("batas bobot per emiten", f.apply(RoleAction.Buy(c.spec.id, (0.4 * f.nav() / (KapitalEngine.LOT * c.price)).toInt())))
        assertFalse("aksi bunga ditolak", f.apply(RoleAction.SetPolicy(6.0)))
    }

    @Test
    fun fund_isDeterministic_andReplayMatches() {
        val a = runFund(9L, ::indexFund, 48)
        val b = runFund(9L, ::indexFund, 48)
        assertEquals(a.nav(), b.nav(), 0.0)
        val r = RoleGames.replay(Role.DANA, 9L, 48, a.log) as FundGame
        assertEquals(a.nav(), r.nav(), 0.0); assertEquals(a.score(), r.score())
    }

    @Test
    fun fund_scoresSeparateStrategies() {
        val seeds = 1L..20L
        fun avg(bot: (FundGame) -> Unit) = seeds.map { runFund(it * 104729L, bot).score() }.average()
        val idx = avg(::indexFund); val cash = avg(::cashFund); val gamble = avg(::gamblerFund)
        println("DANA skor rata-rata: indeks=${"%.1f".format(idx)} kas=${"%.1f".format(cash)} penjudi=${"%.1f".format(gamble)}")
        assertTrue("skor 0..100", listOf(idx, cash, gamble).all { it in 0.0..100.0 })
        assertTrue("dana indeks mendekati netral (biaya kecil): $idx", idx in 35.0..65.0)
    }
}
