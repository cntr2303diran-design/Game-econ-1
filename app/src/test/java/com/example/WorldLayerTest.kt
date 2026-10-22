package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** Tahap A2–A4: negara sekunder, sektor Leontief, rantai pasok, geopolitik dinamis. */
class WorldLayerTest {

    private fun geo(seed: Long = 1L, sundaraElection: Int = 60) =
        Geopolitics(seed, sundaraElection, mapOf("aurelia" to 58, "norvaldia" to 66, "kestrel" to 64, "halvenia" to 32, "meridia" to 24, "tarsis" to 44, "velmora" to 76))

    private val calmMacro: (String) -> Pair<Double, Double> = { 3.0 to 5.0 }

    // ---------------- A2: registri negara ----------------
    @Test
    fun countryRegistry_has8Core16SecondaryInFourBlocks() {
        assertEquals(8, Countries.core.size)
        assertEquals(16, Countries.secondary.size)
        assertEquals(24, Countries.all.size)
        assertEquals(24, Countries.all.map { it.id }.toSet().size)
        for (b in Block.values()) assertEquals("blok ${b.label}", 4, Countries.secondary.count { it.country.block == b })
        assertTrue(Countries.core.all { it.id in Countries.byId })
        for (b in Block.values()) assertTrue("jangkar blok ${b.label} harus negara inti", Countries.byId.getValue(b.anchor).core)
    }

    @Test
    fun secondaryWorld_isBounded_deterministic_andLinkedOnesAreFullySimulated() {
        fun run(): List<Double> {
            val w = SecondaryWorld(); val out = ArrayList<Double>()
            for (turn in 1..480) {
                val notes = mutableListOf<DuniaNote>()
                val linked = if (turn < 240) setOf(Block.TIMUR) else setOf(Block.TIMUR, Block.TROPIKA)
                w.step(turn, Random(turn * 31L), linked, Block.values().associateWith { 2.5 }, 1.0 + 0.2 * Math.sin(turn / 20.0), notes)
                for (s in w.states) {
                    assertTrue(s.growth in -6.0..12.0); assertTrue(s.inflation in -1.0..30.0)
                    assertTrue(s.policyRate in 0.0..25.0); assertTrue(s.gdpIndex in 0.6..1.8); assertTrue(s.risk in 5..100)
                    assertEquals(s.profile.country.block in linked, s.fullySimulated)
                }
                if (turn % 40 == 0) out += w.states.map { it.gdpIndex }
            }
            return out
        }
        assertEquals(run(), run())
    }

    // ---------------- A3: Leontief + rantai pasok ----------------
    @Test
    fun leontief_solvesExactly_andInverseIsNonNegative() {
        val m = SectorModel()
        assertEquals(12, Sector.values().size)
        assertTrue("kolom A harus berjumlah < 1: ${m.maxColumnSum()}", m.maxColumnSum() < 1.0)
        val d = m.baselineFinalDemand(); val x = m.baselineOutput()
        for (j in Sector.values()) {
            var inputs = 0.0
            for (i in Sector.values()) inputs += m.coefficient(j, i) * x[i.ordinal]
            assertEquals("(I-A)x = d untuk ${j.label}", d[j.ordinal], x[j.ordinal] - inputs, 1e-9)
            assertTrue("output sektor ${j.label} harus >= permintaan akhir", x[j.ordinal] >= d[j.ordinal] - 1e-12)
        }
        // Linearitas: 2d -> 2x
        val x2 = m.leontief(DoubleArray(12) { 2.0 * d[it] })
        for (i in 0 until 12) assertEquals(2.0 * x[i], x2[i], 1e-9)
    }

    @Test
    fun noShock_meansNoShortage() {
        val m = SectorModel(); val g = geo()
        val res = m.step(Random(1), 0.0, 0.0, g, 0.0, mutableListOf())
        for (i in 0 until 12) assertTrue("ketersediaan ${Sector.values()[i].label} = ${m.availability[i]}", m.availability[i] > 0.999)
        assertEquals(1.0, res.playerCostMult, 0.02)
    }

    @Test
    fun upstreamShock_propagatesDownstream_toAvailabilityAndPrices() {
        val m = SectorModel(); val g = geo()
        m.applyShock(Sector.MANUFAKTUR, 0.55, 10)
        m.step(Random(1), 0.0, 0.0, g, 0.0, mutableListOf())
        val manuf = m.availability[Sector.MANUFAKTUR.ordinal]
        assertTrue("manufaktur harus langka: $manuf", manuf < 0.9)
        // Konstruksi membeli 25% inputnya dari manufaktur: ikut terimbas meski kapasitasnya sendiri utuh.
        val konst = m.availability[Sector.KONSTRUKSI.ordinal]
        assertTrue("konstruksi ikut terimbas: $konst", konst < 0.995)
        assertTrue("dampak hilir lebih kecil daripada di hulu", konst > manuf)
        assertTrue("harga manufaktur naik", m.priceIndex[Sector.MANUFAKTUR.ordinal] > 1.05)
        assertTrue("harga konstruksi naik lewat pass-through biaya", m.priceIndex[Sector.KONSTRUKSI.ordinal] > 1.0)
        // Sektor yang tidak membeli manufaktur sama sekali (media -> hanya teknologi/energi/perbankan) lebih ringan terkena.
        assertTrue(m.availability[Sector.MEDIA.ordinal] >= konst - 1e-9 || m.availability[Sector.MEDIA.ordinal] > 0.9)
    }

    @Test
    fun chain_hasThreeTiers_withHiddenTier3_andAuditRevealsIt() {
        val m = SectorModel()
        assertEquals(setOf(1, 2, 3), m.chain.map { it.tier }.toSet())
        assertTrue(m.chain.filter { it.tier == 3 }.all { it.hidden })
        assertTrue(m.visibleChain().none { it.tier == 3 })
        m.chainAudited = true
        assertTrue(m.visibleChain().any { it.tier == 3 })
        // Setiap simpul non-tier-1 menyuapi simpul yang ada.
        val ids = m.chain.map { it.id }.toSet()
        assertTrue(m.chain.filter { it.tier > 1 }.all { it.feeds in ids })
        assertTrue(m.chainExposure("kestrel") > 0.0)
    }

    // ---------------- A4: geopolitik ----------------
    @Test
    fun tensionMatrix_has276Pairs_boundedAndMeanReverting() {
        val g = geo(7L)
        var meanSum = 0.0; var steps = 0
        for (turn in 1..480) {
            g.step(turn, Random(turn * 7L), 0.0, calmMacro, mutableListOf())
            for (a in Countries.all) for (b in Countries.all) if (a.id < b.id) {
                val t = g.tension(a.id, b.id)
                assertTrue("ketegangan di luar 0..100: $t", t in 0.0..100.0)
                assertEquals("simetris", t, g.tension(b.id, a.id), 0.0)
            }
            meanSum += g.meanTension(); steps++
        }
        val pairs = Countries.all.size * (Countries.all.size - 1) / 2
        assertEquals(276, pairs)
        val mean = meanSum / steps
        assertTrue("rata-rata ketegangan jangka panjang $mean tidak wajar", mean in 15.0..55.0)
        // Sundara–Velmora dasar 76 (rawan) harus tetap tinggi; Sundara–Meridia dasar 24 tetap rendah.
        assertTrue(g.tension("sundara", "velmora") > g.tension("sundara", "meridia"))
    }

    @Test
    fun ladder_matchesDesignThresholds() {
        assertEquals(Ladder.TENANG, Ladder.of(10.0))
        assertEquals(Ladder.PERANG_KATA, Ladder.of(30.0))
        assertEquals(Ladder.PERANG_TARIF, Ladder.of(45.0))
        assertEquals(Ladder.SANKSI, Ladder.of(60.0))
        assertEquals(Ladder.BLOKADE, Ladder.of(70.0))
        assertEquals(Ladder.KONFLIK, Ladder.of(80.0))
        assertEquals(Ladder.PERANG, Ladder.of(90.0))
        assertEquals(7, Ladder.values().size) // tenang + 6 anak tangga
    }

    @Test
    fun geopolitics_isDeterministic_andStageHasHysteresis() {
        fun run(seed: Long): Pair<List<Int>, Int> {
            val g = geo(seed); val stages = ArrayList<Int>(); var flips = 0
            var prev = g.stage("aurelia", "kestrel").rung
            for (turn in 1..480) {
                g.step(turn, Random(turn * 13L + seed), 0.0, calmMacro, mutableListOf())
                val s = g.stage("aurelia", "kestrel").rung
                if (s != prev) flips++
                prev = s
                if (turn % 20 == 0) stages += s
            }
            return stages to flips
        }
        val (a, fa) = run(3L); val (b, _) = run(3L)
        assertEquals(a, b)
        assertTrue("terlalu sering berpindah anak tangga ($fa) -> histeresis gagal", fa < 140)
    }

    @Test
    fun crisisPressureRaisesTension_andCanTriggerSanctionsAndBlockades() {
        var sanctionsSeen = 0; var blockadeSeen = 0; var calmSanctions = 0
        for (seed in 1L..25L) {
            val hot = geo(seed); val calm = geo(seed)
            for (turn in 1..160) {
                hot.step(turn, Random(turn * 5L + seed), if (turn in 40..100) 0.3 else 0.0, calmMacro, mutableListOf())
                calm.step(turn, Random(turn * 5L + seed), 0.0, calmMacro, mutableListOf())
                assertTrue(hot.shippingCostMultiplier() >= 1.0)
                // Invarian: sanksi aktif hanya bila pasangan itu setidaknya di anak tangga sanksi (dengan histeresis).
                for (s in hot.sanctions) assertTrue("sanksi tanpa dasar tangga", hot.stage(s.from, s.target).rung >= 3 || hot.tension(s.from, s.target) >= 50.0)
            }
            assertTrue("krisis harus memanaskan dunia (seed $seed)", hot.meanTension() > calm.meanTension())
            if (hot.sanctions.isNotEmpty()) sanctionsSeen++
            if (hot.anyBlockade()) blockadeSeen++
            if (calm.sanctions.isNotEmpty()) calmSanctions++
        }
        println("GEO: tekanan krisis -> sanksi aktif di $sanctionsSeen/25 seed, blokade di $blockadeSeen/25; tanpa krisis sanksi di $calmSanctions/25")
        assertTrue("krisis berkepanjangan harus memicu sanksi di sebagian seed", sanctionsSeen >= 5)
        assertTrue("krisis harus menambah sanksi dibanding dunia tenang ($sanctionsSeen vs $calmSanctions)", sanctionsSeen > calmSanctions)
        assertTrue("dunia tenang tidak boleh tersanksi di hampir semua seed ($calmSanctions/25)", calmSanctions <= 15)
    }

    @Test
    fun straitClosure_followsTension() {
        val g = geo(2L)
        g.nudge("norvaldia", "halvenia", 100.0)
        assertTrue(g.strait("selat_energi") > 0.9)
        assertEquals(0.0, g.strait("selat_pangan"), 1.0)
        g.nudge("tarsis", "sundara", 100.0)
        assertTrue(g.strait("selat_pangan") > 0.9)
        assertTrue(g.shippingCostMultiplier() > 2.5)
        g.nudge("norvaldia", "halvenia", -100.0)
        assertEquals(0.0, g.strait("selat_energi"), 1e-9)
    }

    @Test
    fun elections_happenEveryFourToFiveYears_perCountry_andShiftStance() {
        val g = geo(11L, sundaraElection = 50)
        val count = HashMap<String, Int>()
        var protectionist = 0
        val log = mutableListOf<DuniaNote>()
        for (turn in 1..480) {
            val before = Countries.all.associate { it.id to g.stance(it.id) }
            g.step(turn, Random(turn * 3L), 0.0, calmMacro, log)
            for (c in Countries.all) if (g.stance(c.id) !== before.getValue(c.id)) {
                count.merge(c.id, 1, Int::plus)
                if (g.stance(c.id).type == ElectionType.PROTEKSIONIS) protectionist++
            }
        }
        for (c in Countries.all) assertTrue("${c.id}: ${count[c.id]} pemilu dalam 480 giliran", (count[c.id] ?: 0) in 7..10)
        assertTrue("harus ada hasil proteksionis", protectionist > 0)
        assertTrue(g.monthsToElection("sundara", 479) >= 0)
    }

    @Test
    fun electionOutcome_dependsOnMacro_highInflationFavoursMarketParties() {
        fun share(infl: Double, unemp: Double): Double {
            var mkt = 0; var total = 0
            for (seed in 1L..60L) {
                val g = geo(seed, 20)
                for (turn in 1..480) g.step(turn, Random(turn + seed), 0.0, { infl to unemp }, mutableListOf())
                // Hitung hasil pemilu terakhir tiap negara.
                for (c in Countries.all) { total++; if (g.stance(c.id).type == ElectionType.PRO_PASAR) mkt++ }
            }
            return mkt.toDouble() / total
        }
        val high = share(9.0, 5.0); val low = share(2.0, 5.0)
        println("PEMILU: pangsa pro-pasar inflasi tinggi=${"%.2f".format(high)} vs rendah=${"%.2f".format(low)}")
        assertTrue("inflasi tinggi harus menaikkan peluang pro-pasar", high > low + 0.03)
    }

    @Test
    fun sanctionOnKestrel_raisesPlayerCosts_andAuditMitigates() {
        fun costWith(audited: Boolean): Double {
            val m = SectorModel(); m.chainAudited = audited
            val g = geo(4L)
            // Dorong ke anak tangga sanksi (skor ~64), bukan ke perang terbuka.
            g.nudge("aurelia", "kestrel", 64.0 - g.tension("aurelia", "kestrel"))
            g.step(1, Random(1L), 0.0, calmMacro, mutableListOf())
            assertTrue("sanksi harus terbentuk", g.sanctions.any { it.target == "kestrel" })
            var res = m.step(Random(1), 0.0, 0.0, g, 0.0, mutableListOf())
            repeat(8) { res = m.step(Random(2L + it), 0.0, 0.0, g, 0.0, mutableListOf()) }
            return res.playerCostMult
        }
        val blind = costWith(false); val audited = costWith(true)
        println("SANKSI-KESTREL: pengali biaya tanpa uji tuntas=${"%.3f".format(blind)} dengan uji tuntas=${"%.3f".format(audited)}")
        assertTrue("sanksi harus menaikkan biaya: $blind", blind > 1.01)
        assertTrue("sanksi sedang tidak boleh menyentuh batas atas: $blind", blind < 1.2)
        assertTrue("uji tuntas harus meredam dampak", audited < blind)
    }
}
