package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/** Tahap A6: buku pesanan 5.000 investor dan rilis data resmi yang tertunda/direvisi. */
class MarketTest {

    @Test
    fun investorPool_is5000_inFourClasses() {
        assertEquals(5_000, InvestorPool.TOTAL)
        assertEquals(4, InvestorClass.values().size)
        assertTrue(InvestorPool.WEIGHT > 0.0)
    }

    @Test
    fun normalCdf_isAccurate_andSymmetric() {
        assertEquals(0.5, normalCdf(0.0), 1e-7)
        assertEquals(0.975, normalCdf(1.959964), 1e-6)
        assertEquals(0.8413447, normalCdf(1.0), 1e-6)
        for (x in listOf(0.3, 1.2, 2.7)) assertEquals(1.0, normalCdf(x) + normalCdf(-x), 1e-9)
    }

    private fun meanFlow(lnMis: Double, ret3m: Double = 0.0, mood: Double = 0.0, riskOff: Double = 0.0, seeds: Int = 60): Pair<Double, DoubleArray> {
        var imb = 0.0; val by = DoubleArray(4)
        for (s in 1..seeds) {
            val f = InvestorBook().trade("X", Random(s.toLong()), lnMis, ret3m, 1.0, mood, riskOff)
            imb += f.imbalance
            for (i in 0..3) by[i] += f.byClass[i] / seeds
        }
        return imb / seeds to by
    }

    @Test
    fun orderBook_buysWhenCheap_sellsWhenExpensive_andRespectsBounds() {
        val (cheap, _) = meanFlow(0.4)
        val (dear, _) = meanFlow(-0.4)
        val (fair, _) = meanFlow(0.0)
        assertTrue("murah -> pembeli bersih: $cheap", cheap > 0.10)
        assertTrue("mahal -> penjual bersih: $dear", dear < -0.10)
        assertTrue("wajar -> hampir seimbang: $fair", abs(fair) < 0.06)
        for (s in 1..200) {
            val f = InvestorBook().trade("Y", Random(s.toLong()), (s % 9 - 4) * 0.5, (s % 7 - 3) * 0.05, 1.3, (s % 5 - 2) * 0.1, (s % 4) * 0.3)
            assertTrue(f.imbalance in -1.0..1.0)
            assertTrue(abs(f.dLnPrice) <= InvestorBook.MAX_MOVE + 1e-12)
        }
    }

    @Test
    fun orderBook_classesBehaveAsLabelled() {
        val v = InvestorClass.VALUE.ordinal; val t = InvestorClass.TREND.ordinal; val i = InvestorClass.INSTITUTION.ordinal
        val (_, up) = meanFlow(0.0, ret3m = 0.08)
        val (_, down) = meanFlow(0.0, ret3m = -0.08)
        assertTrue("pengikut tren membeli saat naik", up[t] > 100.0)
        assertTrue("pengikut tren menjual saat turun", down[t] < -100.0)
        assertTrue("investor nilai tidak bereaksi pada tren: ${up[v]}", abs(up[v]) < 80.0)
        val (_, calm) = meanFlow(0.0, riskOff = 0.0)
        val (_, panic) = meanFlow(0.0, riskOff = 0.9)
        assertTrue("institusi menjual saat risk-off: ${calm[i]} -> ${panic[i]}", panic[i] < calm[i] - 150.0)
    }

    @Test
    fun orderBook_isDeterministic() {
        fun run(): List<Double> {
            val b = InvestorBook(); val out = ArrayList<Double>()
            for (s in 1..50) out += b.trade("Z", Random(s.toLong()), 0.1, 0.01, 1.0, 0.02, 0.1).dLnPrice
            return out
        }
        assertEquals(run(), run())
    }

    @Test
    fun priceMeanReverts_toFairValue_andDoesNotExplodeWithMomentum() {
        // Satu saham, nilai wajar tetap: mulai 30% di bawah; rata-rata kembali mendekati wajar dalam setahun.
        var sumGap = 0.0
        val seeds = 60
        for (s in 1..seeds) {
            val book = InvestorBook(); val r = Random(s.toLong())
            var p = 0.7; val hist = ArrayList<Double>(listOf(p, p, p, p))
            repeat(12) {
                val ret3m = ln(hist.last() / hist[hist.size - 4]) / 3.0
                val f = book.trade("A", r, ln(1.0 / p), ret3m, 1.0, 0.0, 0.0)
                p *= exp(f.dLnPrice); hist += p
            }
            sumGap += abs(ln(p))
        }
        assertTrue("selisih rata-rata setelah 12 bulan ${sumGap / seeds}", sumGap / seeds < 0.12)
        // 600 bulan tanpa berita fundamental: harga tetap terikat di sekitar nilai wajar (umpan balik tren terbatas).
        var worst = 0.0
        for (s in 1..20) {
            val book = InvestorBook(); val r = Random(1000L + s)
            var p = 1.0; val hist = ArrayList<Double>(listOf(p, p, p, p))
            repeat(600) {
                val ret3m = ln(hist.last() / hist[hist.size - 4]) / 3.0
                p *= exp(book.trade("B", r, ln(1.0 / p), ret3m, 1.0, 0.0, 0.0).dLnPrice + 0.03 * stdNormal(r)); hist += p
                worst = Math.max(worst, abs(ln(p)))
            }
        }
        assertTrue("harga tidak boleh meledak: $worst", worst < 1.0)
    }

    // ---------------- Rilis data ----------------
    private fun truthInput(turn: Int): MacroInput =
        MacroInput(3.0 + 0.02 * turn + 0.5 * Math.sin(turn / 5.0), 0.0, 5.0 + 0.5 * Math.cos(turn / 7.0), 6.0, 6.0, 2.5 + Math.sin(turn / 11.0))

    @Test
    fun dataDesk_releasesAreDelayed_noisy_thenRevisedToTruth() {
        val d = DataDesk()
        val publishedFirst = HashMap<Int, Int>()
        for (t in 0..60) {
            d.step(t, Random(t.toLong()), { truthInput(t) }, mutableListOf())
            for (rel in d.releases("sundara", DataKind.INFLASI)) if (rel.revision == 0) publishedFirst.putIfAbsent(rel.period, rel.published)
            d.latest("sundara", DataKind.INFLASI)?.let { assertTrue("tidak boleh terbit sebelum periodenya", it.published >= it.period) }
        }
        assertTrue(publishedFirst.isNotEmpty())
        for ((p, pub) in publishedFirst) assertEquals("inflasi periode $p terbit jeda 1 bulan", p + 1, pub)
        // PDB triwulanan: periode hanya di akhir triwulan, terbit 2 bulan kemudian.
        val gdp = d.releases("sundara", DataKind.PDB).filter { it.revision == 0 }
        assertTrue(gdp.isNotEmpty())
        for (g in gdp) { assertEquals(2, g.period % 3); assertEquals(g.period + 2, g.published) }
        // Final = nilai sebenarnya persis; perkiraan awal berisik dengan simpangan ~0,25.
        val errs = ArrayList<Double>()
        for (p in 0..40) {
            val rs = d.releases("sundara", DataKind.INFLASI).filter { it.period == p }
            if (rs.any { it.isFinal }) {
                assertEquals(truthInput(p).inflation, rs.last().value, 1e-12)
                errs += rs.first().value - truthInput(p).inflation
            }
        }
        assertTrue(errs.size >= 30)
        val sd = sqrt(errs.sumOf { it * it } / errs.size)
        assertTrue("galat perkiraan awal sd=$sd", sd in 0.12..0.45)
        assertTrue("harus ada revisi nyata", d.revisionSizes("sundara", DataKind.INFLASI).any { abs(it) > 0.05 })
    }

    @Test
    fun dataDesk_isDeterministic_andNewsIsBounded() {
        fun run(): List<Double> {
            val d = DataDesk(); val out = ArrayList<Double>()
            for (t in 0..120) {
                val res = d.step(t, Random(t * 7L), { truthInput(t) }, mutableListOf())
                assertTrue(res.news in -0.05..0.05)
                out += res.news
            }
            out += d.latest("aurelia", DataKind.PDB)!!.value
            return out
        }
        assertEquals(run(), run())
    }

    @Test
    fun engine_publishesData_andStockMarketStaysFiniteAndDeterministic() {
        fun play(seed: Long): List<Double> {
            val e = KapitalEngine(seed)
            val out = ArrayList<Double>()
            var t = 0
            while (e.turn < 150 && e.gameOverReason == null) {
                t++; TestBots.disciplinedStep(e, t)
                if (e.gameOverReason != null) break
                e.apply(Action.EndTurn)
                for (c in e.bourse.companies) {
                    assertTrue("harga ${c.spec.ticker} terbatas: ${c.price}", c.price.isFinite() && c.price > 0.0)
                    assertTrue(c.sentiment.isFinite())
                }
                if (e.turn % 10 == 0) out += e.bourse.index
            }
            val rel = e.dunia.data.latest("sundara", DataKind.INFLASI)
            assertNotNull(rel)
            assertTrue(rel!!.published <= e.turn)
            return out
        }
        assertEquals(play(31L), play(31L))
        assertTrue(play(31L) != play(32L))
    }
}
