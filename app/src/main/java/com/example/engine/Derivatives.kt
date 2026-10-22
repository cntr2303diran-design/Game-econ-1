package com.example.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * B4b: derivatif lindung nilai, semuanya diselesaikan tunai (tanpa mark-to-market di neraca).
 * Premi dibayar di muka lewat beban satu kali; penyelesaian masuk lewat pendapatan lain (negatif = rugi),
 * sehingga identitas neraca dan arus kas terjaga dengan konstruksi.
 *
 * Harga memuat marjin (loading) dan dibatasi pada eksposur nyata (lindung nilai saja, bukan spekulasi).
 */
enum class DerivKind(val code: String, val label: String, val months: Int) {
    FXOPT("fxo", "Opsi beli A$ (call)", 6),
    COTFUT("ctf", "Futures kapas (posisi beli)", 6),
    SWAP("swp", "Swap bunga (bayar tetap, terima mengambang)", 12);

    companion object { fun fromCode(c: String): DerivKind? = values().firstOrNull { it.code == c } }
}

class Deriv(
    val kind: DerivKind,
    val startTurn: Int,
    /** Giliran terakhir tempat penyelesaian terjadi (pada akhir giliran itu). */
    val expiryTurn: Int,
    /** Nilai pokok (jt RS) eksposur yang dilindungi. */
    val notional: Double,
    /** Kurs awal (opsi), strike indeks kapas (futures), atau suku bunga tetap % (swap). */
    val strike: Double,
    val premium: Double,
    val ref0: Double
)

object DerivPricing {
    const val FX_STRIKE = 1.03
    const val FX_DRIFT = 0.023          // depresiasi rata-rata 6 bulan yang terukur pada simulasi
    const val FX_LOADING = 1.4
    const val COT_LOADING = 1.02        // strike 2% di atas ekspektasi indeks 6 bulan (marjin harga futures)
    const val COT_FEE = 0.005
    const val SWAP_LOADING = 0.6        // poin % di atas ekspektasi rata-rata suku bunga kebijakan
    const val SWAP_ELECTION_ADD = 0.35  // tambahan selama pemilu (risiko lonjakan kurs lalu bunga) masih di depan
    const val SWAP_FEE = 0.001
    private const val COT_RHO = 0.96
    private const val COT_SIGMA = 0.058

    private fun pdf(x: Double) = exp(-0.5 * x * x) / sqrt(2.0 * Math.PI)
    private fun cdf(x: Double): Double {
        // Abramowitz-Stegun 7.1.26
        val t = 1.0 / (1.0 + 0.2316419 * abs(x))
        val poly = t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))))
        val c = 1.0 - pdf(x) * poly
        return if (x >= 0) c else 1.0 - c
    }

    /** Volatilitas bulanan kurs dari riwayat (minimal 0,8%). */
    fun monthlyVol(hist: List<Double>): Double {
        val n = hist.size
        if (n < 4) return 0.008
        val from = max(1, n - 12)
        val r = (from until n).map { ln(hist[it] / hist[it - 1]) }
        val m = r.average()
        val v = r.sumOf { (it - m) * (it - m) } / max(1, r.size - 1)
        return max(0.008, sqrt(v))
    }

    /** Premi opsi beli A$ sebagai fraksi dari pokok: (nilai Bachelier pada strike 103% + nilai harapan lonjakan kurs yang dikenal pasar) x loading + biaya tetap. */
    fun fxPremiumPct(monthlyVol: Double, jumpExpected: Double = 0.0): Double {
        val sT = monthlyVol * sqrt(6.0)
        val d = (FX_STRIKE - (1.0 + FX_DRIFT)) / sT
        val v = sT * (pdf(d) - d * (1.0 - cdf(d)))
        return (max(0.0, v) + jumpExpected) * FX_LOADING + 0.002
    }

    /** Strike futures kapas (indeks): ekspektasi indeks 6 bulan ke depan di bawah mean reversion, dikali loading. */
    fun cottonStrike(idx0: Double): Double {
        val v = COT_SIGMA * COT_SIGMA * (1.0 - StrictMath.pow(COT_RHO, 12.0)) / (1.0 - COT_RHO * COT_RHO)
        return exp(ln(idx0) * StrictMath.pow(COT_RHO, 6.0) + 0.5 * v) * COT_LOADING
    }

    /** Ekspektasi (terukur) selisih rata-rata suku bunga kebijakan 12 bulan ke depan terhadap sekarang. */
    fun swapExpectedDiff(r0: Double): Double = if (r0 < 6.4) 0.38 * (6.4 - r0) else -0.2 * (r0 - 6.4)

    fun swapFixedRate(policyRate: Double, electionAhead: Boolean = false): Double =
        policyRate + swapExpectedDiff(policyRate) + SWAP_LOADING + (if (electionAhead) SWAP_ELECTION_ADD else 0.0)
}
