import com.example.engine.*
fun main() {
    var reach = IntArray(5); var finished = 0; val times = mutableListOf<Double>(); val eqAt = mutableMapOf<Int, MutableList<Double>>(); val minEqLate = mutableListOf<Double>(); val mgn = mutableListOf<Double>(); var worst = 0.0; val reasons = mutableMapOf<String,Int>(); val finalEq = mutableListOf<Double>(); var nan = 0
    val N = 40
    for (seed in 1L..N) {
        val eng = KapitalEngine(seed * 7919L)
        var t = 0
        while (eng.gameOverReason == null && eng.turn < 481) {
            t++
            if (t == 1) { eng.apply(Action.SetSalesScheme(SalesScheme.KAS)); eng.apply(Action.SetFactoryScheme(FactoryScheme.ANDAL)); eng.apply(Action.Journal("dec_termsheet","cnt_ts_1",75,5500.0,"R","k","b")) }
            if (t == 2) { eng.apply(Action.ReviewContract("cnt_factor")); eng.apply(Action.DualSourcing) }
            if (t % 4 == 1) eng.apply(Action.HedgeCotton)
            for (ev in eng.openEvents.toList()) { val o = ev.options.firstOrNull { it.id == "b" || it.id == "a" } ?: ev.options.first(); eng.apply(Action.EventChoice(ev.id, o.id)) }
            if (eng.covenantDistancePct() < 4.5 && !eng.cureUsed) eng.apply(Action.EquityCure)
            val t0 = System.nanoTime(); eng.apply(Action.EndTurn); val ms = (System.nanoTime() - t0) / 1e6
            if (eng.turn > 290) { times += ms; worst = maxOf(worst, ms) }
            if (eng.turn % 60 == 0) eqAt.getOrPut(eng.turn) { mutableListOf() } += eng.equity
            if (eng.equity.isNaN() || eng.cash.isNaN()) nan++
        }
        reach[KapitalEngine.era(eng.turn)]++
        if (eng.gameOverReason == null) finished++ else { val k = eng.gameOverReason!!.take(40); reasons[k] = (reasons[k] ?: 0) + 1 }
        finalEq += eng.equity
    }
    times.sort()
    println("40 seed bot disiplin: akhir di era ${reach.toList()} | selesai tanpa gagal=$finished | NaN=$nan")
    println("alasan gagal=$reasons")
    for ((k, v) in eqAt.toSortedMap()) { v.sort(); println("ekuitas giliran $k: n=${v.size} p10=${v[v.size/10].toInt()} median=${v[v.size/2].toInt()} p90=${v[v.size*9/10].toInt()}") }
    println("waktu EndTurn giliran>290 (JVM, ms): median=%.1f p95=%.1f maks=%.1f n=%d".format(times[times.size/2], times[(times.size*0.95).toInt()], worst, times.size))
}
