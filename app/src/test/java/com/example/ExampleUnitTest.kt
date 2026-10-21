package com.example

import com.example.data.GameRepository
import com.example.data.SaveStore
import com.example.engine.Action
import com.example.engine.FactoryScheme
import com.example.engine.KapitalEngine
import com.example.engine.SalesScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ExampleUnitTest {

    private class MemoryStore : SaveStore {
        var text: String? = null
        override fun read() = text
        override fun write(text: String) { this.text = text }
    }

    @Test
    fun initialState_matchesStartingScenario() {
        val state = GameRepository(null, 1L).gameState.value
        assertEquals("Maret 2033", state.dateText)
        assertEquals(39, state.turn)
        assertEquals(4, state.apCurrent)
        assertEquals(7, state.apMax)
        assertEquals(4, state.markasIndicators.size)
        assertEquals(3, state.pendingDecisions.size)
        assertEquals(5, state.morningPaperEdition.size)
        assertEquals(8, state.countries.size)
    }

    @Test
    fun actionsRequireEnoughAp_andCannotRepeat() {
        val e = KapitalEngine(3L)
        assertTrue(e.apply(Action.Journal("dec_pemintalan", "", 60, 5000.0, "a", "b", "c"))) // 3 AP
        assertEquals(1, e.ap)
        assertFalse(e.apply(Action.Journal("dec_termsheet", "cnt_ts_1", 60, 5000.0, "a", "b", "c"))) // butuh 2 AP
        assertFalse(e.apply(Action.Journal("dec_pemintalan", "", 60, 5000.0, "a", "b", "c"))) // sudah diputuskan
        assertFalse(e.apply(Action.SetSalesScheme(SalesScheme.OMZET))) // skema sama
        assertTrue(e.pendingDecisions().none { it.id == "dec_pemintalan" })
    }

    @Test
    fun balanceSheetAlwaysBalances() {
        val e = KapitalEngine(7L)
        e.apply(Action.UnionOption("opt_union_1"))
        e.apply(Action.Journal("dec_termsheet", "cnt_ts_2", 70, 5000.0, "a", "b", "c"))
        repeat(60) {
            if (e.turn == 41) {
                e.apply(Action.SetSalesScheme(SalesScheme.KAS))
                e.apply(Action.SetFactoryScheme(FactoryScheme.ANDAL))
            }
            e.apply(Action.EndTurn)
            val diff = e.totalAssets() - e.totalLiabilities() - e.equity
            assertTrue("neraca selisih $diff pada giliran ${e.turn}", abs(diff) < 1.0)
            if (e.gameOverReason != null) return
        }
    }

    @Test
    fun saveAndReload_reproducesSameWorld() {
        val store = MemoryStore()
        val a = GameRepository(store, 11L)
        a.resolveEventOption(a.gameState.value.activeEventDialog.options[0])
        repeat(5) { a.advanceTurn() }
        val b = GameRepository(store)
        assertEquals(a.gameState.value.turn, b.gameState.value.turn)
        assertEquals(a.gameState.value.markasIndicators[0].valueText, b.gameState.value.markasIndicators[0].valueText)
    }

    @Test
    fun ignoringUnion_leadsToStrike() {
        val e = KapitalEngine(5L)
        e.apply(Action.EndTurn)
        e.apply(Action.EndTurn)
        assertTrue(e.chronicle.any { it.title.contains("Mogok") })
    }

    @Test
    fun actionEncoding_roundTrips() {
        val a = Action.Journal("dec_termsheet", "cnt_ts_1", 70, 1234.5, "alasan | dengan pipa", "prediksi\nbaris", "gagal 100%")
        val decoded = Action.decode(a.encode())
        assertNotNull(decoded)
        assertEquals(a, decoded)
    }
}
