import com.example.engine.*
fun main() {
    for (mode in TransferPricingMode.values()) {
        val rs = mutableListOf<Double>(); var dead = 0
        for (seed in 1L..20L) {
            val eng = KapitalEngine(seed * 104729L); var t0 = 0
            while (eng.turn < 97 && eng.gameOverReason == null) { t0++; step(eng, t0); eng.apply(Action.EndTurn) }
            if (eng.gameOverReason != null) { dead++; continue }
            eng.apply(Action.OpenSubsidiary("meridia"))
            for (t in 1..40) { if (eng.gameOverReason != null) break; step(eng, 100 + t); if (t == 6) eng.apply(Action.SetTransferPricing(mode)); eng.apply(Action.EndTurn)
                if (t > 12) rs += (eng.lastIncome["op_ic_fee"] ?: 0.0) / (eng.lastIncome["revenue"] ?: 1.0) }
        }
        println("$mode: fee/revenue rata2=%.4f n=%d".format(if (rs.isEmpty()) 0.0 else rs.average(), rs.size))
    }
}
fun step(eng: KapitalEngine, t: Int) {
    if (t == 1) { eng.apply(Action.SetSalesScheme(SalesScheme.KAS)); eng.apply(Action.SetFactoryScheme(FactoryScheme.ANDAL)); eng.apply(Action.Journal("dec_termsheet","cnt_ts_1",75,5500.0,"R","k","b")) }
    if (t == 2) { eng.apply(Action.ReviewContract("cnt_factor")); eng.apply(Action.DualSourcing) }
    if (t % 4 == 1) eng.apply(Action.HedgeCotton)
    for (ev in eng.openEvents.toList()) { val o = ev.options.firstOrNull { it.id == "b" || it.id == "a" } ?: ev.options.first(); eng.apply(Action.EventChoice(ev.id, o.id)) }
    if (eng.covenantDistancePct() < 4.5 && !eng.cureUsed) eng.apply(Action.EquityCure)
}
