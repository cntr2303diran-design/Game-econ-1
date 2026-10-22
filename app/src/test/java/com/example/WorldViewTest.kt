package com.example

import com.example.data.GameRepository
import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test

/** Tampilan dunia mengikuti mesin (bukan data statis): ketegangan, pemilu asing, dan lapisan dunia lanjutan. */
class WorldViewTest {

    @Test
    fun tensionsAndWorldLayer_areLive_notStatic() {
        val repo = GameRepository(null, 5L)
        val s0 = repo.gameState.value
        assertEquals(4, s0.pairwiseTensions.size)
        assertEquals(8, s0.worldLayer.rows.size)
        assertTrue(s0.worldLayer.lines.size >= 5)
        assertTrue(s0.worldLayer.rows.all { it.rating in Rating.values().map { r -> r.label } })
        val first = s0.pairwiseTensions.map { it.score }
        // jalankan beberapa giliran dan pastikan skor berubah dan tetap di 0..100
        var changed = false
        repeat(24) {
            repo.advanceTurn()
            val s = repo.gameState.value
            for (p in s.pairwiseTensions) assertTrue(p.score in 0..100)
            for (c in s.countries) assertTrue(c.tensionScore in 0..100)
            if (s.pairwiseTensions.map { it.score } != first) changed = true
        }
        assertTrue("skor ketegangan harus bergerak mengikuti mesin", changed)
        val s1 = repo.gameState.value
        assertTrue(s1.countries.filter { it.id != "sundara" }.all { it.electionCountdown.isNotBlank() })
    }

    @Test
    fun straitClosures_occurNaturally_rareEarly_andMoreOftenLater() {
        val n = 20
        var seedsWith = 0; var months = 0; var total = 0; val perEra = IntArray(5)
        for (seed in 1L..n) {
            val e = KapitalEngine(seed * 2654435761L % 100000007L)
            var t = 0; var any = false
            while (e.gameOverReason == null && e.turn < 481) {
                t++; TestBots.disciplinedStep(e, t); if (e.gameOverReason != null) break
                e.apply(Action.EndTurn); total++
                val closed = e.dunia.geo.straits.any { e.dunia.geo.strait(it.id) > 0.0 }
                if (closed) { months++; any = true; perEra[KapitalEngine.era(e.turn)]++ }
            }
            if (any) seedsWith++
        }
        assertTrue("selat harus menutup secara alami pada sebagian seed ($seedsWith/$n)", seedsWith >= 5)
        assertTrue("penutupan harus langka, bukan keadaan normal: $months/$total", months < 0.15 * total)
        assertTrue("era akhir lebih sering daripada era 1: ${perEra.toList()}", perEra[3] + perEra[4] > perEra[1])
    }
}
