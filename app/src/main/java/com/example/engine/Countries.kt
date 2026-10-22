package com.example.engine

import kotlin.math.abs
import kotlin.math.exp
import kotlin.random.Random

/**
 * Registri 24 negara fiktif (dokumen desain bagian 4): 8 inti yang disimulasikan penuh dan 16 sekunder
 * dalam 4 blok regional (4 negara per blok). Negara sekunder tidak punya agen individual; ia dihitung sebagai
 * model ringkas dan baru disimulasikan penuh bila terhubung dagang dengan pemain.
 */
enum class Block(val label: String, val anchor: String) {
    SAMUDRA("Blok Samudra Barat", "aurelia"),
    UTARA("Blok Utara Beku", "halvenia"),
    TIMUR("Blok Timur Baja", "kestrel"),
    TROPIKA("Blok Tropika", "tarsis")
}

/** @param power bobot kekuatan geopolitik (0..1); negara berkekuatan lebih besar menjatuhkan sanksi. */
class Country(val id: String, val name: String, val core: Boolean, val block: Block, val power: Double)

class SecondaryProfile(
    val country: Country,
    val inflation0: Double,
    val policy0: Double,
    val growth0: Double,
    val tariffOnSundara0: Double,
    val risk0: Int,
    /** Sensitivitas pertumbuhan terhadap harga minyak (negatif = pengimpor). */
    val oilSensitivity: Double
)

object Countries {
    val core: List<Country> = listOf(
        Country("sundara", "Sundara", true, Block.TROPIKA, 0.30),
        Country("aurelia", "Aurelia", true, Block.SAMUDRA, 1.00),
        Country("norvaldia", "Norvaldia", true, Block.UTARA, 0.50),
        Country("kestrel", "Kestrel", true, Block.TIMUR, 0.80),
        Country("halvenia", "Halvenia", true, Block.UTARA, 0.70),
        Country("meridia", "Meridia", true, Block.SAMUDRA, 0.20),
        Country("tarsis", "Tarsis", true, Block.TROPIKA, 0.30),
        Country("velmora", "Velmora", true, Block.TIMUR, 0.35)
    )

    private fun sec(id: String, name: String, block: Block, i: Int): SecondaryProfile {
        // Profil dasar per blok, dengan variasi deterministik kecil per negara.
        val j = ((i * 37) % 11) - 5 // -5..5
        val (infl, growth, risk, pol) = when (block) {
            Block.SAMUDRA -> listOf(2.5, 2.2, 20.0, 3.5)
            Block.UTARA -> listOf(2.0, 1.5, 15.0, 3.0)
            Block.TIMUR -> listOf(4.0, 4.5, 40.0, 5.0)
            Block.TROPIKA -> listOf(6.5, 4.0, 50.0, 6.5)
        }
        return SecondaryProfile(
            Country(id, name, false, block, 0.04 + 0.01 * ((i * 7) % 5)),
            inflation0 = infl + 0.15 * j,
            policy0 = pol + 0.12 * j,
            growth0 = growth + 0.12 * j,
            tariffOnSundara0 = (if (block == Block.TROPIKA) 3.0 else 6.0) + 0.4 * abs(j),
            risk0 = (risk + 2 * j).toInt().coerceIn(5, 90),
            oilSensitivity = if (block == Block.UTARA) 0.10 else -0.06 - 0.01 * abs(j)
        )
    }

    val secondary: List<SecondaryProfile> = listOf(
        sec("belmara", "Belmara", Block.SAMUDRA, 0), sec("caldris", "Caldris", Block.SAMUDRA, 1),
        sec("dorvane", "Dorvane", Block.SAMUDRA, 2), sec("esperin", "Esperin", Block.SAMUDRA, 3),
        sec("fjordal", "Fjordal", Block.UTARA, 4), sec("grenholt", "Grenholt", Block.UTARA, 5),
        sec("havrik", "Havrik", Block.UTARA, 6), sec("isenmark", "Isenmark", Block.UTARA, 7),
        sec("jadewan", "Jadewan", Block.TIMUR, 8), sec("kharmin", "Kharmin", Block.TIMUR, 9),
        sec("lotrang", "Lotrang", Block.TIMUR, 10), sec("mekhor", "Mekhor", Block.TIMUR, 11),
        sec("nusamera", "Nusamera", Block.TROPIKA, 12), sec("oralang", "Oralang", Block.TROPIKA, 13),
        sec("pelitan", "Pelitan", Block.TROPIKA, 14), sec("quenara", "Quenara", Block.TROPIKA, 15)
    )

    val all: List<Country> = core + secondary.map { it.country }
    val byId: Map<String, Country> = all.associateBy { it.id }
    val coreIds: List<String> = core.map { it.id }

    fun blockMembers(b: Block): List<Country> = all.filter { it.block == b }
}

/**
 * Keadaan dinamis satu negara sekunder. Negara yang "terhubung dagang" dengan pemain disimulasikan penuh
 * (tiap bulan, dengan guncangan idiosinkratik dan peristiwa risiko); sisanya model ringkas yang hanya
 * mengikuti faktor blok dan diperbarui tiap kuartal.
 */
class SecondaryState(val profile: SecondaryProfile) {
    var growth: Double = profile.growth0          // % per tahun
    var inflation: Double = profile.inflation0    // % per tahun
    var policyRate: Double = profile.policy0
    var gdpIndex: Double = 1.0
    var risk: Int = profile.risk0                 // 0..100
    var fullySimulated: Boolean = false
    var crisisMonthsLeft: Int = 0
}

/**
 * Empat blok regional + 16 negara sekunder. Mengeluarkan indeks permintaan ekspor Sundara dan peristiwa risiko.
 */
class SecondaryWorld {
    val states: List<SecondaryState> = Countries.secondary.map { SecondaryState(it) }
    private val blockFactor = DoubleArray(Block.values().size)

    /** Indeks permintaan blok (1,0 = netral). */
    fun blockDemandIndex(b: Block): Double {
        val m = states.filter { it.profile.country.block == b }
        return m.map { it.gdpIndex }.average()
    }

    fun state(id: String): SecondaryState = states.first { it.profile.country.id == id }

    /**
     * @param linkedBlocks blok yang terhubung dagang dengan pemain (disimulasikan penuh).
     * @param anchorGrowth pertumbuhan tahunan (%) negara jangkar tiap blok, dari simulasi negara inti.
     * @param oilIndex indeks harga minyak (1,0 = awal).
     */
    fun step(
        turn: Int,
        r: Random,
        linkedBlocks: Set<Block>,
        anchorGrowth: Map<Block, Double>,
        oilIndex: Double,
        notes: MutableList<DuniaNote>
    ) {
        val quarterly = turn % 3 == 0
        for (b in Block.values()) {
            // Faktor blok mengikuti pertumbuhan jangkar (AR(1)).
            val g = anchorGrowth[b] ?: 2.5
            blockFactor[b.ordinal] = 0.85 * blockFactor[b.ordinal] + 0.15 * (g - 2.5) + 0.0
        }
        for (s in states) {
            val b = s.profile.country.block
            val linked = b in linkedBlocks
            s.fullySimulated = linked
            if (!linked && !quarterly) continue
            val dt = if (linked) 1.0 else 3.0
            val idio = if (linked) stdNormal(r) * 0.55 else 0.0
            val oilEff = s.profile.oilSensitivity * (oilIndex - 1.0) * 30.0
            val crisisEff = if (s.crisisMonthsLeft > 0) -2.5 else 0.0
            val target = s.profile.growth0 + blockFactor[b.ordinal] + oilEff + crisisEff
            s.growth += 0.25 * (target - s.growth) + idio
            s.growth = s.growth.coerceIn(-6.0, 12.0)
            s.gdpIndex *= exp((s.growth - 2.5) / 1200.0 * dt) // indeks permintaan relatif tren 2,5%
            s.gdpIndex = s.gdpIndex.coerceIn(0.6, 1.8)
            val oilPass = -s.profile.oilSensitivity * (oilIndex - 1.0) * 60.0 // pengimpor minyak: harga naik -> inflasi naik
            val inflTarget = s.profile.inflation0 + 0.35 * (s.growth - s.profile.growth0) + oilPass
            s.inflation += 0.20 * (inflTarget - s.inflation) + (if (linked) stdNormal(r) * 0.12 else 0.0)
            s.inflation = s.inflation.coerceIn(-1.0, 30.0)
            val star = 2.0 + s.inflation + 0.5 * (s.inflation - 3.0) + 0.4 * (s.growth - s.profile.growth0)
            s.policyRate += 0.25 * (star - s.policyRate)
            s.policyRate = s.policyRate.coerceIn(0.0, 25.0)
            if (s.crisisMonthsLeft > 0) s.crisisMonthsLeft--
            if (linked) {
                // Peristiwa risiko hanya muncul pada negara yang disimulasikan penuh.
                val pEvent = 0.0016 * (1.0 + s.risk / 40.0)
                if (s.crisisMonthsLeft == 0 && r.nextDouble() < pEvent) {
                    s.crisisMonthsLeft = 8 + r.nextInt(8)
                    s.risk = (s.risk + 12 + r.nextInt(10)).coerceAtMost(100)
                    notes += DuniaNote(
                        "Gejolak di ${s.profile.country.name} (${b.label}): kudeta atau gagal bayar menekan permintaan kawasan.",
                        Tone.WARN, "Gejolak di ${s.profile.country.name}",
                        "Risiko politik negara sekunder → pasar ekspor menyusut → permintaan blok turun."
                    )
                }
            }
            s.risk = (s.risk - if (s.crisisMonthsLeft == 0) (if (r.nextDouble() < 0.1) 1 else 0) else 0).coerceIn(5, 100)
        }
    }
}

/** Catatan peristiwa dari lapisan dunia; diteruskan mesin ke laporan bulanan dan kronik. */
class DuniaNote(
    val text: String,
    val tone: Tone,
    val chronicleTitle: String? = null,
    val chain: String? = null
)
