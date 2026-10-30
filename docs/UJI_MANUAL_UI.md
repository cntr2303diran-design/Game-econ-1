# Daftar uji manual UI KAPITAL (C2)

**Status jujur:** sejak sesi "Selesaikan yang kurang", tampilan tidak lagi hanya terkompilasi. Tiap push dijalankan otomatis di CI: (1) uji UI Robolectric di JVM (`PhoneUiTest`, `TabletUiTest`, `MenuStateUiTest`) yang membuka aplikasi sungguhan, mengetuk tombolnya, dan menulis "tangkapan teks" tiap layar; (2) emulator Android 14 (ART, x86_64): tur semua tab dan sepuluh subtab Perusahaan pada permainan baru dan pada simpanan akhir permainan, setiap tombol korporasi, penjelajah acak, APK release (R8) dengan 2.500 kejadian monkey, dan tangkapan layar nyata (JPG, tersimpan di cabang `ci-hasil-claude-simulasi-era1`). Yang dibuktikan mesin: tidak ada crash, setiap layar dan tombol utama terbuka, status dan angka sesuai keadaan permainan, simpanan dan slot termuat, pemuatan di latar belakang tidak membekukan layar.

**Yang tetap harus dinilai manusia** (mesin tidak bisa menilainya): rasa pakai dan keterbacaan di ponsel sungguhan, kepadatan dan keindahan tata letak, kinerja pada CPU ARM dan perangkat murah, TalkBack, lanskap dan tablet, font sistem 130%, tema gelap (hanya diambil satu tangkapan), dan apakah permainannya menyenangkan. Daftar di bawah adalah daftar itu. Kolom "Hasil" sengaja kosong sampai manusia mengisinya; jangan menganggap sebuah baris terverifikasi hanya karena uji otomatis tidak gagal.

**Cakupan otomatis per bagian** (uji ada di `app/src/uiTest` dan `app/src/androidTest`; yang tercantum adalah yang diperiksa oleh pernyataan uji, bukan sekadar tidak crash):
| Bagian | Dicakup otomatis | Tetap manual |
|---|---|---|
| A Menu dan alur awal | A1–A3: menu, dialog Cara bermain, dialog kesulitan (semua tingkat ada, Manajer dimulai), giliran pertama, kembali ke menu, ganti tema (`PhoneUiTest.freshInstall_menuToFirstTurn_andBack`); A5: muat slot di latar belakang lalu pindah ke Markas (`autosaveSlot_loadsInBackground_thenOpensMarkas`); A7: panel pemuatan dan tombol tersembunyi (`MenuStateUiTest`) serta responsivitas di emulator | A4 tutup paksa sungguhan, A6 rotasi perangkat, tampilan tiap kesulitan |
| B Markas dan giliran | B2: akhiri giliran, konfirmasi, laporan bulanan, giliran +1; B7: panel permainan berakhir dan permainan baru (`lostGame_showsGameOverPanel_andNewGameWorks`); spanduk peringatan simpanan lama dan hilangnya spanduk | B1 keterbacaan; B3–B5 kartu keputusan hanya tersentuh penjelajah acak, isinya tidak diperiksa |
| C Perusahaan, Operasi, Intel | C1: sepuluh subtab terbuka di Era 1 dan akhir permainan; tombol korporasi: Era 1 menolak dengan alasan, akhir permainan semuanya menjawab (`corporateButtons_inEra1...`, `lateGame_everyCorporateButtonAnswers`) | C2–C7: tampilan grafik, alur taruhan, derivatif, Intel |
| D Pasar, Bursa, Peta, Dunia | semua tab terbuka di Era 1 dan akhir permainan; Kronik "Bagaimana Jika" menghitung di latar belakang dan menampilkan hasil (`chronicleCounterfactual_runsInBackground_andShowsTheResult`) | D2–D4 tampilan dan interaksi Peta; D5–D6 isi |
| E Aksesibilitas | tidak ada pernyataan uji; deskripsi konten tercatat dalam tangkapan teks | seluruh E1–E5 pada perangkat |
| F Kinerja | pemuatan simpanan akhir diukur di CI (`cold-load.txt`, `load_time.txt`); 2.500 kejadian monkey pada APK release tanpa crash atau ANR | F1–F3 pada ponsel sungguhan |
| G Peran dan tantangan | tiap peran dimainkan sampai selesai dan hasilnya masuk papan peringkat (`role_*_playsToTheEnd`); tantangan CEO dimulai dari layar Peran (`ceoChallenge_startsFromPeranScreen_withTypedSeed`) | G4, G6–G8: salin-tempel dua pemain; G10 rotasi dan font |
| H Syarat era dini | umpan balik tombol era terkunci memuat "Belum bisa" di Era 1 | H1 tampilan syarat, H2–H3 butuh bermain panjang |
| Tata letak lebar | `TabletUiTest` menjalankan uji dasar pada 900x560 dp lanskap (tangkapan teks) | melihat tata letaknya |

Cara mengisi: pasang APK dari artefak CI cabang `claude/simulasi-era1`, jalankan tiap baris, isi Hasil dengan LULUS / GAGAL + catatan (perangkat, versi Android, ukuran layar).

Perangkat minimum yang disarankan: ponsel 5,5" (360 dp lebar), ponsel besar (412 dp), tablet 10" atau mode lanskap, tema terang dan gelap, ukuran font sistem 100% dan 130%.

## A. Menu dan alur awal
| # | Langkah | Yang diharapkan | Hasil |
|---|---|---|---|
| A1 | Buka aplikasi pertama kali | Menu muncul tanpa crash; tombol Permainan baru, Lanjutkan (bila ada simpanan), Cara bermain, Tentang | |
| A2 | Permainan baru → pilih tiap kesulitan | Dialog kesulitan; permainan mulai di Markas; seed berbeda tiap permainan baru | |
| A3 | Cara bermain | Dialog bisa digulir; 9 butir termasuk kartu keputusan dan Intel; tombol Mengerti menutup | |
| A4 | Tutup paksa aplikasi lalu buka lagi → Lanjutkan | Permainan kembali ke giliran yang sama dengan kas dan ekuitas sama | |
| A5 | Menu → Muat autosave | Daftar slot (maks. 5) berlabel giliran; memuat satu slot menampilkan panel "Memuat permainan tersimpan…" dengan persen, lalu pindah sendiri ke Markas pada keadaan slot itu; Ironman tidak menampilkan slot | |
| A6 | Putar perangkat (potret ↔ lanskap) di tiap layar | Tidak ada crash; keadaan permainan dan input tidak hilang; lanskap memakai dua kolom di Markas | |
| A7 | Buka aplikasi dengan simpanan sangat panjang (±30 tahun bermain) | Menu muncul seketika dengan panel pemuatan berpersen; layar tetap menjawab sentuhan (Cara bermain bisa dibuka); tombol permainan baru tersembunyi sampai selesai; catat berapa detik sampai tombol Lanjutkan muncul | |

## B. Markas dan alur giliran
| # | Langkah | Yang diharapkan | Hasil |
|---|---|---|---|
| B1 | Lihat Markas | Peringatan, koran pagi, indikator, FanChart, keputusan menunggu, tombol Akhiri giliran tidak saling menimpa | |
| B2 | Akhiri giliran | Laporan bulanan tampil; Attention Points (AP) pulih; giliran bertambah satu | |
| B3 | Buka kartu keputusan (dialog) | Judul, isi, konteks, 3 pilihan dengan peluang dan biaya AP terbaca; pilihan bawaan jelas; tombol cukup besar (≥48 dp) | |
| B4 | Kartu lanjutan (berantai) | Teks peringatan menjelaskan sebab-akibat dari keputusan sebelumnya | |
| B5 | Abaikan kartu sampai jendela habis | Pilihan bawaan dijalankan; masuk jurnal keputusan | |
| B6 | Tekan lama / ketuk sebuah angka | Lembar asal data dan tingkat keyakinan terbuka | |
| B7 | Permainan berakhir (bangkrut atau menang) | Panel akhir tampil; Permainan baru mewarisi kesulitan; pasca-mortem bias terbaca | |

## C. Perusahaan, Operasi, Intel
| # | Langkah | Yang diharapkan | Hasil |
|---|---|---|---|
| C1 | Layar Perusahaan: tiap tab | Tab bisa digulir dan tidak terpotong; angka tidak bertumpuk | |
| C2 | Operasi: kartu **Riwayat perusahaan** (baru) | Tiga grafik (Kas, Ekuitas, Laba bersih) muncul setelah ≥2 bulan; garis tipis nol muncul pada laba/rugi; teks awal→kini cocok dengan laporan | |
| C3 | Operasi: grafik harga saham (hanya setelah IPO) | Garis dengan acuan harga awal; tidak muncul sebelum IPO | |
| C4 | Operasi: derivatif, asuransi, L/C, sewa-balik, percantikan, SPV | Tombol nonaktif menampilkan alasan blokir; AP berkurang saat dipakai | |
| C5 | Saham minoritas anak usaha | Pilihan porsi 5/10/20% dan diskon 10/20/30%; penolakan menampilkan pesan | |
| C6 | Intel: beli riset dan rapat dewan | Margin error ditampilkan; jawaban dewan tersimpan | |
| C7 | Intel: pasar prediksi (taruhan) | Probabilitas kerumunan, jumlah poin, status posisi terbaca; taruhan mengurangi poin | |

## D. Pasar, Bursa, Peta, Dunia
| # | Langkah | Yang diharapkan | Hasil |
|---|---|---|---|
| D1 | Pasar: pilih instrumen | FanChart proyeksi dan Sparkline sesuai instrumen terpilih | |
| D2 | Bursa: daftar saham | Indeks dan sparkline terbaca; nada warna naik/turun mudah dibedakan | |
| D3 | Peta: ketuk sebuah negara | Panel negara menampilkan bidak (pabrik, gudang, toko, kapal) dan aset; bidak tidak keluar dari peta | |
| D4 | Peta: pinch/gulir pada layar kecil | Peta tetap bisa dipakai; label tidak bertumpuk berat | |
| D5 | Dunia dan Kronik: tren geopolitik, sanksi, pemilu | Teks terbaca; daftar tidak kosong setelah beberapa bulan | |
| D6 | Jurnal keputusan | Daftar keputusan terdahulu urut; filter bekerja | |

## E. Aksesibilitas dan keterbacaan
| # | Langkah | Yang diharapkan | Hasil |
|---|---|---|---|
| E1 | Font sistem 130% | Tidak ada teks terpotong pada tombol atau dialog penting | |
| E2 | Tema gelap | Kontras cukup di semua kartu dan grafik; grafik garis terlihat | |
| E3 | TalkBack | Tombol utama punya label bermakna; grafik punya deskripsi atau diabaikan dengan jelas (grafik saham, riwayat, FanChart, Sparkline, dan peta punya deskripsi teks di kode; yang belum diuji adalah apakah isinya berguna saat dibacakan TalkBack) | |
| E4 | Target sentuh | Tombol aksi minimal 48 dp dan tidak terlalu rapat | |
| E5 | Bahasa | Seluruh teks UI berbahasa Indonesia, tanpa tulisan Inggris tersisa selain nama istilah (AP, L/C, SPV) | |

## F. Kinerja
| # | Langkah | Yang diharapkan | Hasil |
|---|---|---|---|
| F1 | Akhiri giliran 60 kali berturut-turut | Tidak ada lonjakan penundaan berarti; tidak ada ANR | |
| F2 | Buka Operasi dengan riwayat 60 bulan | Tab terbuka tanpa jeda panjang | |
| F3 | Pantau memori beberapa puluh giliran | Tidak naik terus tanpa henti | |

## G. Peran lain dan tantangan
| # | Langkah | Yang diharapkan | Hasil |
|---|---|---|---|
| G1 | Menu → "Peran lain dan tantangan" | Layar terbuka; tombol "Kembali" kembali ke menu | |
| G2 | Pilih Gubernur, seed 123, 24 bln, Mulai | Ringkasan bulan 0 dari 24 dan suku bunga awal tampil | |
| G3 | Tekan -0,25 dua kali lalu "Tetapkan dan lanjut 1 bulan" | Suku bunga naik/turun sesuai angka di layar; bulan ke-1 | |
| G4 | Tekan +0,25 tujuh kali (+1,75) lalu lanjut | Pesan penolakan merah (batas 1,5 poin per bulan); bulan tidak maju | |
| G5 | "Tahan suku bunga dan lanjut 6 bulan" berulang sampai selesai | Layar "Selesai" dengan skor; tidak macet | |
| G6 | Salin kode tantangan, kembali, tempel di "Terima tantangan", Mainkan | Dunia identik (inflasi bulan pertama sama dengan sesi sebelumnya) | |
| G7 | Selesai → isi nama → "Salin hasil saya" → "Papan peringkat" → tempel hasil → Verifikasi | "Diterima: skor terverifikasi N" dan baris peringkat muncul | |
| G8 | Ubah satu angka skor pada teks hasil sebelum menempel | Ditolak dengan alasan skor tidak cocok | |
| G9 | Pilih Manajer Dana; Beli 1 lot di satu saham berulang | Setelah melewati 35% dana, pembelian ditolak dengan pesan; kas dan lot di ringkasan berubah | |
| G10 | Putar layar / font 130% / tema gelap di layar peran | Tidak ada teks terpotong; sesi peran dan papan peringkat tetap ada setelah diputar (disimpan dengan rememberSaveable); bila hilang, itu galat | |

## H. Syarat era dini
| # | Langkah | Yang diharapkan | Hasil |
|---|---|---|---|
| H1 | Permainan baru → tab yang menampilkan fitur terkunci (anak usaha/M&A) | Pesan 'Terkunci sampai Era 2' diikuti syarat kinerja dengan penanda [ok]/[belum] dan angka kemajuan; tidak terpotong | |
| H2 | Mainkan sampai Januari 2038 dengan kinerja biasa | Fitur Era 2 terbuka pada tanggal kalender (jaring pengaman) | |
| H3 | Mainkan dengan ekspansi agresif sampai syarat terpenuhi sebelum 2038 | Muncul laporan 'Fitur Era 2 ... terbuka lebih awal' dan entri kronik; aksi anak usaha dapat dipakai | |

## Risiko yang sudah diketahui (belum diperbaiki)
- Peta belum mendukung pinch-zoom. Label negara tidak lagi tertutup bidak pemain (diperbaiki dari tangkapan layar emulator), tetapi tumpang tindih antar-label pada layar sangat kecil tidak diperiksa otomatis.
- Tata letak lanskap/tablet hanya diuji tangkapan teks di Robolectric (`TabletUiTest`), tidak dilihat di perangkat.
- Tidak ada tutorial interaktif, hanya dialog "Cara bermain" berisi teks.
- Tangkapan layar emulator hanya mencakup satu ukuran layar (Pixel 6) dan terutama tema terang; tema gelap dan font besar belum ditinjau.
- Pemuatan simpanan panjang masih membutuhkan beberapa detik sampai belasan detik di emulator (lihat `docs/RELEASE_NOTES.md`); layar tidak lagi membeku, tetapi pemain menunggu.
- Uji otomatis tidak menilai kualitas visual atau rasa permainan; hanya manusia yang bisa mengisi kolom "Hasil".
