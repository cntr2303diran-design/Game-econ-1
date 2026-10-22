package com.example.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Sistem kredit agen (Tahap A1/A5): 2.000 perusahaan dan 120 bank sebagai agen individual di delapan negara inti.
 * Perusahaan gagal bayar menurut leverage, margin, siklus, bunga, dan kelangkaan sektor; kerugian kredit menggerus
 * modal bank (CAR); bank yang modalnya habis diselamatkan pemerintah (menambah utang negara) dan menularkan panik.
 *
 * Seperti lapisan dunia lainnya, ia hanya memengaruhi mesin lewat premi kredit, permintaan, dan tekanan panik,
 * jadi identitas akuntansi pemain tidak tersentuh.
 */
object CreditAgents {
    /** Jumlah perusahaan per negara inti (jumlah = 2.000). */
    val FIRMS: Map<String, Int> = linkedMapOf(
        "sundara" to 500, "aurelia" to 250, "norvaldia" to 100, "kestrel" to 400,
        "halvenia" to 150, "meridia" to 30, "tarsis" to 450, "velmora" to 120
    )
    /** Jumlah bank per negara inti (jumlah = 120). */
    val BANKS: Map<String, Int> = linkedMapOf(
        "sundara" to 30, "aurelia" to 24, "norvaldia" to 8, "kestrel" to 20,
        "halvenia" to 10, "meridia" to 4, "tarsis" to 16, "velmora" to 8
    )
    val TOTAL_FIRMS: Int = FIRMS.values.sum()
    val TOTAL_BANKS: Int = BANKS.values.sum()
}

class Firm(val sector: Sector) {
    var leverage = 0.5
    var margin = 0.08
    var vacantMonths = 0
}

class Bank {
    var car = 14.0
    var concentration = 1.0
    var resolvedMonths = 0
}

class CountryCredit(val id: String, val firms: List<Firm>, val banks: List<Bank>) {
    /** Tingkat gagal bayar tahunan (EMA, %). */
    var defaultRate = 2.4; internal set
    var defaultsLastMonth = 0; internal set
    var failuresTotal = 0; internal set
    var failuresLastMonth = 0; internal set
    /** Porsi bank dengan CAR < 8% (tekanan kredit) dan < 6% (rapuh). */
    var weakShare = 0.0; internal set
    var fragileShare = 0.0; internal set
    var meanCar = 14.0; internal set
    /** Biaya penyelamatan bank bulan ini (% PDB), diteruskan ke utang pemerintah. */
    var recapCost = 0.0; internal set
    internal var uBase = Double.NaN
}

class CreditSystem(seed: Long) {

    val countries: Map<String, CountryCredit>

    init {
        val m = LinkedHashMap<String, CountryCredit>()
        for ((ci, id) in Countries.coreIds.withIndex()) {
            val rr = Random(seed * 7_919L + ci * 104_729L + 13L)
            val firms = (0 until CreditAgents.FIRMS.getValue(id)).map { i ->
                Firm(Sector.values()[(i * 7 + ci) % Sector.values().size]).also {
                    it.leverage = (0.5 + 0.12 * stdNormal(rr)).coerceIn(0.1, 0.9)
                    it.margin = (0.08 + 0.02 * stdNormal(rr)).coerceIn(-0.05, 0.2)
                }
            }
            val banks = (0 until CreditAgents.BANKS.getValue(id)).map {
                Bank().also { b ->
                    b.car = (14.0 + 2.0 * stdNormal(rr)).coerceIn(8.0, 20.0)
                    b.concentration = exp(0.25 * stdNormal(rr))
                }
            }
            m[id] = CountryCredit(id, firms, banks)
        }
        countries = m
    }

    val firmCount: Int get() = countries.values.sumOf { it.firms.size }
    val bankCount: Int get() = countries.values.sumOf { it.banks.size }

    private fun logistic(z: Double) = 1.0 / (1.0 + exp(-z))

    /**
     * @param input masukan makro tiap negara inti.
     * @param costMult pengali biaya input (hanya Sundara menyimpang dari 1,0).
     * @param availability ketersediaan sektor Sundara (0..1) dari model Leontief.
     * @param panic indeks panik Sundara (0..100); penarikan simpanan menggerus modal bank Sundara.
     */
    fun step(
        rAll: Random,
        input: (String) -> MacroInput,
        costMult: Double,
        availability: DoubleArray,
        panic: Double,
        notes: MutableList<DuniaNote>
    ) {
        for ((id, cc) in countries) {
            // Aliran acak terpisah per negara: guncangan di satu negara tidak menggeser realisasi acak negara lain.
            val r = Random(rAll.nextLong())
            val inp = input(id)
            val sundara = id == "sundara"
            if (cc.uBase.isNaN()) cc.uBase = inp.unemployment
            cc.uBase += 0.01 * (inp.unemployment - cc.uBase)
            val uGap = min(1.2, 0.3 * max(0.0, inp.unemployment - cc.uBase))
            val realRate = inp.policyRate - inp.inflation
            var defaults = 0
            for (f in cc.firms) {
                if (f.vacantMonths > 0) {
                    f.vacantMonths--
                    if (f.vacantMonths == 0) { f.leverage = 0.3; f.margin = 0.08 }
                    continue
                }
                val target = 0.08 + 0.6 * inp.gap - (if (sundara) 0.5 * (costMult - 1.0) else 0.0)
                f.margin = (f.margin + 0.15 * (target - f.margin) + 0.01 * stdNormal(r)).coerceIn(-0.25, 0.30)
                f.leverage = (f.leverage + 0.03 * (0.5 - f.leverage) - 0.25 * min(0.0, f.margin) + 0.008 * stdNormal(r)).coerceIn(0.1, 0.95)
                val shortage = if (sundara) 2.5 * (1.0 - availability[f.sector.ordinal].coerceIn(0.0, 1.0)) else 0.0
                val z = -6.2 + 2.2 * (f.leverage - 0.5) - 6.0 * (f.margin - 0.08) + 0.15 * (realRate - 2.0) * f.leverage +
                    uGap + shortage
                if (r.nextDouble() < logistic(z)) {
                    defaults++
                    f.vacantMonths = 3
                }
            }
            val share = defaults.toDouble() / max(1, cc.firms.size)
            cc.defaultsLastMonth = defaults
            cc.defaultRate = 0.9 * cc.defaultRate + 0.1 * (share * 1200.0)

            // Bank: laba bunga bersih menambah modal (ditahan sampai 16%), kerugian kredit dan penarikan simpanan menggerusnya.
            var recapBanks = 0
            var fails = 0
            var weak = 0; var fragile = 0; var carSum = 0.0
            for (b in cc.banks) {
                if (b.resolvedMonths > 0) {
                    b.resolvedMonths--
                } else {
                    val loss = 28.0 * share * b.concentration
                    val flight = if (sundara) 0.004 * max(0.0, panic - 30.0) else 0.0
                    // bank yang lebih terkonsentrasi menanggung risiko lebih besar tetapi juga memungut margin lebih tinggi
                    b.car = min(16.0, b.car + 0.14 * (0.6 + 0.4 * b.concentration) - loss - flight + 0.02 * stdNormal(r))
                    if (b.car < 4.0) {
                        fails++; recapBanks++
                        // Penyelamatan: modal disuntik, portofolio dibersihkan (konsentrasi turun) dan bank diawasi 12 bulan.
                        b.car = 11.0; b.resolvedMonths = 12
                        b.concentration = max(0.6, b.concentration * 0.75)
                    }
                }
                if (b.car < 8.0) weak++
                if (b.car < 6.0) fragile++
                carSum += b.car
            }
            val n = max(1, cc.banks.size)
            cc.weakShare = weak.toDouble() / n
            cc.fragileShare = fragile.toDouble() / n
            cc.meanCar = carSum / n
            cc.failuresLastMonth = fails
            cc.failuresTotal += fails
            cc.recapCost = 0.5 * recapBanks.toDouble() / n * 10.0 // ~5 poin % PDB bila seluruh bank diselamatkan sekaligus
            if (sundara && fails > 0) {
                notes += DuniaNote(
                    "$fails bank Sundara kehabisan modal akibat kredit macet dan diselamatkan pemerintah; kreditur memperketat syarat.",
                    Tone.BAD, "Bank diselamatkan pemerintah",
                    "Gagal bayar perusahaan naik → modal bank terkikis → bank bermasalah → penyelamatan memperbesar utang negara dan memperketat kredit.")
            }
        }
    }
}
