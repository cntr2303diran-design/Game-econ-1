package com.example.model

enum class SemanticTone {
    DANGER_RED,     // Merah = bahaya
    WARNING_YELLOW, // Kuning = peringatan
    GOOD_GREEN,     // Hijau = membaik
    PRIMARY_BLUE,   // Biru = aksi utama
    NEUTRAL_GRAY    // Abu-abu = netral / data tertunda
}

enum class ConfidenceTier(val label: String, val tone: SemanticTone) {
    LOW("Keyakinan rendah", SemanticTone.DANGER_RED),
    MEDIUM("Keyakinan sedang", SemanticTone.WARNING_YELLOW),
    HIGH("Keyakinan tinggi", SemanticTone.GOOD_GREEN)
}

/**
 * Metadata yang ditampilkan saat pengguna melakukan ketuk lama (long-press) pada angka mana pun
 * untuk membuka Bottom Sheet "Asal data" (sumber, tanggal, cara hitung, bias).
 */
data class DataOriginInfo(
    val title: String,
    val displayedValue: String,
    val sourceName: String,
    val dataDate: String,
    val calculationMethod: String,
    val uncertaintyAndBiasNote: String,
    val confidence: ConfidenceTier
)

/**
 * Model untuk kartu indikator di Markas dan di seluruh layar.
 */
data class Indicator(
    val id: String,
    val label: String,
    val valueText: String,
    val deltaOrSubText: String,
    val tone: SemanticTone,
    val dataDate: String,
    val source: String,
    val confidence: ConfidenceTier,
    val originInfo: DataOriginInfo
)

enum class MediaSlant(
    val outletName: String,
    val slantLabel: String,
    val editorialProfile: String
) {
    BISNIS("Harian Bisnis", "Bisnis", "Fokus likuiditas, suku bunga, rantai pasok, dan covenant bank"),
    PRO_PEMERINTAH("Suara Sundara", "Pro-Pemerintah", "Menonjolkan stabilitas makro dan memperhalus angka inflasi jelang pemilu"),
    OPOSISI("Mimbar Rakyat", "Oposisi", "Menyorot tajam pemadaman listrik, biaya hidup, dan tuntutan serikat pekerja"),
    SENSASIONAL("Kilat Sore", "Sensasional", "Judul bombastis; rawan ditunggangi rumor spekulan atau short-seller"),
    INDEPENDEN("Lensa Independen", "Independen", "Jurnalisme investigatif rantai pasok dan akuntansi forensik")
}

data class NewsItem(
    val id: String,
    val mediaSlant: MediaSlant,
    val headline: String,
    val summary: String,
    val dateLabel: String,
    val confidence: ConfidenceTier,
    val isRumor: Boolean = false,
    val isMacroRelease: Boolean = false,
    val crossChecked: Boolean = false,
    val eventTopicId: String = "KESTREL_RARE_EARTH",
    val originInfo: DataOriginInfo
)

data class Decision(
    val id: String,
    val title: String,
    val subtitle: String,
    val apCost: Int,
    val badgeText: String,
    val tone: SemanticTone,
    val assetSharePct: Int,
    val defaultContextNote: String,
    val isCompleted: Boolean = false,
    val opensEventDialog: Boolean = false,
    val options: List<Pair<String, String>> = emptyList(), // (id, label) untuk keputusan pilihan
    val canReject: Boolean = false
)

data class MemoItem(val from: String, val role: String, val text: String)

data class ReportLineItem(val text: String, val tone: SemanticTone)

data class ContractActionItem(val id: String, val label: String, val enabled: Boolean)

data class CompletedJournalEntry(
    val id: String,
    val decisionTitle: String,
    val turnSubmitted: Int,
    val evaluateAtTurn: Int,
    val strategicReason: String,
    val numericPrediction: String,
    val confidencePct: Int,
    val failureScenario: String,
    val evaluated: Boolean = false,
    val brierScoreContribution: Double? = null
)

data class Executive(
    val id: String,
    val initials: String,
    val name: String,
    val roleTitle: String,
    val loyaltyEstimate: String,   // Ditampilkan sebagai perkiraan ("Tinggi?", "Sedang?", "Rendah?")
    val ambitionEstimate: String,  // Ditampilkan sebagai perkiraan ("Tinggi?", "Sedang?")
    val skillEstimate: String,
    val horizonNote: String,
    val incentiveScheme: String,
    val suspectedBehaviorNote: String,
    val loyaltyTone: SemanticTone,
    val originInfo: DataOriginInfo,
    val present: Boolean = true,
    val signalNote: String = "",
    val auditStatus: String = "",
    val canAudit: Boolean = false,
    val canConfront: Boolean = false,
    val canFire: Boolean = false,
    val lastOutcome: String = ""
)

enum class MapTokenType(val label: String) {
    PABRIK("Pabrik"),
    GUDANG("Gudang"),
    TOKO("Toko"),
    KAPAL("Kapal")
}

enum class MapLayerFilter(val label: String) {
    PELAYARAN("Pelayaran"),
    ZONA_KONFLIK("Zona konflik"),
    RISIKO_IKLIM("Risiko iklim"),
    RANTAI_PASOK("Rantai pasok")
}

data class MapPiece(
    val id: String,
    val type: MapTokenType,
    val label: String,
    val countryId: String,
    val normX: Float,
    val normY: Float,
    val statusNote: String
)

data class Country(
    val id: String,
    val name: String,
    val currencyCode: String,
    val archetype: String,
    val opportunity: String,
    val mainRisk: String,
    val centralBankStyle: String,
    val inflationRange: String,
    val policyRate: String,
    val gdpGrowth: String,
    val tensionScore: Int, // 0..100
    val electionCountdown: String,
    val electricityReliability: String,
    val climateRiskLabel: String,
    val supplyChainRole: String,
    val polygonPoints: List<Pair<Float, Float>>, // Normalized 0f..1f coordinates for Canvas polygon
    val pieces: List<MapPiece>,
    val originInfo: DataOriginInfo
)

/** Satu baris lapisan dunia lanjutan: kondisi makro-fiskal satu negara inti. */
data class WorldLayerRow(
    val countryName: String,
    val rating: String,
    val debtText: String,
    val yieldText: String,
    val curveText: String,
    val tone: SemanticTone
)

/** Ringkasan lapisan dunia lanjutan (Tahap A): peringkat, panik, bank, rilis data, iklim, teknologi, demografi. */
data class WorldLayerView(
    val rows: List<WorldLayerRow> = emptyList(),
    val lines: List<Pair<String, String>> = emptyList()
)

/** B1: satu laporan intelijen yang sudah dibeli. */
data class IntelReportView(
    val title: String,
    val metric: String,
    val estimateText: String,
    val bandText: String,
    val whenText: String,
    val tierText: String
)

/** B1: pertanyaan rapat dewan yang sedang terbuka. */
data class BoardView(
    val member: String,
    val text: String,
    val metricLabel: String,
    val dueText: String,
    val answered: String?
)

/** B1: tampilan tab Intelijen (riset berbayar dan rapat dewan). */
data class IntelView(
    val kinds: List<Triple<String, String, List<Pair<String, String>>>> = emptyList(), // kode, label, subjek (kode, label)
    val tierCosts: List<String> = emptyList(),
    val reports: List<IntelReportView> = emptyList(),
    val board: BoardView? = null,
    val boardTrust: Int = 0,
    val boardRecord: String = ""
)

/** B2: satu segmen konsumen di tab Segmen. */
data class SegmentRowView(
    val code: String,
    val label: String,
    val weightText: String,
    val surveyText: String,
    val adoptionText: String,
    val level: Int,
    /** Alasan penolakan untuk memilih tingkat 0, 1, 2 (null = boleh). */
    val blocks: List<String?>
)

/** B2: tampilan tab Segmen (pasar produk). */
data class ProductMarketView(
    val rows: List<SegmentRowView> = emptyList(),
    val brandText: String = "",
    val focusText: String = "",
    val costText: String = ""
)

/** B3: satu jabatan karyawan kunci. */
data class StaffRowView(
    val code: String,
    val label: String,
    val present: Boolean,
    val skillText: String,
    val loyaltyText: String,
    val salaryText: String,
    val canHire: Boolean,
    val canFire: Boolean,
    val canRaise: Boolean
)

/** B3: tampilan tab Operasi (SDM, R&D, logistik, armada). */
/** B4c: anak usaha yang dapat dijual sebagian ke investor minoritas. */
data class StakeRowView(
    val countryId: String,
    val name: String,
    val statusText: String,
    val blockText: String?
)

/** B4b: baris derivatif: jenis, ringkasan kontrak aktif (atau null), alasan terblokir per porsi 25/50/100%, dan premi per porsi. */
data class DerivRowView(
    val code: String,
    val label: String,
    val activeText: String?,
    val blocks: List<String?>,
    val premiums: List<Double>,
    val hint: String
)

data class OperationsView(
    val staff: List<StaffRowView> = emptyList(),
    val hireCosts: List<String> = emptyList(),
    val rdLevel: Int = 0,
    val rdText: String = "",
    val logisticsLevel: Int = 0,
    val logisticsText: String = "",
    val fleetText: String = "",
    val costText: String = "",
    // B4a: keuangan lanjut
    val insuranceLevel: Int = 0,
    val insuranceText: String = "",
    val tradeFinance: Boolean = false,
    val tradeFinanceText: String = "",
    val leaseText: String = "",
    val leaseBlocks: List<String?> = emptyList(),
    // B4b: derivatif
    val derivs: List<DerivRowView> = emptyList(),
    // B4c: saham minoritas anak usaha dan grafik harga saham
    val stakes: List<StakeRowView> = emptyList(),
    val stockSeries: List<Double> = emptyList(),
    val stockText: String = "",
    // B5: forensik
    val auditorLevel: Int = 0,
    val auditText: String = "",
    val mScoreText: String = "",
    val mScoreFlag: Boolean = false,
    val dressText: String = "",
    val dressBlocks: List<String?> = emptyList()
)

data class GanttTask(
    val id: String,
    val projectName: String,
    val phaseName: String,
    val startMonth: Int, // 1..12
    val durationMonths: Int,
    val progressMonth: Int,
    val budgetRsMillion: Int,
    val realizedRsMillion: Int,
    val statusLabel: String,
    val tone: SemanticTone,
    val noteFromStaff: String
)

data class ContractItem(
    val id: String,
    val category: String, // "Kredit Bank", "Term Sheet", "Pemasok Tier-1..3", " Pelanggan Ritel"
    val counterparty: String,
    val countryName: String,
    val valueText: String,
    val maturityOrTenor: String,
    val covenantOrClause: String,
    val hiddenRiskNote: String,
    val tone: SemanticTone,
    val apCostToAct: Int = 2,
    val actionLabel: String = "Negosiasi / Tinjau",
    val originInfo: DataOriginInfo,
    val actions: List<ContractActionItem> = emptyList()
)

data class MarketInstrument(
    val id: String,
    val category: String, // "Komoditas", "Kurs Valas", "Saham & Korporasi"
    val name: String,
    val ticker: String,
    val priceText: String,
    val changeText: String,
    val tone: SemanticTone,
    val sparklineHistory: List<Float>,
    val fanProjectionMedian: List<Float>,
    val fanProjectionSpread: List<Float>,
    val forensicMScoreNote: String? = null,
    val originInfo: DataOriginInfo
)

data class EventOptionItem(
    val id: String,
    val title: String,
    val apCost: Int,
    val impactEstimate: String,
    val advisorAttribution: String = "menurut Direktur Operasi",
    val tone: SemanticTone
)

data class InvestItem(
    val id: String,
    val title: String,
    val description: String,
    val costText: String,
    val durationText: String,
    val benefitText: String,
    val apCost: Int,
    val enabled: Boolean,
    val blockReason: String?,
    val statusText: String
)

data class EventDialogModel(
    val id: String,
    val title: String,
    val contextDescription: String,
    val earlyWarningNote: String,
    val options: List<EventOptionItem>
)

data class FinancialRowItem(
    val label: String,
    val currentVal: String,
    val priorVal: String,
    val note: String,
    val isHeaderOrTotal: Boolean = false,
    val tone: SemanticTone = SemanticTone.NEUTRAL_GRAY,
    val originInfo: DataOriginInfo
)

data class PairwiseTension(
    val pairLabel: String,
    val straitOrBorder: String,
    val score: Int, // 0..100
    val escalationStage: String,
    val businessEffect: String,
    val tone: SemanticTone
)

data class ChronicleEntry(
    val turn: Int,
    val dateLabel: String,
    val eraLabel: String,
    val title: String,
    val description: String,
    val causalChain: String,
    val canReplayCounterfactual: Boolean = false,
    val actionIndex: Int? = null
)

/** Pesaing di pasar tekstil Sundara (harga hasil estimasi intel pasar, bukan data pasti). */
data class RivalItem(
    val id: String,
    val name: String,
    val priceText: String,
    val vsYouText: String,
    val statusText: String,
    val tone: SemanticTone
)

data class GameState(
    val dateText: String = "Maret 2033",
    val turn: Int = 39,
    val totalTurns: Int = 480,
    val eraNumber: Int = 1,
    val eraName: String = "Era 1: Bertahan Hidup",
    val apCurrent: Int = 4,
    val apMax: Int = 7,
    val runwayMonths: Int = 5,
    val activeRole: String = "CEO PT Benang Sundara",
    val isDarkTheme: Boolean? = null, // null = ikuti setelan HP
    val seed: Long = 0L,
    val covenantTone: SemanticTone = SemanticTone.WARNING_YELLOW,
    val memos: List<MemoItem> = emptyList(),
    val monthlyReportTitle: String = "",
    val monthlyReportLines: List<ReportLineItem> = emptyList(),
    val showMonthlyReport: Boolean = false,
    val gameOverReason: String? = null,
    val evaluationTitle: String = "",
    val evaluationRows: List<Pair<String, String>> = emptyList(),
    val salesSchemeLabel: String = "",
    val factorySchemeLabel: String = "",
    val salesSchemeOptions: List<Pair<String, String>> = emptyList(),
    val factorySchemeOptions: List<Pair<String, String>> = emptyList(),
    val priceAdjPct: Int = 0,
    val counterfactualText: String? = null,
    val ganttTitle: String = "",
    val ganttTotalMonths: Int = 12,
    val periodLabel: String = "",
    val prevPeriodLabel: String = "",
    val accrualWarning: String? = null,
    val hasSavedGame: Boolean = false,
    val companyEquityText: String = "",
    val rivals: List<RivalItem> = emptyList(),
    val bourse: BourseState = BourseState(),
    val actionPoints: Int = 0,
    val credit: CreditViewState = CreditViewState(),
    val actors: List<ActorItem> = emptyList(),
    val playerShareText: String = "",
    val unemploymentText: String = "",
    val marketNote: String = "",

    // Peringatan Covenant di Markas
    val covenantWarningTitle: String = "Jarak ke covenant 6%",
    val covenantWarningSubtitle: String = "Uji kuartalan Bank B dalam 1 giliran",
    val covenantOrigin: DataOriginInfo,

    // Kartu Koran Pagi Ringkas di Markas
    val featuredNewsSource: String = "Harian Bisnis",
    val featuredNewsConfidence: ConfidenceTier = ConfidenceTier.MEDIUM,
    val featuredNewsHeadline: String = "Kestrel dikabarkan batasi ekspor logam langka",
    val featuredNewsFooter: String = "+4 berita lain · 2 rumor · 1 rilis data",

    // 4 Indikator Utama di Markas (Grid 2 Kolom)
    val markasIndicators: List<Indicator>,

    // FanChart Proyeksi Kas 6 Bulan ke Depan
    val cashHistoryPoints: List<Float>,
    val cashFanMedianPoints: List<Float>,
    val cashFanBand50Points: List<Float>,
    val cashFanBand90Points: List<Float>,

    // Daftar Menunggu Keputusan di Markas
    val pendingDecisions: List<Decision>,
    val completedJournals: List<CompletedJournalEntry>,

    // Layar Koran Pagi (5 Media)
    val morningPaperEdition: List<NewsItem>,

    // Layar Peta & Dunia (8 Negara Inti)
    val countries: List<Country>,
    val mapPieces: List<MapPiece>,
    val activeMapLayer: MapLayerFilter = MapLayerFilter.PELAYARAN,
    val pairwiseTensions: List<PairwiseTension>,
    val worldLayer: WorldLayerView = WorldLayerView(),
    val intel: IntelView = IntelView(),
    val productMarket: ProductMarketView = ProductMarketView(),
    val operations: OperationsView = OperationsView(),

    // Layar Perusahaan (Laporan Keuangan, Organisasi, Proyek Gantt, Kontrak)
    val incomeStatementRows: List<FinancialRowItem>,
    val balanceSheetRows: List<FinancialRowItem>,
    val cashFlowRows: List<FinancialRowItem>,
    val equityRows: List<FinancialRowItem> = emptyList(),
    val notesRows: List<FinancialRowItem> = emptyList(),
    val groupRows: List<FinancialRowItem> = emptyList(),
    val entityRows: List<FinancialRowItem> = emptyList(),
    val investments: List<InvestItem> = emptyList(),
    val investSummary: List<Pair<String, String>> = emptyList(),
    val executives: List<Executive>,
    val ganttTasks: List<GanttTask>,
    val contracts: List<ContractItem>,

    // Layar Pasar (Komoditas, Kurs RS/A$, Saham & Forensik)
    val marketInstruments: List<MarketInstrument>,

    // Fitur PDF: Skor Kalibrasi Brier, Bias Kognitif, & Kronik Dunia
    val brierScore: Double = 0.16,
    val evaluatedJournals: Int = 0,
    val detectedBiases: List<String>,
    val worldChronicles: List<ChronicleEntry>,

    // Kartu Event Dialog & Bottom Sheet Asal Data
    val activeEventDialog: EventDialogModel,
    val eventOutcomeText: String? = null,
    val showEventDialog: Boolean = false,
    val inspectedDataOrigin: DataOriginInfo? = null,
    val selectedCountryForSheet: Country? = null
)

/** Bursa saham: satu emiten beserta laporan keuangan resminya. */
data class StockItem(
    val id: String,
    val ticker: String,
    val name: String,
    val sector: String,
    val blurb: String,
    val riskNote: String,
    val listed: Boolean,
    val priceText: String,
    val changeText: String,
    val changeTone: SemanticTone,
    val holdingLots: Int,
    val holdingText: String,
    val lotCostText: String,
    val unrealizedText: String,
    val unrealizedTone: SemanticTone,
    val spark: List<Float>,
    val ratios: List<Pair<String, String>>,
    val reportLabel: String,
    val reportNote: String,
    val income: List<FinancialRowItem>,
    val balance: List<FinancialRowItem>,
    val cashFlow: List<FinancialRowItem>,
    val canBuy: Boolean,
    val buyBlockReason: String
)

data class BourseState(
    val indexText: String = "",
    val indexChangeText: String = "",
    val indexTone: SemanticTone = SemanticTone.NEUTRAL_GRAY,
    val indexSpark: List<Float> = emptyList(),
    val cashText: String = "",
    val portfolioText: String = "",
    val unrealizedText: String = "",
    val dividendText: String = "",
    val stocks: List<StockItem> = emptyList(),
    val headlines: List<String> = emptyList()
)

/** Meja kredit dan pelaku. */
data class BankItem(
    val id: String,
    val name: String,
    val banker: String,
    val bankerRole: String,
    val style: String,
    val incentive: String,
    val tell: String,
    val tellKnown: Boolean,
    val relationText: String,
    val relationTone: SemanticTone,
    val rateText: String,
    val speedText: String,
    val maxTenor: Int,
    val maxAmountText: String,
    val foreign: Boolean,
    val canApply: Boolean,
    val blockReason: String
)

data class OfferItem(
    val amountText: String,
    val rateText: String,
    val tenorText: String,
    val feeText: String,
    val covenantText: String,
    val structureText: String,
    val extraTerms: List<String>,
    val hiddenTerms: List<String>,
    val hiddenKnown: Boolean,
    val validText: String
)

data class CreditAppItem(
    val id: String,
    val bankName: String,
    val banker: String,
    val amountText: String,
    val purposeText: String,
    val statusLabel: String,
    val statusTone: SemanticTone,
    val stageText: String,
    val offer: OfferItem?,
    val officialReason: String,
    val informalNote: String,
    val canReview: Boolean,
    val canAccept: Boolean,
    val canDecline: Boolean
)

data class LoanItem(
    val lender: String,
    val balanceText: String,
    val rateText: String,
    val maturityText: String,
    val repaymentText: String,
    val covenantText: String,
    val flags: List<String>
)

data class CreditViewState(
    val ratios: List<Pair<String, String>> = emptyList(),
    val collateral: List<Pair<String, String>> = emptyList(),
    val banks: List<BankItem> = emptyList(),
    val applications: List<CreditAppItem> = emptyList(),
    val loans: List<LoanItem> = emptyList(),
    val note: String = ""
)

data class ActorItem(
    val id: String,
    val group: String,
    val name: String,
    val role: String,
    val goal: String,
    val lastAction: String,
    val signals: List<String>,
    val advice: String,
    val tone: SemanticTone
)
