package com.example.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Sistem kejadian: kartu keputusan dan pemberitahuan yang muncul dari kondisi dunia dan skenario.
 * Peluang tiap kartu dipengaruhi inflasi, pengangguran, bunga, kurs, umur piutang, perawatan mesin, riwayat etika, dan lainnya.
 * Setiap pilihan punya hasil yang berisiko (tidak pasti); kartu yang dibiarkan sampai tenggat memakai pilihan bawaan (biasanya yang terburuk).
 */
data class Effect(val kind: String, val a: Double = 0.0, val b: Int = 0, val s: String = "")

class Outcome(val p: Double, val text: String, val effects: List<Effect> = emptyList())

class EventOption(
    val id: String,
    val title: String,
    val apCost: Int,
    val impact: String,
    val advisor: String,
    val tone: Tone,
    val outcomes: List<Outcome>
)

class GameEvent(
    val id: String,
    val templateId: String,
    val category: String,
    val title: String,
    val body: String,
    val warning: String,
    val options: List<EventOption>,
    val raisedTurn: Int,
    var deadlineTurn: Int,
    val defaultOptionId: String
)

class EventCtx(
    val turn: Int,
    val turn0: Int,
    val size: Double,
    val costIndex: Double,
    val inflation: Double,
    val unemployment: Double,
    val policyRate: Double,
    val policyDelta: Double,
    val kursDepr6m: Double,
    val econ: Double,
    val cash: Double,
    val dso: Double,
    val arShare: Double,
    val deferredMonths: Int,
    val dualSourcing: Boolean,
    val strikeActive: Boolean,
    val covenantBreaches: Int,
    val ethicsLoad: Double,
    val dumping: Boolean,
    val cottonIndex: Double,
    val presentExecs: List<Pair<String, String>>,
    val workers: Int,
    val hasFloatingDebt: Boolean,
    val bankRelation: Double,
    val isPublic: Boolean = false,
    val profitFalling: Boolean = false,
    // C1: kondisi dunia tambahan untuk pemicu kartu
    val panic: Double = 10.0,
    val ratingIdx: Int = 3,
    val tension: Double = 0.0,
    val sanctioned: Boolean = false,
    val blockade: Boolean = false,
    val monthsToElection: Int = 99,
    val util: Double = 0.8,
    val priceAdj: Int = 0,
    val sentiment: Double = 0.0,
    val oilIndex: Double = 1.0,
    val gapPct: Double = 0.0,
    val era: Int = 1
) {
    fun m(x: Double) = x * size * costIndex
    val wu: Double get() = 24.6 * size * costIndex
}

class Template(
    val id: String,
    val category: String,
    val cooldown: Int,
    val weight: (EventCtx) -> Double,
    val build: (EventCtx, Random, String, Int) -> GameEvent?
)

class Notice(val text: String, val tone: Tone, val effects: List<Effect>)

class NoticeTemplate(
    val id: String,
    val cooldown: Int,
    val weight: (EventCtx) -> Double,
    val build: (EventCtx, Random) -> Notice
)

object EventDeck {
    private fun o(id: String, title: String, ap: Int, impact: String, advisor: String, tone: Tone, vararg outs: Outcome) =
        EventOption(id, title, ap, impact, advisor, tone, outs.toList())

    private fun sure(text: String, vararg e: Effect) = Outcome(1.0, text, e.toList())

    private fun fmt(x: Double) = KapitalEngine.fmt(x)

    private fun ev(id: String, tpl: String, cat: String, title: String, body: String, warning: String, options: List<EventOption>,
                   turn: Int, window: Int, default: String) =
        GameEvent(id, tpl, cat, title, body, warning, options, turn, turn + window - 1, default)

    val baseTemplates: List<Template> = listOf(
        // ------------------------------------------------------------------ SDM dan serikat
        Template("union", "SDM", 14, { c -> 0.6 + max(0.0, c.inflation - 4.0) * 0.35 + (if (c.unemployment < 4.5) 0.5 else 0.0) }) { c, r, id, t ->
            val d = (2.0 + 1.6 * c.inflation).coerceIn(5.0, 14.0).roundToInt()
            val cause = listOf("harga beras dan kebutuhan pokok naik", "inflasi memangkas daya beli upah", "laba perusahaan naik tetapi upah tertinggal",
                "PHK di pabrik tetangga membuat pekerja cemas", "pabrik pesaing menawarkan upah lebih tinggi").random(r)
            ev(id, "union", "SDM", "Tuntutan upah Serikat Pekerja Tekstil Kalimas",
                "Serikat menuntut kenaikan upah $d% karena $cause. Mereka memberi tenggat 2 giliran sebelum mogok, tepat saat pabrik mengejar target pengiriman.",
                "Pengurus serikat sudah dua kali muncul di koran lokal bulan ini.",
                listOf(
                    o("a", "Setujui kenaikan upah ${d - 3}% dan tunjangan", 1, "Biaya naik ±${fmt(c.wu * (d - 3))} jt RS per bulan; mogok batal", "menurut Direktur Operasi", Tone.GOOD,
                        sure("Serikat puas. Upah naik ${d - 3}%.", Effect("wage", c.wu * (d - 3)))),
                    o("b", "Tawarkan bagi hasil berbasis mutu", 2, "Biaya awal lebih kecil (±${fmt(c.wu * 2.4)} jt RS per bulan), tetapi serikat skeptis", "menurut Direktur Operasi", Tone.INFO,
                        Outcome(0.7, "Serikat menerima skema bagi hasil.", listOf(Effect("wage", c.wu * 2.4))),
                        Outcome(0.3, "Serikat menolak dan mogok 2 bulan; skema bagi hasil tetap berlaku.", listOf(Effect("wage", c.wu * 2.4), Effect("strike", 0.0, 2)))),
                    o("c", "Tolak dan sewa tenaga kontrak", 1, "Hemat kas sekarang; risiko mogok dan kerusakan mesin naik", "menurut Kepala Pabrik", Tone.BAD,
                        Outcome(0.45, "Serikat mundur untuk sementara; tenaga kontrak kurang terampil.", listOf(Effect("wage", c.wu * 1.6), Effect("unskilled", 0.0, 6))),
                        Outcome(0.55, "Serikat mogok 2 bulan; tenaga kontrak kurang terampil.", listOf(Effect("wage", c.wu * 1.6), Effect("strike", 0.0, 2), Effect("unskilled", 0.0, 6))))
                ), t, 2, "z")
                .let { e -> GameEvent(e.id, e.templateId, e.category, e.title, e.body, e.warning,
                    e.options + o("z", "(Dibiarkan sampai tenggat)", 0, "", "", Tone.BAD,
                        sure("Tenggat lewat: mogok massal 2 bulan, lalu upah naik 10% sebagai syarat damai.", Effect("strike", 0.0, 2), Effect("wage", c.wu * 10))), e.raisedTurn, e.deadlineTurn, "z") }
        },
        Template("poach", "SDM", 18, { c -> if (c.presentExecs.isNotEmpty()) 0.5 + (if (c.cash > 0) 0.0 else 0.0) else 0.0 }) { c, r, id, t ->
            val (xid, name) = c.presentExecs.random(r)
            ev(id, "poach", "SDM", "Pesaing membajak $name",
                "Headhunter pesaing menawarkan $name posisi dengan gaji 40% lebih tinggi. Ia meminta Anda menjawab sebelum akhir pekan.",
                "Beberapa bulan terakhir $name sering rapat tertutup dengan pihak luar.",
                listOf(
                    o("a", "Tawarkan bonus penahanan", 1, "Biaya ±${fmt(c.m(300.0))} jt RS; kemungkinan besar bertahan", "menurut Direktur Keuangan", Tone.GOOD,
                        Outcome(0.8, "$name bertahan setelah menerima bonus.", listOf(Effect("cost", c.m(300.0)), Effect("loyalty", 0.1, 0, xid))),
                        Outcome(0.2, "$name tetap pergi walau sudah diberi bonus.", listOf(Effect("cost", c.m(300.0)), Effect("exec_quit", 0.0, 3, xid)))),
                    o("b", "Ikat dengan klausul non-kompetisi", 2, "Murah, tetapi memperburuk hubungan", "menurut penasihat hukum", Tone.INFO,
                        Outcome(0.5, "$name bertahan dengan enggan.", listOf(Effect("cost", c.m(80.0)), Effect("loyalty", -0.15, 0, xid))),
                        Outcome(0.5, "$name pergi dan membawa sebagian pelanggan.", listOf(Effect("cost", c.m(80.0)), Effect("exec_quit", 0.0, 3, xid), Effect("lostcust", 0.02)))),
                    o("c", "Biarkan pergi", 0, "Posisi kosong 3 giliran; AP berkurang", "menurut Anda", Tone.WARN,
                        sure("$name pindah ke pesaing.", Effect("exec_quit", 0.0, 3, xid), Effect("lostcust", 0.01)))
                ), t, 1, "c")
        },
        Template("accident", "SDM", 20, { c -> 0.35 + c.deferredMonths * 0.08 }) { c, r, id, t ->
            ev(id, "accident", "SDM", "Kecelakaan kerja di lini tenun",
                "Seorang operator terluka karena pelindung mesin lepas. Dinas ketenagakerjaan akan memeriksa; pekerja lain gelisah.",
                "Laporan keselamatan internal bulan lalu mencatat pelindung mesin longgar.",
                listOf(
                    o("a", "Tanggung semua biaya dan perbaiki pelindung mesin", 1, "±${fmt(c.m(380.0))} jt RS", "menurut Kepala Pabrik", Tone.GOOD,
                        sure("Operator dirawat penuh; moral pekerja pulih.", Effect("cost", c.m(380.0)))),
                    o("b", "Bantu seperlunya, nyatakan kelalaian operator", 1, "±${fmt(c.m(100.0))} jt RS, risiko gugatan dan mogok solidaritas", "menurut penasihat hukum", Tone.WARN,
                        Outcome(0.55, "Kasus mereda.", listOf(Effect("cost", c.m(100.0)))),
                        Outcome(0.45, "Serikat menggelar mogok solidaritas 1 bulan dan denda keselamatan dijatuhkan.", listOf(Effect("cost", c.m(500.0)), Effect("strike", 0.0, 1))))
                ), t, 2, "b")
        },
        Template("min_wage", "SDM", 36, { c -> 0.25 + max(0.0, c.inflation - 4.0) * 0.2 }) { c, r, id, t ->
            ev(id, "min_wage", "Regulasi", "Pemerintah menaikkan upah minimum 7%",
                "Gubernur menetapkan kenaikan upah minimum 7% mulai bulan depan. Pengusaha tekstil memprotes; serikat bersorak.",
                "Draf kenaikan beredar di koran sebulan sebelumnya.",
                listOf(
                    o("a", "Patuhi dan serap kenaikan", 0, "Biaya ±${fmt(c.wu * 4)} jt RS per bulan", "menurut Direktur Keuangan", Tone.INFO,
                        sure("Upah minimum dipatuhi.", Effect("wage", c.wu * 4))),
                    o("b", "Otomasi sebagian lini (investasi)", 2, "Investasi ±${fmt(c.m(900.0))} jt RS; kenaikan upah efektif hanya ±${fmt(c.wu * 2)} jt RS per bulan", "menurut Direktur Operasi", Tone.GOOD,
                        Outcome(0.75, "Otomasi berjalan; kebutuhan tenaga turun.", listOf(Effect("capex", c.m(900.0)), Effect("wage", c.wu * 2))),
                        Outcome(0.25, "Otomasi tersendat; biaya membengkak dan produksi turun 1 bulan.", listOf(Effect("capex", c.m(1100.0)), Effect("wage", c.wu * 3), Effect("mod", 0.9, 1, "prod")))),
                    o("c", "Ajukan penangguhan kenaikan", 1, "Peluang berhasil ±50%", "menurut penasihat hukum", Tone.WARN,
                        Outcome(0.5, "Penangguhan disetujui 6 bulan.", listOf(Effect("wage", c.wu * 4, 0, "delay6"))),
                        Outcome(0.5, "Penangguhan ditolak dan serikat marah.", listOf(Effect("wage", c.wu * 4), Effect("cost", c.m(100.0)))))
                ), t, 1, "a")
        },
        // ------------------------------------------------------------------ Pelanggan
        Template("retail_terms", "Pelanggan", 16, { c -> 0.6 + (if (c.dso > 70) 0.0 else 0.4) }) { c, r, id, t ->
            ev(id, "retail_terms", "Pelanggan", "Grup Mahkota menuntut termin 120 hari",
                "Pelanggan ritel terbesar meminta termin pembayaran diperpanjang menjadi 120 hari dan diskon 3%, atau pesanan dialihkan ke pesaing.",
                "Pesaing Anda terlihat mengirim sampel ke Mahkota bulan lalu.",
                listOf(
                    o("a", "Terima termin 120 hari dan diskon 3%", 1, "Omzet aman; piutang membengkak ±25 hari, margin turun 3%", "menurut Direktur Penjualan", Tone.WARN,
                        sure("Pesanan aman, tetapi piutang bertambah.", Effect("mod", 25.0, 12, "dso"), Effect("mod", 0.97, 12, "rev"))),
                    o("b", "Tawar balik: termin 90 hari tanpa diskon", 2, "Peluang berhasil ±50%", "menurut Direktur Keuangan", Tone.INFO,
                        Outcome(0.5, "Mahkota menyetujui termin 90 hari.", listOf(Effect("mod", 12.0, 12, "dso"))),
                        Outcome(0.5, "Mahkota memindahkan sebagian pesanan ke pesaing.", listOf(Effect("lostcust", 0.06)))),
                    o("c", "Tolak", 0, "Risiko kehilangan sebagian besar pesanan", "menurut Anda", Tone.BAD,
                        Outcome(0.6, "Mahkota memotong pesanan Anda.", listOf(Effect("lostcust", 0.1))),
                        Outcome(0.4, "Mahkota mengalah; hubungan dingin.", emptyList()))
                ), t, 2, "c")
        },
        Template("customer_bankrupt", "Pelanggan", 14, { c -> 0.3 + max(0.0, c.unemployment - 5.5) * 0.35 + max(0.0, c.dso - 65.0) * 0.01 }) { c, r, id, t ->
            val loss = c.m(700.0) * (0.7 + r.nextDouble() * 0.8)
            ev(id, "customer_bankrupt", "Pelanggan", "Peritel pelanggan Anda gagal bayar",
                "Sebuah jaringan peritel menengah menunda pembayaran dan mengajukan penundaan kewajiban utang. Tagihan Anda sekitar ${fmt(loss)} jt RS.",
                "Peritel itu sudah dua kali memperpanjang termin bulan lalu.",
                listOf(
                    o("a", "Ajukan tagihan di pengadilan niaga", 1, "Biaya hukum ±${fmt(c.m(60.0))} jt RS; bisa menarik kembali sebagian", "menurut penasihat hukum", Tone.INFO,
                        Outcome(0.5, "Pengadilan mengembalikan sebagian tagihan.", listOf(Effect("cost", c.m(60.0)), Effect("writeoff", loss * 0.55))),
                        Outcome(0.5, "Aset peritel tak cukup; hampir semua tagihan hangus.", listOf(Effect("cost", c.m(60.0)), Effect("writeoff", loss)))),
                    o("b", "Hapus buku sekarang", 0, "Rugi langsung, tanpa biaya tambahan", "menurut Direktur Keuangan", Tone.WARN,
                        sure("Piutang dihapusbukukan.", Effect("writeoff", loss)))
                ), t, 2, "b")
        },
        Template("fashion_trend", "Pasar", 14, { c -> 0.4 + (if (c.econ > 1.0) 0.3 else 0.0) }) { c, r, id, t ->
            ev(id, "fashion_trend", "Pasar", "Tren motif baru melonjak",
                "Influencer mempopulerkan motif batik modern. Pesanan ritel naik, tetapi tren seperti ini sering hilang dalam beberapa bulan.",
                "Media sosial sudah ramai membahasnya sepekan terakhir.",
                listOf(
                    o("a", "Tambah stok bahan dan produksi motif itu", 1, "Biaya ±${fmt(c.m(400.0))} jt RS; permintaan naik 8% bila tren bertahan", "menurut Direktur Penjualan", Tone.GOOD,
                        Outcome(0.6, "Tren bertahan: penjualan naik selama 3 bulan.", listOf(Effect("cost", c.m(400.0)), Effect("mod", 1.08, 3, "rev"))),
                        Outcome(0.4, "Tren cepat mati: stok motif menumpuk.", listOf(Effect("cost", c.m(400.0)), Effect("mod", 1.02, 1, "rev")))),
                    o("b", "Abaikan", 0, "Tanpa biaya", "menurut Anda", Tone.NEUTRAL, sure("Peluang dilewatkan."))
                ), t, 1, "b")
        },
        Template("quality_recall", "Pelanggan", 18, { c -> 0.3 + c.deferredMonths * 0.1 }) { c, r, id, t ->
            ev(id, "quality_recall", "Pelanggan", "Pelanggan mengembalikan satu batch kain cacat",
                "Satu batch kain luntur ditemukan oleh peritel. Mereka meminta ganti rugi dan mengancam menghentikan pesanan.",
                "Tingkat cacat pada pemeriksaan mutu naik tipis dua bulan terakhir.",
                listOf(
                    o("a", "Tarik batch dan ganti penuh", 1, "±${fmt(c.m(520.0))} jt RS", "menurut Kepala Pabrik", Tone.GOOD,
                        sure("Batch ditarik; pelanggan tetap loyal.", Effect("cost", c.m(520.0)))),
                    o("b", "Salahkan pemasok zat warna", 1, "±${fmt(c.m(100.0))} jt RS; berisiko bila pemasok membantah", "menurut Direktur Operasi", Tone.WARN,
                        Outcome(0.4, "Pemasok mengakui dan memberi kompensasi.", listOf(Effect("cost", c.m(100.0)))),
                        Outcome(0.6, "Pemasok membantah; peritel kehilangan kepercayaan.", listOf(Effect("cost", c.m(300.0)), Effect("lostcust", 0.03)))),
                    o("c", "Abaikan keluhan", 0, "Tanpa biaya sekarang", "menurut Anda", Tone.BAD,
                        Outcome(0.5, "Peritel menghentikan sebagian pesanan.", listOf(Effect("lostcust", 0.05))),
                        Outcome(0.5, "Peritel menggugat dan menang.", listOf(Effect("fine", c.m(900.0)), Effect("lostcust", 0.04))))
                ), t, 2, "c")
        },
        // ------------------------------------------------------------------ Pemasok dan logistik
        Template("cotton_prepay", "Pemasok", 14, { c -> 0.3 + max(0.0, c.cottonIndex - 1.05) * 1.2 }) { c, r, id, t ->
            ev(id, "cotton_prepay", "Pemasok", "Pemasok kapas menuntut bayar di muka",
                "Gagal panen lokal membuat pemasok kapas Tarsis hanya mau mengirim bila dibayar di muka untuk tiga bulan.",
                "Harga kapas sudah naik dalam dua bulan terakhir.",
                listOf(
                    o("a", "Bayar di muka (diskon 3%)", 1, "Kas keluar ±${fmt(c.m(1300.0))} jt RS menjadi persediaan; HPP turun 3% selama 3 bulan", "menurut Direktur Keuangan", Tone.INFO,
                        sure("Kapas tiga bulan dibayar di muka.", Effect("prepay", c.m(1300.0)), Effect("mod", 0.97, 3, "cogs"))),
                    o("b", "Tolak dan beli di pasar spot", 0, "HPP naik ±4% selama 3 bulan", "menurut Direktur Operasi", Tone.WARN,
                        sure("Anda membeli di pasar spot dengan harga lebih mahal.", Effect("mod", 1.04, 3, "cogs"))),
                    o("c", "Cari pemasok alternatif di Velmora", 2, "HPP naik ±2%; ada risiko sanksi", "menurut Direktur Pengadaan", Tone.WARN,
                        Outcome(0.7, "Pemasok Velmora mengirim tepat waktu.", listOf(Effect("mod", 1.02, 3, "cogs"))),
                        Outcome(0.3, "Pemasok Velmora ternyata terkena sanksi; Anda didenda.", listOf(Effect("mod", 1.02, 3, "cogs"), Effect("fine", c.m(400.0)))))
                ), t, 2, "b")
        },
        Template("supplier_hike", "Pemasok", 16, { c -> 0.5 + (if (c.dualSourcing) 0.0 else 0.4) }) { c, r, id, t ->
            ev(id, "supplier_hike", "Pemasok", "Kestrel menaikkan harga suku cadang 9%",
                "Kestrel Precision Loom Parts mengumumkan kenaikan harga 9% dengan alasan biaya baja. Anda tahu mereka tahu Anda bergantung pada mereka.",
                "Harga baja dunia memang naik, tetapi tidak sebesar itu.",
                listOf(
                    o("a", "Terima kenaikan", 0, "Biaya perawatan dan HPP naik ±1% selama 6 bulan", "menurut Direktur Operasi", Tone.WARN,
                        sure("Kenaikan diterima.", Effect("mod", 1.01, 6, "cogs"))),
                    o("b", "Aktifkan pemasok kedua (dual sourcing)", 2, "±${fmt(c.m(300.0))} jt RS; HPP +1,2% permanen, tetapi daya tawar pulih", "menurut Direktur Pengadaan", Tone.GOOD,
                        sure("Pemasok kedua disiapkan.", Effect("cost", c.m(300.0)), Effect("dual", 1.0))),
                    o("c", "Negosiasi keras", 1, "Peluang berhasil ±45%", "menurut Anda", Tone.INFO,
                        Outcome(0.45, "Kestrel menurunkan kenaikan menjadi 3%.", listOf(Effect("mod", 1.003, 6, "cogs"))),
                        Outcome(0.55, "Kestrel menunda pengiriman sebagai balasan.", listOf(Effect("mod", 1.01, 6, "cogs"), Effect("mod", 0.9, 1, "prod"))))
                ), t, 2, "a")
        },
        Template("port_congestion", "Logistik", 14, { c -> 0.35 }) { c, r, id, t ->
            ev(id, "port_congestion", "Logistik", "Kontainer tertahan di pelabuhan",
                "Antrean kapal membuat kontainer kapas Anda tertahan 3 minggu. Gudang hampir kosong.",
                "Kantor pelabuhan sudah menyiarkan peringatan antrean.",
                listOf(
                    o("a", "Kirim dengan kargo udara untuk bahan kritis", 1, "±${fmt(c.m(350.0))} jt RS", "menurut Direktur Operasi", Tone.INFO,
                        sure("Bahan kritis tiba lewat udara.", Effect("cost", c.m(350.0)))),
                    o("b", "Tunggu antrean", 0, "HPP naik ±4% selama 3 bulan", "menurut Anda", Tone.WARN,
                        sure("Pabrik berjalan dengan stok tipis dan bahan mahal.", Effect("mod", 1.04, 3, "cogs"), Effect("mod", 0.95, 1, "prod")))
                ), t, 1, "b")
        },
        Template("power_outage", "Infrastruktur", 14, { c -> 0.5 }) { c, r, id, t ->
            ev(id, "power_outage", "Infrastruktur", "Pemadaman listrik bergilir",
                "Kekurangan pasokan batu bara membuat listrik padam 6 jam per hari di kawasan industri Kalimas selama sebulan.",
                "Perusahaan listrik negara sudah memberi peringatan lewat pengumuman kawasan.",
                listOf(
                    o("a", "Sewa genset solar", 1, "±${fmt(c.m(220.0))} jt RS", "menurut Direktur Operasi", Tone.GOOD,
                        sure("Pabrik tetap berjalan dengan genset.", Effect("cost", c.m(220.0)))),
                    o("b", "Pangkas shift", 0, "Produksi turun 15% selama 2 bulan", "menurut Kepala Pabrik", Tone.WARN,
                        sure("Produksi berkurang.", Effect("mod", 0.85, 2, "prod"))),
                    o("c", "Lobi pengelola kawasan agar didahulukan", 2, "Peluang berhasil ±50%", "menurut Anda", Tone.INFO,
                        Outcome(0.5, "Kawasan Anda mendapat pasokan prioritas.", emptyList()),
                        Outcome(0.5, "Lobi gagal; pabrik padam berkali-kali.", listOf(Effect("mod", 0.8, 2, "prod"))))
                ), t, 1, "b")
        },
        Template("flood", "Infrastruktur", 30, { c -> 0.2 }) { c, r, id, t ->
            ev(id, "flood", "Infrastruktur", "Banjir merendam gudang",
                "Hujan ekstrem membuat sungai meluap. Gudang bahan baku terendam sebagian.",
                "Prakiraan cuaca dua hari sebelumnya sudah menyebut potensi banjir.",
                listOf(
                    o("a", "Perbaikan cepat dan kirim ulang bahan", 1, "±${fmt(c.m(300.0))} jt RS plus kehilangan persediaan 5%", "menurut Direktur Operasi", Tone.INFO,
                        sure("Gudang dibersihkan dan stok diganti.", Effect("cost", c.m(300.0)), Effect("invloss", 0.05))),
                    o("b", "Klaim asuransi lalu perbaiki", 1, "Asuransi mengganti sebagian bila polis berlaku", "menurut Direktur Keuangan", Tone.GOOD,
                        Outcome(0.5, "Klaim cair 60% dari kerugian.", listOf(Effect("invloss", 0.05), Effect("income", c.m(280.0)))),
                        Outcome(0.5, "Klaim ditolak karena klausul pengecualian banjir.", listOf(Effect("invloss", 0.08), Effect("cost", c.m(300.0)))))
                ), t, 1, "a")
        },
        Template("cyber", "Teknologi", 24, { c -> 0.2 }) { c, r, id, t ->
            ev(id, "cyber", "Teknologi", "Serangan ransomware",
                "Sistem pemesanan dan akuntansi terkunci. Penyerang meminta tebusan dalam mata uang kripto.",
                "Beberapa perusahaan di kawasan industri mengalami hal serupa bulan lalu.",
                listOf(
                    o("a", "Bayar tebusan", 1, "±${fmt(c.m(450.0))} jt RS; kunci tidak selalu dikirim", "menurut konsultan keamanan", Tone.WARN,
                        Outcome(0.7, "Data dipulihkan.", listOf(Effect("cost", c.m(450.0)))),
                        Outcome(0.3, "Penyerang menghilang setelah dibayar.", listOf(Effect("cost", c.m(450.0)), Effect("mod", 0.8, 1, "prod")))),
                    o("b", "Pulihkan dari cadangan", 2, "Produksi turun 1 bulan; data bisa hilang", "menurut Direktur Operasi", Tone.INFO,
                        Outcome(0.7, "Cadangan bagus; sistem pulih.", listOf(Effect("mod", 0.85, 1, "prod"), Effect("cost", c.m(120.0)))),
                        Outcome(0.3, "Cadangan sebagian rusak; data bulan ini hilang.", listOf(Effect("mod", 0.7, 2, "prod"), Effect("cost", c.m(300.0))))),
                    o("c", "Abaikan", 0, "Sistem terkunci lama", "menurut Anda", Tone.BAD,
                        sure("Pabrik berjalan manual berminggu-minggu.", Effect("mod", 0.6, 2, "prod"), Effect("cost", c.m(600.0))))
                ), t, 1, "c")
        },
        // ------------------------------------------------------------------ Negara dan regulasi
        Template("tax_audit", "Regulasi", 24, { c -> 0.4 }) { c, r, id, t ->
            ev(id, "tax_audit", "Regulasi", "Pemeriksaan pajak",
                "Kantor pajak mengirim surat pemeriksaan untuk tiga tahun terakhir. Petugas memberi isyarat bahwa urusan bisa \"dipercepat\".",
                "Pemeriksaan serupa menimpa beberapa pabrik tekstil lain tahun ini.",
                listOf(
                    o("a", "Kooperatif dan sewa konsultan pajak", 2, "±${fmt(c.m(250.0))} jt RS; risiko koreksi kecil", "menurut Direktur Keuangan", Tone.GOOD,
                        Outcome(0.9, "Pemeriksaan selesai tanpa koreksi besar.", listOf(Effect("cost", c.m(250.0)))),
                        Outcome(0.1, "Ada koreksi pajak.", listOf(Effect("cost", c.m(250.0)), Effect("fine", c.m(500.0))))),
                    o("b", "Kooperatif tanpa konsultan", 1, "±${fmt(c.m(60.0))} jt RS; risiko koreksi sedang", "menurut Anda", Tone.INFO,
                        Outcome(0.7, "Pemeriksaan selesai.", listOf(Effect("cost", c.m(60.0)))),
                        Outcome(0.3, "Petugas menemukan selisih; koreksi pajak dan denda.", listOf(Effect("cost", c.m(60.0)), Effect("fine", c.m(700.0))))),
                    o("c", "\"Selesaikan secara kekeluargaan\" (suap)", 1, "±${fmt(c.m(200.0))} jt RS; cepat tetapi meninggalkan jejak", "menurut perantara", Tone.BAD,
                        Outcome(0.9, "Pemeriksaan ditutup.", listOf(Effect("cost", c.m(200.0)), Effect("ethics", 2.0))),
                        Outcome(0.1, "Petugas menolak dan melaporkan Anda.", listOf(Effect("fine", c.m(1500.0)), Effect("ethics", 3.0))))
                ), t, 2, "b")
        },
        Template("env_inspection", "Regulasi", 24, { c -> 0.3 }) { c, r, id, t ->
            ev(id, "env_inspection", "Regulasi", "Inspeksi limbah pewarna",
                "Kementerian lingkungan menemukan kadar limbah pewarna melebihi baku mutu di saluran pembuangan pabrik.",
                "Warga sekitar sudah beberapa kali mengeluh soal warna air sungai.",
                listOf(
                    o("a", "Bangun unit pengolah limbah", 2, "±${fmt(c.m(1100.0))} jt RS; selesai dan aman", "menurut Direktur Operasi", Tone.GOOD,
                        sure("IPAL dibangun; pabrik patuh.", Effect("capex", c.m(1100.0)))),
                    o("b", "Bayar denda dan tunda perbaikan", 1, "±${fmt(c.m(700.0))} jt RS; masalah berulang", "menurut Direktur Keuangan", Tone.WARN,
                        Outcome(0.6, "Denda dibayar; belum ada tindak lanjut.", listOf(Effect("fine", c.m(700.0)))),
                        Outcome(0.4, "Inspeksi ulang menutup lini pewarnaan 1 bulan.", listOf(Effect("fine", c.m(700.0)), Effect("mod", 0.8, 1, "prod")))),
                    o("c", "Beri \"uang pelicin\" kepada petugas", 1, "±${fmt(c.m(180.0))} jt RS; murah tetapi berbahaya", "menurut perantara", Tone.BAD,
                        Outcome(0.85, "Laporan inspeksi \"diperbaiki\".", listOf(Effect("cost", c.m(180.0)), Effect("ethics", 2.0))),
                        Outcome(0.15, "Disiarkan media sebagai kasus suap.", listOf(Effect("fine", c.m(1800.0)), Effect("ethics", 3.0), Effect("lostcust", 0.04))))
                ), t, 2, "b")
        },
        Template("export_incentive", "Regulasi", 30, { c -> 0.3 }) { c, r, id, t ->
            ev(id, "export_incentive", "Regulasi", "Insentif pajak untuk industri padat karya",
                "Pemerintah membuka insentif pengurangan pajak bagi pabrik padat karya yang mengajukan sertifikasi tenaga kerja.",
                "Program ini diumumkan menteri perindustrian pekan lalu.",
                listOf(
                    o("a", "Ajukan insentif", 2, "Biaya pengurusan ±${fmt(c.m(80.0))} jt RS; peluang berhasil ±60%", "menurut Direktur Keuangan", Tone.GOOD,
                        Outcome(0.6, "Insentif disetujui.", listOf(Effect("cost", c.m(80.0)), Effect("income", c.m(650.0)))),
                        Outcome(0.4, "Aplikasi ditolak karena kuota habis.", listOf(Effect("cost", c.m(80.0))))),
                    o("b", "Lewati", 0, "Tanpa biaya", "menurut Anda", Tone.NEUTRAL, sure("Peluang dilewatkan."))
                ), t, 2, "b")
        },
        Template("antidumping", "Regulasi", 24, { c -> if (c.dumping) 1.6 else 0.0 }) { c, r, id, t ->
            ev(id, "antidumping", "Regulasi", "Petisi anti-dumping terhadap kain impor",
                "Asosiasi tekstil mengajak produsen mengajukan petisi bea masuk anti-dumping terhadap kain murah Importir Velmora.",
                "Harga kain Velmora jauh di bawah biaya produksi wajar.",
                listOf(
                    o("a", "Ikut patungan petisi", 2, "±${fmt(c.m(400.0))} jt RS; peluang berhasil ±50%", "menurut asosiasi tekstil", Tone.GOOD,
                        Outcome(0.5, "Pemerintah menetapkan bea masuk sementara; banjir impor mereda.", listOf(Effect("cost", c.m(400.0)), Effect("end_dumping"))),
                        Outcome(0.5, "Petisi ditolak karena bukti dianggap kurang.", listOf(Effect("cost", c.m(400.0))))),
                    o("b", "Biarkan pesaing yang urus", 0, "Tanpa biaya", "menurut Anda", Tone.NEUTRAL, sure("Anda tidak ikut."))
                ), t, 2, "b")
        },
        // ------------------------------------------------------------------ Keuangan
        Template("credit_crunch", "Keuangan", 24, { c -> 0.2 + max(0.0, c.policyDelta) * 0.5 + max(0.0, c.unemployment - 6.0) * 0.2 }) { c, r, id, t ->
            ev(id, "credit_crunch", "Keuangan", "Bank-bank mengetatkan kredit",
                "Setelah kenaikan kredit macet industri, bank menaikkan spread dan mempersulit pinjaman baru. Pinjaman mengambang Anda ikut terdampak.",
                "Regulator perbankan memperingatkan kualitas kredit tekstil.",
                listOf(
                    o("a", "Kunci bunga tetap sekarang", 2, "Biaya ±1% saldo pinjaman mengambang; bunga tidak naik lagi", "menurut Direktur Keuangan", Tone.GOOD,
                        sure("Pinjaman mengambang dikunci ke bunga tetap.", Effect("fix_rates"), Effect("cost", c.m(250.0)))),
                    o("b", "Tunggu dan lihat", 0, "Spread naik ±1 poin selama 6 bulan", "menurut Anda", Tone.WARN,
                        sure("Spread kredit melebar.", Effect("credit_shock", 1.0)))
                ), t, 2, "b")
        },
        Template("dividend_demand", "Keuangan", 36, { c -> if (c.cash > 9000.0 * c.size) 1.0 else 0.0 }) { c, r, id, t ->
            ev(id, "dividend_demand", "Keuangan", "Keluarga pemilik meminta dividen khusus",
                "Pemegang saham keluarga mendengar kas Anda tebal dan meminta dividen khusus untuk membayar warisan.",
                "Salah satu ahli waris menelepon CFO untuk menanyakan saldo kas.",
                listOf(
                    o("a", "Bayar dividen penuh", 0, "Kas keluar ±${fmt(c.m(1500.0))} jt RS", "menurut Direktur Keuangan", Tone.WARN,
                        sure("Dividen khusus dibayar.", Effect("dividend", c.m(1500.0)))),
                    o("b", "Bayar separuh dan jelaskan rencana investasi", 1, "±${fmt(c.m(750.0))} jt RS", "menurut Anda", Tone.INFO,
                        Outcome(0.7, "Keluarga menerima.", listOf(Effect("dividend", c.m(750.0)))),
                        Outcome(0.3, "Keluarga kecewa dan menekan dewan komisaris.", listOf(Effect("dividend", c.m(750.0)), Effect("cost", c.m(200.0))))),
                    o("c", "Tolak", 0, "Tanpa biaya langsung, tetapi kepercayaan dewan turun dan keluarga bisa menekan (±${fmt(c.m(300.0))} jt RS)", "menurut Anda", Tone.WARN,
                        Outcome(0.6, "Keluarga menahan diri, tetapi hubungan dengan dewan memburuk.", listOf(Effect("board_trust", -3.0))),
                        Outcome(0.4, "Keluarga menekan lewat dewan komisaris dan menyewa penasihat hukum.", listOf(Effect("board_trust", -5.0), Effect("cost", c.m(300.0)))))
                ), t, 2, "c")
        },
        Template("used_machines", "Peluang", 24, { c -> 0.35 + c.deferredMonths * 0.05 }) { c, r, id, t ->
            ev(id, "used_machines", "Peluang", "Mesin tenun bekas pabrik tetangga yang bangkrut",
                "Kurator menawarkan 6 mesin tenun bekas dengan harga 40% di bawah harga baru. Kondisi baik tetapi tidak diuji penuh.",
                "Pabrik itu tutup bulan lalu; sebagian karyawannya melamar ke Anda.",
                listOf(
                    o("a", "Beli semua mesin", 2, "±${fmt(c.m(2000.0))} jt RS; mengurangi beban perawatan mesin lama", "menurut Kepala Pabrik", Tone.GOOD,
                        Outcome(0.75, "Mesin bagus; perawatan lebih ringan.", listOf(Effect("capex", c.m(2000.0)), Effect("deferred", -3.0))),
                        Outcome(0.25, "Beberapa mesin rusak tersembunyi.", listOf(Effect("capex", c.m(2000.0)), Effect("cost", c.m(400.0))))),
                    o("b", "Lewati", 0, "Tanpa biaya", "menurut Anda", Tone.NEUTRAL, sure("Penawaran dilewati."))
                ), t, 2, "b")
        },
        Template("fraud_internal", "Internal", 20, { c -> 0.2 + c.ethicsLoad * 0.05 }) { c, r, id, t ->
            ev(id, "fraud_internal", "Internal", "Selisih persediaan mencurigakan",
                "Penghitungan stok menunjukkan selisih besar di gudang bahan. Ada indikasi kolusi antara staf gudang dan pemasok.",
                "Stok opname terakhir sudah menunjukkan selisih kecil dua kali.",
                listOf(
                    o("a", "Audit forensik", 2, "±${fmt(c.m(150.0))} jt RS; biasanya menemukan pelaku", "menurut auditor", Tone.GOOD,
                        Outcome(0.8, "Pelaku ditemukan dan sebagian kerugian kembali.", listOf(Effect("cost", c.m(150.0)), Effect("income", c.m(350.0)))),
                        Outcome(0.2, "Audit tidak konklusif.", listOf(Effect("cost", c.m(150.0)), Effect("cost", c.m(300.0))))),
                    o("b", "Tegur staf gudang", 1, "Murah, tetapi tidak menyelesaikan", "menurut Kepala Pabrik", Tone.WARN,
                        sure("Selisih berlanjut.", Effect("cost", c.m(500.0)))),
                    o("c", "Abaikan", 0, "Tanpa biaya", "menurut Anda", Tone.BAD, sure("Selisih makin besar.", Effect("cost", c.m(800.0))))
                ), t, 2, "c")
        },
        Template("media_smear", "Reputasi", 20, { c -> 0.3 + c.ethicsLoad * 0.1 }) { c, r, id, t ->
            ev(id, "media_smear", "Reputasi", "Koran sensasional menuduh pabrik mencemari",
                "Kilat Sore memuat foto sungai berwarna di dekat pabrik dengan judul bombastis. Anda belum tahu siapa yang menyebarkan kisahnya.",
                "Seorang wartawan bertanya soal limbah ke humas sepekan lalu.",
                listOf(
                    o("a", "Klarifikasi dengan data", 1, "±${fmt(c.m(100.0))} jt RS", "menurut humas", Tone.GOOD,
                        Outcome(0.7, "Klarifikasi diterima publik.", listOf(Effect("cost", c.m(100.0)))),
                        Outcome(0.3, "Klarifikasi tenggelam; pelanggan ragu.", listOf(Effect("cost", c.m(100.0)), Effect("lostcust", 0.02)))),
                    o("b", "Bayar iklan untuk meredam", 1, "±${fmt(c.m(400.0))} jt RS", "menurut agen humas", Tone.WARN,
                        Outcome(0.6, "Berita mereda.", listOf(Effect("cost", c.m(400.0)))),
                        Outcome(0.4, "Dituduh membeli berita.", listOf(Effect("cost", c.m(400.0)), Effect("lostcust", 0.03), Effect("ethics", 1.0)))),
                    o("c", "Abaikan", 0, "Tanpa biaya", "menurut Anda", Tone.NEUTRAL,
                        Outcome(0.5, "Isu mereda sendiri.", emptyList()),
                        Outcome(0.5, "Isu membesar; pelanggan menjauh.", listOf(Effect("lostcust", 0.03))))
                ), t, 1, "c")
        },
        Template("cartel_offer", "Etika", 30, { c -> 0.3 + (if (c.econ < 0.98) 0.5 else 0.0) }) { c, r, id, t ->
            ev(id, "cartel_offer", "Etika", "Pesaing mengajak menyepakati harga",
                "Direktur pesaing mengajak makan siang dan menawarkan \"kesepakatan sehat\": kami tidak saling banting harga. Tidak ada tertulis.",
                "Perang harga beberapa bulan ini menggerus margin semua pabrik.",
                listOf(
                    o("a", "Ikut", 1, "Margin naik ±5% setahun; risiko penyelidikan KPPU bertahun-tahun kemudian", "menurut perantara", Tone.BAD,
                        sure("Harga mulai \"stabil\" di pasar.", Effect("mod", 1.05, 12, "rev"), Effect("ethics", 3.0))),
                    o("b", "Tolak", 0, "Tanpa efek", "menurut Anda", Tone.GOOD, sure("Anda menolak; perang harga berlanjut.")),
                    o("c", "Laporkan ke KPPU", 2, "Peluang pesaing terkena denda ±60%; hubungan industri memburuk", "menurut penasihat hukum", Tone.INFO,
                        Outcome(0.6, "KPPU menyelidiki dan menjatuhkan denda; pesaing melemah.", listOf(Effect("cost", c.m(100.0)), Effect("end_dumping"))),
                        Outcome(0.4, "Bukti kurang; Anda dijauhi asosiasi.", listOf(Effect("cost", c.m(100.0)), Effect("mod", 0.98, 6, "rev"))))
                ), t, 2, "b")
        },
        Template("bribe_permit", "Etika", 30, { c -> 0.25 }) { c, r, id, t ->
            ev(id, "bribe_permit", "Etika", "Izin impor mesin tertahan",
                "Izin impor suku cadang mesin tertahan di pelabuhan. Seorang \"fasilitator\" menawarkan menyelesaikannya dalam seminggu dengan biaya khusus.",
                "Pengurusan resmi biasanya memakan 2 bulan.",
                listOf(
                    o("a", "Bayar biaya khusus", 1, "±${fmt(c.m(250.0))} jt RS; cepat, ada jejak", "menurut fasilitator", Tone.BAD,
                        Outcome(0.9, "Izin keluar cepat.", listOf(Effect("cost", c.m(250.0)), Effect("ethics", 2.0), Effect("mod", 0.99, 3, "cogs"))),
                        Outcome(0.1, "Fasilitator ditangkap membawa nama Anda.", listOf(Effect("fine", c.m(1200.0)), Effect("ethics", 3.0)))),
                    o("b", "Tempuh jalur resmi", 0, "Produksi tersendat 2 bulan", "menurut Anda", Tone.GOOD,
                        sure("Izin keluar setelah dua bulan.", Effect("mod", 0.96, 2, "prod")))
                ), t, 1, "b")
        },
        Template("new_entrant", "Pasar", 30, { c -> 0.3 + (if (c.econ < 1.0) 0.3 else 0.0) }) { c, r, id, t ->
            ev(id, "new_entrant", "Pasar", "Startup tekstil masuk dengan harga sangat murah",
                "Perusahaan rintisan berdana ventura menjual kain di bawah biaya untuk merebut rak ritel. Pembeli mulai membandingkan harga.",
                "Pendiri startup itu mengakui di media akan \"membakar uang\" dua tahun.",
                listOf(
                    o("a", "Ikuti harga mereka sementara (perang harga)", 1, "Margin turun, pangsa terjaga", "menurut Direktur Penjualan", Tone.WARN,
                        sure("Anda menurunkan harga.", Effect("mod", 0.97, 4, "rev"), Effect("start_dumping", 5.0))),
                    o("b", "Pertahankan harga, tonjolkan mutu", 1, "Sebagian pelanggan beralih", "menurut Anda", Tone.INFO,
                        Outcome(0.5, "Pelanggan loyal bertahan.", emptyList()),
                        Outcome(0.5, "Sebagian ritel pindah ke startup.", listOf(Effect("lostcust", 0.04), Effect("start_dumping", 5.0))))
                ), t, 2, "b")
        },
        // ------------------------------------------------------------------ KARTU BERANTAI (hanya muncul sebagai akibat kartu/keputusan lain; bobot acak 0)
        Template("union_wildcat", "SDM", 12, { 0.0 }) { c, r, id, t ->
            ev(id, "union_wildcat", "SDM", "Mogok liar menyebar ke lini lain",
                "Penolakan tuntutan upah memicu mogok tanpa izin serikat pusat di dua lini. Pesanan ritel mulai terlambat dan pembeli menelepon.",
                "Efek berantai dari penolakan tuntutan upah.",
                listOf(
                    o("a", "Berunding langsung dengan wakil pekerja", 2, "Upah naik ±${fmt(c.wu * 5)} jt RS per bulan; mogok berakhir", "menurut Direktur Operasi", Tone.GOOD,
                        sure("Kesepakatan tercapai.", Effect("wage", c.wu * 5), Effect("loyalty", 0.1, 0, "exec_factory"))),
                    o("b", "Ancam PHK dan rekrut pengganti", 1, "Murah sekarang; produktivitas jatuh dan reputasi buruk", "menurut Kepala Pabrik", Tone.BAD,
                        Outcome(0.5, "Mogok pecah dan pekerja baru belum terampil.", listOf(Effect("strike", 0.0, 2), Effect("unskilled", 0.0, 6), Effect("lostcust", 0.015))),
                        Outcome(0.5, "Pekerja kembali bekerja dengan dendam.", listOf(Effect("unskilled", 0.0, 4), Effect("mod", 0.97, 4, "prod"))))
                ), t, 2, "b")
        },
        Template("labor_inspection", "Regulasi", 20, { 0.0 }) { c, r, id, t ->
            ev(id, "labor_inspection", "Regulasi", "Dinas ketenagakerjaan memeriksa keselamatan kerja",
                "Setelah kecelakaan di lini tenun, inspektur memeriksa seluruh pabrik dan menemukan beberapa pelanggaran standar keselamatan.",
                "Efek berantai dari kecelakaan kerja sebelumnya.",
                listOf(
                    o("a", "Perbaiki semua temuan segera", 2, "±${fmt(c.m(260.0))} jt RS", "menurut Kepala Pabrik", Tone.GOOD,
                        sure("Pabrik lolos pemeriksaan ulang.", Effect("capex", c.m(260.0)))),
                    o("b", "Perbaiki sebagian dan negosiasi denda", 1, "Denda ±${fmt(c.m(180.0))} jt RS; risiko pemeriksaan ulang", "menurut penasihat hukum", Tone.WARN,
                        Outcome(0.6, "Denda dibayar.", listOf(Effect("fine", c.m(180.0)))),
                        Outcome(0.4, "Pemeriksaan ulang menemukan pelanggaran sama; denda berlipat.", listOf(Effect("fine", c.m(520.0)), Effect("mod", 0.95, 1, "prod"))))
                ), t, 2, "b")
        },
        Template("retailer_audit", "Pasar", 20, { 0.0 }) { c, r, id, t ->
            ev(id, "retailer_audit", "Pasar", "Peritel besar mengaudit mutu pemasoknya",
                "Setelah masalah mutu, pembeli ritel terbesar mengirim tim audit ke pabrik dan mengancam mengurangi pesanan bila standar tidak dinaikkan.",
                "Efek berantai dari masalah mutu sebelumnya.",
                listOf(
                    o("a", "Investasi sistem kendali mutu", 2, "±${fmt(c.m(300.0))} jt RS; pesanan aman", "menurut Direktur Operasi", Tone.GOOD,
                        sure("Audit lulus bersyarat.", Effect("capex", c.m(300.0)), Effect("mod", 1.01, 6, "rev"))),
                    o("b", "Janji perbaikan tanpa investasi", 1, "Tanpa biaya; peritel bisa kehilangan sabar", "menurut Direktur Penjualan", Tone.BAD,
                        Outcome(0.5, "Peritel menunggu satu kuartal lagi.", emptyList()),
                        Outcome(0.5, "Peritel memangkas pesanan dan memindahkan sebagian ke pesaing.", listOf(Effect("lostcust", 0.05))))
                ), t, 3, "b")
        },
        Template("supplier_squeeze", "Keuangan", 20, { 0.0 }) { c, r, id, t ->
            ev(id, "supplier_squeeze", "Keuangan", "Pemasok menuntut bayar di muka",
                "Kabar piutang macet pelanggan besar menyebar. Pemasok kapas dan benang mulai meminta pembayaran di muka dan menahan kredit dagang.",
                "Efek berantai dari kebangkrutan pelanggan.",
                listOf(
                    o("a", "Bayar di muka sebagian", 1, "Kas keluar ±${fmt(c.m(450.0))} jt RS, kredit dagang pulih", "menurut Direktur Keuangan", Tone.INFO,
                        sure("Pemasok kembali memberi kredit.", Effect("prepay", c.m(450.0)))),
                    o("b", "Pindah ke pemasok lain", 2, "Biaya peralihan ±${fmt(c.m(120.0))} jt RS; mutu belum pasti", "menurut Direktur Operasi", Tone.WARN,
                        Outcome(0.6, "Pemasok baru bersedia memberi kredit.", listOf(Effect("cost", c.m(120.0)))),
                        Outcome(0.4, "Pemasok baru mengirim barang bermutu rendah.", listOf(Effect("cost", c.m(120.0)), Effect("mod", 0.97, 3, "prod"))))
                ), t, 2, "a")
        },
        Template("bank_early_review", "Keuangan", 24, { 0.0 }) { c, r, id, t ->
            ev(id, "bank_early_review", "Keuangan", "Bank meminta tinjauan dini kondisi keuangan",
                "Setelah pengetatan kredit, bank pemberi pinjaman meminta laporan tambahan dan menimbang ulang batas pinjaman Anda.",
                "Efek berantai dari pengetatan kredit.",
                listOf(
                    o("a", "Serahkan laporan lengkap dan rencana kas", 1, "Tanpa biaya; hubungan membaik bila angka sehat", "menurut Direktur Keuangan", Tone.GOOD,
                        sure("Bank menerima rencana Anda.", Effect("board_trust", 3.0))),
                    o("b", "Tunda dan cari bank lain", 2, "Risiko bank menaikkan spread", "menurut Anda", Tone.WARN,
                        Outcome(0.5, "Bank lain menolak.", listOf(Effect("credit_shock", 0.3))),
                        Outcome(0.5, "Bank lama menurunkan batas pinjaman.", listOf(Effect("credit_shock", 0.5))))
                ), t, 2, "b")
        },
        Template("journalist_probe", "Etika", 30, { 0.0 }) { c, r, id, t ->
            ev(id, "journalist_probe", "Etika", "Jurnalis investigasi mengajukan pertanyaan",
                "Seorang jurnalis bisnis menelepon dengan dokumen yang menyinggung praktik masa lalu perusahaan dan meminta tanggapan sebelum naik cetak.",
                "Jejak keputusan lama mulai terbaca pihak luar.",
                listOf(
                    o("a", "Akui, jelaskan, dan perbaiki", 2, "Reputasi tergores, tetapi beban etika turun", "menurut penasihat hukum", Tone.GOOD,
                        sure("Berita terbit apa adanya; dewan menghargai keterbukaan.", Effect("ethics_reduce", 1.0), Effect("lostcust", 0.01), Effect("board_trust", 2.0))),
                    o("b", "Ancam jalur hukum", 1, "Murah; bisa memicu liputan lebih besar", "menurut pengacara perusahaan", Tone.BAD,
                        Outcome(0.5, "Jurnalis mundur sementara.", emptyList()),
                        Outcome(0.5, "Berita naik dengan nada keras; pelanggan dan bank ikut bertanya.", listOf(Effect("lostcust", 0.03), Effect("fine", c.m(300.0)), Effect("ethics", 1.0, 0, "media_pressure"))))
                ), t, 2, "b")
        },
        Template("data_lawsuit", "Teknologi", 24, { 0.0 }) { c, r, id, t ->
            ev(id, "data_lawsuit", "Teknologi", "Pelanggan menggugat karena data bocor",
                "Data pesanan sejumlah pelanggan ritel ikut bocor saat serangan siber. Dua pelanggan menuntut ganti rugi.",
                "Efek berantai dari serangan siber.",
                listOf(
                    o("a", "Berdamai dan beri kompensasi", 1, "±${fmt(c.m(220.0))} jt RS", "menurut penasihat hukum", Tone.GOOD,
                        sure("Kasus selesai di luar pengadilan.", Effect("cost", c.m(220.0)))),
                    o("b", "Lawan di pengadilan", 2, "Biaya hukum ±${fmt(c.m(90.0))} jt RS; putusan tidak pasti", "menurut pengacara", Tone.WARN,
                        Outcome(0.5, "Gugatan ditolak.", listOf(Effect("cost", c.m(90.0)))),
                        Outcome(0.5, "Gugatan dikabulkan sebagian.", listOf(Effect("cost", c.m(90.0)), Effect("fine", c.m(500.0)))))
                ), t, 3, "b")
        },
        Template("tariff_retaliation", "Regulasi", 24, { 0.0 }) { c, r, id, t ->
            ev(id, "tariff_retaliation", "Regulasi", "Negara asal impor membalas dengan tarif",
                "Setelah bea antidumping dikenakan, negara pengekspor membalas dengan tarif atas bahan dan mesin yang Anda impor.",
                "Efek berantai dari sengketa antidumping.",
                listOf(
                    o("a", "Cari sumber alternatif", 2, "Biaya peralihan ±${fmt(c.m(200.0))} jt RS", "menurut Direktur Operasi", Tone.INFO,
                        sure("Pemasok alternatif ditemukan dengan harga sedikit lebih tinggi.", Effect("cost", c.m(200.0)), Effect("mod", 1.01, 6, "cogs"))),
                    o("b", "Teruskan impor dan bayar tarif", 0, "Biaya bahan naik beberapa bulan", "menurut Direktur Keuangan", Tone.WARN,
                        sure("Biaya impor naik.", Effect("mod", 1.025, 6, "cogs")))
                ), t, 3, "b")
        },
        Template("cartel_investigation", "Etika", 40, { 0.0 }) { c, r, id, t ->
            ev(id, "cartel_investigation", "Etika", "KPPU membuka penyelidikan kartel",
                "Komisi pengawas persaingan usaha memanggil sejumlah produsen tekstil, termasuk Anda, terkait kesepakatan harga bertahun lalu.",
                "Konsekuensi dari keikutsertaan dalam pemufakatan harga.",
                listOf(
                    o("a", "Kooperatif dan ajukan keringanan", 2, "Denda lebih kecil ±${fmt(c.m(700.0))} jt RS; beban etika turun", "menurut penasihat hukum", Tone.GOOD,
                        sure("Denda dikurangi karena kerja sama.", Effect("fine", c.m(700.0)), Effect("ethics_reduce", 2.0))),
                    o("b", "Bantah semua tuduhan", 1, "Peluang lolos ±35%; bila kalah denda berat", "menurut pengacara perusahaan", Tone.BAD,
                        Outcome(0.35, "Bukti tidak cukup; kasus ditutup.", emptyList()),
                        Outcome(0.65, "Terbukti bersalah; denda berat dan kepercayaan dewan turun.", listOf(Effect("fine", c.m(2200.0)), Effect("ethics", 2.0, 0, "cartel"), Effect("board_trust", -10.0))))
                ), t, 2, "b")
        },
        // ------------------------------------------------------------------ Kartu baru berdiri sendiri
        Template("activist_investor", "Pasar modal", 30, { c -> if (c.isPublic) (if (c.profitFalling) 0.7 else 0.12) else 0.0 }) { c, r, id, t ->
            ev(id, "activist_investor", "Pasar modal", "Investor aktivis membeli saham dan menuntut perubahan",
                "Dana aktivis mengumumkan kepemilikan 6% dan menuntut pembelian kembali saham, penjualan unit yang lemah, serta kursi dewan.",
                "Harga saham tertinggal dari pesaing sejak laba menurun.",
                listOf(
                    o("a", "Beli kembali saham dan beri kursi dewan", 2, "±${fmt(c.m(900.0))} jt RS; aktivis tenang", "menurut penasihat bank investasi", Tone.INFO,
                        sure("Aktivis puas; kas terpakai untuk pembelian kembali.", Effect("buyback", c.m(900.0)), Effect("sentiment", 0.10), Effect("board_trust", -4.0))),
                    o("b", "Lawan dengan pertahanan racun pil", 2, "±${fmt(c.m(250.0))} jt RS; pertarungan proksi", "menurut penasihat hukum", Tone.WARN,
                        Outcome(0.5, "Pemegang saham lain mendukung manajemen.", listOf(Effect("cost", c.m(250.0)), Effect("board_trust", 3.0), Effect("sentiment", 0.03))),
                        Outcome(0.5, "Kampanye aktivis menang di rapat umum; reputasi dan kepercayaan dewan turun.", listOf(Effect("cost", c.m(250.0)), Effect("board_trust", -12.0), Effect("lostcust", 0.01), Effect("sentiment", -0.12)))),
                    o("c", "Abaikan", 0, "Tekanan berlanjut", "menurut Anda", Tone.BAD,
                        sure("Aktivis menggalang dukungan publik.", Effect("board_trust", -8.0), Effect("sentiment", -0.10)))
                ), t, 2, "c")
        },
        Template("short_seller", "Pasar modal", 36, { c -> if (c.isPublic) 0.08 + 0.18 * min(c.ethicsLoad, 4.0) + (if (c.arShare < 0.9) 0.25 else 0.0) else 0.0 }) { c, r, id, t ->
            ev(id, "short_seller", "Pasar modal", "Penjual-pendek menerbitkan laporan tuduhan akuntansi",
                "Sebuah firma riset yang bertaruh melawan saham Anda menerbitkan laporan 60 halaman yang mempertanyakan piutang, hubungan dengan pihak terkait, dan kualitas laba.",
                "Beberapa tuduhan menyinggung praktik yang memang pernah dipertimbangkan manajemen.",
                listOf(
                    o("a", "Minta audit independen dan terbitkan hasilnya", 2, "±${fmt(c.m(380.0))} jt RS; transparan", "menurut komite audit", Tone.GOOD,
                        Outcome(0.7, "Audit membantah sebagian besar tuduhan; saham pulih.", listOf(Effect("cost", c.m(380.0)), Effect("board_trust", 4.0), Effect("sentiment", 0.06))),
                        Outcome(0.3, "Audit menemukan sejumlah kelemahan pengendalian internal; koreksi laporan diperlukan.", listOf(Effect("cost", c.m(380.0)), Effect("writeoff", c.m(400.0)), Effect("ethics", 1.0, 0, "accounting_weakness"), Effect("board_trust", -6.0), Effect("sentiment", -0.30)))),
                    o("b", "Bantah lewat siaran pers", 1, "Murah; tuduhan tetap beredar", "menurut humas", Tone.WARN,
                        Outcome(0.5, "Pasar mereda.", listOf(Effect("sentiment", -0.05))),
                        Outcome(0.5, "Regulator pasar modal meminta klarifikasi; saham tertekan.", listOf(Effect("fine", c.m(200.0)), Effect("board_trust", -6.0), Effect("sentiment", -0.25)))),
                    o("c", "Gugat firma riset", 1, "±${fmt(c.m(120.0))} jt RS; efek tidak pasti", "menurut pengacara", Tone.BAD,
                        sure("Gugatan menarik perhatian media pada isi laporan.", Effect("cost", c.m(120.0)), Effect("lostcust", 0.01), Effect("board_trust", -4.0), Effect("sentiment", -0.12)))
                ), t, 2, "b")
        },
        Template("esg_activist", "Pasar", 30, { c -> 0.3 + (if (!c.dumping) 0.1 else 0.0) }) { c, r, id, t ->
            ev(id, "esg_activist", "Pasar", "Aktivis lingkungan menyoroti limbah pewarna",
                "Kelompok aktivis memublikasikan foto saluran limbah dekat pabrik. Beberapa merek ritel mengutip laporan itu dalam penilaian pemasok.",
                "Pembeli besar mulai menyaratkan sertifikasi ESG.",
                listOf(
                    o("a", "Bangun instalasi pengolahan limbah", 2, "±${fmt(c.m(520.0))} jt RS; memenuhi syarat ESG", "menurut Direktur Operasi", Tone.GOOD,
                        sure("Instalasi dibangun dan disertifikasi.", Effect("capex", c.m(520.0)), Effect("esg_clean"), Effect("mod", 1.015, 8, "rev"))),
                    o("b", "Terbitkan klarifikasi dan tunda", 1, "Murah; risiko inspeksi lingkungan", "menurut humas", Tone.WARN,
                        Outcome(0.6, "Isu mereda.", emptyList()),
                        Outcome(0.4, "Pemerintah daerah menjadwalkan inspeksi mendadak.", listOf(Effect("chain", 0.0, 2, "env_inspection:Sorotan aktivis memicu inspeksi lingkungan.")))),
                    o("c", "Tekan aktivis lewat jalur hukum", 1, "Murah; reputasi memburuk", "menurut penasihat hukum", Tone.BAD,
                        sure("Gugatan balik menjadi berita negatif.", Effect("lostcust", 0.02), Effect("ethics", 1.0, 0, "intimidation")))
                ), t, 2, "b")
        },
        Template("gov_tender", "Pasar", 30, { c -> if (c.econ < 1.05) 0.35 else 0.15 }) { c, r, id, t ->
            ev(id, "gov_tender", "Pasar", "Tender seragam pegawai negeri",
                "Kementerian membuka tender seragam bernilai besar. Seorang perantara menawarkan \"memastikan\" Anda menang dengan imbalan komisi di luar pembukuan.",
                "Margin tender besar, tetapi pesaing juga mengincarnya.",
                listOf(
                    o("a", "Ikut tender secara bersih", 2, "Biaya penawaran ±${fmt(c.m(60.0))} jt RS; peluang menang ±35%", "menurut Direktur Penjualan", Tone.GOOD,
                        Outcome(0.35, "Anda memenangkan tender.", listOf(Effect("cost", c.m(60.0)), Effect("mod", 1.06, 8, "rev"))),
                        Outcome(0.65, "Tender dimenangkan pesaing.", listOf(Effect("cost", c.m(60.0))))),
                    o("b", "Pakai perantara dengan komisi", 1, "Komisi ±${fmt(c.m(300.0))} jt RS; peluang menang tinggi, jejak etika", "menurut perantara", Tone.BAD,
                        Outcome(0.8, "Tender dimenangkan.", listOf(Effect("cost", c.m(300.0)), Effect("mod", 1.07, 8, "rev"), Effect("ethics", 2.0, 0, "bribery"))),
                        Outcome(0.2, "Tender dibatalkan karena dugaan kolusi.", listOf(Effect("cost", c.m(300.0)), Effect("ethics", 2.0, 0, "bribery")))),
                    o("c", "Lewati", 0, "Tanpa efek", "menurut Anda", Tone.NEUTRAL, sure("Anda tidak ikut."))
                ), t, 2, "c")
        },
        Template("rival_price_war", "Pasar", 24, { c -> if (c.dumping) 0.0 else 0.3 }) { c, r, id, t ->
            ev(id, "rival_price_war", "Pasar", "Pesaing menurunkan harga serempak",
                "Dua pesaing besar menurunkan harga 8% pada produk yang sama dengan lini Anda, menguji seberapa jauh pembeli setia.",
                "Mereka membaca bahwa kas Anda tidak banyak.",
                listOf(
                    o("a", "Ikuti sebagian", 1, "Margin turun, pangsa terjaga", "menurut Direktur Penjualan", Tone.WARN,
                        sure("Anda menurunkan harga.", Effect("mod", 0.97, 4, "rev"), Effect("start_dumping", 4.0))),
                    o("b", "Pertahankan harga", 0, "Sebagian pembeli pindah", "menurut Anda", Tone.INFO,
                        Outcome(0.5, "Pelanggan loyal bertahan.", emptyList()),
                        Outcome(0.5, "Pangsa turun.", listOf(Effect("lostcust", 0.03), Effect("start_dumping", 4.0))))
                ), t, 2, "b")
        },
        Template("tech_licence", "Teknologi", 36, { c -> if (c.cash > c.m(4000.0)) 0.25 else 0.0 }) { c, r, id, t ->
            ev(id, "tech_licence", "Teknologi", "Lisensi teknologi pencelupan hemat air",
                "Sebuah institut menawarkan lisensi teknologi pencelupan hemat air dan energi. Hasil di pabrik lain bervariasi.",
                "Dua pabrik pesaing sudah mencobanya.",
                listOf(
                    o("a", "Ambil lisensi", 2, "±${fmt(c.m(380.0))} jt RS; efisiensi tidak pasti", "menurut Direktur Operasi", Tone.INFO,
                        Outcome(0.6, "Biaya produksi turun.", listOf(Effect("capex", c.m(380.0)), Effect("mod", 0.985, 12, "cogs"))),
                        Outcome(0.4, "Hasil jauh di bawah janji.", listOf(Effect("capex", c.m(380.0))))),
                    o("b", "Tolak", 0, "Tanpa efek", "menurut Anda", Tone.NEUTRAL, sure("Anda menolak."))
                ), t, 3, "b")
        },
        Template("green_subsidy", "Regulasi", 36, { c -> if (c.econ < 1.0) 0.3 else 0.1 }) { c, r, id, t ->
            ev(id, "green_subsidy", "Regulasi", "Subsidi bunga untuk investasi hemat energi",
                "Pemerintah membuka subsidi bagi pabrik yang memasang panel surya dan mesin hemat energi. Dana terbatas dan pemeriksa cermat.",
                "Pesaing tampak sudah mengajukan.",
                listOf(
                    o("a", "Ajukan dengan dokumen lengkap", 2, "Biaya pengurusan ±${fmt(c.m(70.0))} jt RS; peluang ±55%", "menurut Direktur Keuangan", Tone.GOOD,
                        Outcome(0.55, "Subsidi cair.", listOf(Effect("cost", c.m(70.0)), Effect("income", c.m(450.0)))),
                        Outcome(0.45, "Dana habis sebelum giliran Anda.", listOf(Effect("cost", c.m(70.0))))),
                    o("b", "Lewati", 0, "Tanpa efek", "menurut Anda", Tone.NEUTRAL, sure("Anda tidak mengajukan."))
                ), t, 3, "b")
        }
    )

    /** Aturan rantai: setelah kartu [from] diselesaikan dengan salah satu pilihan [options] (kosong = semua), peluang [p] memicu kartu [to] setelah [delay] giliran. */
    class ChainRule(val from: String, val options: Set<String>, val p: Double, val to: String, val delay: Int, val cause: String)

    val baseChainRules: List<ChainRule> = listOf(
        ChainRule("union", setOf("c", "z"), 0.70, "union_wildcat", 3, "Tuntutan upah serikat ditolak atau dibiarkan; pekerja melawan di luar serikat pusat."),
        ChainRule("accident", setOf("b"), 0.50, "labor_inspection", 3, "Operator dinyatakan lalai setelah kecelakaan; dinas ketenagakerjaan turun tangan."),
        ChainRule("accident", setOf("a"), 0.15, "labor_inspection", 4, "Kecelakaan kerja menarik perhatian inspektur."),
        ChainRule("quality_recall", setOf("b", "c", "z"), 0.55, "retailer_audit", 3, "Keluhan mutu tidak ditangani tuntas; peritel besar mengaudit pemasok."),
        ChainRule("customer_bankrupt", emptySet(), 0.45, "supplier_squeeze", 2, "Kabar piutang macet pelanggan besar memicu pemasok menuntut bayar di muka."),
        ChainRule("credit_crunch", setOf("b", "z"), 0.45, "bank_early_review", 2, "Pengetatan kredit membuat bank meninjau debitur lebih dini."),
        ChainRule("fraud_internal", setOf("b", "c", "z"), 0.45, "journalist_probe", 4, "Kecurangan internal yang tidak tuntas bocor ke jurnalis."),
        ChainRule("bribe_permit", setOf("a"), 0.45, "journalist_probe", 6, "Jejak pembayaran fasilitator diketahui pihak luar."),
        ChainRule("cartel_offer", setOf("a"), 0.55, "cartel_investigation", 10, "Keikutsertaan dalam pemufakatan harga akhirnya diselidiki."),
        ChainRule("cyber", setOf("a", "c", "z"), 0.40, "data_lawsuit", 3, "Data pelanggan ikut bocor saat serangan siber."),
        ChainRule("antidumping", emptySet(), 0.40, "tariff_retaliation", 3, "Bea antidumping memancing pembalasan tarif dari negara pengekspor.")
    )

    /** Dek penuh: kartu tulisan tangan + kartu generator (C1). */
    val templates: List<Template> by lazy { baseTemplates + EventCards.templates }
    val chainRules: List<ChainRule> by lazy { baseChainRules + EventCards.chainRules }

    val notices: List<NoticeTemplate> = listOf(
        NoticeTemplate("vat_hike", 40, { 0.25 }) { c, r -> Notice("Pemerintah menaikkan PPN 1 poin. Permintaan ritel melemah sedikit selama beberapa bulan.", Tone.WARN, listOf(Effect("mod", 0.985, 4, "rev"))) },
        NoticeTemplate("heatwave", 24, { 0.25 }) { c, r -> Notice("Gelombang panas ekstrem menurunkan produktivitas pekerja pabrik tanpa pendingin.", Tone.WARN, listOf(Effect("mod", 0.96, 2, "prod"))) },
        NoticeTemplate("shopping_fest", 18, { if (it.econ > 1.0) 0.35 else 0.15 }) { c, r -> Notice("Festival belanja nasional mendorong pesanan ritel naik sementara.", Tone.GOOD, listOf(Effect("mod", 1.03, 3, "rev"))) },
        NoticeTemplate("good_harvest", 20, { 0.25 }) { c, r -> Notice("Panen kapas Tarsis melimpah; harga bahan baku sedikit turun beberapa bulan.", Tone.GOOD, listOf(Effect("mod", 0.985, 4, "cogs"))) },
        NoticeTemplate("rival_news", 24, { 0.3 }) { c, r -> Notice("Pesaing besar mengumumkan PHK 12% karyawan; sejumlah tenaga terampil mencari kerja.", Tone.INFO, listOf(Effect("mod", 0.99, 2, "cogs"))) },
        NoticeTemplate("bank_rumor", 30, { c -> if (c.policyDelta > 0.5) 0.4 else 0.1 }) { c, r -> Notice("Desas-desus bank kecil bermasalah membuat bankir lebih berhati-hati terhadap debitur tekstil.", Tone.WARN, listOf(Effect("credit_shock", 0.4))) }
    )

    fun makeEvent(templateId: String, ctx: EventCtx, r: Random, id: String, customWarning: String? = null): GameEvent? {
        val e = templates.firstOrNull { it.id == templateId }?.build?.invoke(ctx, r, id, ctx.turn) ?: return null
        if (customWarning == null) return e
        return GameEvent(e.id, e.templateId, e.category, e.title, e.body, customWarning, e.options, e.raisedTurn, e.deadlineTurn, e.defaultOptionId)
    }

    class Drawn(val event: GameEvent?, val notice: Notice?, val noticeId: String?)

    /** Memilih kartu baru (atau pemberitahuan) untuk giliran ini. */
    fun draw(ctx: EventCtx, r: Random, open: Set<String>, lastFired: Map<String, Int>, counter: Int, rateMult: Double = 1.0): Drawn {
        val roll = r.nextDouble()
        val pEvent = (0.30 * rateMult).coerceIn(0.0, 0.8)
        if (roll < pEvent) {
            val cands = baseTemplates.filter { it.id !in open && ctx.turn - (lastFired[it.id] ?: -999) >= it.cooldown }
                .map { it to it.weight(ctx) }.filter { it.second > 0.0 }
            if (cands.isNotEmpty()) {
                var x = r.nextDouble() * cands.sumOf { it.second }
                for ((t, w) in cands) {
                    if (x < w) {
                        val e = t.build(ctx, r, "evt_${ctx.turn}_${counter}_${t.id}", ctx.turn)
                        if (e != null) return Drawn(e, null, null)
                    }
                    x -= w
                }
            }
        } else if (roll < pEvent + 0.17) {
            val cands = notices.filter { ctx.turn - (lastFired[it.id] ?: -999) >= it.cooldown }
                .map { it to it.weight(ctx) }.filter { it.second > 0.0 }
            if (cands.isNotEmpty()) {
                var x = r.nextDouble() * cands.sumOf { it.second }
                for ((t, w) in cands) {
                    if (x < w) return Drawn(null, t.build(ctx, r), t.id)
                    x -= w
                }
            }
        }
        return Drawn(null, null, null)
    }
}
