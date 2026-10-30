package com.example.engine

/**
 * Versi mesin. Simpanan, tantangan, dan hasil yang dibagikan adalah seed + daftar tindakan yang diputar ulang, sehingga setiap
 * perubahan perilaku mesin (rumus, probabilitas, urutan RNG, efek kartu) mengubah hasil log lama. Naikkan angka ini pada setiap
 * perubahan yang memengaruhi hasil putar ulang, lalu bangkitkan ulang fixture dengan tools/gen-replay-fixture.sh
 * (ReplayFixtureTest gagal bila lupa). Baris "VER:n" pada simpanan, cap pada kode tantangan, dan cap pada hasil memakai angka ini.
 *
 * Riwayat: 1 = simpanan tanpa cap (sampai build-77); 2 = mogok meninggalkan efek pasca-mogok dan efek aktif di laporan.
 */
const val ENGINE_VERSION = 2
