package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Tes kinerja `EndTurn`. Target dokumen desain: < 3 detik per giliran di HP kelas menengah.
 * Batas di sini sengaja longgar karena mesin CI/laptop berbeda dengan HP; angka nyata dicetak
 * supaya bisa dibandingkan dari waktu ke waktu (lihat docs/AUDIT_vs_KONSEP.md).
 */
class PerformanceTest {

    class Stat(val label: String, val turns: Int, val meanMs: Double, val p99Ms: Double, val maxMs: Double)

    private fun measure(label: String, seeds: List<Long>, active: Boolean, maxTurns: Int = 480): Stat {
        val samples = ArrayList<Double>()
        for (seed in seeds) {
            val eng = KapitalEngine(seed)
            var t = 0
            while (eng.turn < maxTurns && eng.gameOverReason == null) {
                t++
                if (active) ActiveBot.step(eng, t) else TestBots.disciplinedStep(eng, t)
                if (eng.gameOverReason != null) break
                val t0 = System.nanoTime()
                eng.apply(Action.EndTurn)
                samples.add((System.nanoTime() - t0) / 1e6)
            }
        }
        samples.sort()
        val mean = samples.average()
        val p99 = samples[((samples.size - 1) * 0.99).toInt()]
        return Stat(label, samples.size, mean, p99, samples.last())
    }

    @Test
    fun endTurnStaysWithinBudget_onPassiveAndActiveBots() {
        // Pemanasan JIT: satu permainan pendek tidak dihitung.
        measure("pemanasan", listOf(1L), active = false, maxTurns = 60)

        val seeds = listOf(7919L, 15838L, 23757L)
        val results = listOf(
            measure("pasif", seeds, active = false),
            measure("aktif", seeds, active = true)
        )
        for (r in results) {
            println(
                "KINERJA ${r.label}: ${r.turns} giliran, rata2=${"%.1f".format(r.meanMs)} ms, " +
                    "p99=${"%.1f".format(r.p99Ms)} ms, maks=${"%.1f".format(r.maxMs)} ms"
            )
            assertTrue("${r.label}: terlalu sedikit giliran terukur (${r.turns})", r.turns >= 200)
            // Anggaran dokumen: <3 dtk per giliran. Rata-rata dan maksimum dijaga terpisah.
            assertTrue("${r.label}: rata-rata EndTurn ${r.meanMs} ms melebihi 1000 ms", r.meanMs < 1000.0)
            assertTrue("${r.label}: EndTurn terlama ${r.maxMs} ms melebihi 3000 ms", r.maxMs < 3000.0)
        }
    }
}
