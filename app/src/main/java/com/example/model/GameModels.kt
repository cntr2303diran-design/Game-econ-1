package com.example.model

enum class SemanticTone {
    DANGER_RED,
    WARNING_YELLOW,
    GOOD_GREEN,
    PRIMARY_BLUE,
    NEUTRAL_GRAY
}

enum class ConfidenceTier(val label: String, val tone: SemanticTone) {
    LOW("Keyakinan rendah", SemanticTone.DANGER_RED),
    MEDIUM("Keyakinan sedang", SemanticTone.WARNING_YELLOW),
    HIGH("Keyakinan tinggi", SemanticTone.GOOD_GREEN)
}

data class DataOriginInfo(
    val title: String,
    val displayedValue: String,
    val sourceName: String,
    val dataDate: String,
    val calculationMethod: String,
    val uncertaintyAndBiasNote: String,
    val confidence: ConfidenceTier
)

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
    PRO_PEMERINTAH("Suara Sundara", "Pro-Pemerintah", "Menonjolkan stabilitas makro dan memperhalus angka inflasi"),
    OPOSISI("Mimbar Rakyat", "Oposisi", "Menyorot tajam pemadaman listrik, biaya hidup, dan tuntutan serikat pekerja"),
    SENSASIONAL("Kilat Sore", "Sensasional", "Judul bombastis; rawan ditunggangi rumor spekulan"),
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

data class DecisionOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val tone: SemanticTone
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
    val canReject: Boolean = false,
    val options: List<DecisionOption> = emptyList()
)

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
    val brierScoreContribution: Double? = null,
    val outcomeText: String? = null
)

data class Executive(
    val id: String,
    val initials: String,
    val name: String,
    val roleTitle: String,
    val loyaltyEstimate: String,
    val ambitionEstimate: String,
    val skillEstimate: String,
    val horizonNote: String,
    val incentiveScheme: String,
    val suspectedBehaviorNote: String,
    val loyaltyTone: SemanticTone,
    val originInfo: DataOriginInfo,
    val present: Boolean = true,
    val vacantNote: String = "",
    val canAudit: Boolean = true,
    val auditStatus: String = "Belum diaudit",
    val canConfront: Boolean = false,
    val canFire: Boolean = true,
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
    val tensionScore: Int,
    val electionCountdown: String,
    val electricityReliability: String,
    val climateRiskLabel: String,
    val supplyChainRole: String,
    val polygonPoints: List<Pair<Float, Float>>,
    val pieces: List<MapPiece>,
    val originInfo: DataOriginInfo,
    val fxToRsText: String = "1,00 RS",
    val corpTaxText: String = "22%",
    val tariffText: String = "0%",
    val hasSubsidiary: Boolean = false
)

data class GanttTask(
    val id: String,
    val projectName: String,
    val phaseName: String,
    val startMonth: Int,
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
    val category: String,
    val counterparty: String,
    val countryName: String,
    val valueText: String,
    val maturityOrTenor: String,
    val covenantOrClause: String,
    val hiddenRiskNote: String,
    val tone: SemanticTone,
    val apCostToAct: Int = 1,
    val actionLabel: String = "Tinjau dengan konsultan hukum (1 AP)",
    val originInfo: DataOriginInfo,
    val reviewed: Boolean = false,
    val revealedClause: String = "",
    val canAct: Boolean = true
)

data class MarketInstrument(
    val id: String,
    val category: String,
    val name: String,
    val ticker: String,
    val priceText: String,
    val changeText: String,
    val tone: SemanticTone,
    val sparklineHistory: List<Float>,
    val fanProjectionMedian: List<Float>,
    val fanProjectionSpread: List<Float>,
    val forensicMScoreNote: String? = null,
    val originInfo: DataOriginInfo,
    val actionLabel: String? = null,
    val actionEnabled: Boolean = false
)

data class EventOptionItem(
    val id: String,
    val title: String,
    val apCost: Int,
    val impactEstimate: String,
    val advisorAttribution: String = "menurut Direktur Operasi",
    val tone: SemanticTone
)

data class EventDialogModel(
    val id: String,
    val title: String,
    val contextDescription: String,
    val earlyWarningNote: String,
    val options: List<EventOptionItem>,
    val isCascadingChain: Boolean = false
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
    val score: Int,
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

data class TurnReportItem(val text: String, val tone: SemanticTone)

data class MemoItem(val execId: String, val from: String, val role: String, val text: String)

data class SaveSlotInfo(
    val slot: Int,
    val exists: Boolean,
    val summary: String
)

data class RivalRow(
    val id: String,
    val name: String,
    val archetype: String,
    val priceRelText: String,
    val shareText: String,
    val statusText: String,
    val tone: SemanticTone,
    val activeStrategyText: String = "Bersaing normal",
    val warningSignalText: String = "",
    val canAcquire: Boolean = false,
    val acquireCostText: String = "",
    val acquireBlockReason: String? = null
)

data class InvestCard(
    val id: String,
    val title: String,
    val desc: String,
    val costText: String,
    val durationText: String,
    val apCost: Int,
    val accountingNote: String,
    val benefitText: String,
    val statusText: String,
    val canStart: Boolean,
    val blockReason: String?
)

/** Model UI untuk Anak Usaha Multinasional (Era 2 Ekspansi Lintas Negara). */
data class SubsidiaryCardUi(
    val countryId: String,
    val countryName: String,
    val currencyCode: String,
    val title: String,
    val desc: String,
    val costText: String,
    val buildMonths: Int,
    val apCost: Int,
    val benefitText: String,
    val riskText: String,
    val statusText: String,
    val isActive: Boolean,
    val isBuilding: Boolean,
    val retainedForeignText: String,
    val retainedRsEquivalentText: String,
    val withholdingTaxText: String,
    val canOpen: Boolean,
    val openBlockReason: String?,
    val canRepatriate: Boolean,
    val tone: SemanticTone
)

/** Model UI untuk Kampanye Evaluasi 5 Komponen & Skor Etika (Tahap C). */
data class CampaignEvaluationUi(
    val nilaiPerusahaanScore: Int,
    val ketahananScore: Int,
    val kualitasKeputusanScore: Int,
    val etikaReputasiScore: Int,
    val dampakSosialScore: Int,
    val totalScore: Int,
    val grade: String,
    val archetypeTitle: String,
    val summaryText: String,
    val boardTrust: Int,
    val ethicsRiskLabel: String,
    val ethicsEvidencesCount: Int,
    val scandalsCount: Int
)

data class StatementNote(
    val number: Int,
    val title: String,
    val body: String
)

data class ReportCheck(
    val balanceOk: Boolean,
    val assetsText: String,
    val liabPlusEqText: String,
    val cashFlowOk: Boolean,
    val cfSumText: String,
    val dCashText: String,
    val equityOk: Boolean,
    val eqCalcText: String,
    val eqActualText: String
)

data class StockQuarter(
    val label: String,
    val revenue: String, val cogs: String, val opex: String, val dep: String,
    val ebit: String, val interest: String, val tax: String, val ni: String,
    val ar: String, val inv: String, val fa: String, val ap: String, val debt: String, val equity: String,
    val cfo: String, val capex: String, val dDebt: String, val dividend: String, val cashStart: String, val cashEnd: String,
    val surprise: String
)

data class StockCard(
    val id: String,
    val ticker: String,
    val name: String,
    val sector: String,
    val blurb: String,
    val riskNote: String,
    val listed: Boolean,
    val priceText: String,
    val changePctText: String,
    val tone: SemanticTone,
    val sparkline: List<Float>,
    val perText: String,
    val pbvText: String,
    val divYieldText: String,
    val roeText: String,
    val debtEbitdaText: String,
    val marketCapText: String,
    val lastQuarterLabel: String,
    val surpriseNote: String,
    val quarters: List<StockQuarter>,
    val heldLots: Int,
    val heldValueText: String,
    val avgCostText: String,
    val unrealizedText: String,
    val unrealizedTone: SemanticTone,
    val lotCostText: String,
    val canBuy1: Boolean,
    val canBuy10: Boolean,
    val canSell1: Boolean,
    val canSellAll: Boolean
)

data class HoldingRow(
    val id: String,
    val ticker: String,
    val name: String,
    val lots: Int,
    val avgPriceText: String,
    val lastPriceText: String,
    val valueText: String,
    val pnlText: String,
    val pnlTone: SemanticTone
)

data class BankCard(
    val id: String,
    val name: String,
    val banker: String,
    val bankerRole: String,
    val kindLabel: String,
    val foreign: Boolean,
    val indicativeRateText: String,
    val feeText: String,
    val maxTenor: Int,
    val maxAmountText: String,
    val reviewTurns: Int,
    val covenantText: String,
    val relationshipScore: Int,
    val style: String,
    val incentive: String,
    val tell: String,
    val estimatedChance: String,
    val weakestFactor: String,
    val canApply: Boolean,
    val blockReason: String?
)

data class CreditAppUi(
    val id: String,
    val bankName: String,
    val banker: String,
    val amountText: String,
    val tenorText: String,
    val purposeText: String,
    val structureText: String,
    val statusLabel: String,
    val statusTone: SemanticTone,
    val stageText: String,
    val officialReason: String,
    val informalNote: String,
    val offerSummary: String,
    val offerTerms: List<String>,
    val hiddenTerms: List<String>,
    val reviewed: Boolean,
    val canReview: Boolean,
    val canAccept: Boolean,
    val canDecline: Boolean,
    val expiresText: String
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
    val isDarkTheme: Boolean = false,
    val seed: Long = 203303L,
    val scenarioTitle: String = "",
    val scenarioStory: String = "",
    val difficultyName: String = "MANAJER",
    val difficultyLabel: String = "Manajer",
    val difficultyDesc: String = "",
    val isIronman: Boolean = false,
    val gameOverReason: String? = null,
    val isVictory: Boolean = false,

    val covenantWarningTitle: String = "Jarak ke covenant 6%",
    val covenantWarningSubtitle: String = "Uji kuartalan Bank B dalam 1 giliran",
    val covenantTone: SemanticTone = SemanticTone.WARNING_YELLOW,
    val covenantBreaches: Int = 0,
    val canEquityCure: Boolean = false,
    val equityCureAmountText: String = "4.000 jt RS",
    val covenantOrigin: DataOriginInfo,

    val featuredNewsSource: String = "Harian Bisnis",
    val featuredNewsConfidence: ConfidenceTier = ConfidenceTier.MEDIUM,
    val featuredNewsHeadline: String = "Kestrel dikabarkan batasi ekspor logam langka",
    val featuredNewsFooter: String = "+4 sudut pandang lain · ketuk untuk periksa silang",

    val markasIndicators: List<Indicator>,

    val cashHistoryPoints: List<Float>,
    val cashFanMedianPoints: List<Float>,
    val cashFanBand50Points: List<Float>,
    val cashFanBand90Points: List<Float>,

    val pendingDecisions: List<Decision>,
    val completedJournals: List<CompletedJournalEntry>,

    val morningPaperEdition: List<NewsItem>,

    val countries: List<Country>,
    val mapPieces: List<MapPiece>,
    val activeMapLayer: MapLayerFilter = MapLayerFilter.PELAYARAN,
    val pairwiseTensions: List<PairwiseTension>,

    val incomeStatementRows: List<FinancialRowItem>,
    val balanceSheetRows: List<FinancialRowItem>,
    val cashFlowRows: List<FinancialRowItem>,
    val equityStatementRows: List<FinancialRowItem> = emptyList(),
    val financialNotes: List<StatementNote> = emptyList(),
    val reportCheck: ReportCheck? = null,
    val periodLabel: String = "Maret 2033",
    val priorPeriodLabel: String = "Februari 2033",
    val accrualTrapActive: Boolean = true,
    val accrualTrapSummary: String = "",
    val executives: List<Executive>,
    val activeIncentiveScheme: String = "Bonus berbasis omzet",
    val activeFactoryScheme: String = "KPI penghematan biaya kuartalan",
    val ganttTasks: List<GanttTask>,
    val contracts: List<ContractItem>,

    val marketInstruments: List<MarketInstrument>,
    val bourseIndexText: String = "1.000",
    val bourseChangeText: String = "0,0%",
    val bourseSparkline: List<Float> = emptyList(),
    val portfolioValueText: String = "0 jt RS",
    val portfolioPnlText: String = "0 jt RS",
    val dividendsTotalText: String = "0 jt RS",
    val holdings: List<HoldingRow> = emptyList(),
    val stocks: List<StockCard> = emptyList(),

    val banks: List<BankCard> = emptyList(),
    val creditApps: List<CreditAppUi> = emptyList(),
    val creditMetricsSummary: String = "",
    val freeCollateralText: String = "",

    val brierScore: Double = 0.25,
    val detectedBiases: List<String>,
    val worldChronicles: List<ChronicleEntry>,

    val turnReportTitle: String = "",
    val turnReport: List<TurnReportItem> = emptyList(),
    val showTurnReportDialog: Boolean = false,
    val memos: List<MemoItem> = emptyList(),
    val saveSlots: List<SaveSlotInfo> = emptyList(),
    val showSaveDialog: Boolean = false,
    val statusBanner: String? = null,
    val priceAdjustmentPct: Int = 0,
    val playerShareText: String = "20%",
    val rivalLearningProfile: String = "",
    val rivals: List<RivalRow> = emptyList(),
    val scheduledChainCount: Int = 0,
    val investments: List<InvestCard> = emptyList(),
    val subsidiaries: List<SubsidiaryCardUi> = emptyList(),
    val transferPricingModeName: String = "WAJAR",
    val transferPricingLabel: String = "Harga Wajar (Arm's Length)",
    val effectiveTaxRateText: String = "22,0%",
    val fxHedgeActive: Boolean = false,
    val fxHedgeStatusText: String = "Belum dilindung nilai (terbuka terhadap lonjakan kurs A$)",
    val campaignEvaluation: CampaignEvaluationUi? = null,
    val capacitySummary: String = "",

    val activeEventDialog: EventDialogModel,
    val showEventDialog: Boolean = false,
    val inspectedDataOrigin: DataOriginInfo? = null,
    val selectedCountryForSheet: Country? = null
)
