# KAPITAL — Serah Terima (Tahap 6)

Status per commit terakhir di cabang `claude/simulasi-era1` (PR #1). Dokumen ini jujur tentang apa yang sudah terbukti dan apa yang belum.

## Arsitektur
- `engine/KapitalEngine.kt` — mesin deterministik. Satu-satunya pintu masuk perubahan state adalah `apply(Action)`. Seed + daftar `Action` ter-encode = file simpanan (`SEED:` lalu satu aksi per baris). Pemutaran ulang menghasilkan state identik.
- `engine/Actions.kt` — semua aksi pemain + encode/decode. Menambah aksi: tambah `data class`, kode di `encode()`, `decode()`, cabang di `apply`.
- `engine/Events.kt` — dek kartu kejadian (42 templat, 9 hanya-berantai), `chainRules`, `Effect(kind, a, b, s)` yang ditangani `applyEffect` di mesin.
- `engine/World.kt` (pesaing yang belajar), `Bourse.kt`, `Credit.kt`, `Commodities.kt`, `Scenario.kt` (awal permainan acak).
- **Lapisan dunia (Tahap A)**, dikendalikan `engine/Dunia.kt` dan dipanggil sekali per `EndTurn` sebelum loop negara asing: `Countries.kt` (24 negara), `Geopolitics.kt` (ketegangan, sanksi, selat, pemilu), `Sectors.kt` (Leontief + rantai), `MacroPlus.kt` (fiskal, kurva, peringkat, bank sentral, panik), `Banking.kt` (2.000 perusahaan, 120 bank), `Market.kt` (buku pesanan 5.000 investor), `DataDesk.kt` (rilis data), `LongCycles.kt` (demografi, iklim, teknologi), `Agents.kt` (kohort rumah tangga). Aliran acak 101–108 dipakai lapisan dunia; jangan memakai ulang. Prinsip: lapisan ini **tidak membuat arus kas**; ia hanya mengubah permintaan, biaya pemain (`cogsRatio`), kurs, premi kredit, dan bias Taylor.
- `data/GameRepository.kt` — jembatan mesin ke UI: menyusun semua baris laporan, menyimpan log aksi. `viewmodel/`, `ui/` — Compose.
- Era: 4 era (giliran 1–96, 97–228, 229–372, 373–480). Fitur dikunci lewat `eraBlock(n)`. Rapor akhir era menggeser syarat kredit era berikutnya.

## Invarian akuntansi (wajib dijaga)
Toleransi 1e-6, diuji tiap giliran oleh `StressCalibrationTest`: aset = liabilitas + ekuitas induk; arus kas = Δkas; roll-forward ekuitas; kas grup konsolidasi = Δkas grup; ekuitas konsolidasi = awal + laba + langsung + ΔCTA + dekonsolidasi. Aksi ekuitas baru (IPO, SPO, jual saham minoritas, buyback) harus lewat `periodCureIn` atau variabel `dividend` agar identitas ini otomatis terjaga.

## Menjalankan
- CI: `.github/workflows/build-apk.yml` membangun APK pada tiap push.
- Tes lokal tanpa Android: kompilasi `engine/`, `data/`, `model/` dengan kotlinc JVM + stub JUnit; kelas tes ada di `app/src/test`. Simulasi tambahan di `tools/sim/`.
- Skrip `tools/run-tests.sh` (set `KOTLINC_HOME`) menjalankan seluruh suite tanpa Android SDK (±3–6 menit). Tes Tahap A: `AgentScaleTest`, `WorldLayerTest`, `MacroPlusTest`, `MarketTest`, `CreditSystemTest`, `LongCyclesTest`, `PerformanceTest`, `StageAStressTest` (≥5.000 giliran acak, semua lapisan), `WorldViewTest` (tampilan dunia hidup, selat menutup alami).
- Tes kunci: `StressCalibrationTest` (identitas, determinisme), `NciTest`, `StockTest`, `BalanceTest` (bot pasif vs aktif), `RivalLearningTest`, `DifficultyTest`, `EraReviewTest`, `SuccessionTest`.

## Fitur yang selesai (terbukti oleh tes)
Pilihan kesulitan (4 tingkat, tersimpan sebagai aksi pertama); pesaing belajar dari gaya pemain; kartu berantai; krisis makro per era; anak usaha multi-mata-uang + CTA + transfer pricing + tarif/risiko politik; laporan konsolidasi + per entitas; IPO, harga saham, dividen/saham, buyback, SPO; kepentingan non-pengendali; obligasi, bank grup, media/lobi, karbon; suksesi CEO dengan serah terima Desember 2068; evaluasi 5 komponen.

## Keseimbangan terukur (akhir Tahap A: 300 seed, 480 giliran)
Bot pasif disiplin menang 132/300 (44%); bot aktif 192/300 (64%). Rincian dan catatan kalibrasi: `docs/AUDIT_vs_KONSEP.md`. (Angka lama 60 seed: pasif 28/60, aktif 33–34/60.) Kematian Era 1 ±20% (fase bertahan hidup, disengaja), Era 4 ±15% (sisa guncangan Era 3 dan serah terima darurat). Selisih aktif vs pasif masih tipis: bot aktif belum mengelola leverage. **Belum disetel oleh manusia.**

## Belum dikerjakan / risiko
1. Tampilan Compose belum pernah dilihat di perangkat; hanya terbukti kompilasi di CI.
2. Kartu kejadian: 621 (selesai di C1, lihat bagian Tahap C); menambah lagi = baris baru di `engine/CardData*.kt`.
3. Multiplayer belum dimulai (sesuai desain, terakhir).
4. Derivatif: opsi valas, futures kapas, swap suku bunga sudah ada (B4b); CDS dan opsi jual belum. Withholding NCI dan grafik harga saham selesai (B4c); tag-along belum.
5. Transisi era berbasis kalender; kinerja hanya memengaruhi syarat kredit.
6. Cabang `main` milik pihak lain tidak dapat dikompilasi; cabang ini adalah basis yang valid.

## Tahap B (mekanik pemain dan perilaku bot) — status akhir sesi ini
Berkas mesin baru: `engine/Derivatives.kt` (derivatif), `Forensics.kt` (skor-M Beneish), `Actors.kt` (politisi/aktivis/eksekutif egois yang belajar, `ActorTuning`), `Insight.kt` (pasar prediksi berpoin, `BiasDetector`, `PostMortemBuilder`).
Kode aksi baru: `DRV` derivatif, `WDR` percantikan laporan, `EAU` auditor, `SPV`, `PMK` taruhan pasar prediksi; `NCI|c|pct|ask` kini membawa diskon tawar.
Tes baru: `DerivativesTest`, `MinorityTest`, `ForensicsTest`, `LawTest`, `ActorsTest`, `InsightTest`, `SaveSlotsTest`, `ActiveBotTest` (daftar di `tools/run-tests.sh`). Suite lokal terakhir: 193 lulus, 0 gagal. `StressCalibrationTest.randomActionB` memuat aksi B4–B7 dan dipakai `AntiExploitTest` (200 seed).
Simpanan: `GameRepository(store, seedOverride, slots: SlotStore?)`; lima slot autosave bergilir tiap 6 giliran (baris `META:` setelah `SEED:`), tidak ada pada Ironman; "Permainan baru" tanpa argumen mewarisi kesulitan.
Bot uji: `ActiveBot` (di `TestBots.kt`) mengatur harga, cadangan kas untuk belanja non-produktif, batas leverage, lindung nilai, asuransi; sakelar `ActiveBotTuning` untuk eksperimen. Keseimbangan terukur: pasif ±41% (300 seed), aktif ±64% (200 seed).
Catatan jujur tiap mekanik ada di `docs/AUDIT_vs_KONSEP.md` (baris B4a–B9).

## Tahap C — C1 (dek kartu) — status
`engine/EventCards.kt` (arketipe, pemicu, pembangun kartu, undian) + `CardData1–14.kt` (tabel; format baris di `EventCards.parse`, kolom 15–16 opsional: lama jendela, jeda). 621 kartu, 106 berantai. Kartu generator diundi dari aliran acak 125 (peluang `CardTuning.drawChance`=0,20 per giliran, skala `CardTuning.severityScale`); dek lama (`EventDeck.baseTemplates`) tidak menyentuh kartu generator. Menambah kartu: tambah baris ke tabel, jalankan `CardDeckTest ChainCardsTest`; kartu berantai harus dirujuk satu aturan rantai di baris kartu pemicunya (`target:pilihan:peluang:tunda:sebab`).
Keseimbangan dengan kartu (200 seed): pasif 35%, cakap 62%; tanpa kartu 40%/64%. Kematian Era 1 masih di atas target (C3).

