package com.example.ui

import com.example.ActiveBot
import com.example.RecklessBot
import com.example.TestBots
import com.example.data.ENGINE_VERSION
import com.example.engine.Action
import com.example.engine.KapitalEngine

/** Teks simpanan siap muat (SEED, VER, aksi) yang dibangkitkan bot uji, supaya UI diuji pada keadaan permainan yang nyata dan deterministik. */
object UiSaves {
    private fun stamp(text: String): String {
        val l = text.lines()
        return (listOf(l.first(), "VER:$ENGINE_VERSION") + l.drop(1)).joinToString("\n")
    }

    /** Bot disiplin, 30 bulan: Era 1, perusahaan tertutup. */
    val early: String by lazy {
        for (k in 1..60) {
            val seed = 7919L * k
            val s = TestBots.recordedSave(seed, KapitalEngine(seed).turn + 30) ?: continue
            return@lazy stamp(s)
        }
        throw AssertionError("tidak ada seed yang bertahan 30 bulan")
    }

    /** Bot aktif ±400 bulan: emiten (IPO), punya anak usaha, bank grup; keadaan terkaya yang bisa dicapai bot. */
    val late: String by lazy {
        for (k in 1..120) {
            val seed = 7919L * k
            val eng = KapitalEngine(seed)
            val rec = mutableListOf<String>()
            val t0 = eng.turn
            var t = 0
            while (eng.turn < t0 + 400 && eng.gameOverReason == null) {
                t++
                ActiveBot.step(eng, t, rec)
                if (eng.gameOverReason != null) break
                if (eng.apply(Action.EndTurn)) rec += Action.EndTurn.encode()
            }
            if (eng.gameOverReason == null && eng.isPublicCompany && eng.subsidiaries.isNotEmpty()) {
                return@lazy stamp((listOf("SEED:$seed") + rec).joinToString("\n"))
            }
        }
        throw AssertionError("tidak ada seed bot aktif yang menjadi emiten dengan anak usaha dalam 400 bulan")
    }

    /** Bot sembrono sampai permainan berakhir (kalah). */
    val lost: String by lazy {
        for (k in 1..200) {
            val seed = 7919L * k
            val eng = KapitalEngine(seed)
            val rec = mutableListOf<String>()
            var t = 0
            while (eng.gameOverReason == null && t < 600) {
                t++
                RecklessBot.step(eng, t, rec)
                if (eng.gameOverReason != null) break
                if (eng.apply(Action.EndTurn)) rec += Action.EndTurn.encode()
            }
            if (eng.gameOverReason != null) return@lazy stamp((listOf("SEED:$seed") + rec).joinToString("\n"))
        }
        throw AssertionError("tidak ada seed sembrono yang berakhir kalah")
    }

    /** Simpanan lama tanpa cap versi: harus memunculkan spanduk peringatan. */
    val legacy: String by lazy { early.lines().filter { !it.startsWith("VER:") }.joinToString("\n") }
}
