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
    val covenantIcr: Double,
    val bankId: String = "",
    var macClause: Boolean = false,
    var stepUpTurn: Int = -1,
    var stepUpPct: Double = 0.0,
    var dividendLock: Boolean = false,
    var callable: Boolean = false,
    var inceptionPolicy: Double = 0.0,
    var inceptionEbit12: Double = 0.0,
    var macTriggered: Boolean = false,
    var callNoticeTurn: Int = -1,
    var bullet: Boolean = false
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

    /** Skenario awal acak (premis, tanggal, ukuran usaha, utang, kondisi makro) yang ditentukan seed. */
    val sc: Scenario = ScenarioGen.make(seed)

    // ---------- Waktu dan sumber daya ----------
    var turn = sc.turn0; private set
    var ap = 4; private set
    val apMax: Int get() = 7 - execs.count { !it.present }
    var gameOverReason: String? = null; private set

    // ---------- Neraca ----------
    var cash = sc.cash; private set
    var normalAR = sc.ar; private set
    val stuffedBuckets = mutableListOf<Pair<Int, Double>>() // (giliran dibuat, nilai)
    var inventory = sc.inv; private set
    var payables = sc.pay; private set
    var fixedAssets = sc.fa; private set
    var equity = 0.0; private set
    val loans = mutableListOf<Loan>()

    // ---------- Makro ----------
    val world = World()
    val bourse = Bourse()
    val credit = CreditDesk()
    private val policyHist = MutableList(6) { sc.policy }
    /** Kepemilikan saham dalam lot (1 lot = 100 ribu lembar), biaya perolehan total (jt RS). */
    val holdings = LinkedHashMap<String, Int>()
    val costBasis = HashMap<String, Double>()
    var dividendsReceivedTotal = 0.0; private set
    var realizedGainTotal = 0.0; private set
    /** Kas bersih keluar untuk saham sejak giliran lalu (untuk laporan arus kas). */
    var periodInvestOut = 0.0; private set
    var periodInvestIn = 0.0; private set
    private var periodStartCash = sc.cash
    var lastDividendIncome = 0.0; private set
    val commodities = Commodities()

    /** Ekonomi asing: tiap negara punya rumah tangga, pesaing, dan bank sentral sendiri (skala kecil). */
    class ForeignProfile(val id: String, val infl: Double, val policy: Double, val target: Double, val rStar: Double)
    val foreignProfiles = listOf(
        ForeignProfile("aurelia", 2.6, 4.25, 2.0, 1.5),
        ForeignProfile("norvaldia", 5.7, 7.00, 4.5, 2.5),
        ForeignProfile("kestrel", 2.1, 3.10, 2.0, 1.0),
        ForeignProfile("halvenia", 2.15, 3.50, 2.0, 1.0),
        ForeignProfile("meridia", 2.8, 3.25, 2.5, 1.0),
        ForeignProfile("tarsis", 7.6, 6.75, 5.0, 2.0),
        ForeignProfile("velmora", 10.5, 10.00, 6.0, 3.0)
    )
    val foreign: Map<String, World> = foreignProfiles.associate { p ->
        p.id to World(2_000, p.target, p.rStar).also { it.initScenario(p.infl, p.policy) }
    }
    private var fxPassState = 1.0
    private var aurPrice = 1.0
    /** Kurs riil: kurs nominal dibagi kurs paritas daya beli. Hanya penyimpangan dari PPP yang mengubah daya saing. */
    fun fxReal(): Double = kurs / (sc.kurs0 * priceIndex / aurPrice)
    var creditAdj = 0.0; private set
    var hedgeUntilTurn = -1; private set
    var cureUsed = false; private set
    private var hedgeCottonIdx = 1.0
    val hedgeActive: Boolean get() = turn <= hedgeUntilTurn
    /** Indeks biaya bahan efektif: kapas dikunci kontrak berjangka bila ada lindung nilai. */
    fun materialIndexEff(): Double =
        0.78 * (if (hedgeActive) hedgeCottonIdx else commodities.cotton.index) + 0.22 * commodities.oil.index
    /** Rugi fiskal yang boleh dikompensasi 5 tahun ke depan (aturan PPh badan Indonesia). */
    private val lossPool = mutableListOf<Pair<Int, Double>>()
    fun taxLossCarryforward(): Double = lossPool.sumOf { it.second }
    private fun computeTax(taxable: Double): Double {
        lossPool.removeAll { turn - it.first > 60 }
        if (taxable <= 0.0) { if (taxable < 0.0) lossPool += turn to -taxable; return 0.0 }
        var left = taxable
        val it = lossPool.sortedBy { x -> x.first }.toMutableList()
        lossPool.clear()
        for ((t, v) in it) {
            if (left <= 0.0) { lossPool += t to v; continue }
            val use = min(left, v); left -= use
            if (v - use > 0.01) lossPool += t to (v - use)
        }
        return left * 0.22
    }
    var policyRate = sc.policy; private set
    var kurs = sc.kurs0; private set
    var econ = 1.0; private set
    var priceIndex = 1.0; private set
    var costIndex = 1.0; private set
    var inflationAnnual = sc.inflation; private set
    private var volOverride: Double? = null
    private var trendMonths = 0

    // ---------- Rahasia dunia (ditentukan seed) ----------
    private val worldRng = Random(seed)
    val electionShock = worldRng.nextDouble() < 0.6
    val rateHikeAfterElection = worldRng.nextDouble() < 0.6
    val cooKickback = worldRng.nextDouble() < 0.5
    val electionTurn = sc.turn0 + sc.electionOffset

    // ---------- Kebijakan dan status ----------
    var salesScheme = if (sc.salesStartsStuffing) SalesScheme.OMZET else SalesScheme.KAS; private set
    var factoryScheme = FactoryScheme.HEMAT; private set
    var deferredMonths = sc.deferredMonths; private set
    var dualSourcing = false; private set
    var wageAddOn = 0.0; private set
    var lostCustomerShare = 0.0; private set
    var priceAdjPct = 0; private set
    // ---------- Investasi dan ekspansi ----------
    class InvestSpec(val id: String, val title: String, val desc: String, val baseCost: Double, val months: Int, val ap: Int, val capex: Boolean, val benefit: String, val maxCount: Int)
    class InvestProject(val type: String, val startTurn: Int, val months: Int, val totalCost: Double, val capex: Boolean) {
        var spent = 0.0; var done = false
    }
    val projectsInv = mutableListOf<InvestProject>()
    var extraLines = 0; private set
    var demandBoost = 0.0; private set
    var automation = false; private set
    var warehouse = false; private set
    var exportOn = false; private set
    private var periodStartEquity = 0.0
    private var periodCureIn = 0.0
    private var periodTradeFees = 0.0
    var lastDetail: Map<String, Double> = emptyMap(); private set
    var prevDetail: Map<String, Double> = emptyMap(); private set

    // ---------- Kartu kejadian ----------
    class Mod(val key: String, val value: Double, val until: Int)
    class EventRecord(val turn: Int, val title: String, val choice: String, val result: String, val auto: Boolean)
    val mods = mutableListOf<Mod>()
    val openEvents = mutableListOf<GameEvent>()
    val eventHistory = mutableListOf<EventRecord>()
    private val eventLastFired = HashMap<String, Int>()
    private var eventCounter = 0
    var ethicsLoad = 0.0; private set
    var lastEventResult = ""; private set
    var dumpingUntil = -1; private set
    private var creditShock = 0.0
    private var pendingCapex = 0.0
    private var pendingPrepay = 0.0
    private var pendingWriteoff = 0.0
    private var pendingIncome = 0.0
    private var pendingDividend = 0.0
    private var pendingInvLoss = 0.0
    private val scheduledWage = mutableListOf<Pair<Int, Double>>()
    private val reportExtra = mutableListOf<ReportLine>()
    fun modProduct(key: String): Double = mods.filter { it.key == key && turn <= it.until }.fold(1.0) { a, m -> a * m.value }
    fun modSum(key: String): Double = mods.filter { it.key == key && turn <= it.until }.sumOf { it.value }
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
    val ebitHist = mutableListOf<Double>()
    val interestHist = mutableListOf<Double>()
    val cashHist = mutableListOf(sc.cash * 1.55, sc.cash * 1.42, sc.cash * 1.3, sc.cash * 1.22, sc.cash * 1.18, sc.cash)
    val revenueHist = mutableListOf<Double>()
    val journals = mutableListOf<JournalRecord>()
    val chronicle = mutableListOf<ChronicleItem>()
    var lastReport: List<ReportLine> = emptyList(); private set
    var lastReportTitle = ""; private set
    var memos: List<Memo> = emptyList(); private set
    var news: List<NewsPiece> = emptyList(); private set
    var actionCount = 0; private set
    var lastProdFactor = 1.0; private set
    val kursHist = mutableListOf(sc.kurs0 * 0.958, sc.kurs0 * 0.964, sc.kurs0 * 0.974, sc.kurs0 * 0.977, sc.kurs0 * 0.985, sc.kurs0)
    var prevCashFlow: Map<String, Double> = emptyMap(); private set
    var totalCrossChecks = 0; private set

    // Laporan keuangan bulan terakhir (untuk layar Perusahaan)
    var lastIncome: Map<String, Double> = emptyMap(); private set
    var prevIncome: Map<String, Double> = emptyMap(); private set
    var lastCashFlow: Map<String, Double> = emptyMap(); private set
    var prevBalance: Map<String, Double> = emptyMap(); private set

    val execs = listOf(
        ExecState("exec_coo", sc.execNames.getValue("exec_coo").first, sc.execNames.getValue("exec_coo").second, "Direktur Operasi (COO)", (0.35 + worldRng.nextDouble() * 0.6 + sc.execLoyaltyBias).coerceIn(0.05, 0.98)),
        ExecState("exec_sales", sc.execNames.getValue("exec_sales").first, sc.execNames.getValue("exec_sales").second, "Direktur Penjualan", (0.1 + worldRng.nextDouble() * 0.5 + sc.execLoyaltyBias).coerceIn(0.05, 0.98)),
        ExecState("exec_factory", sc.execNames.getValue("exec_factory").first, sc.execNames.getValue("exec_factory").second, "Kepala Pabrik Kalimas", (0.25 + worldRng.nextDouble() * 0.6 + sc.execLoyaltyBias).coerceIn(0.05, 0.98)),
        ExecState("exec_cfo", sc.execNames.getValue("exec_cfo").first, sc.execNames.getValue("exec_cfo").second, "Direktur Keuangan (CFO)", (0.75 + worldRng.nextDouble() * 0.2).coerceIn(0.05, 0.98))
    )

    val spinningPhases: List<Phase>
        get() {
            val p3months = if (spinningDelayed) 7 else 4
            val p3budget = (if (spinningDelayed) 5600.0 * 1.18 else 5600.0) * sc.size
            return listOf(
                Phase("1. Pembebasan lahan dan izin lingkungan", 0, 3, 1680.0 * sc.size),
                Phase("2. Konstruksi sipil dan gardu listrik", 3, 5, 4760.0 * sc.size),
                Phase("3. Impor dan perakitan mesin pintal", 8, p3months, p3budget),
                Phase("4. Uji coba produksi dan pelatihan operator", 8 + p3months, 3, 1960.0 * sc.size)
            )
        }

    init {
        world.initScenario(sc.inflation, sc.policy)
        val bSpread = (sc.bankBRate - 6.5).coerceIn(2.5, 6.0)
        loans += Loan("bank_b", "Bank Niaga Sundara (Bank B)", sc.bankB, sc.policy + bSpread, false, 0.0, 999, sc.turn0 + sc.bankBMaturityOffset, bSpread, sc.bankBCov)
        loans += Loan("rcf", "Fasilitas Modal Kerja Bank B (revolving, plafon ${fmt(sc.rcfLimit)} jt RS)", 0.0, sc.policy + 3.5, false, 0.0, 999, 999, 3.5, 0.0)
        loans += Loan("bank_lama", "Bank Pembangunan (pinjaman lama)", sc.bankLama, sc.bankLamaRate, false, 200.0 * sc.size, 0, 999, null, 0.0)
        // Piutang hasil "obral kredit" 5 bulan terakhir (sudah terjadi sebelum giliran awal)
        if (sc.stuffedPerMonth > 1.0) for (t in (turn - 5) until turn) stuffedBuckets += t to sc.stuffedPerMonth
        equity = totalAssets() - totalLiabilities()
        periodStartEquity = equity
        prevBalance = balanceSnapshot()
        val est = estimateMonth()
        ebitHist += est.getValue("ebit") * 0.97; ebitHist += est.getValue("ebit")
        interestHist += est.getValue("interest"); interestHist += est.getValue("interest")
        chronicle += ChronicleItem(1, "Awal: ${sc.title}", sc.story, "Titik awal simulasi (seed $seed, ${dateText(turn)}).")
        chronicle += ChronicleItem(turn - 8, "Reformasi modal kerja",
            "Penagihan tunggakan dan negosiasi ulang pemasok memperbaiki siklus kas sebelum Anda mengambil alih.",
            "Restrukturisasi piutang → DSO turun → kas operasi tertolong.")
        journals += JournalRecord("jrn_${turn - 8}", "Pengetatan syarat kredit ritel (giliran ${turn - 8})", turn - 8, turn - 2,
            "Menurunkan siklus kas agar tidak kehabisan kas.", "Kas tetap di atas ${fmt(sc.cash * 0.95)} jt RS",
            75, "Peritel pindah ke pesaing yang mengobral kredit.", sc.cash * 0.95, true, sc.cash * 1.18)
        lastReportTitle = "Selamat datang di ${dateText(turn)}"
        val startLines = mutableListOf<ReportLine>()
        startLines += ReportLine("Anda memimpin PT Benang Sundara. Premis: ${sc.title}. ${sc.story}", Tone.INFO)
        startLines += ReportLine("Pinjaman Bank B (${fmt(sc.bankB)} jt RS) jatuh tempo di ${dateText(turn + sc.bankBMaturityOffset)}.", Tone.WARN)
        if (turn % 3 == 0) startLines += ReportLine("Uji covenant Bank B terjadi di akhir giliran ini.", Tone.WARN)
        lastReport = startLines
        val dep = est.getValue("dep")
        lastIncome = est
        prevIncome = est.mapValues { (k, v) -> if (k == "revenue" || k == "cogs") v * 1.02 else v }
        lastCashFlow = mapOf("ni" to est.getValue("ni"), "dep" to dep, "wc" to -sc.size * 600.0, "cfo" to est.getValue("ni") + dep - sc.size * 600.0,
            "capex" to -dep * REPLACEMENT_CAPEX, "fin" to -200.0 * sc.size,
            "net" to est.getValue("ni") + dep - sc.size * 600.0 - dep * REPLACEMENT_CAPEX - 200.0 * sc.size)
        memos = generateMemos(Random(seed + 7))
        news = generateNews(Random(seed + 11))
        // Kartu awal sesuai premis skenario
        sc.initialEvents.forEachIndexed { i, (tpl, off) ->
            val ev = EventDeck.makeEvent(tpl, eventCtx(), Random(seed + 900 + i), "evt_${turn}_${i}_$tpl")
            if (ev != null) {
                ev.deadlineTurn = turn + off
                openEvents += ev; eventLastFired[tpl] = turn
            }
        }
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
    fun portfolioValue(): Double = holdings.entries.sumOf { (id, lots) -> lots * LOT * (bourse.company(id)?.price ?: 0.0) }
    fun totalAssets(): Double = cash + totalAR() + inventory + fixedAssets + portfolioValue()
    fun totalLiabilities(): Double = payables + totalDebt()

    fun balanceSnapshot(): Map<String, Double> = mapOf(
        "cash" to cash, "ar" to totalAR(), "ar_normal" to normalAR, "ar_risky" to stuffedTotal(), "inv" to inventory, "fa" to fixedAssets, "pf" to portfolioValue(),
        "assets" to totalAssets(), "payables" to payables,
        "debt_st" to loans.filter { it.maturityTurn - turn <= 12 }.sumOf { loanBalanceRs(it) },
        "debt_lt" to loans.filter { it.maturityTurn - turn > 12 }.sumOf { loanBalanceRs(it) },
        "liab" to totalLiabilities(), "equity" to equity
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

    /** Tingkat harga paritas impor: 60% dari depresiasi rial menular ke harga jual (kain impor bersaing dalam USD). */
    fun fxPassLevel(): Double = fxPassState

    /** Pendapatan tanpa efek harga diskresioner dan paritas impor (basis HPP). */
    private fun volumeRevenue(revenue: Double): Double = revenue / ((1.0 + priceAdjPct / 100.0) * fxPassLevel())

    fun cogsRatio(): Double {
        // 65% HPP adalah bahan (kapas + energi); sisanya upah langsung dan overhead
        var r = sc.cogsBase * (0.8 + 0.2 * fxReal()) * (0.35 + 0.65 * materialIndexEff()) * modProduct("cogs")
        if (kickbackActive) r += 0.01
        if (dualSourcing) r += 0.012
        r -= spinningBenefit()
        if (automation) r -= 0.015
        return r
    }

    fun monthlyInterest(): Double = loans.sumOf { loanBalanceRs(it) * it.annualRatePct / 100.0 / 12.0 }

    fun covenantThreshold(): Double = loans.filter { it.balance > 0.5 }.maxOfOrNull { it.covenantIcr } ?: 0.0

    /** Perkiraan laba rugi bulan berjalan tanpa kejadian acak (dipakai untuk peringatan dini). */
    fun estimateMonth(): Map<String, Double> {
        val normalRev = baseNormalRevenue(1.0)
        val stuff = if (stuffingActive) normalRev * 0.08 else 0.0
        val revenue = normalRev + stuff
        val cogs = volumeRevenue(revenue) * cogsRatio() * (costIndex / priceIndex).coerceIn(0.8, 1.6)
        val opex = opexBase() + extraOpex() + wageAddOn
        val maint = maintenanceSpend()
        val dep = fixedAssets * 0.0081
        val baddebt = stuffedBuckets.filter { turn - it.first >= 6 }.sumOf { it.second * STUFFED_LOSS }
        val ebit = revenue - cogs - opex - maint - dep - baddebt
        val interest = monthlyInterest()
        val tax = max(0.0, ebit - interest) * 0.22
        return mapOf("revenue" to revenue, "cogs" to cogs, "opex" to opex, "maint" to maint, "dep" to dep,
            "baddebt" to baddebt, "ebit" to ebit, "interest" to interest, "tax" to tax, "ni" to ebit - interest - tax) + costDetail(cogs)
    }

    /** Rincian biaya (tampilan): pembagian HPP dan beban operasional ke pos-pos. Jumlahnya selalu sama dengan total. */
    private fun costDetail(cogs: Double): Map<String, Double> {
        val ob = opexBase()
        return mapOf(
            "cogs_mat" to cogs * 0.50, "cogs_energy" to cogs * 0.15, "cogs_labor" to cogs * 0.25, "cogs_oh" to cogs * 0.10,
            "op_salary" to ob * 0.55 + wageAddOn, "op_admin" to ob * 0.12, "op_mkt" to ob * 0.15,
            "op_util" to ob * 0.08, "op_ins" to ob * 0.05, "op_other" to ob * 0.05, "op_lines" to extraOpex())
    }

    /** Faktor harga: pendapatan per unit naik, volume mengikuti pangsa pasar yang ditentukan harga pesaing. */
    fun priceRevenueFactor(): Double = (1.0 + priceAdjPct / 100.0) * priceVolumeFactor()

    /** Volume relatif akibat harga Anda vs harga pesaing saat ini (pesaing bereaksi, lihat World). */
    fun priceVolumeFactor(): Double = volOverride ?: world.volumeFactor((1.0 + priceAdjPct / 100.0) * fxPassLevel())

    fun playerShare(): Double = world.playerShare((1.0 + priceAdjPct / 100.0) * fxPassLevel())

    private fun baseNormalRevenue(prodFactor: Double): Double {
        var r = sc.baseRevenue * volumeCap() * econ * trendGrowth() * modProduct("rev") * (if (turn <= dumpingUntil) 0.98 else 1.0) * priceIndex * fxPassLevel() * prodFactor * (1.0 - lostCustomerShare) * priceRevenueFactor()
        if (salesScheme == SalesScheme.KAS) r *= 0.97
        if (!exec("exec_sales").present) r *= 0.95
        return r
    }

    /** Kapasitas produksi relatif (1,08 = ada ruang 8% di atas permintaan awal). */
    fun capacityIdx(): Double = 1.08 + 0.12 * extraLines
    fun demandIdx(): Double = 1.0 + demandBoost
    /** Volume yang bisa dijual: permintaan, tetapi tidak melebihi kapasitas pabrik. */
    fun volumeCap(): Double = min(demandIdx(), capacityIdx())
    fun utilization(): Double = demandIdx() / capacityIdx()
    fun headcountProd(): Int = (sc.workers * (1.0 + 0.12 * extraLines) * (if (automation) 0.9 else 1.0)).toInt()
    fun headcountStaff(): Int = (sc.workers * 0.26).toInt()
    private fun extraOpex(): Double = (extraLines * 70.0 + (if (exportOn) 40.0 else 0.0) + (if (warehouse) 25.0 else 0.0)) * sc.size * costIndex

    /** Pertumbuhan riil tren industri (2,5%/tahun; tekstil Indonesia tumbuh 6,4% pada Q2 2026 tetapi dari basis yang lemah). */
    private fun trendGrowth(): Double = Math.pow(1.025, trendMonths / 12.0)

    /** Beban usaha: tumbuh dengan inflasi dan 80% dari pertumbuhan volume (biaya semi-tetap). */
    private fun opexBase(): Double = sc.baseOpex * costIndex * Math.pow(trendGrowth(), 0.95) * (if (automation) 0.945 else 1.0)

    private fun maintenanceSpend(): Double {
        val scale = costIndex * Math.pow(trendGrowth(), 0.95)
        val needed = sc.baseMaint * scale
        return if (deferralActive) sc.baseMaint * 420.0 / 610.0 * scale
        else needed + if (deferredMonths > 0) sc.baseMaint * 120.0 / 610.0 * scale else 0.0
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

    fun dso(): Double = totalAR() / max(1.0, lastIncome["revenue"] ?: sc.baseRevenue) * 30.0
    fun dio(): Double = inventory / max(1.0, lastIncome["cogs"] ?: sc.baseRevenue * 0.68) * 30.0
    fun dpo(): Double = payables / max(1.0, lastIncome["cogs"] ?: sc.baseRevenue * 0.68) * 30.0

    fun brierScore(): Double {
        val ev = journals.filter { it.outcome != null }
        if (ev.isEmpty()) return 0.25
        return ev.sumOf { val p = it.confidencePct / 100.0; val o = if (it.outcome == true) 1.0 else 0.0; (p - o) * (p - o) } / ev.size
    }

    fun pendingDecisions(): List<PendingDecision> {
        val list = mutableListOf<PendingDecision>()
        val assets = totalAssets()
        for (ev in openEvents) {
            val left = ev.deadlineTurn - turn
            val minAp = ev.options.filter { it.id != "z" }.minOfOrNull { it.apCost } ?: 0
            list += PendingDecision(ev.id, ev.title, ev.category + " · " + ev.body.take(90) + if (ev.body.length > 90) "…" else "",
                minAp, if (left <= 0) "tenggat giliran ini" else "tenggat ${left + 1} giliran", if (left <= 0) Tone.BAD else Tone.WARN,
                0, ev.body + " " + ev.warning, true)
        }
        if (termSheetChosen == null && loans.any { it.id == "bank_b" && it.balance > 0.5 } && gameOverReason == null) {
            val bankB = loans.first { it.id == "bank_b" }
            list += PendingDecision("dec_termsheet", "Term sheet dari 2 bank",
                "Refinancing Bank B yang jatuh tempo ${dateText(bankB.maturityTurn)} (pilih salah satu)",
                2, "2 AP", Tone.INFO, (bankB.balance / assets * 100).toInt(),
                "Pinjaman Bank B ${fmt(bankB.balance)} jt RS berbunga mengambang ${fmt1(bankB.annualRatePct)}%. Tanpa refinancing, bank hanya memperpanjang jika covenant terpenuhi.",
                false)
        }
        if (spinningDecision == null && gameOverReason == null) {
            list += PendingDecision("dec_pemintalan", "Proposal pabrik pemintalan",
                "Integrasi hulu benang di Kalimas · ${fmt(14000.0 * sc.size)} jt RS; pembiayaan harus Anda cari sendiri (kas atau kredit bank)",
                3, "3 AP", Tone.INFO, (14000.0 * sc.size / assets * 100).toInt(),
                "Direktur Operasi mengusulkan lini pemintalan sendiri agar tidak bergantung pada benang impor. Proyek sekitar 15 bulan; biaya benang turun setelah beroperasi. Tidak ada pembiayaan otomatis: ajukan kredit di layar Bank (tujuan: Proyek pabrik pemintalan) atau tanggung dari kas. Bank akan menilai kelayakan Anda dan bisa menolak.",
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
            is Action.EventChoice -> doEventChoice(action.eventId, action.optionId)
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
            Action.EquityCure -> {
                if (!cureUsed && covenantBreaches >= 1 && gameOverReason == null && spend(2)) {
                    cureUsed = true
                    cash += sc.equityCure; equity += sc.equityCure; periodCureIn += sc.equityCure
                    covenantBreaches -= 1
                    chronicle += ChronicleItem(turn, "Suntikan ekuitas pemegang saham",
                        "Pemegang saham menyuntik ${fmt(sc.equityCure)} jt RS sebagai equity cure atas pelanggaran covenant.",
                        "Covenant dilanggar → hak equity cure → kas bertambah → pelanggaran dianggap pulih satu tingkat.")
                    true
                } else false
            }
            Action.HedgeCotton -> {
                if (!hedgeActive && spend(1)) {
                    hedgeCottonIdx = commodities.cotton.index * 1.02   // premi kontrak berjangka 2%
                    hedgeUntilTurn = turn + 6
                    oneOffOpex += 60.0
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
            is Action.ApplyCredit -> doApplyCredit(action)
            is Action.ReviewOffer -> {
                val a = credit.app(action.appId)
                if (a != null && a.status == AppStatus.OFFER && !a.reviewed && spend(1)) { a.reviewed = true; oneOffOpex += 50.0; true } else false
            }
            is Action.AcceptOffer -> {
                val a = credit.app(action.appId)
                if (a != null && a.status == AppStatus.OFFER && spend(1)) { a.status = AppStatus.ACCEPTED; a.disburseTurn = turn; a.stageText = "Diterima; dana cair akhir bulan ini."; true } else false
            }
            is Action.DeclineOffer -> {
                val a = credit.app(action.appId)
                if (a != null && a.status == AppStatus.OFFER) { a.status = AppStatus.DECLINED; a.stageText = "Anda menolak penawaran."; credit.gainRelationship(a.bankId, -1.0); true } else false
            }
            is Action.Invest -> doInvest(action.typeId)
            is Action.BuyStock -> doBuy(action.companyId, action.lots)
            is Action.SellStock -> doSell(action.companyId, action.lots)
            is Action.AdjustPrice -> {
                val target = (priceAdjPct + action.deltaPct).coerceIn(-15, 15)
                if (target != priceAdjPct && spend(1)) { priceAdjPct = target; true } else false
            }
        }
        if (ok) actionCount++
        return ok
    }

    private fun doApplyCredit(a: Action.ApplyCredit): Boolean {
        val purpose = Purpose.entries.firstOrNull { it.name == a.purpose } ?: return false
        if (a.amount < 500 || a.amount > 60000) return false
        if (credit.bank(a.bankId) == null) return false
        if (ap < 2) return false
        val app = credit.submit(a.bankId, a.amount.toDouble(), a.tenor, purpose, a.floating, a.bullet, turn) ?: return false
        ap -= 2
        oneOffOpex += 30.0
        return true
    }

    fun creditMetrics(amount: Double = 0.0): CreditMetrics {
        val e12 = ebitHist.takeLast(12); val i12 = interestHist.takeLast(12)
        val ebit12 = e12.sum() * 12.0 / e12.size
        val int12 = i12.sum() * 12.0 / i12.size
        val dep12 = fixedAssets * 0.0081 * 12.0
        var due = payables
        for (l in loans) if (l.balance > 0.5) {
            val bal = loanBalanceRs(l)
            due += if (l.maturityTurn <= turn + 12) bal else min(bal, 12.0 * l.amortPerMonth * (if (l.currencyAud) kurs else 1.0))
        }
        val coll = 0.55 * fixedAssets * (if (deferredMonths >= 3) 0.9 else 1.0) + 0.70 * normalAR + 0.50 * inventory + 0.50 * portfolioValue()
        val kd = if (kursHist.size >= 6) (kurs / kursHist[kursHist.size - 6] - 1.0) * 100.0 else 0.0
        val pd = policyRate - policyHist.first()
        return CreditMetrics(ebit12, ebit12 + dep12, int12, totalDebt(), cash, cash + totalAR() + inventory, due,
            max(0.0, coll - totalDebt()), covenantBreaches, dso(), world.unemployment, policyRate, pd, kd, deferredMonths >= 3,
            max(1000.0, (lastIncome["cogs"] ?: sc.baseRevenue * 0.68) + (lastIncome["opex"] ?: sc.baseOpex)),
            loans.filter { it.balance > 0.5 }.sumOf { 12.0 * it.amortPerMonth * (if (it.currencyAud) kurs else 1.0) },
            fixedAssets * 0.0081 * 12.0 * REPLACEMENT_CAPEX)
    }

    fun investSpec(id: String): InvestSpec? = INVEST.firstOrNull { it.id == id }
    fun investCost(spec: InvestSpec): Double =
        spec.baseCost * sc.size * costIndex * (if (spec.id == "line") 1.0 + 0.25 * extraLines else 1.0)
    fun investCount(id: String): Int = when (id) {
        "line" -> extraLines
        "automation" -> if (automation) 1 else 0
        "warehouse" -> if (warehouse) 1 else 0
        "export" -> if (exportOn) 1 else 0
        "rnd" -> projectsInv.count { it.type == "rnd" && it.done }
        "brand" -> projectsInv.count { it.type == "brand" && it.done && turn - it.startTurn < it.months + 12 }
        else -> 0
    }
    /** Null bila boleh dijalankan; selain itu alasan penolakan. */
    fun investBlock(id: String): String? {
        val spec = investSpec(id) ?: return "Proyek tidak dikenal."
        if (projectsInv.any { it.type == id && !it.done }) return "Proyek ini sedang berjalan."
        if (investCount(id) >= spec.maxCount) return "Sudah tercapai batas (${spec.maxCount})."
        if (ap < spec.ap) return "AP tidak cukup (butuh ${spec.ap})."
        val first = investCost(spec) / spec.months
        if (cash - first < 1500.0) return "Kas tidak cukup untuk bulan pertama (${fmt(first)} jt RS). Ajukan kredit di tab Bank dulu."
        return null
    }

    private fun doInvest(id: String): Boolean {
        val spec = investSpec(id) ?: return false
        if (investBlock(id) != null) return false
        spend(spec.ap)
        projectsInv += InvestProject(id, turn, spec.months, investCost(spec), spec.capex)
        chronicle += ChronicleItem(turn, "Investasi dimulai: ${spec.title}", "Anggaran ${fmt(investCost(spec))} jt RS selama ${spec.months} bulan.",
            "Keputusan investasi → kas keluar bertahap → manfaat baru terasa setelah selesai.", actionCount)
        return true
    }

    private fun finishInvest(p: InvestProject, lines: MutableList<ReportLine>) {
        p.done = true
        val spec = investSpec(p.type)!!
        when (p.type) {
            "line" -> extraLines++
            "automation" -> automation = true
            "warehouse" -> warehouse = true
            "export" -> { exportOn = true; demandBoost += 0.06 }
            "rnd" -> demandBoost += 0.04
            "brand" -> mods += Mod("rev", 1.03, turn + 11)
        }
        lines += ReportLine("Investasi selesai: ${spec.title}. ${spec.benefit}.", Tone.GOOD)
        chronicle += ChronicleItem(turn, "Investasi selesai: ${spec.title}", spec.benefit, "Belanja modal atau program → manfaat operasional.")
    }

    private fun doBuy(id: String, lots: Int): Boolean {
        val c = bourse.company(id) ?: return false
        if (lots <= 0 || !c.listed) return false
        val gross = lots * LOT * c.price
        val fee = gross * BROKER_FEE
        // jaga kas minimum agar tidak langsung krisis kas
        if (cash - gross - fee < 1500.0) return false
        if (gross > 0.5 * totalAssets()) return false
        if (!spend(1)) return false
        cash -= gross + fee
        equity -= fee
        holdings[id] = (holdings[id] ?: 0) + lots
        costBasis[id] = (costBasis[id] ?: 0.0) + gross
        periodInvestOut += gross + fee
        periodTradeFees += fee
        return true
    }

    private fun doSell(id: String, lots: Int): Boolean {
        val c = bourse.company(id) ?: return false
        val have = holdings[id] ?: 0
        if (lots <= 0 || lots > have || !c.listed) return false
        if (!spend(1)) return false
        val gross = lots * LOT * c.price
        val fee = gross * (BROKER_FEE + SELL_TAX)
        val basis = (costBasis[id] ?: 0.0) * lots / have
        cash += gross - fee
        // selisih harga dengan nilai pasar akhir bulan sudah masuk ekuitas lewat mark-to-market; biaya jual dibebankan
        equity -= fee
        realizedGainTotal += gross - basis
        periodInvestIn += gross - fee
        periodTradeFees += fee
        if (lots == have) { holdings.remove(id); costBasis.remove(id) }
        else { holdings[id] = have - lots; costBasis[id] = (costBasis[id] ?: 0.0) - basis }
        return true
    }

    /** Jumlah kredit refinancing bank domestik (sedikit di atas saldo Bank B untuk menutup biaya). */
    fun termSheetAmount(): Double = Math.round((loans.firstOrNull { it.id == "bank_b" }?.balance ?: sc.bankB) * 1.04 / 100.0) * 100.0
    fun termSheetFixedRate(): Double = Math.round((policyRate + 4.25) * 4.0) / 4.0
    fun termSheetAudAmountRs(): Double = (loans.firstOrNull { it.id == "bank_b" }?.balance ?: sc.bankB)

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

    // =====================================================================
    // Kartu kejadian
    // =====================================================================
    fun eventCtx(): EventCtx = EventCtx(
        turn, sc.turn0, sc.size, costIndex, inflationAnnual, world.unemployment, policyRate, policyRate - policyHist.first(),
        if (kursHist.size >= 6) (kurs / kursHist[kursHist.size - 6] - 1.0) * 100.0 else 0.0, econ, cash,
        if (lastIncome.isEmpty()) 62.0 else dso(), if (totalAR() > 0) normalAR / totalAR() else 1.0,
        deferredMonths, dualSourcing, strikeMonthsLeft > 0, covenantBreaches, ethicsLoad, turn <= dumpingUntil,
        commodities.cotton.index, execs.filter { it.present }.map { it.id to it.name }, sc.workers,
        loans.any { it.balance > 0.5 && it.floatingSpreadPct != null }, 0.0)

    private fun doEventChoice(eventId: String, optionId: String): Boolean {
        val ev = openEvents.firstOrNull { it.id == eventId } ?: return false
        val opt = ev.options.firstOrNull { it.id == optionId && it.id != "z" } ?: return false
        if (!spend(opt.apCost)) return false
        lastEventResult = resolveEvent(ev, opt, false)
        return true
    }

    private fun resolveEvent(ev: GameEvent, opt: EventOption, auto: Boolean): String {
        val r = Random(seed * 31L + ev.id.hashCode() * 17L + opt.id.hashCode() * 131L + ev.raisedTurn)
        var x = r.nextDouble()
        var chosen = opt.outcomes.last()
        for (o in opt.outcomes) { if (x < o.p) { chosen = o; break }; x -= o.p }
        chosen.effects.forEach { applyEffect(it) }
        openEvents.remove(ev)
        eventHistory += EventRecord(turn, ev.title, opt.title, chosen.text, auto)
        chronicle += ChronicleItem(turn, ev.title,
            (if (auto) "Tenggat lewat tanpa keputusan. " else "Keputusan Anda: ${opt.title}. ") + chosen.text,
            "${ev.category} → ${if (auto) "diabaikan" else "pilihan Anda"} → akibat bagi kas, biaya, atau hubungan.", actionCount)
        return chosen.text
    }

    private fun applyEffect(f: Effect) {
        when (f.kind) {
            "wage" -> if (f.s == "delay6") scheduledWage += (turn + 6) to f.a else wageAddOn += f.a
            "strike" -> strikeMonthsLeft = max(strikeMonthsLeft, f.b)
            "unskilled" -> unskilledUntil = turn + f.b
            "cost", "fine" -> oneOffOpex += f.a
            "capex" -> pendingCapex += f.a
            "prepay" -> pendingPrepay += f.a
            "writeoff" -> pendingWriteoff += f.a
            "income" -> pendingIncome += f.a
            "invloss" -> pendingInvLoss += f.a
            "dividend" -> pendingDividend += f.a
            "ethics" -> ethicsLoad += f.a
            "lostcust" -> lostCustomerShare = (lostCustomerShare + f.a).coerceAtMost(0.6)
            "loyalty" -> execs.firstOrNull { it.id == f.s }?.let { it.loyalty = (it.loyalty + f.a).coerceIn(0.0, 1.0) }
            "exec_quit" -> execs.firstOrNull { it.id == f.s && it.present }?.let { vacate(it, f.b); it.lastOutcome = "${it.name} pergi." }
            "mod" -> mods += Mod(f.s, f.a, turn + f.b - 1)
            "dual" -> dualSourcing = true
            "fix_rates" -> loans.forEach { if (it.floatingSpreadPct != null && it.balance > 0.5) it.floatingSpreadPct = null }
            "credit_shock" -> creditShock += f.a
            "start_dumping" -> dumpingUntil = max(dumpingUntil, turn + f.a.toInt())
            "end_dumping" -> dumpingUntil = -1
            "deferred" -> deferredMonths = max(0, deferredMonths + f.a.toInt())
        }
    }

    private fun drawEvents(lines: MutableList<ReportLine>) {
        val r = rng(60)
        if (gameOverReason != null) return
        val d = EventDeck.draw(eventCtx(), r, openEvents.map { it.templateId }.toSet(), eventLastFired, eventCounter++)
        d.event?.let { ev ->
            if (openEvents.size < 3) {
                openEvents += ev; eventLastFired[ev.templateId] = turn
                lines += ReportLine("Kartu keputusan baru: ${ev.title}.", Tone.WARN)
            }
        }
        d.notice?.let { n ->
            eventLastFired[d.noticeId ?: ""] = turn
            n.effects.forEach { applyEffect(it) }
            lines += ReportLine(n.text, n.tone)
        }
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
        val startCash = periodStartCash
        val startAR = totalAR()
        val startInv = inventory
        val startPay = payables
        prevBalance = balanceSnapshot()
        prevIncome = lastIncome
        prevDetail = lastDetail
        var rcfFlow = 0.0; var factorProceeds = 0.0; var factorDiscount = 0.0; var forcedPay = 0.0
        var mtm = 0.0; var divInCash = 0.0; var eqFx = 0.0

        // ---- 1. Makro: dunia berbasis agen (rumah tangga, pesaing, bank sentral) ----
        val rMacro = rng(1)
        val rWorld = rng(8)
        val kursBefore = kurs
        // Ekonomi asing berjalan dulu; kurs RS/A$ mengikuti selisih inflasi (PPP) + premi risiko negara berkembang
        foreignProfiles.forEachIndexed { i, p ->
            foreign.getValue(p.id).step(rng(20 + i), 1.0, 1.0, 1.0, 0.0, 1.0)
        }
        aurPrice *= 1.0 + foreign.getValue("aurelia").inflation / 1200.0
        val infDiff = inflationAnnual - foreign.getValue("aurelia").inflation
        val driftMonthly = infDiff / 1200.0 + 0.0012
        kurs *= 1.0 + driftMonthly + gaussian(rMacro) * 0.008
        var demandShock = 0.0
        if (turn == electionTurn) {
            lines += ReportLine("Pemilu Sundara berlangsung bulan ini.", Tone.INFO)
            if (electionShock) {
                kurs *= 1.12
                demandShock = -0.03
                lines += ReportLine("Rial Sundara anjlok sekitar 12% setelah hasil pemilu; inflasi impor naik dan bank sentral akan bereaksi.", Tone.BAD)
                chronicle += ChronicleItem(turn, "Guncangan kurs pascapemilu",
                    "Rial Sundara melemah tajam terhadap A$.", "Subsidi jelang pemilu → defisit → investor keluar → kurs anjlok → bunga naik.")
                if (loans.any { it.id == "aurelia" && it.balance > 0.0 }) {
                    macDemandTurn = turn + 3
                    loans.first { it.id == "aurelia" }.annualRatePct = 9.2
                    lines += ReportLine("Aurelia CitiTrust memakai klausul MAC: bunga naik ke 9,2% dan 30% pokok harus dilunasi dalam 3 giliran.", Tone.BAD)
                }
            } else {
                lines += ReportLine("Pasar tenang setelah pemilu; kurs relatif stabil.", Tone.GOOD)
            }
        }
        // Harga impor menyesuaikan bertahap (puncak di kuartal kedua); 60% dari depresiasi menular ke harga jual
        commodities.step(rng(30), world.gap)
        commodities.lastEvents.forEach { lines += ReportLine(it, Tone.WARN) }
        if (hedgeUntilTurn == turn) lines += ReportLine("Kontrak berjangka kapas berakhir; HPP kembali mengikuti harga pasar.", Tone.INFO)
        // Harga jual dan biaya pesaing mengikuti harga PASAR; hanya HPP Anda yang bisa dikunci kontrak berjangka
        val marketMat = commodities.materialIndex()
        val matCost = 0.35 + 0.65 * marketMat
        val fxTarget = (1.0 + 0.6 * ((0.8 + 0.2 * fxReal()) - 1.0)) * (1.0 + 0.65 * 0.7 * (marketMat - 1.0))
        fxPassState += 0.35 * (fxTarget - fxPassState)
        val costPressure = (0.8 + 0.2 * fxReal()) * (costIndex / priceIndex) * matCost
        val rpPlayer = (1.0 + priceAdjPct / 100.0) * fxPassLevel()
        volOverride = world.volumeFactor(rpPlayer)          // pembeli melihat harga pesaing bulan lalu
        val prevPolicy = policyRate
        val out = world.step(rWorld, rpPlayer, costPressure, fxPassLevel(), kurs / kursBefore - 1.0, kurs, demandShock)
        econ = out.demand
        run {
            val pvBefore = portfolioValue()
            val ev = bourse.step(rng(40), Bourse.Ctx(
                turn, econ, trendGrowth(), priceIndex, costPressure,
                commodities.cotton.index, commodities.oil.index, commodities.steel.index,
                fxReal(), out.policyRate, world.gap, rpPlayer,
                { id -> world.rivalShareOf(id, rpPlayer) },
                { id -> world.rivals.first { it.id == id }.price },
                { id -> world.rivals.first { it.id == id }.active },
                { id -> world.rivals.first { it.id == id }.share0 }))
            // dividen diterima & penilaian ulang portofolio
            var divIn = 0.0
            for ((id, lots) in holdings) {
                val c = bourse.company(id) ?: continue
                val m = c.monthly.lastOrNull() ?: continue
                if (m.turn == turn && m.dividend > 0 && c.sharesM > 0) divIn += lots * LOT * (m.dividend / c.sharesM)
            }
            // saham emiten pailit tidak bernilai lagi
            val delisted = holdings.keys.filter { bourse.company(it)?.listed == false }
            for (id in delisted) {
                lines += ReportLine("Saham $id dihentikan perdagangannya karena emiten pailit; nilai investasi Anda nyaris hilang.", Tone.BAD)
            }
            val pvAfter = portfolioValue()
            equity += (pvAfter - pvBefore) + divIn
            mtm = pvAfter - pvBefore; divInCash = divIn
            cash += divIn
            dividendsReceivedTotal += divIn
            lastDividendIncome = divIn
            if (divIn > 0.0) lines += ReportLine("Dividen saham diterima ${fmt(divIn)} jt RS.", Tone.GOOD)
            if (holdings.isNotEmpty() && Math.abs(pvAfter - pvBefore) > 0.0) {
                val d = pvAfter - pvBefore
                lines += ReportLine("Portofolio saham ${if (d >= 0) "naik" else "turun"} ${fmt(Math.abs(d))} jt RS bulan ini (nilai ${fmt(pvAfter)} jt RS, belum direalisasi).", if (d >= 0) Tone.GOOD else Tone.WARN)
            }
            ev.forEach { lines += ReportLine(it, Tone.INFO) }
        }
        inflationAnnual = out.inflationAnnual
        policyRate = out.policyRate
        policyHist += policyRate; if (policyHist.size > 6) policyHist.removeAt(0)
        trendMonths++
        val monthlyInfl = (inflationAnnual / 12.0) / 100.0
        priceIndex *= 1.0 + monthlyInfl
        costIndex *= 1.0 + monthlyInfl
        if (policyRate != prevPolicy) {
            lines += ReportLine(
                "Bank sentral ${if (policyRate > prevPolicy) "menaikkan" else "menurunkan"} suku bunga acuan ke ${fmt2(policyRate)}% (inflasi ${fmt2(inflationAnnual)}%, pengangguran ${fmt2(out.unemployment)}%).",
                if (policyRate > prevPolicy) Tone.WARN else Tone.INFO)
        }
        out.events.forEach { lines += ReportLine(it, Tone.WARN) }
        // Kondisi kredit: saat pengangguran naik, kredit macet bank naik dan spread melebar
        creditShock *= 0.96
        creditAdj = (0.4 * (out.unemployment - 5.0)).coerceIn(-0.5, 2.0) + creditShock
        loans.forEach { l -> l.floatingSpreadPct?.let { l.annualRatePct = policyRate + it + creditAdj } }

        // ---- 2. Kejadian operasional ----
        val rOps = rng(2)
        scheduledWage.filter { it.first <= turn }.forEach { wageAddOn += it.second; lines += ReportLine("Kenaikan upah yang ditangguhkan mulai berlaku (+${fmt(it.second)} jt RS per bulan).", Tone.WARN) }
        scheduledWage.removeAll { it.first <= turn }
        for (ev in openEvents.toList()) {
            if (turn >= ev.deadlineTurn) {
                val o = ev.options.first { it.id == ev.defaultOptionId }
                val txt = resolveEvent(ev, o, true)
                lines += ReportLine("Tenggat \"${ev.title}\" lewat tanpa keputusan: $txt", Tone.BAD)
            }
        }
        // Risiko skandal etika: makin banyak jalan pintas, makin besar peluang terungkap
        ethicsLoad *= 0.985
        var scandalCost = 0.0
        if (ethicsLoad >= 2.0 && rOps.nextDouble() < 0.025 * ethicsLoad) {
            scandalCost = 900.0 * sc.size * costIndex * (0.6 + 0.4 * ethicsLoad)
            lostCustomerShare = (lostCustomerShare + 0.03).coerceAtMost(0.6)
            lines += ReportLine("Skandal: praktik jalan pintas Anda terungkap. Denda dan biaya hukum ${fmt(scandalCost)} jt RS, pelanggan ragu.", Tone.BAD)
            chronicle += ChronicleItem(turn, "Skandal etika terungkap", "Suap, pemufakatan harga, atau pelanggaran lain yang pernah dilakukan akhirnya muncul ke publik.",
                "Jalan pintas → catatan menumpuk → terungkap → denda dan reputasi rusak.", actionCount)
            ethicsLoad *= 0.4
        }
        var breakdownCost = 0.0
        if (breakdownMonthsLeft == 0) {
            var p = 0.015 * (1.0 + 0.5 * deferredMonths)
            if (!exec("exec_factory").present) p *= 1.5
            if (turn <= unskilledUntil) p *= 1.4
            if (rOps.nextDouble() < p) {
                breakdownMonthsLeft = 2
                breakdownCost = 1800.0 * sc.size * costIndex
                lines += ReportLine("Mesin tenun lini 3 rusak berat: produksi turun 50% selama 2 bulan, perbaikan ${fmt(breakdownCost)} jt RS.", Tone.BAD)
                chronicle += ChronicleItem(turn, "Kerusakan mesin besar",
                    "Perawatan yang tertunda $deferredMonths bulan berujung kerusakan.",
                    "KPI penghematan → perawatan ditunda → keausan → mesin rusak.")
            }
        }
        holdupThisTurn = false
        if (!dualSourcing && rOps.nextDouble() < (if (warehouse) 0.05 else 0.10)) {
            holdupThisTurn = true
            lines += ReportLine("Pemasok Kestrel menahan kiriman sensor dengan alasan \"mesin rusak\"; produksi turun 30% bulan ini.", Tone.BAD)
        }
        var sanctionFine = 0.0
        if (!dualSourcing && !sanctionHit && turn >= sc.turn0 + sc.sanctionAfter && rOps.nextDouble() < 0.05) {
            sanctionHit = true
            sanctionFine = 600.0 * sc.size
            dualSourcing = true
            lines += ReportLine("Aurelia mendenda perusahaan Anda 600 jt RS karena pemasok Tier-2 memakai mineral Velmora. Dual sourcing kini diwajibkan.", Tone.BAD)
            chronicle += ChronicleItem(turn, "Denda sanksi berlapis",
                "Pemasok Kestrel ternyata memakai mineral Velmora.", "Sanksi Aurelia → pemasok Tier-2 → denda ke pemain.")
        }

        var prodFactor = modProduct("prod")
        if (strikeMonthsLeft > 0) prodFactor *= 0.6
        if (breakdownMonthsLeft > 0) prodFactor *= 0.6
        if (holdupThisTurn) prodFactor *= 0.7
        lastProdFactor = prodFactor

        // ---- 3. Laba rugi ----
        val normalRev = baseNormalRevenue(prodFactor)
        val stuffRev = if (stuffingActive) normalRev * 0.08 else 0.0
        val revenue = normalRev + stuffRev
        val cogs = volumeRevenue(revenue) * cogsRatio() * (costIndex / priceIndex).coerceIn(0.8, 1.6)
        val otherLoss = pendingInvLoss * inventory
        if (pendingInvLoss > 0.0) { inventory -= otherLoss; pendingInvLoss = 0.0 }
        var projExp = 0.0
        for (p in projectsInv) if (!p.done && !p.capex) { val part = p.totalCost / p.months; projExp += part; p.spent += part }
        val oneOffNow = oneOffOpex + sanctionFine + scandalCost
        val incomeNow = pendingIncome
        val detailNow = costDetail(cogs)
        val opex = opexBase() + extraOpex() + wageAddOn + oneOffOpex + sanctionFine + scandalCost + otherLoss - pendingIncome + projExp
        oneOffOpex = 0.0; pendingIncome = 0.0
        val maint = maintenanceSpend() + breakdownCost + if (holdupThisTurn) 200.0 else 0.0
        if (deferralActive) deferredMonths += 1 else if (deferredMonths > 0) deferredMonths -= 1
        val dep = fixedAssets * 0.0081
        // Piutang obral kredit yang berumur 6 bulan: 45% macet, 55% tertagih
        var writeOff = 0.0
        var stuffedCollected = 0.0
        val keep = mutableListOf<Pair<Int, Double>>()
        for ((t, v) in stuffedBuckets) {
            if (turn - t >= 6) { writeOff += v * STUFFED_LOSS; stuffedCollected += v * (1.0 - STUFFED_LOSS) } else keep += t to v
        }
        stuffedBuckets.clear(); stuffedBuckets += keep
        if (stuffRev > 0.0) stuffedBuckets += turn to stuffRev
        if (writeOff > 0.0) {
            lines += ReportLine("Piutang ${fmt(writeOff)} jt RS dari peritel rapuh (termin 90 hari, 6 bulan lalu) dihapusbukukan sebagai kredit macet.", Tone.BAD)
        }
        if (pendingWriteoff > 0.0) {
            val w = min(pendingWriteoff, normalAR * 0.9)
            normalAR -= w; writeOff += w; pendingWriteoff = 0.0
            lines += ReportLine("Piutang pelanggan yang gagal bayar ${fmt(w)} jt RS dihapusbukukan.", Tone.BAD)
        }
        val ebit = revenue - cogs - opex - maint - dep - writeOff
        val interest = monthlyInterest()
        val interestIncome = max(0.0, cash) * max(0.0, policyRate - 3.0) / 1200.0
        val taxable = ebit - interest + interestIncome
        val tax = computeTax(taxable)
        val ni = ebit - interest + interestIncome - tax
        equity += ni

        volOverride = null

        // ---- 4. Modal kerja ----
        val dsoNormal = (if (salesScheme == SalesScheme.OMZET) 62.0 else 50.0) + modSum("dso")
        val arTarget = normalRev * dsoNormal / 30.0
        val newNormalAR = normalAR + 0.5 * (arTarget - normalAR)
        val invTarget = cogs * 53.0 / 30.0 * (if (holdupThisTurn) 1.15 else 1.0) * (if (warehouse) 0.92 else 1.0)
        val newInv = inventory + 0.5 * (invTarget - inventory)
        val payTarget = cogs * 35.0 / 30.0
        val newPay = payables + 0.5 * (payTarget - payables)
        normalAR = newNormalAR
        inventory = newInv + pendingPrepay
        pendingPrepay = 0.0
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
        }
        val capSpin = capex
        val replacement = dep * REPLACEMENT_CAPEX
        var expCapex = 0.0
        for (p in projectsInv) if (!p.done && p.capex) { val part = p.totalCost / p.months; expCapex += part; p.spent += part }
        for (p in projectsInv.toList()) if (!p.done && p.spent >= p.totalCost - 0.01) finishInvest(p, lines)
        val evCapex = pendingCapex
        capex += replacement + pendingCapex + expCapex
        pendingCapex = 0.0
        fixedAssets += capex - dep

        // Pencairan refinancing
        if (termSheetDisburseTurn == turn && termSheetChosen != null) {
            val bankB = loans.firstOrNull { it.id == "bank_b" }
            val payoff = bankB?.balance ?: 0.0
            if (termSheetChosen == "cnt_ts_1") {
                val ts1 = termSheetAmount(); val ts1Rate = termSheetFixedRate()
                loans += Loan("bank_pembangunan", "Bank Pembangunan Sundara", ts1, ts1Rate, false, ts1 / 48.0, turn + 1, turn + 48, null, 1.5)
                newDebt += ts1
                lines += ReportLine("Kredit Bank Pembangunan ${fmt(ts1)} jt RS cair; Bank B dilunasi. Cicilan pokok ±${fmt(ts1 / 48.0)} jt RS per bulan mulai bulan depan.", Tone.GOOD)
            } else {
                val ts2 = termSheetAudAmountRs()
                loans += Loan("aurelia", "Aurelia Global CitiTrust", ts2 / kurs, 5.2, true, 0.0, 999, turn + 36, null, 1.75)
                newDebt += ts2
                lines += ReportLine("Pinjaman Aurelia ${fmt2(ts2 / kurs * 1000)} ribu A$ cair; Bank B dilunasi. Utang kini dalam A$.", Tone.INFO)
                if (turn >= electionTurn && electionShock && macDemandTurn < 0) { /* guncangan sudah lewat */ }
            }
            if (bankB != null) { principal += payoff; bankB.balance = 0.0 }
        }
        // Meja kredit: keputusan bank, pencairan, dan efek klausul
        run {
            credit.process(turn, policyRate, { _, amt -> creditMetrics(amt) }, rng(50)).forEach { lines += ReportLine(it.first, it.second) }
            for (a in credit.applications) {
                if (a.status != AppStatus.ACCEPTED || a.disburseTurn > turn) continue
                val b = credit.bank(a.bankId) ?: continue
                val o = a.offer ?: continue
                val aud = b.foreign
                val bal = if (aud) o.amount / kurs else o.amount
                val amort = if (o.bullet) 0.0 else bal / max(1, o.tenor - o.graceMonths)
                val e12 = ebitHist.takeLast(12).let { it.sum() * 12.0 / it.size }
                loans += Loan("cr_${a.id}", "${b.name} (${a.purpose.label})", bal, o.ratePct, aud, amort, turn + 1 + o.graceMonths,
                    turn + o.tenor, o.spreadPct, o.covenantIcr, b.id, o.macClause, if (o.stepUpPct > 0) turn + 12 else -1, o.stepUpPct,
                    o.dividendLock, o.callable, policyRate, e12, false, -1, o.bullet)
                newDebt += o.amount
                oneOffOpex += o.amount * o.feePct / 100.0
                credit.gainRelationship(b.id, 3.0)
                a.status = AppStatus.DISBURSED
                lines += ReportLine("Kredit ${b.name} ${fmt(o.amount)} jt RS cair (${a.purpose.label}). Biaya provisi ${fmt(o.amount * o.feePct / 100.0)} jt RS.", Tone.GOOD)
                chronicle += ChronicleItem(turn, "Kredit baru dari ${b.name}", "${fmt(o.amount)} jt RS, tenor ${o.tenor} bulan, bunga ${fmt2(o.ratePct)}%.",
                    "Pengajuan → analisis bank → penawaran → persetujuan Anda → pencairan.")
            }
            for (l in loans) {
                if (l.balance <= 0.5 || !l.id.startsWith("cr_")) continue
                val rsFactor = if (l.currencyAud) kurs else 1.0
                if (l.stepUpTurn == turn) {
                    if (l.floatingSpreadPct != null) l.floatingSpreadPct = l.floatingSpreadPct!! + l.stepUpPct else l.annualRatePct += l.stepUpPct
                    lines += ReportLine("Bunga promosi ${l.lender} berakhir: bunga naik ${fmt2(l.stepUpPct)} poin.", Tone.WARN)
                }
                if (l.macClause && !l.macTriggered) {
                    val ebit3 = ebitHist.takeLast(3).sum() * 4.0
                    if (policyRate - l.inceptionPolicy >= 2.0 || (turn >= l.amortStartTurn - 6 && ebit3 < 0.6 * l.inceptionEbit12)) {
                        l.macTriggered = true
                        val pay = l.balance * (if (l.currencyAud) 0.30 else 0.20)
                        l.balance -= pay; principal += pay * rsFactor
                        lines += ReportLine("${l.lender} menggunakan klausul MAC: pelunasan paksa ${fmt(pay * rsFactor)} jt RS karena suku bunga atau kinerja memburuk.", Tone.BAD)
                    }
                }
                if (l.callable) {
                    val stress = world.unemployment > 7.5 || policyRate - l.inceptionPolicy >= 2.5
                    if (stress && l.callNoticeTurn < 0 && rng(51).nextDouble() < 0.35) {
                        l.callNoticeTurn = turn + 2
                        lines += ReportLine("${l.lender} memberi pemberitahuan: seluruh pinjaman akan ditarik dalam 2 giliran.", Tone.BAD)
                    } else if (l.callNoticeTurn == turn) {
                        val pay = l.balance; l.balance = 0.0; principal += pay * rsFactor
                        lines += ReportLine("${l.lender} menarik seluruh pinjaman: ${fmt(pay * rsFactor)} jt RS harus dibayar sekarang.", Tone.BAD)
                    }
                }
                if (l.bullet && l.maturityTurn == turn && l.balance > 0.5) {
                    val pay = l.balance; l.balance = 0.0; principal += pay * rsFactor
                    lines += ReportLine("Pokok bullet ${l.lender} jatuh tempo dan dilunasi: ${fmt(pay * rsFactor)} jt RS.", Tone.WARN)
                }
            }
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
                b.floatingSpreadPct = (b.floatingSpreadPct ?: 4.0) + 2.0
                b.annualRatePct = policyRate + (b.floatingSpreadPct ?: 0.0) + creditAdj
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
            equity -= fxLoss; eqFx = fxLoss
            if (fxLoss > 50.0) lines += ReportLine("Rugi selisih kurs utang A$: ${fmt(fxLoss)} jt RS (mengurangi ekuitas).", Tone.BAD)
        }

        // ---- 5b. Dividen: pemegang saham menarik kas berlebih tiap Desember ----
        var dividend = 0.0
        val cashFloor = 36000.0 * sc.size * costIndex
        if ((turn + 1) % 12 == 0 && covenantBreaches == 0 && loans.none { it.dividendLock && it.balance > 0.5 } && cash + (ni + dep) > cashFloor && gameOverReason == null) {
            dividend = 0.5 * (cash + ni + dep - cashFloor)
            equity -= dividend
            lines += ReportLine("RUPS menetapkan dividen ${fmt(dividend)} jt RS (50% kas di atas cadangan ${fmt(cashFloor)} jt RS). Kas berlebih tidak boleh menganggur selamanya.", Tone.INFO)
        }

        if (pendingDividend > 0.0) {
            val d = min(pendingDividend, max(0.0, cash + ni + dep - 1500.0))
            dividend += d; equity -= d; pendingDividend = 0.0
            if (d > 0.0) lines += ReportLine("Dividen khusus dibayar ${fmt(d)} jt RS.", Tone.INFO)
        }

        // ---- 6. Kas ----
        val dAR = totalAR() - startAR
        val dInv = inventory - startInv
        val dPay = payables - startPay
        val cfo = ni + dep - dAR - dInv + dPay
        cash += cfo - capex + newDebt - principal - dividend

        // Fasilitas modal kerja: tarik otomatis saat kas negatif, lunasi saat kas longgar
        loans.firstOrNull { it.id == "rcf" }?.let { f ->
            if (cash < 0.0 && f.balance < sc.rcfLimit) {
                val draw = min(-cash, sc.rcfLimit - f.balance)
                f.balance += draw; cash += draw; rcfFlow += draw
                lines += ReportLine("Kas menipis: fasilitas modal kerja Bank B ditarik ${fmt(draw)} jt RS (bunga acuan + 3,5%).", Tone.WARN)
            } else if (f.balance > 0.0 && cash > 2500.0) {
                val pay = min(f.balance, cash - 2500.0)
                f.balance -= pay; cash -= pay; rcfFlow -= pay
            }
        }

        // Likuiditas darurat: factoring otomatis
        if (cash < 0.0) {
            val needed = -cash
            val maxFactor = normalAR * 0.7
            val faceValue = min(maxFactor, needed / FACTOR_KEEP)
            normalAR -= faceValue
            val proceeds = faceValue * FACTOR_KEEP
            val discount = faceValue - proceeds
            cash += proceeds
            equity -= discount
            factorProceeds += proceeds; factorDiscount += discount
            lines += ReportLine("Kas negatif: piutang ${fmt(faceValue)} jt RS dijual ke perusahaan factoring dengan potongan 4%.", Tone.BAD)
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
                    cash -= demand; forcedPay += demand
                    lines += ReportLine("Uji covenant GAGAL lagi: ICR ${fmt2(icr)}x (batas ${fmt2(th)}x). Bank memaksa pelunasan 15% pokok (${fmt(demand)} jt RS) dan menaikkan bunga 2%. Gagal sekali lagi = penyitaan.", Tone.BAD)
                    chronicle += ChronicleItem(turn, "Restrukturisasi paksa oleh bank", "Covenant gagal dua kuartal berturut-turut.",
                        "ICR rendah berulang → bank menarik sebagian pokok → kas tersedot.")
                    if (cash < 0.0) {
                        val face = min(normalAR * 0.7, -cash / FACTOR_KEEP)
                        normalAR -= face
                        cash += face * FACTOR_KEEP
                        equity -= face * (1.0 - FACTOR_KEEP)
                        factorProceeds += face * FACTOR_KEEP; factorDiscount += face * (1.0 - FACTOR_KEEP)
                        lines += ReportLine("Pelunasan paksa dibiayai dengan menjual piutang ${fmt(face)} jt RS (potongan 4%).", Tone.BAD)
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

        mods.removeAll { turn >= it.until }
        if (strikeMonthsLeft > 0) strikeMonthsLeft--
        if (breakdownMonthsLeft > 0) breakdownMonthsLeft--

        // ---- 9. Catat dan maju ----
        lastIncome = mapOf("revenue" to revenue, "cogs" to cogs, "opex" to opex, "maint" to maint, "dep" to dep,
            "baddebt" to writeOff, "ebit" to ebit, "interest" to interest, "intinc" to interestIncome, "tax" to tax, "ni" to ni) +
            detailNow + mapOf("op_proj" to projExp, "op_oneoff" to oneOffNow, "op_loss" to otherLoss, "op_gain" to incomeNow)
        run {
            val cfi = -capex - periodInvestOut + periodInvestIn + divInCash
            val cff = newDebt - principal + rcfFlow - forcedPay + factorProceeds - dividend + periodCureIn
            val net = cash - startCash
            val eqSum = periodCureIn - periodTradeFees + ni + mtm + divInCash - factorDiscount - eqFx - dividend
            lastDetail = mapOf(
                "d_capex_repl" to -replacement, "d_capex_spin" to -capSpin, "d_capex_exp" to -(expCapex + evCapex),
                "d_stock_buy" to -periodInvestOut, "d_stock_sell" to periodInvestIn, "d_divin" to divInCash, "cfi" to cfi,
                "d_newdebt" to newDebt, "d_repay" to -principal, "d_rcf" to rcfFlow, "d_forced" to -forcedPay,
                "d_factor" to factorProceeds, "d_divpaid" to -dividend, "d_cure" to periodCureIn, "cff" to cff,
                "cfo" to cfo, "net" to net, "d_ni" to ni, "d_dep" to dep, "d_ar" to -dAR, "d_inv" to -dInv, "d_pay" to dPay, "d_resid" to net - (cfo + cfi + cff),
                "e_open" to periodStartEquity, "e_cure" to periodCureIn, "e_fees" to -periodTradeFees, "e_ni" to ni, "e_mtm" to mtm,
                "e_divin" to divInCash, "e_factor" to -factorDiscount, "e_fx" to -eqFx, "e_div" to -dividend, "e_close" to equity,
                "e_resid" to equity - periodStartEquity - eqSum)
        }
        prevCashFlow = lastCashFlow
        lastCashFlow = mapOf("ni" to ni, "dep" to dep, "wc" to (-dAR - dInv + dPay), "cfo" to cfo,
            "capex" to -capex, "fin" to newDebt - principal, "stock" to periodInvestIn - periodInvestOut, "div" to lastDividendIncome, "net" to cash - startCash)
        periodInvestIn = 0.0; periodInvestOut = 0.0; periodStartCash = cash
        periodCureIn = 0.0; periodTradeFees = 0.0; periodStartEquity = equity
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
        val newLines = mutableListOf<ReportLine>()
        drawEvents(newLines)
        if (newLines.isNotEmpty()) lastReport = lastReport + newLines
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
        val unionOpen = openEvents.any { it.templateId == "union" }
        if (unionOpen || inflationAnnual > 5.5) topics += listOf("upah", "upah")
        val cot = commodities.cotton.index
        if (cot > 1.12 || cot < 0.9) topics += "kapas"
        if (turn <= dumpingUntil) topics += listOf("impor", "impor")
        if (creditShock > 0.3 || policyRate - policyHist.first() >= 0.5) topics += "kredit"
        if (world.rivalAveragePrice() < 0.985) topics += "pesaing"
        for (ev in openEvents) topics += listOf("ev:${ev.id}", "ev:${ev.id}")
        if (topics.isEmpty()) topics += "ekonomi"
        val topic = topics[(turn + r.nextInt(topics.size)) % topics.size]
        fun piece(slant: String, h: String, s: String, conf: Int, rumor: Boolean = false) =
            NewsPiece("news_${turn}_$slant", slant, h, s, conf, rumor, false)
        val m = electionTurn - turn
        if (topic.startsWith("ev:")) {
            val ev = openEvents.first { it.id == topic.removePrefix("ev:") }
            return listOf(
                piece("BISNIS", "Kabar industri: ${ev.category.lowercase()} jadi sorotan pelaku usaha", "Beberapa pabrik tekstil melaporkan situasi serupa bulan ini.", 1),
                piece("PRO_PEMERINTAH", "Pemerintah: situasi ${ev.category.lowercase()} terkendali", "Pejabat meminta pelaku usaha tidak panik.", 0),
                piece("OPOSISI", "Pengusaha kecil tertekan oleh ${ev.category.lowercase()}", "Mimbar Rakyat mewawancarai pemilik pabrik di Kalimas.", 1),
                piece("SENSASIONAL", "GEGER! ${ev.category.uppercase()} bisa lumpuhkan pabrik!", "Kabar burung yang belum diverifikasi.", 0, true),
                piece("INDEPENDEN", "Dari lapangan: ${ev.title}", ev.warning, 2)
            )
        }
        return when (topic) {
            "upah" -> listOf(
                piece("BISNIS", "Survei: upah pabrik tekstil naik rata-rata ${fmt1(inflationAnnual * 0.8)}% tahun ini", "Asosiasi pengusaha mencatat tekanan upah di kawasan industri.", 1),
                piece("PRO_PEMERINTAH", "Pemerintah: kenaikan upah akan 'terukur dan adil'", "Menteri tenaga kerja menolak tuntutan di atas inflasi.", 0),
                piece("OPOSISI", "Harga pangan melonjak, buruh tekstil makin terjepit", "Mimbar Rakyat: upah riil menurun sejak tahun lalu.", 1),
                piece("SENSASIONAL", "MOGOK NASIONAL TEKSTIL SEBULAN LAGI?!", "Sumber anonim di grup pesan pekerja.", 0, true),
                piece("INDEPENDEN", if (unionOpen) "Serikat Kalimas siap mogok bila tuntutan tak dijawab sebelum tenggat" else "Inflasi ${fmt1(inflationAnnual)}% menggerus upah riil; tuntutan baru diperkirakan dalam beberapa bulan",
                    "Analisis data upah dan wawancara pengurus serikat.", 2)
            )
            "kapas" -> listOf(
                piece("BISNIS", if (cot > 1.0) "Harga kapas naik, margin pabrik tertekan" else "Harga kapas turun, peluang menekan HPP", "Indeks kapas ${fmt2(cot)} dari basis 1,00.", 1),
                piece("PRO_PEMERINTAH", "Pasokan kapas nasional aman", "Kementerian perdagangan menyebut stok cukup.", 0),
                piece("OPOSISI", "Petani kapas dan pabrik sama-sama dirugikan tengkulak", "Wawancara di sentra Tarsis.", 1),
                piece("SENSASIONAL", if (cot > 1.0) "KAPAS LANGKA! Harga bisa dobel bulan depan!" else "Kapas anjlok, pasar kolaps!", "Spekulasi di grup pedagang.", 0, true),
                piece("INDEPENDEN", "Data: indeks kapas ${fmt2(cot)}, cenderung kembali ke rata-rata dalam beberapa bulan", "Analisis harga berjangka dan musim panen.", 2)
            )
            "impor" -> listOf(
                piece("BISNIS", "Kain impor murah membanjiri pasar ritel", "Harga jual produsen lokal ditekan.", 1),
                piece("PRO_PEMERINTAH", "Pemerintah: impor tekstil sesuai aturan", "Kemendag menolak tuduhan dumping tanpa bukti.", 0),
                piece("OPOSISI", "Industri tekstil lokal dibiarkan mati oleh kain impor", "Asosiasi menuntut bea masuk.", 1),
                piece("SENSASIONAL", "PABRIK TEKSTIL TUTUP SERENTAK!", "Kabar yang belum dikonfirmasi.", 0, true),
                piece("INDEPENDEN", "Harga kain impor Velmora di bawah biaya produksi wajar; petisi anti-dumping disiapkan", "Perbandingan struktur biaya dan harga jual.", 2)
            )
            "kredit" -> listOf(
                piece("BISNIS", "Bank memperketat kredit industri", "Spread kredit melebar seiring kenaikan bunga acuan.", 1),
                piece("PRO_PEMERINTAH", "Sistem perbankan sehat, kredit tetap mengalir", "OJK menyatakan kualitas kredit terjaga.", 0),
                piece("OPOSISI", "UMKM dan pabrik kecil kesulitan akses kredit", "Wawancara debitur ditolak bank.", 1),
                piece("SENSASIONAL", "BANK AMBRUK? Nasabah ramai tarik dana!", "Pesan berantai tanpa sumber.", 0, true),
                piece("INDEPENDEN", "Data bank: kredit macet tekstil naik, bank mengetatkan syarat dan menaikkan spread", "Laporan statistik perbankan.", 2)
            )
            "pesaing" -> listOf(
                piece("BISNIS", "Perang harga kain di kalangan produsen menengah", "Rata-rata harga pesaing turun ${fmt1((1.0 - world.rivalAveragePrice()) * 100)}%.", 1),
                piece("PRO_PEMERINTAH", "Persaingan sehat menguntungkan konsumen", "Pejabat memuji harga lebih murah.", 0),
                piece("OPOSISI", "Perang harga tekan upah pekerja", "Serikat khawatir PHK.", 1),
                piece("SENSASIONAL", "PESAING BANTING HARGA 50%!", "Pesan dari agen penjualan yang melebih-lebihkan.", 0, true),
                piece("INDEPENDEN", "Pesaing menurunkan harga rata-rata ${fmt1((1.0 - world.rivalAveragePrice()) * 100)}% untuk merebut pangsa pasar", "Pemantauan harga di 40 toko ritel.", 2)
            )
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
                piece("BISNIS", if (world.taylorStar > policyRate + 0.2) "Ekonom perkirakan bunga acuan naik karena inflasi dan kurs" else "Ekonom: bank sentral cenderung menahan bunga",
                    "Survei 12 ekonom; proyeksi bunga acuan akhir tahun.", 1),
                piece("PRO_PEMERINTAH", "Bank sentral: tidak ada rencana menaikkan bunga",
                    "Gubernur menegaskan dukungan pada pertumbuhan.", 0),
                piece("OPOSISI", "Bunga ditahan demi pemilu, rakyat bayar belakangan",
                    "Oposisi menilai bank sentral tunduk pada istana.", 1),
                piece("SENSASIONAL", "Bunga KPR bakal meroket 5%!", "Kabar dari grup percakapan bankir.", 0, true),
                piece("INDEPENDEN", if (world.taylorStar > policyRate + 0.2) "Notulen rapat bocor: mayoritas anggota dewan condong menaikkan bunga" else "Notulen rapat: dewan gubernur terbelah, belum ada kenaikan bunga",
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
        /** Rasio HPP bahan+energi (dikalibrasi agar margin EBIT ±5%, setara pabrikan tekstil Indonesia). */
        val INVEST = listOf(
            InvestSpec("line", "Tambah lini tenun", "Lini tenun baru menambah kapasitas. Tanpa tambahan permintaan, lini baru menganggur dan hanya menambah biaya.",
                3600.0, 4, 3, true, "Kapasitas +12%, biaya operasi +70 jt RS per bulan", 3),
            InvestSpec("automation", "Otomasi pabrik", "Mesin otomatis menurunkan kebutuhan tenaga kerja dan HPP, tetapi memicu keresahan pekerja.",
                2400.0, 3, 2, true, "HPP −1,5% dan tenaga kerja −10%", 1),
            InvestSpec("rnd", "R&D dan desain motif", "Tim desain dan laboratorium warna. Dibebankan sebagai biaya, bukan aset.",
                1800.0, 6, 2, false, "Permintaan +4% permanen", 2),
            InvestSpec("brand", "Kampanye merek ritel", "Iklan dan promosi bersama peritel.",
                1200.0, 4, 2, false, "Penjualan +3% selama 12 bulan", 1),
            InvestSpec("warehouse", "Gudang dan logistik", "Gudang tambahan dekat pelabuhan.",
                1500.0, 2, 2, true, "Persediaan −8% dan risiko pengiriman tertahan separuh", 1),
            InvestSpec("export", "Pasar ekspor Aurelia", "Sertifikasi, pameran dagang, dan agen penjualan luar negeri.",
                1500.0, 6, 3, false, "Permintaan +6% permanen, biaya operasi +40 jt RS per bulan", 1)
        )
        const val LOT = 0.1
        const val BROKER_FEE = 0.0015
        const val SELL_TAX = 0.001
                /** Bagian nilai piutang yang diterima saat factoring (potongan ±4% untuk tenor ±60 hari). */
        const val FACTOR_KEEP = 0.96
        /** Bagian piutang obral kredit yang akhirnya macet. */
        const val STUFFED_LOSS = 0.45
        /** Belanja modal penggantian mesin sebagai bagian dari penyusutan. */
                const val REPLACEMENT_CAPEX = 0.70

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
