package com.example.engine

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Pasar komoditas dinamis: kapas, minyak, baja. Log-harga mengikuti proses mean-reverting (AR(1))
 * dengan volatilitas bulanan khas komoditas (kapas ~25%/tahun, minyak ~35%, baja ~18%),
 * dipengaruhi permintaan global (kesenjangan output), guncangan pasokan, dan krisis (El Niño, Selat Norvaldia).
 * Indeks 1,0 = harga awal skenario.
 */
class Commodities {
    class Series(val name: String, val base: Double, val rho: Double, val sigmaMonthly: Double, val demandBeta: Double) {
        var logDev = 0.0
        var supplyShockMonths = 0
        var supplyShockSize = 0.0
        val history = ArrayDeque<Double>()
        val index: Double get() = exp(logDev)
        val price: Double get() = base * index
    }

    val cotton = Series("kapas", 1850.0, 0.96, 0.058, 1.2)
    val oil = Series("minyak", 84.5, 0.95, 0.08, 2.0)
    val steel = Series("baja", 760.0, 0.97, 0.052, 1.0)
    val all get() = listOf(cotton, oil, steel)
    var lastEvents: List<String> = emptyList(); private set

    init {
        // Riwayat 6 bulan terakhir sebelum skenario (sama dengan data awal di layar Pasar)
        val h = mapOf(
            cotton to listOf(1680.0, 1710.0, 1695.0, 1740.0, 1790.0, 1850.0),
            oil to listOf(74.0, 76.0, 75.5, 78.0, 80.2, 84.5),
            steel to listOf(690.0, 700.0, 715.0, 710.0, 730.0, 760.0)
        )
        for ((s, v) in h) v.forEach { s.history.addLast(it) }
    }

    private fun gauss(r: Random): Double {
        val u1 = max(r.nextDouble(), 1e-9)
        return sqrt(-2.0 * ln(u1)) * kotlin.math.cos(2.0 * Math.PI * r.nextDouble())
    }

    /** @param gap kesenjangan output global; @param kursChangeMonthly depresiasi RS (komoditas dihargai A$). */
    fun step(r: Random, gap: Double): List<String> {
        val events = mutableListOf<String>()
        // Guncangan pasokan acak: kapas (El Niño), minyak (blokade selat)
        if (cotton.supplyShockMonths == 0 && r.nextDouble() < 0.012) {
            cotton.supplyShockMonths = 8; cotton.supplyShockSize = 0.22
            events += "El Niño menekan panen kapas Tarsis: harga kapas diperkirakan naik dalam beberapa bulan."
        }
        if (oil.supplyShockMonths == 0 && r.nextDouble() < 0.008) {
            oil.supplyShockMonths = 6; oil.supplyShockSize = 0.28
            events += "Ketegangan di Selat Norvaldia mengganggu pasokan minyak: harga energi melonjak."
        }
        for (s in all) {
            var shock = 0.0
            if (s.supplyShockMonths > 0) { shock = s.supplyShockSize / 8.0; s.supplyShockMonths-- }
            s.logDev = s.rho * s.logDev + s.demandBeta * 0.15 * gap + shock + gauss(r) * s.sigmaMonthly
            s.logDev = s.logDev.coerceIn(-0.9, 1.1)
            s.history.addLast(s.price); if (s.history.size > 6) s.history.removeFirst()
        }
        lastEvents = events
        return events
    }

    /** Guncangan pasokan kapas buatan pesaing (memborong spot). */
    fun triggerCorneringShock(months: Int, size: Double) {
        cotton.supplyShockMonths = max(cotton.supplyShockMonths, months)
        cotton.supplyShockSize = max(cotton.supplyShockSize, size * 8.0 / months)
    }

    /** Indeks biaya bahan (kapas 78%, energi 22%) terhadap awal skenario. */
    fun materialIndex(): Double = 0.78 * cotton.index + 0.22 * oil.index
}
