package com.example

import com.example.data.FakeGameRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun initialDummyState_matchesKapitalSpecification() {
        val repo = FakeGameRepository()
        val state = repo.gameState.value

        assertEquals("Maret 2033", state.dateText)
        assertEquals(39, state.turn)
        assertEquals(1, state.eraNumber)
        assertEquals(4, state.apCurrent)
        assertEquals(7, state.apMax)
        assertEquals(5, state.runwayMonths)
        assertEquals("Jarak ke covenant 6%", state.covenantWarningTitle)
        assertEquals("Uji kuartalan Bank B dalam 1 giliran", state.covenantWarningSubtitle)
        assertEquals("Kestrel dikabarkan batasi ekspor logam langka", state.featuredNewsHeadline)
        assertEquals(4, state.markasIndicators.size)
        assertEquals(3, state.pendingDecisions.size)
        assertEquals(5, state.morningPaperEdition.size)
        assertEquals(8, state.countries.size)
    }

    @Test
    fun decisionJournalAndTurnAdvance_updatesStateCorrectly() {
        val repo = FakeGameRepository()
        repo.submitDecisionJournal(
            decisionId = "dec_pemintalan",
            strategicReason = "Integrasi hulu benang",
            numericPrediction = "Margin naik 4%",
            confidencePct = 75,
            failureScenario = "Kestrel tahan mesin"
        )
        val afterJournal = repo.gameState.value
        assertEquals(1, afterJournal.apCurrent)
        assertTrue(afterJournal.pendingDecisions.first { it.id == "dec_pemintalan" }.isCompleted)

        repo.advanceTurn()
        val nextState = repo.gameState.value
        assertEquals(40, nextState.turn)
        assertEquals("April 2033", nextState.dateText)
        assertEquals(7, nextState.apCurrent)
    }
}
