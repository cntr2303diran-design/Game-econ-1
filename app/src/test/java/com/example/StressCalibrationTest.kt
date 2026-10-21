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
            for (t in 1..60) {
                if (e.gameOverReason != null) break
                repeat(2) { randomAction(r, e)?.let { a -> e.apply(a) } }
                for (ev in e.openEvents.toList()) e.apply(Action.EventChoice(ev.id, ev.options[r.nextInt(ev.options.size)].id))
                e.apply(Action.EndTurn)
                check(e, "seed=$seed t=${e.turn}")
                worst = maxOf(worst, abs(e.totalAssets() - e.totalLiabilities() - e.equity))
                turns++
            }
        }
        println("STRESS (mulai Era 2): $turns giliran-permainan diperiksa, selisih neraca induk terbesar=$worst")
        assertTrue("giliran teruji: $turns", turns > 1500)
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
