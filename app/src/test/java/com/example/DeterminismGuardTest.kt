package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Mesin harus identik di semua perangkat: tak boleh ada panggilan transendental yang boleh berbeda antar CPU/runtime. */
class DeterminismGuardTest {

    private fun engineSources(): List<File> {
        val dirs = listOf("src/main/java/com/example/engine", "app/src/main/java/com/example/engine").map { File(it) }
        val dir = dirs.firstOrNull { it.isDirectory } ?: throw AssertionError("folder sumber mesin tidak ditemukan dari ${File(".").absolutePath}")
        return dir.listFiles { f -> f.name.endsWith(".kt") }!!.toList()
    }

    @Test
    fun engineSources_doNotCallPlatformDependentMath() {
        val files = engineSources()
        assertTrue("uji tidak hampa: ${files.size} berkas", files.size > 10)
        val banned = Regex("""(?<![A-Za-z.])Math\.(exp|log|log10|log1p|expm1|pow|sin|cos|tan|tanh|sinh|cosh|cbrt|atan|atan2|asin|acos|hypot)\(|import kotlin\.math\.(exp|ln|pow|tanh|cos|sin|tan|log|log10|ln1p|expm1|atan|atan2|asin|acos|cbrt|hypot|sinh|cosh)\b""")
        val hits = ArrayList<String>()
        for (f in files) {
            if (f.name == "DMath.kt") continue
            f.readLines().forEachIndexed { i, line ->
                if (line.trimStart().startsWith("*") || line.trimStart().startsWith("//")) return@forEachIndexed
                if (banned.containsMatchIn(line)) hits += "${f.name}:${i + 1}: ${line.trim()}"
            }
        }
        assertTrue("panggilan matematika yang bergantung platform: $hits", hits.isEmpty())
    }

    @Test
    fun sameSeedAndActions_giveBitIdenticalState() {
        fun fingerprint(): String {
            val e = KapitalEngine(4242L); var t = 0
            while (e.gameOverReason == null && e.turn < 200) { t++; ActiveBot.step(e, t); e.apply(Action.EndTurn) }
            return "${e.turn}|${java.lang.Double.doubleToLongBits(e.equity)}|${java.lang.Double.doubleToLongBits(e.cash)}|${java.lang.Double.doubleToLongBits(e.world.inflation)}"
        }
        assertEquals(fingerprint(), fingerprint())
    }
}
