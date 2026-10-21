package com.example.engine

import java.util.Locale
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

enum class SalesScheme(val label: String) {
    OMZET("Bonus berbasis omzet"),
    KAS("Bonus kas tertagih + clawback 3 tahun")
}

enum class FactoryScheme(val label: String) {
    HEMAT("KPI penghematan biaya kuartalan"),
    ANDAL("KPI keandalan mesin + audit perawatan")
}

enum class Tone { BAD, WARN, GOOD, INFO, NEUTRAL }

data class ReportLine(val text: String, val tone: Tone)
data class Memo(val execId: String, val from: String, val role: String, val text: String)
data class NewsPiece(
    val id: String,
    val slant: String,
    val headline: String,
    val summary: String,
    val confidence: Int, // 0 rendah, 1 sedang, 2 tinggi
    val isRumor: Boolean,
    val crossChecked: Boolean
)

data class ChronicleItem(
    val turn: Int,
    val title: String,
    val description: String,
    val causalChain: String,
    val actionIndex: Int? = null
)

data class JournalRecord(
    val id: String,
    val decisionTitle: String,
    val turnSubmitted: Int,
    val evaluateAtTurn: Int,
    val reason: String,
    val prediction: String,
    val confidencePct: Int,
    val failure: String,
    val predictedCash: Double,
    var outcome: Boolean? = null,
    var actualCash: Double? = null
)

data class PendingDecision(
    val id: String,
    val title: String,
    val subtitle: String,
    val apCost: Int,
    val badge: String,
    val tone: Tone,
    val assetSharePct: Int,
    val context: String,
    val isEvent: Boolean
)

data class Phase(val name: String, val startMonth: Int, val months: Int, val budget: Double)

class Loan(
    val id: String,
    val lender: String,
    var balance: Double,       // dalam mata uang pinjaman
    var annualRatePct: Double,
    val currencyAud: Boolean,  // true = A$, false = RS
    var amortPerMonth: Double,
    val amortStartTurn: Int,
    var maturityTurn: Int,
    var floatingSpreadPct: Double?,
    val covenantIcr: Double
)

class ExecState(
    val id: String,
    var name: String,
    var initials: String,
    val role: String,
    var loyalty: Double
) {
    var present = true
    var stopped = false
    var replacement = false
    var evidence: Boolean? = null
    var auditDueTurn = -1
    var vacantUntil = -1
    var lastOutcome = ""
}

/**
 * Mesin simulasi Era 1 KAPITAL. Kotlin murni (tanpa Android) agar bisa diuji di JVM.
 * Semua angka uang dalam juta Rial Sundara (jt RS), kecuali disebut lain.
 * Keacakan dunia diambil dari aliran per giliran (seed, giliran, subsistem), sehingga
 * dua cabang "Bagaimana Jika" mengalami dunia yang sama.
 */
class KapitalEngine(val seed: Long) {

    // ---------- Waktu dan sumber daya ----------
    var turn = 39; private set
    var ap = 4; private set
    val apMax: Int get() = 7 - execs.count { !it.present }
    var gameOverReason: String? = null; private set

    // ---------- Neraca ----------
    var cash = 6200.0; private set
    var normalAR = 26080.0; private set
    val stuffedBuckets = mutableListOf<Pair<Int, Double>>() // (giliran dibuat, nilai)
    var inventory = 17580.0; private set
    var payables = 11600.0; private set
    var fixedAssets = 64200.0; private set
    var equity = 0.0; private set
    val loans = mutableListOf<Loan>()

    // ---------- Makro ----------
    var policyRate = 8.25; private set
    var kurs = 15450.0; private set
    var econ = 1.0; private set
    var priceIndex = 1.0; private set
    var costIndex = 1.0; private set
    var inflationAnnual = 8.0; private set

    // ---------- Rahasia dunia (ditentukan seed) ----------
    private val worldRng = Random(seed)
    val electionShock = worldRng.nextDouble() < 0.6
    val rateHikeAfterElection = worldRng.nextDouble() < 0.6
    val cooKickback = worldRng.nextDouble() < 0.5
    val electionTurn = 48

    // ---------- Kebijakan dan status ----------
    var salesScheme = SalesScheme.OMZET; private set
    var factoryScheme = FactoryScheme.HEMAT; private set
    var deferredMonths = 3; private set
    var dualSourcing = false; private set
    var wageAddOn = 0.0; private set
    var lostCustomerShare = 0.0; private set
    var priceAdjPct = 0; private set
    var unionResolved = false; private set
    val unionDeadlineTurn = 40
    private var unionStrikeChance = 0.0
    private var unionStrikeRollTurn = -1
    var strikeMonthsLeft = 0; private set
    var breakdownMonthsLeft = 0; private set
    private var holdupThisTurn = false
    private var unskilledUntil = -1
    var sanctionHit = false; private set
    var covenantBreaches = 0; private set
    val reviewedContracts = mutableSetOf<String>()
    val checkedNews = mutableSetOf<String>()
    var termSheetChosen: String? = null; private set
    private var termSheetDisburseTurn = -1
    var spinningDecision: String? = null; private set  // "approved" / "rejected"
    var spinningStartTurn = -1; private set
    var spinningDelayed = false; private set
    var spinningSpent = 0.0; private set
    var spinningCompletedTurn = -1; private set
    private var macDemandTurn = -1
    private var oneOffOpex = 0.0

    // ---------- Riwayat ----------
    val ebitHist = mutableListOf(1300.0, 1190.0)
    val interestHist = mutableListOf(500.0, 510.0)
    val cashHist = mutableListOf(9800.0, 8900.0, 8200.0, 7600.0, 7300.0, 6200.0)
    val revenueHist = mutableListOf<Double>()
    val journals = mutableListOf<JournalRecord>()
    val chronicle = mutableListOf<ChronicleItem>()
    var lastReport: List<ReportLine> = emptyList(); private set
    var lastReportTitle = ""; private set
    var memos: List<Memo> = emptyList(); private set
    var news: List<NewsPiece> = emptyList(); private set
    var actionCount = 0; private set
    var lastProdFactor = 1.0; private set
    val kursHist = mutableListOf(14800.0, 14900.0, 15050.0, 15100.0, 15220.0, 15450.0)
    var prevCashFlow: Map<String, Double> = emptyMap(); private set
    var totalCrossChecks = 0; private set

    // Laporan keuangan bulan terakhir (untuk layar Perusahaan)
    var lastIncome: Map<String, Double> = emptyMap(); private set
    var prevIncome: Map<String, Double> = emptyMap(); private set
    var lastCashFlow: Map<String, Double> = emptyMap(); private set
    var prevBalance: Map<String, Double> = emptyMap(); private set

    val execs = listOf(
        ExecState("exec_coo", "Surya Atmaja", "SA", "Direktur Operasi (COO)", 0.35 + worldRng.nextDouble() * 0.6),
        ExecState("exec_sales", "Baskoro Wijaya", "BW", "Direktur Penjualan", 0.1 + worldRng.nextDouble() * 0.5),
        ExecState("exec_factory", "Ir. Hendra Gunawan", "HG", "Kepala Pabrik Kalimas", 0.25 + worldRng.nextDouble() * 0.6),
        ExecState("exec_cfo", "Ratna Pramudita", "RP", "Direktur Keuangan (CFO)", 0.75 + worldRng.nextDouble() * 0.2)
    )

    val spinningPhases: List<Phase>
        get() {
            val p3months = if (spinningDelayed) 7 else 4
            val p3budget = if (spinningDelayed) 5600.0 * 1.18 else 5600.0
            return listOf(
                Phase("1. Pembebasan lahan dan izin lingkungan", 0, 3, 1680.0),
                Phase("2. Konstruksi sipil dan gardu listrik", 3, 5, 4760.0),
                Phase("3. Impor dan perakitan mesin pintal", 8, p3months, p3budget),
                Phase("4. Uji coba produksi dan pelatihan operator", 8 + p3months, 3, 1960.0)
            )
        }

    init {
        loans += Loan("bank_b", "Bank Niaga Sundara (Bank B)", 24000.0, 16.25, false, 0.0, 999, 45, 8.0, 2.0)
        loans += Loan("bank_lama", "Bank Pembangunan (pinjaman lama)", 18000.0, 15.0, false, 200.0, 0, 999, null, 0.0)
        // Piutang hasil "obral kredit" 5 bulan terakhir (sudah terjadi sebelum giliran 39)
        for (t in 34..38) stuffedBuckets += t to 600.0
        equity = totalAssets() - totalLiabilities()
        chronicle += ChronicleItem(1, "Awal warisan PT Benang Sundara",
            "Mewarisi pabrik tekstil 340 pekerja dengan mesin tua, kas tipis, dan utang jatuh tempo.",
            "Titik awal simulasi (seed $seed).")
        chronicle += ChronicleItem(31, "Reformasi modal kerja",
            "Penagihan tunggakan dan negosiasi ulang pemasok menurunkan siklus kas dari 125 ke 80 hari.",
            "Restrukturisasi piutang → DSO turun → kas operasi tertolong.")
        journals += JournalRecord("jrn_31", "Pengetatan syarat kredit ritel (giliran 31)", 31, 37,
            "Menurunkan siklus kas agar tidak kehabisan kas.", "Kas tetap di atas 6.000 jt RS",
            75, "Peritel pindah ke pesaing yang mengobral kredit.", 6000.0, true, 7300.0)
        lastReportTitle = "Selamat datang di ${dateText(turn)}"
        lastReport = listOf(
            ReportLine("Anda memimpin PT Benang Sundara. Uji covenant Bank B terjadi di akhir giliran ini.", Tone.WARN),
            ReportLine("Serikat pekerja memberi tenggat sampai akhir giliran depan.", Tone.BAD),
            ReportLine("Pinjaman Bank B (24.000 jt RS) jatuh tempo di ${dateText(45)}.", Tone.WARN)
        )
        prevBalance = balanceSnapshot()
        val est = estimateMonth()
        lastIncome = est
        prevIncome = mapOf("revenue" to 14100.0, "cogs" to 9400.0, "opex" to 2380.0, "maint" to 610.0,
            "dep" to 520.0, "baddebt" to 0.0, "ebit" to 1190.0, "interest" to 510.0, "tax" to 150.0, "ni" to 530.0)
        lastCashFlow = mapOf("ni" to 530.0, "dep" to 520.0, "wc" to -2010.0, "cfo" to -960.0, "capex" to -120.0,
            "fin" to -20.0, "net" to -1100.0)
        memos = generateMemos(Random(seed + 7))
        news = generateNews(Random(seed + 11))
    }

    // =====================================================================
    // Utilitas
    // =====================================================================
    private fun rng(stream: Int): Random = Random(seed * 1_000_003L + turn * 7_919L + stream * 104_729L)

    private fun gaussian(r: Random): Double {
        val u1 = max(r.nextDouble(), 1e-9)
        val u2 = r.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * Math.PI * u2)
    }

    fun exec(id: String) = execs.first { it.id == id }

    fun stuffedTotal(): Double = stuffedBuckets.sumOf { it.second }
    fun totalAR(): Double = normalAR + stuffedTotal()
    fun loanBalanceRs(l: Loan): Double = if (l.currencyAud) l.balance * kurs else l.balance
    fun totalDebt(): Double = loans.sumOf { loanBalanceRs(it) }
    fun totalAssets(): Double = cash + totalAR() + inventory + fixedAssets
    fun totalLiabilities(): Double = payables + totalDebt()

    fun balanceSnapshot(): Map<String, Double> = mapOf(
        "cash" to cash, "ar" to totalAR(), "inv" to inventory, "fa" to fixedAssets,
        "assets" to totalAssets(), "payables" to payables,
        "debt_st" to loans.filter { it.maturityTurn - turn <= 12 }.sumOf { loanBalanceRs(it) },
        "debt_lt" to loans.filter { it.maturityTurn - turn > 12 }.sumOf { loanBalanceRs(it) },
        "equity" to equity
    )

    val stuffingActive: Boolean
        get() = salesScheme == SalesScheme.OMZET && exec("exec_sales").present && !exec("exec_sales").stopped
    val deferralActive: Boolean
        get() = factoryScheme == FactoryScheme.HEMAT && exec("exec_factory").present && !exec("exec_factory").stopped
    val kickbackActive: Boolean
        get() = cooKickback && exec("exec_coo").present && !exec("exec_coo").stopped && !exec("exec_coo").replacement

    fun spinningBenefit(): Double {
        if (spinningCompletedTurn < 0) return 0.0
        val months = turn - spinningCompletedTurn
        return 0.05 * min(1.0, months / 6.0)
    }

    fun cogsRatio(): Double {
        var r = 0.60 * (0.8 + 0.2 * kurs / 15450.0)
        if (kickbackActive) r += 0.01
        if (dualSourcing) r += 0.012
        r -= spinningBenefit()
        return r
    }

    fun monthlyInterest(): Double = loans.sumOf { loanBalanceRs(it) * it.annualRatePct / 100.0 / 12.0 }

    fun covenantThreshold(): Double = loans.filter { it.balance > 0.5 }.maxOfOrNull { it.covenantIcr } ?: 0.0

    /** Perkiraan laba rugi bulan berjalan tanpa kejadian acak (dipakai untuk peringatan dini). */
    fun estimateMonth(): Map<String, Double> {
        val normalRev = baseNormalRevenue(1.0)
        val stuff = if (stuffingActive) normalRev * 0.08 else 0.0
        val revenue = normalRev + stuff
        val cogs = revenue / (1.0 + priceAdjPct / 100.0) * cogsRatio() * (costIndex / priceIndex).coerceIn(0.8, 1.6)
        val opex = 2300.0 * costIndex + wageAddOn
        val maint = maintenanceSpend()
        val dep = fixedAssets * 0.0081
        val baddebt = stuffedBuckets.filter { turn - it.first >= 6 }.sumOf { it.second * 0.6 }
        val ebit = revenue - cogs - opex - maint - dep - baddebt
        val interest = monthlyInterest()
        val tax = max(0.0, ebit - interest) * 0.22
        return mapOf("revenue" to revenue, "cogs" to cogs, "opex" to opex, "maint" to maint, "dep" to dep,
            "baddebt" to baddebt, "ebit" to ebit, "interest" to interest, "tax" to tax, "ni" to ebit - interest - tax)
    }

    /** Faktor harga: pendapatan per unit naik, volume turun (semi-elastisitas 2,5). */
    fun priceRevenueFactor(): Double {
        val a = priceAdjPct / 100.0
        return (1.0 + a) * kotlin.math.exp(-2.5 * a)
    }

    fun priceVolumeFactor(): Double = kotlin.math.exp(-2.5 * priceAdjPct / 100.0)

    private fun baseNormalRevenue(prodFactor: Double): Double {
        var r = 13704.0 * econ * priceIndex * prodFactor * (1.0 - lostCustomerShare) * priceRevenueFactor()
        if (salesScheme == SalesScheme.KAS) r *= 0.97
        if (!exec("exec_sales").present) r *= 0.95
        return r
    }

    private fun maintenanceSpend(): Double {
        val needed = 610.0 * costIndex
        return if (deferralActive) 420.0 * costIndex
        else needed + if (deferredMonths > 0) 120.0 * costIndex else 0.0
    }

    /** ICR tiga bulan terakhir termasuk perkiraan bulan berjalan. */
    fun projectedIcr(): Double {
        val est = estimateMonth()
        val e = ebitHist.takeLast(2).sum() + est.getValue("ebit")
        val i = interestHist.takeLast(2).sum() + est.getValue("interest")
        return if (i <= 0.0) 99.0 else e / i
    }

    fun covenantDistancePct(): Double {
        val th = covenantThreshold()
        if (th <= 0.0) return 99.0
        return (projectedIcr() / th - 1.0) * 100.0
    }

    fun monthlyBurn(): Double {
        if (cashHist.size < 4) return 0.0
        val recent = cashHist.takeLast(4)
        return (recent.first() - recent.last()) / 3.0
    }

    fun runwayMonths(): Int {
        val burn = monthlyBurn()
        if (burn <= 0.0) return 99
        return (cash / burn).toInt().coerceIn(0, 99)
    }

    fun dso(): Double = totalAR() / max(1.0, lastIncome["revenue"] ?: 14800.0) * 30.0
    fun dio(): Double = inventory / max(1.0, lastIncome["cogs"] ?: 9900.0) * 30.0
    fun dpo(): Double = payables / max(1.0, lastIncome["cogs"] ?: 9900.0) * 30.0

    fun brierScore(): Double {
        val ev = journals.filter { it.outcome != null }
        if (ev.isEmpty()) return 0.25
        return ev.sumOf { val p = it.confidencePct / 100.0; val o = if (it.outcome == true) 1.0 else 0.0; (p - o) * (p - o) } / ev.size
    }

    fun pendingDecisions(): List<PendingDecision> {
        val list = mutableListOf<PendingDecision>()
        val assets = totalAssets()
        if (!unionResolved && gameOverReason == null) {
            val left = unionDeadlineTurn - turn
            list += PendingDecision("dec_serikat", "Tuntutan upah serikat",
                "Serikat Pekerja Tekstil Kalimas menuntut kenaikan upah 11% mengikuti harga beras",
                1, if (left <= 0) "tenggat giliran ini" else "tenggat ${left + 1} giliran", Tone.BAD,
                (2940.0 / assets * 100).toInt(),
                "Jika diabaikan sampai tenggat, serikat mogok tepat saat pengiriman kontrak ritel.", true)
        }
        if (termSheetChosen == null && loans.any { it.id == "bank_b" && it.balance > 0.5 } && gameOverReason == null) {
            val bankB = loans.first { it.id == "bank_b" }
            list += PendingDecision("dec_termsheet", "Term sheet dari 2 bank",
                "Refinancing Bank B yang jatuh tempo ${dateText(bankB.maturityTurn)} (pilih salah satu)",
                2, "2 AP", Tone.INFO, (24000.0 / assets * 100).toInt(),
                "Pinjaman Bank B 24.000 jt RS berbunga mengambang ${fmt1(bankB.annualRatePct)}%. Tanpa refinancing, bank hanya memperpanjang jika covenant terpenuhi.",
                false)
        }
        if (spinningDecision == null && gameOverReason == null) {
            list += PendingDecision("dec_pemintalan", "Proposal pabrik pemintalan",
                "Integrasi hulu benang di Kalimas · 14.000 jt RS, 70% dibiayai kredit investasi",
                3, "3 AP", Tone.INFO, (14000.0 / assets * 100).toInt(),
                "Direktur Operasi mengusulkan lini pemintalan sendiri agar tidak bergantung pada benang impor. Proyek sekitar 15 bulan; biaya benang turun setelah beroperasi.",
                false)
        }
        return list
    }

    // =====================================================================
    // Tindakan pemain
    // =====================================================================
    fun apply(action: Action): Boolean {
        if (gameOverReason != null) return false
        val ok = when (action) {
            Action.EndTurn -> { endTurn(); true }
            is Action.Journal -> doJournal(action)
            is Action.RejectProposal -> {
                if (action.decisionId == "dec_pemintalan" && spinningDecision == null) {
                    spinningDecision = "rejected"
                    chronicle += ChronicleItem(turn, "Proposal pemintalan ditolak",
                        "Anda menolak proyek integrasi hulu benang.", "Kas terjaga → biaya benang tetap bergantung impor.", actionCount)
                    true
                } else false
            }
            is Action.UnionOption -> doUnion(action.optionId)
            is Action.SetSalesScheme -> {
                if (action.scheme != salesScheme && spend(1)) { salesScheme = action.scheme; true } else false
            }
            is Action.SetFactoryScheme -> {
                if (action.scheme != factoryScheme && spend(1)) { factoryScheme = action.scheme; true } else false
            }
            is Action.CrossCheck -> {
                if (action.newsId !in checkedNews && news.any { it.id == action.newsId } && spend(1)) {
                    checkedNews += action.newsId
                    totalCrossChecks++
                    news = news.map { if (it.id == action.newsId) it.copy(crossChecked = true) else it }
                    true
                } else false
            }
            is Action.ReviewContract -> {
                if (action.contractId !in reviewedContracts && spend(1)) {
                    reviewedContracts += action.contractId
                    oneOffOpex += 50.0
                    true
                } else false
            }
            Action.DualSourcing -> {
                if (!dualSourcing && spend(1)) { dualSourcing = true; true } else false
            }
            is Action.Audit -> {
                val e = exec(action.execId)
                if (e.present && e.auditDueTurn < 0 && e.evidence == null && spend(2)) {
                    e.auditDueTurn = turn
                    oneOffOpex += 150.0
                    true
                } else false
            }
            is Action.Confront -> doConfront(action.execId)
            is Action.Fire -> doFire(action.execId)
            is Action.AdjustPrice -> {
                val target = (priceAdjPct + action.deltaPct).coerceIn(-15, 15)
                if (target != priceAdjPct && spend(1)) { priceAdjPct = target; true } else false
            }
        }
        if (ok) actionCount++
        return ok
    }

    private fun spend(cost: Int): Boolean {
        if (ap < cost) return false
        ap -= cost
        return true
    }

    private fun doJournal(a: Action.Journal): Boolean {
        val title: String
        when (a.decisionId) {
            "dec_pemintalan" -> {
                if (spinningDecision != null || !spend(3)) return false
                spinningDecision = "approved"
                spinningStartTurn = turn + 1
                spinningDelayed = Random(seed + 501).nextDouble() < 0.4
                loans += Loan("kredit_investasi", "Bank Pembangunan (kredit investasi)", 0.0, 14.0, false,
                    9800.0 / 60.0, turn + 13, turn + 73, null, 1.8)
                title = "Persetujuan pabrik pemintalan"
            }
            "dec_termsheet" -> {
                if (termSheetChosen != null || a.optionId !in listOf("cnt_ts_1", "cnt_ts_2")) return false
                if (!spend(2)) return false
                termSheetChosen = a.optionId
                termSheetDisburseTurn = turn + 1
                title = if (a.optionId == "cnt_ts_1") "Refinancing: Bank Pembangunan Sundara" else "Refinancing: Aurelia Global CitiTrust (A$)"
            }
            else -> return false
        }
        journals += JournalRecord("jrn_${turn}_${journals.size + 1}", title, turn, turn + 6,
            a.reason, a.prediction, a.confidencePct.coerceIn(0, 100), a.failure, a.predictedCash)
        chronicle += ChronicleItem(turn, title, "Keputusan besar dengan jurnal: \"${a.reason.take(120)}\"",
            "Prediksi kas ≥ ${fmt(a.predictedCash)} jt RS dalam 6 giliran (keyakinan ${a.confidencePct}%).", actionCount)
        return true
    }

    private fun doUnion(optionId: String): Boolean {
        if (unionResolved) return false
        val cost = when (optionId) { "opt_union_1" -> 1; "opt_union_2" -> 2; "opt_union_3" -> 1; else -> return false }
        if (!spend(cost)) return false
        unionResolved = true
        when (optionId) {
            "opt_union_1" -> { wageAddOn += 180.0; unionStrikeChance = 0.0 }
            "opt_union_2" -> { wageAddOn += 60.0; unionStrikeChance = 0.3 }
            "opt_union_3" -> { wageAddOn += 40.0; unionStrikeChance = 0.55; unskilledUntil = turn + 6 }
        }
        unionStrikeRollTurn = turn
        chronicle += ChronicleItem(turn, "Respons atas tuntutan serikat",
            when (optionId) {
                "opt_union_1" -> "Kenaikan upah 8% dan tunjangan beras disetujui."
                "opt_union_2" -> "Ditawarkan bagi hasil berbasis kualitas."
                else -> "Tuntutan ditolak; tenaga kontrak disiapkan."
            }, "Harga beras Tarsis naik → tuntutan upah → biaya tenaga kerja Sundara.", actionCount)
        return true
    }

    private fun isGuilty(e: ExecState): Boolean = when (e.id) {
        "exec_sales" -> stuffingActive || (!e.replacement && stuffedTotal() > 0.0)
        "exec_factory" -> deferralActive || (!e.replacement && deferredMonths > 0)
        "exec_coo" -> kickbackActive
        else -> false
    }

    private fun doConfront(id: String): Boolean {
        val e = exec(id)
        if (!e.present || e.evidence != true || !spend(1)) return false
        val guilty = isGuilty(e)
        val name = e.name
        if (guilty) {
            when {
                e.loyalty > 0.6 -> {
                    e.stopped = true
                    e.lastOutcome = "$name mengaku dan berjanji berhenti. Perilakunya berhenti selama skema insentif tidak mendorongnya lagi."
                }
                e.loyalty > 0.3 -> {
                    vacate(e, 3)
                    e.lastOutcome = "$name mengundurkan diri sebelum diperiksa lebih jauh. Posisi kosong 3 giliran."
                }
                else -> {
                    vacate(e, 3)
                    when (id) {
                        "exec_sales" -> { lostCustomerShare += 0.06; e.lastOutcome = "$name keluar dan membawa sekitar 6% pelanggan ritel ke Grup Mahkota." }
                        "exec_factory" -> { lostCustomerShare += 0.02; e.lastOutcome = "$name keluar dan membocorkan kondisi mesin ke media; dua pembeli menunda pesanan." }
                        else -> { oneOffOpex += 400.0; e.lastOutcome = "$name keluar sambil mengancam gugatan; biaya hukum 400 jt RS." }
                    }
                }
            }
        } else {
            e.loyalty -= 0.3
            e.evidence = false
            e.lastOutcome = "$name ternyata tidak bersalah (bukti audit keliru). Kepercayaannya pada Anda turun."
            if (e.loyalty < 0.2) {
                vacate(e, 3)
                e.lastOutcome += " Ia mengundurkan diri."
            }
        }
        chronicle += ChronicleItem(turn, "Konfrontasi: $name", e.lastOutcome,
            "Audit → bukti → konfrontasi → reaksi eksekutif sesuai loyalitas tersembunyinya.", actionCount)
        return true
    }

    private fun doFire(id: String): Boolean {
        val e = exec(id)
        if (!e.present || !spend(1)) return false
        oneOffOpex += 300.0
        val name = e.name
        vacate(e, 3)
        e.lastOutcome = "$name diberhentikan (pesangon 300 jt RS). Pengganti datang dalam 3 giliran."
        chronicle += ChronicleItem(turn, "Pemberhentian: $name", e.lastOutcome,
            "Posisi kosong → AP berkurang sementara.", actionCount)
        return true
    }

    private fun vacate(e: ExecState, turns: Int) {
        e.present = false
        e.vacantUntil = turn + turns
        e.evidence = null
        e.auditDueTurn = -1
    }

    // =====================================================================
    // Akhir giliran
    // =====================================================================
    private fun endTurn() {
        val lines = mutableListOf<ReportLine>()
        val startCash = cash
        val startAR = totalAR()
        val startInv = inventory
        val startPay = payables
        prevBalance = balanceSnapshot()
        prevIncome = lastIncome

        // ---- 1. Makro ----
        val rMacro = rng(1)
        econ = 1.0 + 0.85 * (econ - 1.0) + gaussian(rMacro) * 0.012
        val monthlyInfl = (inflationAnnual / 12.0) / 100.0
        priceIndex *= 1.0 + monthlyInfl
        inflationAnnual += 0.08 * (7.5 - inflationAnnual)
        costIndex *= 1.0 + monthlyInfl
        kurs *= 1.0 + 0.003 + gaussian(rMacro) * 0.008
        if (turn == electionTurn) {
            lines += ReportLine("Pemilu Sundara berlangsung bulan ini.", Tone.INFO)
            if (electionShock) {
                kurs *= 1.18
                inflationAnnual += 3.0
                lines += ReportLine("Rial Sundara anjlok sekitar 18% setelah hasil pemilu; inflasi impor naik.", Tone.BAD)
                chronicle += ChronicleItem(turn, "Guncangan kurs pascapemilu",
                    "Rial Sundara melemah tajam terhadap A$.", "Subsidi jelang pemilu → defisit → investor keluar → kurs anjlok.")
                if (loans.any { it.id == "aurelia" && it.balance > 0.0 }) {
                    macDemandTurn = turn + 3
                    loans.first { it.id == "aurelia" }.annualRatePct = 9.2
                    lines += ReportLine("Aurelia CitiTrust memakai klausul MAC: bunga naik ke 9,2% dan 30% pokok harus dilunasi dalam 3 giliran.", Tone.BAD)
                }
            } else {
                lines += ReportLine("Pasar tenang setelah pemilu; kurs relatif stabil.", Tone.GOOD)
            }
        }
        if (turn in (electionTurn + 1)..(electionTurn + 3) && rateHikeAfterElection) {
            policyRate += 0.5
            lines += ReportLine("Bank sentral menaikkan suku bunga acuan ke ${fmt2(policyRate)}%.", Tone.WARN)
        }
        loans.forEach { l -> l.floatingSpreadPct?.let { l.annualRatePct = policyRate + it } }

        // ---- 2. Kejadian operasional ----
        val rOps = rng(2)
        if (!unionResolved && turn >= unionDeadlineTurn) {
            unionResolved = true
            strikeMonthsLeft = 2
            wageAddOn += 245.0
            lines += ReportLine("Tenggat serikat lewat: mogok massal 2 bulan, lalu upah naik 10% sebagai syarat damai.", Tone.BAD)
            chronicle += ChronicleItem(turn, "Mogok massal Kalimas", "Tuntutan serikat diabaikan sampai tenggat.",
                "Harga beras naik → tuntutan upah diabaikan → mogok → produksi anjlok.")
        } else if (unionStrikeRollTurn == turn && unionStrikeChance > 0.0) {
            if (rOps.nextDouble() < unionStrikeChance) {
                strikeMonthsLeft = 2
                lines += ReportLine("Serikat menolak tawaran Anda dan mogok 2 bulan.", Tone.BAD)
                chronicle += ChronicleItem(turn, "Serikat menolak tawaran", "Mogok 2 bulan.", "Tawaran dianggap kurang → mogok.")
            } else {
                lines += ReportLine("Serikat menerima tawaran Anda. Produksi berjalan normal.", Tone.GOOD)
            }
        }
        var breakdownCost = 0.0
        if (breakdownMonthsLeft == 0) {
            var p = 0.015 * (1.0 + 0.5 * deferredMonths)
            if (!exec("exec_factory").present) p *= 1.5
            if (turn <= unskilledUntil) p *= 1.4
            if (rOps.nextDouble() < p) {
                breakdownMonthsLeft = 2
                breakdownCost = 1800.0 * costIndex
                lines += ReportLine("Mesin tenun lini 3 rusak berat: produksi turun 50% selama 2 bulan, perbaikan ${fmt(breakdownCost)} jt RS.", Tone.BAD)
                chronicle += ChronicleItem(turn, "Kerusakan mesin besar",
                    "Perawatan yang tertunda $deferredMonths bulan berujung kerusakan.",
                    "KPI penghematan → perawatan ditunda → keausan → mesin rusak.")
            }
        }
        holdupThisTurn = false
        if (!dualSourcing && rOps.nextDouble() < 0.10) {
            holdupThisTurn = true
            lines += ReportLine("Pemasok Kestrel menahan kiriman sensor dengan alasan \"mesin rusak\"; produksi turun 30% bulan ini.", Tone.BAD)
        }
        var sanctionFine = 0.0
        if (!dualSourcing && !sanctionHit && turn >= 44 && rOps.nextDouble() < 0.05) {
            sanctionHit = true
            sanctionFine = 600.0
            dualSourcing = true
            lines += ReportLine("Aurelia mendenda perusahaan Anda 600 jt RS karena pemasok Tier-2 memakai mineral Velmora. Dual sourcing kini diwajibkan.", Tone.BAD)
            chronicle += ChronicleItem(turn, "Denda sanksi berlapis",
                "Pemasok Kestrel ternyata memakai mineral Velmora.", "Sanksi Aurelia → pemasok Tier-2 → denda ke pemain.")
        }

        var prodFactor = 1.0
        if (strikeMonthsLeft > 0) prodFactor *= 0.6
        if (breakdownMonthsLeft > 0) prodFactor *= 0.6
        if (holdupThisTurn) prodFactor *= 0.7
        lastProdFactor = prodFactor

        // ---- 3. Laba rugi ----
        val normalRev = baseNormalRevenue(prodFactor)
        val stuffRev = if (stuffingActive) normalRev * 0.08 else 0.0
        val revenue = normalRev + stuffRev
        val cogs = revenue / (1.0 + priceAdjPct / 100.0) * cogsRatio() * (costIndex / priceIndex).coerceIn(0.8, 1.6)
        val opex = 2300.0 * costIndex + wageAddOn + oneOffOpex + sanctionFine
        oneOffOpex = 0.0
        val maint = maintenanceSpend() + breakdownCost + if (holdupThisTurn) 200.0 else 0.0
        if (deferralActive) deferredMonths += 1 else if (deferredMonths > 0) deferredMonths -= 1
        val dep = fixedAssets * 0.0081
        // Piutang obral kredit yang berumur 6 bulan: 60% macet, 40% tertagih
        var writeOff = 0.0
        var stuffedCollected = 0.0
        val keep = mutableListOf<Pair<Int, Double>>()
        for ((t, v) in stuffedBuckets) {
            if (turn - t >= 6) { writeOff += v * 0.6; stuffedCollected += v * 0.4 } else keep += t to v
        }
        stuffedBuckets.clear(); stuffedBuckets += keep
        if (stuffRev > 0.0) stuffedBuckets += turn to stuffRev
        if (writeOff > 0.0) {
            lines += ReportLine("Piutang ${fmt(writeOff)} jt RS dari peritel rapuh (termin 90 hari, 6 bulan lalu) dihapusbukukan sebagai kredit macet.", Tone.BAD)
        }
        val ebit = revenue - cogs - opex - maint - dep - writeOff
        val interest = monthlyInterest()
        val interestIncome = max(0.0, cash) * max(0.0, policyRate - 3.0) / 1200.0
        val tax = max(0.0, ebit - interest + interestIncome) * 0.22
        val ni = ebit - interest + interestIncome - tax
        equity += ni

        // ---- 4. Modal kerja ----
        val dsoNormal = if (salesScheme == SalesScheme.OMZET) 62.0 else 50.0
        val arTarget = normalRev * dsoNormal / 30.0
        val newNormalAR = normalAR + 0.5 * (arTarget - normalAR)
        val invTarget = cogs * 53.0 / 30.0 * (if (holdupThisTurn) 1.15 else 1.0)
        val newInv = inventory + 0.5 * (invTarget - inventory)
        val payTarget = cogs * 35.0 / 30.0
        val newPay = payables + 0.5 * (payTarget - payables)
        normalAR = newNormalAR
        inventory = newInv
        payables = newPay

        // ---- 5. Investasi dan pendanaan ----
        var capex = 0.0
        var newDebt = 0.0
        var principal = 0.0
        if (spinningDecision == "approved" && spinningCompletedTurn < 0 && turn >= spinningStartTurn) {
            val m = turn - spinningStartTurn
            for (ph in spinningPhases) if (m >= ph.startMonth && m < ph.startMonth + ph.months) capex += ph.budget / ph.months * costIndex
            val last = spinningPhases.last()
            if (m == spinningPhases[2].startMonth && spinningDelayed) {
                lines += ReportLine("Mesin pintal tertahan pembatasan ekspor Kestrel: tahap 3 molor 3 bulan dan biayanya naik 18%.", Tone.BAD)
            }
            if (m >= last.startMonth + last.months - 1) {
                spinningCompletedTurn = turn + 1
                lines += ReportLine("Pabrik pemintalan selesai. Biaya benang turun bertahap selama 6 bulan.", Tone.GOOD)
                chronicle += ChronicleItem(turn, "Pabrik pemintalan beroperasi", "Integrasi hulu selesai setelah ${m + 1} bulan.",
                    "Investasi hulu → benang sendiri → rasio HPP turun.")
            }
            spinningSpent += capex
            val inv = loans.firstOrNull { it.id == "kredit_investasi" }
            if (inv != null) { inv.balance += capex * 0.7; newDebt += capex * 0.7 }
        }
        fixedAssets += capex - dep

        // Pencairan refinancing
        if (termSheetDisburseTurn == turn && termSheetChosen != null) {
            val bankB = loans.firstOrNull { it.id == "bank_b" }
            val payoff = bankB?.balance ?: 0.0
            if (termSheetChosen == "cnt_ts_1") {
                loans += Loan("bank_pembangunan", "Bank Pembangunan Sundara", 25000.0, 14.5, false, 25000.0 / 48.0, turn + 1, turn + 48, null, 1.8)
                newDebt += 25000.0
                lines += ReportLine("Kredit Bank Pembangunan 25.000 jt RS cair; Bank B dilunasi. Cicilan pokok ±521 jt RS per bulan mulai bulan depan.", Tone.GOOD)
            } else {
                loans += Loan("aurelia", "Aurelia Global CitiTrust", 24000.0 / kurs, 5.2, true, 0.0, 999, turn + 36, null, 2.4)
                newDebt += 24000.0
                lines += ReportLine("Pinjaman Aurelia ${fmt2(24000.0 / kurs * 1000)} ribu A$ cair; Bank B dilunasi. Utang kini dalam A$.", Tone.INFO)
                if (turn >= electionTurn && electionShock && macDemandTurn < 0) { /* guncangan sudah lewat */ }
            }
            if (bankB != null) { principal += payoff; bankB.balance = 0.0 }
        }
        // Tuntutan MAC Aurelia
        if (macDemandTurn == turn) {
            val au = loans.firstOrNull { it.id == "aurelia" }
            if (au != null && au.balance > 0) {
                val pay = au.balance * 0.3
                au.balance -= pay
                principal += pay * kurs
                lines += ReportLine("Pelunasan paksa 30% pokok Aurelia: ${fmt(pay * kurs)} jt RS.", Tone.BAD)
            }
        }
        // Cicilan rutin
        for (l in loans) {
            if (l.balance > 0.0 && turn >= l.amortStartTurn && l.amortPerMonth > 0.0) {
                val p = min(l.balance, l.amortPerMonth)
                l.balance -= p
                principal += if (l.currencyAud) p * kurs else p
            }
        }
        // Jatuh tempo Bank B tanpa refinancing
        loans.firstOrNull { it.id == "bank_b" && it.balance > 0.5 && it.maturityTurn == turn }?.let { b ->
            if (covenantBreaches == 0) {
                b.maturityTurn += 12
                b.floatingSpreadPct = (b.floatingSpreadPct ?: 8.0) + 2.0
                b.annualRatePct = policyRate + (b.floatingSpreadPct ?: 0.0)
                oneOffOpex += b.balance * 0.01
                lines += ReportLine("Bank B memperpanjang pinjaman 12 bulan dengan bunga +2% dan biaya 1%.", Tone.WARN)
            } else {
                gameOverReason = "Bank B menolak memperpanjang pinjaman yang jatuh tempo karena covenant dilanggar. Bank menyita Pabrik Kalimas."
            }
        }
        // Selisih kurs atas utang A$ (rugi/laba kurs belum direalisasi)
        val fxBefore = prevBalance["debt_st"]!! + prevBalance["debt_lt"]!!
        val debtAfterFlows = totalDebt()
        val fxLoss = debtAfterFlows - (fxBefore + newDebt - principal)
        if (kotlin.math.abs(fxLoss) > 1.0) {
            equity -= fxLoss
            if (fxLoss > 50.0) lines += ReportLine("Rugi selisih kurs utang A$: ${fmt(fxLoss)} jt RS (mengurangi ekuitas).", Tone.BAD)
        }

        // ---- 6. Kas ----
        val dAR = totalAR() - startAR
        val dInv = inventory - startInv
        val dPay = payables - startPay
        val cfo = ni + dep - dAR - dInv + dPay
        cash += cfo - capex + newDebt - principal

        // Likuiditas darurat: factoring otomatis
        if (cash < 0.0) {
            val needed = -cash
            val maxFactor = normalAR * 0.7
            val faceValue = min(maxFactor, needed / 0.94)
            normalAR -= faceValue
            val proceeds = faceValue * 0.94
            val discount = faceValue - proceeds
            cash += proceeds
            equity -= discount
            lines += ReportLine("Kas negatif: piutang ${fmt(faceValue)} jt RS dijual ke perusahaan factoring dengan potongan 6%.", Tone.BAD)
            if (cash < 0.0) {
                gameOverReason = "Kas habis dan tidak ada lagi piutang yang bisa dijual. Perusahaan bangkrut karena cash crunch, walaupun laporan laba rugi masih bisa terlihat untung."
            }
        }

        // ---- 7. Covenant kuartalan ----
        ebitHist += ebit
        interestHist += interest
        if (turn % 3 == 0 && gameOverReason == null) {
            val th = covenantThreshold()
            val icr = ebitHist.takeLast(3).sum() / max(1.0, interestHist.takeLast(3).sum())
            val ebitdaAnnual = (ebitHist.takeLast(3).sum() + 3 * dep) * 4.0
            val netDebtRatio = (totalDebt() - cash) / max(1.0, ebitdaAnnual)
            val breach = (th > 0 && icr < th) || netDebtRatio > 3.5
            if (breach) {
                covenantBreaches++
                if (covenantBreaches >= 3) {
                    gameOverReason = "Covenant dilanggar tiga kuartal berturut-turut (ICR ${fmt2(icr)}x). Bank menyatakan technical default dan menyita Pabrik Kalimas."
                } else if (covenantBreaches == 2) {
                    var demand = 0.0
                    for (l in loans) if (!l.currencyAud && l.covenantIcr > 0.0 && l.balance > 0.5) {
                        val p = l.balance * 0.15
                        l.balance -= p
                        demand += p
                        l.floatingSpreadPct?.let { l.floatingSpreadPct = it + 2.0 }
                        if (l.floatingSpreadPct == null) l.annualRatePct += 2.0
                    }
                    cash -= demand
                    lines += ReportLine("Uji covenant GAGAL lagi: ICR ${fmt2(icr)}x (batas ${fmt2(th)}x). Bank memaksa pelunasan 15% pokok (${fmt(demand)} jt RS) dan menaikkan bunga 2%. Gagal sekali lagi = penyitaan.", Tone.BAD)
                    chronicle += ChronicleItem(turn, "Restrukturisasi paksa oleh bank", "Covenant gagal dua kuartal berturut-turut.",
                        "ICR rendah berulang → bank menarik sebagian pokok → kas tersedot.")
                    if (cash < 0.0) {
                        val face = min(normalAR * 0.7, -cash / 0.94)
                        normalAR -= face
                        cash += face * 0.94
                        equity -= face * 0.06
                        lines += ReportLine("Pelunasan paksa dibiayai dengan menjual piutang ${fmt(face)} jt RS (potongan 6%).", Tone.BAD)
                        if (cash < 0.0) gameOverReason = "Kas tidak cukup memenuhi tuntutan pelunasan bank. Perusahaan bangkrut."
                    }
                } else {
                    val fee = totalDebt() * 0.005
                    oneOffOpex += fee
                    loans.firstOrNull { it.id == "bank_b" }?.let { it.floatingSpreadPct = (it.floatingSpreadPct ?: 0.0) + 1.5 }
                    lines += ReportLine("Uji covenant GAGAL: ICR ${fmt2(icr)}x (batas ${fmt2(th)}x), utang bersih/EBITDA ${fmt2(netDebtRatio)}x. Bank memberi waiver dengan biaya ${fmt(fee)} jt RS dan bunga Bank B +1,5%. Gagal lagi kuartal depan = pelunasan paksa.", Tone.BAD)
                    chronicle += ChronicleItem(turn, "Technical default pertama", "ICR ${fmt2(icr)}x di bawah batas ${fmt2(th)}x.",
                        "Laba operasi tertekan + bunga naik → ICR jatuh → bank menekan.")
                }
            } else {
                covenantBreaches = 0
                lines += ReportLine("Uji covenant lulus: ICR ${fmt2(icr)}x (batas ${fmt2(th)}x).", Tone.GOOD)
            }
        }

        // ---- 8. Audit, eksekutif, jurnal ----
        val rAudit = rng(3)
        for (e in execs) {
            if (e.auditDueTurn == turn) {
                e.auditDueTurn = -1
                val guilty = isGuilty(e)
                val found = if (guilty) rAudit.nextDouble() < 0.85 else rAudit.nextDouble() < 0.10
                e.evidence = found
                lines += ReportLine(
                    if (found) "Audit atas ${e.name}: auditor menemukan indikasi pelanggaran (akurasi audit ±85%)."
                    else "Audit atas ${e.name}: tidak ditemukan bukti pelanggaran.",
                    if (found) Tone.WARN else Tone.INFO)
            }
            if (!e.present && e.vacantUntil == turn) {
                e.present = true
                e.replacement = true
                e.stopped = false
                e.evidence = null
                e.loyalty = 0.4 + rAudit.nextDouble() * 0.5
                val (n, i) = when (e.id) {
                    "exec_sales" -> "Dewi Lestari" to "DL"
                    "exec_factory" -> "Bima Santoso" to "BS"
                    "exec_coo" -> "Andi Kurniawan" to "AK"
                    else -> "Maya Sari" to "MS"
                }
                e.name = n; e.initials = i
                lines += ReportLine("$n bergabung sebagai ${e.role}. Ingat: skema insentif tetap membentuk perilaku pengganti.", Tone.INFO)
            }
        }
        for (j in journals) {
            if (j.outcome == null && j.evaluateAtTurn == turn) {
                j.actualCash = cash
                j.outcome = cash >= j.predictedCash
                lines += ReportLine("Evaluasi jurnal \"${j.decisionTitle}\": Anda memprediksi kas ≥ ${fmt(j.predictedCash)} jt RS (yakin ${j.confidencePct}%). Aktual ${fmt(cash)} jt RS → ${if (j.outcome == true) "benar" else "salah"}.",
                    if (j.outcome == true) Tone.GOOD else Tone.WARN)
            }
        }

        if (strikeMonthsLeft > 0) strikeMonthsLeft--
        if (breakdownMonthsLeft > 0) breakdownMonthsLeft--

        // ---- 9. Catat dan maju ----
        lastIncome = mapOf("revenue" to revenue, "cogs" to cogs, "opex" to opex, "maint" to maint, "dep" to dep,
            "baddebt" to writeOff, "ebit" to ebit, "interest" to interest, "intinc" to interestIncome, "tax" to tax, "ni" to ni)
        prevCashFlow = lastCashFlow
        lastCashFlow = mapOf("ni" to ni, "dep" to dep, "wc" to (-dAR - dInv + dPay), "cfo" to cfo,
            "capex" to -capex, "fin" to newDebt - principal, "net" to cash - startCash)
        revenueHist += revenue
        cashHist += cash
        kursHist += kurs

        val summary = mutableListOf<ReportLine>()
        summary += ReportLine("Kas: ${fmt(startCash)} → ${fmt(cash)} jt RS (${signed(cash - startCash)}).",
            if (cash >= startCash) Tone.GOOD else Tone.BAD)
        summary += ReportLine("Penyebab: laba bersih ${signed(ni)}, penyusutan +${fmt(dep)}, perubahan piutang ${signed(-dAR)}, persediaan ${signed(-dInv)}, utang dagang ${signed(dPay)}, investasi ${signed(-capex)}, pendanaan ${signed(newDebt - principal)}.", Tone.NEUTRAL)
        if (ni > 0 && cfo < 0) summary += ReportLine("Jebakan akrual: laba bersih positif tetapi arus kas operasi ${signed(cfo)} jt RS.", Tone.WARN)
        lastReportTitle = "Laporan akhir bulan · ${dateText(turn)}"
        lastReport = summary + lines
        if (gameOverReason != null) {
            lastReport = lastReport + ReportLine(gameOverReason!!, Tone.BAD)
            chronicle += ChronicleItem(turn, "Permainan berakhir", gameOverReason!!, "Lihat post-mortem di laporan bulan terakhir.")
        }

        turn += 1
        ap = apMax
        memos = generateMemos(rng(4))
        news = generateNews(rng(5))
        checkedNews.clear()
    }

    // =====================================================================
    // Memo eksekutif (bisa jujur, bisa menutupi)
    // =====================================================================
    private fun generateMemos(r: Random): List<Memo> {
        val out = mutableListOf<Memo>()
        val sales = exec("exec_sales")
        if (sales.present) out += Memo(sales.id, sales.name, sales.role,
            if (stuffingActive) listOf(
                "Omzet tumbuh sehat. Peritel baru sangat antusias, dan termin 90 hari sudah jadi standar pasar.",
                "Kita merebut rak dari Grup Mahkota bulan ini. Target kuartal hampir pasti tercapai.",
                "Mohon jangan perketat kredit sekarang; momentum penjualan sedang bagus."
            ).random(r) else listOf(
                "Penagihan lebih ketat membuat beberapa peritel kecil menahan pesanan.",
                "Pelanggan yang tersisa membayar tepat waktu, walau omzet sedikit turun."
            ).random(r))
        val fac = exec("exec_factory")
        if (fac.present) out += Memo(fac.id, fac.name, fac.role,
            when {
                breakdownMonthsLeft > 0 -> "Perbaikan lini 3 berjalan. Kami butuh suku cadang Kestrel secepatnya."
                deferralActive -> listOf(
                    "Mesin berjalan baik. Biaya perawatan kuartal ini di bawah anggaran.",
                    "Utilisasi aman. Penggantian bantalan bisa menunggu kuartal depan."
                ).random(r)
                else -> "Anggaran perawatan terserap penuh, tetapi getaran mesin mulai turun."
            })
        val cfo = exec("exec_cfo")
        if (cfo.present) {
            val aged = stuffedBuckets.filter { turn - it.first >= 3 }.sumOf { it.second }
            out += Memo(cfo.id, cfo.name, cfo.role,
                if (aged > 500) "Piutang berumur di atas 90 hari kini ${fmt(aged)} jt RS. Runway ${runwayMonths()} bulan; jarak covenant ${covenantDistancePct().toInt()}%."
                else "Runway ${runwayMonths()} bulan. Jarak ke covenant ${covenantDistancePct().toInt()}%. Saya sarankan menjaga kas.")
        }
        val coo = exec("exec_coo")
        if (coo.present) out += Memo(coo.id, coo.name, coo.role,
            if (kickbackActive) "Harga kapas dari pemasok baru sedikit lebih tinggi, tetapi kualitasnya jauh lebih stabil."
            else listOf("Pemasok kapas Tarsis meminta pembayaran lebih cepat karena El Niño.",
                "Jadwal pengiriman lancar. Proposal pemintalan masih menjadi prioritas saya.").random(r))
        return out.shuffled(r).take(3)
    }

    // =====================================================================
    // Koran pagi: satu topik, lima sudut pandang dengan akurasi berbeda
    // =====================================================================
    private fun generateNews(r: Random): List<NewsPiece> {
        val topics = mutableListOf<String>()
        if (turn < electionTurn) topics += listOf("pemilu", "bunga")
        if (!dualSourcing) topics += "kestrel"
        if (stuffedTotal() > 3000) topics += "piutang"
        if (topics.isEmpty()) topics += "ekonomi"
        val topic = topics[(turn + r.nextInt(topics.size)) % topics.size]
        fun piece(slant: String, h: String, s: String, conf: Int, rumor: Boolean = false) =
            NewsPiece("news_${turn}_$slant", slant, h, s, conf, rumor, false)
        val m = electionTurn - turn
        return when (topic) {
            "pemilu" -> listOf(
                piece("BISNIS", if (electionShock) "Investor asing kurangi obligasi Sundara jelang pemilu" else "Arus modal asing stabil jelang pemilu",
                    "Data kepemilikan obligasi menunjukkan ${if (electionShock) "penurunan" else "kestabilan"} porsi asing. Pemilu tinggal $m bulan.", 1),
                piece("PRO_PEMERINTAH", "Pemerintah jamin Rial Sundara tetap kuat usai pemilu",
                    "Menteri Keuangan menyebut cadangan devisa cukup dan subsidi tetap berlanjut.", 0),
                piece("OPOSISI", "Subsidi jelang pemilu ancam stabilitas rupiah Sundara",
                    "Oposisi menuding belanja populis memperlebar defisit menjelang pemungutan suara.", 1),
                piece("SENSASIONAL", "AWAS! Rial bisa ambruk 40% usai pemilu!",
                    "Sumber anonim di lantai valas menyebut spekulan sudah bersiap.", 0, true),
                piece("INDEPENDEN", if (electionShock) "Analisis: defisit dan utang jangka pendek membuat kurs rawan usai pemilu" else "Analisis: fundamental eksternal Sundara cukup kuat menghadapi pemilu",
                    "Penelusuran neraca pembayaran dan jadwal jatuh tempo utang luar negeri.", 2)
            )
            "bunga" -> listOf(
                piece("BISNIS", if (rateHikeAfterElection) "Ekonom perkirakan bunga naik setelah pemilu" else "Ekonom: bank sentral cenderung menahan bunga",
                    "Survei 12 ekonom; proyeksi bunga acuan akhir tahun.", 1),
                piece("PRO_PEMERINTAH", "Bank sentral: tidak ada rencana menaikkan bunga",
                    "Gubernur menegaskan dukungan pada pertumbuhan.", 0),
                piece("OPOSISI", "Bunga ditahan demi pemilu, rakyat bayar belakangan",
                    "Oposisi menilai bank sentral tunduk pada istana.", 1),
                piece("SENSASIONAL", "Bunga KPR bakal meroket 5%!", "Kabar dari grup percakapan bankir.", 0, true),
                piece("INDEPENDEN", if (rateHikeAfterElection) "Notulen rapat bocor: mayoritas anggota dewan dukung kenaikan bunga pascapemilu" else "Notulen rapat: dewan gubernur terbelah, kenaikan bunga belum disepakati",
                    "Dokumen internal dan wawancara dua mantan pejabat bank sentral.", 2)
            )
            "kestrel" -> listOf(
                piece("BISNIS", "Kestrel perketat kuota ekspor komponen mesin",
                    "Biaya suku cadang mesin tekstil diperkirakan naik 14–22%.", 1),
                piece("PRO_PEMERINTAH", "Pasokan komponen industri dijamin aman",
                    "Kementerian Perindustrian menyebut cadangan cukup 6 bulan.", 0),
                piece("OPOSISI", "Ketergantungan pada Kestrel bukti gagalnya industri hulu",
                    "Pabrik lokal terjepit biaya suku cadang dan pemadaman listrik.", 1),
                piece("SENSASIONAL", "PANIK! Pabrik tekstil berhenti total bulan depan!",
                    "Rumor spekulan Meridia memborong kontrak pengiriman.", 0, true),
                piece("INDEPENDEN", "Jejak rantai pasok: sensor mesin tenun memakai mineral Velmora",
                    "Pemakai pemasok Tier-2 tanpa uji tuntas berisiko denda sanksi Aurelia.", 2)
            )
            "piutang" -> listOf(
                piece("BISNIS", "Peritel kecil menunda pembayaran ke pemasok tekstil",
                    "Survei asosiasi ritel: termin pembayaran makin panjang.", 1),
                piece("PRO_PEMERINTAH", "Penjualan ritel tumbuh, daya beli terjaga", "Data resmi penjualan eceran.", 0),
                piece("OPOSISI", "Gelombang kredit macet mengintai peritel kecil", "Wawancara pedagang pasar.", 1),
                piece("SENSASIONAL", "Benang Sundara di ujung tanduk?!", "Kabar burung dari distributor.", 0, true),
                piece("INDEPENDEN", "Investigasi: obral termin 90 hari di industri tekstil menumpuk piutang berisiko",
                    "Analisis laporan keuangan lima pemasok tekstil.", 2)
            )
            else -> listOf(
                piece("BISNIS", "PMI manufaktur Sundara di sekitar 50", "Pesanan baru stagnan.", 1),
                piece("PRO_PEMERINTAH", "Ekonomi Sundara tumbuh solid", "Pertumbuhan di atas 5%.", 0),
                piece("OPOSISI", "Harga beras masih tinggi", "Daya beli buruh tertekan.", 1),
                piece("SENSASIONAL", "Resesi datang besok!", "Kata seorang influencer keuangan.", 0, true),
                piece("INDEPENDEN", "Data: pertumbuhan kredit melambat, belum ada tanda resesi", "Analisis data perbankan.", 2)
            )
        }
    }

    companion object {
        private val idLocale: Locale = Locale.forLanguageTag("id-ID")
        val MONTHS = listOf("Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli",
            "Agustus", "September", "Oktober", "November", "Desember")

        fun dateText(turn: Int): String = "${MONTHS[(turn - 1) % 12]} ${2030 + (turn - 1) / 12}"
        fun era(turn: Int): Int = when {
            turn <= 96 -> 1
            turn <= 228 -> 2
            turn <= 372 -> 3
            turn <= 492 -> 4
            else -> 5
        }

        fun fmt(x: Double): String = String.format(idLocale, "%,.0f", x)
        fun fmt1(x: Double): String = String.format(idLocale, "%.1f", x)
        fun fmt2(x: Double): String = String.format(idLocale, "%.2f", x)
        fun signed(x: Double): String = (if (x >= 0) "+" else "−") + fmt(kotlin.math.abs(x))
    }
}
