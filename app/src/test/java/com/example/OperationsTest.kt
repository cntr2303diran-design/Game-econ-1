package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/** Tahap B3: karyawan kunci, pembajakan staf, R&D berkelanjutan, logistik, armada. */
class OperationsTest {

    @Test
    fun actions_roundTrip_andRulesAreEnforced() {
        for (a in listOf(Action.HireStaff("insinyur", 2), Action.FireStaff("penjual"), Action.RaiseSalary("teknisi"), Action.SetRdLevel(2), Action.SetLogistics(1)))
            assertEquals(a, Action.decode(a.encode()))
        val e = KapitalEngine(2L)
        val ap0 = e.ap; val cash0 = e.cash
        assertFalse("jabatan terisi", e.apply(Action.HireStaff("insinyur", 1)))
        assertFalse(e.apply(Action.HireStaff("zzz", 1)))
        assertFalse(e.apply(Action.HireStaff("insinyur", 9)))
        assertFalse(e.apply(Action.SetRdLevel(9))); assertFalse(e.apply(Action.SetRdLevel(0)))
        assertFalse(e.apply(Action.SetLogistics(3)))
        assertEquals(ap0, e.ap); assertEquals(cash0, e.cash, 0.0)
        assertTrue(e.apply(Action.FireStaff("insinyur")))
        assertFalse("sudah kosong", e.apply(Action.FireStaff("insinyur")))
        assertFalse("tak bisa naikkan gaji jabatan kosong", e.apply(Action.RaiseSalary("insinyur")))
        assertTrue(e.apply(Action.HireStaff("insinyur", 3)))
        // kenaikan gaji sampai batas 150%
        var raises = 0
        e.apply(Action.EndTurn)
        while (e.apply(Action.RaiseSalary("penjual"))) { raises++; if (e.ap == 0) { e.apply(Action.EndTurn) } ; if (raises > 20) break }
        assertEquals(5, raises)
        assertEquals(1.5, e.staffPool.of(StaffRole.PENJUAL).salaryMult, 1e-9)
    }

    @Test
    fun neutralStaff_changeNothing_andVacancyIsWorseThanItSaves() {
        val e = KapitalEngine(4L)
        assertEquals(1.0, e.staffPool.cogsMult(), 1e-12); assertEquals(1.0, e.staffPool.revenueMult(), 1e-12)
        assertEquals(0.0, e.operationsMonthlyCost(), 1e-9)
        val base = e.estimateMonth().getValue("ebit")
        for (role in listOf("insinyur", "penjual", "supervisor")) {
            val x = KapitalEngine(4L)
            assertTrue(x.apply(Action.FireStaff(role)))
            val ebit = x.estimateMonth().getValue("ebit")
            assertTrue("$role kosong menurunkan EBIT bulanan meski menghemat gaji: $ebit vs $base", ebit < base)
        }
    }

    @Test
    fun hiring_higherTierMeansBetterButNoisyCandidates_andSkillRevealsLater() {
        val sums = DoubleArray(4); val n = 200
        var noise = 0.0
        for (t in 1..3) for (seed in 1L..n) {
            val pool = StaffPool(); val r = kotlin.random.Random(seed * 31 + t)
            pool.fire(StaffRole.INSINYUR)
            val s = pool.hire(StaffRole.INSINYUR, t, r)
            sums[t] += s.skill
            if (t == 2) noise += abs(s.interviewEstimate - s.skill)
            assertFalse(s.revealed); repeat(3) { pool.step(kotlin.random.Random(1), 0.0, listOf("x")) }
            assertTrue(s.revealed)
        }
        assertTrue("skill rata-rata naik menurut tingkat: ${sums.toList()}", sums[1] < sums[2] && sums[2] < sums[3])
        assertEquals("rata-rata skill tingkat 2", StaffEconomy.candidateMean(2), sums[2] / n, 0.03)
        assertTrue("kesan wawancara berderau (galat rata-rata ${noise / n})", noise / n in 0.04..0.15)
    }

    @Test
    fun poaching_isRealButRare_andPayingMoreProtects() {
        fun events(mult: Double): Double {
            var total = 0
            for (seed in 1L..80L) {
                val p = StaffPool(); val r = kotlin.random.Random(seed)
                p.staff.forEach { it.salaryMult = mult }
                repeat(120) {
                    p.step(r, 2.05, listOf("A", "B"))
                    for (s in p.staff) if (!s.present) { s.present = true; s.skill = 0.5; s.loyalty = 0.65; s.salaryMult = mult } // diganti seketika
                }
                total += p.poachedTotal
            }
            return total / 80.0
        }
        val low = events(0.7); val mid = events(1.0); val high = events(1.4)
        assertTrue("pembajakan terjadi: $mid per 10 tahun", mid in 0.5..8.0)
        assertTrue("gaji tinggi melindungi, gaji rendah merugikan: $low > $mid > $high", low > mid && mid > high)
        // deterministik
        fun snap(): List<Double> { val p = StaffPool(); val r = kotlin.random.Random(5); repeat(60) { p.step(r, 2.05, listOf("A")) }; return p.staff.map { it.loyalty } + p.poachedTotal.toDouble() }
        assertEquals(snap(), snap())
    }

    @Test
    fun rdProgram_buildsBoundedQualitySlowly_andCostsMoney() {
        val off = RdProgram(); val on = RdProgram()
        on.setLevel(3); val r = kotlin.random.Random(1); var innovations = 0
        repeat(120) { off.step(r, 1.0); if (on.step(r, 1.0)) innovations++ }
        assertEquals(0.0, off.quality, 0.0)
        assertTrue("inovasi terjadi: $innovations", innovations >= 10)
        assertTrue("kualitas terbatas: ${on.quality}", on.quality in 0.005..RdProgram.QUALITY_CAP + 1e-9)
        // setelah anggaran dihentikan, kualitas menyusut perlahan
        val q = on.quality; on.setLevel(0); repeat(24) { on.step(r, 1.0) }
        assertTrue(on.quality < q && on.quality > 0.5 * q)
        val e = KapitalEngine(6L)
        val c0 = e.operationsMonthlyCost()
        assertTrue(e.apply(Action.SetRdLevel(2)))
        assertEquals(c0 + 2 * RdProgram.COST_PER_LEVEL * e.sc.size * e.costIndex, e.operationsMonthlyCost(), 1e-6)
    }

    @Test
    fun logisticsAndFleet_costMoneyAndChangeCostsAsDocumented() {
        val a = KapitalEngine(9L); val b = KapitalEngine(9L)
        assertTrue(b.apply(Action.SetLogistics(2)))
        assertEquals(2 * 25.0 * b.sc.size * b.costIndex, b.operationsMonthlyCost(), 1e-6)
        assertEquals(a.cogsRatio(), b.cogsRatio(), 1e-12) // tanpa guncangan biaya, logistik tidak mengubah HPP
        // armada: proyek belanja modal, selesai dalam 2 bulan, menurunkan HPP 0,4 poin dan menambah biaya perawatan
        val f = KapitalEngine(9L)
        val before = f.cogsRatio()
        assertTrue(f.apply(Action.Invest("fleet")))
        repeat(3) { f.apply(Action.EndTurn); assertEquals(0.0, f.totalAssets() - f.totalLiabilities() - f.equity, 1e-6) }
        assertTrue("armada selesai", f.fleetOwned)
        assertTrue("perawatan armada tercatat", f.operationsMonthlyCost() > 0.0)
        assertFalse("batas satu armada", f.apply(Action.Invest("fleet")))
    }

    @Test
    fun operations_inEngine_areDeterministic_andKeepBooksBalanced() {
        fun run(): List<Double> {
            val e = KapitalEngine(15L); val out = ArrayList<Double>()
            var t = 0
            while (t < 80 && e.gameOverReason == null) {
                t++; TestBots.disciplinedStep(e, t)
                if (t == 4) { e.apply(Action.SetRdLevel(2)); e.apply(Action.SetLogistics(1)) }
                if (t % 9 == 0) e.apply(Action.RaiseSalary("penjual"))
                if (e.gameOverReason != null) break
                e.apply(Action.EndTurn)
                assertEquals(0.0, e.totalAssets() - e.totalLiabilities() - e.equity, 1e-6)
                out += e.cash
            }
            return out + e.rd.quality + e.staffPool.poachedTotal.toDouble()
        }
        assertEquals(run(), run())
    }

    @Test
    fun repositoryView_listsStaffAndActionsWork() {
        val repo = com.example.data.GameRepository(null, 3L)
        val v0 = repo.gameState.value.operations
        assertEquals(5, v0.staff.size)
        assertTrue(v0.staff.all { it.present && it.canFire && !it.canHire })
        assertTrue(repo.fireStaff("teknisi"))
        assertTrue(repo.gameState.value.operations.staff.first { it.code == "teknisi" }.canHire)
        assertTrue(repo.setRdLevel(1)); assertEquals(1, repo.gameState.value.operations.rdLevel)
    }
}
