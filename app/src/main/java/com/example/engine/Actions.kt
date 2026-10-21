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
    data class UnionOption(val optionId: String) : Action()
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

    fun encode(): String = when (this) {
        EndTurn -> "END"
        is Journal -> listOf(
            "JRN", decisionId, optionId, confidencePct.toString(), predictedCash.toString(),
            reason, prediction, failure
        ).joinToString("|") { esc(it) }
        is RejectProposal -> "REJ|${esc(decisionId)}"
        is UnionOption -> "UNI|${esc(optionId)}"
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
                    "UNI" -> UnionOption(p[1])
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
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
