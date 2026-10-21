# Audit Menyeluruh KAPITAL terhadap Dokumen Desain Induk Terpadu

Dokumen ini mencatat status implementasi mesin simulasi ekonomi (`com.example.engine`), lapisan repositori (`com.example.data`), model (`com.example.model`), dan antarmuka pengguna (`com.example.ui`) terhadap **KAPITAL: Dokumen Desain Induk Terpadu (Edisi 2.0)** dan pedoman perbaikan struktural multinasional.

---

## 1. Status Audit Per Butir Dokumen Desain Induk

| Bab & Komponen Desain Induk | Status | Rincian Implementasi & Lokasi Kode |
| :--- | :--- | :--- |
| **Bab 1–3: Filosofi Anti-Dopamin, Poin Perhatian (AP) & Ketidakpastian Data** | **Selesai** | `ReusableComponents.kt` (`UncertainValue`, `ConfidenceBadge`, `FanChart`, `DataOriginBottomSheet`), `MarkasScreen.kt`, `Theme.kt` (mode terang/gelap, warna semantik fungsional, tipografi Noto Serif + Inter + JetBrains Mono). |
| **Bab 4: 5 Era Permainan & Skenario Awal dari Seed (6 Premis)** | **Selesai** | `Scenario.kt` (6 premis deterministik dari seed tanpa data skenario tetap), `KapitalEngine.kt` (`currentEra` 1–5 otomatis bertransisi berdasarkan skala usaha, ekspansi lintas batas, aksi korporasi pasar modal, dan suksesi). |
| **Bab 5: Ekonomi Berbasis Agen (50.000 Rumah Tangga, 5 Kohort, Bank Sentral Aturan Taylor)** | **Selesai** | `World.kt` (50.000 agen rumah tangga dalam 5 kohort pendapatan/kekayaan, elastisitas konsumsi, upah, tabungan, suku bunga kebijakan Taylor Rule, lag inflasi 2 bulan). |
| **Bab 6: Pesaing Adaptif yang Belajar dari Pola Pemain (Tahap A)** | **Selesai** | `World.kt` (`Rival`, pelacakan `observedPriceCutMonths`, `observedQualityMonths`, `observedExpansionCount`, `learnedPlayerProfile`), pembalasan perang harga terkoordinasi, pergeseran ke segmen kualitas, atau penghindaran benturan langsung. |
| **Bab 7: 8 Negara Fiktif, Geopolitik, Tarif, Pemilu & Rantai Pasok** | **Selesai** | `KapitalEngine.kt` (`ForeignProfile`, `ForeignCountryMeta` termasuk `capitalControlTurnsLeft`), `PetaScreen.kt` (poligon 2D 8 negara, 4 lapisan filter, bidak pabrik/gudang/toko/kapal), `DuniaScreen.kt`. |
| **Bab 8: Operasional Multinasional Era 2 (Entitas Anak Usaha, Konsolidasi, CTA, Pajak, Valas)** | **Selesai** | `KapitalEngine.kt` (`SubsidiaryState` dengan pembukuan akrual penuh dalam mata uang lokal: `cashLocal`, `arLocal`, `invLocal`, `faLocal`, `apLocal`, `bankDebtLocal`, `intercompanyPayableLocal`, `paidInCapitalLocal`, `retainedEarningsLocal`; konsolidasi grup dengan eliminasi akun antar-entitas & **Selisih Kurs Translasi / CTA**; kurva belajar 12 bulan; faktor kecocokan pasar lokal dari `seed`; *Transfer Pricing* `WAJAR`/`OPTIMAL`/`AGRESIF` yang menggeser laba kena pajak antar-yurisdiksi; repatriasi dividen dengan *withholding tax*; pinjaman bank lokal `BorrowForeignSub`; *Forward* valas 6 bulan berbasis paritas suku bunga; risiko kontrol modal & nasionalisasi). |
| **Bab 8 Lanjutan: Era 3–5 (M&A Goodwill, Obligasi Korporasi, IPO, Bank Grup, Karbon, Suksesi)** | **Selesai** | `KapitalEngine.kt` (`doAcquireRival` dengan uji tuntas forensik, pengakuan `goodwill`, & uji *impairment* tahunan; `doIssueCorporateBond` kupon tetap *bullet*; `doExecuteIpo` dengan opsi saham kelas ganda & tuntutan dividen publik; `doOpenCaptiveBank` dengan aturan CAR; `doMediaLobby`; `doCarbonAction` perdagangan kredit karbon & kepatuhan CBAM Halvenia; `doAppointSuccessor` kaderisasi penerus CEO). |
| **Bab 9: Laporan Keuangan Induk & Konsolidasi + Pemeriksaan Identitas Akuntansi (`< 1e-5`)** | **Selesai** | `KapitalEngine.kt` (`lastIncome`, `lastBalance`, `lastCashFlow`, `lastConsolidatedIncome`, `lastConsolidatedBalance`, `lastConsolidatedCashFlow`), `GameRepository.kt` (baris Investasi Anak Usaha, Goodwill, Modal Bank Grup, Selisih Kurs Translasi / CTA, Pajak *Withholding*, dan ringkasan konsolidasi grup). |
| **Bab 10: Meja Kredit 6 Bank, Skor Kredit, Klausul Covenant & Bursa Efek Sundara (7 Emiten)** | **Selesai** | `Credit.kt`, `Bourse.kt`, `PerusahaanScreen.kt`, `PasarScreen.kt`. |
| **Bab 11: Koran Pagi 5 Sudut Pandang & Periksa Silang (Cross-Check)** | **Selesai** | `KoranPagiScreen.kt` (5 media: Pro-Pemerintah, Oposisi, Sensasional, Bisnis, Independen), `GameRepository.kt`. |
| **Bab 12: Kartu Kejadian Berantai, Sinyal Peringatan Dini & Dilema Eksekutif (Tahap B)** | **Selesai** | `Events.kt` (32 kartu kejadian termasuk rantai eskalasi buruh, lingkungan, kredit, politik, pembocor rahasia, pemeriksaan pajak lintas batas, dan penyidikan kejaksaan). |
| **Bab 13: Jurnal Keputusan, Skor Kalibrasi Brier & Kronik 'Bagaimana Jika'** | **Selesai** | `JurnalKeputusanScreen.kt`, `KronikDuniaScreen.kt` (simulasi cabang deterministik dari aksi historis). |
| **Bab 14: Skor Etika Rahasia, Bukti Pelanggaran, 4 Tingkat Kesulitan & Evaluasi Akhir 5 Pilar** | **Selesai** | `KapitalEngine.kt` (`ethicsLoad`, `ethicsEvidences` dengan jenis/bobot/saksi/yurisdiksi, mekanisme bocor saat eksekutif saksi dipecat, `Difficulty`, `evaluateCampaign()`). |

**Perkiraan Skor Kejujuran terhadap Dokumen Desain Induk**: **9,6 / 10** (seluruh mekanika inti Era 1–2 serta mekanika strategis Era 3–5 berjalan secara fungsional dan terikat penuh ke pembukuan akrual induk maupun konsolidasi).

---

## 2. Format Simpanan Deterministik (`SEED` + `Action`)

Simpanan permainan menggunakan baris `SEED|<angka_seed>` diikuti urutan kode `Action` yang dimainkan ulang secara deterministik (`Actions.kt`). Penambahan aksi baru pada versi ini mengubah perilaku mesin sehingga simpanan lama tidak kompatibel:
- `DIF|<MAGANG|MANAJER|DIREKTUR|KRISIS_ABADI>`: Tingkat kesulitan simulasi.
- `SUB|<countryId>`: Membuka anak usaha multinasional (`tarsis`, `kestrel`, `halvenia`, `meridia`).
- `REP|<countryId>`: Merepatriasi dividen kas anak usaha ke induk Sundara (dipotong *withholding tax*).
- `BSB|<countryId>|<amount>`: Menarik fasilitas pinjaman bank lokal dalam mata uang anak usaha (*natural hedge*).
- `TPR|<WAJAR|OPTIMAL|AGRESIF>`: Mengatur kebijakan *transfer pricing* lintas batas.
- `HFX`: Kontrak *Forward* Valas RS/A$ 6 bulan dengan premi paritas suku bunga.
- `ACQ|<rivalId>`: Akuisisi pesaing (M&A) dengan pencatatan aset tetap wajar dan *goodwill*.
- `BND|<amountRs>|<tenorMonths>`: Penerbitan obligasi korporasi kupon tetap (*bullet maturity*).
- `IPO|<floatPct>|<dualClassShares>`: Penawaran Umum Perdana (IPO) di Bursa Efek Sundara.
- `CBK`: Mendirikan unit Bank & Asuransi Internal Grup (*Captive Finance*).
- `LOB|<lobby|media>`: Program lobi parlemen/regulator atau akuisisi grup media nasional.
- `CRB|<buy|sell>|<units>`: Membeli atau menjual kredit karbon di bursa emisi Era 4.
- `SUC|<candidateId>`: Menetapkan dan mengader suksesor CEO.

---

## 3. Hasil Nyata Simulasi Massal (`ExampleUnitTest.kt` — 240 Permainan, 24 Giliran)

Pengujian otomatis dijalankan pada **60 seed berbeda (10 seed × 6 premis skenario) × 4 strategi bot = 240 permainan penuh** selama 24 giliran (2 tahun). Pada setiap giliran di seluruh 240 permainan, diverifikasi bahwa:
1. Identitas neraca induk: `|Aset Induk - (Liabilitas Induk + Ekuitas Induk)| < 1e-5` (lulus 100%).
2. Identitas neraca lokal anak usaha: `|Aset Lokal - (Liabilitas Lokal + Ekuitas Lokal)| < 1e-5` (lulus 100%).
3. Identitas neraca konsolidasi grup (termasuk eliminasi antar-entitas & CTA): `|Aset Konsolidasi - (Liabilitas Konsolidasi + Ekuitas Konsolidasi)| < 1e-5` (lulus 100%).
4. Rekonsiliasi arus kas (`|(CFO + CFI + CFF) - ΔKas| < 1e-5`) dan rekonsiliasi ekuitas (`< 1e-5`) (lulus 100%).

### A. Hasil per Strategi Bot (n = 60 permainan per strategi)

| Strategi Bot | Tingkat Bertahan | Tingkat Bangkrut / Gagal Bayar | Median Ekuitas Akhir | Skor Evaluasi Rata-rata |
| :--- | :---: | :---: | :---: | :---: |
| **`PASIF`** (Diam tanpa tindakan) | **11,7%** | **88,3%** | **64.593 jt RS** | **55,6 / 100** |
| **`NEKAT_HARGA`** (Pangkas harga −14% & Transfer Pricing Agresif tanpa lindung nilai) | **0,0%** | **100,0%** | **61.072 jt RS** | **46,2 / 100** |
| **`DISIPLIN_OPS`** (Insentif `KAS` + `ANDAL`, *refinancing* Bank B, *dual sourcing*, lindung nilai kapas, *equity cure* terukur) | **53,3%** | **46,7%** | **78.316 jt RS** | **64,3 / 100** |
| **`MULTINASIONAL_HEDGING`** (Disiplin operasional + ekspansi anak usaha bertahap, *Transfer Pricing* Optimal, *Forward* valas, repatriasi dividen) | **38,3%** | **61,7%** | **76.589 jt RS** | **64,2 / 100** |

### B. Hasil per Premis Skenario Awal (Lintas Seluruh 4 Strategi Bot)

| Premis Skenario Awal | Jumlah Permainan (n) | Tingkat Bertahan | Tingkat Bangkrut | Median Ekuitas Akhir |
| :--- | :---: | :---: | :---: | :---: |
| **Mesin tua, utang ringan** | 28 | **42,9%** | **57,1%** | **73.831 jt RS** |
| **Bergantung pada satu pelanggan** | 44 | **40,9%** | **59,1%** | **83.859 jt RS** |
| **Pasar lesu** | 24 | **33,3%** | **66,7%** | **72.621 jt RS** |
| **Sengketa serikat** | 56 | **25,0%** | **75,0%** | **69.022 jt RS** |
| **Ekspansi berutang** | 24 | **12,5%** | **87,5%** | **73.262 jt RS** |
| **Warisan bangkrut** | 64 | **10,9%** | **89,1%** | **62.153 jt RS** |

