package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Tahap A5: fiskal, kurva imbal hasil, peringkat, bank sentral, bank run, kurs dari neraca dagang. */
class MacroPlusTest {

    private fun geo(seed: Long = 1L) =
        Geopolitics(seed, 60, mapOf("aurelia" to 58, "norvaldia" to 66, "kestrel" to 64, "halvenia" to 32, "meridia" to 24, "tarsis" to 44, "velmora" to 76))

    private fun calm(policy: Double = 6.0, gap: Double = 0.0, infl: Double = 4.0, unemp: Double = 5.0, star: Double = policy) =
        MacroInput(infl, gap, unemp, policy, star, 2.5 + 40.0 * gap)

    private fun run(
        months: Int, seed: Long = 1L, input: (Int, String) -> MacroInput, crisis: (Int) -> Boolean = { false },
        export: Double = 1.0, oil: Double = 1.0, onStep: (Int, MacroPlus, MacroEffects) -> Unit = { _, _, _ -> }
    ): MacroPlus {
        val m = MacroPlus(seed); val g = geo(seed)
        for (t in 1..months) {
            val notes = mutableListOf<DuniaNote>()
            val fx = m.step(t, Random(seed * 17 + t), { id -> input(t, id) }, g, export, oil, crisis(t), notes)
            onStep(t, m, fx)
        }
        return m
    }

    @Test
    fun registry_has24GovernmentsAndCentralBanks_withSensibleInitialRatings() {
        val m = run(2, input = { _, _ -> calm() })
        assertEquals(24, m.states.size)
        val r = { id: String -> m.state(id).rating.ordinal }
        assertTrue("Norvaldia lebih aman dari Sundara", r("norvaldia") < r("sundara"))
        assertTrue("Aurelia lebih aman dari Velmora", r("aurelia") < r("velmora"))
        assertTrue("Sundara lebih aman dari negara tropika berisiko tinggi", r("sundara") <= r("oralang"))
        assertEquals(Rating.BBB, m.state("sundara").rating)
    }

    @Test
    fun fiscal_recessionRaisesDeficitAndDebt_boundedAndDeterministic() {
        fun debtAfter(gap: Double): Pair<Double, Double> {
            val m = run(60, input = { _, _ -> calm(gap = gap) })
            return m.state("sundara").debtGdp to m.state("sundara").primaryDeficit
        }
        val (dBoom, pBoom) = debtAfter(0.03)
        val (dBust, pBust) = debtAfter(-0.06)
        assertTrue("resesi menaikkan utang: $dBust vs $dBoom", dBust > dBoom + 3.0)
        assertTrue("resesi memperlebar defisit primer: $pBust vs $pBoom", pBust > pBoom + 1.5)
        val a = run(120, input = { t, _ -> calm(gap = 0.04 * Math.sin(t / 9.0)) })
        val b = run(120, input = { t, _ -> calm(gap = 0.04 * Math.sin(t / 9.0)) })
        for (s in a.states) {
            assertEquals(s.debtGdp, b.state(s.country.id).debtGdp, 0.0)
            assertTrue(s.debtGdp in 10.0..260.0)
        }
    }

    @Test
    fun rating_worseFundamentalsLowerScore_andChangesAreRateLimited() {
        val good = run(36, input = { _, _ -> calm(infl = 3.0, unemp = 4.5) }).state("sundara")
        val bad = run(36, input = { _, _ -> calm(infl = 14.0, unemp = 12.0, gap = -0.06) }).state("sundara")
        assertTrue("skor ${bad.score} < ${good.score}", bad.score < good.score - 15.0)
        assertTrue(bad.rating.ordinal > good.rating.ordinal)
        // Histeresis: dalam guncangan berulang, tidak ada lompatan > 1 anak tangga dan jarak antar-perubahan >= 6 bulan.
        var last = -1; var lastChange = -99
        run(240, input = { t, _ -> if ((t / 20) % 2 == 0) calm(infl = 14.0, unemp = 12.0) else calm(infl = 3.0, unemp = 4.0) }) { t, m, _ ->
            val now = m.state("sundara").rating.ordinal
            if (last < 0) last = now // peringkat awal ditetapkan dari skor pertama, bukan perubahan
            if (now != last) {
                assertEquals("lompat > 1 anak tangga di bulan $t", 1, Math.abs(now - last))
                assertTrue("perubahan terlalu rapat di bulan $t", t - lastChange >= 6)
                last = now; lastChange = t
            }
        }
    }

    private fun run(months: Int, input: (Int, String) -> MacroInput, onStep: (Int, MacroPlus, MacroEffects) -> Unit): MacroPlus =
        run(months, 1L, input, { false }, 1.0, 1.0, onStep)

    @Test
    fun yieldCurve_invertsOnlyWhenPolicyIsVeryHigh() {
        val loose = run(60, input = { _, _ -> calm(policy = 4.0, infl = 4.0) }).state("sundara")
        val tight = run(60, input = { _, _ -> calm(policy = 13.0, infl = 4.0) }).state("sundara")
        assertTrue("kurva normal saat bunga rendah: ${loose.slope}", loose.slope > 0.5)
        assertTrue("kurva terbalik saat bunga sangat tinggi: ${tight.slope}", tight.slope < 0.0)
        assertTrue(tight.inversionMonths >= 3)
    }

    @Test
    fun centralBank_qeTapersAndBoundsStock_guidanceBreachHurtsCredibility() {
        var sawQe = false; var maxStock = 0.0; var tantrum = false
        val m = run(
            90,
            input = { t, _ -> if (t < 45) calm(policy = 2.0, gap = -0.05) else calm(policy = 5.0, gap = 0.03) }
        ) { t, mm, _ ->
            val s = mm.state("sundara")
            if (s.qeOn) sawQe = true
            maxStock = Math.max(maxStock, s.qeStock)
            if (t > 45 && !s.qeOn && s.qeStock > 8.0) tantrum = true
        }
        assertTrue("QE harus menyala saat suku bunga di batas bawah dan ekonomi lesu", sawQe)
        assertTrue("stok QE dibatasi: $maxStock", maxStock <= 25.0 + 1e-9)
        assertTrue(tantrum)
        assertFalse("QE berhenti setelah pemulihan", m.state("sundara").qeOn)

        // Panduan: bunga acuan jauh di bawah Taylor -> bank sentral mengisyaratkan pengetatan; menurunkan bunga = melanggar.
        var credBefore = 1.0; var credAfter = 1.0
        run(60, input = { t, _ -> calm(policy = if (t < 30) 5.0 else 4.5, star = 6.5) }) { t, mm, _ ->
            val s = mm.state("sundara")
            if (t == 29) credBefore = s.guidanceCredibility
            if (t == 31) credAfter = s.guidanceCredibility
        }
        assertTrue("pelanggaran panduan menurunkan kredibilitas: $credBefore -> $credAfter", credAfter < credBefore)
    }

    @Test
    fun bankRun_needsGenuineStress_isRare_hasCooldown_andHurtsDemandAndCredit() {
        // Tenang: tidak pernah terjadi pada banyak seed.
        for (seed in 1L..20L) {
            val m = run(240, seed, { _, _ -> calm() })
            assertEquals("tanpa tekanan tidak ada bank run (seed $seed)", 0, m.runsSoFar)
        }
        // Tekanan ekstrem berkepanjangan: terjadi pada hampir semua seed, dengan jeda minimal 60 bulan.
        var seedsWithRun = 0
        for (seed in 1L..20L) {
            var lastRun = -999; var firstDemand = 0.0; var firstCredit = 0.0
            val runTurns = mutableListOf<Int>()
            var prevRuns = 0
            run(240, seed, { _, _ -> calm(infl = 16.0, unemp = 14.0, gap = -0.08) }, { true }) { t, m, fx ->
                if (m.runsSoFar > prevRuns) { runTurns += t; prevRuns = m.runsSoFar; firstDemand = fx.demandAdd; firstCredit = fx.creditAdd }
            }
            if (runTurns.isNotEmpty()) {
                seedsWithRun++
                assertTrue("bank run mengurangi permintaan: $firstDemand", firstDemand < -0.005)
                assertTrue("bank run menambah premi kredit: $firstCredit", firstCredit > 0.0)
                for (i in 1 until runTurns.size) assertTrue("jeda antar-run >= 60", runTurns[i] - runTurns[i - 1] >= 60)
            }
        }
        assertTrue("run harus mungkin terjadi di bawah tekanan ekstrem ($seedsWithRun/20)", seedsWithRun >= 15)
    }

    @Test
    fun fx_followsTradeBalance_andRatingDowngradeWeakensCurrency() {
        fun meanDrift(export: Double, oil: Double): Double {
            var s = 0.0
            run(80, 1L, { _, _ -> calm() }, { false }, export, oil) { _, _, fx -> s += fx.kursDrift }
            return s / 80
        }
        val surplus = meanDrift(1.12, 0.8)
        val deficit = meanDrift(0.88, 1.5)
        assertTrue("surplus dagang -> rupiah menguat (drift negatif): $surplus", surplus < -0.0003)
        assertTrue("defisit dagang -> rupiah melemah (drift positif): $deficit", deficit > 0.0003)
        // Penurunan peringkat memicu denyut pelemahan kurs.
        var pulse = 0.0
        run(60, input = { t, _ -> if (t < 20) calm(infl = 3.0, unemp = 4.5) else calm(infl = 16.0, unemp = 13.0, gap = -0.08) }) { _, _, fx ->
            pulse = Math.max(pulse, fx.kursDrift)
        }
        assertTrue("denyut pelemahan setelah downgrade/panik: $pulse", pulse > 0.003)
    }

    @Test
    fun outputsStayBoundedOverFullCampaign_underRandomInputs() {
        val rr = Random(5)
        var maxAbsDrift = 0.0; var maxBias = 0.0
        val m = run(480, 3L, { _, _ ->
            calm(policy = 2.0 + rr.nextDouble() * 12.0, gap = (rr.nextDouble() - 0.5) * 0.14, infl = rr.nextDouble() * 20.0 - 1.0,
                unemp = 3.0 + rr.nextDouble() * 12.0, star = 2.0 + rr.nextDouble() * 12.0)
        }, { rr.nextDouble() < 0.1 }) { _, mm, fx ->
            maxAbsDrift = Math.max(maxAbsDrift, Math.abs(fx.kursDrift)); maxBias = Math.max(maxBias, Math.abs(fx.policyBias))
            assertTrue(mm.panic in 0.0..100.0)
            assertTrue(fx.demandAdd >= -0.011 && fx.demandAdd <= 0.0)
            for (s in mm.states) {
                assertTrue(s.debtGdp in 10.0..260.0); assertTrue(s.yield10 in 0.2..40.0)
                assertTrue(s.qeStock in 0.0..25.0); assertTrue(s.guidanceCredibility in 0.0..1.0)
                assertFalse(s.score.isNaN()); assertFalse(s.yield10.isNaN())
            }
        }
        assertTrue("kurs drift terbatas: $maxAbsDrift", maxAbsDrift < 0.03)
        assertTrue("bias Taylor terbatas: $maxBias", maxBias < 1.0)
        assertEquals(24, m.states.size)
    }

    @Test
    fun engine_macroLayerIsDeterministic_andSundaraPolicyBiasReachesTheTaylorRule() {
        fun snap(seed: Long): String {
            val e = KapitalEngine(seed)
            repeat(90) { e.apply(Action.EndTurn) }
            val m = e.dunia.macro
            return m.states.joinToString("|") { "%.9f/%.9f/%s/%.4f".format(it.debtGdp, it.yield10, it.rating.label, it.cbBias) } +
                "|%.9f".format(m.panic) + "|%.9f".format(e.kurs) + "|%.4f".format(e.world.policyRate)
        }
        assertEquals(snap(11L), snap(11L))
        assertTrue(snap(11L) != snap(12L))
        // Bias Taylor +1,5 pp pada bank sentral yang sama menggeser suku bunga naik (kanal policyBias aktif).
        val w0 = World(50_000); val w1 = World(50_000)
        var d = 0.0
        for (i in 1..24) {
            val a = w0.step(Random(i.toLong()), 1.0, 1.0, 1.0, 0.0, 1.0)
            val b = w1.step(Random(i.toLong()), 1.0, 1.0, 1.0, 0.0, 1.0, policyBias = 1.5)
            d = b.policyRate - a.policyRate
        }
        assertTrue("bias +1,5 harus menaikkan bunga: $d", d >= 0.75)
    }
}
