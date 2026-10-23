package com.example.engine

import kotlin.math.max
import kotlin.math.min

/**
 * B6: aktor yang belajar dari pola pemain. `PlayerProfile` adalah ringkasan perilaku pemain (rata-rata bergerak) yang
 * dibaca politisi, investor aktivis, dan eksekutif; `ActorMind` menyimpan ingatan mereka. Seluruhnya deterministik
 * (hanya fungsi dari riwayat tindakan dan aliran acak bertoken seed) sehingga dua gaya main pada seed yang sama memicu
 * perilaku bot yang berbeda: pembayar disukai pemeras, penolak dihukum, penimbun kas dan pembayar greenmail dikejar aktivis.
 */
/** Penyetelan keseimbangan (dipakai uji kalibrasi; nilai bawaan = permainan sebenarnya). */
object ActorTuning {
    var fineScale = 0.3
    var freqScale = 1.0
    var activistScale = 1.0
    var selfishScale = 1.0
}

class PlayerProfile {
    /** Bulan berperang harga (potong harga ≥5%), pelobi, korupsi (muatan etika), perilaku menyuap/mengalah, perlawanan, leverage, timbunan kas. 0..1. */
    var priceWar = 0.0; private set
    var lobbying = 0.0; private set
    var corruption = 0.0; private set
    var appeasement = 0.0; private set
    var defiance = 0.0; private set
    var leverage = 0.0; private set
    var hoard = 0.0; private set

    fun observe(priceAdjPct: Int, lobbyCount: Int, ethicsLoad: Double, leverageRatio: Double, cashShare: Double) {
        fun ema(old: Double, x: Double) = 0.92 * old + 0.08 * x.coerceIn(0.0, 1.0)
        priceWar = ema(priceWar, if (priceAdjPct <= -5) 1.0 else 0.0)
        lobbying = ema(lobbying, min(1.0, lobbyCount / 2.0))
        corruption = ema(corruption, ethicsLoad / 4.0)
        leverage = ema(leverage, leverageRatio / 4.0)
        hoard = ema(hoard, cashShare / 0.4)
        appeasement *= 0.995
        defiance *= 0.995
    }

    fun appease(x: Double) { appeasement = min(1.0, appeasement + x) }
    fun defy(x: Double) { defiance = min(1.0, defiance + x) }
}

class ActorMind {
    val profile = PlayerProfile()

    // Politisi
    var politicianAppetite = 0.25; private set
    var politicianGrudge = 0.0; private set
    var lastDemandTurn = -99
    var demands = 0; private set
    var paid = 0; private set
    var refused = 0; private set
    var reported = 0; private set
    var finesFromRefusals = 0.0; private set

    // Investor aktivis
    var activistEvents = 0; private set
    var lastActivistTurn = -99
    var greenmailPaid = 0; private set
    var proxyWins = 0; private set
    var proxyLosses = 0; private set
    var activistAccepted = 0; private set

    // Eksekutif egois
    var selfishActs = 0; private set

    /** Dipanggil saat efek kejadian "actor" diterapkan. */
    fun onChoice(code: String, fineRs: Double = 0.0) {
        when (code) {
            "pol_pay" -> { politicianAppetite = min(1.0, politicianAppetite + 0.2); politicianGrudge = max(0.0, politicianGrudge - 0.5); paid++; profile.appease(0.25) }
            "pol_refuse" -> { politicianAppetite = max(0.05, politicianAppetite - 0.08); politicianGrudge += 1.0; refused++; profile.defy(0.25); finesFromRefusals += fineRs }
            "pol_report" -> { politicianAppetite = max(0.05, politicianAppetite - 0.15); politicianGrudge += 2.0; reported++; profile.defy(0.35) }
            "act_accept" -> { activistAccepted++; profile.appease(0.1) }
            "act_greenmail" -> { greenmailPaid++; profile.appease(0.35) }
            "act_win" -> { proxyWins++; profile.defy(0.25) }
            "act_lose" -> { proxyLosses++; profile.defy(0.1) }
        }
    }

    /** Dendam memudar perlahan (separuh dalam sekitar 23 bulan); nafsu kembali ke dasar 0,25. */
    fun decayGrudge() { politicianGrudge *= 0.97; politicianAppetite += 0.01 * (0.25 - politicianAppetite) }

    fun onDemandRaised(turn: Int) { demands++; lastDemandTurn = turn }
    fun onActivistRaised(turn: Int) { activistEvents++; lastActivistTurn = turn }
    fun onSelfish() { selfishActs++ }
}
