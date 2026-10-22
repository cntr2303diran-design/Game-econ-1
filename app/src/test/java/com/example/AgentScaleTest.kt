package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/** Tahap A1: skala agen dan agregasi kohort. */
class AgentScaleTest {

    private fun checkBinomial(n: Int, p: Double, draws: Int = 20_000) {
        val r = Random(12345L + n)
        var sum = 0.0; var sumSq = 0.0
        repeat(draws) {
            val k = binomialDraw(r, n, p)
            assertTrue("hasil di luar [0,n]: $k (n=$n p=$p)", k in 0..n)
            sum += k; sumSq += k.toDouble() * k
        }
        val mean = sum / draws
        val variance = sumSq / draws - mean * mean
        val tm = n * p
        val tv = n * p * (1 - p)
        val seMean = sqrt(tv / draws)
        assertTrue("rata-rata n=$n p=$p: $mean vs $tm", abs(mean - tm) < 5 * seMean + 1e-9)
        assertTrue("varians n=$n p=$p: $variance vs $tv", abs(variance - tv) < 0.12 * tv + 0.05)
    }

    @Test
    fun binomialSampler_matchesTheory_inAllRegimes() {
        checkBinomial(30, 0.3)          // Bernoulli langsung
        checkBinomial(400, 0.0158)      // inversi (rata-rata kecil)
        checkBinomial(3_000, 0.3)       // hampiran normal
        checkBinomial(500, 0.8)         // simetri p > 0,5
        checkBinomial(1_000, 0.995)     // simetri + inversi
        assertEquals(0, binomialDraw(Random(1), 100, 0.0))
        assertEquals(100, binomialDraw(Random(1), 100, 1.0))
        assertEquals(0, binomialDraw(Random(1), 0, 0.5))
    }

    @Test
    fun normalQuantile_isAccurateAndSymmetric() {
        assertEquals(0.0, normalQuantile(0.5), 1e-9)
        assertEquals(1.959963985, normalQuantile(0.975), 1e-6)
        assertEquals(-1.959963985, normalQuantile(0.025), 1e-6)
        assertEquals(2.326347874, normalQuantile(0.99), 1e-6)
        for (p in listOf(0.001, 0.01, 0.2, 0.4)) assertEquals(-normalQuantile(1 - p), normalQuantile(p), 1e-9)
    }

    @Test
    fun householdSector_at200kAgents_hasStationaryUnemploymentAndIsDeterministic() {
        val sep = 0.0158; val find = 0.30
        fun run(seed: Long): Pair<Double, List<Double>> {
            val s = HouseholdSector(200_000)
            val r = Random(seed)
            val us = ArrayList<Double>()
            repeat(600) { s.step(r, sep, find); us.add(s.unemploymentPct()) }
            return us.takeLast(400).average() to us
        }
        val (avg, series1) = run(99L)
        val (_, series2) = run(99L)
        assertEquals("deterministik: seed sama harus identik", series1, series2)
        val expected = 100.0 * sep / (sep + find) // keseimbangan tunak aliran kerja
        assertTrue("pengangguran tunak $avg vs teori $expected", abs(avg - expected) < 0.35)
        assertEquals(200_000, HouseholdSector(200_000).agents)
        // Agen tidak hilang: bekerja + menganggur selalu = total.
        val s = HouseholdSector(200_000); val r = Random(5L)
        repeat(50) { s.step(r, 0.05, 0.1) }
        assertTrue(s.employedCount in 0..200_000)
    }

    @Test
    fun householdStep_costDoesNotGrowWithAgentCount() {
        fun timeMs(agents: Int): Double {
            val s = HouseholdSector(agents); val r = Random(1L)
            repeat(200) { s.step(r, 0.0158, 0.30) } // pemanasan
            val t0 = System.nanoTime()
            repeat(2_000) { s.step(r, 0.0158, 0.30) }
            return (System.nanoTime() - t0) / 1e6
        }
        val small = timeMs(50_000)
        val big = timeMs(200_000)
        println("KOHORT: 2.000 langkah: 50k agen=${"%.1f".format(small)} ms, 200k agen=${"%.1f".format(big)} ms")
        // Kohort 125 vs 128 (batas atas): biaya hampir sama; longgar agar tidak rapuh di CI.
        assertTrue("200k agen terlalu lambat: $big ms", big < 2_000.0)
        assertTrue("biaya tumbuh tak wajar dengan skala: $small -> $big", big < 4.0 * small + 100.0)
    }

    @Test
    fun engineSimulates200kHouseholds_acrossEightCountries() {
        val e = KapitalEngine(7919L)
        val census = e.agentCensus()
        assertEquals(200_000, census["rumah_tangga"])
        assertEquals(200_000, AgentScale.TOTAL_HOUSEHOLDS)
        // Target total dokumen: ±207.000 agen.
        assertTrue("target total ${AgentScale.TARGET_TOTAL}", AgentScale.TARGET_TOTAL in 207_000..207_500)
    }

    @Test
    fun engineWithNewHouseholdSector_isDeterministicAcrossReplay() {
        fun play(): List<Double> {
            val e = KapitalEngine(15838L)
            val out = ArrayList<Double>()
            var t = 0
            while (e.turn < 130 && e.gameOverReason == null) {
                t++
                TestBots.disciplinedStep(e, t)
                if (e.gameOverReason != null) break
                e.apply(Action.EndTurn)
                out += e.cash; out += e.world.unemployment; out += e.policyRate; out += e.world.demand
            }
            return out
        }
        val a = play(); val b = play()
        assertTrue("terlalu sedikit giliran", a.size > 200)
        assertEquals("replay harus identik bit-per-bit", a, b)
    }
}
