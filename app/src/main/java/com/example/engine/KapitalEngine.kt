package com.example.engine

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
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

enum class Difficulty(
    val label: String,
    val noiseMult: Double,
    val eventRateMult: Double,
    val allowRivalCoalition: Boolean,
    val ironman: Boolean,
    val description: String
) {
    MAGANG("Magang", 0.25, 0.75, false, false, "Informasi akurat, kejadian ringan, AI pesaing pasif, cocok untuk belajar."),
    MANAJER("Manajer", 1.0, 1.0, false, false, "Sedikit bising, kejadian normal, pesaing kompetitif."),
    DIREKTUR("Direktur", 1.45, 1.20, true, false, "Data tertunda dan bias, kejadian berantai aktif, pesaing belajar dari pola Anda."),
    KRISIS_ABADI("Krisis Abadi", 2.0, 1.40, true, true, "Sangat bising, angsa hitam lebih sering, pesaing berkoalisi melawan pemimpin pasar, Ironman wajib.")
}

enum class TransferPricingMode(val label: String, val description: String) {
    WAJAR("Harga Wajar (Arm's Length)", "Pajak dihitung normal di tiap negara (Sundara 22%); aman dari pemeriksaan transfer pricing."),
    OPTIMAL("Optimalisasi Sah", "Menggeser margin lisensi dan pengadaan secara wajar ke anak usaha berpajak rendah (hemat PPh 3–5 poin)."),
    AGRESIF("Penghindaran Agresif via Meridia", "Mengalihkan sebagian besar laba ke SPV Meridia (8,5%); hemat pajak besar tetapi menumpuk risiko audit & skandal etika.")
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

/** Bukti pelanggaran etika yang tersimpan di skor rahasia (Bagian 16 Dokumen Desain). */
data class EthicsEvidence(
    val id: String,
    val kind: String,
    val severity: Double,
    val witnessExecId: String,
    val countryId: String,
    val turnCommitted: Int
)

/** Kartu berantai yang dijadwalkan muncul beberapa giliran setelah pemicu (Bagian 15 Dokumen Desain). */
data class ScheduledChainEvent(
    val dueTurn: Int,
    val templateId: String,
    val causeNote: String
)

/** Evaluasi 5 komponen akhir kampanye (Bagian 19 Dokumen Desain). */
data class CampaignEvaluation(
    val nilaiPerusahaanScore: Int,   // Bobot 30%
    val ketahananScore: Int,         // Bobot 20%
    val kualitasKeputusanScore: Int, // Bobot 20%
    val etikaReputasiScore: Int,     // Bobot 15%
    val dampakSosialScore: Int,      // Bobot 15%
    val totalScore: Int,
    val grade: String,
    val archetypeTitle: String,
    val summaryText: String
)

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
 * Mesin simulasi KAPITAL (Era 1 s.d. Era 4). Kotlin murni (tanpa Android) agar bisa diuji di JVM.
 * Semua angka uang dalam juta Rial Sundara (jt RS), kecuali disebut lain.
 * Keacakan dunia diambil dari aliran per giliran (seed, giliran, subsistem), sehingga
 * dua cabang "Bagaimana Jika" mengalami dunia yang sama.
 */
class KapitalEngine(val seed: Long) {

    val sc: Scenario = ScenarioGen.make(seed)

    // ---------- Waktu, kesulitan, dan sumber daya ----------
    var turn = sc.turn0; private set
    var difficulty: Difficulty = Difficulty.MANAJER; private set
    var ap = 4; private set
    val apMax: Int get() = 7 - execs.count { !it.present }
    var gameOverReason: String? = null; private set
    var isVictory: Boolean = false; private set
    var boardTrust: Int = 78; private set
    /** B2: pasar produk (harus dideklarasikan sebelum blok init yang memperkirakan pendapatan). */
    val market = ProductMarket()
    /** B3: karyawan kunci, R&D berkelanjutan, logistik (dideklarasikan sebelum blok init). */
    val staffPool = StaffPool()
    val rd = RdProgram()
    var logisticsLevel = 0; private set
    var fleetOwned = false; private set
    /** B4: asuransi, L/C, sale-leaseback. */
    var insuranceLevel = 0; private set
    var tradeFinance = false; private set
    /** Nilai aset tetap yang dijual dan disewa kembali (jt RS), dasar sewa bulanan. */
    var leasedBackRs = 0.0; private set
    val insuranceClaimTurns = mutableListOf<Int>()
    /** B4b: derivatif aktif dan jumlah bersih (penyelesaian dikurangi premi) sejak awal, jt RS. */
    /** B6: aktor yang belajar dari pola pemain (politisi, investor aktivis, eksekutif egois). */
    val actors = ActorMind()
    /** B5: riwayat forensik (skor-M), auditor eksternal, dan percantikan laporan. */
    val forensics = ForensicBook()
    var auditorLevel = 0; private set
    /** Sisa biaya yang dikapitalisasi ke persediaan dan belum berbalik; total sejak pembersihan terakhir; jumlah koreksi laporan. */
    var dressedRs = 0.0; private set
    var dressedSinceCleanRs = 0.0; private set
    private var dressedFreshRs = 0.0
    var restatements = 0; private set
    var shortReports = 0; private set
    /** B5b: SPV di luar laporan resmi. */
    private val pendingSpv = mutableListOf<Pair<Double, Double>>()
    private val spvDisclosedIds = mutableSetOf<String>()
    var spvRevealed = 0; private set
    /** B5c: antimonopoli dan skandal tertunda. */
    var antitrustFines = 0; private set
    var antitrustBlocks = 0; private set
    private var antitrustNextTurn = -1
    var whistleblowerLeaks = 0; private set
    val derivs = mutableListOf<Deriv>()
    var derivNetRs = 0.0; private set
    var derivPremiumsRs = 0.0; private set

    // ---------- Neraca ----------
    var cash = sc.cash; private set
    var normalAR = sc.ar; private set
    val stuffedBuckets = mutableListOf<Pair<Int, Double>>()
    var inventory = sc.inv; private set
    var payables = sc.pay; private set
    var fixedAssets = sc.fa; private set
    var equity = 0.0; private set
    val loans = mutableListOf<Loan>()
    private var initialEquity = 0.0

    // ---------- Makro & Dunia Multinasional (Era 2–4) ----------
    val world = World(AgentScale.HOUSEHOLDS.getValue("sundara"))
    val bourse = Bourse()
    val credit = CreditDesk()
    private val policyHist = MutableList(6) { sc.policy }
    val holdings = LinkedHashMap<String, Int>()
    val costBasis = HashMap<String, Double>()
    var dividendsReceivedTotal = 0.0; private set
    var realizedGainTotal = 0.0; private set
    var periodInvestOut = 0.0; private set
    var periodInvestIn = 0.0; private set
    private var periodStartCash = sc.cash
    var lastDividendIncome = 0.0; private set
    val commodities = Commodities()

    class ForeignProfile(
        val id: String,
        val name: String,
        val currencyCode: String,
        val infl: Double,
        val policy: Double,
        val target: Double,
        val rStar: Double,
        val baseFxToRs: Double,
        val corpTaxPct: Double,
        val withholdingTaxPct: Double,
        val baseTariffPct: Double,
        val baseTension: Int
    )

    class ForeignCountryMeta(val profile: ForeignProfile) {
        var fxToRs: Double = profile.baseFxToRs
        var tariffPct: Double = profile.baseTariffPct
        var tensionScore: Int = profile.baseTension
        var politicalRisk: Int = profile.baseTension
        var capitalControlTurnsLeft: Int = 0
    }

    val foreignProfiles = listOf(
        ForeignProfile("aurelia", "Aurelia", "A$", 2.6, 4.25, 2.0, 1.5, sc.kurs0, 21.0, 15.0, 5.0, 58),
        ForeignProfile("norvaldia", "Norvaldia", "NRV", 5.7, 7.00, 4.5, 2.5, 1820.0, 24.0, 10.0, 6.0, 66),
        ForeignProfile("kestrel", "Kestrel", "KST", 2.1, 3.10, 2.0, 1.0, 2150.0, 18.0, 8.0, 8.0, 64),
        ForeignProfile("halvenia", "Halvenia", "HLV", 2.15, 3.50, 2.0, 1.0, 16800.0, 26.0, 5.0, 4.0, 32),
        ForeignProfile("meridia", "Meridia", "MRD", 2.8, 3.25, 2.5, 1.0, 11200.0, 8.5, 0.0, 0.0, 24),
        ForeignProfile("tarsis", "Tarsis", "TRS", 7.6, 6.75, 5.0, 2.0, 185.0, 20.0, 10.0, 6.0, 44),
        ForeignProfile("velmora", "Velmora", "VLM", 10.5, 10.00, 6.0, 3.0, 420.0, 25.0, 20.0, 14.0, 76)
    )

    val foreign: Map<String, World> = foreignProfiles.associate { p ->
        p.id to World(AgentScale.HOUSEHOLDS.getValue(p.id), p.target, p.rStar).also { it.initScenario(p.infl, p.policy) }
    }

    /**
     * Jumlah agen yang BENAR-BENAR disimulasikan saat ini, dihitung dari objek hidup (bukan konstanta desain).
     * Kelas agen yang belum diimplementasikan tidak dilaporkan di sini.
     */
    /** Blok yang terhubung dagang dengan pemain: pemasok, pembiayaan, dan lokasi anak usaha aktif. */
    fun linkedBlocksNow(): Set<Block> {
        val s = Dunia.baselineLinks()
        for ((cid, sub) in subsidiaries) if (sub.active && !sub.nationalized) Countries.byId[cid]?.let { s += it.block }
        return s
    }

    fun agentCensus(): Map<String, Int> {
        val m = LinkedHashMap<String, Int>()
        m["rumah_tangga"] = world.householdSector.agents + foreign.values.sumOf { it.householdSector.agents }
        // Pemerintah dan bank sentral: satu objek hidup per negara (24) di modul makro lanjut (A5).
        m["perusahaan"] = dunia.credit.firmCount
        m["bank"] = dunia.credit.bankCount
        m["investor"] = InvestorPool.TOTAL
        m["pemerintah"] = dunia.macro.states.size
        m["bank_sentral"] = dunia.macro.states.size
        return m
    }
    val foreignMeta: Map<String, ForeignCountryMeta> = foreignProfiles.associate { p ->
        p.id to ForeignCountryMeta(p)
    }

    // ---------- Anak Usaha Lintas Negara (Tahap 3: Entitas dengan Pembukuan Lokal & Konsolidasi) ----------
    class SubsidiarySpec(
        val countryId: String,
        val countryName: String,
        val currencyCode: String,
        val title: String,
        val desc: String,
        val baseCostRs: Double,
        val buildMonths: Int,
        val apCost: Int,
        val cogsReduction: Double,
        val demandBoost: Double,
        val workersAdded: Int,
        val benefitText: String,
        val riskText: String,
        val capitalTurnoverAnnual: Double = 0.88,
        val baseLocalCogsRatio: Double = 0.67,
        val fixedOpexRs: Double = 36.0,
        val fixedOpexRateMonthly: Double = 0.0080,
        val intercompanyShare: Double = 0.25,
        val isFactory: Boolean = true,
        val monthlyOpexRs: Double = fixedOpexRs,
        val localSurplusRate: Double = 0.015
    )

    /**
     * Entitas anak usaha multinasional dengan pembukuan akrual penuh dalam mata uang lokal ([spec.currencyCode]).
     * Identitas neraca lokal: totalAssetsLocal() == totalLiabilitiesLocal() + equityLocal() (< 1e-6).
     */
    class SubsidiaryState(val spec: SubsidiarySpec, val startTurn: Int) {
        var spentRs: Double = 0.0
        var active: Boolean = false
        var nationalized: Boolean = false
        var totalRepatriatedRs: Double = 0.0

        // Neraca dalam mata uang lokal (juta unit lokal)
        var cashLocal: Double = 0.0
        var arLocal: Double = 0.0
        var invLocal: Double = 0.0
        var faLocal: Double = 0.0
        var apLocal: Double = 0.0
        var bankDebtLocal: Double = 0.0
        var intercompanyPayableLocal: Double = 0.0
        var paidInCapitalLocal: Double = 0.0
        var retainedEarningsLocal: Double = 0.0

        // Pelacakan kos historis untuk eliminasi konsolidasi & Selisih Kurs Translasi (CTA)
        var paidInCapitalHistoricalRs: Double = 0.0
        var cumulativeTranslatedNiRs: Double = 0.0
        var cumulativeDivGrossRs: Double = 0.0

        // Status operasi lokal
        var monthsActive: Int = 0
        var monthsOperating: Int
            get() = monthsActive
            set(value) { monthsActive = value }
        var cumulativeNetIncomeRs: Double
            get() = cumulativeTranslatedNiRs
            set(value) { cumulativeTranslatedNiRs = value }
        var localPriceIndex: Double = 1.0
        var localCostIndex: Double = 1.0
        var localDemandFactor: Double = 1.0
        var lastUtilization: Double = 0.0
        var lossCarryLocal: Double = 0.0
        var monthlyDebtAmortLocal: Double = 0.0
        var periodCapitalInjectedLocal: Double = 0.0
        var periodNewDebtLocal: Double = 0.0
        var periodDivPaidLocal: Double = 0.0
        var lastDivPaidLocal: Double = 0.0
        /** Bagian pemilik minoritas (kepentingan non-pengendali), 0..0,49. */
        var nciPct: Double = 0.0
        /** B4c: setelah penawaran ditolak, investor tidak mau berunding sampai giliran ini. */
        var nciCooldownUntil: Int = -1
        var periodDivParentLocal: Double = 0.0
        var lastDivParentLocal: Double = 0.0

        var lastIncomeLocal: Map<String, Double> = emptyMap()
        var lastBalanceLocal: Map<String, Double> = emptyMap()
        var lastCashFlowLocal: Map<String, Double> = emptyMap()

        fun totalAssetsLocal(): Double = cashLocal + arLocal + invLocal + faLocal
        fun totalLiabilitiesLocal(): Double = apLocal + bankDebtLocal + intercompanyPayableLocal
        fun equityLocal(): Double = paidInCapitalLocal + retainedEarningsLocal
        fun totalAssetsForeign(): Double = totalAssetsLocal()
        fun totalLiabForeign(): Double = totalLiabilitiesLocal()
        val localEquity: Double get() = equityLocal()
        val localDebt: Double get() = bankDebtLocal
        fun retainedEarningsHistoricalRs(): Double = cumulativeTranslatedNiRs - cumulativeDivGrossRs

        /** Kapasitas dividen kas lokal yang dapat direpatriasi (diturunkan dari laba ditahan & kas lokal nyata). */
        val retainedForeign: Double
            get() = if (!active || nationalized) 0.0 else max(0.0, min(retainedEarningsLocal, cashLocal * 0.80))
    }

    val subsidiaries = LinkedHashMap<String, SubsidiaryState>()
    var currentEra: Int = 1; private set
    var investmentInSubsRs: Double = 0.0; private set
    val subsidiaryInvestmentRs: Double get() = investmentInSubsRs
    var goodwill: Double = 0.0; private set
    var ctaEquity: Double = 0.0; private set
    var lastConsolidatedEquity: Map<String, Double> = emptyMap(); private set
    private var consEquityPrev: Double = Double.NaN
    private var ctaPrev: Double = 0.0
    var lastConsolidatedIncome: Map<String, Double> = emptyMap(); private set
    var lastConsolidatedBalance: Map<String, Double> = emptyMap(); private set
    var lastConsolidatedCashFlow: Map<String, Double> = emptyMap(); private set

    // Era 3 & Era 4 state
    var isPublicCompany: Boolean = false; private set
    var founderSharePct: Double = 100.0; private set
    val publicFloatPct: Double get() = (100.0 - founderSharePct) / 100.0
    var dualClassShares: Boolean = false; private set
    /** Saham beredar (juta lembar), harga (RS/saham), sentimen pasar (log-premi), dan riwayat harga. */
    var sharesOutM = 0.0; private set
    var stockPrice = 0.0; private set
    var stockSentiment = 0.0; private set
    val stockHistory = mutableListOf<Double>()
    private var emaNi = 0.0
    private var stockFairMc = 0.0
    var buybackTotalRs = 0.0; private set
    var sharesRetiredM = 0.0; private set
    var lastDividendPerShare = 0.0; private set
    private var pendingBuyback = 0.0
    private var founderSharesM = 0.0
    var captiveBankActive: Boolean = false; private set
    var captiveBankCapitalRs: Double = 0.0; private set
    var mediaOwned: Boolean = false; private set
    var lobbyCount: Int = 0; private set
    var carbonCredits: Int = 0; private set
    var appointedSuccessorId: String? = null; private set
    val successorExecId: String? get() = appointedSuccessorId
    var successorReadiness: Double = 0.0; private set
    private var appointedAtTurn = 0

    private var pendingWithholdingTaxRs: Double = 0.0
    private var periodSubInvestOutRs: Double = 0.0
    private var periodSubCashAcquiredRs: Double = 0.0
    private var periodSubSeizedCashRs: Double = 0.0
    private var periodSubDeconsolRs: Double = 0.0
    private var periodNciDivRs: Double = 0.0
    var nciSaleProceedsTotal = 0.0; private set
    /** B4c: pajak tertahan atas dividen bagian minoritas (ditanggung pemegang minoritas, netral bagi grup), kumulatif jt RS. */
    var nciWithheldRs = 0.0; private set
    private val subCashSnap = mutableMapOf<String, Pair<Double, Double>>()
    private var periodSubDivInGrossRs: Double = 0.0
    private var periodGoodwillImpairmentRs: Double = 0.0
    private var periodIntercompanyFeeToSubRs: Double = 0.0

    var transferPricingMode: TransferPricingMode = TransferPricingMode.WAJAR; private set
    var fxHedgeUntilTurn = -1; private set
    var hedgedKurs = sc.kurs0; private set
    val fxHedgeActive: Boolean get() = turn <= fxHedgeUntilTurn
    var acquiredRivalsCount = 0; private set
    var esgCompliant = false; private set

    private var fxPassState = 1.0
    private var aurPrice = 1.0
    fun fxReal(): Double = kurs / (sc.kurs0 * priceIndex / aurPrice)
    var creditAdj = 0.0; private set
    var hedgeUntilTurn = -1; private set
    var cureUsed = false; private set
    private var hedgeCottonIdx = 1.0
    val hedgeActive: Boolean get() = turn <= hedgeUntilTurn

    fun materialIndexEff(): Double =
        0.78 * (if (hedgeActive) hedgeCottonIdx else commodities.cotton.index) + 0.22 * commodities.oil.index

    private val lossPool = mutableListOf<Pair<Int, Double>>()
    fun taxLossCarryforward(): Double = lossPool.sumOf { it.second }

    /** Tarif pajak penghasilan badan induk Sundara (22% dasar) dan tarif efektif grup. */
    fun effectiveTaxRate(): Double {
        val consEbt = lastConsolidatedIncome["ebt"] ?: 0.0
        val consTax = lastConsolidatedIncome["tax"] ?: 0.0
        if (consEbt > 10.0 && consTax >= 0.0) {
            return (consTax / consEbt).coerceIn(0.05, 0.35)
        }
        return 0.22
    }

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

    // ---------- Lapisan dunia (Tahap A): geopolitik, negara sekunder, sektor & rantai pasok ----------
    val dunia = Dunia(seed, electionTurn, foreignProfiles.associate { it.id to it.baseTension })

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

    // ---------- Kartu kejadian, Kartu Berantai, & Skor Etika ----------
    class Mod(val key: String, val value: Double, val until: Int)
    class EventRecord(val turn: Int, val title: String, val choice: String, val result: String, val auto: Boolean)
    val mods = mutableListOf<Mod>()
    val openEvents = mutableListOf<GameEvent>()
    val scheduledChainEvents = mutableListOf<ScheduledChainEvent>()
    val eventHistory = mutableListOf<EventRecord>()
    val ethicsEvidences = mutableListOf<EthicsEvidence>()
    var scandalsTriggered = 0; private set
    var crisesSurvived = 0; private set
    var factoringUsedCount = 0; private set
    private val eventLastFired = HashMap<String, Int>()
    private var eventCounter = 0
    var eventsRaised = 0; private set
    var ethicsLoad = 0.0; private set
    var lastEventResult = ""; private set
    var dumpingUntil = -1; private set
    private var creditShock = 0.0
    /** Nilai rapor (0–100) pada akhir tiap era; indeks 0 = akhir Era 1. */
    val eraGrades = mutableListOf<Int>()
    private var pendingCapex = 0.0
    private var pendingPrepay = 0.0
    private var pendingWriteoff = 0.0
    private var pendingIncome = 0.0
    private var pendingDividend = 0.0
    private var pendingInvLoss = 0.0
    private val scheduledWage = mutableListOf<Pair<Int, Double>>()
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
    var spinningDecision: String? = null; private set
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
    /** B7: pasar prediksi berpoin dan riwayat bulanan untuk detektor bias dan post-mortem (tak memengaruhi neraca). */
    val predMarket = PredictionMarket()
    val history = mutableListOf<MonthSnap>()
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
        if (sc.stuffedPerMonth > 1.0) for (t in (turn - 5) until turn) stuffedBuckets += t to sc.stuffedPerMonth
        equity = totalAssets() - totalLiabilities()
        initialEquity = max(1000.0, equity)
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
        lastConsolidatedIncome = est + mapOf("ebt" to (est.getValue("ebit") - est.getValue("interest")), "wht_tax" to 0.0, "elim_intercompany_rev" to 0.0, "elim_intercompany_div" to 0.0)
        lastConsolidatedBalance = consolidatedBalanceSnapshot()
        lastConsolidatedCashFlow = mapOf("cfo" to lastCashFlow.getValue("cfo"), "cfi" to lastCashFlow.getValue("capex"), "cff" to lastCashFlow.getValue("fin"), "elim_div" to 0.0, "net" to lastCashFlow.getValue("net"))
        memos = generateMemos(Random(seed + 7))
        news = generateNews(Random(seed + 11))
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
    fun totalAssets(): Double = cash + totalAR() + inventory + fixedAssets + portfolioValue() + investmentInSubsRs + goodwill + captiveBankCapitalRs
    fun totalLiabilities(): Double = payables + totalDebt()

    fun balanceSnapshot(): Map<String, Double> = mapOf(
        "cash" to cash, "ar" to totalAR(), "ar_normal" to normalAR, "ar_risky" to stuffedTotal(),
        "inv" to inventory, "fa" to fixedAssets, "pf" to portfolioValue(),
        "inv_subs" to investmentInSubsRs, "goodwill" to goodwill, "captive_bank" to captiveBankCapitalRs,
        "assets" to totalAssets(), "payables" to payables,
        "debt_st" to loans.filter { it.maturityTurn - turn <= 12 }.sumOf { loanBalanceRs(it) },
        "debt_lt" to loans.filter { it.maturityTurn - turn > 12 }.sumOf { loanBalanceRs(it) },
        "liab" to totalLiabilities(), "equity" to equity
    )

    // ---------- Konsolidasi Grup (Induk + Anak Usaha dengan Eliminasi Antar-Entitas & CTA) ----------
    fun consolidatedTotalAssets(): Double {
        val validSubs = subsidiaries.values.filter { !it.nationalized }
        val subAssetsRs = validSubs.sumOf { it.totalAssetsLocal() * foreignMeta.getValue(it.spec.countryId).fxToRs }
        val icElimRs = validSubs.sumOf { it.intercompanyPayableLocal * foreignMeta.getValue(it.spec.countryId).fxToRs }
        return (totalAssets() - investmentInSubsRs) + subAssetsRs - icElimRs
    }

    fun consolidatedTotalLiabilities(): Double {
        val validSubs = subsidiaries.values.filter { !it.nationalized }
        val subLiabRs = validSubs.sumOf { it.totalLiabilitiesLocal() * foreignMeta.getValue(it.spec.countryId).fxToRs }
        val icElimRs = validSubs.sumOf { it.intercompanyPayableLocal * foreignMeta.getValue(it.spec.countryId).fxToRs }
        return totalLiabilities() + subLiabRs - icElimRs
    }

    fun computeCtaEquity(): Double {
        val validSubs = subsidiaries.values.filter { !it.nationalized }
        return validSubs.sumOf { sub ->
            val fx = foreignMeta.getValue(sub.spec.countryId).fxToRs
            (sub.equityLocal() * fx) - (sub.paidInCapitalHistoricalRs + sub.retainedEarningsHistoricalRs())
        }
    }

    fun consolidatedEquity(): Double {
        val validSubs = subsidiaries.values.filter { !it.nationalized }
        val subRetainedHistRs = validSubs.sumOf { it.retainedEarningsHistoricalRs() }
        return equity + subRetainedHistRs + computeCtaEquity()
    }

    fun consolidatedBalanceSnapshot(): Map<String, Double> {
        val validSubs = subsidiaries.values.filter { !it.nationalized }
        fun sumSub(sel: (SubsidiaryState) -> Double): Double =
            validSubs.sumOf { sel(it) * foreignMeta.getValue(it.spec.countryId).fxToRs }
        val icElimRs = sumSub { it.intercompanyPayableLocal }
        val consCash = cash + sumSub { it.cashLocal }
        val consAr = max(0.0, totalAR() + sumSub { it.arLocal } - icElimRs)
        val consInv = inventory + sumSub { it.invLocal }
        val consFa = fixedAssets + sumSub { it.faLocal }
        val consAp = max(0.0, payables + sumSub { it.apLocal + it.intercompanyPayableLocal } - icElimRs)
        val subDebtRs = sumSub { it.bankDebtLocal }
        val debtSt = loans.filter { it.maturityTurn - turn <= 12 }.sumOf { loanBalanceRs(it) } + subDebtRs * 0.25
        val debtLt = loans.filter { it.maturityTurn - turn > 12 }.sumOf { loanBalanceRs(it) } + subDebtRs * 0.75
        val cta = computeCtaEquity()
        val consEq = consolidatedEquity()
        return mapOf(
            "cash" to consCash,
            "ar" to consAr,
            "inv" to consInv,
            "fa" to consFa,
            "pf" to portfolioValue(),
            "goodwill" to goodwill,
            "captive_bank" to captiveBankCapitalRs,
            "elim_inv_subs" to -investmentInSubsRs,
            "assets" to consolidatedTotalAssets(),
            "payables" to consAp,
            "debt_st" to debtSt,
            "debt_lt" to debtLt,
            "liab" to consolidatedTotalLiabilities(),
            "cta_equity" to cta,
            "nci_equity" to nciEquityRs(),
            "equity" to consEq
        )
    }

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

    fun fxPassLevel(): Double = fxPassState

    private fun volumeRevenue(revenue: Double): Double = revenue / ((1.0 + priceAdjPct / 100.0) * fxPassLevel())

    fun cogsRatio(): Double {
        val fxFactor = if (fxHedgeActive) min(fxReal(), hedgedKurs / sc.kurs0) else fxReal()
        var r = sc.cogsBase * (0.8 + 0.2 * fxFactor) * (0.35 + 0.65 * materialIndexEff()) * modProduct("cogs")
        if (kickbackActive) r += 0.01
        if (dualSourcing) r += 0.012
        r -= spinningBenefit()
        if (automation) r -= 0.015
        // Sinergi bahan baku hulu dari anak usaha yang benar-benar berproduksi (dikalikan utilisasi nyata anak usaha)
        for (sub in subsidiaries.values) {
            if (sub.active && !sub.nationalized) {
                r -= SUB_SYNERGY * sub.spec.cogsReduction * sub.lastUtilization.coerceIn(0.4, 1.0)
            }
        }
        val kestrelTariff = foreignMeta["kestrel"]?.tariffPct ?: 8.0
        val hasKestrelSub = subsidiaries["kestrel"]?.let { it.active && !it.nationalized } == true
        if (!hasKestrelSub && kestrelTariff > 10.0) {
            r += (kestrelTariff - 10.0) * 0.0008
        }
        if (fleetOwned) r -= 0.004
        val costM = 1.0 + (dunia.lastEffects.costMult - 1.0) * (1.0 - 0.35 * logisticsLevel)
        return (r * costM * staffPool.cogsMult()).coerceIn(0.45, 0.92)
    }

    fun monthlyInterest(): Double = loans.sumOf { loanBalanceRs(it) * it.annualRatePct / 100.0 / 12.0 }

    fun covenantThreshold(): Double = (loans.filter { it.balance > 0.5 }.maxOfOrNull { it.covenantIcr } ?: 0.0) * BalanceTuning.covenantScale

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

    private fun costDetail(cogs: Double): Map<String, Double> {
        val ob = opexBase()
        return mapOf(
            "cogs_mat" to cogs * 0.50, "cogs_energy" to cogs * 0.15, "cogs_labor" to cogs * 0.25, "cogs_oh" to cogs * 0.10,
            "op_salary" to ob * 0.55 + wageAddOn, "op_admin" to ob * 0.12, "op_mkt" to ob * 0.15,
            "op_util" to ob * 0.08, "op_ins" to ob * 0.05, "op_other" to ob * 0.05, "op_lines" to extraOpex())
    }

    fun priceRevenueFactor(): Double = (1.0 + priceAdjPct / 100.0) * priceVolumeFactor()

    fun priceVolumeFactor(): Double = volOverride ?: world.volumeFactor((1.0 + priceAdjPct / 100.0) * fxPassLevel())

    fun playerShare(): Double = world.playerShare((1.0 + priceAdjPct / 100.0) * fxPassLevel())

    private fun baseNormalRevenue(prodFactor: Double): Double {
        var r = sc.baseRevenue * volumeCap() * econ * trendGrowth() * modProduct("rev") * (if (turn <= dumpingUntil) 0.98 else 1.0) * priceIndex * fxPassLevel() * prodFactor * (1.0 - lostCustomerShare) * priceRevenueFactor() * market.revenueFactor(turn) * staffPool.revenueMult() * (1.0 + rd.quality)
        if (salesScheme == SalesScheme.KAS) r *= 0.97
        if (!exec("exec_sales").present) r *= 0.95
        return r
    }

    fun capacityIdx(): Double {
        val subCap = subsidiaries.values.count { it.active && !it.nationalized && it.spec.isFactory } * 0.06
        return 1.08 + 0.12 * extraLines + subCap
    }

    fun demandIdx(): Double {
        val subDem = subsidiaries.values.filter { it.active && !it.nationalized }.sumOf { SUB_SYNERGY * it.spec.demandBoost * it.lastUtilization.coerceIn(0.4, 1.0) }
        return 1.0 + demandBoost + subDem
    }

    fun volumeCap(): Double = min(demandIdx(), capacityIdx())
    fun utilization(): Double = demandIdx() / capacityIdx()
    fun foreignWorkers(): Int = subsidiaries.values.filter { it.active && !it.nationalized }.sumOf { it.spec.workersAdded }
    fun headcountProd(): Int = (sc.workers * (1.0 + 0.12 * extraLines) * (if (automation) 0.9 else 1.0)).toInt() + foreignWorkers()
    fun headcountStaff(): Int = (sc.workers * 0.26).toInt()

    /** Biaya operasional tambahan di induk (beban lokal anak usaha sudah dibukukan di laporan entitas anak masing-masing!). */
    private fun extraOpex(): Double {
        return (extraLines * 70.0 + (if (exportOn) 40.0 else 0.0) + (if (warehouse) 25.0 else 0.0) + (if (isPublicCompany) 35.0 else 0.0)) * sc.size * costIndex + market.monthlyCost(sc.baseRevenue, costIndex) + operationsMonthlyCost()
    }

    /** B3: gaji tambahan karyawan kunci, anggaran R&D, logistik, dan perawatan armada (jt RS per bulan). */
    fun operationsMonthlyCost(): Double {
        val u = sc.size * costIndex
        return staffPool.extraMonthlyCost(StaffEconomy.BASE_WAGE * u) + rd.level * RdProgram.COST_PER_LEVEL * u +
            logisticsLevel * 25.0 * u + (if (fleetOwned) 18.0 * u else 0.0) + financeMonthlyCost()
    }

    /** B4: premi asuransi (naik 15% per klaim dalam 24 bulan terakhir), biaya L/C, dan sewa kembali (jt RS per bulan). */
    fun financeMonthlyCost(): Double {
        val u = sc.size * costIndex
        val loading = 1.0 + 0.15 * insuranceClaimTurns.count { turn - it <= 24 }
        val premium = when (insuranceLevel) { 1 -> 21.0; 2 -> 30.0; else -> 0.0 } * u * loading
        return premium + (if (tradeFinance) LC_FEE * u else 0.0) + leasedBackRs * LEASE_RATE + auditFee()
    }

    private fun trendGrowth(): Double = StrictMath.pow(1.025, trendMonths / 12.0)

    private fun opexBase(): Double = sc.baseOpex * costIndex * StrictMath.pow(trendGrowth(), 0.95) * (if (automation) 0.945 else 1.0)

    private fun maintenanceSpend(): Double {
        val scale = costIndex * StrictMath.pow(trendGrowth(), 0.95)
        val needed = sc.baseMaint * scale
        return if (deferralActive) sc.baseMaint * 420.0 / 610.0 * scale
        else needed + if (deferredMonths > 0) sc.baseMaint * 120.0 / 610.0 * scale else 0.0
    }

    fun projectedIcr(): Double {
        val est = estimateMonth()
        val e = ebitHist.takeLast(2).sum() + est.getValue("ebit")
        val i = interestHist.takeLast(2).sum() + est.getValue("interest") - 3.0 * spvMonthlyInterest()
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

    /** Evaluasi 5 komponen kemenangan kampanye sesuai Bagian 19 Dokumen Desain. */
    /**
     * Rapor akhir era: era berganti menurut kalender dunia, tetapi kinerja Anda menentukan
     * warisan yang dibawa: rapor kuat menurunkan tekanan kredit (kreditur percaya), rapor rapuh
     * menaikkannya (kreditur menuntut premi) dan memicu pengawasan.
     */
    /** Harga saham = nilai fundamental (kelipatan laba dan nilai buku) x sentimen yang kembali ke rata-rata. */
    private fun updateStockPrice(ni: Double) {
        emaNi = 0.9 * emaNi + 0.1 * ni
        val fair = max(max(0.6 * equity, 11.0 * 12.0 * emaNi), 0.25 * max(equity, 1.0)).coerceAtLeast(1.0)
        val r = rng(61)
        // Harga ditentukan buku pesanan 5.000 investor (A6). Kapitalisasi tersirat = nilai wajar bulan lalu x e^sentimen;
        // kabar fundamental baru (nilai wajar bergeser) hanya masuk lewat arus pesanan investor nilai, bukan lompatan instan.
        if (stockFairMc <= 0.0) stockFairMc = fair
        // Kabar fundamental langsung sebagian tercermin (pengumuman); sisanya lewat arus pesanan (seperti emiten bursa).
        val jump = 0.6 * StrictMath.log(fair / stockFairMc).coerceIn(-0.5, 0.5)
        val lnMis = StrictMath.log(fair) - StrictMath.log(stockFairMc) - stockSentiment - jump
        val h = stockHistory
        val ret3m = if (h.size >= 4 && h.last() > 0.0 && h[h.size - 4] > 0.0) StrictMath.log(h.last() / h[h.size - 4]) / 3.0 else 0.0
        val flow = bourse.book.trade("PLAYER", r, lnMis, ret3m, 1.0, bourse.mood + dunia.lastEffects.news, dunia.lastEffects.riskOff)
        stockSentiment = (stockSentiment + jump + flow.dLnPrice + 0.03 * stdNormal(r) + StrictMath.log(stockFairMc) - StrictMath.log(fair)).coerceIn(-0.9, 0.6)
        stockFairMc = fair
        stockPrice = fair * StrictMath.exp(stockSentiment) / sharesOutM
        stockHistory += stockPrice
        if (stockHistory.size > 480) stockHistory.removeAt(0)
    }

    var handoverDone = false; private set
    var handoverChaotic = false; private set

    /**
     * Suksesi Era 4: pada giliran 445 (Januari 2067) dewan mengumumkan masa jabatan Anda berakhir tahun 2068.
     * Pada giliran 468 terjadi serah terima: penerus yang siap menjaga kepercayaan kreditur, tanpa penerus
     * terjadi serah terima darurat yang mengguncang dewan dan kredit.
     */
    private fun successionMilestones(out: MutableList<ReportLine>) {
        if (turn == 445) {
            out += ReportLine("Ketua Dewan mengumumkan: masa jabatan Anda berakhir Desember 2068. Tunjuk dan siapkan penerus sekarang.", Tone.WARN)
            chronicle += ChronicleItem(turn, "Pengumuman suksesi", "Dewan menetapkan serah terima kepemimpinan pada Desember 2068.", "Era 4 → suksesi wajib → siapkan penerus.")
        }
        if (turn == 468 && !handoverDone) {
            handoverDone = true
            val months = if (appointedSuccessorId != null) 468 - appointedAtTurn else 0
            val ready = if (appointedSuccessorId != null) (successorReadiness + 0.012 * months).coerceAtMost(1.0) else 0.0
            successorReadiness = ready
            if (appointedSuccessorId == null || ready < 0.5) {
                handoverChaotic = true
                boardTrust = (boardTrust - 25).coerceAtLeast(0)
                creditShock += 0.5
                out += ReportLine("SERAH TERIMA DARURAT: penerus ${if (appointedSuccessorId == null) "belum ditunjuk" else "belum siap (${(ready * 100).toInt()}%)"}. Kepercayaan dewan anjlok dan kreditur menuntut premi.", Tone.BAD)
                chronicle += ChronicleItem(turn, "Serah terima darurat", "Penerus tidak siap; dewan dan kreditur goyah.", "Suksesi gagal → kepercayaan dewan turun → kredit mengetat.")
            } else {
                boardTrust = (boardTrust + 8).coerceAtMost(100)
                creditShock = (creditShock - 0.2).coerceAtLeast(-0.25)
                out += ReportLine("Serah terima mulus: penerus siap (${(ready * 100).toInt()}%). Kreditur dan dewan tenang.", Tone.GOOD)
                chronicle += ChronicleItem(turn, "Serah terima mulus", "Penerus siap memimpin.", "Kaderisasi berhasil → kepercayaan terjaga.")
            }
        }
    }

    private fun eraTransitionReview(endingEra: Int, out: MutableList<ReportLine>) {
        val g = evaluateCampaign().totalScore
        eraGrades += g
        val label = when { g >= 75 -> "Cemerlang"; g >= 55 -> "Memadai"; else -> "Rapuh" }
        val effect = when {
            g >= 75 -> { creditShock = (creditShock - 0.25).coerceAtLeast(-0.25); "Kreditur dan pemasok memperlonggar syarat memasuki era berikutnya." }
            g >= 55 -> "Tidak ada perubahan syarat kredit."
            else -> { creditShock += 0.35; "Kreditur menuntut premi risiko lebih tinggi memasuki era berikutnya." }
        }
        val title = "Rapor Era $endingEra (${ERA_NAMES[endingEra - 1]}): $label ($g/100)"
        out += ReportLine("$title. $effect", if (g >= 75) Tone.GOOD else if (g >= 55) Tone.INFO else Tone.BAD)
        chronicle += ChronicleItem(turn, title, effect, "Kinerja era → rapor → syarat kredit era berikutnya.")
    }

    fun evaluateCampaign(): CampaignEvaluation {
        // 1. Nilai perusahaan riil disesuaikan inflasi (30%) — menggunakan Ekuitas Konsolidasi Grup
        val realEquityMultiple = (consolidatedEquity() / max(0.5, priceIndex)) / max(1000.0, initialEquity)
        val nilaiScore = ((realEquityMultiple - 0.5) * 65.0).roundToInt().coerceIn(0, 100)

        // 2. Ketahanan: krisis dilewati tanpa bailout / equity cure (20%)
        val baseKetahanan = 72 + crisesSurvived * 6 - (if (cureUsed) 22 else 0) - factoringUsedCount * 8 - covenantBreaches * 12 +
            (if (appointedSuccessorId != null) (successorReadiness * 10.0).roundToInt() else 0)
        val ketahananScore = baseKetahanan.coerceIn(0, 100)

        // 3. Kualitas keputusan: skor kalibrasi Brier & jurnal (20%)
        val brier = brierScore()
        val calBase = ((0.35 - brier) / 0.35 * 85.0).roundToInt() + min(15, journals.size * 3) + min(10, totalCrossChecks * 2) + predMarket.scoreBonus()
        val kualitasScore = calBase.coerceIn(0, 100)

        // 4. Reputasi dan skor etika (15%)
        val etikaScore = (100.0 - ethicsLoad * 16.0 - scandalsTriggered * 24.0).roundToInt().coerceIn(0, 100)

        // 5. Dampak sosial: lapangan kerja, upah, dan kepatuhan lingkungan/IPAL & karbon (15%)
        val jobGrowth = (headcountProd() + headcountStaff()).toDouble() / max(1, (sc.workers * 1.26).toInt())
        val sosialScore = (55.0 + (jobGrowth - 1.0) * 70.0 + (if (esgCompliant) 15.0 else 0.0) + (if (wageAddOn > 0) 10.0 else 0.0) + min(10, carbonCredits)).roundToInt().coerceIn(0, 100)

        val total = (0.30 * nilaiScore + 0.20 * ketahananScore + 0.20 * kualitasScore + 0.15 * etikaScore + 0.15 * sosialScore).roundToInt().coerceIn(0, 100)
        val grade = when {
            gameOverReason != null && !isVictory -> "F"
            total >= 85 -> "S"
            total >= 75 -> "A"
            total >= 65 -> "B"
            total >= 50 -> "C"
            else -> "D"
        }
        val activeSubsCount = subsidiaries.values.count { it.active && !it.nationalized }
        val archetype = when {
            gameOverReason != null && !isVictory -> when {
                gameOverReason!!.contains("Penjara") || gameOverReason!!.contains("Kejaksaan") -> "Oligarki Tumbang di Penjara"
                gameOverReason!!.contains("Dewan") -> "CEO yang Digulingkan Dewan"
                gameOverReason!!.contains("menyita") -> "Korban Penyitaan Covenant Bank"
                else -> "Korban Jebakan Akrual & Krisis Kas"
            }
            activeSubsCount >= 2 && etikaScore >= 75 && total >= 75 -> "Arsitek Institusi Multinasional Bersih"
            activeSubsCount >= 2 && etikaScore < 55 -> "Konglomerat Lintas Batas Abu-Abu"
            total >= 75 -> "Nakhoda Industri Tangguh"
            total >= 55 -> "Manajer Bertahan Hidup"
            else -> "Pengelola di Tepi Jurang"
        }
        val summary = "Skor Kampanye $total/100 ( Kelas $grade · $archetype ) — " +
            "Nilai Riil $nilaiScore · Ketahanan $ketahananScore · Kalibrasi $kualitasScore · Etika $etikaScore · Sosial $sosialScore."
        return CampaignEvaluation(nilaiScore, ketahananScore, kualitasScore, etikaScore, sosialScore, total, grade, archetype, summary)
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
                    hedgeCottonIdx = commodities.cotton.index * 1.02
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
                val target = (priceAdjPct.toLong() + action.deltaPct.toLong()).coerceIn(-15L, 15L).toInt() // Long: Int.MAX_VALUE tidak boleh meluap dan memutar tanda
                if (target != priceAdjPct && spend(1)) { priceAdjPct = target; true } else false
            }
            is Action.SetDifficulty -> {
                // Hanya sah sebagai tindakan pertama permainan; mengganti kesulitan di tengah jalan = curang.
                if (actionCount == 0 && turn == sc.turn0) { difficulty = action.difficulty; true } else false
            }
            is Action.OpenSubsidiary -> eraBlock(2) == null && doOpenSubsidiary(action.countryId)
            is Action.SetTransferPricing -> {
                if (eraBlock(2) == null && action.mode != transferPricingMode && transferPricingVeto(action.mode) == null && spend(1)) {
                    transferPricingMode = action.mode
                    if (action.mode == TransferPricingMode.AGRESIF) {
                        recordEthicsEvidence("transfer_pricing", 1.5, "exec_cfo", "meridia")
                    }
                    chronicle += ChronicleItem(turn, "Kebijakan Transfer Pricing: ${action.mode.label}",
                        action.mode.description,
                        "Perubahan kebijakan pajak lintas batas → menggeser basis laba kena pajak antar-yurisdiksi.", actionCount)
                    true
                } else false
            }
            is Action.Repatriate -> doRepatriate(action.countryId)
            Action.HedgeFx -> {
                if (!fxHedgeActive && spend(1)) {
                    val intDiff = max(0.0, policyRate - foreign.getValue("aurelia").policyRate)
                    val premiumRs = (45.0 + 10.0 * intDiff) * sc.size
                    hedgedKurs = kurs * (1.0 + intDiff / 2400.0)
                    fxHedgeUntilTurn = turn + 6
                    oneOffOpex += premiumRs
                    chronicle += ChronicleItem(turn, "Kontrak Forward Valas RS/A$ 6 Bulan",
                        "Mengunci kurs lindung nilai pada ${fmt(hedgedKurs)} RS/A$ selama 6 bulan (premi ${fmt(premiumRs)} jt RS sesuai selisih bunga).",
                        "Forward valas → melindungi utang A$ dan biaya impor dari kejatuhan Rial.", actionCount)
                    true
                } else false
            }
            is Action.AcquireRival -> eraBlock(2) == null && doAcquireRival(action.rivalId)
            is Action.BorrowForeignSub -> eraBlock(2) == null && doBorrowForeignSub(action.countryId, action.amountLocal)
            is Action.IssueCorporateBond -> eraBlock(3) == null && doIssueCorporateBond(action.amountRs, action.tenorMonths)
            is Action.Buyback -> isPublicCompany && action.amountRs in 100..20000 && pendingBuyback == 0.0 &&
                cash - action.amountRs >= 3000.0 * sc.size * costIndex && spend(1).also { if (it) pendingBuyback = action.amountRs.toDouble() }
            is Action.SellSubStake -> eraBlock(2) == null && doSellSubStake(action.countryId, action.pct, action.askDiscountPct)
            is Action.IssueShares -> eraBlock(3) == null && doIssueShares(action.amountRs)
            is Action.ExecuteIpo -> eraBlock(3) == null && ipoBlock() == null && doExecuteIpo(action.floatPct, action.dualClassShares)
            Action.OpenCaptiveBank -> eraBlock(3) == null && doOpenCaptiveBank()
            is Action.MediaLobby -> eraBlock(3) == null && doMediaLobby(action.kind)
            is Action.ChainAudit -> eraBlock(2) == null && doChainAudit()
            is Action.CarbonAction -> eraBlock(4) == null && doCarbonAction(action.kind, action.units)
            is Action.AppointSuccessor -> eraBlock(4) == null && doAppointSuccessor(action.candidateId)
            is Action.BuyIntel -> doBuyIntel(action.kind, action.subject, action.tier)
            is Action.BoardAnswer -> doBoardAnswer(action.claim)
            is Action.SetSegmentFocus -> doSetSegmentFocus(action.segment, action.level)
            is Action.HireStaff -> doHireStaff(action.role, action.tier)
            is Action.FireStaff -> doFireStaff(action.role)
            is Action.RaiseSalary -> doRaiseSalary(action.role)
            is Action.SetRdLevel -> doSetRdLevel(action.level)
            is Action.SetLogistics -> doSetLogistics(action.level)
            is Action.SetInsurance -> doSetInsurance(action.level)
            is Action.SetTradeFinance -> doSetTradeFinance(action.on)
            is Action.SaleLeaseback -> doSaleLeaseback(action.pct)
            is Action.BuyDerivative -> doBuyDerivative(action.kind, action.pct)
            is Action.WindowDress -> doWindowDress(action.pct)
            is Action.SetAuditor -> doSetAuditor(action.level)
            is Action.OpenSpv -> doOpenSpv(action.amountRs)
            is Action.Predict -> predMarket.bet(action.qid, action.yes, action.stake)
        }
        if (ok) actionCount++
        return ok
    }

    // =====================================================================
    // Era 2–4: Anak Usaha, Repatriasi, Pembiayaan Lokal, M&A, IPO, Obligasi, Karbon & Suksesi
    // =====================================================================
    fun subsidiarySpec(countryId: String): SubsidiarySpec? = SUBSIDIARIES.firstOrNull { it.countryId == countryId }

    fun subsidiaryCostRs(spec: SubsidiarySpec): Double = spec.baseCostRs * sc.size * costIndex

    /** Fitur terkunci sampai era tertentu (sesuai dokumen desain: Era 2 lintas batas, Era 3 finansialisasi, Era 4 kekaisaran global). */
    fun eraBlock(minEra: Int): String? {
        if (era(turn) >= minEra || earlyEra >= minEra) return null
        val startTurn = ERA_START_TURNS[minEra - 1]
        val early = if (BalanceTuning.performanceUnlock && minEra == max(era(turn), earlyEra) + 1) {
            val c = eraCriteria(minEra)
            " Atau terbuka lebih awal bila semua syarat kinerja terpenuhi: " + c.joinToString("; ") { (if (it.met) "[ok] " else "[belum] ") + it.label + " (" + it.progress + ")" } + "."
        } else ""
        return "Terkunci sampai Era $minEra (${ERA_NAMES[minEra - 1]}), ${dateText(startTurn)}.$early"
    }

    /** Era tertinggi yang fiturnya sudah dibuka lebih awal karena kinerja (kait permanen; kalender tetap jaring pengaman: fitur tidak pernah terkunci melewati tanggal era). */
    var earlyEra = 0; private set

    class EraCriterion(val label: String, val progress: String, val met: Boolean)

    /** Syarat membuka fitur era [target] sebelum tanggal kalendernya. Bukan hukuman: pemain yang tidak memenuhinya tetap mendapat fitur pada tanggal era. */
    fun eraCriteria(target: Int): List<EraCriterion> {
        val minTurn = ERA_START_TURNS[target - 2] + 36
        val ttm = revenueHist.takeLast(12).sum()
        val base = 12.0 * sc.baseRevenue
        val revMult = if (revenueHist.size >= 12 && base > 1.0) (ttm / max(0.5, priceIndex)) / base else 0.0
        val eqMult = (consolidatedEquity() / max(0.5, priceIndex)) / max(1000.0, initialEquity)
        val needRev = listOf(1.15, 1.4, 1.8)[target - 2]
        val needEq = listOf(1.15, 1.5, 2.0)[target - 2]
        val needSubs = listOf(0, 1, 3)[target - 2]
        val subs = subsidiaries.values.count { it.active && !it.nationalized }
        val out = ArrayList<EraCriterion>()
        out += EraCriterion("sudah berjalan sampai ${dateText(minTurn)}", "sekarang ${dateText(turn)}", turn >= minTurn)
        out += EraCriterion("pendapatan 12 bulan riil minimal ${fmt2(needRev)}x basis awal", "sekarang ${fmt2(revMult)}x", revMult >= needRev)
        out += EraCriterion("ekuitas konsolidasi riil minimal ${fmt2(needEq)}x awal", "sekarang ${fmt2(eqMult)}x", eqMult >= needEq)
        out += EraCriterion("laba operasi 12 bulan positif", if (ebitHist.size >= 12) fmt(ebitHist.takeLast(12).sum()) + " jt" else "riwayat kurang dari 12 bulan",
            ebitHist.size >= 12 && ebitHist.takeLast(12).sum() > 0.0)
        out += EraCriterion("tanpa pelanggaran covenant yang berjalan", "$covenantBreaches pelanggaran", covenantBreaches == 0)
        if (needSubs > 0) out += EraCriterion("anak usaha aktif minimal $needSubs", "sekarang $subs", subs >= needSubs)
        return out
    }

    /** Dipanggil tiap akhir bulan; membuka era berikutnya lebih awal bila semua syarat terpenuhi. Tanpa efek acak, jadi mesin tetap deterministik. */
    private fun checkEarlyUnlock(out: MutableList<ReportLine>) {
        if (!BalanceTuning.performanceUnlock) return
        val target = max(era(turn + 1), earlyEra) + 1
        if (target > 4) return
        if (!eraCriteria(target).all { it.met }) return
        earlyEra = target
        val title = "Fitur Era $target (${ERA_NAMES[target - 1]}) terbuka lebih awal"
        out += ReportLine("$title karena kinerja perusahaan memenuhi semua syarat.", Tone.GOOD)
        chronicle += ChronicleItem(turn, title, "Syarat pendapatan, ekuitas, laba, covenant dan entitas terpenuhi sebelum tanggal kalender.", "Kinerja kuat → fitur era berikutnya dibuka lebih awal.")
    }

    /** IPO butuh rekam jejak laba (minimal 12 bulan riwayat dan EBIT rata-rata 6 bulan positif). */
    fun ipoBlock(): String? {
        if (isPublicCompany) return "Perusahaan sudah tercatat di bursa."
        if (ebitHist.size < 12 || ebitHist.takeLast(6).average() <= 0.0) return "Bursa menolak: butuh laba operasi positif rata-rata 6 bulan terakhir."
        return null
    }

    fun subsidiaryBlock(countryId: String): String? {
        eraBlock(2)?.let { return it }
        val spec = subsidiarySpec(countryId) ?: return "Negara tujuan anak usaha tidak tersedia."

        if (subsidiaries.containsKey(countryId)) {
            val st = subsidiaries.getValue(countryId)
            return if (st.nationalized) "Aset di ${spec.countryName} telah dibekukan/dinasionalisasi."
            else if (st.active) "Anak usaha di ${spec.countryName} sudah beroperasi."
            else "Pembangunan anak usaha di ${spec.countryName} sedang berjalan."
        }
        if (ap < spec.apCost) return "AP tidak cukup (butuh ${spec.apCost} AP)."
        val firstMonthCost = subsidiaryCostRs(spec) / spec.buildMonths
        if (cash - firstMonthCost < 1500.0) return "Kas tidak cukup untuk tahap pertama (${fmt(firstMonthCost)} jt RS)."
        return null
    }

    private fun doOpenSubsidiary(countryId: String): Boolean {
        val spec = subsidiarySpec(countryId) ?: return false
        if (subsidiaryBlock(countryId) != null) return false
        spend(spec.apCost)
        val totalCost = subsidiaryCostRs(spec)
        val sub = SubsidiaryState(spec, turn)
        // Faktor kecocokan pasar lokal deterministik dari (seed, countryId):
        // ~28% kasus menghadapi hambatan struktural lokal (permintaan jauh di bawah rencana: rugi nyata), ~72% beroperasi normal
        val rSub = Random(seed * 65537L + countryId.hashCode().toLong() * 997L + 13L)
        val roll = rSub.nextDouble()
        sub.localDemandFactor = if (roll < 0.28) {
            0.40 + rSub.nextDouble() * 0.16
        } else {
            0.90 + rSub.nextDouble() * 0.28
        }
        subsidiaries[countryId] = sub
        projectsInv += InvestProject("sub_$countryId", turn, spec.buildMonths, totalCost, capex = true)
        chronicle += ChronicleItem(turn, "Pendirian Anak Usaha: ${spec.title}",
            "Investasi lintas negara di ${spec.countryName} (${spec.currencyCode}) senilai ${fmt(totalCost)} jt RS selama ${spec.buildMonths} bulan.",
            "Ekspansi Era 2 → entitas anak usaha ${spec.countryName} dengan pembukuan lokal → konsolidasi grup.", actionCount)
        return true
    }

    /**
     * Repatriasi dividen dari anak usaha ke induk:
     * - Mengurangi kas lokal (`cashLocal`) dan laba ditahan lokal (`retainedEarningsLocal`) di pembukuan anak usaha.
     * - Pajak withholding lintas batas (`taxRs`) dicatat sebagai beban pajak (`pendingWithholdingTaxRs`), BUKAN beban operasi.
     * - Dalam konsolidasi grup, dividen antar-entitas (`grossRs`) dieliminasi penuh (bukan pendapatan grup).
     */
    private fun doRepatriate(countryId: String): Boolean {
        val sub = subsidiaries[countryId] ?: return false
        if (!sub.active || sub.nationalized || sub.retainedForeign <= 0.05) return false
        val meta = foreignMeta.getValue(countryId)
        if (meta.capitalControlTurnsLeft > 0) return false
        val divLocal = sub.retainedForeign
        val grossRs = divLocal * meta.fxToRs
        if (grossRs < 10.0) return false
        if (!spend(1)) return false

        val hasMeridia = subsidiaries["meridia"]?.let { it.active && !it.nationalized } == true
        val effWithholdingPct = if (hasMeridia && countryId != "meridia") meta.profile.withholdingTaxPct * 0.5 else meta.profile.withholdingTaxPct
        val taxRs = grossRs * (effWithholdingPct / 100.0)

        // Pembukuan lokal anak usaha: kas keluar pendanaan dividen & pengurangan laba ditahan lokal
        sub.cashLocal -= divLocal
        sub.retainedEarningsLocal -= divLocal
        sub.periodDivPaidLocal += divLocal
        sub.cumulativeDivGrossRs += grossRs
        sub.totalRepatriatedRs += (grossRs - taxRs)

        // Pembukuan induk: kas & ekuitas induk menerima dividen bruto (dieliminasi penuh di konsolidasi grup);
        // pajak withholding lintas batas diakui sebagai beban pajak (pendingWithholdingTaxRs), bukan beban operasi.
        val parentShare = 1.0 - sub.nciPct
        val parentRs = grossRs * parentShare
        cash += parentRs
        equity += parentRs
        periodSubDivInGrossRs += parentRs
        periodNciDivRs += grossRs - parentRs
        sub.periodDivParentLocal += divLocal * parentShare
        pendingWithholdingTaxRs += taxRs * parentShare
        nciWithheldRs += taxRs * sub.nciPct

        chronicle += ChronicleItem(turn, "Repatriasi Dividen dari ${sub.spec.countryName}",
            "Menarik dividen ${fmt2(divLocal)} jt ${sub.spec.currencyCode} (${fmt(grossRs)} jt RS); pajak withholding dividen ${fmt1(effWithholdingPct)}% (${fmt(taxRs)} jt RS)." +
                if (sub.nciPct > 0.0) " Pajak atas bagian minoritas ${fmt1(sub.nciPct * 100)}% (${fmt(taxRs * sub.nciPct)} jt RS) dipotong dari dividen mereka dan tidak membebani grup." else "",
            "Laba ditahan anak usaha → dividen kas lintas batas (dieliminasi pada konsolidasi) → pajak withholding diakui sebagai beban pajak.", actionCount)
        return true
    }

    /** Pinjaman bank lokal dalam mata uang anak usaha (Tahap 3.9: Pembiayaan multimata uang). */
    private fun doBorrowForeignSub(countryId: String, amountLocal: Int): Boolean {
        val sub = subsidiaries[countryId] ?: return false
        if (!sub.active || sub.nationalized || amountLocal <= 0) return false
        val meta = foreignMeta.getValue(countryId)
        val maxLocalDebt = sub.paidInCapitalLocal * 1.2
        val amt = if (amountLocal.toDouble() > maxLocalDebt && meta.fxToRs > 1.0) {
            amountLocal.toDouble() / meta.fxToRs
        } else {
            amountLocal.toDouble()
        }
        if (sub.bankDebtLocal + amt > maxLocalDebt) return false
        if (!spend(1)) return false
        sub.bankDebtLocal += amt
        sub.cashLocal += amt
        sub.periodNewDebtLocal += amt
        sub.monthlyDebtAmortLocal = sub.bankDebtLocal / 36.0
        chronicle += ChronicleItem(turn, "Pinjaman Bank Lokal ${sub.spec.countryName}",
            "Anak usaha ${sub.spec.title} menarik fasilitas kredit lokal ${fmt2(amt)} jt ${sub.spec.currencyCode}.",
            "Pembiayaan mata uang lokal → mencocokkan arus kas dan liabilitas valas (natural hedge).", actionCount)
        return true
    }

    fun acquireRivalCostRs(rivalId: String): Double {
        val rv = world.rivals.firstOrNull { it.id == rivalId } ?: return 999999.0
        val distressDisc = if (!rv.active) 0.55 else if (rv.weakMonths >= 4) 0.75 else 1.15
        return 16000.0 * rv.share0 * distressDisc * sc.size * costIndex
    }

    fun acquireRivalBlock(rivalId: String): String? {
        eraBlock(2)?.let { return it }
        val rv = world.rivals.firstOrNull { it.id == rivalId } ?: return "Pesaing tidak ditemukan."
        if (rv.active && rv.weakMonths < 3 && turn < sc.turn0 + 18) return "Pesaing masih sehat; tunggu saat mereka tertekan (rugi ≥3 bulan) atau setelah 18 bulan bermain."
        if (ap < 3) return "Butuh 3 AP untuk uji tuntas dan akuisisi."
        val cost = acquireRivalCostRs(rivalId)
        if (cash - cost < 1500.0) return "Kas tidak cukup untuk akuisisi (${fmt(cost)} jt RS)."
        if (rv.active && postMergerShare(rivalId) >= ANTITRUST_BLOCK_SHARE) return "Komisi persaingan akan memblokir: pangsa gabungan ${fmt1(postMergerShare(rivalId) * 100)}% (batas ${(ANTITRUST_BLOCK_SHARE * 100).toInt()}%)."
        return null
    }

    private fun relPriceNow(): Double = (1.0 + priceAdjPct / 100.0) * fxPassLevel()
    /** Pangsa pasar gabungan seandainya pesaing ini diakuisisi (pangsa sekarang ditambah pangsa pesaing). */
    fun postMergerShare(rivalId: String): Double = playerShare() + world.rivalShareOf(rivalId, relPriceNow())

    /**
     * Akuisisi Pesaing (M&A) dengan Uji Tuntas Forensik, Pengakuan Goodwill, dan Risiko Liabilitas Tersembunyi (Tahap 4).
     */
    private fun doAcquireRival(rivalId: String): Boolean {
        val rv = world.rivals.firstOrNull { it.id == rivalId } ?: return false
        if (acquireRivalBlock(rivalId) != null) return false
        val cost = acquireRivalCostRs(rivalId)
        if (!spend(3)) return false
        val fairPhysicalFa = cost * 0.68
        val acqGoodwill = cost - fairPhysicalFa
        pendingCapex += fairPhysicalFa
        goodwill += acqGoodwill
        cash -= acqGoodwill
        periodInvestOut += acqGoodwill
        val mergedShare = postMergerShare(rivalId)
        if (mergedShare >= ANTITRUST_REMEDY_SHARE) {
            val remedy = 0.06 * cost
            oneOffOpex += remedy
            chronicle += ChronicleItem(turn, "Syarat komisi persaingan",
                "Pangsa gabungan ${fmt1(mergedShare * 100)}% melewati ${(ANTITRUST_REMEDY_SHARE * 100).toInt()}%: komisi menyetujui dengan syarat. Biaya konsesi dan pelepasan aset ${fmt(remedy)} jt RS.",
                "Merger besar → telaah antimonopoli → persetujuan bersyarat → biaya konsesi.", actionCount)
        }
        rv.active = false
        extraLines += 1
        demandBoost += rv.share0 * 0.45
        acquiredRivalsCount++

        // Uji tuntas tidak pernah 100% sempurna: peluang deterministik menemukan tunggakan perawatan/pajak target
        val rDd = rng(71 + acquiredRivalsCount)
        val hiddenIssue = if (rDd.nextDouble() < 0.35) {
            val remediation = 280.0 * sc.size * costIndex
            oneOffOpex += remediation
            "Pasca-akuisisi ditemukan tunggakan perawatan mesin target sebesar ${fmt(remediation)} jt RS."
        } else {
            "Integrasi aset fisik berjalan lancar."
        }
        chronicle += ChronicleItem(turn, "Akuisisi Pesaing (M&A): ${rv.name}",
            "Harga akuisisi ${fmt(cost)} jt RS (Aset Tetap Wajar ${fmt(fairPhysicalFa)} jt RS + Goodwill ${fmt(acqGoodwill)} jt RS). $hiddenIssue",
            "Uji tuntas M&A → alokasi harga beli & goodwill di neraca → kapasitas +1 lini.", actionCount)
        return true
    }

    private val pendingBonds = mutableListOf<Triple<Double, Double, Int>>()

    /** Penerbitan Obligasi Korporasi (Era 3). */
    private fun doIssueCorporateBond(amountRs: Int, tenorMonths: Int): Boolean {
        if (amountRs < 1000 || amountRs > 50000 || tenorMonths < 12 || tenorMonths > 120) return false
        if (!spend(2)) return false
        val amt = amountRs.toDouble()
        val couponPct = (policyRate + 2.8 + covenantBreaches * 1.2).coerceIn(5.5, 16.0)
        val fee = amt * 0.012
        pendingBonds += Triple(amt, couponPct, tenorMonths)
        oneOffOpex += fee
        chronicle += ChronicleItem(turn, "Penerbitan Obligasi Korporasi ${fmt(amt)} jt RS",
            "Tenor $tenorMonths bulan (bullet maturity), kupon tetap ${fmt2(couponPct)}%, biaya emisi ${fmt(fee)} jt RS (cair akhir bulan ini).",
            "Pendanaan pasar modal Era 3 → kas masuk jangka panjang dengan pokok jatuh tempo sekaligus.", actionCount)
        return true
    }

    fun nciEquityRs(): Double =
        subsidiaries.values.filter { !it.nationalized && it.active }.sumOf { it.nciPct * it.equityLocal() * foreignMeta.getValue(it.spec.countryId).fxToRs }

    /** Porsi minoritas dari mana investor mendapat hak veto atas transaksi pihak terkait (B4c). */
    val NCI_VETO_PCT = 0.20

    /** Alasan transfer pricing agresif diveto oleh pemegang minoritas anak usaha, atau null. */
    fun transferPricingVeto(mode: TransferPricingMode): String? {
        if (mode != TransferPricingMode.AGRESIF) return null
        val s = subsidiaries.values.firstOrNull { it.active && !it.nationalized && it.nciPct >= NCI_VETO_PCT - 1e-9 } ?: return null
        return "Pemegang minoritas ${s.spec.countryName} (${fmt1(s.nciPct * 100)}%) memveto pengalihan laba ke SPV Meridia: transaksi pihak terkait merugikan mereka."
    }

    /** Nilai wajar 100% anak usaha (jt RS): yang lebih besar antara 70% ekuitas dan 9x laba tahunan. */
    fun subValueRs(countryId: String): Double {
        val sub = subsidiaries[countryId] ?: return 0.0
        val fx = foreignMeta.getValue(countryId).fxToRs
        return max(0.7 * sub.equityLocal() * fx, 9.0 * 12.0 * (sub.lastIncomeLocal["ni"] ?: 0.0) * fx)
    }

    /** Batas diskon investor (fraksi): 8%..28% dari aliran acak giliran ini, naik 6 poin bila anak usaha merugi, dan 5 poin bila kas induk menipis. */
    private fun nciReservation(countryId: String, sub: SubsidiaryState): Double {
        val idx = max(0, Countries.coreIds.indexOf(countryId))
        val base = 0.08 + 0.20 * rng(116 + idx).nextDouble()
        val loss = if ((sub.lastIncomeLocal["ni"] ?: 0.0) <= 0.0) 0.06 else 0.0
        val desperate = if (cash < 3000.0) 0.05 else 0.0
        return base + loss + desperate
    }

    /** Alasan penjualan saham minoritas ditolak (sebelum berunding), atau null. */
    fun nciSaleBlock(countryId: String, pct: Int, askDiscountPct: Int = 20): String? {
        val sub = subsidiaries[countryId] ?: return "Belum ada anak usaha di negara ini."
        if (!sub.active || sub.nationalized) return "Anak usaha belum aktif."
        if (sub.monthsActive < 12) return "Anak usaha perlu beroperasi 12 bulan dulu."
        if (pct !in 5..25) return "Porsi harus 5–25%."
        if (askDiscountPct !in 5..30) return "Diskon penawaran harus 5–30%."
        if (sub.nciPct + pct / 100.0 > 0.49 + 1e-9) return "Minoritas tidak boleh lebih dari 49%."
        if (turn < sub.nciCooldownUntil) return "Investor menolak penawaran terakhir; berunding lagi mulai giliran ${sub.nciCooldownUntil}."
        if (ap < 2) return "AP tidak cukup (butuh 2)."
        return null
    }

    /**
     * Jual saham minoritas anak usaha ke investor luar (kepentingan non-pengendali), dengan tawar-menawar: investor menerima
     * bila diskon yang ditawarkan sudah mencapai batasnya (tidak diketahui; 8–34%), jika tidak ia menolak dan menghilang 3 bulan.
     * Biaya transaksi 3%. Dana masuk sebagai ekuitas induk. Bagian minoritas 20% atau lebih memberi hak veto atas
     * transfer pricing agresif. Pembeli menerima dividen bruto; pajak tertahan atas bagiannya ditanggung pemegang minoritas.
     */
    private fun doSellSubStake(countryId: String, pct: Int, askDiscountPct: Int): Boolean {
        if (nciSaleBlock(countryId, pct, askDiscountPct) != null) return false
        val sub = subsidiaries.getValue(countryId)
        if (!spend(2)) return false
        val reservation = nciReservation(countryId, sub)
        val value = subValueRs(countryId)
        val gross = value * pct / 100.0
        if (gross <= 0.0) { sub.nciCooldownUntil = turn + 3; return true }
        if (askDiscountPct / 100.0 + 1e-12 < reservation) {
            sub.nciCooldownUntil = turn + 3
            chronicle += ChronicleItem(turn, "Investor menolak harga saham ${sub.spec.countryName}",
                "Diskon ${askDiscountPct}% dari nilai wajar dianggap terlalu sempit; investor mundur tiga bulan.",
                "Tawar-menawar → penawaran terlalu mahal → ditolak → jeda sebelum bisa berunding lagi.", actionCount)
            return true
        }
        val net = gross * (1.0 - askDiscountPct / 100.0) * 0.97
        sub.nciPct += pct / 100.0
        cash += net; equity += net; periodCureIn += net; nciSaleProceedsTotal += net
        chronicle += ChronicleItem(turn, "Jual $pct% saham anak usaha ${sub.spec.countryName}",
            "Investor luar membeli $pct% seharga ${fmt(net)} jt RS (diskon ${askDiscountPct}% dari nilai wajar, bersih biaya 3%). Mereka kini berhak atas ${fmt1(sub.nciPct * 100)}% laba dan dividen anak usaha ini." +
                if (sub.nciPct >= NCI_VETO_PCT - 1e-9) " Dengan porsi sebesar ini mereka memegang hak veto atas transfer pricing agresif." else "",
            "Penjualan saham minoritas → kas masuk sebagai ekuitas → laba & dividen dibagi dengan kepentingan non-pengendali.", actionCount)
        return true
    }

    private var lastSpoTurn = -99
    /** Penerbitan saham lanjutan (SPO): kas masuk, pendiri terdilusi. */
    private fun doIssueShares(amountRs: Int): Boolean {
        if (!isPublicCompany || amountRs !in 500..30000 || turn - lastSpoTurn < 12 || stockPrice <= 0.0) return false
        if (!spend(2)) return false
        val net = amountRs * 0.96
        val issuePrice = stockPrice * 0.95
        val newShares = amountRs / issuePrice
        sharesOutM += newShares
        founderSharePct = (founderSharesM / sharesOutM * 100.0).coerceAtMost(100.0)
        cash += net; equity += net; periodCureIn += net
        lastSpoTurn = turn
        stockSentiment -= 0.04
        chronicle += ChronicleItem(turn, "Penerbitan saham lanjutan (SPO)",
            "Menerbitkan ${fmt1(newShares)} juta lembar di ${fmt(issuePrice)} RS (diskon 5%); dana bersih ${fmt(net)} jt RS. Porsi pendiri turun ke ${fmt1(founderSharePct)}%.",
            "Ekuitas baru → kas bertambah → kepemilikan pendiri terdilusi.", actionCount)
        return true
    }

    /** Penawaran Umum Perdana Saham / IPO (Era 3). */
    private fun doExecuteIpo(floatPct: Int, useDualClass: Boolean): Boolean {
        if (isPublicCompany || floatPct !in 10..45 || stuffingActive) return false
        if (!spend(3)) return false
        val govDiscount = if (useDualClass) 0.92 else 1.0
        val valuation = max(consolidatedEquity() * 1.35, (ebitHist.takeLast(3).sum() * 4.0) * 6.5) * govDiscount
        val grossProceeds = valuation * (floatPct / 100.0)
        val ipoFee = grossProceeds * 0.045
        val netProceeds = grossProceeds - ipoFee
        isPublicCompany = true
        dualClassShares = useDualClass
        founderSharePct = (100.0 - floatPct).coerceIn(50.0, 90.0)
        sharesOutM = 1000.0
        founderSharesM = sharesOutM * founderSharePct / 100.0
        stockPrice = valuation / sharesOutM
        stockSentiment = 0.0
        stockFairMc = valuation
        emaNi = max(0.0, ebitHist.takeLast(3).average() * 0.65)
        stockHistory += stockPrice
        cash += netProceeds
        equity += netProceeds
        periodCureIn += netProceeds
        chronicle += ChronicleItem(turn, "IPO di Bursa Efek Sundara ($floatPct% Saham Publik)",
            "Dana bersih IPO ${fmt(netProceeds)} jt RS masuk ke kas dan ekuitas (${if (useDualClass) "Saham Kelas Ganda aktif" else "Satu saham satu suara"}).",
            "IPO Era 3 → modal ekuitas meningkat drastis → kewajiban keterbukaan publik.", actionCount)
        return true
    }

    /** Mendirikan Unit Bank / Asuransi Grup Sendiri (Era 3). */
    private fun doOpenCaptiveBank(): Boolean {
        if (captiveBankActive) return false
        val minCapital = 3200.0 * sc.size * costIndex
        if (cash - minCapital < 1500.0 || !spend(3)) return false
        captiveBankActive = true
        captiveBankCapitalRs = minCapital
        cash -= minCapital
        periodInvestOut += minCapital
        chronicle += ChronicleItem(turn, "Pendirian Unit Jasa Keuangan & Asuransi Grup",
            "Modal inti disetor ${fmt(minCapital)} jt RS (CAR ≥ 12%). Menghasilkan margin bunga bersih namun terpapar risiko kredit saat resesi.",
            "Finansialisasi Era 3 → bank/asuransi internal → pendapatan bunga & risiko likuiditas.", actionCount)
        return true
    }

    /** Uji tuntas rantai pasok sampai Tier-3 (Era 2+): berbiaya, membuka simpul tersembunyi, meredam dampak sanksi (tidak pernah 100%). */
    private fun doChainAudit(): Boolean {
        if (dunia.sectors.chainAudited) return false
        if (!spend(2)) return false
        val cost = 180.0 * sc.size * costIndex
        if (cash - cost < 1200.0) return false
        oneOffOpex += cost
        dunia.auditChain()
        val hidden = dunia.sectors.chain.filter { it.hidden }.joinToString("; ") { "Tier-${it.tier} ${it.label} (${Countries.byId.getValue(it.country).name})" }
        chronicle += ChronicleItem(turn, "Uji Tuntas Rantai Pasok Tier-3",
            "Biaya ${fmt(cost)} jt RS. Simpul tersembunyi terungkap: $hidden. Pasokan bisa dialihkan lebih cepat bila sanksi menyentuhnya, tetapi uji tuntas tidak pernah 100% akurat.",
            "Uji tuntas → paparan Tier-2/3 terlihat → dampak sanksi lebih kecil.", actionCount)
        return true
    }


    // =====================================================================
    // B1: Intelijen berbayar dan rapat dewan kuartalan (lihat Intel.kt)
    // =====================================================================
    val intelReports = mutableListOf<IntelReport>()
    private var intelSeq = 0
    var boardQuestion: BoardQuestion? = null; private set
    private var boardQuestionSeq = 0
    var boardCorrect = 0; private set
    var boardWrong = 0; private set
    var boardAbstained = 0; private set
    var boardIgnored = 0; private set
    private var boardAbstainStreak = 0

    /** Daftar subjek yang bisa diriset untuk suatu jenis laporan (kode, label). */
    fun intelSubjects(kind: IntelKind): List<Pair<String, String>> = when (kind) {
        IntelKind.SEKTOR -> Sector.values().map { it.name to it.label }
        IntelKind.NEGARA -> Countries.coreIds.filter { it != "sundara" }.map { it to Countries.byId.getValue(it).name }
        IntelKind.PESAING -> world.rivals.map { it.id to it.name }
        IntelKind.SEGMEN -> Segment.values().map { it.code to it.label }
    }

    /** Nilai sebenarnya yang disembunyikan dari pemain; hanya dipakai untuk menurunkan perkiraan berderau. */
    private fun intelTruth(kind: IntelKind, subject: String): Triple<Double, Double, Pair<String, String>>? = when (kind) {
        IntelKind.SEKTOR -> Sector.values().firstOrNull { it.name == subject }?.let {
            Triple(dunia.sectors.availability[it.ordinal], 1.0, "ketersediaan pasokan (1,0 = penuh)" to "indeks")
        }
        IntelKind.NEGARA -> if (subject != "sundara" && Countries.coreIds.contains(subject))
            Triple(dunia.geo.tension("sundara", subject), 50.0, "ketegangan dengan Sundara (0–100)" to "poin") else null
        IntelKind.PESAING -> world.rivals.firstOrNull { it.id == subject }?.let {
            Triple(it.unitCost, 0.5, "biaya satuan relatif (1,0 = harga pasar awal)" to "rasio")
        }
        IntelKind.SEGMEN -> Segment.of(subject)?.let {
            Triple(market.trend[it.ordinal] * market.secular(it, turn), 0.25, "indeks permintaan segmen (1,0 = normal)" to "indeks")
        }
    }

    /** Biaya riset tingkat [tier] dalam jt RS pada skala usaha dan indeks biaya saat ini. */
    fun intelCostRs(tier: Int): Double = IntelTier.cost(tier) * sc.size * costIndex

    private fun doBuyIntel(kindCode: String, subject: String, tier: Int): Boolean {
        val kind = IntelKind.of(kindCode) ?: return false
        if (tier !in 1..3) return false
        val (truth, scale, meta) = intelTruth(kind, subject) ?: return false
        val cost = intelCostRs(tier)
        if (cash - cost < 1200.0 || !spend(1)) return false
        oneOffOpex += cost
        val sd = IntelTier.errorSd(tier) * scale
        val r = Random(seed * 1_000_003L + intelSeq * 7_919L + turn * 104_729L + 110L)
        intelSeq++
        var est = truth + sd * gaussian(r)
        est = when (kind) {
            IntelKind.SEKTOR -> est.coerceIn(0.0, 1.2)
            IntelKind.NEGARA -> est.coerceIn(0.0, 100.0)
            IntelKind.SEGMEN -> est.coerceIn(0.3, 1.8)
            else -> est.coerceAtLeast(0.1)
        }
        val label = intelSubjects(kind).firstOrNull { it.first == subject }?.second ?: subject
        intelReports += IntelReport(intelSeq, kind, subject, label, meta.first, turn, tier, cost, est, sd, meta.second)
        chronicle += ChronicleItem(turn, "${kind.label}: $label",
            "Anggaran ${fmt(cost)} jt RS (tingkat $tier). Perkiraan ${meta.first}: ${fmt2(est)} ± ${fmt2(sd)}.",
            "Riset berbayar → perkiraan dengan margin error → keputusan lebih terinformasi (tidak pernah pasti).", actionCount)
        return true
    }

    private fun boardMetricNow(metric: String): Double = when (metric) {
        "ebit" -> ebitHist.takeLast(3).sum()
        "cash" -> cash
        else -> revenueHist.lastOrNull() ?: 0.0
    }

    private fun doBoardAnswer(claim: String): Boolean {
        val q = boardQuestion ?: return false
        if (q.answer != null || claim !in Board.CLAIMS) return false
        q.answer = claim
        return true
    }

    /** Dipanggil tiap tiga bulan pada akhir giliran: menilai jawaban rapat lalu dan mengajukan pertanyaan baru. */
    private fun boardMeeting(lines: MutableList<ReportLine>) {
        val q = boardQuestion
        if (q != null && q.dueTurn <= turn) {
            val actual = Board.classify(q.baseline, boardMetricNow(q.metric))
            val ans = q.answer
            when {
                ans == null -> {
                    boardIgnored++; boardTrust = (boardTrust - 4).coerceAtLeast(0)
                    lines += ReportLine("Dewan: pertanyaan ${q.member} tentang ${Board.METRICS.getValue(q.metric)} tidak Anda jawab. Kepercayaan dewan turun.", Tone.WARN)
                }
                ans == "abstain" -> {
                    boardAbstained++; boardAbstainStreak++
                    if (boardAbstainStreak >= 3) { boardTrust = (boardTrust - 3).coerceAtLeast(0); lines += ReportLine("Dewan mulai kesal: Anda terus mengelak ($boardAbstainStreak rapat berturut-turut).", Tone.WARN) }
                }
                ans == actual -> {
                    boardCorrect++; boardAbstainStreak = 0; boardTrust = (boardTrust + 6).coerceAtMost(100)
                    lines += ReportLine("Dewan: perkiraan Anda tentang ${Board.METRICS.getValue(q.metric)} ($ans) terbukti benar. Kepercayaan dewan naik.", Tone.GOOD)
                }
                else -> {
                    boardWrong++; boardAbstainStreak = 0; boardTrust = (boardTrust - 6).coerceAtLeast(0)
                    lines += ReportLine("Dewan: Anda memperkirakan ${Board.METRICS.getValue(q.metric)} $ans, kenyataannya $actual. Kepercayaan dewan turun.", Tone.BAD)
                }
            }
        }
        val r = rng(110)
        val member = Board.MEMBERS[r.nextInt(Board.MEMBERS.size)]
        val metric = Board.METRICS.keys.toList()[r.nextInt(Board.METRICS.size)]
        val desc = Board.METRICS.getValue(metric)
        val text = when (Board.MEMBERS.indexOf(member)) {
            0 -> "Angka kuartal ini sudah kami baca. Terus terang, ke mana arah $desc tiga bulan ke depan: naik, turun, atau datar?"
            1 -> "Catatan auditor menyoroti $desc. Berani Anda menjamin arahnya dalam tiga bulan: naik, turun, atau tetap?"
            2 -> "Pemegang saham pengendali ingin kepastian soal $desc. Apa taruhan Anda untuk kuartal depan?"
            else -> "Dana kami menimbang posisi. Jika $desc turun, kami keluar. Naik, turun, atau tetap kuartal depan?"
        }
        boardQuestionSeq++
        boardQuestion = BoardQuestion(boardQuestionSeq, member, text, metric, boardMetricNow(metric), turn, turn + 3)
    }


    // =====================================================================
    // B2: pasar produk, lima segmen konsumen (lihat ProductMarket.kt)
    // =====================================================================
    /** Alasan fokus segmen ditolak, atau null bila boleh. */
    fun segmentFocusBlock(segmentCode: String, level: Int): String? {
        val sg = Segment.of(segmentCode) ?: return "Segmen tidak dikenal."
        if (level !in 0..2) return "Tingkat fokus harus 0, 1, atau 2."
        val cur = market.level[sg.ordinal]
        if (level == cur) return "Segmen sudah pada tingkat itu."
        if (turn - market.lastChangeTurn[sg.ordinal] < ProductMarket.COOLDOWN) return "Segmen ini baru diubah; tunggu ${ProductMarket.COOLDOWN} bulan antar perubahan."
        val cap = ProductMarket.focusCap(era(turn))
        if (market.focusUsed() - cur + level > cap) return "Batas total fokus $cap sudah terpakai; turunkan segmen lain dulu."
        return null
    }

    private fun doSetSegmentFocus(segmentCode: String, level: Int): Boolean {
        if (segmentFocusBlock(segmentCode, level) != null) return false
        if (!spend(1)) return false
        val sg = Segment.of(segmentCode)!!
        market.level[sg.ordinal] = level
        market.lastChangeTurn[sg.ordinal] = turn
        chronicle += ChronicleItem(turn, "Fokus segmen: ${sg.label}",
            "Tingkat fokus menjadi $level (${listOf("diabaikan", "dilayani", "difokuskan")[level]}).",
            "Strategi segmen → cakupan permintaan dan merek → pendapatan; fokus menambah biaya pemasaran bulanan.", actionCount)
        return true
    }


    // =====================================================================
    // B3: operasi: karyawan kunci, R&D berkelanjutan, logistik (lihat Operations.kt)
    // =====================================================================
    private var hireSeq = 0
    private fun staffUnit(): Double = StaffEconomy.BASE_WAGE * sc.size * costIndex

    fun staffBlock(roleCode: String, action: String): String? {
        val role = StaffRole.of(roleCode) ?: return "Jabatan tidak dikenal."
        val st = staffPool.of(role)
        return when (action) {
            "hire" -> if (st.present) "Jabatan ini masih terisi." else null
            "fire" -> if (!st.present) "Jabatan ini kosong." else null
            else -> if (!st.present) "Jabatan ini kosong." else if (st.salaryMult >= StaffEconomy.MAX_SALARY_MULT - 1e-9) "Gaji sudah di batas 150% upah pasar." else null
        }
    }

    fun hireCostRs(tier: Int): Double = StaffEconomy.recruitMultiple(tier) * staffUnit()

    private fun doHireStaff(roleCode: String, tier: Int): Boolean {
        val role = StaffRole.of(roleCode) ?: return false
        if (tier !in 1..3 || staffBlock(roleCode, "hire") != null) return false
        val cost = hireCostRs(tier)
        if (cash - cost < 1200.0 || !spend(1)) return false
        oneOffOpex += cost
        val r = Random(seed * 1_000_003L + hireSeq * 7_919L + turn * 104_729L + 113L)
        hireSeq++
        val st = staffPool.hire(role, tier, r)
        chronicle += ChronicleItem(turn, "Rekrut: ${role.label}", "Biaya perekrut ${fmt(cost)} jt RS (tingkat $tier). Kesan wawancara skill ${fmt2(st.interviewEstimate)}; nilai asli baru terlihat setelah 3 bulan.",
            "Rekrutmen → kandidat berderau → produktivitas terukur kemudian.", actionCount)
        return true
    }

    private fun doFireStaff(roleCode: String): Boolean {
        val role = StaffRole.of(roleCode) ?: return false
        if (staffBlock(roleCode, "fire") != null) return false
        val st = staffPool.of(role)
        val sev = 6.0 * StaffEconomy.marketWage(st.skill) * st.salaryMult * staffUnit()
        if (cash - sev < 1200.0 || !spend(1)) return false
        oneOffOpex += sev
        staffPool.fire(role)
        chronicle += ChronicleItem(turn, "Pesangon: ${role.label}", "Pesangon ${fmt(sev)} jt RS; karyawan lain resah (loyalitas turun).",
            "Pemecatan → pesangon → jabatan kosong → efek hilang dan moral turun.", actionCount)
        return true
    }

    private fun doRaiseSalary(roleCode: String): Boolean {
        val role = StaffRole.of(roleCode) ?: return false
        if (staffBlock(roleCode, "raise") != null || !spend(1)) return false
        return staffPool.raise(role)
    }

    private fun doSetRdLevel(level: Int): Boolean {
        if (level !in 0..RdProgram.MAX_LEVEL || level == rd.level || !spend(1)) return false
        rd.setLevel(level)
        return true
    }

    private fun doSetLogistics(level: Int): Boolean {
        if (level !in 0..2 || level == logisticsLevel || !spend(1)) return false
        logisticsLevel = level
        return true
    }


    // =====================================================================
    // B4a: asuransi, L/C, sale-leaseback
    // =====================================================================
    /** Alasan sale-leaseback ditolak, atau null. Dijual pada nilai buku (tanpa laba/rugi); sewa diperlakukan sebagai beban operasi. */
    fun saleLeasebackBlock(pct: Int): String? {
        if (pct !in 5..20) return "Porsi harus 5–20% dari aset tetap."
        val proceeds = fixedAssets * pct / 100.0
        if (leasedBackRs + proceeds > LEASEBACK_MAX * (fixedAssets + leasedBackRs)) return "Total aset yang disewa kembali tidak boleh lebih dari ${(LEASEBACK_MAX * 100).toInt()}% aset tetap."
        if (ap < 2) return "AP tidak cukup (butuh 2)."
        return null
    }

    private fun doSetInsurance(level: Int): Boolean {
        if (level !in 0..2 || level == insuranceLevel || !spend(1)) return false
        insuranceLevel = level
        return true
    }

    private fun doSetTradeFinance(on: Boolean): Boolean {
        if (on == tradeFinance) return false
        // Mematikan L/C membuat utang dagang turun kembali; butuh kas untuk menutupnya.
        if (!on && cash < 0.35 * payables + 1500.0) return false
        if (!spend(1)) return false
        tradeFinance = on
        chronicle += ChronicleItem(turn, if (on) "L/C perdagangan diaktifkan" else "L/C perdagangan dimatikan",
            if (on) "Utang dagang diperpanjang dari 35 ke 50 hari; kas tertahan terlepas bertahap dengan biaya ${fmt(LC_FEE * sc.size * costIndex)} jt RS per bulan."
            else "Pemasok kembali dibayar dalam 35 hari; kas keluar bertahap.",
            "Pembiayaan perdagangan → utang dagang lebih panjang → kas lebih longgar, biaya bulanan.", actionCount)
        return true
    }

    private fun doSaleLeaseback(pct: Int): Boolean {
        if (saleLeasebackBlock(pct) != null || !spend(2)) return false
        val proceeds = fixedAssets * pct / 100.0
        pendingCapex -= proceeds          // arus masuk investasi sebesar nilai buku; aset tetap turun dengan jumlah sama
        leasedBackRs += proceeds
        chronicle += ChronicleItem(turn, "Sale-leaseback aset tetap",
            "Aset senilai ${fmt(proceeds)} jt RS dijual pada nilai buku lalu disewa kembali: sewa ${fmt(proceeds * LEASE_RATE)} jt RS per bulan, penyusutan berkurang. Tidak ada laba penjualan; agunan berkurang.",
            "Sale-leaseback → kas masuk sekarang → beban sewa tetap bulanan dan agunan kredit mengecil.", actionCount)
        return true
    }

    // =====================================================================
    // B6: aktor yang belajar: politisi pemeras, investor aktivis, eksekutif egois
    // =====================================================================
    private fun actorEvent(tpl: String, title: String, body: String, warning: String, options: List<EventOption>, window: Int, default: String): GameEvent =
        GameEvent("evt_${turn}_${eventCounter++}_$tpl", tpl, "Aktor", title, body, warning, options, turn, turn + window - 1, default)

    private fun eo(id: String, title: String, ap: Int, impact: String, advisor: String, tone: Tone, vararg outs: Outcome) =
        EventOption(id, title, ap, impact, advisor, tone, outs.toList())

    private fun raisePoliticianDemand() {
        val rev = max(1.0, lastIncome["revenue"] ?: sc.baseRevenue)
        val a = actors
        val amt = 0.18 * ActorTuning.fineScale * rev * (1.0 + a.politicianAppetite)
        val pFine = min(0.80, 0.30 + 0.12 * min(3.0, a.politicianGrudge) + 0.15 * a.politicianAppetite)
        val fine = 0.12 * ActorTuning.fineScale * rev * (1.0 + 0.5 * min(3.0, a.politicianGrudge))
        val lawyer = 200.0 * sc.size * costIndex
        val tone = if (a.paid > a.refused) "Anda pernah membayar; ia tahu Anda mudah ditekan." else if (a.refused > 0) "Anda pernah menolaknya; ia menaruh dendam." else "Ini permintaan pertamanya."
        val ev = actorEvent("pol_demand", "Politisi meminta \"uang pelicin\"",
            "Seorang politisi berpengaruh atas perizinan industri meminta ${fmt(amt)} jt RS sebagai \"sumbangan\". $tone Ia mengatur inspeksi, izin, dan tarif.",
            "Pemeras yang dibayar akan kembali dengan permintaan lebih besar dan lebih sering; yang ditolak membalas lewat inspeksi.",
            listOf(
                eo("a", "Tolak", 0, "Risiko inspeksi dengan denda hingga ${fmt(fine)} jt RS", "Mempertahankan integritas; peluang inspeksi ${(pFine * 100).toInt()}% dan dendam menumpuk.", Tone.NEUTRAL,
                    Outcome(pFine, "Inspeksi mendadak menemukan pelanggaran; denda ${fmt(fine)} jt RS.", listOf(Effect("fine", fine), Effect("actor", fine, 0, "pol_refuse"))),
                    Outcome(1.0 - pFine, "Tidak ada tindak lanjut kali ini.", listOf(Effect("actor", 0.0, 0, "pol_refuse")))),
                eo("b", "Bayar", 1, "Biaya ${fmt(amt)} jt RS; bukti suap tersimpan", "Cepat dan aman hari ini, tetapi menumbuhkan nafsu pemeras dan menambah bukti etika.", Tone.WARN,
                    Outcome(1.0, "Permintaan dibayar; izin lancar untuk sementara.", listOf(Effect("cost", amt), Effect("ethics", 1.2, 0, "bribe"), Effect("actor", 0.0, 0, "pol_pay")))),
                eo("c", "Laporkan ke Komisi Antikorupsi", 2, "Biaya hukum ${fmt(lawyer)} jt RS; kepercayaan dewan naik", "Menunjukkan kepatuhan; politisi itu menjadi musuh seumur hidup.", Tone.GOOD,
                    Outcome(1.0, "Laporan diterima; dewan menghargai sikap Anda, politisi itu murka.", listOf(Effect("cost", lawyer), Effect("board_trust", 4.0), Effect("actor", 0.0, 0, "pol_report"))))
            ), 4, "a")
        openEvents += ev; actors.onDemandRaised(turn)
    }

    private fun raiseActivist(stakePct: Double) {
        val cap = stockPrice * sharesOutM
        val stakeValue = cap * stakePct / 100.0
        val premium = 0.20 * stakeValue
        val legal = 300.0 * sc.size * costIndex
        val pWin = (boardTrust / 150.0).coerceIn(0.15, 0.60)
        val buyDemand = 0.08 * cap
        val ev = actorEvent("activist", "Investor aktivis mengambil ${"%.1f".format(stakePct)}% saham",
            "Sebuah dana aktivis membeli ${"%.1f".format(stakePct)}% saham Anda (${fmt(stakeValue)} jt RS) dan menuntut pembelian kembali saham ${fmt(buyDemand)} jt RS atau perubahan dewan.",
            "Aktivis mengincar perusahaan yang menimbun kas atau diperdagangkan di bawah nilai buku; yang pernah membayar greenmail menarik pemburu baru.",
            listOf(
                eo("a", "Lawan di rapat pemegang saham", 2, "Biaya ${fmt(legal)} jt RS; peluang menang ${(pWin * 100).toInt()}%", "Bergantung pada kepercayaan dewan; kalah berarti tuntutan dipaksakan.", Tone.WARN,
                    Outcome(pWin, "Pemegang saham berpihak pada manajemen; aktivis mundur.", listOf(Effect("cost", legal), Effect("actor", 0.0, 0, "act_win"))),
                    Outcome(1.0 - pWin, "Anda kalah suara: pembelian kembali dipaksakan dan saham tertekan.", listOf(Effect("cost", legal), Effect("buyback", buyDemand), Effect("sentiment", -0.08), Effect("actor", 0.0, 0, "act_lose")))),
                eo("b", "Penuhi tuntutan (buyback)", 1, "Pembelian kembali saham ${fmt(buyDemand)} jt RS dari kas", "Menenangkan aktivis; kas berkurang.", Tone.NEUTRAL,
                    Outcome(1.0, "Pembelian kembali disetujui; aktivis puas dan keluar.", listOf(Effect("buyback", buyDemand), Effect("actor", 0.0, 0, "act_accept")))),
                eo("c", "Greenmail: beli kembali saham mereka dengan premi", 1, "Premi ${fmt(premium)} jt RS di atas nilai saham ${fmt(stakeValue)} jt RS", "Mengusir aktivis sekarang, tetapi mengundang aktivis lain.", Tone.BAD,
                    Outcome(1.0, "Saham aktivis dibeli kembali dengan premi 20%.", listOf(Effect("buyback", stakeValue), Effect("cost", premium), Effect("actor", 0.0, 0, "act_greenmail"))))
            ), 4, "a")
        openEvents += ev; actors.onActivistRaised(turn)
    }

    private fun dressInternal(x: Double, severity: Double, label: String) {
        pendingIncome += x; pendingPrepay += x
        dressedRs += x; dressedFreshRs += x; dressedSinceCleanRs += x
        recordEthicsEvidence(label, severity, "exec_cfo", "sundara")
    }

    private fun marketObs(): MarketObs {
        val h = history
        var up = 0; var n = 0
        for (i in 6 until h.size) { n++; if (h[i].kurs > h[i - 6].kurs) up++ }
        // prior kerumunan: mata uang berinflasi tinggi cenderung melemah (75%), bobot setara 6 pengamatan
        val share = (up + 4.5) / (n + 6.0)
        return MarketObs(world.inflation, world.inflationExpectation, world.policyRate, world.gap * 100.0, kurs, share)
    }

    /** B7: selesaikan pertanyaan pasar prediksi yang jatuh tempo dan buka yang baru tiap 3 bulan. */
    private fun insightMonthly(lines: MutableList<ReportLine>) {
        val o = marketObs()
        for ((t, tone) in predMarket.resolveDue(turn, o)) lines += ReportLine(t, tone)
        if (gameOverReason == null && turn - sc.turn0 >= 3 && (turn - sc.turn0) % 3 == 0) predMarket.openNew(turn, o, rng(124))
    }

    /** B7: pola bias kognitif pemain dari jurnal, taruhan pasar, dan riwayat. */
    fun biasFindings(): List<BiasFinding> = BiasDetector.detect(journals, predMarket, history, totalCrossChecks, turn,
        spinningDecision == "approved" && spinningDelayed && turn - spinningStartTurn in 8..14)

    /** B7: post-mortem (penyebab, titik balik, kualitas keputusan, pelajaran). */
    fun postMortem(): PostMortem = PostMortemBuilder.build(gameOverReason, isVictory, history, chronicle, journals, predMarket, biasFindings(),
        scandalsTriggered, covenantBreaches, cureUsed, brierScore())

    private fun actorsMonthly(lines: MutableList<ReportLine>) {
        val a = actors
        a.decayGrudge()
        val ta = max(1.0, totalAssets())
        val ebitda = max(1.0, (ebitHist.takeLast(3).sum() + 3 * fixedAssets * 0.0081) * 4.0)
        a.profile.observe(priceAdjPct, lobbyCount, ethicsLoad, max(0.0, (totalDebt() - cash) / ebitda), cash / ta)
        if (gameOverReason != null || turn - sc.turn0 < 12) return

        // Politisi: peluang naik bila pernah dibayar (nafsu), mendekati pemilu; turun dengan lobi parlemen
        val r = rng(120)
        val election = if (electionTurn >= turn && electionTurn - turn <= 3) 0.03 else 0.0
        val pPol = max(0.0, 0.015 + 0.045 * a.politicianAppetite + election - 0.025 * min(1.0, lobbyCount / 2.0)) * ActorTuning.freqScale
        if (turn - a.lastDemandTurn >= 8 && openEvents.none { it.templateId == "pol_demand" } && openEvents.size < 3 && r.nextDouble() < pPol) raisePoliticianDemand()

        // Investor aktivis (hanya emiten): pemburu kas menganggur, saham di bawah nilai buku, dan pembayar greenmail
        if (isPublicCompany && stockPrice > 0.0) {
            val rAct = rng(121)
            val hoardExcess = min(0.20, max(0.0, cash / ta - 0.22) * 4.0)
            val lowPb = if (stockPrice * sharesOutM < 0.9 * equity) 0.04 else 0.0
            val pAct = (0.008 + 0.05 * hoardExcess + lowPb + 0.03 * a.profile.appeasement) * ActorTuning.activistScale
            val stake = 3.0 + 5.0 * rAct.nextDouble()
            if (turn - a.lastActivistTurn >= 18 && openEvents.none { it.templateId == "activist" } && openEvents.size < 3 && rAct.nextDouble() < pAct) raiseActivist(stake)
            // Serangan jual kosong (bear raid) pada perusahaan dengan leverage tinggi
            val rBear = rng(122)
            val lev = (totalDebt() - cash) / ebitda
            if (lev > 3.0 && rBear.nextDouble() < 0.04) {
                stockSentiment = (stockSentiment - 0.06).coerceIn(-0.9, 0.6)
                lines += ReportLine("Penjual kosong menyerang saham Anda dengan menyorot leverage ${"%.1f".format(lev)}x EBITDA. Harga tertekan.", Tone.WARN)
            }
        }

        // Eksekutif egois: yang loyalitasnya rendah mengambil jalan pintas yang menguntungkan dirinya
        val rEx = rng(123)
        for (e in execs) {
            if (!e.present || e.loyalty >= 0.40) continue
            val roll = rEx.nextDouble() / max(1e-9, ActorTuning.selfishScale)
            if (e.id == "exec_cfo" && auditorLevel == 0 && roll < 0.05 && ebitHist.size >= 7 && ebitHist.takeLast(3).sum() < ebitHist.takeLast(6).take(3).sum()) {
                val x = 0.10 * estimateMonth().getValue("opex")
                dressInternal(x, 0.6, "window_dressing")
                a.onSelfish()
                lines += ReportLine("${e.name} (CFO) diam-diam mempercantik laporan ${fmt(x)} jt RS demi bonus. Tanpa auditor, hal ini tidak terdeteksi.", Tone.WARN)
            } else if ((e.id == "exec_factory" || e.id == "exec_coo") && roll < 0.02) {
                val kick = 0.004 * (lastIncome["cogs"] ?: 0.0)
                oneOffOpex += kick
                recordEthicsEvidence("kickback", 0.25, e.id, "sundara")
                a.onSelfish()
                lines += ReportLine("${e.name} menerima komisi terselubung dari pemasok; biaya pasokan naik ${fmt(kick)} jt RS.", Tone.WARN)
            }
        }
    }

    // =====================================================================
    // B5: forensik: percantikan laporan, auditor eksternal, skor-M
    // =====================================================================
    fun auditFee(): Double = when (auditorLevel) { 1 -> 45.0; 2 -> 120.0; else -> 0.0 } * sc.size * costIndex

    /** Alasan percantikan ditolak, atau null. Batas: sisa terkapitalisasi tidak lebih dari 3x biaya operasi bulanan. */
    fun windowDressBlock(pct: Int): String? {
        if (pct != 10 && pct != 25 && pct != 50) return "Porsi harus 10, 25, atau 50% dari biaya operasi bulanan."
        val opex = estimateMonth().getValue("opex")
        if (dressedRs + opex * pct / 100.0 > 3.0 * opex) return "Biaya terkapitalisasi sudah terlalu besar; tunggu berbalik."
        if (ap < 2) return "AP tidak cukup (butuh 2)."
        return null
    }

    private fun doWindowDress(pct: Int): Boolean {
        if (windowDressBlock(pct) != null || !spend(2)) return false
        val x = estimateMonth().getValue("opex") * pct / 100.0
        pendingIncome += x           // beban operasi turun sebesar x
        pendingPrepay += x           // tetapi kas yang sama keluar dan menjadi persediaan (kas bersih tidak berubah)
        dressedRs += x; dressedFreshRs += x; dressedSinceCleanRs += x
        recordEthicsEvidence("window_dressing", 0.5 + pct / 50.0, "exec_cfo", "sundara")
        chronicle += ChronicleItem(turn, "Laporan dipercantik",
            "Biaya operasi ${fmt(x)} jt RS dikapitalisasi ke persediaan: laba bulan ini naik, tetapi berbalik sebagai beban dalam beberapa bulan. Kas tidak berubah, pajak dibayar lebih awal.",
            "Kapitalisasi biaya → laba dan ICR terlihat lebih baik → akrual naik (skor-M) → auditor atau penjual kosong dapat membongkarnya.", actionCount)
        return true
    }

    private fun doSetAuditor(level: Int): Boolean {
        if (level !in 0..2 || level == auditorLevel || !spend(1)) return false
        auditorLevel = level
        chronicle += ChronicleItem(turn, if (level == 0) "Auditor eksternal diberhentikan" else "Auditor eksternal ${if (level == 1) "regional" else "internasional"} ditunjuk",
            if (level == 0) "Tanpa auditor, kecurangan hanya terbongkar lewat saksi atau penyelidikan dan akibatnya jauh lebih besar." else "Biaya ${fmt(auditFee())} jt RS per bulan. Auditor memeriksa percantikan laporan tiap bulan dan akibat temuan lebih ringan daripada skandal.",
            "Auditor → pemeriksaan berkala → koreksi laporan dini dengan denda kecil.", actionCount)
        return true
    }

    private fun isHiddenSpv(l: Loan) = l.id.startsWith("spv_") && l.id !in spvDisclosedIds
    /** Saldo utang SPV yang masih tersembunyi (jt RS). */
    fun spvHiddenRs(): Double = loans.filter { isHiddenSpv(it) && it.balance > 0.5 }.sumOf { loanBalanceRs(it) }
    fun spvMonthlyInterest(): Double = loans.filter { isHiddenSpv(it) && it.balance > 0.5 }.sumOf { loanBalanceRs(it) * it.annualRatePct / 100.0 / 12.0 }
    /** Utang yang tampak bagi kreditur dan analis: utang nyata dikurangi SPV yang belum terbongkar. */
    fun reportedDebt(): Double = totalDebt() - spvHiddenRs()

    fun spvBlock(amount: Int): String? {
        eraBlock(3)?.let { return it }
        if (amount !in 1000..20000) return "Jumlah SPV 1.000–20.000 jt RS."
        if (pendingSpv.isNotEmpty()) return "Satu pencairan SPV pada satu waktu."
        if (spvHiddenRs() + amount > 0.30 * totalAssets()) return "SPV tersembunyi tidak boleh lebih dari 30% aset."
        if (ap < 2) return "AP tidak cukup (butuh 2)."
        return null
    }

    private fun doOpenSpv(amount: Int): Boolean {
        if (spvBlock(amount) != null || !spend(2)) return false
        pendingSpv += amount.toDouble() to (policyRate + 5.5)
        oneOffOpex += 0.03 * amount
        recordEthicsEvidence("spv", 1.5, "exec_cfo", "sundara")
        chronicle += ChronicleItem(turn, "Pendanaan lewat SPV",
            "Pinjaman ${fmt(amount.toDouble())} jt RS disalurkan lewat SPV di luar laporan resmi (biaya struktur 3%, bunga ${fmt1(policyRate + 5.5)}%). Kreditur melihat utang lebih kecil, tetapi utangnya nyata dan bisa terbongkar.",
            "SPV → utang tersembunyi → metrik kreditur membaik → terbongkar oleh auditor atau saat krisis → denda dan skandal.", actionCount)
        return true
    }

    /** Skor-M terkini (null sebelum 24 bulan riwayat) dan apakah ia menandai kemungkinan manipulasi. */
    fun mScore(): Double? = forensics.mScore()
    fun mScoreFlag(): Boolean = (forensics.mScore() ?: Double.NEGATIVE_INFINITY) > ForensicBook.THRESHOLD

    /** Balikkan sebagian biaya terkapitalisasi sebagai beban (sama dengan laju kembalinya persediaan ke target), lalu periksa auditor dan analis. */
    private fun forensicsMonthly(lines: MutableList<ReportLine>) {
        // bagian yang baru dikapitalisasi bulan ini belum berbalik (persediaan baru bergerak ke target bulan depan)
        val old = dressedRs - dressedFreshRs
        if (old > 0.005) {
            val u = 0.5 * old
            oneOffOpex += u
            dressedRs -= u
        }
        dressedFreshRs = 0.0
        if (dressedRs < 0.01) dressedRs = 0.0
        val r = rng(117)
        val flagged = mScoreFlag()
        if (auditorLevel > 0 && dressedSinceCleanRs > 0.01) {
            val p = 0.06 * auditorLevel * (if (flagged) 2.0 else 1.0)
            if (r.nextDouble() < p) {
                val fine = 0.30 * dressedSinceCleanRs
                oneOffOpex += fine
                restatements++
                boardTrust = (boardTrust - 6).coerceAtLeast(0)
                val kept = ethicsEvidences.filter { it.kind != "window_dressing" }
                val removed = ethicsEvidences.filter { it.kind == "window_dressing" }.sumOf { it.severity }
                ethicsEvidences.clear(); ethicsEvidences.addAll(kept)
                ethicsLoad = max(0.0, ethicsLoad - removed)
                lines += ReportLine("Auditor menemukan percantikan laporan senilai ${fmt(dressedSinceCleanRs)} jt RS dan mewajibkan koreksi (denda ${fmt(fine)} jt RS). Kepercayaan dewan turun.", Tone.BAD)
                chronicle += ChronicleItem(turn, "Koreksi laporan oleh auditor", "Kapitalisasi biaya yang tidak wajar dikoreksi; denda ${fmt(fine)} jt RS.",
                    "Percantikan laporan → temuan auditor → koreksi dini dengan denda kecil (lebih ringan daripada skandal).", actionCount)
                dressedSinceCleanRs = 0.0
            }
        }
        // Antimonopoli: pangsa dominan menarik penyelidikan berkala
        val share = playerShare()
        if (share >= ANTITRUST_WATCH_SHARE && turn >= antitrustNextTurn) {
            val pHit = (0.03 + (share - ANTITRUST_WATCH_SHARE) * 0.5) * (if (lobbyCount > 0) 0.6 else 1.0)
            if (rng(118).nextDouble() < pHit) {
                val fine = 0.35 * (lastIncome["revenue"] ?: 0.0)
                oneOffOpex += fine
                antitrustFines++; antitrustNextTurn = turn + 12
                boardTrust = (boardTrust - 8).coerceAtLeast(0)
                lines += ReportLine("Komisi persaingan menyelidiki dominasi pasar Anda (pangsa ${fmt1(share * 100)}%): denda ${fmt(fine)} jt RS.", Tone.BAD)
                chronicle += ChronicleItem(turn, "Denda antimonopoli", "Pangsa ${fmt1(share * 100)}% menarik penyelidikan; denda ${fmt(fine)} jt RS.",
                    "Pangsa dominan → penyelidikan → denda berkala (lobi parlemen menurunkan peluangnya).", actionCount)
            }
        }
        // Bukti menua: peluang bocor naik bersama umur (maks. 36 bulan), saksi yang sudah keluar melipatgandakannya; setelah 60 bulan bukti kedaluwarsa
        val rLeak = rng(119)
        for (ev in ethicsEvidences.toList()) {
            val age = turn - ev.turnCommitted
            if (age > 60) {
                ethicsEvidences.remove(ev); ethicsLoad = max(0.0, ethicsLoad - 0.3 * ev.severity)
                continue
            }
            val departed = execs.any { it.id == ev.witnessExecId && !it.present }
            val hazard = 0.0025 * ev.severity * (1.0 + min(age, 36) / 24.0) * (if (departed) 3.0 else 1.0) * (if (mediaOwned) 0.72 else 1.0)
            if (rLeak.nextDouble() < hazard) {
                val cost = 400.0 * sc.size * costIndex * ev.severity
                oneOffOpex += cost
                whistleblowerLeaks++
                boardTrust = (boardTrust - 10).coerceAtLeast(0)
                ethicsEvidences.remove(ev); ethicsLoad = max(0.0, ethicsLoad - ev.severity)
                lines += ReportLine("Pembocor mengungkap praktik lama ($age bulan lalu, ${ev.kind}): denda dan biaya hukum ${fmt(cost)} jt RS.", Tone.BAD)
                chronicle += ChronicleItem(turn, "Skandal tertunda terbongkar", "Bukti ${ev.kind} dari $age bulan lalu bocor; biaya ${fmt(cost)} jt RS.",
                    "Jalan pintas → bukti menua tersimpan → bocor bertahun kemudian → denda dan kepercayaan dewan turun.", actionCount)
            }
        }
        // SPV terbongkar: auditor, skor-M, atau uji covenant yang gagal memperbesar peluangnya
        val hidden = loans.filter { isHiddenSpv(it) && it.balance > 0.5 }
        if (hidden.isNotEmpty()) {
            val p = 0.02 + 0.03 * auditorLevel + (if (flagged) 0.03 else 0.0) + (if (covenantBreaches > 0) 0.04 else 0.0)
            if (r.nextDouble() < p) {
                val bal = spvHiddenRs()
                val fine = 0.20 * bal
                oneOffOpex += fine
                spvRevealed++; scandalsTriggered++
                boardTrust = (boardTrust - 20).coerceAtLeast(0)
                creditShock += 0.3
                hidden.forEach { spvDisclosedIds += it.id }
                recordEthicsEvidence("spv_exposed", 1.0, "exec_cfo", "sundara")
                lines += ReportLine("SPV terbongkar: utang tersembunyi ${fmt(bal)} jt RS kini terlihat. Denda ${fmt(fine)} jt RS, kreditur memperketat syarat, kepercayaan dewan anjlok.", Tone.BAD)
                chronicle += ChronicleItem(turn, "Skandal SPV", "Pendanaan di luar laporan resmi terbongkar; denda ${fmt(fine)} jt RS.",
                    "SPV → metrik kreditur menipu → terbongkar → denda, premi kredit naik, reputasi rusak.", actionCount)
            }
        }
        if (isPublicCompany && flagged && r.nextDouble() < 0.10) {
            shortReports++
            stockSentiment = (stockSentiment - 0.05).coerceIn(-0.9, 0.6)
            lines += ReportLine("Penjual kosong menerbitkan laporan forensik: skor-M Anda ${"%.2f".format(mScore() ?: 0.0)} di atas ambang. Harga saham tertekan.", Tone.WARN)
        }
    }

    // =====================================================================
    // B4b: derivatif lindung nilai (diselesaikan tunai)
    // =====================================================================
    /** Eksposur yang dapat dilindungi (jt RS): utang A$, pembelian kapas 6 bulan, atau utang RS berbunga mengambang. */
    fun derivExposure(kind: DerivKind): Double = when (kind) {
        // utang A$ ditambah porsi HPP yang peka kurs (bobot 0,2 pada tekanan biaya) selama 6 bulan
        DerivKind.FXOPT -> loans.filter { it.currencyAud }.sumOf { loanBalanceRs(it) } + (estimateMonth()["cogs"] ?: 0.0) * 0.20 * 6.0
        DerivKind.COTFUT -> (estimateMonth()["cogs"] ?: 0.0) * 0.50 * 0.78 * 6.0
        DerivKind.SWAP -> loans.filter { !it.currencyAud && it.floatingSpreadPct != null }.sumOf { it.balance }
    }

    /** Pokok yang akan dibeli untuk [pct] persen dari eksposur yang belum dilindungi. */
    fun derivNotional(kind: DerivKind, pct: Int): Double =
        max(0.0, derivExposure(kind) - derivs.filter { it.kind == kind }.sumOf { it.notional }) * pct / 100.0

    /**
     * Nilai harapan (fraksi pokok) dari lonjakan kurs terjadwal dalam 6 bulan ke depan yang dikenal pasar: krisis makro
     * (pasti bila peringatan dini sudah terbit, selain itu peluang prior) dan guncangan pemilu (peluang prior 60%, hasil tidak bocor).
     * Dengan ini harga opsi mencerminkan risiko kejadian sehingga membeli sebelum krisis bukan uang gratis.
     */
    fun fxJumpExpected(): Double {
        var e = 0.0
        for (cp in crisisPlans()) if (cp.start >= turn && cp.start <= turn + 5) {
            val p = if (turn >= cp.start - 3) 1.0 else 0.07
            e += p * max(0.0, 1.0 + cp.fxHit - DerivPricing.FX_STRIKE)
        }
        if (electionTurn >= turn && electionTurn <= turn + 5) e += 0.6 * max(0.0, 1.12 - DerivPricing.FX_STRIKE)
        return e
    }

    fun derivPremium(kind: DerivKind, pct: Int): Double {
        val n = derivNotional(kind, pct)
        return when (kind) {
            DerivKind.FXOPT -> n * DerivPricing.fxPremiumPct(DerivPricing.monthlyVol(kursHist), fxJumpExpected())
            DerivKind.COTFUT -> n * DerivPricing.COT_FEE
            DerivKind.SWAP -> n * DerivPricing.SWAP_FEE
        }
    }

    fun derivBlock(kindCode: String, pct: Int): String? {
        val kind = DerivKind.fromCode(kindCode) ?: return "Jenis derivatif tidak dikenal."
        if (pct != 25 && pct != 50 && pct != 100) return "Porsi harus 25, 50, atau 100% dari eksposur."
        if (derivs.any { it.kind == kind }) return "Kontrak ${kind.label} masih berjalan."
        val n = derivNotional(kind, pct)
        if (n < 100.0) return when (kind) {
            DerivKind.FXOPT -> "Tidak ada eksposur kurs yang cukup besar untuk dilindungi."
            DerivKind.COTFUT -> "Tidak ada pembelian kapas yang cukup besar untuk dilindungi."
            DerivKind.SWAP -> "Tidak ada utang RS berbunga mengambang yang perlu dilindungi."
        }
        if (cash < 2.0 * derivPremium(kind, pct)) return "Kas tidak cukup untuk membayar premi."
        if (ap < 1) return "AP tidak cukup (butuh 1)."
        return null
    }

    private fun doBuyDerivative(kindCode: String, pct: Int): Boolean {
        if (derivBlock(kindCode, pct) != null || !spend(1)) return false
        val kind = DerivKind.fromCode(kindCode)!!
        val n = derivNotional(kind, pct)
        val prem = derivPremium(kind, pct)
        val (strike, ref0) = when (kind) {
            DerivKind.FXOPT -> kurs * DerivPricing.FX_STRIKE to kurs
            DerivKind.COTFUT -> DerivPricing.cottonStrike(commodities.cotton.index) to commodities.cotton.index
            DerivKind.SWAP -> DerivPricing.swapFixedRate(policyRate, turn <= electionTurn && electionTurn <= turn + 12) to policyRate
        }
        val expiry = turn + kind.months - 1
        derivs += Deriv(kind, turn, expiry, n, strike, prem, ref0)
        oneOffOpex += prem
        derivNetRs -= prem; derivPremiumsRs += prem
        chronicle += ChronicleItem(turn, "Derivatif dibeli: ${kind.label}",
            "Pokok ${fmt(n)} jt RS, premi ${fmt(prem)} jt RS, ${kind.months} bulan, diselesaikan tunai. " + when (kind) {
                DerivKind.FXOPT -> "Membayar bila kurs naik lebih dari 3% (strike ${fmt(strike)})."
                DerivKind.COTFUT -> "Membayar bila indeks kapas melampaui ${"%.3f".format(strike)}, dan Anda membayar selisih bila di bawahnya."
                DerivKind.SWAP -> "Tetap ${"%.2f".format(strike)}% lawan suku bunga kebijakan bulanan."
            },
            "Derivatif → premi di muka → penyelesaian tunai saat jatuh tempo; lindung nilai, bukan spekulasi.", actionCount)
        return true
    }

    /** Penyelesaian derivatif pada akhir giliran (setelah kurs, kapas, dan suku bunga bulan ini diketahui). */
    private fun settleDerivatives(lines: MutableList<ReportLine>) {
        if (derivs.isEmpty()) return
        val done = mutableListOf<Deriv>()
        for (d in derivs) {
            when (d.kind) {
                DerivKind.SWAP -> {
                    val pay = d.notional * (policyRate - d.strike) / 100.0 / 12.0
                    pendingIncome += pay; derivNetRs += pay
                    if (turn >= d.expiryTurn) {
                        done += d
                        lines += ReportLine("Swap bunga berakhir; penyelesaian terakhir ${if (pay >= 0) "+" else ""}${fmt(pay)} jt RS.", Tone.INFO)
                    }
                }
                DerivKind.FXOPT -> if (turn >= d.expiryTurn) {
                    done += d
                    val pay = d.notional * max(0.0, kurs / d.ref0 - DerivPricing.FX_STRIKE)
                    pendingIncome += pay; derivNetRs += pay
                    lines += ReportLine(if (pay > 0.5) "Opsi beli A$ jatuh tempo di dalam uang: menerima ${fmt(pay)} jt RS."
                        else "Opsi beli A$ kedaluwarsa tanpa nilai (premi ${fmt(d.premium)} jt RS hangus).", if (pay > 0.5) Tone.GOOD else Tone.INFO)
                }
                DerivKind.COTFUT -> if (turn >= d.expiryTurn) {
                    done += d
                    val pay = d.notional * (commodities.cotton.index / d.strike - 1.0)
                    pendingIncome += pay; derivNetRs += pay
                    lines += ReportLine("Futures kapas jatuh tempo: ${if (pay >= 0) "menerima" else "membayar"} ${fmt(abs(pay))} jt RS.", if (pay >= 0) Tone.GOOD else Tone.WARN)
                }
            }
        }
        derivs.removeAll(done)
    }

    /** Akuisisi Media atau Lobi Parlemen (Era 3). */
    private fun doMediaLobby(kind: String): Boolean {
        if (!spend(2)) return false
        val cost = if (kind == "media") 1400.0 * sc.size * costIndex else 450.0 * sc.size * costIndex
        if (cash - cost < 1200.0) return false
        oneOffOpex += cost
        lobbyCount++
        if (kind == "media") {
            mediaOwned = true
            boardTrust = (boardTrust + 5).coerceAtMost(100)
        } else {
            recordEthicsEvidence("parliament_lobby", 1.1, "exec_coo", "sundara")
            foreignMeta.values.forEach { it.tariffPct = max(it.profile.baseTariffPct, it.tariffPct - 2.0) }
            dunia.geo.nudgeSundara(-1.2)
        }
        chronicle += ChronicleItem(turn, if (kind == "media") "Akuisisi Grup Media Nasional" else "Kampanye Lobi Parlemen & Asosiasi",
            "Biaya ${fmt(cost)} jt RS untuk memengaruhi opini publik dan menahan kenaikan tarif/regulasi.",
            "Lobi & media Era 3 → meredam tekanan regulasi → meninggalkan jejak bagi jurnalis investigasi.", actionCount)
        return true
    }

    /** Perdagangan Kredit Karbon (Era 4). */
    private fun doCarbonAction(kind: String, units: Int): Boolean {
        if (units <= 0 || !spend(1)) return false
        val unitPriceRs = 42.0 * sc.size * costIndex
        return if (kind.equals("buy", ignoreCase = true)) {
            val totalCost = units * unitPriceRs
            if (cash - totalCost < 1000.0) return false
            oneOffOpex += totalCost
            carbonCredits += units
            if (carbonCredits >= 5) esgCompliant = true
            chronicle += ChronicleItem(turn, "Pembelian $units Kredit Karbon",
                "Biaya ${fmt(totalCost)} jt RS di bursa karbon untuk menutup jejak emisi pabrik.",
                "Pasar karbon Era 4 → kepatuhan emisi & bebas penalti CBAM.", actionCount)
            true
        } else {
            if (carbonCredits < units) return false
            val proceeds = units * unitPriceRs * 0.95
            carbonCredits -= units
            pendingIncome += proceeds
            chronicle += ChronicleItem(turn, "Penjualan $units Kredit Karbon Surplus",
                "Menerima ${fmt(proceeds)} jt RS dari penjualan kuota emisi hijau.",
                "Surplus energi bersih → monetisasi kredit karbon.", actionCount)
            true
        }
    }

    /** Penunjukan & Kaderisasi Penerus CEO (Era 4). */
    private fun doAppointSuccessor(candidateId: String): Boolean {
        if (candidateId.isBlank() || !spend(2)) return false
        appointedSuccessorId = candidateId
        appointedAtTurn = turn
        val trainingCost = 380.0 * sc.size * costIndex
        oneOffOpex += trainingCost
        val rSuc = Random(seed * 19L + candidateId.hashCode().toLong())
        successorReadiness = when {
            candidateId.contains("cfo") || candidateId.contains("coo") -> 0.72 + rSuc.nextDouble() * 0.23
            candidateId.contains("family") -> 0.40 + rSuc.nextDouble() * 0.45
            else -> 0.65 + rSuc.nextDouble() * 0.25
        }
        boardTrust = (boardTrust + 6).coerceAtMost(100)
        chronicle += ChronicleItem(turn, "Penetapan Kandidat Suksesi CEO: $candidateId",
            "Program transisi kepemimpinan dimulai (biaya tata kelola ${fmt(trainingCost)} jt RS).",
            "Suksesi Era 4 → menyiapkan penerus untuk menjaga warisan korporasi.", actionCount)
        return true
    }

    private fun recordEthicsEvidence(kind: String, severity: Double, witnessExecId: String, countryId: String) {
        ethicsLoad += severity
        ethicsEvidences += EthicsEvidence(
            id = "eth_${turn}_${ethicsEvidences.size + 1}",
            kind = kind,
            severity = severity,
            witnessExecId = witnessExecId,
            countryId = countryId,
            turnCommitted = turn
        )
    }

    private fun doApplyCredit(a: Action.ApplyCredit): Boolean {
        val purpose = Purpose.entries.firstOrNull { it.name == a.purpose } ?: return false
        if (a.amount < 500 || a.amount > 60000) return false
        if (credit.bank(a.bankId) == null) return false
        if (ap < 2) return false
        credit.submit(a.bankId, a.amount.toDouble(), a.tenor, purpose, a.floating, a.bullet, turn) ?: return false
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
        return CreditMetrics(ebit12, ebit12 + dep12, max(0.0, int12 - 12.0 * spvMonthlyInterest()), reportedDebt(), cash, cash + totalAR() + inventory, due,
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
        "fleet" -> if (fleetOwned) 1 else 0
        "export" -> if (exportOn) 1 else 0
        "solar" -> if (esgCompliant) 1 else 0
        "institute" -> projectsInv.count { it.type == "institute" && it.done }
        "rnd" -> projectsInv.count { it.type == "rnd" && it.done }
        "brand" -> projectsInv.count { it.type == "brand" && it.done && turn - it.startTurn < it.months + 12 }
        else -> 0
    }

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
        if (p.type.startsWith("sub_")) {
            val cid = p.type.removePrefix("sub_")
            val sub = subsidiaries[cid] ?: return
            sub.active = true
            // Alokasi modal disetor lokal dari konstruksi (CIP) menjadi Aset Tetap (72%) & Modal Kerja Awal (28%)
            val capLocal = sub.paidInCapitalLocal
            sub.faLocal = capLocal * 0.72
            sub.invLocal = capLocal * 0.12
            sub.arLocal = capLocal * 0.08
            sub.cashLocal = capLocal * 0.08
            periodSubCashAcquiredRs += sub.cashLocal * foreignMeta.getValue(cid).fxToRs
            if (cid == "kestrel") dualSourcing = true
            lines += ReportLine("Anak usaha luar negeri resmi beroperasi: ${sub.spec.title} di ${sub.spec.countryName}. ${sub.spec.benefitText}", Tone.GOOD)
            chronicle += ChronicleItem(turn, "Anak Usaha Beroperasi: ${sub.spec.title}", sub.spec.benefitText,
                "Konstruksi lintas batas selesai → pembukuan lokal ${sub.spec.currencyCode} mulai beroperasi.")
            return
        }
        val spec = investSpec(p.type) ?: return
        when (p.type) {
            "line" -> extraLines++
            "automation" -> automation = true
            "warehouse" -> warehouse = true
            "fleet" -> fleetOwned = true
            "export" -> { exportOn = true; demandBoost += 0.06 }
            "rnd" -> demandBoost += 0.04
            "brand" -> mods += Mod("rev", 1.03, turn + 11)
            "solar" -> { esgCompliant = true; demandBoost += 0.03; carbonCredits += 4 }
            "institute" -> { boardTrust = (boardTrust + 15).coerceAtMost(100); execs.forEach { it.loyalty = (it.loyalty + 0.08).coerceAtMost(0.98) } }
        }
        lines += ReportLine("Investasi selesai: ${spec.title}. ${spec.benefit}.", Tone.GOOD)
        chronicle += ChronicleItem(turn, "Investasi selesai: ${spec.title}", spec.benefit, "Belanja modal atau program → manfaat operasional.")
    }

    private fun doBuy(id: String, lots: Int): Boolean {
        val c = bourse.company(id) ?: return false
        if (lots <= 0 || !c.listed) return false
        val gross = lots * LOT * c.price
        val fee = gross * BROKER_FEE
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
        equity -= fee
        realizedGainTotal += gross - basis
        periodInvestIn += gross - fee
        periodTradeFees += fee
        if (lots == have) { holdings.remove(id); costBasis.remove(id) }
        else { holdings[id] = have - lots; costBasis[id] = (costBasis[id] ?: 0.0) - basis }
        return true
    }

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
    // Kartu kejadian & Kartu Berantai
    // =====================================================================
    fun eventCtx(): EventCtx = EventCtx(
        turn, sc.turn0, sc.size, costIndex, inflationAnnual, world.unemployment, policyRate, policyRate - policyHist.first(),
        if (kursHist.size >= 6) (kurs / kursHist[kursHist.size - 6] - 1.0) * 100.0 else 0.0, econ, cash,
        if (lastIncome.isEmpty()) 62.0 else dso(), if (totalAR() > 0) normalAR / totalAR() else 1.0,
        deferredMonths, dualSourcing, strikeMonthsLeft > 0, covenantBreaches, ethicsLoad, turn <= dumpingUntil,
        commodities.cotton.index, execs.filter { it.present }.map { it.id to it.name }, sc.workers,
        loans.any { it.balance > 0.5 && it.floatingSpreadPct != null }, 0.0,
        isPublic = isPublicCompany,
        profitFalling = ebitHist.size >= 6 && ebitHist.takeLast(3).average() < 0.85 * ebitHist.dropLast(3).takeLast(3).average(),
        panic = dunia.macro.panic, ratingIdx = dunia.macro.state("sundara").rating.ordinal, tension = dunia.geo.meanTensionOf("sundara"),
        sanctioned = dunia.geo.activeSanctionsOn("sundara").isNotEmpty(), blockade = dunia.geo.anyBlockade(),
        monthsToElection = dunia.geo.monthsToElection("sundara", turn), util = utilization(), priceAdj = priceAdjPct,
        sentiment = stockSentiment, oilIndex = commodities.oil.index, gapPct = world.gap * 100.0, era = KapitalEngine.era(turn))

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
        // Rantai sebab-akibat: keputusan ini dapat memicu kartu lanjutan beberapa bulan kemudian
        for (rule in EventDeck.chainRules) {
            if (rule.from != ev.templateId || (rule.options.isNotEmpty() && opt.id !in rule.options)) continue
            if (r.nextDouble() < rule.p && scheduledChainEvents.none { it.templateId == rule.to }) {
                scheduledChainEvents += ScheduledChainEvent(turn + rule.delay, rule.to, rule.cause)
            }
        }
        openEvents.remove(ev)
        if (!auto && opt.tone != Tone.BAD) crisesSurvived++
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
            "buyback" -> if (isPublicCompany) pendingBuyback += f.a else pendingDividend += f.a
            "sentiment" -> if (isPublicCompany) stockSentiment += f.a
            "ethics" -> {
                val kindLabel = f.s.ifEmpty { "shortcut" }
                val witness = execs.firstOrNull { it.present }?.id ?: "exec_coo"
                recordEthicsEvidence(kindLabel, f.a, witness, "sundara")
            }
            "ethics_reduce" -> {
                ethicsLoad = max(0.0, ethicsLoad - f.a)
                if (ethicsEvidences.isNotEmpty()) ethicsEvidences.removeAt(0)
            }
            "esg_clean" -> esgCompliant = true
            "actor" -> actors.onChoice(f.s, f.a)
            "board_trust" -> boardTrust = (boardTrust + f.a.toInt()).coerceIn(0, 100)
            "chain" -> {
                val parts = f.s.split(":", limit = 2)
                val tplId = parts[0]
                val cause = parts.getOrElse(1) { "Efek berantai dari keputusan sebelumnya." }
                val delay = max(1, f.b)
                if (scheduledChainEvents.none { it.templateId == tplId }) {
                    scheduledChainEvents += ScheduledChainEvent(turn + delay, tplId, cause)
                }
            }
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

    class CrisisPlan(
        val era: Int, val start: Int, val months: Int, val title: String, val warning: String, val body: String, val chain: String,
        val demandPerMonth: Double, val creditShock: Double, val fxHit: Double, val commodityShock: Double
    )

    private var crisisCache: List<CrisisPlan>? = null

    /** Satu krisis makro di tiap Era 2–4, waktu mulai deterministik dari seed; keparahan naik per era. */
    fun crisisPlans(): List<CrisisPlan> {
        crisisCache?.let { return it }
        val plans = mutableListOf<CrisisPlan>()
        for (era in 2..4) {
            val r = Random(seed * 977L + era * 131L + 7L)
            val eraStart = ERA_START_TURNS[era - 1]
            val span = (if (era < 4) ERA_START_TURNS[era] else 481) - eraStart
            val start = eraStart + 14 + r.nextInt(span - 40)
            plans += when (era) {
                2 -> CrisisPlan(2, start, 9, "Resesi Global", "Pesanan ekspor dunia melemah dan bank sentral asing menaikkan bunga; ekonom memperingatkan resesi.",
                    "Permintaan dunia mengerut selama sekitar 9 bulan; bank memperketat kredit.", "Overinvestasi global → pengetatan moneter → resesi → permintaan dan kredit mengerut.",
                    -0.014 - 0.004 * r.nextDouble(), 0.5, 0.03, 0.0)
                3 -> CrisisPlan(3, start, 12, "Krisis Keuangan", "Spread kredit melebar dan beberapa lembaga keuangan menahan pencairan; pasar saham gelisah.",
                    "Krisis kepercayaan di sektor keuangan: kredit mengering, Rial melemah, permintaan turun tajam selama sekitar 12 bulan.", "Utang korporasi menumpuk → gagal bayar berantai → kredit mengering → permintaan jatuh.",
                    -0.019 - 0.005 * r.nextDouble(), 1.0, 0.06, 0.0)
                else -> CrisisPlan(4, start, 14, "Guncangan Energi dan Disrupsi Teknologi", "Harga energi melonjak dan otomasi AI mengubah pasar tenaga kerja; pemerintah menyiapkan paket darurat.",
                    "Biaya energi melonjak dan gelombang disrupsi menekan permintaan serta upah selama sekitar 14 bulan.", "Transisi energi dan disrupsi AI → biaya melonjak, permintaan bergeser → margin tertekan.",
                    -0.018 - 0.006 * r.nextDouble(), 0.6, 0.04, 0.2)
            }
        }
        crisisCache = plans
        return plans
    }

    private fun drawEvents(lines: MutableList<ReportLine>) {
        val r = rng(60)
        if (gameOverReason != null) return

        // 1. Proses kartu berantai (cascading events) yang jatuh tempo pada giliran ini
        val dueChains = scheduledChainEvents.filter { it.dueTurn <= turn }
        scheduledChainEvents.removeAll { it.dueTurn <= turn }
        for (ch in dueChains) {
            if (openEvents.none { it.templateId == ch.templateId } && openEvents.size < 4) {
                val chainedEv = EventDeck.makeEvent(
                    templateId = ch.templateId,
                    ctx = eventCtx(),
                    r = r,
                    id = "evt_${turn}_${eventCounter++}_${ch.templateId}",
                    customWarning = ch.causeNote
                )
                if (chainedEv != null) {
                    openEvents += chainedEv
                    eventLastFired[ch.templateId] = turn
                    lines += ReportLine("EFEK BERANTAI: ${chainedEv.title} (${ch.causeNote})", Tone.BAD)
                    chronicle += ChronicleItem(turn, "Krisis Berantai: ${chainedEv.title}", ch.causeNote,
                        "Keputusan/kejadian lama → rantai transmisi → ${chainedEv.title}.")
                }
            }
        }

        // 2. Undi kartu kejadian reguler sesuai kondisi dunia & tingkat kesulitan
        val d = EventDeck.draw(eventCtx(), r, openEvents.map { it.templateId }.toSet(), eventLastFired, eventCounter++, difficulty.eventRateMult)
        d.event?.let { ev ->
            if (openEvents.size < 3) {
                openEvents += ev; eventsRaised++; eventLastFired[ev.templateId] = turn
                lines += ReportLine("Kartu keputusan baru: ${ev.title}.", Tone.WARN)
            }
        }
        // C1: kartu generator diundi terpisah (aliran acak sendiri) agar dek lama tidak bergeser
        EventCards.draw(eventCtx(), rng(125), openEvents.map { it.templateId }.toSet(), eventLastFired, eventCounter, difficulty.eventRateMult)?.let { ev ->
            if (openEvents.size < 3) {
                eventCounter++; openEvents += ev; eventsRaised++; eventLastFired[ev.templateId] = turn
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
        var periodSubWriteOffRs = 0.0

        // ---- 1. Makro & Geopolitik 7 Negara Asing ----
        market.step(turn, rng(111))
        for ((role, by) in staffPool.step(rng(112), world.rivals.filter { it.active }.sumOf { it.aggression }, world.rivals.filter { it.active }.map { it.name }.ifEmpty { listOf("pesaing") })) {
            lines += ReportLine("$by membajak ${role.label} Anda. Jabatan kosong: efeknya hilang sampai Anda merekrut pengganti.", Tone.BAD)
            chronicle += ChronicleItem(turn, "Staf kunci dibajak: ${role.label}", "$by menawarkan paket lebih tinggi.",
                "Loyalitas rendah atau gaji di bawah pasar → tawaran pesaing → jabatan kosong → produktivitas turun.", actionCount)
        }
        if (rd.step(rng(114), staffPool.rdRateMult())) {
            lines += ReportLine("Tim R&D menuntaskan satu inovasi: indeks kualitas produk naik (kini ${fmt2(rd.quality * 100)}%).", Tone.GOOD)
        }
        val rMacro = rng(1)
        val rWorld = rng(8)
        val kursBefore = kurs
        val crisisNow = crisisPlans().any { turn >= it.start && turn < it.start + it.months }
        val duniaFx = dunia.step(
            DuniaContext(
                turn, world.gap, -0.012 * (policyRate - inflationAnnual - 2.5), commodities.oil.index, crisisNow,
                linkedBlocksNow(),
                Block.values().associateWith { b -> (2.5 + 40.0 * (foreign[b.anchor]?.gap ?: 0.0)).coerceIn(-2.0, 7.0) },
                { id ->
                    when {
                        id == "sundara" -> inflationAnnual to world.unemployment
                        foreign.containsKey(id) -> foreign.getValue(id).let { it.inflation to it.unemployment }
                        else -> dunia.secondary.state(id).let { it.inflation to 5.0 }
                    }
                },
                { id ->
                    when {
                        id == "sundara" -> MacroInput(inflationAnnual, world.gap, world.unemployment, policyRate, world.taylorStar, 2.5 + 40.0 * world.gap)
                        foreign.containsKey(id) -> foreign.getValue(id).let { MacroInput(it.inflation, it.gap, it.unemployment, it.policyRate, it.taylorStar, 2.5 + 40.0 * it.gap) }
                        else -> dunia.secondary.state(id).let { MacroInput(it.inflation, (it.gdpIndex - 1.0) * 0.5, 5.0, it.policyRate, it.taylorStar, it.growth) }
                    }
                }
            ), this::rng
        )
        for (nt in duniaFx.notes) {
            lines += ReportLine(nt.text, nt.tone)
            if (nt.chronicleTitle != null) chronicle += ChronicleItem(turn, nt.chronicleTitle, nt.text, nt.chain ?: "", actionCount)
        }
        foreignProfiles.forEachIndexed { i, p ->
            val fw = foreign.getValue(p.id)
            fw.step(rng(20 + i), 1.0, 1.0, 1.0, 0.0, 1.0, policyBias = duniaFx.foreignBias[p.id] ?: 0.0)
            val meta = foreignMeta.getValue(p.id)
            if (meta.capitalControlTurnsLeft > 0) meta.capitalControlTurnsLeft--
            // Kurs mata uang lokal terhadap RS mengikuti PPP selisih inflasi Sundara vs negara tersebut
            val diffInfl = (inflationAnnual - fw.inflation) / 1200.0
            meta.fxToRs = (meta.fxToRs * (1.0 + diffInfl + gaussian(rMacro) * 0.005)).coerceAtLeast(meta.profile.baseFxToRs * 0.4)
            // Skor ketegangan Sundara–negara ini kini berasal dari matriks geopolitik dinamis (Tahap A4);
            // tarif bea masuk mengikuti anak tangga eskalasi ditambah haluan pemilu kedua negara.
            meta.tensionScore = dunia.geo.tension("sundara", p.id).roundToInt().coerceIn(15, 92)
            meta.tariffPct = (when {
                meta.tensionScore >= 70 -> meta.profile.baseTariffPct + 14.0
                meta.tensionScore >= 60 -> meta.profile.baseTariffPct + 9.0
                meta.tensionScore >= 45 -> meta.profile.baseTariffPct + 4.0
                else -> meta.profile.baseTariffPct
            } + dunia.geo.stance(p.id).tariffAdd * 0.5 + dunia.geo.stance("sundara").tariffAdd * 0.3).coerceAtLeast(0.0)
        }
        aurPrice *= 1.0 + foreign.getValue("aurelia").inflation / 1200.0
        val infDiff = inflationAnnual - foreign.getValue("aurelia").inflation
        val driftMonthly = infDiff / 1200.0 + 0.0012 + duniaFx.kursDrift
        kurs *= 1.0 + driftMonthly + gaussian(rMacro) * 0.008
        foreignMeta.getValue("aurelia").fxToRs = kurs

        var demandShock = duniaFx.demandShock
        // Krisis makro per era (tekanan naik tiap era, sesuai dokumen desain): jadwal deterministik dari seed
        crisisPlans().firstOrNull { turn >= it.start - 3 && turn < it.start + it.months }?.let { cp ->
            if (turn == cp.start - 3) {
                lines += ReportLine("Sinyal dini: ${cp.warning}", Tone.WARN)
            }
            if (turn >= cp.start) {
                val k = turn - cp.start
                demandShock += cp.demandPerMonth * (if (k < cp.months / 2) 1.0 else 0.6)
                if (turn == cp.start) {
                    creditShock += cp.creditShock
                    kurs *= 1.0 + cp.fxHit
                    foreignMeta.getValue("aurelia").fxToRs = kurs
                    if (cp.commodityShock > 0.0) commodities.triggerCorneringShock(cp.months / 2, cp.commodityShock)
                    lines += ReportLine("${cp.title} dimulai: ${cp.body}", Tone.BAD)
                    chronicle += ChronicleItem(turn, cp.title, cp.body, cp.chain, actionCount)
                } else if (turn == cp.start + cp.months - 1) {
                    lines += ReportLine("${cp.title} mereda; pasar mulai pulih perlahan.", Tone.GOOD)
                }
            }
        }
        if (turn == electionTurn) {
            lines += ReportLine("Pemilu Sundara berlangsung bulan ini.", Tone.INFO)
            if (electionShock) {
                kurs *= 1.12
                foreignMeta.getValue("aurelia").fxToRs = kurs
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

        commodities.step(rng(30), world.gap)
        commodities.lastEvents.forEach { lines += ReportLine(it, Tone.WARN) }
        if (hedgeUntilTurn == turn) lines += ReportLine("Kontrak berjangka kapas berakhir; HPP kembali mengikuti harga pasar.", Tone.INFO)
        if (fxHedgeUntilTurn == turn) lines += ReportLine("Kontrak forward valas RS/A$ berakhir; nilai tukar kembali terbuka pada kurs spot.", Tone.INFO)

        val marketMat = commodities.materialIndex()
        val matCost = 0.35 + 0.65 * marketMat
        val fxTarget = (1.0 + 0.6 * ((0.8 + 0.2 * fxReal()) - 1.0)) * (1.0 + 0.65 * 0.7 * (marketMat - 1.0))
        fxPassState += 0.35 * (fxTarget - fxPassState)
        // Catatan: pengali biaya lapisan dunia (sanksi/kelangkaan) sengaja TIDAK dimasukkan ke costPressure, karena
        // costPressure juga menentukan biaya pesaing/emiten dan pesaing tidak meneruskan biaya ke harga (mereka bangkrut).
        // Pengali itu dipakai pada HPP pemain di cogsRatio().
        val costPressure = (0.8 + 0.2 * fxReal()) * (costIndex / priceIndex) * matCost
        val rpPlayer = (1.0 + priceAdjPct / 100.0) * fxPassLevel()
        volOverride = world.volumeFactor(rpPlayer)
        val prevPolicy = policyRate

        // Observasi pesaing AI atas kondisi publik pemain (Tahap A)
        val ebitdaEst = max(1.0, (ebitHist.takeLast(3).sum() + 3.0 * fixedAssets * 0.0081) * 4.0)
        val netDebtRatioNow = (totalDebt() - cash) / ebitdaEst
        val debtMaturingSoon = loans.any { it.balance > 0.5 && it.maturityTurn - turn in 1..6 }
        val playerObs = World.PlayerObservation(
            priceAdjPct = priceAdjPct,
            netDebtToEbitda = netDebtRatioNow,
            debtMaturingWithin6m = debtMaturingSoon,
            singleSupplier = !dualSourcing && subsidiaries["kestrel"]?.active != true && subsidiaries["tarsis"]?.active != true,
            cottonHedged = hedgeActive,
            playerMarketShare = playerShare(),
            allowCoalition = difficulty.allowRivalCoalition
        )

        val out = world.step(rWorld, rpPlayer, costPressure, fxPassLevel(), kurs / kursBefore - 1.0, kurs, demandShock, playerObs, duniaFx.policyBias, duniaFx.cbMisread)
        econ = out.demand

        // Tindak lanjut serangan terarah dari pesaing AI yang belajar (Tahap A)
        when (out.rivalTriggeredAction) {
            "cornering" -> {
                commodities.triggerCorneringShock(4, 0.14)
                chronicle += ChronicleItem(turn, "Serangan Pesaing: Borongan Pasokan Kapas",
                    "Mitra Tekstil membaca ketergantungan pemasok Anda dan memborong stok kapas spot.",
                    "Tanpa dual sourcing / lindung nilai → pesaing memborong spot → biaya bahan baku naik.")
            }
            "regulatory_sabotage" -> {
                if (scheduledChainEvents.none { it.templateId == "env_inspection" }) {
                    scheduledChainEvents += ScheduledChainEvent(turn + 1, "env_inspection", "Dilobi oleh pesaing kroni Prima Garmen Utama untuk menghambat ekspansi Anda.")
                }
                chronicle += ChronicleItem(turn, "Serangan Pesaing: Sabotase Regulasi",
                    "Prima Garmen menggunakan lobi politik untuk mengirim inspeksi ke pabrik Anda.",
                    "Pangsa pasar tumbuh → pesaing kroni melobi regulator → inspeksi mendadak.")
            }
            "predatory_debt" -> {
                chronicle += ChronicleItem(turn, "Serangan Pesaing: Perang Harga Saat Utang Jatuh Tempo",
                    "Cahaya Weaving & Importir Velmora memangkas harga tepat saat Anda menghadapi jadwal kredit.",
                    "Utang tinggi / jatuh tempo dekat → pesaing banting harga → tekanan ICR.")
            }
            "coalition" -> {
                chronicle += ChronicleItem(turn, "Koalisi Pesaing Melawan Pemimpin Pasar",
                    "Pesaing bersatu menurunkan harga bersama untuk membendung dominasi Anda.",
                    "Dominasi pangsa pasar → koalisi pesaing AI.")
            }
        }

        run {
            val pvBefore = portfolioValue()
            val ev = bourse.step(rng(40), Bourse.Ctx(
                turn, econ, trendGrowth(), priceIndex, costPressure,
                commodities.cotton.index, commodities.oil.index, commodities.steel.index,
                fxReal(), out.policyRate, world.gap, rpPlayer,
                { id -> world.rivalShareOf(id, rpPlayer) },
                { id -> world.rivals.first { it.id == id }.price },
                { id -> world.rivals.first { it.id == id }.active },
                { id -> world.rivals.first { it.id == id }.share0 },
                dunia.lastEffects.news, dunia.lastEffects.riskOff))
            var divIn = 0.0
            for ((id, lots) in holdings) {
                val c = bourse.company(id) ?: continue
                val m = c.monthly.lastOrNull() ?: continue
                if (m.turn == turn && m.dividend > 0 && c.sharesM > 0) divIn += lots * LOT * (m.dividend / c.sharesM)
            }
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
            if (holdings.isNotEmpty() && abs(pvAfter - pvBefore) > 0.0) {
                val d = pvAfter - pvBefore
                lines += ReportLine("Portofolio saham ${if (d >= 0) "naik" else "turun"} ${fmt(abs(d))} jt RS bulan ini (nilai ${fmt(pvAfter)} jt RS, belum direalisasi).", if (d >= 0) Tone.GOOD else Tone.WARN)
            }
            ev.forEach { lines += ReportLine(it, Tone.INFO) }
        }
        inflationAnnual = out.inflationAnnual
        policyRate = out.policyRate
        policyHist += policyRate; if (policyHist.size > 6) policyHist.removeAt(0)
        settleDerivatives(lines)
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
        creditShock = creditShock * 0.96 + duniaFx.creditShock
        creditAdj = (0.4 * (out.unemployment - 5.0)).coerceIn(-0.5, 2.0) + creditShock - (if (isPublicCompany) (stockSentiment * 1.0).coerceIn(-0.3, 0.3) else 0.0)
        loans.forEach { l -> l.floatingSpreadPct?.let { l.annualRatePct = policyRate + it + creditAdj } }

        // ---- 2. Kejadian operasional, Pembukuan Anak Usaha Lokal, Skandal Etika, & Risiko Politik ----
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

        // Pembukuan entitas anak usaha dalam mata uang lokal, harga transfer antar-entitas, & risiko politik (Tahap 3)
        periodIntercompanyFeeToSubRs = 0.0
        val hasMeridiaActive = subsidiaries["meridia"]?.let { it.active && !it.nationalized } == true
        for ((cid, sub) in subsidiaries) {
            if (!sub.active || sub.nationalized) continue
            val meta = foreignMeta.getValue(cid)
            val fw = foreign.getValue(cid)

            // Risiko politik: pengendalian modal atau nasionalisasi saat ketegangan tinggi
            if (meta.tensionScore >= 74 && meta.capitalControlTurnsLeft == 0 && rOps.nextDouble() < 0.08) {
                meta.capitalControlTurnsLeft = 4
                lines += ReportLine("Pengendalian Modal ${sub.spec.countryName}: otoritas moneter membekukan repatriasi dividen selama 4 bulan.", Tone.WARN)
            }
            if (meta.tensionScore >= 86 && rOps.nextDouble() < 0.03) {
                sub.nationalized = true
                // kas yang sudah ditarik sebagai dividen bulan ini tidak ikut disita
                subCashSnap.remove(cid)?.let { periodSubSeizedCashRs += (it.first - sub.periodDivPaidLocal) * it.second }
                periodSubDeconsolRs -= sub.retainedEarningsHistoricalRs()
                val lostInvRs = sub.paidInCapitalHistoricalRs
                investmentInSubsRs = max(0.0, investmentInSubsRs - lostInvRs)
                equity -= lostInvRs
                periodSubWriteOffRs += lostInvRs
                lines += ReportLine("NASIONALISASI ASET: Pemerintah ${sub.spec.countryName} menyita ${sub.spec.title}. Investasi induk ${fmt(lostInvRs)} jt RS dihapusbukukan.", Tone.BAD)
                chronicle += ChronicleItem(turn, "Nasionalisasi Aset di ${sub.spec.countryName}",
                    "Ketegangan geopolitik ekstrem memicu penyitaan ${sub.spec.title}.",
                    "Risiko politik lintas batas → aset anak usaha hilang → ekuitas tergerus.")
                continue
            }

            sub.monthsOperating += 1
            val learningFactor = 0.68 + 0.32 * min(1.0, sub.monthsOperating / 12.0)
            val util = (0.84 * fw.demand * sub.localDemandFactor * learningFactor).coerceIn(0.35, 1.15)
            sub.lastUtilization = util

            val annualRevAtParLocal = sub.paidInCapitalLocal * sub.spec.capitalTurnoverAnnual
            var revLocal = (annualRevAtParLocal / 12.0) * util * (1.0 + fw.inflation / 2400.0)
            val cogsLocal = revLocal * sub.spec.baseLocalCogsRatio * (0.85 + 0.15 * commodities.materialIndex())
            val baseOpexLocal = (sub.spec.fixedOpexRs / meta.profile.baseFxToRs) * (1.0 + fw.inflation / 1800.0)
            val cbamLocal = if (cid == "halvenia" && !esgCompliant) (55.0 * sc.size * costIndex) / meta.fxToRs else 0.0
            if (cbamLocal > 0.0) {
                lines += ReportLine("Bea Karbon Halvenia (CBAM): ${sub.spec.title} dikenai pungutan emisi ${fmt2(cbamLocal)} jt HLV karena pabrik belum bersertifikat ESG/IPAL.", Tone.WARN)
            }
            val opexLocal = baseOpexLocal + cbamLocal
            val depLocal = sub.faLocal * 0.0065

            // Penggeseran laba kena pajak (Transfer Pricing / Royalti Antar-Entitas) dari Induk Sundara (22%) ke yurisdiksi pajak rendah
            if (transferPricingMode != TransferPricingMode.WAJAR) {
                if (cid == "meridia") {
                    val shiftRate = if (transferPricingMode == TransferPricingMode.AGRESIF) 0.068 else 0.032
                    val icFeeRs = baseNormalRevenue(1.0) * shiftRate
                    periodIntercompanyFeeToSubRs += icFeeRs
                    revLocal += icFeeRs / meta.fxToRs
                } else if (!hasMeridiaActive && meta.profile.corpTaxPct < 22.0) {
                    val shiftRate = if (transferPricingMode == TransferPricingMode.AGRESIF) 0.024 else 0.012
                    val icFeeRs = baseNormalRevenue(1.0) * shiftRate
                    periodIntercompanyFeeToSubRs += icFeeRs
                    revLocal += icFeeRs / meta.fxToRs
                }
            }

            val ebitLocal = revLocal - cogsLocal - opexLocal - depLocal
            val intLocal = sub.bankDebtLocal * ((fw.policyRate + 2.5) / 1200.0)
            val ebtLocal = ebitLocal - intLocal
            val taxLocal = max(0.0, ebtLocal) * (meta.profile.corpTaxPct / 100.0)
            val niLocal = ebtLocal - taxLocal
            sub.retainedEarningsLocal += niLocal
            sub.cumulativeNetIncomeRs += niLocal * meta.fxToRs

            val targetArLocal = revLocal * 1.05
            val targetInvLocal = cogsLocal * 1.05
            val targetApLocal = cogsLocal * 0.85
            val newArLocal = sub.arLocal + 0.4 * (targetArLocal - sub.arLocal)
            val newInvLocal = sub.invLocal + 0.4 * (targetInvLocal - sub.invLocal)
            val newApLocal = sub.apLocal + 0.4 * (targetApLocal - sub.apLocal)
            val dArLocal = newArLocal - sub.arLocal; sub.arLocal = newArLocal
            val dInvLocal = newInvLocal - sub.invLocal; sub.invLocal = newInvLocal
            val dApLocal = newApLocal - sub.apLocal; sub.apLocal = newApLocal

            val subCapexLocal = depLocal * 0.65
            sub.faLocal += subCapexLocal - depLocal
            val subDebtRepayLocal = min(sub.bankDebtLocal, sub.monthlyDebtAmortLocal)
            sub.bankDebtLocal -= subDebtRepayLocal

            val subCfoLocal = niLocal + depLocal - dArLocal - dInvLocal + dApLocal
            sub.cashLocal += subCfoLocal - subCapexLocal - subDebtRepayLocal
            if (sub.cashLocal < 0.0) {
                val od = -sub.cashLocal
                sub.cashLocal = 0.0
                sub.bankDebtLocal += od
                sub.periodNewDebtLocal += od
            }
            val subCfiLocal = -subCapexLocal
            val subCffLocal = sub.periodNewDebtLocal - subDebtRepayLocal - sub.periodDivPaidLocal
            sub.lastIncomeLocal = mapOf(
                "revenue" to revLocal, "cogs" to cogsLocal, "opex" to opexLocal, "dep" to depLocal,
                "ebit" to ebitLocal, "interest" to intLocal, "ebt" to ebtLocal, "tax" to taxLocal, "ni" to niLocal
            )
            sub.lastCashFlowLocal = mapOf(
                "cfo" to subCfoLocal, "cfi" to subCfiLocal, "cff" to subCffLocal,
                "net" to (subCfoLocal + subCfiLocal + subCffLocal)
            )
            sub.periodNewDebtLocal = 0.0
            sub.lastDivPaidLocal = sub.periodDivPaidLocal
            sub.periodDivPaidLocal = 0.0
            sub.lastDivParentLocal = sub.periodDivParentLocal
            sub.periodDivParentLocal = 0.0
        }

        // Audit transfer pricing: peluang bergantung pada seberapa besar laba yang digeser dibanding pendapatan induk (bukan peluang tetap)
        val shiftRatio = periodIntercompanyFeeToSubRs / max(1.0, baseNormalRevenue(1.0))
        if (transferPricingMode != TransferPricingMode.WAJAR && shiftRatio > 0.0) {
            val auditP = (0.001 + 70.0 * StrictMath.pow(shiftRatio, 2.5)).coerceAtMost(0.15)
            if (rOps.nextDouble() < auditP) {
                val tpPenalty = 620.0 * sc.size * costIndex * (shiftRatio / 0.045).coerceIn(0.25, 1.5)
                oneOffOpex += tpPenalty
                recordEthicsEvidence("transfer_pricing_audit", if (transferPricingMode == TransferPricingMode.AGRESIF) 1.2 else 0.4, "exec_cfo", "meridia")
                lines += ReportLine("Otoritas pajak mengaudit skema transfer pricing (${transferPricingMode.label}); laba digeser ${fmt1(shiftRatio * 100)}% dari pendapatan. Koreksi dan denda ${fmt(tpPenalty)} jt RS.", Tone.BAD)
                chronicle += ChronicleItem(turn, "Denda Audit Transfer Pricing", "Skema pengalihan laba ke yurisdiksi pajak rendah dikoreksi kantor pajak.",
                    "Transfer pricing → laba digeser besar dibanding aktivitas → audit lintas batas → denda pajak.")
            }
        }

        // Operasi Unit Bank/Asuransi Grup & Uji Penurunan Nilai Goodwill (Era 3)
        if (captiveBankActive) {
            if (out.unemployment >= 6.8) {
                val nplLoss = captiveBankCapitalRs * 0.018 * (out.unemployment - 5.8)
                oneOffOpex += nplLoss
                lines += ReportLine("Unit Bank & Asuransi Grup mencatat kredit macet saat pengangguran naik: beban pencadangan ${fmt(nplLoss)} jt RS.", Tone.BAD)
            } else {
                val nimGain = captiveBankCapitalRs * ((policyRate + 2.0) / 1200.0)
                pendingIncome += nimGain
            }
        }
        if (goodwill > 0.0 && econ < 0.91) {
            val gwImpair = min(goodwill, goodwill * 0.05)
            goodwill -= gwImpair
            equity -= gwImpair
            periodGoodwillImpairmentRs += gwImpair
            lines += ReportLine("Penurunan Nilai Goodwill (Impairment): pelemahan pasar pasca-akuisisi memangkas nilai goodwill ${fmt(gwImpair)} jt RS.", Tone.WARN)
        }

        // Skor Rahasia Etika & Bom Waktu Skandal (Tahap C - Bagian 16 PDF)
        forensicsMonthly(lines)
        actorsMonthly(lines)
        insightMonthly(lines)
        ethicsLoad *= 0.988
        var scandalCost = 0.0
        val departedWitnessBoost = ethicsEvidences.count { ev -> execs.any { it.id == ev.witnessExecId && !it.present } } * 0.035
        val regimeChangeBoost = if (turn == electionTurn) 0.08 else 0.0
        val sizeScrutinyBoost = if (totalAssets() > 145_000.0 * sc.size) 0.015 else 0.0
        val mediaShield = if (mediaOwned) 0.72 else 1.0
        val scandalProb = (0.022 * ethicsLoad + departedWitnessBoost + regimeChangeBoost + sizeScrutinyBoost) * mediaShield
        if (ethicsLoad >= 2.0 && rOps.nextDouble() < scandalProb) {
            scandalsTriggered++
            boardTrust = (boardTrust - 18).coerceAtLeast(0)
            if (ethicsLoad >= 6.2 && scandalsTriggered >= 2) {
                gameOverReason = "Kejaksaan Agung dan Komisi Antikorupsi menahan Anda atas bukti suap, manipulasi pajak, dan kartel berlapis yang dibongkar saksi kunci. Perusahaan dibekukan (Game Over: Penjara & Skandal)."
            } else {
                scandalCost = 900.0 * sc.size * costIndex * (0.6 + 0.4 * ethicsLoad)
                lostCustomerShare = (lostCustomerShare + 0.03).coerceAtMost(0.6)
                lines += ReportLine("Skandal: praktik jalan pintas Anda terungkap (saksi mantan eksekutif / penyelidikan jurnalis). Denda dan biaya hukum ${fmt(scandalCost)} jt RS, kepercayaan dewan turun.", Tone.BAD)
                chronicle += ChronicleItem(turn, "Skandal etika terungkap", "Suap, pemufakatan harga, atau pelanggaran lain yang pernah dilakukan akhirnya muncul ke publik.",
                    "Jalan pintas → bukti tersimpan → saksi keluar / rezim berganti → denda dan reputasi rusak.", actionCount)
                ethicsLoad *= 0.45
            }
        }

        var breakdownCost = 0.0
        if (breakdownMonthsLeft == 0) {
            var p = 0.015 * (1.0 + 0.5 * deferredMonths) * staffPool.breakdownMult()
            if (!exec("exec_factory").present) p *= 1.5
            if (turn <= unskilledUntil) p *= 1.4
            if (rOps.nextDouble() < p) {
                breakdownMonthsLeft = 2
                breakdownCost = 1800.0 * sc.size * costIndex
                if (insuranceLevel > 0) {
                    val covered = breakdownCost * (if (insuranceLevel == 2) 0.85 else 0.60)
                    pendingIncome += covered
                    insuranceClaimTurns += turn
                    lines += ReportLine("Asuransi membayar klaim ${fmt(covered)} jt RS untuk perbaikan mesin (premi akan naik).", Tone.GOOD)
                }
                lines += ReportLine("Mesin tenun lini 3 rusak berat: produksi turun 50% selama 2 bulan, perbaikan ${fmt(breakdownCost)} jt RS.", Tone.BAD)
                chronicle += ChronicleItem(turn, "Kerusakan mesin besar",
                    "Perawatan yang tertunda $deferredMonths bulan berujung kerusakan.",
                    "KPI penghematan → perawatan ditunda → keausan → mesin rusak.")
            }
        }
        holdupThisTurn = false
        val hasKestrelSub = subsidiaries["kestrel"]?.let { it.active && !it.nationalized } == true
        if (!dualSourcing && !hasKestrelSub && rOps.nextDouble() < (if (warehouse) 0.05 else 0.10) * (1.0 - 0.30 * logisticsLevel) * (if (fleetOwned) 0.8 else 1.0)) {
            holdupThisTurn = true
            lines += ReportLine("Pemasok Kestrel menahan kiriman sensor dengan alasan \"mesin rusak\"; produksi turun 30% bulan ini.", Tone.BAD)
        }
        var sanctionFine = 0.0
        if (!dualSourcing && !hasKestrelSub && !sanctionHit && turn >= sc.turn0 + sc.sanctionAfter && rOps.nextDouble() < 0.05) {
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

        // ---- 3. Laba rugi Induk ----
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
        val opex = opexBase() + extraOpex() + wageAddOn + oneOffOpex + sanctionFine + scandalCost + otherLoss - pendingIncome + projExp + periodIntercompanyFeeToSubRs
        oneOffOpex = 0.0; pendingIncome = 0.0
        val maint = maintenanceSpend() + breakdownCost + if (holdupThisTurn) 200.0 else 0.0
        if (deferralActive) deferredMonths += 1 else if (deferredMonths > 0) deferredMonths -= 1
        val dep = fixedAssets * 0.0081
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
        val whtNow = pendingWithholdingTaxRs
        pendingWithholdingTaxRs = 0.0
        val tax = computeTax(taxable) + whtNow
        val ni = ebit - interest + interestIncome - tax
        equity += ni

        volOverride = null

        // ---- 4. Modal kerja Induk ----
        val dsoNormal = (if (salesScheme == SalesScheme.OMZET) 62.0 else 50.0) + modSum("dso")
        val arTarget = normalRev * dsoNormal / 30.0
        val newNormalAR = normalAR + 0.5 * (arTarget - normalAR)
        val invTarget = cogs * 53.0 / 30.0 * (if (holdupThisTurn) 1.15 else 1.0) * (if (warehouse) 0.92 else 1.0)
        val newInv = inventory + 0.5 * (invTarget - inventory)
        val payTarget = cogs * (if (tradeFinance) 50.0 else 35.0) / 30.0
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
        for (p in projectsInv) if (!p.done && p.capex) {
            val part = p.totalCost / p.months
            p.spent += part
            if (p.type.startsWith("sub_")) {
                val cid = p.type.removePrefix("sub_")
                val sub = subsidiaries[cid]
                if (sub != null && !sub.nationalized) {
                    val fx = foreignMeta.getValue(cid).fxToRs
                    val partLocal = part / fx
                    sub.spentRs += part
                    sub.paidInCapitalHistoricalRs += part
                    sub.paidInCapitalLocal += partLocal
                    sub.faLocal += partLocal
                    investmentInSubsRs += part
                    periodSubInvestOutRs += part
                }
            } else {
                expCapex += part
            }
        }
        for (p in projectsInv.toList()) if (!p.done && p.spent >= p.totalCost - 0.01) finishInvest(p, lines)
        val evCapex = pendingCapex
        capex += replacement + pendingCapex + expCapex
        pendingCapex = 0.0
        fixedAssets += capex - dep

        // Pencairan SPV (B5b): utang nyata, tercatat di neraca, tetapi tidak tampak di metrik kreditur
        for ((amt, rate) in pendingSpv) {
            loans += Loan("spv_${turn}_${loans.size + 1}", "SPV Kalimas Funding (di luar laporan resmi)", amt, rate,
                currencyAud = false, amortPerMonth = 0.0, amortStartTurn = 9999, maturityTurn = turn + 36,
                floatingSpreadPct = null, covenantIcr = 0.0, bullet = true)
            newDebt += amt
            lines += ReportLine("Dana SPV ${fmt(amt)} jt RS cair (bunga tetap ${fmt2(rate)}%, bullet 36 bulan). Utang ini tidak tampil pada metrik kreditur selama belum terbongkar.", Tone.INFO)
        }
        pendingSpv.clear()

        // Pencairan obligasi korporasi baru (Era 3)
        for ((amt, couponPct, tenorMonths) in pendingBonds) {
            loans += Loan("bond_${turn}_${loans.size + 1}", "Obligasi Korporasi PT Benang Sundara", amt, couponPct,
                currencyAud = false, amortPerMonth = 0.0, amortStartTurn = 9999, maturityTurn = turn + tenorMonths,
                floatingSpreadPct = null, covenantIcr = 1.6, bullet = true)
            newDebt += amt
            lines += ReportLine("Obligasi Korporasi ${fmt(amt)} jt RS resmi tercatat dan cair (kupon tetap ${fmt2(couponPct)}%, jatuh tempo bullet $tenorMonths bulan).", Tone.GOOD)
        }
        pendingBonds.clear()

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
            }
            if (bankB != null) { principal += payoff; bankB.balance = 0.0 }
        }
        // Meja kredit
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
                if (l.bullet && l.balance > 0.5 && (l.maturityTurn - turn == 6 || l.maturityTurn - turn == 2)) {
                    lines += ReportLine("Peringatan: pokok bullet ${l.lender} sebesar ${fmt(l.balance * rsFactor)} jt RS jatuh tempo ${l.maturityTurn - turn} bulan lagi. Siapkan kas atau refinancing (kas sekarang ${fmt(cash)} jt RS).", Tone.WARN)
                }
                if (l.bullet && l.maturityTurn == turn && l.balance > 0.5) {
                    val pay = l.balance; l.balance = 0.0; principal += pay * rsFactor
                    lines += ReportLine("Pokok bullet ${l.lender} jatuh tempo dan dilunasi: ${fmt(pay * rsFactor)} jt RS.", Tone.WARN)
                }
            }
        }
        if (macDemandTurn == turn) {
            val au = loans.firstOrNull { it.id == "aurelia" }
            if (au != null && au.balance > 0) {
                val pay = au.balance * 0.3
                au.balance -= pay
                principal += pay * kurs
                lines += ReportLine("Pelunasan paksa 30% pokok Aurelia: ${fmt(pay * kurs)} jt RS.", Tone.BAD)
            }
        }
        for (l in loans) {
            if (l.balance > 0.0 && turn >= l.amortStartTurn && l.amortPerMonth > 0.0) {
                val p = min(l.balance, l.amortPerMonth)
                l.balance -= p
                principal += if (l.currencyAud) p * kurs else p
            }
        }
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
        // Selisih kurs atas utang A$ (dilindungi kontrak Forward Valas jika fxHedgeActive!)
        val fxBefore = prevBalance["debt_st"]!! + prevBalance["debt_lt"]!!
        val debtAfterFlows = totalDebt()
        val rawFxLoss = debtAfterFlows - (fxBefore + newDebt - principal)
        // Selisih kurs dibukukan berapa pun kecilnya: ambang lama (> 1 jt RS) membuang selisih kecil dan neraca meleset.
        if (abs(rawFxLoss) > 1e-9) {
            if (fxHedgeActive && rawFxLoss > 0.0) {
                // Kontrak forward valas menutup kerugian depresiasi kurs atas utang A$
                val covered = rawFxLoss * 0.90
                pendingIncome += covered
                lines += ReportLine("Lindung Nilai Forward Valas aktif: kerugian kurs utang A$ sebesar ${fmt(covered)} jt RS dikompensasi kontrak forward.", Tone.GOOD)
            }
            equity -= rawFxLoss; eqFx = rawFxLoss
            if (rawFxLoss > 50.0 && !fxHedgeActive) lines += ReportLine("Rugi selisih kurs utang A$: ${fmt(rawFxLoss)} jt RS (mengurangi ekuitas).", Tone.BAD)
        }

        // ---- 5b. Dividen ----
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

        var bbThisTurn = 0.0
        if (pendingBuyback > 0.0) {
            val b = min(pendingBuyback, max(0.0, cash + ni + dep - dividend - 1500.0))
            pendingBuyback = 0.0
            if (b > 0.0 && isPublicCompany && stockPrice > 0.0) {
                val retired = b / stockPrice
                sharesOutM -= retired; sharesRetiredM += retired; buybackTotalRs += b
                founderSharePct = (founderSharesM / sharesOutM * 100.0).coerceAtMost(100.0)
                dividend += b; equity -= b; bbThisTurn = b
                lines += ReportLine("Pembelian kembali saham ${fmt(b)} jt RS: ${fmt1(retired)} juta lembar ditarik di harga ${fmt(stockPrice)} RS.", Tone.INFO)
            }
        }
        if (isPublicCompany && sharesOutM > 0.0) {
            lastDividendPerShare = (dividend - bbThisTurn) / sharesOutM
            updateStockPrice(ni)
        }

        // ---- 6. Kas Induk ----
        val dAR = totalAR() - startAR
        val dInv = inventory - startInv
        val dPay = payables - startPay
        val cfo = ni + dep - dAR - dInv + dPay
        cash += cfo - capex - periodSubInvestOutRs + newDebt - principal - dividend

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
            factoringUsedCount++
            lines += ReportLine("Kas negatif: piutang ${fmt(faceValue)} jt RS dijual ke perusahaan factoring dengan potongan 4%.", Tone.BAD)
            if (cash < 0.0) {
                gameOverReason = "Kas habis dan tidak ada lagi piutang yang bisa dijual. Perusahaan bangkrut karena cash crunch, walaupun laporan laba rugi masih bisa terlihat untung."
            }
        }

        // ---- 7. Covenant kuartalan & Kepercayaan Dewan ----
        ebitHist += ebit
        interestHist += interest
        if (turn % 3 == 0 && gameOverReason == null) {
            val th = covenantThreshold()
            val icr = ebitHist.takeLast(3).sum() / max(1.0, interestHist.takeLast(3).sum() - 3.0 * spvMonthlyInterest())
            val ebitdaAnnual = (ebitHist.takeLast(3).sum() + 3 * dep) * 4.0
            val netDebtRatio = (reportedDebt() - cash) / max(1.0, ebitdaAnnual)
            val breach = (th > 0 && icr < th) || netDebtRatio > 3.5
            if (breach) {
                covenantBreaches++
                boardTrust = (boardTrust - 12).coerceAtLeast(0)
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
                        factoringUsedCount++
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
                boardTrust = (boardTrust + 4).coerceAtMost(100)
                lines += ReportLine("Uji covenant lulus: ICR ${fmt2(icr)}x (batas ${fmt2(th)}x).", Tone.GOOD)
            }
            if (boardTrust <= 0 && gameOverReason == null) {
                gameOverReason = "Dewan Direksi kehilangan seluruh kepercayaan atas kepemimpinan Anda dan mengesahkan mosi pemecatan CEO dalam rapat kuartalan."
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

        // Akhir kampanye 40 tahun (Giliran 480 = Desember 2069/2070)
        if (turn >= 480 && gameOverReason == null) {
            isVictory = true
            val eval = evaluateCampaign()
            gameOverReason = "KAMPANYE 40 TAHUN SELESAI (2030–2070)! ${eval.summaryText}"
        }

        // ---- 9. Catat laporan Induk, Konsolidasi Grup (dengan Eliminasi Antar-Entitas & CTA), dan maju ----
        lastIncome = mapOf("revenue" to revenue, "cogs" to cogs, "opex" to opex, "maint" to maint, "dep" to dep,
            "baddebt" to writeOff, "ebit" to ebit, "interest" to interest, "intinc" to interestIncome, "tax" to tax, "wht_tax" to whtNow, "ni" to ni) +
            detailNow + mapOf("op_proj" to projExp, "op_oneoff" to oneOffNow, "op_loss" to otherLoss, "op_gain" to incomeNow, "op_ic_fee" to periodIntercompanyFeeToSubRs)
        run {
            val cfi = -capex - periodSubInvestOutRs - periodInvestOut + periodInvestIn + divInCash + periodSubDivInGrossRs
            val cff = newDebt - principal + rcfFlow - forcedPay + factorProceeds - dividend + periodCureIn
            val net = cash - startCash
            val eqSum = periodCureIn - periodTradeFees + periodSubDivInGrossRs - periodSubWriteOffRs - periodGoodwillImpairmentRs + ni + mtm + divInCash - factorDiscount - eqFx - dividend
            lastDetail = mapOf(
                "d_capex_repl" to -replacement, "d_capex_spin" to -capSpin, "d_capex_exp" to -(expCapex + evCapex + periodSubInvestOutRs),
                "d_stock_buy" to -periodInvestOut, "d_stock_sell" to periodInvestIn, "d_divin" to (divInCash + periodSubDivInGrossRs), "cfi" to cfi,
                "d_newdebt" to newDebt, "d_repay" to -principal, "d_rcf" to rcfFlow, "d_forced" to -forcedPay,
                "d_factor" to factorProceeds, "d_divpaid" to -dividend, "d_cure" to periodCureIn, "cff" to cff,
                "cfo" to cfo, "net" to net, "d_ni" to ni, "d_dep" to dep, "d_ar" to -dAR, "d_inv" to -dInv, "d_pay" to dPay, "d_resid" to net - (cfo + cfi + cff),
                "e_open" to periodStartEquity, "e_cure" to periodCureIn, "e_fees" to -periodTradeFees, "e_sub_div" to periodSubDivInGrossRs,
                "e_impair" to -(periodSubWriteOffRs + periodGoodwillImpairmentRs), "e_ni" to ni, "e_mtm" to mtm,
                "e_divin" to divInCash, "e_factor" to -factorDiscount, "e_fx" to -eqFx, "e_div" to -dividend, "e_close" to equity,
                "e_resid" to equity - periodStartEquity - eqSum)
        }
        forensics.record(revenue, cogs, opex + maint, dep, ni, cfo, totalAR(), totalAssets(), fixedAssets, totalLiabilities(),
            max(0.0, totalAssets() - cash - totalAR() - inventory - fixedAssets))
        history += MonthSnap(turn, cash, totalDebt(), equity, revenue, ni, covenantDistancePct(), ethicsLoad, priceAdjPct, costIndex, kurs)
        prevCashFlow = lastCashFlow
        lastCashFlow = mapOf("ni" to ni, "dep" to dep, "wc" to (-dAR - dInv + dPay), "cfo" to cfo,
            "capex" to -(capex + periodSubInvestOutRs), "fin" to newDebt - principal, "stock" to periodInvestIn - periodInvestOut, "div" to (lastDividendIncome + periodSubDivInGrossRs), "net" to cash - startCash)

        // Laporan Konsolidasi Grup (Induk + Anak Usaha Aktif, dengan Eliminasi Pendapatan/Beban & Dividen Antar-Entitas)
        run {
            val validSubs = subsidiaries.values.filter { it.active && !it.nationalized && it.lastIncomeLocal.isNotEmpty() }
            fun sumSubInc(key: String): Double =
                validSubs.sumOf { (it.lastIncomeLocal[key] ?: 0.0) * foreignMeta.getValue(it.spec.countryId).fxToRs }
            fun sumSubCf(key: String): Double =
                validSubs.sumOf { (it.lastCashFlowLocal[key] ?: 0.0) * foreignMeta.getValue(it.spec.countryId).fxToRs }

            val icRevElim = periodIntercompanyFeeToSubRs
            val icDivElim = periodSubDivInGrossRs
            val consRev = revenue + sumSubInc("revenue") - icRevElim
            val consCogs = cogs + sumSubInc("cogs")
            val consOpex = opex + sumSubInc("opex") - icRevElim
            val consDep = dep + sumSubInc("dep")
            val consEbit = ebit + sumSubInc("ebit")
            val consInt = interest + sumSubInc("interest")
            val consEbt = taxable + sumSubInc("ebt")
            val consTax = tax + sumSubInc("tax")
            val consNi = ni + sumSubInc("ni")
            val nciNiRs = validSubs.sumOf { (it.lastIncomeLocal["ni"] ?: 0.0) * foreignMeta.getValue(it.spec.countryId).fxToRs * it.nciPct }
            ctaEquity = computeCtaEquity()
            val consEqClose = consolidatedEquity()
            val parentDirect = (periodCureIn - periodTradeFees - periodSubWriteOffRs - periodGoodwillImpairmentRs + mtm + divInCash - factorDiscount - eqFx - dividend - periodNciDivRs)
            val consOpen = if (consEquityPrev.isNaN()) consEqClose - consNi - parentDirect - (ctaEquity - ctaPrev) else consEquityPrev
            lastConsolidatedEquity = mapOf(
                "open" to consOpen, "ni" to consNi, "direct" to parentDirect, "cta" to (ctaEquity - ctaPrev), "deconsol" to periodSubDeconsolRs,
                "close" to consEqClose, "resid" to (consEqClose - consOpen - consNi - parentDirect - (ctaEquity - ctaPrev) - periodSubDeconsolRs)
            )
            consEquityPrev = consEqClose; ctaPrev = ctaEquity
            lastConsolidatedIncome = mapOf(
                "revenue" to consRev, "cogs" to consCogs, "opex" to consOpex, "maint" to maint,
                "dep" to consDep, "baddebt" to writeOff, "ebit" to consEbit, "interest" to consInt,
                "ebt" to consEbt, "tax" to consTax, "wht_tax" to whtNow, "ni" to consNi,
                "elim_intercompany_rev" to -icRevElim, "elim_intercompany_div" to -icDivElim,
                "ni_nci" to nciNiRs, "ni_parent" to (consNi - nciNiRs)
            )
            lastConsolidatedBalance = consolidatedBalanceSnapshot()
            val consCfo = cfo + sumSubCf("cfo")
            // Dividen internal dieliminasi pada nilai penutupan yang dipakai di sisi anak usaha; selisih kurs dividen dalam perjalanan masuk ke pengaruh kurs
            val icDivElimClose = validSubs.sumOf { it.lastDivParentLocal * foreignMeta.getValue(it.spec.countryId).fxToRs }
            // Investasi: belanja modal induk + pembangunan anak usaha (kas keluar grup) - kas anak usaha yang diperoleh saat aktivasi + investasi saham + CFI lokal
            val consCfi = (-capex - periodSubInvestOutRs - periodInvestOut + periodInvestIn + divInCash) + sumSubCf("cfi") + periodSubCashAcquiredRs
            val consCff = (newDebt - principal + rcfFlow - forcedPay + factorProceeds - dividend + periodCureIn) + sumSubCf("cff") + icDivElimClose
            // Pengaruh kurs terhadap kas: saldo kas awal anak usaha (dikurangi dividen yang ditarik) dinilai ulang ke kurs penutupan
            var fxCash = 0.0
            for ((cid, snap) in subCashSnap) {
                val sub = subsidiaries[cid] ?: continue
                if (!sub.active || sub.nationalized) continue
                fxCash += (snap.first - sub.lastDivPaidLocal) * (foreignMeta.getValue(cid).fxToRs - snap.second)
                // Dividen ke pemilik minoritas keluar dari grup pada kurs awal bulan, sedangkan arus kas lokal dihitung pada kurs penutupan
                fxCash += (sub.lastDivPaidLocal - sub.lastDivParentLocal) * (foreignMeta.getValue(cid).fxToRs - snap.second)
            }
            val consNet = consCfo + consCfi + consCff + fxCash - periodSubSeizedCashRs
            lastConsolidatedCashFlow = mapOf(
                "cfo" to consCfo, "cfi" to consCfi, "cff" to consCff,
                "fx_cash" to fxCash, "seized_cash" to -periodSubSeizedCashRs, "sub_cash_acquired" to periodSubCashAcquiredRs,
                "elim_div" to -icDivElimClose, "net" to consNet
            )
            subCashSnap.clear()
            for ((cid, sub) in subsidiaries) if (sub.active && !sub.nationalized) subCashSnap[cid] = Pair(sub.cashLocal, foreignMeta.getValue(cid).fxToRs)
        }

        periodInvestIn = 0.0; periodInvestOut = 0.0; periodStartCash = cash
        periodCureIn = 0.0; periodTradeFees = 0.0; periodStartEquity = equity
        periodSubInvestOutRs = 0.0; periodSubDivInGrossRs = 0.0; periodGoodwillImpairmentRs = 0.0
        periodSubCashAcquiredRs = 0.0; periodSubSeizedCashRs = 0.0; periodSubDeconsolRs = 0.0; periodNciDivRs = 0.0
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
            val eval = evaluateCampaign()
            lastReport = lastReport +
                ReportLine(gameOverReason!!, if (isVictory) Tone.GOOD else Tone.BAD) +
                ReportLine(eval.summaryText, Tone.INFO)
            chronicle += ChronicleItem(turn, if (isVictory) "Kampanye Selesai: ${eval.archetypeTitle}" else "Permainan berakhir: ${eval.archetypeTitle}",
                "$gameOverReason ${eval.summaryText}", "Lihat post-mortem dan evaluasi 5 komponen.")
        }

        if (gameOverReason == null && (turn + 1) % 3 == 0) boardMeeting(lines)
        if (gameOverReason == null) successionMilestones(lines)
        if (gameOverReason == null) checkEarlyUnlock(lines)
        if (gameOverReason == null && era(turn + 1) != era(turn)) eraTransitionReview(era(turn), lines)
        turn += 1
        currentEra = era(turn)
        ap = apMax
        val newLines = mutableListOf<ReportLine>()
        drawEvents(newLines)
        if (newLines.isNotEmpty()) lastReport = lastReport + newLines
        memos = generateMemos(rng(4))
        news = generateNews(rng(5))
        checkedNews.clear()
    }

    // =====================================================================
    // Memo eksekutif
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
                else "Runway ${runwayMonths()} bulan. Jarak ke covenant ${covenantDistancePct().toInt()}%. Tarif PPh efektif kita ${fmt1(effectiveTaxRate() * 100)}%.")
        }
        val coo = exec("exec_coo")
        if (coo.present) out += Memo(coo.id, coo.name, coo.role,
            if (kickbackActive) "Harga kapas dari pemasok baru sedikit lebih tinggi, tetapi kualitasnya jauh lebih stabil."
            else listOf("Pemasok kapas Tarsis meminta pembayaran lebih cepat karena El Niño.",
                "Jadwal pengiriman lancar. Proposal pemintalan dan ekspansi lintas batas layak dipertimbangkan.").random(r))
        return out.shuffled(r).take(3)
    }

    // =====================================================================
    // Koran pagi: satu topik, lima sudut pandang dengan akurasi berbeda
    // =====================================================================
    /** Angka di kabar media bisnis meleset sesuai kesulitan (Magang: akurat; Krisis Abadi: ±21%). */
    private fun noisy(v: Double, r: Random): Double =
        v * (1.0 + (r.nextDouble() * 2 - 1) * 0.12 * (difficulty.noiseMult - 0.25).coerceAtLeast(0.0))

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
                piece("BISNIS", "Survei: upah pabrik tekstil naik rata-rata ${fmt1(noisy(inflationAnnual * 0.8, r))}% tahun ini", "Asosiasi pengusaha mencatat tekanan upah di kawasan industri.", 1),
                piece("PRO_PEMERINTAH", "Pemerintah: kenaikan upah akan 'terukur dan adil'", "Menteri tenaga kerja menolak tuntutan di atas inflasi.", 0),
                piece("OPOSISI", "Harga pangan melonjak, buruh tekstil makin terjepit", "Mimbar Rakyat: upah riil menurun sejak tahun lalu.", 1),
                piece("SENSASIONAL", "MOGOK NASIONAL TEKSTIL SEBULAN LAGI?!", "Sumber anonim di grup pesan pekerja.", 0, true),
                piece("INDEPENDEN", if (unionOpen) "Serikat Kalimas siap mogok bila tuntutan tak dijawab sebelum tenggat" else "Inflasi ${fmt1(inflationAnnual)}% menggerus upah riil; tuntutan baru diperkirakan dalam beberapa bulan",
                    "Analisis data upah dan wawancara pengurus serikat.", 2)
            )
            "kapas" -> listOf(
                piece("BISNIS", if (cot > 1.0) "Harga kapas naik, margin pabrik tertekan" else "Harga kapas turun, peluang menekan HPP", "Indeks kapas ${fmt2(noisy(cot, r))} dari basis 1,00.", 1),
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
                piece("BISNIS", "Perang harga kain di kalangan produsen menengah", "Rata-rata harga pesaing turun ${fmt1(noisy((1.0 - world.rivalAveragePrice()) * 100, r))}%.", 1),
                piece("PRO_PEMERINTAH", "Persaingan sehat menguntungkan konsumen", "Pejabat memuji harga lebih murah.", 0),
                piece("OPOSISI", "Perang harga tekan upah pekerja", "Serikat khawatir PHK.", 1),
                piece("SENSASIONAL", "PESAING BANTING HARGA 50%!", "Pesan dari agen penjualan yang melebih-lebihkan.", 0, true),
                piece("INDEPENDEN", "Pesaing menurunkan harga rata-rata ${fmt1((1.0 - world.rivalAveragePrice()) * 100)}% (${world.learnedPlayerProfile})", "Pemantauan harga di 40 toko ritel.", 2)
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
        /** Biaya L/C bulanan (jt RS pada skala 1), tarif sewa kembali per bulan, dan batas aset yang boleh disewa kembali. */
        const val LC_FEE = 31.0
        /** B5c: ambang antimonopoli: telaah bersyarat, blokir, dan pengawasan dominasi (pangsa gabungan atau organik). */
        const val ANTITRUST_REMEDY_SHARE = 0.33
        const val ANTITRUST_BLOCK_SHARE = 0.45
        const val ANTITRUST_WATCH_SHARE = 0.35
        const val LEASE_RATE = 0.0095
        const val LEASEBACK_MAX = 0.40
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
            InvestSpec("fleet", "Armada truk dan kontainer sendiri", "Armada milik sendiri mengurangi ketergantungan pada ekspeditur dan penundaan pengiriman.",
                1400.0, 2, 1, true, "HPP −0,4%, risiko pengiriman tertahan −20%, perawatan 18 jt RS per bulan", 1),
            InvestSpec("export", "Pasar ekspor Aurelia", "Sertifikasi, pameran dagang, dan agen penjualan luar negeri.",
                1500.0, 6, 3, false, "Permintaan +6% permanen, biaya operasi +40 jt RS per bulan", 1),
            InvestSpec("solar", "PLTS Atap & IPAL Hijau (Transisi Era 4)", "Panel surya kawasan dan daur ulang air limbah untuk sertifikasi ESG penuh dan bebas pajak karbon Halvenia (CBAM).",
                2600.0, 4, 2, true, "Sertifikasi ESG/IPAL penuh (bebas CBAM Halvenia) & permintaan +3%", 1),
            InvestSpec("institute", "Institut Kepemimpinan & Suksesi (Warisan Era 5)", "Akademi kaderisasi manajemen dan tata kelola institusi agar konglomerasi tahan melintasi generasi.",
                2000.0, 4, 2, false, "Kepercayaan Dewan +15 poin & loyalitas seluruh eksekutif naik", 1)
        )

        /** Spesifikasi 4 Anak Usaha Multinasional (Era 2 Ekspansi Lintas Batas — dikalibrasi payback median 3,5–6,5 tahun). */
        val SUBSIDIARIES = listOf(
            SubsidiarySpec(
                countryId = "tarsis",
                countryName = "Tarsis",
                currencyCode = "TRS",
                title = "PT Benang Tarsis Agro (Pabrik Hulu Kapas)",
                desc = "Mendirikan pabrik pengolahan kapas dan pemintalan di lembah irigasi Tarsis dengan buruh murah.",
                baseCostRs = 3800.0,
                buildMonths = 3,
                apCost = 3,
                cogsReduction = 0.022,
                demandBoost = 0.04,
                workersAdded = 140,
                benefitText = "HPP −2,2%, kapasitas +6%, permintaan +4%, pembukuan lokal TRS (PPh lokal 20%, pajak dividen 10%)",
                riskText = "Rentan kekeringan El Niño, kurva belajar 12 bulan, dan gejolak politik Tarsis.",
                capitalTurnoverAnnual = 1.34,
                baseLocalCogsRatio = 0.66,
                fixedOpexRs = 42.0,
                isFactory = true
            ),
            SubsidiarySpec(
                countryId = "kestrel",
                countryName = "Kestrel",
                currencyCode = "KST",
                title = "Kestrel Precision Weaving Ltd (Pabrik Komponen & Kain Teknis)",
                desc = "Pabrik patungan di kawasan manufaktur Kestrel untuk mengamankan sensor tenun dan ekspor Asia.",
                baseCostRs = 4800.0,
                buildMonths = 4,
                apCost = 3,
                cogsReduction = 0.018,
                demandBoost = 0.05,
                workersAdded = 110,
                benefitText = "HPP −1,8%, kapasitas +6%, kebal hold-up suku cadang Kestrel, pembukuan lokal KST (PPh 18%, pajak dividen 8%)",
                riskText = "Terpapar perang tarif Aurelia–Kestrel, kurva belajar 12 bulan, dan pembatasan ekspor teknologi.",
                capitalTurnoverAnnual = 1.29,
                baseLocalCogsRatio = 0.65,
                fixedOpexRs = 52.0,
                isFactory = true
            ),
            SubsidiarySpec(
                countryId = "halvenia",
                countryName = "Halvenia",
                currencyCode = "HLV",
                title = "Halvenia Eco-Apparel GmbH (Distribusi & Merek Garmen ESG)",
                desc = "Cabang distribusi garmen premium di pasar konsumen kaya Halvenia dengan mata uang kuat HLV.",
                baseCostRs = 3400.0,
                buildMonths = 3,
                apCost = 2,
                cogsReduction = 0.0,
                demandBoost = 0.05,
                workersAdded = 45,
                benefitText = "Permintaan premium +5%, pembukuan mata uang kuat HLV (PPh 26%, pajak dividen 5%)",
                riskText = "Wajib kepatuhan IPAL/ESG; tanpa IPAL dikenai bea karbon CBAM setiap bulan.",
                capitalTurnoverAnnual = 1.30,
                baseLocalCogsRatio = 0.67,
                fixedOpexRs = 38.0,
                isFactory = false
            ),
            SubsidiarySpec(
                countryId = "meridia",
                countryName = "Meridia",
                currencyCode = "MRD",
                title = "Meridia Global Treasury & Holding SPV",
                desc = "Entitas induk perdagangan, kekayaan intelektual, dan pusat kas lepas pantai di surga pajak Meridia.",
                baseCostRs = 2200.0,
                buildMonths = 2,
                apCost = 2,
                cogsReduction = 0.004,
                demandBoost = 0.02,
                workersAdded = 15,
                benefitText = "PPh badan Meridia hanya 8,5%, menerima royalti/harga transfer antar-entitas & memotong 50% pajak dividen repatriasi",
                riskText = "Memicu risiko pemeriksaan pajak lintas batas bila memakai Transfer Pricing Agresif.",
                capitalTurnoverAnnual = 0.95,
                baseLocalCogsRatio = 0.58,
                fixedOpexRs = 24.0,
                isFactory = false
            )
        )

        /** Sinergi anak usaha terhadap induk (HPP/permintaan) diskon: pendapatan anak usaha sendiri sudah dikonsolidasi, jadi tidak dihitung ganda penuh. */
        const val SUB_SYNERGY = 0.5
        const val LOT = 0.1
        const val BROKER_FEE = 0.0015
        const val SELL_TAX = 0.001
        const val FACTOR_KEEP = 0.96
        const val STUFFED_LOSS = 0.45
        const val REPLACEMENT_CAPEX = 0.70

        private val idLocale: Locale = Locale.forLanguageTag("id-ID")
        val MONTHS = listOf("Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli",
            "Agustus", "September", "Oktober", "November", "Desember")

        fun dateText(turn: Int): String = "${MONTHS[(turn - 1) % 12]} ${2030 + (turn - 1) / 12}"
        /** 4 era sesuai dokumen desain: 2030–2037, 2038–2048, 2049–2060, 2061–2070. */
        val ERA_START_TURNS = listOf(1, 97, 229, 373)
        val ERA_NAMES = listOf("Bertahan Hidup", "Ekspansi Lintas Batas", "Konglomerasi dan Finansialisasi", "Kekaisaran Global dan Suksesi")
        fun era(turn: Int): Int = when {
            turn <= 96 -> 1
            turn <= 228 -> 2
            turn <= 372 -> 3
            else -> 4
        }

        fun fmt(x: Double): String = String.format(idLocale, "%,.0f", x)
        fun fmt1(x: Double): String = String.format(idLocale, "%.1f", x)
        fun fmt2(x: Double): String = String.format(idLocale, "%.2f", x)
        fun signed(x: Double): String = (if (x >= 0) "+" else "−") + fmt(abs(x))
    }
}


/** C3: sakelar keseimbangan global yang disetel lewat simulasi besar (lihat docs/AUDIT_vs_KONSEP.md, baris C3). */
object BalanceTuning {
    /** Pengali batas ICR covenant bank (1,0 = nilai skenario apa adanya). 0,6 hasil sapuan C3: kematian Era 1 pasif 34%→18%, cakap 23%→13%. */
    @JvmField var covenantScale = 0.6
    /** C5: bila true, fitur era berikutnya dapat dibuka lebih awal oleh kinerja (kalender tetap jaring pengaman). */
    @JvmField var performanceUnlock = true
}
