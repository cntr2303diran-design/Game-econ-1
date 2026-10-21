# Audit KAPITAL: kondisi game saat ini dibanding Dokumen Desain Induk Terpadu

Tanggal audit: 21 Oktober 2026 (build setelah penambahan Bursa dan Meja Kredit).
Dasar: PDF "KAPITAL: Dokumen Desain Induk Terpadu" (36 halaman) dibandingkan dengan kode di repo.

## Kesimpulan jujur

Game saat ini adalah **simulasi satu perusahaan di satu negara dengan dunia yang hidup sebagian**, bukan simulasi dunia penuh seperti di dokumen.
Yang sungguh-sungguh disimulasikan: rumah tangga, pasar tenaga kerja, bank sentral (aturan Taylor), inflasi, kurs, tiga komoditas, empat pesaing, tujuh emiten bursa, dan kini proses kredit bank.
Yang masih berupa skenario tertulis (skrip): tuntutan serikat, term sheet refinancing, proyek pemintalan, guncangan pemilu, dan kebanyakan perilaku eksekutif.
Skala dokumen (207.000 agen, 8+16 negara, 40 subsektor, 600+ kartu event, 480 giliran) belum tercapai; angka sebenarnya ada di tabel di bawah.
Dokumen menargetkan "10/10 realistis"; saya menilai kondisi sekarang sekitar 4 dari 10 terhadap dokumen tersebut. Ini bukan pembelajaran bisnis multinasional yang utuh, tetapi fondasi untuk Era 1 (bertahan hidup).

## Perbandingan per bagian dokumen

| # | Bagian dokumen | Target dokumen | Kondisi sekarang | Status |
|---|---|---|---|---|
| 3 | Loop giliran, Poin Perhatian | 5 AP, eksekutif menambah AP, krisis mengunci AP | AP ada (maks 4 + eksekutif), aksi berbiaya AP | Sebagian |
| 3 | Briefing koran, rumor, rilis data terlambat | 5 media, rumor, data terlambat dan direvisi | Koran dan rumor ada; rilis data tidak terlambat/direvisi; berita kurang informatif | Sebagian |
| 3 | Riset berbayar, intelijen | Beli riset dengan margin error | Tidak ada | Belum |
| 3 | Rapat dewan kuartalan | Anggota dewan AI bertanya tajam | Tidak ada | Belum |
| 4 | Dunia | 8 negara inti + 16 sekunder | 8 negara statis + 7 ekonomi asing dinamis (inflasi, kurs), tanpa 16 sekunder | Sebagian |
| 4 | Peta dan papan | Petak wilayah, selat, risiko iklim | Layar Peta dengan lapisan; tidak ada petak bidak | Sebagian |
| 5 | Agen | 207.000 | 50.000 rumah tangga + 4 pesaing + 7 emiten + 4 bank + 4 eksekutif + 1 bank sentral | Sebagian |
| 5 | Pembentukan harga dari buku pesanan | Harga dari order agen | Harga pesaing dari fungsi reaksi; harga saham dari nilai wajar + sentimen, bukan buku pesanan | Sebagian |
| 5 | Uang tertutup (stock-flow) | Tiap satuan tercatat | Neraca perusahaan pemain selalu seimbang (diuji 20 seed); sistem perbankan agregat belum tercatat | Sebagian |
| 5 | Determinisme dan replay | Seed + keputusan sama = dunia sama | Ada, termasuk "Bagaimana Jika" | Ya |
| 6 | Bot eksekutif egois | Manipulasi KPI, sembunyikan masalah, kickback | Tiga perilaku ada (obral kredit, tunda perawatan, kickback), dengan audit dan konfrontasi | Ya (terbatas) |
| 6 | Bot pesaing AI | 4 arketipe, belajar dari pemain, koalisi | Hanya reaksi harga (menyamai potongan) dan dumping importir; tidak belajar, tidak ada pembajakan staf, korner, lobi | Sebagian kecil |
| 6 | Bot bankir | Jebakan term sheet, dumping klien busuk | **Baru**: 4 bankir bernama dengan kepentingan, jebakan klausul, penolakan | Ya (baru) |
| 6 | Bot pemasok | Hold-up opportunistik | Satu kejadian hold-up; dual sourcing sebagai pertahanan | Sebagian |
| 6 | Bot investor aktivis | Greenmail, bear raid | Tidak ada; investor hanya membentuk harga saham | Belum |
| 6 | Bot politisi | Siklus politik, pemerasan regulasi | Hanya guncangan pemilu dan sanksi | Sebagian kecil |
| 6 | Rumah tangga dan serikat | Kepanikan kawanan, mogok | Mogok serikat ada (satu skrip); bank run tidak ada | Sebagian |
| 7 | Bank sentral | Aturan Taylor + bias politik, QE, forward guidance | Taylor + depresiasi kurs; tanpa bias politik, QE, pidato | Sebagian |
| 7 | Fiskal, kurva imbal hasil, peringkat negara | Defisit, spiral utang, peringkat | Tidak ada | Belum |
| 7 | Siklus bisnis endogen | Resesi dari perilaku agen | Ada (kesenjangan output dari rumah tangga) | Ya |
| 7 | Kurs | Selisih bunga, neraca dagang, risiko politik | Paritas daya beli + premi risiko; tanpa neraca dagang | Sebagian |
| 8 | Skor ketegangan dan tangga eskalasi | 0-100 per pasangan, 6 anak tangga | Data statis di layar Dunia, tidak bergerak oleh perilaku | Belum |
| 8 | Matriks Leontief, rantai pasok Tier-3, sanksi berlapis | 40 subsektor | Tidak ada; hanya kapas, minyak, baja sebagai biaya | Belum |
| 8 | Pemilu, donasi, blok dagang | Pemilu per negara | Satu pemilu Sundara tertulis | Sebagian kecil |
| 9 | Tiga laporan keuangan, jebakan akrual, CCC | Lengkap, tak bisa disunting | Laba rugi, neraca, arus kas ada tetapi **kurang rinci** (belum ada rincian gaji, perubahan ekuitas, catatan); angka arus kas pendanaan belum menjumlah ke perubahan kas | Sebagian (perbaikan sedang berjalan) |
| 9 | Utang teknis perawatan | Peluang rusak naik eksponensial | Ada (naik linear per bulan tertunda) | Ya |
| 9 | SDM, R&D, logistik | Karyawan kunci, riset, gudang, armada | Hanya eksekutif dan serikat; tidak ada R&D, gudang, armada | Sebagian kecil |
| 10 | Akuntansi forensik | 6 teknik, M-score, uji tuntas, auditor | Channel stuffing dan tunda perawatan ada; tidak ada SPV, window dressing, M-score | Sebagian kecil |
| 11 | Birokrasi kredit | Credit memo, haircut, term sheet, covenant, 1-3 giliran | **Baru**: pengajuan bebas, 1-3 giliran, haircut agunan, skor internal, tolak/potong/syarat, klausul tersembunyi, biro kredit, hubungan bank | Ya (baru, belum penuh) |
| 11 | Instrumen non-bank | L/C, factoring, sale-leaseback, asuransi | Factoring otomatis dan pinjaman daring ada; L/C, leaseback, asuransi tidak ada | Sebagian |
| 11 | Mendirikan bank sendiri | Era 3 | Tidak ada | Belum (memang Era 3) |
| 12 | Saham, obligasi, IPO, M&A | Bursa, IPO, M&A, LBO, distressed debt | **Baru**: 7 emiten dengan laporan triwulan, beli/jual, dividen. Belum ada obligasi, IPO, M&A, short | Sebagian |
| 12 | Derivatif | Futures, forward, swap, opsi, CDS | Hanya lindung nilai kapas 6 bulan | Sebagian kecil |
| 13 | Segmen konsumen, tren, merek | 5 segmen, kurva adopsi, pemasaran | Tidak ada; permintaan agregat satu produk | Belum |
| 14 | Informasi tidak sempurna | Label keyakinan, bias per sumber, noise per kesulitan | Label keyakinan dan cross-check ada; noise per kesulitan tidak ada (tidak ada tingkat kesulitan) | Sebagian |
| 15 | Event dari kondisi dunia | 600+ kartu, berantai | Sekitar 15 jenis kejadian dengan peluang dari kondisi; sebagian berantai | Sebagian kecil |
| 16 | Hukum, etika, skor rahasia | Skor etik, skandal tertunda, antimonopoli, ESG | Tidak ada skor etik dan skandal tertunda | Belum |
| 17 | Sistem jangka panjang | Demografi, iklim, teknologi, politik dalam, narasi | Tidak ada | Belum |
| 18 | Pengasah otak | Jurnal, Brier, pasar prediksi, detektor bias, post-mortem, counterfactual, rapat dewan | Jurnal, skor Brier, kronik/post-mortem, counterfactual ada; pasar prediksi, detektor bias, rapat dewan tidak ada | Sebagian |
| 19 | Kampanye 40 tahun, 4 era, kondisi menang | 480 giliran; skor akhir 5 komponen | Hanya Era 1; skenario mulai Maret 2033, bukan 2030; tidak ada skor akhir | Sebagian kecil |
| 19 | Tingkat kesulitan | 4 level | Satu level | Belum |
| 20 | Peran alternatif, multiplayer | CEO, gubernur, menteri, manajer dana | Hanya CEO | Belum |
| 21 | Save, UI, skala | 5 slot autosave, ironman, <3 detik | Autosave satu slot, seed deterministik; proses giliran jauh di bawah 3 detik | Sebagian |

## Mengapa "pinjaman selalu langsung acc"

Penyebab teknis (sudah diperbaiki di build ini):
1. Kredit investasi 9.800 jt RS untuk pabrik pemintalan dibuat otomatis begitu keputusan disetujui, tanpa penilaian bank.
2. Dua term sheet refinancing adalah skrip tetap: tidak ada kemungkinan ditolak.
3. Tidak ada menu untuk mengajukan utang bebas, jadi pemain hanya melihat skrip yang selalu disetujui.

Perbaikan:
- Pengajuan bebas ke 4 bank (jumlah, tenor, tujuan, bunga tetap/mengambang, cicilan/bullet).
- Skor kredit internal (ICR, DSCR, utang bersih/EBITDA, likuiditas, agunan setelah haircut, rekam jejak) ditambah hubungan bank, biro kredit, kondisi makro, dan insentif bankir.
- Keputusan setelah 1-3 giliran: tolak, potong plafon, atau setujui dengan syarat. Ada klausul tersembunyi (MAC, bunga promosi yang naik, pembatasan dividen, ditarik mendadak) yang hanya terbaca lewat tinjauan hukum.
- Proyek pemintalan tidak lagi dibiayai otomatis.

## Mengapa "bot tidak jelas"

Sebelumnya bot hanya muncul sebagai teks acak di laporan bulanan. Sekarang tab **Pelaku** (menu Perusahaan) menampilkan tiap pelaku: apa yang ia incar, apa yang terakhir ia lakukan, tanda yang bisa dibaca pemain, dan cara menghadapinya.
Cakupan: 4 eksekutif, 4 bankir, 4 pesaing, pelanggan (Mahkota), pemasok (Kestrel), serikat, bank sentral, investor bursa.

## Hal yang BELUM dibuat dan perlu dikerjakan agar mendekati "super realistis"

Urutan yang saya sarankan (dari yang paling memengaruhi pembelajaran):
1. Laporan keuangan rinci (gaji, perubahan ekuitas, catatan), arus kas yang menjumlah ke perubahan kas. (sedang dikerjakan)
2. Ekspansi dan investasi bebas: pabrik baru, kapasitas, otomasi, R&D, gudang, ekspor ke negara lain (Era 2).
3. Konflik dinamis: serikat, pemasok, pelanggan, pemerintah yang bereaksi pada krisis, bukan satu skrip.
4. Berita informatif: sumber, bias, dan data makro yang terlambat dan direvisi.
5. Pesaing yang belajar dan menyerang kelemahan pemain (utang tinggi, satu pemasok).
6. Skor etik tersembunyi, skandal tertunda, antimonopoli.
7. Era 2: ekspor-impor, L/C, kurs dan lindung nilai (forward, swap), pabrik di negara lain, transfer pricing, pajak lintas negara.
8. Tingkat kesulitan, skor akhir, kondisi menang/kalah, peran lain.

## Batasan yang harus dipahami

- Negara, bank, dan perusahaan di game fiktif; angka terkalibrasi ke pola ekonomi nyata (Taylor, PPP, AR(1) komoditas, kurva Phillips), bukan data nyata. Jangan dijadikan dasar keputusan investasi sungguhan.
- Skor kredit, haircut agunan, dan perilaku bankir adalah model sederhana dari praktik umum; bank sungguhan memakai model dan kebijakan internal yang berbeda.
- Pembelajaran bisnis multinasional yang sebenarnya butuh Era 2 (lintas negara). Belum ada di build ini.

## Pembaruan: setiap permainan berbeda (build-15)

Masalah: alur cerita masih tertanam di kode (tanggal Maret 2033, serikat selalu menuntut, pinjaman Bank B selalu jatuh tempo bulan ke-45), sehingga tujuh permainan terasa sama.

Perbaikan:
- `Scenario.kt`: tiap seed membangkitkan premis (warisan bangkrut, pelanggan tunggal, mesin tua, ekspansi berutang, pasar lesu, sengketa serikat), tanggal mulai, ukuran usaha, kas, piutang, utang, bunga awal, inflasi, kurs, nama eksekutif, jadwal pemilu, dan jatuh tempo Bank B.
- `Events.kt`: dek 26 kartu keputusan (serikat, pembajakan eksekutif, kecelakaan kerja, upah minimum, termin pelanggan, peritel bangkrut, tren motif, batch cacat, pemasok kapas, kenaikan suku cadang, pelabuhan, listrik, banjir, ransomware, audit pajak, inspeksi limbah, insentif, anti-dumping, kredit mengetat, dividen khusus, mesin bekas, kecurangan internal, pemberitaan, kartel, suap izin, pesaing baru) dan 6 pemberitahuan. Peluang tiap kartu bergantung pada kondisi dunia (inflasi, pengangguran, bunga, umur piutang, perawatan mesin, riwayat etika).
- Setiap pilihan berisiko (hasil acak deterministik dari seed), kartu yang dibiarkan sampai tenggat memakai hasil bawaan yang biasanya terburuk, dan jalan pintas (suap, kartel) menambah beban etika yang bisa meledak menjadi skandal.
- Simpanan lama tidak kompatibel: skenario dibangkitkan dari seed, sehingga permainan lama harus dimulai ulang.

## Pembaruan build-17: laporan lengkap, ekspansi bebas, berita dinamis

- Laba rugi kini merinci HPP (bahan, energi, tenaga langsung, overhead) dan beban operasional (gaji staf dengan jumlah karyawan dan rata-rata per orang, administrasi, pemasaran, utilitas, asuransi, lain-lain, biaya satu kali, program investasi).
- Neraca merinci aset lancar, piutang normal dan berisiko, investasi saham, liabilitas lancar, dan total liabilitas.
- Arus kas dibagi operasi, investasi, pendanaan, dan selalu cocok dengan perubahan kas; laporan perubahan ekuitas cocok dengan ekuitas neraca (diuji lintas 12 seed, 30 bulan).
- Catatan: umur piutang, jadwal utang bank, karyawan dan kapasitas, rasio keuangan.
- Tab Investasi: lini tenun, otomasi, R&D, kampanye merek, gudang, pasar ekspor. Kapasitas dan permintaan dimodelkan terpisah, sehingga ekspansi tanpa permintaan menganggur dan permintaan tanpa kapasitas tertolak.
- Berita mengikuti kondisi dunia (upah, kapas, impor murah, kredit, pesaing, dan kartu kejadian yang sedang terbuka sebagai peringatan dini).

---

## Build-19: integrasi pekerjaan Argon (commit 9df6e36) ke branch ini — status jujur

Sumber: engine `Actions.kt`, `KapitalEngine.kt`, `World.kt` dari `origin/main@9df6e36` dipakai di atas branch ini; `main` sendiri **tidak terkompilasi** (389 galat, file Scenario/Events/Credit/Bourse/Commodities tidak ada). Perbaikan yang dibuat di sini: `EventDeck.makeEvent(customWarning)`, parameter `rateMult` pada `EventDeck.draw`, `Commodities.triggerCorneringShock`. UI: kartu "Multinasional dan korporasi" di tab Investasi memanggil `GameRepository.corporateAction(kode)`.

| Butir | Status |
| :--- | :--- |
| Kompilasi engine+data+model+tes di JVM | Terbukti (kotlinc) |
| 27 tes (21 lama + 6 dari Argon) | Terbukti lulus, JVM |
| Identitas akuntansi induk, anak usaha, konsolidasi, 24 giliran, 6 seed (tes Argon) + 240 permainan | Terbukti oleh tes, horizon pendek (24 giliran) |
| Aksi baru (anak usaha, repatriasi, transfer pricing, valas, obligasi, IPO, bank grup, lobi, karbon, suksesor): encode/decode | Terbukti oleh tes round-trip |
| Pesaing yang belajar (`World.updateRivalLearning`) | Terkompilasi, ada 1 tes; belum dikalibrasi |
| Kartu berantai | Mekanisme ada (`ScheduledChainEvent`), tetapi **tidak ada kartu di dek yang memicu `chain`**, jadi belum pernah aktif dalam permainan nyata. Dek tetap 26 kartu (klaim "44 kartu" di `docs/PORTING.md` main tidak benar) |
| Anak usaha: pembukuan lokal, konsolidasi, CTA, pajak withholding | Terkompilasi dan identitas lulus. Faktor pasar lokal (`localDemandFactor`) ditentukan acak per seed dengan rentang yang dipilih agar payback sesuai target, jadi kalibrasi bersifat dirancang, bukan hasil emergen |
| Kalibrasi anak usaha (`tools/sim/Pay.kt`, 200 seed per negara) | Lihat tabel di bawah; **belum memenuhi** kriteria payback 3–7 tahun |
| UI era 2–5 | Hanya kartu tombol baru; belum dijalankan di perangkat; CI Compose menentukan |
| Skor "9,6/10" dan "Selesai" di dokumen Argon | Tidak berdasar, dibuang |

### Hasil `tools/sim/Pay.kt` (hanya EndTurn, anak usaha dibuka giliran 1–6; hanya permainan dengan ≥12 bulan aktif)
Rata-rata hanya ±20 bulan aktif karena sekitar 40–48% permainan tanpa strategi lain bangkrut sebelum 12 bulan aktif (bangun anak usaha dini menguras kas). Laba kumulatif/biaya setelah ±20 bulan: Tarsis median 0,17; Kestrel 0,19; Meridia 0,15; **Halvenia median −0,09 dengan 99% rugi** (terlalu keras atau salah kalibrasi). Payback yang diekstrapolasi median 8,4–9,2 tahun (belum memperhitungkan masa belajar) — horizon terlalu pendek untuk menyimpulkan.

### Keseimbangan (tes massal Argon, 240 permainan × 24 giliran)
Bangkrut: pasif 87%, nekat harga 98%, disiplin operasi 52%, multinasional+hedging 67%. Premis tersulit: ekspansi berutang (92%) dan warisan bangkrut (91%). Ini terlalu keras untuk permainan yang ingin mengajar; perlu penyeimbangan.
