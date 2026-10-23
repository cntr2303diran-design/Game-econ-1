package com.example

import com.example.engine.*

/** Pemain bot disiplin untuk menuntun permainan ke era yang diinginkan dalam uji. */
object TestBots {
    fun disciplinedStep(eng: KapitalEngine, t: Int, rec: MutableList<String>? = null) {
        fun apply(a: Action): Boolean { val ok = eng.apply(a); if (ok) rec?.add(a.encode()); return ok }
        if (t == 1) {
            apply(Action.SetSalesScheme(SalesScheme.KAS)); apply(Action.SetFactoryScheme(FactoryScheme.ANDAL))
            apply(Action.Journal("dec_termsheet", "cnt_ts_1", 75, 5500.0, "Refinancing Bank B", "Kas terjaga", "Bunga tetap"))
        }
        if (t == 2) { apply(Action.ReviewContract("cnt_factor")); apply(Action.DualSourcing) }
        if (t % 4 == 1) apply(Action.HedgeCotton)
        for (ev in eng.openEvents.toList()) {
            val o = ev.options.firstOrNull { it.id == "b" || it.id == "a" } ?: ev.options.first()
            apply(Action.EventChoice(ev.id, o.id))
        }
        if (eng.covenantDistancePct() < 4.5 && !eng.cureUsed) apply(Action.EquityCure)
        // B3: pemain cakap mengisi jabatan kunci yang kosong (dibajak pesaing) dengan perekrutan tingkat 2.
        for (role in StaffRole.values()) if (eng.staffBlock(role.code, "hire") == null) apply(Action.HireStaff(role.code, 2))
    }

    /** Bermain disiplin sampai giliran absolut [targetTurn]; null bila permainan berakhir sebelum itu. */
    fun playUntil(seed: Long, targetTurn: Int): KapitalEngine? {
        val eng = KapitalEngine(seed)
        var t = 0
        while (eng.turn < targetTurn) {
            if (eng.gameOverReason != null) return null
            t++
            disciplinedStep(eng, t)
            eng.apply(Action.EndTurn)
        }
        return if (eng.gameOverReason == null) eng else null
    }

    /** Teks simpanan (SEED + aksi) dari permainan disiplin sampai [targetTurn], untuk menguji GameRepository; null bila gagal. */
    fun recordedSave(seed: Long, targetTurn: Int, accept: (KapitalEngine) -> Boolean = { true }): String? {
        val eng = KapitalEngine(seed)
        val rec = mutableListOf<String>()
        var t = 0
        while (eng.turn < targetTurn) {
            if (eng.gameOverReason != null) return null
            t++
            disciplinedStep(eng, t, rec)
            if (eng.apply(Action.EndTurn)) rec.add(Action.EndTurn.encode())
        }
        if (eng.gameOverReason != null || !accept(eng)) return null
        return (listOf("SEED:$seed") + rec).joinToString("\n")
    }

    /** Cari seed pertama yang bertahan sampai [targetTurn]. */
    fun firstSurvivor(targetTurn: Int, maxSeeds: Int = 80, accept: (KapitalEngine) -> Boolean = { true }): KapitalEngine {
        for (s in 1..maxSeeds) playUntil(s * 7919L, targetTurn)?.let { if (accept(it)) return it }
        throw AssertionError("tidak ada seed yang bertahan sampai giliran $targetTurn dalam $maxSeeds percobaan")
    }
}

/** Sakelar perilaku ActiveBot (dipakai eksperimen keseimbangan untuk mengukur sumbangan tiap kebiasaan). */
object ActiveBotTuning {
    var reserve = true
    var leverage = true
    var hedge = true
    var insure = true
    var pricing = true
    var reserveMult = 1.1
}

/**
 * Bot aktif (B9): bertindak seperti pemain cakap. Selain ekspansi (garis produksi, anak usaha, IPO, buyback, suksesi, karbon)
 * ia (1) menjaga kas minimum sebelum belanja non-produktif, (2) membatasi leverage untuk ekspansi berisiko (anak usaha, buyback) dan menerbitkan saham saat terlalu berutang,
 * (2b) menyesuaikan harga mengikuti utilisasi dan kas (temuan terbesar: harga tak pernah diubah = bias status quo),
 * (3) melindungi nilai (kapas, kurs, suku bunga) dan (4) mengasuransikan aset.
 */
object ActiveBot {
    /** Kas minimum: bagian dari penjualan bulanan; belanja non-produktif (anak usaha, IPO, buyback, bank, karbon, lindung nilai) hanya dari kas di atas ini. Kapasitas produksi dikecualikan: mengekangnya terbukti memperbanyak kematian Era 1. */
    fun reserveRs(e: KapitalEngine): Double = ActiveBotTuning.reserveMult * (e.lastIncome["revenue"] ?: e.sc.baseRevenue)

    fun leverage(e: KapitalEngine): Double = e.totalDebt() / maxOf(1.0, e.equity)

    fun step(e: KapitalEngine, t: Int) {
        TestBots.disciplinedStep(e, t)
        if (e.gameOverReason != null) return
        val T = ActiveBotTuning
        val era = KapitalEngine.era(e.turn)
        val n = e.sc.size
        val spare = if (T.reserve) e.cash - reserveRs(e) else e.cash
        if (T.pricing) {
            val util = e.utilization()
            val rev = e.lastIncome["revenue"] ?: e.sc.baseRevenue
            if (e.cash < 0.25 * rev && e.priceAdjPct < 12) e.apply(Action.AdjustPrice(3))
            else if (t % 3 == 0 && util > 0.92 && e.priceAdjPct < 10) e.apply(Action.AdjustPrice(2))
            else if (t % 3 == 0 && util < 0.70 && e.priceAdjPct > -6) e.apply(Action.AdjustPrice(-2))
        }
        val levOk = !T.leverage || (leverage(e) < 1.2 && e.covenantDistancePct() > 15.0)

        // kapasitas dan produktivitas tak dikekang leverage atau cadangan kas (terbukti memperbanyak kematian Era 1)
        if (e.utilization() > 0.97 && e.extraLines < 3 && e.cash > 18000.0 * n) e.apply(Action.Invest("line"))
        if (t % 24 == 7 && e.cash > 15000.0 * n) e.apply(Action.Invest(listOf("automation", "brand", "rnd")[(t / 24) % 3]))
        if (!e.esgCompliant && spare > 12000.0 * n) e.apply(Action.Invest("solar"))
        if (T.insure && e.insuranceLevel == 0 && t >= 5 && t % 4 == 1 && spare > 3000.0 * n) e.apply(Action.SetInsurance(1))
        if (T.hedge) {
            if (t % 6 == 2 && spare > 1000.0 * n && e.derivBlock("ctf", 50) == null) e.apply(Action.BuyDerivative("ctf", 50))
            if (e.subsidiaries.isNotEmpty() && t % 12 == 4 && spare > 1000.0 * n && e.derivBlock("fxo", 50) == null) e.apply(Action.BuyDerivative("fxo", 50))
        }
        if (era >= 2 && t % 12 == 3 && levOk) {
            for (c in listOf("tarsis", "kestrel")) if (e.cash - (if (T.reserve) reserveRs(e) else 0.0) > 30000.0 * n) e.apply(Action.OpenSubsidiary(c))
            if (e.fxHedgeActive.not() && e.subsidiaries.isNotEmpty()) e.apply(Action.HedgeFx)
            for (c in e.subsidiaries.keys) e.apply(Action.Repatriate(c))
        } else if (era >= 2 && t % 12 == 3) {
            if (e.fxHedgeActive.not() && e.subsidiaries.isNotEmpty()) e.apply(Action.HedgeFx)
            for (c in e.subsidiaries.keys) e.apply(Action.Repatriate(c))
        }
        if (era >= 3) {
            if (!e.isPublicCompany && e.ipoBlock() == null && spare > 20000.0 * n) e.apply(Action.ExecuteIpo(20, false))
            if (e.isPublicCompany && t % 12 == 9 && spare > 60000.0 * n && levOk) e.apply(Action.Buyback(2000))
            if (t % 12 == 5 && spare > 40000.0 * n) e.apply(Action.OpenCaptiveBank)
            // pemulihan leverage: emiten yang terlalu berutang menerbitkan saham baru
            if (T.leverage && e.isPublicCompany && leverage(e) > 2.0 && t % 12 == 11) e.apply(Action.IssueShares((0.25 * e.equity).toInt().coerceAtLeast(1000)))
        }
        if (era >= 4) {
            if (e.appointedSuccessorId == null) e.apply(Action.AppointSuccessor("exec_cfo"))
            if (e.carbonCredits < 5 && spare > 20000.0 * n) e.apply(Action.CarbonAction("buy", 5))
        }
    }
}

/**
 * C3: bot sembrono. Mewakili pemain yang mengabaikan peringatan: selalu memilih opsi paling berisiko pada kartu,
 * mengekspansi tanpa memeriksa kas atau covenant, menurunkan harga terus, memoles laporan tiap bulan, tanpa
 * lindung nilai, asuransi, auditor, ataupun suntikan modal. Dipakai hanya untuk mengukur keseimbangan: strategi
 * ceroboh harus jarang menang.
 */
object RecklessBot {
    fun step(e: KapitalEngine, t: Int) {
        if (e.gameOverReason != null) return
        val era = KapitalEngine.era(e.turn)
        val n = e.sc.size
        for (ev in e.openEvents.toList()) {
            val o = ev.options.lastOrNull() ?: continue
            e.apply(Action.EventChoice(ev.id, o.id))
        }
        if (e.priceAdjPct > -15) e.apply(Action.AdjustPrice(-3))
        if (e.cash > 6000.0 * n && e.extraLines < 3) e.apply(Action.Invest("line"))
        if (t % 12 == 5) e.apply(Action.WindowDress(50))
        if (era >= 2 && t % 6 == 3 && e.cash > 8000.0 * n) for (c in listOf("tarsis", "kestrel")) e.apply(Action.OpenSubsidiary(c))
        if (era >= 3) {
            if (!e.isPublicCompany && e.ipoBlock() == null) e.apply(Action.ExecuteIpo(30, true))
            if (e.isPublicCompany && e.cash > 10000.0 * n) e.apply(Action.Buyback((e.cash * 0.5).toInt().coerceAtLeast(1000)))
        }
    }
}
