import com.example.*
import com.example.engine.*

/**
 * Harness kalibrasi keseimbangan: menjalankan bot pasif dan aktif pada N seed (480 giliran) dan mencetak tingkat menang
 * serta kematian per era, dengan tombol DuniaTuning yang bisa diubah dari argumen.
 *
 * Kompilasi bersama engine/data/model dan kelas bot tes (TestBots.kt), lalu:
 *   java ... BalanceExpKt [N] [effectScale] [tensionDriftPerTurn] [pairNoise]
 * Contoh nilai terkirim: BalanceExpKt 300 1.0 0.035 1.9
 */
fun campaign(seed: Long, active: Boolean): KapitalEngine {
    val e = KapitalEngine(seed)
    var t = 0
    while (e.gameOverReason == null && e.turn < 481) {
        t++
        if (active) ActiveBot.step(e, t) else TestBots.disciplinedStep(e, t)
        e.apply(Action.EndTurn)
    }
    return e
}

fun main(args: Array<String>) {
    val n = args.getOrNull(0)?.toInt() ?: 120
    DuniaTuning.effectScale = args.getOrNull(1)?.toDouble() ?: DuniaTuning.effectScale
    DuniaTuning.tensionDriftPerTurn = args.getOrNull(2)?.toDouble() ?: DuniaTuning.tensionDriftPerTurn
    DuniaTuning.pairNoise = args.getOrNull(3)?.toDouble() ?: DuniaTuning.pairNoise
    println("n=$n effectScale=${DuniaTuning.effectScale} drift=${DuniaTuning.tensionDriftPerTurn} noise=${DuniaTuning.pairNoise}")
    for (active in listOf(false, true)) {
        var win = 0; val deathEra = IntArray(5); var gradeSum = 0
        for (seed in 1L..n) {
            val e = campaign(seed * 2654435761L % 100000007L, active)
            if (e.isVictory) { win++; gradeSum += e.evaluateCampaign().totalScore } else deathEra[KapitalEngine.era(e.turn)]++
        }
        println("  ${if (active) "AKTIF" else "PASIF"}: menang=$win/$n (${100 * win / n}%) mati per era=${deathEra.toList().drop(1)} skor rata2=${if (win == 0) 0 else gradeSum / win}")
    }
}
