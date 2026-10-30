package com.example

import android.content.pm.ApplicationInfo
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.GameRepository
import com.example.viewmodel.FileSaveStore
import com.example.viewmodel.FileSlotStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Mengukur muat simpanan akhir permainan dari PROSES BARU di ART (dipanggil terpisah oleh tools/ci-emulator.sh dengan
 * `-e class com.example.ColdLoadTest`, satu proses per pengukuran, dengan atau tanpa kompilasi AOT, pada build debuggable atau tidak).
 *
 * Argumen instrumentasi:
 *  - label : nama pengukuran untuk dicatat (mis. nd-jit1)
 *  - turns : jumlah giliran simpanan (bawaan 380; sama dengan tools/make_late_save.py)
 *  - mode  : "waktu" (bawaan) menulis simpanan lalu memutar ulangnya lewat GameRepository persis seperti KapitalViewModel;
 *            "siapkan" hanya menulis simpanan ke folder aplikasi, lalu skrip membuka aplikasi sungguhan (am start) dari proses dingin
 *            dan membaca log KAPITAL-LOAD dari KapitalViewModel (tidak butuh run-as, jadi bekerja pada build yang tidak debuggable).
 */
@RunWith(AndroidJUnit4::class)
class ColdLoadTest {
    private fun lateSave(turns: Int): Pair<String, Int> {
        val inst = InstrumentationRegistry.getInstrumentation()
        val text = inst.context.assets.open("replay_fixture.txt").bufferedReader().use { it.readText() }
        val fx = ReplayFixture.parse(text)
        val lines = ArrayList<String>()
        lines += "SEED:${fx.seed}"
        lines += "VER:${fx.engineVersion}"
        var ended = 0
        for (a in fx.actions) {
            lines += a
            if (a == "END") { ended++; if (ended >= turns) break }
        }
        return lines.joinToString("\n") + "\n" to ended
    }

    private fun outFile(name: String): File {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        return File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, name)
    }

    @Test
    fun lateCampaignSave_isRestoredFromAFreshProcess() {
        val args = InstrumentationRegistry.getArguments()
        val label = args.getString("label") ?: "tanpa-label"
        val turns = args.getString("turns")?.toIntOrNull() ?: 380
        val mode = args.getString("mode") ?: "waktu"
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val debuggable = (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val sinceProcessStart = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()

        val (save, ended) = lateSave(turns)
        val file = File(ctx.filesDir, "kapital_save.txt")
        file.writeText(save)
        if (mode == "siapkan") {
            Log.i("KAPITAL-LOAD", "SIAP label=$label simpanan=${save.length} byte, $ended giliran; debuggable=$debuggable")
            assertTrue(file.length() > 0)
            return
        }

        val t0 = SystemClock.elapsedRealtime()
        val repo = GameRepository(FileSaveStore(file), slots = FileSlotStore(ctx.filesDir), deferRestore = true)
        val t1 = SystemClock.elapsedRealtime()
        assertTrue("simpanan harus tertunda untuk dipulihkan", repo.needsRestore)
        repo.restorePending()
        val t2 = SystemClock.elapsedRealtime()

        val st = repo.gameState.value
        val line = "COLD label=$label; debuggable=$debuggable; giliran simpanan=$ended; konstruktor=${t1 - t0} ms; pemulihan=${t2 - t1} ms; " +
            "uji mulai ${sinceProcessStart} ms setelah proses dibuat; vm=${System.getProperty("java.vm.version")}; giliran akhir=${st.turn}"
        Log.i("KAPITAL-LOAD", line)
        outFile("cold_$label.txt").writeText(line + "\n")
        assertFalse("pemulihan harus selesai", st.isRestoring)
        assertEquals("pemulihan harus menambah penghitung sekali", 1, repo.restoredCount.value)
        assertTrue("giliran setelah pulih harus melewati giliran simpanan (${st.turn} vs $ended)", st.turn > ended)
    }
}
