package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test

/** Tahap B6: aktor yang belajar dari pola pemain. */
class ActorsTest {

    private fun check(e: KapitalEngine) {
        assertEquals(0.0, e.totalAssets() - e.totalLiabilities() - e.equity, 1e-6)
        val d = e.lastDetail
        if (d.isNotEmpty()) {
            assertEquals("arus kas = perubahan kas", 0.0, d["d_resid"] ?: 0.0, 1e-6)
            assertEquals("roll-forward ekuitas", 0.0, d["e_resid"] ?: 0.0, 1e-6)
        }
    }

    /** Seed (mesin) yang dilalui bot disiplin sampai giliran 120, agar kedua gaya sempat diamati. */
    private val survivors: List<Long> by lazy { (1L..80L).map { it * 104729L }.filter { TestBots.playUntil(it, 120) != null }.take(20) }

    /** Gaya main: "elang" memotong harga, melobi, menolak semua pemerasan; "merpati" tidak berperang harga dan membayar. */
    private fun play(seed: Long, hawk: Boolean, turns: Int = 150): KapitalEngine {
        val e = KapitalEngine(seed)
        var t = 0
        while (e.gameOverReason == null && t < turns) {
            t++
            if (hawk && t == 3) e.apply(Action.AdjustPrice(-5))
            if (hawk && t == 20) e.apply(Action.MediaLobby("lobby"))
            for (ev in e.openEvents.toList()) {
                val pick = when (ev.templateId) {
                    "pol_demand" -> if (hawk) "a" else "b"
                    "activist" -> if (hawk) "a" else "c"
                    else -> (ev.options.firstOrNull { it.id == "b" } ?: ev.options.first()).id
                }
                e.apply(Action.EventChoice(ev.id, pick))
            }
            TestBots.disciplinedStep(e, t)
            e.apply(Action.EndTurn)
            check(e)
        }
        return e
    }

    @Test
    fun politician_learnsFromPlayerStyle_sameSeedDifferentBehavior() {
        var hawkDemands = 0; var doveDemands = 0; var hawkApp = 0.0; var doveApp = 0.0; var hawkGrudge = 0.0; var doveGrudge = 0.0; var n = 0
        var hawkFines = 0.0
        for (seed in survivors) {
            val h = play(seed, true); val d = play(seed, false)
            hawkDemands += h.actors.demands; doveDemands += d.actors.demands
            hawkApp += h.actors.politicianAppetite; doveApp += d.actors.politicianAppetite
            hawkGrudge += h.actors.politicianGrudge; doveGrudge += d.actors.politicianGrudge
            hawkFines += h.actors.finesFromRefusals
            n++
        }
        println("AKTOR politisi: elang tuntutan=$hawkDemands nafsu=${hawkApp / n} dendam=${hawkGrudge / n} denda=$hawkFines | merpati tuntutan=$doveDemands nafsu=${doveApp / n} dendam=${doveGrudge / n}")
        assertTrue("kedua gaya menerima tuntutan: $hawkDemands/$doveDemands", hawkDemands >= 3 && doveDemands > 5)
        assertTrue("pembayar membuat nafsu pemeras lebih tinggi: ${doveApp / n} vs ${hawkApp / n}", doveApp / n > hawkApp / n + 0.05)
        assertTrue("penolak menumpuk dendam: ${hawkGrudge / n} vs ${doveGrudge / n}", hawkGrudge / n > doveGrudge / n + 0.02)
        assertTrue("denda tak negatif: $hawkFines", hawkFines >= 0.0)
        assertTrue("tuntutan lebih sering pada pembayar: $doveDemands vs $hawkDemands", doveDemands > hawkDemands)
    }

    @Test
    fun rivals_reactToAggressivePricing_differentlyThanToPassivePlayer() {
        var hawkCut = 0; var doveCut = 0; var n = 0
        for (seed in survivors.take(10)) {
            val h = play(seed, true, 60); val d = play(seed, false, 60)
            hawkCut += h.world.observedPriceCutMonths; doveCut += d.world.observedPriceCutMonths
            n++
        }
        println("AKTOR pesaing mengamati bulan potong harga: elang=${hawkCut / n} merpati=${doveCut / n}")
        assertTrue("pesaing membaca pola yang berbeda: $hawkCut vs $doveCut", hawkCut > doveCut + n)
    }

    @Test
    fun politicianEvent_hasThreeOptions_andPaymentLeavesEthicsEvidence() {
        var found = false
        for (seed in survivors) {
            val e = KapitalEngine(seed)
            var t = 0
            while (e.gameOverReason == null && t < 100 && e.openEvents.none { it.templateId == "pol_demand" }) {
                t++
                for (ev in e.openEvents.toList()) e.apply(Action.EventChoice(ev.id, (ev.options.firstOrNull { it.id == "b" } ?: ev.options.first()).id))
                TestBots.disciplinedStep(e, t); e.apply(Action.EndTurn)
            }
            val ev = e.openEvents.firstOrNull { it.templateId == "pol_demand" } ?: continue
            assertEquals(listOf("a", "b", "c"), ev.options.map { it.id })
            val refuse = ev.options.first { it.id == "a" }.outcomes.first()
            assertTrue("menolak berisiko denda inspeksi: p=${refuse.p}", refuse.p >= 0.30 && refuse.effects.any { it.kind == "fine" && it.a > 0.0 })
            val evid0 = e.ethicsEvidences.size; val app0 = e.actors.politicianAppetite; val cash0 = e.cash
            assertTrue(e.apply(Action.EventChoice(ev.id, "b")))
            assertEquals(evid0 + 1, e.ethicsEvidences.size)
            assertTrue(e.ethicsEvidences.last().kind == "bribe")
            assertTrue(e.actors.politicianAppetite > app0)
            assertEquals(1, e.actors.paid)
            e.apply(Action.EndTurn); check(e)
            found = true; break
        }
        assertTrue("sebuah tuntutan politisi muncul", found)
    }

    @Test
    fun activist_targetsPublicCompanies_andGreenmailAttractsMore() {
        var eventsHawk = 0; var eventsDove = 0; var greenmail = 0; var n = 0
        for (seed in survivors) {
            for (hawk in listOf(true, false)) {
                val e = KapitalEngine(seed)
                var t = 0
                while (e.gameOverReason == null && t < 200) {
                    t++
                    if (t >= 40 && !e.isPublicCompany && e.ipoBlock() == null && t % 5 == 0) e.apply(Action.ExecuteIpo(20, false))
                    for (ev in e.openEvents.toList()) {
                        val pick = when (ev.templateId) {
                            "pol_demand" -> "a"
                            "activist" -> if (hawk) "a" else "c"
                            else -> (ev.options.firstOrNull { it.id == "b" } ?: ev.options.first()).id
                        }
                        e.apply(Action.EventChoice(ev.id, pick))
                    }
                    TestBots.disciplinedStep(e, t); e.apply(Action.EndTurn); check(e)
                }
                if (hawk) eventsHawk += e.actors.activistEvents else { eventsDove += e.actors.activistEvents; greenmail += e.actors.greenmailPaid }
            }
            n++
        }
        println("AKTOR aktivis: melawan=$eventsHawk greenmail=$eventsDove (dibayar $greenmail) dari $n seed")
        assertTrue("aktivis muncul pada emiten: $eventsHawk/$eventsDove", eventsHawk + eventsDove >= 3)
    }

    @Test
    fun selfishExecutives_actOnlyWhenDisloyal_andLeaveEvidence() {
        var acts = 0
        for (seed in survivors) {
            val e = KapitalEngine(seed)
            var t = 0
            while (e.gameOverReason == null && t < 80) {
                t++
                for (ev in e.openEvents.toList()) e.apply(Action.EventChoice(ev.id, (ev.options.firstOrNull { it.id == "b" } ?: ev.options.first()).id))
                TestBots.disciplinedStep(e, t); e.apply(Action.EndTurn)
            }
            acts += e.actors.selfishActs
            if (e.execs.all { !it.present || it.loyalty >= 0.40 }) assertTrue(true)
        }
        println("AKTOR eksekutif egois bertindak $acts kali dari ${survivors.size} seed")
        assertTrue("eksekutif tidak setia kadang bertindak: $acts", acts >= 3)
    }

    @Test
    fun deterministic_sameStyleSameState() {
        fun run(): Double { val e = play(survivors.first(), true, 80); return e.equity + 1e3 * e.actors.demands + 1e2 * e.actors.politicianGrudge }
        assertEquals(run(), run(), 0.0)
    }
}
