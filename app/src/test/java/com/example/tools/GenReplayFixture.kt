package com.example.tools

import com.example.ActiveBot
import com.example.ReplayDigest
import com.example.ReplayFixture
import com.example.data.ENGINE_VERSION
import com.example.engine.Action
import com.example.engine.KapitalEngine
import java.io.File

/**
 * Membangkitkan app/src/androidTest/assets/replay_fixture.txt: satu kampanye penuh yang dimainkan bot aktif (anak usaha, IPO,
 * bank grup, suksesi, karbon) sampai akhir kampanye, direkam sebagai log aksi, lalu diputar ulang untuk mencatat ringkasan.
 * Jalankan lewat tools/gen-replay-fixture.sh. Bangkitkan ulang setiap kali ENGINE_VERSION dinaikkan.
 */
fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "replay_fixture.txt" })
    val endTurn = 481 // sama dengan CEO_CHALLENGE_HORIZON + 1: kampanye selesai
    for (k in 1..400) {
        val seed = 7919L * k
        val eng = KapitalEngine(seed)
        val rec = mutableListOf<String>()
        var t = 0
        while (eng.gameOverReason == null && eng.turn < endTurn) {
            t++
            ActiveBot.step(eng, t, rec)
            if (eng.gameOverReason != null) break
            if (eng.apply(Action.EndTurn)) rec += Action.EndTurn.encode()
        }
        if (eng.turn < endTurn) continue
        val direct = ReplayDigest.digest(eng)
        val fx = ReplayFixture(ENGINE_VERSION, seed, rec, emptyMap())
        val collected = fx.replay(collect = true)
        val finalCheck = collected.checks[-1]!!
        check(finalCheck.hash == direct) { "putar ulang dari log tidak sama dengan permainan langsung (seed $seed): encode/decode asimetris?" }
        check(collected.rejected == 0) { "ada ${collected.rejected} aksi ditolak saat diputar ulang (seed $seed)" }
        val full = ReplayFixture(ENGINE_VERSION, seed, rec, collected.checks)
        // pastikan berkas yang ditulis lolos verifikasinya sendiri
        val again = ReplayFixture.parse(full.render()).replay()
        check(again.mismatches.isEmpty()) { "berkas hasil tidak lolos verifikasi sendiri: ${again.mismatches}" }
        out.parentFile?.mkdirs()
        out.writeText(full.render())
        println("seed=$seed giliran akhir=${eng.turn} baris log=${rec.size} titik periksa=${collected.checks.size} ekuitas akhir=${eng.equity} putar ulang=${again.elapsedMs} ms -> ${out.path}")
        return
    }
    throw IllegalStateException("tidak ada seed bot aktif yang menamatkan kampanye dalam 400 percobaan")
}
