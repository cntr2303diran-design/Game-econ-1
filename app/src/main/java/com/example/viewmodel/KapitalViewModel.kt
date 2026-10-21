package com.example.viewmodel

import androidx.lifecycle.ViewModel
import com.example.data.FakeGameRepository
import com.example.model.*
import kotlinx.coroutines.flow.StateFlow

class KapitalViewModel(
    private val repository: FakeGameRepository = FakeGameRepository()
) : ViewModel() {

    val gameState: StateFlow<GameState> = repository.gameState

    fun toggleTheme() = repository.toggleTheme()

    fun setMapLayer(layer: MapLayerFilter) = repository.setMapLayer(layer)

    fun selectCountryForBottomSheet(country: Country?) = repository.selectCountryForBottomSheet(country)

    fun inspectDataOrigin(origin: DataOriginInfo?) = repository.inspectDataOrigin(origin)

    fun openEventDialog(show: Boolean) = repository.openEventDialog(show)

    fun switchRole(roleName: String) = repository.switchRole(roleName)

    fun changeIncentiveScheme(schemeName: String) = repository.changeIncentiveScheme(schemeName)

    fun crossCheckNewsItem(newsId: String) = repository.crossCheckNewsItem(newsId)

    fun submitDecisionJournal(
        decisionId: String,
        strategicReason: String,
        numericPrediction: String,
        confidencePct: Int,
        failureScenario: String
    ) = repository.submitDecisionJournal(
        decisionId = decisionId,
        strategicReason = strategicReason,
        numericPrediction = numericPrediction,
        confidencePct = confidencePct,
        failureScenario = failureScenario
    )

    fun resolveEventOption(option: EventOptionItem) = repository.resolveEventOption(option)

    fun executeContractAction(contractId: String) = repository.executeContractAction(contractId)

    fun advanceTurn() = repository.advanceTurn()
}
