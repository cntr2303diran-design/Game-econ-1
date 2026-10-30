package com.example.engine

/**
 * C4: multiplayer asinkron berbasis seed bersama + log aksi.
 *
 * Tidak ada server: sebuah [Challenge] (seed, peran, panjang) dibagikan sebagai teks. Tiap pemain memainkan sendiri,
 * lalu mengirim [Submission] (log aksi + skor yang diklaim) sebagai teks. Siapa pun bisa memverifikasi dengan memutar
 * ulang log pada mesin deterministik ([ChallengeVerifier]); skor yang diklaim harus sama persis dengan hasil putar ulang,
 * jadi mengubah log atau menggelembungkan skor ditolak. Papan peringkat ([Leaderboard]) hanya menyimpan hasil terverifikasi.
 *
 * Batasan jujur: log aksi tidak menyembunyikan apa pun, jadi pemain yang melihat log pemain lain dapat menyalinnya
 * (kecurangan sosial tidak dicegah). Verifikasi hanya menjamin skor sesuai log, bukan bahwa pemain memainkannya sendiri.
 */
data class Challenge(
    val role: Role,
    val seed: Long,
    val horizon: Int,
    val difficulty: Difficulty = Difficulty.MANAJER,
    /** Versi mesin pembuat kode; null pada kode lama tanpa cap. Dunia dan skor hanya sebanding pada versi yang sama. */
    val engineVersion: Int? = ENGINE_VERSION
) {
    val id: String get() = "${role.code}-$seed-$horizon-${difficulty.name}"

    /** Dunia yang sama (peran, seed, panjang, kesulitan) terlepas dari cap versi mesin. */
    fun sameWorld(o: Challenge): Boolean = role == o.role && seed == o.seed && horizon == o.horizon && difficulty == o.difficulty

    fun encode(): String = "KAPITAL-TANTANGAN|1|${role.code}|$seed|$horizon|${difficulty.name}" + (engineVersion?.let { "|$it" } ?: "")

    companion object {
        fun decode(text: String): Challenge? {
            return try {
                val p = text.trim().split("|")
                if (p.size < 6 || p[0] != "KAPITAL-TANTANGAN" || p[1] != "1") return null
                val horizon = p[4].toInt()
                if (horizon !in 6..600) return null
                val role = Role.of(p[2]) ?: return null
                Challenge(role, p[3].toLong(), horizon, Difficulty.valueOf(p[5]), p.getOrNull(6)?.trim()?.toIntOrNull())
            } catch (e: Exception) { null }
        }
    }
}

data class Submission(
    val player: String,
    val challenge: Challenge,
    val log: List<String>,
    val claimedScore: Int,
    /** Versi mesin tempat log dimainkan; null pada hasil lama tanpa cap. */
    val engineVersion: Int? = ENGINE_VERSION
) {
    fun encode(): String = buildString {
        append("KAPITAL-HASIL|1|").append(safeName(player)).append('|').append(claimedScore)
        engineVersion?.let { append('|').append(it) }
        append('\n')
        append(challenge.encode())
        for (l in log) append('\n').append(l)
    }

    companion object {
        fun safeName(n: String): String = n.replace("|", "").replace("\n", " ").filter { !it.isISOControl() }.trim().take(24).ifEmpty { "anonim" }

        fun decode(text: String): Submission? {
            return try {
                val lines = text.trim().split("\n")
                val h = lines[0].split("|")
                if (lines.size < 2 || h.size < 4 || h[0] != "KAPITAL-HASIL" || h[1] != "1") return null
                val c = Challenge.decode(lines[1]) ?: return null
                Submission(h[2], c, lines.drop(2).filter { it.isNotBlank() }, h[3].toInt(), h.getOrNull(4)?.trim()?.toIntOrNull())
            } catch (e: Exception) { null }
        }
    }
}

class Verification(val ok: Boolean, val score: Int, val reason: String)

object ChallengeVerifier {
    /** Skor CEO pada akhir tantangan; kegagalan (bangkrut/disita) dibatasi 25 agar tak mengalahkan yang bertahan. */
    fun ceoScore(e: KapitalEngine): Int {
        val total = e.evaluateCampaign().totalScore
        return if (e.gameOverReason != null && !e.isVictory) minOf(total, 25) else total
    }

    /** Alasan penolakan bila hasil dibuat di versi mesin lain (putar ulang lintas versi tidak sebanding), atau null. */
    fun versionProblem(sub: Submission): String? {
        for (v in listOfNotNull(sub.engineVersion, sub.challenge.engineVersion)) {
            if (v != ENGINE_VERSION) {
                return "Hasil ini dibuat di mesin versi $v, sedangkan aplikasi ini versi $ENGINE_VERSION. Skor tidak bisa diverifikasi lintas versi " +
                    "karena dunia berjalan berbeda; minta pemain memainkan ulang di versi yang sama atau samakan versi aplikasi (ini bukan tuduhan curang)."
            }
        }
        return null
    }

    fun verify(sub: Submission): Verification {
        versionProblem(sub)?.let { return Verification(false, 0, it) }
        val c = sub.challenge
        val real = if (c.role == Role.CEO) replayCeo(c, sub.log) else replayRole(c, sub.log)
        if (real.first == null) return Verification(false, 0, real.second)
        val s = real.first!!
        if (s != sub.claimedScore) {
            val hint = if (sub.engineVersion == null && c.engineVersion == null) " Hasil ini lama dan tidak memuat cap versi mesin; bisa jadi dibuat di versi lain." else ""
            return Verification(false, s, "Skor klaim ${sub.claimedScore} tidak sama dengan hasil putar ulang $s.$hint")
        }
        return Verification(true, s, "Terverifikasi lewat putar ulang.")
    }

    private fun replayRole(c: Challenge, log: List<String>): Pair<Int?, String> {
        val g = RoleGames.replay(c.role, c.seed, c.horizon, log) ?: return null to "Log aksi tidak sah atau ditolak oleh aturan permainan."
        if (!g.finished) return null to "Tantangan belum selesai (${g.turn} dari ${c.horizon} bulan)."
        return g.score() to ""
    }

    private fun replayCeo(c: Challenge, log: List<String>): Pair<Int?, String> {
        val e = KapitalEngine(c.seed)
        if (c.difficulty != Difficulty.MANAJER && !e.apply(Action.SetDifficulty(c.difficulty))) return null to "Kesulitan tantangan tidak dapat diterapkan."
        val endTurn = e.turn + c.horizon
        for ((i, line) in log.withIndex()) {
            if (e.gameOverReason != null || e.turn >= endTurn) return null to "Ada aksi setelah tantangan berakhir (baris ${i + 1})."
            val a = Action.decode(line) ?: return null to "Baris ${i + 1} bukan aksi yang sah."
            if (a is Action.SetDifficulty) return null to "Log tidak boleh mengganti kesulitan."
            if (!e.apply(a)) return null to "Aksi pada baris ${i + 1} ditolak oleh aturan permainan."
        }
        if (e.gameOverReason == null && e.turn < endTurn) return null to "Tantangan belum selesai (giliran ${e.turn - (endTurn - c.horizon)} dari ${c.horizon})."
        return ceoScore(e) to ""
    }
}

class LeaderboardEntry(val player: String, val score: Int, val actions: Int)

/** Papan peringkat satu tantangan: hanya hasil terverifikasi; satu hasil terbaik per pemain. */
class Leaderboard(val challenge: Challenge) {
    private val best = LinkedHashMap<String, LeaderboardEntry>()

    fun submit(sub: Submission): Verification {
        if (!sub.challenge.sameWorld(challenge)) return Verification(false, 0, "Hasil ini untuk tantangan lain.")
        val v = ChallengeVerifier.verify(sub)
        if (!v.ok) return v
        val name = Submission.safeName(sub.player)
        val old = best[name]
        if (old == null || v.score > old.score) best[name] = LeaderboardEntry(name, v.score, sub.log.size)
        return v
    }

    /** Teks papan (untuk disimpan sementara oleh layar); hanya berisi entri yang sudah terverifikasi saat masuk. */
    fun export(): String = best.values.joinToString("\n") { "${it.player}\t${it.score}\t${it.actions}" }

    /** Muat kembali teks hasil [export] tanpa memverifikasi ulang (entri itu dulu sudah diputar ulang). */
    fun restore(text: String) {
        for (line in text.lines()) {
            val p = line.split("\t")
            if (p.size != 3) continue
            val sc = p[1].toIntOrNull() ?: continue
            val ac = p[2].toIntOrNull() ?: continue
            best[Submission.safeName(p[0])] = LeaderboardEntry(Submission.safeName(p[0]), sc, ac)
        }
    }

    fun ranking(): List<LeaderboardEntry> = best.values.sortedWith(compareByDescending<LeaderboardEntry> { it.score }.thenBy { it.actions }.thenBy { it.player })
}
