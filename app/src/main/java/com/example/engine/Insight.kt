package com.example.engine

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Tahap B7: pengasah otak. Tiga bagian yang tidak menyentuh neraca perusahaan (hanya poin, skor, dan teks),
 * sehingga identitas akuntansi dan determinisme jalur uang tidak berubah:
 *  1. Pasar prediksi: pertanyaan dunia nyata-simulasi dengan harga kerumunan, ditaruh dengan poin (bukan kas).
 *  2. Detektor bias kognitif dari riwayat keputusan pemain.
 *  3. Post-mortem: penyebab, titik balik, kualitas keputusan, dan pelajaran.
 */

/** Pengamatan dunia saat pertanyaan dibuat atau diselesaikan. */
data class MarketObs(
    val inflation: Double, val inflationExpectation: Double, val policyRate: Double, val gapPct: Double, val kurs: Double,
    /** Porsi perubahan kurs 6 bulan yang naik dalam riwayat, dihaluskan dengan prior 75% (bobot 6 pengamatan). */
    val fxUpShare: Double = 0.75
)

enum class QKind(val code: String) { INFLATION("INF"), RATE("RTE"), FX("FXK"), RECESSION("RSN") }

class MarketQuestion(
    val id: Int, val kind: QKind, val text: String, val createdTurn: Int, val resolveTurn: Int,
    val baseline: Double, val crowdYes: Double
) {
    var outcome: Boolean? = null; internal set
    var finalValue: Double? = null; internal set
}

class MarketPosition(val qid: Int, val yes: Boolean, val stake: Double, val price: Double) {
    var payout: Double? = null; internal set
    val won: Boolean? get() = payout?.let { it > 0.0 }
}

class PredictionMarket {
    companion object {
        const val START_POINTS = 100.0
        const val HORIZON = 6
        const val FEE = 0.02
        const val MAX_OPEN = 3
        const val MAX_STAKE_SHARE = 0.20
        /** Hanya sebagian kecil skor kualitas keputusan (maks. +10 dari 100) yang berasal dari pasar prediksi. */
        const val MAX_SCORE_BONUS = 10

        /** Peluang "ya" menurut kerumunan dari informasi yang tampak sekarang (tanpa melihat masa depan), ditambah derau. */
        /**
         * Peluang "ya" menurut kerumunan dari informasi yang tampak sekarang (tanpa melihat masa depan), ditambah derau.
         * Skala (a, b) per jenis dikalibrasi lewat regresi logistik hasil sebenarnya pada 150 seed bot disiplin
         * (tes `InsightTest` memeriksa kalibrasi dan bahwa penebak acak tak untung); bisa bergeser untuk gaya main lain.
         */
        fun crowdProbability(kind: QKind, o: MarketObs, noise: Double): Double {
            val z = when (kind) {
                // inflasi cenderung kembali ke sasaran 3% dan mengikuti ekspektasi
                QKind.INFLATION -> 0.5 * (o.inflationExpectation - o.inflation) - 0.4 * (o.inflation - 3.0)
                // bank sentral mengejar aturan Taylor
                QKind.RATE -> 0.8 * (1.5 + o.inflation + 0.5 * (o.inflation - 3.0) + 0.5 * o.gapPct - o.policyRate)
                // kurs: kerumunan mengekstrapolasi arah naik-turun 6 bulanan yang teramati
                QKind.FX -> { val s = o.fxUpShare.coerceIn(0.05, 0.95); Math.log(s / (1.0 - s)) }
                // resesi (output gap negatif) cukup persisten
                QKind.RECESSION -> -1.2 * o.gapPct
            }
            val (a, b) = when (kind) {
                QKind.INFLATION -> 0.229 to 0.697
                QKind.RATE -> 0.540 to 0.830
                QKind.FX -> 0.0 to 1.0
                QKind.RECESSION -> -0.168 to 0.297
            }
            return (1.0 / (1.0 + exp(-(a + b * (z + noise))))).coerceIn(0.05, 0.95)
        }

        fun questionText(kind: QKind, o: MarketObs): String = when (kind) {
            QKind.INFLATION -> "Inflasi Sundara $HORIZON bulan lagi lebih tinggi dari sekarang (${"%.1f".format(o.inflation)}%)?"
            QKind.RATE -> "Suku bunga acuan $HORIZON bulan lagi lebih tinggi dari sekarang (${"%.2f".format(o.policyRate)}%)?"
            QKind.FX -> "Kurs RS/A$ $HORIZON bulan lagi lebih tinggi (RS melemah) dari sekarang (${"%.4f".format(o.kurs)})?"
            QKind.RECESSION -> "Output gap Sundara $HORIZON bulan lagi negatif (ekonomi di bawah potensi)?"
        }

        fun value(kind: QKind, o: MarketObs): Double = when (kind) {
            QKind.INFLATION -> o.inflation
            QKind.RATE -> o.policyRate
            QKind.FX -> o.kurs
            QKind.RECESSION -> o.gapPct
        }

        fun resolves(kind: QKind, baseline: Double, now: Double): Boolean =
            if (kind == QKind.RECESSION) now < 0.0 else now > baseline
    }

    val questions = mutableListOf<MarketQuestion>()
    val positions = mutableListOf<MarketPosition>()
    var points = START_POINTS; private set
    private var nextId = 1

    fun open(): List<MarketQuestion> = questions.filter { it.outcome == null }
    fun positionOf(qid: Int): MarketPosition? = positions.firstOrNull { it.qid == qid }

    fun maxStake(): Int = max(0, (points * MAX_STAKE_SHARE).toInt())

    fun betBlock(qid: Int, stake: Int): String? {
        val q = questions.firstOrNull { it.id == qid } ?: return "Pertanyaan tidak ada."
        if (q.outcome != null) return "Pertanyaan sudah selesai."
        if (positionOf(qid) != null) return "Anda sudah menaruh posisi pada pertanyaan ini."
        if (stake < 1) return "Taruhan minimal 1 poin."
        if (stake > maxStake()) return "Taruhan maksimum ${maxStake()} poin (20% dari poin Anda)."
        return null
    }

    fun bet(qid: Int, yes: Boolean, stake: Int): Boolean {
        if (betBlock(qid, stake) != null) return false
        val q = questions.first { it.id == qid }
        val price = if (yes) q.crowdYes else 1.0 - q.crowdYes
        points -= stake
        positions += MarketPosition(qid, yes, stake.toDouble(), price)
        return true
    }

    /** Buat pertanyaan baru (maks. [MAX_OPEN] terbuka); jenis bergilir mengikuti [pick]. */
    fun openNew(turn: Int, o: MarketObs, r: Random): MarketQuestion? {
        if (open().size >= MAX_OPEN) return null
        val kind = QKind.values()[r.nextInt(QKind.values().size)]
        if (open().any { it.kind == kind }) return null
        val p = crowdProbability(kind, o, 0.35 * stdNormal(r))
        val q = MarketQuestion(nextId++, kind, questionText(kind, o), turn, turn + HORIZON, value(kind, o), p)
        questions += q
        return q
    }

    /** Selesaikan pertanyaan yang jatuh tempo; kembalikan teks laporan. */
    fun resolveDue(turn: Int, o: MarketObs): List<Pair<String, Tone>> {
        val out = mutableListOf<Pair<String, Tone>>()
        for (q in questions) {
            if (q.outcome != null || q.resolveTurn > turn) continue
            val now = value(q.kind, o)
            val yes = resolves(q.kind, q.baseline, now)
            q.outcome = yes; q.finalValue = now
            val pos = positionOf(q.id)
            if (pos != null) {
                val win = pos.yes == yes
                val pay = if (win) pos.stake / pos.price * (1.0 - FEE) else 0.0
                pos.payout = pay
                points += pay
                out += "Pasar prediksi: \"${q.text}\" → ${if (yes) "YA" else "TIDAK"}. Posisi ${if (pos.yes) "YA" else "TIDAK"} (${pos.stake.toInt()} poin pada harga ${"%.0f".format(pos.price * 100)}%) ${if (win) "menang +${"%.1f".format(pay - pos.stake)}" else "kalah −${pos.stake.toInt()}"} poin." to (if (win) Tone.GOOD else Tone.WARN)
            }
        }
        return out
    }

    fun resolved(): List<MarketQuestion> = questions.filter { it.outcome != null }

    /** Brier kerumunan pada semua pertanyaan selesai (pembanding kualitas pasar). */
    fun crowdBrier(): Double {
        val r = resolved(); if (r.isEmpty()) return 0.25
        return r.sumOf { val o = if (it.outcome == true) 1.0 else 0.0; (it.crowdYes - o) * (it.crowdYes - o) } / r.size
    }

    /** Keyakinan tersirat pemain pada posisi selesai = harga sisi yang dipilih; Brier pemain terhadap hasil. */
    fun playerBrier(): Double {
        val r = positions.filter { it.payout != null }; if (r.isEmpty()) return 0.25
        return r.sumOf { val won = if (it.won == true) 1.0 else 0.0; (it.price - won) * (it.price - won) } / r.size
    }

    /** Tambahan skor kualitas keputusan (0..[MAX_SCORE_BONUS]); hanya dari laba poin, tak pernah negatif. */
    fun scoreBonus(): Int = ((points - START_POINTS) / 10.0).toInt().coerceIn(0, MAX_SCORE_BONUS)
}

/** Potret bulanan untuk detektor bias dan post-mortem (tidak memengaruhi simulasi). */
class MonthSnap(
    val turn: Int, val cash: Double, val debt: Double, val equity: Double, val revenue: Double, val ni: Double,
    val covDistPct: Double, val ethicsLoad: Double, val priceAdjPct: Int, val costIndex: Double,
    val kurs: Double = 1.0
)

class BiasFinding(val code: String, val title: String, val severity: Double, val text: String)

object BiasDetector {
    /**
     * Pola bias dari jurnal keputusan, taruhan pasar prediksi, dan riwayat bulanan.
     * Semua bergantung pada data yang cukup (ambang jumlah sampel) agar tak menuduh tanpa bukti.
     */
    fun detect(
        journals: List<JournalRecord>, market: PredictionMarket, history: List<MonthSnap>,
        crossChecks: Int, turn: Int, spinningSunkCost: Boolean
    ): List<BiasFinding> {
        val out = mutableListOf<BiasFinding>()
        val ev = journals.filter { it.outcome != null }
        if (ev.size >= 2) {
            val avgConf = ev.map { it.confidencePct }.average()
            val hit = ev.count { it.outcome == true } * 100.0 / ev.size
            if (avgConf - hit > 20) out += BiasFinding("OVERCONFIDENCE", "Terlalu percaya diri", min(1.0, (avgConf - hit) / 50.0),
                "Overconfidence: rata-rata keyakinan Anda ${avgConf.toInt()}%, tetapi hanya ${hit.toInt()}% prediksi yang benar.")
            else if (hit - avgConf > 20) out += BiasFinding("UNDERCONFIDENCE", "Kurang percaya diri", min(1.0, (hit - avgConf) / 50.0),
                "Underconfidence: Anda benar ${hit.toInt()}% dari waktu, lebih sering dari keyakinan Anda (${avgConf.toInt()}%).")
        }
        // Kekeliruan perencanaan: kas aktual rutin di bawah yang diprediksi sendiri
        val ratios = ev.filter { it.predictedCash > 1.0 && it.actualCash != null }.map { it.actualCash!! / it.predictedCash }
        if (ratios.size >= 3 && ratios.average() < 0.85)
            out += BiasFinding("PLANNING", "Kekeliruan perencanaan", min(1.0, (0.85 - ratios.average()) * 4.0),
                "Kekeliruan perencanaan: kas aktual rata-rata hanya ${"%.0f".format(ratios.average() * 100)}% dari yang Anda prediksi (${ratios.size} jurnal).")
        if (turn >= 42 && crossChecks == 0)
            out += BiasFinding("CONFIRMATION", "Bias konfirmasi", 0.5, "Confirmation bias: Anda belum pernah memeriksa silang berita sejak memimpin perusahaan.")
        if (spinningSunkCost)
            out += BiasFinding("SUNK_COST", "Biaya tenggelam", 0.6, "Waspadai sunk cost: proyek pemintalan molor. Nilai ulang berdasarkan biaya ke depan, bukan dana yang sudah keluar.")

        // Pasar prediksi
        val done = market.positions.filter { it.payout != null }
        if (done.size >= 6) {
            val avgPrice = done.map { it.price }.average()
            val hit = done.count { it.won == true }.toDouble() / done.size
            if (avgPrice - hit > 0.15) out += BiasFinding("MARKET_OVERCONF", "Taruhan terlalu berani", min(1.0, (avgPrice - hit) * 2.0),
                "Taruhan terlalu berani: Anda memilih sisi yang menurut kerumunan ${"%.0f".format(avgPrice * 100)}% mungkin, tetapi hanya ${"%.0f".format(hit * 100)}% menang.")
            val yesShare = done.count { it.yes }.toDouble() / done.size
            if (yesShare >= 0.85 || yesShare <= 0.15) out += BiasFinding("DIRECTION", "Condong satu arah", 0.4,
                "Condong satu arah: ${"%.0f".format(max(yesShare, 1 - yesShare) * 100)}% taruhan Anda di sisi ${if (yesShare >= 0.5) "YA" else "TIDAK"}; pertanyaan berbeda butuh jawaban berbeda.")
            // Mengejar kerugian: taruhan setelah kalah jauh lebih besar daripada setelah menang
            val afterLoss = mutableListOf<Double>(); val afterWin = mutableListOf<Double>()
            for (i in 1 until done.size) (if (done[i - 1].won == true) afterWin else afterLoss) += done[i].stake
            if (afterLoss.size >= 2 && afterWin.size >= 2 && afterLoss.average() > 1.5 * afterWin.average())
                out += BiasFinding("LOSS_CHASING", "Mengejar kerugian", min(1.0, afterLoss.average() / afterWin.average() / 3.0),
                    "Mengejar kerugian: taruhan setelah kalah rata-rata ${"%.1f".format(afterLoss.average())} poin vs ${"%.1f".format(afterWin.average())} setelah menang.")
        }

        // Bias status quo: harga jual tak pernah disesuaikan selagi biaya naik jauh
        if (history.size >= 36) {
            val w = history.takeLast(36)
            val priceSame = w.all { it.priceAdjPct == w.first().priceAdjPct }
            val costRise = w.last().costIndex / max(0.01, w.first().costIndex) - 1.0
            if (priceSame && costRise >= 0.12)
                out += BiasFinding("STATUS_QUO", "Bias status quo", min(1.0, costRise * 3.0),
                    "Bias status quo: harga jual tidak diubah 36 bulan padahal indeks biaya naik ${"%.0f".format(costRise * 100)}%.")
        }
        return out
    }

    /** Teks daftar seperti yang ditampilkan ke pemain. */
    fun texts(f: List<BiasFinding>): List<String> =
        if (f.isEmpty()) listOf("Belum ada pola bias yang terdeteksi. Kalibrasi dinilai setelah jurnal dievaluasi.") else f.sortedByDescending { it.severity }.map { it.text }
}

class PostMortem(val headline: String, val causes: List<String>, val turningPoints: List<String>, val decisionQuality: List<String>, val lessons: List<String>) {
    fun lines(): List<String> = buildList {
        add(headline)
        if (causes.isNotEmpty()) { add("Faktor penyumbang:"); addAll(causes.map { "• $it" }) }
        if (turningPoints.isNotEmpty()) { add("Titik balik:"); addAll(turningPoints.map { "• $it" }) }
        if (decisionQuality.isNotEmpty()) { add("Kualitas keputusan:"); addAll(decisionQuality.map { "• $it" }) }
        if (lessons.isNotEmpty()) { add("Pelajaran:"); addAll(lessons.map { "• $it" }) }
    }
}

object PostMortemBuilder {
    private class Cause(val score: Double, val text: String, val lesson: String)

    fun build(
        reason: String?, victory: Boolean, history: List<MonthSnap>, chronicle: List<ChronicleItem>,
        journals: List<JournalRecord>, market: PredictionMarket, biases: List<BiasFinding>,
        scandals: Int, covenantBreaches: Int, cureUsed: Boolean, brier: Double
    ): PostMortem {
        val headline = when {
            reason == null -> "Permainan masih berjalan; ini tinjauan sementara."
            victory -> "Kampanye selesai: Anda bertahan sampai akhir."
            else -> "Akhir permainan: ${reason.substringBefore(" (").take(160)}"
        }
        val causes = mutableListOf<Cause>()
        val tail = history.takeLast(24)
        if (tail.isNotEmpty()) {
            val lev = tail.maxByOrNull { if (it.equity > 1.0) it.debt / it.equity else 50.0 }!!
            val ratio = if (lev.equity > 1.0) lev.debt / lev.equity else 50.0
            if (ratio > 1.5) causes += Cause(min(1.0, ratio / 4.0), "Leverage tinggi: utang/ekuitas mencapai ${"%.1f".format(ratio)}x pada giliran ${lev.turn}.",
                "Jaga utang/ekuitas di bawah ±1,5x; tambahan utang mempercepat kematian saat laba turun.")
            val lowCash = tail.minByOrNull { it.cash / max(1.0, it.revenue) }!!
            val months = lowCash.cash / max(1.0, lowCash.revenue)
            if (months < 0.6) causes += Cause(min(1.0, (0.6 - months) * 2.0 + 0.3), "Kas tipis: hanya ${"%.2f".format(months)} bulan pendapatan pada giliran ${lowCash.turn}.",
                "Sisakan kas minimum ±1 bulan pendapatan sebelum membayar dividen atau berinvestasi.")
            val y = tail.takeLast(12)
            val loss = y.count { it.ni < 0.0 }
            if (loss >= 6) causes += Cause(min(1.0, loss / 12.0 + 0.2), "Rugi di $loss dari ${y.size} bulan terakhir (laba bersih total ${"%.0f".format(y.sumOf { it.ni })} jt RS).",
                "Rugi berkepanjangan menggerus ekuitas dan kovenan; perbaiki margin lebih awal.")
            if (tail.size >= 12) {
                val rise = tail.last().costIndex / max(0.01, tail.first().costIndex) - 1.0
                val priceMove = (tail.last().priceAdjPct - tail.first().priceAdjPct) / 100.0
                if (rise > 0.10 && priceMove < rise - 0.05) causes += Cause(min(1.0, (rise - priceMove) * 3.0), "Jepitan biaya: indeks biaya naik ${"%.0f".format(rise * 100)}% dalam ${tail.size} bulan, harga jual hanya ${"%.0f".format(priceMove * 100)}%.",
                    "Teruskan kenaikan biaya ke harga atau hedging sebelum margin habis.")
            }
            val eth = tail.maxOf { it.ethicsLoad }
            if (eth > 3.0 || scandals > 0) causes += Cause(min(1.0, eth / 8.0 + 0.3 * scandals), "Jejak etika: muatan etika puncak ${"%.1f".format(eth)}, $scandals skandal meledak.",
                "Bukti pelanggaran menua dan bocor; jangan menyuap atau mempercantik laporan.")
        }
        if (covenantBreaches > 0) causes += Cause(min(1.0, 0.4 + 0.2 * covenantBreaches), "Kovenan dilanggar $covenantBreaches kali.", "Pantau jarak kovenan tiap kuartal, bukan setelah terlambat.")
        if (cureUsed) causes += Cause(0.35, "Suntikan ekuitas darurat terpakai: sinyal keuangan sudah rapuh.", "Jangan bergantung pada penyelamatan pemegang saham; cure hanya sekali.")
        val ranked = causes.sortedByDescending { it.score }.take(4)

        // Titik balik: bulan penurunan ekuitas terdalam (berjarak ≥6 bulan), dicocokkan dengan kronik di sekitarnya
        val tps = mutableListOf<String>()
        if (history.size >= 2) {
            val drops = (1 until history.size).map { history[it].turn to (history[it].equity - history[it - 1].equity) }
                .filter { it.second < 0.0 }.sortedBy { it.second }
            val used = mutableListOf<Int>()
            for ((t, d) in drops) {
                if (used.any { kotlin.math.abs(it - t) < 6 }) continue
                used += t
                val near = chronicle.filter { it.turn in (t - 2)..t }.lastOrNull()
                tps += "Giliran $t: ekuitas turun ${"%.0f".format(-d)} jt RS" + (near?.let { " (${it.title}: ${it.causalChain})" } ?: " (tanpa catatan kronik; kemungkinan siklus, kurs, atau rugi operasi)")
                if (tps.size >= 3) break
            }
        }

        val dq = mutableListOf<String>()
        val ev = journals.filter { it.outcome != null }
        dq += if (ev.isEmpty()) "Tidak ada jurnal keputusan yang dievaluasi; keputusan besar tak teruji." else "Skor Brier jurnal ${"%.2f".format(brier)} dari ${ev.size} prediksi (0,25 = menebak acak; makin kecil makin baik)."
        val settled = market.positions.filter { it.payout != null }
        if (settled.isNotEmpty()) dq += "Pasar prediksi: ${settled.count { it.won == true }}/${settled.size} posisi menang, poin ${"%.0f".format(market.points)} (awal ${PredictionMarket.START_POINTS.toInt()}); Brier Anda ${"%.2f".format(market.playerBrier())} vs kerumunan ${"%.2f".format(market.crowdBrier())}."
        biases.sortedByDescending { it.severity }.take(3).forEach { dq += "Bias: ${it.title}. ${it.text}" }

        val lessons = mutableListOf<String>()
        ranked.forEach { lessons += it.lesson }
        biases.sortedByDescending { it.severity }.take(2).forEach {
            lessons += when (it.code) {
                "OVERCONFIDENCE", "MARKET_OVERCONF" -> "Turunkan keyakinan: tulis alasan mengapa Anda bisa salah sebelum mengisi angka."
                "UNDERCONFIDENCE" -> "Percayai analisis Anda lebih berani saat bukti kuat."
                "PLANNING" -> "Tambahkan margin 15–25% pada rencana kas; proyek hampir selalu lebih lambat dan mahal."
                "CONFIRMATION" -> "Periksa silang berita dengan sumber kedua sebelum mengambil keputusan besar."
                "SUNK_COST" -> "Nilai ulang proyek berdasarkan biaya dan manfaat ke depan, bukan dana yang sudah keluar."
                "LOSS_CHASING" -> "Batasi taruhan setelah kalah; ukuran taruhan mengikuti keyakinan, bukan keinginan membalas."
                "DIRECTION" -> "Beri jawaban YA/TIDAK sesuai bukti tiap pertanyaan, bukan kebiasaan."
                "STATUS_QUO" -> "Tinjau harga jual tiap kuartal terhadap indeks biaya."
                else -> "Perhatikan pola keputusan berulang."
            }
        }
        if (lessons.isEmpty()) lessons += if (victory) "Pertahankan disiplin: kas, leverage, dan etika yang terjaga membawa Anda sampai akhir." else "Tidak ada satu penyebab dominan; tinjau Kronik Dunia untuk rangkaian kejadian."
        return PostMortem(headline, ranked.map { it.text }, tps, dq, lessons.distinct())
    }
}
