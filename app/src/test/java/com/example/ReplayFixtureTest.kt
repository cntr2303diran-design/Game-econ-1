package com.example

import com.example.engine.ENGINE_VERSION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Menjaga disiplin ENGINE_VERSION. Berkas replay_fixture.txt (satu kampanye penuh + ringkasan keadaan tiap 12 giliran) diputar ulang
 * di sini (JVM) dan di emulator (ART, ArtReplayTest). Bila mesin berubah sehingga log lama menghasilkan keadaan lain, uji ini gagal:
 * naikkan ENGINE_VERSION lalu bangkitkan ulang berkas dengan tools/gen-replay-fixture.sh. Dengan begitu simpanan pemain yang dibuat
 * pada mesin lama selalu memunculkan peringatan versi dan tidak diam-diam berjalan berbeda.
 */
class ReplayFixtureTest {
    private fun fixtureText(): String {
        for (p in listOf("src/androidTest/assets/replay_fixture.txt", "app/src/androidTest/assets/replay_fixture.txt")) {
            val f = File(p)
            if (f.exists()) return f.readText()
        }
        throw AssertionError("replay_fixture.txt tidak ditemukan; jalankan tools/gen-replay-fixture.sh")
    }

    @Test
    fun fixtureMatchesCurrentEngine_soEngineChangesMustBumpTheVersion() {
        val fx = ReplayFixture.parse(fixtureText())
        assertEquals("berkas dibuat untuk ENGINE_VERSION lain; jalankan tools/gen-replay-fixture.sh", ENGINE_VERSION, fx.engineVersion)
        assertTrue("log kosong", fx.actions.size > 300)
        assertTrue("titik periksa terlalu sedikit (${fx.checks.size})", fx.checks.size >= 30)
        val out = fx.replay()
        assertTrue(
            "Mesin berubah tanpa menaikkan ENGINE_VERSION (atau putar ulang tidak deterministik). Naikkan ENGINE_VERSION di " +
                "data/GameRepository.kt, catat alasannya di komentar riwayat, lalu jalankan tools/gen-replay-fixture.sh. " +
                "Ketidakcocokan pertama: ${out.mismatches.take(3)}",
            out.mismatches.isEmpty()
        )
        assertEquals("tidak boleh ada aksi ditolak saat diputar ulang", 0, out.rejected)
        assertTrue("kampanye penuh seharusnya mencapai akhir (giliran ${out.finalTurn})", out.finalTurn >= 481)
        println("REPLAY-FIXTURE JVM: ${fx.actions.size} baris log, ${out.elapsedMs} ms, ${fx.checks.size} titik periksa cocok")
    }

    @Test
    fun replayIsIdenticalOnRepeat() {
        val fx = ReplayFixture.parse(fixtureText())
        val a = fx.replay(collect = true)
        val b = fx.replay(collect = true)
        assertEquals(a.checks.keys, b.checks.keys)
        for (k in a.checks.keys) assertEquals("giliran $k", a.checks[k]!!.hash, b.checks[k]!!.hash)
    }
}
