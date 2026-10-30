package com.example.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Bursa Efek Sundara (BES). Tujuh emiten fiktif dengan laporan keuangan lengkap (laba rugi, neraca, arus kas)
 * yang dihitung dari simulasi dunia: harga pesaing, kapas, baja, kurs, bunga, dan permintaan.
 *
 * Laporan dirilis tiap akhir triwulan (Maret, Juni, September, Desember). Harga saham bergerak bulanan dari
 * nilai wajar (laba 12 bulan terakhir x P/E yang bergantung suku bunga, atau nilai buku bila rugi) ditambah
 * sentimen pasar. Pemain tidak tahu nilai wajar; hanya melihat harga, laporan resmi, dan konsensus analis yang berisik.
 */
class Bourse {

    /** Konteks makro yang diberikan mesin tiap bulan. */
    class Ctx(
        val turn: Int,
        val econ: Double,
        val trend: Double,
        val priceIndex: Double,
        val costPressure: Double,
        val cotton: Double,
        val oil: Double,
        val steel: Double,
        val fxReal: Double,
        val policyRate: Double,
        val gap: Double,
        val playerRelPrice: Double,
        val rivalShare: (String) -> Double,
        val rivalPrice: (String) -> Double,
        val rivalActive: (String) -> Boolean,
        val rivalBase: (String) -> Double,
        /** Kejutan berita dari rilis data resmi (positif = kabar baik), -0,05..0,05. */
        val news: Double = 0.0,
        /** Tekanan risk-off 0..1 (panik perbankan dan ketegangan geopolitik). */
        val riskOff: Double = 0.0
    )

    class Month(
        val turn: Int,
        val revenue: Double, val cogs: Double, val opex: Double, val dep: Double,
        val interest: Double, val tax: Double, val ni: Double,
        // arus kas
        val dAR: Double, val dInv: Double, val dAP: Double, val capex: Double,
        val dDebt: Double, val dividend: Double, val cashEnd: Double,
        // neraca akhir bulan
        val ar: Double, val inv: Double, val fa: Double, val ap: Double, val debt: Double, val equity: Double
    ) {
        val ebit: Double get() = revenue - cogs - opex - dep
    }

    /** Laporan triwulanan resmi (jumlah 3 bulan, neraca akhir periode). */
    class Quarter(val label: String, val months: List<Month>) {
        val revenue = months.sumOf { it.revenue }
        val cogs = months.sumOf { it.cogs }
        val opex = months.sumOf { it.opex }
        val dep = months.sumOf { it.dep }
        val ebit = months.sumOf { it.ebit }
        val interest = months.sumOf { it.interest }
        val tax = months.sumOf { it.tax }
        val ni = months.sumOf { it.ni }
        val dAR = months.sumOf { it.dAR }
        val dInv = months.sumOf { it.dInv }
        val dAP = months.sumOf { it.dAP }
        val capex = months.sumOf { it.capex }
        val dDebt = months.sumOf { it.dDebt }
        val dividend = months.sumOf { it.dividend }
        val cfo: Double get() = ni + dep - dAR - dInv + dAP
        val last: Month get() = months.last()
        val cashEnd: Double get() = last.cashEnd
        val cashStart: Double get() = cashEnd - (cfo - capex + dDebt - dividend)
    }

    class Spec(
        val id: String, val name: String, val ticker: String, val sector: String, val blurb: String,
        val baseRevenue: Double, val unitCost: Double, val debtMultiple: Double, val rate: Double,
        val payout: Double, val beta: Double, val kind: String, val riskNote: String
    )

    class Company(val spec: Spec) {
        var cash = 0.0; var ar = 0.0; var inv = 0.0; var fa = 0.0; var ap = 0.0; var debt = 0.0; var equity = 0.0
        var sharesM = 1.0
        /** ln(harga/nilai wajar) setelah bulan terakhir: selisih penilaian pasar (turunan buku pesanan). */
        var sentiment = 0.0
        /** Informasi baru (kejutan laba) yang belum tercermin di harga; dipakai investor nilai. */
        var info = 0.0
        /** Nilai wajar bulan lalu; selisihnya ke bulan ini adalah kabar fundamental yang langsung sebagian tercermin. */
        var lastFair = 0.0
        var price = 0.0
        var listed = true
        var weakMonths = 0
        val priceHist = ArrayDeque<Double>()
        val monthly = ArrayList<Month>()
        val quarters = ArrayList<Quarter>()
        var lastDps = 0.0
        var lastReleaseTurn = -1
        var surpriseNote: String = ""
        val bvps: Double get() = if (sharesM > 0) equity / sharesM else 0.0
        fun ttmNi(): Double {
            val m = monthly.takeLast(12)
            if (m.isEmpty()) return 0.0
            return m.sumOf { it.ni } * (12.0 / m.size)
        }
        fun eps(): Double = if (sharesM > 0) ttmNi() / sharesM else 0.0
        fun marketCap(): Double = price * sharesM
    }

    private val specs = listOf(
        Spec("sx_mitra", "Mitra Tekstil Nusantara", "MTNU", "Tekstil", "Pemain terbesar, efisien, berutang sedang.",
            20556.0, 0.89, 2.0, 0.095, 0.40, 1.0, "riv_mitra", "Terpukul bila harga kapas naik dan perang harga."),
        Spec("sx_cahaya", "Cahaya Weaving", "CAWE", "Tekstil", "Agresif memotong harga untuk merebut pangsa.",
            17130.0, 0.92, 2.4, 0.10, 0.30, 1.2, "riv_cahaya", "Margin tipis; rentan saat kredit mengetat."),
        Spec("sx_prima", "Prima Garmen Utama", "PRGU", "Tekstil", "Garmen berbiaya tinggi, merek menengah.",
            10278.0, 0.95, 2.6, 0.105, 0.20, 1.3, "riv_prima", "Biaya tertinggi di industri; pertama bangkrut saat harga jatuh."),
        Spec("sx_velmora", "Importir Velmora", "VELM", "Tekstil", "Importir kain murah dari Velmora, suka membanting harga.",
            6852.0, 0.83, 1.4, 0.09, 0.35, 1.5, "riv_importir", "Bergantung kurs dan hubungan dagang; sanksi bisa memukulnya."),
        Spec("sx_mahkota", "Grup Mahkota Ritel", "MHKT", "Ritel", "Jaringan ritel pelanggan utama; menekan pemasok soal termin.",
            22000.0, 0.955, 1.6, 0.085, 0.45, 0.8, "retail", "Ikut siklus konsumsi; kredit dagang ke pemasok bergantung arus kasnya."),
        Spec("sx_kestrel", "Kestrel Precision Loom Parts", "KPLP", "Industri", "Pemasok suku cadang mesin tenun.",
            6200.0, 0.86, 1.2, 0.09, 0.50, 0.9, "parts", "Harga baja dan kuota ekspor menentukan margin."),
        Spec("sx_tarsis", "Tarsis Agro Kapas", "TAKA", "Agribisnis", "Petani kapas besar di Tarsis; untung besar saat kapas mahal.",
            9500.0, 0.90, 1.5, 0.08, 0.35, 1.1, "agro", "Pendapatan mengikuti harga kapas; cuaca ekstrem memukul panen.")
    )

    private companion object {
        /** Porsi kabar fundamental yang langsung tercermin pada harga bulan itu. */
        const val ANNOUNCE = 0.6
        /** Derau berita idiosinkratik emiten per bulan. */
        const val IDIO = 0.03
    }

    val companies: List<Company> = specs.map { Company(it) }
    val index: Double get() = companies.filter { it.listed }.sumOf { it.marketCap() } / baseCap * 1000.0
    private var baseCap = 1.0
    private var marketFactor = 0.0
    val indexHist = ArrayDeque<Double>()
    /** Buku pesanan 5.000 investor (A6) yang menggerakkan harga tujuh emiten dan saham perusahaan pemain. */
    val book = InvestorBook()
    /** Suasana pasar bersama bulan ini (faktor pasar). */
    val mood: Double get() = marketFactor
    var lastEvents: List<String> = emptyList(); private set

    init {
        for (c in companies) initBalance(c)
        baseCap = companies.sumOf { it.marketCap() }
        repeat(6) { indexHist.addLast(1000.0) }
    }

    fun company(id: String): Company? = companies.firstOrNull { it.spec.id == id }

    private fun initBalance(c: Company) {
        val s = c.spec
        val cost = s.unitCost * s.baseRevenue
        val cogs0 = 0.76 * cost
        val dep0 = 0.04 * cost
        c.fa = dep0 / 0.0081
        c.ar = s.baseRevenue * 55.0 / 30.0
        c.inv = cogs0 * 50.0 / 30.0
        c.ap = cogs0 * 40.0 / 30.0
        val ebitda = s.baseRevenue - cost + dep0
        c.debt = s.debtMultiple * ebitda * 12.0
        c.cash = cost * 1.5
        c.equity = c.cash + c.ar + c.inv + c.fa - c.ap - c.debt
        val bvps0 = 1500.0
        c.sharesM = c.equity / bvps0
        // harga awal: sedikit di atas nilai buku mengikuti profitabilitas
        val margin = 1.0 - s.unitCost
        c.price = bvps0 * (1.0 + 6.0 * margin)
        // riwayat 12 bulan stasioner agar laba 12 bulan terakhir tersedia sejak awal
        val netIncomeMonth = (s.baseRevenue - cost - c.debt * s.rate / 12.0) * 0.78
        repeat(12) { i ->
            c.monthly += Month(27 + i, s.baseRevenue, cogs0, 0.20 * cost, dep0, c.debt * s.rate / 12.0, max(0.0, (s.baseRevenue - cost - c.debt * s.rate / 12.0) * 0.22),
                netIncomeMonth, 0.0, 0.0, 0.0, dep0 * 0.7, 0.0, 0.0, c.cash, c.ar, c.inv, c.fa, c.ap, c.debt, c.equity)
        }
        repeat(6) { c.priceHist.addLast(c.price * (1.0 - 0.01 * (5 - it))) }
    }

    private fun gauss(r: Random): Double {
        val u1 = max(r.nextDouble(), 1e-9)
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * Math.PI * r.nextDouble())
    }

    // =====================================================================
    // Satu bulan
    // =====================================================================
    fun step(r: Random, x: Ctx): List<String> {
        val events = mutableListOf<String>()
        marketFactor = 0.85 * marketFactor + 0.45 * x.gap + gauss(r) * 0.018

        for (c in companies) {
            val s = c.spec
            // Pesaing yang bangkrut di dunia: perusahaan ikut bangkrut dan dihapus dari bursa
            if (s.kind.startsWith("riv_")) {
                val active = x.rivalActive(s.kind)
                if (!active && c.listed) {
                    c.listed = false
                    c.price = max(1.0, c.bvps * 0.05)
                    events += "${s.name} (${s.ticker}) dinyatakan pailit; saham dihentikan perdagangannya. Pemegang saham hampir tidak menerima apa-apa."
                    continue
                }
                if (active && !c.listed) {
                    resetAsNewIssuer(c, x)
                    events += "${s.name} kembali melantai di bursa sebagai emiten baru (${s.ticker}) setelah restrukturisasi. Saham lama tidak berlaku."
                }
            }
            if (!c.listed) continue
            val m = simulateMonth(c, x, r)
            c.monthly += m
            if (c.monthly.size > 60) c.monthly.removeAt(0)

            // Rilis triwulan
            if ((x.turn + 1) % 3 == 0) {
                val q = Quarter(quarterLabel(x.turn), c.monthly.takeLast(3).toList())
                val prevYear = c.quarters.lastOrNull()
                c.quarters += q
                if (c.quarters.size > 8) c.quarters.removeAt(0)
                c.lastReleaseTurn = x.turn
                // Konsensus analis berisik: rata-rata laba tiga bulan sebelumnya +- noise
                val consensus = (prevYear?.ni ?: q.ni) * (1.0 + gauss(r) * 0.08)
                val surprise = if (abs(consensus) > 1.0) (q.ni - consensus) / abs(consensus) else 0.0
                // Konsensus yang nyaris nol membuat selisih relatif meledak (laba tipis -> rugi besar = "17341%"); teks dibatasi, nilai asli dipakai apa adanya.
                fun gap(v: Double) = if (v > 1.0) "lebih dari 100%" else fmtPct(v)
                c.surpriseNote = when {
                    surprise > 0.10 -> "melampaui konsensus analis ${gap(surprise)}"
                    surprise < -0.10 -> "di bawah konsensus analis ${gap(-surprise)}"
                    else -> "sesuai konsensus"
                }
                c.info += (surprise * 0.5).coerceIn(-0.12, 0.12)
                events += "${s.name} (${s.ticker}) merilis laporan ${q.label}: pendapatan ${fmt(q.revenue)} jt RS, laba bersih ${fmt(q.ni)} jt RS, ${c.surpriseNote}."
                // Dividen tahunan setelah laporan Desember
                if (x.turn % 12 == 11) {
                    val ttm = c.ttmNi()
                    c.lastDps = if (ttm > 0) s.payout * ttm / c.sharesM else 0.0
                }
            }
        }

        // Harga saham
        val pe = (1.0 / (0.6 * x.policyRate / 100.0 + 0.07)).coerceIn(6.0, 16.0)
        for (c in companies) {
            if (!c.listed) { continue }
            val fair = fairValue(c, pe)
            c.info *= 0.92
            val fairSafe = max(fair, 1.0) // nilai wajar bisa <= 0 pada emiten berekuitas negatif
            // Kabar fundamental (laba dan buku berubah) langsung sebagian tercermin; sisanya lewat arus pesanan.
            if (c.lastFair > 0.0) c.price *= exp(ANNOUNCE * ln(fairSafe / c.lastFair).coerceIn(-0.5, 0.5))
            c.lastFair = fairSafe
            val lnMis = ln(fairSafe / c.price) + c.info
            val h = c.priceHist
            val ret3m = if (h.size >= 4) ln(h.last() / h[h.size - 4]) / 3.0 else 0.0
            val flow = book.trade(c.spec.id, r, lnMis, ret3m, c.spec.beta, marketFactor + x.news, x.riskOff)
            c.price = max(max(c.bvps * 0.15, 1.0), c.price * exp(flow.dLnPrice + IDIO * gauss(r)))
            c.sentiment = ln(c.price / fairSafe)
            c.priceHist.addLast(c.price); if (c.priceHist.size > 12) c.priceHist.removeFirst()
        }
        indexHist.addLast(index); if (indexHist.size > 12) indexHist.removeFirst()
        lastEvents = events
        return events
    }

    private fun abs(v: Double) = if (v < 0) -v else v
    private fun fmt(v: Double) = KapitalEngine.fmt(v)
    private fun fmtPct(v: Double) = KapitalEngine.fmt1(v * 100.0) + "%"

    fun fairValue(c: Company, pe: Double): Double {
        val eps = c.eps()
        val bv = c.bvps
        val earningsValue = if (eps > 0) eps * pe else bv * 0.5
        return max(bv * 0.5, 0.65 * earningsValue + 0.35 * bv)
    }

    private fun quarterLabel(turn: Int): String {
        // turn dengan (turn+1)%3==0 adalah akhir triwulan: turn % 12 = 2 (Maret), 5, 8, 11
        val m = turn % 12
        val q = (m / 3) + 1
        val year = 2030 + turn / 12
        return "Triwulan $q $year"
    }

    private fun resetAsNewIssuer(c: Company, x: Ctx) {
        c.listed = true
        c.weakMonths = 0
        initBalance(c)
        c.monthly.clear()
        c.quarters.clear()
        repeat(12) { c.monthly += Month(x.turn - 11 + it, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, c.cash, c.ar, c.inv, c.fa, c.ap, c.debt, c.equity) }
        c.sentiment = 0.0; c.info = 0.0; c.lastFair = 0.0
    }

    private fun simulateMonth(c: Company, x: Ctx, r: Random): Month {
        val s = c.spec
        val noise = 1.0 + gauss(r) * 0.01
        val base = s.baseRevenue
        val cost0 = s.unitCost * base
        // ---- volume, harga, biaya menurut jenis usaha ----
        var revenue: Double
        var cogs: Double
        var opex: Double
        val pi = x.priceIndex
        when {
            s.kind.startsWith("riv_") -> {
                val id = s.kind
                val marketSize = 68520.0
                val vol = marketSize * x.rivalShare(id) * x.econ * x.trend
                revenue = vol * x.rivalPrice(id) * pi * noise
                cogs = vol * (0.76 * s.unitCost) * x.costPressure * pi
                opex = 0.20 * cost0 * x.trend * pi
            }
            s.kind == "retail" -> {
                val vol = base * x.econ * x.trend
                revenue = vol * pi * noise
                cogs = vol * 0.76 * s.unitCost * pi * (1.0 + 0.35 * (x.costPressure - 1.0))
                opex = 0.20 * cost0 * x.trend * pi
            }
            s.kind == "parts" -> {
                val vol = base * (0.7 + 0.3 * x.econ) * x.trend
                val passSteel = 1.0 + 0.7 * 0.35 * (x.steel - 1.0)
                revenue = vol * pi * passSteel * noise
                cogs = vol * 0.76 * s.unitCost * pi * (0.5 + 0.5 * x.steel)
                opex = 0.20 * cost0 * x.trend * pi
            }
            else -> { // agro kapas
                val vol = base * x.trend
                revenue = vol * pi * x.cotton * x.fxReal.coerceIn(0.8, 1.3) * noise
                cogs = vol * 0.76 * s.unitCost * pi * (0.85 + 0.15 * x.cotton)
                opex = 0.20 * cost0 * x.trend * pi
            }
        }
        val dep = c.fa * 0.0081
        val interest = c.debt * (x.policyRate + 3.0 + (s.rate - 0.095) * 100.0) / 1200.0
        val ebit = revenue - cogs - opex - dep
        val pretax = ebit - interest
        val tax = max(0.0, pretax) * 0.22
        val ni = pretax - tax

        // modal kerja: penyesuaian bertahap ke target
        val arT = revenue * 55.0 / 30.0
        val invT = cogs * 50.0 / 30.0
        val apT = cogs * 40.0 / 30.0
        val newAr = c.ar + 0.5 * (arT - c.ar)
        val newInv = c.inv + 0.5 * (invT - c.inv)
        val newAp = c.ap + 0.5 * (apT - c.ap)
        val dAR = newAr - c.ar; val dInv = newInv - c.inv; val dAP = newAp - c.ap
        c.ar = newAr; c.inv = newInv; c.ap = newAp

        // belanja modal penggantian + dividen tahunan (April)
        val capex = dep * 0.85
        var dividend = 0.0
        if (x.turn % 12 == 3 && c.lastDps > 0) dividend = c.lastDps * c.sharesM
        c.fa += capex - dep

        var cfo = ni + dep - dAR - dInv + dAP
        var dDebt = 0.0
        var cashNew = c.cash + cfo - capex - dividend
        // pinjaman modal kerja otomatis bila kas kurang dari 0,5 bulan biaya, pelunasan bila berlebih
        val minCash = 0.5 * (cogs + opex)
        if (cashNew < minCash) { dDebt = minCash - cashNew; c.debt += dDebt; cashNew = minCash }
        else if (cashNew > 3.0 * (cogs + opex) && c.debt > 0) { val pay = min(c.debt, cashNew - 3.0 * (cogs + opex)); c.debt -= pay; dDebt = -pay; cashNew -= pay }
        c.cash = cashNew
        c.equity += ni - dividend

        // tekanan kebangkrutan
        val icr = if (interest > 0) ebit / interest else 99.0
        c.weakMonths = if (icr < 0.8 || c.equity < 0) c.weakMonths + 1 else max(0, c.weakMonths - 1)

        return Month(x.turn, revenue, cogs, opex, dep, interest, tax, ni, dAR, dInv, dAP, capex, dDebt, dividend, c.cash,
            c.ar, c.inv, c.fa, c.ap, c.debt, c.equity)
    }
}
