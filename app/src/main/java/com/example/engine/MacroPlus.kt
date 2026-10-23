package com.example.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Makro lanjut (Tahap A5): fiskal (defisit dan utang), kurva imbal hasil, peringkat utang, bank sentral
 * (bias politik, QE, panduan ke depan), indeks panik/bank run, dan kurs dari neraca dagang.
 *
 * Seperti seluruh lapisan dunia, modul ini TIDAK membuat arus kas. Ia memengaruhi mesin lewat saluran lama saja:
 * bias aturan Taylor (policyBias), kurs (kursDrift), premi kredit (creditAdd) dan guncangan permintaan (demandAdd).
 */
class MacroInput(
    val inflation: Double,
    val gap: Double,
    val unemployment: Double,
    val policyRate: Double,
    val taylorStar: Double,
    val growthAnnual: Double
)

enum class Guidance(val label: String) {
    NETRAL("menahan suku bunga"), HAWKISH("mengisyaratkan pengetatan"), DOVISH("mengisyaratkan pelonggaran")
}

/** Peringkat utang negara: delapan anak tangga. spreadBp = premi risiko relatif surat utang bebas risiko. */
enum class Rating(val label: String, val spreadBp: Int) {
    AAA("AAA", 0), AA("AA", 40), A("A", 90), BBB("BBB", 160), BB("BB", 280), B("B", 450), CCC("CCC", 800), D("D", 1500)
}

class MacroEffects(
    val kursDrift: Double,
    /** Bias aturan Taylor Sundara (poin persen); negatif = lebih longgar. */
    val policyBias: Double,
    /** Bias aturan Taylor bank sentral negara inti asing. */
    val foreignBias: Map<String, Double>,
    val creditAdd: Double,
    val demandAdd: Double
) {
    companion object { val NEUTRAL = MacroEffects(0.0, 0.0, emptyMap(), 0.0, 0.0) }
}

/** Keadaan makro-fiskal satu negara, sekaligus pemerintahnya dan bank sentralnya. */
class CountryMacroState(val country: Country) {
    var debtGdp = 55.0; internal set
    var deficit = 0.0; internal set
    var primaryDeficit = 0.0; internal set
    var yield10 = 6.0; internal set
    var rating = Rating.BBB; internal set
    var score = 50.0; internal set
    var piBar = 4.0; internal set

    var cbBias = 0.0; internal set
    var qeOn = false; internal set
    var qeStock = 0.0; internal set
    var guidance = Guidance.NETRAL; internal set
    var guidanceCredibility = 1.0; internal set

    internal var debtBase = 55.0
    internal var termShock = 0.0
    internal var initialised = false
    internal var lastPolicy = 0.0
    internal var monthsInRating = 0
    var inversionMonths = 0; internal set
    val slope: Double get() = yield10 - lastPolicy
}

class MacroPlus(private val seed: Long) {

    val states: List<CountryMacroState> = Countries.all.map { CountryMacroState(it) }
    private val byId = states.associateBy { it.country.id }
    fun state(id: String): CountryMacroState = byId.getValue(id)

    // ---- Sundara: panik, bank run, kurs ----
    var panic = 10.0; private set
    var runMonthsLeft = 0; private set
    var runsSoFar = 0; private set
    private var runCooldown = 0
    private var cbLiquidityMonths = 0
    private var downgradePulse = 0.0
    private var fxPulse = 0.0
    var tradeBalance = 0.0; private set

    init {
        for (s in states) {
            s.debtGdp = initialDebt(s.country); s.debtBase = s.debtGdp
        }
    }

    private fun initialDebt(c: Country): Double = when (c.id) {
        "sundara" -> 55.0; "aurelia" -> 95.0; "norvaldia" -> 40.0; "kestrel" -> 70.0
        "halvenia" -> 60.0; "meridia" -> 35.0; "tarsis" -> 50.0; "velmora" -> 80.0
        else -> {
            val base = when (c.block) { Block.SAMUDRA -> 70.0; Block.UTARA -> 45.0; Block.TIMUR -> 55.0; Block.TROPIKA -> 50.0 }
            val i = Countries.secondary.indexOfFirst { it.country.id == c.id }
            base + ((i * 53) % 21 - 10) * 1.2
        }
    }

    /** Kualitas kelembagaan/struktur ekonomi (poin skor peringkat); membedakan negara dengan utang yang sama. */
    private fun institutions(c: Country): Double = when (c.id) {
        "sundara" -> -32.0; "aurelia" -> 7.0; "norvaldia" -> 14.0; "kestrel" -> -17.0
        "halvenia" -> -30.0; "meridia" -> -24.0; "tarsis" -> -30.0; "velmora" -> -18.0
        else -> {
            val p = Countries.secondary.first { it.country.id == c.id }
            1.5 - 0.95 * p.risk0
        }
    }

    // Batas bawah skor tiap anak tangga: AAA>=85, AA>=72, A>=58, BBB>=44, BB>=30, B>=16, CCC>=5, selain itu D.
    private val bounds = doubleArrayOf(85.0, 72.0, 58.0, 44.0, 30.0, 16.0, 5.0)

    private fun notchOf(score: Double): Int {
        var n = 0
        while (n < bounds.size && score < bounds[n]) n++
        return n
    }

    /**
     * @param exportIndex indeks permintaan ekspor Sundara (1,0 = netral), @param oilIndex harga minyak.
     * @param crisis ada krisis makro aktif di Sundara.
     */
    fun step(
        turn: Int,
        r: Random,
        input: (String) -> MacroInput,
        geo: Geopolitics,
        exportIndex: Double,
        oilIndex: Double,
        crisis: Boolean,
        notes: MutableList<DuniaNote>,
        /** Porsi bank rapuh (CAR < 6%) di Sundara, dari sistem kredit agen. */
        bankFragile: Double = 0.0,
        /** Biaya penyelamatan bank bulan ini (% PDB) per negara; menambah utang pemerintah. */
        recapCost: (String) -> Double = { 0.0 },
        /** Biaya penuaan dan kesehatan (% PDB per tahun) di atas level awal, dari modul demografi. */
        agingCost: (String) -> Double = { 0.0 }
    ): MacroEffects {
        var creditAdd = 0.0
        var demandAdd = 0.0
        val foreignBias = HashMap<String, Double>()
        var sundaraBias = 0.0

        for (s in states) {
            val id = s.country.id
            val inp = input(id)
            val sundara = id == "sundara"
            val st = geo.stance(id)

            if (!s.initialised) {
                s.piBar = inp.inflation
                s.lastPolicy = inp.policyRate
                s.yield10 = inp.policyRate + 1.0
                s.score = rawScore(s, inp, geo)
                s.rating = Rating.values()[notchOf(s.score)]
                s.initialised = true
            }
            s.piBar += 0.03 * (inp.inflation - s.piBar)

            // ---- Fiskal: stabilisator otomatis + haluan pemilu + pengetatan bila utang melewati dasar ----
            val interest = s.debtGdp * s.yield10 / 100.0
            // Aturan fiskal: pemerintah menutup 80% beban (bunga - pertumbuhan nominal) lewat surplus primer dan mengetatkan bila utang
            // melewati dasar. Tanpa ini utang Sundara meledak (rata-rata 59 -> 131% PDB) karena bunga acuan 9-11% > pertumbuhan nominal.
            val rMinusG = s.debtGdp * (s.yield10 - (inp.growthAnnual + inp.inflation)) / 100.0
            val primary = -0.8 * rMinusG - 0.4 * inp.gap * 100.0 + st.deficitAdd + agingCost(id) - 0.08 * (s.debtGdp - s.debtBase)
            s.primaryDeficit = primary
            s.deficit = primary + interest // % PDB per tahun
            val nominal = inp.growthAnnual + inp.inflation
            s.debtGdp = (s.debtGdp + (primary + interest - s.debtGdp * nominal / 100.0) / 12.0 + recapCost(id)).coerceIn(10.0, 260.0)

            // ---- Peringkat: skor dihaluskan, perubahan satu anak tangga dengan histeresis 2 poin ----
            s.score += 0.12 * (rawScore(s, inp, geo) - s.score)
            s.monthsInRating++
            var n = s.rating.ordinal
            val oldRating = s.rating
            if (n < 7 && s.score < bounds[n] - 2.0 && s.monthsInRating >= 6) n++
            else if (n > 0 && s.score >= bounds[n - 1] + 2.0 && s.monthsInRating >= 6) n--
            if (n != s.rating.ordinal) {
                s.rating = Rating.values()[n]; s.monthsInRating = 0
                val down = n > oldRating.ordinal
                if (sundara) {
                    if (down) { downgradePulse = 1.0; fxPulse += 0.012 }
                    else fxPulse -= 0.006
                    notes += DuniaNote(
                        "Lembaga pemeringkat ${if (down) "menurunkan" else "menaikkan"} peringkat utang Sundara dari ${oldRating.label} ke ${s.rating.label}. " +
                            if (down) "Imbal hasil naik dan rupiah tertekan." else "Biaya pinjaman negara mereda.",
                        if (down) Tone.BAD else Tone.GOOD,
                        "Peringkat utang Sundara ${if (down) "turun" else "naik"}: ${s.rating.label}",
                        if (down) "Utang/PDB, inflasi, ketegangan geopolitik → skor peringkat turun → premi risiko naik → kurs dan kredit memburuk."
                        else "Fiskal dan stabilitas membaik → peringkat naik → premi risiko turun.")
                } else if (s.country.core && abs(s.rating.ordinal - oldRating.ordinal) >= 1 && down && s.rating.ordinal >= Rating.BB.ordinal) {
                    notes += DuniaNote("${s.country.name} diturunkan ke ${s.rating.label} oleh lembaga pemeringkat.", Tone.INFO)
                }
            }

            // ---- Bank sentral: QE, panduan ke depan, bias politik ----
            centralBank(s, inp, sundara, notes, turn)
            val cbTarget = st.cbPressure + (if (s.debtGdp > 100.0) -0.25 else 0.0) + (if (sundara && cbLiquidityMonths > 0) -0.5 else 0.0)
            s.cbBias += 0.10 * (cbTarget - s.cbBias)
            if (sundara) sundaraBias = s.cbBias else if (s.country.core) foreignBias[id] = s.cbBias

            // ---- Kurva imbal hasil 10 tahun: ekspektasi rata-rata bunga + premi jangka + premi peringkat/utang ----
            s.termShock *= 0.9
            val longRun = s.piBar + 2.5
            val premium = 0.9 + 0.004 * s.rating.spreadBp + 0.015 * (s.debtGdp - 60.0) + 0.25 * (inp.inflation - 3.0) * 0.5 +
                s.termShock - 0.1 * s.guidanceCredibility * (if (s.guidance == Guidance.NETRAL) 1.0 else 0.0) - min(0.5, 0.02 * s.qeStock)
            val target = 0.5 * inp.policyRate + 0.5 * longRun + premium
            s.yield10 = (s.yield10 + 0.25 * (target - s.yield10)).coerceIn(0.2, 40.0)
            s.lastPolicy = inp.policyRate
            if (s.slope < 0.0) s.inversionMonths++ else s.inversionMonths = 0
            if (sundara && s.inversionMonths == 3) {
                notes += DuniaNote("Kurva imbal hasil Sundara terbalik: bunga jangka pendek melampaui jangka panjang, sinyal klasik resesi.", Tone.WARN)
            }
            if (sundara) {
                // Spread peringkat relatif awal mengalir ke premi kredit (skala: 100bp ≈ 0,25 poin premi pada keadaan tunak).
                val base = Rating.BBB.spreadBp
                creditAdd += (s.rating.spreadBp - base) * 1e-4 - 0.00016 * s.qeStock
            }
        }

        // ---- Panik / bank run (hanya Sundara; kolam bank pemain bereaksi lewat saluran kredit dan permintaan) ----
        val su = state("sundara"); val sin = input("sundara")
        val tension = geo.meanTensionOf("sundara")
        val stress = 35.0 * (if (crisis) 1.0 else 0.0) + 15.0 * downgradePulse +
            25.0 * ((sin.unemployment - 5.5) / 5.0).coerceIn(0.0, 1.0) +
            15.0 * ((sin.inflation - 8.0) / 10.0).coerceIn(0.0, 1.0) +
            15.0 * ((tension - 55.0) / 35.0).coerceIn(0.0, 1.0) +
            10.0 * (-su.slope / 2.0).coerceIn(0.0, 1.0) +
            10.0 * ((su.debtGdp - 90.0) / 60.0).coerceIn(0.0, 1.0) +
            15.0 * bankFragile.coerceIn(0.0, 1.0) -
            5.0 * su.guidanceCredibility
        downgradePulse *= 0.9
        panic = (0.85 * panic + 0.15 * stress.coerceIn(0.0, 100.0)).coerceIn(0.0, 100.0)
        if (panic > 45.0) panic = (panic + 0.04 * (panic - 45.0)).coerceAtMost(100.0) // efek kawanan
        if (runCooldown > 0) runCooldown--
        if (cbLiquidityMonths > 0) cbLiquidityMonths--
        if (runMonthsLeft == 0 && runCooldown == 0 && panic >= 48.0 && r.nextDouble() < 0.15) {
            runMonthsLeft = 4; runsSoFar++; runCooldown = 60
            fxPulse += 0.020
            notes += DuniaNote(
                "Pelarian dana: nasabah menarik simpanan secara massal dan bank menahan kredit. Bank sentral menyuntikkan likuiditas darurat.",
                Tone.BAD, "Pelarian dana bank (bank run)",
                "Ketakutan menular → simpanan ditarik → kredit macet mengering → permintaan jatuh; likuiditas darurat menenangkan setelah beberapa bulan.")
        }
        if (runMonthsLeft > 0) {
            demandAdd -= 0.010
            creditAdd += 0.04
            runMonthsLeft--
            if (runMonthsLeft == 0) {
                panic = min(panic, 40.0); cbLiquidityMonths = 6
                notes += DuniaNote("Pelarian dana mereda setelah suntikan likuiditas bank sentral; kepercayaan pulih perlahan.", Tone.GOOD)
            }
        }

        // ---- Kurs dari neraca dagang (indeks dalam % PDB), peringkat, dan panik ----
        val tb = 20.0 * (exportIndex - 1.0) - 8.0 * (oilIndex - 1.0) - 0.06 * (tension - 45.0)
        tradeBalance = 0.9 * tradeBalance + 0.1 * tb
        val tbDrift = (-0.0004 * tradeBalance).coerceIn(-0.0012, 0.0012)
        val fxNow = fxPulse * 0.5; fxPulse -= fxNow
        val kursDrift = tbDrift + fxNow + 0.00003 * (panic - 30.0).coerceAtLeast(0.0)

        return MacroEffects(kursDrift, sundaraBias, foreignBias, creditAdd, demandAdd)
    }

    private fun rawScore(s: CountryMacroState, inp: MacroInput, geo: Geopolitics): Double =
        100.0 + institutions(s.country) -
            0.45 * max(0.0, s.debtGdp - 30.0) -
            3.0 * max(0.0, inp.inflation - 4.0) -
            0.6 * max(0.0, geo.meanTensionOf(s.country.id) - 40.0) -
            2.0 * max(0.0, inp.unemployment - 6.0) -
            2.5 * max(0.0, s.primaryDeficit - 2.0)

    private fun centralBank(s: CountryMacroState, inp: MacroInput, sundara: Boolean, notes: MutableList<DuniaNote>, turn: Int) {
        // QE: aktif di dekat batas bawah suku bunga saat ekonomi lesu; berhenti (taper) saat pulih atau inflasi tinggi.
        if (!s.qeOn && inp.policyRate <= 2.5 && inp.gap < -0.02) {
            s.qeOn = true
            if (sundara) notes += DuniaNote("Bank sentral Sundara meluncurkan pelonggaran kuantitatif (QE): membeli surat utang untuk menekan imbal hasil.", Tone.INFO)
        } else if (s.qeOn && (inp.gap > 0.01 || inp.inflation > 5.0)) {
            s.qeOn = false
            if (s.qeStock > 8.0) {
                s.termShock += 0.6
                if (sundara) notes += DuniaNote("Bank sentral Sundara mulai mengurangi pembelian obligasi (taper); imbal hasil melonjak sesaat.", Tone.WARN)
            }
        }
        s.qeStock = if (s.qeOn) min(25.0, s.qeStock + 0.8) else max(0.0, s.qeStock - 0.5)

        // Panduan ke depan: dievaluasi tiap 6 giliran dari selisih aturan Taylor dan suku bunga; melanggarnya merusak kredibilitas.
        val lastGuidance = s.guidance
        val breach = (lastGuidance == Guidance.HAWKISH && inp.policyRate < s.lastPolicy - 0.1) ||
            (lastGuidance == Guidance.DOVISH && inp.policyRate > s.lastPolicy + 0.1)
        if (breach) {
            s.guidanceCredibility = max(0.0, s.guidanceCredibility - 0.35)
            s.termShock += 0.15
            if (sundara) notes += DuniaNote("Bank sentral Sundara melanggar panduan ke depan yang ia umumkan; pasar menilai kebijakannya kurang dapat dipercaya.", Tone.WARN)
        } else {
            s.guidanceCredibility = min(1.0, s.guidanceCredibility + 0.02)
        }
        if (turn % 6 == 0) {
            val d = inp.taylorStar - inp.policyRate
            val g = when { d > 0.4 -> Guidance.HAWKISH; d < -0.4 -> Guidance.DOVISH; else -> Guidance.NETRAL }
            if (g != s.guidance) {
                s.guidance = g
                if (sundara && g != Guidance.NETRAL) {
                    notes += DuniaNote("Bank sentral Sundara ${g.label} dalam beberapa pertemuan mendatang.", Tone.INFO)
                }
            }
        }
    }
}
