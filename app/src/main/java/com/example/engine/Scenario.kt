package com.example.engine

import kotlin.random.Random

/**
 * Skenario awal yang dibangkitkan dari seed: setiap permainan baru dimulai dari premis, tanggal, ukuran usaha,
 * susunan utang, kondisi makro, nama eksekutif, dan jadwal kejadian yang berbeda.
 */
class Scenario(
    val id: String,
    val title: String,
    val story: String,
    val turn0: Int,
    val size: Double,
    val cash: Double,
    val ar: Double,
    val inv: Double,
    val pay: Double,
    val fa: Double,
    val stuffedPerMonth: Double,
    val deferredMonths: Int,
    val cogsBase: Double,
    val demandBase: Double,
    val bankB: Double,
    val bankBRate: Double,
    val bankBMaturityOffset: Int,
    val bankBCov: Double,
    val bankLama: Double,
    val bankLamaRate: Double,
    val rcfLimit: Double,
    val inflation: Double,
    val policy: Double,
    val kurs0: Double,
    val electionOffset: Int,
    val spinOfferOffset: Int,
    val workers: Int,
    val execNames: Map<String, Pair<String, String>>,
    val execLoyaltyBias: Double,
    val salesStartsStuffing: Boolean,
    val initialEvents: List<Pair<String, Int>>,
    val sanctionAfter: Int
) {
    val baseRevenue: Double get() = 13704.0 * size
    val baseOpex: Double get() = 2300.0 * size
    val baseMaint: Double get() = 610.0 * size
    val equityCure: Double get() = 4000.0 * size
}

object ScenarioGen {
    private val cooNames = listOf("Surya Atmaja" to "SA", "Teguh Prasetyo" to "TP", "Anwar Siregar" to "AS", "Yohanes Pardede" to "YP", "Rahmat Hidayat" to "RH", "Gilang Permana" to "GP")
    private val salesNames = listOf("Baskoro Wijaya" to "BW", "Dimas Aryanto" to "DA", "Kevin Salim" to "KS", "Bonar Simatupang" to "BS", "Reza Mahendra" to "RM", "Lukas Tanuwijaya" to "LT")
    private val factoryNames = listOf("Ir. Hendra Gunawan" to "HG", "Ir. Sutrisno" to "IS", "Ir. Made Wirawan" to "MW", "Ir. Zulkifli" to "ZK", "Ir. Paulus Ginting" to "PG", "Ir. Slamet Raharjo" to "SR")
    private val cfoNames = listOf("Ratna Pramudita" to "RP", "Fitri Handayani" to "FH", "Lina Susilowati" to "LS", "Grace Wibowo" to "GW", "Dewi Anggraini" to "DA", "Kartika Sari" to "KS")

    private class Premise(
        val id: String, val title: String, val story: String, val weight: Int
    )

    private val premises = listOf(
        Premise("warisan", "Warisan bangkrut",
            "Pendiri meninggal mendadak. Anda mewarisi pabrik tekstil dengan mesin tua, kas tipis, utang bank yang segera jatuh tempo, dan tuntutan serikat yang menunggu jawaban.", 3),
        Premise("pelanggan_tunggal", "Bergantung pada satu pelanggan",
            "Pabrik efisien dengan margin bagus, tetapi sekitar 60% penjualan mengalir ke satu peritel besar yang gemar menekan termin pembayaran. Piutang menumpuk di satu nama.", 2),
        Premise("mesin_tua", "Mesin tua, utang ringan",
            "Utang kecil dan kas cukup, tetapi perawatan mesin sudah lama ditunda. Penghematan bertahun-tahun meninggalkan utang teknis yang belum terlihat di laporan.", 2),
        Premise("ekspansi_berutang", "Ekspansi berutang",
            "Pemilik lama baru memperluas pabrik dengan kredit. Aset besar, utang besar, bunga mencekik, dan kas hampir kering saat pasar mulai melambat.", 2),
        Premise("pasar_lesu", "Pasar lesu",
            "Pabrik sehat dan kas lumayan, tetapi permintaan melemah dan pesaing bersiap perang harga. Siapa yang kuat bertahan akan menguasai pasar saat pulih.", 2),
        Premise("sengketa_serikat", "Sengketa serikat",
            "Hubungan dengan pekerja memburuk. Serikat menuntut kenaikan upah besar dan mengancam mogok, sementara kas dan utang berada pada level sedang.", 2)
    )

    fun make(seed: Long): Scenario {
        val r = Random(seed * 48271L + 17L)
        var pick = r.nextInt(premises.sumOf { it.weight })
        var p = premises.first()
        for (x in premises) { if (pick < x.weight) { p = x; break }; pick -= x.weight }
        fun j(base: Double, spread: Double) = base * (1.0 + (r.nextDouble() * 2.0 - 1.0) * spread)

        val size = j(1.0, 0.18)
        var cash = 6200.0; var ar = 26080.0; var inv = 17580.0; var pay = 11600.0; var fa = 64200.0
        var bankB = 24000.0; var bankLama = 18000.0; var rcf = 4000.0
        var deferred = 3; var cogs = 0.638; var demandBase = 1.0
        var bankBMat = 6 + r.nextInt(5)
        var bankBCov = 1.75
        var stuff = 400.0
        var election = 9 + r.nextInt(22)
        var spinOffer = r.nextInt(8)
        var salesStuff = true
        val events = mutableListOf<Pair<String, Int>>()
        when (p.id) {
            "warisan" -> { cogs -= 0.006; events += "union" to (1 + r.nextInt(3)) }
            "pelanggan_tunggal" -> {
                ar *= 1.25; cash = 7500.0; cogs -= 0.012; bankB = 20000.0
                events += "retail_terms" to (1 + r.nextInt(2))
            }
            "mesin_tua" -> {
                deferred = 6 + r.nextInt(3); bankB = 12000.0; bankLama = 10000.0; cash = 8200.0; fa = 52000.0
                cogs += 0.008; bankBMat = 12 + r.nextInt(6); rcf = 6000.0; stuff = 200.0
            }
            "ekspansi_berutang" -> {
                fa = 80000.0; bankB = 30000.0; bankLama = 22000.0; cash = 5400.0; cogs -= 0.012
                bankBMat = 6 + r.nextInt(3); bankBCov = 2.0; stuff = 300.0
            }
            "pasar_lesu" -> {
                cash = 9500.0; cogs -= 0.02; demandBase = 0.93; stuff = 0.0; salesStuff = r.nextBoolean()
                events += "new_entrant" to (1 + r.nextInt(3))
            }
            "sengketa_serikat" -> {
                cash = 6400.0; bankB = 17000.0; cogs -= 0.008
                events += "union" to 1
            }
        }
        val infl = j(4.8, 0.45).coerceIn(2.2, 8.0)
        val policy = ((infl + 2.5 + (r.nextDouble() * 2.0 - 1.0)).coerceIn(4.5, 9.5) * 4.0).toInt() / 4.0
        val kurs0 = (15450.0 * (0.92 + r.nextDouble() * 0.18)).toInt().toDouble()

        fun pickName(list: List<Pair<String, String>>) = list[r.nextInt(list.size)]
        val names = mapOf(
            "exec_coo" to pickName(cooNames), "exec_sales" to pickName(salesNames),
            "exec_factory" to pickName(factoryNames), "exec_cfo" to pickName(cfoNames)
        )
        val s = size
        return Scenario(
            id = p.id, title = p.title, story = p.story,
            turn0 = 33 + r.nextInt(43), size = s,
            cash = j(cash, 0.12) * s, ar = j(ar, 0.08) * s, inv = j(inv, 0.08) * s, pay = j(pay, 0.08) * s, fa = j(fa, 0.07) * s,
            stuffedPerMonth = stuff * s, deferredMonths = deferred, cogsBase = cogs + (r.nextDouble() * 0.016 - 0.008),
            demandBase = demandBase,
            bankB = j(bankB, 0.06) * s, bankBRate = j(10.5, 0.06), bankBMaturityOffset = bankBMat, bankBCov = bankBCov,
            bankLama = j(bankLama, 0.06) * s, bankLamaRate = j(11.75, 0.05), rcfLimit = rcf * s,
            inflation = infl, policy = policy, kurs0 = kurs0,
            electionOffset = election, spinOfferOffset = spinOffer,
            workers = (340 * s).toInt(), execNames = names, execLoyaltyBias = r.nextDouble() * 0.2 - 0.1,
            salesStartsStuffing = salesStuff, initialEvents = events,
            sanctionAfter = 3 + r.nextInt(14)
        )
    }
}
