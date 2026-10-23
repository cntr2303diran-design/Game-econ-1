import com.example.engine.*
fun playDisciplined(seed: Long, horizon: Int): KapitalEngine {
    val eng = KapitalEngine(seed)
    for (t in 1..horizon) {
        if (eng.gameOverReason != null) break
        if (t == 1) {
            eng.apply(Action.SetSalesScheme(SalesScheme.KAS)); eng.apply(Action.SetFactoryScheme(FactoryScheme.ANDAL))
            eng.apply(Action.Journal("dec_termsheet", "cnt_ts_1", 75, 5500.0, "Refinancing Bank B", "Kas terjaga", "Bunga tetap"))
        }
        if (t == 2) { eng.apply(Action.ReviewContract("cnt_factor")); eng.apply(Action.DualSourcing) }
        if (t % 4 == 1) eng.apply(Action.HedgeCotton)
        for (ev in eng.openEvents.toList()) { val o = ev.options.firstOrNull { it.id == "b" || it.id == "a" } ?: ev.options.first(); eng.apply(Action.EventChoice(ev.id, o.id)) }
        if (eng.covenantDistancePct() < 4.5 && !eng.cureUsed) eng.apply(Action.EquityCure)
        eng.apply(Action.EndTurn)
    }
    return eng
}
fun main() {
    for (h in listOf(24, 72)) {
        val reasons = mutableMapOf<String, Int>(); var dead = 0; val byPrem = mutableMapOf<String, IntArray>()
        for (seed in 1L..200L) {
            val e = playDisciplined(seed * 7919L, h)
            val p = byPrem.getOrPut(e.sc.id) { IntArray(2) }; p[0]++
            if (e.gameOverReason != null) { dead++; p[1]++; val k = e.gameOverReason!!.take(45); reasons[k] = (reasons[k] ?: 0) + 1 }
        }
        println("disiplin h=$h bangkrut=$dead/200 $reasons")
        println(byPrem.map { "${it.key}:${it.value[1]}/${it.value[0]}" })
    }
}
