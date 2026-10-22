# Audit KAPITAL: kondisi game saat ini dibanding Dokumen Desain Induk Terpadu

Tanggal audit: 22 Oktober 2026 (diperbarui setelah Tahap A; bagian bawah dokumen adalah catatan build lama yang dipertahankan sebagai riwayat).
Dasar: PDF "KAPITAL: Dokumen Desain Induk Terpadu" (36 halaman) dibandingkan dengan kode di repo.

## Kesimpulan jujur (22 Oktober 2026)

Setelah Tahap A, game adalah **simulasi satu perusahaan pemain di dalam dunia 24 negara yang hidup** dengan geopolitik dinamis, rantai pasok Leontief 12 sektor, makro lanjut (fiskal, kurva imbal hasil, peringkat, bank sentral, bank run), 2.000 perusahaan dan 120 bank sebagai agen, pasar saham dari buku pesanan 5.000 investor, rilis data resmi yang terlambat/direvisi, serta demografi, iklim, dan adopsi teknologi.
Namun lapisan dunia ini **tidak membuat arus kas baru**; ia hanya menekan perusahaan pemain lewat saluran lama (permintaan, biaya, kurs, kredit, bunga). Itu disengaja agar identitas akuntansi terjaga, tetapi artinya dunia belum punya pelaku yang bisa Anda "ajak transaksi" selain yang sudah ada.
Belum selesai: 600+ kartu event (ada 42), 40 subsektor (ada 12), mekanik pemain Tahap B, bot Tahap B, multiplayer, rilis. Kalibrasi keseimbangan hanya diukur dengan bot uji, belum oleh manusia.
Saya tidak memberi skor "x dari 10" karena tidak ada ukuran yang bisa dipertanggungjawabkan; tabel di bawah dan bagian Tahap A memuat angka yang diukur.

## Tahap A — fondasi dunia: bukti dan kekurangan jujur

Semua angka dari kotak pasir ini (kotlinc 2.0.21 + JUnit stub; `tools/run-tests.sh`), bukan dari perangkat.

| Butir | Yang dibuat | Bukti terukur | Kekurangan jujur |
| :--- | :--- | :--- | :--- |
| A1 skala agen | 200.000 rumah tangga (kohort kuantil pendapatan, ±400 agen per kohort, transisi binomial), 5.000 investor (4 kelas), 2.000 perusahaan, 120 bank, 24 pemerintah, 24 bank sentral = **207.168** (`agentCensus()` dihitung dari objek hidup, tes `CreditSystemTest.engineCensus_adds_to207168`) | `EndTurn` rata-rata 0,8 ms, p99 1,3 ms, maks 3,2 ms (`PerformanceTest`, 895–967 giliran); 5.749 giliran acak di stress test: rata-rata 0,83–1,29 ms, maks 8–16 ms | Rumah tangga adalah **agregat kohort**, bukan 200.000 objek bernegara sendiri. Investor ditarik sebagai jumlah binomial per kelas, tanpa portofolio individual. Perusahaan dan bank adalah objek individual tetapi sederhana (satu leverage, satu margin; satu CAR); tidak ada jaringan utang antar-perusahaan |
| A2 16 negara sekunder | 4 blok × 4 negara; model ringkas, disimulasikan bulanan hanya bila bloknya terhubung dagang, kalau tidak per kuartal | 14 tes `WorldLayerTest` lulus | Tidak punya rumah tangga/perusahaan sendiri; hanya pertumbuhan, inflasi, bunga, risiko |
| A3 sektor dan rantai pasok | 12 sektor (bukan 40 subsektor), matriks Leontief dengan kendala kapasitas, 12 simpul rantai Tier 1–3 (Tier 3 tersembunyi sampai uji tuntas), sanksi berlapis | Hawkins–Simon (jumlah kolom < 1) diuji; pengali biaya sanksi Kestrel 1,088 tanpa uji tuntas vs 1,055 dengan | Hanya 12 sektor. Selat tertutup secara alami pada 22/40 seed bot (5,5% bulan; 0 / 69 / 207 / 290 bulan di Era 1–4; penutupan maks 46%), diuji di `WorldViewTest`; dampak penutupan pada pemain hanya lewat biaya dan ketersediaan sektor |
| A4 geopolitik | Ketegangan 0–100 untuk 276 pasangan, 6 anak tangga, 3 selat, pemilu 4 tipe di 24 negara | Sanksi aktif di 19/25 seed saat krisis vs 3/25 tenang; pangsa pro-pasar pemilu 0,39 (inflasi tinggi) vs 0,26 | Tren eskalasi 0,035/giliran pada pasangan Sundara adalah **penyetel kalibrasi** agar Era 3–4 tidak mudah (lihat Catatan kalibrasi); sanksi hanya antar negara inti dan tidak menyasar Sundara |
| A5 makro lanjut | Utang/PDB dan defisit 24 negara; kurva imbal hasil 10 tahun; peringkat 8 anak tangga dengan histeresis; bias Taylor dari haluan pemilu dan dominasi fiskal; QE + taper; panduan ke depan berkredibilitas; indeks panik dan bank run; kurs dari proksi neraca dagang | 9 tes `MacroPlusTest`; kurva terbalik pada 7,4% bulan (60 seed); bank run hanya 1 kejadian pada 60 seed bot disiplin (3 dalam 5.681 giliran stress acak) | Setelah aturan fiskal diperbaiki (r−g plus pengetatan menuju utang dasar): peringkat Sundara 39 penurunan vs 7 kenaikan pada 60 seed (sebelumnya 83 vs 3), akhir permainan BBB 34 seed, BB 24 seed, **D 2 seed** (gagal bayar negara; belum diselidiki apakah wajar). Masih condong turun. Bank run jarang pada bot disiplin (1/60 seed); dengan aksi acak ada 3. QE hampir tidak pernah menyala (bunga jarang ≤2,5%) |
| A6 informasi dan buku pesanan | Rilis data (inflasi, pengangguran jeda 1 bulan; PDB jeda 2 bulan; perkiraan awal berisik, revisi, final); harga 7 emiten dan saham pemain dari buku pesanan 5.000 investor (nilai, tren, ritel, institusi) | 9 tes `MarketTest`; simpangan baku return bulanan emiten 7,4% (lama 11%); korelasi serial return 0,62 (lama 0,73) | Metrik harga memotong sampel |ln(harga/nilai wajar)| > 0,6 (±4%) sehingga bukan perbandingan sempurna. Bank sentral Sundara kini bereaksi pada inflasi yang **diumumkan** (perkiraan awal berisik), tetapi bank sentral asing dan pesaing AI masih memakai angka benar; kejutan data juga menggeser suasana pasar saham. Rilis data tampil hanya sebagai satu baris (inflasi terakhir) di bagian "Dunia Lanjutan"; belum ada layar rilis/revisi lengkap |
| A7 siklus panjang | Demografi 8 negara; iklim (suhu, kejadian ekstrem per blok jadi guncangan kapasitas sektor); 3 kurva adopsi teknologi dengan terobosan | 6 tes `LongCyclesTest`; kejadian iklim per seed per era 1,8 / 3,5 / 5,4 / 5,1; suhu akhir rata-rata 0,98 °C; Sundara menua 7% → 15,8% | Parameter dampak adalah **asumsi**, bukan data; dampak ke pemain kecil (permintaan ≤ −0,3%, biaya ≥ −1,5%); politik dalam negeri dan narasi jangka panjang belum ada |
| A8 CI, kinerja, determinisme, audit | CI kini gagal bila tes gagal dan mengunggah log tes; `StageAStressTest` ≥ 5.000 giliran acak; tes determinisme seluruh lapisan dunia | Lokal: **105 tes lulus, 0 gagal** (104 pada suite penuh, ditambah 1 tes selat). Stress: 5.681 giliran, selisih neraca induk terbesar 2,3e-9 (batas 1e-6), arus kas dan konsolidasi tetap cocok. CI GitHub: langkah "Uji unit" sukses pada commit A6–A8; commit perbaikan lihat bagian di bawah | Log CI mentah tidak bisa diunduh dari lingkungan ini (host blob diblokir), jadi "log bersih" hanya disimpulkan dari status langkah, bukan dibaca |

### Perbaikan setelah laporan Tahap A (permintaan "perbaiki yang kurang")
Dikerjakan sebelum masuk Tahap B; semua terverifikasi lokal dengan suite penuh (104 lulus, 0 gagal; satu tes tambahan untuk selat).
- **Fiskal:** defisit primer kini `−0,8·(r−g)·utang − 0,4·gap + tambahan + biaya penuaan − 0,08·(utang − utang dasar)`; utang rata-rata ±56–62% PDB per era, peringkat tidak lagi turun satu arah (39 turun vs 7 naik, lama 83 vs 3).
- **Bank:** bank yang diselamatkan dibersihkan portofolionya (CAR 11, konsentrasi ×0,75, diawasi 12 bulan); kegagalan berulang pada bank yang sama hilang. Pada 40 seed bot, kegagalan bank Sundara tinggal 29 (Era 2), 21 (Era 3), 4 (Era 4) kasus; Halvenia 12 dan 22 kasus di Era 3 dan 4. Angka ini belum dinilai wajar atau tidak.
- **Bank sentral membaca rilis:** inflasi diumumkan (berisik) menggeser aturan Taylor Sundara (`1,5 × salah baca`).
- **UI:** skor ketegangan, tahap eskalasi, penutupan selat, hitung mundur pemilu asing, dan bagian "Dunia Lanjutan" (peringkat, utang, imbal hasil, kurva, panik, CAR bank, inflasi terakhir, suhu, adopsi teknologi, demografi) memakai data mesin. Hanya `WorldViewTest` (model) yang diuji; kartu Compose hanya terbukti kompilasi di CI. **Langkah manual di perangkat:** buka tab Dunia → bagian "Dunia Lanjutan" harus berisi 8 negara berperingkat dan 6 baris ringkasan; mainkan beberapa giliran lalu skor ketegangan di kartu "Skor Ketegangan" harus berubah.
- **Bug akuntansi lama ditemukan dan diperbaiki:** selisih kurs utang A$ di bawah 1 jt RS dibuang sehingga neraca induk bisa meleset (seed 7: −0,86 pada giliran 41 dan −0,56 pada giliran 56; `balanceSheetAlwaysBalances` menangkapnya). Kini dibukukan penuh. Bug ini sudah ada sebelum Tahap A dan hanya tersingkap oleh lintasan baru.
- **Belum diperbaiki:** log CI mentah tidak bisa dibaca; 12 vs 40 subsektor; 42 vs ≥600 kartu; rumah tangga masih kohort agregat; belum ada uji manusia; peringkat negara D di 2/60 seed belum diselidiki.

### Keseimbangan terukur (300 seed × 480 giliran, bot uji)
Sebelum Tahap A (60 seed, mesin lama): pasif 45%, aktif 53%. Awal Tahap A: pasif 137/300 (45,7%), aktif 186/300 (62%). **Akhir Tahap A (setelah perbaikan): pasif 132/300 (44%), kematian per era [80, 38, 25, 25]; aktif 192/300 (64%), [62, 34, 7, 5].** (Sebelum perbaikan: 133 dan 189.) Galat baku ±2,9 poin persentase per angka. Ini hanya mengukur bot; belum ada uji manusia.

## Tahap B — mekanik pemain dan bot: progres

| Butir | Yang dibuat | Bukti terukur | Kekurangan jujur |
| :--- | :--- | :--- | :--- |
| B1 intelijen | `BuyIntel` (laporan sektor, negara, pesaing, segmen; 3 tingkat anggaran, galat relatif 25/12/6%) dan rapat dewan kuartalan (`BoardAnswer`, benar +6, salah −6, diam −4); tab "Intelijen" | `IntelBoardTest` 6 tes: galat menyusut menurut anggaran, ±2 pita mencakup ≥90% (150 seed), biaya masuk beban usaha, neraca seimbang; CI hijau | Laporan hanya memberi informasi; dewan tidak punya kepribadian lanjutan atau memori antar pertanyaan; belum ada laporan "pesaing" yang membuka niat strategis mereka |
| B2 pasar produk | 5 segmen konsumen (bobot, tren AR(1), kurva adopsi jangka panjang, kepekaan merek), fokus 0/1/2 per segmen dengan batas total, jeda 6 bulan, merek, biaya pemasaran bulanan; tab "Segmen" | `ProductMarketTest` 8 tes: faktor awal 1,0; mengabaikan segmen terbesar merugikan ≈ −8% ekuitas; fokus tetap ≈ +1% (bukan uang gratis); `AntiExploitTest` bot acak 200 seed maks 1,44× ekuitas (bot disiplin maks 2,64×) | **Keseimbangan bergeser:** bot pasif 44% → 40%, bot aktif 64% → 58% (300 seed; kematian Era 1 naik karena variansi pendapatan dari tren segmen). Nilai informasi kecil: fokus berdasar survei atau oracle hanya sekitar +1–2% ekuitas dibanding fokus acak. Permintaan agregat lama masih dipakai di bawah faktor segmen (belum diganti total); harga masih global, belum per segmen |
| B3 operasi | 5 karyawan kunci (insinyur, kepala desain, supervisor, manajer penjualan, kepala teknisi): rekrut (3 tingkat, kandidat berderau, skill asli terlihat setelah 3 bulan), pecat (pesangon 6 bulan), naikkan gaji (loyalitas), pembajakan oleh pesaing; R&D berkelanjutan 0–3 (jalur inovasi, kualitas maks +4% menyusut 0,4%/bulan); logistik 0–2 (meredam guncangan biaya pasokan 35% per tingkat, mengurangi risiko tertahan); armada sendiri (proyek belanja modal); tab "Operasi" | `OperationsTest` 8 tes (aturan aksi, vakansi lebih rugi daripada hemat gaji, pembajakan nyata tetapi jarang dan gaji tinggi melindungi, R&D terbatas, armada, determinisme, neraca seimbang); suite 127 lulus | **Efek dan harga adalah asumsi** yang disetel agar tidak melenceng dari keseimbangan: tanpa mengelola, pembajakan merugikan; bot disiplin kini mengisi jabatan kosong (tingkat 2) sehingga keseimbangan tetap 39–40% pasif dan 58% aktif. Belum ada gudang berstok (persediaan lain) dan armada hanya pengurang HPP/risiko; belum ada pembajakan oleh pemain terhadap pesaing; pesaing tidak "memakai" staf yang dibajak; R&D dan proyek R&D lama (+4% permanen) menumpuk dan belum diuji bersama untuk keseimbangan jangka panjang |
| B4a keuangan lanjut | Asuransi 0–2 (premi bulanan; klaim atas kebakaran/pemogokan/kerusakan mesin cair lewat `pendingIncome`), L/C perdagangan (tenor utang usaha 35→50 hari, biaya L/C per bulan), sale-leaseback (jual aset tetap hingga 40% pada nilai buku, sewa 0,95%/bulan; tanpa laba/rugi penjualan); blok "Keuangan lanjut" di tab Operasi | `FinanceTest` 7 tes (aturan aksi, premi dan klaim, L/C menambah kas sekali lalu berbiaya, leaseback netral terhadap ekuitas saat penjualan, identitas arus kas `d_resid`/`e_resid`, determinisme); suite 134 lulus; anti-eksploit maks 1,54 | **Harga, tenor, dan peluang klaim adalah asumsi.** Bot belum memakai ketiganya, jadi keseimbangan tidak diukur ulang. Leaseback tidak memodelkan opsi beli kembali; L/C tidak memodelkan penolakan bank; asuransi tidak punya franchise/pengecualian. Derivatif (B4b) dan pajak/NCI (B4c) belum ada |
| B4b derivatif | Opsi beli A$ (strike 103%, 6 bulan), futures kapas (6 bulan), swap bunga bayar-tetap (12 bulan); diselesaikan tunai tanpa mark-to-market; premi lewat beban satu kali, penyelesaian lewat pendapatan lain; satu kontrak per jenis, pokok dibatasi pada eksposur (utang A$ + porsi HPP peka kurs; pembelian kapas 6 bulan; utang RS berbunga mengambang); harga memuat marjin dan risiko lonjakan terjadwal (krisis, pemilu) yang dikenal pasar; kartu "Derivatif lindung nilai" di tab Operasi | `DerivativesTest` 9 tes (aksi, batas eksposur, premi dan penyelesaian sesuai kontrak, neraca dan arus kas seimbang tiap giliran, determinisme dan simpan-muat, korelasi penyelesaian swap dengan suku bunga 0,68, repository); suite 143 lulus. Rata-rata bersih pembeli lintas ±100 seed: opsi −86, futures −546, swap −18 jt RS | **Swap hanya sedikit negatif (−18 dengan galat baku sekitar 30): harganya hampir wajar, belum terbukti merugikan secara signifikan.** Pembelian derivatif sebelum peringatan krisis mendapat harga prior (7%), bukan harga pasti. Setelah utang ditetapkan tetap, swap yang masih hidup menjadi posisi spekulatif (tidak dipantau). Parameter ekspektasi bunga/kurs/kapas diukur dari simulasi bot disiplin 99 seed dan bisa meleset pada gaya main lain. Belum ada CDS, opsi jual, atau penutupan dini. Bot belum memakai derivatif, jadi keseimbangan tidak diukur ulang |
| B4c minoritas dan saham | Penjualan saham minoritas anak usaha kini tawar-menawar: Anda menawarkan diskon 5–30% dari nilai wajar, investor menolak bila di bawah batasnya (8–28% acak per giliran/negara, +6 bila anak usaha merugi, +5 bila kas < 3.000) lalu menghilang 3 bulan; harga = nilai × porsi × (1−diskon) × 0,97. Minoritas ≥ 20% memveto transfer pricing agresif. Pajak tertahan atas dividen bagian NCI dilacak (`nciWithheldRs`) dan dijelaskan sebagai beban pemegang minoritas (netral bagi grup). UI: kartu "Saham minoritas anak usaha" (sebelumnya aksi ini **tidak punya UI sama sekali**) dan grafik garis harga saham 60 bulan | `MinorityTest` 9 tes (kompatibilitas simpanan lama, blokir, tawaran rendah ditolak dan memakan AP dengan jeda, harga selalu di bawah nilai wajar dan diskon besar lebih sering diterima: 3 vs 16 dari 17, veto 20%, identitas kas/ekuitas konsolidasi, determinisme); suite 152 lulus | **Tag-along belum ada** karena tidak ada aksi menjual pengendalian anak usaha. Veto tidak memaksa pembatalan transfer pricing agresif yang sudah aktif. Pajak tertahan atas NCI hanya informasi (pemodelan sudah netral bagi grup sebelumnya, bukan perubahan akuntansi). Batas diskon investor adalah asumsi. Grafik saham dan kartu minoritas **belum diverifikasi di perangkat** (hanya kompilasi lewat CI) |
| Anti-eksploit | `AntiExploitTest`: bot acak 200 seed × 60 giliran, ekuitas akhir/awal median 0,98, p99 1,26, maks 1,44 | Lulus; neraca seimbang | Hanya mengukur aksi yang sudah ada; diperbarui tiap mekanik B baru |

### Catatan kalibrasi (agar tidak diulang)
- Lapisan dunia sempat membuat permainan hampir mustahil (menang 1%) karena penuh dampak sejak Era 1; sekarang dampaknya diredam 0,35 di Era 1 (`DuniaTuning.era1Gate`) karena rantai pasok lintas negara baru relevan di Era 2.
- Pengali biaya dunia hanya boleh masuk ke HPP pemain, bukan ke `costPressure` bersama (pesaing tidak bisa menaikkan harga dan bangkrut, pemain menang 72%).
- Skor ketegangan lama adalah jalan acak dengan drift +0,04/bulan; dihilangkan murni membuat bot pasif menang 44/60 di Era 3–4, jadi tren eskalasi dipertahankan hanya pada pasangan Sundara.
- Kegagalan bank pada sistem kredit sempat 16 per permainan karena bank yang lebih terkonsentrasi menanggung rugi lebih besar tanpa margin lebih tinggi; sudah diperbaiki. Sisa ekor: pada 2/40 seed, bank Sundara gagal berulang dalam krisis berkepanjangan (puluhan kegagalan per seed) — tidak berpengaruh pada keseimbangan terukur, tetapi tampak tidak realistis.
- Tes bergantung pada "seed pertama yang bertahan" rapuh karena bot disiplin sering menahan kas hampir nol; dua tes kini memilih survivor yang punya kas untuk membuka anak usaha.

## Perbandingan per bagian dokumen

| # | Bagian dokumen | Target dokumen | Kondisi sekarang | Status |
|---|---|---|---|---|
| 3 | Loop giliran, Poin Perhatian | 5 AP, eksekutif menambah AP, krisis mengunci AP | AP ada (maks 4 + eksekutif), aksi berbiaya AP | Sebagian |
| 3 | Briefing koran, rumor, rilis data terlambat | 5 media, rumor, data terlambat dan direvisi | Koran dan rumor ada; rilis data resmi terlambat dan direvisi di mesin (`DataDesk`), tampil sebagai baris laporan bulanan dan satu baris di "Dunia Lanjutan"; belum ada layar rilis/revisi lengkap | Sebagian |
| 3 | Riset berbayar, intelijen | Beli riset dengan margin error | Tidak ada | Belum |
| 3 | Rapat dewan kuartalan | Anggota dewan AI bertanya tajam | Tidak ada | Belum |
| 4 | Dunia | 8 negara inti + 16 sekunder | 8 inti dinamis + 16 sekunder (model ringkas) dalam 4 blok | Ya (A2) |
| 4 | Peta dan papan | Petak wilayah, selat, risiko iklim | Layar Peta dengan lapisan; tidak ada petak bidak | Sebagian |
| 5 | Agen | 207.000 | 207.168 menurut sensus mesin (200.000 rumah tangga sebagai kohort, 5.000 investor, 2.000 perusahaan, 120 bank, 24 pemerintah, 24 bank sentral) plus 4 pesaing, 7 emiten, 4 bankir, 4 eksekutif | Sebagian (agregat) |
| 5 | Pembentukan harga dari buku pesanan | Harga dari order agen | Harga saham (emiten dan perusahaan pemain) dari buku pesanan 5.000 investor; harga produk pesaing masih dari fungsi reaksi | Sebagian (A6) |
| 5 | Uang tertutup (stock-flow) | Tiap satuan tercatat | Neraca perusahaan pemain selalu seimbang (diuji 20 seed); sistem perbankan agregat belum tercatat | Sebagian |
| 5 | Determinisme dan replay | Seed + keputusan sama = dunia sama | Ada, termasuk "Bagaimana Jika" | Ya |
| 6 | Bot eksekutif egois | Manipulasi KPI, sembunyikan masalah, kickback | Tiga perilaku ada (obral kredit, tunda perawatan, kickback), dengan audit dan konfrontasi | Ya (terbatas) |
| 6 | Bot pesaing AI | 4 arketipe, belajar dari pemain, koalisi | Hanya reaksi harga (menyamai potongan) dan dumping importir; tidak belajar, tidak ada pembajakan staf, korner, lobi | Sebagian kecil |
| 6 | Bot bankir | Jebakan term sheet, dumping klien busuk | **Baru**: 4 bankir bernama dengan kepentingan, jebakan klausul, penolakan | Ya (baru) |
| 6 | Bot pemasok | Hold-up opportunistik | Satu kejadian hold-up; dual sourcing sebagai pertahanan | Sebagian |
| 6 | Bot investor aktivis | Greenmail, bear raid | Tidak ada; investor hanya membentuk harga saham | Belum |
| 6 | Bot politisi | Siklus politik, pemerasan regulasi | Hanya guncangan pemilu dan sanksi | Sebagian kecil |
| 6 | Rumah tangga dan serikat | Kepanikan kawanan, mogok | Mogok serikat ada (satu skrip); indeks panik dan bank run ada (A5) | Sebagian |
| 7 | Bank sentral | Aturan Taylor + bias politik, QE, forward guidance | Taylor + depresiasi kurs + bias politik, QE, panduan ke depan berkredibilitas (A5) | Ya (sederhana) |
| 7 | Fiskal, kurva imbal hasil, peringkat negara | Defisit, spiral utang, peringkat | Ada untuk 24 negara (A5); peringkat hanya memengaruhi kurs dan premi kredit | Ya (sederhana) |
| 7 | Siklus bisnis endogen | Resesi dari perilaku agen | Ada (kesenjangan output dari rumah tangga) | Ya |
| 7 | Kurs | Selisih bunga, neraca dagang, risiko politik | Paritas daya beli + proksi neraca dagang + denyut peringkat/panik | Sebagian |
| 8 | Skor ketegangan dan tangga eskalasi | 0-100 per pasangan, 6 anak tangga | Dinamis untuk 276 pasangan, 6 anak tangga (A4); layar Dunia kini memakainya untuk 4 pasangan utama (skor, tahap, penutupan selat) | Sebagian (mesin teruji; layar hanya terbukti kompilasi di CI, belum dilihat di perangkat) |
| 8 | Matriks Leontief, rantai pasok Tier-3, sanksi berlapis | 40 subsektor | 12 sektor, rantai Tier 1-3, sanksi berlapis (A3) | Sebagian |
| 8 | Pemilu, donasi, blok dagang | Pemilu per negara | Pemilu dinamis di 24 negara (4 tipe hasil) memengaruhi tarif, defisit, bias bank sentral; donasi belum ada | Sebagian |
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
| 15 | Event dari kondisi dunia | 600+ kartu, berantai | 42 template, 11 aturan rantai (Tahap C menargetkan ≥600) | Sebagian kecil |
| 16 | Hukum, etika, skor rahasia | Skor etik, skandal tertunda, antimonopoli, ESG | Tidak ada skor etik dan skandal tertunda | Belum |
| 17 | Sistem jangka panjang | Demografi, iklim, teknologi, politik dalam, narasi | Demografi, iklim, adopsi teknologi (A7); politik dalam dan narasi belum | Sebagian |
| 18 | Pengasah otak | Jurnal, Brier, pasar prediksi, detektor bias, post-mortem, counterfactual, rapat dewan | Jurnal, skor Brier, kronik/post-mortem, counterfactual ada; pasar prediksi, detektor bias, rapat dewan tidak ada | Sebagian |
| 19 | Kampanye 40 tahun, 4 era, kondisi menang | 480 giliran; skor akhir 5 komponen | 480 giliran, 4 era, evaluasi 5 komponen, suksesi (lihat HANDOFF) | Ya (belum diuji manusia) |
| 19 | Tingkat kesulitan | 4 level | 4 tingkat tersimpan sebagai aksi pertama (`DifficultyTest`) | Ya |
| 20 | Peran alternatif, multiplayer | CEO, gubernur, menteri, manajer dana | Hanya CEO | Belum |
| 21 | Save, UI, skala | 5 slot autosave, ironman, <3 detik | Autosave satu slot, seed deterministik; `EndTurn` rata-rata 0,8 ms (maks 3,2 ms pada uji kinerja) dengan 207.168 agen | Sebagian |

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

---

## Build-20: kalibrasi anak usaha dan koreksi diagnosis keseimbangan

**Koreksi:** angka bangkrut 52–98% dari tes massal Argon sebagian besar artefak bot. Bot "pasif" selalu bangkrut karena tidak memperpanjang pinjaman Bank B (200/200 mati dalam 72 giliran, juga pada build lama); itu desain, bukan cacat. Bot disiplin yang menerima refinancing (`tools/sim/Bot.kt`, 200 seed): bangkrut 15% dalam 24 giliran dan 30% dalam 72 giliran; tersulit ekspansi berutang (59%) dan warisan bangkrut (46%).

**Perubahan model anak usaha** (`KapitalEngine.kt`):
1. Sinergi anak usaha ke induk (HPP, permintaan) dikali `SUB_SYNERGY = 0.5`. Sebelumnya pendapatan anak usaha sudah dikonsolidasi DAN permintaan induk naik penuh (hitung ganda); akibatnya membuka anak usaha menaikkan peluang bertahan induk +12 s.d. +17 poin.
2. Faktor pasar lokal: 28% kasus buruk (0,40–0,56, rugi nyata; dulu 24% pada 0,58–0,72 yang masih untung), 72% normal (0,90–1,18).
3. Perputaran modal Tarsis 1,34 dan Kestrel 1,29 agar payback masuk rentang.

**Hasil `tools/sim/Pay2.kt`** (200 seed; bot disiplin; anak usaha dibuka giliran 12–20; diukur 72 bulan kemudian; hanya anak usaha yang hidup ≥48 bulan aktif):

| Negara | Induk bertahan (kontrol 138/200) | Anak usaha rugi kumulatif | Payback kondisi stabil (tahun, p25/median/p75) |
| :--- | :---: | :---: | :---: |
| Tarsis | 149/200 | 11% | 6,1 / 6,9 / 9,2 |
| Kestrel | 149/200 | 17% | 4,9 / 5,9 / 8,2 |
| Halvenia + IPAL (`Invest solar`) | ≈144/200 | ≈22% | ≈6,3 / 7,7 / sangat lambat |
| Halvenia tanpa IPAL | ≈141/200 | 88% | tidak pernah (bea karbon CBAM) |
| Meridia (SPV) | ≈131/200 | ≈24% | ≈5,7 / 6,9 / sangat lambat |

Halvenia tanpa sertifikat ESG sengaja merugi (pungutan CBAM bulanan tetap); itu pelajaran kepatuhan, bukan salah kalibrasi, tetapi harus terlihat jelas oleh pemain. Angka dengan tanda ≈ berasal dari satu putaran sebelum penyetelan perputaran modal Tarsis/Kestrel.

**Tes baru** (`StressCalibrationTest.kt`): identitas neraca induk, anak usaha, konsolidasi, arus kas, dan ekuitas pada 100 seed × hingga 60 giliran dengan aksi acak (anak usaha, repatriasi, transfer pricing, valas, obligasi, IPO, bank grup, akuisisi, investasi, saham, karbon, equity cure): 1.829 giliran-permainan, selisih terbesar 1,5e-10 (batas 1e-6). Rata-rata permainan hanya ±18 giliran karena banyak yang berakhir dini, jadi horizon panjang kurang tercakup. Uji determinisme: dua kali jalan, hasil identik.

**Keterbatasan yang diketahui:** CTA dihitung sebagai selisih (bukan akumulasi terpisah), sehingga identitas neraca konsolidasi tidak menguji laba bersih konsolidasi; rekonsiliasi ekuitas konsolidasi per periode belum ada. Perputaran, margin, dan pajak anak usaha adalah perkiraan model, bukan data riil.

---

## Build-21: kartu berantai aktif dan dek 40 kartu

- Dek bertambah dari 26 ke **40 template** (`Events.kt`): 9 kartu khusus rantai (bobot acak 0: mogok liar, inspeksi keselamatan, audit peritel, tekanan pemasok, tinjauan dini bank, investigasi jurnalis, gugatan data, pembalasan tarif, penyelidikan kartel) dan 5 kartu mandiri (aktivis ESG, tender pemerintah dengan godaan komisi, perang harga pesaing, lisensi teknologi, subsidi hijau).
- 11 `ChainRule` (`EventDeck.chainRules`) memicu kartu lanjutan dengan peluang dan jeda tertentu setelah keputusan tertentu; dievaluasi di `resolveEvent` dengan RNG yang sama dengan hasil kartu, jadi deterministik dan ikut terulang saat memuat simpanan. Penjelasan sebab tampil sebagai peringatan kartu dan catatan kronik ("Krisis Berantai").
- Bukti (`ChainCardsTest`): semua 40 kartu terbangun valid (peluang hasil berjumlah 1, pilihan bawaan ada); pada 150 permainan × 60 giliran dengan bot yang memilih opsi terakhir, kartu lanjutan benar-benar muncul di 93 permainan.
- Bukan klaim: 40 kartu masih jauh dari ±600 pada dokumen desain; kualitas dan keseimbangan tiap kartu belum diuji dengan pemain manusia.

---

## Build-22: era dan penguncian fitur (Tahap 2)

- `KapitalEngine.era(turn)` kini 4 era sesuai dokumen desain (sebelumnya 5): Era 1 Bertahan Hidup (giliran 1–96, 2030–2037), Era 2 Ekspansi Lintas Batas (97–228), Era 3 Konglomerasi dan Finansialisasi (229–372), Era 4 Kekaisaran Global dan Suksesi (373–480). `currentEra` sekarang murni mengikuti tanggal; lompatan era semu akibat aksi dihapus.
- Penguncian fitur (`eraBlock`): anak usaha, pinjaman lokal anak usaha, akuisisi pesaing, dan transfer pricing terkunci sampai Era 2; obligasi, IPO, bank grup, lobi/media sampai Era 3; karbon dan suksesi CEO sampai Era 4. Alasan penguncian (dengan tanggal) tampil di kartu Investasi. IPO juga butuh riwayat ≥12 bulan dengan EBIT rata-rata 6 bulan terakhir positif (`ipoBlock`).
- Syarat transisi era berbasis kinerja (omzet, ekuitas, jumlah entitas) **belum ada**; transisi murni berdasarkan tanggal. Karena skenario awal dimulai pada giliran 33–75 (2032–2036), Era 1 pendek (22–64 bulan) bagi pemain.
- Tes: `stageD_...` ditulis ulang agar bermain disiplin sampai tiap era (giliran 97, 229, 373 pada seed yang bertahan) lalu menguji fitur era itu dan memeriksa semua identitas akuntansi; fitur terkunci terbukti ditolak. Uji tekanan identitas kini dimulai di Era 2: 4.076 giliran-permainan, selisih terbesar 3,2e-10.
- Kalibrasi anak usaha ulang saat dibuka di Era 2 (`tools/sim/Pay2.kt`, 200 seed, dibuka giliran 97–110, diukur 72 bulan): rugi kumulatif Tarsis 4%, Kestrel 13%, Halvenia+IPAL 19%, Meridia 19%, Halvenia tanpa IPAL 34%; payback stabil median 5,5–7,7 tahun (Halvenia tanpa IPAL 11); peluang bertahan induk 142–143/200 vs kontrol 147/200 (membuka anak usaha kini sedikit menurunkan peluang bertahan, bukan menaikkan).

---

## Build-23: krisis makro per era dan uji 480 giliran

**Temuan** (`tools/sim/Long.kt`, 40 seed, bot disiplin sampai giliran 481): sebelum perubahan, bot yang melewati Era 1 hampir pasti menyelesaikan kampanye (29/40 selesai; seluruh 11 kegagalan terjadi di Era 1) dan ekuitas naik terus tanpa hambatan: Era 2–4 tidak punya tekanan, bertentangan dengan dokumen desain ("tekanan krisis naik tiap era").

**Perubahan** (`KapitalEngine.crisisPlans`): satu krisis makro deterministik dari seed di tiap Era 2–4 dengan sinyal dini 3 bulan sebelumnya: Resesi Global (Era 2, ±9 bulan), Krisis Keuangan (Era 3, 12 bulan; kredit mengering, Rial melemah), Guncangan Energi dan Disrupsi Teknologi (Era 4, 14 bulan; lonjakan harga kapas/energi). Efek: guncangan permintaan bulanan, `creditShock`, pelemahan kurs, dan guncangan komoditas; dicatat di laporan dan kronik. Kekuatan efek adalah angka perkiraan yang disetel lewat simulasi, bukan data riil.

**Hasil setelah penyetelan** (40 seed, bot disiplin tanpa persiapan khusus): gagal di Era 1: 11; Era 2: 0; Era 3: 4; Era 4: 10; selesai: 15/40. Waktu `EndTurn` giliran >290 di JVM: median 0,5 ms, p95 1,0 ms, maks ±10 ms (n≈3.700); belum diukur di perangkat Android. Tidak ada NaN.

**Keterbatasan:** (1) Bot tidak mempersiapkan krisis, jadi ini batas bawah untuk pemain yang cermat; keseimbangan sebenarnya hanya bisa dinilai dengan pemain manusia. (2) Krisis Era 2 belum menimbulkan kegagalan sama sekali pada bot (terlalu ringan atau bot terlalu sehat saat itu). (3) Sinyal dini baru berupa satu baris laporan, belum kartu atau koran. (4) Dampak ke bursa saham dan portofolio pemain belum dimodelkan.

Tes baru: `EraCrisisTest` (satu krisis per era, dalam batas era, deterministik; krisis tercatat di kronik).

---

## Build-24: konsekuensi menjadi perusahaan terbuka (Tahap 4)

- Dua kartu pasar modal (dek kini 42 template): investor aktivis (peluang tinggi bila laba 3 bulan terakhir turun >15% dibanding 3 bulan sebelumnya) dan penjual-pendek (peluang naik dengan beban etika dan komposisi piutang berisiko). Keduanya hanya muncul bila `isPublicCompany`.
- Peringatan pelunasan pokok obligasi/pinjaman bullet 6 dan 2 bulan sebelum jatuh tempo (sebelumnya pelunasan terjadi tanpa peringatan).
- Tes: `identities_hold_inEra3And4_withCorporateActions` (3.390 giliran-permainan mulai giliran 232 dan 376, aksi acak termasuk IPO/obligasi/bank grup/akuisisi, semua identitas < 1e-6) dan `PublicCompanyTest` (43 IPO: kartu pasar modal diterima perusahaan publik 54 kali, perusahaan swasta 0 kali).
- Belum ada: harga saham perusahaan pemain di bursa, dividen/pembelian kembali saham sebagai keputusan berkelanjutan, kepentingan nonpengendali, dan dampak dilusi ke kendali (`founderSharePct` hanya dicatat).

---

## Build-25: evaluasi akhir tampil di layar (Tahap 5, sebagian)

- Panel akhir permainan (`GameOverPanel`) kini menampilkan kelas, arketipe, skor total dan 5 pilar (nilai riil 30%, ketahanan 20%, kalibrasi keputusan 20%, etika 15%, sosial 15%) dari `evaluateCampaign()` lewat `GameState.evaluationTitle/evaluationRows`. Sebelumnya hanya teks ringkas di laporan bulanan.
- Tes `EvaluationTest` (40 seed, bot disiplin sampai akhir): semua skor dalam 0..100; kegagalan selalu Kelas F, pemenang tidak pernah F. Hasil: 15 menang, 25 gagal; skor pemenang 53–87, rata-rata 74.
- Belum ada: grafik atau tabel pembanding antar kampanye, post-mortem naratif yang menautkan keputusan ke skor, dan kalibrasi rumus skor terhadap pemain manusia (bobot dan konstanta adalah perkiraan).

---

## Build-26: laporan konsolidasi yang benar-benar cocok (Tahap 3)

**Temuan:** arus kas konsolidasi (`lastConsolidatedCashFlow`) tidak cocok dengan perubahan kas grup: selisih terbesar **3.870 jt RS** pada 2.928 giliran uji (`tools/sim/Cons.kt`). Penyebab: pembangunan anak usaha tidak dihitung sebagai kas keluar grup, kas awal anak usaha saat aktivasi muncul tanpa baris arus kas, pengaruh kurs atas kas anak usaha tidak ada, dan dividen internal dieliminasi pada kurs yang berbeda. Pernyataan "Selesai" dan "identitas lulus" di dokumen Argon untuk konsolidasi hanya berlaku untuk neraca (aset = liabilitas + ekuitas), yang memang tidak menguji arus kas.

**Perbaikan** (`KapitalEngine.kt`):
- Arus kas konsolidasi kini punya baris: operasi, investasi (termasuk pembangunan anak usaha dan kas anak usaha yang diperoleh saat aktivasi), pendanaan (dividen internal dieliminasi), pengaruh kurs atas kas, dan kas disita (nasionalisasi). Selisih terhadap perubahan kas grup: 2e-11.
- Laporan perubahan ekuitas konsolidasi baru (`lastConsolidatedEquity`): saldo awal + laba bersih konsolidasi + komponen langsung ke ekuitas induk + perubahan CTA + pelepasan anak usaha yang disita = saldo akhir. Sisa: 1e-10.
- Laba rugi konsolidasi: pemecahan EBIT dan NI cocok (1e-11).

**Tes:** `checkConsolidated` kini dipanggil di uji tekanan Era 2 dan Era 3–4 (≈7.470 giliran-permainan dengan aksi acak, termasuk repatriasi, pinjaman lokal, transfer pricing, nasionalisasi): semua selisih < 1e-6. Satu kasus tepi (nasionalisasi anak usaha dengan laba ditahan) sempat gagal dan diperbaiki lewat baris pelepasan.

**Masih belum:** laporan konsolidasi belum tampil di layar (hanya induk), dan laporan per entitas belum ada di UI.

---

## Build-27: laporan grup di layar dan audit transfer pricing berbasis laba yang digeser (Tahap 3)

- Tab Laporan Keuangan menampilkan dua kartu baru bila ada anak usaha: "6. Laporan konsolidasi grup" (laba rugi, neraca, arus kas, perubahan ekuitas, dengan CTA dan pengaruh kurs) dan "7. Anak usaha per entitas" (pendapatan, EBIT, laba bersih, ekuitas lokal, dividen siap ditarik, dalam mata uang lokal dan RS). Terkompilasi di JVM untuk data/model; kartu Compose hanya terbukti lolos CI, belum dilihat di perangkat. Tes `GroupStatementsUiTest`: baris grup kosong tanpa anak usaha, 30 baris grup dan 6 baris entitas setelah anak usaha dibuka.
- Peluang audit transfer pricing kini `0,001 + 70 × (laba digeser / pendapatan)^2,5` per bulan (maks 15%), dan denda ikut skala laba digeser. Sebelumnya peluang tetap 4,5% hanya untuk mode agresif. Hasil `TransferPricingAuditTest` (44 permainan × 80 giliran, Meridia): wajar 0 audit, optimal 34, agresif 119.
- Rasio fee/pendapatan terukur (`tools/sim/Tp.kt`): optimal ±2,7%, agresif ±4,5% (bukan 3,2%/6,8% nominal, karena pendapatan sebenarnya lebih besar dari basis nominal).

## Build-28 — Penyempurnaan Tahap 1–2 (jujur)
- **Kesulitan** kini dipilih di menu (dialog 4 tingkat) dan menjadi tindakan pertama yang tersimpan di log (sah hanya sebelum tindakan lain; mengganti di tengah permainan ditolak). Efek nyata: frekuensi kartu kejadian (tes: Magang 9,1 vs Krisis Abadi 16,4 kejadian/48 bulan), koalisi pesaing, **noise angka berita bisnis** (0 s.d. ±21%), dan **Ironman** (mematikan "Bagaimana Jika").
- **Pesaing belajar**: tes 40 seed menunjukkan profil pemain yang dipelajari berbeda untuk gaya potong-harga vs premium (40/40), agresivitas pesaing lebih tinggi pada potong-harga (4,77 vs 3,21).
- **Rapor transisi era**: era tetap berganti menurut kalender dunia, tetapi nilai kampanye pada akhir era menentukan syarat kredit era berikutnya (≥75 melonggarkan, <55 menaikkan premi). Tersimpan di `eraGrades` dan kronik.
- **Belum**: UI belum pernah dilihat di perangkat; keseimbangan belum diuji manusia; 42 kartu vs ±600 target.

## Build-29 — Tahap 4a: saham perusahaan Anda setelah IPO (jujur)
- Setelah IPO: 1.000 juta lembar beredar, harga = nilai fundamental (maks. 0,6x buku atau 11x laba tahunan yang dihaluskan) x sentimen acak yang kembali ke rata-rata; riwayat harga disimpan; dividen per saham dihitung.
- **Buyback** (`Action.Buyback`): menarik saham di harga berjalan, menaikkan porsi pendiri, diperlakukan sebagai distribusi ekuitas sehingga neraca, arus kas dan ekuitas konsolidasi tetap cocok (diuji dalam tes tekanan acak 7.700+ giliran).
- Kartu aktivis dan penjual-pendek kini menggeser sentimen harga; pilihan "beli kembali saham" memakai buyback sungguhan.
- **Belum**: kepentingan non-pengendali (anak usaha kepemilikan sebagian) dan pembelian saham oleh pemegang lain; harga saham belum memengaruhi biaya modal atau penerbitan saham lanjutan (SPO); tidak ada grafik harga di UI.

## Build-30 — Tahap 4b: kepentingan non-pengendali, SPO, harga saham ke biaya kredit (jujur)
- **Kepentingan non-pengendali (NCI)**: jual 5–25% saham anak usaha aktif (≥12 bulan, maks. 49% total) ke investor luar, harga = maks(0,7x ekuitas, 9x laba tahunan), biaya 3%. Dana masuk sebagai ekuitas induk. Laba bersih grup dipecah ke pemilik induk vs NCI; ekuitas NCI tampil; dividen anak usaha dibagi — porsi NCI keluar dari grup.
- Tes (228 penjualan, 38 seed) + tes tekanan acak: kas, laba, dan ekuitas konsolidasi tetap cocok (residu ~1e-10).
- **Bug lama yang ditemukan tes**: repatriasi dividen pada bulan yang sama dengan nasionalisasi dihitung ganda sebagai kas disita. Diperbaiki.
- **SPO**: terbitkan saham lanjutan (diskon 5%, biaya 4%, jeda 12 bulan), pendiri terdilusi.
- Sentimen saham kini memengaruhi spread kredit mengambang (±0,3 poin).
- **Belum/disederhanakan**: pajak withholding atas bagian NCI tidak dihitung; harga jual NCI tidak dinegosiasikan; tidak ada hak veto/tag-along; tidak ada grafik harga sungguhan (hanya tren panah).
