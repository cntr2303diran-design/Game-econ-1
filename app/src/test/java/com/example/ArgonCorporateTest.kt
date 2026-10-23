package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ArgonCorporateTest {

    private fun verifyIdentities(engine: KapitalEngine, context: String) {
        val assets = engine.totalAssets()
        val liabPlusEq = engine.totalLiabilities() + engine.equity
        assertTrue(
            "Parent balance sheet identity (Assets == Liab + Equity) failed at $context: assets=$assets, L+E=$liabPlusEq, diff=${assets - liabPlusEq}",
            abs(assets - liabPlusEq) < 1e-5
        )
        assertFalse("NaN/Inf in assets at $context", assets.isNaN() || assets.isInfinite())
        assertFalse("NaN/Inf in equity at $context", engine.equity.isNaN() || engine.equity.isInfinite())

        val consBal = engine.lastConsolidatedBalance
        if (consBal.isNotEmpty()) {
            val cAssets = consBal["cons_assets"] ?: 0.0
            val cLiabEq = (consBal["cons_liab"] ?: 0.0) + (consBal["cons_equity"] ?: 0.0)
            assertTrue(
                "Consolidated balance sheet identity failed at $context: cAssets=$cAssets, cLiab+cEq=$cLiabEq, diff=${cAssets - cLiabEq}",
                abs(cAssets - cLiabEq) < 1e-5
            )
        }

        for ((cid, sub) in engine.subsidiaries) {
            if (sub.active && !sub.nationalized) {
                val subA = sub.totalAssetsForeign()
                val subLE = sub.totalLiabForeign() + sub.localEquity
                assertTrue(
                    "Subsidiary ($cid) local balance sheet identity failed at $context: A=$subA, L+E=$subLE",
                    abs(subA - subLE) < 1e-5
                )
            }
        }

        val det = engine.lastDetail
        if (det.isNotEmpty()) {
            val dResid = det["d_resid"] ?: 0.0
            val eResid = det["e_resid"] ?: 0.0
            assertTrue(
                "Cash flow identity (CFO + CFI + CFF == dCash) failed at $context: residual=$dResid",
                abs(dResid) < 1e-5
            )
            assertTrue(
                "Equity reconciliation identity failed at $context: residual=$eResid",
                abs(eResid) < 1e-5
            )
        }
    }

    @Test
    fun accountingIdentities_holdAcrossMultipleSeedsAndStrategies() {
        val seeds = listOf(203303L, 100001L, 777777L, 424242L, 999999L, 314159L)
        for (seed in seeds) {
            val engine = KapitalEngine(seed)
            verifyIdentities(engine, "seed=$seed initial")

            for (step in 1..24) {
                if (engine.gameOverReason != null) break

                when (step % 8) {
                    1 -> engine.apply(Action.AdjustPrice(2))
                    2 -> engine.apply(Action.HedgeCotton)
                    3 -> engine.apply(Action.HedgeFx)
                    4 -> engine.apply(
                        Action.ApplyCredit(
                            bankId = "bank_a",
                            amount = 1500,
                            tenor = 18,
                            purpose = "MODAL_KERJA",
                            floating = true,
                            bullet = false
                        )
                    )
                    5 -> {
                        val offer = engine.credit.applications.firstOrNull { it.status == AppStatus.OFFER }
                        if (offer != null) {
                            engine.apply(Action.ReviewOffer(offer.id))
                            engine.apply(Action.AcceptOffer(offer.id))
                        }
                    }
                    6 -> engine.apply(Action.MediaLobby("lobby"))
                    7 -> engine.apply(Action.CarbonAction("buy", 2))
                }

                engine.apply(Action.EndTurn)
                verifyIdentities(engine, "seed=$seed turn=${engine.turn}")
            }
        }
    }

    @Test
    fun actionEncodeDecode_roundTripForAllActionTypes() {
        val actions = listOf(
            Action.EndTurn,
            Action.Journal("dec_pemintalan", "accept", 75, 4200.0, "Integrasi hulu", "Margin +4%", "Kestrel tahan mesin"),
            Action.RejectProposal("dec_pemintalan"),
            Action.EventChoice("ev_union", "a"),
            Action.SetSalesScheme(SalesScheme.KAS),
            Action.SetFactoryScheme(FactoryScheme.ANDAL),
            Action.CrossCheck("news_39_BISNIS"),
            Action.ReviewContract("cnt_factor"),
            Action.EquityCure,
            Action.HedgeCotton,
            Action.DualSourcing,
            Action.Audit("exec_sales"),
            Action.Confront("exec_sales"),
            Action.Fire("exec_factory"),
            Action.ApplyCredit("bank_a", 2500, 24, "PEMINTALAN", floating = false, bullet = false),
            Action.ReviewOffer("CA-1"),
            Action.AcceptOffer("CA-1"),
            Action.DeclineOffer("CA-2"),
            Action.Invest("solar"),
            Action.Invest("institute"),
            Action.BuyStock("BSND", 5),
            Action.SellStock("BSND", 2),
            Action.AdjustPrice(-4),
            Action.SetDifficulty(Difficulty.DIREKTUR),
            Action.OpenSubsidiary("halvenia"),
            Action.SetTransferPricing(TransferPricingMode.AGRESIF),
            Action.Repatriate("halvenia"),
            Action.HedgeFx,
            Action.AcquireRival("riv_importir"),
            Action.BorrowForeignSub("halvenia", 1500),
            Action.IssueCorporateBond(3000, 36),
            Action.ExecuteIpo(20, false),
            Action.OpenCaptiveBank,
            Action.MediaLobby("lobby"),
            Action.CarbonAction("buy", 3),
            Action.AppointSuccessor("exec_factory")
        )

        for (act in actions) {
            val encoded = act.encode()
            val decoded = Action.decode(encoded)
            assertEquals("Round-trip failed for action $act", act, decoded)
        }
    }

    @Test
    fun stageA_rivalLearningAdaptsToPlayerBehavior() {
        val engine = KapitalEngine(203303L)
        val initialCutMonths = engine.world.observedPriceCutMonths

        engine.apply(Action.AdjustPrice(-8))
        engine.apply(Action.EndTurn)
        engine.apply(Action.AdjustPrice(-6))
        engine.apply(Action.EndTurn)

        assertTrue(
            "Rivals must observe and record repeated player price cuts",
            engine.world.observedPriceCutMonths > initialCutMonths
        )
        assertTrue(engine.world.learnedPlayerProfile.isNotBlank())
    }

    @Test
    fun stageB_cascadingEventChainsAndEthicsWork() {
        // Catatan: versi lama tes ini memanggil SetTransferPricing di Era 1, padahal aksi itu terkunci sampai Era 2
        // (apply mengembalikan false). Skor etika turun hanya kebetulan akibat event acak. Sekarang dimainkan sampai
        // Era 2 dan aksi harus benar-benar berhasil.
        val engine = TestBots.firstSurvivor(97)
        assertEquals(2, KapitalEngine.era(engine.turn))
        verifyIdentities(engine, "stageB awal Era 2")
        val before = engine.evaluateCampaign()
        val evidenceBefore = engine.ethicsEvidences.size
        assertTrue("transfer pricing agresif harus bisa dipilih di Era 2", engine.apply(Action.SetTransferPricing(TransferPricingMode.AGRESIF)))
        val afterEval = engine.evaluateCampaign()
        assertTrue("Ethics score must drop after aggressive transfer pricing evidence: ${before.etikaReputasiScore} -> ${afterEval.etikaReputasiScore}",
            afterEval.etikaReputasiScore < before.etikaReputasiScore)
        assertEquals("tepat satu bukti etika baru dicatat", evidenceBefore + 1, engine.ethicsEvidences.size)
        assertTrue(afterEval.grade in listOf("S", "A", "B", "C", "D", "F"))

        // Aksi yang sama di Era 1 ditolak dan tidak mencatat bukti.
        val early = KapitalEngine(203303L)
        assertFalse(early.apply(Action.SetTransferPricing(TransferPricingMode.AGRESIF)))
        assertEquals(0, early.ethicsEvidences.size)
        assertEquals(100, early.evaluateCampaign().etikaReputasiScore)

        // Rantai kejadian berjalan dan identitas tetap terjaga.
        for (i in 1..6) {
            val ev = engine.openEvents.firstOrNull()
            if (ev != null) engine.apply(Action.EventChoice(ev.id, ev.options.first().id))
            engine.apply(Action.EndTurn)
            verifyIdentities(engine, "stageB turn $i")
            if (engine.gameOverReason != null) break
        }
    }

    @Test
    fun stageD_multinationalSubsidiaryConsolidationLocalDebtAndEra3To5Mechanics() {
        // Era 1: semua fitur lintas batas/pasar modal terkunci
        val early = KapitalEngine(203303L)
        assertFalse("anak usaha terkunci di Era 1", early.apply(Action.OpenSubsidiary("meridia")))
        assertFalse("obligasi terkunci di Era 1", early.apply(Action.IssueCorporateBond(2500, 36)))
        assertFalse("IPO terkunci di Era 1", early.apply(Action.ExecuteIpo(20, false)))
        assertTrue("alasan penguncian tampil", early.subsidiaryBlock("meridia")!!.contains("Era 2"))

        // Era 2: bermain disiplin sampai giliran 97 lalu buka anak usaha
        // Disiplin sering menahan kas nyaris nol; skenario ini butuh kas untuk tahap pertama anak usaha.
        val engine = TestBots.firstSurvivor(97, accept = { it.subsidiaryBlock("meridia") == null && it.subsidiaryBlock("tarsis") == null })
        assertEquals(2, KapitalEngine.era(engine.turn))
        verifyIdentities(engine, "awal Era 2")
        assertTrue("Anak usaha Meridia harus bisa dibuka di Era 2", engine.apply(Action.OpenSubsidiary("meridia")))
        val mrdSub = engine.subsidiaries["meridia"]!!
        verifyIdentities(engine, "after open meridia")
        repeat(3) {
            for (ev in engine.openEvents.toList()) engine.apply(Action.EventChoice(ev.id, ev.options.first().id))
            engine.apply(Action.EndTurn)
            verifyIdentities(engine, "meridia build step $it")
        }
        assertTrue("Meridia aktif setelah masa bangun", mrdSub.active)
        assertTrue("Ekuitas lokal positif", mrdSub.localEquity > 0.0)
        assertTrue("Biaya historis investasi tercatat", engine.subsidiaryInvestmentRs > 0.0)
        assertTrue("Pinjaman lokal berhasil", engine.apply(Action.BorrowForeignSub("meridia", 1500)))
        assertTrue(mrdSub.localDebt > 0.0)
        verifyIdentities(engine, "after foreign sub borrow")
        repeat(3) { engine.apply(Action.EndTurn); verifyIdentities(engine, "turn with sub debt $it") }
        if (mrdSub.retainedForeign > 0.05) {
            assertTrue("Repatriasi berhasil", engine.apply(Action.Repatriate("meridia")))
            engine.apply(Action.EndTurn)
            verifyIdentities(engine, "after repatriation turn")
        }
        assertFalse("obligasi masih terkunci di Era 2", engine.apply(Action.IssueCorporateBond(2500, 36)))

        // Era 3: bermain sampai giliran 229
        val late = TestBots.firstSurvivor(229)
        assertEquals(3, KapitalEngine.era(late.turn))
        verifyIdentities(late, "awal Era 3")
        assertTrue("Obligasi berhasil di Era 3", late.apply(Action.IssueCorporateBond(2500, 36)))
        late.apply(Action.EndTurn); verifyIdentities(late, "setelah obligasi")
        if (late.ipoBlock() == null) {
            assertTrue("IPO berhasil", late.apply(Action.ExecuteIpo(20, false)))
            assertTrue(late.publicFloatPct >= 0.19)
            verifyIdentities(late, "after IPO")
            late.apply(Action.EndTurn); verifyIdentities(late, "turn after IPO")
        }
        if (late.cash > 5000.0) {
            assertTrue("bank grup", late.apply(Action.OpenCaptiveBank))
            assertTrue(late.captiveBankCapitalRs > 0.0)
            verifyIdentities(late, "after captive bank")
        }
        assertFalse("karbon dan suksesi masih terkunci di Era 3", late.apply(Action.AppointSuccessor("exec_factory")))

        // Era 4: bermain sampai giliran 373
        val last = TestBots.firstSurvivor(373, 120)
        assertEquals(4, KapitalEngine.era(last.turn))
        assertTrue("Suksesor berhasil di Era 4", last.apply(Action.AppointSuccessor("exec_factory")))
        assertEquals("exec_factory", last.successorExecId)
        verifyIdentities(last, "Era 4")
    }

    @Test
    fun stageE_massSimulation240GamesAcrossPremisesAndBotStrategies() {
        // 60 seeds (covering all 6 premises x 10 seeds each) x 4 bot strategies = 240 full 24-turn games
        val strategies = listOf("PASIF", "NEKAT_HARGA", "DISIPLIN_OPS", "MULTINASIONAL_HEDGING")
        val statsByStrategy = strategies.associateWith { mutableListOf<Triple<Boolean, Double, Int>>() }
        val statsByPremise = LinkedHashMap<String, MutableList<Pair<Boolean, Double>>>()

        for (premiseIdx in 0 until 6) {
            for (rep in 0 until 10) {
                val baseSeed = 100000L + rep * 997L + premiseIdx * 13L
                for (strat in strategies) {
                    val eng = KapitalEngine(baseSeed)
                    for (t in 1..24) {
                        if (eng.gameOverReason != null) break
                        when (strat) {
                            "PASIF" -> {
                                // Do nothing except end turn
                            }
                            "NEKAT_HARGA" -> {
                                if (t == 1) eng.apply(Action.AdjustPrice(-14))
                                if (t == 3) eng.apply(Action.SetTransferPricing(TransferPricingMode.AGRESIF))
                            }
                            "DISIPLIN_OPS" -> {
                                if (t == 1) {
                                    eng.apply(Action.SetSalesScheme(SalesScheme.KAS))
                                    eng.apply(Action.SetFactoryScheme(FactoryScheme.ANDAL))
                                    eng.apply(Action.AdjustPrice(4))
                                    eng.apply(Action.Journal("dec_termsheet", "cnt_ts_1", 75, 5500.0, "Refinancing Bank B", "Kas terjaga", "Bunga tetap"))
                                }
                                if (t == 2) {
                                    eng.apply(Action.ReviewContract("cnt_factor"))
                                    eng.apply(Action.DualSourcing)
                                }
                                if (t % 4 == 1) eng.apply(Action.HedgeCotton)
                                val ev = eng.openEvents.firstOrNull()
                                if (ev != null) {
                                    val opt = ev.options.firstOrNull { it.id == "b" || it.id == "a" } ?: ev.options.first()
                                    eng.apply(Action.EventChoice(ev.id, opt.id))
                                }
                                if (eng.covenantDistancePct() < 4.5 && !eng.cureUsed) {
                                    eng.apply(Action.EquityCure)
                                }
                            }
                            "MULTINASIONAL_HEDGING" -> {
                                if (t == 1) {
                                    eng.apply(Action.SetSalesScheme(SalesScheme.KAS))
                                    eng.apply(Action.SetFactoryScheme(FactoryScheme.ANDAL))
                                    eng.apply(Action.AdjustPrice(5))
                                    eng.apply(Action.Journal("dec_termsheet", "cnt_ts_1", 75, 5500.0, "Refinancing Bank B", "Kas terjaga", "Bunga tetap"))
                                }
                                if (t == 3 && eng.cash > 4500.0) {
                                    eng.apply(Action.OpenSubsidiary("meridia"))
                                    eng.apply(Action.SetTransferPricing(TransferPricingMode.OPTIMAL))
                                }
                                if (t == 8 && eng.cash > 5200.0) {
                                    eng.apply(Action.OpenSubsidiary("halvenia"))
                                }
                                if (t % 3 == 1) eng.apply(Action.HedgeCotton)
                                if (t % 3 == 2) eng.apply(Action.HedgeFx)
                                for ((cid, sub) in eng.subsidiaries) {
                                    if (sub.active && sub.retainedForeign > 0.08 && eng.ap >= 1) {
                                        eng.apply(Action.Repatriate(cid))
                                    }
                                }
                                val ev = eng.openEvents.firstOrNull()
                                if (ev != null) {
                                    val opt = ev.options.firstOrNull { it.id == "a" || it.id == "b" } ?: ev.options.first()
                                    eng.apply(Action.EventChoice(ev.id, opt.id))
                                }
                                if (eng.covenantDistancePct() < 4.5 && !eng.cureUsed) {
                                    eng.apply(Action.EquityCure)
                                }
                            }
                        }
                        eng.apply(Action.EndTurn)
                        verifyIdentities(eng, "massSim seed=$baseSeed strat=$strat turn=${eng.turn}")
                    }
                    val survived = eng.gameOverReason == null
                    val finalEq = eng.lastConsolidatedBalance["cons_equity"] ?: eng.equity
                    val evalScore = eng.evaluateCampaign().totalScore
                    statsByStrategy.getValue(strat).add(Triple(survived, finalEq, evalScore))
                    statsByPremise.getOrPut(eng.sc.title) { mutableListOf() }.add(survived to finalEq)
                }
            }
        }

        println("=== MASS SIMULATION REPORT (240 GAMES, 24 TURNS) ===")
        for (strat in strategies) {
            val list = statsByStrategy.getValue(strat)
            val survRate = list.count { it.first } * 100.0 / list.size
            val bankruptRate = 100.0 - survRate
            val sortedEq = list.map { it.second }.sorted()
            val medianEq = sortedEq[sortedEq.size / 2]
            val avgScore = list.map { it.third }.average()
            println("STRATEGI $strat -> Bertahan: ${"%.1f".format(survRate)}% | Bangkrut: ${"%.1f".format(bankruptRate)}% | Median Ekuitas: ${"%.0f".format(medianEq)} jt RS | Skor Rata-rata: ${"%.1f".format(avgScore)}")
        }
        println("=== BREAKDOWN PER PREMIS SKENARIO ===")
        for ((premTitle, list) in statsByPremise) {
            val survRate = list.count { it.first } * 100.0 / list.size
            val bankruptRate = 100.0 - survRate
            val sortedEq = list.map { it.second }.sorted()
            val medianEq = sortedEq[sortedEq.size / 2]
            println("PREMIS [$premTitle] (n=${list.size}) -> Bertahan: ${"%.1f".format(survRate)}% | Bangkrut: ${"%.1f".format(bankruptRate)}% | Median Ekuitas: ${"%.0f".format(medianEq)} jt RS")
        }

        val nekatSurv = statsByStrategy.getValue("NEKAT_HARGA").count { it.first }
        val disiplinSurv = statsByStrategy.getValue("DISIPLIN_OPS").count { it.first }
        val mncSurv = statsByStrategy.getValue("MULTINASIONAL_HEDGING").count { it.first }

        assertTrue(
            "Disciplined ($disiplinSurv/60) and Multinational ($mncSurv/60) strategies must survive more often than Reckless Price War ($nekatSurv/60)",
            disiplinSurv >= nekatSurv && mncSurv >= nekatSurv
        )
    }
}

