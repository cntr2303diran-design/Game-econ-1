package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test

/** C4: multiplayer asinkron (tantangan seed bersama + log aksi). */
class ChallengeTest {
    private fun playGovernor(c: Challenge, bot: (GovernorGame) -> Unit): Submission {
        val g = GovernorGame(c.seed, c.horizon)
        while (!g.finished) bot(g)
        return Submission("pemain", c, g.log.toList(), g.score())
    }

    private fun playCeo(c: Challenge): Submission {
        val e = KapitalEngine(c.seed)
        if (c.difficulty != Difficulty.MANAJER) e.apply(Action.SetDifficulty(c.difficulty))
        val endTurn = e.turn + c.horizon
        val rec = ArrayList<String>(); var t = 0
        while (e.gameOverReason == null && e.turn < endTurn) {
            t++
            TestBots.disciplinedStep(e, t, rec)
            if (e.gameOverReason != null) break
            if (e.apply(Action.EndTurn)) rec.add(Action.EndTurn.encode())
        }
        return Submission("ceo", c, rec, ChallengeVerifier.ceoScore(e))
    }

    @Test
    fun codec_roundTrip_andRejectsGarbage() {
        val c = Challenge(Role.GUBERNUR, 12345L, 60, Difficulty.DIREKTUR)
        assertEquals(c, Challenge.decode(c.encode()))
        val s = Submission("Ani|Budi\nx", c, listOf("NM", "POL|6.5"), 42)
        val back = Submission.decode(s.encode())!!
        assertEquals(c, back.challenge); assertEquals(listOf("NM", "POL|6.5"), back.log); assertEquals(42, back.claimedScore)
        assertFalse("nama dibersihkan dari pemisah", back.player.contains("|") || back.player.contains("\n"))
        assertTrue(Challenge.decode("sampah") == null)
        assertTrue(Challenge.decode("KAPITAL-TANTANGAN|1|GUB|1|2|MANAJER") == null) // panjang di bawah batas
        assertTrue(Challenge.decode("KAPITAL-TANTANGAN|1|XXX|1|60|MANAJER") == null)
        assertTrue(Submission.decode("KAPITAL-HASIL|1|a|zzz\n${c.encode()}") == null)
    }

    @Test
    fun governorChallenge_rankingFollowsSkill_andIsVerifiedByReplay() {
        val c = Challenge(Role.GUBERNUR, 777L, 60)
        val good = playGovernor(c, RolesTest.Companion::taylorGovernor).copy(player = "Cermat")
        val lazy = playGovernor(c, RolesTest.Companion::holdGovernor).copy(player = "Diam")
        val board = Leaderboard(c)
        assertTrue(board.submit(good).ok); assertTrue(board.submit(lazy).ok)
        // sampai lewat teks (seolah dikirim antarpemain)
        val viaText = Submission.decode(good.copy(player = "Cermat2").encode())!!
        assertTrue(board.submit(viaText).ok)
        val r = board.ranking()
        println("TANTANGAN peringkat: ${r.map { "${it.player}=${it.score}" }}")
        assertEquals("Cermat", r.first().player.takeIf { it == "Cermat" || it == "Cermat2" }?.let { "Cermat" } ?: r.first().player)
        assertTrue("pengikut sinyal di atas pemain diam", r.first().score > r.first { it.player == "Diam" }.score)
        assertEquals(3, r.size)
    }

    @Test
    fun tamperingIsRejected() {
        val c = Challenge(Role.GUBERNUR, 31L, 36)
        val good = playGovernor(c, RolesTest.Companion::taylorGovernor)
        assertTrue(ChallengeVerifier.verify(good).ok)
        // skor digelembungkan
        assertFalse(ChallengeVerifier.verify(good.copy(claimedScore = minOf(100, good.claimedScore + 15))).ok)
        // log diubah: bunga terakhir dibelokkan ke arah buruk
        val bad = good.log.toMutableList(); val i = bad.indexOfLast { it.startsWith("POL|") }
        bad[i] = "POL|1.0"
        assertFalse("log diubah tapi klaim tetap", ChallengeVerifier.verify(good.copy(log = bad)).ok)
        // aksi yang tak sah
        assertFalse(ChallengeVerifier.verify(good.copy(log = good.log + "BUY|sx_mitra|1")).ok)
        assertFalse(ChallengeVerifier.verify(good.copy(log = listOf("POL|16.0"))).ok)
        // belum selesai
        assertFalse(ChallengeVerifier.verify(good.copy(log = good.log.take(10))).ok)
        // tantangan lain
        val board = Leaderboard(Challenge(Role.GUBERNUR, 32L, 36))
        assertFalse(board.submit(good).ok)
    }

    @Test
    fun fundChallenge_works() {
        val c = Challenge(Role.DANA, 55L, 36)
        val f = FundGame(c.seed, c.horizon)
        while (!f.finished) RolesTest.indexFund(f)
        val sub = Submission("indeks", c, f.log.toList(), f.score())
        assertTrue(ChallengeVerifier.verify(sub).ok)
        assertFalse(ChallengeVerifier.verify(sub.copy(claimedScore = (f.score() + 20).coerceAtMost(100))).ok)
    }

    @Test
    fun ceoChallenge_replayVerified_andDeterministic() {
        val c = Challenge(Role.CEO, 4242L, 24)
        val a = playCeo(c); val b = playCeo(c)
        assertTrue("log tidak kosong (uji tidak hampa): ${a.log.size}", a.log.size > 10)
        assertEquals(a.log, b.log); assertEquals(a.claimedScore, b.claimedScore)
        val v = ChallengeVerifier.verify(a)
        assertTrue(v.reason, v.ok)
        assertFalse(ChallengeVerifier.verify(a.copy(claimedScore = (a.claimedScore + 17).coerceAtMost(100).let { if (it == a.claimedScore) a.claimedScore - 1 else it })).ok)
        // aksi sesudah akhir tantangan ditolak
        assertFalse(ChallengeVerifier.verify(a.copy(log = a.log + Action.EndTurn.encode())).ok)
        // pengaturan kesulitan di log dilarang
        assertFalse(ChallengeVerifier.verify(a.copy(log = listOf(Action.SetDifficulty(Difficulty.MAGANG).encode()) + a.log)).ok)
    }
}
