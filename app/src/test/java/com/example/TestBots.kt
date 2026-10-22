package com.example

import com.example.engine.*

/** Pemain bot disiplin untuk menuntun permainan ke era yang diinginkan dalam uji. */
object TestBots {
    fun disciplinedStep(eng: KapitalEngine, t: Int, rec: MutableList<String>? = null) {
        fun apply(a: Action): Boolean { val ok = eng.apply(a); if (ok) rec?.add(a.encode()); return ok }
        if (t == 1) {
            apply(Action.SetSalesScheme(SalesScheme.KAS)); apply(Action.SetFactoryScheme(FactoryScheme.ANDAL))
            apply(Action.Journal("dec_termsheet", "cnt_ts_1", 75, 5500.0, "Refinancing Bank B", "Kas terjaga", "Bunga tetap"))
        }
        if (t == 2) { apply(Action.ReviewContract("cnt_factor")); apply(Action.DualSourcing) }
        if (t % 4 == 1) apply(Action.HedgeCotton)
        for (ev in eng.openEvents.toList()) {
            val o = ev.options.firstOrNull { it.id == "b" || it.id == "a" } ?: ev.options.first()
            apply(Action.EventChoice(ev.id, o.id))
        }
        if (eng.covenantDistancePct() < 4.5 && !eng.cureUsed) apply(Action.EquityCure)
    }

    /** Bermain disiplin sampai giliran absolut [targetTurn]; null bila permainan berakhir sebelum itu. */
    fun playUntil(seed: Long, targetTurn: Int): KapitalEngine? {
        val eng = KapitalEngine(seed)
        var t = 0
        while (eng.turn < targetTurn) {
            if (eng.gameOverReason != null) return null
            t++
            disciplinedStep(eng, t)
            eng.apply(Action.EndTurn)
        }
        return if (eng.gameOverReason == null) eng else null
    }

    /** Teks simpanan (SEED + aksi) dari permainan disiplin sampai [targetTurn], untuk menguji GameRepository; null bila gagal. */
    fun recordedSave(seed: Long, targetTurn: Int, accept: (KapitalEngine) -> Boolean = { true }): String? {
        val eng = KapitalEngine(seed)
        val rec = mutableListOf<String>()
        var t = 0
        while (eng.turn < targetTurn) {
            if (eng.gameOverReason != null) return null
            t++
            disciplinedStep(eng, t, rec)
            if (eng.apply(Action.EndTurn)) rec.add(Action.EndTurn.encode())
        }
        if (eng.gameOverReason != null || !accept(eng)) return null
        return (listOf("SEED:$seed") + rec).joinToString("\n")
    }

    /** Cari seed pertama yang bertahan sampai [targetTurn]. */
    fun firstSurvivor(targetTurn: Int, maxSeeds: Int = 80, accept: (KapitalEngine) -> Boolean = { true }): KapitalEngine {
        for (s in 1..maxSeeds) playUntil(s * 7919L, targetTurn)?.let { if (accept(it)) return it }
        throw AssertionError("tidak ada seed yang bertahan sampai giliran $targetTurn dalam $maxSeeds percobaan")
    }
}

/** Bot aktif: bertindak seperti pemain cakap (ekspansi saat penuh, anak usaha, IPO, buyback, suksesi, karbon). */
object ActiveBot {
    fun step(e: KapitalEngine, t: Int) {
        TestBots.disciplinedStep(e, t)
        if (e.gameOverReason != null) return
        val era = KapitalEngine.era(e.turn)
        if (e.utilization() > 0.97 && e.extraLines < 3 && e.cash > 18000.0 * e.sc.size) e.apply(Action.Invest("line"))
        if (t % 24 == 7 && e.cash > 15000.0 * e.sc.size) e.apply(Action.Invest(listOf("automation", "brand", "rnd")[(t / 24) % 3]))
        if (!e.esgCompliant && e.cash > 12000.0 * e.sc.size) e.apply(Action.Invest("solar"))
        if (era >= 2 && t % 12 == 3) {
            for (c in listOf("tarsis", "kestrel")) if (e.cash > 30000.0 * e.sc.size) e.apply(Action.OpenSubsidiary(c))
            if (e.fxHedgeActive.not() && e.subsidiaries.isNotEmpty()) e.apply(Action.HedgeFx)
            for (c in e.subsidiaries.keys) e.apply(Action.Repatriate(c))
        }
        if (era >= 3) {
            if (!e.isPublicCompany && e.ipoBlock() == null && e.cash > 20000.0 * e.sc.size) e.apply(Action.ExecuteIpo(20, false))
            if (e.isPublicCompany && t % 12 == 9 && e.cash > 60000.0 * e.sc.size) e.apply(Action.Buyback(2000))
            if (t % 12 == 5 && e.cash > 40000.0 * e.sc.size) e.apply(Action.OpenCaptiveBank)
        }
        if (era >= 4) {
            if (e.appointedSuccessorId == null) e.apply(Action.AppointSuccessor("exec_cfo"))
            if (e.carbonCredits < 5 && e.cash > 20000.0 * e.sc.size) e.apply(Action.CarbonAction("buy", 5))
        }
    }
}
