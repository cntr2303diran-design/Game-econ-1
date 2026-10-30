package com.example.viewmodel

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.GameRepository
import com.example.data.SaveStore
import com.example.data.SlotStore
import com.example.model.*
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import kotlin.concurrent.thread

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

/** Lima slot autosave bergilir sebagai berkas internal (kapital_slot_1..5.txt). */
class FileSlotStore(private val dir: File) : SlotStore {
    private fun file(slot: Int) = File(dir, "kapital_slot_$slot.txt")
    override fun readSlot(slot: Int): String? = try {
        file(slot).let { if (it.exists()) it.readText() else null }
    } catch (e: Exception) { null }

    override fun writeSlot(slot: Int, text: String) {
        FileSaveStore(file(slot)).write(text)
    }
}

/**
 * Pabrik eksplisit. Pabrik bawaan androidx (AndroidViewModelFactory.getInstance) menyimpan Application pertama dalam bidang statis,
 * sehingga di Robolectric setiap uji memakai folder simpanan uji pertama dan berkas simpanan suntikan tidak pernah terbaca.
 * Dengan pabrik ini ViewModel selalu mendapat Application milik aktivitasnya sendiri.
 */
class KapitalViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(KapitalViewModel::class.java)) { "ViewModel tidak dikenal: ${modelClass.name}" }
        return KapitalViewModel(app) as T
    }
}

class KapitalViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GameRepository(
        FileSaveStore(File(application.filesDir, "kapital_save.txt")),
        slots = FileSlotStore(application.filesDir),
        deferRestore = true
    )

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Memutar ulang simpanan di thread latar. Di ART yang belum hangat, kampanye panjang (ratusan bulan) memakan 6 sampai 20 detik;
     * di thread utama itu membekukan layar saat aplikasi dibuka. Selama berjalan, menu menampilkan panel progres dan perintah ditolak.
     */
    private fun startRestoreIfNeeded() {
        if (!repository.needsRestore) return
        thread(name = "kapital-restore", isDaemon = true) {
            val t0 = SystemClock.elapsedRealtime()
            repository.restorePending()
            val now = SystemClock.elapsedRealtime()
            // Dibaca skrip CI (tools/ci-emulator.sh) untuk mengukur muat simpanan dari proses dingin.
            Log.i("KAPITAL-LOAD", "restorePending selesai dalam ${now - t0} ms; sejak proses mulai ${now - Process.getStartElapsedRealtime()} ms")
        }
    }

    val gameState: StateFlow<GameState> = repository.gameState

    /** Kemajuan pemulihan simpanan, 0..1. */
    val restoreProgress: StateFlow<Float> = repository.restoreProgress

    /** Naik satu setiap pemulihan selesai (untuk berpindah layar setelah memuat autosave). */
    val restoredCount: StateFlow<Int> = repository.restoredCount

    init {
        startRestoreIfNeeded()
    }

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
    fun dismissSaveNotice() = repository.dismissSaveNotice()
    fun corporateAction(code: String) = repository.corporateAction(code)
    fun corporateActionFeedback(code: String) = repository.corporateActionFeedback(code)
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
    fun windowDress(pct: Int) = repository.windowDress(pct)
    fun setAuditor(level: Int) = repository.setAuditor(level)
    fun predict(qid: Int, yes: Boolean, stake: Int) = repository.predict(qid, yes, stake)
    fun openSpv(amountRs: Int) = repository.openSpv(amountRs)
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
    fun newGame(difficulty: com.example.engine.Difficulty? = null) = repository.newGame(difficulty)
    fun newGameFromChallenge(c: com.example.engine.Challenge) = repository.newGameFromChallenge(c)
    fun ceoSubmission(player: String): String? = repository.ceoSubmission(player)
    /** Memulai muat autosave di latar belakang; true bila slot sah dan pemulihan dimulai (selesainya ditandai [restoredCount]). */
    fun loadSlot(slot: Int): Boolean {
        val ok = repository.beginLoadSlot(slot)
        if (ok) startRestoreIfNeeded()
        return ok
    }

    /** "Bagaimana Jika" menghitung ulang seluruh riwayat, jadi dijalankan di thread latar; hasilnya diserahkan kembali ke thread utama. */
    fun runCounterfactual(actionIndex: Int) {
        val job = repository.beginCounterfactual(actionIndex) ?: return
        thread(name = "kapital-counterfactual", isDaemon = true) {
            val text = job.run()
            mainHandler.post { repository.finishCounterfactual(text) }
        }
    }
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
