package com.example.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/**
 * Geopolitik dinamis (dokumen desain bagian 8): skor ketegangan 0–100 untuk setiap pasangan dari 24 negara
 * (276 pasangan). Skor bergerak karena perilaku (tarif, lobi pemain, hasil pemilu), faktor global (krisis),
 * dan guncangan bilateral; kembali pelan ke garis dasar pasangan itu. Tangga eskalasi 6 anak tangga menentukan
 * apa yang aman, mahal, atau ilegal bagi bisnis pemain: sanksi berlapis, blokade selat, hingga perang terbuka.
 */
enum class Ladder(val rung: Int, val label: String, val from: Int, val effect: String) {
    TENANG(0, "Tenang", 0, "Hubungan normal."),
    PERANG_KATA(1, "Perang kata-kata", 30, "Volatilitas kurs dan saham naik, rumor membanjir."),
    PERANG_TARIF(2, "Perang tarif", 45, "Bea masuk naik; pemain memindah pemasok atau menaikkan harga."),
    SANKSI(3, "Sanksi ekonomi", 60, "Aset di negara tersanksi bisa dibekukan; berdagang dengannya berisiko denda."),
    BLOKADE(4, "Blokade selat", 70, "Kapal memutar, kongesti pelabuhan, ongkos kirim naik berkali lipat."),
    KONFLIK(5, "Konflik terbatas", 80, "Pabrik di zona konflik terancam; premi asuransi melonjak atau ditolak."),
    PERANG(6, "Perang terbuka", 90, "Energi dan pangan melonjak, aset disita atau hancur, pasar saham panik.");

    companion object {
        fun of(tension: Double): Ladder = values().last { tension >= it.from }
    }
}

/** Sanksi berlapis: negara -> sektor -> perusahaan. */
class Sanction(
    val from: String,
    val target: String,
    val startTurn: Int,
    val sectors: Set<Sector>,
    var severity: Double
)

class Strait(val id: String, val name: String, val a: String, val b: String, val goods: String)

enum class ElectionType(val label: String) {
    POPULIS("populis"), PRO_PASAR("pro-pasar"), PROTEKSIONIS("proteksionis"), HIJAU("hijau")
}

/** Kebijakan negara hasil pemilu terakhir; bertahan sampai pemilu berikutnya. */
class Stance(val type: ElectionType?, val tariffAdd: Double, val deficitAdd: Double, val carbonAdd: Double, val cbPressure: Double) {
    companion object { val NEUTRAL = Stance(null, 0.0, 0.0, 0.0, 0.0) }
}

class Election(val country: String, var turn: Int)

class Geopolitics(private val seed: Long, sundaraElectionTurn: Int, sundaraBaseTension: Map<String, Int>) {

    private val ids: List<String> = Countries.all.map { it.id }
    private val n = ids.size
    private val idx: Map<String, Int> = ids.withIndex().associate { it.value to it.index }

    private val base = DoubleArray(n * n)
    private val t = DoubleArray(n * n)
    private val stageState = IntArray(n * n)
    private var globalFactor = 0.0

    val straits = listOf(
        Strait("selat_energi", "Selat Norvaldia–Halvenia", "norvaldia", "halvenia", "energi"),
        Strait("selat_chip", "Selat dekat Kestrel", "kestrel", "aurelia", "chip dan barang jadi"),
        Strait("selat_pangan", "Selat Tarsis–Sundara", "tarsis", "sundara", "pangan")
    )

    val sanctions = mutableListOf<Sanction>()
    private val stances = HashMap<String, Stance>()
    val elections: List<Election>
    val history = mutableListOf<String>()

    private val cycles = HashMap<String, Int>()

    private fun key(a: String, b: String): String = if (a < b) "$a|$b" else "$b|$a"

    private val overrides: Map<String, Double> = mapOf(
        key("aurelia", "kestrel") to 52.0,
        key("aurelia", "velmora") to 50.0,
        key("norvaldia", "halvenia") to 52.0,
        key("kestrel", "halvenia") to 40.0,
        key("aurelia", "norvaldia") to 34.0,
        key("tarsis", "velmora") to 36.0,
        key("kestrel", "velmora") to 28.0
    )


    init {
        val r = Random(seed * 7_919L + 13L)
        for (i in 0 until n) for (j in i + 1 until n) {
            val a = ids[i]; val b = ids[j]
            val ca = Countries.byId.getValue(a); val cb = Countries.byId.getValue(b)
            var v = if (ca.block == cb.block) 18.0 else 32.0
            v += (r.nextDouble() - 0.5) * 12.0
            overrides[key(a, b)]?.let { v = it }
            if (a == "sundara" || b == "sundara") {
                val other = if (a == "sundara") b else a
                v = sundaraBaseTension[other]?.toDouble() ?: (if (Countries.byId.getValue(other).block == Block.TROPIKA) 28.0 else 36.0)
            }
            base[i * n + j] = v; base[j * n + i] = v
            t[i * n + j] = v; t[j * n + i] = v
            stageState[i * n + j] = Ladder.of(v).rung; stageState[j * n + i] = stageState[i * n + j]
        }
        // Jadwal pemilu: tiap negara punya siklus 48–60 giliran dengan tahap awal acak. Sundara mengikuti jadwal skenario.
        elections = ids.mapIndexed { k, id ->
            val rr = Random(seed * 31L + k * 104_729L + 5L)
            val cycle = 48 + rr.nextInt(13)
            val first = if (id == "sundara") sundaraElectionTurn else 12 + rr.nextInt(cycle)
            Election(id, first).also { cycles[id] = cycle }
        }
    }

    fun tension(a: String, b: String): Double {
        if (a == b) return 0.0
        return t[idx.getValue(a) * n + idx.getValue(b)]
    }

    fun stage(a: String, b: String): Ladder = Ladder.values()[stageState[idx.getValue(a) * n + idx.getValue(b)]]

    fun baseTension(a: String, b: String): Double = base[idx.getValue(a) * n + idx.getValue(b)]

    /** Dorongan perilaku (mis. lobi, tarif, pemilu) pada pasangan negara; positif = memanas. */
    fun nudge(a: String, b: String, delta: Double) {
        if (a == b) return
        val i = idx.getValue(a); val j = idx.getValue(b)
        val v = (t[i * n + j] + delta).coerceIn(0.0, 100.0)
        t[i * n + j] = v; t[j * n + i] = v
    }

    /** Dorong semua pasangan Sundara–X (mis. lobi pemain meredam tekanan). */
    fun nudgeSundara(delta: Double) {
        for (id in Countries.coreIds) if (id != "sundara") nudge("sundara", id, delta)
    }

    fun stance(country: String): Stance = stances[country] ?: Stance.NEUTRAL

    /** Bulan sampai pemilu berikutnya di [country] (>= 0), 9999 bila tidak ada. */
    fun monthsToElection(country: String, turn: Int): Int {
        val e = elections.first { it.country == country }
        return max(0, e.turn - turn)
    }

    /** Tingkat penutupan selat 0..1; naik saat ketegangan pasangan selat di atas 68. */
    fun strait(id: String): Double {
        val s = straits.first { it.id == id }
        return ((tension(s.a, s.b) - 68.0) / 22.0).coerceIn(0.0, 1.0)
    }

    fun anyBlockade(): Boolean = straits.any { strait(it.id) > 0.0 }

    /** Sektor yang terpukul sanksi terhadap [target], berdasarkan spesialisasi negara itu. */
    private fun sectorsOf(target: String): Set<Sector> = when (target) {
        "velmora" -> setOf(Sector.TEKNOLOGI, Sector.MANUFAKTUR)
        "norvaldia" -> setOf(Sector.ENERGI)
        "kestrel" -> setOf(Sector.MANUFAKTUR, Sector.TEKNOLOGI)
        "tarsis" -> setOf(Sector.PANGAN)
        "meridia" -> setOf(Sector.PERBANKAN)
        "halvenia" -> setOf(Sector.FARMASI, Sector.MANUFAKTUR)
        "sundara" -> setOf(Sector.TEKSTIL)
        else -> setOf(Sector.PERBANKAN)
    }

    private fun pickOutcome(r: Random, inflation: Double, unemployment: Double): ElectionType {
        var wPop = 0.25; var wMkt = 0.30; var wPro = 0.25; var wGreen = 0.20
        if (inflation > 6.0) { wMkt += 0.15; wPro += 0.05; wPop -= 0.10 }
        if (unemployment > 7.0) { wPop += 0.18; wPro += 0.04; wMkt -= 0.10 }
        if (inflation < 3.0 && unemployment < 6.0) { wGreen += 0.12; wMkt += 0.04 }
        val total = wPop + wMkt + wPro + wGreen
        val u = r.nextDouble() * total
        return when {
            u < wPop -> ElectionType.POPULIS
            u < wPop + wMkt -> ElectionType.PRO_PASAR
            u < wPop + wMkt + wPro -> ElectionType.PROTEKSIONIS
            else -> ElectionType.HIJAU
        }
    }

    private fun stanceFor(type: ElectionType): Stance = when (type) {
        ElectionType.POPULIS -> Stance(type, 1.0, 1.5, 0.0, -0.6)
        ElectionType.PRO_PASAR -> Stance(type, -2.0, -0.5, 0.0, 0.2)
        ElectionType.PROTEKSIONIS -> Stance(type, 5.0, 0.5, -0.1, 0.0)
        ElectionType.HIJAU -> Stance(type, 0.0, 0.8, 0.5, 0.0)
    }

    /**
     * Satu bulan dinamika geopolitik.
     * @param crisisPressure tekanan krisis global per bulan (0 = tidak ada; ~0,25 selama krisis), menaikkan faktor global
     *   yang bersifat AR(1) dan menggeser target ketegangan.
     * @param macro (inflasi %, pengangguran %) suatu negara, dipakai menentukan hasil pemilu.
     */
    fun step(turn: Int, r: Random, crisisPressure: Double, macro: (String) -> Pair<Double, Double>, notes: MutableList<DuniaNote>) {
        globalFactor = 0.92 * globalFactor + stdNormal(r) * 0.35 + crisisPressure
        val kappa = 0.03
        for (i in 0 until n) for (j in i + 1 until n) {
            val k = i * n + j
            val z = stdNormal(r)
            val pw = Countries.all[i].power + Countries.all[j].power
            // Faktor global menggeser TARGET (bukan menambah tiap bulan), sehingga geserannya terbatas.
            // Tren eskalasi antar-era (dokumen: tekanan terus naik sampai 2070) hanya pada pasangan Sundara–X:
            // inilah yang menggerakkan kontrol modal dan nasionalisasi aset anak usaha. Pasangan antar-negara asing
            // tetap mean-reverting; lonjakannya berasal dari krisis dan peristiwa, bukan tren buta.
            val sundaraPair = ids[i] == "sundara" || ids[j] == "sundara"
            val trend = if (sundaraPair) DuniaTuning.tensionDriftPerTurn * turn else 0.0
            val target = base[k] + trend + 4.0 * globalFactor * (0.6 + 0.5 * pw)
            val sigma = if (sundaraPair) DuniaTuning.pairNoise else DuniaTuning.foreignPairNoise
            var v = t[k] + kappa * (target - t[k]) + sigma * z
            v = v.coerceIn(0.0, 100.0)
            t[k] = v; t[j * n + i] = v
        }
        // Tangga eskalasi dengan histeresis 3 poin saat menurun (tidak berkedip di sekitar ambang).
        for (i in 0 until n) for (j in i + 1 until n) {
            val k = i * n + j
            val cur = stageState[k]
            val up = Ladder.of(t[k]).rung
            var next = cur
            if (up > cur) next = up
            else if (up < cur && t[k] < Ladder.values()[cur].from - 3.0) next = up
            if (next != cur) {
                stageState[k] = next; stageState[j * n + i] = next
                val a = Countries.all[i]; val b = Countries.all[j]
                val relevant = a.id == "sundara" || b.id == "sundara" || (a.core && b.core && next >= 3)
                if (relevant) {
                    val lad = Ladder.values()[next]
                    val msg = "${a.name}–${b.name}: ${if (next > cur) "eskalasi" else "mereda"} ke tingkat \"${lad.label}\" (skor ${t[k].toInt()}). ${lad.effect}"
                    history += "t$turn $msg"
                    notes += DuniaNote(msg, if (next > cur) (if (next >= 3) Tone.BAD else Tone.WARN) else Tone.INFO,
                        if (next >= 3 && next > cur) "Eskalasi geopolitik ${a.name}–${b.name}" else null,
                        "Perilaku negara & krisis global → skor ketegangan naik → ${lad.label.lowercase()} → biaya dan risiko bisnis berubah.")
                }
            }
        }
        // Sanksi: negara berkekuatan lebih besar menjatuhkan sanksi pada negara inti lain saat tangga >= 3.
        for (i in 0 until n) for (j in i + 1 until n) {
            val a = Countries.all[i]; val b = Countries.all[j]
            // Sundara (negara asal pemain) tidak disanksi: risikonya lewat tarif, kontrol modal, dan nasionalisasi.
            if (!(a.core && b.core) || a.id == "sundara" || b.id == "sundara") continue
            val st = stageState[i * n + j]
            val (hi, lo) = if (a.power >= b.power) a to b else b to a
            val existing = sanctions.firstOrNull { it.from == hi.id && it.target == lo.id }
            if (st >= 3 && abs(a.power - b.power) >= 0.10 && existing == null) {
                sanctions += Sanction(hi.id, lo.id, turn, sectorsOf(lo.id), 1.0 + 0.5 * (st - 3))
                notes += DuniaNote(
                    "${hi.name} menjatuhkan sanksi ekonomi pada ${lo.name} (sektor ${sectorsOf(lo.id).joinToString { it.label }}). Pemasok Tier-2/3 yang memakai bahan dari ${lo.name} berisiko terdampak.",
                    Tone.BAD, "Sanksi ${hi.name} → ${lo.name}",
                    "Ketegangan >= 60 → sanksi negara → sektor terpukul → perusahaan yang bergantung pada rantai pasok itu.")
            } else if (existing != null) {
                if (st >= 3) existing.severity = 1.0 + 0.5 * (st - 3)
                else if (st <= 2) {
                    sanctions.remove(existing)
                    notes += DuniaNote("${hi.name} mencabut sanksi atas ${lo.name} setelah ketegangan mereda.", Tone.INFO)
                }
            }
        }
        // Pemilu.
        for (e in elections) {
            if (e.turn != turn) continue
            val (infl, unemp) = macro(e.country)
            val out = pickOutcome(Random(seed * 17L + turn * 7_919L + ids.indexOf(e.country) * 31L), infl, unemp)
            val st = stanceFor(out)
            stances[e.country] = st
            val c = Countries.byId.getValue(e.country)
            // Perilaku pasca-pemilu: proteksionis memanaskan hubungan dagang, pro-pasar mendinginkan.
            when (out) {
                ElectionType.PROTEKSIONIS -> for (o in Countries.all) if (o.id != e.country && (o.block != c.block)) nudge(e.country, o.id, 3.5)
                ElectionType.PRO_PASAR -> for (o in Countries.all) if (o.id != e.country) nudge(e.country, o.id, -2.5)
                ElectionType.POPULIS -> nudge(e.country, if (c.block.anchor == e.country) "aurelia" else c.block.anchor, 2.0)
                ElectionType.HIJAU -> {}
            }
            e.turn += cycles.getValue(e.country)
            if (e.country == "sundara" || c.core) {
                notes += DuniaNote("Pemilu ${c.name}: pemenang berhaluan ${out.label}. Kebijakan tarif, fiskal, dan karbon bergeser.", Tone.INFO,
                    "Pemilu ${c.name}", "Pemilu → haluan pemerintah → tarif/fiskal/karbon berubah → ketegangan dagang bergerak.")
            }
        }
    }

    /** Pengali biaya logistik global dari penutupan selat (1,0 = normal). */
    fun shippingCostMultiplier(): Double = 1.0 + straits.sumOf { strait(it.id) } * 0.9

    fun activeSanctionsOn(country: String): List<Sanction> = sanctions.filter { it.target == country }

    fun stageSummary(): Map<Int, Int> {
        val m = HashMap<Int, Int>()
        for (i in 0 until n) for (j in i + 1 until n) m.merge(stageState[i * n + j], 1, Int::plus)
        return m
    }

    fun meanTension(): Double {
        var s = 0.0; var c = 0
        for (i in 0 until n) for (j in i + 1 until n) { s += t[i * n + j]; c++ }
        return s / c
    }
}
