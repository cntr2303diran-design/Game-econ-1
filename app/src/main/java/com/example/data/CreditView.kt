package com.example.data

import com.example.engine.AppStatus
import com.example.engine.BankKind
import com.example.engine.KapitalEngine
import com.example.engine.KapitalEngine.Companion.dateText
import com.example.model.*

/** Tampilan meja kredit dan daftar pelaku (bot egois) yang bisa dibaca pemain. */
internal object CreditView {
    private fun f(v: Double) = KapitalEngine.fmt(v)
    private fun f1(v: Double) = KapitalEngine.fmt1(v)
    private fun f2(v: Double) = KapitalEngine.fmt2(v)

    fun credit(e: KapitalEngine): CreditViewState {
        val m = e.creditMetrics()
        val icr = m.ebit12 / maxOf(1.0, m.interest12)
        val lev = (m.debt - m.cash) / maxOf(1.0, m.ebitda12)
        val cr = m.currentAssets / maxOf(1.0, m.currentLiabilities)
        val ratios = listOf(
            "EBIT 12 bulan" to "${f(m.ebit12)} jt RS",
            "EBITDA 12 bulan" to "${f(m.ebitda12)} jt RS",
            "Beban bunga 12 bulan" to "${f(m.interest12)} jt RS",
            "ICR (EBIT / bunga)" to "${f2(icr)}x",
            "Utang bersih / EBITDA" to "${f2(lev)}x",
            "Rasio lancar" to "${f2(cr)}x",
            "Kas dalam bulan biaya" to "${f1(m.cash / maxOf(1.0, m.monthlyCashCost))} bulan",
            "DSO (umur piutang)" to "${f1(m.dso)} hari",
            "Gagal covenant berturut-turut" to "${m.covenantBreaches} kuartal",
            "Pengajuan ditolak 6 bulan terakhir" to "${e.credit.recentRejections(e.turn)} (tercatat di biro kredit)"
        )
        val fa = 0.55 * e.fixedAssets * (if (e.deferredMonths >= 3) 0.9 else 1.0)
        val collateral = listOf(
            "Pabrik dan mesin (haircut 45%, lebih besar bila perawatan tertunda)" to "${f(fa)} jt RS",
            "Piutang lancar (haircut 30%)" to "${f(0.70 * e.normalAR)} jt RS",
            "Persediaan (haircut 50%)" to "${f(0.50 * e.inventory)} jt RS",
            "Portofolio saham (haircut 50%)" to "${f(0.50 * e.portfolioValue())} jt RS",
            "Dikurangi utang yang sudah berjalan" to "-${f(e.totalDebt())} jt RS",
            "Agunan bebas yang bisa dijaminkan" to "${f(m.freeCollateral)} jt RS"
        )
        val busy = e.credit.active()
        val banks = e.credit.banks.map { b ->
            val rel = e.credit.relationship[b.id] ?: 0.0
            val hasHistory = e.credit.applications.any { it.bankId == b.id && it.status != AppStatus.PROCESSING }
            val block = when {
                busy.any { it.bankId == b.id } -> "Masih ada pengajuan aktif di bank ini"
                busy.size >= 3 -> "Maksimal 3 pengajuan aktif sekaligus"
                e.ap < 2 -> "Butuh 2 poin aksi"
                else -> ""
            }
            val base = e.credit.baseRate(b, e.policyRate)
            BankItem(
                id = b.id, name = b.name, banker = b.banker, bankerRole = b.bankerRole,
                style = b.style, incentive = b.incentive, tell = b.tell, tellKnown = hasHistory,
                relationText = when { rel >= 60 -> "Hubungan baik"; rel >= 35 -> "Hubungan cukup"; rel >= 15 -> "Hubungan dingin"; else -> "Belum dikenal" } + " (${rel.toInt()}/100)",
                relationTone = when { rel >= 60 -> SemanticTone.GOOD_GREEN; rel >= 35 -> SemanticTone.PRIMARY_BLUE; else -> SemanticTone.NEUTRAL_GRAY },
                rateText = if (b.foreign) "≈${f2(base)}% dalam A\$ + premi risiko (kurs ditanggung Anda)" else "≈${f2(base)}% + premi risiko, biaya ${f2(b.feePct)}%",
                speedText = when (b.reviewTurns) { 1 -> "Keputusan ±1 giliran"; else -> "Keputusan ±${b.reviewTurns} giliran" },
                maxTenor = b.maxTenor,
                maxAmountText = b.maxAmount?.let { "maks ${f(it)} jt RS" } ?: "plafon mengikuti agunan dan EBITDA",
                foreign = b.foreign,
                canApply = block.isEmpty(), blockReason = block
            )
        }
        val apps = e.credit.applications.reversed().map { a ->
            val b = e.credit.bank(a.bankId)!!
            val o = a.offer
            val offer = if (o != null && (a.status == AppStatus.OFFER || a.status == AppStatus.ACCEPTED || a.status == AppStatus.DISBURSED)) OfferItem(
                amountText = "${f(o.amount)} jt RS" + if (b.foreign) " (≈${f2(o.amount / e.kurs)} juta A\$)" else "",
                rateText = "${f2(o.ratePct)}% " + (if (o.spreadPct != null) "mengambang (acuan + ${f2(o.spreadPct)}%)" else "tetap"),
                tenorText = "${o.tenor} bulan" + if (o.graceMonths > 0) ", masa tenggang ${o.graceMonths} bulan" else "",
                feeText = "Provisi ${f2(o.feePct)}% = ${f(o.amount * o.feePct / 100.0)} jt RS",
                covenantText = if (o.covenantIcr > 0) "ICR ≥ ${f2(o.covenantIcr)}x, diuji kuartalan; berlaku silang dengan semua pinjaman Anda" else "Tidak ada covenant, tetapi bank dapat menarik pinjaman",
                structureText = if (o.bullet) "Bullet: pokok dilunasi sekaligus saat jatuh tempo" else "Cicilan pokok merata",
                extraTerms = o.extraTerms,
                hiddenTerms = if (a.reviewed || a.status == AppStatus.DISBURSED) o.hiddenTerms else emptyList(),
                hiddenKnown = a.reviewed || a.status == AppStatus.DISBURSED,
                validText = if (a.status == AppStatus.OFFER) "Berlaku sampai ${dateText(a.offerExpiresTurn - 1)}" else ""
            ) else null
            val (label, tone) = when (a.status) {
                AppStatus.PROCESSING -> "Diproses" to SemanticTone.PRIMARY_BLUE
                AppStatus.OFFER -> "Penawaran" to SemanticTone.GOOD_GREEN
                AppStatus.REJECTED -> "Ditolak" to SemanticTone.DANGER_RED
                AppStatus.ACCEPTED -> "Diterima" to SemanticTone.GOOD_GREEN
                AppStatus.DISBURSED -> "Cair" to SemanticTone.GOOD_GREEN
                AppStatus.DECLINED -> "Anda tolak" to SemanticTone.NEUTRAL_GRAY
                AppStatus.EXPIRED -> "Kedaluwarsa" to SemanticTone.NEUTRAL_GRAY
            }
            CreditAppItem(
                id = a.id, bankName = b.name, banker = b.banker,
                amountText = "${f(a.amount)} jt RS · ${a.tenor} bulan · ${if (a.floating) "bunga mengambang" else "bunga tetap"}${if (a.bullet) " · bullet" else ""}",
                purposeText = a.purpose.label, statusLabel = label, statusTone = tone, stageText = a.stageText, offer = offer,
                officialReason = a.officialReason, informalNote = a.informalNote,
                canReview = a.status == AppStatus.OFFER && !a.reviewed && e.ap >= 1,
                canAccept = a.status == AppStatus.OFFER && e.ap >= 1,
                canDecline = a.status == AppStatus.OFFER
            )
        }
        val loans = e.loans.filter { it.balance > 0.5 }.map { l ->
            val flags = mutableListOf<String>()
            if (l.currencyAud) flags += "Dalam A\$"
            if (l.macClause) flags += if (l.macTriggered) "MAC sudah dipakai bank" else "Ada klausul MAC"
            if (l.stepUpTurn > e.turn) flags += "Bunga naik di ${dateText(l.stepUpTurn)}"
            if (l.dividendLock) flags += "Dividen dibatasi"
            if (l.callable) flags += "Bisa ditarik mendadak"
            if (l.bullet) flags += "Bullet"
            if (l.floatingSpreadPct != null) flags += "Mengambang"
            LoanItem(
                lender = l.lender,
                balanceText = if (l.currencyAud) "${f2(l.balance)} juta A\$ (≈${f(l.balance * e.kurs)} jt RS)" else "${f(l.balance)} jt RS",
                rateText = "${f2(l.annualRatePct)}%",
                maturityText = if (l.maturityTurn >= 999) "tanpa tenor tetap" else "jatuh tempo ${dateText(l.maturityTurn)}",
                repaymentText = if (l.amortPerMonth > 0) "cicilan pokok ${f(l.amortPerMonth * (if (l.currencyAud) e.kurs else 1.0))} jt RS/bulan" else "pokok tidak dicicil (bullet atau revolving)",
                covenantText = if (l.covenantIcr > 0) "ICR ≥ ${f2(l.covenantIcr)}x" else "tanpa covenant",
                flags = flags
            )
        }
        return CreditViewState(ratios, collateral, banks, apps, loans,
            "Bank melihat angka yang sama dengan yang bisa Anda hitung sendiri, tetapi skor internalnya tidak ditampilkan. Pengajuan memakan 2 poin aksi dan biaya persiapan 30 jt RS. Penolakan tercatat di biro kredit dan menurunkan peluang di bank lain selama 6 bulan.")
    }

    fun actors(e: KapitalEngine, executives: List<Executive>, closed: String): List<ActorItem> {
        val out = mutableListOf<ActorItem>()
        val turn = e.turn
        // ---- Di dalam perusahaan ----
        for (x in executives) {
            val goal = when (x.id) {
                "exec_sales" -> "Bonus penjualan dan karier. Jika bonusnya dari omzet, ia mengobral kredit ke pelanggan rapuh."
                "exec_factory" -> "Biaya kuartal tampak hemat dan aman dari pemecatan; perawatan mesin bisa ia tunda."
                "exec_coo" -> "Efisiensi, ketepatan kirim, dan komisi dari pemasok yang ia pilih."
                else -> "Stabilitas likuiditas dan audit bersih; paling selaras dengan Anda tetapi tetap punya kepentingan karier."
            }
            out += ActorItem(
                id = x.id, group = "Di dalam perusahaan", name = x.name, role = x.roleTitle,
                goal = goal,
                lastAction = if (x.lastOutcome.isNotEmpty()) x.lastOutcome else "Tidak ada kejadian khusus bulan ini.",
                signals = listOfNotNull(
                    "Insentif saat ini: ${x.incentiveScheme}",
                    x.signalNote.takeIf { it.isNotBlank() },
                    "Status audit: ${x.auditStatus}"
                ),
                advice = "Audit membutuhkan 2 AP dan akurasinya sekitar 85%. Tanpa bukti, konfrontasi bisa salah sasaran.",
                tone = x.loyaltyTone
            )
        }
        // ---- Bank ----
        for (b in e.credit.banks) {
            val last = e.credit.applications.lastOrNull { it.bankId == b.id }
            val rel = e.credit.relationship[b.id] ?: 0.0
            out += ActorItem(
                id = b.id, group = "Bank dan pemberi dana", name = b.banker, role = "${b.bankerRole} · ${b.name}",
                goal = b.incentive,
                lastAction = when (last?.status) {
                    null -> "Belum pernah berurusan dengan Anda."
                    AppStatus.REJECTED -> "Menolak pengajuan ${f(last.amount)} jt RS: ${last.officialReason}"
                    AppStatus.OFFER -> "Mengajukan penawaran ${f(last.offer?.amount ?: 0.0)} jt RS; menunggu jawaban Anda."
                    AppStatus.PROCESSING -> last.stageText
                    AppStatus.DISBURSED -> "Mencairkan ${f(last.offer?.amount ?: 0.0)} jt RS."
                    else -> last.stageText
                },
                signals = listOf(
                    if (turn % 3 == 2) "Akhir kuartal: bankir mengejar target; peluang disetujui lebih besar." else if (turn % 3 == 0) "Awal kuartal: bankir paling selektif." else "Pertengahan kuartal.",
                    "Hubungan dengan Anda ${rel.toInt()}/100",
                    if (e.credit.applications.any { it.bankId == b.id && it.status != AppStatus.PROCESSING }) b.tell else "Pola perilaku belum terbaca; ajukan pinjaman untuk mengenalnya."
                ),
                advice = "Bandingkan beberapa bank, tinjau klausul dengan penasihat hukum, dan jangan mengajukan ke banyak bank sekaligus saat kondisi lemah.",
                tone = if (b.kind == BankKind.SHADOW) SemanticTone.WARNING_YELLOW else SemanticTone.PRIMARY_BLUE
            )
        }
        // ---- Pesaing ----
        val arche = mapOf(
            "riv_mitra" to ("Raksasa konservatif" to "Lambat, kaya kas; membeli pemain yang terlihat lemah."),
            "riv_cahaya" to ("Startup agresif" to "Membakar uang demi pangsa pasar; menyamai potongan harga Anda."),
            "riv_prima" to ("Pemain kroni" to "Bergantung pada kedekatan dengan pejabat; biaya tertinggi."),
            "riv_importir" to ("Perampas harga" to "Membanting harga untuk mengusir pemain lain; paling cepat membalas.")
        )
        val myPrice = (1.0 + e.priceAdjPct / 100.0)
        for (rv in e.world.rivals) {
            val (type, goal) = arche[rv.id] ?: ("Pesaing" to "Memaksimalkan keuntungan.")
            val dump = rv.id == "riv_importir" && e.world.dumpingMonthsLeft > 0
            out += ActorItem(
                id = rv.id, group = "Pesaing", name = rv.name, role = "Pesaing: $type",
                goal = goal,
                lastAction = when {
                    !rv.active -> "Sudah keluar pasar."
                    dump -> "Membanting harga (dumping) ${e.world.dumpingMonthsLeft} bulan lagi."
                    rv.price < 0.97 -> "Menurunkan harga ke ${f1(rv.price * 100)}% dari harga awal."
                    rv.price > 1.05 -> "Menaikkan harga ke ${f1(rv.price * 100)}% dari harga awal."
                    else -> "Harga stabil sekitar ${f1(rv.price * 100)}% dari harga awal."
                },
                signals = listOf(
                    "Harga mereka ${f1(rv.price * 100)} vs harga Anda ${f1(myPrice * e.fxPassLevel() * 100)}",
                    if (rv.weakMonths > 0) "Menjual di bawah biaya ${rv.weakMonths} bulan (rawan bangkrut)" else "Margin sehat",
                    "Pangsa awal ${f1(rv.share0 * 100)}%"
                ),
                advice = "Pesaing yang agresif membalas potongan harga dalam beberapa bulan. Periksa saham dan laporan mereka di tab Bursa.",
                tone = if (!rv.active) SemanticTone.NEUTRAL_GRAY else if (dump) SemanticTone.DANGER_RED else SemanticTone.PRIMARY_BLUE
            )
        }
        // ---- Pelanggan, pemasok, serikat ----
        val mhkt = e.bourse.company("sx_mahkota")
        out += ActorItem(
            id = "act_mahkota", group = "Pelanggan dan pemasok", name = "Grup Mahkota Ritel", role = "Pelanggan ritel terbesar",
            goal = "Harga rendah dan termin bayar panjang; memindahkan risiko arus kas ke pemasok.",
            lastAction = "Termin pembayaran kontrak saat ini mengikat Anda; DSO Anda ${f1(e.dso())} hari.",
            signals = listOfNotNull(
                mhkt?.let { "Harga saham MHKT ${f(it.price)} RS; laba 12 bulan ${f(it.ttmNi())} jt RS" },
                "Pelanggan rapuh yang diobral kredit berisiko macet 6 bulan kemudian."
            ),
            advice = "Pantau laporan keuangan Mahkota di tab Bursa sebelum memperpanjang termin.",
            tone = SemanticTone.PRIMARY_BLUE
        )
        out += ActorItem(
            id = "act_kestrel", group = "Pelanggan dan pemasok", name = "Kestrel Precision Loom Parts", role = "Pemasok suku cadang dan mesin",
            goal = "Margin maksimal; saat Anda bergantung padanya ia menaikkan harga atau menahan kiriman (hold-up).",
            lastAction = "Indeks baja ${f2(e.commodities.steel.index)}; kapas ${f2(e.commodities.cotton.index)}.",
            signals = listOf("Ketergantungan pada satu pemasok meningkatkan risiko hold-up.", if (e.dualSourcing) "Dual sourcing aktif." else "Belum ada pemasok kedua."),
            advice = "Dual sourcing dan kontrak berjangka menurunkan daya tawar mereka.",
            tone = SemanticTone.WARNING_YELLOW
        )
        out += ActorItem(
            id = "act_serikat", group = "Pekerja", name = "Serikat Pekerja Tekstil Kalimas", role = "Serikat pekerja",
            goal = "Upah riil dan keamanan kerja bagi 340 pekerja.",
            lastAction = when {
                e.strikeMonthsLeft > 0 -> "Sedang mogok (${e.strikeMonthsLeft} bulan lagi)."
                e.unionResolved -> "Tuntutan terakhir sudah diselesaikan."
                else -> "Menuntut kenaikan upah mengikuti harga beras; tenggat ${dateText(e.unionDeadlineTurn)}."
            },
            signals = listOf("Inflasi ${f1(e.inflationAnnual)}% per tahun", "Pengangguran ${f1(e.world.unemployment)}% (makin tinggi, daya tawar serikat turun)"),
            advice = "Tuntutan yang diabaikan saat inflasi tinggi berujung mogok tepat ketika kontrak pengiriman berjalan.",
            tone = if (e.strikeMonthsLeft > 0) SemanticTone.DANGER_RED else SemanticTone.WARNING_YELLOW
        )
        // ---- Negara dan pasar ----
        out += ActorItem(
            id = "act_bank_sentral", group = "Negara dan pasar", name = "Bank Sentral Sundara", role = "Otoritas moneter (aturan Taylor, kepribadian politis)",
            goal = "Inflasi 3%, lapangan kerja, stabilitas kurs; mendekati pemilu cenderung menahan bunga.",
            lastAction = "Suku bunga acuan ${f2(e.policyRate)}%; aturan Taylor menyarankan ${f2(e.world.taylorStar)}%.",
            signals = listOf("Inflasi ${f1(e.inflationAnnual)}%", "Pengangguran ${f1(e.world.unemployment)}%", "Pemilu presiden ${dateText(e.electionTurn)}"),
            advice = "Kenaikan bunga memukul kurs dalam 1–2 giliran, kredit dalam 3–6, permintaan dalam 6–12.",
            tone = SemanticTone.PRIMARY_BLUE
        )
        val idx = e.bourse.index
        out += ActorItem(
            id = "act_investor", group = "Negara dan pasar", name = "Investor Bursa Efek Sundara", role = "Pasar saham (investor ritel dan institusi)",
            goal = "Keuntungan modal cepat; bereaksi berlebihan pada berita dan laporan triwulan.",
            lastAction = "Indeks BES ${f(idx)} (awal 1.000).",
            signals = e.bourse.lastEvents.take(3).ifEmpty { listOf("Tidak ada rilis laporan bulan ini.") },
            advice = "Beli karena laporan keuangan dan valuasi, bukan karena harga naik. Dividen dibayar tiap April.",
            tone = SemanticTone.NEUTRAL_GRAY
        )
        return out
    }
}
