package com.example

import com.example.engine.Action
import com.example.engine.KapitalEngine

/**
 * Satu kampanye penuh yang direkam (seed + log aksi) beserta ringkasan keadaan di sejumlah titik periksa. Diputar ulang di JVM
 * (uji unit) dan di ART (uji instrumented di emulator); keduanya harus menghasilkan ringkasan yang sama bit demi bit.
 *
 * Format berkas (app/src/androidTest/assets/replay_fixture.txt):
 *   #ENGINE_VERSION|n
 *   SEED:s
 *   <satu baris aksi per baris>
 *   #CHECK|giliran|hash-heks|kas|ekuitas      (giliran -1 = keadaan akhir)
 * Bangkitkan ulang dengan tools/gen-replay-fixture.sh setiap kali ENGINE_VERSION dinaikkan.
 */
class ReplayFixture(val engineVersion: Int, val seed: Long, val actions: List<String>, val checks: Map<Int, Check>) {
    class Check(val hash: Long, val cash: Double, val equity: Double)

    class Outcome(
        val checks: Map<Int, Check>,
        val mismatches: List<String>,
        val rejected: Int,
        val elapsedMs: Long,
        val finalTurn: Int,
        val gameOver: String?
    )

    /** Memutar ulang log. collect = true: catat ringkasan tiap giliran kelipatan 12 dan keadaan akhir (untuk membangkitkan berkas). */
    fun replay(collect: Boolean = false): Outcome {
        val t0 = System.nanoTime()
        val e = KapitalEngine(seed)
        val got = LinkedHashMap<Int, Check>()
        val mismatches = ArrayList<String>()
        var rejected = 0
        fun checkAt(key: Int) {
            val c = Check(ReplayDigest.digest(e), e.cash, e.equity)
            got[key] = c
            if (collect) return
            val want = checks[key] ?: return
            if (want.hash != c.hash) {
                mismatches += "giliran $key: ringkasan ${ReplayDigest.hex(c.hash)} != ${ReplayDigest.hex(want.hash)} (kas ${c.cash} vs ${want.cash}; ekuitas ${c.equity} vs ${want.equity})"
            }
        }
        for (line in actions) {
            val a = Action.decode(line)
            if (a == null) { rejected++; continue }
            if (!e.apply(a)) rejected++
            if (a === Action.EndTurn) {
                val turn = e.turn
                if (if (collect) turn % 12 == 0 else checks.containsKey(turn)) checkAt(turn)
            }
        }
        checkAt(-1)
        if (!collect) for (k in checks.keys) if (k !in got) mismatches += "titik periksa giliran $k tidak tercapai"
        return Outcome(got, mismatches, rejected, (System.nanoTime() - t0) / 1_000_000, e.turn, e.gameOverReason)
    }

    fun render(): String = buildString {
        append("#ENGINE_VERSION|").append(engineVersion).append('\n')
        append("SEED:").append(seed).append('\n')
        for (a in actions) append(a).append('\n')
        for ((k, c) in checks) append("#CHECK|").append(k).append('|').append(ReplayDigest.hex(c.hash)).append('|').append(c.cash).append('|').append(c.equity).append('\n')
    }

    companion object {
        fun parse(text: String): ReplayFixture {
            var version = -1
            var seed = 0L
            val actions = ArrayList<String>()
            val checks = LinkedHashMap<Int, Check>()
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                when {
                    line.isEmpty() -> {}
                    line.startsWith("#ENGINE_VERSION|") -> version = line.substringAfter('|').toInt()
                    line.startsWith("#CHECK|") -> {
                        val p = line.split('|')
                        checks[p[1].toInt()] = Check(java.lang.Long.parseUnsignedLong(p[2], 16), p[3].toDouble(), p[4].toDouble())
                    }
                    line.startsWith("#") -> {}
                    line.startsWith("SEED:") -> seed = line.substringAfter(':').toLong()
                    else -> actions += line
                }
            }
            return ReplayFixture(version, seed, actions, checks)
        }
    }
}
