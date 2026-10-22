# PROMPT LANJUTAN KAPITAL — 3 TAHAP PENYELESAIAN

Salin seluruh isi dokumen ini sebagai prompt pertama untuk Claude (atau AI lain). Kerjakan Tahap A, B, C **berurutan**; jangan lompat. Di akhir tiap tahap: tes lulus, CI hijau, audit diperbarui secara jujur, commit dan push.

---

## 0. PERAN DAN ATURAN KERJA

Kamu adalah insinyur senior game-simulasi ekonomi. Kamu melanjutkan **KAPITAL**, game strategi korporasi berbasis giliran untuk Android (Kotlin, Jetpack Compose), berbahasa Indonesia, selaras dengan dokumen desain (PDF): 4 era (2030–2070), 480 giliran bulanan, ±207.000 agen, 12 sektor, ±600 kartu kejadian, multiplayer paling akhir.

Aturan yang tidak boleh dilanggar:
1. **Jujur.** Jangan pernah menulis "selesai/sempurna/siap rilis" tanpa bukti (tes, angka simulasi, CI). Setiap klaim di dokumen harus bisa diverifikasi. Kekurangan ditulis apa adanya di `docs/AUDIT_vs_KONSEP.md`. Pelajaran: cabang `main` buatan AI lain mengklaim selesai tetapi tidak bisa dikompilasi (389 error).
2. **Jangan merusak invarian akuntansi** (bagian 3). Setiap fitur keuangan baru wajib punya tes tekanan acak yang memeriksa identitas tiap giliran.
3. **Deterministik.** Seed + daftar aksi = state identik. Jangan memakai `Random()` tanpa seed, `System.currentTimeMillis()` di dalam mesin, atau urutan iterasi yang tidak stabil.
4. **Verifikasi sebelum klaim.** Kompilasi, jalankan tes, lihat hasil CI. Jangan menandai tugas selesai bila tes gagal atau belum dijalankan.
5. **Bahasa antarmuka: Indonesia.** Nama kode boleh Inggris/Indonesia campuran mengikuti gaya yang ada.
6. **Jangan mengganti arsitektur** tanpa alasan terukur. Perluas, jangan tulis ulang.
7. Satu perubahan kecil → tes → commit. Commit sering, pesan commit jelas (Indonesia).
8. Hormati batas AI: jika sesuatu tidak bisa diverifikasi di lingkungan kerjamu (mis. tampilan perangkat), katakan terus terang dan sediakan cara pengujian untuk manusia.

---

## 1. INFO REPO

- Repo: `https://github.com/cntr2303diran-design/game-econ-1`
- Cabang kerja yang valid: **`claude/simulasi-era1`** (PR #1). Cabang `main` **tidak bisa dikompilasi**; jangan jadikan basis. Pada akhirnya gabungkan cabang ini ke `main` hanya setelah CI hijau dan pemilik repo setuju.
- Build: GitHub Actions `.github/workflows/build-apk.yml` (Java 21, Gradle 9.3.1, `:app:assembleDebug`, lalu `:app:testDebugUnitTest`). **Perhatian:** langkah uji unit memakai `continue-on-error: true`, jadi CI hijau **tidak membuktikan tes lulus**. Periksa log langkah "Uji unit" atau jalankan tes sendiri. Usulan Tahap A: buat langkah itu gagal bila tes gagal.
- Struktur kode (`app/src/main/java/com/example/`):
  - `engine/KapitalEngine.kt` (±3.000 baris) — mesin deterministik. Pintu perubahan state: `apply(Action)`. Proses bulanan di aksi `EndTurn`.
  - `engine/Actions.kt` — semua aksi + `encode()/decode()` (format baris `KODE|arg|arg`).
  - `engine/Events.kt` — dek kartu: `Template(id, kategori, jeda, bobot){...}`, `Effect(kind,a,b,s)` ditangani `applyEffect`, `chainRules` untuk kartu berantai.
  - `engine/World.kt` (rumah tangga, bank sentral, pesaing yang belajar), `Bourse.kt` (bursa 7 emiten), `Credit.kt` (birokrasi kredit), `Commodities.kt`, `Scenario.kt` (awal permainan acak, turn0 33–75).
  - `data/GameRepository.kt` — jembatan mesin ke UI; menyusun semua baris laporan; menyimpan log aksi (`SEED:` + aksi per baris).
  - `model/GameModels.kt`, `viewmodel/KapitalViewModel.kt`, `ui/screens/*`, `ui/components/*`, `MainActivity.kt`.
- Tes (`app/src/test/java/com/example/`): `ExampleUnitTest`, `ArgonCorporateTest`, `StressCalibrationTest` (memuat banyak kelas: StressCalibrationTest, ChainCardsTest, EraCrisisTest, PublicCompanyTest, EvaluationTest, GroupStatementsUiTest, TransferPricingAuditTest, RivalLearningTest, DifficultyTest, EraReviewTest, StockTest, NciTest, SuccessionTest, BalanceTest), `TestBots` (bot disiplin `TestBots` dan bot aktif `ActiveBot`).
- Dokumen: `docs/AUDIT_vs_KONSEP.md` (audit jujur terhadap dokumen desain; **bagian atasnya usang**, perbarui), `docs/HANDOFF.md` (arsitektur dan invarian), `tools/sim/*.kt` (simulasi bantu).
- Menjalankan tes tanpa Android (cepat): kompilasi `engine/`, `data/`, `model/` dengan `kotlinc` JVM + stub JUnit minimal, lalu jalankan kelas tes. Alternatif resmi: `gradle :app:testDebugUnitTest`.
- Atribusi commit (wajib bila lingkunganmu memberikannya): ikuti instruksi atribusi sesi.

### Yang SUDAH ada (jangan dikerjakan ulang; hanya perluas)
Pilihan 4 kesulitan (aksi pertama, efek ke frekuensi kejadian/noise berita/Ironman); pesaing belajar dari gaya pemain; 42 kartu (9 hanya-berantai) + 11 aturan rantai; krisis makro per era; 4 era dengan penguncian fitur dan rapor akhir era → syarat kredit; anak usaha 4 negara (buku lokal, kurva belajar, CTA, withholding, transfer pricing WAJAR/OPTIMAL/AGRESIF, CBAM, risiko nasionalisasi/kontrol modal); laporan konsolidasi + per entitas + atribusi NCI; forward valas; obligasi korporasi; M&A pesaing; IPO, harga saham (fundamental×sentimen), dividen/saham, buyback, SPO, jual saham minoritas anak usaha; bank grup, media/lobi, karbon; suksesi CEO (pengumuman giliran 445, serah terima 468); evaluasi 5 komponen; 48 tes lulus lokal.

---

## 2. KONDISI KEJUJURAN SAAT INI (jangan dilebih-lebihkan)

Terhadap dokumen desain penuh, kondisi ±35–45%. Belum terbukti: tampilan Compose di perangkat (hanya kompilasi), keseimbangan oleh manusia. Keseimbangan bot (60 seed, 480 giliran): pasif menang 28, aktif 34 — selisih tipis karena bot aktif belum mengelola leverage. Kartu kejadian 42/600.

---

## 3. INVARIAN AKUNTANSI (toleransi 1e-6, diuji tiap giliran)

- Induk: aset = liabilitas + ekuitas.
- Arus kas induk: CFO + CFI + CFF = Δkas (`d_resid`≈0); roll-forward ekuitas (`e_resid`≈0).
- Konsolidasi: kas grup = Δkas grup (termasuk pengaruh kurs atas kas anak usaha, kas diperoleh saat aktivasi, kas disita, dividen ke NCI); ekuitas konsolidasi = awal + laba + langsung + ΔCTA + dekonsolidasi; EBIT dan NI terurai konsisten; NI grup = NI pemilik induk + NI NCI.
- Aksi yang menambah/mengurangi ekuitas harus lewat `periodCureIn` (masuk) atau variabel `dividend` (keluar) agar identitas otomatis terjaga. Entitas/instrumen baru harus ikut ke laporan konsolidasi dan ke tes `StressCalibrationTest.randomAction`.
- Bug nyata yang pernah ditemukan tes: dividen ditarik di bulan yang sama dengan nasionalisasi dihitung ganda sebagai kas disita. Tes acak dengan aksi random adalah jaring pengaman utama — tambahkan aksi barumu ke dalamnya.

---

## 4. TAHAP A — DUNIA DAN EKONOMI MAKRO (fondasi skala)

Tujuan: dunia yang hidup dan saling terkait, bukan latar statis. Tidak menambah UI besar; fokus mesin + laporan.

A1. **Skala agen**: naikkan dari ±50.000 rumah tangga ke skala dokumen (±207.000 agen: rumah tangga, perusahaan non-pemain, bank, emiten) **dengan agregasi yang efisien** (kohort/ember bukan objek per agen) agar `EndTurn` tetap <3 detik di perangkat menengah. Ukur dengan tes kinerja (batas waktu longgar di CI).
A2. **Negara**: tambah 16 negara sekunder (profil: PDB, inflasi, kebijakan, tarif, risiko, mata uang) di samping 8 inti; hanya yang terhubung dagang dengan pemain yang disimulasikan penuh, sisanya model ringkas.
A3. **Sektor dan rantai pasok**: perluas dari 3 komoditas ke 12 sektor/subsektor dengan matriks input-output (Leontief) sederhana; rantai pasok tier-3 untuk produk pemain; guncangan hulu merambat ke biaya/ketersediaan; sanksi berlapis (negara → sektor → perusahaan).
A4. **Geopolitik dinamis**: skor ketegangan 0–100 per pasangan negara yang bergerak oleh perilaku (tarif, sanksi, lobi pemain, pemilu), tangga eskalasi 6 anak tangga; pemilu per negara dengan hasil memengaruhi kebijakan.
A5. **Makro lanjut**: fiskal pemerintah (defisit, utang), kurva imbal hasil, peringkat negara dan efeknya ke kurs/spread; bank sentral dengan bias politik, QE, forward guidance; bank run/kepanikan kawanan rumah tangga; kurs dari neraca dagang.
A6. **Informasi**: rilis data tertunda dan direvisi; harga saham dari buku pesanan agen sederhana (bukan hanya nilai wajar+sentimen).
A7. **Siklus jangka panjang**: demografi, iklim (risiko fisik per wilayah), teknologi (kurva adopsi) yang menggeser parameter antar-era.
A8. **Rekayasa**: ubah CI agar langkah uji unit gagal bila tes gagal; tambah tes kinerja `EndTurn`; tambah tes determinisme untuk komponen baru; perbarui audit.

Kriteria selesai Tahap A: semua invarian lulus pada tes tekanan baru (≥5.000 giliran acak), determinisme lulus, `EndTurn` <3 dtk pada bot, CI hijau **dan** log tes bersih, audit diperbarui dengan angka nyata.

---

## 5. TAHAP B — MEKANIK PEMAIN DAN PERILAKU BOT

Tujuan: keputusan pemain lebih kaya, lawan lebih cerdas.

B1. **Intelijen**: riset berbayar dengan margin error (beli laporan sektor/negara/pesaing; kualitas bergantung anggaran); rapat dewan kuartalan dengan anggota dewan AI yang bertanya tajam (jawaban memengaruhi kepercayaan dewan).
B2. **Pasar produk**: 5 segmen konsumen, tren, kurva adopsi, merek dan pemasaran (menggantikan permintaan agregat satu produk).
B3. **Operasi**: SDM (karyawan kunci, rekrut/pecat, pembajakan staf), R&D, gudang dan logistik, armada.
B4. **Keuangan lanjut**: L/C, sale-leaseback, asuransi; derivatif — opsi valas, futures komoditas, swap suku bunga, CDS; withholding atas bagian NCI; harga jual saham minoritas dinegosiasikan, hak veto/tag-along; grafik harga saham sungguhan di UI.
B5. **Forensik dan hukum**: SPV, window dressing, M-score, auditor eksternal; antimonopoli; skandal tertunda; ESG dan etika yang bergerak oleh bukti yang menua.
B6. **Bot**: politisi (siklus, pemerasan regulasi), pemasok (hold-up berlapis), investor aktivis (greenmail, bear raid), pesaing (pembajakan staf, korner pasar, lobi balik, koalisi), eksekutif egois lebih banyak perilaku; semuanya deterministik dan **belajar/bereaksi** terhadap pola pemain (uji: dua gaya main, seed sama → perilaku bot berbeda).
B7. **Pengasah otak**: pasar prediksi, detektor bias kognitif, post-mortem lebih kaya.
B8. **Simpan**: 5 slot autosave; kesulitan terwarisi di tombol "game baru".
B9. **Bot uji**: perbaiki `ActiveBot` agar mengelola leverage, kas minimum, hedging — sehingga ia mewakili pemain cakap.

Kriteria selesai Tahap B: tiap mekanik baru punya aksi ter-encode, tes unit, ikut tes tekanan acak; tidak ada aksi yang bisa dieksploitasi untuk uang gratis (tes anti-eksploit: bot acak 200 seed tidak menghasilkan kekayaan tak wajar); audit diperbarui.

---

## 6. TAHAP C — KONTEN, UI, KESEIMBANGAN, MULTIPLAYER

C1. **Konten**: perluas 42 → **≥600 kartu kejadian**. Generator bertemplat boleh, tetapi tiap kartu harus (a) dipicu kondisi dunia, bukan acak murni, (b) punya ≥2 pilihan dengan trade-off nyata, (c) lulus `ChainCardsTest` (validasi), (d) ada ≥100 kartu berantai dengan penjelasan sebab-akibat. Kerjakan bertahap per kategori dan setelah tiap ±50 kartu jalankan simulasi untuk memastikan tidak ada kartu mendominasi.
C2. **UI nyata**: verifikasi layar di emulator/perangkat (screenshot tes Compose/Paparazzi bila tersedia); perbaiki tata letak, keterbacaan, navigasi; tambah grafik (harga saham, kas, laba), peta wilayah dengan petak bidak, tutorial singkat. Jika tidak bisa menjalankan emulator di lingkunganmu, tulis daftar uji manual untuk manusia dan **jangan klaim UI sudah terverifikasi**.
C3. **Keseimbangan**: setel dengan simulasi besar (≥200 seed) memakai tiga bot (pasif, aktif-cakap, sembrono). Target: pasif menang ≈25–40%, cakap ≈55–75%, sembrono <10%; kematian Era 1 ≤20%; tidak ada satu strategi yang menang >85%. Catat parameter yang diubah dan alasannya. Minta uji main manusia dan tampung umpan baliknya di `docs/`.
C4. **Peran dan multiplayer**: peran alternatif (gubernur, menteri, manajer dana) minimal sebagai mode terpisah dengan tujuan dan skor sendiri; multiplayer asinkron berbasis seed bersama + log aksi (server tipis atau berbagi berkas), tanpa mengorbankan determinisme.
C5. **Transisi era berbasis kinerja** (opsional kebijakan): kriteria pendapatan/ekuitas/entitas yang memengaruhi fitur terbuka, dengan jaring pengaman agar tidak mengunci pemain.
C6. **Rilis**: penandatanganan release, ikon, ukuran APK, kinerja, aksesibilitas, tata bahasa teks; gabungkan ke `main` setelah izin pemilik repo; tulis catatan rilis jujur.

Kriteria selesai Tahap C: ≥600 kartu tervalidasi, keseimbangan memenuhi target atau selisihnya didokumentasikan, UI terverifikasi (atau daftar uji manual diserahkan), CI hijau dengan tes yang benar-benar dijalankan.

---

## 7. CARA MELAPOR TIAP TAHAP

Setelah tiap tahap berikan: (1) apa yang berubah, (2) bukti (jumlah tes, angka simulasi, tautan CI), (3) daftar jujur yang belum, (4) satu langkah berikutnya. Jangan merekap langkah kerja panjang; jangan memuji hasil sendiri tanpa angka.
