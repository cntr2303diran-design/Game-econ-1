import com.example.engine.*
fun main() {
    var n = 0; var worstCash = 0.0; var worstEq = 0.0; var worstNi = 0.0; var worstStmt = 0.0; var worstFx = 0.0; var worstEqC = 0.0
    for (seed in 1L..60L) {
        val e = KapitalEngine(seed * 6700417L)
        var t = 0
        while (e.turn < 97 && e.gameOverReason == null) { t++; disc(e, t); e.apply(Action.EndTurn) }
        if (e.gameOverReason != null) continue
        e.apply(Action.OpenSubsidiary(listOf("tarsis","kestrel","meridia")[(seed % 3).toInt()]))
        var prevCash = e.consolidatedBalanceSnapshot()["cash"]!!
        for (k in 1..60) {
            if (e.gameOverReason != null) break
            t++; disc(e, t)
            if (k % 5 == 0) for (cid in e.subsidiaries.keys) e.apply(Action.Repatriate(cid))
            if (k == 10) e.apply(Action.SetTransferPricing(TransferPricingMode.OPTIMAL))
            val cashB = prevCash
            val eqB = e.consolidatedEquity()
            val begin = e.subsidiaries.mapValues { (cid, sb) -> Pair(sb.cashLocal, e.foreignMeta[cid]!!.fxToRs) }
            val activeB = e.subsidiaries.mapValues { it.value.active }
            e.apply(Action.EndTurn)
            val ci = e.lastConsolidatedIncome; val cb = e.lastConsolidatedBalance; val cf = e.lastConsolidatedCashFlow
            val cashA = cb["cash"]!!; prevCash = cashA
            var fxEff = 0.0; var buildCash = 0.0
            for ((cid, sb) in e.subsidiaries) { val b = begin[cid]; if (b != null && activeB[cid] == true) fxEff += b.first * (e.foreignMeta[cid]!!.fxToRs - b.second) }
            val dCash = cashA - cashB
            worstCash = maxOf(worstCash, kotlin.math.abs(dCash - cf["net"]!!))
            val resid = dCash - cf["net"]!!
            worstFx = maxOf(worstFx, kotlin.math.abs(resid - fxEff))
            if (kotlin.math.abs(resid) > 1e-6) println("resid=%.3f seed=%d k=%d subs=%s nat=%s lines=%s".format(resid, seed, k, e.subsidiaries.values.map { it.active }, e.subsidiaries.values.map { it.nationalized }, cf))
            // laba rugi konsolidasi: EBT - pajak = NI
            worstNi = maxOf(worstNi, kotlin.math.abs(ci["ebt"]!! - ci["tax"]!! - ci["ni"]!!))
            worstStmt = maxOf(worstStmt, kotlin.math.abs(ci["revenue"]!! - ci["cogs"]!! - ci["opex"]!! - ci["maint"]!! - ci["dep"]!! - ci["baddebt"]!! - ci["ebit"]!!))
            worstEqC = maxOf(worstEqC, kotlin.math.abs(e.lastConsolidatedEquity["resid"] ?: 0.0)); if (n % 211 == 0) println("eq: " + e.lastConsolidatedEquity)
            n++
            if (n % 97 == 0) println("seed=$seed k=$k dCash=%.2f net=%.2f  ebit_chk=%.3f".format(dCash, cf["net"], ci["revenue"]!! - ci["cogs"]!! - ci["opex"]!! - ci["maint"]!! - ci["dep"]!! - ci["baddebt"]!! - ci["ebit"]!!))
        }
    }
    println("worst cons equity resid=$worstEqC"); println("worst |resid - fxEff| = $worstFx"); println("n=$n worst |dCash - cons net|=$worstCash  |ebt-tax-ni|=$worstNi  |ebit decomposition|=$worstStmt")
}
fun disc(eng: KapitalEngine, t: Int) {
    if (t == 1) { eng.apply(Action.SetSalesScheme(SalesScheme.KAS)); eng.apply(Action.SetFactoryScheme(FactoryScheme.ANDAL)); eng.apply(Action.Journal("dec_termsheet","cnt_ts_1",75,5500.0,"R","k","b")) }
    if (t == 2) { eng.apply(Action.ReviewContract("cnt_factor")); eng.apply(Action.DualSourcing) }
    if (t % 4 == 1) eng.apply(Action.HedgeCotton)
    for (ev in eng.openEvents.toList()) { val o = ev.options.firstOrNull { it.id == "b" || it.id == "a" } ?: ev.options.first(); eng.apply(Action.EventChoice(ev.id, o.id)) }
    if (eng.covenantDistancePct() < 4.5 && !eng.cureUsed) eng.apply(Action.EquityCure)
}
