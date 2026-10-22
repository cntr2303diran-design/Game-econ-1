package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test

/** C5: pembukaan fitur era lebih awal berdasarkan kinerja; kalender tetap jaring pengaman. */
class EraUnlockTest {

    private fun play(seed: Long, active: Boolean, upTo: Int = 481, onTurn: (KapitalEngine) -> Unit = {}): KapitalEngine {
        val e = KapitalEngine(seed)
        var t = 0
        while (e.gameOverReason == null && e.turn < upTo) {
            t++
            if (active) ActiveBot.step(e, t) else TestBots.disciplinedStep(e, t)
            e.apply(Action.EndTurn)
            onTurn(e)
        }
        return e
    }

    private val seeds = (1..24).map { it * 2654435761L % 100000007L }

    private fun <T> withUnlock(on: Boolean, body: () -> T): T {
        val old = BalanceTuning.performanceUnlock
        BalanceTuning.performanceUnlock = on
        try { return body() } finally { BalanceTuning.performanceUnlock = old }
    }

    @Test
    fun calendarIsAlwaysTheSafetyNet() {
        // Bahkan dengan sakelar kinerja menyala dan syarat tak terpenuhi, fitur terbuka pada tanggal era.
        withUnlock(true) {
            var checked = 0
            for (seed in seeds) {
                val e = play(seed, false, 98)
                if (e.gameOverReason != null || e.turn < 97) continue
                checked++
                assertTrue("Era 2 harus terbuka pada giliran ${e.turn} (seed $seed)", e.eraBlock(2) == null)
            }
            assertTrue("ada permainan yang mencapai Era 2: $checked", checked >= 5)
        }
        // Dan terkunci di awal permainan, dengan pesan yang menyebut syarat kinerja.
        withUnlock(true) {
            val msg = KapitalEngine(7L).eraBlock(2)
            assertNotNull(msg)
            assertTrue("pesan menyebut syarat: $msg", msg!!.contains("syarat kinerja") && msg.contains("[belum]"))
        }
        withUnlock(false) {
            val msg = KapitalEngine(7L).eraBlock(2)
            assertNotNull(msg); assertFalse("tanpa kebijakan, tak ada syarat dini: $msg", msg!!.contains("syarat kinerja"))
        }
    }

    @Test
    fun earlyUnlock_isLatched_oneEraAhead_andReallyOpensFeatures() {
        withUnlock(true) {
            var early = 0; var opened = 0
            for (seed in seeds) for (active in listOf(false, true)) {
                var prev = 0
                var sawEarly = false
                val e = play(seed, active) { x ->
                    assertTrue("kait tidak turun: ${x.earlyEra} < $prev", x.earlyEra >= prev)
                    prev = x.earlyEra
                    assertTrue("maksimal satu era di depan kalender", x.earlyEra <= KapitalEngine.era(x.turn) + 1)
                    if (x.earlyEra > KapitalEngine.era(x.turn) && !sawEarly) {
                        sawEarly = true
                        assertTrue("fitur era ${x.earlyEra} terbuka", x.eraBlock(x.earlyEra) == null)
                        if (x.earlyEra == 2) {
                            val cid = KapitalEngine.SUBSIDIARIES.first().countryId
                            val b = x.subsidiaryBlock(cid)
                            assertTrue("blokir anak usaha bukan karena era: $b", b == null || !b.startsWith("Terkunci"))
                            opened++
                        }
                        assertTrue("tercatat di kronik", x.chronicle.any { it.title.contains("terbuka lebih awal") })
                    }
                }
                if (sawEarly) early++
            }
            println("C5 pembukaan dini: $early dari ${seeds.size * 2} permainan; anak usaha terbuka dini diperiksa $opened kali")
            assertTrue("uji tidak hampa: minimal satu pembukaan dini ($early)", early >= 1)
        }
    }

    @Test
    fun switchOff_neverUnlocksEarly() {
        withUnlock(false) {
            for (seed in seeds.take(8)) {
                val e = play(seed, true)
                assertEquals("seed $seed", 0, e.earlyEra)
            }
        }
    }

    @Test
    fun earlyUnlock_isDeterministic() {
        withUnlock(true) {
            fun timeline(seed: Long): List<Int> {
                val out = ArrayList<Int>(); var prev = 0
                play(seed, true) { x -> if (x.earlyEra != prev) { prev = x.earlyEra; out += x.turn; out += x.earlyEra } }
                return out
            }
            for (seed in seeds.take(10)) assertEquals("seed $seed", timeline(seed), timeline(seed))
        }
    }

    @Test
    fun botOutcomes_areUnchangedBySwitch_becauseBotsFollowTheCalendar() {
        fun finish(seed: Long, active: Boolean, on: Boolean): String = withUnlock(on) {
            val e = play(seed, active)
            "${e.turn}|${e.gameOverReason}|${"%.4f".format(e.equity)}|${"%.4f".format(e.cash)}"
        }
        for (seed in seeds.take(8)) for (active in listOf(false, true))
            assertEquals("seed $seed aktif=$active", finish(seed, active, false), finish(seed, active, true))
    }
}
