package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/** Tahap B5a: percantikan laporan, auditor eksternal, skor-M. */
class ForensicsTest {

    private fun check(e: KapitalEngine) {
        assertEquals(0.0, e.totalAssets() - e.totalLiabilities() - e.equity, 1e-6)
        val d = e.lastDetail
        if (d.isNotEmpty()) {
            assertEquals("arus kas = perubahan kas", 0.0, d["d_resid"] ?: 0.0, 1e-6)
            assertEquals("roll-forward ekuitas", 0.0, d["e_resid"] ?: 0.0, 1e-6)
        }
    }

    @Test
    fun actions_roundTrip_andBlocks() {
        for (a in listOf(Action.WindowDress(25), Action.SetAuditor(2))) assertEquals(a, Action.decode(a.encode()))
        val e = KapitalEngine(5L)
        assertFalse(e.apply(Action.WindowDress(30))); assertNotNull(e.windowDressBlock(30))
        assertFalse(e.apply(Action.SetAuditor(0))); assertFalse(e.apply(Action.SetAuditor(3)))
        assertEquals(0.0, e.auditFee(), 0.0)
        assertTrue(e.apply(Action.SetAuditor(1)))
        assertTrue(e.auditFee() > 0.0 && e.financeMonthlyCost() >= e.auditFee())
    }

    @Test
    fun windowDress_isTimingOnly_cashNeutralNowAndProfitNeutralLater() {
        // Kartu generator dipicu keadaan dunia (kas, laba), jadi percantikan menggeser kartu yang muncul dan
        // mengaburkan selisih yang diuji. Undian kartu dimatikan agar hanya efek percantikan yang terukur.
        // Uji covenant juga dimatikan: percantikan memang dapat menggeser lulus/gagalnya uji covenant (manfaat nyata
        // yang dikejar dan dihukum auditor), dan itu bukan 'uang gratis' yang sedang diuji di sini.
        val oldDraw = CardTuning.drawChance; val oldCov = BalanceTuning.covenantScale
        CardTuning.drawChance = 0.0; BalanceTuning.covenantScale = 0.0
        try { windowDressBody() } finally { CardTuning.drawChance = oldDraw; BalanceTuning.covenantScale = oldCov }
    }

    private fun windowDressBody() {
        var ran = 0
        for (seed in listOf(3L, 8L, 17L, 21L, 30L, 41L)) {
            val ctl = KapitalEngine(seed); val e = KapitalEngine(seed)
            val opex = e.estimateMonth().getValue("opex")
            if (!e.apply(Action.WindowDress(50))) continue
            val x = opex * 0.5
            ctl.apply(Action.EndTurn); e.apply(Action.EndTurn)
            check(e)
            val niGain = (e.lastIncome["ni"] ?: 0.0) - (ctl.lastIncome["ni"] ?: 0.0)
            assertTrue("laba naik mendekati x(1-pajak): $niGain vs $x", niGain > 0.5 * x && niGain < 1.01 * x)
            val dCash = e.cash - ctl.cash
            assertTrue("kas hanya turun oleh pajak lebih awal: $dCash vs $x", dCash < 1.0 && dCash > -0.30 * x)
            var sumNi = niGain
            // Sepuluh giliran tanpa aksi lain: perbedaan hanya dari percantikan (tindakan bot lain menggeser kejadian acak berikutnya).
            for (t in 2..10) {
                ctl.apply(Action.EndTurn); e.apply(Action.EndTurn); check(e)
                sumNi += (e.lastIncome["ni"] ?: 0.0) - (ctl.lastIncome["ni"] ?: 0.0)
            }
            assertTrue("jumlah laba setelah berbalik tidak lebih tinggi (tanpa uang gratis): $sumNi vs $x", sumNi < 0.05 * x && sumNi > -0.7 * x)
            assertEquals("sisa terkapitalisasi hampir habis", 0.0, e.dressedRs, 0.03 * x)
            ran++
        }
        assertTrue("diuji pada beberapa seed: $ran", ran >= 4)
    }

    @Test
    fun auditor_findsDressing_chargesFine_andClearsEvidence() {
        var restated = 0; var withoutAuditor = 0
        for (seed in 1L..40L) {
            val a = KapitalEngine(seed); val b = KapitalEngine(seed)
            a.apply(Action.SetAuditor(2))
            for (t in 1..20) {
                if (a.gameOverReason != null || b.gameOverReason != null) break
                a.apply(Action.WindowDress(10)); b.apply(Action.WindowDress(10))
                a.apply(Action.EndTurn); b.apply(Action.EndTurn)
                check(a)
            }
            restated += a.restatements; withoutAuditor += b.restatements
        }
        println("FORENSIK koreksi dengan auditor=$restated tanpa=$withoutAuditor")
        assertTrue("auditor menemukan percantikan di banyak seed: $restated", restated >= 20)
        assertEquals(0, withoutAuditor)
    }

    @Test
    fun restatement_removesDressingEvidence() {
        for (seed in 1L..60L) {
            val e = KapitalEngine(seed)
            e.apply(Action.SetAuditor(2))
            var t = 0
            while (e.restatements == 0 && e.gameOverReason == null && t < 30) {
                t++; e.apply(Action.WindowDress(10)); e.apply(Action.EndTurn)
            }
            if (e.restatements > 0) {
                assertTrue("bukti percantikan sudah dibersihkan", e.ethicsEvidences.none { it.kind == "window_dressing" })
                assertEquals(0.0, e.dressedSinceCleanRs, 0.0)
                return
            }
        }
        assertTrue("tidak ada seed yang menghasilkan koreksi", false)
    }

    @Test
    fun mScore_honestFalsePositiveRateLow() {
        var flag = 0; var n = 0
        for (seed in 1L..40L) {
            val h = KapitalEngine(seed)
            for (t in 1..60) {
                if (h.gameOverReason != null) break
                TestBots.disciplinedStep(h, t); h.apply(Action.EndTurn)
                val m = h.mScore() ?: continue
                n++; if (m > ForensicBook.THRESHOLD) flag++
            }
        }
        println("FORENSIK skor-M jujur: tanda=${flag.toDouble() / n} dari $n pengamatan")
        assertTrue("sampel cukup: $n", n >= 500)
        assertTrue("tanda palsu pada permainan jujur < 12%: ${flag.toDouble() / n}", flag.toDouble() / n < 0.12)
    }

    @Test
    fun mScore_risesWhenProfitOutrunsCash() {
        fun book(inflate: Double): ForensicBook {
            val b = ForensicBook()
            for (m in 0 until 30) {
                val ni = 100.0 + (if (m >= 18) inflate else 0.0)
                b.record(1000.0, 650.0, 200.0, 20.0, ni, 100.0 + 20.0, 150.0, 5000.0, 3000.0, 2000.0, 0.0)
            }
            return b
        }
        val honest = book(0.0).mScore()!!; val padded = book(60.0).mScore()!!
        assertTrue("laba melebihi kas menaikkan skor-M: $padded vs $honest", padded > honest + 0.3)
        assertNotNull(book(0.0).components())
        assertTrue(ForensicBook().mScore() == null)
    }


    @Test
    fun deterministic_andSaveRoundTrip() {
        fun run(): Pair<Double, String> {
            val e = KapitalEngine(11L); val rec = mutableListOf<String>()
            for (t in 1..30) {
                TestBots.disciplinedStep(e, t, rec)
                if (t == 2) e.apply(Action.SetAuditor(1)).also { rec += Action.SetAuditor(1).encode() }
                if (t % 3 == 0 && e.apply(Action.WindowDress(25))) rec += Action.WindowDress(25).encode()
                e.apply(Action.EndTurn); rec += Action.EndTurn.encode()
            }
            return e.equity to rec.joinToString("\n")
        }
        val (a, rec) = run(); val (b, _) = run()
        assertEquals(a, b, 0.0)
        val r = KapitalEngine(11L)
        for (l in rec.split("\n")) Action.decode(l)?.let { r.apply(it) }
        assertEquals(a, r.equity, 1e-6)
    }

    @Test
    fun repositoryView_showsForensics_andActionsWork() {
        val repo = com.example.data.GameRepository(null, 3L)
        val v0 = repo.gameState.value.operations
        assertEquals(3, v0.dressBlocks.size)
        assertTrue(v0.mScoreText.isNotEmpty())
        assertTrue(repo.setAuditor(1)); assertEquals(1, repo.gameState.value.operations.auditorLevel)
        assertTrue(repo.windowDress(10))
        assertTrue(repo.gameState.value.operations.dressText.contains("Sisa belum berbalik"))
    }
}
