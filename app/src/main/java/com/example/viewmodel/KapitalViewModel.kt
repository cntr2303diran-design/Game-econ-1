package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.data.GameRepository
import com.example.model.*
import kotlinx.coroutines.flow.StateFlow

class KapitalViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = GameRepository(application.applicationContext)

    val gameState: StateFlow<GameState> = repository.gameState

    fun toggleTheme() = repository.toggleTheme()
    fun setMapLayer(layer: MapLayerFilter) = repository.setMapLayer(layer)
    fun selectCountryForBottomSheet(country: Country?) = repository.selectCountryForBottomSheet(country)
    fun inspectDataOrigin(origin: DataOriginInfo?) = repository.inspectDataOrigin(origin)
    fun openEventDialog(show: Boolean, eventId: String? = null) = repository.openEventDialog(show, eventId)
    fun dismissTurnReport() = repository.dismissTurnReport()
    fun openSaveDialog(show: Boolean) = repository.openSaveDialog(show)
    fun clearBanner() = repository.clearBanner()
    fun switchRole(roleName: String) = repository.switchRole(roleName)
    fun setDifficulty(difficultyName: String) = repository.setDifficulty(difficultyName)
    fun changeIncentiveScheme(schemeName: String) = repository.changeIncentiveScheme(schemeName)
    fun changeFactoryScheme(schemeName: String) = repository.changeFactoryScheme(schemeName)
    fun crossCheckNewsItem(newsId: String) = repository.crossCheckNewsItem(newsId)

    fun submitDecisionJournal(
        decisionId: String,
        optionId: String,
        strategicReason: String,
        numericPrediction: String,
        confidencePct: Int,
        failureScenario: String
    ) = repository.submitDecisionJournal(
        decisionId = decisionId,
        optionId = optionId,
        strategicReason = strategicReason,
        numericPrediction = numericPrediction,
        confidencePct = confidencePct,
        failureScenario = failureScenario
    )

    fun rejectProposal(decisionId: String) = repository.rejectProposal(decisionId)
    fun resolveEventOption(option: EventOptionItem) = repository.resolveEventOption(option)
    fun executeContractAction(contractId: String) = repository.executeContractAction(contractId)
    fun auditExecutive(execId: String) = repository.auditExecutive(execId)
    fun confrontExecutive(execId: String) = repository.confrontExecutive(execId)
    fun fireExecutive(execId: String) = repository.fireExecutive(execId)
    fun adjustPrice(deltaPct: Int) = repository.adjustPrice(deltaPct)
    fun applyCredit(bankId: String, amount: Int, tenor: Int, purpose: String, floating: Boolean, bullet: Boolean) =
        repository.applyCredit(bankId, amount, tenor, purpose, floating, bullet)
    fun reviewCreditOffer(appId: String) = repository.reviewCreditOffer(appId)
    fun acceptCreditOffer(appId: String) = repository.acceptCreditOffer(appId)
    fun declineCreditOffer(appId: String) = repository.declineCreditOffer(appId)
    fun startInvestment(typeId: String) = repository.startInvestment(typeId)
    fun openSubsidiary(countryId: String) = repository.openSubsidiary(countryId)
    fun repatriateSubsidiary(countryId: String) = repository.repatriateSubsidiary(countryId)
    fun setTransferPricingMode(modeName: String) = repository.setTransferPricingMode(modeName)
    fun acquireRival(rivalId: String) = repository.acquireRival(rivalId)
    fun borrowForeignSub(countryId: String, amountRs: Int) = repository.borrowForeignSub(countryId, amountRs)
    fun issueCorporateBond(amountRs: Int, tenorMonths: Int) = repository.issueCorporateBond(amountRs, tenorMonths)
    fun executeIpo(stakePct: Int) = repository.executeIpo(stakePct, dualClassShares = false)
    fun openCaptiveBank() = repository.openCaptiveBank()
    fun mediaLobby(intensity: Int) = repository.mediaLobby(if (intensity >= 2) "media" else "lobby")
    fun carbonAction(actionType: Int) = repository.carbonAction(if (actionType >= 1) "buy" else "sell", 5)
    fun appointSuccessor(execId: String) = repository.appointSuccessor(execId)
    fun buyStock(companyId: String, lots: Int) = repository.buyStock(companyId, lots)
    fun sellStock(companyId: String, lots: Int) = repository.sellStock(companyId, lots)
    fun advanceTurn() = repository.advanceTurn()
    fun saveToSlot(slot: Int) = repository.saveToSlot(slot)
    fun loadFromSlot(slot: Int) = repository.loadFromSlot(slot)
    fun newGame(seed: Long) = repository.newGame(seed)
    fun newRandomGame() = repository.newGame()
    fun branchFromAction(actionIndex: Int) = repository.branchFromAction(actionIndex)
}
