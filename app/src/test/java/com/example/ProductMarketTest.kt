package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/** Tahap B2: lima segmen konsumen, tren, kurva adopsi, merek dan fokus. */
class ProductMarketTest {

    @Test
    fun initialState_isNeutral_andWeightsSumToOne() {
        assertEquals(1.0, Segment.values().sumOf { it.weight }, 1e-12)
        val m = ProductMarket()
        assertEquals(1.0, m.revenueFactor(60), 0.01)
        assertEquals(5, Segment.values().size)
        assertTrue(m.level.all { it == 1 })
    }

    @Test
    fun trends_areBoundedMeanRevert_andDeterministic() {
        fun run(seed: Long): ProductMarket {
            val m = ProductMarket(); val r = kotlin.random.Random(seed)
            repeat(480) { m.step(it + 1, r) }
            return m
        }
        assertEquals(run(3L).trend.toList(), run(3L).trend.toList())
        assertTrue(run(3L).trend.toList() != run(4L).trend.toList())
        var sum = 0.0; var n = 0
        for (seed in 1L..30L) {
            val m = ProductMarket(); val r = kotlin.random.Random(seed)
            for (t in 1..480) {
                m.step(t, r)
                for (x in m.trend) { assertTrue("tren terbatas $x", x in 0.6..1.5); sum += x; n++ }
                for (x in m.survey) assertTrue(x in 0.4..1.7)
            }
        }
        assertEquals("rata-rata tren mendekati 1", 1.0, sum / n, 0.03)
    }

    @Test
    fun adoptionCurves_shiftDemandSlowly_andMonotonic() {
        val m = ProductMarket()
        var prev = Double.NEGATIVE_INFINITY
        for (turn in 0..480 step 12) { val v = m.secular(Segment.MUDA, turn); assertTrue(v >= prev - 1e-12); prev = v }
        assertTrue("segmen muda tumbuh >= 20% dari tren dasar pada akhir: ${m.secular(Segment.MUDA, 480)}", m.secular(Segment.MUDA, 480) > 1.15)
        assertTrue("segmen hemat menyusut: ${m.secular(Segment.HEMAT, 480)}", m.secular(Segment.HEMAT, 480) < 0.97)
        assertEquals(1.0, m.secular(Segment.KELUARGA, 200) , 0.15) // netral dan tidak liar
    }

    @Test
    fun focusActions_followRules_andRoundTrip() {
        val a = Action.SetSegmentFocus("muda", 2)
        assertEquals(a, Action.decode(a.encode()))
        val e = KapitalEngine(8L)
        val ap0 = e.ap
        assertFalse(e.apply(Action.SetSegmentFocus("zzz", 1)))
        assertFalse(e.apply(Action.SetSegmentFocus("muda", 3)))
        assertFalse("sama dengan tingkat sekarang", e.apply(Action.SetSegmentFocus("muda", 1)))
        assertEquals(ap0, e.ap)
        assertTrue(e.apply(Action.SetSegmentFocus("muda", 2)))
        assertEquals(ap0 - 1, e.ap)
        assertEquals(2, e.market.level[Segment.MUDA.ordinal])
        assertFalse("jeda 6 bulan", e.apply(Action.SetSegmentFocus("muda", 0)))
        // batas total fokus: Era 1 = 6; tingkat awal 5 + naik satu = 6, naik lagi ditolak
        assertEquals(6, e.market.focusUsed())
        assertFalse(e.apply(Action.SetSegmentFocus("mode", 2)))
        assertNotNull(e.segmentFocusBlock("mode", 2))
        assertTrue(e.apply(Action.SetSegmentFocus("hemat", 0)))   // turunkan satu dulu
        assertTrue(e.apply(Action.SetSegmentFocus("mode", 2)))
    }

    @Test
    fun strategies_haveRealTradeoffs_ignoringSegmentHurts_focusIsNotFreeMoney() {
        fun gain(mode: String, n: Int = 30, turns: Int = 36): Double {
            var sum = 0.0
            for (seed in 1L..n) {
                val base = play(seed, "default", turns); val v = play(seed, mode, turns)
                sum += v / base - 1.0
            }
            return sum / n
        }
        val ignore = gain("ignore_hemat"); val focus = gain("fixed_mode")
        assertTrue("mengabaikan segmen terbesar merugikan: ${100 * ignore}%", ignore < -0.03)
        assertTrue("fokus tetap tidak jadi uang gratis: ${100 * focus}%", focus < 0.05)
        assertTrue("fokus bukan kerugian besar: ${100 * focus}%", focus > -0.05)
    }

    private fun play(seed: Long, mode: String, turns: Int): Double {
        val e = KapitalEngine(seed * 6700417L % 100000007L)
        val eq0 = e.equity.coerceAtLeast(1.0)
        var t = 0
        while (t < turns && e.gameOverReason == null) {
            t++; TestBots.disciplinedStep(e, t)
            if (t == 3) when (mode) {
                "ignore_hemat" -> e.apply(Action.SetSegmentFocus("hemat", 0))
                "fixed_mode" -> e.apply(Action.SetSegmentFocus("mode", 2))
            }
            if (e.gameOverReason != null) break
            e.apply(Action.EndTurn)
            assertEquals(0.0, e.totalAssets() - e.totalLiabilities() - e.equity, 1e-6)
        }
        return e.equity / eq0
    }

    @Test
    fun focusCost_flowsThroughOpex_andSegmentIntelIsHonest() {
        val a = KapitalEngine(12L); val b = KapitalEngine(12L)
        assertTrue(b.apply(Action.SetSegmentFocus("keluarga", 2)))
        a.apply(Action.EndTurn); b.apply(Action.EndTurn)
        assertTrue("biaya pemasaran tercatat", b.market.monthlyCost(b.sc.baseRevenue, b.costIndex) > 0.0)
        assertEquals(0.0, a.market.monthlyCost(a.sc.baseRevenue, a.costIndex), 0.0)
        // laporan segmen berderau dengan pita jujur
        var covered = 0; var n = 0
        for (seed in 1L..60L) {
            val e = KapitalEngine(seed)
            val truth = e.market.trend[Segment.MODE.ordinal] * e.market.secular(Segment.MODE, e.turn)
            if (e.apply(Action.BuyIntel("segmen", "mode", 2))) {
                val rep = e.intelReports.last(); n++
                if (abs(rep.estimate - truth) <= 2 * rep.errorBand + 1e-9) covered++
            }
        }
        assertTrue(n >= 30); assertTrue("±2 pita ≥ 90%: $covered/$n", covered >= 0.90 * n)
    }

    @Test
    fun repositoryView_listsFiveSegments_andFocusActionWorks() {
        val repo = com.example.data.GameRepository(null, 6L)
        val v0 = repo.gameState.value.productMarket
        assertEquals(5, v0.rows.size)
        assertTrue(v0.rows.all { it.level == 1 && it.blocks.size == 3 && it.blocks[1] != null })
        assertTrue(repo.setSegmentFocus("hemat", 0))
        val v1 = repo.gameState.value.productMarket
        assertEquals(0, v1.rows.first { it.code == "hemat" }.level)
        assertTrue(v1.focusText.contains("4/"))
    }
}
