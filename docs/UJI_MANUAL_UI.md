# Daftar uji manual UI KAPITAL (C2)

**Status jujur:** lingkungan pengembangan tidak punya Android SDK atau emulator. Tampilan Compose **hanya terbukti lolos kompilasi di CI** ("Build APK"); belum ada satu pun layar yang dilihat di perangkat atau emulator, dan belum ada tes tangkapan layar (Paparazzi/Compose test). Seluruh isi dokumen ini adalah daftar yang harus dijalankan manusia pada perangkat sungguhan. Jangan menganggap UI terverifikasi sebelum kolom "Hasil" diisi.

Cara mengisi: pasang APK dari artefak CI cabang `claude/simulasi-era1`, jalankan tiap baris, isi Hasil dengan LULUS / GAGAL + catatan (perangkat, versi Android, ukuran layar).

Perangkat minimum yang disarankan: ponsel 5,5" (360 dp lebar), ponsel besar (412 dp), tablet 10" atau mode lanskap, tema terang dan gelap, ukuran font sistem 100% dan 130%.

## A. Menu dan alur awal
| # | Langkah | Yang diharapkan | Hasil |
|---|---|---|---|
| A1 | Buka aplikasi pertama kali | Menu muncul tanpa crash; tombol Permainan baru, Lanjutkan (bila ada simpanan), Cara bermain, Tentang | |
| A2 | Permainan baru → pilih tiap kesulitan | Dialog kesulitan; permainan mulai di Markas; seed berbeda tiap permainan baru | |
| A3 | Cara bermain | Dialog bisa digulir; 9 butir termasuk kartu keputusan dan Intel; tombol Mengerti menutup | |
| A4 | Tutup paksa aplikasi lalu buka lagi → Lanjutkan | Permainan kembali ke giliran yang sama dengan kas dan ekuitas sama | |
| A5 | Menu → Muat autosave | Daftar slot (maks. 5) berlabel giliran; memuat satu slot memulihkan keadaan; Ironman tidak menampilkan slot | |
| A6 | Putar perangkat (potret ↔ lanskap) di tiap layar | Tidak ada crash; keadaan permainan dan input tidak hilang; lanskap memakai dua kolom di Markas | |

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
| E3 | TalkBack | Tombol utama punya label bermakna; grafik punya deskripsi atau diabaikan dengan jelas (**kemungkinan GAGAL**: grafik Canvas belum diberi deskripsi aksesibilitas) | |
| E4 | Target sentuh | Tombol aksi minimal 48 dp dan tidak terlalu rapat | |
| E5 | Bahasa | Seluruh teks UI berbahasa Indonesia, tanpa tulisan Inggris tersisa selain nama istilah (AP, L/C, SPV) | |

## F. Kinerja
| # | Langkah | Yang diharapkan | Hasil |
|---|---|---|---|
| F1 | Akhiri giliran 60 kali berturut-turut | Tidak ada lonjakan penundaan berarti; tidak ada ANR | |
| F2 | Buka Operasi dengan riwayat 60 bulan | Tab terbuka tanpa jeda panjang | |
| F3 | Pantau memori beberapa puluh giliran | Tidak naik terus tanpa henti | |

## Risiko yang sudah diketahui (belum diperbaiki)
- Grafik Canvas (saham, riwayat, FanChart, Sparkline) tidak punya deskripsi aksesibilitas.
- Peta belum mendukung pinch-zoom (belum diverifikasi); label negara bisa bertumpuk pada layar kecil.
- Tata letak lanskap/tablet belum diuji; hanya dikodekan.
- Tidak ada tutorial interaktif, hanya dialog "Cara bermain" berisi teks.
- Tidak ada tes Compose/Paparazzi; satu-satunya jaring pengaman otomatis adalah kompilasi CI dan tes mesin (JVM).
