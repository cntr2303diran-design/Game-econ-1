package com.example.engine

import kotlin.random.Random

/**
 * Orkestrator lapisan dunia (Tahap A): geopolitik, negara sekunder, sektor/rantai pasok, dan (menyusul) makro lanjut,
 * informasi, serta siklus jangka panjang.
 *
 * Prinsip desain: lapisan ini HANYA memengaruhi mesin lewat saluran yang sudah ada (permintaan, biaya, kurs, kredit,
 * suku bunga) dan lewat catatan laporan. Tidak ada arus kas baru yang dibuat di sini, sehingga identitas akuntansi
 * (neraca, arus kas, konsolidasi) terjaga secara konstruksi. Semua keluaran dibatasi agar tidak menggoyang mesin.
 */
class DuniaContext(
    val turn: Int,
    /** Kesenjangan output Sundara (permintaan - 1). */
    val gap: Double,
    /** Tekanan suku bunga riil terhadap sektor peka bunga (negatif = ketat). */
    val rateDrag: Double,
    val oilIndex: Double,
    val crisisActive: Boolean,
    val linkedBlocks: Set<Block>,
    val anchorGrowth: Map<Block, Double>,
    /** (inflasi %, pengangguran %) suatu negara; dipakai pemilu. */
    val macro: (String) -> Pair<Double, Double>,
    /** Masukan makro lengkap tiap negara (24) untuk modul makro lanjut. */
    val macroInput: (String) -> MacroInput
)

class DuniaEffects(
    val demandShock: Double,
    val costMult: Double,
    val creditShock: Double,
    val kursDrift: Double,
    val policyBias: Double,
    val notes: List<DuniaNote>,
    /** Bias aturan Taylor bank sentral negara inti asing. */
    val foreignBias: Map<String, Double> = emptyMap()
) {
    companion object {
        val NEUTRAL = DuniaEffects(0.0, 1.0, 0.0, 0.0, 0.0, emptyList())
    }
}

/**
 * Tombol kalibrasi lapisan dunia. Nilai awal adalah nilai yang dikirim; harness eksperimen mengubahnya hanya
 * untuk mengukur sensitivitas keseimbangan. Jangan diubah di dalam mesin saat permainan berjalan.
 */
object DuniaTuning {
    /** Pengali dampak ke mesin (permintaan, biaya, kredit). 0 = lapisan dunia tidak memengaruhi mesin. */
    @JvmField var effectScale = 1.0
    /** Tren eskalasi target ketegangan pasangan Sundara–X (poin per giliran absolut). */
    @JvmField var tensionDriftPerTurn = 0.035
    /** Simpangan baku kebisingan bulanan pasangan Sundara–X (poin). */
    @JvmField var pairNoise = 1.9
    /** Redaman dampak dunia pada ekonomi pemain di Era 1 (rantai pasok lintas negara baru dibuka di Era 2). */
    @JvmField var era1Gate = 0.35
    /** Simpangan baku kebisingan bulanan pasangan antar-negara asing (poin). */
    @JvmField var foreignPairNoise = 1.05
}

class Dunia(seed: Long, sundaraElectionTurn: Int, sundaraBaseTension: Map<String, Int>) {

    val geo = Geopolitics(seed, sundaraElectionTurn, sundaraBaseTension)
    val secondary = SecondaryWorld()
    val sectors = SectorModel()
    val macro = MacroPlus(seed)

    private val discovered = HashSet<String>()
    private var lastCostBand = 0
    var lastEffects: DuniaEffects = DuniaEffects.NEUTRAL; private set

    /** Simpul rantai yang kini terlihat oleh pemain (Tier 1, plus Tier 2/3 yang terbongkar atau diuji tuntas). */
    fun visibleChain(): List<ChainNode> = sectors.chain.filter { !it.hidden || sectors.chainAudited || it.id in discovered }

    /** Uji tuntas rantai pasok: membuka simpul tersembunyi dan meredam dampak sanksi (tidak pernah 100%). */
    fun auditChain() { sectors.chainAudited = true }

    fun step(ctx: DuniaContext, rng: (Int) -> Random): DuniaEffects {
        val notes = mutableListOf<DuniaNote>()
        val crisisPressure = if (ctx.crisisActive) 0.30 else 0.0
        geo.step(ctx.turn, rng(101), crisisPressure, ctx.macro, notes)
        secondary.step(ctx.turn, rng(102), ctx.linkedBlocks, ctx.anchorGrowth, ctx.oilIndex, notes)
        val sr = sectors.step(rng(103), ctx.gap, ctx.rateDrag, geo, 0.0, notes)

        // Paparan sanksi tersembunyi baru terungkap saat sanksi sudah menyentuhnya (bom waktu rantai pasok).
        if (!sectors.chainAudited) {
            for (nd in sectors.exposedNodes(geo)) if (nd.hidden && discovered.add(nd.id)) {
                val sn = geo.sanctions.first { it.target == nd.country && nd.sector in it.sectors }
                val from = Countries.byId.getValue(sn.from).name
                notes += DuniaNote(
                    "Terungkap: simpul Tier-${nd.tier} \"${nd.label}\" (${Countries.byId.getValue(nd.country).name}) ternyata menopang rantai pasok Anda, dan kini terkena sanksi $from. Bank menaikkan premi risiko.",
                    Tone.BAD, "Bom waktu rantai pasok: ${nd.label}",
                    "Pemasok Tier-2/3 memakai bahan dari negara tersanksi → paparan tersembunyi → biaya naik dan kreditur memperketat syarat.")
            }
        }

        // Ekspor: permintaan blok yang terhubung dagang menggeser permintaan Sundara sedikit saja.
        val linked = ctx.linkedBlocks.map { secondary.blockDemandIndex(it) }
        val exportAdj = if (linked.isEmpty()) 0.0 else (0.08 * (linked.average() - 1.0)).coerceIn(-0.01, 0.01)

        val k = DuniaTuning.effectScale * (if (KapitalEngine.era(ctx.turn) == 1) DuniaTuning.era1Gate else 1.0)
        val demand = ((sr.demandAdj + exportAdj) * k).coerceIn(-0.03, 0.01)
        val cost = 1.0 + (sr.playerCostMult.coerceIn(0.97, 1.25) - 1.0) * k
        val band = if (cost >= 1.10) 2 else if (cost >= 1.04) 1 else 0
        if (band > lastCostBand) {
            notes += DuniaNote("Biaya rantai pasok Anda naik sekitar ${((cost - 1.0) * 100).toInt()}% akibat sanksi, kelangkaan sektor, atau gangguan hulu.",
                if (band >= 2) Tone.BAD else Tone.WARN)
        }
        lastCostBand = band

        val mx = macro.step(
            ctx.turn, rng(104), ctx.macroInput, geo,
            if (linked.isEmpty()) 1.0 else linked.average(), ctx.oilIndex, ctx.crisisActive, notes
        )
        val demandAll = (demand + mx.demandAdd * k).coerceIn(-0.04, 0.01)
        val fx = DuniaEffects(
            demandAll, cost, (sr.creditAdd * 0.2 + mx.creditAdd) * k, mx.kursDrift * k, mx.policyBias * k, notes,
            mx.foreignBias.mapValues { it.value * k }
        )
        lastEffects = fx
        return fx
    }

    /** Blok yang terhubung dagang dengan pemain: pemasok (Kestrel, Tarsis), pembiayaan (Aurelia), dan lokasi anak usaha aktif. */
    companion object {
        fun baselineLinks(): MutableSet<Block> = mutableSetOf(Block.TIMUR, Block.TROPIKA, Block.SAMUDRA)
    }
}
