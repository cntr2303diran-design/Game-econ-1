package com.example.engine

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Siklus jangka panjang (Tahap A7): demografi, iklim, dan adopsi teknologi, yang bergeser antar-era.
 *
 *  - Demografi (8 negara inti): struktur umur bergeser lambat; penuaan menambah biaya pensiun dan kesehatan di fiskal
 *    (masuk MacroPlus), dan pangsa usia kerja menggeser permintaan Sundara sedikit.
 *  - Iklim: anomali suhu naik menurut skenario seed; peluang kejadian ekstrem tiap blok naik bersama suhu dan era.
 *    Kejadian menjadi guncangan kapasitas sektor (pangan, konstruksi, energi, logistik) lewat model Leontief.
 *  - Teknologi: tiga kurva adopsi logistik (otomasi, energi terbarukan, bioteknologi) dengan waktu tengah yang
 *    bergeser acak dan terobosan yang memajukannya.
 *
 * Semua dampak lewat saluran lama (permintaan, biaya, kapasitas sektor, fiskal). Parameter kekuatan dampak adalah
 * asumsi yang dikalibrasi dengan simulasi bot, bukan data empiris.
 */
enum class Tech(val label: String, val t0: Int, val k: Double) {
    OTOMASI("otomasi dan AI industri", 210, 0.030),
    ENERGI_HIJAU("energi terbarukan", 250, 0.028),
    BIOTEK("bioteknologi dan farmasi", 330, 0.030)
}

class Demography(val id: String, var young: Double, var old: Double, var tfr: Double, val tfrLongRun: Double) {
    val old0: Double = old
    var work: Double = 1.0 - young - old
    val work0: Double = work
}

class LongEffects(
    /** Guncangan permintaan bulanan Sundara (level dasar x 0,15, lihat konstanta di LongCycles). */
    val demand: Double,
    /** Pengali biaya pemain dari produktivitas otomasi (1,0 turun sampai 0,985). */
    val costMult: Double,
    /** Tekanan iklim 0..1 untuk model sektor. */
    val climateStress: Double,
    /** Ketahanan energi (0..0,4) dari energi terbarukan; meredam sanksi/selat energi. */
    val energyResilience: Double
)

class ClimateEvent(val turn: Int, val block: Block, val kind: String)

class LongCycles(seed: Long) {

    // ---- Demografi ----
    val demography: Map<String, Demography> = linkedMapOf(
        "sundara" to Demography("sundara", 0.27, 0.07, 2.4, 1.7),
        "aurelia" to Demography("aurelia", 0.17, 0.20, 1.5, 1.6),
        "norvaldia" to Demography("norvaldia", 0.16, 0.22, 1.5, 1.6),
        "kestrel" to Demography("kestrel", 0.19, 0.15, 1.6, 1.5),
        "halvenia" to Demography("halvenia", 0.18, 0.17, 1.5, 1.5),
        "meridia" to Demography("meridia", 0.24, 0.10, 2.0, 1.7),
        "tarsis" to Demography("tarsis", 0.29, 0.06, 2.6, 1.8),
        "velmora" to Demography("velmora", 0.22, 0.10, 1.8, 1.6)
    )

    // ---- Iklim ----
    val slopePerYear: Double
    var temperature = 0.0; private set
    val events = ArrayList<ClimateEvent>()
    private var firstEventNoted = false

    // ---- Teknologi ----
    private val shift = IntArray(Tech.values().size)
    private val milestone = IntArray(Tech.values().size)
    val breakthroughs = IntArray(Tech.values().size)

    init {
        val rr = Random(seed * 48_271L + 17L)
        slopePerYear = 0.022 + 0.016 * rr.nextDouble()           // 0,022..0,038 °C per tahun
        for (t in Tech.values()) shift[t.ordinal] = (stdNormal(rr) * 18.0).toInt().coerceIn(-36, 36)
    }

    fun adoption(t: Tech, turn: Int): Double = 1.0 / (1.0 + exp(-t.k * (turn - t.t0 - shift[t.ordinal])))

    /** Biaya penuaan dan kesehatan (% PDB per tahun) di atas level awal; bioteknologi meredamnya sampai 25%. */
    fun agingCost(country: String, turn: Int): Double {
        val d = demography[country] ?: return 0.0
        val relief = 1.0 - 0.25 * adoption(Tech.BIOTEK, turn)
        return max(0.0, 0.12 * (d.old - d.old0) * 100.0) * relief
    }

    private fun vulnerability(b: Block) = when (b) { Block.TROPIKA -> 1.5; Block.SAMUDRA -> 0.9; Block.TIMUR -> 1.0; Block.UTARA -> 0.6 }

    /**
     * @param blockShare pangsa pasokan global sektor yang berada di blok tertentu (untuk menskalakan guncangan kapasitas).
     */
    fun step(
        turn: Int,
        r: Random,
        sectors: SectorModel,
        notes: MutableList<DuniaNote>
    ): LongEffects {
        val era = KapitalEngine.era(turn)
        val dt = 1.0 / 12.0

        // Demografi
        for (d in demography.values) {
            d.tfr += 0.12 * dt * (d.tfrLongRun - d.tfr)
            d.young += 0.06 * (0.11 * d.tfr - d.young) * dt
            val longevity = 0.0005 * adoption(Tech.BIOTEK, turn)
            d.old = min(0.33, d.old + dt * (0.0020 + 0.010 * max(0.0, 0.22 - d.young) + longevity))
            d.work = 1.0 - d.young - d.old
        }
        val su = demography.getValue("sundara")
        val agedNote = su.old >= 0.14 && !agedNoted
        if (agedNote) {
            agedNoted = true
            notes += DuniaNote("Sundara resmi menjadi masyarakat menua: penduduk 65+ melewati 14%. Biaya pensiun dan kesehatan menekan anggaran.",
                Tone.WARN, "Sundara menua", "Demografi bergeser → rasio ketergantungan naik → beban fiskal naik dan konsumsi melambat.")
        }
        if (!bonusEnded && su.work < su.work0 - 0.025) {
            bonusEnded = true
            notes += DuniaNote("Bonus demografi Sundara berakhir: pangsa usia kerja turun lebih dari 2,5 poin dari puncaknya.",
                Tone.WARN, "Bonus demografi berakhir", "Pangsa usia kerja turun → pertumbuhan potensial melambat.")
        }

        // Teknologi
        val adopt = DoubleArray(Tech.values().size)
        for (t in Tech.values()) {
            val i = t.ordinal
            val a = adoption(t, turn)
            if (a in 0.05..0.80 && r.nextDouble() < 0.004) {
                shift[i] -= 8; breakthroughs[i]++
                notes += DuniaNote("Terobosan di bidang ${t.label}: adopsi dipercepat beberapa bulan.", Tone.INFO)
            }
            adopt[i] = adoption(t, turn)
            val level = when { adopt[i] >= 0.75 -> 3; adopt[i] >= 0.50 -> 2; adopt[i] >= 0.25 -> 1; else -> 0 }
            if (level > milestone[i]) {
                milestone[i] = level
                notes += DuniaNote("Adopsi ${t.label} mencapai ${level * 25}% di dunia.", Tone.INFO,
                    if (level == 2) "Titik balik: ${t.label}" else null,
                    "Kurva adopsi teknologi → biaya, lapangan kerja, dan ketahanan pasokan bergeser.")
            }
        }
        val green = adopt[Tech.ENERGI_HIJAU.ordinal]
        val auto = adopt[Tech.OTOMASI.ordinal]

        // Iklim
        temperature += dt * slopePerYear * (1.0 - 0.35 * green)
        val eraMult = when (era) { 1 -> 1.0; 2 -> 1.1; 3 -> 1.25; else -> 1.4 }
        for (b in Block.values()) {
            val hazard = 0.004 * vulnerability(b) * (1.0 + 1.2 * temperature) * eraMult
            if (r.nextDouble() < hazard) {
                val kind = when (r.nextInt(3)) { 0 -> "kekeringan"; 1 -> "banjir besar"; else -> "gelombang panas" }
                events += ClimateEvent(turn, b, kind)
                val months = 4 + r.nextInt(5)
                val size = 0.15 + 0.20 * r.nextDouble()
                val hit = when (kind) {
                    "kekeringan" -> listOf(Sector.PANGAN to 1.0, Sector.TEKSTIL to 0.5)
                    "banjir besar" -> listOf(Sector.KONSTRUKSI to 0.8, Sector.LOGISTIK to 0.8, Sector.PROPERTI to 0.5)
                    else -> listOf(Sector.ENERGI to 0.6, Sector.PANGAN to 0.5, Sector.LOGISTIK to 0.4)
                }
                var worst = 0.0
                for ((sec, w) in hit) {
                    val share = Countries.blockMembers(b).sumOf { sectors.supplyShare(sec, it.id) }.coerceAtMost(1.0)
                    val sz = size * w * min(1.0, 2.0 * share)
                    worst = max(worst, sz)
                    if (sz > 0.01) sectors.applyShock(sec, sz, months)
                }
                if (worst > 0.03 || !firstEventNoted) {
                    firstEventNoted = true
                    notes += DuniaNote("Cuaca ekstrem di ${b.label}: $kind selama sekitar $months bulan mengganggu pasokan ${hit.joinToString(", ") { it.first.label }}.",
                        Tone.WARN, "Cuaca ekstrem: $kind di ${b.label}",
                        "Pemanasan global → peristiwa ekstrem lebih sering → kapasitas sektor turun → harga dan ketersediaan memburuk.")
                }
            }
        }
        val climateStress = (temperature / 3.0).coerceIn(0.0, 1.0)

        val workEffect = 0.3 * (su.work - su.work0)                   // level permintaan
        val demandLevel = workEffect - 0.012 * auto                   // otomasi: tenaga kerja tergeser menekan konsumsi
        val demand = 0.15 * demandLevel                               // guncangan bulanan yang menghasilkan level itu (AR 0,85)
        val costMult = 1.0 - 0.015 * auto
        return LongEffects(demand, costMult, climateStress, 0.4 * green)
    }

    private var agedNoted = false
    private var bonusEnded = false
}
