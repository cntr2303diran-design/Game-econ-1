package com.example.engine

/**
 * Semua tindakan pemain. Permainan disimpan sebagai seed + daftar tindakan ini,
 * lalu dimainkan ulang saat dimuat (mesin bersifat deterministik).
 */
sealed class Action {
    object EndTurn : Action()
    data class Journal(
        val decisionId: String,
        val optionId: String,
        val confidencePct: Int,
        val predictedCash: Double,
        val reason: String,
        val prediction: String,
        val failure: String
    ) : Action()
    data class RejectProposal(val decisionId: String) : Action()
    data class EventChoice(val eventId: String, val optionId: String) : Action()
    data class SetSalesScheme(val scheme: SalesScheme) : Action()
    data class SetFactoryScheme(val scheme: FactoryScheme) : Action()
    data class CrossCheck(val newsId: String) : Action()
    data class ReviewContract(val contractId: String) : Action()
    object DualSourcing : Action()
    object HedgeCotton : Action()
    object EquityCure : Action()
    data class Audit(val execId: String) : Action()
    data class Confront(val execId: String) : Action()
    data class Fire(val execId: String) : Action()
    data class AdjustPrice(val deltaPct: Int) : Action()
    data class ApplyCredit(val bankId: String, val amount: Int, val tenor: Int, val purpose: String, val floating: Boolean, val bullet: Boolean) : Action()
    data class ReviewOffer(val appId: String) : Action()
    data class AcceptOffer(val appId: String) : Action()
    data class DeclineOffer(val appId: String) : Action()
    data class Invest(val typeId: String) : Action()
    data class BuyStock(val companyId: String, val lots: Int) : Action()
    data class SellStock(val companyId: String, val lots: Int) : Action()

    // Tahap 1 & 3: Tingkat kesulitan & Operasional Multinasional (Era 2)
    data class SetDifficulty(val difficulty: Difficulty) : Action()
    data class OpenSubsidiary(val countryId: String) : Action()
    data class SetTransferPricing(val mode: TransferPricingMode) : Action()
    data class Repatriate(val countryId: String) : Action()
    object HedgeFx : Action()
    data class AcquireRival(val rivalId: String) : Action()
    data class BorrowForeignSub(val countryId: String, val amountLocal: Int) : Action()

    // Tahap 4 & 5: Era 3 (Konglomerasi & Finansialisasi) & Era 4 (Karbon & Suksesi)
    data class IssueCorporateBond(val amountRs: Int, val tenorMonths: Int) : Action()
    data class ExecuteIpo(val floatPct: Int, val dualClassShares: Boolean) : Action()
    object OpenCaptiveBank : Action()
    data class Buyback(val amountRs: Int) : Action()
    data class SellSubStake(val countryId: String, val pct: Int) : Action()
    data class IssueShares(val amountRs: Int) : Action()
    data class MediaLobby(val kind: String) : Action()
    data class CarbonAction(val kind: String, val units: Int) : Action()
    data class AppointSuccessor(val candidateId: String) : Action()

    fun encode(): String = when (this) {
        EndTurn -> "END"
        is Journal -> listOf(
            "JRN", decisionId, optionId, confidencePct.toString(), predictedCash.toString(),
            reason, prediction, failure
        ).joinToString("|") { esc(it) }
        is RejectProposal -> "REJ|${esc(decisionId)}"
        is EventChoice -> "EVC|${esc(eventId)}|${esc(optionId)}"
        is SetSalesScheme -> "SAL|${scheme.name}"
        is SetFactoryScheme -> "FAC|${scheme.name}"
        is CrossCheck -> "CHK|${esc(newsId)}"
        is ReviewContract -> "REV|${esc(contractId)}"
        DualSourcing -> "DUAL"
        HedgeCotton -> "HEDGE"
        EquityCure -> "CURE"
        is Audit -> "AUD|${esc(execId)}"
        is Confront -> "CON|${esc(execId)}"
        is Fire -> "FIR|${esc(execId)}"
        is AdjustPrice -> "PRC|$deltaPct"
        is ApplyCredit -> "APL|${esc(bankId)}|$amount|$tenor|${esc(purpose)}|$floating|$bullet"
        is Invest -> "INV|${esc(typeId)}"
        is ReviewOffer -> "ROF|${esc(appId)}"
        is AcceptOffer -> "AOF|${esc(appId)}"
        is DeclineOffer -> "DOF|${esc(appId)}"
        is BuyStock -> "BUY|${esc(companyId)}|$lots"
        is SellStock -> "SEL|${esc(companyId)}|$lots"
        is SetDifficulty -> "DIF|${difficulty.name}"
        is OpenSubsidiary -> "SUB|${esc(countryId)}"
        is SetTransferPricing -> "TPR|${mode.name}"
        is Repatriate -> "REP|${esc(countryId)}"
        HedgeFx -> "HFX"
        is AcquireRival -> "ACQ|${esc(rivalId)}"
        is BorrowForeignSub -> "BSB|${esc(countryId)}|$amountLocal"
        is IssueCorporateBond -> "BND|$amountRs|$tenorMonths"
        is ExecuteIpo -> "IPO|$floatPct|$dualClassShares"
        OpenCaptiveBank -> "CBK"
        is Buyback -> "BYB|$amountRs"
        is SellSubStake -> "NCI|${esc(countryId)}|$pct"
        is IssueShares -> "SPO|$amountRs"
        is MediaLobby -> "LOB|${esc(kind)}"
        is CarbonAction -> "CRB|${esc(kind)}|$units"
        is AppointSuccessor -> "SUC|${esc(candidateId)}"
    }

    companion object {
        private fun esc(s: String): String =
            s.replace("%", "%25").replace("|", "%7C").replace("\n", "%0A").replace("\r", "")

        private fun unesc(s: String): String =
            s.replace("%0A", "\n").replace("%7C", "|").replace("%25", "%")

        fun decode(line: String): Action? {
            val p = line.split("|").map { unesc(it) }
            return try {
                when (p[0]) {
                    "END" -> EndTurn
                    "JRN" -> Journal(p[1], p[2], p[3].toInt(), p[4].toDouble(), p[5], p[6], p[7])
                    "REJ" -> RejectProposal(p[1])
                    "EVC" -> EventChoice(p[1], p[2])
                    "INV" -> Invest(p[1])
                    "SAL" -> SetSalesScheme(SalesScheme.valueOf(p[1]))
                    "FAC" -> SetFactoryScheme(FactoryScheme.valueOf(p[1]))
                    "CHK" -> CrossCheck(p[1])
                    "REV" -> ReviewContract(p[1])
                    "DUAL" -> DualSourcing
                    "HEDGE" -> HedgeCotton
                    "CURE" -> EquityCure
                    "AUD" -> Audit(p[1])
                    "CON" -> Confront(p[1])
                    "FIR" -> Fire(p[1])
                    "PRC" -> AdjustPrice(p[1].toInt())
                    "APL" -> ApplyCredit(p[1], p[2].toInt(), p[3].toInt(), p[4], p[5].toBoolean(), p[6].toBoolean())
                    "ROF" -> ReviewOffer(p[1])
                    "AOF" -> AcceptOffer(p[1])
                    "DOF" -> DeclineOffer(p[1])
                    "BUY" -> BuyStock(p[1], p[2].toInt())
                    "SEL" -> SellStock(p[1], p[2].toInt())
                    "DIF" -> SetDifficulty(Difficulty.valueOf(p[1]))
                    "SUB" -> OpenSubsidiary(p[1])
                    "TPR" -> SetTransferPricing(TransferPricingMode.valueOf(p[1]))
                    "REP" -> Repatriate(p[1])
                    "HFX" -> HedgeFx
                    "ACQ" -> AcquireRival(p[1])
                    "BSB" -> BorrowForeignSub(p[1], p[2].toInt())
                    "BND" -> IssueCorporateBond(p[1].toInt(), p[2].toInt())
                    "IPO" -> ExecuteIpo(p[1].toInt(), p[2].toBoolean())
                    "CBK" -> OpenCaptiveBank
                    "BYB" -> Buyback(p[1].toInt())
                    "NCI" -> SellSubStake(p[1], p[2].toInt())
                    "SPO" -> IssueShares(p[1].toInt())
                    "LOB" -> MediaLobby(p[1])
                    "CRB" -> CarbonAction(p[1], p[2].toInt())
                    "SUC" -> AppointSuccessor(p[1])
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
