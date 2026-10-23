package com.example.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Tahap C1: generator kartu kejadian bertemplat.
 *
 * Tiap kartu berasal dari satu baris tabel (lihat `CardData*.kt`) dan salah satu dari delapan arketipe trade-off.
 * Syarat C1: (a) kartu dipicu kondisi dunia (pemicu `Trig`, bukan acak murni), (b) ≥2 pilihan dengan trade-off nyata
 * (tiga pilihan dengan profil risiko-imbalan berbeda), (c) lolos validasi `ChainCardsTest`, (d) ≥100 kartu berantai
 * dengan penjelasan sebab-akibat (`ChainRule.cause`, tampil sebagai peringatan kartu lanjutan).
 *
 * Kartu baru diundi dari aliran acak sendiri (rng 125) sehingga dek tulisan tangan lama tidak bergeser.
 * Skala kerugian dan frekuensi dapat disetel lewat [CardTuning].
 */
object CardTuning {
    /** Pengali besar semua kerugian/keuntungan kartu generator. */
    @JvmField var severityScale = 1.0
    /** Peluang undi kartu generator per giliran (sebelum pengali kesulitan). */
    @JvmField var drawChance = 0.20
}

enum class Arch { SHOCK, DEAL, REG, INCIDENT, COMPETE, INVEST, GOV, FIN }
enum class Loss { COST, COGS, REV, PROD }

/** Pemicu: nilai > 0 = aktif (makin besar makin kuat), ≤ 0 = tidak aktif. */
class Trig(val text: String, val f: (EventCtx) -> Double)

object EventCards {
    private fun fmt(x: Double) = KapitalEngine.fmt(x)

    // ------------------------------------------------------------------ pemicu
    private fun hi(th: Double, scale: Double, get: (EventCtx) -> Double): (EventCtx) -> Double =
        { c -> val v = get(c); if (v > th) (0.5 + (v - th) / scale).coerceAtMost(2.0) else 0.0 }

    private fun lo(th: Double, scale: Double, get: (EventCtx) -> Double): (EventCtx) -> Double =
        { c -> val v = get(c); if (v < th) (0.5 + (th - v) / scale).coerceAtMost(2.0) else 0.0 }

    private fun flag(b: (EventCtx) -> Boolean): (EventCtx) -> Double = { c -> if (b(c)) 1.0 else 0.0 }

    /** Daftar nama pemicu; parameter numerik dipisah titik dua (mis. `inflHi:5`). Beberapa pemicu digabung `&` (DAN). */
    fun trigger(spec: String): Trig? {
        if (spec == "chain") return null
        val parts = spec.split("&").map { p ->
            val name = p.substringBefore(":"); val a = p.substringAfter(":", "").toDoubleOrNull() ?: 0.0
            name to a
        }
        val fs = parts.map { (name, a) ->
            when (name) {
                "always" -> { _: EventCtx -> 1.0 }
                "inflHi" -> hi(a, 3.0) { it.inflation }
                "inflLo" -> lo(a, 2.0) { it.inflation }
                "unempLo" -> lo(a, 2.0) { it.unemployment }
                "unempHi" -> hi(a, 3.0) { it.unemployment }
                "rateHi" -> hi(a, 3.0) { it.policyRate }
                "rateUp" -> hi(a, 1.0) { it.policyDelta }
                "kursWeak" -> hi(a, 5.0) { it.kursDepr6m }
                "kursStrong" -> lo(-a, 5.0) { it.kursDepr6m }
                "boom" -> hi(a, 0.1) { it.econ }
                "slump" -> lo(a, 0.1) { it.econ }
                "cashLow" -> { c: EventCtx -> if (c.cash < c.m(a)) 1.0 else 0.0 }
                "cashHigh" -> { c: EventCtx -> if (c.cash > c.m(a)) 1.0 else 0.0 }
                "dsoHi" -> hi(a, 20.0) { it.dso }
                "cottonHi" -> hi(a, 0.3) { it.cottonIndex }
                "cottonLo" -> lo(a, 0.3) { it.cottonIndex }
                "strike" -> flag { it.strikeActive }
                "breach" -> flag { it.covenantBreaches > 0 }
                "ethicsHi" -> hi(a, 3.0) { it.ethicsLoad }
                "dumping" -> flag { it.dumping }
                "floating" -> flag { it.hasFloatingDebt }
                "public" -> flag { it.isPublic }
                "profitFall" -> flag { it.profitFalling }
                "panicHi" -> hi(a, 30.0) { it.panic }
                "ratingLow" -> flag { it.ratingIdx >= a.toInt() }
                "tensionHi" -> hi(a, 20.0) { it.tension }
                "sanction" -> flag { it.sanctioned }
                "blockade" -> flag { it.blockade }
                "election" -> flag { it.monthsToElection <= a.toInt() }
                "utilHi" -> hi(a, 0.1) { it.util }
                "utilLo" -> lo(a, 0.2) { it.util }
                "priceCut" -> flag { it.priceAdj < 0 }
                "priceHigh" -> flag { it.priceAdj >= a.toInt() }
                "crisis" -> flag { it.gapPct < -2.0 }
                "oilHi" -> hi(a, 0.3) { it.oilIndex }
                "sentLow" -> lo(a, 0.2) { it.sentiment }
                "era" -> flag { it.era >= a.toInt() }
                "workers" -> flag { it.workers >= a.toInt() }
                else -> throw IllegalArgumentException("pemicu tidak dikenal: $name")
            }
        }
        return Trig(spec) { c -> fs.minOf { it(c) } }
    }

    // ------------------------------------------------------------------ profil pilihan
    /** (peluang, hasil bersih dalam satuan L; negatif = kerugian). */
    private class Prof(val ap: Int, val up: Double, val outs: List<Pair<Double, Double>>, val ethics: Double = 0.0, val trust: Int = 0, val cust: Double = 0.0)

    private fun profiles(a: Arch): List<Prof> = when (a) {
        Arch.SHOCK -> listOf(
            Prof(1, 0.0, listOf(1.0 to -1.0)),
            Prof(1, 0.0, listOf(0.6 to -0.6, 0.4 to -1.4), cust = 0.004),
            Prof(2, 0.35, listOf(0.65 to -0.25, 0.35 to -1.0)))
        Arch.DEAL -> listOf(
            Prof(1, 0.0, listOf(0.75 to 1.0, 0.25 to -0.8)),
            Prof(2, 0.15, listOf(0.5 to 1.6, 0.5 to 0.0)),
            Prof(0, 0.0, listOf(1.0 to 0.0)))
        Arch.REG -> listOf(
            Prof(2, 0.8, listOf(1.0 to 0.0), trust = 1),
            Prof(1, 0.3, listOf(0.5 to 0.0, 0.5 to -1.6), ethics = 1.0),
            Prof(0, 0.0, listOf(0.4 to 0.0, 0.6 to -1.8)))
        Arch.INCIDENT -> listOf(
            Prof(2, 0.7, listOf(1.0 to 0.0)),
            Prof(1, 0.25, listOf(0.55 to 0.0, 0.45 to -1.5)),
            Prof(0, 0.0, listOf(0.3 to 0.0, 0.7 to -1.6)))
        Arch.COMPETE -> listOf(
            Prof(0, 0.0, listOf(0.5 to -0.3, 0.5 to -1.6), cust = 0.006),
            Prof(1, 0.0, listOf(1.0 to -0.9)),
            Prof(2, 0.4, listOf(0.45 to 0.5, 0.55 to -1.1)))
        Arch.INVEST -> listOf(
            Prof(2, 1.0, listOf(0.6 to 2.2, 0.4 to 0.2)),
            Prof(1, 0.4, listOf(0.45 to 1.5, 0.55 to 0.1)),
            Prof(0, 0.0, listOf(1.0 to 0.0)))
        Arch.GOV -> listOf(
            Prof(2, 0.6, listOf(1.0 to 0.0), trust = 3),
            Prof(1, 0.9, listOf(1.0 to 0.0), ethics = 0.8),
            Prof(0, 0.0, listOf(0.5 to 0.0, 0.5 to -2.0), ethics = 1.5))
        Arch.FIN -> listOf(
            Prof(1, 0.0, listOf(1.0 to -0.9)),
            Prof(2, 0.2, listOf(0.55 to -0.2, 0.45 to -1.6)),
            Prof(2, 0.5, listOf(1.0 to -0.4)))
    }

    private fun tones(a: Arch): List<Tone> = when (a) {
        Arch.SHOCK, Arch.FIN -> listOf(Tone.WARN, Tone.INFO, Tone.GOOD)
        Arch.DEAL, Arch.INVEST -> listOf(Tone.INFO, Tone.INFO, Tone.NEUTRAL)
        Arch.REG, Arch.INCIDENT -> listOf(Tone.GOOD, Tone.WARN, Tone.BAD)
        Arch.COMPETE -> listOf(Tone.BAD, Tone.WARN, Tone.INFO)
        Arch.GOV -> listOf(Tone.GOOD, Tone.WARN, Tone.BAD)
    }

    private fun advisors(a: Arch): List<String> = when (a) {
        Arch.SHOCK -> listOf("menurut Direktur Keuangan", "menurut Direktur Penjualan", "menurut Direktur Pembelian")
        Arch.DEAL -> listOf("menurut Direktur Penjualan", "menurut Direktur Keuangan", "menurut Anda")
        Arch.REG -> listOf("menurut Penasihat Hukum", "menurut Kepala Hubungan Pemerintah", "menurut Anda")
        Arch.INCIDENT -> listOf("menurut Kepala Pabrik", "menurut Direktur Operasi", "menurut Anda")
        Arch.COMPETE -> listOf("menurut Direktur Penjualan", "menurut Direktur Penjualan", "menurut Direktur Strategi")
        Arch.INVEST -> listOf("menurut Direktur Strategi", "menurut Direktur Operasi", "menurut Anda")
        Arch.GOV -> listOf("menurut Komite Audit", "menurut Penasihat Hukum", "menurut Anda")
        Arch.FIN -> listOf("menurut Direktur Keuangan", "menurut Bankir", "menurut Penasihat Investasi")
    }

    /** Efek dari hasil bersih [net] (satuan L, bernilai L jt RS) menurut jenis kerugian. */
    private fun netEffects(net: Double, loss: Loss, c: EventCtx, l: Double, months: Int): List<Effect> {
        if (net == 0.0) return emptyList()
        val amt = abs(net) * l
        val mRev = c.m(12500.0); val mCogs = c.m(8500.0)
        return when (loss) {
            Loss.COST -> listOf(if (net < 0) Effect("cost", amt) else Effect("income", amt))
            Loss.COGS -> listOf(Effect("mod", (1.0 + sign(-net) * amt / (months * mCogs)).coerceIn(0.85, 1.15), months, "cogs"))
            Loss.REV -> listOf(Effect("mod", (1.0 + sign(net) * amt / (0.45 * months * mRev)).coerceIn(0.85, 1.15), months, "rev"))
            Loss.PROD -> listOf(Effect("mod", (1.0 + sign(net) * amt / (0.45 * months * mRev)).coerceIn(0.85, 1.15), months, "prod"))
        }
    }

    private fun abs(x: Double) = if (x < 0) -x else x
    private fun sign(x: Double) = if (x < 0) -1.0 else 1.0

    // ------------------------------------------------------------------ kartu
    class Card(
        val id: String, val cat: String, val arch: Arch, val loss: Loss, val sev: Double, val trigText: String, val w: Double,
        val title: String, val body: String, val warn: String, val opts: List<String>, val chain: ChainSpec?, val months: Int, val cooldown: Int
    ) {
        val trig: Trig? = trigger(trigText)
    }

    class ChainSpec(val to: String, val from: Set<String>, val p: Double, val delay: Int, val cause: String)

    /** Baris: id | kategori | ARSIP | JENIS | sev | pemicu | bobot | judul | isi | peringatan | pilihanA | pilihanB | pilihanC | rantai[ | bulan | jeda]. */
    fun parse(data: String): List<Card> = data.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.map { line ->
        val f = line.split(" | ").map { it.trim() }
        require(f.size >= 14) { "baris kartu kurang kolom (${f.size}): ${line.take(60)}" }
        val chain = f[13].takeIf { it.isNotEmpty() && it != "-" }?.let { s ->
            val p = s.split(":", limit = 5)
            require(p.size == 5) { "rantai salah format: $s" }
            ChainSpec(p[0], p[1].toSet().map { it.toString() }.toSet(), p[2].toDouble(), p[3].toInt(), p[4])
        }
        Card(f[0], f[1], Arch.valueOf(f[2]), Loss.valueOf(f[3]), f[4].toDouble(), f[5], f[6].toDouble(), f[7], f[8], f[9], listOf(f[10], f[11], f[12]), chain,
            f.getOrNull(14)?.toIntOrNull() ?: 4, f.getOrNull(15)?.toIntOrNull() ?: 24)
    }.toList()

    val cards: List<Card> by lazy { CardData.all().flatMap { parse(it) } }

    /** Kartu tuntutan (bukan tawaran sukarela): menolak = pengali (kunci, nilai) selama [Card.months] bulan. */
    private val DECLINE_BACKLASH: Map<String, Pair<String, Double>> = mapOf("hr_remote_request" to ("prod" to 0.99))

    fun build(card: Card, c: EventCtx, id: String, turn: Int): GameEvent {
        val sev = card.sev * CardTuning.severityScale
        val l = c.m(150.0 * sev)
        val profs = profiles(card.arch); val tn = tones(card.arch); val adv = advisors(card.arch)
        val ids = listOf("a", "b", "c")
        val options = ids.indices.map { i ->
            val pr = profs[i]
            val up = pr.up * l
            // Menolak tuntutan pihak yang punya pengaruh (bukan sekadar melewatkan tawaran) memicu reaksi, bukan status quo gratis.
            val backlash = if (i == 2) DECLINE_BACKLASH[card.id] else null
            val outs = if (backlash != null) listOf(Outcome(1.0, "Penolakan Anda mengecewakan pihak itu; dampaknya terasa ${card.months} bulan.",
                listOf(Effect("mod", backlash.second, card.months, backlash.first)))) else pr.outs.map { (p, net) ->
                val eff = mutableListOf<Effect>()
                if (up > 0.0) eff += Effect("cost", up)
                eff += netEffects(net, card.loss, c, l, card.months)
                if (net < 0 && pr.cust > 0.0) eff += Effect("lostcust", pr.cust * card.sev)
                if (pr.ethics > 0.0) eff += Effect("ethics", pr.ethics, 0, "card_${card.id}")
                if (pr.trust != 0) eff += Effect("board_trust", pr.trust.toDouble())
                Outcome(p, outcomeText(card.arch, i, net, p, pr.outs.size), eff)
            }
            val impactText = if (backlash != null) "penolakan berisiko: ${if (backlash.first == "prod") "produksi" else "pendapatan"} turun ±${fmt((1.0 - backlash.second) * 100.0)}% selama ${card.months} bulan"
                else impact(pr, l)
            EventOption(ids[i], card.opts[i], pr.ap, impactText, adv[i], tn[i], outs)
        }
        val default = if (card.arch == Arch.DEAL || card.arch == Arch.INVEST) "c" else "a"
        val body = card.body.replace("{L}", fmt(l))
        return GameEvent(id, card.id, card.cat, card.title, body, card.warn, options, turn, turn + 1, default)
    }

    private fun impact(pr: Prof, l: Double): String {
        val up = if (pr.up > 0.0) "biaya awal ±${fmt(pr.up * l)} jt RS; " else ""
        val lo = pr.outs.minOf { it.second }; val hi = pr.outs.maxOf { it.second }
        val range = when {
            lo == hi && lo == 0.0 -> "tanpa dampak lanjutan"
            lo == hi -> "${if (lo < 0) "kerugian" else "keuntungan"} pasti ±${fmt(abs(lo) * l)} jt RS"
            lo < 0 && hi <= 0 -> "kerugian ±${fmt(abs(hi) * l)}–${fmt(abs(lo) * l)} jt RS"
            lo >= 0 -> "hasil ±${fmt(lo * l)}–${fmt(hi * l)} jt RS (tidak pasti)"
            else -> "antara rugi ${fmt(abs(lo) * l)} dan untung ${fmt(hi * l)} jt RS"
        }
        val eth = if (pr.ethics > 0.0) "; menambah bukti etika" else ""
        return up + range + eth
    }

    private fun outcomeText(a: Arch, opt: Int, net: Double, p: Double, n: Int): String {
        if (n == 1) return when {
            net > 0 -> "Langkah ini berhasil dan memberi keuntungan."
            net < 0 -> "Anda menanggung kerugiannya sekarang."
            else -> "Masalah ditutup dengan rapi."
        }
        return if (net > 0) "Hasilnya lebih baik dari perkiraan." else if (net < 0) "Hasilnya merugikan; risiko yang dipilih terjadi." else "Tidak ada dampak lanjutan."
    }

    // ------------------------------------------------------------------ dek
    val templates: List<Template> by lazy {
        cards.map { card ->
            Template(card.id, card.cat, card.cooldown, { c -> card.trig?.let { t -> card.w * t.f(c) } ?: 0.0 }) { c, _, id, t -> build(card, c, id, t) }
        }
    }

    val chainRules: List<EventDeck.ChainRule> by lazy {
        cards.mapNotNull { card -> card.chain?.let { EventDeck.ChainRule(card.id, it.from, it.p, it.to, it.delay, it.cause) } }
    }

    /** Undi sebuah kartu generator (null bila tidak ada giliran ini). */
    fun draw(ctx: EventCtx, r: Random, open: Set<String>, lastFired: Map<String, Int>, counter: Int, rateMult: Double): GameEvent? {
        if (r.nextDouble() >= (CardTuning.drawChance * rateMult).coerceIn(0.0, 0.6)) return null
        val cands = cards.filter { it.trig != null && it.id !in open && ctx.turn - (lastFired[it.id] ?: -999) >= it.cooldown }
            .map { it to it.w * it.trig!!.f(ctx) }.filter { it.second > 0.0 }
        if (cands.isEmpty()) return null
        var x = r.nextDouble() * cands.sumOf { it.second }
        for ((card, w) in cands) {
            if (x < w) return build(card, ctx, "evt_${ctx.turn}_${counter}_${card.id}", ctx.turn)
            x -= w
        }
        return null
    }
}
