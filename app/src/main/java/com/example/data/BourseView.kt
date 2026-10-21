package com.example.data

import com.example.engine.Bourse
import com.example.engine.KapitalEngine
import com.example.model.*

/** Menyusun tampilan bursa (kutipan harga + laporan keuangan emiten) dari mesin. */
internal object BourseView {
    private fun f(v: Double) = KapitalEngine.fmt(v)
    private fun f1(v: Double) = KapitalEngine.fmt1(v)
    private fun f2(v: Double) = KapitalEngine.fmt2(v)
    private fun pct(v: Double) = (if (v >= 0) "+" else "") + f1(v * 100.0) + "%"
    private fun m(v: Double, neg: Boolean = false): String {
        val x = if (neg) -v else v
        return if (x < 0) "(${f(-x)})" else f(x)
    }

    fun build(e: KapitalEngine, origin: DataOriginInfo): BourseState {
        val b = e.bourse
        val idxNow = b.index
        val idxPrev = b.indexHist.toList().let { if (it.size >= 2) it[it.size - 2] else idxNow }
        val idxCh = idxNow / idxPrev - 1.0
        val port = e.portfolioValue()
        val basis = e.costBasis.values.sum()
        val unreal = port - basis
        val stocks = b.companies.map { c -> stock(e, c, origin) }
        return BourseState(
            indexText = f(idxNow),
            indexChangeText = pct(idxCh) + " bulan ini",
            indexTone = if (idxCh >= 0) SemanticTone.GOOD_GREEN else SemanticTone.DANGER_RED,
            indexSpark = b.indexHist.map { it.toFloat() },
            cashText = "${f(e.cash)} jt RS",
            portfolioText = "${f(port)} jt RS",
            unrealizedText = (if (unreal >= 0) "+" else "-") + f(Math.abs(unreal)) + " jt RS",
            dividendText = "${f(e.dividendsReceivedTotal)} jt RS",
            stocks = stocks,
            headlines = b.lastEvents
        )
    }

    private fun stock(e: KapitalEngine, c: Bourse.Company, origin: DataOriginInfo): StockItem {
        val s = c.spec
        val hist = c.priceHist.toList()
        val prev = if (hist.size >= 2) hist[hist.size - 2] else c.price
        val ch = if (prev > 0) c.price / prev - 1.0 else 0.0
        val lots = e.holdings[s.id] ?: 0
        val value = lots * KapitalEngine.LOT * c.price
        val basis = e.costBasis[s.id] ?: 0.0
        val unreal = value - basis
        val lotCost = KapitalEngine.LOT * c.price * (1.0 + KapitalEngine.BROKER_FEE)
        val eps = c.eps()
        val pe = if (eps > 0) c.price / eps else Double.NaN
        val pb = if (c.bvps > 0) c.price / c.bvps else Double.NaN
        val q = c.quarters.lastOrNull()
        val ttm = c.monthly.takeLast(12)
        val rev12 = ttm.sumOf { it.revenue }
        val ebit12 = ttm.sumOf { it.ebit }
        val int12 = ttm.sumOf { it.interest }
        val ni12 = ttm.sumOf { it.ni }
        val last = c.monthly.last()
        val roe = if (c.equity > 0) ni12 / c.equity else Double.NaN
        val der = if (c.equity > 0) c.debt / c.equity else Double.NaN
        val icr = if (int12 > 0) ebit12 / int12 else Double.NaN
        val dy = if (c.price > 0) c.lastDps / c.price else 0.0
        val ratios = listOf(
            "Harga / laba (P/E)" to (if (pe.isNaN()) "rugi" else f1(pe) + "x"),
            "Harga / nilai buku (P/B)" to (if (pb.isNaN()) "n/a" else f2(pb) + "x"),
            "Laba per saham (12 bln)" to f(eps) + " RS",
            "Nilai buku per saham" to f(c.bvps) + " RS",
            "Dividen terakhir / saham" to (if (c.lastDps > 0) f(c.lastDps) + " RS (imbal ${f1(dy * 100)}%)" else "belum ada"),
            "Margin EBIT (12 bln)" to (if (rev12 > 0) f1(ebit12 / rev12 * 100.0) + "%" else "n/a"),
            "Margin bersih (12 bln)" to (if (rev12 > 0) f1(ni12 / rev12 * 100.0) + "%" else "n/a"),
            "ROE" to (if (roe.isNaN()) "n/a" else f1(roe * 100.0) + "%"),
            "Utang / ekuitas (DER)" to (if (der.isNaN()) "ekuitas negatif" else f2(der) + "x"),
            "Penutup bunga (ICR)" to (if (icr.isNaN()) "tanpa utang" else f2(icr) + "x"),
            "Kapitalisasi pasar" to f(c.marketCap()) + " jt RS"
        )
        fun fr(label: String, cur: Double, prior: Double?, note: String, neg: Boolean = false, total: Boolean = false) =
            FinancialRowItem(label, m(cur, neg), if (prior == null) "—" else m(prior, neg), note, total,
                if (total && cur < 0) SemanticTone.DANGER_RED else SemanticTone.NEUTRAL_GRAY, origin)
        val pq = if (c.quarters.size >= 2) c.quarters[c.quarters.size - 2] else null
        val income = if (q == null) emptyList() else listOf(
            fr("Pendapatan", q.revenue, pq?.revenue, "Tiga bulan terakhir"),
            fr("Beban pokok penjualan", q.cogs, pq?.cogs, "Bahan baku, tenaga kerja pabrik", true),
            fr("Laba kotor", q.revenue - q.cogs, pq?.let { it.revenue - it.cogs }, "Margin ${f1((q.revenue - q.cogs) / q.revenue * 100)}%", false, true),
            fr("Beban usaha (gaji, pemasaran, umum)", q.opex, pq?.opex, "Di luar pabrik", true),
            fr("Penyusutan", q.dep, pq?.dep, "Aset tetap", true),
            fr("Laba usaha (EBIT)", q.ebit, pq?.ebit, "Margin ${f1(q.ebit / q.revenue * 100)}%", false, true),
            fr("Beban bunga", q.interest, pq?.interest, "Utang bank", true),
            fr("Pajak penghasilan (22%)", q.tax, pq?.tax, "Hanya bila untung", true),
            fr("Laba bersih", q.ni, pq?.ni, c.surpriseNote.ifEmpty { "—" }, false, true)
        )
        val ql = q?.last
        val balance = if (ql == null) emptyList() else listOf(
            fr("Kas dan setara kas", ql.cashEnd, pq?.cashEnd, "Akhir periode"),
            fr("Piutang usaha", ql.ar, pq?.last?.ar, "Rata-rata 55 hari"),
            fr("Persediaan", ql.inv, pq?.last?.inv, "Rata-rata 50 hari"),
            fr("Aset tetap neto", ql.fa, pq?.last?.fa, "Setelah penyusutan"),
            fr("Total aset", ql.cashEnd + ql.ar + ql.inv + ql.fa, pq?.last?.let { it.cashEnd + it.ar + it.inv + it.fa }, "", false, true),
            fr("Utang dagang", ql.ap, pq?.last?.ap, "Rata-rata 40 hari"),
            fr("Utang berbunga", ql.debt, pq?.last?.debt, "Pinjaman bank"),
            fr("Total liabilitas", ql.ap + ql.debt, pq?.last?.let { it.ap + it.debt }, "", false, true),
            fr("Ekuitas", ql.equity, pq?.last?.equity, "Modal + laba ditahan", false, true)
        )
        val cashFlow = if (q == null) emptyList() else listOf(
            fr("Laba bersih", q.ni, pq?.ni, ""),
            fr("Penyusutan (non-kas)", q.dep, pq?.dep, ""),
            fr("Perubahan piutang", -q.dAR, pq?.let { -it.dAR }, ""),
            fr("Perubahan persediaan", -q.dInv, pq?.let { -it.dInv }, ""),
            fr("Perubahan utang dagang", q.dAP, pq?.dAP, ""),
            fr("Arus kas operasi", q.cfo, pq?.cfo, "", false, true),
            fr("Belanja modal", -q.capex, pq?.let { -it.capex }, "Investasi"),
            fr("Pinjaman bersih", q.dDebt, pq?.dDebt, "Pendanaan"),
            fr("Dividen dibayar", -q.dividend, pq?.let { -it.dividend }, "Pendanaan"),
            fr("Perubahan kas", q.cfo - q.capex + q.dDebt - q.dividend, pq?.let { it.cfo - it.capex + it.dDebt - it.dividend }, "", false, true)
        )
        val reason = when {
            !c.listed -> "Dihentikan dari perdagangan"
            e.cash - lotCost < 1500.0 -> "Kas perusahaan Anda akan turun di bawah cadangan 1.500 jt RS"
            e.ap < 1 -> "Butuh 1 poin aksi"
            else -> ""
        }
        return StockItem(
            id = s.id, ticker = s.ticker, name = s.name, sector = s.sector, blurb = s.blurb, riskNote = s.riskNote,
            listed = c.listed,
            priceText = if (c.listed) f(c.price) + " RS" else "—",
            changeText = if (c.listed) pct(ch) else "dihentikan",
            changeTone = if (ch >= 0) SemanticTone.GOOD_GREEN else SemanticTone.DANGER_RED,
            holdingLots = lots,
            holdingText = if (lots > 0) "$lots lot · nilai ${f(value)} jt RS" else "belum punya",
            lotCostText = "${f(lotCost)} jt RS per lot (100 ribu lembar)",
            unrealizedText = if (lots > 0) (if (unreal >= 0) "+" else "-") + f(Math.abs(unreal)) + " jt RS" else "",
            unrealizedTone = if (unreal >= 0) SemanticTone.GOOD_GREEN else SemanticTone.DANGER_RED,
            spark = hist.map { it.toFloat() },
            ratios = ratios,
            reportLabel = q?.label ?: "Belum ada laporan",
            reportNote = if (q == null) "Laporan triwulan pertama dirilis pada akhir triwulan berjalan." else "Laporan resmi ${q.label} · ${c.surpriseNote}",
            income = income, balance = balance, cashFlow = cashFlow,
            canBuy = reason.isEmpty(), buyBlockReason = reason
        )
    }
}
