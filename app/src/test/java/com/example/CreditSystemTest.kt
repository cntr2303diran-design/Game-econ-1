package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Tahap A1/A5: 2.000 perusahaan dan 120 bank sebagai agen, dan sensus 207.168 agen. */
class CreditSystemTest {

    private fun calm(gap: Double = 0.0, unemp: Double = 5.0, policy: Double = 6.0, infl: Double = 4.0) =
        MacroInput(infl, gap, unemp, policy, policy, 2.5 + 40.0 * gap)

    private val fullAvail = DoubleArray(Sector.values().size) { 1.0 }

    private fun run(
        seed: Long, months: Int, input: (Int, String) -> MacroInput,
        avail: (Int) -> DoubleArray = { fullAvail }, panic: (Int) -> Double = { 0.0 },
        onStep: (Int, CreditSystem) -> Unit = { _, _ -> }
    ): CreditSystem {
        val cs = CreditSystem(seed)
        for (t in 1..months) {
            cs.step(Random(seed * 31 + t), { id -> input(t, id) }, 1.0, avail(t), panic(t), mutableListOf())
            onStep(t, cs)
        }
        return cs
    }

    @Test
    fun agentCounts_are2000Firms_120Banks_acrossEightCountries() {
        val cs = CreditSystem(1L)
        assertEquals(2_000, cs.firmCount)
        assertEquals(120, cs.bankCount)
        assertEquals(2_000, CreditAgents.TOTAL_FIRMS)
        assertEquals(120, CreditAgents.TOTAL_BANKS)
        assertEquals(8, cs.countries.size)
        for ((id, c) in cs.countries) {
            assertEquals(CreditAgents.FIRMS.getValue(id), c.firms.size)
            assertEquals(CreditAgents.BANKS.getValue(id), c.banks.size)
        }
        assertTrue("12 sektor terwakili di Sundara", cs.countries.getValue("sundara").firms.map { it.sector }.toSet().size == 12)
    }

    @Test
    fun calmEconomy_hasLowDefaults_andNoBankFailures() {
        var totalFails = 0
        for (seed in 1L..10L) {
            val cs = run(seed, 240, { _, _ -> calm() })
            for (c in cs.countries.values) {
                totalFails += c.failuresTotal
                assertTrue("tingkat gagal bayar tenang ${c.id}: ${c.defaultRate}", c.defaultRate in (if (c.firms.size >= 100) 0.5 else 0.0)..8.0)
            }
        }
        assertTrue("tanpa tekanan bank tidak boleh sering gagal: $totalFails", totalFails <= 2)
    }

    @Test
    fun severeRecession_raisesDefaults_erodesCapital_andForcesRescues() {
        var seedsWithFailure = 0; var maxRecap = 0.0
        var calmDefault = 0.0; var stressDefault = 0.0; var calmCar = 0.0; var stressCar = 0.0
        val seeds = 12
        for (seed in 1L..seeds) {
            var failuresSeen = false
            val cs = run(seed, 150, { t, _ -> if (t < 30) calm() else calm(gap = -0.10, unemp = 12.0, policy = 12.0, infl = 3.0) },
                panic = { t -> if (t < 30) 0.0 else 70.0 }) { t, c ->
                val su = c.countries.getValue("sundara")
                if (t == 29) { calmDefault += su.defaultRate / seeds; calmCar += su.meanCar / seeds }
                if (t == 149) { stressDefault += su.defaultRate / seeds; stressCar += su.meanCar / seeds }
                if (su.failuresLastMonth > 0) { failuresSeen = true; maxRecap = Math.max(maxRecap, su.recapCost); assertTrue(su.recapCost > 0.0) }
            }
            if (failuresSeen) seedsWithFailure++
            for (c in cs.countries.values) for (b in c.banks) assertTrue("CAR dibatasi: ${b.car}", b.car in 3.9..16.0 + 1e-9)
        }
        assertTrue("gagal bayar naik: $calmDefault -> $stressDefault", stressDefault > 2.0 * calmDefault)
        assertTrue("modal bank terkikis: $calmCar -> $stressCar", stressCar < calmCar - 1.5)
        assertTrue("bank harus gagal di bawah tekanan berat ($seedsWithFailure/$seeds)", seedsWithFailure >= 8)
        assertTrue(maxRecap > 0.0)
    }

    @Test
    fun sectorShortage_hitsSundaraFirmsOnly() {
        val short = DoubleArray(Sector.values().size) { 0.5 }
        val normal = run(3L, 60, { _, _ -> calm() })
        val shortage = run(3L, 60, { _, _ -> calm() }, avail = { short })
        assertTrue("Sundara: ${normal.countries.getValue("sundara").defaultRate} -> ${shortage.countries.getValue("sundara").defaultRate}",
            shortage.countries.getValue("sundara").defaultRate > 2.0 * normal.countries.getValue("sundara").defaultRate)
        assertEquals(normal.countries.getValue("aurelia").defaultRate, shortage.countries.getValue("aurelia").defaultRate, 1e-9)
    }

    @Test
    fun creditSystem_isDeterministic() {
        fun snap(): List<Double> {
            val cs = run(9L, 100, { t, _ -> calm(gap = 0.05 * Math.sin(t / 8.0)) })
            return cs.countries.values.flatMap { c -> listOf(c.defaultRate, c.meanCar, c.weakShare) + c.banks.map { it.car } }
        }
        assertEquals(snap(), snap())
    }

    @Test
    fun bankRescue_addsToGovernmentDebt() {
        fun debt(recap: Double): Double {
            val m = MacroPlus(1L); val g = Geopolitics(1L, 60, mapOf("aurelia" to 58, "norvaldia" to 66, "kestrel" to 64, "halvenia" to 32, "meridia" to 24, "tarsis" to 44, "velmora" to 76))
            for (t in 1..24) m.step(t, Random(t.toLong()), { calm() }, g, 1.0, 1.0, false, mutableListOf(), 0.0, { if (it == "sundara" && t == 10) recap else 0.0 })
            return m.state("sundara").debtGdp
        }
        assertEquals(debt(0.0) + 1.5, debt(1.5), 0.3) // sisa selisih berasal dari umpan balik pengetatan fiskal
    }

    @Test
    fun engineCensus_adds_to207168() {
        val e = KapitalEngine(77L)
        val c = e.agentCensus()
        assertEquals(AgentScale.FIRMS, c["perusahaan"])
        assertEquals(AgentScale.BANKS, c["bank"])
        assertEquals(AgentScale.INVESTORS, c["investor"])
        assertEquals(AgentScale.GOVERNMENTS, c["pemerintah"])
        assertEquals(AgentScale.CENTRAL_BANKS, c["bank_sentral"])
        assertEquals(AgentScale.TARGET_TOTAL, c.values.sum())
        assertEquals(207_168, c.values.sum())
    }

    @Test
    fun engine_creditLayerIsBounded_andDoesNotBreakAccountingOverACampaign() {
        val e = KapitalEngine(2024L)
        var t = 0; var worst = 0.0
        while (e.turn < 200 && e.gameOverReason == null) {
            t++; TestBots.disciplinedStep(e, t)
            if (e.gameOverReason != null) break
            e.apply(Action.EndTurn)
            val su = e.dunia.credit.countries.getValue("sundara")
            assertTrue(su.defaultRate.isFinite() && su.meanCar in 3.9..16.0 + 1e-9)
            worst = Math.max(worst, Math.abs(e.totalAssets() - (e.totalLiabilities() + e.equity)))
        }
        assertTrue("neraca induk seimbang: $worst", worst < 1e-6)
    }
}
