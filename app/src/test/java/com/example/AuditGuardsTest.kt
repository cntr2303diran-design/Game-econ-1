package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/** Penjaga hasil audit rilis: parameter aksi ekstrem tidak boleh meledak, dan keadaan makro/pembukuan tetap waras sepanjang kampanye penuh. */
class AuditGuardsTest {

    private fun botTo(seed: Long, turn: Int): KapitalEngine? {
        val e = KapitalEngine(seed); var t = 0
        while (e.gameOverReason == null && e.turn < turn) { t++; ActiveBot.step(e, t); e.apply(Action.EndTurn) }
        return if (e.gameOverReason == null) e else null
    }

    private fun extremeActions(): List<Action> {
        val ints = listOf(Int.MIN_VALUE, -1_000_000, -1, 0, 1, 7, 100, 1_000_000, Int.MAX_VALUE)
        val countries = listOf("tarsis", "kestrel", "halvenia", "meridia", "xx")
        val a = ArrayList<Action>()
        for (i in ints) {
            a += Action.AdjustPrice(i); a += Action.BuyStock("BSND", i); a += Action.SellStock("BSND", i)
            for (c in countries) { a += Action.BorrowForeignSub(c, i); a += Action.SellSubStake(c, i); a += Action.SellSubStake(c, 10, i) }
            a += Action.IssueCorporateBond(i, 36); a += Action.IssueCorporateBond(2000, i)
            a += Action.ExecuteIpo(i, true); a += Action.Buyback(i); a += Action.IssueShares(i)
            a += Action.CarbonAction("buy", i); a += Action.CarbonAction("sell", i)
            a += Action.BuyIntel("sektor", "x", i); a += Action.SetSegmentFocus("premium", i); a += Action.HireStaff("cfo", i)
            a += Action.SetRdLevel(i); a += Action.SetLogistics(i); a += Action.SetInsurance(i); a += Action.SaleLeaseback(i)
            a += Action.BuyDerivative("fxo", i); a += Action.WindowDress(i); a += Action.SetAuditor(i); a += Action.OpenSpv(i)
            a += Action.Predict(i, true, i); a += Action.Predict(1, false, i)
            a += Action.ApplyCredit("bank", i, 12, "modal", false, false); a += Action.ApplyCredit("bank", 1000, i, "modal", true, true)
        }
        return a
    }

    @Test
    fun extremeActionParameters_neverThrow_andNeverBreakTheBooks() {
        var tried = 0; var accepted = 0; var phases = 0
        for (k in 1..4) {
            val seed = k * 2654435761L % 100000007L
            for (ph in listOf(30, 110, 240, 380)) {
                var e = botTo(seed, ph) ?: continue
                phases++
                for (a in extremeActions()) {
                    tried++
                    val ok = try { e.apply(a) } catch (x: Throwable) { throw AssertionError("pengecualian ${x::class.simpleName}: ${x.message} pada ${a.encode()} seed=$seed turn=${e.turn}"); false }
                    val d = abs(e.totalAssets() - e.totalLiabilities() - e.equity)
                    assertTrue("neraca meleset $d setelah ${a.encode()} seed=$seed turn=${e.turn}", d < 1e-6)
                    assertTrue("nilai tak hingga setelah ${a.encode()}", e.cash.isFinite() && e.equity.isFinite())
                    if (ok) {
                        accepted++
                        // Aksi yang diterima mengubah keadaan; pulihkan keadaan bersih agar fase berikutnya tetap menguji fitur era itu.
                        e = botTo(seed, ph) ?: break
                    }
                }
            }
        }
        println("AUDIT parameter ekstrem: dicoba=$tried diterima=$accepted fase=$phases")
        assertTrue("uji tidak hampa: $phases fase, $tried aksi", phases >= 10 && tried >= 3000)
    }

    @Test
    fun adjustPrice_doesNotOverflowAtIntLimits() {
        val e = KapitalEngine(11L)
        while (e.priceAdjPct < 15) { if (!e.apply(Action.AdjustPrice(5))) { e.apply(Action.EndTurn) } }
        assertEquals(15, e.priceAdjPct)
        e.apply(Action.EndTurn)
        e.apply(Action.AdjustPrice(Int.MAX_VALUE))
        assertEquals("tetap 15, tidak memutar ke -15", 15, e.priceAdjPct)
        e.apply(Action.EndTurn)
        e.apply(Action.AdjustPrice(Int.MIN_VALUE))
        assertEquals(-15, e.priceAdjPct)
    }

    @Test
    fun fullCampaigns_keepMacroAndBooksSane() {
        var months = 0
        for (k in 1..12) {
            val e = KapitalEngine(k * 2654435761L % 100000007L); var t = 0
            while (e.gameOverReason == null && e.turn < 481) {
                t++
                if (k % 2 == 0) ActiveBot.step(e, t) else TestBots.disciplinedStep(e, t)
                e.apply(Action.EndTurn); months++
                val ctx = "seed=$k turn=${e.turn}"
                assertTrue("nilai terhingga $ctx", listOf(e.cash, e.equity, e.inventory, e.totalAssets(), e.totalLiabilities(), e.kurs, e.priceIndex, e.world.inflation, e.world.unemployment, e.world.gap).all { it.isFinite() })
                assertTrue("persediaan tak negatif $ctx: ${e.inventory}", e.inventory > -1e-6)
                assertTrue("kas tak negatif $ctx: ${e.cash}", e.cash > -1e-6)
                assertTrue("pengangguran dalam batas $ctx: ${e.world.unemployment}", e.world.unemployment in 0.5..40.0)
                assertTrue("inflasi dalam batas $ctx: ${e.world.inflation}", e.world.inflation in -5.0..30.0)
                assertTrue("kesenjangan output dalam batas $ctx: ${e.world.gap}", e.world.gap in -0.25..0.25)
                assertTrue("suku bunga dalam batas $ctx: ${e.policyRate}", e.policyRate in 0.5..25.0)
                assertTrue("kurs positif $ctx: ${e.kurs}", e.kurs > 0.0)
                assertTrue("indeks bursa positif $ctx: ${e.bourse.index}", e.bourse.index > 0.0)
            }
        }
        assertTrue("uji tidak hampa: $months bulan", months > 2000)
    }

    /**
     * Setiap aksi pemain harus punya jalur dari antarmuka: dipanggil lewat fungsi bernama di repositori, atau kodenya dipakai
     * sebagai tombol korporasi di layar. Aksi yang hanya bisa dijalankan bot adalah fitur yang tak bisa dipakai manusia.
     */
    @Test
    fun everyPlayerAction_isReachableFromTheUi() {
        fun dir(sub: String) = listOf("src/main/java/com/example/$sub", "app/src/main/java/com/example/$sub").map { java.io.File(it) }.firstOrNull { it.exists() }
            ?: throw AssertionError("sumber $sub tidak ditemukan dari ${java.io.File(".").absolutePath}")
        val actions = dir("engine/Actions.kt").readLines()
        val repo = dir("data/GameRepository.kt").readText()
        val ui = (dir("ui/screens").listFiles { f -> f.name.endsWith(".kt") }!!.toList() + dir("MainActivity.kt")).joinToString("\n") { it.readText() }
        val rx = Regex("""^\s*(?:is\s+)?(\w+)\s*->\s*"([A-Z]{3})""")
        val pairs = actions.mapNotNull { rx.find(it)?.let { m -> m.groupValues[1] to m.groupValues[2] } }
        assertTrue("uji tidak hampa: ${pairs.size} aksi", pairs.size >= 45)
        val unreachable = pairs.filter { (name, code) ->
            val viaRepo = Regex("""Action\.$name\b""").containsMatchIn(repo)
            val viaUiCode = Regex("\"$code(\\||\")").containsMatchIn(ui)
            !viaRepo && !viaUiCode
        }.map { it.first }
        // EndTurn dan Journal dst. dipanggil lewat repositori; sisanya harus punya tombol.
        assertTrue("aksi tanpa jalur dari UI: $unreachable", unreachable.isEmpty())
    }
}
