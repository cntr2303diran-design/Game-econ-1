package com.example.engine

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Meja kredit: proses pengajuan pinjaman seperti di bank sungguhan.
 *
 * Alur: pengajuan (credit memo + agunan) -> analisis dan komite (1-3 giliran, tergantung bank) ->
 * keputusan: DITOLAK, atau penawaran (kadang dipotong atau diberi syarat tambahan) -> pemain menerima atau menolak
 * -> pencairan. Penilaian memakai rasio yang sama yang bisa dihitung pemain sendiri (ICR, utang bersih/EBITDA,
 * likuiditas, nilai agunan setelah haircut, rekam jejak) ditambah hubungan dengan bank, berita pengajuan lain (biro kredit),
 * kondisi makro, dan kepentingan pribadi bankir (target volume akhir kuartal, fee).
 * Skor internal tidak pernah ditampilkan; pemain hanya melihat alasan resmi dan, bila hubungannya baik, catatan informal.
 */
enum class BankKind { COMMERCIAL, STATE, FOREIGN, SHADOW }

enum class Purpose(val label: String) {
    MODAL_KERJA("Modal kerja"),
    EKSPANSI("Belanja modal dan ekspansi"),
    PEMINTALAN("Proyek pabrik pemintalan"),
    REFINANCING("Refinancing utang lama")
}

enum class AppStatus { PROCESSING, OFFER, REJECTED, ACCEPTED, DECLINED, EXPIRED, DISBURSED }

class BankProfile(
    val id: String,
    val name: String,
    val banker: String,
    val bankerRole: String,
    val kind: BankKind,
    val baseSpread: Double,
    val minScore: Double,
    val reviewTurns: Int,
    val feePct: Double,
    /** Nilai agunan (setelah haircut) yang disyaratkan per 1 rupiah pinjaman. */
    val coverage: Double,
    /** Batas utang bersih/EBITDA yang masih diterima bank. */
    val leverageCap: Double,
    val maxTenor: Int,
    val maxAmount: Double?,
    val foreign: Boolean,
    val covenantIcr: Double,
    val style: String,
    val incentive: String,
    val tell: String
)

class Offer(
    val amount: Double,
    val ratePct: Double,
    val spreadPct: Double?,
    val tenor: Int,
    val graceMonths: Int,
    val bullet: Boolean,
    val feePct: Double,
    val covenantIcr: Double,
    val cut: Boolean,
    val extraTerms: List<String>,
    val hiddenTerms: List<String>,
    val macClause: Boolean,
    val stepUpPct: Double,
    val dividendLock: Boolean,
    val callable: Boolean
)

class Application(
    val id: String,
    val bankId: String,
    val amount: Double,
    val tenor: Int,
    val purpose: Purpose,
    val floating: Boolean,
    val bullet: Boolean,
    var submittedTurn: Int
) {
    var status = AppStatus.PROCESSING
    var decisionTurn = -1
    var delayedOnce = false
    var offer: Offer? = null
    var offerExpiresTurn = -1
    var disburseTurn = -1
    var reviewed = false
    var officialReason = ""
    var informalNote = ""
    var stageText = "Pengajuan diterima; analis kredit mulai menelaah."
}

class CreditMetrics(
    val ebit12: Double,
    val ebitda12: Double,
    val interest12: Double,
    val debt: Double,
    val cash: Double,
    val currentAssets: Double,
    val currentLiabilities: Double,
    val freeCollateral: Double,
    val covenantBreaches: Int,
    val dso: Double,
    val unemployment: Double,
    val policyRate: Double,
    val policyDelta6m: Double,
    val kursDepr6m: Double,
    val maintenanceDeferred: Boolean,
    val monthlyCashCost: Double = 14000.0,
    val annualPrincipalDue: Double = 0.0,
    val maintCapex12: Double = 0.0
)

class ScoreResult(val total: Double, val parts: Map<String, Double>, val weakest: String)

class CreditDesk {
    val banks = listOf(
        BankProfile("bk_niaga", "Bank Niaga Sundara", "Rudi Hartanto", "Relationship manager korporasi", BankKind.COMMERCIAL,
            3.8, 56.0, 2, 1.0, 1.25, 3.5, 60, null, false, 1.75,
            "Komersial, cepat, mengejar volume kredit.",
            "Bonus kuartalan dari volume kredit baru; paling longgar di akhir kuartal, paling ketat saat awal kuartal.",
            "Bila skor Anda pas-pasan, dokumen sering berisi klausul MAC dan bunga promosi yang naik setelah setahun."),
        BankProfile("bk_pembangunan", "Bank Pembangunan Sundara", "Ibu Sulastri", "Kepala cabang korporasi (BUMN)", BankKind.STATE,
            2.6, 62.0, 3, 0.5, 1.35, 3.0, 84, null, false, 1.5,
            "Milik negara, bunga paling murah, birokratis, komite kredit lambat.",
            "Dinilai dari kepatuhan prosedur dan kredit macet, bukan dari volume; takut disalahkan auditor negara.",
            "Menolak mentah bila ada pelanggaran covenant dua kuartal terakhir, seberapa pun bagus proyeknya."),
        BankProfile("bk_aurelia", "Aurelia Global CitiTrust", "Nathan Brooks", "Managing director pembiayaan pasar berkembang", BankKind.FOREIGN,
            1.9, 64.0, 3, 1.25, 1.2, 3.0, 60, null, true, 1.75,
            "Bank asing, pinjaman dalam A$ berbunga murah, dokumen tebal.",
            "Mengejar fee transaksi dan sindikasi; risiko kurs ditanggung peminjam, bukan bank.",
            "Hampir selalu menyelipkan klausul MAC; skor turun bila rial melemah cepat."),
        BankProfile("bk_dana", "Dana Cepat Nusantara", "Sistem penilai otomatis \"Kilat\"", "Pinjaman daring (shadow banking)", BankKind.SHADOW,
            13.0, 28.0, 1, 3.0, 0.3, 6.0, 12, 3000.0, false, 0.0,
            "Cair dalam 1 giliran, tanpa pengawasan ketat, sangat mahal.",
            "Margin tinggi dan portofolio jangka pendek; menarik dana saat pasar panik.",
            "Bisa menagih seluruh pinjaman mendadak ketika pengangguran atau suku bunga melonjak.")
    )

    val applications = mutableListOf<Application>()
    val relationship = mutableMapOf("bk_niaga" to 55.0, "bk_pembangunan" to 45.0, "bk_aurelia" to 20.0, "bk_dana" to 0.0)
    private val rejectionTurns = mutableListOf<Int>()
    private var nextId = 1

    fun bank(id: String): BankProfile? = banks.firstOrNull { it.id == id }
    fun app(id: String): Application? = applications.firstOrNull { it.id == id }
    fun active(): List<Application> = applications.filter { it.status == AppStatus.PROCESSING || it.status == AppStatus.OFFER }
    fun recentRejections(turn: Int): Int = rejectionTurns.count { turn - it <= 6 }

    fun submit(bankId: String, amount: Double, tenor: Int, purpose: Purpose, floating: Boolean, bullet: Boolean, turn: Int): Application? {
        val b = bank(bankId) ?: return null
        if (amount < 500.0 || tenor < 6) return null
        if (active().count { it.bankId == bankId } > 0) return null
        if (active().size >= 3) return null
        val a = Application("app_${nextId++}", bankId, amount, min(tenor, b.maxTenor), purpose, floating, bullet, turn)
        applications += a
        return a
    }

    private fun gauss(r: Random): Double {
        val u1 = max(r.nextDouble(), 1e-9)
        return sqrt(-2.0 * ln(u1)) * kotlin.math.cos(2.0 * Math.PI * r.nextDouble())
    }

    private fun interp(x: Double, pts: List<Pair<Double, Double>>): Double {
        if (x <= pts.first().first) return pts.first().second
        if (x >= pts.last().first) return pts.last().second
        for (i in 0 until pts.size - 1) {
            val (x0, y0) = pts[i]; val (x1, y1) = pts[i + 1]
            if (x in x0..x1) return y0 + (y1 - y0) * (x - x0) / (x1 - x0)
        }
        return pts.last().second
    }

    /** Skor 0-100 dari sudut pandang bank, dengan rasio pro forma setelah pinjaman baru. */
    fun score(b: BankProfile, m: CreditMetrics, amount: Double, rateGuess: Double, turn: Int, noise: Double, a0Bullet: Boolean = false): ScoreResult {
        val interestPF = m.interest12 + amount * rateGuess / 100.0
        val icr = m.ebit12 / max(1.0, interestPF)
        val netDebtPF = m.debt + amount - m.cash - (if (b.kind == BankKind.SHADOW) 0.0 else 0.0)
        val lev = netDebtPF / max(1.0, m.ebitda12)
        val dueSoon = m.currentLiabilities
        val currentRatio = m.currentAssets / max(1.0, dueSoon)
        val colRatio = m.freeCollateral / max(1.0, amount * b.coverage)
        val newService = if (a0Bullet) amount * rateGuess / 100.0 else amount * rateGuess / 100.0 + amount * 12.0 / max(12.0, 48.0)
        val dscr = (m.ebitda12 - m.maintCapex12) / max(1.0, m.interest12 + m.annualPrincipalDue + newService)
        val cashMonths = m.cash / max(1.0, m.monthlyCashCost)
        val dscrS = interp(dscr, listOf(1.0 to 0.0, 1.3 to 35.0, 1.6 to 65.0, 2.0 to 90.0, 2.5 to 100.0))
        val cashS = interp(cashMonths, listOf(0.3 to 0.0, 1.0 to 40.0, 2.0 to 75.0, 3.0 to 100.0))
        val cov = interp(icr, listOf(1.0 to 0.0, 1.5 to 25.0, 2.0 to 50.0, 2.5 to 72.0, 3.5 to 100.0))
        val levS = interp(lev, listOf(1.0 to 100.0, 2.0 to 85.0, 3.0 to 55.0, 4.0 to 25.0, 5.5 to 0.0))
        val liq0 = interp(currentRatio, listOf(0.7 to 0.0, 1.1 to 35.0, 1.6 to 75.0, 2.2 to 100.0))
        val liq = 0.5 * liq0 + 0.5 * cashS
        val col = interp(colRatio, listOf(0.5 to 0.0, 0.9 to 55.0, 1.2 to 85.0, 1.6 to 100.0))
        var track = 100.0 - 35.0 * m.covenantBreaches
        if (m.dso > 85.0) track -= 10.0
        if (m.maintenanceDeferred) track -= 8.0
        track = track.coerceIn(0.0, 100.0)
        var total = 0.20 * cov + 0.15 * dscrS + 0.20 * levS + 0.15 * liq + 0.20 * col + 0.10 * track
        if (m.unemployment > 6.5) total -= min(10.0, (m.unemployment - 6.5) * 3.0)
        if (m.policyDelta6m > 1.0) total -= 4.0
        if (b.foreign && m.kursDepr6m > 6.0) total -= min(8.0, m.kursDepr6m - 6.0)
        total += (relationship[b.id] ?: 0.0) * 0.08
        total -= min(12.0, 4.0 * recentRejections(turn))
        if ((b.kind == BankKind.COMMERCIAL || b.kind == BankKind.FOREIGN) && turn % 3 == 2) total += 3.0
        if (b.kind == BankKind.COMMERCIAL && turn % 3 == 0) total -= 2.0
        total += noise
        val parts = mapOf("cov" to cov, "dscr" to dscrS, "lev" to levS, "liq" to liq, "col" to col, "track" to track)
        val weakest = parts.minByOrNull { it.value }!!.key
        return ScoreResult(total.coerceIn(0.0, 100.0), parts, weakest)
    }

    private fun reasonFor(key: String, state: Boolean): String = when (key) {
        "cov" -> "Rasio penutupan bunga (ICR) tidak memenuhi kebijakan kredit kami."
        "lev" -> "Leverage setelah pinjaman ini melampaui batas risiko portofolio."
        "liq" -> "Proyeksi likuiditas jangka pendek dinilai belum memadai."
        "dscr" -> "Kemampuan membayar cicilan dan bunga (DSCR) dari arus kas operasi dinilai terlalu tipis."
        "col" -> "Nilai agunan setelah haircut tidak mencukupi untuk plafon yang diminta."
        else -> if (state) "Rekam jejak kepatuhan covenant belum memenuhi persyaratan penyaluran." else "Rekam jejak kredit dan kualitas piutang belum memenuhi kebijakan."
    }

    private fun informalFor(key: String, m: CreditMetrics, amount: Double, rate: Double): String {
        val icr = m.ebit12 / max(1.0, m.interest12 + amount * rate / 100.0)
        val lev = (m.debt + amount - m.cash) / max(1.0, m.ebitda12)
        return when (key) {
            "cov" -> "Secara informal: ICR pro forma Anda sekitar ${KapitalEngine.fmt2(icr)}x; komite ingin melihat di atas 2,5x."
            "lev" -> "Secara informal: utang bersih/EBITDA pro forma sekitar ${KapitalEngine.fmt2(lev)}x; komite nyaman di bawah 3x."
            "dscr" -> "Secara informal: arus kas operasi Anda hanya menutup cicilan plus bunga sekitar ${KapitalEngine.fmt2((m.ebitda12 - m.maintCapex12) / max(1.0, m.interest12 + m.annualPrincipalDue + amount * rate / 100.0))}x; komite ingin di atas 1,6x."
            "liq" -> "Secara informal: kewajiban jatuh tempo 12 bulan ke depan terlalu besar dibanding kas dan piutang Anda."
            "col" -> "Secara informal: agunan bebas Anda sekitar ${KapitalEngine.fmt(m.freeCollateral)} jt RS setelah haircut dan utang lama."
            else -> "Secara informal: riwayat covenant dan umur piutang Anda membuat komite ragu."
        }
    }

    /** Memproses semua pengajuan yang sedang berjalan; mengembalikan baris laporan (teks, nada). */
    fun process(turn: Int, policyRate: Double, metricsFor: (BankProfile, Double) -> CreditMetrics, r: Random): List<Pair<String, Tone>> {
        val out = mutableListOf<Pair<String, Tone>>()
        for (a in applications.toList()) {
            val b = bank(a.bankId) ?: continue
            when (a.status) {
                AppStatus.PROCESSING -> {
                    val elapsed = turn - a.submittedTurn
                    if (elapsed + 1 < b.reviewTurns) {
                        a.stageText = when {
                            elapsed == 0 -> "Analis menelaah laporan keuangan dan agunan Anda."
                            elapsed == 1 && b.reviewTurns >= 3 -> "Penilaian agunan oleh appraisal independen."
                            else -> "Menunggu rapat komite kredit."
                        }
                        continue
                    }
                    if (!a.delayedOnce && b.kind != BankKind.SHADOW && r.nextDouble() < (if (b.kind == BankKind.STATE) 0.35 else 0.18)) {
                        a.delayedOnce = true
                        a.stageText = "Bank meminta dokumen tambahan (laporan teraudit, proyeksi arus kas). Keputusan mundur satu giliran."
                        out += "${b.name}: meminta dokumen tambahan untuk pengajuan Anda; keputusan mundur satu giliran." to Tone.WARN
                        a.submittedTurn += 1
                        continue
                    }
                    decide(a, b, turn, policyRate, metricsFor, r, out)
                }
                AppStatus.OFFER -> {
                    if (turn >= a.offerExpiresTurn) {
                        a.status = AppStatus.EXPIRED
                        a.stageText = "Penawaran kedaluwarsa karena tidak dijawab."
                        out += "Penawaran ${b.name} kedaluwarsa karena tidak Anda jawab." to Tone.INFO
                    }
                }
                else -> {}
            }
        }
        // hubungan bank pelan-pelan kembali ke netral
        for (k in relationship.keys.toList()) relationship[k] = ((relationship[k] ?: 0.0) * 0.995).coerceIn(0.0, 100.0)
        return out
    }

    private fun decide(a: Application, b: BankProfile, turn: Int, policyRate: Double,
                       metricsFor: (BankProfile, Double) -> CreditMetrics, r: Random, out: MutableList<Pair<String, Tone>>) {
        val m = metricsFor(b, a.amount)
        val rateGuess = baseRate(b, policyRate) + 1.0
        val sr = score(b, m, a.amount, rateGuess, turn, gauss(r) * 3.0, a.bullet)
        a.decisionTurn = turn
        // penolakan keras bank negara bila ada pelanggaran covenant baru-baru ini
        val hardNo = b.kind == BankKind.STATE && m.covenantBreaches >= 2
        // batas plafon
        val levCap = b.leverageCap * m.ebitda12 - m.debt + m.cash
        val colCap = m.freeCollateral / b.coverage
        var cap = min(a.amount, min(levCap, colCap))
        b.maxAmount?.let { cap = min(cap, it) }
        cap = max(0.0, cap)
        if (hardNo || sr.total < b.minScore || cap < 0.3 * a.amount) {
            a.status = AppStatus.REJECTED
            val key = if (hardNo) "track" else if (cap < 0.3 * a.amount && sr.total >= b.minScore) (if (colCap < levCap) "col" else "lev") else sr.weakest
            a.officialReason = if (hardNo) "Pembiayaan ditunda: pelanggaran covenant terbaru belum dipulihkan." else reasonFor(key, b.kind == BankKind.STATE)
            val rel = relationship[b.id] ?: 0.0
            a.informalNote = if (rel >= 35.0) informalFor(key, m, a.amount, rateGuess)
                else "Tidak ada catatan tambahan dari ${b.banker}."
            a.stageText = "Ditolak."
            rejectionTurns += turn
            relationship[b.id] = max(0.0, (relationship[b.id] ?: 0.0) - 4.0)
            out += "${b.name} MENOLAK pengajuan kredit ${KapitalEngine.fmt(a.amount)} jt RS. Alasan resmi: ${a.officialReason}" to Tone.BAD
            return
        }
        val cut = cap < a.amount * 0.98
        val amount = if (cut) cap else a.amount
        val marginal = sr.total < b.minScore + 8.0
        val riskAdj = max(0.0, 80.0 - sr.total) * 0.045
        val base = baseRate(b, policyRate)
        var ratePct = base + riskAdj + (if (a.floating) 0.0 else 0.6) - (if (a.bullet) 0.3 else 0.0)
        var stepUp = 0.0
        val hidden = mutableListOf<String>()
        val extra = mutableListOf<String>()
        var mac = false; var divLock = false; var callable = false
        var covenant = b.covenantIcr
        when (b.kind) {
            BankKind.COMMERCIAL -> {
                if (sr.total < b.minScore + 15.0) { mac = true; hidden += "Klausul material adverse change (MAC): bank boleh menuntut pelunasan sebagian bila kinerja atau suku bunga memburuk." }
                if (r.nextDouble() < 0.5) {
                    stepUp = 2.5; ratePct -= 1.0
                    hidden += "Bunga promosi: spread turun 1% selama 12 bulan pertama, lalu naik 2,5% dari spread semula."
                    extra += "Bunga promosi tahun pertama"
                }
                if (marginal) { divLock = true; covenant += 0.25; extra += "Dividen dibatasi selama pinjaman berjalan"; extra += "Covenant diperketat" }
            }
            BankKind.STATE -> {
                divLock = true
                extra += "Dividen dibatasi selama pinjaman berjalan"
                hidden += "Cross-default: pelanggaran covenant pada pinjaman mana pun dianggap pelanggaran pada pinjaman ini."
                if (marginal) { covenant += 0.25; extra += "Covenant diperketat" }
            }
            BankKind.FOREIGN -> {
                mac = true
                hidden += "Klausul MAC: bank boleh menuntut pelunasan 30% pokok bila rial melemah tajam atau kinerja turun."
                hidden += "Biaya hedging dan risiko kurs sepenuhnya ditanggung peminjam; tidak ada fasilitas lindung nilai."
                extra += "Pinjaman dalam A\$ (risiko selisih kurs di tangan Anda)"
                if (marginal) { divLock = true; extra += "Dividen dibatasi selama pinjaman berjalan" }
            }
            BankKind.SHADOW -> {
                callable = true
                hidden += "Bank dapat menarik seluruh pinjaman dengan pemberitahuan 2 giliran saat pasar tertekan."
                extra += "Tenor pendek; cicilan mulai segera"
                ratePct = policyRate + 13.0 + riskAdj
            }
        }
        if (cut) extra += "Plafon dipotong dari ${KapitalEngine.fmt(a.amount)} menjadi ${KapitalEngine.fmt(amount)} jt RS"
        val grace = if (a.bullet || b.kind == BankKind.SHADOW) 0 else if (a.purpose == Purpose.EKSPANSI || a.purpose == Purpose.PEMINTALAN) 6 else 0
        val spread = if (a.floating && b.kind != BankKind.FOREIGN) ratePct - policyRate else null
        a.offer = Offer(amount, ratePct, spread, a.tenor, grace, a.bullet, b.feePct, covenant, cut, extra, hidden, mac, stepUp, divLock, callable)
        a.status = AppStatus.OFFER
        a.offerExpiresTurn = turn + 3
        a.stageText = if (cut || extra.isNotEmpty()) "Disetujui dengan syarat. Jawab sebelum ${3} giliran." else "Disetujui. Jawab sebelum 3 giliran."
        out += "${b.name} menyetujui pengajuan Anda${if (cut) " (dipotong menjadi ${KapitalEngine.fmt(amount)} jt RS)" else ""}: bunga ${KapitalEngine.fmt2(ratePct)}%, biaya ${KapitalEngine.fmt2(b.feePct)}%. Penawaran berlaku 3 giliran." to Tone.GOOD
    }

    fun baseRate(b: BankProfile, policyRate: Double): Double = when (b.kind) {
        BankKind.FOREIGN -> 3.3 + b.baseSpread
        BankKind.SHADOW -> policyRate + b.baseSpread
        else -> policyRate + b.baseSpread
    }

    fun gainRelationship(bankId: String, d: Double) {
        relationship[bankId] = ((relationship[bankId] ?: 0.0) + d).coerceIn(0.0, 100.0)
    }
}
