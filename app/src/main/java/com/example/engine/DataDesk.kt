package com.example.engine

import kotlin.math.abs
import kotlin.random.Random

/**
 * Rilis data resmi (Tahap A6): angka ekonomi tidak diketahui seketika dan tidak langsung benar.
 *  - Inflasi dan pengangguran dirilis dengan jeda 1 bulan; PDB triwulanan dengan jeda 2 bulan setelah akhir triwulan.
 *  - Perkiraan awal berisik, lalu direvisi (derau separuh) dan akhirnya dikunci pada nilai sebenarnya.
 *
 * Ini lapisan informasi: mesin tetap memakai nilai sebenarnya di dalam; yang berubah adalah apa yang "diumumkan"
 * kepada pemain dan pasar. Kejutan rilis menggerakkan suasana pasar saham. Tidak ada arus kas yang dibuat di sini.
 */
enum class DataKind(val label: String, val unit: String, val lag: Int, val firstNoise: Double) {
    INFLASI("Inflasi", "%", 1, 0.25),
    PENGANGGURAN("Pengangguran", "%", 1, 0.20),
    PDB("Pertumbuhan PDB", "%", 2, 0.80)
}

class Release(val country: String, val kind: DataKind, val period: Int, val published: Int, val value: Double, val revision: Int) {
    val isFinal: Boolean get() = revision >= 2
}

class DataDeskResult(val news: Double)

class DataDesk {

    private class Pending(val country: String, val kind: DataKind, val period: Int, val truth: Double, var stage: Int)

    private val truthLog = HashMap<String, Double>()
    private val pending = ArrayList<Pending>()
    private val history = LinkedHashMap<String, ArrayList<Release>>() // kunci "negara/jenis"
    private val coreIds = Countries.coreIds

    private fun key(c: String, k: DataKind) = "$c/${k.name}"

    /** Rilis terbaru (angka paling baru yang diumumkan) untuk suatu negara dan jenis data; null bila belum ada. */
    fun latest(country: String, kind: DataKind): Release? = history[key(country, kind)]?.lastOrNull()

    /** Seluruh riwayat rilis (termasuk revisi) untuk satu seri, urut waktu terbit. */
    fun releases(country: String, kind: DataKind): List<Release> = history[key(country, kind)] ?: emptyList()

    /** Selisih terbesar antara perkiraan awal dan nilai final untuk periode yang sudah final (untuk uji). */
    fun revisionSizes(country: String, kind: DataKind): List<Double> {
        val rs = releases(country, kind)
        val out = ArrayList<Double>()
        for (p in rs.map { it.period }.distinct()) {
            val byPeriod = rs.filter { it.period == p }
            if (byPeriod.any { it.isFinal }) out += byPeriod.last().value - byPeriod.first().value
        }
        return out
    }

    fun step(turn: Int, r: Random, input: (String) -> MacroInput, notes: MutableList<DuniaNote>): DataDeskResult {
        // 1. Catat nilai sebenarnya bulan ini dan jadwalkan rilis.
        val gdpBuf = HashMap<String, Double>()
        for (c in coreIds) {
            val inp = input(c)
            truthLog["$c/INFLASI/$turn"] = inp.inflation
            truthLog["$c/PENGANGGURAN/$turn"] = inp.unemployment
            truthLog["$c/PDB/$turn"] = inp.growthAnnual
            pending += Pending(c, DataKind.INFLASI, turn, inp.inflation, 0)
            pending += Pending(c, DataKind.PENGANGGURAN, turn, inp.unemployment, 0)
            if (turn % 3 == 2) {
                val g = (0..2).map { truthLog["$c/PDB/${turn - it}"] ?: inp.growthAnnual }.average()
                pending += Pending(c, DataKind.PDB, turn, g, 0)
            }
            gdpBuf[c] = inp.growthAnnual
        }

        // 2. Terbitkan yang jatuh tempo (jeda, revisi 1 di +2 bulan, final di +5 bulan setelah rilis awal).
        val sundaraFirst = StringBuilder()
        var surprise = 0.0
        val done = ArrayList<Pending>()
        // Urutan tetap (negara, jenis, periode) agar penarikan derau deterministik.
        val due = pending.filter { p ->
            val first = p.period + p.kind.lag
            when (p.stage) { 0 -> turn >= first; 1 -> turn >= first + 2; else -> turn >= first + 5 }
        }.sortedWith(compareBy({ it.country }, { it.kind.ordinal }, { it.period }, { it.stage }))
        for (p in due) {
            val sd = when (p.stage) { 0 -> p.kind.firstNoise; 1 -> 0.5 * p.kind.firstNoise; else -> 0.0 }
            val value = p.truth + (if (sd > 0.0) stdNormal(r) * sd else 0.0)
            val prev = latest(p.country, p.kind)
            val rel = Release(p.country, p.kind, p.period, turn, value, p.stage)
            history.getOrPut(key(p.country, p.kind)) { ArrayList() }.let { l -> l += rel; if (l.size > 240) l.removeAt(0) }
            if (p.country == "sundara") {
                if (p.stage == 0) {
                    if (p.kind == DataKind.INFLASI && prev != null) surprise -= 0.02 * (value - prev.value)
                    if (p.kind == DataKind.PDB && prev != null) surprise += 0.004 * (value - prev.value)
                    sundaraFirst.append(if (sundaraFirst.isEmpty()) "" else ", ").append("${p.kind.label.lowercase()} ${KapitalEngine.fmt1(value)}${p.kind.unit}")
                } else if (p.stage == 1 && prev != null && abs(value - prev.value) >= (if (p.kind == DataKind.PDB) 0.6 else 0.3)) {
                    notes += DuniaNote(
                        "Revisi data resmi: ${p.kind.label.lowercase()} periode lalu direvisi dari ${KapitalEngine.fmt1(prev.value)}${p.kind.unit} menjadi ${KapitalEngine.fmt1(value)}${p.kind.unit}.",
                        Tone.INFO)
                }
            }
            if (p.stage >= 2) done += p else p.stage++
        }
        pending.removeAll(done)
        if (sundaraFirst.isNotEmpty()) {
            notes += DuniaNote("Rilis data resmi (perkiraan awal, bisa direvisi): $sundaraFirst.", Tone.INFO)
        }
        return DataDeskResult(surprise.coerceIn(-0.05, 0.05))
    }
}
