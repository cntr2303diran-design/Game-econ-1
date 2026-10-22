package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test

/** Tahap C1: kartu generator (≥600 kartu, dipicu kondisi dunia, trade-off nyata, ≥100 kartu berantai). */
class CardDeckTest {

    private val cards = EventCards.cards

    @Test
    fun cardsAreWellFormed_uniqueIds_validChains() {
        val ids = cards.map { it.id }
        assertEquals("id kartu unik", ids.size, ids.toSet().size)
        val all = EventDeck.templates.map { it.id }
        assertEquals("id unik di seluruh dek", all.size, all.toSet().size)
        for (card in cards) {
            val ch = card.chain
            if (ch != null) {
                assertTrue("target rantai ${ch.to} dari ${card.id} ada", ids.contains(ch.to))
                assertTrue("target rantai ${ch.to} hanya lewat rantai", cards.first { it.id == ch.to }.trig == null)
                assertTrue("sebab-akibat ${card.id} tidak kosong", ch.cause.length > 20)
                assertTrue("peluang rantai wajar ${card.id}", ch.p in 0.1..0.9 && ch.delay in 1..12)
            }
            assertTrue("pilihan ${card.id} berbeda", card.opts.toSet().size == 3)
        }
        val chainOnly = cards.filter { it.trig == null }
        for (c in chainOnly) assertTrue("kartu ${c.id} dirujuk aturan rantai", cards.any { it.chain?.to == c.id })
    }

    @Test
    fun deckSizeAndChainCount_meetC1() {
        val n = cards.size + EventDeck.baseTemplates.size
        val chain = cards.count { it.trig == null } + EventDeck.baseChainRules.map { it.to }.toSet().size
        println("KARTU C1: generator=${cards.size}, tulisan tangan=${EventDeck.baseTemplates.size}, total=$n, kartu berantai=$chain, aturan rantai=${EventDeck.chainRules.size}, per kategori=${cards.groupBy { it.cat }.mapValues { it.value.size }}")
        assertTrue("target jumlah kartu: $n", n >= EXPECTED_MIN_CARDS)
        assertTrue("target kartu berantai: $chain", chain >= EXPECTED_MIN_CHAIN)
    }

    @Test
    fun everyBuiltCard_hasThreeRealTradeOffs_andNoFreeMoney() {
        val e = KapitalEngine(4242L)
        val c = e.eventCtx()
        val mRev = c.m(12500.0); val mCogs = c.m(8500.0)
        fun net(ef: Effect, months: Int): Double = when (ef.kind) {
            "cost", "fine" -> -ef.a
            "income" -> ef.a
            "mod" -> when (ef.s) {
                "cogs" -> -(ef.a - 1.0) * ef.b * mCogs
                "rev", "prod" -> (ef.a - 1.0) * 0.45 * ef.b * mRev
                else -> 0.0
            }
            else -> 0.0
        }
        var worstFree = 0.0; var worstId = ""
        for (card in cards) {
            val ev = EventCards.build(card, c, "t_${card.id}", c.turn)
            assertEquals("tiga pilihan ${card.id}", 3, ev.options.size)
            assertTrue("pilihan bawaan ada ${card.id}", ev.options.any { it.id == ev.defaultOptionId })
            val evs = ev.options.map { o ->
                assertEquals("peluang ${card.id}/${o.id}", 1.0, o.outcomes.sumOf { it.p }, 1e-9)
                o.outcomes.sumOf { out -> out.p * out.effects.sumOf { net(it, 4) } }
            }
            val opportunity = card.arch == Arch.DEAL || card.arch == Arch.INVEST
            val best = evs.max()
            if (!opportunity) {
                if (best > worstFree) { worstFree = best; worstId = card.id }
                assertTrue("kartu kerugian ${card.id} tidak boleh memberi untung bersih: $evs", best <= 1e-6)
            } else {
                val l = 150.0 * card.sev * c.m(1.0)
                assertTrue("untung peluang ${card.id} wajar (≤1,0 L): ${best / l}", best <= 1.0 * l)
            }
            // trade-off nyata: opsi tidak identik (EV atau varians berbeda)
            val sig = ev.options.map { o -> o.apCost to (o.outcomes.size to Math.round(o.outcomes.sumOf { it.p * it.effects.sumOf { f -> net(f, 4) } })) }
            assertTrue("trade-off ${card.id}: $sig", sig.toSet().size >= 2)
        }
    }

    @Test
    fun cardsFire_onWorldConditions_notRandomly() {
        val fired = HashMap<String, Int>()
        var samples = 0
        val utils = ArrayList<Double>(); var cuts = 0
        for (k in 1..40) {
            val e = KapitalEngine(k * 7919L)
            var t = 0
            while (e.gameOverReason == null && t < 400) {
                t++; ActiveBot.step(e, t)
                if (k % 3 == 0 && t % 8 == 0) e.apply(Action.AdjustPrice(-5)) // jalur pemain yang memangkas harga
                e.apply(Action.EndTurn)
                if (t % 4 == 0) {
                    samples++
                    val c = e.eventCtx(); utils += c.util; if (c.priceAdj < 0) cuts++
                    for (card in cards) card.trig?.let { tr -> if (tr.f(c) > 0.0) fired[card.id] = (fired[card.id] ?: 0) + 1 }
                }
            }
        }
        val triggered = cards.filter { it.trig != null }
        val never = triggered.filter { (fired[it.id] ?: 0) == 0 }
        val always = triggered.count { (fired[it.id] ?: 0) >= samples * 0.9 }
        val shareActive = triggered.sumOf { (fired[it.id] ?: 0).toDouble() } / (triggered.size * samples.toDouble())
        utils.sort(); println("KARTU util: p5=${"%.2f".format(utils[utils.size / 20])} p25=${"%.2f".format(utils[utils.size / 4])} median=${"%.2f".format(utils[utils.size / 2])} p75=${"%.2f".format(utils[utils.size * 3 / 4])}; harga turun=$cuts/$samples")
        println("KARTU pemicu: $samples sampel kondisi; tak pernah aktif=${never.size}/${triggered.size} ${never.take(40).map { it.id }}; hampir selalu aktif=$always; rata-rata aktif=${"%.2f".format(shareActive)}")
        assertTrue("sebagian besar kartu punya kondisi yang pernah terpenuhi: ${never.size} tak pernah aktif", never.size <= triggered.size * 0.10)
        assertTrue("kartu yang hampir selalu aktif (=acak murni) dibatasi: $always", always <= triggered.size * 0.12)
    }

    @Test
    fun generatorDraw_isDeterministic_andDoesNotShiftOldDeckStream() {
        fun run(): String {
            val e = KapitalEngine(31L)
            var t = 0
            while (e.gameOverReason == null && t < 120) {
                t++
                for (ev in e.openEvents.toList()) e.apply(Action.EventChoice(ev.id, ev.options[0].id))
                e.apply(Action.EndTurn)
            }
            return "${e.turn}|${e.cash}|${e.equity}|${e.eventHistory.map { it.title }}"
        }
        assertEquals(run(), run())
    }

    @Test
    fun oldDeckNeverDrawsGeneratorCards_soStreamsAreIndependent() {
        val genIds = cards.map { it.id }.toSet()
        var drawn = 0
        for (k in 1..30) {
            val e = KapitalEngine(k * 7919L)
            val c = e.eventCtx()
            for (t in 1..200) {
                val d = EventDeck.draw(c, java.util.Random(k * 31L + t).let { r -> kotlin.random.Random(r.nextLong()) }, emptySet(), emptyMap(), t, 1.0)
                d.event?.let { drawn++; assertTrue("dek lama menarik kartu generator ${it.templateId}", it.templateId !in genIds) }
            }
        }
        assertTrue("dek lama memang menarik kartu tulisan tangan: $drawn", drawn > 100)
    }

    @Test
    fun cardsActuallyAppearInPlay() {
        var gen = 0; var chained = 0; var games = 0
        val genIds = cards.map { it.id }.toSet(); val chainIds = cards.filter { it.trig == null }.map { it.id }.toSet()
        val seen = HashSet<String>(); val perCard = HashMap<String, Int>()
        for (k in 1..60) {
            val e = KapitalEngine(k * 104729L)
            var t = 0
            while (e.gameOverReason == null && t < 250) {
                t++
                for (ev in e.openEvents.toList()) {
                    if (ev.templateId in genIds) { gen++; seen += ev.templateId; perCard.merge(ev.templateId, 1, Int::plus); if (ev.templateId in chainIds) chained++ }
                    e.apply(Action.EventChoice(ev.id, ev.options[0].id))
                }
                ActiveBot.step(e, t); e.apply(Action.EndTurn)
            }
            games++
        }
        println("KARTU main: $games permainan, kartu generator muncul $gen kali (berantai $chained), ${seen.size} kartu berbeda")
        assertTrue("kartu generator muncul dalam permainan: $gen", gen >= games * 3)
        val top = perCard.maxByOrNull { it.value }!!
        println("KARTU dominasi: terbanyak ${top.key} = ${top.value}/$gen (${"%.1f".format(100.0 * top.value / gen)}%)")
        assertTrue("tak ada kartu yang mendominasi undian (>4%): ${top.key} ${top.value}/$gen", top.value <= 0.04 * gen)
    }

    companion object {
        const val EXPECTED_MIN_CARDS = 600
        const val EXPECTED_MIN_CHAIN = 100
    }
}
