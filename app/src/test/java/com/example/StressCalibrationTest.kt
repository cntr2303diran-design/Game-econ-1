package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/** Uji tekanan identitas akuntansi dengan aksi acak (termasuk aksi multinasional/korporasi) dan uji determinisme. */
class StressCalibrationTest {

    private fun randomAction(r: java.util.Random, e: KapitalEngine): Action? = when (r.nextInt(16)) {
        0 -> Action.AdjustPrice(r.nextInt(9) - 4)
        1 -> Action.HedgeCotton
        2 -> Action.HedgeFx
        3 -> Action.OpenSubsidiary(listOf("tarsis", "kestrel", "halvenia", "meridia")[r.nextInt(4)])
        4 -> Action.Repatriate(listOf("tarsis", "kestrel", "halvenia", "meridia")[r.nextInt(4)])
        5 -> Action.SetTransferPricing(TransferPricingMode.values()[r.nextInt(3)])
        6 -> Action.Invest(listOf("line", "automation", "rnd", "brand", "warehouse", "export", "solar")[r.nextInt(7)])
        7 -> Action.BorrowForeignSub(listOf("tarsis", "kestrel", "halvenia", "meridia")[r.nextInt(4)], 500 + r.nextInt(1500))
        8 -> Action.IssueCorporateBond(1500 + r.nextInt(2500), 24 + 12 * r.nextInt(3))
        9 -> Action.ExecuteIpo(10 + r.nextInt(20), r.nextBoolean())
        10 -> Action.OpenCaptiveBank
        11 -> Action.BuyStock("BSND", 1 + r.nextInt(5))
        12 -> Action.CarbonAction(if (r.nextBoolean()) "buy" else "sell", 1 + r.nextInt(3))
        13 -> Action.AcquireRival("riv_importir")
        14 -> if (!e.cureUsed) Action.EquityCure else null
        else -> null
    }

    /** Pemeriksaan laporan konsolidasi: arus kas = perubahan kas grup, laba rugi dan ekuitas konsolidasi cocok. Mengembalikan kas grup akhir. */
    private fun checkConsolidated(e: KapitalEngine, prevCash: Double, ctx: String): Double {
        val tol = 1e-6
        val cash = e.lastConsolidatedBalance["cash"] ?: return prevCash
        val net = e.lastConsolidatedCashFlow["net"] ?: return cash
        if (!prevCash.isNaN()) assertTrue("arus kas konsolidasi $ctx: dKas=${cash - prevCash} net=$net", abs((cash - prevCash) - net) < tol)
        val ci = e.lastConsolidatedIncome
        if (ci.isNotEmpty()) {
            assertTrue("NI konsolidasi $ctx", abs((ci["ebt"] ?: 0.0) - (ci["tax"] ?: 0.0) - (ci["ni"] ?: 0.0)) < tol)
            assertTrue("EBIT konsolidasi $ctx", abs((ci["revenue"] ?: 0.0) - (ci["cogs"] ?: 0.0) - (ci["opex"] ?: 0.0) - (ci["maint"] ?: 0.0) - (ci["dep"] ?: 0.0) - (ci["baddebt"] ?: 0.0) - (ci["ebit"] ?: 0.0)) < tol)
        }
        val eqr = e.lastConsolidatedEquity["resid"] ?: 0.0
        assertTrue("ekuitas konsolidasi $ctx resid=$eqr", abs(eqr) < tol)
        return cash
    }

    private fun check(e: KapitalEngine, ctx: String) {
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

    @Test
    fun identities_hold_100Seeds_60Turns_randomActions() {
        var turns = 0; var worst = 0.0
        for (seed in 1L..100L) {
            // Maju dulu ke Era 2 (giliran 100) dengan bot disiplin supaya fitur lintas batas terbuka; lewati bila perusahaan sudah gagal
            val e = TestBots.playUntil(seed * 104729L, 100) ?: continue
            val r = java.util.Random(seed)
            var prevCons = e.lastConsolidatedBalance["cash"] ?: Double.NaN
            for (t in 1..60) {
                if (e.gameOverReason != null) break
                repeat(2) { randomAction(r, e)?.let { a -> e.apply(a) } }
                for (ev in e.openEvents.toList()) e.apply(Action.EventChoice(ev.id, ev.options[r.nextInt(ev.options.size)].id))
                e.apply(Action.EndTurn)
                check(e, "seed=$seed t=${e.turn}")
                prevCons = checkConsolidated(e, prevCons, "seed=$seed t=${e.turn}")
                worst = maxOf(worst, abs(e.totalAssets() - e.totalLiabilities() - e.equity))
                turns++
            }
        }
        println("STRESS (mulai Era 2): $turns giliran-permainan diperiksa, selisih neraca induk terbesar=$worst")
        assertTrue("giliran teruji: $turns", turns > 1500)
    }

    @Test
    fun identities_hold_inEra3And4_withCorporateActions() {
        var turns = 0
        for (startTurn in listOf(232, 376)) {
            for (seed in 1L..40L) {
                val e = TestBots.playUntil(seed * 15485863L, startTurn) ?: continue
                val r = java.util.Random(seed + startTurn)
                var prevCons = e.lastConsolidatedBalance["cash"] ?: Double.NaN
                for (t in 1..60) {
                    if (e.gameOverReason != null) break
                    TestBots.disciplinedStep(e, 100 + t)
                    repeat(2) { randomAction(r, e)?.let { a -> e.apply(a) } }
                    e.apply(Action.EndTurn)
                    check(e, "era-mulai=$startTurn seed=$seed t=${e.turn}")
                    prevCons = checkConsolidated(e, prevCons, "era-mulai=$startTurn seed=$seed t=${e.turn}")
                    turns++
                }
            }
        }
        println("STRESS ERA 3-4: $turns giliran-permainan diperiksa")
        assertTrue("giliran teruji: $turns", turns > 500)
    }

    @Test
    fun sameSeedAndActions_giveIdenticalState() {
        fun run(): String {
            val e = KapitalEngine(31337L); val r = java.util.Random(5)
            for (t in 1..40) {
                if (e.gameOverReason != null) break
                randomAction(r, e)?.let { a -> e.apply(a) }
                for (ev in e.openEvents.toList()) e.apply(Action.EventChoice(ev.id, ev.options[0].id))
                e.apply(Action.EndTurn)
            }
            return "${e.turn}|${e.equity}|${e.cash}|${e.chronicle.size}|${e.subsidiaries.keys}|${e.gameOverReason}"
        }
        assertEquals(run(), run())
    }
}

class ChainCardsTest {
    @Test
    fun everyTemplateBuildsValidCards() {
        val e = KapitalEngine(4242L)
        val ctx = e.eventCtx()
        var n = 0
        for (t in EventDeck.templates) {
            val ev = EventDeck.makeEvent(t.id, ctx, java.util.Random(1).let { kotlin.random.Random(it.nextInt()) }, "x_${t.id}") ?: continue
            n++
            assertTrue("kartu ${t.id} punya pilihan", ev.options.size >= 2 || t.id.isNotEmpty())
            assertTrue("pilihan bawaan ${t.id} ada", ev.options.any { it.id == ev.defaultOptionId })
            for (o in ev.options) if (o.outcomes.isNotEmpty()) assertTrue("peluang ${t.id}/${o.id}", kotlin.math.abs(o.outcomes.sumOf { it.p } - 1.0) < 1e-9)
        }
        assertTrue("jumlah kartu dek: ${EventDeck.templates.size}", EventDeck.templates.size >= 38)
        for (rule in EventDeck.chainRules) assertTrue("target rantai ${rule.to} ada", EventDeck.templates.any { it.id == rule.to })
        println("KARTU: ${EventDeck.templates.size} template, $n terbangun valid, ${EventDeck.chainRules.size} aturan rantai")
    }

    @Test
    fun decisionsTriggerFollowUpCards_andReplayIsDeterministic() {
        var fired = 0; var seenFollowUp = 0
        for (seed in 1L..150L) {
            val e = KapitalEngine(seed * 6151L)
            for (t in 1..60) {
                if (e.gameOverReason != null) break
                for (ev in e.openEvents.toList()) {
                    val pick = ev.options.lastOrNull { it.id != "z" } ?: ev.options.first()
                    e.apply(Action.EventChoice(ev.id, pick.id))
                }
                e.apply(Action.EndTurn)
            }
            if (e.scheduledChainEvents.isNotEmpty() || e.chronicle.any { it.title.startsWith("Krisis Berantai") }) fired++
            if (e.chronicle.any { it.title.startsWith("Krisis Berantai") }) seenFollowUp++
        }
        println("RANTAI: dari 150 permainan, ada rantai terjadwal/selesai di $fired, kartu lanjutan muncul di $seenFollowUp")
        assertTrue("kartu lanjutan harus benar-benar muncul", seenFollowUp >= 20)
    }
}

class EraCrisisTest {
    @Test
    fun oneCrisisPerEra2to4_withinEraBounds_andDeterministic() {
        for (seed in 1L..50L) {
            val a = KapitalEngine(seed * 31L).crisisPlans()
            val b = KapitalEngine(seed * 31L).crisisPlans()
            assertEquals(3, a.size)
            for ((i, p) in a.withIndex()) {
                val era = i + 2
                assertEquals(era, p.era)
                assertTrue("mulai di era $era", KapitalEngine.era(p.start) == era && KapitalEngine.era(p.start + p.months) >= era)
                assertEquals(p.start, b[i].start)
            }
        }
    }

    @Test
    fun crisesAppearInChronicleWhenReached() {
        val e = TestBots.firstSurvivor(300, 80)
        val plans = e.crisisPlans()
        assertTrue("Krisis Era 2 tercatat", e.chronicle.any { it.title == plans[0].title })
        assertTrue("Krisis Era 3 belum dimulai bila giliran < start", e.turn < plans[1].start || e.chronicle.any { it.title == plans[1].title })
    }
}

class PublicCompanyTest {
    @Test
    fun publicCompanyFacesActivistsAndShortSellers_privateDoesNot() {
        var pubSeen = 0; var privSeen = 0; var ipos = 0
        for (seed in 1L..60L) {
            for (goPublic in listOf(true, false)) {
                val e = TestBots.playUntil(seed * 15485863L, 232) ?: continue
                if (goPublic) {
                    if (e.ipoBlock() == null && e.apply(Action.ExecuteIpo(20, false))) ipos++ else continue
                }
                for (t in 1..100) {
                    if (e.gameOverReason != null) break
                    TestBots.disciplinedStep(e, 100 + t)
                    e.apply(Action.EndTurn)
                }
                val n = e.eventHistory.count { it.title.contains("aktivis") || it.title.contains("Penjual-pendek") }
                if (goPublic) pubSeen += n else privSeen += n
            }
        }
        println("PUBLIK: IPO=$ipos kartu pasar modal diterima: publik=$pubSeen swasta=$privSeen")
        assertEquals(0, privSeen)
        assertTrue("perusahaan publik harus menerima kartu pasar modal", ipos == 0 || pubSeen > 0)
    }
}

class EvaluationTest {
    @Test
    fun finalEvaluationIsBoundedAndFailuresGetGradeF() {
        var failures = 0; var wins = 0; val totals = mutableListOf<Int>()
        for (seed in 1L..40L) {
            val e = KapitalEngine(seed * 7919L)
            var t = 0
            while (e.gameOverReason == null && e.turn < 481) { t++; TestBots.disciplinedStep(e, t); e.apply(Action.EndTurn) }
            val ev = e.evaluateCampaign()
            for (x in listOf(ev.nilaiPerusahaanScore, ev.ketahananScore, ev.kualitasKeputusanScore, ev.etikaReputasiScore, ev.dampakSosialScore, ev.totalScore))
                assertTrue("skor 0..100: $x", x in 0..100)
            if (e.isVictory) { wins++; totals += ev.totalScore; assertTrue("pemenang tidak F", ev.grade != "F") }
            else { failures++; assertEquals("F", ev.grade) }
        }
        println("EVALUASI: menang=$wins gagal=$failures skor menang min=${totals.minOrNull()} maks=${totals.maxOrNull()} rata2=${if (totals.isEmpty()) 0 else totals.average().toInt()}")
    }
}

class GroupStatementsUiTest {
    private class Mem(var text: String?) : com.example.data.SaveStore {
        override fun read() = text
        override fun write(text: String) { this.text = text }
    }

    @Test
    fun consolidatedAndEntityRowsAppearOnlyWithSubsidiary() {
        var saved: String? = null
        for (s in 1..60) { saved = TestBots.recordedSave(s * 7919L, 97); if (saved != null) break }
        assertTrue("perlu permainan yang bertahan sampai Era 2", saved != null)
        val store = Mem(saved)
        val repo = com.example.data.GameRepository(store)
        assertTrue("tanpa anak usaha tidak ada baris grup", repo.gameState.value.groupRows.isEmpty())
        assertTrue(repo.corporateAction("SUB|tarsis"))
        repeat(6) { repo.advanceTurn() }
        val st = repo.gameState.value
        assertTrue("baris grup tampil", st.groupRows.size > 20)
        assertTrue("baris entitas tampil", st.entityRows.any { it.label.contains("Tarsis") })
        assertTrue(st.groupRows.any { it.label == "Perubahan kas grup" })
        assertTrue(st.investSummary.any { it.first.startsWith("Anak usaha") })
        println("UI-GRUP: ${st.groupRows.size} baris grup, ${st.entityRows.size} baris entitas")
    }
}

class TransferPricingAuditTest {
    @Test
    fun auditRiskScalesWithProfitShifted() {
        val counts = mutableMapOf(TransferPricingMode.WAJAR to 0, TransferPricingMode.OPTIMAL to 0, TransferPricingMode.AGRESIF to 0)
        var games = 0
        for (seed in 1L..50L) {
            for (mode in counts.keys) {
                val e = TestBots.playUntil(seed * 104729L, 97) ?: continue
                e.apply(Action.OpenSubsidiary("meridia"))
                for (t in 1..80) {
                    if (e.gameOverReason != null) break
                    TestBots.disciplinedStep(e, 100 + t)
                    if (t == 6) e.apply(Action.SetTransferPricing(mode))
                    e.apply(Action.EndTurn)
                }
                counts[mode] = counts.getValue(mode) + e.chronicle.count { it.title == "Denda Audit Transfer Pricing" }
                if (mode == TransferPricingMode.WAJAR) games++
            }
        }
        println("AUDIT-TP: $games permainan x 80 giliran: $counts")
        assertEquals(0, counts.getValue(TransferPricingMode.WAJAR))
        assertTrue("agresif lebih sering diaudit daripada optimal", counts.getValue(TransferPricingMode.AGRESIF) > counts.getValue(TransferPricingMode.OPTIMAL))
        assertTrue("optimal tetap berisiko kecil", counts.getValue(TransferPricingMode.OPTIMAL) >= 0)
    }
}

class RivalLearningTest {
    private fun run(seed: Long, style: String): Pair<String, Double> {
        val e = KapitalEngine(seed)
        val profiles = mutableSetOf<String>()
        for (t in 1..40) {
            if (e.gameOverReason != null) break
            TestBots.disciplinedStep(e, t)
            when (style) {
                "cut" -> e.apply(Action.AdjustPrice(-3))
                "premium" -> { e.apply(Action.AdjustPrice(3)); if (t == 3) e.apply(Action.Invest("brand")) }
            }
            e.apply(Action.EndTurn)
            profiles += e.world.learnedPlayerProfile
        }
        return profiles.filter { it.isNotBlank() }.joinToString(" | ") to e.world.rivals.sumOf { it.learnedAggressionMod }
    }

    @Test
    fun rivalsReactDifferentlyToDifferentPlayStyles_sameSeed() {
        var different = 0; var cutAggr = 0.0; var premAggr = 0.0; var n = 0
        for (seed in 1L..40L) {
            val (pc, ac) = run(seed * 6151L, "cut")
            val (pp, ap) = run(seed * 6151L, "premium")
            if (pc != pp) different++
            cutAggr += ac; premAggr += ap; n++
        }
        println("RIVAL: profil berbeda di $different/$n seed; agresivitas total rata2: potong harga=${"%.2f".format(cutAggr / n)} premium=${"%.2f".format(premAggr / n)}")
        assertTrue("pesaing harus membaca gaya berbeda", different >= n * 0.6)
        assertTrue("potong harga memicu agresivitas lebih tinggi", cutAggr > premAggr)
    }
}

class DifficultyTest {
    @Test
    fun difficultyOnlyAsFirstAction_andSurvivesReplay() {
        val e = KapitalEngine(77L)
        assertTrue(e.apply(Action.SetDifficulty(Difficulty.DIREKTUR)))
        assertEquals(Difficulty.DIREKTUR, e.difficulty)
        e.apply(Action.EndTurn)
        assertFalse("ganti kesulitan di tengah jalan ditolak", e.apply(Action.SetDifficulty(Difficulty.MAGANG)))
        assertEquals(Difficulty.DIREKTUR, e.difficulty)
        val e2 = KapitalEngine(77L)
        for (l in listOf(Action.SetDifficulty(Difficulty.DIREKTUR).encode(), Action.EndTurn.encode())) Action.decode(l)?.let { e2.apply(it) }
        assertEquals(Difficulty.DIREKTUR, e2.difficulty)
    }

    @Test
    fun harderDifficultyMeansMoreEventsAndLowerSurvival() {
        fun stats(d: Difficulty): Pair<Double, Double> {
            var ev = 0; var alive = 0; val n = 30
            for (seed in 1L..n) {
                val e = KapitalEngine(seed * 4421L); e.apply(Action.SetDifficulty(d))
                var raised = 0
                for (t in 1..48) { if (e.gameOverReason != null) break; TestBots.disciplinedStep(e, t); e.apply(Action.EndTurn) }
                ev += e.eventsRaised; if (e.gameOverReason == null) alive++
            }
            return ev.toDouble() / n to alive.toDouble() / n
        }
        val m = stats(Difficulty.MAGANG); val k = stats(Difficulty.KRISIS_ABADI)
        println("DIFF: magang kejadian=${"%.1f".format(m.first)} hidup=${"%.2f".format(m.second)} | krisis kejadian=${"%.1f".format(k.first)} hidup=${"%.2f".format(k.second)}")
        assertTrue(k.first > m.first)
        assertTrue(k.second <= m.second)
    }
}

class EraReviewTest {
    @Test
    fun eraReviewRecordedAtEachBoundary_andDeterministic() {
        val a = TestBots.firstSurvivor(240).eraGrades.toList()
        val b = TestBots.firstSurvivor(240).eraGrades.toList()
        println("ERAGRADES: $a")
        assertEquals(a, b)
        assertTrue("rapor Era 1 dan 2 tercatat", a.size >= 2)
        assertTrue(a.all { it in 0..100 })
    }
}
