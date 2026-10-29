# KAPITAL 1.0.0 — catatan rilis (jujur)

Simulasi ekonomi dunia dan strategi korporasi berbahasa Indonesia untuk Android. Anda memimpin Benang Sundara selama 40 tahun (480 bulan, empat era) di dunia yang digerakkan agen: rumah tangga, pesaing, bank, bursa saham, komoditas, dan geopolitik.

## Isi
- **Kampanye CEO** empat era dengan akuntansi akrual penuh (laba rugi, neraca, arus kas, konsolidasi grup dengan anak usaha dan kepentingan non-pengendali), kredit dan kovenan, derivatif, IPO, M&A, transfer pricing, karbon, dan suksesi.
- **621 kartu kejadian** (42 tulisan tangan, 579 dari generator berarketipe) dengan 106 kartu berantai; pemicunya memakai kondisi dunia nyata.
- **Peran lain**: Gubernur Bank Sentral, Menteri Keuangan, Manajer Dana, masing-masing dengan skor sendiri.
- **Tantangan asinkron**: kode tantangan (seed + kesulitan) dan hasil berisi log aksi; penerima memutar ulang log untuk memverifikasi skor. Termasuk tantangan CEO satu kampanye penuh.
- **Fitur era dibuka lebih awal oleh kinerja** (pendapatan, ekuitas, laba, covenant, anak usaha); kalender tetap menjamin fitur terbuka pada tanggal era.
- Simpan/muat berbasis putar ulang (seed + daftar aksi), 5 slot autosave, 4 tingkat kesulitan, "Bagaimana Jika". Simpanan memuat cap versi mesin; bila versi berbeda atau ada tindakan yang tak bisa diputar ulang, pemain diberi tahu lewat spanduk, tidak diam-diam.
- Tombol tindakan korporasi Era 2–4 selalu memberi umpan balik (dilaksanakan / terkunci era / AP habis / syarat belum terpenuhi).

## Yang terbukti
- 227+ tes mesin lulus tanpa kegagalan pada JVM, dan CI "Build APK" menjalankan tes yang sama lewat Gradle. Tes mencakup invarian akuntansi (aset = liabilitas + ekuitas, arus kas = perubahan kas, roll-forward ekuitas, konsolidasi) pada puluhan ribu giliran acak, serta determinisme (seed + aksi = keadaan identik).
- Audit rilis: 8.280 aksi berparameter ekstrem tanpa pengecualian; 12 kampanye penuh tanpa NaN atau nilai negatif yang mustahil; hasil identik pada tiga mode hash JVM; fungsi transendental lewat `StrictMath` agar putar ulang identik antar CPU.
- Akibat pilihan diuji dengan menjalankan mesin (`EventConsequenceTest`): semua hasil kartu yang punya efek mengubah simulasi; mogok terukur 0,58× pendapatan selama 2 bulan, lalu 0,97× empat bulan, lalu pulih, dan tampil di laporan bulanan.
- Keseimbangan (250 seed baru, hanya bot): bot disiplin menang 39% (mati di Era 1: 20%), bot cakap 65% (18%), bot sembrono 0%. Tak ada strategi yang menang >85%.

## Yang BELUM terbukti atau terbatas
1. **Tampilan belum pernah dilihat di emulator atau perangkat.** Semua layar Compose hanya terbukti terkompilasi (CI). Daftar uji manual ada di `docs/UJI_MANUAL_UI.md` (bagian A–H).
2. **Belum ada uji main manusia.** Keseimbangan hanya diukur dengan tiga bot rancangan sendiri; `docs/UMPAN_BALIK_PLAYTEST.md` masih kosong. Bot sembrono mungkin orang-orangan sawah.
3. **APK release (R8) belum dijalankan.** CI memastikan build release dengan kunci sementara berhasil dan mencatat ukurannya, tetapi perilaku APK yang diperkecil di perangkat tidak diuji. **Penandatanganan rilis** butuh rahasia repositori `RELEASE_KEYSTORE_B64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_PASSWORD` (alias kunci `upload`) yang belum dipasang; tanpa itu hanya APK debug yang terbit.
4. **Putar ulang lintas perangkat belum diuji di ART/ARM.** Hanya HotSpot x86 dengan `StrictMath` dan mode hash berbeda. Bila berbeda, hasil tantangan yang dibagikan bisa ditolak.
5. **Tantangan tidak anti-curang.** Verifikasi membuktikan skor cocok dengan log, bukan bahwa log dimainkan sendiri; log bisa disalin dan seed bisa dicoba berulang. Tidak ada server: berbagi lewat salin-tempel; papan peringkat hanya selama sesi layar.
6. **Peran Gubernur, Menteri, dan Manajer Dana memakai dunia makro yang disederhanakan** (tanpa geopolitik penuh dan tanpa perusahaan pemain); perusahaan CEO tidak merasakan kebijakan fiskal atau moneter pemain di mode itu.
7. **10,4% opsi kartu (191 dari 1.841) tidak berefek**: semuanya "lewati/tolak" pada tawaran sukarela, dan teks dampaknya menyatakan itu. Keputusan terhadap politikus/aktivis baru terasa lewat kejadian berikutnya. Akibat kartu generator kecil dibanding laba rugi bulanan; mogok yang terbesar.
8. **Kartu bervariasi lebih sempit daripada jumlahnya**: 579 kartu berasal dari delapan arketipe dengan profil untung-rugi sama. Beberapa kondisi (sanksi, mogok) jarang terjadi pada bot cakap.
9. **Realisme**: ROE 21% hampir tanpa utang agak tinggi untuk manufaktur tekstil; bulan dengan margin EBIT sangat tinggi berasal dari pembayaran derivatif/asuransi yang dibukukan sebagai pengurang beban operasi; ekor krisis (kesenjangan output −20%, kurs sampai 100.000) mungkin terlalu dalam. Era 2 lebih keras sejak kovenan dilonggarkan (kematian Era 2 pasif ±25–27%).
10. **Aksesibilitas**: grafik dan peta kini punya deskripsi teks, tetapi belum diuji dengan TalkBack; peta hanya bisa dipilih lewat sentuhan (daftar negara sebagai alternatif). Tata letak lanskap/tablet hanya dikodekan, tidak diuji.
11. **Bahasa**: pemindaian otomatis tidak menemukan salah eja umum atau teks Inggris tersisa, tetapi tidak ada penyuntingan oleh penutur asli.
12. **Simpanan lama dari versi mesin berbeda bisa berjalan berbeda**: pemain kini diberi peringatan, tetapi permainan tetap dimuat dari log yang sama dan keadaannya mungkin tidak sama dengan yang pernah dilihat. Tantangan yang dibagikan belum memuat versi mesin: log yang dibuat di versi lain bisa ditolak verifikasi seolah curang.
13. **Waktu muat simpanan 480 giliran di perangkat belum diukur**: muat berarti memutar ulang seluruh log. Terukur di JVM x86 (kontainer awan 2 inti): kampanye 481 giliran, 755 baris log, 0,4–0,6 detik per putar ulang (pertama/dingin 0,4 detik; median lima kali 0,5 detik). Di ponsel (ART, CPU lebih lambat) tidak diketahui; bila lebih dari ±5 detik, pemuatan perlu dipindah dari utas UI.

## Ukuran APK
CI mencatat ukuran APK debug dan release (R8) di ringkasan tiap run ("Ukuran APK"); APK debug sebelumnya ±18 MB.

## Pemasangan
Unduh APK dari halaman Releases, buka di HP Android, izinkan "Instal aplikasi tidak dikenal" bila diminta. Build debug ditandatangani kunci debug; jangan menganggapnya build produksi.
