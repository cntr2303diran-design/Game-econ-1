package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test

/** Tahap B5b–d: SPV, antimonopoli, skandal tertunda dari bukti yang menua. */
class LawTest {

    private fun check(e: KapitalEngine) {
        assertEquals(0.0, e.totalAssets() - e.totalLiabilities() - e.equity, 1e-6)
        val d = e.lastDetail
        if (d.isNotEmpty()) {
            assertEquals("arus kas = perubahan kas", 0.0, d["d_resid"] ?: 0.0, 1e-6)
            assertEquals("roll-forward ekuitas", 0.0, d["e_resid"] ?: 0.0, 1e-6)
        }
    }

    private fun era3(seed: Long): KapitalEngine? = TestBots.playUntil(seed * 104729L, 235)

    @Test
    fun action_roundTrip_andEraLock() {
        assertEquals(Action.OpenSpv(5000), Action.decode(Action.OpenSpv(5000).encode()))
        val e = KapitalEngine(3L)
        assertNotNull(e.spvBlock(5000))
        assertFalse(e.apply(Action.OpenSpv(5000)))
    }

    @Test
    fun spv_hidesDebtFromReportedMetrics_butBooksStayBalanced() {
        var ran = 0
        for (seed in 1L..12L) {
            val e = era3(seed) ?: continue
            if (e.spvBlock(4000) != null) continue
            val cash0 = e.cash
            assertTrue(e.apply(Action.OpenSpv(4000)))
            e.apply(Action.EndTurn); check(e)
            if (e.gameOverReason != null) continue
            val hidden = e.spvHiddenRs()
            assertEquals("utang SPV nyata tercatat", 4000.0, hidden, 1.0)
            assertEquals(e.totalDebt() - hidden, e.reportedDebt(), 1e-9)
            assertEquals("metrik kreditur memakai utang yang dilaporkan", e.reportedDebt(), e.creditMetrics().debt, 1e-6)
            assertTrue("kas naik karena SPV", e.cash > cash0 + 2000.0 || e.lastDetail["d_newdebt"]!! >= 3999.0)
            assertTrue(e.spvMonthlyInterest() > 0.0)
            for (t in 1..6) { if (e.gameOverReason != null) break; TestBots.disciplinedStep(e, 600 + t); e.apply(Action.EndTurn); check(e) }
            ran++
        }
        assertTrue("diuji pada beberapa seed: $ran", ran >= 4)
    }

    @Test
    fun spv_getsExposed_withPenalties_andDebtBecomesVisible() {
        var exposed = 0; var tested = 0
        for (seed in 1L..16L) {
            val e = era3(seed) ?: continue
            if (!e.apply(Action.SetAuditor(2))) continue
            if (e.spvBlock(4000) != null || !e.apply(Action.OpenSpv(4000))) { continue }
            tested++
            val scandals0 = e.scandalsTriggered; val trust0 = e.boardTrust
            var t = 0
            while (e.spvRevealed == 0 && e.gameOverReason == null && t < 36) { t++; TestBots.disciplinedStep(e, 700 + t); e.apply(Action.EndTurn); check(e) }
            if (e.spvRevealed > 0) {
                exposed++
                assertEquals("setelah terbongkar utang terlihat", e.totalDebt(), e.reportedDebt(), 1e-9)
                assertTrue(e.scandalsTriggered >= scandals0 + 1)
                assertTrue(e.chronicle.any { it.title == "Skandal SPV" })
            }
        }
        println("HUKUM SPV terbongkar $exposed dari $tested")
        assertTrue(tested >= 4)
        assertTrue("SPV akhirnya terbongkar pada sebagian besar seed: $exposed/$tested", exposed >= tested / 2)
    }

    @Test
    fun spv_isNotFreeMoney_onAverage() {
        var sum = 0.0; var n = 0
        for (seed in 1L..16L) {
            val a = era3(seed) ?: continue
            val b = era3(seed) ?: continue
            if (a.spvBlock(4000) != null || !a.apply(Action.OpenSpv(4000))) continue
            for (t in 1..30) {
                if (a.gameOverReason != null || b.gameOverReason != null) break
                TestBots.disciplinedStep(a, 800 + t); TestBots.disciplinedStep(b, 800 + t)
                a.apply(Action.EndTurn); b.apply(Action.EndTurn)
            }
            if (a.gameOverReason != null || b.gameOverReason != null) continue
            sum += a.equity - b.equity; n++
        }
        println("HUKUM SPV selisih ekuitas rata-rata=${sum / n} n=$n")
        assertTrue(n >= 4)
        assertTrue("SPV tidak menambah ekuitas rata-rata: ${sum / n}", sum / n < 0.0)
    }

    @Test
    fun antitrust_blocksBigMergers_andRemedyFeeApplies() {
        var checked = 0; var remedy = 0
        for (seed in 1L..20L) {
            val e = TestBots.playUntil(seed * 104729L, 130) ?: continue
            for (rv in e.world.rivals.filter { it.active }) {
                val post = e.postMergerShare(rv.id)
                assertTrue(post >= e.playerShare())
                val msg = e.acquireRivalBlock(rv.id)
                if (post >= KapitalEngine.ANTITRUST_BLOCK_SHARE) assertNotNull("pangsa gabungan terlalu besar harus diblokir", msg)
                if (msg == null) {
                    val before = e.chronicle.size
                    if (e.apply(Action.AcquireRival(rv.id))) {
                        checked++
                        val wasRemedy = e.chronicle.drop(before).any { it.title == "Syarat komisi persaingan" }
                        assertEquals("syarat komisi hanya bila pangsa gabungan >= 33%", post >= KapitalEngine.ANTITRUST_REMEDY_SHARE, wasRemedy)
                        if (wasRemedy) remedy++
                    }
                }
            }
        }
        println("HUKUM akuisisi diuji=$checked dengan syarat komisi=$remedy")
        assertTrue("sedikitnya satu akuisisi diuji: $checked", checked >= 1)
    }

    @Test
    fun antitrust_dominantShareDrawsFines() {
        var fines = 0; var dominant = 0; var tried = 0
        for (seed in 1L..40L) {
            val e = TestBots.playUntil(seed * 104729L, 130) ?: continue
            tried++
            // mengakuisisi pesaing satu per satu sampai diblokir atau tak mampu: pangsa naik menjadi dominan
            var guard = 0
            while (guard++ < 12 && e.gameOverReason == null) {
                val target = e.world.rivals.filter { it.active }.firstOrNull { e.acquireRivalBlock(it.id) == null }
                if (target != null) e.apply(Action.AcquireRival(target.id))
                TestBots.disciplinedStep(e, 1000 + guard); e.apply(Action.EndTurn)
            }
            if (e.gameOverReason != null || e.playerShare() < KapitalEngine.ANTITRUST_WATCH_SHARE) continue
            dominant++
            var t = 0
            while (e.gameOverReason == null && t < 80 && e.antitrustFines == 0) { t++; TestBots.disciplinedStep(e, 1100 + t); e.apply(Action.EndTurn); check(e) }
            fines += e.antitrustFines
        }
        println("HUKUM denda antimonopoli=$fines dari $dominant seed dominan ($tried dicoba)")
        assertTrue("ada seed dengan pangsa dominan: $dominant", dominant >= 2)
        assertTrue("pangsa dominan akhirnya didenda: $fines", fines >= 1)
    }

    @Test
    fun agedEvidence_leaksOverTime_andExpires() {
        var leaks = 0; var seeds = 0
        for (seed in 1L..40L) {
            val e = KapitalEngine(seed)
            // beberapa bukti ringan (percantikan 10% tiap beberapa bulan) tanpa auditor agar menumpuk dan menua
            var t = 0
            while (e.gameOverReason == null && t < 70) {
                t++
                if (t % 6 == 1 && t < 25) e.apply(Action.WindowDress(10))
                TestBots.disciplinedStep(e, t)
                e.apply(Action.EndTurn)
            }
            seeds++
            leaks += e.whistleblowerLeaks
            if (e.gameOverReason == null) assertTrue("bukti lebih dari 60 bulan sudah kedaluwarsa", e.ethicsEvidences.none { e.turn - it.turnCommitted > 61 })
        }
        println("HUKUM bocoran tertunda=$leaks dari $seeds seed")
        assertTrue("bocoran tertunda terjadi pada sebagian seed: $leaks", leaks >= 3)
    }

    @Test
    fun deterministic() {
        fun run(): Double {
            val e = era3(2L) ?: era3(3L)!!
            e.apply(Action.OpenSpv(3000)); e.apply(Action.WindowDress(25))
            repeat(12) { TestBots.disciplinedStep(e, 900 + it); e.apply(Action.EndTurn) }
            return e.equity + 1e3 * e.spvRevealed + 1e2 * e.whistleblowerLeaks
        }
        assertEquals(run(), run(), 0.0)
    }

    @Test
    fun repositoryView_showsLawInfo() {
        val repo = com.example.data.GameRepository(null, 3L)
        val v = repo.gameState.value.operations
        assertEquals(3, v.spvBlocks.size)
        assertTrue(v.spvBlocks.all { it != null })   // terkunci sebelum Era 3
        assertTrue(v.lawText.contains("Pangsa pasar"))
        assertFalse(repo.openSpv(5000))
    }
}
