package com.example.engine

/**
 * Fungsi transendental deterministik lintas perangkat.
 *
 * `Math.exp/log/pow/cos` boleh berbeda hingga 1 ulp antar CPU dan runtime (HotSpot, ART). Mesin ini dipakai untuk
 * memutar ulang log aksi (simpanan, "Bagaimana Jika", tantangan asinkron) pada perangkat lain, jadi selisih sekecil itu
 * dapat berlipat dalam simulasi yang kacau. `StrictMath` mengikuti fdlibm sehingga hasilnya identik di semua platform.
 *
 * Berkas lain di paket ini TIDAK mengimpor `kotlin.math.exp/ln/pow/tanh/cos`; fungsi di bawah dipakai lewat paket yang sama.
 * `DeterminismGuardTest` menjaga agar panggilan langsung tidak kembali.
 */
internal fun exp(x: Double): Double = StrictMath.exp(x)
internal fun ln(x: Double): Double = StrictMath.log(x)
internal fun cos(x: Double): Double = StrictMath.cos(x)
internal fun tanh(x: Double): Double = StrictMath.tanh(x)
internal fun Double.pow(y: Double): Double = StrictMath.pow(this, y)
internal fun Double.pow(n: Int): Double = StrictMath.pow(this, n.toDouble())
