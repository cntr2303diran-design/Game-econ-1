package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/**
 * Setiap pilihan harus punya akibat yang benar-benar terbaca di simulasi (bukan hanya teks). Uji ini menjalankan mesin:
 * kejadian disuntikkan ke permainan yang sama, lalu keadaan setelah beberapa bulan dibandingkan dengan kontrol tanpa kejadian.
 */
class EventConsequenceTest {

    private class Base(val seed: Long, val log: List<String>)

    private fun base(): Base {
        for (k in 0 until 40) {
            val seed = 7919L * (k + 1)
            val months = 30
            val save = TestBots.recordedSave(seed, KapitalEngine(seed).turn + months) ?: continue
            return Base(seed, save.lines().drop(1))
        }
        throw AssertionError("tidak ada seed yang bertahan 30 bulan")
    }

    private fun engineAt(b: Base): KapitalEngine {
        val e = KapitalEngine(b.seed)
        for (l in b.log) Action.decode(l)?.let { e.apply(it) }
        return e
    }

    /** Kejadian satu opsi dengan satu hasil pasti (peluang 1), supaya setiap hasil bisa diuji sendiri. */
    private fun inject(e: KapitalEngine, tpl: String, optId: String, tone: Tone, ap: Int, effects: List<Effect>): Boolean {
        val ev = GameEvent("probe", tpl, "X", "uji", "uji", "", listOf(EventOption(optId, "o", ap, "", "", tone, listOf(Outcome(1.0, "t", effects)))), e.turn, e.turn + 1, optId)
        e.openEvents += ev
        return e.apply(Action.EventChoice("probe", optId))
    }

    private val BOOKKEEPING = setOf("CrisesSurvived", "ActionCount", "Ap")

    /** Semua properti numerik/boolean publik mesin + baris laba rugi bulan terakhir. */
    private fun snapshot(e: KapitalEngine): Map<String, Double> {
        val out = LinkedHashMap<String, Double>()
        for (m in e.javaClass.methods) {
            if (m.parameterCount != 0 || java.lang.reflect.Modifier.isStatic(m.modifiers)) continue
            val n = m.name
            val key = when { n.startsWith("get") && n.length > 3 -> n.substring(3); n.startsWith("is") && n.length > 2 -> n.substring(2); else -> continue }
            if (key in BOOKKEEPING || key == "Class") continue
            val t = m.returnType
            if (!(t == java.lang.Double.TYPE || t == Integer.TYPE || t == java.lang.Long.TYPE || t == java.lang.Boolean.TYPE)) continue
            val v = try { m.invoke(e) } catch (_: Throwable) { continue }
            out[key] = when (v) { is Boolean -> if (v) 1.0 else 0.0; is Number -> v.toDouble(); else -> continue }
        }
        for ((k, v) in e.lastIncome) out["inc_$k"] = v
        return out
    }

    private fun differs(a: Map<String, Double>, b: Map<String, Double>): Boolean =
        a.keys.any { k -> val x = a[k]!!; val y = b[k] ?: x; Math.abs(x - y) > 1e-9 * (1.0 + Math.abs(x)) }

    private fun ctx(b: Base): EventCtx = engineAt(b).eventCtx()

    private fun allEvents(c: EventCtx): List<GameEvent> = EventDeck.templates.mapNotNull { it.build(c, Random(7), "p_${it.id}", c.turn) }

    // -----------------------------------------------------------------------------------------------------------------

    @Test
    fun strike_cutsProductionTwoMonths_leavesAftermath_thenRecovers() {
        val b = base()
        val ctl = engineAt(b); val ex = engineAt(b)
        assertTrue("pilihan diterima", inject(ex, "union", "a", Tone.BAD, 0, listOf(Effect("strike", 0.0, 2))))
        assertEquals("mogok tercatat 2 bulan", 2, ex.strikeMonthsLeft)
        val ratio = ArrayList<Double>(); val reports = ArrayList<Boolean>()
        for (m in 1..8) {
            ctl.apply(Action.EndTurn); ex.apply(Action.EndTurn)
            ratio += (ex.lastIncome["revenue"] ?: 0.0) / (ctl.lastIncome["revenue"] ?: 1.0)
            reports += ex.lastReport.any { it.text.startsWith("Mogok pekerja berlangsung") }
        }
        println("RASIO PENDAPATAN MOGOK per bulan: ${ratio.map { "%.3f".format(it) }}; laporan menyebut mogok: $reports")
        for (m in 0..1) assertTrue("bulan ${m + 1} mogok: pendapatan ${ratio[m]} ~ 0,6 x 0,97", ratio[m] in 0.50..0.66)
        for (m in 2..5) assertTrue("bulan ${m + 1} pasca-mogok: pendapatan ${ratio[m]} ~ 0,97", ratio[m] in 0.94..0.995)
        for (m in 6..7) assertTrue("bulan ${m + 1} pulih: pendapatan ${ratio[m]} ~ 1,0", ratio[m] in 0.97..1.03)
        assertEquals("mogok selesai", 0, ex.strikeMonthsLeft)
        assertTrue("laporan bulanan menyebut mogok selama berlangsung", reports[0] && reports[1])
        assertTrue("laporan tidak menyebut mogok sesudah usai", !reports[2] && !reports[3])
    }

    @Test
    fun wageEffect_raisesPermanentWageCost_exactly() {
        val b = base()
        val ctl = engineAt(b); val ex = engineAt(b)
        val c = ex.eventCtx()
        val raise = c.wu * 5
        assertTrue(inject(ex, "union", "a", Tone.GOOD, 0, listOf(Effect("wage", raise))))
        for (m in 1..6) {
            ctl.apply(Action.EndTurn); ex.apply(Action.EndTurn)
            val dOpex = (ex.lastIncome["opex"] ?: 0.0) - (ctl.lastIncome["opex"] ?: 0.0)
            assertEquals("bulan $m: beban operasi naik tepat sebesar kenaikan upah", raise, dOpex, raise * 0.01)
        }
    }

    @Test
    fun unionCard_everyOptionHasItsPromisedConsequence() {
        val b = base()
        val ev = EventDeck.templates.first { it.id == "union" }.build(ctx(b), Random(5), "u", ctx(b).turn)!!
        fun eff(optId: String) = ev.options.first { it.id == optId }.outcomes.flatMap { o -> o.effects.map { it.kind to o.p } }
        assertTrue("setuju: ada kenaikan upah", eff("a").any { it.first == "wage" })
        assertTrue("setuju: tidak ada risiko mogok", eff("a").none { it.first == "strike" })
        assertTrue("bagi hasil: ada kemungkinan mogok", eff("b").any { it.first == "strike" })
        assertTrue("tolak: ada kemungkinan mogok", eff("c").any { it.first == "strike" })
        assertTrue("tolak: tenaga kontrak kurang terampil", eff("c").any { it.first == "unskilled" })
        assertEquals("dibiarkan sampai tenggat = pilihan bawaan", "z", ev.defaultOptionId)
        assertTrue("dibiarkan: pasti mogok", ev.options.first { it.id == "z" }.outcomes.all { o -> o.effects.any { it.kind == "strike" } })
        val pStrikeC = ev.options.first { it.id == "c" }.outcomes.filter { o -> o.effects.any { it.kind == "strike" } }.sumOf { it.p }
        val pStrikeB = ev.options.first { it.id == "b" }.outcomes.filter { o -> o.effects.any { it.kind == "strike" } }.sumOf { it.p }
        assertTrue("menolak lebih berisiko mogok daripada bagi hasil ($pStrikeC vs $pStrikeB)", pStrikeC > pStrikeB)
    }

    @Test
    fun everyFreeOption_saysSoOnItsFace_andTheyStayRare() {
        val b = base()
        val evs = allEvents(ctx(b))
        var options = 0; var free = 0; val silent = ArrayList<String>()
        for (ev in evs) for (o in ev.options) {
            options++
            if (o.outcomes.all { it.effects.isEmpty() }) {
                free++
                val t = o.impact.lowercase()
                if (!(t.contains("tanpa") || t.contains("tidak ada"))) silent += "${ev.templateId}/${o.id}: '${o.impact}'"
            }
        }
        println("OPSI TANPA EFEK: $free dari $options (${"%.1f".format(100.0 * free / options)}%)")
        assertTrue("opsi tanpa efek yang tidak menyatakannya di teks dampak: $silent", silent.isEmpty())
        assertTrue("opsi gratis tidak boleh membengkak ($free dari $options)", free <= 0.12 * options)
    }

    /** Kejadian yang efeknya hanya menyentuh keadaan tersembunyi, khusus emiten, atau yang bergantung kondisi: tidak diuji lewat selisih keadaan. */
    private val CONDITIONAL_KINDS = setOf("sentiment", "unskilled", "loyalty", "actor", "chain", "dual", "fix_rates", "esg_clean", "ethics_reduce", "exec_quit", "buyback", "credit_shock")

    @Test
    fun everyOutcomeWithEffects_actuallyChangesTheSimulation() {
        val b = base()
        val events = allEvents(ctx(b))
        // seluruh kartu tulisan tangan + setiap kedelapan kartu generator (cakupan luas dengan waktu terbatas)
        val handIds = EventDeck.baseTemplates.map { it.id }.toSet()
        val picked = events.filterIndexed { i, ev -> ev.templateId in handIds || i % 8 == 0 }
        // kenaikan upah yang ditangguhkan ("delay6") baru berlaku enam bulan kemudian, jadi diukur pada cakrawala lebih panjang
        fun horizonFor(fx: List<Effect>) = if (fx.any { it.kind == "wage" && it.s == "delay6" }) 8 else 3
        val ctlByHorizon = HashMap<Int, Map<String, Double>>()
        fun control(h: Int) = ctlByHorizon.getOrPut(h) { snapshot(engineAt(b).also { e -> repeat(h) { e.apply(Action.EndTurn) } }) }
        var tested = 0; val dead = ArrayList<String>()
        for (ev in picked) for (opt in ev.options) {
            if (opt.id == "z") continue
            for ((oi, out) in opt.outcomes.withIndex()) {
                if (out.effects.isEmpty() || out.effects.all { it.kind in CONDITIONAL_KINDS }) continue
                val e = engineAt(b)
                if (!inject(e, ev.templateId, opt.id, opt.tone, 0, out.effects)) { dead += "${ev.templateId}/${opt.id}/${oi + 1}: pilihan ditolak"; continue }
                val horizon = horizonFor(out.effects)
                repeat(horizon) { e.apply(Action.EndTurn) }
                tested++
                if (!differs(snapshot(e), control(horizon))) dead += "${ev.templateId}/${opt.id}/${oi + 1}: ${out.effects.map { it.kind }}"
            }
        }
        println("HASIL BERUBAH vs KONTROL: diuji $tested hasil dari ${picked.size} kartu; tanpa perubahan: ${dead.size}")
        assertTrue("uji tidak hampa ($tested)", tested > 300)
        assertTrue("hasil berefek yang tidak mengubah apa pun di simulasi: $dead", dead.isEmpty())
    }

    @Test
    fun activeEventEffects_areListedInTheMonthlyReport() {
        val b = base()
        val e = engineAt(b)
        e.mods.clear() // hanya efek yang disuntikkan di sini yang diperiksa (permainan dasar bisa membawa efek lain, mis. kampanye merek)
        assertTrue(inject(e, "uji", "a", Tone.INFO, 0, listOf(Effect("mod", 0.95, 4, "rev"), Effect("mod", 1.04, 3, "cogs"))))
        e.apply(Action.EndTurn)
        val line = e.lastReport.firstOrNull { it.text.startsWith("Efek kejadian yang masih berjalan") }
        assertTrue("laporan memuat efek aktif: ${e.lastReport.map { it.text.take(40) }}", line != null)
        assertTrue("menyebut pendapatan −5,0%: ${line!!.text}", line.text.contains("pendapatan −5,0%"))
        assertTrue("menyebut HPP +4,0%: ${line.text}", line.text.contains("HPP +4,0%"))
    }
}
