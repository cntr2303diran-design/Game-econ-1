# KAPITAL — Serah Terima (Tahap 6)

Status per commit terakhir di cabang `claude/simulasi-era1` (PR #1). Dokumen ini jujur tentang apa yang sudah terbukti dan apa yang belum.

## Arsitektur
- `engine/KapitalEngine.kt` — mesin deterministik. Satu-satunya pintu masuk perubahan state adalah `apply(Action)`. Seed + daftar `Action` ter-encode = file simpanan (`SEED:` lalu satu aksi per baris). Pemutaran ulang menghasilkan state identik.
- `engine/Actions.kt` — semua aksi pemain + encode/decode. Menambah aksi: tambah `data class`, kode di `encode()`, `decode()`, cabang di `apply`.
- `engine/Events.kt` — dek kartu kejadian (42 templat, 9 hanya-berantai), `chainRules`, `Effect(kind, a, b, s)` yang ditangani `applyEffect` di mesin.
- `engine/World.kt` (pesaing yang belajar), `Bourse.kt`, `Credit.kt`, `Commodities.kt`, `Scenario.kt` (awal permainan acak).
- `data/GameRepository.kt` — jembatan mesin ke UI: menyusun semua baris laporan, menyimpan log aksi. `viewmodel/`, `ui/` — Compose.
- Era: 4 era (giliran 1–96, 97–228, 229–372, 373–480). Fitur dikunci lewat `eraBlock(n)`. Rapor akhir era menggeser syarat kredit era berikutnya.

## Invarian akuntansi (wajib dijaga)
Toleransi 1e-6, diuji tiap giliran oleh `StressCalibrationTest`: aset = liabilitas + ekuitas induk; arus kas = Δkas; roll-forward ekuitas; kas grup konsolidasi = Δkas grup; ekuitas konsolidasi = awal + laba + langsung + ΔCTA + dekonsolidasi. Aksi ekuitas baru (IPO, SPO, jual saham minoritas, buyback) harus lewat `periodCureIn` atau variabel `dividend` agar identitas ini otomatis terjaga.

## Menjalankan
- CI: `.github/workflows/build-apk.yml` membangun APK pada tiap push.
- Tes lokal tanpa Android: kompilasi `engine/`, `data/`, `model/` dengan kotlinc JVM + stub JUnit; kelas tes ada di `app/src/test`. Simulasi tambahan di `tools/sim/`.
- Tes kunci: `StressCalibrationTest` (identitas, determinisme), `NciTest`, `StockTest`, `BalanceTest` (bot pasif vs aktif), `RivalLearningTest`, `DifficultyTest`, `EraReviewTest`, `SuccessionTest`.

## Fitur yang selesai (terbukti oleh tes)
Pilihan kesulitan (4 tingkat, tersimpan sebagai aksi pertama); pesaing belajar dari gaya pemain; kartu berantai; krisis makro per era; anak usaha multi-mata-uang + CTA + transfer pricing + tarif/risiko politik; laporan konsolidasi + per entitas; IPO, harga saham, dividen/saham, buyback, SPO; kepentingan non-pengendali; obligasi, bank grup, media/lobi, karbon; suksesi CEO dengan serah terima Desember 2068; evaluasi 5 komponen.

## Keseimbangan terukur (60 seed, 480 giliran)
Bot pasif disiplin menang 28/60; bot aktif 33–34/60. Kematian Era 1 ±20% (fase bertahan hidup, disengaja), Era 4 ±15% (sisa guncangan Era 3 dan serah terima darurat). Selisih aktif vs pasif masih tipis: bot aktif belum mengelola leverage. **Belum disetel oleh manusia.**

## Belum dikerjakan / risiko
1. Tampilan Compose belum pernah dilihat di perangkat; hanya terbukti kompilasi di CI.
2. Kartu kejadian 42 dari target ±600 — kekurangan konten terbesar. Cara menambah: templat baru di `Events.kt`, ikuti pola `Template(id, kategori, jeda, bobot) { ... }`; tes `ChainCardsTest` memvalidasi semua kartu.
3. Multiplayer belum dimulai (sesuai desain, terakhir).
4. Opsi valas tidak ada (hanya forward); pajak withholding atas bagian NCI tidak dihitung; tidak ada grafik harga saham sungguhan.
5. Transisi era berbasis kalender; kinerja hanya memengaruhi syarat kredit.
6. Cabang `main` milik pihak lain tidak dapat dikompilasi; cabang ini adalah basis yang valid.
