package com.example.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * C4: peran alternatif sebagai mode terpisah dengan tujuan dan skor sendiri.
 *
 * Gubernur Bank Sentral dan Manajer Dana memakai lapisan dunia yang sama dengan mode CEO ([World] untuk makro,
 * [Commodities], [Bourse] untuk saham), tetapi tanpa perusahaan pemain: [MacroSim] menjalankan dunia murni dari seed.
 * Seperti mode CEO, hasil ditentukan sepenuhnya oleh seed + daftar aksi (deterministik), sehingga bisa diputar ulang
 * dan dipakai sebagai tantangan asinkron ([Challenge]).
 *
 * Menteri Keuangan ([MinisterGame]) memegang belanja dan pajak; bank sentral mengikuti aturan Taylor dunia.
 * Tuas fiskal ditambahkan lewat [MacroSim.step] (dorongan permintaan dan tekanan risiko), bukan lewat lapisan dunia CEO:
 * perusahaan CEO tidak merasakan kebijakan fiskal pemain.
 */
enum class Role(val code: String, val label: String, val goal: String) {
    CEO("CEO", "CEO Benang Sundara", "Bertahan dan bertumbuh melewati empat era."),
    GUBERNUR("GUB", "Gubernur Bank Sentral", "Jaga inflasi dekat target 3%, kesenjangan output dan pengangguran kecil."),
    DANA("DNA", "Manajer Dana", "Kalahkan indeks bursa dengan penurunan nilai (drawdown) lebih kecil."),
    MENTERI("MEN", "Menteri Keuangan", "Stabilkan ekonomi lewat belanja dan pajak tanpa membiarkan utang negara lepas kendali.");
    companion object { fun of(code: String): Role? = values().firstOrNull { it.code == code } }
}

/** Aksi pemain pada mode Gubernur dan Manajer Dana; kode teksnya adalah isi log aksi. */
sealed class RoleAction {
    object NextMonth : RoleAction()
    data class SetPolicy(val rate: Double) : RoleAction()
    data class Buy(val companyId: String, val lots: Int) : RoleAction()
    data class Sell(val companyId: String, val lots: Int) : RoleAction()
    /** Menteri: belanja pemerintah (% PDB). */
    data class SetSpending(val pct: Double) : RoleAction()
    /** Menteri: penerimaan pajak struktural (% PDB). */
    data class SetTax(val pct: Double) : RoleAction()

    fun encode(): String = when (this) {
        NextMonth -> "NM"
        is SetPolicy -> "POL|$rate"
        is Buy -> "BUY|$companyId|$lots"
        is Sell -> "SEL|$companyId|$lots"
        is SetSpending -> "SPD|$pct"
        is SetTax -> "TAX|$pct"
    }

    companion object {
        fun decode(line: String): RoleAction? {
            val p = line.trim().split("|")
            return try {
                when (p[0]) {
                    "NM" -> NextMonth
                    "POL" -> SetPolicy(p[1].toDouble())
                    "BUY" -> Buy(p[1], p[2].toInt())
                    "SEL" -> Sell(p[1], p[2].toInt())
                    "SPD" -> SetSpending(p[1].toDouble())
                    "TAX" -> SetTax(p[1].toDouble())
                    else -> null
                }
            } catch (e: Exception) { null }
        }
    }
}

/** Antarmuka bersama mode peran. */
interface RoleGame {
    val role: Role
    val seed: Long
    val turn: Int
    val horizon: Int
    val finished: Boolean
    val log: List<String>
    fun apply(a: RoleAction): Boolean
    /** Skor 0..100 pada keadaan sekarang (final bila [finished]). */
    fun score(): Int
    fun summary(): List<String>
}

object RoleGames {
    const val DEFAULT_HORIZON = 120

    fun create(role: Role, seed: Long, horizon: Int = DEFAULT_HORIZON): RoleGame? = when (role) {
        Role.GUBERNUR -> GovernorGame(seed, horizon)
        Role.DANA -> FundGame(seed, horizon)
        Role.MENTERI -> MinisterGame(seed, horizon)
        Role.CEO -> null
    }

    /** Memutar ulang log aksi; null bila ada baris yang tidak sah atau ditolak. */
    fun replay(role: Role, seed: Long, horizon: Int, log: List<String>): RoleGame? {
        val g = create(role, seed, horizon) ?: return null
        for (line in log) {
            val a = RoleAction.decode(line) ?: return null
            if (!g.apply(a)) return null
        }
        return g
    }
}

/** Guncangan makro terjadwal dari seed (krisis permintaan, depresiasi kurs, lonjakan kapas) agar pemain punya sesuatu untuk dihadapi. */
class MacroShock(val start: Int, val months: Int, val demandPerMonth: Double, val fxHit: Double, val cottonShock: Double, val riskOff: Double, val title: String)

/** Dunia makro dan bursa tanpa perusahaan pemain. */
class MacroSim(val seed: Long) {
    val world = World(AgentScale.HOUSEHOLDS.getValue("sundara"))
    val commodities = Commodities()
    val bourse = Bourse()
    var turn = 1; private set
    var kurs = 1.0; private set
    private var econ = 1.0
    private var priceIndex = 1.0
    private var trendMonths = 0
    var riskOff = 0.0; private set
    val events = ArrayList<String>()

    val shocks: List<MacroShock> = run {
        val r = Random(seed * 31L + 7L)
        val out = ArrayList<MacroShock>()
        var t = 10 + r.nextInt(14)
        while (t < 520) {
            val kind = r.nextInt(3)
            out += when (kind) {
                0 -> MacroShock(t, 8 + r.nextInt(8), -0.011 - 0.004 * r.nextDouble(), 0.05 + 0.05 * r.nextDouble(), 0.0, 0.5, "Krisis permintaan")
                1 -> MacroShock(t, 6 + r.nextInt(6), -0.002, 0.10 + 0.08 * r.nextDouble(), 0.0, 0.7, "Pelarian modal dan depresiasi kurs")
                else -> MacroShock(t, 6 + r.nextInt(6), 0.003, 0.02, 0.18 + 0.12 * r.nextDouble(), 0.2, "Lonjakan harga kapas dan energi")
            }
            t += 30 + r.nextInt(40)
        }
        out
    }

    private fun rng(stream: Int) = Random(seed * 1_000_003L + turn * 7_919L + stream * 104_729L)

    /** Satu bulan. [policy] = suku bunga acuan yang ditetapkan pemain (null = aturan Taylor bank sentral); [fiscalImpulse] = tambahan permintaan bulan ini dari kebijakan fiskal; [extraRiskOff] = tekanan risiko tambahan 0..1. */
    fun step(policy: Double?, fiscalImpulse: Double = 0.0, extraRiskOff: Double = 0.0) {
        events.clear()
        var demandShock = fiscalImpulse; riskOff = extraRiskOff
        for (s in shocks) {
            if (turn >= s.start && turn < s.start + s.months) {
                val k = turn - s.start
                demandShock += s.demandPerMonth * (if (k < s.months / 2) 1.0 else 0.6)
                riskOff = max(riskOff, s.riskOff)
                if (turn == s.start) {
                    kurs *= 1.0 + s.fxHit
                    if (s.cottonShock > 0.0) commodities.triggerCorneringShock(s.months / 2, s.cottonShock)
                    events += "${s.title} dimulai."
                }
            }
        }
        commodities.step(rng(30), world.gap)
        val matCost = 0.35 + 0.65 * commodities.materialIndex()
        val costPressure = (0.8 + 0.2 * kurs) * matCost
        val realRate = world.policyRate - world.inflation
        val rk = rng(2)
        val dk = (0.002 + 0.010 * (rk.nextDouble() + rk.nextDouble() + rk.nextDouble() - 1.5) / 0.5 - 0.0008 * (realRate - 2.5)).coerceIn(-0.06, 0.06)
        kurs *= 1.0 + dk
        val out = world.step(rng(8), 1.0, costPressure, 1.0, dk, kurs, demandShock, null, 0.0, 0.0, policy)
        econ = out.demand
        trendMonths++
        priceIndex *= 1.0 + (out.inflationAnnual / 12.0) / 100.0
        bourse.step(rng(40), Bourse.Ctx(
            turn, econ, StrictMath.pow(1.025, trendMonths / 12.0), priceIndex, costPressure,
            commodities.cotton.index, commodities.oil.index, commodities.steel.index,
            kurs, out.policyRate, world.gap, 1.0,
            { id -> world.rivalShareOf(id, 1.0) },
            { id -> world.rivals.first { it.id == id }.price },
            { id -> world.rivals.first { it.id == id }.active },
            { id -> world.rivals.first { it.id == id }.share0 },
            0.0, riskOff))
        turn++
    }
}

/** Gubernur: menetapkan suku bunga acuan tiap bulan. Skor dari rata-rata kerugian kuadrat (inflasi, kesenjangan output, pengangguran). */
class GovernorGame(override val seed: Long, override val horizon: Int) : RoleGame {
    override val role = Role.GUBERNUR
    val sim = MacroSim(seed)
    override val log = ArrayList<String>()
    var policy = sim.world.policyRate; private set
    private var lossSum = 0.0
    var months = 0; private set
    var maxInflation = 0.0; private set
    var minGapPct = 0.0; private set
    override val turn: Int get() = sim.turn
    override val finished: Boolean get() = months >= horizon

    override fun apply(a: RoleAction): Boolean {
        if (finished) return false
        when (a) {
            is RoleAction.SetPolicy -> {
                val r = Math.round(a.rate / 0.25) * 0.25
                if (r < 1.0 || r > 16.0 || abs(r - policy) > MAX_MOVE + 1e-9) return false
                policy = r
            }
            RoleAction.NextMonth -> {
                sim.step(policy)
                val infl = sim.world.inflation; val gap = sim.world.gap * 100.0; val un = sim.world.unemployment
                lossSum += (infl - TARGET) * (infl - TARGET) + gap * gap + 0.3 * (un - NAIRU) * (un - NAIRU)
                maxInflation = max(maxInflation, infl); minGapPct = min(minGapPct, gap)
                months++
            }
            else -> return false
        }
        log += a.encode()
        return true
    }

    fun avgLoss(): Double = if (months == 0) 0.0 else lossSum / months
    override fun score(): Int = (100.0 - 14.0 * sqrt(avgLoss())).coerceIn(0.0, 100.0).toInt()
    override fun summary(): List<String> = listOf(
        "Bulan ke-$months dari $horizon. Suku bunga acuan ${"%.2f".format(policy)}%.",
        "Inflasi ${"%.1f".format(sim.world.inflation)}% (target ${TARGET.toInt()}%), kesenjangan output ${"%.1f".format(sim.world.gap * 100.0)}%, pengangguran ${"%.1f".format(sim.world.unemployment)}%.",
        "Inflasi tertinggi ${"%.1f".format(maxInflation)}%, kesenjangan terdalam ${"%.1f".format(minGapPct)}%. Skor ${score()}."
    )

    companion object {
        const val TARGET = 3.0
        const val NAIRU = 5.0
        const val MAX_MOVE = 1.5
    }
}

/** Manajer dana: modal awal 100.000 jt RS di bursa yang sama; skor dari imbal hasil di atas indeks dan drawdown. */
class FundGame(override val seed: Long, override val horizon: Int) : RoleGame {
    override val role = Role.DANA
    val sim = MacroSim(seed)
    override val log = ArrayList<String>()
    var cash = START_CASH; private set
    val holdings = HashMap<String, Int>()
    var dividendsReceived = 0.0; private set
    var feesPaid = 0.0; private set
    private val index0 = sim.bourse.index
    private var peak = START_CASH
    private var indexPeak = index0
    var maxDrawdown = 0.0; private set
    var indexMaxDrawdown = 0.0; private set
    var months = 0; private set
    override val turn: Int get() = sim.turn
    override val finished: Boolean get() = months >= horizon

    private fun price(id: String): Double = sim.bourse.company(id)?.let { if (it.listed) it.price else 0.0 } ?: 0.0
    fun holdingsValue(): Double = holdings.entries.sumOf { (id, lots) -> lots * KapitalEngine.LOT * price(id) }
    fun nav(): Double = cash + holdingsValue()
    fun indexReturn(): Double = sim.bourse.index / index0 - 1.0
    fun fundReturn(): Double = nav() / START_CASH - 1.0

    override fun apply(a: RoleAction): Boolean {
        if (finished) return false
        when (a) {
            is RoleAction.Buy -> {
                val c = sim.bourse.company(a.companyId) ?: return false
                if (a.lots <= 0 || !c.listed) return false
                val gross = a.lots * KapitalEngine.LOT * c.price
                val fee = gross * KapitalEngine.BROKER_FEE
                if (cash - gross - fee < 0.0) return false
                val newPos = (holdings[a.companyId] ?: 0) * KapitalEngine.LOT * c.price + gross
                if (newPos > MAX_WEIGHT * nav()) return false
                cash -= gross + fee; feesPaid += fee
                holdings[a.companyId] = (holdings[a.companyId] ?: 0) + a.lots
            }
            is RoleAction.Sell -> {
                val c = sim.bourse.company(a.companyId) ?: return false
                val have = holdings[a.companyId] ?: 0
                if (a.lots <= 0 || a.lots > have || !c.listed) return false
                val gross = a.lots * KapitalEngine.LOT * c.price
                val fee = gross * (KapitalEngine.BROKER_FEE + KapitalEngine.SELL_TAX)
                cash += gross - fee; feesPaid += fee
                if (a.lots == have) holdings.remove(a.companyId) else holdings[a.companyId] = have - a.lots
            }
            RoleAction.NextMonth -> {
                sim.step(null)
                for ((id, lots) in holdings) {
                    val c = sim.bourse.company(id) ?: continue
                    val m = c.monthly.lastOrNull() ?: continue
                    if (m.turn == sim.turn - 1 && m.dividend > 0 && c.sharesM > 0) {
                        val d = lots * KapitalEngine.LOT * (m.dividend / c.sharesM)
                        cash += d; dividendsReceived += d
                    }
                }
                months++
                val v = nav(); peak = max(peak, v); maxDrawdown = max(maxDrawdown, 1.0 - v / peak)
                val ix = sim.bourse.index; indexPeak = max(indexPeak, ix); indexMaxDrawdown = max(indexMaxDrawdown, 1.0 - ix / indexPeak)
            }
            else -> return false
        }
        log += a.encode()
        return true
    }

    /** Imbal hasil tahunan fund dikurangi indeks (harga). */
    fun excessAnnual(): Double {
        if (months == 0) return 0.0
        val yrs = months / 12.0
        val f = StrictMath.pow(max(1e-6, nav() / START_CASH), 1.0 / yrs) - 1.0
        val i = StrictMath.pow(max(1e-6, sim.bourse.index / index0), 1.0 / yrs) - 1.0
        return f - i
    }

    override fun score(): Int =
        (50.0 + 150.0 * excessAnnual() - 60.0 * max(0.0, maxDrawdown - indexMaxDrawdown)).coerceIn(0.0, 100.0).toInt()

    override fun summary(): List<String> = listOf(
        "Bulan ke-$months dari $horizon. NAV ${"%.0f".format(nav())} jt RS (kas ${"%.0f".format(cash)}).",
        "Imbal hasil dana ${"%.1f".format(fundReturn() * 100)}% vs indeks ${"%.1f".format(indexReturn() * 100)}%; drawdown dana ${"%.0f".format(maxDrawdown * 100)}% vs indeks ${"%.0f".format(indexMaxDrawdown * 100)}%.",
        "Dividen ${"%.0f".format(dividendsReceived)} jt RS, biaya transaksi ${"%.0f".format(feesPaid)} jt RS. Skor ${score()}."
    )

    companion object {
        const val START_CASH = 100_000.0
        /** Satu emiten paling banyak 35% NAV pada saat dibeli. */
        const val MAX_WEIGHT = 0.35
    }
}


/**
 * Menteri Keuangan: menetapkan belanja dan pajak (% PDB) tiap bulan. Bank sentral mengikuti aturan Taylor.
 * Defisit struktural (belanja - pajak) di atas basis 2% PDB mendorong permintaan; revenue pajak ikut siklus (penstabil otomatis);
 * utang tumbuh oleh defisit primer dan bunga, menyusut oleh pertumbuhan nominal; premi risiko naik nonlinier bersama utang
 * dan menekan pasar saat utang tinggi. Utang >= 120% PDB = gagal bayar (permainan selesai, skor maksimal 10).
 */
class MinisterGame(override val seed: Long, override val horizon: Int) : RoleGame {
    override val role = Role.MENTERI
    val sim = MacroSim(seed)
    override val log = ArrayList<String>()
    var spending = BASE_SPEND; private set
    var tax = BASE_TAX; private set
    var debt = START_DEBT; private set
    var interestBill = 0.0; private set
    var months = 0; private set
    var defaulted = false; private set
    var maxDebt = START_DEBT; private set
    private var lossSum = 0.0
    override val turn: Int get() = sim.turn
    override val finished: Boolean get() = months >= horizon || defaulted

    /** Defisit struktural (% PDB, tanpa siklus). */
    fun structuralDeficit(): Double = spending - tax
    /** Premi risiko negara (poin persen) di atas suku bunga acuan. */
    fun riskPremium(): Double = PREM_LIN * max(0.0, debt - 40.0) + PREM_QUAD * max(0.0, debt - 60.0).let { it * it }

    override fun apply(a: RoleAction): Boolean {
        if (finished) return false
        when (a) {
            is RoleAction.SetSpending -> {
                val r = Math.round(a.pct / 0.5) * 0.5
                if (r < MIN_SPEND || r > MAX_SPEND || abs(r - spending) > MAX_MOVE + 1e-9) return false
                spending = r
            }
            is RoleAction.SetTax -> {
                val r = Math.round(a.pct / 0.5) * 0.5
                if (r < MIN_TAX || r > MAX_TAX || abs(r - tax) > MAX_MOVE + 1e-9) return false
                tax = r
            }
            RoleAction.NextMonth -> {
                val impulse = FISCAL_K * (structuralDeficit() - (BASE_SPEND - BASE_TAX)) / 100.0
                val risk = max(0.0, (debt - 70.0) / 100.0).coerceAtMost(0.5)
                sim.step(null, impulse, risk)
                val gap = sim.world.gap; val infl = sim.world.inflation; val un = sim.world.unemployment
                val revenue = tax * (1.0 + REV_ELASTICITY * gap)
                val primary = spending - revenue
                val rate = sim.world.policyRate + riskPremium()
                interestBill = rate / 100.0 * debt
                val gNom = infl + TREND_GROWTH
                debt = max(0.0, debt * (1.0 - gNom / 1200.0) + (primary + interestBill) / 12.0)
                maxDebt = max(maxDebt, debt)
                val g = gap * 100.0
                lossSum += g * g + (infl - 3.0) * (infl - 3.0) + 0.3 * (un - 5.0) * (un - 5.0) + DEBT_PENALTY * max(0.0, debt - 45.0).let { it * it }
                months++
                if (debt >= DEFAULT_DEBT) defaulted = true
            }
            else -> return false
        }
        log += a.encode()
        return true
    }

    fun avgLoss(): Double = if (months == 0) 0.0 else lossSum / months
    override fun score(): Int {
        val s = (100.0 - 14.0 * sqrt(avgLoss())).coerceIn(0.0, 100.0).toInt()
        return if (defaulted) min(s, 10) else s
    }
    override fun summary(): List<String> = listOf(
        "Bulan ke-$months dari $horizon. Belanja ${"%.1f".format(spending)}% PDB, pajak ${"%.1f".format(tax)}% PDB (defisit struktural ${"%.1f".format(structuralDeficit())}%).",
        "Utang ${"%.1f".format(debt)}% PDB (tertinggi ${"%.1f".format(maxDebt)}%), bunga ${"%.1f".format(interestBill)}% PDB, premi risiko ${"%.1f".format(riskPremium())} poin.",
        "Inflasi ${"%.1f".format(sim.world.inflation)}%, kesenjangan output ${"%.1f".format(sim.world.gap * 100.0)}%, pengangguran ${"%.1f".format(sim.world.unemployment)}%.",
        (if (defaulted) "NEGARA GAGAL BAYAR. " else "") + "Skor ${score()}."
    )

    companion object {
        const val BASE_SPEND = 17.0
        const val BASE_TAX = 15.0
        const val START_DEBT = 40.0
        const val MIN_SPEND = 10.0
        const val MAX_SPEND = 30.0
        const val MIN_TAX = 8.0
        const val MAX_TAX = 26.0
        /** Perubahan maksimum belanja atau pajak per bulan (poin % PDB). */
        const val MAX_MOVE = 1.0
        const val DEFAULT_DEBT = 120.0
        const val TREND_GROWTH = 4.0
        const val REV_ELASTICITY = 1.2
        const val PREM_LIN = 0.03
        const val PREM_QUAD = 0.0015
        const val DEBT_PENALTY = 0.01
        /** Skala dorongan permintaan per % PDB defisit struktural di atas basis. */
        @JvmField var FISCAL_K = 0.6
    }
}
