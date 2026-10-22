package com.example.engine

/**
 * B5: forensik akuntansi. Riwayat bulanan laporan keuangan induk dan skor-M Beneish (8 variabel) yang dihitung
 * dari 12 bulan terakhir dibanding 12 bulan sebelumnya. Dipakai auditor eksternal dan analis penjual kosong;
 * pemain juga dapat membacanya untuk memeriksa laporannya sendiri.
 *
 * Murni deterministik: hanya fungsi dari angka laporan yang sudah dibukukan mesin.
 */
class ForensicBook {
    private class Row(
        val rev: Double, val cogs: Double, val sga: Double, val dep: Double, val ni: Double, val cfo: Double,
        val ar: Double, val assets: Double, val fa: Double, val liab: Double, val other: Double
    )

    private val rows = ArrayList<Row>()
    val months: Int get() = rows.size

    fun record(rev: Double, cogs: Double, sga: Double, dep: Double, ni: Double, cfo: Double,
               ar: Double, assets: Double, fa: Double, liab: Double, other: Double) {
        rows += Row(rev, cogs, sga, dep, ni, cfo, ar, assets, fa, liab, other)
        if (rows.size > 36) rows.removeAt(0)
    }

    /** Komponen skor-M, atau null bila riwayat belum mencapai 24 bulan. */
    fun components(): Map<String, Double>? {
        if (rows.size < 24) return null
        val cur = rows.subList(rows.size - 12, rows.size)
        val prev = rows.subList(rows.size - 24, rows.size - 12)
        fun sum(l: List<Row>, f: (Row) -> Double) = l.sumOf(f)
        val revC = sum(cur) { it.rev }; val revP = sum(prev) { it.rev }
        if (revC <= 1.0 || revP <= 1.0) return null
        val arC = cur.last().ar; val arP = prev.last().ar
        val gmC = (revC - sum(cur) { it.cogs }) / revC; val gmP = (revP - sum(prev) { it.cogs }) / revP
        val taC = cur.last().assets; val taP = prev.last().assets
        if (taC <= 1.0 || taP <= 1.0) return null
        // kualitas aset: porsi aset selain kas, piutang, persediaan, aset tetap (portofolio, investasi, goodwill)
        val aqC = cur.last().other / taC; val aqP = prev.last().other / taP
        val depC = sum(cur) { it.dep }; val depP = sum(prev) { it.dep }
        val depRateC = depC / (depC + cur.last().fa); val depRateP = depP / (depP + prev.last().fa)
        val sgaC = sum(cur) { it.sga } / revC; val sgaP = sum(prev) { it.sga } / revP
        val tata = (sum(cur) { it.ni } - sum(cur) { it.cfo }) / taC
        val lvC = cur.last().liab / taC; val lvP = prev.last().liab / taP
        fun ratio(a: Double, b: Double) = if (kotlin.math.abs(b) < 1e-9) 1.0 else (a / b).coerceIn(0.2, 5.0)
        return linkedMapOf(
            "DSRI" to ratio(arC / revC, arP / revP),
            "GMI" to ratio(gmP, gmC),
            "AQI" to ratio(1.0 - aqC.coerceIn(0.0, 1.0) + 0.0, 1.0 - aqP.coerceIn(0.0, 1.0) + 0.0),
            "SGI" to ratio(revC, revP),
            "DEPI" to ratio(depRateP, depRateC),
            "SGAI" to ratio(sgaC, sgaP),
            "TATA" to tata.coerceIn(-0.3, 0.3),
            "LVGI" to ratio(lvC, lvP)
        )
    }

    /** Skor-M Beneish (1999); lebih tinggi dari [THRESHOLD] menandai kemungkinan manipulasi laba. */
    fun mScore(): Double? {
        val c = components() ?: return null
        return -4.84 + 0.920 * c.getValue("DSRI") + 0.528 * c.getValue("GMI") + 0.404 * c.getValue("AQI") +
            0.892 * c.getValue("SGI") + 0.115 * c.getValue("DEPI") - 0.172 * c.getValue("SGAI") +
            4.679 * c.getValue("TATA") - 0.327 * c.getValue("LVGI")
    }

    companion object {
        /** Ambang asli Beneish -1,78 menandai 25% perusahaan jujur di simulasi ini, jadi dikalibrasi ulang ke persentil ke-92 permainan jujur (bot disiplin, 60 seed). */
        const val THRESHOLD = -1.0
    }
}
