package com.example.engine

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Agregasi agen berbasis kohort (ember) untuk KAPITAL.
 *
 * Dokumen desain menargetkan ±207.000 agen dengan waktu proses giliran < 3 detik di HP kelas menengah.
 * Menyimpan satu objek (atau satu undian acak) per agen tidak perlu: agen yang sama kelas-nya dikelompokkan
 * ke dalam kohort, dan transisi status (mis. kehilangan/memperoleh kerja) diambil dari sebaran binomial per kohort.
 * Biaya per giliran menjadi O(jumlah kohort), bukan O(jumlah agen), sehingga skala agen naik tanpa memperlambat mesin.
 *
 * Semua undian memakai [Random] yang diberikan pemanggil (deterministik dari seed); tidak ada sumber acak lain.
 */

/** Sampel normal baku (Box–Muller) dari [r]. Selalu mengonsumsi tepat dua undian `nextDouble`. */
fun stdNormal(r: Random): Double {
    val u1 = max(r.nextDouble(), 1e-9)
    return sqrt(-2.0 * ln(u1)) * kotlin.math.cos(2.0 * Math.PI * r.nextDouble())
}

/**
 * Sampel Binomial(n, p) deterministik dari [r]. Jumlah undian bergantung pada n dan p saja (bukan pada nilai
 * undian sebelumnya), sehingga aliran acak tetap dapat diputar ulang.
 *  - n kecil: jumlah percobaan Bernoulli.
 *  - varians besar: hampiran normal dengan koreksi kontinuitas, dipangkas ke [0, n].
 *  - selainnya: inversi sekuensial (rata-rata kecil).
 */
fun binomialDraw(r: Random, n: Int, p: Double): Int {
    if (n <= 0 || p <= 0.0) return 0
    if (p >= 1.0) return n
    if (p > 0.5) return n - binomialDraw(r, n, 1.0 - p)
    if (n <= 40) {
        var k = 0
        repeat(n) { if (r.nextDouble() < p) k++ }
        return k
    }
    val mean = n * p
    val variance = mean * (1.0 - p)
    if (variance >= 9.0) {
        val k = (mean + sqrt(variance) * stdNormal(r)).roundToInt()
        return k.coerceIn(0, n)
    }
    // Inversi sekuensial: p <= 0.5 dan varians < 9 => n*p < 18, jadi (1-p)^n tidak underflow.
    val u = r.nextDouble()
    var pmf = (1.0 - p).pow(n)
    var cdf = pmf
    var k = 0
    val ratio = p / (1.0 - p)
    while (u > cdf && k < n) {
        pmf *= ratio * (n - k) / (k + 1)
        k++
        cdf += pmf
    }
    return k
}

/** Kebalikan fungsi sebaran normal baku (aproksimasi Acklam, galat relatif < 1,2e-9). */
fun normalQuantile(p: Double): Double {
    val a = doubleArrayOf(-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02, 1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00)
    val b = doubleArrayOf(-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02, 6.680131188771972e+01, -1.328068155288572e+01)
    val c = doubleArrayOf(-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00, -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00)
    val d = doubleArrayOf(7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00, 3.754408661907416e+00)
    val pl = 0.02425
    return when {
        p < pl -> {
            val q = sqrt(-2.0 * ln(p))
            (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0)
        }
        p <= 1.0 - pl -> {
            val q = p - 0.5
            val s = q * q
            (((((a[0] * s + a[1]) * s + a[2]) * s + a[3]) * s + a[4]) * s + a[5]) * q / (((((b[0] * s + b[1]) * s + b[2]) * s + b[3]) * s + b[4]) * s + 1.0)
        }
        else -> -normalQuantile(1.0 - p)
    }
}

/**
 * Sektor rumah tangga satu negara, diagregasi menjadi kohort pendapatan (kuantil sebaran log-normal).
 * Tiap kohort menyimpan jumlah agen bekerja; pendapatan dan kecenderungan konsumsi (MPC) per kohort tetap.
 *
 * @param agents jumlah agen rumah tangga (tiap agen mewakili ribuan orang; lihat dokumen desain bagian 5).
 */
class HouseholdSector(val agents: Int, initialUnemploymentPct: Double = 5.0, incomeSigma: Double = 0.55) {

    /** Banyak kohort: sekitar 400 agen per kohort, antara 8 dan 128. */
    val cohorts: Int = (agents / 400).coerceIn(8, 128)

    private val size = IntArray(cohorts)
    private val employed = IntArray(cohorts)
    private val income = DoubleArray(cohorts)
    private val mpc = DoubleArray(cohorts)

    init {
        require(agents >= cohorts) { "agen harus >= jumlah kohort" }
        val base = agents / cohorts
        val extra = agents % cohorts
        for (c in 0 until cohorts) {
            size[c] = base + if (c < extra) 1 else 0
            employed[c] = (size[c] * (1.0 - initialUnemploymentPct / 100.0)).roundToInt()
            // Pendapatan kohort = pendapatan di titik tengah kuantilnya (deterministik, tanpa undian).
            val z = normalQuantile((c + 0.5) / cohorts)
            income[c] = exp(incomeSigma * z)
            mpc[c] = (0.97 - 0.30 * (income[c] / (income[c] + 1.5))).coerceIn(0.55, 0.97)
        }
    }

    val employedCount: Int get() = employed.sum()

    fun unemploymentPct(): Double = 100.0 * (1.0 - employedCount.toDouble() / agents)

    /** Konsumsi mentah: jumlah pendapatan x MPC seluruh agen yang bekerja. */
    fun consumption(): Double {
        var s = 0.0
        for (c in 0 until cohorts) s += employed[c] * income[c] * mpc[c]
        return s
    }

    /**
     * Satu bulan transisi kerja: tiap agen bekerja kehilangan kerja dengan peluang [sep];
     * tiap agen menganggur memperoleh kerja dengan peluang [find]. Mengembalikan konsumsi setelah transisi.
     */
    fun step(r: Random, sep: Double, find: Double): Double {
        var s = 0.0
        for (c in 0 until cohorts) {
            val lost = binomialDraw(r, employed[c], sep)
            val found = binomialDraw(r, size[c] - employed[c], find)
            employed[c] += found - lost
            s += employed[c] * income[c] * mpc[c]
        }
        return s
    }
}

/**
 * Skala agen menurut dokumen desain (bagian 5): ±207.000 agen.
 * Konstanta di sini adalah TARGET desain; jumlah yang benar-benar disimulasikan dilaporkan
 * oleh [KapitalEngine.agentCensus] dari objek hidup, sehingga klaim skala bisa diverifikasi tes.
 */
object AgentScale {
    /** Rumah tangga per negara inti (total 200.000). Sundara = pasar asal pemain. */
    val HOUSEHOLDS: Map<String, Int> = linkedMapOf(
        "sundara" to 50_000,
        "aurelia" to 25_000,
        "norvaldia" to 10_000,
        "kestrel" to 40_000,
        "halvenia" to 15_000,
        "meridia" to 3_000,
        "tarsis" to 45_000,
        "velmora" to 12_000
    )
    const val INVESTORS = 5_000
    const val FIRMS = 2_000
    const val BANKS = 120
    const val GOVERNMENTS = 24
    const val CENTRAL_BANKS = 24

    val TOTAL_HOUSEHOLDS: Int get() = HOUSEHOLDS.values.sum()
    val TARGET_TOTAL: Int get() = TOTAL_HOUSEHOLDS + INVESTORS + FIRMS + BANKS + GOVERNMENTS + CENTRAL_BANKS
}
