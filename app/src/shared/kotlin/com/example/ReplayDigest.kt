package com.example

import com.example.engine.KapitalEngine
import java.lang.reflect.Modifier

/**
 * Ringkasan numerik keadaan mesin untuk membandingkan putar ulang antar runtime (HotSpot JVM di CI vs ART di emulator).
 * Memakai refleksi atas seluruh properti numerik/boolean publik mesin plus baris laba rugi bulan terakhir, diurutkan menurut nama,
 * sehingga urutan metode yang berbeda antar runtime tidak berpengaruh. Hanya dipakai uji (sumber bersama test dan androidTest);
 * tidak termasuk APK.
 */
object ReplayDigest {
    fun snapshot(e: KapitalEngine): List<Pair<String, Double>> {
        val out = ArrayList<Pair<String, Double>>()
        for (m in e.javaClass.methods) {
            if (m.parameterCount != 0 || Modifier.isStatic(m.modifiers)) continue
            val n = m.name
            val key = when {
                n.startsWith("get") && n.length > 3 -> n.substring(3)
                n.startsWith("is") && n.length > 2 -> n.substring(2)
                else -> continue
            }
            if (key == "Class") continue
            val t = m.returnType
            if (!(t == java.lang.Double.TYPE || t == Integer.TYPE || t == java.lang.Long.TYPE || t == java.lang.Boolean.TYPE)) continue
            val v = try { m.invoke(e) } catch (_: Throwable) { continue }
            out += key to when (v) {
                is Boolean -> if (v) 1.0 else 0.0
                is Number -> v.toDouble()
                else -> continue
            }
        }
        for ((k, v) in e.lastIncome.toSortedMap()) out += "inc_$k" to v
        out.sortBy { it.first }
        return out
    }

    /** FNV-1a 64-bit atas nama dan bit nilai (NaN dikanonkan) dari [snapshot]. */
    fun digest(e: KapitalEngine): Long {
        var h = -3750763034362895579L // 0xcbf29ce484222325
        val prime = 1099511628211L
        fun mix(b: Int) { h = (h xor (b.toLong() and 0xff)) * prime }
        for ((k, v) in snapshot(e)) {
            for (c in k) { mix(c.code and 0xff); mix((c.code shr 8) and 0xff) }
            mix(0)
            val bits = java.lang.Double.doubleToLongBits(v)
            for (i in 0 until 8) mix(((bits shr (8 * i)) and 0xff).toInt())
        }
        return h
    }

    fun hex(x: Long): String = java.lang.Long.toHexString(x)
}
