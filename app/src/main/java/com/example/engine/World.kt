package com.example.engine

import kotlin.math.max
import kotlin.random.Random

/**
 * Lapisan dunia berbasis agen untuk KAPITAL.
 *
 * Agen yang benar-benar disimulasikan:
 *  - [households] rumah tangga (status kerja, pendapatan, kecenderungan konsumsi).
 *  - 4 pesaing tekstil dengan arketipe berbeda yang BELAJAR dari pola tindakan pemain setiap 12 giliran:
 *      1. riv_mitra (Raksasa konservatif): lambat, kaya kas, memborong pasokan saat pemain tergantung 1 pemasok,
 *         dan mengakuisisi kapasitas pesaing yang bangkrut.
 *      2. riv_cahaya (Startup agresif): membakar uang demi pangsa pasar, menyerang harga saat pemain terlilit utang.
 *      3. riv_prima (Pemain kroni): melobi sabotase regulasi/inspeksi saat pangsa pemain membesar.
 *      4. riv_importir (Perampas harga): memicu perang harga tajam untuk mengusir pemain yang lemah likuiditas.
 *  - 1 bank sentral dengan aturan Taylor yang memakai inflasi, kesenjangan output, dan depresiasi kurs.
 */
class World(
    private val households: Int = 50_000,
    private val inflationTarget: Double = 3.0,
    private val neutralRealRate: Double = 2.5
) {

    companion object {
        const val SEP_BASE = 0.0158
        const val FIND_BASE = 0.30
        private const val PRICE_SENSITIVITY = 4.0
    }

    /** Profil perilaku pemain yang diamati pesaing dari data pasar & laporan publik (bukan data rahasia internal). */
    data class PlayerObservation(
        val priceAdjPct: Int = 0,
        val netDebtToEbitda: Double = 2.0,
        val debtMaturingWithin6m: Boolean = false,
        val singleSupplier: Boolean = true,
        val cottonHedged: Boolean = false,
        val playerMarketShare: Double = 0.20,
        val allowCoalition: Boolean = false,
        val qualityInvestmentsCount: Int = 0
    )

    enum class RivalStrategy(val label: String) {
        NORMAL("Bersaing normal"),
        PREDATORY_DEBT_ATTACK("Perang harga mengincar jatuh tempo utang Anda"),
        SUPPLY_CORNERING("Memborong pasokan kapas spot untuk menekan HPP Anda"),
        REGULATORY_LOBBY("Melobi pengetatan inspeksi & tarif industri"),
        COALITION_UNDERCUT("Koalisi harga melawan pemimpin pasar"),
        PRICE_WAR_RETALIATION("Membalas pemotongan harga agresif Anda"),
        PREMIUM_FOLLOW("Mengikuti kenaikan harga & fokus margin")
    }

    class Rival(
        val id: String,
        val name: String,
        val archetype: String,
        /** Pangsa pasar awal (juga bobot kualitas/merek). */
        var share0: Double,
        /** 0..1: seberapa cepat menyamai potongan harga lawan. */
        val aggression: Double,
        /** Biaya satuan relatif terhadap harga pasar awal (1,0 = impas). Makin kecil makin efisien. */
        var unitCost: Double
    ) {
        var price = 1.0
        var weakMonths = 0
        var active = true
        var activeStrategy: RivalStrategy = RivalStrategy.NORMAL
        var strategyMonthsLeft: Int = 0
        var warningSignal: String = ""
        var learnedAggressionMod: Double = 1.0
    }

    val rivals = listOf(
        Rival("riv_mitra", "Mitra Tekstil Nusantara", "Raksasa konservatif", 0.30, 0.35, 0.89),
        Rival("riv_cahaya", "Cahaya Weaving", "Startup agresif", 0.25, 0.55, 0.92),
        Rival("riv_prima", "Prima Garmen Utama", "Pemain kroni", 0.15, 0.25, 0.95),
        Rival("riv_importir", "Importir Velmora", "Perampas harga", 0.10, 0.90, 0.83)
    )
    private val playerShare0 = 0.20

    // Memori pembelajaran pesaing atas pola tindakan pemain (diperbarui tiap bulan, dievaluasi tiap 12 giliran)
    var observedPriceCutMonths = 0; private set
    var observedPriceRaiseMonths = 0; private set
    var observedQualityInvestments = 0; private set
    var observedHighDebtMonths = 0; private set
    var observedSingleSupplierMonths = 0; private set
    var learnedCycleCount = 0; private set
    var learnedPlayerProfile: String = "Belum terbaca (mengamati pola harga dan utang Anda)"; private set
    var lastLearnedAttackTrigger: String? = null; private set
    private var monthsElapsed = 0

    // ---- Rumah tangga (kohort pendapatan; lihat Agents.kt) ----
    val householdSector = HouseholdSector(households)
    private var baseConsumption = 1.0

    // ---- Keadaan makro ----
    var demand = 1.0; private set
    var gap = 0.0; private set
    var unemployment = 5.0; private set
    var inflation = 5.0; private set
    var inflationExpectation = 5.0; private set
    var policyRate = 6.25; private set
    private var shadowRate = 6.25
    private var confidence = 0.0
    private val kursHistory = ArrayDeque<Double>()
    var dumpingMonthsLeft = 0; private set
    var coalitionMonthsLeft = 0; private set
    var lastEvents: List<String> = emptyList(); private set
    var taylorStar = 6.5; private set

    init {
        baseConsumption = householdSector.consumption()
    }

    private fun gauss(r: Random): Double = stdNormal(r)

    // =====================================================================
    // Pasar produk: pangsa pemain sebagai fungsi harga relatif
    // =====================================================================
    private fun weight(share0: Double, relPrice: Double) = share0 * relPrice.coerceAtLeast(0.3).pow(-PRICE_SENSITIVITY)

    fun playerShare(relPrice: Double): Double {
        val wp = weight(playerShare0, relPrice)
        val wr = rivals.filter { it.active }.sumOf { weight(it.share0, it.price) }
        return if (wp + wr <= 0.0) 1.0 else wp / (wp + wr)
    }

    fun volumeFactor(relPrice: Double): Double = playerShare(relPrice) / playerShare0

    fun rivalShareOf(id: String, playerRelPrice: Double): Double {
        val rv = rivals.firstOrNull { it.id == id && it.active } ?: return 0.0
        val wp = weight(playerShare0, playerRelPrice)
        val wr = rivals.filter { it.active }.sumOf { weight(it.share0, it.price) }
        return weight(rv.share0, rv.price) / (wp + wr)
    }

    fun rivalAveragePrice(): Double {
        val a = rivals.filter { it.active }
        if (a.isEmpty()) return 1.0
        return a.sumOf { it.share0 * it.price } / a.sumOf { it.share0 }
    }

    class Output(
        val demand: Double,
        val inflationAnnual: Double,
        val policyRate: Double,
        val unemployment: Double,
        val events: List<String>,
        val policyChanged: Boolean,
        val rivalTriggeredAction: String? = null
    )

    fun step(
        r: Random,
        playerRelPrice: Double,
        costPressure: Double,
        priceLevel: Double,
        kursChangeMonthly: Double,
        kurs: Double,
        extraDemandShock: Double = 0.0,
        obs: PlayerObservation? = null,
        policyBias: Double = 0.0,
        /** Selisih inflasi yang DIUMUMKAN (rilis awal berisik, tertunda) dikurangi inflasi sebenarnya; bank sentral bereaksi pada angka rilis. */
        inflationMisread: Double = 0.0,
        /** Mode peran Gubernur (C4): bila tidak null, suku bunga acuan ditetapkan pemain, bukan aturan Taylor. */
        policyOverride: Double? = null
    ): Output {
        val events = mutableListOf<String>()
        monthsElapsed++
        lastLearnedAttackTrigger = null

        // ---- 1. Guncangan permintaan (kepercayaan konsumen) ----
        confidence = 0.85 * confidence + gauss(r) * 0.010 + extraDemandShock
        val realRate = policyRate - inflation
        val rateDrag = -0.012 * (realRate - neutralRealRate)

        // ---- 2. Pasar tenaga kerja ----
        val gapLag = gap
        val sep = (SEP_BASE * (1.0 - 4.0 * gapLag)).coerceIn(0.004, 0.045)
        val find = (FIND_BASE * (1.0 + 4.0 * gapLag)).coerceIn(0.08, 0.60)
        val consumption = householdSector.step(r, sep, find)
        unemployment = householdSector.unemploymentPct()

        // ---- 3. Permintaan agregat ----
        demand = ((consumption / baseConsumption) * (1.0 + confidence + rateDrag)).coerceIn(0.80, 1.20)
        gap = demand - 1.0

        // ---- 4. Pesaing: Pembaruan Model Pembelajaran & Reaksi Harga ----
        if (obs != null) {
            updateRivalLearning(obs, events)
        }

        if (dumpingMonthsLeft == 0 && r.nextDouble() < 0.012) {
            dumpingMonthsLeft = 6 + r.nextInt(5)
            events += "Importir Velmora membanjiri pasar dengan kain murah (dumping): harga mereka turun sekitar 15% selama beberapa bulan."
        }
        for (rv in rivals) {
            if (!rv.active) continue
            val ownCost = costPressure.coerceIn(0.7, 1.6) * rv.unitCost
            var target = priceLevel.coerceIn(0.7, 1.6) * (1.0 + 0.6 * gap)
            if (rv.id == "riv_importir" && dumpingMonthsLeft > 0) target *= 0.85
            when (rv.activeStrategy) {
                RivalStrategy.PREDATORY_DEBT_ATTACK -> if (rv.strategyMonthsLeft > 0) target *= 0.91
                RivalStrategy.COALITION_UNDERCUT -> if (rv.strategyMonthsLeft > 0) target *= 0.93
                RivalStrategy.PRICE_WAR_RETALIATION -> if (rv.strategyMonthsLeft > 0) target *= 0.92
                RivalStrategy.PREMIUM_FOLLOW -> if (rv.strategyMonthsLeft > 0) target *= 1.04
                else -> {}
            }

            val undercut = playerRelPrice - rv.price
            var move = 0.30 * (target - rv.price)
            if (undercut < 0.0) move += (rv.aggression * rv.learnedAggressionMod) * 0.75 * undercut
            rv.price = (rv.price + move + gauss(r) * 0.002).coerceIn(0.6, 1.8)
            val margin = rv.price / max(0.3, ownCost) - 1.0
            rv.weakMonths = if (margin < 0.0) rv.weakMonths + 1 else max(0, rv.weakMonths - 1)

            if (rv.strategyMonthsLeft > 0) {
                rv.strategyMonthsLeft--
                if (rv.strategyMonthsLeft == 0) rv.activeStrategy = RivalStrategy.NORMAL
            }

            if (rv.weakMonths >= 9 && rivals.count { it.active } > 2) {
                rv.active = false
                val mitra = rivals.firstOrNull { it.id == "riv_mitra" && it.active && it.id != rv.id }
                if (mitra != null) {
                    val absorbed = rv.share0 * 0.35
                    mitra.share0 += absorbed
                    rv.share0 -= absorbed
                    events += "${rv.name} bangkrut; ${mitra.name} mengakuisisi sebagian gudang dan pelanggannya."
                } else {
                    events += "${rv.name} bangkrut dan keluar dari pasar setelah berbulan-bulan menjual di bawah biaya."
                }
            }
        }
        if (dumpingMonthsLeft > 0) {
            dumpingMonthsLeft--
            if (dumpingMonthsLeft == 0) events += "Banjir impor murah mereda; harga importir kembali normal."
        }
        if (coalitionMonthsLeft > 0) {
            coalitionMonthsLeft--
            if (coalitionMonthsLeft == 0) events += "Koalisi harga pesaing bubar karena mereka kembali berebut pangsa."
        }
        if (rivals.any { !it.active } && gap > 0.0 && r.nextDouble() < 0.04) {
            val dead = rivals.first { !it.active }
            dead.active = true; dead.weakMonths = 0; dead.price = 1.0
            events += "Pemain baru masuk menggantikan ${dead.name} karena margin industri kembali menarik."
        }

        // ---- 5. Inflasi: kurva Phillips + pass-through kurs ----
        val fxExcess = 0.07 * 12.0 * 100.0 * (kursChangeMonthly - 0.002)
        val demandPush = 22.0 * gap
        val pressure = inflationExpectation + demandPush + fxExcess
        inflation = (0.80 * inflation + 0.20 * pressure + gauss(r) * 0.15).coerceIn(-1.0, 25.0)
        inflationExpectation = 0.90 * inflationExpectation + 0.06 * inflation + 0.04 * inflationTarget

        // ---- 6. Bank sentral: aturan Taylor ----
        kursHistory.addLast(kurs); if (kursHistory.size > 4) kursHistory.removeFirst()
        val depr3m = if (kursHistory.size >= 4) (kurs / kursHistory.first() - 1.0) * 100.0 else 0.0
        val star = neutralRealRate + inflation + 0.5 * (inflation - inflationTarget) +
            0.5 * (gap * 100.0) + 0.12 * max(0.0, depr3m - 1.5) + policyBias + 1.5 * inflationMisread
        taylorStar = star
        shadowRate += (0.30 * (star - shadowRate)).coerceIn(-0.5, 0.5)
        shadowRate = shadowRate.coerceIn(2.0, 14.0)
        if (policyOverride != null) shadowRate = policyOverride.coerceIn(0.5, 20.0)
        val newPolicy = Math.round(shadowRate / 0.25) * 0.25
        val changed = newPolicy != policyRate
        policyRate = newPolicy

        lastEvents = events
        return Output(demand, inflation, policyRate, unemployment, events, changed, lastLearnedAttackTrigger)
    }

    /**
     * Setiap bulan pesaing mencatat jejak publik pemain; setiap 12 giliran mereka memperbarui model perilaku
     * pemain dan meluncurkan strategi respons yang berbeda tergantung gaya bermain pemain.
     */
    private fun updateRivalLearning(obs: PlayerObservation, events: MutableList<String>) {
        if (obs.priceAdjPct <= -3) observedPriceCutMonths++ else observedPriceCutMonths = max(0, observedPriceCutMonths - 1)
        if (obs.priceAdjPct >= 3) observedPriceRaiseMonths++ else observedPriceRaiseMonths = max(0, observedPriceRaiseMonths - 1)
        observedQualityInvestments = max(observedQualityInvestments, obs.qualityInvestmentsCount)
        if (obs.netDebtToEbitda > 2.8 || obs.debtMaturingWithin6m) observedHighDebtMonths++ else observedHighDebtMonths = max(0, observedHighDebtMonths - 1)
        if (obs.singleSupplier && !obs.cottonHedged) observedSingleSupplierMonths++ else observedSingleSupplierMonths = max(0, observedSingleSupplierMonths - 1)

        // Sinyal peringatan dini 2 bulan sebelum evaluasi tahunan (giliran ke-10 dari siklus 12)
        if (monthsElapsed % 12 == 10) {
            for (rv in rivals) rv.warningSignal = ""
            when {
                obs.allowCoalition && obs.playerMarketShare > 0.25 -> {
                    rivals.filter { it.active }.forEach {
                        it.warningSignal = "Sinyal intelijen: Direksi pesaing mengadakan pertemuan tertutup di asosiasi untuk membendung pangsa pasar Anda."
                    }
                }
                observedPriceCutMonths >= 3 -> {
                    rivals.firstOrNull { it.id == "riv_prima" && it.active }?.warningSignal =
                        "Sinyal politik: Komisaris Prima Garmen menemui pejabat kementerian terkait perang harga Anda."
                }
                observedHighDebtMonths >= 3 && obs.debtMaturingWithin6m -> {
                    rivals.firstOrNull { it.id == "riv_cahaya" && it.active }?.warningSignal =
                        "Sinyal pasar: Cahaya Weaving menumpuk persediaan kain jadi menjelang jadwal jatuh tempo utang bank Anda."
                }
                observedSingleSupplierMonths >= 4 -> {
                    rivals.firstOrNull { it.id == "riv_mitra" && it.active }?.warningSignal =
                        "Sinyal komoditas: Mitra Tekstil meningkatkan kontrak pembelian kapas dan suku cadang spot di Tarsis & Kestrel."
                }
            }
        }

        // Evaluasi model perilaku pemain setiap 12 giliran (Bagian 6 dokumen desain)
        if (monthsElapsed % 12 == 0) {
            learnedCycleCount++
            when {
                obs.allowCoalition && obs.playerMarketShare > 0.25 && rivals.count { it.active } >= 2 -> {
                    learnedPlayerProfile = "Pemimpin pasar dominan (${KapitalEngine.fmt1(obs.playerMarketShare * 100)}%) → Pesaing berkoalisi"
                    coalitionMonthsLeft = 5
                    rivals.filter { it.active }.forEach {
                        it.activeStrategy = RivalStrategy.COALITION_UNDERCUT
                        it.strategyMonthsLeft = 5
                        it.learnedAggressionMod = 1.25
                    }
                    lastLearnedAttackTrigger = "coalition"
                    events += "Pesaing AI membaca dominasi pangsa Anda dan membentuk koalisi pemotongan harga bersama selama 5 bulan."
                }
                observedPriceCutMonths >= 3 -> {
                    learnedPlayerProfile = "Pemangkas harga agresif → Pesaing membalas perang harga & lobi regulasi"
                    rivals.filter { it.active }.forEach {
                        it.learnedAggressionMod = (it.learnedAggressionMod * 1.18).coerceAtMost(1.6)
                        if (it.id == "riv_prima") {
                            it.activeStrategy = RivalStrategy.REGULATORY_LOBBY
                            it.strategyMonthsLeft = 4
                        } else {
                            it.activeStrategy = RivalStrategy.PRICE_WAR_RETALIATION
                            it.strategyMonthsLeft = 4
                        }
                    }
                    lastLearnedAttackTrigger = "regulatory_sabotage"
                    events += "Pesaing AI mempelajari pola pemotongan harga Anda: Cahaya & Importir membalas memangkas harga, sementara Prima Garmen melobi inspeksi."
                }
                observedPriceRaiseMonths >= 3 || observedQualityInvestments >= 1 -> {
                    learnedPlayerProfile = "Fokus mutu & harga premium → Pesaing menaikkan margin dan efisiensi"
                    rivals.filter { it.active }.forEach {
                        it.learnedAggressionMod = (it.learnedAggressionMod * 0.90).coerceAtLeast(0.7)
                        it.unitCost = (it.unitCost * 0.992).coerceAtLeast(0.78)
                        it.activeStrategy = RivalStrategy.PREMIUM_FOLLOW
                        it.strategyMonthsLeft = 4
                    }
                    lastLearnedAttackTrigger = "premium_adaptation"
                    events += "Pesaing AI mengamati fokus mutu dan harga premium Anda; mereka ikut menaikkan harga rata-rata dan berinvestasi efisiensi."
                }
                observedHighDebtMonths >= 3 && (obs.debtMaturingWithin6m || obs.netDebtToEbitda > 3.0) -> {
                    learnedPlayerProfile = "Terlilit utang / jatuh tempo dekat → Diserang perang harga (Predatory Pricing)"
                    rivals.filter { it.active && (it.id == "riv_cahaya" || it.id == "riv_importir") }.forEach {
                        it.activeStrategy = RivalStrategy.PREDATORY_DEBT_ATTACK
                        it.strategyMonthsLeft = 4
                    }
                    lastLearnedAttackTrigger = "predatory_debt"
                    events += "Cahaya Weaving & Importir Velmora membaca beban utang Anda dan memicu perang harga untuk menekan ICR Anda menjelang uji bank."
                }
                observedSingleSupplierMonths >= 4 -> {
                    learnedPlayerProfile = "Bergantung pada satu pemasok & tanpa lindung nilai → Diserang borongan pasokan (Cornering)"
                    rivals.firstOrNull { it.id == "riv_mitra" && it.active }?.let {
                        it.activeStrategy = RivalStrategy.SUPPLY_CORNERING
                        it.strategyMonthsLeft = 4
                    }
                    lastLearnedAttackTrigger = "cornering"
                    events += "Mitra Tekstil membaca ketergantungan pasokan Anda dan memborong stok kapas/komponen spot untuk mendongkrak biaya HPP Anda."
                }
                else -> {
                    learnedPlayerProfile = "Seimbang dan disiplin → Pesaing menjaga margin masing-masing"
                }
            }
        }
    }

    fun initScenario(startInflation: Double, startPolicy: Double, startUnemployment: Double? = null) {
        inflation = startInflation
        inflationExpectation = startInflation
        policyRate = startPolicy
        shadowRate = startPolicy
        if (startUnemployment != null) unemployment = startUnemployment
    }

    fun unemploymentNow(): Double = unemployment
}
