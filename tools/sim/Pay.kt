import com.example.engine.*
fun main() {
    for (cid in listOf("tarsis","kestrel","halvenia","meridia")) {
        val ratios = mutableListOf<Double>(); var loss = 0; var n = 0; var skipped = 0; var died = 0; val pay = mutableListOf<Double>(); var mAct = 0
        for (seed in 1L..200L) {
            val e = KapitalEngine(seed * 7919L)
            var opened = false
            for (t in 1..100) {
                if (e.gameOverReason != null) break
                if (!opened && t <= 6 && e.subsidiaryBlock(cid) == null) { opened = e.apply(Action.OpenSubsidiary(cid)); }
                e.apply(Action.EndTurn)
                if (opened && e.subsidiaries[cid]?.monthsActive ?: 0 >= 60) break
            }
            val s = e.subsidiaries[cid]
            if (!opened || s == null) { skipped++; continue }
            if (s.monthsActive < 12) { died++; continue }
            n++
            val cost = s.spentRs.coerceAtLeast(1.0)
            val ni = s.cumulativeTranslatedNiRs
            ratios += ni / cost
            if (ni < 0) loss++
            val perM = ni / s.monthsActive.coerceAtLeast(1); mAct += s.monthsActive
            if (perM > 0) pay += cost / perM / 12.0
        }
        ratios.sort(); pay.sort()
        fun q(l: List<Double>, p: Double) = if (l.isEmpty()) Double.NaN else l[((l.size - 1) * p).toInt()]
        println("avgAktif=%.0f bln ".format(mAct.toDouble()/n.coerceAtLeast(1)) + "%-9s n=%d skip=%d died=%d | NI kumulatif/biaya (~60 bln aktif) p10=%.2f p50=%.2f p90=%.2f | rugi=%.0f%% | payback tahun p25=%.1f p50=%.1f p75=%.1f".format(cid, n, skipped, died, q(ratios,.1), q(ratios,.5), q(ratios,.9), 100.0*loss/n.coerceAtLeast(1), q(pay,.25), q(pay,.5), q(pay,.75)))
    }
}
