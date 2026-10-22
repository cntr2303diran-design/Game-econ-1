package com.example.engine

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Buku pesanan agen sederhana (Tahap A6): 5.000 investor dalam empat kelas yang menilai tiap saham tiap bulan.
 * Harga bergerak menurut ketidakseimbangan pesanan beli dan jual, bukan lagi sentimen acak.
 *
 *  - Investor nilai: beli bila harga di bawah nilai wajar yang mereka persepsikan (dengan kesalahan penilaian per agen).
 *  - Pengikut tren: beli bila return tiga bulan terakhir positif (umpan balik positif yang dibatasi).
 *  - Investor ritel/sentimen: ikut kerumunan (ketidakseimbangan bulan lalu) dan suasana pasar.
 *  - Institusi: lot besar; sensitif terhadap suasana pasar dan indikator risk-off (panik, ketegangan geopolitik).
 *
 * Jumlah pembeli dan penjual ditarik dari sebaran binomial (ukuran sampel hingga), sehingga ada derau yang
 * wajar dan perhitungan tetap O(kelas) per saham. Seluruh keluaran deterministik terhadap Random yang diberikan.
 */
enum class InvestorClass(val label: String, val agents: Int, val unit: Double) {
    VALUE("investor nilai", 1600, 1.0),
    TREND("pengikut tren", 1400, 1.0),
    NOISE("investor ritel/sentimen", 1400, 0.4),
    INSTITUTION("institusi", 600, 3.0)
}

object InvestorPool {
    val TOTAL: Int = InvestorClass.values().sumOf { it.agents }
    val WEIGHT: Double = InvestorClass.values().sumOf { it.agents * it.unit }
}

/** Fungsi sebaran normal baku (aproksimasi Abramowitz–Stegun 26.2.17, galat < 7,5e-8). */
fun normalCdf(x: Double): Double {
    val z = abs(x)
    val t = 1.0 / (1.0 + 0.2316419 * z)
    val poly = t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))))
    val pdf = exp(-0.5 * z * z) / Math.sqrt(2.0 * Math.PI)
    val c = 1.0 - pdf * poly
    return if (x >= 0) c else 1.0 - c
}

class OrderFlow(val imbalance: Double, val byClass: DoubleArray, val dLnPrice: Double)

class InvestorBook {

    /** Ketidakseimbangan bulan lalu per sekuritas (kerumunan ritel). */
    private val lastImbalance = HashMap<String, Double>()

    companion object {
        /** Kepekaan harga terhadap ketidakseimbangan pesanan bersih (-1..1). */
        const val IMPACT = 0.30
        const val MAX_MOVE = 0.25
        const val VALUE_SIGMA = 0.20
        const val VALUE_HURDLE = 0.02
        const val TREND_SIGMA = 0.14
    }

    fun lastImbalanceOf(key: String): Double = lastImbalance[key] ?: 0.0

    private fun draw(r: Random, n: Int, pBuy: Double, pSell: Double): Int {
        val pb = pBuy.coerceIn(0.0, 1.0)
        val ps = pSell.coerceIn(0.0, 1.0 - pb)
        val buyers = binomialDraw(r, n, pb)
        val rest = n - buyers
        val sellers = if (rest <= 0 || pb >= 1.0) 0 else binomialDraw(r, rest, (ps / (1.0 - pb)).coerceIn(0.0, 1.0))
        return buyers - sellers // pesanan bersih dalam jumlah agen
    }

    /**
     * @param lnMis ln(nilai wajar yang dipersepsikan / harga); positif = murah.
     * @param ret3m rata-rata return log bulanan tiga bulan terakhir.
     * @param beta kepekaan sekuritas terhadap suasana pasar.
     * @param mood suasana pasar bersama (positif = optimis).
     * @param riskOff 0..1, tekanan risk-off dari panik dan geopolitik.
     */
    fun trade(key: String, r: Random, lnMis: Double, ret3m: Double, beta: Double, mood: Double, riskOff: Double): OrderFlow {
        val by = DoubleArray(4)
        val prev = lastImbalance[key] ?: 0.0
        for (c in InvestorClass.values()) {
            val net: Int = when (c) {
                InvestorClass.VALUE -> {
                    val pb = normalCdf((lnMis - VALUE_HURDLE) / VALUE_SIGMA)
                    val ps = normalCdf((-lnMis - VALUE_HURDLE) / VALUE_SIGMA)
                    draw(r, c.agents, pb, ps)
                }
                InvestorClass.TREND -> {
                    val up = normalCdf(ret3m / TREND_SIGMA)
                    draw(r, c.agents, 0.8 * up, 0.8 * (1.0 - up))
                }
                InvestorClass.NOISE -> {
                    val crowd = 0.35 * prev + 0.5 * mood * beta
                    val pb = 0.5 + 0.28 * tanh(2.0 * crowd)
                    draw(r, c.agents, pb, 1.0 - pb)
                }
                InvestorClass.INSTITUTION -> {
                    val a = 0.5 * mood * beta - 0.25 * riskOff + 0.15 * lnMis
                    val up = normalCdf(a / 0.12)
                    draw(r, c.agents, 0.9 * up, 0.9 * (1.0 - up))
                }
            }
            by[c.ordinal] = net * c.unit
        }
        val imbalance = (by.sum() / InvestorPool.WEIGHT).coerceIn(-1.0, 1.0)
        lastImbalance[key] = imbalance
        val d = (IMPACT * imbalance).coerceIn(-MAX_MOVE, MAX_MOVE)
        return OrderFlow(imbalance, by, d)
    }
}
