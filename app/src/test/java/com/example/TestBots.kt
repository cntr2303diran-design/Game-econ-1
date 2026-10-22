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
    fun recordedSave(seed: Long, targetTurn: Int): String? {
        val eng = KapitalEngine(seed)
        val rec = mutableListOf<String>()
        var t = 0
        while (eng.turn < targetTurn) {
            if (eng.gameOverReason != null) return null
            t++
            disciplinedStep(eng, t, rec)
            if (eng.apply(Action.EndTurn)) rec.add(Action.EndTurn.encode())
        }
        return (listOf("SEED:$seed") + rec).joinToString("\n")
    }

    /** Cari seed pertama yang bertahan sampai [targetTurn]. */
    fun firstSurvivor(targetTurn: Int, maxSeeds: Int = 80): KapitalEngine {
        for (s in 1..maxSeeds) playUntil(s * 7919L, targetTurn)?.let { return it }
        throw AssertionError("tidak ada seed yang bertahan sampai giliran $targetTurn dalam $maxSeeds percobaan")
    }
}
