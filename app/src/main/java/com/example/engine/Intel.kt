package com.example.engine

/**
 * Tahap B1: intelijen berbayar dan rapat dewan kuartalan.
 *
 * Riset berbayar memberi PERKIRAAN atas nilai yang tersembunyi dengan margin error yang menyusut menurut anggaran
 * (tingkat 1–3). Biayanya masuk beban usaha sekali jalan (oneOffOpex), jadi tidak ada uang yang tercipta.
 * Rapat dewan tiap tiga bulan mengajukan satu pertanyaan tajam (arah suatu metrik tiga bulan ke depan); jawaban
 * dinilai pada rapat berikutnya: tebakan benar menaikkan kepercayaan dewan, salah menurunkannya, "tidak tahu" netral
 * tetapi dewan tidak suka bila terlalu sering mengelak. Kepercayaan dewan tidak memberi uang, hanya ruang gerak
 * (kredit, suksesi, game over bila 0).
 */
enum class IntelKind(val code: String, val label: String) {
    SEKTOR("sektor", "Laporan sektor"),
    NEGARA("negara", "Laporan negara"),
    PESAING("pesaing", "Laporan pesaing"),
    SEGMEN("segmen", "Laporan segmen konsumen");

    companion object { fun of(code: String): IntelKind? = values().firstOrNull { it.code == code } }
}

/** Tingkat anggaran riset: biaya dasar (jt RS, dikali skala usaha dan indeks biaya) dan galat relatif 1 simpangan baku. */
object IntelTier {
    fun cost(tier: Int): Double = when (tier) { 1 -> 80.0; 2 -> 200.0; 3 -> 450.0; else -> 0.0 }
    fun errorSd(tier: Int): Double = when (tier) { 1 -> 0.25; 2 -> 0.12; 3 -> 0.06; else -> 1.0 }
}

class IntelReport(
    val id: Int,
    val kind: IntelKind,
    val subject: String,
    val subjectLabel: String,
    val metric: String,
    val turn: Int,
    val tier: Int,
    val costRs: Double,
    /** Perkiraan yang dilaporkan (sudah berderau). */
    val estimate: Double,
    /** Simpangan baku galat dalam satuan metrik. */
    val errorBand: Double,
    val unit: String
)

class BoardQuestion(
    val id: Int,
    val member: String,
    val text: String,
    val metric: String,
    val baseline: Double,
    val askedTurn: Int,
    val dueTurn: Int
) {
    var answer: String? = null
}

object Board {
    val CLAIMS = listOf("naik", "turun", "tetap", "abstain")
    val MEMBERS = listOf("Ketua Dewan (mantan bankir)", "Komisaris Independen (akuntan)", "Wakil Pemegang Saham Pengendali", "Komisaris Aktivis (dana investasi)")
    val METRICS = linkedMapOf(
        "ebit" to "laba operasi (EBIT) tiga bulan",
        "cash" to "kas perusahaan",
        "revenue" to "pendapatan bulanan"
    )

    /** Pita "tetap": perubahan kurang dari ±4% dianggap datar. */
    const val FLAT_BAND = 0.04

    fun classify(baseline: Double, now: Double): String {
        val denom = Math.max(Math.abs(baseline), 1.0)
        val ch = (now - baseline) / denom
        return when { ch > FLAT_BAND -> "naik"; ch < -FLAT_BAND -> "turun"; else -> "tetap" }
    }
}
