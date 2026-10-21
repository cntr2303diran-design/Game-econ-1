package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ExampleUnitTest {

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
        val engine = KapitalEngine(203303L)
        val initialEval = engine.evaluateCampaign()
        assertEquals(100, initialEval.etikaReputasiScore)

        for (i in 1..6) {
            val ev = engine.openEvents.firstOrNull()
            if (ev != null) {
                val opt = ev.options.first()
                engine.apply(Action.EventChoice(ev.id, opt.id))
            }
            engine.apply(Action.EndTurn)
            verifyIdentities(engine, "stageB turn $i")
        }

        engine.apply(Action.SetTransferPricing(TransferPricingMode.AGRESIF))
        val afterEval = engine.evaluateCampaign()
        assertTrue("Ethics score must drop after aggressive transfer pricing evidence", afterEval.etikaReputasiScore < initialEval.etikaReputasiScore)
        assertTrue("Ethics evidence must be recorded", engine.ethicsEvidences.isNotEmpty())
        assertTrue(afterEval.grade in listOf("S", "A", "B", "C", "D", "F"))
    }

    @Test
    fun stageD_multinationalSubsidiaryConsolidationLocalDebtAndEra3To5Mechanics() {
        val engine = KapitalEngine(203303L)
        engine.apply(Action.SetSalesScheme(SalesScheme.KAS))
        engine.apply(Action.SetFactoryScheme(FactoryScheme.ANDAL))
        engine.apply(Action.Journal("dec_termsheet", "cnt_ts_1", 75, 5500.0, "Refinancing Bank B", "Likuiditas aman", "Bunga naik"))
        engine.apply(Action.EndTurn)
        verifyIdentities(engine, "after initial setup turn")

        assertTrue("Opening Meridia subsidiary should succeed", engine.apply(Action.OpenSubsidiary("meridia")))
        val mrdSub = engine.subsidiaries["meridia"]!!
        verifyIdentities(engine, "after open meridia")

        // Advance 3 turns so Meridia SPV finishes construction and operates
        repeat(3) {
            val ev = engine.openEvents.firstOrNull()
            if (ev != null) {
                engine.apply(Action.EventChoice(ev.id, ev.options.first().id))
            }
            engine.apply(Action.EndTurn)
            verifyIdentities(engine, "meridia build step $it")
        }
        assertTrue("Meridia subsidiary should be active after build months", mrdSub.active)
        assertTrue("Active subsidiary must have positive local equity", mrdSub.localEquity > 0.0)
        assertTrue("Parent subsidiaryInvestmentRs must track historical cost", engine.subsidiaryInvestmentRs > 0.0)

        // Borrow local debt in Meridia
        assertTrue("Borrowing local debt in active subsidiary should succeed", engine.apply(Action.BorrowForeignSub("meridia", 1500)))
        assertTrue("Local debt in foreign currency must increase", mrdSub.localDebt > 0.0)
        verifyIdentities(engine, "after foreign sub borrow")

        engine.apply(Action.EndTurn)
        verifyIdentities(engine, "after turn with foreign sub debt")

        // Repatriate dividend from Meridia
        if (mrdSub.retainedForeign > 0.05) {
            assertTrue("Repatriation should succeed when retainedForeign > 0.05", engine.apply(Action.Repatriate("meridia")))
            engine.apply(Action.EndTurn)
            verifyIdentities(engine, "after repatriation turn")
        }

        // Test Corporate Bond, IPO, Captive Bank, and Succession
        assertTrue("Corporate bond issuance should succeed in Era 3", engine.apply(Action.IssueCorporateBond(2500, 36)))
        verifyIdentities(engine, "after bond issuance")
        engine.apply(Action.EndTurn)
        verifyIdentities(engine, "turn after bond issuance")

        assertTrue("IPO should succeed in Era 3", engine.apply(Action.ExecuteIpo(20, false)))
        assertTrue("Public float pct should be 20%", engine.publicFloatPct >= 0.19)
        verifyIdentities(engine, "after IPO")
        engine.apply(Action.EndTurn)
        verifyIdentities(engine, "turn after IPO")

        assertTrue("Opening captive bank should succeed after IPO proceeds", engine.apply(Action.OpenCaptiveBank))
        assertTrue("Captive bank capital must be recorded on balance sheet", engine.captiveBankCapitalRs > 0.0)
        verifyIdentities(engine, "after open captive bank")
        engine.apply(Action.EndTurn)
        verifyIdentities(engine, "turn after open captive bank")

        assertTrue("Appointing successor should succeed", engine.apply(Action.AppointSuccessor("exec_factory")))
        assertEquals("exec_factory", engine.successorExecId)
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

