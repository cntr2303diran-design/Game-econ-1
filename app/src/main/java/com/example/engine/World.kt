package com.example.engine

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Lapisan dunia berbasis agen untuk KAPITAL.
 *
 * Agen yang benar-benar disimulasikan:
 *  - [households] rumah tangga (status kerja, pendapatan, kecenderungan konsumsi). Mereka kehilangan dan
 *    menemukan pekerjaan lewat proses pencocokan; konsumsi gabungan mereka adalah permintaan agregat.
 *  - 4 pesaing tekstil dengan fungsi reaksi harga sendiri (menyamai potongan harga, menaikkan harga
 *    bila biaya naik, dan satu importir yang sesekali membanting harga).
 *  - 1 bank sentral dengan aturan Taylor yang memakai inflasi, kesenjangan output, dan depresiasi kurs.
 *
 * Inflasi, suku bunga, pengangguran, dan permintaan TIDAK diskrip: semuanya muncul dari interaksi agen.
 * Semua keacakan berasal dari [Random] yang diberikan mesin (deterministik per seed dan giliran).
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

    class Rival(
        val id: String,
        val name: String,
        /** Pangsa pasar awal (juga bobot kualitas/merek). */
        val share0: Double,
        /** 0..1: seberapa cepat menyamai potongan harga lawan. */
        val aggression: Double,
        /** Biaya satuan relatif terhadap harga pasar awal (1,0 = impas). Makin kecil makin efisien. */
        val unitCost: Double
    ) {
        /** Harga relatif terhadap tingkat harga umum (1,0 = harga pasar awal). */
        var price = 1.0
        var weakMonths = 0
        var active = true
    }

    val rivals = listOf(
        Rival("riv_mitra", "Mitra Tekstil Nusantara", 0.30, 0.35, 0.89),
        Rival("riv_cahaya", "Cahaya Weaving", 0.25, 0.55, 0.92),
        Rival("riv_prima", "Prima Garmen Utama", 0.15, 0.25, 0.95),
        Rival("riv_importir", "Importir Velmora", 0.10, 0.90, 0.83)
    )
    private val playerShare0 = 0.20

    // ---- Rumah tangga ----
    private val employed = BooleanArray(households)
    private val income = DoubleArray(households)
    private val mpc = DoubleArray(households)
    private var baseConsumption = 1.0

    // ---- Keadaan makro ----
    var demand = 1.0; private set          // indeks permintaan (1,0 = tren)
    var gap = 0.0; private set             // kesenjangan output (pecahan)
    var unemployment = 5.0; private set    // %
    var inflation = 5.0; private set       // % tahunan
    var inflationExpectation = 5.0; private set
    var policyRate = 6.25; private set
    private var shadowRate = 6.25
    private var confidence = 0.0           // guncangan "naluri hewani" AR(1)
    private val kursHistory = ArrayDeque<Double>()
    var dumpingMonthsLeft = 0; private set
    var lastEvents: List<String> = emptyList(); private set
    /** Suku bunga yang disarankan aturan Taylor bulan ini (dipakai berita dan peringatan dini). */
    var taylorStar = 6.5; private set

    init {
        // Populasi awal deterministik (tidak bergantung seed agar dunia awal sama untuk semua pemain)
        val r = Random(20330301L)
        for (i in 0 until households) {
            employed[i] = r.nextDouble() > 0.05
            val z = gauss(r)
            income[i] = exp(0.55 * z)            // lognormal, rata-rata ±1,16
            mpc[i] = (0.97 - 0.30 * (income[i] / (income[i] + 1.5))).coerceIn(0.55, 0.97)
        }
        baseConsumption = rawConsumption()
    }

    private fun gauss(r: Random): Double {
        val u1 = max(r.nextDouble(), 1e-9)
        return sqrt(-2.0 * ln(u1)) * kotlin.math.cos(2.0 * Math.PI * r.nextDouble())
    }

    private fun rawConsumption(): Double {
        var s = 0.0
        for (i in 0 until households) if (employed[i]) s += income[i] * mpc[i]
        return s
    }

    // =====================================================================
    // Pasar produk: pangsa pemain sebagai fungsi harga relatif
    // =====================================================================
    private fun weight(share0: Double, relPrice: Double) = share0 * relPrice.coerceAtLeast(0.3).pow(-PRICE_SENSITIVITY)

    /** Pangsa pemain pada harga relatif tertentu (1,0 = harga pasar). */
    fun playerShare(relPrice: Double): Double {
        val wp = weight(playerShare0, relPrice)
        val wr = rivals.filter { it.active }.sumOf { weight(it.share0, it.price) }
        return wp / (wp + wr)
    }

    /**
     * Faktor volume pemain relatif terhadap kondisi awal (1,0 = pangsa awal 20%).
     * Bila pesaing keluar pasar, volumenya dibagi ulang ke yang tersisa; total pasar tetap.
     */
    fun volumeFactor(relPrice: Double): Double = playerShare(relPrice) / playerShare0

    /** Harga pesaing rata-rata tertimbang relatif terhadap harga pasar awal. */
    fun rivalAveragePrice(): Double {
        val a = rivals.filter { it.active }
        if (a.isEmpty()) return 1.0
        return a.sumOf { it.share0 * it.price } / a.sumOf { it.share0 }
    }

    // =====================================================================
    // Satu bulan
    // =====================================================================
    class Output(
        val demand: Double,
        val inflationAnnual: Double,
        val policyRate: Double,
        val unemployment: Double,
        val events: List<String>,
        val policyChanged: Boolean
    )

    /**
     * @param playerRelPrice harga pemain relatif terhadap harga pasar (1 + penyesuaian harga)
     * @param costPressure   biaya satuan industri relatif (kurs dan biaya input)
     * @param priceLevel     tingkat harga jual paritas impor (naik bila rial melemah)
     * @param kursChangeMonthly perubahan kurs bulan ini (pecahan, positif = rial melemah)
     * @param kurs           kurs saat ini (untuk riwayat)
     * @param extraDemandShock guncangan permintaan eksogen bulan ini (mis. pemilu)
     */
    fun step(
        r: Random,
        playerRelPrice: Double,
        costPressure: Double,
        priceLevel: Double,
        kursChangeMonthly: Double,
        kurs: Double,
        extraDemandShock: Double = 0.0
    ): Output {
        val events = mutableListOf<String>()

        // ---- 1. Guncangan permintaan (kepercayaan konsumen) ----
        confidence = 0.85 * confidence + gauss(r) * 0.010 + extraDemandShock
        val realRate = policyRate - inflation
        val rateDrag = -0.012 * (realRate - neutralRealRate)   // suku bunga riil tinggi menekan konsumsi

        // ---- 2. Pasar tenaga kerja: pencocokan pada tingkat pekerja ----
        val gapLag = gap
        val sep = (SEP_BASE * (1.0 - 4.0 * gapLag)).coerceIn(0.004, 0.045)
        val find = (FIND_BASE * (1.0 + 4.0 * gapLag)).coerceIn(0.08, 0.60)
        var emp = 0
        var consumption = 0.0
        for (i in 0 until households) {
            if (employed[i]) { if (r.nextDouble() < sep) employed[i] = false }
            else { if (r.nextDouble() < find) employed[i] = true }
            if (employed[i]) {
                emp++
                consumption += income[i] * mpc[i]
            }
        }
        unemployment = 100.0 * (1.0 - emp.toDouble() / households)

        // ---- 3. Permintaan agregat = konsumsi rumah tangga * kepercayaan * efek suku bunga riil ----
        demand = ((consumption / baseConsumption) * (1.0 + confidence + rateDrag)).coerceIn(0.80, 1.20)
        gap = demand - 1.0

        // ---- 4. Pesaing: reaksi harga ----
        if (dumpingMonthsLeft == 0 && r.nextDouble() < 0.012) {
            dumpingMonthsLeft = 6 + r.nextInt(5)
            events += "Importir Velmora membanjiri pasar dengan kain murah (dumping): harga mereka turun sekitar 15% selama beberapa bulan."
        }
        for (rv in rivals) {
            if (!rv.active) continue
            val ownCost = costPressure.coerceIn(0.7, 1.6) * rv.unitCost
            // Harga jangka panjang mengikuti tingkat biaya industri; permintaan kuat memberi ruang naik
            var target = priceLevel.coerceIn(0.7, 1.6) * (1.0 + 0.6 * gap)
            if (rv.id == "riv_importir" && dumpingMonthsLeft > 0) target *= 0.85
            // Menyamai bila pemain lebih murah (sesuai agresivitas); tidak mengikuti kenaikan harga pemain
            val undercut = playerRelPrice - rv.price
            var move = 0.30 * (target - rv.price)
            if (undercut < 0.0) move += rv.aggression * 0.75 * undercut
            rv.price = (rv.price + move + gauss(r) * 0.002).coerceIn(0.6, 1.8)
            val margin = rv.price / max(0.3, ownCost) - 1.0
            rv.weakMonths = if (margin < 0.0) rv.weakMonths + 1 else max(0, rv.weakMonths - 1)
            if (rv.weakMonths >= 9 && rivals.count { it.active } > 2) {
                rv.active = false
                events += "${rv.name} bangkrut dan keluar dari pasar setelah berbulan-bulan menjual di bawah biaya."
            }
        }
        if (dumpingMonthsLeft > 0) {
            dumpingMonthsLeft--
            if (dumpingMonthsLeft == 0) events += "Banjir impor murah mereda; harga importir kembali normal."
        }
        if (rivals.any { !it.active } && gap > 0.0 && r.nextDouble() < 0.04) {
            val dead = rivals.first { !it.active }
            dead.active = true; dead.weakMonths = 0; dead.price = 1.0
            events += "Pemain baru masuk menggantikan ${dead.name} karena margin industri kembali menarik."
        }

        // ---- 5. Inflasi: kurva Phillips + pass-through kurs ----
        // 7% dari depresiasi kurs di atas tren (0,3%/bulan) menular ke inflasi tahunan
        val fxExcess = 0.07 * 12.0 * 100.0 * (kursChangeMonthly - 0.002)
        val demandPush = 22.0 * gap
        val pressure = inflationExpectation + demandPush + fxExcess
        inflation = (0.80 * inflation + 0.20 * pressure + gauss(r) * 0.15).coerceIn(-1.0, 25.0)
        inflationExpectation = 0.90 * inflationExpectation + 0.06 * inflation + 0.04 * inflationTarget

        // ---- 6. Bank sentral: aturan Taylor, langkah 25 bp, dengan kelembaman ----
        kursHistory.addLast(kurs); if (kursHistory.size > 4) kursHistory.removeFirst()
        val depr3m = if (kursHistory.size >= 4) (kurs / kursHistory.first() - 1.0) * 100.0 else 0.0
        val star = neutralRealRate + inflation + 0.5 * (inflation - inflationTarget) +
            0.5 * (gap * 100.0) + 0.12 * max(0.0, depr3m - 1.5)
        taylorStar = star
        shadowRate += (0.30 * (star - shadowRate)).coerceIn(-0.5, 0.5)
        shadowRate = shadowRate.coerceIn(2.0, 14.0)
        val newPolicy = Math.round(shadowRate / 0.25) * 0.25
        val changed = newPolicy != policyRate
        policyRate = newPolicy

        lastEvents = events
        return Output(demand, inflation, policyRate, unemployment, events, changed)
    }

    /** Mengatur ulang awal skenario (inflasi dan suku bunga awal Sundara 2033). */
    fun initScenario(startInflation: Double, startPolicy: Double, startUnemployment: Double? = null) {
        inflation = startInflation
        inflationExpectation = startInflation
        policyRate = startPolicy
        shadowRate = startPolicy
        if (startUnemployment != null) unemployment = startUnemployment
    }

    fun unemploymentNow(): Double = unemployment
}
