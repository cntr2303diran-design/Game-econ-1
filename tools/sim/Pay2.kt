import com.example.engine.*
fun step(eng: KapitalEngine, t: Int) {
    if (t == 1) {
        eng.apply(Action.SetSalesScheme(SalesScheme.KAS)); eng.apply(Action.SetFactoryScheme(FactoryScheme.ANDAL))
        eng.apply(Action.Journal("dec_termsheet", "cnt_ts_1", 75, 5500.0, "Refinancing Bank B", "Kas terjaga", "Bunga tetap"))
    }
    if (t == 2) { eng.apply(Action.ReviewContract("cnt_factor")); eng.apply(Action.DualSourcing) }
    if (t % 4 == 1) eng.apply(Action.HedgeCotton)
    for (ev in eng.openEvents.toList()) { val o = ev.options.firstOrNull { it.id == "b" || it.id == "a" } ?: ev.options.first(); eng.apply(Action.EventChoice(ev.id, o.id)) }
    if (eng.covenantDistancePct() < 4.5 && !eng.cureUsed) eng.apply(Action.EquityCure)
    if (eng.subsidiaries.values.any { it.active }) for ((cid, s) in eng.subsidiaries) if (s.active && s.retainedForeign > 0.08 && eng.ap >= 1) eng.apply(Action.Repatriate(cid))
}
fun q(l: List<Double>, p: Double) = if (l.isEmpty()) Double.NaN else l.sorted()[((l.size - 1) * p).toInt()]
fun main() {
    val H = 300
    for (cid in listOf("control", "tarsis", "kestrel", "halvenia", "halvenia+solar", "meridia")) {
        var alive = 0; var n = 0; var loss = 0; val pb = mutableListOf<Double>(); val roi = mutableListOf<Double>()
        for (seed in 1L..200L) {
            val e = KapitalEngine(seed * 7919L); var opened = false; var ni24 = 0.0
            for (t in 1..H) {
                if (e.gameOverReason != null) break
                step(e, t)
                if (cid != "control" && !opened && e.turn >= 97 && e.turn <= 110) {
                    if (cid == "halvenia+solar") e.apply(Action.Invest("solar"))
                    val c = cid.substringBefore("+")
                    if (e.subsidiaryBlock(c) == null) opened = e.apply(Action.OpenSubsidiary(c))
                }
                e.apply(Action.EndTurn)
                if (e.turn >= 97 + 14 + 72 + 6) break
                val s = e.subsidiaries[cid.substringBefore("+")]
                if (s != null && s.monthsActive == 24 && ni24 == 0.0) ni24 = s.cumulativeTranslatedNiRs
            }
            if (e.gameOverReason == null) alive++
            val s = e.subsidiaries[cid.substringBefore("+")]
            if (s != null && opened && s.monthsActive >= 48 && !s.nationalized) {
                n++; val cost = s.spentRs.coerceAtLeast(1.0)
                roi += s.cumulativeTranslatedNiRs / cost
                val steady = (s.cumulativeTranslatedNiRs - ni24) / (s.monthsActive - 24) * 12.0
                if (s.cumulativeTranslatedNiRs < 0) loss++
                pb += if (steady > 0) cost / steady else 99.0
            }
        }
        println("%-15s hidupInduk=%d/200 subN=%d rugiKum=%.0f%% ROIkum(p25/p50/p75)=%.2f/%.2f/%.2f paybackSteady(thn p25/p50/p75)=%.1f/%.1f/%.1f".format(cid, alive, n, 100.0*loss/n.coerceAtLeast(1), q(roi,.25), q(roi,.5), q(roi,.75), q(pb,.25), q(pb,.5), q(pb,.75)))
    }
}
