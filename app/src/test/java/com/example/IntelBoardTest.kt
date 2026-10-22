package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/** Tahap B1: riset berbayar dengan margin error, rapat dewan kuartalan, tak ada uang gratis. */
class IntelBoardTest {

    @Test
    fun actions_roundTripThroughEncoding() {
        for (a in listOf(Action.BuyIntel("pesaing", "riv_cahaya", 3), Action.BuyIntel("sektor", "ENERGI", 1), Action.BoardAnswer("naik"), Action.BoardAnswer("abstain"))) {
            assertEquals(a, Action.decode(a.encode()))
        }
        assertTrue(Action.decode("INT|x") == null)
    }

    @Test
    fun invalidPurchases_areRejected_andCostNothing() {
        val e = KapitalEngine(3L)
        val cash0 = e.cash; val ap0 = e.ap
        assertFalse(e.apply(Action.BuyIntel("asing", "x", 1)))
        assertFalse(e.apply(Action.BuyIntel("pesaing", "tidak_ada", 1)))
        assertFalse(e.apply(Action.BuyIntel("pesaing", "riv_cahaya", 0)))
        assertFalse(e.apply(Action.BuyIntel("pesaing", "riv_cahaya", 4)))
        assertFalse(e.apply(Action.BuyIntel("negara", "sundara", 1)))
        assertFalse(e.apply(Action.BoardAnswer("naik"))) // belum ada pertanyaan
        assertEquals(cash0, e.cash, 0.0); assertEquals(ap0, e.ap)
        assertTrue(e.intelReports.isEmpty())
    }

    @Test
    fun higherBudget_meansSmallerError_andBandIsHonest() {
        val n = 150
        val rms = DoubleArray(4); val cnt = IntArray(4); val covered = IntArray(4)
        for (seed in 1L..n) {
            val e = KapitalEngine(seed)
            val truth = e.world.rivals.first { it.id == "riv_cahaya" }.unitCost
            val before = e.intelReports.size
            for (tier in 1..3) {
                if (!e.apply(Action.BuyIntel("pesaing", "riv_cahaya", tier))) continue
                val rep = e.intelReports.last()
                val err = rep.estimate - truth
                rms[tier] += err * err; cnt[tier]++
                if (abs(err) <= 2 * rep.errorBand + 1e-9) covered[tier]++
                assertEquals(tier, rep.tier)
            }
            assertTrue(e.intelReports.size > before)
        }
        for (t in 1..3) assertTrue("tingkat $t terbeli cukup sering (${cnt[t]})", cnt[t] > n / 2)
        val r = (1..3).map { sqrt(rms[it] / cnt[it]) }
        assertTrue("galat menyusut menurut anggaran: $r", r[0] > r[1] && r[1] > r[2])
        // galat nyata sebanding dengan pita yang dilaporkan (0,5 × galat relatif)
        for (t in 1..3) assertEquals("RMS tingkat $t", IntelTier.errorSd(t) * 0.5, r[t - 1], IntelTier.errorSd(t) * 0.5 * 0.35)
        for (t in 1..3) assertTrue("±2 pita mencakup ≥90% (tingkat $t: ${covered[t]}/${cnt[t]})", covered[t] >= 0.90 * cnt[t])
    }

    @Test
    fun intel_costsRealMoney_noFreeCash_andKeepsBooksBalanced() {
        for (seed in listOf(5L, 9L, 21L)) {
            val control = KapitalEngine(seed); val spender = KapitalEngine(seed)
            assertTrue(spender.apply(Action.BuyIntel("sektor", "ENERGI", 3)))
            assertTrue(spender.apply(Action.BuyIntel("negara", "kestrel", 2)))
            control.apply(Action.EndTurn); spender.apply(Action.EndTurn)
            assertTrue("kas dengan riset lebih kecil: ${spender.cash} vs ${control.cash}", spender.cash < control.cash - 100.0)
            assertTrue("ekuitas juga turun", spender.equity < control.equity - 50.0)
            val diff = spender.totalAssets() - spender.totalLiabilities() - spender.equity
            assertEquals(0.0, diff, 1e-6)
        }
    }

    @Test
    fun boardMeeting_asksEveryQuarter_gradesAnswers_andIsDeterministic() {
        fun play(seed: Long, turns: Int = 60, claim: (BoardQuestion) -> String?): KapitalEngine {
            val e = KapitalEngine(seed)
            var t = 0
            while (t < turns && e.gameOverReason == null) {
                t++; TestBots.disciplinedStep(e, t)
                e.boardQuestion?.let { q -> if (q.answer == null) claim(q)?.let { c -> e.apply(Action.BoardAnswer(c)) } }
                if (e.gameOverReason != null) break
                e.apply(Action.EndTurn)
            }
            return e
        }
        val a = play(4L) { "naik" }; val b = play(4L) { "naik" }
        assertEquals(a.boardTrust, b.boardTrust); assertEquals(a.boardCorrect, b.boardCorrect); assertEquals(a.boardWrong, b.boardWrong)
        assertTrue("ada pertanyaan dewan", a.boardQuestion != null)
        assertTrue("jawaban dinilai: ${a.boardCorrect}+${a.boardWrong}", a.boardCorrect + a.boardWrong >= 5)
        assertTrue(a.boardQuestion!!.dueTurn - a.boardQuestion!!.askedTurn == 3)
        // Tidak menjawab dihukum lebih berat daripada mengelak (agregat atas 10 seed)
        var ignore = 0; var abstain = 0
        for (s in 1L..10L) { ignore += play(s) { null }.boardTrust; abstain += play(s) { "abstain" }.boardTrust }
        assertTrue("diam ($ignore) < mengelak ($abstain)", ignore < abstain)
    }

    @Test
    fun boardTrust_cannotBeFarmed_byAlwaysGuessingOneWay() {
        // Menebak satu arah terus-menerus tidak membuat kepercayaan dewan melejit: benar dan salah impas.
        var gain = 0; var runs = 0
        for (s in 1L..25L) {
            val e = KapitalEngine(s * 7919L)
            var t = 0; val start = e.boardTrust
            while (t < 60 && e.gameOverReason == null) {
                t++; TestBots.disciplinedStep(e, t)
                e.boardQuestion?.let { q -> if (q.answer == null) e.apply(Action.BoardAnswer("naik")) }
                if (e.gameOverReason != null) break
                e.apply(Action.EndTurn)
            }
            gain += (e.boardCorrect - e.boardWrong); runs++
            assertTrue(e.boardTrust in 0..100)
        }
        assertTrue("selisih benar-salah rata-rata kecil: ${gain.toDouble() / runs}", abs(gain.toDouble() / runs) < 4.0)
    }
}
