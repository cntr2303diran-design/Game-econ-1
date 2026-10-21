package com.example.data

import com.example.engine.Action
import com.example.engine.FactoryScheme
import com.example.engine.KapitalEngine
import com.example.engine.KapitalEngine.Companion.dateText
import com.example.engine.KapitalEngine.Companion.fmt
import com.example.engine.KapitalEngine.Companion.fmt1
import com.example.engine.KapitalEngine.Companion.fmt2
import com.example.engine.KapitalEngine.Companion.signed
import com.example.engine.SalesScheme
import com.example.engine.Tone
import com.example.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Penyimpanan permainan: seed + daftar tindakan (satu baris per tindakan). */
interface SaveStore {
    fun read(): String?
    fun write(text: String)
}

/** Status tampilan yang bukan bagian dari simulasi. */
private data class UiFlags(
    val isDarkTheme: Boolean? = null,
    val activeMapLayer: MapLayerFilter = MapLayerFilter.PELAYARAN,
    val showEventDialog: Boolean = false,
    val activeEventId: String? = null,
    val eventOutcome: String? = null,
    val showMonthlyReport: Boolean = false,
    val inspectedOrigin: DataOriginInfo? = null,
    val selectedCountry: Country? = null,
    val counterfactualText: String? = null
)

class GameRepository(
    private val store: SaveStore? = null,
    seedOverride: Long? = null
) {
    private var seed: Long
    private val log = mutableListOf<String>()
    private var engine: KapitalEngine
    private var ui = UiFlags()

    init {
        val saved = store?.read()
        val lines = saved?.lines()?.filter { it.isNotBlank() }.orEmpty()
        val savedSeed = lines.firstOrNull()?.removePrefix("SEED:")?.toLongOrNull()
        if (seedOverride == null && savedSeed != null) {
            seed = savedSeed
            engine = KapitalEngine(seed)
            for (l in lines.drop(1)) {
                val a = Action.decode(l) ?: continue
                if (engine.apply(a)) log += l
            }
        } else {
            seed = seedOverride ?: System.currentTimeMillis()
            engine = KapitalEngine(seed)
            persist()
        }
    }

    private val _gameState = MutableStateFlow(buildState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    // ------------------------------------------------------------------
    // Perintah dari UI
    // ------------------------------------------------------------------
    private fun perform(action: Action): Boolean {
        val ok = engine.apply(action)
        if (ok) {
            log += action.encode()
            persist()
        }
        publish()
        return ok
    }

    private fun persist() {
        store?.write((listOf("SEED:$seed") + log).joinToString("\n"))
    }

    private fun publish() {
        _gameState.value = buildState()
    }

    private fun setUi(f: (UiFlags) -> UiFlags) {
        ui = f(ui)
        publish()
    }

    fun toggleTheme(currentlyDark: Boolean) = setUi { it.copy(isDarkTheme = !currentlyDark) }
    fun setMapLayer(layer: MapLayerFilter) = setUi { it.copy(activeMapLayer = layer) }
    fun selectCountryForBottomSheet(country: Country?) = setUi { it.copy(selectedCountry = country) }
    fun inspectDataOrigin(origin: DataOriginInfo?) = setUi { it.copy(inspectedOrigin = origin) }
    fun openEventDialog(show: Boolean, id: String? = null) = setUi { it.copy(showEventDialog = show, activeEventId = if (show) id else null) }
    fun clearEventOutcome() = setUi { it.copy(eventOutcome = null) }
    fun dismissMonthlyReport() = setUi { it.copy(showMonthlyReport = false) }
    fun showMonthlyReport() = setUi { it.copy(showMonthlyReport = true) }
    fun clearCounterfactual() = setUi { it.copy(counterfactualText = null) }

    fun setSalesScheme(id: String) {
        SalesScheme.entries.firstOrNull { it.name == id }?.let { perform(Action.SetSalesScheme(it)) }
    }

    fun setFactoryScheme(id: String) {
        FactoryScheme.entries.firstOrNull { it.name == id }?.let { perform(Action.SetFactoryScheme(it)) }
    }

    fun adjustPrice(deltaPct: Int) = perform(Action.AdjustPrice(deltaPct))
    fun applyCredit(bankId: String, amount: Int, tenor: Int, purpose: String, floating: Boolean, bullet: Boolean) =
        perform(Action.ApplyCredit(bankId, amount, tenor, purpose, floating, bullet))
    fun reviewOffer(appId: String) = perform(Action.ReviewOffer(appId))
    fun acceptOffer(appId: String) = perform(Action.AcceptOffer(appId))
    fun declineOffer(appId: String) = perform(Action.DeclineOffer(appId))
    fun buyStock(id: String, lots: Int) = perform(Action.BuyStock(id, lots))
    fun sellStock(id: String, lots: Int) = perform(Action.SellStock(id, lots))
    fun crossCheckNewsItem(newsId: String) = perform(Action.CrossCheck(newsId))
    fun auditExecutive(id: String) = perform(Action.Audit(id))
    fun confrontExecutive(id: String) = perform(Action.Confront(id))
    fun fireExecutive(id: String) = perform(Action.Fire(id))

    fun contractAction(contractId: String, actionId: String) {
        when (actionId) {
            "review" -> perform(Action.ReviewContract(contractId))
            "dual" -> perform(Action.DualSourcing)
            "hedge" -> perform(Action.HedgeCotton)
            "cure" -> perform(Action.EquityCure)
        }
    }

    fun submitDecisionJournal(
        decisionId: String,
        optionId: String,
        strategicReason: String,
        numericPrediction: String,
        confidencePct: Int,
        predictedCash: Double,
        failureScenario: String
    ): Boolean = perform(
        Action.Journal(decisionId, optionId, confidencePct, predictedCash, strategicReason, numericPrediction, failureScenario)
    )

    fun invest(typeId: String) = perform(Action.Invest(typeId))

    /** Aksi korporasi/multinasional (anak usaha, repatriasi, transfer pricing, valas, obligasi, IPO, dst.) dari kode simpanan. */
    fun corporateAction(code: String): Boolean {
        val a = try { Action.decode(code) } catch (ex: Exception) { null } ?: return false
        return perform(a)
    }

    fun rejectDecision(decisionId: String) = perform(Action.RejectProposal(decisionId))

    fun resolveEventOption(option: EventOptionItem) {
        val id = ui.activeEventId ?: engine.openEvents.firstOrNull()?.id ?: return
        ui = ui.copy(showEventDialog = false, activeEventId = null)
        if (perform(Action.EventChoice(id, option.id))) {
            ui = ui.copy(eventOutcome = engine.lastEventResult)
            publish()
        }
    }

    fun advanceTurn() {
        if (engine.gameOverReason != null) return
        ui = ui.copy(showMonthlyReport = true)
        perform(Action.EndTurn)
    }

    fun newGame() {
        seed = System.currentTimeMillis()
        log.clear()
        engine = KapitalEngine(seed)
        ui = UiFlags(isDarkTheme = ui.isDarkTheme)
        persist()
        publish()
    }

    /**
     * "Bagaimana Jika": mainkan ulang seluruh riwayat dengan seed yang sama, tetapi tanpa
     * tindakan nomor [actionIndex]. Dunia (cuaca, pemilu, rumor) identik; hanya pilihan Anda berbeda.
     */
    fun runCounterfactual(actionIndex: Int) {
        val alt = KapitalEngine(seed)
        log.forEachIndexed { i, line ->
            if (i == actionIndex) return@forEachIndexed
            Action.decode(line)?.let { alt.apply(it) }
        }
        val skipped = Action.decode(log.getOrNull(actionIndex) ?: "")
        val label = when (skipped) {
            is Action.Journal -> "tanpa keputusan \"${skipped.decisionId.removePrefix("dec_")}\""
            is Action.EventChoice -> "tanpa pilihan Anda pada salah satu kartu kejadian"
            is Action.RejectProposal -> "tanpa menolak proposal pemintalan (proposal dibiarkan menggantung)"
            else -> "tanpa tindakan tersebut"
        }
        val text = buildString {
            append("Seandainya Anda bermain $label, ")
            if (alt.gameOverReason != null && engine.gameOverReason == null) {
                append("perusahaan sudah berakhir: ${alt.gameOverReason}")
            } else {
                append("kas sekarang ${fmt(alt.cash)} jt RS (aktual ${fmt(engine.cash)}, selisih ${signed(alt.cash - engine.cash)}), ")
                append("ekuitas ${fmt(alt.equity)} jt RS (aktual ${fmt(engine.equity)}).")
                if (alt.gameOverReason != null) append(" Di cabang itu permainan juga berakhir: ${alt.gameOverReason}")
            }
        }
        setUi { it.copy(counterfactualText = text) }
    }

    // ------------------------------------------------------------------
    // Pemetaan mesin → model tampilan
    // ------------------------------------------------------------------
    private fun tone(t: Tone): SemanticTone = when (t) {
        Tone.BAD -> SemanticTone.DANGER_RED
        Tone.WARN -> SemanticTone.WARNING_YELLOW
        Tone.GOOD -> SemanticTone.GOOD_GREEN
        Tone.INFO -> SemanticTone.PRIMARY_BLUE
        Tone.NEUTRAL -> SemanticTone.NEUTRAL_GRAY
    }

    private fun conf(level: Int): ConfidenceTier = when {
        level <= 0 -> ConfidenceTier.LOW
        level == 1 -> ConfidenceTier.MEDIUM
        else -> ConfidenceTier.HIGH
    }

    private fun origin(title: String, value: String, source: String, date: String, method: String, note: String, c: ConfidenceTier) =
        DataOriginInfo(title, value, source, date, method, note, c)

    private fun buildState(): GameState {
        val e = engine
        val turn = e.turn
        val closed = dateText(turn - 1)
        val prevClosed = dateText(turn - 2)
        StaticWorldData.setDate(dateText(turn), closed, prevClosed)

        // ---- Covenant ----
        val th = e.covenantThreshold()
        val dist = e.covenantDistancePct()
        val k = (3 - turn % 3) % 3
        val covTitle = when {
            th <= 0.0 -> "Tidak ada covenant aktif"
            dist < 0 -> "Covenant diperkirakan GAGAL (${dist.toInt()}%)"
            else -> "Jarak ke covenant ${dist.toInt()}%"
        }
        val covSub = buildString {
            append(if (k == 0) "Uji kuartalan di akhir giliran ini" else "Uji kuartalan dalam $k giliran")
            if (e.covenantBreaches > 0) append(" · sudah gagal ${e.covenantBreaches}× berturut-turut")
        }
        val covTone = when {
            th <= 0.0 -> SemanticTone.GOOD_GREEN
            dist < 0 -> SemanticTone.DANGER_RED
            dist < 15 -> SemanticTone.WARNING_YELLOW
            else -> SemanticTone.GOOD_GREEN
        }
        val covOrigin = origin("Jarak ke covenant", "ICR proyeksi ${fmt2(e.projectedIcr())}x vs batas ${fmt2(th)}x",
            "Divisi Treasury (perkiraan bulan berjalan)", "Proyeksi ${dateText(turn)}",
            "ICR = EBIT 3 bulan / beban bunga 3 bulan. Bulan berjalan memakai perkiraan tanpa kejadian acak. Juga diuji: utang bersih/EBITDA ≤ 3,5x.",
            "Perkiraan tidak memasukkan mogok, kerusakan mesin, atau kredit macet yang belum terjadi.", ConfidenceTier.MEDIUM)

        // ---- Indikator ----
        val burn = e.monthlyBurn()
        val ccc = e.dso() + e.dio() - e.dpo()
        val util = (88.0 * e.lastProdFactor).toInt()
        val inflLo = e.inflationAnnual - 1.0
        val indicators = listOf(
            Indicator("ind_kas", "Kas", "${fmt(e.cash)} jt RS",
                if (burn > 0) "−${fmt(burn)}/bulan" else "+${fmt(-burn)}/bulan",
                if (burn > 0) SemanticTone.DANGER_RED else SemanticTone.GOOD_GREEN,
                "Akhir $closed", "Rekening bank", ConfidenceTier.HIGH,
                origin("Saldo kas", "${fmt(e.cash)} jt RS", "Mutasi rekening bank", "Akhir $closed",
                    "Kas awal + arus kas operasi + investasi + pendanaan. Rata-rata perubahan 3 bulan terakhir: ${signed(-burn)} jt RS/bulan.",
                    "Saldo bank akurat, kecuali ada window dressing menjelang tanggal laporan.", ConfidenceTier.HIGH)),
            Indicator("ind_utilisasi", "Utilisasi pabrik", "$util%",
                if (e.lastProdFactor < 1.0) "produksi terganggu" else "normal",
                if (e.lastProdFactor < 1.0) SemanticTone.DANGER_RED else SemanticTone.GOOD_GREEN,
                "Laporan $closed", "Kepala Pabrik", ConfidenceTier.MEDIUM,
                origin("Utilisasi pabrik", "$util%", "Laporan Kepala Pabrik", closed,
                    "Output aktual dibagi kapasitas terpasang.",
                    "Angka utilisasi tidak memberi tahu kondisi mesin. Bandingkan belanja perawatan dengan kebutuhan teknis.", ConfidenceTier.MEDIUM)),
            Indicator("ind_ccc", "Siklus kas", "${ccc.toInt()} hari",
                "DSO ${e.dso().toInt()} · DIO ${e.dio().toInt()} · DPO ${e.dpo().toInt()}",
                if (ccc > 90) SemanticTone.WARNING_YELLOW else SemanticTone.GOOD_GREEN,
                "Akhir $closed", "Akuntansi CFO", ConfidenceTier.MEDIUM,
                origin("Siklus konversi kas", "${ccc.toInt()} hari", "Laporan modal kerja CFO", closed,
                    "CCC = DSO + DIO − DPO = ${e.dso().toInt()} + ${e.dio().toInt()} − ${e.dpo().toInt()}.",
                    "DSO yang naik bersamaan dengan omzet bisa berarti penjualan kredit ke pelanggan berisiko.", ConfidenceTier.MEDIUM)),
            Indicator("ind_inflasi", "Inflasi Sundara", "${fmt1(inflLo)}–${fmt1(inflLo + 2.0)}%",
                "data ${dateText(turn - 2)}", SemanticTone.NEUTRAL_GRAY,
                "terlambat 2 bln", "Biro Statistik Sundara", ConfidenceTier.LOW,
                origin("Inflasi tahunan Sundara", "${fmt1(inflLo)}–${fmt1(inflLo + 2.0)}%", "Biro Statistik Sundara",
                    "${dateText(turn - 2)} (terlambat 2 giliran)", "Perubahan indeks harga konsumen tahun ke tahun.",
                    "Pemerintah politis cenderung mempercantik angka menjelang pemilu.", ConfidenceTier.LOW))
        )

        // ---- Proyeksi kas (FanChart) ----
        val hist = e.cashHist.takeLast(6).map { it.toFloat() }
        val median = (1..6).map { (e.cash - burn * it).toFloat() }
        val base50 = max(300.0, abs(e.cash) * 0.05)
        val s50 = (1..6).map { (base50 * sqrt(it.toDouble())).toFloat() }
        val s90 = s50.map { it * 2f }

        // ---- Keputusan ----
        val decisions = e.pendingDecisions().map { d ->
            Decision(
                id = d.id, title = d.title, subtitle = d.subtitle, apCost = d.apCost, badgeText = d.badge,
                tone = tone(d.tone), assetSharePct = d.assetSharePct, defaultContextNote = d.context,
                opensEventDialog = d.isEvent,
                options = if (d.id == "dec_termsheet") listOf(
                    "cnt_ts_1" to "Bank Pembangunan Sundara · ${fmt(e.termSheetAmount())} jt RS · bunga tetap ${fmt2(e.termSheetFixedRate())}% · cicilan 48 bulan",
                    "cnt_ts_2" to "Aurelia Global CitiTrust · 5,2% dalam A$ · bullet 36 bulan"
                ) else emptyList(),
                canReject = d.id == "dec_pemintalan"
            )
        }

        // ---- Koran pagi ----
        val outletSource = mapOf(
            "BISNIS" to "Harian Bisnis", "PRO_PEMERINTAH" to "Suara Sundara", "OPOSISI" to "Mimbar Rakyat",
            "SENSASIONAL" to "Kilat Sore", "INDEPENDEN" to "Lensa Independen"
        )
        val newsItems = e.news.map { n ->
            val c = when {
                n.isRumor -> 0
                n.crossChecked -> n.confidence + 1
                else -> n.confidence
            }
            NewsItem(
                id = n.id, mediaSlant = MediaSlant.valueOf(n.slant), headline = n.headline, summary = n.summary,
                dateLabel = "1 ${dateText(turn)}${if (n.isRumor) " · Kolom rumor" else ""}",
                confidence = conf(c), isRumor = n.isRumor, crossChecked = n.crossChecked,
                originInfo = origin("Berita: ${outletSource[n.slant]}", n.headline, outletSource[n.slant] ?: n.slant,
                    "1 ${dateText(turn)}",
                    if (n.crossChecked) "Sudah diperiksa silang dengan sumber lain." else "Belum diperiksa silang.",
                    when (n.slant) {
                        "INDEPENDEN" -> "Sumber paling independen, tetapi tetap bisa keliru."
                        "PRO_PEMERINTAH" -> "Cenderung memperhalus kabar buruk demi pemerintah."
                        "OPOSISI" -> "Cenderung membesar-besarkan kabar buruk."
                        "SENSASIONAL" -> "Sering ditunggangi spekulan atau short-seller."
                        else -> "Akurat soal data pasar, tetapi pengiklannya punya kepentingan."
                    }, conf(c))
            )
        }
        val featured = newsItems.firstOrNull()

        // ---- Negara dan pasar (Sundara dan kurs dinamis) ----
        val monthsToElection = e.electionTurn - turn
        val countries = StaticWorldData.countries().map { c ->
            if (c.id == "sundara") c.copy(
                inflationRange = "${fmt1(inflLo)}–${fmt1(inflLo + 2.0)}%",
                policyRate = "${fmt2(e.policyRate)}%",
                electionCountdown = if (monthsToElection > 0) "$monthsToElection bulan lagi" else "Selesai (${dateText(e.electionTurn)})"
            ) else e.foreign[c.id]?.let { f ->
                // Perkiraan statistik asing punya ketidakpastian ±0,6 pp (imperfect information)
                val lo = f.inflation - 0.6
                c.copy(
                    inflationRange = "${fmt1(max(0.0, lo))}–${fmt1(max(0.0, lo) + 1.2)}%",
                    policyRate = "${fmt2(f.policyRate)}%"
                )
            } ?: c
        }
        val markets = StaticWorldData.marketInstruments().map { m ->
            if (m.id == "mkt_kurs") {
                val kh = e.kursHist.takeLast(6)
                val ch = (kh.last() / kh[kh.size - 2] - 1.0) * 100.0
                m.copy(
                    priceText = "${fmt(e.kurs)} RS",
                    changeText = if (ch >= 0) "Rial melemah ${fmt1(ch)}%" else "Rial menguat ${fmt1(-ch)}%",
                    tone = if (ch >= 0) SemanticTone.DANGER_RED else SemanticTone.GOOD_GREEN,
                    sparklineHistory = kh.map { it.toFloat() },
                    fanProjectionMedian = (1..4).map { (e.kurs * (1 + 0.003 * it)).toFloat() },
                    fanProjectionSpread = (1..4).map { (e.kurs * 0.02 * sqrt(it.toDouble()) * if (monthsToElection in 0..4) 3 else 1).toFloat() },
                    originInfo = m.originInfo.copy(displayedValue = "${fmt(e.kurs)} RS per 1 A$", dataDate = dateText(turn))
                )
            } else {
                val ser = when (m.id) {
                    "mkt_kapas" -> e.commodities.cotton
                    "mkt_minyak" -> e.commodities.oil
                    "mkt_baja" -> e.commodities.steel
                    else -> null
                }
                if (ser == null) m else {
                    val h = ser.history.toList()
                    val ch = (h.last() / h[h.size - 2] - 1.0) * 100.0
                    val unit = when (m.id) { "mkt_kapas" -> "A$/ton"; "mkt_minyak" -> "A$/bbl"; else -> "A$/ton" }
                    val vol = ser.sigmaMonthly
                    m.copy(
                        priceText = "${fmt1(ser.price)} $unit",
                        changeText = (if (ch >= 0) "+" else "") + "${fmt1(ch)}% bulan ini",
                        tone = if (abs(ch) > 4.0) SemanticTone.DANGER_RED else if (abs(ch) > 2.0) SemanticTone.WARNING_YELLOW else SemanticTone.GOOD_GREEN,
                        sparklineHistory = h.map { it.toFloat() },
                        fanProjectionMedian = (1..4).map { (ser.base * Math.exp(ser.logDev * Math.pow(ser.rho, it.toDouble()))).toFloat() },
                        fanProjectionSpread = (1..4).map { (ser.price * vol * 1.65 * sqrt(it.toDouble())).toFloat() },
                        originInfo = m.originInfo.copy(displayedValue = "${fmt1(ser.price)} $unit (${if (ch >= 0) "+" else ""}${fmt1(ch)}%)", dataDate = dateText(turn),
                            calculationMethod = "Proses mean-reverting (kecepatan ${fmt2(ser.rho)}/bulan) dengan guncangan pasokan dan permintaan global. Pita = 1,65 simpangan baku.")
                    )
                }
            }
        }

        // ---- Investasi dan ekspansi ----
        val investItems = KapitalEngine.INVEST.map { sp ->
            val block = e.investBlock(sp.id)
            val running = e.projectsInv.firstOrNull { it.type == sp.id && !it.done }
            val count = e.investCount(sp.id)
            InvestItem(sp.id, sp.title, sp.desc, "${fmt(e.investCost(sp))} jt RS", "${sp.months} bulan", sp.benefit, sp.ap, block == null, block,
                when {
                    running != null -> "Berjalan: ${fmt(running.spent)} dari ${fmt(running.totalCost)} jt RS dibayar"
                    count > 0 -> "Selesai ($count)"
                    else -> "Belum dimulai"
                })
        }
        val investSummary = listOf(
            "Kapasitas" to "${fmt1(e.capacityIdx() * 100)}% dari basis",
            "Permintaan" to "${fmt1(e.demandIdx() * 100)}% dari basis",
            "Utilisasi" to "${fmt1(e.utilization() * 100)}% ${if (e.utilization() > 1.0) "(pesanan tertolak, tambah kapasitas)" else "(ada ruang)"}",
            "Lini tambahan" to "${e.extraLines} dari 3",
            "Pekerja produksi" to "${e.headcountProd()}"
        ) + e.subsidiaries.values.map { sb ->
            "Anak usaha ${sb.spec.countryName}" to when {
                sb.nationalized -> "Dinasionalisasi"
                sb.active -> "Aktif ${sb.monthsActive} bln; laba kumulatif ${fmt(sb.cumulativeTranslatedNiRs)} jt RS"
                else -> "Dalam pembangunan"
            }
        }

        // ---- Laporan keuangan ----
        val inc = e.lastIncome
        val pinc = e.prevIncome
        fun iv(m: Map<String, Double>, key: String) = m[key] ?: 0.0
        fun money(x: Double, negative: Boolean = false) =
            if (negative) "(${fmt(x)})" else fmt(x)
        val stmtOrigin = origin("Laporan keuangan", "Bulan $closed", "Akuntansi internal (Ratna Pramudita)", closed,
            "Disusun otomatis dari transaksi dengan basis akrual. Neraca selalu seimbang (aset = liabilitas + ekuitas).",
            "Laporan akurat atas transaksi yang tercatat; tidak menunjukkan piutang yang berisiko macet.", ConfidenceTier.HIGH)
        fun row(label: String, key: String, note: String, negative: Boolean = false, total: Boolean = false, t: SemanticTone = SemanticTone.NEUTRAL_GRAY) =
            FinancialRowItem(label, money(iv(inc, key), negative), money(iv(pinc, key), negative), note, total, t, stmtOrigin)
        val gp = iv(inc, "revenue") - iv(inc, "cogs")
        val pgp = iv(pinc, "revenue") - iv(pinc, "cogs")
        val ebitda = iv(inc, "ebit") + iv(inc, "dep")
        val pebitda = iv(pinc, "ebit") + iv(pinc, "dep")
        val staff = e.headcountStaff(); val prod = e.headcountProd()
        val salaryPer = if (staff > 0) iv(inc, "op_salary") / staff else 0.0
        val laborPer = if (prod > 0) iv(inc, "cogs_labor") / prod else 0.0
        fun sub(label: String, cur: Double, prior: Double, note: String, negative: Boolean = true, tone: SemanticTone = SemanticTone.NEUTRAL_GRAY) =
            FinancialRowItem("    $label", money(cur, negative && cur != 0.0), money(prior, negative && prior != 0.0), note, false, tone, stmtOrigin)
        fun optRow(label: String, key: String, note: String, negative: Boolean = true): List<FinancialRowItem> =
            if (iv(inc, key) != 0.0 || iv(pinc, key) != 0.0) listOf(sub(label, iv(inc, key), iv(pinc, key), note, negative)) else emptyList()
        val pebt = iv(pinc, "ebit") - iv(pinc, "interest") + iv(pinc, "intinc")
        val ebt = iv(inc, "ebit") - iv(inc, "interest") + iv(inc, "intinc")
        val incomeRows = listOf(
            row("Pendapatan (akrual)", "revenue", "Termasuk penjualan kredit yang kasnya belum masuk", total = true),
            row("Harga pokok penjualan", "cogs", "Rasio ${fmt1(iv(inc, "cogs") / max(1.0, iv(inc, "revenue")) * 100)}% dari pendapatan", negative = true),
            sub("Bahan baku (kapas, benang)", iv(inc, "cogs_mat"), iv(pinc, "cogs_mat"), "Mengikuti indeks harga kapas ${fmt2(e.commodities.cotton.index)}"),
            sub("Energi dan utilitas pabrik", iv(inc, "cogs_energy"), iv(pinc, "cogs_energy"), "Listrik, uap, bahan bakar"),
            sub("Tenaga kerja langsung", iv(inc, "cogs_labor"), iv(pinc, "cogs_labor"), "$prod pekerja produksi · rata-rata ${fmt1(laborPer)} jt RS per orang per bulan"),
            sub("Overhead pabrik", iv(inc, "cogs_oh"), iv(pinc, "cogs_oh"), "Suku cadang habis pakai, gudang, pengiriman masuk"),
            FinancialRowItem("Laba kotor", money(gp), money(pgp), "Margin ${fmt1(gp / max(1.0, iv(inc, "revenue")) * 100)}%", true, SemanticTone.NEUTRAL_GRAY, stmtOrigin),
            row("Beban operasional", "opex", "Total beban usaha di luar HPP dan perawatan", negative = true),
            sub("Gaji staf dan manajemen", iv(inc, "op_salary"), iv(pinc, "op_salary"), "$staff karyawan · rata-rata ${fmt1(salaryPer)} jt RS per orang per bulan (termasuk kenaikan upah yang disepakati)"),
            sub("Administrasi dan kantor", iv(inc, "op_admin"), iv(pinc, "op_admin"), "Sewa kantor, IT, perjalanan, profesional"),
            sub("Pemasaran dan distribusi", iv(inc, "op_mkt"), iv(pinc, "op_mkt"), "Tim penjualan, pengiriman keluar, promosi"),
            sub("Utilitas kantor dan gudang", iv(inc, "op_util"), iv(pinc, "op_util"), "Listrik, air, komunikasi"),
            sub("Asuransi dan pajak bumi", iv(inc, "op_ins"), iv(pinc, "op_ins"), "Polis kebakaran dan pajak properti"),
            sub("Beban lain-lain", iv(inc, "op_other"), iv(pinc, "op_other"), "Perlengkapan, perizinan, iuran asosiasi")
        ) + optRow("Operasi lini dan fasilitas tambahan", "op_lines", "Tenaga dan utilitas dari ekspansi") +
            optRow("Program investasi (R&D, pemasaran)", "op_proj", "Dibebankan sebagai biaya, bukan aset") +
            optRow("Biaya satu kali (audit, hukum, pesangon, provisi, denda)", "op_oneoff", "Muncul karena keputusan atau kejadian bulan ini") +
            optRow("Kerugian persediaan", "op_loss", "Banjir atau kerusakan stok") +
            optRow("Pendapatan lain (asuransi, insentif, pemulihan)", "op_gain", "Mengurangi beban", negative = false) + listOf(
            row("Perawatan mesin", "maint", "Bandingkan dengan kebutuhan teknis ±${fmt(e.sc.baseMaint * e.costIndex)} jt RS", negative = true),
            row("Kredit macet dihapusbukukan", "baddebt", "Piutang berumur 6 bulan yang gagal ditagih", negative = true,
                t = if (iv(inc, "baddebt") > 0) SemanticTone.DANGER_RED else SemanticTone.NEUTRAL_GRAY),
            FinancialRowItem("EBITDA", money(ebitda), money(pebitda), "Acuan rasio utang bersih/EBITDA", true, SemanticTone.NEUTRAL_GRAY, stmtOrigin),
            row("Penyusutan", "dep", "Mesin dan bangunan (0,81% per bulan dari nilai buku)", negative = true),
            row("EBIT (laba operasi)", "ebit", "Acuan covenant ICR", total = true),
            row("Beban bunga", "interest", "Mengikuti suku bunga dan saldo utang", negative = true, t = SemanticTone.DANGER_RED),
            row("Pendapatan bunga deposito", "intinc", "Bunga atas kas mengendap"),
            FinancialRowItem("Laba sebelum pajak", money(ebt), money(pebt), "Dasar pajak sebelum kompensasi rugi", true, SemanticTone.NEUTRAL_GRAY, stmtOrigin),
            row("Pajak penghasilan badan", "tax", "22%, setelah kompensasi rugi fiskal ${fmt(e.taxLossCarryforward())} jt RS", negative = true),
            row("Laba bersih", "ni", "Laba di atas kertas; cek arus kas", total = true,
                t = if (iv(inc, "ni") >= 0) SemanticTone.GOOD_GREEN else SemanticTone.DANGER_RED)
        )
        val bal = e.balanceSnapshot()
        val pbal = e.prevBalance
        fun bv(m: Map<String, Double>, k: String) = m[k] ?: 0.0
        fun brow(label: String, key: String, note: String, total: Boolean = false, t: SemanticTone = SemanticTone.NEUTRAL_GRAY) =
            FinancialRowItem(label, money(bal[key] ?: 0.0), money(pbal[key] ?: 0.0), note, total, t, stmtOrigin)
        fun bsub(label: String, key: String, note: String) =
            FinancialRowItem("    $label", money(bv(bal, key)), money(bv(pbal, key)), note, false, SemanticTone.NEUTRAL_GRAY, stmtOrigin)
        fun bsum(label: String, cur: Double, prior: Double, note: String, t: SemanticTone = SemanticTone.NEUTRAL_GRAY) =
            FinancialRowItem(label, money(cur), money(prior), note, true, t, stmtOrigin)
        val curA = bv(bal, "cash") + bv(bal, "ar") + bv(bal, "inv")
        val pcurA = bv(pbal, "cash") + bv(pbal, "ar") + bv(pbal, "inv")
        val curL = bv(bal, "payables") + bv(bal, "debt_st")
        val pcurL = bv(pbal, "payables") + bv(pbal, "debt_st")
        val balanceRows = listOf(
            bsub("Kas dan setara kas", "cash", "Runway ${if (e.runwayMonths() >= 99) "> 24" else e.runwayMonths().toString()} bulan"),
            bsub("Piutang usaha normal", "ar_normal", "DSO total ${e.dso().toInt()} hari"),
            bsub("Piutang berisiko (obral kredit)", "ar_risky", "Rentan macet; lihat umur piutang di Catatan"),
            bsub("Persediaan", "inv", "DIO ${e.dio().toInt()} hari"),
            bsum("Total aset lancar", curA, pcurA, "Rasio lancar ${fmt2(curA / max(1.0, curL))}x", SemanticTone.PRIMARY_BLUE),
            bsub("Aset tetap bersih", "fa", "Agunan utama pinjaman; perawatan tertunda ${e.deferredMonths} bulan"),
            bsub("Investasi saham (nilai pasar)", "pf", "Portofolio di Bursa; selisih nilai masuk ekuitas"),
            brow("Total aset", "assets", "Aset = liabilitas + ekuitas", true, SemanticTone.PRIMARY_BLUE),
            bsub("Utang dagang", "payables", "DPO ${e.dpo().toInt()} hari"),
            bsub("Utang bank jatuh tempo ≤12 bulan", "debt_st", "Risiko refinancing"),
            bsum("Total liabilitas lancar", curL, pcurL, "Harus dibayar dalam 12 bulan", SemanticTone.WARNING_YELLOW),
            bsub("Utang bank jangka panjang", "debt_lt", "Termasuk utang A$ dinilai dengan kurs hari ini"),
            brow("Total liabilitas", "liab", "Utang dagang + utang bank", true),
            brow("Total ekuitas", "equity", "Modal disetor + laba ditahan − rugi kurs", true, SemanticTone.GOOD_GREEN)
        )
        val cf = e.lastCashFlow
        val pcf = e.prevCashFlow
        val dt = e.lastDetail
        val pdt = e.prevDetail
        fun drow(label: String, key: String, note: String, total: Boolean = false, indent: Boolean = true, forceShow: Boolean = false): List<FinancialRowItem> {
            val cur = dt[key] ?: 0.0; val pr = pdt[key] ?: 0.0
            if (!total && !forceShow && abs(cur) < 0.5 && abs(pr) < 0.5) return emptyList()
            return listOf(FinancialRowItem((if (indent && !total) "    " else "") + label, if (dt.isEmpty()) "—" else signed(cur), if (pdt.isEmpty()) "—" else signed(pr), note, total,
                if (total && cur < 0) SemanticTone.DANGER_RED else if (total) SemanticTone.GOOD_GREEN else SemanticTone.NEUTRAL_GRAY, stmtOrigin))
        }
        val cashFlowRows = listOf(FinancialRowItem("A. ARUS KAS OPERASI", "", "", "", true, SemanticTone.NEUTRAL_GRAY, stmtOrigin)) +
            drow("Laba bersih", "d_ni", "Titik awal rekonsiliasi", forceShow = true) +
            drow("Penyusutan (non-kas)", "d_dep", "Ditambahkan kembali", forceShow = true) +
            drow("Perubahan piutang", "d_ar", "Penjualan kredit menahan kas", forceShow = true) +
            drow("Perubahan persediaan", "d_inv", "Stok menahan kas", forceShow = true) +
            drow("Perubahan utang dagang", "d_pay", "Pemasok membiayai sebagian modal kerja", forceShow = true) +
            drow("Arus kas operasi", "cfo", "Bisa negatif walau laba positif", true) +
            listOf(FinancialRowItem("B. ARUS KAS INVESTASI", "", "", "", true, SemanticTone.NEUTRAL_GRAY, stmtOrigin)) +
            drow("Penggantian mesin (belanja modal rutin)", "d_capex_repl", "70% dari penyusutan", forceShow = true) +
            drow("Proyek pemintalan", "d_capex_spin", "Tahapan proyek hulu") +
            drow("Ekspansi dan investasi lain", "d_capex_exp", "Lini baru, otomasi, gudang, IPAL, mesin bekas") +
            drow("Pembelian saham", "d_stock_buy", "Termasuk komisi broker") +
            drow("Penjualan saham", "d_stock_sell", "Dikurangi komisi dan pajak jual") +
            drow("Dividen saham diterima", "d_divin", "Dari emiten yang dimiliki") +
            drow("Arus kas investasi", "cfi", "Belanja modal dan portofolio", true) +
            listOf(FinancialRowItem("C. ARUS KAS PENDANAAN", "", "", "", true, SemanticTone.NEUTRAL_GRAY, stmtOrigin)) +
            drow("Pencairan kredit baru", "d_newdebt", "Termasuk refinancing") +
            drow("Pembayaran pokok pinjaman", "d_repay", "Cicilan, pelunasan paksa, pelunasan refinancing") +
            drow("Fasilitas modal kerja (tarik/lunasi)", "d_rcf", "Revolving otomatis saat kas menipis") +
            drow("Pelunasan paksa covenant", "d_forced", "Tuntutan bank setelah pelanggaran") +
            drow("Penjualan piutang (factoring)", "d_factor", "Dana darurat dengan potongan 4%") +
            drow("Dividen dibayar", "d_divpaid", "RUPS atau dividen khusus") +
            drow("Suntikan ekuitas", "d_cure", "Equity cure pemegang saham") +
            drow("Arus kas pendanaan", "cff", "Pinjaman, pemegang saham", true) +
            drow("Perubahan kas bersih", "net", "Sama dengan perubahan kartu Kas (A + B + C)", true)
        val eqRows = listOf(
            FinancialRowItem("Ekuitas awal periode", if (dt.isEmpty()) "—" else money(dt["e_open"] ?: 0.0), if (pdt.isEmpty()) "—" else money(pdt["e_open"] ?: 0.0), "Ekuitas akhir bulan lalu", true, SemanticTone.NEUTRAL_GRAY, stmtOrigin)) +
            drow("Laba bersih", "e_ni", "Dari laporan laba rugi", forceShow = true) +
            drow("Nilai pasar saham dan dividen saham", "e_mtm", "Kenaikan atau penurunan nilai portofolio belum direalisasi") .map { it } +
            drow("Dividen saham diterima", "e_divin", "Langsung masuk ekuitas") +
            drow("Biaya transaksi saham", "e_fees", "Komisi broker dan pajak jual") +
            drow("Biaya factoring", "e_factor", "Potongan penjualan piutang") +
            drow("Rugi selisih kurs utang A$", "e_fx", "Belum direalisasi") +
            drow("Dividen dibayar", "e_div", "Pembagian laba kepada pemegang saham") +
            drow("Suntikan ekuitas", "e_cure", "Modal baru dari pemegang saham") +
            listOf(FinancialRowItem("Ekuitas akhir periode", money(bal["equity"] ?: 0.0), money(pbal["equity"] ?: 0.0), "Sama dengan ekuitas di neraca", true, SemanticTone.GOOD_GREEN, stmtOrigin))
        fun nrow(label: String, value: String, note: String, header: Boolean = false) =
            FinancialRowItem(label, value, "", note, header, SemanticTone.NEUTRAL_GRAY, stmtOrigin)
        val ageSum = { lo: Int, hi: Int -> e.stuffedBuckets.filter { (turn - 1 - it.first) in lo..hi }.sumOf { it.second } }
        val notesRows = mutableListOf<FinancialRowItem>()
        notesRows += nrow("Umur piutang", "", "", true)
        notesRows += nrow("    Piutang normal (termin ≤ 60 hari)", money(e.normalAR), "Pelanggan reguler")
        notesRows += nrow("    Piutang berisiko 0–1 bulan", money(ageSum(0, 1)), "Obral kredit baru")
        notesRows += nrow("    Piutang berisiko 2–3 bulan", money(ageSum(2, 3)), "Mulai perlu ditagih")
        notesRows += nrow("    Piutang berisiko 4–6 bulan", money(ageSum(4, 6)), "Perkiraan ${fmt(ageSum(4, 6) * KapitalEngine.STUFFED_LOSS)} jt RS akan macet (45%)")
        notesRows += nrow("Utang bank", "", "", true)
        for (l in e.loans.filter { it.balance > 0.5 }) {
            val bs = if (l.currencyAud) l.balance * e.kurs else l.balance
            notesRows += nrow("    ${l.lender}", money(bs), "Bunga ${fmt2(l.annualRatePct)}%${if (l.floatingSpreadPct != null) " mengambang" else " tetap"} · ${if (l.maturityTurn < 900) "jatuh tempo ${dateText(l.maturityTurn)}" else "revolving"}" +
                (if (l.amortPerMonth > 0) " · cicilan ${fmt(l.amortPerMonth * (if (l.currencyAud) e.kurs else 1.0))} jt RS/bulan" else ""))
        }
        notesRows += nrow("Karyawan dan kapasitas", "", "", true)
        notesRows += nrow("    Pekerja produksi", "$prod", "Rata-rata ${fmt1(laborPer)} jt RS per orang per bulan (di HPP)")
        notesRows += nrow("    Staf dan manajemen", "$staff", "Rata-rata ${fmt1(salaryPer)} jt RS per orang per bulan (di beban operasional)")
        notesRows += nrow("    Utilisasi kapasitas", "${fmt1(e.utilization() * 100)}%", "Permintaan ${fmt1(e.demandIdx() * 100)}% dari basis · kapasitas ${fmt1(e.capacityIdx() * 100)}%")
        notesRows += nrow("Rasio keuangan", "", "", true)
        notesRows += nrow("    Margin EBIT", "${fmt1(iv(inc, "ebit") / max(1.0, iv(inc, "revenue")) * 100)}%", "EBIT / pendapatan")
        notesRows += nrow("    Perputaran kas (DSO + DIO − DPO)", "${(e.dso() + e.dio() - e.dpo()).toInt()} hari", "Makin panjang makin banyak kas tertahan")
        notesRows += nrow("    ICR (EBIT / bunga, 3 bulan)", "${fmt2(e.ebitHist.takeLast(3).sum() / max(1.0, e.interestHist.takeLast(3).sum()))}x", "Batas covenant ${fmt2(e.covenantThreshold())}x")
        notesRows += nrow("    Utang bersih / EBITDA", "${fmt2((e.totalDebt() - e.cash) / max(1.0, (e.ebitHist.takeLast(3).sum() + 3 * iv(inc, "dep")) * 4.0))}x", "Batas covenant 3,5x")
        notesRows += nrow("    Rasio lancar", "${fmt2(curA / max(1.0, curL))}x", "Aset lancar / liabilitas lancar")
        val ni = iv(inc, "ni")
        val cfo = cf["cfo"] ?: 0.0
        val accrual = if (ni > 0 && cfo < 0)
            "Jebakan akrual: laba bersih ${signed(ni)} jt RS, tetapi arus kas operasi ${signed(cfo)} jt RS karena kas tertahan di piutang dan persediaan."
        else null

        // ---- Eksekutif ----
        val agedAR = e.stuffedBuckets.filter { turn - it.first >= 3 }.sumOf { it.second }
        val executives = e.execs.map { x ->
            val noise = ((seed xor x.id.hashCode().toLong()) % 100) / 500.0
            val perceived = (x.loyalty + noise).coerceIn(0.0, 1.0)
            val loyal = when { perceived > 0.6 -> "Tinggi?"; perceived > 0.35 -> "Sedang?"; else -> "Rendah?" }
            val loyalTone = when { perceived > 0.6 -> SemanticTone.GOOD_GREEN; perceived > 0.35 -> SemanticTone.WARNING_YELLOW; else -> SemanticTone.DANGER_RED }
            val signal = when (x.id) {
                "exec_sales" -> "Sinyal: omzet bulan lalu ${fmt(iv(inc, "revenue"))} jt RS; piutang berumur >90 hari ${fmt(agedAR)} jt RS."
                "exec_factory" -> "Sinyal: belanja perawatan bulan lalu ${fmt(iv(inc, "maint"))} jt RS vs kebutuhan teknis ±${fmt(610 * e.costIndex)} jt RS."
                "exec_coo" -> {
                    val gap = if (e.kickbackActive) 3.4 else 0.6
                    "Sinyal: harga beli kapas ${fmt1(gap + noise * 5)}% di atas indeks bursa Tarsis."
                }
                else -> "Sinyal: rekonsiliasi kas cocok dengan rekening bank."
            }
            val audit = when {
                !x.present -> "Posisi kosong sampai ${dateText(x.vacantUntil)}"
                x.auditDueTurn >= 0 -> "Audit berjalan · hasil di laporan akhir bulan"
                x.evidence == true -> "Audit: indikasi pelanggaran ditemukan (akurasi ±85%)"
                x.evidence == false -> "Audit: tidak ditemukan bukti"
                else -> "Belum diaudit"
            }
            val scheme = when (x.id) {
                "exec_sales" -> e.salesScheme.label
                "exec_factory" -> e.factoryScheme.label
                "exec_coo" -> "Bonus efisiensi dan ketepatan kirim"
                else -> "Bonus stabilitas likuiditas dan audit bersih"
            }
            Executive(
                id = x.id,
                initials = if (x.present) x.initials else "—",
                name = if (x.present) x.name else "Posisi kosong",
                roleTitle = x.role,
                loyaltyEstimate = if (x.present) loyal else "—",
                ambitionEstimate = when (x.id) { "exec_sales" -> "Sangat tinggi?"; "exec_factory" -> "Tinggi?"; else -> "Sedang?" },
                skillEstimate = when (x.id) {
                    "exec_sales" -> "Agresif membuka pasar ritel"; "exec_factory" -> "Ahli mesin tenun"
                    "exec_coo" -> "Kuat di logistik dan pengadaan"; else -> "Akuntansi akrual dan negosiasi bank"
                },
                horizonNote = if (x.replacement) "Baru bergabung" else "Rekam jejak sejak 2028",
                incentiveScheme = scheme,
                suspectedBehaviorNote = signal,
                loyaltyTone = if (x.present) loyalTone else SemanticTone.NEUTRAL_GRAY,
                originInfo = origin("Profil ${x.role}", "Loyalitas: $loyal", "Observasi HRD dan rapat", closed,
                    "Tidak ada angka pasti. Perkiraan berasal dari perilaku yang terlihat.",
                    "Perilaku sebenarnya hanya bisa dipastikan lewat audit, dan audit pun bisa keliru.", ConfidenceTier.LOW),
                present = x.present, signalNote = signal, auditStatus = audit,
                canAudit = x.present && x.evidence == null && x.auditDueTurn < 0 && e.ap >= 2,
                canConfront = x.present && x.evidence == true && e.ap >= 1,
                canFire = x.present && e.ap >= 1,
                lastOutcome = x.lastOutcome
            )
        }

        // ---- Kontrak ----
        val contracts = mutableListOf<ContractItem>()
        fun reviewAction(id: String) = if (id in e.reviewedContracts) emptyList()
            else listOf(ContractActionItem("review", "Tinjau dengan penasihat hukum (1 AP, 50 jt RS)", e.ap >= 1))
        val notReviewed = "Belum ditinjau penasihat hukum. Klausul berisiko tidak terlihat."
        val bankB = e.loans.firstOrNull { it.id == "bank_b" && it.balance > 0.5 }
        if (bankB != null) contracts += ContractItem("cnt_bank_b", "Kredit bank aktif", bankB.lender, "Sundara",
            "${fmt(bankB.balance)} jt RS (mengambang ${fmt2(bankB.annualRatePct)}%)",
            "Jatuh tempo ${dateText(bankB.maturityTurn)}", "ICR ≥ ${fmt1(bankB.covenantIcr)}x dan utang bersih/EBITDA ≤ 3,5x, diuji kuartalan.",
            if ("cnt_bank_b" in e.reviewedContracts) "Cross-default: gagal covenant 3 kuartal berturut-turut memberi bank hak menyita Pabrik Kalimas. Perpanjangan saat jatuh tempo hanya jika kuartal terakhir lulus." else notReviewed,
            SemanticTone.WARNING_YELLOW, originInfo = covOrigin, actions = reviewAction("cnt_bank_b"))
        if (e.termSheetChosen == null && bankB != null) {
            contracts += ContractItem("cnt_ts_1", "Term sheet #1", "Bank Pembangunan Sundara", "Sundara",
                "${fmt(e.termSheetAmount())} jt RS (bunga tetap ${fmt2(e.termSheetFixedRate())}%)", "Tenor 48 bulan · cicilan merata ±${fmt(e.termSheetAmount() / 48.0)} jt RS/bulan",
                "ICR ≥ 1,5x · agunan pabrik dengan haircut 25%.",
                if ("cnt_ts_1" in e.reviewedContracts) "Aman dari kurs dan kenaikan bunga, tetapi cicilan pokok menyedot sekitar ${fmt(e.termSheetAmount() / 4.0)} jt RS kas per tahun." else notReviewed,
                SemanticTone.PRIMARY_BLUE, originInfo = covOrigin,
                actions = reviewAction("cnt_ts_1") + ContractActionItem("choose", "Pilih lewat jurnal keputusan (2 AP)", e.ap >= 2))
            contracts += ContractItem("cnt_ts_2", "Term sheet #2", "Aurelia Global CitiTrust", "Aurelia",
                "≈${fmt2(e.termSheetAudAmountRs() / e.kurs)} juta A$ (≈${fmt(e.termSheetAudAmountRs())} jt RS, bunga 5,2%)", "Tenor 36 bulan · bullet (pokok di akhir)",
                "ICR ≥ 1,75x · klausul Material Adverse Change (MAC).",
                if ("cnt_ts_2" in e.reviewedContracts) "JEBAKAN: utang dalam A$. Jika Rial anjlok, klausul MAC mengizinkan bank menaikkan bunga ke 9,2% dan menagih 30% pokok dalam 3 bulan." else notReviewed,
                SemanticTone.PRIMARY_BLUE, originInfo = covOrigin,
                actions = reviewAction("cnt_ts_2") + ContractActionItem("choose", "Pilih lewat jurnal keputusan (2 AP)", e.ap >= 2))
        }
        for (l in e.loans.filter { it.id != "bank_b" && it.balance > 0.5 }) {
            contracts += ContractItem("loan_${l.id}", "Pinjaman aktif", l.lender, if (l.currencyAud) "Aurelia" else "Sundara",
                if (l.currencyAud) "${fmt2(l.balance)} juta A$ (≈${fmt(l.balance * e.kurs)} jt RS) · ${fmt1(l.annualRatePct)}%"
                else "${fmt(l.balance)} jt RS · ${fmt1(l.annualRatePct)}%",
                if (l.maturityTurn < 900) "Jatuh tempo ${dateText(l.maturityTurn)}" else "Cicilan berjalan",
                if (l.covenantIcr > 0) "ICR ≥ ${fmt1(l.covenantIcr)}x" else "Tanpa covenant rasio",
                if (l.currencyAud) "Nilai dalam Rial berubah mengikuti kurs." else "Cicilan pokok ${fmt(l.amortPerMonth)} jt RS/bulan.",
                SemanticTone.NEUTRAL_GRAY, originInfo = covOrigin)
        }
        contracts += ContractItem("cnt_sup_kestrel", "Pemasok Tier-2", "Kestrel Precision Loom Parts", "Kestrel",
            "Suku cadang dan sensor tenun (±${fmt(1800.0 * e.sc.size)} jt RS/tahun)",
            if (e.dualSourcing) "Dual sourcing aktif (HPP +1,2%)" else "Just-in-time · tanpa denda keterlambatan",
            "Memasok 80% kebutuhan sensor dan jarum mesin tenun Kalimas.",
            if ("cnt_sup_kestrel" in e.reviewedContracts) "Pemasok diam-diam memakai magnet dari Velmora (terkena sanksi Aurelia) dan bisa menahan kiriman saat kelangkaan untuk dijual ke pasar spot." else notReviewed,
            if (e.dualSourcing) SemanticTone.GOOD_GREEN else SemanticTone.WARNING_YELLOW, originInfo = covOrigin,
            actions = reviewAction("cnt_sup_kestrel") +
                if (e.dualSourcing) emptyList() else listOf(ContractActionItem("dual", "Aktifkan dual sourcing (1 AP, HPP +1,2%)", e.ap >= 1)))

        if (e.covenantBreaches >= 1 && !e.cureUsed) contracts += ContractItem("cnt_cure", "Hak equity cure", "Pemegang saham", "Sundara",
            "Suntikan ekuitas ${fmt(e.sc.equityCure)} jt RS (sekali seumur permainan)",
            "Berlaku selama ada pelanggaran covenant aktif",
            "Menambah kas dan menurunkan hitungan pelanggaran satu tingkat. Tidak memperbaiki ICR.",
            "Menunda masalah, bukan menyelesaikannya: jika laba operasi tidak pulih, pelanggaran terulang.",
            SemanticTone.WARNING_YELLOW, originInfo = covOrigin,
            actions = listOf(ContractActionItem("cure", "Suntik ekuitas (2 AP, sekali)", e.ap >= 2)))
        contracts += ContractItem("cnt_hedge_kapas", "Kontrak berjangka kapas", "Bursa Komoditas Tarsis–Sundara", "Tarsis",
            "Kunci harga kapas ${fmt1(e.commodities.cotton.price)} A$/ton (+2% premi) selama 6 bulan",
            if (e.hedgeActive) "Aktif sampai ${dateText(e.hedgeUntilTurn)}" else "Belum ada lindung nilai · HPP mengikuti harga pasar",
            "Biaya 60 jt RS. Melindungi dari kenaikan harga, tetapi Anda tidak menikmati penurunan harga selama kontrak.",
            "Kapas ±45% dari biaya produksi. Harga kapas bisa bergerak ±25% per tahun dan melonjak saat El Niño.",
            if (e.hedgeActive) SemanticTone.GOOD_GREEN else SemanticTone.WARNING_YELLOW, originInfo = covOrigin,
            actions = if (e.hedgeActive) emptyList() else listOf(ContractActionItem("hedge", "Lindung nilai kapas 6 bulan (1 AP, 60 jt RS)", e.ap >= 1)))

        // ---- Proyek ----
        val gantt = mutableListOf<GanttTask>()
        var ganttTitle = "Belum ada proyek aktif"
        var totalMonths = 12
        if (e.spinningDecision == "approved") {
            val m = turn - e.spinningStartTurn
            val phases = e.spinningPhases
            val showPhases = if (m < 8 && e.spinningDelayed) listOf(
                phases[0], phases[1], phases[2].copy(months = 4, budget = 5600.0), phases[3].copy(startMonth = 12)
            ) else phases
            totalMonths = showPhases.last().startMonth + showPhases.last().months
            ganttTitle = "Pabrik pemintalan · mulai ${dateText(e.spinningStartTurn)}"
            showPhases.forEachIndexed { i, ph ->
                val prog = (m - ph.startMonth).coerceIn(0, ph.months)
                val done = prog >= ph.months || e.spinningCompletedTurn > 0
                gantt += GanttTask(
                    "gantt_$i", "Pabrik pemintalan", ph.name, ph.startMonth + 1, ph.months,
                    if (e.spinningCompletedTurn > 0) ph.months else prog,
                    ph.budget.toInt(), (ph.budget * (if (done) 1.0 else prog.toDouble() / ph.months)).toInt(),
                    when {
                        done -> "Selesai"
                        prog > 0 -> "Berjalan (bulan $prog/${ph.months})"
                        else -> "Menunggu"
                    },
                    when {
                        done -> SemanticTone.GOOD_GREEN
                        prog > 0 -> SemanticTone.PRIMARY_BLUE
                        else -> SemanticTone.NEUTRAL_GRAY
                    },
                    when (i) {
                        2 -> "Mesin dari Kestrel; rawan tertunda bila kuota ekspor diperketat."
                        3 -> "Biaya benang turun bertahap selama 6 bulan setelah selesai."
                        else -> "Dibiayai 70% kredit investasi, 30% kas."
                    }
                )
            }
        }

        // ---- Jurnal, bias, kronik ----
        val journals = e.journals.reversed().map {
            CompletedJournalEntry(it.id, it.decisionTitle, it.turnSubmitted, it.evaluateAtTurn, it.reason, it.prediction,
                it.confidencePct, it.failure, it.outcome != null,
                it.outcome?.let { o -> val p = it.confidencePct / 100.0; val v = if (o) 1.0 else 0.0; (p - v) * (p - v) })
        }
        val evaluated = e.journals.filter { it.outcome != null }
        val biases = mutableListOf<String>()
        if (evaluated.size >= 2) {
            val avgConf = evaluated.map { it.confidencePct }.average()
            val hit = evaluated.count { it.outcome == true } * 100.0 / evaluated.size
            if (avgConf - hit > 20) biases += "Overconfidence: rata-rata keyakinan Anda ${avgConf.toInt()}%, tetapi hanya ${hit.toInt()}% prediksi yang benar."
            else if (hit - avgConf > 20) biases += "Underconfidence: Anda benar ${hit.toInt()}% dari waktu, lebih sering dari keyakinan Anda (${avgConf.toInt()}%)."
        }
        if (turn >= 42 && e.totalCrossChecks == 0) biases += "Confirmation bias: Anda belum pernah memeriksa silang berita sejak memimpin perusahaan."
        if (e.spinningDecision == "approved" && e.spinningDelayed && turn - e.spinningStartTurn in 8..14)
            biases += "Waspadai sunk cost: proyek pemintalan molor. Nilai ulang berdasarkan biaya ke depan, bukan dana yang sudah keluar."
        if (biases.isEmpty()) biases += "Belum ada pola bias yang terdeteksi. Kalibrasi dinilai setelah jurnal dievaluasi."

        val chronicles = e.chronicle.reversed().map {
            ChronicleEntry(it.turn, dateText(it.turn), "Era ${KapitalEngine.era(it.turn)}", it.title, it.description, it.causalChain,
                canReplayCounterfactual = it.actionIndex != null && turn - it.turn >= 12, actionIndex = it.actionIndex)
        }

        // ---- Pesaing dan pasar produk (estimasi intel ±1,5%, bukan angka pasti) ----
        val intel = kotlin.random.Random(seed * 31L + turn * 17L)
        val myRel = (1.0 + e.priceAdjPct / 100.0) * e.fxPassLevel()
        val rivalItems = e.world.rivals.mapIndexed { idx, rv ->
            val observed = rv.price * (1.0 + (intel.nextDouble() - 0.5) * 0.03)
            val diff = (observed / myRel - 1.0) * 100.0
            RivalItem(
                id = rv.id, name = rv.name,
                priceText = "${fmt2(observed * 100.0)} (indeks)",
                vsYouText = when {
                    !rv.active -> "—"
                    diff < -1.0 -> "${fmt1(-diff)}% lebih murah dari Anda"
                    diff > 1.0 -> "${fmt1(diff)}% lebih mahal dari Anda"
                    else -> "sepadan dengan harga Anda"
                },
                statusText = when {
                    !rv.active -> "Keluar pasar"
                    rv.id == "riv_importir" && e.world.dumpingMonthsLeft > 0 -> "Membanting harga (dumping)"
                    rv.weakMonths >= 4 -> "Tertekan: jual di bawah biaya ${rv.weakMonths} bulan"
                    else -> "Bersaing normal"
                },
                tone = when {
                    !rv.active -> SemanticTone.NEUTRAL_GRAY
                    rv.id == "riv_importir" && e.world.dumpingMonthsLeft > 0 -> SemanticTone.DANGER_RED
                    rv.weakMonths >= 4 -> SemanticTone.WARNING_YELLOW
                    else -> SemanticTone.PRIMARY_BLUE
                }
            )
        }
        val shareText = "${fmt1(e.playerShare() * 100.0)}% (awal 20%)"
        val unempText = "${fmt1(e.world.unemployment)}%"
        val marketNote = "Ketika Anda memotong harga, pesaing menyamai dalam beberapa bulan dan perang harga menggerus margin semua pihak. " +
            "Menaikkan harga membuat Anda kehilangan pangsa karena pesaing tidak ikut naik."

        return GameState(
            hasSavedGame = log.isNotEmpty() || e.turn > e.sc.turn0,
            companyEquityText = "${fmt(e.equity)} jt RS",
            rivals = rivalItems,
            actionPoints = e.ap,
            credit = CreditView.credit(e),
            actors = CreditView.actors(e, executives, closed),
            bourse = BourseView.build(e, origin("Bursa Efek Sundara", "Harga penutupan", "Bursa Efek Sundara (BES)", "Bulan $closed", "Harga bergerak dari nilai wajar (laba 12 bulan x P/E yang bergantung suku bunga) ditambah sentimen pasar. Laporan dirilis resmi tiap akhir triwulan.", "Harga bisa menyimpang jauh dari nilai wajar dalam jangka pendek.", ConfidenceTier.HIGH)),
            playerShareText = shareText,
            unemploymentText = unempText,
            marketNote = marketNote,
            dateText = dateText(turn),
            turn = turn,
            eraNumber = KapitalEngine.era(turn),
            eraName = "Era ${KapitalEngine.era(turn)}",
            apCurrent = e.ap,
            apMax = e.apMax,
            runwayMonths = e.runwayMonths(),
            isDarkTheme = ui.isDarkTheme,
            seed = seed,
            covenantTone = covTone,
            memos = e.memos.map { MemoItem(it.from, it.role, it.text) },
            monthlyReportTitle = e.lastReportTitle,
            monthlyReportLines = e.lastReport.map { ReportLineItem(it.text, tone(it.tone)) },
            showMonthlyReport = ui.showMonthlyReport,
            gameOverReason = e.gameOverReason,
            salesSchemeLabel = e.salesScheme.name,
            factorySchemeLabel = e.factoryScheme.name,
            salesSchemeOptions = SalesScheme.entries.map { it.name to it.label },
            factorySchemeOptions = FactoryScheme.entries.map { it.name to it.label },
            priceAdjPct = e.priceAdjPct,
            counterfactualText = ui.counterfactualText,
            ganttTitle = ganttTitle,
            ganttTotalMonths = totalMonths,
            periodLabel = closed,
            prevPeriodLabel = prevClosed,
            accrualWarning = accrual,
            covenantWarningTitle = covTitle,
            covenantWarningSubtitle = covSub,
            covenantOrigin = covOrigin,
            featuredNewsSource = featured?.mediaSlant?.outletName ?: "",
            featuredNewsConfidence = featured?.confidence ?: ConfidenceTier.MEDIUM,
            featuredNewsHeadline = featured?.headline ?: "",
            featuredNewsFooter = "+${max(0, newsItems.size - 1)} sudut pandang lain · ${newsItems.count { it.isRumor }} rumor",
            markasIndicators = indicators,
            cashHistoryPoints = hist,
            cashFanMedianPoints = median,
            cashFanBand50Points = s50,
            cashFanBand90Points = s90,
            pendingDecisions = decisions,
            completedJournals = journals,
            morningPaperEdition = newsItems,
            countries = countries,
            mapPieces = StaticWorldData.mapPieces(),
            activeMapLayer = ui.activeMapLayer,
            pairwiseTensions = StaticWorldData.pairwiseTensions(),
            incomeStatementRows = incomeRows,
            balanceSheetRows = balanceRows,
            cashFlowRows = cashFlowRows,
            equityRows = eqRows,
            notesRows = notesRows,
            investments = investItems,
            investSummary = investSummary,
            executives = executives,
            ganttTasks = gantt,
            contracts = contracts,
            marketInstruments = markets,
            brierScore = e.brierScore(),
            evaluatedJournals = evaluated.size,
            detectedBiases = biases,
            worldChronicles = chronicles,
            activeEventDialog = run {
                val ev = e.openEvents.firstOrNull { it.id == ui.activeEventId } ?: e.openEvents.firstOrNull()
                if (ev == null) EventDialogModel("none", "Tidak ada kartu aktif", "Tidak ada kejadian yang menunggu keputusan.", "", emptyList())
                else EventDialogModel(ev.id, ev.title, ev.body, ev.warning,
                    ev.options.filter { it.id != "z" }.map { EventOptionItem(it.id, it.title, it.apCost, it.impact, it.advisor, tone(it.tone)) })
            },
            eventOutcomeText = ui.eventOutcome,
            showEventDialog = ui.showEventDialog,
            inspectedDataOrigin = ui.inspectedOrigin,
            selectedCountryForSheet = ui.selectedCountry
        )
    }
}
