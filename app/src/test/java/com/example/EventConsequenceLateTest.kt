package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/**
 * Akibat pilihan di akhir permainan. EventConsequenceTest menjaga Era 1 (perusahaan tertutup, belum punya anak usaha); di sini
 * keadaan Era 3 (giliran 250) dan Era 4 (giliran 380) dari satu rekaman bot aktif yang sudah menjadi emiten dan punya anak usaha.
 * Kartu dibangun dengan konteks keadaan itu (jumlah uang mengikuti skala perusahaan), lalu tiap hasil disuntikkan dan dibandingkan
 * dengan kontrol tanpa kejadian. Ringkasan keadaan yang dibandingkan juga memuat keadaan tersembunyi (efek aktif, rantai, eksekutif,
 * aktor), jadi jenis efek bersyarat ikut teruji, bukan dikecualikan seperti di uji Era 1.
 */
class EventConsequenceLateTest {
    private val bases get() = EventProbe.lateBases

    private fun deckAt(e: KapitalEngine): List<GameEvent> {
        val c = e.eventCtx()
        return EventDeck.templates.mapNotNull { it.build(c, Random(7), "p_${it.id}", c.turn) }
    }

    /** Kenaikan upah yang ditangguhkan ("delay6") baru berlaku enam bulan kemudian, jadi diukur pada cakrawala lebih panjang. */
    private fun horizonFor(fx: List<Effect>) = if (fx.any { it.kind == "wage" && it.s == "delay6" }) 8 else 3

    /**
     * Syarat keadaan agar sebuah efek HARUS terlihat. Di luar syarat itu efek memang tidak mengubah apa pun (mis. saham pada perusahaan
     * tertutup, ESG yang sudah patuh): dilewati dan dicatat, bukan dianggap rusak. Syarat-syarat ini dibuktikan terpisah di
     * [conditionalEffects_actWhenTheirConditionHolds].
     */
    private fun mustBeLive(f: Effect, e: KapitalEngine): Boolean = when (f.kind) {
        "sentiment" -> e.isPublicCompany
        "esg_clean" -> !e.esgCompliant
        "dual" -> !e.dualSourcing
        "fix_rates" -> e.loans.any { it.floatingSpreadPct != null && it.balance > 0.5 }
        "deferred" -> f.a > 0 || e.deferredMonths > 0
        "ethics_reduce" -> e.ethicsLoad > 0.0 || e.ethicsEvidences.isNotEmpty()
        "exec_quit" -> e.execs.any { it.id == f.s && it.present }
        "loyalty" -> e.execs.any { it.id == f.s && (it.loyalty + f.a).coerceIn(0.0, 1.0) != it.loyalty }
        "board_trust" -> (e.boardTrust + f.a.toInt()).coerceIn(0, 100) != e.boardTrust
        "lostcust" -> minOf(0.6, e.lostCustomerShare + f.a) != e.lostCustomerShare
        "chain" -> e.scheduledChainEvents.none { it.templateId == f.s.substringBefore(":") }
        "start_dumping" -> e.turn + f.a.toInt() > e.dumpingUntil
        "end_dumping" -> e.turn <= e.dumpingUntil
        "unskilled" -> false // efeknya menaikkan peluang kerusakan mesin; terlihat di laporan bulanan (uji terpisah)
        else -> true
    }

    @Test
    fun lateBases_areReallyLate_publicWithSubsidiaries() {
        for (b in bases) {
            val e = b.engine()
            assertEquals("${b.label}: era", b.era, e.currentEra)
            assertTrue("${b.label}: perusahaan sudah emiten", e.isPublicCompany)
            assertTrue("${b.label}: punya anak usaha", e.subsidiaries.isNotEmpty())
            assertTrue("${b.label}: permainan belum berakhir", e.gameOverReason == null)
        }
    }

    @Test
    fun emptyOutcome_changesNothing_soTheSweepsMeanSomething() {
        for (b in bases) {
            val ctl = EventProbe.snapshot(EventProbe.afterMonths(b.engine(), 3))
            val ex = b.engine()
            assertTrue("${b.label}: pilihan diterima", EventProbe.inject(ex, "probe_kosong", "a", Tone.INFO, emptyList()))
            val d = EventProbe.changed(EventProbe.snapshot(EventProbe.afterMonths(ex, 3)), ctl)
            assertTrue("${b.label}: memilih hasil tanpa efek tidak boleh mengubah apa pun, tetapi mengubah $d", d.isEmpty())
        }
    }

    @Test
    fun everyEffectKind_isLiveInTheLateGame() {
        val problems = ArrayList<String>()
        var tested = 0
        var skipped = 0
        for ((bi, b) in bases.withIndex()) {
            val e0 = b.engine()
            val control = HashMap<Int, Map<String, Double>>()
            fun ctl(h: Int) = control.getOrPut(h) { EventProbe.snapshot(EventProbe.afterMonths(b.engine(), h)) }
            // satu kelompok per (jenis, kunci s); "chain" menyimpan nama kartu di s sehingga dikelompokkan per jenis saja
            val groups = LinkedHashMap<String, LinkedHashMap<String, Effect>>()
            for (ev in deckAt(e0)) for (o in ev.options) for (out in o.outcomes) for (f in out.effects) {
                val g = if (f.kind == "chain") "chain" else "${f.kind}/${f.s}"
                groups.getOrPut(g) { LinkedHashMap() }.putIfAbsent("${f.a}|${f.b}|${f.s}", f)
            }
            for ((g, inst) in groups) {
                val all = inst.values.toList()
                // Tiap putar ulang dasar memakan ±0,3 detik, jadi contoh dibatasi: Era 3 satu contoh tengah, Era 4 contoh pertama dan terakhir.
                // Sapuan penuh (semua parameter, semua era) dijalankan sekali dan hasilnya dicatat di docs/RELEASE_NOTES.md.
                val pick = if (bi == 0) listOf(all[all.size / 2]) else if (all.size <= 2) all else listOf(all.first(), all.last())
                for (f in pick) {
                    if (!mustBeLive(f, e0)) { skipped++; continue }
                    val e = b.engine()
                    if (!EventProbe.inject(e, "probe_${f.kind}", "a", Tone.INFO, listOf(f))) { problems += "${b.label} $g (${f.a}, ${f.b}, '${f.s}'): pilihan ditolak"; continue }
                    val h = horizonFor(listOf(f))
                    EventProbe.afterMonths(e, h)
                    tested++
                    val d = EventProbe.changed(EventProbe.snapshot(e), ctl(h))
                    if (d.isEmpty()) problems += "${b.label} $g (${f.a}, ${f.b}, '${f.s}'): tidak mengubah apa pun"
                }
            }
        }
        println("JENIS EFEK DI AKHIR PERMAINAN: diuji $tested contoh; dilewati karena syarat keadaan tak terpenuhi $skipped")
        assertTrue("uji tidak hampa ($tested)", tested > 120)
        assertTrue("efek yang seharusnya hidup tetapi tidak mengubah simulasi: $problems", problems.isEmpty())
    }

    @Test
    fun sampledCardOutcomes_changeTheLateSimulation() {
        val handIds = EventDeck.baseTemplates.map { it.id }.toSet()
        var tested = 0
        var skipped = 0
        val dead = ArrayList<String>()
        val rejected = ArrayList<String>()
        // Era 3: setiap kartu tulisan tangan genap; Era 4: setiap kartu ketiga puluh dua dari generator (sudut pandang berbeda dari uji Era 1)
        var handSeen = 0
        val plan = listOf(
            bases[0] to { _: Int, ev: GameEvent -> ev.templateId in handIds && (handSeen++ % 2 == 0) },
            bases[1] to { i: Int, ev: GameEvent -> ev.templateId !in handIds && i % 32 == 5 }
        )
        for ((b, take) in plan) {
            val e0 = b.engine()
            val control = HashMap<Int, Map<String, Double>>()
            fun ctl(h: Int) = control.getOrPut(h) { EventProbe.snapshot(EventProbe.afterMonths(b.engine(), h)) }
            val picked = deckAt(e0).filterIndexed { i, ev -> take(i, ev) }
            for (ev in picked) for (opt in ev.options) {
                if (opt.id == "z") continue
                for ((oi, out) in opt.outcomes.withIndex()) {
                    if (out.effects.isEmpty()) continue
                    if (out.effects.none { mustBeLive(it, e0) }) { skipped++; continue }
                    val e = b.engine()
                    if (!EventProbe.inject(e, ev.templateId, opt.id, opt.tone, out.effects)) { rejected += "${b.label} ${ev.templateId}/${opt.id}/${oi + 1}"; continue }
                    val h = horizonFor(out.effects)
                    EventProbe.afterMonths(e, h)
                    tested++
                    if (EventProbe.changed(EventProbe.snapshot(e), ctl(h)).isEmpty()) dead += "${b.label} ${ev.templateId}/${opt.id}/${oi + 1}: ${out.effects.map { it.kind }}"
                }
            }
        }
        println("HASIL KARTU DI AKHIR PERMAINAN: diuji $tested; dilewati (syarat keadaan) $skipped; ditolak ${rejected.size}; tanpa perubahan ${dead.size}")
        assertTrue("uji tidak hampa ($tested)", tested > 60)
        assertTrue("pilihan kartu ditolak mesin: $rejected", rejected.isEmpty())
        assertTrue("hasil berefek yang tidak mengubah apa pun di akhir permainan: $dead", dead.isEmpty())
    }

    @Test
    fun conditionalEffects_actWhenTheirConditionHolds() {
        // Keadaan awal permainan (belum ada tindakan): belum ada pemasok ganda, ESG belum patuh, ada pinjaman mengambang.
        run {
            val e = KapitalEngine(7919L)
            assertFalse("awal: belum ada pemasok ganda", e.dualSourcing)
            assertTrue(EventProbe.inject(e, "probe_dual", "a", Tone.GOOD, listOf(Effect("dual", 1.0))))
            assertTrue("efek dual mengaktifkan pemasok ganda", e.dualSourcing)
        }
        run {
            val e = KapitalEngine(7919L)
            assertFalse("awal: ESG belum patuh", e.esgCompliant)
            assertTrue(EventProbe.inject(e, "probe_esg", "a", Tone.GOOD, listOf(Effect("esg_clean", 0.0))))
            assertTrue("efek esg_clean membuat perusahaan patuh ESG", e.esgCompliant)
        }
        run {
            val e = KapitalEngine(7919L)
            assertTrue("awal: ada pinjaman berbunga mengambang", e.loans.any { it.floatingSpreadPct != null && it.balance > 0.5 })
            assertTrue(EventProbe.inject(e, "probe_fix", "a", Tone.GOOD, listOf(Effect("fix_rates", 0.0))))
            assertTrue("efek fix_rates menetapkan semua pinjaman jadi bunga tetap", e.loans.none { it.floatingSpreadPct != null && it.balance > 0.5 })
        }
        run {
            val e = KapitalEngine(7919L)
            val before = e.deferredMonths
            assertTrue(EventProbe.inject(e, "probe_def", "a", Tone.BAD, listOf(Effect("deferred", 3.0))))
            assertEquals("deferred +3 menambah perawatan tertunda", before + 3, e.deferredMonths)
            assertTrue(EventProbe.inject(e, "probe_def2", "a", Tone.GOOD, listOf(Effect("deferred", -3.0))))
            assertEquals("deferred -3 melunasinya", before, e.deferredMonths)
        }
        // Keadaan akhir permainan (emiten): saham, bukti etika, eksekutif, rantai, aktor, dumping.
        val b = bases.last()
        val deck = deckAt(b.engine())
        fun first(kind: String): Effect = deck.flatMap { it.options }.flatMap { it.outcomes }.flatMap { it.effects }.first { it.kind == kind }
        run {
            val e = b.engine()
            val f = first("sentiment")
            val before = e.stockSentiment
            assertTrue(EventProbe.inject(e, "probe_sent", "a", Tone.INFO, listOf(f)))
            assertEquals("sentimen saham bergeser sebesar efek", before + f.a, e.stockSentiment, 1e-9)
        }
        run {
            val e = b.engine()
            val f = first("ethics_reduce")
            val load = e.ethicsLoad; val n = e.ethicsEvidences.size
            assertTrue("syarat uji: ada beban etika dan bukti", load > 0.0 && n > 0)
            assertTrue(EventProbe.inject(e, "probe_eth", "a", Tone.GOOD, listOf(f)))
            assertEquals("beban etika turun sebesar efek (tidak di bawah 0)", Math.max(0.0, load - f.a), e.ethicsLoad, 1e-9)
            assertEquals("satu bukti dihapus", n - 1, e.ethicsEvidences.size)
        }
        run {
            val e = b.engine()
            val f = first("exec_quit")
            assertTrue("syarat uji: eksekutif ${f.s} hadir", e.execs.first { it.id == f.s }.present)
            assertTrue(EventProbe.inject(e, "probe_quit", "a", Tone.BAD, listOf(f)))
            assertFalse("eksekutif ${f.s} pergi", e.execs.first { it.id == f.s }.present)
        }
        run {
            val e = b.engine()
            val f = first("loyalty")
            val x = e.execs.first { it.id == f.s }
            val before = x.loyalty
            assertTrue(EventProbe.inject(e, "probe_loy", "a", Tone.INFO, listOf(f)))
            assertEquals("loyalitas ${f.s} bergeser sebesar efek (dibatasi 0..1)", (before + f.a).coerceIn(0.0, 1.0), e.execs.first { it.id == f.s }.loyalty, 1e-9)
        }
        run {
            val e = b.engine()
            val f = first("chain")
            val tpl = f.s.substringBefore(":")
            val n = e.scheduledChainEvents.size
            assertTrue("syarat uji: kartu berantai $tpl belum terjadwal", e.scheduledChainEvents.none { it.templateId == tpl })
            assertTrue(EventProbe.inject(e, "probe_chain", "a", Tone.INFO, listOf(f)))
            assertEquals("satu kartu berantai terjadwal", n + 1, e.scheduledChainEvents.size)
            val s = e.scheduledChainEvents.last()
            assertEquals("kartu berantai yang benar", tpl, s.templateId)
            assertEquals("jatuh tempo = sekarang + tunda", e.turn + Math.max(1, f.b), s.dueTurn)
        }
        run {
            val e = b.engine()
            val paid = e.actors.paid
            assertTrue(EventProbe.inject(e, "probe_actor", "a", Tone.BAD, listOf(Effect("actor", 0.0, 0, "pol_pay"))))
            assertEquals("membayar politisi tercatat", paid + 1, e.actors.paid)
            val refused = e.actors.refused
            assertTrue(EventProbe.inject(e, "probe_actor2", "a", Tone.BAD, listOf(Effect("actor", 500.0, 0, "pol_refuse"))))
            assertEquals("menolak politisi tercatat", refused + 1, e.actors.refused)
            assertEquals("denda penolakan tercatat", 500.0, e.actors.finesFromRefusals, 1e-9)
        }
        run {
            val e = b.engine()
            assertTrue(EventProbe.inject(e, "probe_dump", "a", Tone.BAD, listOf(Effect("start_dumping", 6.0))))
            assertEquals("dumping berlaku enam bulan", e.turn + 6, e.dumpingUntil)
            assertTrue(EventProbe.inject(e, "probe_dump2", "a", Tone.GOOD, listOf(Effect("end_dumping", 0.0))))
            assertEquals("dumping diakhiri", -1, e.dumpingUntil)
        }
        run {
            // tenaga kontrak kurang terampil: efeknya menaikkan peluang kerusakan mesin; yang pasti terlihat adalah baris laporan bulanan
            val e = b.engine()
            assertTrue(EventProbe.inject(e, "probe_unsk", "a", Tone.BAD, listOf(Effect("unskilled", 0.0, 4))))
            e.apply(Action.EndTurn)
            assertTrue("laporan bulanan menyebut tenaga kontrak: ${e.lastReport.map { it.text.take(40) }}",
                e.lastReport.any { it.text.startsWith("Tenaga kontrak kurang terampil") })
        }
    }

    @Test
    fun moneyEffects_landOnTheStatementsExactly() {
        val b = bases.last()
        val x = 200.0
        fun pair(effects: List<Effect>, months: Int = 1): Pair<KapitalEngine, KapitalEngine> {
            val ctl = EventProbe.afterMonths(b.engine(), months)
            val ex = b.engine()
            assertTrue("pilihan diterima: $effects", EventProbe.inject(ex, "probe_uang", "a", Tone.BAD, effects))
            return ctl to EventProbe.afterMonths(ex, months)
        }
        fun inc(p: Pair<KapitalEngine, KapitalEngine>, key: String) = (p.second.lastIncome[key] ?: 0.0) - (p.first.lastIncome[key] ?: 0.0)
        fun det(p: Pair<KapitalEngine, KapitalEngine>, key: String) = (p.second.lastDetail[key] ?: 0.0) - (p.first.lastDetail[key] ?: 0.0)
        for (kind in listOf("cost", "fine")) {
            val p = pair(listOf(Effect(kind, x)))
            assertEquals("$kind: beban sekali jalan naik tepat $x", x, inc(p, "op_oneoff"), 1e-6)
            assertEquals("$kind: beban operasi naik tepat $x", x, inc(p, "opex"), 1e-6)
        }
        run {
            val p = pair(listOf(Effect("income", x)))
            assertEquals("income: pos pendapatan lain tepat $x", x, inc(p, "op_gain"), 1e-6)
            assertEquals("income: beban operasi turun tepat $x", -x, inc(p, "opex"), 1e-6)
        }
        run {
            val e0 = b.engine()
            val p = pair(listOf(Effect("invloss", 0.1)))
            assertEquals("invloss: rugi persediaan = 10% persediaan", 0.1 * e0.inventory, inc(p, "op_loss"), 1e-6)
        }
        run {
            val e0 = b.engine()
            val p = pair(listOf(Effect("writeoff", x)))
            assertEquals("writeoff: piutang dihapusbukukan sebesar efek (maks. 90% piutang)", Math.min(x, e0.normalAR * 0.9), inc(p, "baddebt"), 1e-6)
        }
        run {
            val p = pair(listOf(Effect("prepay", x)))
            assertEquals("prepay: persediaan naik tepat $x", x, p.second.inventory - p.first.inventory, 1e-6)
        }
        run {
            val p = pair(listOf(Effect("capex", x)))
            assertEquals("capex: arus kas investasi keluar tepat $x", -x, det(p, "d_capex_exp"), 1e-6)
        }
        run {
            val e0 = b.engine()
            assertTrue("syarat uji: kas cukup untuk dividen penuh (${e0.cash})", e0.cash > 20 * x + 5000.0)
            val p = pair(listOf(Effect("dividend", x)))
            assertEquals("dividend: kas keluar tepat $x", -x, det(p, "d_divpaid"), 1e-6)
        }
        run {
            val e = b.engine()
            val before = e.boardTrust
            assertTrue(EventProbe.inject(e, "probe_bt", "a", Tone.BAD, listOf(Effect("board_trust", -10.0))))
            assertEquals("board_trust: kepercayaan dewan turun 10", before - 10, e.boardTrust)
        }
        run {
            val s0 = b.engine().lostCustomerShare
            val p = pair(listOf(Effect("lostcust", 0.05)))
            assertEquals("lostcust: pangsa pelanggan hilang naik 0,05", Math.min(0.6, s0 + 0.05), p.second.lostCustomerShare, 1e-9)
            val ratio = (p.second.lastIncome["revenue"] ?: 0.0) / (p.first.lastIncome["revenue"] ?: 1.0)
            val expected = (1.0 - (s0 + 0.05)) / (1.0 - s0)
            assertTrue("lostcust: pendapatan bulan berikutnya turun menurut pangsa hilang ($ratio vs $expected)", Math.abs(ratio - expected) < 0.01)
        }
        run {
            val ctl = b.engine(); val ex = b.engine()
            assertTrue(EventProbe.inject(ex, "probe_mod", "a", Tone.BAD, listOf(Effect("mod", 0.9, 3, "rev"))))
            val ratios = ArrayList<Double>()
            for (m in 1..4) {
                ctl.apply(Action.EndTurn); ex.apply(Action.EndTurn)
                ratios += (ex.lastIncome["revenue"] ?: 0.0) / (ctl.lastIncome["revenue"] ?: 1.0)
            }
            for (m in 0..2) assertTrue("mod rev 0,9 bulan ${m + 1}: ${ratios[m]}", Math.abs(ratios[m] - 0.9) < 0.01)
            assertTrue("mod rev berakhir sesudah 3 bulan: ${ratios[3]}", Math.abs(ratios[3] - 1.0) < 0.02)
        }
        run {
            val ctl = b.engine(); val ex = b.engine()
            val raise = ex.eventCtx().wu * 5
            assertTrue(EventProbe.inject(ex, "probe_wage", "a", Tone.GOOD, listOf(Effect("wage", raise))))
            ctl.apply(Action.EndTurn); ex.apply(Action.EndTurn)
            assertEquals("wage: beban operasi naik tepat sebesar kenaikan upah", raise, (ex.lastIncome["opex"] ?: 0.0) - (ctl.lastIncome["opex"] ?: 0.0), raise * 0.01)
        }
        run {
            val ctl = b.engine(); val ex = b.engine()
            assertTrue(EventProbe.inject(ex, "probe_strike", "a", Tone.BAD, listOf(Effect("strike", 0.0, 2))))
            assertEquals("mogok tercatat 2 bulan", 2, ex.strikeMonthsLeft)
            val ratios = ArrayList<Double>()
            for (m in 1..3) {
                ctl.apply(Action.EndTurn); ex.apply(Action.EndTurn)
                ratios += (ex.lastIncome["revenue"] ?: 0.0) / (ctl.lastIncome["revenue"] ?: 1.0)
            }
            for (m in 0..1) assertTrue("mogok bulan ${m + 1} di akhir permainan: pendapatan induk ${ratios[m]} ~ 0,6", ratios[m] in 0.45..0.70)
            assertTrue("mogok berakhir: ${ratios[2]}", ratios[2] > 0.9)
        }
    }
}
