package com.example.engine

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Tahap B2: pasar produk. Permintaan agregat satu produk diganti lima segmen konsumen yang punya tren sendiri,
 * kurva adopsi jangka panjang, kepekaan terhadap merek, dan tingkat fokus pemain.
 *
 * Faktor pendapatan = Σ bobot × tren × adopsi × cakupan × (1 + efek merek). Pada keadaan awal faktor ini 1,0 (tidak
 * menggeser kalibrasi lama); tren memiliki rata-rata 1,0, jadi pemain pasif tidak diuntungkan maupun dirugikan secara
 * sistematis. Pemain bisa memfokuskan segmen (biaya pemasaran bulanan, merek naik), mengabaikan segmen (cakupan turun),
 * dengan batas total tingkat fokus. Kesempatan datang dari membaca tren lebih dulu (survei berderau, riset B1).
 */
enum class Segment(
    val code: String, val label: String, val weight: Double, val brandSens: Double,
    val trendSd: Double, val adoptGain: Double, val adoptT0: Int, val adoptK: Double
) {
    HEMAT("hemat", "Pasar rakyat dan konsumen hemat", 0.30, 0.4, 0.012, -0.10, 240, 0.020),
    KELUARGA("keluarga", "Keluarga menengah", 0.28, 0.9, 0.012, 0.00, 200, 0.020),
    MUDA("muda", "Pekerja muda perkotaan dan belanja daring", 0.18, 1.3, 0.018, 0.30, 200, 0.025),
    MODE("mode", "Mode dan tren musiman", 0.12, 1.8, 0.030, 0.10, 260, 0.020),
    INSTITUSI("institusi", "Seragam institusi dan pemerintah", 0.12, 0.6, 0.007, 0.05, 300, 0.020);

    companion object { fun of(code: String): Segment? = values().firstOrNull { it.code == code } }
}

class ProductMarket {
    val trend = DoubleArray(Segment.values().size) { 1.0 }
    val level = IntArray(Segment.values().size) { 1 }
    val lastChangeTurn = IntArray(Segment.values().size) { -99 }
    /** Survei konsumen bulanan: tren + derau (yang dilihat pemain tanpa riset berbayar). */
    val survey = DoubleArray(Segment.values().size) { 1.0 }
    var brand = 40.0; private set

    companion object {
        const val COOLDOWN = 6
        const val BRAND_NEUTRAL = 40.0
        const val SURVEY_NOISE = 0.07
        /** Cakupan menurut tingkat fokus: 0 mengabaikan, 1 melayani, 2 fokus. */
        fun coverage(level: Int): Double = when (level) { 0 -> 0.85; 2 -> 1.18; else -> 1.0 }
        /** Batas total tingkat fokus: 6 di Era 1, +1 tiap era berikutnya. */
        fun focusCap(era: Int): Int = 5 + era
        /** Biaya pemasaran bulanan satu segmen berfokus (fraksi pendapatan dasar bulanan per bobot 0,2). */
        const val FOCUS_COST_RATE = 0.0100
    }

    private fun adoption(s: Segment, turn: Int): Double = 1.0 / (1.0 + exp(-s.adoptK * (turn - s.adoptT0)))

    /** Pengali jangka panjang dari kurva adopsi, relatif terhadap giliran 0 (netral di awal permainan). */
    fun secular(s: Segment, turn: Int): Double = 1.0 + s.adoptGain * (adoption(s, turn) - adoption(s, 0))

    fun step(turn: Int, r: Random) {
        for (s in Segment.values()) {
            val i = s.ordinal
            trend[i] = (1.0 + 0.97 * (trend[i] - 1.0) + s.trendSd * stdNormal(r)).coerceIn(0.6, 1.5)
            survey[i] = (trend[i] + SURVEY_NOISE * stdNormal(r)).coerceIn(0.4, 1.7)
        }
        val focused = level.count { it == 2 }
        brand = (brand + 0.35 * focused - 0.01 * (brand - BRAND_NEUTRAL)).coerceIn(0.0, 100.0)
    }

    fun segmentFactor(s: Segment, turn: Int): Double =
        trend[s.ordinal] * secular(s, turn) * coverage(level[s.ordinal]) * (1.0 + s.brandSens * 0.0002 * (brand - BRAND_NEUTRAL))

    /** Pengali pendapatan keseluruhan; 1,0 pada keadaan awal. */
    fun revenueFactor(turn: Int): Double = Segment.values().sumOf { it.weight * segmentFactor(it, turn) }

    fun focusUsed(): Int = level.sum()

    /** Biaya pemasaran bulanan (jt RS) untuk seluruh segmen berfokus. */
    fun monthlyCost(baseRevenue: Double, costIndex: Double): Double =
        Segment.values().filter { level[it.ordinal] == 2 }.sumOf { FOCUS_COST_RATE * baseRevenue * (it.weight / 0.2) * costIndex }
}
