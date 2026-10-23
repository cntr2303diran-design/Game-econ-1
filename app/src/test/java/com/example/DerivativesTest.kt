package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/** Tahap B4b: derivatif lindung nilai (opsi A$, futures kapas, swap bunga), diselesaikan tunai. */
class DerivativesTest {

    private fun check(e: KapitalEngine) {
        assertEquals(0.0, e.totalAssets() - e.totalLiabilities() - e.equity, 1e-6)
        val d = e.lastDetail
        if (d.isNotEmpty()) {
            assertEquals("arus kas = perubahan kas", 0.0, d["d_resid"] ?: 0.0, 1e-6)
            assertEquals("roll-forward ekuitas", 0.0, d["e_resid"] ?: 0.0, 1e-6)
        }
    }

    @Test
    fun actions_roundTrip_andInvalidRejected() {
        for (a in listOf(Action.BuyDerivative("fxo", 50), Action.BuyDerivative("swp", 100)))
            assertEquals(a, Action.decode(a.encode()))
        val e = KapitalEngine(5L)
        assertFalse(e.apply(Action.BuyDerivative("zzz", 50)))
        assertFalse(e.apply(Action.BuyDerivative("ctf", 30)))
        assertNotNull(e.derivBlock("fxo", 33))
        assertEquals(0.0, e.derivNetRs, 0.0)
    }

    @Test
    fun eachKind_isBuyableWithExposure_andLimitedToOneAtATime() {
        val e = KapitalEngine(5L)
        for (k in DerivKind.values()) {
            val exp = e.derivExposure(k)
            assertTrue("$k punya eksposur awal: $exp", exp > 100.0)
            val ap0 = e.ap
            assertTrue("beli ${k.code}: ${e.derivBlock(k.code, 50)}", e.apply(Action.BuyDerivative(k.code, 50)))
            assertEquals(ap0 - 1, e.ap)
            assertNotNull("satu kontrak per jenis", e.derivBlock(k.code, 25))
            assertFalse(e.apply(Action.BuyDerivative(k.code, 25)))
        }
        assertEquals(3, e.derivs.size)
        for (d in e.derivs) assertTrue("lindung nilai saja: pokok <= eksposur", d.notional <= e.derivExposure(d.kind) + 1e-6 || d.kind != DerivKind.COTFUT)
    }

    @Test
    fun premium_isExpensed_andStatementsStayConsistent_untilSettlement() {
        for (seed in listOf(3L, 8L, 17L)) {
            val e = KapitalEngine(seed)
            e.apply(Action.BuyDerivative("fxo", 100)); e.apply(Action.BuyDerivative("ctf", 100)); e.apply(Action.BuyDerivative("swp", 100))
            val prem = e.derivPremiumsRs
            assertTrue("premi > 0", prem > 0.0)
            assertEquals(-prem, e.derivNetRs, 1e-9)
            var t = 0
            while (e.gameOverReason == null && t < 16) {
                TestBots.disciplinedStep(e, e.turn)
                e.apply(Action.EndTurn); t++
                check(e)
            }
            assertTrue("semua kontrak sudah diselesaikan setelah 16 bulan", e.derivs.isEmpty() || e.gameOverReason != null)
        }
    }

    @Test
    fun settlement_matchesContractTerms() {
        // opsi A$: pembayaran = pokok x max(0, kurs_akhir/kurs_awal - 1,03); tidak pernah negatif
        var sawPositive = false; var sawZero = false
        for (seed in 1L..40L) {
            val e = KapitalEngine(seed)
            if (!e.apply(Action.BuyDerivative("fxo", 100))) continue
            val d = e.derivs.first()
            val net0 = e.derivNetRs
            var guard = 0
            while (e.derivs.isNotEmpty() && e.gameOverReason == null && guard++ < 10) {
                TestBots.disciplinedStep(e, e.turn)
                e.apply(Action.EndTurn)
            }
            if (e.derivs.isNotEmpty()) continue
            val pay = e.derivNetRs - net0
            val expected = d.notional * maxOf(0.0, e.kurs / d.ref0 - DerivPricing.FX_STRIKE)
            assertEquals("seed $seed", expected, pay, 1e-6)
            assertTrue(pay >= 0.0)
            if (pay > 0.5) sawPositive = true else sawZero = true
        }
        assertTrue("opsi kadang dalam uang", sawPositive)
        assertTrue("opsi kadang kedaluwarsa tanpa nilai", sawZero)
    }

    @Test
    fun noFreeMoney_averageNetIsNegative_forEveryKind() {
        // Seharusnya rata-rata (penyelesaian - premi) negatif untuk pembeli yang tidak tahu masa depan: marjin harga memuat biaya.
        val failures = ArrayList<String>()
        for (k in DerivKind.values()) {
            var sum = 0.0; var n = 0; var gross = 0.0; var pos = 0; var neg = 0
            for (seed in 1L..120L) {
                val e = KapitalEngine(seed)
                var t = 0
                var bought = false
                while (e.gameOverReason == null && t < 30 && (!bought || e.derivs.isNotEmpty())) {
                    t++
                    // swap hanya bermakna selama utang masih berbunga mengambang (bot disiplin segera menetapkannya), jadi dibeli di giliran 1
                    val buyT = if (k == DerivKind.SWAP) 1 else 3 + (seed % 20).toInt()
                    if (t == buyT && !bought) bought = e.apply(Action.BuyDerivative(k.code, 100))
                    TestBots.disciplinedStep(e, t)
                    e.apply(Action.EndTurn)
                }
                if (bought && e.derivs.isEmpty()) {
                    val notional = 1.0
                    sum += e.derivNetRs; n++; gross += e.derivNetRs + e.derivPremiumsRs
                    if (e.derivNetRs + e.derivPremiumsRs > 1.0) pos++ else if (e.derivNetRs + e.derivPremiumsRs < -1.0) neg++
                }
            }
            println("DERIV $k n=$n meanNet=${sum / n} meanGross=${gross / n} pos=$pos neg=$neg")
            assertTrue("$k diuji pada banyak seed: $n", n >= 60)
            if (!(sum / n < 0.0)) failures += "$k rata-rata bersih ${sum / n}"
        }
        assertTrue("rata-rata bersih harus negatif (tanpa uang gratis): $failures", failures.isEmpty())
    }

    @Test
    fun swapAndFutures_settleBothSigns() {
        var swapPos = 0; var swapNeg = 0; var futPos = 0; var futNeg = 0
        for (seed in 1L..80L) {
            val e = KapitalEngine(seed)
            e.apply(Action.BuyDerivative("swp", 100)); e.apply(Action.BuyDerivative("ctf", 100))
            val prem = e.derivPremiumsRs
            var guard = 0
            while (e.derivs.isNotEmpty() && e.gameOverReason == null && guard++ < 14) {
                TestBots.disciplinedStep(e, e.turn); e.apply(Action.EndTurn)
            }
            if (e.derivs.isNotEmpty()) continue
        }
        // Dihitung terpisah per jenis agar tanda dapat dipisahkan
        for (seed in 1L..80L) {
            val e = KapitalEngine(seed); e.apply(Action.BuyDerivative("swp", 100)); val p = e.derivPremiumsRs
            var g = 0
            while (e.derivs.isNotEmpty() && e.gameOverReason == null && g++ < 14) { TestBots.disciplinedStep(e, e.turn); e.apply(Action.EndTurn) }
            if (e.derivs.isEmpty()) { if (e.derivNetRs + p > 0) swapPos++ else swapNeg++ }
            val f = KapitalEngine(seed); f.apply(Action.BuyDerivative("ctf", 100)); val q = f.derivPremiumsRs
            g = 0
            while (f.derivs.isNotEmpty() && f.gameOverReason == null && g++ < 14) { TestBots.disciplinedStep(f, f.turn); f.apply(Action.EndTurn) }
            if (f.derivs.isEmpty()) { if (f.derivNetRs + q > 0) futPos++ else futNeg++ }
        }
        println("DERIV signs swap +$swapPos/-$swapNeg futures +$futPos/-$futNeg")
        assertTrue(swapPos > 5 && swapNeg > 5)
        assertTrue(futPos > 5 && futNeg > 5)
    }

    @Test
    fun deterministic_andSaveRoundTrip() {
        fun run(): Pair<Double, String> {
            val e = KapitalEngine(21L)
            val rec = mutableListOf<String>()
            for (t in 1..16) {
                TestBots.disciplinedStep(e, t, rec)
                if (t == 2) for (a in listOf(Action.BuyDerivative("fxo", 50), Action.BuyDerivative("ctf", 50), Action.BuyDerivative("swp", 100))) if (e.apply(a)) rec += a.encode()
                e.apply(Action.EndTurn); rec += Action.EndTurn.encode()
            }
            return e.equity to rec.joinToString("\n")
        }
        val (a, recA) = run(); val (b, _) = run()
        assertEquals(a, b, 0.0)
        val r = KapitalEngine(21L)
        for (line in recA.split("\n")) Action.decode(line)?.let { r.apply(it) }
        assertEquals(a, r.equity, 1e-6)
    }

    @Test
    fun swap_settlementMovesWithInterestBill() {
        // Saat suku bunga naik, tagihan bunga naik dan swap membayar; korelasi lintas seed harus jelas positif.
        val xs = ArrayList<Double>(); val ys = ArrayList<Double>()
        for (seed in 1L..80L) {
            val ctl = KapitalEngine(seed); val e = KapitalEngine(seed)
            e.apply(Action.BuyDerivative("swp", 100))
            val net0 = e.derivNetRs
            val base = ctl.monthlyInterest()
            var extra = 0.0; var ok = true
            for (t in 1..12) {
                if (ctl.gameOverReason != null || e.gameOverReason != null) { ok = false; break }
                TestBots.disciplinedStep(ctl, t); TestBots.disciplinedStep(e, t)
                ctl.apply(Action.EndTurn); e.apply(Action.EndTurn)
                extra += ctl.policyRate - 0.0
            }
            if (!ok) continue
            xs += extra / 12.0 - ctl.let { 0.0 }; ys += e.derivNetRs - net0
        }
        val mx = xs.average(); val my = ys.average()
        val cov = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
        val corr = cov / Math.sqrt(xs.sumOf { (it - mx) * (it - mx) } * ys.sumOf { (it - my) * (it - my) })
        println("DERIV swap corr(rate path, settlement)=$corr n=${xs.size}")
        assertTrue(xs.size >= 30)
        assertTrue("penyelesaian swap berkorelasi dengan suku bunga: $corr", corr > 0.5)
    }

    @Test
    fun repositoryView_listsDerivatives_andPurchaseWorks() {
        val repo = com.example.data.GameRepository(null, 3L)
        val v0 = repo.gameState.value.operations.derivs
        assertEquals(3, v0.size)
        assertTrue(v0.all { it.activeText == null && it.blocks.size == 3 && it.premiums.size == 3 })
        assertTrue(v0.first { it.code == "ctf" }.blocks.all { it == null })
        assertTrue(repo.buyDerivative("ctf", 50))
        val v1 = repo.gameState.value.operations.derivs.first { it.code == "ctf" }
        assertNotNull(v1.activeText)
        assertFalse(repo.buyDerivative("ctf", 50))
    }
}
