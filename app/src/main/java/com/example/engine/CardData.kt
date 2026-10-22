package com.example.engine

/** Kumpulan tabel kartu generator; tiap gelombang ditambahkan di sini. */
internal object CardData {
    fun all(): List<String> = listOf(CardData1.DATA, CardData2.DATA, CardData3.DATA, CardData4.DATA, CardData5.DATA)
}
