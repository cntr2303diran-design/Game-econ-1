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
            "Balance sheet identity (Assets == Liab + Equity) failed at $context: assets=$assets, L+E=$liabPlusEq",
            abs(assets - liabPlusEq) < 1e-6
        )
        val det = engine.lastDetail
        if (det.isNotEmpty()) {
            val dResid = det["d_resid"] ?: 0.0
            val eResid = det["e_resid"] ?: 0.0
            assertTrue(
                "Cash flow identity (CFO + CFI + CFF == dCash) failed at $context: residual=$dResid",
                abs(dResid) < 1e-6
            )
            assertTrue(
                "Equity reconciliation identity failed at $context: residual=$eResid",
                abs(eResid) < 1e-6
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

                when (step % 6) {
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
            Action.AcquireRival("riv_importir")
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

        // Repeatedly undercut price and advance turns
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

        // Advance turns and resolve any open events
        for (i in 1..6) {
            val ev = engine.openEvents.firstOrNull()
            if (ev != null) {
                val opt = ev.options.first()
                engine.apply(Action.EventChoice(ev.id, opt.id))
            }
            engine.apply(Action.EndTurn)
        }

        // Setting aggressive transfer pricing adds ethics evidence and alters evaluation
        engine.apply(Action.SetTransferPricing(TransferPricingMode.AGRESIF))
        val afterEval = engine.evaluateCampaign()
        assertTrue("Ethics score must drop after aggressive transfer pricing evidence", afterEval.etikaReputasiScore < initialEval.etikaReputasiScore)
        assertTrue("Ethics evidence must be recorded", engine.ethicsEvidences.isNotEmpty())
        assertTrue(afterEval.grade in listOf("S", "A", "B", "C", "D", "F"))
    }

    @Test
    fun stageD_multinationalSubsidiariesTransferPricingAndErasWork() {
        val engine = KapitalEngine(203303L)
        assertTrue("Opening Meridia subsidiary should succeed", engine.apply(Action.OpenSubsidiary("meridia")))
        val mrdSub = engine.subsidiaries["meridia"]
        assertNotNull(mrdSub)

        // Advance 3 turns so the 2-month Meridia SPV construction completes and starts generating local surplus
        repeat(3) {
            engine.apply(Action.EndTurn)
            verifyIdentities(engine, "meridia build step $it")
        }
        assertTrue("Meridia subsidiary should be active after build months", mrdSub!!.active)
        assertTrue("Active subsidiary must accumulate foreign surplus", mrdSub.retainedForeign > 0.0)

        engine.apply(Action.SetTransferPricing(TransferPricingMode.OPTIMAL))
        assertEquals(TransferPricingMode.OPTIMAL, engine.transferPricingMode)

        // Test Era 4 & Era 5 investments exist in catalog
        assertNotNull(engine.investSpec("solar"))
        assertNotNull(engine.investSpec("institute"))
    }

    @Test
    fun stageE_massSimulationAcrossBotStrategiesMaintainsBalanceAndAccounting() {
        val seeds = (1..8).map { 203300L + it * 17L }
        var recklessSurvivors = 0
        var disciplinedSurvivors = 0

        for (seed in seeds) {
            // Strategy 1: Reckless price war + neglect bot
            val reckless = KapitalEngine(seed)
            reckless.apply(Action.AdjustPrice(-15))
            for (t in 1..15) {
                if (reckless.gameOverReason != null) break
                reckless.apply(Action.EndTurn)
                verifyIdentities(reckless, "reckless seed=$seed t=$t")
            }
            if (reckless.gameOverReason == null && reckless.cash > 2500.0) recklessSurvivors++

            // Strategy 2: Disciplined CEO bot (hedges, fixes incentives, manages price & working capital)
            val disciplined = KapitalEngine(seed)
            disciplined.apply(Action.SetSalesScheme(SalesScheme.KAS))
            disciplined.apply(Action.SetFactoryScheme(FactoryScheme.ANDAL))
            disciplined.apply(Action.ReviewContract("cnt_factor"))
            disciplined.apply(Action.AdjustPrice(4))
            for (t in 1..15) {
                if (disciplined.gameOverReason != null) break
                if (t % 3 == 1) {
                    disciplined.apply(Action.HedgeCotton)
                }
                val ev = disciplined.openEvents.firstOrNull()
                if (ev != null) {
                    val safeOpt = ev.options.firstOrNull { it.id == "a" || it.id == "b" } ?: ev.options.first()
                    disciplined.apply(Action.EventChoice(ev.id, safeOpt.id))
                }
                disciplined.apply(Action.EndTurn)
                verifyIdentities(disciplined, "disciplined seed=$seed t=$t")
            }
            if (disciplined.gameOverReason == null) disciplinedSurvivors++
        }

        assertTrue(
            "Disciplined strategy ($disciplinedSurvivors/8) should outperform or equal reckless strategy ($recklessSurvivors/8)",
            disciplinedSurvivors >= recklessSurvivors
        )
    }
}
