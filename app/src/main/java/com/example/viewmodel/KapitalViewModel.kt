package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.data.GameRepository
import com.example.data.SaveStore
import com.example.model.*
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Menyimpan permainan ke file internal aplikasi (seed + log tindakan). */
class FileSaveStore(private val file: File) : SaveStore {
    override fun read(): String? = try {
        if (file.exists()) file.readText() else null
    } catch (e: Exception) {
        null
    }

    override fun write(text: String) {
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        } catch (e: Exception) {
            // Penyimpanan gagal tidak boleh menghentikan permainan.
        }
    }
}

class KapitalViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GameRepository(FileSaveStore(File(application.filesDir, "kapital_save.txt")))

    val gameState: StateFlow<GameState> = repository.gameState

    fun toggleTheme(currentlyDark: Boolean) = repository.toggleTheme(currentlyDark)
    fun setMapLayer(layer: MapLayerFilter) = repository.setMapLayer(layer)
    fun selectCountryForBottomSheet(country: Country?) = repository.selectCountryForBottomSheet(country)
    fun inspectDataOrigin(origin: DataOriginInfo?) = repository.inspectDataOrigin(origin)
    fun openEventDialog(show: Boolean, id: String? = null) = repository.openEventDialog(show, id)
    fun clearEventOutcome() = repository.clearEventOutcome()
    fun setSalesScheme(id: String) = repository.setSalesScheme(id)
    fun setFactoryScheme(id: String) = repository.setFactoryScheme(id)
    fun adjustPrice(delta: Int) = repository.adjustPrice(delta)
    fun invest(typeId: String) = repository.invest(typeId)
    fun corporateAction(code: String) = repository.corporateAction(code)
    fun setSegmentFocus(segment: String, level: Int) = repository.setSegmentFocus(segment, level)
    fun hireStaff(role: String, tier: Int) = repository.hireStaff(role, tier)
    fun fireStaff(role: String) = repository.fireStaff(role)
    fun raiseSalary(role: String) = repository.raiseSalary(role)
    fun setRdLevel(level: Int) = repository.setRdLevel(level)
    fun setLogistics(level: Int) = repository.setLogistics(level)
    fun setInsurance(level: Int) = repository.setInsurance(level)
    fun setTradeFinance(on: Boolean) = repository.setTradeFinance(on)
    fun saleLeaseback(pct: Int) = repository.saleLeaseback(pct)
    fun buyDerivative(kind: String, pct: Int) = repository.buyDerivative(kind, pct)
    fun sellSubStake(countryId: String, pct: Int, ask: Int) = repository.sellSubStake(countryId, pct, ask)
    fun buyIntel(kind: String, subject: String, tier: Int) = repository.buyIntel(kind, subject, tier)
    fun answerBoard(claim: String) = repository.answerBoard(claim)
    fun applyCredit(bankId: String, amount: Int, tenor: Int, purpose: String, floating: Boolean, bullet: Boolean) =
        repository.applyCredit(bankId, amount, tenor, purpose, floating, bullet)
    fun reviewOffer(appId: String) = repository.reviewOffer(appId)
    fun acceptOffer(appId: String) = repository.acceptOffer(appId)
    fun declineOffer(appId: String) = repository.declineOffer(appId)
    fun buyStock(id: String, lots: Int) = repository.buyStock(id, lots)
    fun sellStock(id: String, lots: Int) = repository.sellStock(id, lots)
    fun crossCheckNewsItem(newsId: String) = repository.crossCheckNewsItem(newsId)
    fun auditExecutive(id: String) = repository.auditExecutive(id)
    fun confrontExecutive(id: String) = repository.confrontExecutive(id)
    fun fireExecutive(id: String) = repository.fireExecutive(id)
    fun contractAction(contractId: String, actionId: String) = repository.contractAction(contractId, actionId)
    fun resolveEventOption(option: EventOptionItem) = repository.resolveEventOption(option)
    fun advanceTurn() = repository.advanceTurn()
    fun dismissMonthlyReport() = repository.dismissMonthlyReport()
    fun showMonthlyReport() = repository.showMonthlyReport()
    fun newGame(difficulty: com.example.engine.Difficulty = com.example.engine.Difficulty.MANAJER) = repository.newGame(difficulty)
    fun runCounterfactual(actionIndex: Int) = repository.runCounterfactual(actionIndex)
    fun clearCounterfactual() = repository.clearCounterfactual()
    fun rejectDecision(decisionId: String) = repository.rejectDecision(decisionId)

    fun submitDecisionJournal(
        decisionId: String,
        optionId: String,
        strategicReason: String,
        numericPrediction: String,
        confidencePct: Int,
        predictedCash: Double,
        failureScenario: String
    ): Boolean = repository.submitDecisionJournal(
        decisionId, optionId, strategicReason, numericPrediction, confidencePct, predictedCash, failureScenario
    )
}
