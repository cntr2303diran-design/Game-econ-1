package com.example

import com.example.engine.*

/**
 * Alat bantu uji akibat kartu pada keadaan akhir permainan: dasar permainan dari rekaman bot, penyuntikan hasil kartu,
 * dan ringkasan keadaan yang diperluas (termasuk keadaan tersembunyi seperti jumlah efek aktif, rantai terjadwal, eksekutif, aktor).
 */
object EventProbe {
    /** Keadaan permainan yang bisa dibangun ulang kapan saja dengan memutar ulang log. */
    class Base(val label: String, val seed: Long, val log: List<String>, val era: Int) {
        fun engine(): KapitalEngine {
            val e = KapitalEngine(seed)
            for (l in log) Action.decode(l)?.let { e.apply(it) }
            return e
        }
    }

    /** Dua keadaan akhir permainan dari satu rekaman bot aktif: giliran 250 (Era 3) dan 380 (Era 4); emiten dengan anak usaha. */
    val lateBases: List<Base> by lazy {
        val rec = TestBots.lateRecording(400)
        listOf(250 to 3, 380 to 4).map { (turn, era) ->
            Base("Era $era (giliran $turn)", rec.seed, TestBots.prefixToTurn(rec.seed, rec.log, turn), era)
        }
    }

    /** Menyuntikkan satu kejadian dengan satu opsi dan satu hasil pasti (peluang 1), lalu memilihnya. */
    fun inject(e: KapitalEngine, tpl: String, optId: String, tone: Tone, effects: List<Effect>): Boolean {
        val ev = GameEvent("probe", tpl, "X", "uji", "uji", "",
            listOf(EventOption(optId, "o", 0, "", "", tone, listOf(Outcome(1.0, "t", effects)))), e.turn, e.turn + 1, optId)
        e.openEvents += ev
        return e.apply(Action.EventChoice("probe", optId))
    }

    fun afterMonths(e: KapitalEngine, n: Int): KapitalEngine { repeat(n) { e.apply(Action.EndTurn) }; return e }

    private val BOOKKEEPING = setOf("CrisesSurvived", "ActionCount", "Ap")

    private fun numeric(prefix: String, o: Any, out: MutableMap<String, Double>) {
        for (m in o.javaClass.methods) {
            if (m.parameterCount != 0 || java.lang.reflect.Modifier.isStatic(m.modifiers)) continue
            val n = m.name
            val key = when { n.startsWith("get") && n.length > 3 -> n.substring(3); n.startsWith("is") && n.length > 2 -> n.substring(2); else -> continue }
            if (key in BOOKKEEPING || key == "Class") continue
            val t = m.returnType
            if (!(t == java.lang.Double.TYPE || t == Integer.TYPE || t == java.lang.Long.TYPE || t == java.lang.Boolean.TYPE)) continue
            val v = try { m.invoke(o) } catch (_: Throwable) { continue }
            out[prefix + key] = when (v) { is Boolean -> if (v) 1.0 else 0.0; is Number -> v.toDouble(); else -> continue }
        }
    }

    /** Semua properti numerik/boolean publik mesin, baris laba rugi bulan terakhir, jumlah efek aktif/rantai/bukti etika, aktor, dan eksekutif. */
    fun snapshot(e: KapitalEngine): Map<String, Double> {
        val out = LinkedHashMap<String, Double>()
        numeric("", e, out)
        for ((k, v) in e.lastIncome) out["inc_$k"] = v
        out["#mods"] = e.mods.size.toDouble()
        out["#chain"] = e.scheduledChainEvents.size.toDouble()
        out["#ethicsEv"] = e.ethicsEvidences.size.toDouble()
        out["#open"] = e.openEvents.size.toDouble()
        numeric("actor_", e.actors, out)
        numeric("profile_", e.actors.profile, out)
        for (x in e.execs) numeric("exec_${x.id}_", x, out)
        return out
    }

    /** Nama keadaan yang berbeda antara dua ringkasan (kosong = tidak ada perbedaan). */
    fun changed(a: Map<String, Double>, b: Map<String, Double>): List<String> =
        (a.keys + b.keys).filter { k -> val x = a[k] ?: 0.0; val y = b[k] ?: 0.0; Math.abs(x - y) > 1e-9 * (1.0 + Math.abs(x)) }
}
