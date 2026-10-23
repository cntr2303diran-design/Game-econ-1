package com.example.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * 12 sektor (dokumen desain bagian 4) dengan matriks input-output Leontief sederhana (bagian 8):
 *   x = (I - A)^-1 d
 * A[i][j] = nilai input sektor i yang dibutuhkan per satuan output sektor j. Kolom A berjumlah < 1 (sisanya nilai tambah),
 * sehingga (I - A) selalu dapat dibalik dan inversnya non-negatif (syarat Hawkins–Simon).
 *
 * Guncangan hulu (kapasitas turun akibat sanksi berlapis, blokade selat, bencana) merambat ke hilir lewat
 * ketersediaan input dan ke biaya lewat pass-through pangsa biaya.
 */
enum class Sector(val label: String, val tradable: Boolean) {
    PANGAN("Pangan", true),
    TEKSTIL("Tekstil", true),
    RITEL("Ritel", false),
    PROPERTI("Properti", false),
    KONSTRUKSI("Konstruksi", false),
    ENERGI("Energi", true),
    LOGISTIK("Logistik/pelayaran", true),
    MANUFAKTUR("Manufaktur", true),
    TEKNOLOGI("Teknologi", true),
    FARMASI("Farmasi", true),
    PERBANKAN("Perbankan", false),
    MEDIA("Media", false)
}

/** Simpul rantai pasok produk pemain. Tier 1 = pemasok langsung; Tier 2/3 = pemasok dari pemasok. */
class ChainNode(
    val id: String,
    val tier: Int,
    val sector: Sector,
    val country: String,
    val label: String,
    /** Pangsa biaya pemain yang bergantung pada simpul ini (tier 1) atau pada rantai di bawahnya. */
    val costShare: Double,
    /** Simpul yang diumpankan (id), kosong untuk tier 1. */
    val feeds: String? = null,
    /** Tersembunyi sampai pemain melakukan uji tuntas rantai pasok. */
    val hidden: Boolean = false
)

class SectorResult(
    /** Pengali biaya pemain dari kelangkaan/sanksi di rantai non-komoditas (1,0 = netral). */
    val playerCostMult: Double,
    /** Penyesuaian guncangan permintaan bulanan (<= 0 saat distribusi ritel terganggu). */
    val demandAdj: Double,
    /** Tambahan guncangan kredit (bank memperketat saat sanksi menyentuh rantai pemain). */
    val creditAdd: Double
)

class SectorModel {
    companion object {
        /** Kapasitas cadangan 8% di atas kebutuhan dasar: guncangan kecil terserap, yang besar menimbulkan kelangkaan. */
        const val SLACK = 1.08
    }

    private val n = Sector.values().size
    private val s = Sector.values()

    // A[i*n + j]: input sektor i per satuan output sektor j.
    private val a = DoubleArray(n * n)
    private val d0 = doubleArrayOf(0.90, 0.50, 1.20, 0.80, 0.70, 0.35, 0.25, 0.60, 0.55, 0.40, 0.50, 0.30)

    private val capacity = DoubleArray(n) { 1.0 }          // pengali kapasitas (1,0 = normal)
    private val shockMonths = IntArray(n)
    private val shockSize = DoubleArray(n)
    val availability = DoubleArray(n) { 1.0 }              // r_i: output tersedia / kebutuhan (0..1)
    val priceIndex = DoubleArray(n) { 1.0 }
    val outputIndex = DoubleArray(n) { 1.0 }               // output relatif terhadap keadaan dasar

    /** Pernah diuji-tuntas: rantai Tier-2/3 terlihat dan paparan sanksi tersembunyi dimitigasi. */
    var chainAudited = false

    private val x0: DoubleArray
    private val colSum = DoubleArray(n)
    private var lastNotedBand = IntArray(n)

    val chain: List<ChainNode> = listOf(
        ChainNode("t1_kapas", 1, Sector.PANGAN, "tarsis", "Pemasok kapas olahan", 0.35),
        ChainNode("t1_suku_cadang", 1, Sector.MANUFAKTUR, "kestrel", "Pemasok suku cadang mesin", 0.12),
        ChainNode("t1_listrik", 1, Sector.ENERGI, "sundara", "Listrik industri", 0.10),
        ChainNode("t1_logistik", 1, Sector.LOGISTIK, "sundara", "Ekspedisi dan pelabuhan", 0.08),
        ChainNode("t2_petani", 2, Sector.PANGAN, "tarsis", "Petani kapas", 0.35, feeds = "t1_kapas"),
        ChainNode("t2_baja", 2, Sector.MANUFAKTUR, "kestrel", "Pabrik baja dan komponen", 0.12, feeds = "t1_suku_cadang"),
        ChainNode("t2_chip", 2, Sector.TEKNOLOGI, "kestrel", "Modul kendali (chip)", 0.05, feeds = "t1_suku_cadang"),
        ChainNode("t2_gas", 2, Sector.ENERGI, "norvaldia", "Gas pembangkit", 0.10, feeds = "t1_listrik"),
        ChainNode("t3_pupuk", 3, Sector.PANGAN, "tarsis", "Pupuk dan air irigasi", 0.35, feeds = "t2_petani", hidden = true),
        ChainNode("t3_logam", 3, Sector.TEKNOLOGI, "velmora", "Logam tanah jarang untuk chip", 0.05, feeds = "t2_chip", hidden = true),
        ChainNode("t3_bunker", 3, Sector.ENERGI, "norvaldia", "Bahan bakar kapal", 0.08, feeds = "t1_logistik", hidden = true),
        ChainNode("t3_bijih", 3, Sector.MANUFAKTUR, "velmora", "Bijih logam dasar baja", 0.12, feeds = "t2_baja", hidden = true)
    )

    /** Pangsa pasokan global tiap sektor yang berada di suatu negara (hanya sektor yang bisa diperdagangkan). */
    private val geography: Map<Sector, Map<String, Double>> = mapOf(
        Sector.PANGAN to mapOf("tarsis" to 0.30, "sundara" to 0.10, "aurelia" to 0.15, "halvenia" to 0.10),
        Sector.ENERGI to mapOf("norvaldia" to 0.35, "aurelia" to 0.15, "velmora" to 0.08, "sundara" to 0.05),
        Sector.MANUFAKTUR to mapOf("kestrel" to 0.55, "aurelia" to 0.15, "halvenia" to 0.15, "velmora" to 0.08),
        Sector.TEKNOLOGI to mapOf("kestrel" to 0.55, "aurelia" to 0.25, "velmora" to 0.12),
        Sector.FARMASI to mapOf("halvenia" to 0.35, "aurelia" to 0.30, "meridia" to 0.05),
        Sector.TEKSTIL to mapOf("sundara" to 0.20, "kestrel" to 0.35, "tarsis" to 0.15),
        Sector.LOGISTIK to mapOf("kestrel" to 0.25, "halvenia" to 0.20, "meridia" to 0.10)
    )

    init {
        fun set(i: Sector, j: Sector, v: Double) { a[i.ordinal * n + j.ordinal] = v }
        // Kolom = sektor yang membeli input.
        for ((i, v) in listOf(Sector.ENERGI to 0.08, Sector.LOGISTIK to 0.06, Sector.MANUFAKTUR to 0.05, Sector.PERBANKAN to 0.03, Sector.PANGAN to 0.04)) set(i, Sector.PANGAN, v)
        for ((i, v) in listOf(Sector.PANGAN to 0.32, Sector.ENERGI to 0.09, Sector.LOGISTIK to 0.06, Sector.MANUFAKTUR to 0.07, Sector.PERBANKAN to 0.03, Sector.TEKNOLOGI to 0.02, Sector.TEKSTIL to 0.04)) set(i, Sector.TEKSTIL, v)
        for ((i, v) in listOf(Sector.TEKSTIL to 0.12, Sector.PANGAN to 0.18, Sector.MANUFAKTUR to 0.10, Sector.LOGISTIK to 0.08, Sector.ENERGI to 0.03, Sector.MEDIA to 0.02, Sector.TEKNOLOGI to 0.03, Sector.PERBANKAN to 0.02, Sector.RITEL to 0.02)) set(i, Sector.RITEL, v)
        for ((i, v) in listOf(Sector.KONSTRUKSI to 0.22, Sector.PERBANKAN to 0.06, Sector.ENERGI to 0.02, Sector.PROPERTI to 0.02)) set(i, Sector.PROPERTI, v)
        for ((i, v) in listOf(Sector.MANUFAKTUR to 0.25, Sector.ENERGI to 0.06, Sector.LOGISTIK to 0.06, Sector.PROPERTI to 0.02, Sector.TEKNOLOGI to 0.02, Sector.PERBANKAN to 0.03)) set(i, Sector.KONSTRUKSI, v)
        for ((i, v) in listOf(Sector.MANUFAKTUR to 0.06, Sector.LOGISTIK to 0.05, Sector.TEKNOLOGI to 0.02, Sector.PERBANKAN to 0.03, Sector.ENERGI to 0.05)) set(i, Sector.ENERGI, v)
        for ((i, v) in listOf(Sector.ENERGI to 0.22, Sector.MANUFAKTUR to 0.12, Sector.TEKNOLOGI to 0.03, Sector.PERBANKAN to 0.03)) set(i, Sector.LOGISTIK, v)
        for ((i, v) in listOf(Sector.ENERGI to 0.14, Sector.MANUFAKTUR to 0.22, Sector.LOGISTIK to 0.07, Sector.TEKNOLOGI to 0.05, Sector.PERBANKAN to 0.03)) set(i, Sector.MANUFAKTUR, v)
        for ((i, v) in listOf(Sector.MANUFAKTUR to 0.22, Sector.ENERGI to 0.05, Sector.TEKNOLOGI to 0.15, Sector.LOGISTIK to 0.03, Sector.PERBANKAN to 0.02)) set(i, Sector.TEKNOLOGI, v)
        for ((i, v) in listOf(Sector.PANGAN to 0.05, Sector.MANUFAKTUR to 0.12, Sector.ENERGI to 0.04, Sector.TEKNOLOGI to 0.05, Sector.LOGISTIK to 0.04, Sector.PERBANKAN to 0.02)) set(i, Sector.FARMASI, v)
        for ((i, v) in listOf(Sector.TEKNOLOGI to 0.08, Sector.ENERGI to 0.01, Sector.MEDIA to 0.01, Sector.PROPERTI to 0.03)) set(i, Sector.PERBANKAN, v)
        for ((i, v) in listOf(Sector.TEKNOLOGI to 0.10, Sector.ENERGI to 0.02, Sector.PERBANKAN to 0.02, Sector.MEDIA to 0.05)) set(i, Sector.MEDIA, v)
        for (j in 0 until n) colSum[j] = (0 until n).sumOf { a[it * n + j] }
        x0 = leontief(d0)
    }

    /** Kolom A harus berjumlah < 1 agar (I - A) dapat dibalik. */
    fun maxColumnSum(): Double = colSum.max()

    /** x = (I - A)^-1 d lewat eliminasi Gauss dengan pivot parsial. */
    fun leontief(d: DoubleArray): DoubleArray {
        val m = Array(n) { i -> DoubleArray(n + 1) { j -> if (j == n) d[i] else (if (i == j) 1.0 else 0.0) - a[i * n + j] } }
        for (c in 0 until n) {
            var p = c
            for (r in c + 1 until n) if (abs(m[r][c]) > abs(m[p][c])) p = r
            val tmp = m[c]; m[c] = m[p]; m[p] = tmp
            val piv = m[c][c]
            for (k in c until n + 1) m[c][k] /= piv
            for (r in 0 until n) if (r != c) {
                val f = m[r][c]
                if (f != 0.0) for (k in c until n + 1) m[r][k] -= f * m[c][k]
            }
        }
        return DoubleArray(n) { m[it][n] }
    }

    fun baselineOutput(): DoubleArray = x0.copyOf()

    /** Koefisien teknis A[i][j]: input sektor [i] per satuan output sektor [j]. */
    fun coefficient(i: Sector, j: Sector): Double = a[i.ordinal * n + j.ordinal]

    fun baselineFinalDemand(): DoubleArray = d0.copyOf()

    /** Pangsa pasokan global sektor [sec] yang terletak di negara [country]. */
    fun supplyShare(sec: Sector, country: String): Double = geography[sec]?.get(country) ?: 0.0

    /** Guncangan kapasitas sektoral eksternal (mis. bencana); pulih bertahap. */
    fun applyShock(sec: Sector, size: Double, months: Int) {
        val i = sec.ordinal
        if (shockMonths[i] == 0 || size > shockSize[i]) { shockSize[i] = size; shockMonths[i] = max(shockMonths[i], months) }
    }

    /** Node rantai yang terlihat oleh pemain saat ini. */
    fun visibleChain(): List<ChainNode> = chain.filter { !it.hidden || chainAudited }

    /** Pangsa biaya rantai pemain yang bergantung pada [country] di tingkat mana pun (termasuk simpul tersembunyi). */
    fun chainExposure(country: String): Double =
        chain.filter { it.country == country }.sumOf { if (it.tier == 1) it.costShare else it.costShare * 0.5 } .coerceAtMost(1.0)

    fun step(
        r: Random,
        gap: Double,
        rateDrag: Double,
        geo: Geopolitics,
        climateStress: Double,
        notes: MutableList<DuniaNote>,
        /** Ketahanan energi 0..0,4 (energi terbarukan): meredam dampak sanksi dan selat energi. */
        energyResilience: Double = 0.0
    ): SectorResult {
        // 1. Kapasitas: pulih bertahap dari guncangan; guncangan acak sektoral; sanksi; selat.
        val tension = geo.meanTension()
        for (i in 0 until n) {
            if (shockMonths[i] > 0) shockMonths[i]-- else shockSize[i] *= 0.7
            val pRand = 0.0035 * (1.0 + (tension - 30.0).coerceAtLeast(0.0) / 25.0) * (if (s[i] == Sector.PANGAN) 1.0 + 2.0 * climateStress else 1.0)
            if (shockMonths[i] == 0 && shockSize[i] < 0.02 && r.nextDouble() < pRand) {
                shockSize[i] = 0.10 + 0.20 * r.nextDouble()
                shockMonths[i] = 4 + r.nextInt(7)
                notes += DuniaNote("Gangguan produksi di sektor ${s[i].label}: kapasitas turun ${(shockSize[i] * 100).toInt()}% beberapa bulan; sektor hilir ikut terimbas.",
                    Tone.WARN, "Gangguan sektor ${s[i].label}", "Guncangan hulu → kapasitas sektor turun → ketersediaan dan biaya sektor hilir memburuk.")
            }
        }
        val capHit = DoubleArray(n)
        for (i in 0 until n) capHit[i] = shockSize[i]
        for (sn in geo.sanctions) for (sec in sn.sectors) {
            capHit[sec.ordinal] += 0.28 * sn.severity * supplyShare(sec, sn.target) * (if (sec == Sector.ENERGI) 1.0 - energyResilience else 1.0)
        }
        val closureEnergi = geo.strait("selat_energi"); val closureChip = geo.strait("selat_chip"); val closurePangan = geo.strait("selat_pangan")
        capHit[Sector.ENERGI.ordinal] += 0.30 * closureEnergi * (1.0 - energyResilience)
        capHit[Sector.TEKNOLOGI.ordinal] += 0.35 * closureChip
        capHit[Sector.MANUFAKTUR.ordinal] += 0.30 * closureChip
        capHit[Sector.PANGAN.ordinal] += 0.25 * closurePangan
        capHit[Sector.LOGISTIK.ordinal] += 0.20 * (closureEnergi + closureChip + closurePangan).coerceAtMost(1.5)
        for (i in 0 until n) capacity[i] = (1.0 - capHit[i]).coerceIn(0.15, 1.0)

        // 2. Permintaan akhir: sektor konsumsi mengikuti kesenjangan output; properti/konstruksi peka suku bunga.
        val d = DoubleArray(n)
        for (i in 0 until n) {
            val mult = when (s[i]) {
                Sector.RITEL, Sector.PANGAN, Sector.TEKSTIL, Sector.FARMASI, Sector.MEDIA -> 1.0 + 1.1 * gap
                Sector.PROPERTI, Sector.KONSTRUKSI -> 1.0 + 1.6 * gap + 0.6 * rateDrag
                else -> 1.0 + 0.5 * gap
            }
            d[i] = d0[i] * mult.coerceIn(0.5, 1.5)
        }
        val need = leontief(d)

        // 3. Ketersediaan: r_i = min(1, kapasitas / kebutuhan), dikurangi kekurangan input berbobot pangsa biaya (iteratif).
        val rr = DoubleArray(n) { 1.0 }
        val phi = 0.8
        for (iter in 0 until 25) {
            var delta = 0.0
            for (j in 0 until n) {
                var shortfall = 0.0
                if (colSum[j] > 0.0) for (i in 0 until n) if (i != j) shortfall += a[i * n + j] / colSum[j] * (1.0 - rr[i])
                val effCap = SLACK * capacity[j] * (1.0 - phi * shortfall).coerceIn(0.0, 1.0)
                val newR = min(1.0, effCap * x0[j] / max(need[j], 1e-9)).coerceIn(0.0, 1.0)
                delta = max(delta, abs(newR - rr[j]))
                rr[j] = newR
            }
            if (delta < 1e-7) break
        }
        for (j in 0 until n) {
            availability[j] = rr[j]
            outputIndex[j] = (min(need[j], capacity[j] * x0[j]) / x0[j]) * 1.0
        }

        // 4. Harga: kelangkaan sendiri + pass-through biaya input; harga lengket (inersia 0,6).
        val target = DoubleArray(n) { 1.0 + 1.2 * (1.0 - rr[it]) }
        for (iter in 0 until 6) {
            for (j in 0 until n) {
                var cost = 1.0
                for (i in 0 until n) if (i != j) cost += a[i * n + j] * (target[i] - 1.0)
                target[j] = max(1.0 + 1.2 * (1.0 - rr[j]), cost.coerceAtMost(2.5))
            }
        }
        for (j in 0 until n) priceIndex[j] = (0.6 * priceIndex[j] + 0.4 * target[j]).coerceIn(0.7, 3.0)

        // 5. Peristiwa: laporkan saat ketersediaan sektor turun melewati pita 0,92 / 0,80 / 0,60.
        for (j in 0 until n) {
            val band = when { rr[j] < 0.60 -> 3; rr[j] < 0.80 -> 2; rr[j] < 0.92 -> 1; else -> 0 }
            if (band > lastNotedBand[j] && (s[j].tradable || band >= 2)) {
                val cause = rootCause(j, capHit, rr)
                notes += DuniaNote(
                    "Kelangkaan di sektor ${s[j].label} (ketersediaan ${(rr[j] * 100).toInt()}%, harga ${(priceIndex[j] * 100 - 100).toInt()}% di atas normal): $cause",
                    if (band >= 2) Tone.BAD else Tone.WARN,
                    if (band >= 2) "Kelangkaan sektor ${s[j].label}" else null,
                    "Guncangan hulu → ketersediaan input turun → output sektor hilir turun → harga naik.")
            }
            lastNotedBand[j] = band
        }

        // 6. Dampak ke pemain: biaya dari rantai non-komoditas, paparan sanksi Tier-1..3, kendala distribusi ritel.
        fun p(sec: Sector) = priceIndex[sec.ordinal]
        var costMult = 1.0 + 0.12 * (p(Sector.LOGISTIK) - 1.0) + 0.15 * (p(Sector.MANUFAKTUR) - 1.0) +
            0.15 * (p(Sector.ENERGI) - 1.0) + 0.20 * (p(Sector.PANGAN) - 1.0) + 0.03 * (p(Sector.TEKNOLOGI) - 1.0)
        var creditAdd = 0.0
        val mitigation = if (chainAudited) 0.55 else 0.0
        for (nd in chain) {
            val sns = geo.sanctions.filter { it.target == nd.country && nd.sector in it.sectors }
            if (sns.isEmpty()) continue
            val sev = sns.maxOf { it.severity }
            val weight = if (nd.tier == 1) nd.costShare else nd.costShare * 0.5
            val hiddenHit = nd.hidden && !chainAudited
            costMult += weight * 0.30 * sev * (1.0 - mitigation)
            if (hiddenHit) creditAdd += 0.03 * sev   // bank menaikkan premi risiko atas paparan sanksi yang tidak diungkap
        }
        costMult = costMult.coerceIn(0.97, 1.25)
        val demandAdj = (-0.06 * (1.0 - rr[Sector.RITEL.ordinal])).coerceIn(-0.03, 0.0)
        return SectorResult(costMult, demandAdj, creditAdd.coerceAtMost(0.25))
    }

    private fun rootCause(j: Int, capHit: DoubleArray, rr: DoubleArray): String {
        // Cari sektor dengan guncangan kapasitas sendiri terbesar di antara pemasok langsung (atau dirinya sendiri).
        var best = j; var bestHit = capHit[j]
        for (i in 0 until n) if (a[i * n + j] >= 0.04 && capHit[i] > bestHit) { best = i; bestHit = capHit[i] }
        return if (best == j) "pemicu: pembatasan kapasitas di sektor itu sendiri."
        else "pemicu: pembatasan kapasitas di sektor hulu ${s[best].label}."
    }

    /** Sanksi pada negara tertentu yang mengenai simpul rantai pemain (untuk laporan dan uji). */
    fun exposedNodes(geo: Geopolitics): List<ChainNode> =
        chain.filter { nd -> geo.sanctions.any { it.target == nd.country && nd.sector in it.sectors } }
}
