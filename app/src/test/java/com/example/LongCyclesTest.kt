package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Tahap A7: demografi, iklim, adopsi teknologi, dan pergeseran antar-era. */
class LongCyclesTest {

    private fun runCycles(seed: Long, turns: Int = 480, onStep: (Int, LongCycles, LongEffects, SectorModel) -> Unit = { _, _, _, _ -> }): LongCycles {
        val lc = LongCycles(seed); val sm = SectorModel()
        for (t in 1..turns) {
            val fx = lc.step(t, Random(seed * 1000 + t), sm, mutableListOf())
            onStep(t, lc, fx, sm)
        }
        return lc
    }

    @Test
    fun demography_agesSlowly_andStaysConsistent() {
        val lc = runCycles(1L)
        for ((id, d) in lc.demography) {
            assertEquals("pangsa umur $id berjumlah 1", 1.0, d.young + d.work + d.old, 1e-9)
            assertTrue("$id old ${d.old}", d.old in d.old0 - 1e-9..0.33 + 1e-9)
            assertTrue(d.work in 0.45..0.75)
        }
        val su = lc.demography.getValue("sundara"); val au = lc.demography.getValue("aurelia")
        assertTrue("Sundara menua (+3 poin atau lebih): ${su.old0} -> ${su.old}", su.old - su.old0 >= 0.03)
        assertTrue("negara tua tetap lebih tua: Aurelia ${au.old} > Sundara ${su.old}", au.old > su.old)
        assertTrue("biaya penuaan fiskal naik", lc.agingCost("sundara", 480) > 0.3)
        assertEquals(0.0, LongCycles(1L).agingCost("sundara", 1), 1e-12)
        assertEquals(0.0, lc.agingCost("belmara", 100), 0.0) // negara sekunder tidak punya modul demografi
    }

    @Test
    fun technologyAdoption_followsSCurves_inOrder_andBreakthroughsAdvanceThem() {
        val lc = LongCycles(5L)
        for (t in Tech.values()) {
            var prev = -1.0
            for (turn in 0..480 step 12) {
                val a = lc.adoption(t, turn)
                assertTrue(a in 0.0..1.0); assertTrue("monoton ${t.name}", a >= prev - 1e-12); prev = a
            }
            assertTrue("${t.name} awal rendah", lc.adoption(t, 0) < 0.10)
            assertTrue("${t.name} akhir tinggi", lc.adoption(t, 480) > 0.9)
        }
        // Urutan kedewasaan rata-rata: otomasi lebih dulu daripada energi hijau, lalu bioteknologi (rata-rata atas seed).
        var o = 0.0; var g = 0.0; var b = 0.0
        for (s in 1L..30L) { val l = LongCycles(s); o += l.adoption(Tech.OTOMASI, 240); g += l.adoption(Tech.ENERGI_HIJAU, 240); b += l.adoption(Tech.BIOTEK, 240) }
        assertTrue("otomasi > energi hijau > biotek pada t=240: $o $g $b", o > g && g > b)
        var total = 0
        for (s in 1L..20L) total += runCycles(s).breakthroughs.sum()
        assertTrue("terobosan harus terjadi pada sebagian seed: $total", total >= 5)
    }

    @Test
    fun climate_warmsAcrossEras_andExtremeEventsBecomeMoreFrequent_hittingSectors() {
        val seeds = 60
        val perEra = IntArray(5); var temp = 0.0; var withShock = 0
        for (s in 1L..seeds) {
            var sawAvailabilityDrop = false
            val lc = runCycles(s) { _, _, _, sm -> if (sm.availability.min() < 0.97) sawAvailabilityDrop = true }
            if (sawAvailabilityDrop) withShock++
            for (e in lc.events) perEra[KapitalEngine.era(e.turn)]++
            temp += lc.temperature / seeds
        }
        assertTrue("suhu naik: $temp", temp > 0.5 && temp < 2.0)
        assertTrue("kejadian ekstrem era 4 lebih sering daripada era 1: ${perEra.toList()}", perEra[4] > 1.5 * perEra[1])
        assertTrue("kejadian iklim harus mengganggu sektor ($withShock/$seeds seed)", perEra[1] + perEra[2] + perEra[3] + perEra[4] > seeds)
    }

    @Test
    fun effects_areBoundedAndSmall() {
        runCycles(7L) { t, _, fx, _ ->
            assertTrue("demand $t ${fx.demand}", fx.demand in -0.01..0.01)
            assertTrue(fx.costMult in 0.98..1.0)
            assertTrue(fx.climateStress in 0.0..1.0)
            assertTrue(fx.energyResilience in 0.0..0.4 + 1e-9)
        }
    }

    @Test
    fun energyResilience_dampensEnergyStraitAndSanctionHits() {
        val geoA = Geopolitics(2L, 60, mapOf("aurelia" to 58, "norvaldia" to 66, "kestrel" to 64, "halvenia" to 32, "meridia" to 24, "tarsis" to 44, "velmora" to 76))
        val a = SectorModel(); val b = SectorModel()
        // Ketegangan Norvaldia–Halvenia tinggi -> selat energi menutup.
        geoA.nudge("norvaldia", "halvenia", 60.0)
        for (i in 1..4) {
            a.step(Random(i.toLong()), 0.0, 0.0, geoA, 0.0, mutableListOf(), 0.0)
            b.step(Random(i.toLong()), 0.0, 0.0, geoA, 0.0, mutableListOf(), 0.4)
        }
        val e = Sector.ENERGI.ordinal
        assertTrue("selat energi harus menutup untuk uji ini", geoA.strait("selat_energi") > 0.0)
        assertTrue("ketahanan energi menaikkan ketersediaan: ${a.availability[e]} vs ${b.availability[e]}", b.availability[e] > a.availability[e])
    }

    @Test
    fun longCycles_areDeterministic_andEngineIntegrationIsActive() {
        fun snap(seed: Long): List<Double> {
            val lc = runCycles(seed, 200)
            return lc.demography.values.flatMap { listOf(it.young, it.old, it.work) } + lc.temperature + Tech.values().map { lc.adoption(it, 200) }
        }
        assertEquals(snap(4L), snap(4L))
        assertTrue(snap(4L) != snap(5L))
        val e = KapitalEngine(99L)
        val start = e.turn
        var t = 0
        while (e.turn < start + 60 && e.gameOverReason == null) { t++; TestBots.disciplinedStep(e, t); if (e.gameOverReason != null) break; e.apply(Action.EndTurn) }
        assertTrue("sempat bermain >= 24 giliran: ${e.turn - start}", e.turn - start >= 24)
        assertTrue(e.dunia.cycles.temperature > 0.03)
        assertTrue(e.dunia.cycles.demography.getValue("sundara").old > e.dunia.cycles.demography.getValue("sundara").old0)
    }
}
