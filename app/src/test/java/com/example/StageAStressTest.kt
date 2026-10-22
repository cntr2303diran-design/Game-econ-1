package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/**
 * Uji tekanan Tahap A: >= 5.000 giliran acak dengan seluruh lapisan dunia aktif (geopolitik, sektor, makro lanjut,
 * kredit agen, pasar saham buku pesanan, rilis data, siklus panjang). Identitas akuntansi diperiksa tiap giliran,
 * batas keadaan lapisan dunia diperiksa, dan waktu EndTurn diukur.
 */
class StageAStressTest {

    private val actions = StressCalibrationTest()

    private fun invariants(e: KapitalEngine, ctx: String) {
        val tol = 1e-6
        assertTrue("induk $ctx", abs(e.totalAssets() - (e.totalLiabilities() + e.equity)) < tol)
        val cb = e.lastConsolidatedBalance
        if (cb.isNotEmpty()) assertTrue("konsolidasi $ctx", abs((cb["cons_assets"] ?: 0.0) - ((cb["cons_liab"] ?: 0.0) + (cb["cons_equity"] ?: 0.0))) < tol)
        for ((cid, s) in e.subsidiaries) if (s.active && !s.nationalized)
            assertTrue("lokal $cid $ctx", abs(s.totalAssetsForeign() - (s.totalLiabForeign() + s.localEquity)) < tol)
        val d = e.lastDetail
        if (d.isNotEmpty()) {
            assertTrue("arus kas $ctx resid=${d["d_resid"]}", abs(d["d_resid"] ?: 0.0) < tol)
            assertTrue("ekuitas $ctx resid=${d["e_resid"]}", abs(d["e_resid"] ?: 0.0) < tol)
        }
        assertFalse("NaN $ctx", e.equity.isNaN() || e.cash.isNaN())
    }

    private var prevCons = Double.NaN

    private fun consolidated(e: KapitalEngine, ctx: String) {
        val tol = 1e-6
        val cash = e.lastConsolidatedBalance["cash"] ?: return
        val net = e.lastConsolidatedCashFlow["net"]
        if (net != null && !prevCons.isNaN()) assertTrue("arus kas konsolidasi $ctx: dKas=${cash - prevCons} net=$net", abs((cash - prevCons) - net) < tol)
        val eqr = e.lastConsolidatedEquity["resid"] ?: 0.0
        assertTrue("ekuitas konsolidasi $ctx resid=$eqr", abs(eqr) < tol)
        prevCons = cash
    }

    private fun worldBounds(e: KapitalEngine, ctx: String) {
        val m = e.dunia.macro
        assertTrue("panik $ctx", m.panic in 0.0..100.0)
        for (s in m.states) {
            assertTrue("utang ${s.country.id} $ctx ${s.debtGdp}", s.debtGdp in 10.0..260.0)
            assertTrue("yield ${s.country.id} $ctx", s.yield10.isFinite() && s.yield10 in 0.2..40.0)
            assertTrue(s.qeStock in 0.0..25.0 && s.guidanceCredibility in 0.0..1.0 && s.score.isFinite())
        }
        for ((id, c) in e.dunia.credit.countries) {
            assertTrue("CAR $id $ctx ${c.meanCar}", c.meanCar in 3.9..16.0 + 1e-9)
            assertTrue(c.defaultRate.isFinite() && c.defaultRate >= 0.0)
        }
        for (a in e.dunia.sectors.availability) assertTrue("ketersediaan $ctx", a in 0.0..1.0)
        assertTrue("tegangan rata2 $ctx", e.dunia.geo.meanTension() in 0.0..100.0)
        for (c in e.bourse.companies) assertTrue("harga ${c.spec.ticker} $ctx ${c.price}", c.price.isFinite() && c.price > 0.0)
        if (e.stockPrice != 0.0) assertTrue("harga saham pemain $ctx", e.stockPrice.isFinite() && e.stockPrice > 0.0)
        val fx = e.dunia.lastEffects
        assertTrue("efek dunia $ctx", fx.demandShock in -0.04..0.01 && fx.costMult in 0.94..1.25 && fx.kursDrift.isFinite() && abs(fx.policyBias) < 1.0)
        for (t in Tech.values()) assertTrue(e.dunia.cycles.adoption(t, e.turn) in 0.0..1.0)
        assertTrue(e.kurs.isFinite() && e.kurs > 0.0)
    }

    @Test
    fun stressAtLeast5000RandomTurns_withAllWorldLayers() {
        var turns = 0; var worstBalance = 0.0; var maxMs = 0.0; var sumMs = 0.0
        var bankRuns = 0; var bankFailures = 0; var downgrades = 0; var sanctionTurns = 0
        val starts = listOf(0, 100, 232, 376)
        for (start in starts) {
            for (seed in 1L..45L) {
                val e = (if (start == 0) KapitalEngine(seed * 7_368_787L) else TestBots.playUntil(seed * 104729L + start, start)) ?: continue
                val r = java.util.Random(seed * 31 + start)
                prevCons = e.lastConsolidatedBalance["cash"] ?: Double.NaN
                var lastRating = e.dunia.macro.state("sundara").rating
                for (t in 1..60) {
                    if (e.gameOverReason != null) break
                    if (start != 0) TestBots.disciplinedStep(e, 500 + t)
                    repeat(2) { actions.randomAction(r, e)?.let { a -> e.apply(a) } }
                    for (ev in e.openEvents.toList()) e.apply(Action.EventChoice(ev.id, ev.options[r.nextInt(ev.options.size)].id))
                    val t0 = System.nanoTime()
                    e.apply(Action.EndTurn)
                    val ms = (System.nanoTime() - t0) / 1e6
                    maxMs = maxOf(maxMs, ms); sumMs += ms
                    val ctx = "start=$start seed=$seed t=${e.turn}"
                    invariants(e, ctx); consolidated(e, ctx); worldBounds(e, ctx)
                    worstBalance = maxOf(worstBalance, abs(e.totalAssets() - e.totalLiabilities() - e.equity))
                    if (e.dunia.macro.state("sundara").rating.ordinal > lastRating.ordinal) downgrades++
                    lastRating = e.dunia.macro.state("sundara").rating
                    if (e.dunia.geo.sanctions.isNotEmpty()) sanctionTurns++
                    turns++
                }
                bankRuns += e.dunia.macro.runsSoFar
                bankFailures += e.dunia.credit.countries.values.sumOf { it.failuresTotal }
            }
        }
        println("STRESS TAHAP A: $turns giliran acak diperiksa; selisih neraca induk terbesar=$worstBalance; EndTurn rata2=%.2f ms maks=%.1f ms; downgrade Sundara=$downgrades, bank run=$bankRuns, bank gagal=$bankFailures, giliran bersanksi=$sanctionTurns".format(sumMs / turns, maxMs))
        assertTrue("giliran teruji: $turns", turns >= 5_000)
        assertTrue("EndTurn terlama $maxMs ms harus < 3000", maxMs < 3_000.0)
        assertTrue("selisih neraca $worstBalance", worstBalance < 1e-6)
    }

    @Test
    fun determinism_includesAllWorldLayers() {
        fun run(seed: Long): String {
            val e = KapitalEngine(seed); val r = java.util.Random(seed)
            for (t in 1..80) {
                if (e.gameOverReason != null) break
                actions.randomAction(r, e)?.let { a -> e.apply(a) }
                for (ev in e.openEvents.toList()) e.apply(Action.EventChoice(ev.id, ev.options[0].id))
                e.apply(Action.EndTurn)
            }
            val d = e.dunia
            val sb = StringBuilder()
            sb.append(e.turn).append('|').append(e.equity).append('|').append(e.cash).append('|').append(e.kurs).append('|').append(e.stockPrice)
            sb.append('|').append(d.geo.meanTension()).append('|').append(d.macro.panic).append('|').append(d.cycles.temperature)
            sb.append('|').append(e.bourse.index).append('|').append(d.lastEffects.costMult).append('|').append(d.lastEffects.demandShock)
            for (s in d.macro.states) sb.append('|').append(s.debtGdp).append(s.rating.label).append(s.yield10)
            for (c in d.credit.countries.values) sb.append('|').append(c.meanCar).append(c.defaultRate).append(c.failuresTotal)
            for (k in DataKind.values()) sb.append('|').append(d.data.latest("sundara", k)?.value)
            for (a in d.sectors.availability) sb.append('|').append(a)
            for (s in e.agentCensus().entries) sb.append('|').append(s.key).append(s.value)
            return sb.toString()
        }
        for (seed in listOf(3L, 77L, 2024L)) assertEquals("seed $seed", run(seed), run(seed))
        assertTrue(run(3L) != run(77L))
    }
}
