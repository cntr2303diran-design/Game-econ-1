package com.example.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Tahap B3: operasi. Karyawan kunci (SDM), program R&D berkelanjutan, dan tingkat logistik.
 *
 * Semua biaya masuk beban usaha (bulanan lewat extraOpex, sekali jalan lewat oneOffOpex); tidak ada arus kas
 * yang dibuat di luar jalur lama. Karyawan awal sudah ada di dasar biaya gaji, jadi hanya selisih upah (skill di atas
 * rata-rata) dan kenaikan gaji yang menjadi biaya tambahan; jabatan kosong menghemat gaji dasar tetapi kehilangan
 * efek, dan kerugiannya dirancang lebih besar daripada penghematannya.
 */
enum class StaffRole(val code: String, val label: String) {
    INSINYUR("insinyur", "Insinyur produksi"),
    PERANCANG("perancang", "Kepala desain dan R&D"),
    SUPERVISOR("supervisor", "Supervisor lantai pabrik"),
    PENJUAL("penjual", "Manajer penjualan kunci"),
    TEKNISI("teknisi", "Kepala teknisi mesin");

    companion object { fun of(code: String): StaffRole? = values().firstOrNull { it.code == code } }
}

class Staff(val role: StaffRole) {
    var present = true
    var skill = 0.5
    var loyalty = 0.7
    /** Gaji relatif terhadap upah pasar untuk skill-nya (1,0 = upah pasar). */
    var salaryMult = 1.0
    var monthsEmployed = 12
    /** Perkiraan skill dari wawancara (berderau); nilai asli terbuka setelah 3 bulan bekerja. */
    var interviewEstimate = 0.5
    val revealed: Boolean get() = monthsEmployed >= 3
}

object StaffEconomy {
    const val VACANT_SKILL = 0.2
    const val BASE_WAGE = 60.0           // jt RS per bulan pada skala 1, indeks biaya 1
    const val MAX_SALARY_MULT = 1.5
    /** Jabatan kosong hanya menghemat sebagian gaji dasar (sisanya kontraktor sementara). */
    const val VACANCY_SAVING = 0.4
    const val RAISE_STEP = 0.10

    /** Upah pasar relatif untuk suatu skill (0,5 → 0,97; 0,7 → 1,34; 0,9 → 1,77). */
    fun marketWage(skill: Double): Double = 0.4 + 1.6 * StrictMath.pow(skill, 1.5)

    /** Mean dan simpangan baku skill kandidat menurut tingkat perekrutan 1..3. */
    fun candidateMean(tier: Int) = when (tier) { 1 -> 0.45; 2 -> 0.58; else -> 0.70 }
    fun candidateSd(tier: Int) = when (tier) { 1 -> 0.15; 2 -> 0.12; else -> 0.10 }
    /** Biaya perekrut sekali jalan sebagai kelipatan upah bulanan. */
    fun recruitMultiple(tier: Int) = when (tier) { 1 -> 1.0; 2 -> 1.6; else -> 2.5 }
}

class StaffPool {
    val staff: List<Staff> = StaffRole.values().map { Staff(it) }
    var poachedTotal = 0; private set
    var hiredTotal = 0; private set
    var firedTotal = 0; private set
    var lastPoachedBy: String? = null; private set

    fun of(role: StaffRole): Staff = staff.first { it.role == role }
    private fun skillOf(role: StaffRole): Double = of(role).let { if (it.present) it.skill else StaffEconomy.VACANT_SKILL }

    /** Pengali HPP: insinyur dan supervisor (masing-masing ±3% HPP per satu skill). 1,0 pada skill 0,5. */
    fun cogsMult(): Double = 1.0 - 0.03 * (skillOf(StaffRole.INSINYUR) - 0.5) - 0.03 * (skillOf(StaffRole.SUPERVISOR) - 0.5)
    /** Pengali pendapatan: manajer penjualan. */
    fun revenueMult(): Double = 1.0 + 0.06 * (skillOf(StaffRole.PENJUAL) - 0.5)
    /** Pengali peluang kerusakan mesin: kepala teknisi. */
    fun breakdownMult(): Double = 1.0 - 1.2 * (skillOf(StaffRole.TEKNISI) - 0.5)
    /** Pengali laju R&D: kepala desain. */
    fun rdRateMult(): Double = 0.5 + skillOf(StaffRole.PERANCANG)

    /** Biaya tambahan bulanan (jt RS) relatif terhadap dasar biaya gaji yang sudah ada. */
    fun extraMonthlyCost(unit: Double): Double {
        val baseline = StaffEconomy.marketWage(0.5)
        var c = 0.0
        for (s in staff) {
            c += if (s.present) StaffEconomy.marketWage(s.skill) * s.salaryMult - baseline else -StaffEconomy.VACANCY_SAVING * baseline
        }
        return c * unit
    }

    fun hire(role: StaffRole, tier: Int, r: Random): Staff {
        val s = of(role)
        s.present = true
        s.skill = (StaffEconomy.candidateMean(tier) + StaffEconomy.candidateSd(tier) * stdNormal(r)).coerceIn(0.15, 0.97)
        s.interviewEstimate = (s.skill + 0.12 * stdNormal(r)).coerceIn(0.1, 1.0)
        s.loyalty = 0.65
        s.salaryMult = 1.0
        s.monthsEmployed = 0
        hiredTotal++
        return s
    }

    fun fire(role: StaffRole) {
        val s = of(role)
        s.present = false
        firedTotal++
        for (o in staff) if (o.present && o !== s) o.loyalty = max(0.0, o.loyalty - 0.04)
    }

    fun raise(role: StaffRole): Boolean {
        val s = of(role)
        if (!s.present || s.salaryMult >= StaffEconomy.MAX_SALARY_MULT - 1e-9) return false
        s.salaryMult = min(StaffEconomy.MAX_SALARY_MULT, s.salaryMult + StaffEconomy.RAISE_STEP)
        return true
    }

    /**
     * Langkah bulanan: loyalitas bergerak ke targetnya (naik bila gaji di atas pasar) dan pesaing bisa membajak.
     * @param rivalAggression jumlah agresivitas pesaing yang aktif.
     * @return daftar (jabatan, nama pesaing) yang dibajak bulan ini.
     */
    fun step(r: Random, rivalAggression: Double, rivalNames: List<String>): List<Pair<StaffRole, String>> {
        val out = ArrayList<Pair<StaffRole, String>>()
        for (s in staff) {
            if (!s.present) continue
            s.monthsEmployed++
            val target = (0.70 + 0.8 * (s.salaryMult - 1.0)).coerceIn(0.1, 0.98)
            s.loyalty += 0.10 * (target - s.loyalty)
            val h = 0.0025 * rivalAggression * StrictMath.pow(max(0.1, 1.5 - s.loyalty), 2.0) * (0.5 + s.skill)
            if (r.nextDouble() < h) {
                s.present = false; poachedTotal++
                val by = rivalNames[r.nextInt(rivalNames.size)]
                lastPoachedBy = by
                out += s.role to by
            }
        }
        return out
    }
}

/** R&D berkelanjutan: anggaran bulanan membangun jalur inovasi; hasilnya naik sebagai indeks kualitas yang menyusut perlahan. */
class RdProgram {
    var level = 0; private set
    var progress = 0.0; private set
    var quality = 0.0; private set
    var innovations = 0; private set

    companion object {
        const val MAX_LEVEL = 3
        const val QUALITY_CAP = 0.04
        const val GAIN = 0.004
        const val DECAY = 0.004
        const val COST_PER_LEVEL = 70.0   // jt RS per bulan pada skala 1, indeks biaya 1
    }

    fun setLevel(l: Int) { level = l.coerceIn(0, MAX_LEVEL) }

    /** @return true bila satu inovasi tercapai bulan ini. */
    fun step(r: Random, rateMult: Double): Boolean {
        var hit = false
        if (level > 0) {
            progress += level * 0.25 * rateMult * (0.6 + 0.8 * r.nextDouble())
            if (progress >= 1.0) { progress -= 1.0; quality = min(QUALITY_CAP, quality + GAIN); innovations++; hit = true }
        }
        quality *= (1.0 - DECAY)
        return hit
    }
}
