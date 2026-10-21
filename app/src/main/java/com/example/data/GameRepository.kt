package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.engine.*
import com.example.engine.KapitalEngine.Companion.dateText
import com.example.engine.KapitalEngine.Companion.era
import com.example.engine.KapitalEngine.Companion.fmt
import com.example.engine.KapitalEngine.Companion.fmt1
import com.example.engine.KapitalEngine.Companion.fmt2
import com.example.engine.KapitalEngine.Companion.signed
import com.example.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Menghubungkan [KapitalEngine] ke [GameState] untuk UI, dan menyimpan permainan sebagai
 * seed + daftar tindakan ke SharedPreferences (deterministik, dapat dimainkan ulang).
 */
class GameRepository(context: Context? = null) {

    private val prefs: SharedPreferences? =
        context?.getSharedPreferences("kapital_saves", Context.MODE_PRIVATE)

    private var seed: Long = DEFAULT_SEED
    private val log = mutableListOf<Action>()
    private var eng = KapitalEngine(seed)

    private var isDark = false
    private var role = "CEO PT Benang Sundara"
    private var mapLayer = MapLayerFilter.PELAYARAN
    private var selectedCountryId: String? = null
    private var inspected: DataOriginInfo? = null
    private var activeEventId: String? = null
    private var showReport = false
    private var showSaves = false
    private var banner: String? = null

    private val _gameState = MutableStateFlow(toState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    init {
        if (prefs != null && prefs.contains("autosave")) {
            loadRaw(prefs.getString("autosave", null))
        }
        refresh()
    }

    private fun recordAndApply(a: Action, showReportAfter: Boolean = false): Boolean {
        val ok = eng.apply(a)
        if (ok) {
            log += a
            if (showReportAfter) showReport = true
            autosave()
        }
        refresh()
        return ok
    }

    private fun refresh() {
        _gameState.value = toState()
    }

    // =====================================================================
    // Perintah dari UI
    // =====================================================================
    fun toggleTheme() { isDark = !isDark; refresh() }
    fun setMapLayer(layer: MapLayerFilter) { mapLayer = layer; refresh() }
    fun selectCountryForBottomSheet(c: Country?) { selectedCountryId = c?.id; refresh() }
    fun inspectDataOrigin(o: DataOriginInfo?) { inspected = o; refresh() }
    fun openEventDialog(show: Boolean, eventId: String? = null) {
        activeEventId = if (show) (eventId ?: eng.openEvents.firstOrNull()?.id) else null
        refresh()
    }
    fun dismissTurnReport() { showReport = false; refresh() }
    fun openSaveDialog(show: Boolean) { showSaves = show; refresh() }
    fun clearBanner() { banner = null; refresh() }
    fun switchRole(roleName: String) { role = roleName; refresh() }

    fun setDifficulty(difficultyName: String) {
        val diff = Difficulty.entries.firstOrNull { it.name == difficultyName } ?: return
        if (recordAndApply(Action.SetDifficulty(diff))) {
            banner = "Tingkat kesulitan diubah ke ${diff.label}: ${diff.description}"
            refresh()
        }
    }

    fun changeIncentiveScheme(schemeName: String) {
        val target = if (schemeName.contains("kas", ignoreCase = true)) SalesScheme.KAS else SalesScheme.OMZET
        if (!recordAndApply(Action.SetSalesScheme(target))) {
            banner = if (eng.ap < 1) "AP tidak cukup (butuh 1 AP)." else "Skema ini sudah aktif."
            refresh()
        } else {
            banner = if (target == SalesScheme.KAS)
                "Skema penjualan diubah ke bonus kas tertagih. Obral kredit berhenti, omzet turun ±3%, DSO membaik ke 50 hari."
            else "Skema penjualan diubah ke bonus omzet. Omzet naik, tetapi piutang macet kembali menumpuk."
            refresh()
        }
    }

    fun changeFactoryScheme(schemeName: String) {
        val target = if (schemeName.contains("andal", ignoreCase = true)) FactoryScheme.ANDAL else FactoryScheme.HEMAT
        if (!recordAndApply(Action.SetFactoryScheme(target))) {
            banner = if (eng.ap < 1) "AP tidak cukup (butuh 1 AP)." else "Skema ini sudah aktif."
        } else {
            banner = if (target == FactoryScheme.ANDAL)
                "KPI pabrik diubah ke keandalan mesin. Perawatan kembali normal dan tunggakan perawatan dikikis."
            else "KPI pabrik diubah ke penghematan biaya. Kas hemat sekarang, tetapi risiko mesin rusak naik."
        }
        refresh()
    }

    fun crossCheckNewsItem(newsId: String) {
        if (!recordAndApply(Action.CrossCheck(newsId))) {
            banner = "Periksa silang membutuhkan 1 AP."
            refresh()
        }
    }

    fun submitDecisionJournal(
        decisionId: String,
        optionId: String,
        strategicReason: String,
        numericPrediction: String,
        confidencePct: Int,
        failureScenario: String
    ) {
        val num = Regex("""\d[\d.,]*""").find(numericPrediction)?.value
            ?.replace(".", "")?.replace(",", ".")?.toDoubleOrNull() ?: eng.cash
        val ok = recordAndApply(Action.Journal(decisionId, optionId, confidencePct, num, strategicReason, numericPrediction, failureScenario))
        banner = if (ok) "Keputusan dieksekusi dan dicatat di jurnal (dievaluasi 6 giliran lagi)."
        else "AP tidak cukup atau keputusan sudah diambil."
        refresh()
    }

    fun rejectProposal(decisionId: String) {
        if (recordAndApply(Action.RejectProposal(decisionId))) {
            banner = "Proposal pabrik pemintalan ditolak. Kas terjaga, tetapi rasio HPP tetap bergantung pada benang impor."
            refresh()
        }
    }

    fun resolveEventOption(option: EventOptionItem) {
        val evId = activeEventId ?: eng.openEvents.firstOrNull()?.id ?: return
        val ok = recordAndApply(Action.EventChoice(evId, option.id))
        if (ok) {
            activeEventId = null
            banner = eng.lastEventResult
        } else {
            banner = "AP tidak cukup untuk pilihan ini (butuh ${option.apCost} AP)."
        }
        refresh()
    }

    fun executeContractAction(contractId: String) {
        val ok = when (contractId) {
            "act_cure" -> recordAndApply(Action.EquityCure)
            "act_hedge" -> recordAndApply(Action.HedgeCotton)
            "act_hedge_fx" -> recordAndApply(Action.HedgeFx)
            "act_dual" -> recordAndApply(Action.DualSourcing)
            else -> recordAndApply(Action.ReviewContract(contractId))
        }
        banner = if (ok) when (contractId) {
            "act_cure" -> "Pemegang saham menyuntik ${fmt(eng.sc.equityCure)} jt RS. Kas bertambah dan 1 tingkat pelanggaran covenant dipulihkan."
            "act_hedge" -> "Harga kapas dikunci untuk 6 bulan ke depan (biaya premi 60 jt RS)."
            "act_hedge_fx" -> "Kontrak Forward Valas RS/A$ dikunci untuk 6 bulan ke depan (premi ${fmt(75.0 * eng.sc.size)} jt RS)."
            "act_dual" -> "Pemasok kedua diaktifkan: bebas penahanan kiriman Kestrel dan risiko sanksi, HPP +1,2%."
            else -> "Klausul tersembunyi kontrak dibuka oleh konsultan hukum (biaya 50 jt RS)."
        } else "Tindakan tidak tersedia atau AP tidak cukup."
        refresh()
    }

    fun auditExecutive(execId: String) {
        if (recordAndApply(Action.Audit(execId))) {
            banner = "Audit forensik dimulai (2 AP, 150 jt RS). Hasil keluar di akhir giliran ini."
        } else banner = "Butuh 2 AP, atau eksekutif sedang tidak bisa diaudit."
        refresh()
    }

    fun confrontExecutive(execId: String) {
        if (recordAndApply(Action.Confront(execId))) {
            banner = eng.exec(execId).lastOutcome
        } else banner = "Konfrontasi membutuhkan hasil audit positif dan 1 AP."
        refresh()
    }

    fun fireExecutive(execId: String) {
        if (recordAndApply(Action.Fire(execId))) {
            banner = eng.exec(execId).lastOutcome
        } else banner = "Butuh 1 AP untuk memberhentikan eksekutif."
        refresh()
    }

    fun adjustPrice(deltaPct: Int) {
        if (recordAndApply(Action.AdjustPrice(deltaPct))) {
            val p = eng.priceAdjPct
            banner = "Harga jual disetel ${if (p >= 0) "+$p%" else "$p%"} terhadap harga acuan; pangsa pasar kini ${fmt1(eng.playerShare() * 100)}%."
        } else banner = "Butuh 1 AP atau sudah mencapai batas (±15%)."
        refresh()
    }

    fun applyCredit(bankId: String, amount: Int, tenor: Int, purpose: String, floating: Boolean, bullet: Boolean) {
        if (recordAndApply(Action.ApplyCredit(bankId, amount, tenor, purpose, floating, bullet))) {
            val b = eng.credit.bank(bankId)!!
            banner = "Pengajuan kredit ${fmt(amount.toDouble())} jt RS dikirim ke ${b.name} (${b.banker}). Keputusan dalam ±${b.reviewTurns} giliran."
        } else banner = "Pengajuan gagal: pastikan AP ≥ 2, belum ada pengajuan aktif di bank ini, dan jumlah antara 500–60.000 jt RS."
        refresh()
    }

    fun reviewCreditOffer(appId: String) {
        if (recordAndApply(Action.ReviewOffer(appId))) {
            banner = "Konsultan hukum membuka semua klausul tersembunyi di draf perjanjian kredit (50 jt RS)."
        } else banner = "Butuh 1 AP."
        refresh()
    }

    fun acceptCreditOffer(appId: String) {
        if (recordAndApply(Action.AcceptOffer(appId))) {
            banner = "Penawaran diterima. Dana cair pada akhir giliran ini."
        } else banner = "Butuh 1 AP atau penawaran sudah tidak berlaku."
        refresh()
    }

    fun declineCreditOffer(appId: String) {
        if (recordAndApply(Action.DeclineOffer(appId))) {
            banner = "Penawaran ditolak."
        }
        refresh()
    }

    fun startInvestment(typeId: String) {
        val block = eng.investBlock(typeId)
        if (recordAndApply(Action.Invest(typeId))) {
            val s = eng.investSpec(typeId)!!
            banner = "${s.title} dimulai (${s.months} bulan, total ${fmt(eng.investCost(s))} jt RS)."
        } else banner = block ?: "Investasi tidak dapat dimulai."
        refresh()
    }

    fun openSubsidiary(countryId: String) {
        val block = eng.subsidiaryBlock(countryId)
        if (recordAndApply(Action.OpenSubsidiary(countryId))) {
            val spec = eng.subsidiarySpec(countryId)!!
            banner = "Pembangunan anak usaha dimulai: ${spec.title} di ${spec.countryName} (${spec.buildMonths} bulan, ${fmt(eng.subsidiaryCostRs(spec))} jt RS)."
        } else banner = block ?: "Anak usaha tidak dapat dibuka."
        refresh()
    }

    fun repatriateSubsidiary(countryId: String) {
        if (recordAndApply(Action.Repatriate(countryId))) {
            val sub = eng.subsidiaries[countryId]
            banner = "Dividen valas dari ${sub?.spec?.countryName ?: countryId} berhasil direpatriasi ke kas induk (masuk laporan bulan ini)."
        } else banner = "Butuh 1 AP dan saldo laba ditahan anak usaha harus di atas 0."
        refresh()
    }

    fun setTransferPricingMode(modeName: String) {
        val mode = TransferPricingMode.entries.firstOrNull { it.name == modeName } ?: return
        if (recordAndApply(Action.SetTransferPricing(mode))) {
            banner = "Kebijakan Transfer Pricing diubah ke ${mode.label}. Tarif PPh efektif grup kini ${fmt1(eng.effectiveTaxRate() * 100)}%."
        } else banner = "Butuh 1 AP atau mode tersebut sudah aktif."
        refresh()
    }

    fun acquireRival(rivalId: String) {
        val block = eng.acquireRivalBlock(rivalId)
        if (recordAndApply(Action.AcquireRival(rivalId))) {
            banner = "Akuisisi pesaing berhasil! Goodwill & aset tetap wajar dicatat di neraca, kapasitas +1 lini."
        } else banner = block ?: "Akuisisi gagal."
        refresh()
    }

    fun borrowForeignSub(countryId: String, amountLocal: Int) {
        if (recordAndApply(Action.BorrowForeignSub(countryId, amountLocal))) {
            val sub = eng.subsidiaries[countryId]
            banner = "Fasilitas kredit bank lokal ${sub?.spec?.countryName ?: countryId} sebesar $amountLocal jt ${sub?.spec?.currencyCode ?: ""} berhasil ditarik."
        } else banner = "Gagal menarik kredit lokal: butuh 1 AP, anak usaha harus aktif, dan belum melebihi batas utang lokal."
        refresh()
    }

    fun issueCorporateBond(amountRs: Int, tenorMonths: Int) {
        if (recordAndApply(Action.IssueCorporateBond(amountRs, tenorMonths))) {
            banner = "Penerbitan Obligasi Korporasi ${fmt(amountRs.toDouble())} jt RS (tenor $tenorMonths bln) dijadwalkan cair akhir bulan ini."
        } else banner = "Gagal menerbitkan obligasi: butuh 2 AP dan nominal 1.000–50.000 jt RS."
        refresh()
    }

    fun executeIpo(floatPct: Int, dualClassShares: Boolean) {
        if (recordAndApply(Action.ExecuteIpo(floatPct, dualClassShares))) {
            banner = "IPO berhasil! Perusahaan kini tercatat di Bursa Efek Sundara dengan $floatPct% saham publik."
        } else banner = "IPO gagal: butuh 3 AP, belum pernah IPO, dan tidak sedang menjalankan obral kredit (channel stuffing)."
        refresh()
    }

    fun openCaptiveBank() {
        if (recordAndApply(Action.OpenCaptiveBank)) {
            banner = "Unit Bank & Asuransi Grup resmi didirikan dengan rasio kecukupan modal (CAR) ≥ 12%."
        } else banner = "Gagal mendirikan Bank Grup: butuh 3 AP, belum berdiri, dan kas induk mencukupi."
        refresh()
    }

    fun mediaLobby(kind: String) {
        if (recordAndApply(Action.MediaLobby(kind))) {
            banner = if (kind == "media") "Akuisisi grup media nasional selesai." else "Program lobi parlemen & asosiasi industri dijalankan."
        } else banner = "Gagal: butuh 2 AP dan kas mencukupi."
        refresh()
    }

    fun carbonAction(kind: String, units: Int) {
        if (recordAndApply(Action.CarbonAction(kind, units))) {
            banner = if (kind == "buy") "Membeli $units kredit karbon di bursa emisi." else "Menjual $units kredit karbon surplus."
        } else banner = "Gagal transaksi karbon: butuh 1 AP dan saldo kas / kredit karbon mencukupi."
        refresh()
    }

    fun appointSuccessor(candidateId: String) {
        if (recordAndApply(Action.AppointSuccessor(candidateId))) {
            banner = "Kandidat suksesi CEO ($candidateId) ditetapkan dan mulai menjalani kaderisasi."
        } else banner = "Gagal menetapkan suksesor: butuh 2 AP."
        refresh()
    }

    fun buyStock(companyId: String, lots: Int) {
        val c = eng.bourse.company(companyId) ?: return
        if (recordAndApply(Action.BuyStock(companyId, lots))) {
            banner = "Membeli $lots lot ${c.spec.ticker} (${fmt(lots * KapitalEngine.LOT * c.price)} jt RS + biaya pialang)."
        } else banner = "Gagal membeli: butuh 1 AP, kas tersisa minimal 1.500 jt RS, dan nilai beli ≤ 50% aset."
        refresh()
    }

    fun sellStock(companyId: String, lots: Int) {
        val c = eng.bourse.company(companyId) ?: return
        if (recordAndApply(Action.SellStock(companyId, lots))) {
            banner = "Menjual $lots lot ${c.spec.ticker} (${fmt(lots * KapitalEngine.LOT * c.price)} jt RS dipotong biaya & pajak)."
        } else banner = "Gagal menjual: butuh 1 AP dan kepemilikan cukup."
        refresh()
    }

    fun advanceTurn() {
        recordAndApply(Action.EndTurn, showReportAfter = true)
    }

    // =====================================================================
    // Simpan / Muat / Cabang "Bagaimana Jika"
    // =====================================================================
    private fun encodeGame(): String =
        buildString {
            appendLine("SEED|$seed")
            log.forEach { appendLine(it.encode()) }
        }

    private fun loadRaw(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val lines = raw.lines().filter { it.isNotBlank() }
        if (lines.isEmpty() || !lines.first().startsWith("SEED|")) return false
        val newSeed = lines.first().removePrefix("SEED|").toLongOrNull() ?: return false
        val actions = lines.drop(1).mapNotNull { Action.decode(it) }
        val fresh = KapitalEngine(newSeed)
        actions.forEach { fresh.apply(it) }
        seed = newSeed
        log.clear(); log += actions
        eng = fresh
        return true
    }

    private fun autosave() {
        prefs?.edit()?.putString("autosave", encodeGame())?.apply()
    }

    fun saveToSlot(slot: Int) {
        val summary = "${dateText(eng.turn)} · Giliran ${eng.turn} · Kas ${fmt(eng.cash)} jt RS · ${eng.sc.title}"
        prefs?.edit()
            ?.putString("slot_$slot", encodeGame())
            ?.putString("slot_${slot}_summary", summary)
            ?.apply()
        banner = "Permainan disimpan di Slot $slot ($summary)."
        refresh()
    }

    fun loadFromSlot(slot: Int) {
        if (eng.difficulty.ironman) {
            banner = "Mode Krisis Abadi (Ironman) aktif: dilarang memuat ulang simpanan."
            refresh()
            return
        }
        val raw = prefs?.getString("slot_$slot", null)
        if (loadRaw(raw)) {
            autosave()
            showSaves = false
            showReport = false
            banner = "Slot $slot dimuat (${eng.sc.title})."
        } else banner = "Slot $slot masih kosong."
        refresh()
    }

    fun newGame(newSeed: Long = Random(System.nanoTime() xor (seed * 6364136223846793005L + 1L)).nextLong(100_000L, 999_999L)) {
        val prevDiff = eng.difficulty
        seed = newSeed
        log.clear()
        eng = KapitalEngine(seed)
        if (prevDiff != Difficulty.MANAJER) {
            eng.apply(Action.SetDifficulty(prevDiff))
            log += Action.SetDifficulty(prevDiff)
        }
        showSaves = false
        showReport = true
        banner = "Skenario baru (seed $seed): ${eng.sc.title} — ${eng.sc.story}"
        autosave()
        refresh()
    }

    fun branchFromAction(actionIndex: Int) {
        if (eng.difficulty.ironman) {
            banner = "Mode Krisis Abadi (Ironman) aktif: fitur cabang 'Bagaimana Jika' dikunci."
            refresh()
            return
        }
        if (actionIndex < 0 || actionIndex > log.size) return
        val slice = log.take(actionIndex)
        val fresh = KapitalEngine(seed)
        slice.forEach { fresh.apply(it) }
        log.clear(); log += slice
        eng = fresh
        autosave()
        banner = "Cabang 'Bagaimana Jika' dibuka kembali pada ${dateText(eng.turn)} (Giliran ${eng.turn}). Dunia ke depan menggunakan seed yang sama."
        refresh()
    }

    private fun slotList(): List<SaveSlotInfo> = (1..3).map { s ->
        val sum = prefs?.getString("slot_${s}_summary", null)
        SaveSlotInfo(s, sum != null, sum ?: "Kosong")
    }

    // =====================================================================
    // Pemetaan keadaan mesin -> GameState UI
    // =====================================================================
    private fun toneOf(t: Tone): SemanticTone = when (t) {
        Tone.BAD -> SemanticTone.DANGER_RED
        Tone.WARN -> SemanticTone.WARNING_YELLOW
        Tone.GOOD -> SemanticTone.GOOD_GREEN
        Tone.INFO -> SemanticTone.PRIMARY_BLUE
        Tone.NEUTRAL -> SemanticTone.NEUTRAL_GRAY
    }

    private fun toState(): GameState {
        val t = eng.turn
        val date = dateText(t)
        val prevDate = dateText(t - 1)
        val covDist = eng.covenantDistancePct()
        val covMod = (3 - (t % 3)) % 3
        val covWhen = if (covMod == 0) "Uji kuartalan di akhir giliran ini" else "Uji kuartalan dalam ${covMod + 1} giliran"
        val covTone = when {
            eng.covenantBreaches >= 2 || covDist < 0 -> SemanticTone.DANGER_RED
            covDist < 12 -> SemanticTone.WARNING_YELLOW
            else -> SemanticTone.GOOD_GREEN
        }
        val covTitle = when {
            eng.covenantBreaches > 0 -> "Covenant dilanggar (${eng.covenantBreaches}x) · ICR proyeksi ${fmt2(eng.projectedIcr())}x"
            covDist < 0 -> "Proyeksi ICR di bawah batas (${fmt2(eng.projectedIcr())}x < ${fmt2(eng.covenantThreshold())}x)"
            else -> "Jarak ke covenant ${covDist.roundToInt()}% (ICR ${fmt2(eng.projectedIcr())}x, batas ${fmt2(eng.covenantThreshold())}x)"
        }
        val eval = eng.evaluateCampaign()
        val evalUi = CampaignEvaluationUi(
            nilaiPerusahaanScore = eval.nilaiPerusahaanScore,
            ketahananScore = eval.ketahananScore,
            kualitasKeputusanScore = eval.kualitasKeputusanScore,
            etikaReputasiScore = eval.etikaReputasiScore,
            dampakSosialScore = eval.dampakSosialScore,
            totalScore = eval.totalScore,
            grade = eval.grade,
            archetypeTitle = eval.archetypeTitle,
            summaryText = eval.summaryText,
            boardTrust = eng.boardTrust,
            ethicsRiskLabel = when {
                eng.ethicsLoad >= 4.0 -> "Tinggi (Rentan skandal / penyelidikan)"
                eng.ethicsLoad >= 1.5 -> "Sedang (Ada jejak jalan pintas)"
                else -> "Bersih"
            },
            ethicsEvidencesCount = eng.ethicsEvidences.size,
            scandalsCount = eng.scandalsTriggered
        )

        val eraNum = era(t)
        val eraTitle = when (eraNum) {
            1 -> "Era 1: Bertahan Hidup"
            2 -> "Era 2: Ekspansi Multinasional"
            3 -> "Era 3: Konglomerasi & Alokasi Modal"
            4 -> "Era 4: Krisis Sistemik & Transisi"
            else -> "Era 5: Suksesi & Warisan"
        }

        return GameState(
            dateText = date,
            turn = t,
            totalTurns = 480,
            eraNumber = eraNum,
            eraName = eraTitle,
            apCurrent = eng.ap,
            apMax = eng.apMax,
            runwayMonths = eng.runwayMonths(),
            activeRole = role,
            isDarkTheme = isDark,
            seed = seed,
            scenarioTitle = eng.sc.title,
            scenarioStory = eng.sc.story,
            difficultyName = eng.difficulty.name,
            difficultyLabel = eng.difficulty.label,
            difficultyDesc = eng.difficulty.description,
            isIronman = eng.difficulty.ironman,
            gameOverReason = eng.gameOverReason,
            isVictory = eng.isVictory,

            covenantWarningTitle = covTitle,
            covenantWarningSubtitle = "$covWhen · Kepercayaan Dewan ${eng.boardTrust}/100 · Suku bunga acuan ${fmt2(eng.policyRate)}%",
            covenantTone = covTone,
            covenantBreaches = eng.covenantBreaches,
            canEquityCure = !eng.cureUsed && eng.covenantBreaches >= 1 && eng.gameOverReason == null && eng.ap >= 2,
            equityCureAmountText = "${fmt(eng.sc.equityCure)} jt RS",
            covenantOrigin = DataOriginInfo(
                title = "Jarak Covenant Kredit",
                displayedValue = "${covDist.roundToInt()}% (ICR ${fmt2(eng.projectedIcr())}x)",
                sourceName = "Meja Tresuri Internal & Perjanjian Kredit Bank B",
                dataDate = "$date (proyeksi kuartal berjalan)",
                calculationMethod = "ICR = Σ EBIT 3 bulan / Σ Beban Bunga 3 bulan. Batas minimum ${fmt2(eng.covenantThreshold())}x; utang bersih/EBITDA ≤ 3,5x.",
                uncertaintyAndBiasNote = if (eng.stuffingActive)
                    "EBIT saat ini terdongkrak omzet kredit 90 hari yang belum tertagih kas. Jika bunga naik atau piutang dihapusbukukan, ICR turun tajam."
                else "Perhitungan memakai proyeksi bulan ini ditambah realisasi dua bulan terakhir.",
                confidence = ConfidenceTier.MEDIUM
            ),

            featuredNewsSource = "Harian Bisnis",
            featuredNewsConfidence = ConfidenceTier.MEDIUM,
            featuredNewsHeadline = eng.news.firstOrNull()?.headline ?: "",
            featuredNewsFooter = "+4 media lain dengan bias berbeda · ketuk untuk periksa silang (1 AP)",

            markasIndicators = buildIndicators(date),

            cashHistoryPoints = eng.cashHist.takeLast(6).map { it.toFloat() },
            cashFanMedianPoints = buildCashFanMedian(),
            cashFanBand50Points = List(6) { i -> (450f + i * 320f) * eng.difficulty.noiseMult.toFloat().coerceAtLeast(0.5f) },
            cashFanBand90Points = List(6) { i -> (950f + i * 700f) * eng.difficulty.noiseMult.toFloat().coerceAtLeast(0.5f) },

            pendingDecisions = buildDecisions(),
            completedJournals = eng.journals.map { j ->
                val brier = j.outcome?.let { o ->
                    val p = j.confidencePct / 100.0; val x = if (o) 1.0 else 0.0; (p - x) * (p - x)
                }
                CompletedJournalEntry(
                    id = j.id,
                    decisionTitle = j.decisionTitle,
                    turnSubmitted = j.turnSubmitted,
                    evaluateAtTurn = j.evaluateAtTurn,
                    strategicReason = j.reason,
                    numericPrediction = j.prediction,
                    confidencePct = j.confidencePct,
                    failureScenario = j.failure,
                    evaluated = j.outcome != null,
                    brierScoreContribution = brier,
                    outcomeText = j.actualCash?.let { "Kas aktual giliran ${j.evaluateAtTurn}: ${fmt(it)} jt RS (${if (j.outcome == true) "prediksi tepat" else "meleset"})" }
                )
            },

            morningPaperEdition = buildNews(date),
            countries = buildCountries(date),
            mapPieces = buildMapPieces(),
            activeMapLayer = mapLayer,
            pairwiseTensions = buildTensions(),

            incomeStatementRows = buildIncomeRows(date),
            balanceSheetRows = buildBalanceRows(date),
            cashFlowRows = buildCashFlowRows(date),
            equityStatementRows = buildEquityRows(date),
            financialNotes = buildFinancialNotes(),
            reportCheck = buildReportCheck(),
            periodLabel = prevDate,
            priorPeriodLabel = dateText(t - 2),
            accrualTrapActive = (eng.lastIncome["ni"] ?: 0.0) > 0 && (eng.lastCashFlow["cfo"] ?: 0.0) < 0,
            accrualTrapSummary = buildAccrualSummary(),
            executives = buildExecutives(date),
            activeIncentiveScheme = eng.salesScheme.label,
            activeFactoryScheme = eng.factoryScheme.label,
            ganttTasks = buildGantt(),
            contracts = buildContracts(date),

            marketInstruments = buildMarkets(date),
            bourseIndexText = fmt1(eng.bourse.index),
            bourseChangeText = run {
                val h = eng.bourse.indexHist.toList()
                val chg = if (h.size >= 2) (h.last() / h[h.size - 2] - 1.0) * 100.0 else 0.0
                "${if (chg >= 0) "+" else ""}${fmt1(chg)}%"
            },
            bourseSparkline = eng.bourse.indexHist.map { it.toFloat() },
            portfolioValueText = "${fmt(eng.portfolioValue())} jt RS",
            portfolioPnlText = run {
                val basis = eng.costBasis.values.sum()
                val u = eng.portfolioValue() - basis
                "Belum realisasi ${signed(u)} · Realisasi ${signed(eng.realizedGainTotal)}"
            },
            dividendsTotalText = "${fmt(eng.dividendsReceivedTotal)} jt RS",
            holdings = buildHoldings(),
            stocks = buildStocks(),
            banks = buildBanks(),
            creditApps = buildCreditApps(),
            creditMetricsSummary = run {
                val m = eng.creditMetrics(0.0)
                val icr = m.ebit12 / max(1.0, m.interest12)
                val lev = (m.debt - m.cash) / max(1.0, m.ebitda12)
                val cr = m.currentAssets / max(1.0, m.currentLiabilities)
                "Rasio Anda di mata bank: ICR ${fmt2(icr)}x · Utang bersih/EBITDA ${fmt2(lev)}x · Current ratio ${fmt2(cr)}x · Agunan bebas ${fmt(m.freeCollateral)} jt RS · Pelanggaran covenant ${m.covenantBreaches}x"
            },
            freeCollateralText = "${fmt(eng.creditMetrics(0.0).freeCollateral)} jt RS",

            brierScore = eng.brierScore(),
            detectedBiases = buildBiases(),
            worldChronicles = eng.chronicle.asReversed().map { c ->
                ChronicleEntry(
                    turn = c.turn,
                    dateLabel = dateText(c.turn),
                    eraLabel = "Era ${era(c.turn)}",
                    title = c.title,
                    description = c.description,
                    causalChain = c.causalChain,
                    canReplayCounterfactual = c.actionIndex != null && !eng.difficulty.ironman,
                    actionIndex = c.actionIndex
                )
            },

            turnReportTitle = eng.lastReportTitle,
            turnReport = eng.lastReport.map { TurnReportItem(it.text, toneOf(it.tone)) },
            showTurnReportDialog = showReport,
            memos = eng.memos.map { MemoItem(it.execId, it.from, it.role, it.text) },
            saveSlots = slotList(),
            showSaveDialog = showSaves,
            statusBanner = banner,
            priceAdjustmentPct = eng.priceAdjPct,
            playerShareText = "${fmt1(eng.playerShare() * 100.0)}%",
            rivalLearningProfile = eng.world.learnedPlayerProfile,
            rivals = eng.world.rivals.map { rv ->
                val rel = 1.0 + eng.priceAdjPct / 100.0
                val sh = eng.world.rivalShareOf(rv.id, rel * eng.fxPassLevel()) * 100.0
                val acqBlock = eng.acquireRivalBlock(rv.id)
                val acqCost = eng.acquireRivalCostRs(rv.id)
                RivalRow(
                    id = rv.id,
                    name = rv.name,
                    archetype = rv.archetype,
                    priceRelText = "${fmt1((rv.price - 1.0) * 100.0)}%",
                    shareText = if (rv.active) "${fmt1(sh)}%" else "Keluar",
                    statusText = when {
                        !rv.active -> "Bangkrut / keluar pasar"
                        rv.weakMonths >= 5 -> "Tertekan (${rv.weakMonths} bln rugi)"
                        else -> "Aktif (${rv.activeStrategy.label})"
                    },
                    tone = when {
                        !rv.active -> SemanticTone.NEUTRAL_GRAY
                        rv.activeStrategy != World.RivalStrategy.NORMAL -> SemanticTone.DANGER_RED
                        rv.weakMonths >= 5 -> SemanticTone.WARNING_YELLOW
                        rv.price < rel * eng.fxPassLevel() - 0.03 -> SemanticTone.DANGER_RED
                        else -> SemanticTone.PRIMARY_BLUE
                    },
                    activeStrategyText = rv.activeStrategy.label,
                    warningSignalText = rv.warningSignal,
                    canAcquire = acqBlock == null,
                    acquireCostText = "${fmt(acqCost)} jt RS (3 AP)",
                    acquireBlockReason = acqBlock
                )
            },
            scheduledChainCount = eng.scheduledChainEvents.size,
            investments = KapitalEngine.INVEST.map { spec ->
                val running = eng.projectsInv.firstOrNull { it.type == spec.id && !it.done }
                val count = eng.investCount(spec.id)
                val block = eng.investBlock(spec.id)
                val cost = eng.investCost(spec)
                InvestCard(
                    id = spec.id,
                    title = spec.title,
                    desc = spec.desc,
                    costText = "${fmt(cost)} jt RS (${fmt(cost / spec.months)}/bln)",
                    durationText = "${spec.months} bulan",
                    apCost = spec.ap,
                    accountingNote = if (spec.capex) "CapEx → menambah Aset Tetap & penyusutan" else "OpEx → langsung mengurangi laba selama proyek berjalan",
                    benefitText = spec.benefit,
                    statusText = when {
                        running != null -> "Berjalan: ${fmt(running.spent)} / ${fmt(running.totalCost)} jt RS"
                        count > 0 -> "Selesai ($count/${spec.maxCount})"
                        else -> "Belum dimulai (0/${spec.maxCount})"
                    },
                    canStart = block == null,
                    blockReason = block
                )
            },
            subsidiaries = buildSubsidiaries(),
            transferPricingModeName = eng.transferPricingMode.name,
            transferPricingLabel = eng.transferPricingMode.label,
            effectiveTaxRateText = "${fmt1(eng.effectiveTaxRate() * 100.0)}%",
            fxHedgeActive = eng.fxHedgeActive,
            fxHedgeStatusText = if (eng.fxHedgeActive)
                "Aktif s.d. ${dateText(eng.fxHedgeUntilTurn)} (Kurs terkunci ${fmt(eng.hedgedKurs)} RS/A$)"
            else "Tidak aktif (Eksposur utang A$ & impor mengikuti kurs spot ${fmt(eng.kurs)} RS/A$)",
            campaignEvaluation = evalUi,
            capacitySummary = "Permintaan ${fmt1(eng.demandIdx() * 100)}% vs kapasitas ${fmt1(eng.capacityIdx() * 100)}% · Utilisasi ${fmt1((eng.utilization() * 100).coerceAtMost(100.0))}% · Lini tambahan ${eng.extraLines} · Anak usaha aktif ${eng.subsidiaries.values.count { it.active && !it.nationalized }} · Karyawan ${eng.headcountProd() + eng.headcountStaff()} orang · Tarif PPh efektif ${fmt1(eng.effectiveTaxRate() * 100)}%",

            activeEventDialog = buildEventDialog(),
            showEventDialog = activeEventId != null && eng.openEvents.any { it.id == activeEventId },
            inspectedDataOrigin = inspected,
            selectedCountryForSheet = selectedCountryId?.let { id -> buildCountries(date).firstOrNull { it.id == id } }
        )
    }

    private fun buildSubsidiaries(): List<SubsidiaryCardUi> =
        KapitalEngine.SUBSIDIARIES.map { spec ->
            val st = eng.subsidiaries[spec.countryId]
            val running = eng.projectsInv.firstOrNull { it.type == "sub_${spec.countryId}" && !it.done }
            val meta = eng.foreignMeta.getValue(spec.countryId)
            val cost = eng.subsidiaryCostRs(spec)
            val block = eng.subsidiaryBlock(spec.countryId)
            val retainedFor = st?.retainedForeign ?: 0.0
            val retainedRs = retainedFor * meta.fxToRs
            val hasMeridia = eng.subsidiaries["meridia"]?.let { it.active && !it.nationalized } == true
            val effWht = if (hasMeridia && spec.countryId != "meridia") meta.profile.withholdingTaxPct * 0.5 else meta.profile.withholdingTaxPct
            SubsidiaryCardUi(
                countryId = spec.countryId,
                countryName = spec.countryName,
                currencyCode = spec.currencyCode,
                title = spec.title,
                desc = spec.desc,
                costText = "${fmt(cost)} jt RS (${spec.buildMonths} bln · ${spec.apCost} AP)",
                buildMonths = spec.buildMonths,
                apCost = spec.apCost,
                benefitText = spec.benefitText,
                riskText = spec.riskText,
                statusText = when {
                    st?.nationalized == true -> "Dinasionalisasi / Dibekukan"
                    st?.active == true -> "Beroperasi · Kurs 1 ${spec.currencyCode} = ${fmt1(meta.fxToRs)} RS · Tarif Impor ${fmt1(meta.tariffPct)}%"
                    running != null -> "Konstruksi berjalan: ${fmt(running.spent)} / ${fmt(running.totalCost)} jt RS"
                    else -> "Belum didirikan (PPh lokal ${fmt1(meta.profile.corpTaxPct)}%, Pajak dividen ${fmt1(meta.profile.withholdingTaxPct)}%)"
                },
                isActive = st?.active == true && !st.nationalized,
                isBuilding = running != null,
                retainedForeignText = "${fmt1(retainedFor)} jt ${spec.currencyCode}",
                retainedRsEquivalentText = "≈ ${fmt(retainedRs)} jt RS",
                withholdingTaxText = "Pajak dividen repatriasi ${fmt1(effWht)}%${if (hasMeridia && spec.countryId != "meridia") " (diskon 50% via Holding Meridia)" else ""}",
                canOpen = block == null,
                openBlockReason = block,
                canRepatriate = st?.active == true && !st.nationalized && retainedRs >= 10.0 && eng.ap >= 1,
                tone = when {
                    st?.nationalized == true -> SemanticTone.DANGER_RED
                    st?.active == true -> SemanticTone.GOOD_GREEN
                    running != null -> SemanticTone.PRIMARY_BLUE
                    else -> SemanticTone.NEUTRAL_GRAY
                }
            )
        }

    private fun buildCashFanMedian(): List<Float> {
        val est = eng.estimateMonth()
        val monthlyDrift = (est.getValue("ni") + est.getValue("dep") - 550.0).toFloat()
        return List(6) { i -> max(0f, eng.cash.toFloat() + monthlyDrift * (i + 1)) }
    }

    private fun buildIndicators(date: String): List<Indicator> {
        val lastNet = eng.lastCashFlow["net"] ?: 0.0
        val utilPct = (eng.utilization() * 100.0 * eng.lastProdFactor).roundToInt().coerceIn(20, 100)
        val ccc = (eng.dso() + eng.dio() - eng.dpo()).roundToInt()
        val infCenter = eng.inflationAnnual
        val noise = eng.difficulty.noiseMult
        val infLow = max(0.0, infCenter - 1.2 * noise).roundToInt()
        val infHigh = (infCenter + 1.4 * noise).roundToInt()
        val twoMonthsAgo = dateText(eng.turn - 2)
        return listOf(
            Indicator(
                id = "ind_kas",
                label = "Kas",
                valueText = "${fmt(eng.cash)} jt RS",
                deltaOrSubText = "${signed(lastNet)}/bulan",
                tone = if (lastNet < 0 || eng.cash < 3000) SemanticTone.DANGER_RED else SemanticTone.GOOD_GREEN,
                dataDate = "$date (T+0)",
                source = "Tresuri Internal",
                confidence = ConfidenceTier.HIGH,
                originInfo = DataOriginInfo(
                    title = "Posisi Kas Likuid",
                    displayedValue = "${fmt(eng.cash)} jt RS (${signed(lastNet)}/bulan)",
                    sourceName = "Rekening Bank Operasional Sundara",
                    dataDate = "$date (T+0, waktu nyata)",
                    calculationMethod = "Saldo kas akhir = kas awal + arus kas operasi + arus kas investasi + arus kas pendanaan.",
                    uncertaintyAndBiasNote = "Kas di rekening bank adalah satu-satunya angka tanpa bias di seluruh perusahaan.",
                    confidence = ConfidenceTier.HIGH
                )
            ),
            Indicator(
                id = "ind_utilisasi",
                label = "Utilisasi pabrik",
                valueText = "$utilPct%",
                deltaOrSubText = when {
                    eng.strikeMonthsLeft > 0 -> "Mogok (${eng.strikeMonthsLeft} bln)"
                    eng.breakdownMonthsLeft > 0 -> "Lini 3 rusak (${eng.breakdownMonthsLeft} bln)"
                    eng.deferralActive -> "Perawatan ditunda (${eng.deferredMonths} bln)"
                    else -> "Perawatan normal"
                },
                tone = if (utilPct < 75 || eng.deferralActive) SemanticTone.WARNING_YELLOW else SemanticTone.GOOD_GREEN,
                dataDate = "$date (T-3 hari)",
                source = "Laporan Kepala Pabrik",
                confidence = if (eng.deferralActive) ConfidenceTier.MEDIUM else ConfidenceTier.HIGH,
                originInfo = DataOriginInfo(
                    title = "Utilisasi Pabrik Kalimas",
                    displayedValue = "$utilPct%",
                    sourceName = "Laporan Operasional Kepala Pabrik",
                    dataDate = "$date",
                    calculationMethod = "Jam tenun efektif dibagi kapasitas terpasang (4 lini di Kalimas + lini ekspansi).",
                    uncertaintyAndBiasNote = if (eng.deferralActive)
                        "Tunggakan perawatan ${eng.deferredMonths} bulan. Probabilitas mesin rusak bulan depan ${(1.5 * (1 + 0.5 * eng.deferredMonths)).roundToInt()}%."
                    else "Jadwal perawatan dipatuhi.",
                    confidence = if (eng.deferralActive) ConfidenceTier.MEDIUM else ConfidenceTier.HIGH
                )
            ),
            Indicator(
                id = "ind_siklus_kas",
                label = "Siklus kas",
                valueText = "$ccc hari",
                deltaOrSubText = "DSO ${eng.dso().roundToInt()} · DIO ${eng.dio().roundToInt()} · DPO ${eng.dpo().roundToInt()}",
                tone = if (ccc > 90) SemanticTone.WARNING_YELLOW else SemanticTone.GOOD_GREEN,
                dataDate = date,
                source = "Akuntansi Manajemen",
                confidence = ConfidenceTier.MEDIUM,
                originInfo = DataOriginInfo(
                    title = "Siklus Konversi Kas (CCC)",
                    displayedValue = "$ccc hari",
                    sourceName = "Divisi Akuntansi Manajemen",
                    dataDate = date,
                    calculationMethod = "CCC = DSO (${eng.dso().roundToInt()} hr) + DIO (${eng.dio().roundToInt()} hr) − DPO (${eng.dpo().roundToInt()} hr).",
                    uncertaintyAndBiasNote = "Piutang berisiko dari obral termin 90 hari saat ini ${fmt(eng.stuffedTotal())} jt RS (sekitar 45% macet saat jatuh tempo 6 bulan).",
                    confidence = ConfidenceTier.MEDIUM
                )
            ),
            Indicator(
                id = "ind_inflasi",
                label = "Inflasi Sundara",
                valueText = "$infLow–$infHigh%",
                deltaOrSubText = "data $twoMonthsAgo · bunga ${fmt2(eng.policyRate)}%",
                tone = SemanticTone.NEUTRAL_GRAY,
                dataDate = "$twoMonthsAgo (terlambat 2 bln)",
                source = "Biro Pusat Statistik Sundara",
                confidence = ConfidenceTier.LOW,
                originInfo = DataOriginInfo(
                    title = "Inflasi Tahunan (IHK) Sundara",
                    displayedValue = "$infLow–$infHigh% YoY",
                    sourceName = "Biro Pusat Statistik (BPS) Sundara",
                    dataDate = "$twoMonthsAgo (lag publikasi 2 bulan)",
                    calculationMethod = "Survei harga konsumen di 48 kota; rentang mencerminkan revisi dan ketidakpastian.",
                    uncertaintyAndBiasNote = "Bank sentral memakai aturan Taylor: suku bunga cenderung naik bila inflasi di atas 3% atau kurs melemah cepat.",
                    confidence = ConfidenceTier.LOW
                )
            )
        )
    }

    private fun buildDecisions(): List<Decision> =
        eng.pendingDecisions().map { d ->
            val opts = when (d.id) {
                "dec_pemintalan" -> listOf(
                    DecisionOption("approve", "Setujui proyek pemintalan (15 bulan)", "CapEx bertahap ±${fmt(14000.0 * eng.sc.size)} jt RS; rasio HPP turun 5 poin setelah selesai", "Setujui", SemanticTone.PRIMARY_BLUE)
                )
                "dec_termsheet" -> listOf(
                    DecisionOption("cnt_ts_1", "Bank Pembangunan Sundara (${fmt(eng.termSheetAmount())} jt RS, tetap ${fmt2(eng.termSheetFixedRate())}%, 4 thn)",
                        "Tanpa risiko kurs; cicilan pokok ±${fmt(eng.termSheetAmount() / 48.0)} jt RS/bulan; melarang dividen", "Pilih RS", SemanticTone.PRIMARY_BLUE),
                    DecisionOption("cnt_ts_2", "Aurelia Global CitiTrust (setara ${fmt(eng.termSheetAudAmountRs())} jt RS dalam A$, tetap 5,2%, 3 thn)",
                        "Bunga murah dan tanpa cicilan pokok bulanan; risiko kurs A$ dan klausul MAC 30%", "Pilih A$", SemanticTone.WARNING_YELLOW)
                )
                else -> emptyList()
            }
            Decision(
                id = d.id,
                title = d.title,
                subtitle = d.subtitle,
                apCost = d.apCost,
                badgeText = d.badge,
                tone = toneOf(d.tone),
                assetSharePct = d.assetSharePct,
                defaultContextNote = d.context,
                isCompleted = false,
                opensEventDialog = d.isEvent,
                canReject = d.id == "dec_pemintalan",
                options = opts
            )
        }

    private fun buildNews(date: String): List<NewsItem> =
        eng.news.map { n ->
            val slant = MediaSlant.valueOf(n.slant)
            val conf = when (n.confidence) {
                2 -> ConfidenceTier.HIGH
                1 -> ConfidenceTier.MEDIUM
                else -> ConfidenceTier.LOW
            }
            val verifiedNote = if (n.crossChecked) {
                when (slant) {
                    MediaSlant.PRO_PEMERINTAH -> "VERIFIKASI SILANG: Pernyataan resmi ini memperhalus risiko sebenarnya."
                    MediaSlant.SENSASIONAL -> "VERIFIKASI SILANG: Klaim ini dibesar-besarkan tanpa bukti primer."
                    MediaSlant.INDEPENDEN -> "VERIFIKASI SILANG: Dikonfirmasi oleh data primer dan dokumen lapangan."
                    else -> "VERIFIKASI SILANG: Fakta utama akurat, tetapi sudut pandang media memengaruhi penekanan."
                }
            } else "Belum diperiksa silang. Gunakan tombol 'Periksa silang (1 AP)' untuk menguji akurasi berita ini."

            NewsItem(
                id = n.id,
                mediaSlant = slant,
                headline = n.headline,
                summary = if (n.crossChecked) "${n.summary}\n\n[$verifiedNote]" else n.summary,
                dateLabel = date,
                confidence = if (n.crossChecked) ConfidenceTier.HIGH else conf,
                isRumor = n.isRumor,
                isMacroRelease = slant == MediaSlant.BISNIS,
                crossChecked = n.crossChecked,
                originInfo = DataOriginInfo(
                    title = "${slant.outletName} (${slant.slantLabel})",
                    displayedValue = n.headline,
                    sourceName = slant.outletName,
                    dataDate = date,
                    calculationMethod = slant.editorialProfile,
                    uncertaintyAndBiasNote = verifiedNote,
                    confidence = if (n.crossChecked) ConfidenceTier.HIGH else conf
                )
            )
        }

    private fun buildIncomeRows(date: String): List<FinancialRowItem> {
        val m = eng.lastIncome
        val p = eng.prevIncome
        fun row(label: String, key: String, neg: Boolean = false, bold: Boolean = false, tone: SemanticTone = SemanticTone.NEUTRAL_GRAY, note: String, formula: String): FinancialRowItem {
            val cur = m[key] ?: 0.0
            val prv = p[key] ?: 0.0
            val fCur = if (neg && cur > 0) "(${fmt(cur)})" else if (cur < 0) "(${fmt(-cur)})" else fmt(cur)
            val fPrv = if (neg && prv > 0) "(${fmt(prv)})" else if (prv < 0) "(${fmt(-prv)})" else fmt(prv)
            return FinancialRowItem(
                label = label, currentVal = fCur, priorVal = fPrv, note = note, isHeaderOrTotal = bold, tone = tone,
                originInfo = DataOriginInfo(label, "$fCur jt RS", "Buku Besar Akrual PT Benang Sundara", date, formula, note, ConfidenceTier.HIGH)
            )
        }
        val rev = m["revenue"] ?: 0.0; val cogs = m["cogs"] ?: 0.0
        val gp = rev - cogs
        val gpPrev = (p["revenue"] ?: 0.0) - (p["cogs"] ?: 0.0)
        return listOf(
            row("Pendapatan Penjualan (Akrual)", "revenue", false, true, SemanticTone.PRIMARY_BLUE,
                if (eng.stuffingActive) "Termasuk ±8% obral kredit 90 hari ke peritel rapuh" else "Kebijakan kredit ketat (DSO ±50 hari)",
                "Volume × harga × permintaan makro × faktor produksi."),
            row("Harga Pokok Penjualan (HPP)", "cogs", true, false, SemanticTone.DANGER_RED,
                "Rasio HPP ${(eng.cogsRatio() * 100).roundToInt()}% (dipengaruhi kurs, kapas, energi, pemasok, & anak usaha)",
                "Bahan baku kapas, benang, pewarna, listrik tenun, dan tenaga kerja langsung."),
            row("  · Bahan baku (kapas, benang, zat warna)", "cogs_mat", true, false, SemanticTone.NEUTRAL_GRAY,
                "50% HPP; mengikuti indeks kapas ${fmt2(eng.commodities.cotton.index)}x", "Bagian bahan baku langsung dari HPP."),
            row("  · Energi & uap pabrik", "cogs_energy", true, false, SemanticTone.NEUTRAL_GRAY,
                "15% HPP; mengikuti minyak ${fmt2(eng.commodities.oil.index)}x", "Listrik tenun dan boiler pewarnaan."),
            row("  · Upah tenaga kerja langsung (${eng.headcountProd()} orang)", "cogs_labor", true, false, SemanticTone.NEUTRAL_GRAY,
                "25% HPP; pabrik domestik & ${eng.foreignWorkers()} pekerja anak usaha", "Upah operator mesin tenun dan pewarnaan."),
            row("  · Overhead pabrik", "cogs_oh", true, false, SemanticTone.NEUTRAL_GRAY,
                "10% HPP", "Bahan pembantu, pengawasan lini, dan utilitas pabrik."),
            FinancialRowItem("Laba Kotor", fmt(gp), fmt(gpPrev), "Margin kotor ${if (rev > 0) (gp / rev * 100).roundToInt() else 0}%",
                true, SemanticTone.GOOD_GREEN, DataOriginInfo("Laba Kotor", "${fmt(gp)} jt RS", "Buku Besar", date, "Pendapatan − HPP", "", ConfidenceTier.HIGH)),
            row("Beban Operasional (SG&A)", "opex", true, false, SemanticTone.NEUTRAL_GRAY,
                "Gaji staf (${eng.headcountStaff()} orang), pemasaran, logistik, dan biaya satu kali", "Beban tetap + tambahan upah + biaya kejadian."),
            row("  · Gaji manajemen, staf & tunjangan", "op_salary", true, false, SemanticTone.NEUTRAL_GRAY,
                "Termasuk kenaikan upah permanen (${fmt(eng.wageAddOn)} jt RS/bln)", "Gaji kantor pusat, administrasi, dan penjualan."),
            row("  · Pemasaran & distribusi", "op_mkt", true, false, SemanticTone.NEUTRAL_GRAY, "Pengiriman ke ritel, komisi, promosi", "15% dari beban operasional dasar."),
            row("  · Administrasi, umum, listrik kantor & asuransi", "op_admin", true, false, SemanticTone.NEUTRAL_GRAY, "Sewa kantor, TI, audit rutin", "Administrasi dan utilitas non-pabrik."),
            row("  · Biaya operasi lini tambahan & anak usaha", "op_lines", true, false, SemanticTone.NEUTRAL_GRAY, "Biaya tetap ekspansi domestik & cabang luar negeri", "Biaya tetap kapasitas baru."),
            row("  · Program berjalan (R&D / merek)", "op_proj", true, false, SemanticTone.NEUTRAL_GRAY, "Investasi non-aset yang langsung dibebankan", "Anggaran program dibagi durasi bulan."),
            row("  · Biaya luar biasa / denda / konsultan", "op_oneoff", true, false, SemanticTone.WARNING_YELLOW, "Denda, biaya hukum, audit, provisi kredit, pajak dividen", "Biaya satu kali periode ini."),
            row("  · Pendapatan lain-lain / klaim / repatriasi", "op_gain", false, false, SemanticTone.GOOD_GREEN, "Insentif pemerintah, klaim asuransi, atau dividen anak usaha", "Pengurang beban periode ini."),
            row("Beban Perawatan Mesin", "maint", true, false, if (eng.deferralActive) SemanticTone.WARNING_YELLOW else SemanticTone.NEUTRAL_GRAY,
                if (eng.deferralActive) "Ditekan di bawah standar (tunggakan ${eng.deferredMonths} bln)" else "Perawatan penuh",
                "Perawatan rutin + perbaikan kerusakan mesin."),
            row("Penyusutan Aset Tetap", "dep", true, false, SemanticTone.NEUTRAL_GRAY,
                "Beban non-kas (ditambahkan kembali di arus kas)", "0,81% dari aset tetap per bulan."),
            row("Penghapusan Piutang Macet", "baddebt", true, false, if ((m["baddebt"] ?: 0.0) > 0) SemanticTone.DANGER_RED else SemanticTone.NEUTRAL_GRAY,
                "45% dari piutang rapuh yang berumur 6 bulan dihapusbukukan", "Kerugian piutang tak tertagih."),
            row("Laba Usaha (EBIT)", "ebit", false, true, if ((m["ebit"] ?: 0.0) >= 0) SemanticTone.GOOD_GREEN else SemanticTone.DANGER_RED,
                "Pembilang rasio ICR untuk uji covenant bank", "Laba Kotor − OpEx − Perawatan − Penyusutan − Piutang Macet."),
            row("Beban Bunga Pinjaman", "interest", true, false, SemanticTone.DANGER_RED,
                "Penyebut rasio ICR; naik saat bunga acuan atau kurs A$ naik", "Σ (Saldo pinjaman × bunga tahunan / 12)."),
            row("Pendapatan Bunga Kas", "intinc", false, false, SemanticTone.GOOD_GREEN,
                "Bunga giro/deposito atas saldo kas positif (suku bunga acuan − 3%)", "Kas × maks(0, bunga acuan − 3%) / 12."),
            row("Pajak Penghasilan Badan & Withholding Dividen (Efektif Grup ${fmt1(eng.effectiveTaxRate() * 100)}%)", "tax", true, false, SemanticTone.NEUTRAL_GRAY,
                "Termasuk pajak withholding repatriasi ${fmt(m["wht_tax"] ?: 0.0)} jt RS; kompensasi rugi: ${fmt(eng.taxLossCarryforward())} jt RS", "PPh badan 22% induk + pajak withholding dividen lintas batas."),
            row("Laba Bersih Induk", "ni", false, true, if ((m["ni"] ?: 0.0) >= 0) SemanticTone.GOOD_GREEN else SemanticTone.DANGER_RED,
                "Akrual Induk — Laba Bersih Konsolidasi Grup: ${fmt(eng.lastConsolidatedIncome["ni"] ?: (m["ni"] ?: 0.0))} jt RS", "EBIT − Bunga + Bunga Kas − Pajak.")
        )
    }

    private fun buildBalanceRows(date: String): List<FinancialRowItem> {
        val b = eng.balanceSnapshot()
        val p = eng.prevBalance
        val cons = eng.consolidatedBalanceSnapshot()
        fun row(label: String, key: String, bold: Boolean = false, tone: SemanticTone = SemanticTone.NEUTRAL_GRAY, note: String): FinancialRowItem {
            val cur = fmt(b[key] ?: 0.0)
            val prv = fmt(p[key] ?: 0.0)
            return FinancialRowItem(label, cur, prv, note, bold, tone,
                DataOriginInfo(label, "$cur jt RS", "Neraca PT Benang Sundara & Konsolidasi Grup", date, "Identitas akuntansi: Aset = Liabilitas + Ekuitas.", note, ConfidenceTier.HIGH))
        }
        val ctaVal = cons["cta_equity"] ?: 0.0
        val ctaTxt = if (ctaVal < -0.5) "(${fmt(-ctaVal)})" else fmt(ctaVal)
        return listOf(
            row("Kas dan Setara Kas", "cash", false, if (eng.cash < 3000) SemanticTone.DANGER_RED else SemanticTone.GOOD_GREEN, "Kas induk (Kas Konsolidasi Grup: ${fmt(cons["cash"] ?: eng.cash)} jt RS)"),
            row("Piutang Usaha (Total)", "ar", false, if (eng.stuffedTotal() > 1500) SemanticTone.WARNING_YELLOW else SemanticTone.NEUTRAL_GRAY,
                "DSO ${eng.dso().roundToInt()} hari"),
            row("  · Piutang lancar (pelanggan sehat)", "ar_normal", false, SemanticTone.NEUTRAL_GRAY, "Termin normal 50–62 hari"),
            row("  · Piutang berisiko (obral termin 90 hari)", "ar_risky", false, if (eng.stuffedTotal() > 500) SemanticTone.DANGER_RED else SemanticTone.NEUTRAL_GRAY,
                "Sekitar 45% macet saat berumur 6 bulan"),
            row("Persediaan Bahan & Barang Jadi", "inv", false, SemanticTone.NEUTRAL_GRAY, "DIO ${eng.dio().roundToInt()} hari"),
            row("Investasi Saham di Bursa (Nilai Pasar)", "pf", false, SemanticTone.PRIMARY_BLUE, "Portofolio saham tercatat (mark-to-market)"),
            row("Investasi pada Anak Usaha (Biaya Historis)", "inv_subs", false, SemanticTone.PRIMARY_BLUE, "Dieliminasi penuh terhadap ekuitas lokal anak usaha saat konsolidasi grup"),
            row("Goodwill Akuisisi (M&A)", "goodwill", false, SemanticTone.NEUTRAL_GRAY, "Selisih harga beli di atas aset bersih wajar pesaing; diuji penurunan nilai (impairment)"),
            row("Modal Inti Unit Bank & Asuransi Grup", "captive_bank", false, SemanticTone.PRIMARY_BLUE, "Aset produktif unit jasa keuangan internal (CAR ≥ 12%)"),
            row("Aset Tetap Bersih (Pabrik & Mesin Induk)", "fa", false, SemanticTone.NEUTRAL_GRAY, "Aset tetap konsolidasi grup: ${fmt(cons["fa"] ?: (b["fa"] ?: 0.0))} jt RS"),
            row("TOTAL ASET", "assets", true, SemanticTone.PRIMARY_BLUE, "Total Aset Konsolidasi Grup: ${fmt(cons["assets"] ?: (b["assets"] ?: 0.0))} jt RS"),
            row("Utang Dagang ke Pemasok", "payables", false, SemanticTone.NEUTRAL_GRAY, "DPO ${eng.dpo().roundToInt()} hari"),
            row("Utang Bank Jangka Pendek (≤12 bln)", "debt_st", false, if ((b["debt_st"] ?: 0.0) > 0) SemanticTone.DANGER_RED else SemanticTone.NEUTRAL_GRAY,
                "Pinjaman yang jatuh tempo dalam 12 giliran"),
            row("Utang Bank & Obligasi Jangka Panjang (>12 bln)", "debt_lt", false, SemanticTone.WARNING_YELLOW, "Pinjaman bank & obligasi korporasi jatuh tempo > 12 giliran"),
            row("TOTAL LIABILITAS", "liab", true, SemanticTone.WARNING_YELLOW, "Total Liabilitas Konsolidasi Grup: ${fmt(cons["liab"] ?: (b["liab"] ?: 0.0))} jt RS"),
            FinancialRowItem("Selisih Kurs Translasi Konsolidasi (CTA)", ctaTxt, "0",
                "Selisih translasi aset bersih valas anak usaha ke Rial Sundara (bagian dari Ekuitas Grup)", false, SemanticTone.PRIMARY_BLUE,
                DataOriginInfo("Selisih Kurs Translasi (CTA)", "$ctaTxt jt RS", "Konsolidasi Grup", date, "Σ (Ekuitas Lokal × Kurs Akhir − Ekuitas Historis RS)", "Bukan laba operasi; dicatat di ekuitas konsolidasi.", ConfidenceTier.HIGH)),
            row("TOTAL EKUITAS", "equity", true, SemanticTone.GOOD_GREEN, "Ekuitas Induk (Ekuitas Konsolidasi Grup: ${fmt(cons["equity"] ?: (b["equity"] ?: 0.0))} jt RS)")
        )
    }

    private fun buildCashFlowRows(date: String): List<FinancialRowItem> {
        val d = eng.lastDetail
        val p = eng.prevDetail
        fun fmtS(x: Double) = if (x < -0.5) "(${fmt(-x)})" else fmt(x)
        fun r(label: String, key: String, bold: Boolean = false, tone: SemanticTone = SemanticTone.NEUTRAL_GRAY, note: String): FinancialRowItem {
            val cur = d[key] ?: 0.0
            val prv = p[key] ?: 0.0
            return FinancialRowItem(label, fmtS(cur), fmtS(prv), note, bold, tone,
                DataOriginInfo(label, "${fmtS(cur)} jt RS", "Laporan Arus Kas", date, "Metode tidak langsung.", note, ConfidenceTier.HIGH))
        }
        val cfo = d["cfo"] ?: 0.0
        val net = d["net"] ?: 0.0
        return listOf(
            r("Laba Bersih", "d_ni", false, SemanticTone.NEUTRAL_GRAY, "Dari laporan laba rugi"),
            r("(+) Penyusutan (beban non-kas)", "d_dep", false, SemanticTone.GOOD_GREEN, "Ditambahkan kembali"),
            r("Perubahan Piutang Usaha (−ΔAR)", "d_ar", false, if ((d["d_ar"] ?: 0.0) < 0) SemanticTone.DANGER_RED else SemanticTone.GOOD_GREEN,
                "Negatif = penjualan tertahan di piutang pelanggan"),
            r("Perubahan Persediaan (−ΔInv)", "d_inv", false, SemanticTone.NEUTRAL_GRAY, "Negatif = kas terikat di gudang"),
            r("Perubahan Utang Dagang (+ΔAP)", "d_pay", false, SemanticTone.NEUTRAL_GRAY, "Positif = pemasok membiayai modal kerja"),
            r("A. ARUS KAS DARI AKTIVITAS OPERASI (CFO)", "cfo", true, if (cfo < 0) SemanticTone.DANGER_RED else SemanticTone.GOOD_GREEN,
                "NI + Penyusutan − ΔAR − ΔInv + ΔAP"),
            r("Belanja modal pengganti mesin", "d_capex_repl", false, SemanticTone.NEUTRAL_GRAY, "70% dari penyusutan untuk menjaga kapasitas"),
            r("Belanja modal proyek pemintalan", "d_capex_spin", false, SemanticTone.WARNING_YELLOW, "Sesuai fase jadwal Gantt"),
            r("Belanja modal ekspansi & setoran modal anak usaha", "d_capex_exp", false, SemanticTone.WARNING_YELLOW, "Lini baru, otomasi, gudang, IPAL, investasi anak usaha luar negeri"),
            r("Pembelian saham / M&A goodwill / modal bank grup", "d_stock_buy", false, SemanticTone.NEUTRAL_GRAY, "Termasuk biaya pialang 0,15% & kas akuisisi"),
            r("Penjualan saham di bursa", "d_stock_sell", false, SemanticTone.GOOD_GREEN, "Bersih setelah biaya & pajak"),
            r("Dividen tunai saham & repatriasi anak usaha", "d_divin", false, SemanticTone.GOOD_GREEN, "Dieliminasi untuk porsi antar-entitas pada konsolidasi grup"),
            r("B. ARUS KAS DARI AKTIVITAS INVESTASI (CFI)", "cfi", true, SemanticTone.NEUTRAL_GRAY,
                "Jumlah seluruh arus kas investasi di atas"),
            r("Pencairan pinjaman bank & obligasi baru", "d_newdebt", false, SemanticTone.GOOD_GREEN, "Kredit baru, obligasi korporasi, atau refinancing"),
            r("Pembayaran pokok pinjaman terjadwal", "d_repay", false, SemanticTone.WARNING_YELLOW, "Cicilan bulanan atau pelunasan bullet"),
            r("Penarikan / (pelunasan) fasilitas RCF", "d_rcf", false, SemanticTone.WARNING_YELLOW, "Fasilitas modal kerja otomatis"),
            r("Pelunasan paksa covenant", "d_forced", false, SemanticTone.DANGER_RED, "15% pokok saat gagal covenant kedua"),
            r("Pencairan anjak piutang (factoring)", "d_factor", false, SemanticTone.DANGER_RED, "Penjualan piutang darurat (potongan 4%)"),
            r("Setoran modal (Equity Cure / IPO)", "d_cure", false, SemanticTone.GOOD_GREEN, "Suntikan pemegang saham atau dana bersih IPO"),
            r("Pembayaran dividen ke pemegang saham", "d_divpaid", false, SemanticTone.WARNING_YELLOW, "Dividen tahunan / khusus"),
            r("C. ARUS KAS DARI AKTIVITAS PENDANAAN (CFF)", "cff", true, SemanticTone.NEUTRAL_GRAY,
                "Jumlah seluruh arus kas pendanaan di atas"),
            r("PERUBAHAN KAS BERSIH (A + B + C)", "net", true, if (net < 0) SemanticTone.DANGER_RED else SemanticTone.GOOD_GREEN,
                "Harus sama persis dengan Kas Akhir − Kas Awal")
        )
    }

    private fun buildEquityRows(date: String): List<FinancialRowItem> {
        val d = eng.lastDetail
        val p = eng.prevDetail
        fun fmtS(x: Double) = if (x < -0.5) "(${fmt(-x)})" else fmt(x)
        fun r(label: String, key: String, bold: Boolean = false, tone: SemanticTone = SemanticTone.NEUTRAL_GRAY, note: String) =
            FinancialRowItem(label, fmtS(d[key] ?: 0.0), fmtS(p[key] ?: 0.0), note, bold, tone,
                DataOriginInfo(label, "${fmtS(d[key] ?: 0.0)} jt RS", "Laporan Perubahan Ekuitas", date, "Rekonsiliasi ekuitas awal ke ekuitas akhir.", note, ConfidenceTier.HIGH))
        return listOf(
            r("Ekuitas Awal Periode", "e_open", true, SemanticTone.NEUTRAL_GRAY, "Sama dengan ekuitas akhir bulan lalu"),
            r("(+) Laba Bersih periode berjalan", "e_ni", false, SemanticTone.GOOD_GREEN, "Dari laporan laba rugi"),
            r("(+) Dividen tunai portofolio saham", "e_divin", false, SemanticTone.GOOD_GREEN, "Pendapatan investasi saham"),
            r("(+) Dividen repatriasi dari anak usaha (Induk)", "e_sub_div", false, SemanticTone.GOOD_GREEN, "Dieliminasi penuh pada ekuitas konsolidasi grup"),
            r("(+/−) Selisih nilai pasar portofolio saham (MTM)", "e_mtm", false, SemanticTone.PRIMARY_BLUE, "Keuntungan/kerugian belum direalisasi"),
            r("(−) Penurunan nilai Goodwill / Hapus buku aset", "e_impair", false, SemanticTone.WARNING_YELLOW, "Impairment goodwill M&A atau nasionalisasi"),
            r("(−) Biaya transaksi jual-beli saham", "e_fees", false, SemanticTone.NEUTRAL_GRAY, "Komisi pialang & pajak bursa"),
            r("(+/−) Selisih kurs atas pinjaman valas (A$)", "e_fx", false, SemanticTone.WARNING_YELLOW, "Perubahan kewajiban RS karena kurs A$"),
            r("(−) Potongan anjak piutang (factoring 4%)", "e_factor", false, SemanticTone.DANGER_RED, "Diskon penjualan piutang darurat"),
            r("(+) Setoran modal (Equity Cure / IPO)", "e_cure", false, SemanticTone.GOOD_GREEN, "Suntikan pemegang saham / penerbitan saham publik"),
            r("(−) Dividen kepada pemegang saham", "e_div", false, SemanticTone.WARNING_YELLOW, "Pembagian laba"),
            r("EKUITAS AKHIR PERIODE", "e_close", true, SemanticTone.GOOD_GREEN, "Harus cocok dengan Total Ekuitas di Neraca")
        )
    }

    private fun buildReportCheck(): ReportCheck {
        val b = eng.balanceSnapshot()
        val assets = b["assets"] ?: 0.0
        val lpe = (b["liab"] ?: 0.0) + (b["equity"] ?: 0.0)
        val d = eng.lastDetail
        val cfSum = (d["cfo"] ?: 0.0) + (d["cfi"] ?: 0.0) + (d["cff"] ?: 0.0)
        val dCash = d["net"] ?: 0.0
        val eqResid = abs(d["e_resid"] ?: 0.0)
        return ReportCheck(
            balanceOk = abs(assets - lpe) < 1.0,
            assetsText = "${fmt(assets)} jt RS",
            liabPlusEqText = "${fmt(lpe)} jt RS",
            cashFlowOk = abs(cfSum - dCash) < 1.0,
            cfSumText = "${signed(cfSum)} jt RS",
            dCashText = "${signed(dCash)} jt RS",
            equityOk = eqResid < 1.0,
            eqCalcText = "${fmt((d["e_close"] ?: 0.0) - (d["e_resid"] ?: 0.0))} jt RS",
            eqActualText = "${fmt(b["equity"] ?: 0.0)} jt RS"
        )
    }

    private fun buildFinancialNotes(): List<StatementNote> {
        val loanLines = eng.loans.filter { it.balance > 0.5 }.joinToString("\n") { l ->
            val rs = eng.loanBalanceRs(l)
            val cur = if (l.currencyAud) "${fmt1(l.balance)} jt A$ (setara ${fmt(rs)} jt RS pada kurs ${fmt(eng.kurs)})" else "${fmt(rs)} jt RS"
            val rate = "${fmt2(l.annualRatePct)}% (${if (l.floatingSpreadPct != null) "mengambang: acuan + ${fmt2(l.floatingSpreadPct!!)}%" else "tetap"})"
            val mat = if (l.maturityTurn < 900) "jatuh tempo ${dateText(l.maturityTurn)}" else "fasilitas bergulir"
            val cov = if (l.covenantIcr > 0) ", covenant ICR ≥ ${fmt2(l.covenantIcr)}x" else ""
            "• ${l.lender}: saldo $cur, bunga $rate, $mat$cov."
        }.ifEmpty { "• Tidak ada pinjaman bank aktif." }

        val ageBuckets = eng.stuffedBuckets.groupBy { (eng.turn - it.first).coerceIn(1, 6) }
            .entries.sortedBy { it.key }
            .joinToString(", ") { (m, list) -> "umur $m bln: ${fmt(list.sumOf { it.second })} jt RS" }
            .ifEmpty { "tidak ada piutang obral" }

        val consBal = eng.consolidatedBalanceSnapshot()
        val subNote = if (eng.subsidiaries.isEmpty()) {
            "Belum ada anak usaha luar negeri. Grup beroperasi sebagai entitas domestik di Sundara (PPh badan standar 22%)."
        } else {
            eng.subsidiaries.values.joinToString("\n") { s ->
                val meta = eng.foreignMeta.getValue(s.spec.countryId)
                "• ${s.spec.title} (${s.spec.countryName}, ${s.spec.currencyCode}): status ${if (s.nationalized) "Dinasionalisasi" else if (s.active) "Beroperasi" else "Konstruksi"}, aset lokal ${fmt1(s.totalAssetsLocal())} jt ${s.spec.currencyCode}, utang bank lokal ${fmt1(s.bankDebtLocal)} jt ${s.spec.currencyCode}, laba ditahan siap repatriasi ${fmt1(s.retainedForeign)} jt ${s.spec.currencyCode} (kurs ${fmt1(meta.fxToRs)} RS/${s.spec.currencyCode}), PPh lokal ${fmt1(meta.profile.corpTaxPct)}%."
            }
        }

        return listOf(
            StatementNote(1, "Kebijakan Akuntansi, Konsolidasi Grup & Pajak Multinasional",
                "Laporan disusun dengan basis akrual dalam juta Rial Sundara (jt RS). Anak usaha luar negeri menyusun pembukuan penuh dalam mata uang lokal masing-masing (TRS, KST, HLV, MRD) dan dikonsolidasikan ke RS dengan eliminasi penuh atas investasi induk, transaksi royalti antar-entitas (${fmt(eng.lastConsolidatedIncome["elim_intercompany_rev"] ?: 0.0)} jt RS), serta dividen antar-entitas. Selisih Kurs Translasi (CTA) saat ini: ${fmt(consBal["cta_equity"] ?: 0.0)} jt RS. Tarif PPh efektif grup: ${fmt1(eng.effectiveTaxRate() * 100)}% (mode ${eng.transferPricingMode.label})."),
            StatementNote(2, "Piutang Usaha & Kualitas Kredit Pelanggan",
                "Piutang lancar sebesar ${fmt(eng.normalAR)} jt RS berasal dari peritel dengan termin standar (${if (eng.salesScheme == SalesScheme.OMZET) 62 else 50} hari). Piutang berisiko sebesar ${fmt(eng.stuffedTotal())} jt RS berasal dari penjualan dengan termin 90 hari kepada peritel kecil ($ageBuckets). Berdasarkan pengalaman historis, 45% dari piutang berisiko yang mencapai umur 6 bulan tidak tertagih dan dihapusbukukan."),
            StatementNote(3, "Rincian Pinjaman Bank, Obligasi, Covenant & Lindung Nilai Valas",
                "$loanLines\n• Status Lindung Nilai Forward Valas RS/A$: ${if (eng.fxHedgeActive) "Aktif hingga ${dateText(eng.fxHedgeUntilTurn)} pada kurs ${fmt(eng.hedgedKurs)} RS/A$" else "Tidak aktif"}."),
            StatementNote(4, "Anak Usaha Lintas Negara (Era 2), Goodwill M&A (Era 3) & Transisi Hijau (Era 4)",
                "$subNote\n• Goodwill Akuisisi: ${fmt(eng.goodwill)} jt RS · Modal Bank Grup: ${fmt(eng.captiveBankCapitalRs)} jt RS · Saham Publik (IPO): ${if (eng.isPublicCompany) "Tercatat (porsi pendiri ${fmt1(eng.founderSharePct)}%)" else "Tertutup"} · Kredit Karbon: ${eng.carbonCredits} unit · Suksesor CEO: ${eng.appointedSuccessorId ?: "Belum ditunjuk"}.")
        )
    }

    private fun buildAccrualSummary(): String {
        val ni = eng.lastIncome["ni"] ?: 0.0
        val cfo = eng.lastCashFlow["cfo"] ?: 0.0
        val net = eng.lastCashFlow["net"] ?: 0.0
        return if (ni > 0 && cfo < 0)
            "JEBAKAN AKRUAL: Bulan lalu Laba Bersih +${fmt(ni)} jt RS, tetapi Arus Kas Operasi ${signed(cfo)} jt RS dan kas berubah ${signed(net)} jt RS. Uang Anda tertahan di piutang dan persediaan."
        else "Bulan lalu: Laba Bersih ${signed(ni)} jt RS · Arus Kas Operasi ${signed(cfo)} jt RS · Perubahan Kas Bersih ${signed(net)} jt RS."
    }

    private fun buildExecutives(date: String): List<Executive> =
        eng.execs.map { e ->
            val loyLabel = when {
                !e.present -> "Kosong"
                e.loyalty >= 0.7 -> "Tinggi?"
                e.loyalty >= 0.4 -> "Sedang?"
                else -> "Rendah?"
            }
            val loyTone = when {
                !e.present -> SemanticTone.NEUTRAL_GRAY
                e.loyalty >= 0.7 -> SemanticTone.GOOD_GREEN
                e.loyalty >= 0.4 -> SemanticTone.WARNING_YELLOW
                else -> SemanticTone.DANGER_RED
            }
            val scheme = when (e.id) {
                "exec_sales" -> eng.salesScheme.label
                "exec_factory" -> eng.factoryScheme.label
                "exec_coo" -> "Gaji tetap + bonus proyek selesai tepat waktu"
                else -> "Gaji tetap + bonus kepatuhan covenant & akurasi kas"
            }
            val suspect = when (e.id) {
                "exec_sales" -> when {
                    e.stopped -> "Telah dikonfrontasi; tidak lagi mengobral termin 90 hari."
                    eng.stuffingActive -> "Memperpanjang termin ritel kecil ke 90 hari demi mengejar bonus omzet (piutang berisiko ${fmt(eng.stuffedTotal())} jt RS)."
                    else -> "Mengikuti kebijakan penagihan kas; piutang berisiko lama sedang dicairkan."
                }
                "exec_factory" -> when {
                    e.stopped -> "Telah dikonfrontasi; mengikuti jadwal perawatan standar."
                    eng.deferralActive -> "Menunda penggantian suku cadang (${eng.deferredMonths} bulan tertunda) agar biaya kuartal terlihat hemat."
                    else -> "Perawatan mesin dijalankan penuh; tunggakan perawatan tersisa ${eng.deferredMonths} bulan."
                }
                "exec_coo" -> if (eng.kickbackActive) "Harga bahan dari pemasok pilihannya ~1% di atas pasar; belum terbukti tanpa audit."
                else "Rantai pasok dikelola wajar; fokus pada proyek pemintalan dan cabang luar negeri."
                else -> "Melaporkan posisi kas, jarak covenant, dan tarif pajak efektif grup tanpa manipulasi."
            }
            val auditTxt = when {
                e.auditDueTurn >= 0 -> "Sedang diaudit (hasil akhir bulan ini)"
                e.evidence == true -> "Audit: ditemukan indikasi pelanggaran (siap dikonfrontasi)"
                e.evidence == false -> "Audit: tidak ditemukan bukti pelanggaran"
                else -> "Belum diaudit"
            }
            Executive(
                id = e.id,
                initials = e.initials,
                name = if (e.present) e.name else "${e.name} (posisi kosong)",
                roleTitle = e.role,
                loyaltyEstimate = loyLabel,
                ambitionEstimate = if (e.id == "exec_sales") "Tinggi?" else "Sedang?",
                skillEstimate = "Tinggi?",
                horizonNote = if (e.id == "exec_cfo") "Jangka panjang" else "Kuartal ini",
                incentiveScheme = scheme,
                suspectedBehaviorNote = if (e.lastOutcome.isNotEmpty()) "${e.lastOutcome} · $suspect" else suspect,
                loyaltyTone = loyTone,
                originInfo = DataOriginInfo(
                    title = "Profil: ${e.name}",
                    displayedValue = "Loyalitas $loyLabel · $auditTxt",
                    sourceName = "Pengamatan HRD & Audit Internal",
                    dataDate = date,
                    calculationMethod = "Loyalitas sejati tersembunyi. Audit forensik (2 AP, 150 jt RS) mendeteksi pelanggaran dengan akurasi ±85% (positif palsu ±10%).",
                    uncertaintyAndBiasNote = "Mengganti orang tanpa mengubah skema insentif akan menghasilkan perilaku yang sama pada penggantinya.",
                    confidence = ConfidenceTier.LOW
                ),
                present = e.present,
                vacantNote = if (!e.present) "Pengganti masuk pada ${dateText(e.vacantUntil)} (selama kosong AP maksimum −1)." else "",
                canAudit = e.present && e.auditDueTurn < 0 && e.evidence == null && eng.ap >= 2,
                auditStatus = auditTxt,
                canConfront = e.present && e.evidence == true && eng.ap >= 1,
                canFire = e.present && eng.ap >= 1,
                lastOutcome = e.lastOutcome
            )
        }

    private fun buildGantt(): List<GanttTask> {
        val elapsed = if (eng.spinningDecision == "approved" && eng.turn >= eng.spinningStartTurn) eng.turn - eng.spinningStartTurn else 0
        val spinTasks = eng.spinningPhases.mapIndexed { idx, ph ->
            val prog = (elapsed - ph.startMonth).coerceIn(0, ph.months)
            val real = (ph.budget * prog / ph.months).roundToInt()
            val status = when {
                eng.spinningDecision == null -> "Menunggu persetujuan di Markas (3 AP)"
                eng.spinningDecision == "rejected" -> "Ditolak"
                prog >= ph.months -> "Selesai"
                prog > 0 -> if (idx == 2 && eng.spinningDelayed) "Molor 3 bln (+18% biaya)" else "Berjalan ($prog/${ph.months} bln)"
                else -> "Dijadwalkan"
            }
            val tone = when {
                idx == 2 && eng.spinningDelayed && prog > 0 -> SemanticTone.DANGER_RED
                prog >= ph.months -> SemanticTone.GOOD_GREEN
                prog > 0 -> SemanticTone.PRIMARY_BLUE
                else -> SemanticTone.NEUTRAL_GRAY
            }
            GanttTask(
                id = "gantt_${idx + 1}",
                projectName = "Pabrik pemintalan",
                phaseName = ph.name,
                startMonth = ph.startMonth + 1,
                durationMonths = ph.months,
                progressMonth = prog,
                budgetRsMillion = ph.budget.roundToInt(),
                realizedRsMillion = real,
                statusLabel = status,
                tone = tone,
                noteFromStaff = if (idx == 2 && eng.spinningDelayed) "Tertahan pembatasan ekspor Kestrel; waktu +3 bulan dan biaya +18%."
                else "Setelah seluruh fase selesai, rasio HPP turun bertahap hingga 5 poin dalam 6 bulan."
            )
        }
        val subTasks = eng.projectsInv.filter { it.type.startsWith("sub_") }.mapIndexed { i, p ->
            val cid = p.type.removePrefix("sub_")
            val spec = eng.subsidiarySpec(cid)
            val prog = (eng.turn - p.startTurn).coerceIn(0, p.months)
            GanttTask(
                id = "gantt_sub_$i",
                projectName = spec?.title ?: "Anak Usaha Luar Negeri",
                phaseName = "Konstruksi & Izin Lintas Negara (${spec?.countryName ?: cid})",
                startMonth = 1,
                durationMonths = p.months,
                progressMonth = if (p.done) p.months else prog,
                budgetRsMillion = p.totalCost.roundToInt(),
                realizedRsMillion = p.spent.roundToInt(),
                statusLabel = if (p.done) "Beroperasi" else "Berjalan ($prog/${p.months} bln)",
                tone = if (p.done) SemanticTone.GOOD_GREEN else SemanticTone.PRIMARY_BLUE,
                noteFromStaff = spec?.benefitText ?: ""
            )
        }
        return spinTasks + subTasks
    }

    private fun buildContracts(date: String): List<ContractItem> {
        val list = mutableListOf<ContractItem>()
        for (l in eng.loans) {
            if (l.balance <= 0.5 && l.id != "rcf") continue
            val rev = l.id in eng.reviewedContracts
            val valTxt = if (l.currencyAud) "${fmt2(l.balance)} jt A$ (≈ ${fmt(eng.loanBalanceRs(l))} jt RS)"
            else "${fmt(l.balance)} jt RS"
            val matTxt = if (l.maturityTurn < 900) "Jatuh tempo ${dateText(l.maturityTurn)} (${max(0, l.maturityTurn - eng.turn)} bln lagi)" else "Revolving s.d. ${fmt(eng.sc.rcfLimit)} jt RS"
            val hidden = when (l.id) {
                "bank_b" -> "Klausul tersembunyi: waiver pertama biaya 0,5% pokok + spread +1,5%; gagal kedua pelunasan paksa 15% pokok; gagal ketiga penyitaan pabrik."
                "aurelia" -> "Klausul MAC: jika Rial melemah ≥10% dalam 1 bulan, bank berhak menuntut pelunasan paksa 30% pokok dalam 3 giliran dan menaikkan bunga ke 9,2%."
                else -> "Klausul cross-default standar."
            }
            list += ContractItem(
                id = l.id,
                category = "Pinjaman Aktif",
                counterparty = l.lender,
                countryName = if (l.currencyAud) "Aurelia (A$)" else "Sundara (RS)",
                valueText = "$valTxt · bunga ${fmt2(l.annualRatePct)}%",
                maturityOrTenor = matTxt,
                covenantOrClause = if (l.covenantIcr > 0) "ICR ≥ ${fmt2(l.covenantIcr)}x; Utang bersih/EBITDA ≤ 3,5x" else "Tanpa uji ICR terpisah",
                hiddenRiskNote = if (rev) hidden else "Belum ditinjau konsultan hukum. Ketuk tombol untuk membuka klausul tersembunyi (1 AP, 50 jt RS).",
                tone = if (l.id == "bank_b" && l.maturityTurn - eng.turn <= 4) SemanticTone.DANGER_RED else SemanticTone.WARNING_YELLOW,
                apCostToAct = 1,
                actionLabel = if (rev) "Klausul sudah ditinjau" else "Tinjau klausul hukum (1 AP)",
                originInfo = DataOriginInfo(l.lender, valTxt, "Perjanjian Kredit", date, "Kewajiban kontraktual.", hidden, ConfidenceTier.HIGH),
                reviewed = rev,
                revealedClause = hidden,
                canAct = !rev && eng.ap >= 1
            )
        }
        list += ContractItem(
            id = "act_dual",
            category = "Rantai Pasok Komponen",
            counterparty = if (eng.dualSourcing) "Kestrel Loom Parts + Halvenia Precision (Dual Sourcing)" else "Kestrel Precision Loom Parts (Pemasok Tunggal)",
            countryName = "Kestrel",
            valueText = if (eng.dualSourcing) "HPP +1,2% (aman dari hold-up, sanksi, & borongan pesaing)" else "650 jt RS/bln (rawan hold-up, sanksi Tier-2, & serangan pasokan pesaing)",
            maturityOrTenor = "Kontrak berjalan",
            covenantOrClause = "Sensor tenun Kestrel mengandung mineral Velmora (Tier-2)",
            hiddenRiskNote = if (eng.dualSourcing) "Pemasok kedua sudah aktif: bebas dari penahanan kiriman dan denda sanksi."
            else "Tanpa pemasok kedua, Kestrel bisa menahan kiriman (produksi −30%), Aurelia bisa menjatuhkan denda sanksi 600 jt RS, dan Mitra Tekstil bisa memborong pasokan.",
            tone = if (eng.dualSourcing) SemanticTone.GOOD_GREEN else SemanticTone.WARNING_YELLOW,
            apCostToAct = 1,
            actionLabel = if (eng.dualSourcing) "Dual sourcing sudah aktif" else "Aktifkan pemasok kedua (1 AP)",
            originInfo = DataOriginInfo("Kontrak Komponen Kestrel", "Tier-1 → Tier-2 Velmora", "Bagian Pengadaan", date,
                "Biaya komponen masuk ke rasio HPP.", "Dual sourcing menambah HPP 1,2% tetapi menghilangkan risiko hold-up dan sanksi.", ConfidenceTier.MEDIUM),
            reviewed = eng.dualSourcing,
            canAct = !eng.dualSourcing && eng.ap >= 1
        )
        list += ContractItem(
            id = "act_hedge",
            category = "Lindung Nilai Bahan Baku",
            counterparty = "Kontrak Berjangka Kapas Bursa Tarsis",
            countryName = "Tarsis",
            valueText = if (eng.hedgeActive) "Aktif s.d. ${dateText(eng.hedgeUntilTurn)}" else "Harga pasar (${fmt(eng.commodities.cotton.price)} A$/ton)",
            maturityOrTenor = "6 bulan per kontrak",
            covenantOrClause = "Mengunci indeks kapas pada harga saat ini + 2% (premi 60 jt RS)",
            hiddenRiskNote = "Melindungi HPP dari lonjakan harga kapas karena El Niño atau borongan pesaing selama 6 giliran.",
            tone = if (eng.hedgeActive) SemanticTone.GOOD_GREEN else SemanticTone.PRIMARY_BLUE,
            apCostToAct = 1,
            actionLabel = if (eng.hedgeActive) "Lindung nilai kapas aktif" else "Kunci harga kapas 6 bln (1 AP)",
            originInfo = DataOriginInfo("Lindung Nilai Kapas", "Kontrak 6 bulan", "Meja Komoditas", date, "Mengunci porsi kapas dalam perhitungan HPP.", "", ConfidenceTier.HIGH),
            reviewed = eng.hedgeActive,
            canAct = !eng.hedgeActive && eng.ap >= 1
        )
        list += ContractItem(
            id = "act_hedge_fx",
            category = "Lindung Nilai Valas (Era 2)",
            counterparty = "Kontrak Forward Valas RS/A$ (Meja Tresuri Meridia)",
            countryName = "Aurelia / Meridia",
            valueText = if (eng.fxHedgeActive) "Terkunci ${fmt(eng.hedgedKurs)} RS/A$ s.d. ${dateText(eng.fxHedgeUntilTurn)}" else "Kurs spot ${fmt(eng.kurs)} RS/A$",
            maturityOrTenor = "6 bulan per kontrak",
            covenantOrClause = "Mengunci nilai tukar RS/A$ untuk porsi HPP impor dan menutup 90% rugi selisih kurs utang A$",
            hiddenRiskNote = "Melindungi neraca dan margin saat Rial jatuh akibat pemilu atau pengetatan suku bunga The Fed Aurelia.",
            tone = if (eng.fxHedgeActive) SemanticTone.GOOD_GREEN else SemanticTone.PRIMARY_BLUE,
            apCostToAct = 1,
            actionLabel = if (eng.fxHedgeActive) "Forward valas aktif" else "Kunci Forward Valas RS/A$ 6 bln (1 AP)",
            originInfo = DataOriginInfo("Kontrak Forward Valas RS/A$", "${fmt(eng.kurs)} RS/A$", "Meja Tresuri Valas", date,
                "Menutup 90% kerugian translasi utang A$ dan membatasi dampak pelemahan kurs pada HPP.", "Premi forward 75 jt RS untuk 6 bulan.", ConfidenceTier.HIGH),
            reviewed = eng.fxHedgeActive,
            canAct = !eng.fxHedgeActive && eng.ap >= 1
        )
        return list
    }

    private fun buildMarkets(date: String): List<MarketInstrument> {
        val c = eng.commodities
        fun seriesInst(id: String, name: String, ticker: String, unit: String, s: Commodities.Series, note: String, actionLabel: String? = null, actionOk: Boolean = false): MarketInstrument {
            val h = s.history.map { it.toFloat() }
            val chg = if (h.size >= 2) (h.last() / h[h.size - 2] - 1f) * 100f else 0f
            val p = s.price.toFloat()
            return MarketInstrument(
                id = id, category = "Komoditas", name = name, ticker = ticker,
                priceText = "${fmt1(s.price)} $unit",
                changeText = "${if (chg >= 0) "+" else ""}${fmt1(chg.toDouble())}% bln ini · indeks ${fmt2(s.index)}x",
                tone = if (s.index > 1.08) SemanticTone.DANGER_RED else if (s.index < 0.95) SemanticTone.GOOD_GREEN else SemanticTone.NEUTRAL_GRAY,
                sparklineHistory = h,
                fanProjectionMedian = List(6) { i -> p * (1f - 0.04f * (i + 1) * (s.index.toFloat() - 1f)) },
                fanProjectionSpread = List(6) { i -> p * 0.05f * (i + 1) },
                forensicMScoreNote = note,
                originInfo = DataOriginInfo(name, "${fmt1(s.price)} $unit", "Bursa Komoditas", date,
                    "Proses AR(1) pada log-harga + permintaan dunia + guncangan pasokan.", note, ConfidenceTier.HIGH),
                actionLabel = actionLabel,
                actionEnabled = actionOk
            )
        }
        val kh = eng.kursHist.takeLast(6).map { it.toFloat() }
        val kChg = if (kh.size >= 2) (kh.last() / kh[kh.size - 2] - 1f) * 100f else 0f
        val kNow = eng.kurs.toFloat()
        return listOf(
            seriesInst("mkt_kapas", "Kapas Mentah (Tarsis Grade-A)", "CTN", "A$/ton", c.cotton,
                if (eng.hedgeActive) "Lindung nilai kapas aktif s.d. ${dateText(eng.hedgeUntilTurn)}."
                else "78% dari indeks bahan baku Anda. Ketuk di tab Kontrak atau di bawah untuk mengunci harga 6 bulan (1 AP).",
                if (eng.hedgeActive) "Lindung nilai kapas aktif" else "Kunci harga kapas 6 bulan (1 AP, 60 jt RS)",
                !eng.hedgeActive && eng.ap >= 1),
            seriesInst("mkt_minyak", "Minyak Mentah Brent-Norvaldia", "OIL", "A$/barel", c.oil,
                "22% dari indeks biaya bahan & energi pabrik Anda. Dipengaruhi ketegangan Selat Norvaldia."),
            seriesInst("mkt_baja", "Baja Konstruksi (Kestrel)", "STL", "A$/ton", c.steel,
                "Mempengaruhi harga suku cadang mesin Kestrel dan biaya konstruksi."),
            MarketInstrument(
                id = "mkt_kurs", category = "Kurs Valas", name = "Kurs Rial Sundara per Aurelia Dollar", ticker = "RS/A$",
                priceText = "${fmt(eng.kurs)} RS",
                changeText = "${if (kChg >= 0) "+" else ""}${fmt1(kChg.toDouble())}% bln ini (riil ${fmt2(eng.fxReal())}x)",
                tone = if (kChg > 1.5f) SemanticTone.DANGER_RED else SemanticTone.WARNING_YELLOW,
                sparklineHistory = kh,
                fanProjectionMedian = List(6) { i -> kNow * (1f + 0.004f * (i + 1)) },
                fanProjectionSpread = List(6) { i -> kNow * 0.025f * (i + 1) },
                forensicMScoreNote = if (eng.fxHedgeActive)
                    "Forward Valas aktif pada ${fmt(eng.hedgedKurs)} RS/A$ s.d. ${dateText(eng.fxHedgeUntilTurn)}."
                else "Mengikuti paritas daya beli (selisih inflasi Sundara vs Aurelia) + kejutan pemilu. Mempengaruhi HPP impor dan nilai utang A$.",
                originInfo = DataOriginInfo("Kurs Spot RS/A$", "${fmt(eng.kurs)} RS", "Pasar Valas Antarbank", date,
                    "Mengikuti selisih inflasi (PPP) + guncangan pemilu.", "Utang dalam A$ dihitung ulang ke RS setiap akhir bulan.", ConfidenceTier.HIGH),
                actionLabel = if (eng.fxHedgeActive) "Forward valas aktif" else "Kunci Forward Valas RS/A$ 6 bln (1 AP, 75 jt RS)",
                actionEnabled = !eng.fxHedgeActive && eng.ap >= 1
            )
        )
    }

    private fun buildHoldings(): List<HoldingRow> =
        eng.holdings.entries.mapNotNull { (id, lots) ->
            val c = eng.bourse.company(id) ?: return@mapNotNull null
            val basis = eng.costBasis[id] ?: 0.0
            val avg = if (lots > 0) basis / (lots * KapitalEngine.LOT) else 0.0
            val value = lots * KapitalEngine.LOT * c.price
            val pnl = value - basis
            val pct = if (basis > 0) pnl / basis * 100.0 else 0.0
            HoldingRow(
                id = id, ticker = c.spec.ticker, name = c.spec.name, lots = lots,
                avgPriceText = "${fmt(avg)} RS", lastPriceText = "${fmt(c.price)} RS",
                valueText = "${fmt(value)} jt RS",
                pnlText = "${signed(pnl)} jt RS (${if (pct >= 0) "+" else ""}${fmt1(pct)}%)",
                pnlTone = if (pnl >= 0) SemanticTone.GOOD_GREEN else SemanticTone.DANGER_RED
            )
        }

    private fun buildStocks(): List<StockCard> =
        eng.bourse.companies.map { c ->
            val h = c.priceHist.map { it.toFloat() }
            val chg = if (h.size >= 2 && h[h.size - 2] > 0f) (h.last() / h[h.size - 2] - 1f) * 100f else 0f
            val eps = c.eps()
            val per = if (eps > 1.0) "${fmt1(c.price / eps)}x" else "Rugi"
            val pbv = if (c.bvps > 1.0) "${fmt2(c.price / c.bvps)}x" else "—"
            val dy = if (c.price > 0) "${fmt1(c.lastDps / c.price * 100.0)}%" else "0%"
            val roe = if (c.equity > 0) "${fmt1(c.ttmNi() / c.equity * 100.0)}%" else "—"
            val ebitda12 = c.monthly.takeLast(12).sumOf { it.ebit + it.dep }
            val de = if (ebitda12 > 1.0) "${fmt1((c.debt - c.cash) / ebitda12)}x" else "—"
            val lots = eng.holdings[c.spec.id] ?: 0
            val basis = eng.costBasis[c.spec.id] ?: 0.0
            val valNow = lots * KapitalEngine.LOT * c.price
            val unr = valNow - basis
            val lotCost = KapitalEngine.LOT * c.price * (1.0 + KapitalEngine.BROKER_FEE)
            val qs = c.quarters.asReversed().take(4).map { q ->
                val l = q.last
                StockQuarter(
                    label = q.label,
                    revenue = fmt(q.revenue), cogs = "(${fmt(q.cogs)})", opex = "(${fmt(q.opex)})", dep = "(${fmt(q.dep)})",
                    ebit = fmt(q.ebit), interest = "(${fmt(q.interest)})", tax = "(${fmt(q.tax)})", ni = fmt(q.ni),
                    ar = fmt(l.ar), inv = fmt(l.inv), fa = fmt(l.fa), ap = fmt(l.ap), debt = fmt(l.debt), equity = fmt(l.equity),
                    cfo = signed(q.cfo), capex = "(${fmt(q.capex)})", dDebt = signed(q.dDebt),
                    dividend = if (q.dividend > 0) "(${fmt(q.dividend)})" else "0",
                    cashStart = fmt(q.cashStart), cashEnd = fmt(q.cashEnd),
                    surprise = c.surpriseNote
                )
            }
            StockCard(
                id = c.spec.id, ticker = c.spec.ticker, name = c.spec.name, sector = c.spec.sector,
                blurb = c.spec.blurb, riskNote = c.spec.riskNote, listed = c.listed,
                priceText = if (c.listed) "${fmt(c.price)} RS" else "SUSPEN (${fmt(c.price)} RS)",
                changePctText = "${if (chg >= 0) "+" else ""}${fmt1(chg.toDouble())}%",
                tone = when {
                    !c.listed -> SemanticTone.DANGER_RED
                    chg >= 0.5f -> SemanticTone.GOOD_GREEN
                    chg <= -0.5f -> SemanticTone.DANGER_RED
                    else -> SemanticTone.NEUTRAL_GRAY
                },
                sparkline = h,
                perText = per, pbvText = pbv, divYieldText = dy, roeText = roe, debtEbitdaText = de,
                marketCapText = "${fmt(c.marketCap())} jt RS",
                lastQuarterLabel = c.quarters.lastOrNull()?.label ?: "Menunggu rilis triwulan",
                surpriseNote = c.surpriseNote.ifEmpty { "Laporan keuangan terbit tiap akhir triwulan (Maret, Juni, September, Desember)." },
                quarters = qs,
                heldLots = lots,
                heldValueText = "${fmt(valNow)} jt RS",
                avgCostText = if (lots > 0) "${fmt(basis / (lots * KapitalEngine.LOT))} RS" else "—",
                unrealizedText = if (lots > 0) "${signed(unr)} jt RS" else "0 jt RS",
                unrealizedTone = if (unr >= 0) SemanticTone.GOOD_GREEN else SemanticTone.DANGER_RED,
                lotCostText = "${fmt(KapitalEngine.LOT * c.price)} jt RS / lot",
                canBuy1 = c.listed && eng.ap >= 1 && eng.cash - lotCost >= 1500.0,
                canBuy10 = c.listed && eng.ap >= 1 && eng.cash - 10 * lotCost >= 1500.0 && 10 * lotCost <= 0.5 * eng.totalAssets(),
                canSell1 = c.listed && eng.ap >= 1 && lots >= 1,
                canSellAll = c.listed && eng.ap >= 1 && lots >= 1
            )
        }

    private fun buildBanks(): List<BankCard> {
        val m = eng.creditMetrics(5000.0)
        val activeIds = eng.credit.active().map { it.bankId }.toSet()
        return eng.credit.banks.map { b ->
            val base = eng.credit.baseRate(b, eng.policyRate)
            val sr = eng.credit.score(b, m, 5000.0, base + 1.0, eng.turn, 0.0)
            val hardNo = b.kind == BankKind.STATE && m.covenantBreaches >= 2
            val margin = sr.total - b.minScore
            val chance = when {
                hardNo -> "Sangat rendah (ada pelanggaran covenant)"
                margin >= 12 -> "Tinggi"
                margin >= 2 -> "Sedang (mungkin dipotong atau bersyarat)"
                margin >= -6 -> "Tipis"
                else -> "Rendah"
            }
            val weakLabel = when (sr.weakest) {
                "cov" -> "Rasio penutupan bunga (ICR)"
                "dscr" -> "Kemampuan bayar cicilan (DSCR)"
                "lev" -> "Utang bersih / EBITDA"
                "liq" -> "Likuiditas kas & current ratio"
                "col" -> "Agunan bebas"
                else -> "Rekam jejak covenant / DSO"
            }
            val block = when {
                b.id in activeIds -> "Pengajuan di bank ini sedang diproses."
                eng.credit.active().size >= 3 -> "Maksimal 3 pengajuan aktif sekaligus."
                eng.ap < 2 -> "Butuh 2 AP untuk menyusun proposal kredit."
                else -> null
            }
            BankCard(
                id = b.id, name = b.name, banker = b.banker, bankerRole = b.bankerRole,
                kindLabel = when (b.kind) {
                    BankKind.COMMERCIAL -> "Bank Komersial"
                    BankKind.STATE -> "Bank BUMN"
                    BankKind.FOREIGN -> "Bank Asing (A$)"
                    BankKind.SHADOW -> "Pinjaman Kilat (Non-Bank)"
                },
                foreign = b.foreign,
                indicativeRateText = if (b.foreign) "±${fmt2(base)}% tetap dalam A$"
                else "±${fmt2(base)}% – ${fmt2(base + 2.5)}% (mengambang: bunga acuan + spread)",
                feeText = "Provisi ${fmt2(b.feePct)}%",
                maxTenor = b.maxTenor,
                maxAmountText = b.maxAmount?.let { "Maks ${fmt(it)} jt RS" } ?: "Sesuai kapasitas agunan & EBITDA",
                reviewTurns = b.reviewTurns,
                covenantText = if (b.covenantIcr > 0) "ICR ≥ ${fmt2(b.covenantIcr)}x" else "Tanpa uji ICR, tetapi pinjaman bisa ditarik sewaktu-waktu",
                relationshipScore = (eng.credit.relationship[b.id] ?: 0.0).roundToInt(),
                style = b.style,
                incentive = b.incentive,
                tell = b.tell,
                estimatedChance = chance,
                weakestFactor = weakLabel,
                canApply = block == null,
                blockReason = block
            )
        }
    }

    private fun buildCreditApps(): List<CreditAppUi> =
        eng.credit.applications.asReversed().map { a ->
            val b = eng.credit.bank(a.bankId)!!
            val o = a.offer
            val tone = when (a.status) {
                AppStatus.OFFER, AppStatus.DISBURSED, AppStatus.ACCEPTED -> SemanticTone.GOOD_GREEN
                AppStatus.REJECTED, AppStatus.EXPIRED -> SemanticTone.DANGER_RED
                AppStatus.PROCESSING -> SemanticTone.PRIMARY_BLUE
                AppStatus.DECLINED -> SemanticTone.NEUTRAL_GRAY
            }
            val label = when (a.status) {
                AppStatus.PROCESSING -> "Sedang ditelaah"
                AppStatus.OFFER -> "Penawaran siap"
                AppStatus.REJECTED -> "Ditolak"
                AppStatus.ACCEPTED -> "Menunggu pencairan"
                AppStatus.DISBURSED -> "Sudah cair"
                AppStatus.DECLINED -> "Ditolak oleh Anda"
                AppStatus.EXPIRED -> "Kedaluwarsa"
            }
            val summary = if (o != null) {
                val cur = if (b.foreign) "jt RS (dicairkan dalam A$)" else "jt RS"
                val rTxt = if (o.spreadPct != null) "mengambang ${fmt2(o.ratePct)}% (acuan + ${fmt2(o.spreadPct)}%)" else "tetap ${fmt2(o.ratePct)}%"
                val grace = if (o.graceMonths > 0) ", grace ${o.graceMonths} bln" else ""
                val bul = if (o.bullet) ", pokok bullet saat jatuh tempo" else ""
                "Disetujui ${fmt(o.amount)} $cur · bunga $rTxt · tenor ${o.tenor} bln$grace$bul · provisi ${fmt2(o.feePct)}% · covenant ICR ≥ ${fmt2(o.covenantIcr)}x"
            } else ""
            CreditAppUi(
                id = a.id,
                bankName = b.name,
                banker = "${b.banker} (${b.bankerRole})",
                amountText = "${fmt(a.amount)} jt RS",
                tenorText = "${a.tenor} bulan",
                purposeText = a.purpose.label,
                structureText = "${if (a.floating) "Bunga mengambang" else "Bunga tetap"} · ${if (a.bullet) "Pokok jatuh tempo (bullet)" else "Amortisasi bulanan"}",
                statusLabel = label,
                statusTone = tone,
                stageText = a.stageText,
                officialReason = a.officialReason,
                informalNote = a.informalNote,
                offerSummary = summary,
                offerTerms = o?.extraTerms ?: emptyList(),
                hiddenTerms = if (a.reviewed) (o?.hiddenTerms ?: emptyList()) else emptyList(),
                reviewed = a.reviewed,
                canReview = a.status == AppStatus.OFFER && !a.reviewed && eng.ap >= 1,
                canAccept = a.status == AppStatus.OFFER && eng.ap >= 1,
                canDecline = a.status == AppStatus.OFFER,
                expiresText = if (a.status == AppStatus.OFFER) "Berlaku s.d. ${dateText(a.offerExpiresTurn)}" else ""
            )
        }

    private fun buildBiases(): List<String> {
        val list = mutableListOf<String>()
        if (eng.stuffingActive) list += "Ilusi Laba Akrual: Anda masih memakai bonus berbasis omzet; laba terlihat tinggi sementara piutang rapuh menumpuk."
        if (eng.deferralActive) list += "Diskon Hiperbolik: Perawatan mesin masih ditunda (${eng.deferredMonths} bulan) demi menghemat kas jangka pendek."
        if (eng.loans.any { it.id == "aurelia" && it.balance > 0 } && !eng.fxHedgeActive) {
            list += "Risiko Mata Uang Terbuka: Anda meminjam dalam A$ tanpa kontrak Forward Valas; jika Rial melemah, saldo utang RS membengkak."
        }
        if (eng.ethicsLoad >= 2.0) {
            list += "Bom Waktu Etika: Jejak jalan pintas (${eng.ethicsEvidences.size} bukti) meningkatkan risiko skandal saat saksi keluar atau pemilu."
        }
        if (eng.world.rivals.any { it.warningSignal.isNotBlank() }) {
            list += "Intelijen Pesaing AI: ${eng.world.rivals.first { it.warningSignal.isNotBlank() }.warningSignal}"
        }
        if (list.isEmpty()) list += "Disiplin Kas & Operasional: Insentif selaras dengan kas tertagih dan keandalan mesin."
        return list
    }

    private fun buildEventDialog(): EventDialogModel {
        val ev = eng.openEvents.firstOrNull { it.id == activeEventId } ?: eng.openEvents.firstOrNull()
        if (ev == null) {
            return EventDialogModel("none", "Tidak ada kejadian mendesak", "", "", emptyList())
        }
        val left = ev.deadlineTurn - eng.turn
        val dl = if (left <= 0) "Tenggat berakhir akhir giliran ini" else "Tenggat ${left + 1} giliran"
        return EventDialogModel(
            id = ev.id,
            title = "${ev.category}: ${ev.title}",
            contextDescription = "${ev.body} ($dl.)",
            earlyWarningNote = "Peringatan dini: ${ev.warning}",
            options = ev.options.filter { it.id != "z" }.map { o ->
                EventOptionItem(o.id, o.title, o.apCost, o.impact, o.advisor, toneOf(o.tone))
            },
            isCascadingChain = ev.category.contains("Rantai")
        )
    }

    private fun buildMapPieces(): List<MapPiece> {
        val base = mutableListOf(
            MapPiece("pc_pabrik_1", MapTokenType.PABRIK, "Pabrik Tenun Kalimas (4 lini + ${eng.extraLines} ekspansi)", "sundara", 0.56f, 0.52f,
                "Utilisasi ${(eng.utilization() * 100 * eng.lastProdFactor).roundToInt().coerceIn(20, 100)}%"),
            MapPiece("pc_gudang_1", MapTokenType.GUDANG, "Gudang Bahan & Benang Kalimas", "sundara", 0.62f, 0.58f,
                "DIO ${eng.dio().roundToInt()} hari"),
            MapPiece("pc_toko_1", MapTokenType.TOKO, "Jaringan Peritel Sundara", "sundara", 0.50f, 0.60f,
                "DSO ${eng.dso().roundToInt()} hari"),
            MapPiece("pc_kapal_1", MapTokenType.KAPAL, "Jalur Komponen Kestrel → Sundara", "kestrel", 0.72f, 0.34f,
                if (eng.dualSourcing) "Dual sourcing aktif" else "Pemasok tunggal"),
            MapPiece("pc_kapal_2", MapTokenType.KAPAL, "Kapal Kapas Tarsis → Sundara", "tarsis", 0.30f, 0.72f,
                "Indeks kapas ${fmt2(eng.commodities.cotton.index)}x")
        )
        eng.subsidiaries.values.filter { it.active && !it.nationalized }.forEach { sub ->
            val (nx, ny) = when (sub.spec.countryId) {
                "tarsis" -> 0.25f to 0.72f
                "kestrel" -> 0.78f to 0.24f
                "halvenia" -> 0.45f to 0.22f
                else -> 0.46f to 0.43f
            }
            base += MapPiece(
                id = "pc_sub_${sub.spec.countryId}",
                type = if (sub.spec.countryId in listOf("tarsis", "kestrel")) MapTokenType.PABRIK else MapTokenType.TOKO,
                label = sub.spec.title,
                countryId = sub.spec.countryId,
                normX = nx,
                normY = ny,
                statusNote = "Anak usaha aktif (${fmt1(sub.retainedForeign)} jt ${sub.spec.currencyCode})"
            )
        }
        return base
    }

    private fun buildTensions(): List<PairwiseTension> {
        val kstMeta = eng.foreignMeta.getValue("kestrel")
        val nrvMeta = eng.foreignMeta.getValue("norvaldia")
        val vlmMeta = eng.foreignMeta.getValue("velmora")
        return listOf(
            PairwiseTension(
                "Aurelia ↔ Kestrel", "Selat Kestrel & Kuota Komponen", kstMeta.tensionScore,
                "Tahap: Tarif Bea Masuk (${fmt1(kstMeta.tariffPct)}%) & Kuota Ekspor",
                if (eng.dualSourcing || eng.subsidiaries["kestrel"]?.active == true) "Anda terlindungi oleh dual sourcing / pabrik lokal Kestrel."
                else "Tanpa dual sourcing, kiriman suku cadang berisiko tertahan dan terkena tarif.",
                if (kstMeta.tensionScore >= 65) SemanticTone.DANGER_RED else SemanticTone.WARNING_YELLOW
            ),
            PairwiseTension(
                "Aurelia ↔ Velmora", "Sanksi Ekstrateritorial", vlmMeta.tensionScore,
                "Tahap: Sanksi Keuangan & Pelacakan Tier-2",
                if (eng.sanctionHit) "Anda telah terkena denda sanksi 600 jt RS." else "Sensor Kestrel memakai mineral Velmora; risiko denda sanksi sekunder.",
                SemanticTone.DANGER_RED
            ),
            PairwiseTension(
                "Halvenia ↔ Norvaldia", "Koridor Energi & Selat Norvaldia", nrvMeta.tensionScore,
                "Tahap: Gesekan Pelayaran & Bea Karbon CBAM",
                "Premi asuransi kargo dan harga minyak (${fmt1(eng.commodities.oil.price)} A$/barel) sensitif terhadap selat ini.",
                SemanticTone.WARNING_YELLOW
            )
        )
    }

    private fun buildCountries(date: String): List<Country> {
        val mToElection = eng.electionTurn - eng.turn
        val elTxt = when {
            mToElection > 0 -> "Pemilu dalam $mToElection giliran (${dateText(eng.electionTurn)})"
            mToElection == 0 -> "Pemilu berlangsung giliran ini"
            else -> "Pemilu selesai ${-mToElection} bln lalu"
        }
        fun fw(id: String) = eng.foreign.getValue(id)
        fun fm(id: String) = eng.foreignMeta.getValue(id)
        fun fInf(id: String): String { val x = fw(id).inflation; return "${fmt1(x - 0.5)}–${fmt1(x + 0.5)}%" }
        fun fPol(id: String): String = "${fmt2(fw(id).policyRate)}%"
        fun fGdp(id: String, base: Double): String = "${ signed(base + fw(id).gap * 25.0).replace("−", "-") }%"
        val pieces = buildMapPieces()
        return listOf(
            Country(
                id = "sundara", name = "Sundara", currencyCode = "RS",
                archetype = "Negara Berkembang Manufaktur (Negara Asal)",
                opportunity = "Basis Pabrik Kalimas; pasar domestik 50.000 rumah tangga; PPh Badan 22%",
                mainRisk = "Pemadaman listrik kawasan, siklus subsidi pemilu, dan fluktuasi Rial",
                centralBankStyle = "Aturan Taylor (target inflasi 3%, merespons kesenjangan output & kurs)",
                inflationRange = "${(eng.inflationAnnual - 1).roundToInt().coerceAtLeast(0)}–${(eng.inflationAnnual + 1).roundToInt()}%",
                policyRate = "${fmt2(eng.policyRate)}%",
                gdpGrowth = "${fmt1((eng.econ - 1.0) * 100.0 + 4.8)}%",
                tensionScore = if (mToElection in 0..3) 58 else 42,
                electionCountdown = elTxt,
                electricityReliability = "Sedang (risiko pemadaman bergilir)",
                climateRiskLabel = "Banjir musiman & El Niño",
                supplyChainRole = "Pabrik tenun utama, pasar ritel domestik",
                polygonPoints = listOf(0.48f to 0.44f, 0.66f to 0.42f, 0.70f to 0.62f, 0.52f to 0.66f, 0.44f to 0.56f),
                pieces = pieces.filter { it.countryId == "sundara" },
                originInfo = DataOriginInfo("Profil Makro Sundara", "Bunga ${fmt2(eng.policyRate)}% · Kurs ${fmt(eng.kurs)} RS/A$",
                    "Bank Sentral & BPS Sundara", date, "Simulasi 50.000 agen rumah tangga + 4 pesaing + aturan Taylor.", "", ConfidenceTier.MEDIUM),
                fxToRsText = "1,00 RS (Mata uang induk)",
                corpTaxText = "PPh 22% (Efektif grup ${fmt1(eng.effectiveTaxRate() * 100)}%)",
                tariffText = "Bea masuk domestik 0%",
                hasSubsidiary = true
            ),
            Country(
                id = "aurelia", name = "Aurelia", currencyCode = "A$",
                archetype = "Negara Maju Konsumen & Penerbit Mata Uang Cadangan",
                opportunity = "Pinjaman A$ berbunga rendah & pasar ekspor garmen",
                mainRisk = "Sanksi ekstrateritorial & klausul MAC saat Rial melemah",
                centralBankStyle = "Independen, mandat ganda",
                inflationRange = fInf("aurelia"), policyRate = fPol("aurelia"), gdpGrowth = fGdp("aurelia", 2.1),
                tensionScore = fm("aurelia").tensionScore, electionCountdown = "22 giliran lagi",
                electricityReliability = "99,8%", climateRiskLabel = "Rendah",
                supplyChainRole = "Kreditur A$ (Aurelia CitiTrust) & pengawas sanksi",
                polygonPoints = listOf(0.06f to 0.12f, 0.28f to 0.10f, 0.30f to 0.34f, 0.10f to 0.38f, 0.04f to 0.25f),
                pieces = emptyList(),
                originInfo = DataOriginInfo("Profil Makro Aurelia", "Kurs ${fmt(eng.kurs)} RS/A$", "Federal Reserve Aurelia", date, "Penerbit A$.", "", ConfidenceTier.HIGH),
                fxToRsText = "${fmt(fm("aurelia").fxToRs)} RS / A$",
                corpTaxText = "PPh ${fmt1(fm("aurelia").profile.corpTaxPct)}%",
                tariffText = "Tarif impor ${fmt1(fm("aurelia").tariffPct)}%"
            ),
            Country(
                id = "kestrel", name = "Kestrel", currencyCode = "KST",
                archetype = "Raksasa Manufaktur & Pemasok Mesin Tenun",
                opportunity = "Suku cadang mesin presisi & Pabrik Patungan Era 2",
                mainRisk = "Kuota ekspor komponen, hold-up pemasok tunggal, & perang tarif",
                centralBankStyle = "Menjaga kurs kompetitif untuk ekspor",
                inflationRange = fInf("kestrel"), policyRate = fPol("kestrel"), gdpGrowth = fGdp("kestrel", 4.6),
                tensionScore = fm("kestrel").tensionScore, electionCountdown = "Kongres 30 giliran",
                electricityReliability = "96%", climateRiskLabel = "Topan pesisir",
                supplyChainRole = "Pemasok Tier-1 sensor tenun & mesin pintal",
                polygonPoints = listOf(0.68f to 0.10f, 0.92f to 0.12f, 0.94f to 0.36f, 0.72f to 0.38f, 0.66f to 0.24f),
                pieces = pieces.filter { it.countryId == "kestrel" },
                originInfo = DataOriginInfo("Profil Makro Kestrel", "Pemasok komponen & anak usaha", "Bea Cukai Kestrel", date, "Pemasok mesin.", "", ConfidenceTier.MEDIUM),
                fxToRsText = "${fmt1(fm("kestrel").fxToRs)} RS / KST",
                corpTaxText = "PPh ${fmt1(fm("kestrel").profile.corpTaxPct)}% · WHT ${fmt1(fm("kestrel").profile.withholdingTaxPct)}%",
                tariffText = "Tarif ${fmt1(fm("kestrel").tariffPct)}%",
                hasSubsidiary = eng.subsidiaries["kestrel"]?.active == true
            ),
            Country(
                id = "tarsis", name = "Tarsis", currencyCode = "TRS",
                archetype = "Negara Agraris Pemasok Kapas & Buruh Murah",
                opportunity = "Kapas mentah & Pabrik Hulu Kapas Tarsis (Era 2)",
                mainRisk = "Kekeringan El Niño yang memicu gagal panen & tuntutan serikat berantai",
                centralBankStyle = "Reaktif terhadap inflasi pangan",
                inflationRange = fInf("tarsis"), policyRate = fPol("tarsis"), gdpGrowth = fGdp("tarsis", 5.5),
                tensionScore = fm("tarsis").tensionScore, electionCountdown = "16 giliran lagi",
                electricityReliability = "78%", climateRiskLabel = "Tinggi (El Niño)",
                supplyChainRole = "Pemasok utama kapas mentah",
                polygonPoints = listOf(0.14f to 0.60f, 0.38f to 0.58f, 0.40f to 0.86f, 0.16f to 0.88f, 0.10f to 0.74f),
                pieces = pieces.filter { it.countryId == "tarsis" },
                originInfo = DataOriginInfo("Profil Makro Tarsis", "Indeks kapas ${fmt2(eng.commodities.cotton.index)}x", "Bursa Pertanian Tarsis", date, "Sumber kapas.", "", ConfidenceTier.MEDIUM),
                fxToRsText = "${fmt1(fm("tarsis").fxToRs)} RS / TRS",
                corpTaxText = "PPh ${fmt1(fm("tarsis").profile.corpTaxPct)}% · WHT ${fmt1(fm("tarsis").profile.withholdingTaxPct)}%",
                tariffText = "Tarif ${fmt1(fm("tarsis").tariffPct)}%",
                hasSubsidiary = eng.subsidiaries["tarsis"]?.active == true
            ),
            Country(
                id = "halvenia", name = "Halvenia", currencyCode = "HLV",
                archetype = "Negara Maju Berstandar Lingkungan Tinggi (ESG & CBAM)",
                opportunity = "Pemasok kedua komponen (dual sourcing) & Cabang Garmen ESG Era 2",
                mainRisk = "Biaya tinggi & denda pajak karbon CBAM jika pabrik induk belum punya IPAL",
                centralBankStyle = "Konservatif anti-inflasi",
                inflationRange = fInf("halvenia"), policyRate = fPol("halvenia"), gdpGrowth = fGdp("halvenia", 1.5),
                tensionScore = fm("halvenia").tensionScore, electionCountdown = "28 giliran lagi",
                electricityReliability = "99,9%", climateRiskLabel = "Rendah",
                supplyChainRole = "Pemasok alternatif komponen mesin & pasar premium ESG",
                polygonPoints = listOf(0.34f to 0.08f, 0.54f to 0.08f, 0.56f to 0.28f, 0.36f to 0.30f),
                pieces = pieces.filter { it.countryId == "halvenia" },
                originInfo = DataOriginInfo("Profil Makro Halvenia", "Standar ESG & CBAM", "Institut Statistik Halvenia", date, "Pemasok alternatif & pasar premium.", "", ConfidenceTier.HIGH),
                fxToRsText = "${fmt(fm("halvenia").fxToRs)} RS / HLV",
                corpTaxText = "PPh ${fmt1(fm("halvenia").profile.corpTaxPct)}% · WHT ${fmt1(fm("halvenia").profile.withholdingTaxPct)}%",
                tariffText = "Tarif ${fmt1(fm("halvenia").tariffPct)}%",
                hasSubsidiary = eng.subsidiaries["halvenia"]?.active == true
            ),
            Country(
                id = "norvaldia", name = "Norvaldia", currencyCode = "NRV",
                archetype = "Eksportir Minyak dan Gas",
                opportunity = "Kontrak energi jangka panjang",
                mainRisk = "Ketegangan Selat Norvaldia mendongkrak harga minyak",
                centralBankStyle = "Mengikuti harga minyak",
                inflationRange = fInf("norvaldia"), policyRate = fPol("norvaldia"), gdpGrowth = fGdp("norvaldia", 3.2),
                tensionScore = fm("norvaldia").tensionScore, electionCountdown = "19 giliran lagi",
                electricityReliability = "91%", climateRiskLabel = "Sedang",
                supplyChainRole = "Pemasok energi kawasan",
                polygonPoints = listOf(0.56f to 0.10f, 0.66f to 0.10f, 0.65f to 0.32f, 0.55f to 0.32f),
                pieces = emptyList(),
                originInfo = DataOriginInfo("Profil Makro Norvaldia", "Minyak ${fmt1(eng.commodities.oil.price)} A$/barel", "Kementerian Energi", date, "Eksportir minyak.", "", ConfidenceTier.LOW),
                fxToRsText = "${fmt1(fm("norvaldia").fxToRs)} RS / NRV",
                corpTaxText = "PPh ${fmt1(fm("norvaldia").profile.corpTaxPct)}%",
                tariffText = "Tarif ${fmt1(fm("norvaldia").tariffPct)}%"
            ),
            Country(
                id = "meridia", name = "Meridia", currencyCode = "MRD",
                archetype = "Pusat Keuangan, Holding SPV, & Pelabuhan Bebas",
                opportunity = "Holding SPV PPh 8,5%, efisiensi Transfer Pricing, & pemotongan pajak dividen 50%",
                mainRisk = "Pemeriksaan pajak lintas batas jika memakai Transfer Pricing Agresif",
                centralBankStyle = "Dewan mata uang terikat A$",
                inflationRange = fInf("meridia"), policyRate = fPol("meridia"), gdpGrowth = fGdp("meridia", 3.6),
                tensionScore = fm("meridia").tensionScore, electionCountdown = "38 giliran lagi",
                electricityReliability = "99,9%", climateRiskLabel = "Kenaikan muka laut",
                supplyChainRole = "Pelabuhan transshipment & pusat tresuri multinasional",
                polygonPoints = listOf(0.41f to 0.36f, 0.51f to 0.36f, 0.51f to 0.43f, 0.41f to 0.43f),
                pieces = pieces.filter { it.countryId == "meridia" },
                originInfo = DataOriginInfo("Profil Makro Meridia", "PPh Badan 8,5% · WHT 0%", "Otoritas Moneter Meridia", date, "Hub keuangan & SPV.", "", ConfidenceTier.HIGH),
                fxToRsText = "${fmt(fm("meridia").fxToRs)} RS / MRD",
                corpTaxText = "PPh 8,5% · WHT 0%",
                tariffText = "Pelabuhan bebas (0%)",
                hasSubsidiary = eng.subsidiaries["meridia"]?.active == true
            ),
            Country(
                id = "velmora", name = "Velmora", currencyCode = "VLM",
                archetype = "Negara Kaya Mineral dalam Sanksi Aurelia",
                opportunity = "Mineral industri & kain murah",
                mainRisk = "Sumber mineral Tier-2 pada sensor Kestrel & sumber kain dumping",
                centralBankStyle = "Kontrol modal ketat",
                inflationRange = fInf("velmora"), policyRate = fPol("velmora"), gdpGrowth = fGdp("velmora", 1.2),
                tensionScore = fm("velmora").tensionScore, electionCountdown = "Tidak terjadwal",
                electricityReliability = "82%", climateRiskLabel = "Kekeringan",
                supplyChainRole = "Pemasok Tier-2 mineral & pesaing impor murah",
                polygonPoints = listOf(0.74f to 0.54f, 0.95f to 0.52f, 0.96f to 0.84f, 0.72f to 0.86f, 0.68f to 0.68f),
                pieces = emptyList(),
                originInfo = DataOriginInfo("Profil Makro Velmora", "Dalam sanksi Aurelia", "Estimasi Intelijen", date, "Risiko sanksi sekunder.", "", ConfidenceTier.LOW),
                fxToRsText = "${fmt1(fm("velmora").fxToRs)} RS / VLM",
                corpTaxText = "PPh ${fmt1(fm("velmora").profile.corpTaxPct)}%",
                tariffText = "Tarif ${fmt1(fm("velmora").tariffPct)}%"
            )
        )
    }

    companion object {
        const val DEFAULT_SEED = 203303L
    }
}
