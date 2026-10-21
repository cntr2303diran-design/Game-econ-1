package com.example.data

import com.example.model.*

/**
 * Data dunia yang (untuk sekarang) statis: negara, instrumen pasar, ketegangan, dan kartu event serikat.
 * Bagian dinamis (Sundara, kurs) ditimpa oleh GameRepository dari mesin simulasi.
 */
object StaticWorldData {
    /** Tanggal acuan data statis; diperbarui tiap kali tampilan disusun agar mengikuti tanggal permainan. */
    private var dNow = "Maret 2033"
    private var dPrev1 = "Februari 2033"
    private var dPrev2 = "Januari 2033"
    fun setDate(now: String, prev1: String, prev2: String) { dNow = now; dPrev1 = prev1; dPrev2 = prev2 }

    fun mapPieces(): List<MapPiece> {
        return listOf(
            MapPiece("pc_pabrik_1", MapTokenType.PABRIK, "Pabrik Kalimas", "sundara", 0.56f, 0.72f, "Utilisasi 88% · Listrik rawan padam"),
            MapPiece("pc_gudang_1", MapTokenType.GUDANG, "Gudang Tanjung", "sundara", 0.62f, 0.66f, "Stok kapas 53 hari (DIO)"),
            MapPiece("pc_toko_1", MapTokenType.TOKO, "Ritel Kota Raya", "sundara", 0.51f, 0.77f, "Piutang ritel DSO 62 hari"),
            MapPiece("pc_kapal_1", MapTokenType.KAPAL, "KM Nusantara I", "kestrel", 0.69f, 0.48f, "Melintasi Selat Kestrel (+2 hari tunda)"),
            MapPiece("pc_gudang_2", MapTokenType.GUDANG, "Depo Kapas Tarsis", "tarsis", 0.34f, 0.74f, "Kontrak pasokan kapas Tier-1"),
            MapPiece("pc_kapal_2", MapTokenType.KAPAL, "MT Boreal Star", "norvaldia", 0.36f, 0.28f, "Jalur energi Selat Norvaldia–Halvenia")
        )
    }

    fun countries(): List<Country> {
        return listOf(
            Country(
                id = "aurelia",
                name = "Aurelia",
                currencyCode = "A$",
                archetype = "Adidaya, mata uang cadangan dunia",
                opportunity = "Pasar modal terdalam, bunga utang murah (4,25%)",
                mainRisk = "Kebijakan bunganya mengguncang dunia; sanksi ekstrateritorial",
                centralBankStyle = "Hawkish (Kredibilitas tinggi)",
                inflationRange = "2,4–2,8%",
                policyRate = "4,25%",
                gdpGrowth = "2,3%",
                tensionScore = 58,
                electionCountdown = "19 bulan lagi",
                electricityReliability = "97% (Sangat stabil)",
                climateRiskLabel = "Rendah (18/100)",
                supplyChainRole = "Pasar ekspor garmen premium & sumber kredit valas A$",
                polygonPoints = listOf(0.05f to 0.10f, 0.27f to 0.08f, 0.29f to 0.34f, 0.07f to 0.36f),
                pieces = emptyList(),
                originInfo = DataOriginInfo(
                    title = "Data Makro Aurelia",
                    displayedValue = "Inflasi 2,4–2,8% · Bunga 4,25%",
                    sourceName = "Bank Sentral Federal Aurelia",
                    dataDate = "${dPrev1} (Tepat waktu)",
                    calculationMethod = "Aturan Taylor Hawkish (α = 1,65 pada deviasi inflasi).",
                    uncertaintyAndBiasNote = "Transparansi statistik sangat tinggi; risiko utama bagi pemain adalah penguatan A$ yang memicu currency mismatch.",
                    confidence = ConfidenceTier.HIGH
                )
            ),
            Country(
                id = "norvaldia",
                name = "Norvaldia",
                currencyCode = "NRV",
                archetype = "Eksportir minyak dan gas",
                opportunity = "Energi murah & dana kekayaan negara besar",
                mainRisk = "Ekonomi ikut harga komoditas, rawan blokade Selat Energi",
                centralBankStyle = "Politis",
                inflationRange = "5,0–6,5%",
                policyRate = "7,00%",
                gdpGrowth = "3,1%",
                tensionScore = 66,
                electionCountdown = "27 bulan lagi",
                electricityReliability = "88% (Stabil)",
                climateRiskLabel = "Rendah (22/100)",
                supplyChainRole = "Pemasok minyak mentah & gas (Penentu biaya listrik & pelayaran)",
                polygonPoints = listOf(0.32f to 0.07f, 0.56f to 0.09f, 0.53f to 0.31f, 0.31f to 0.29f),
                pieces = mapPieces().filter { it.countryId == "norvaldia" },
                originInfo = DataOriginInfo(
                    title = "Data Makro Norvaldia",
                    displayedValue = "Inflasi 5,0–6,5% · Ketegangan 66/100",
                    sourceName = "Kementerian Energi Norvaldia",
                    dataDate = "${dPrev2}",
                    calculationMethod = "Neraca perdagangan migas & indeks risiko geopolitik selat.",
                    uncertaintyAndBiasNote = "Data cadangan devisa sering ditutupi saat ketegangan militer meningkat.",
                    confidence = ConfidenceTier.MEDIUM
                )
            ),
            Country(
                id = "kestrel",
                name = "Kestrel",
                currencyCode = "KST",
                archetype = "Raksasa manufaktur (80% rantai chip & logam)",
                opportunity = "Pabrik komponen, baja, dan buruh terampil murah",
                mainRisk = "Perang tarif & pembatasan ekspor dengan Aurelia",
                centralBankStyle = "Dovish terkendali",
                inflationRange = "1,8–2,4%",
                policyRate = "3,10%",
                gdpGrowth = "4,9%",
                tensionScore = 64,
                electionCountdown = "Siklus partai 45 bulan",
                electricityReliability = "94% (Stabil)",
                climateRiskLabel = "Sedang (34/100)",
                supplyChainRole = "Pemasok Tier-2 suku cadang mesin tenun, baja, & semikonduktor",
                polygonPoints = listOf(0.61f to 0.09f, 0.93f to 0.11f, 0.90f to 0.42f, 0.60f to 0.38f),
                pieces = mapPieces().filter { it.countryId == "kestrel" },
                originInfo = DataOriginInfo(
                    title = "Data Makro & Rantai Pasok Kestrel",
                    displayedValue = "PDB 4,9% · Kuasai 80% rantai komponen",
                    sourceName = "Biro Industri Kestrel & Lensa Independen",
                    dataDate = "${dPrev2}",
                    calculationMethod = "Matriks Input-Output Leontief x = (I - A)^-1 d.",
                    uncertaintyAndBiasNote = "Angka pertumbuhan resmi sangat mulus; risiko sebenarnya ada pada penahanan kiriman (opportunistic hold-up) oleh pemasok saat kelangkaan.",
                    confidence = ConfidenceTier.MEDIUM
                )
            ),
            Country(
                id = "halvenia",
                name = "Halvenia",
                currencyCode = "HLV",
                archetype = "Uni ekonomi maju",
                opportunity = "Konsumen kaya, kepastian hukum & kontrak tertinggi",
                mainRisk = "Populasi menua, standar ESG & pajak karbon ketat",
                centralBankStyle = "Hawkish",
                inflationRange = "2,0–2,3%",
                policyRate = "3,50%",
                gdpGrowth = "1,2%",
                tensionScore = 32,
                electionCountdown = "23 bulan lagi",
                electricityReliability = "98% (Hijau & stabil)",
                climateRiskLabel = "Rendah (16/100)",
                supplyChainRole = "Pasar ekspor garmen bersertifikat ESG & mesin ramah lingkungan",
                polygonPoints = listOf(0.06f to 0.42f, 0.28f to 0.40f, 0.27f to 0.64f, 0.05f to 0.62f),
                pieces = emptyList(),
                originInfo = DataOriginInfo(
                    title = "Data Makro & ESG Halvenia",
                    displayedValue = "Inflasi 2,0–2,3% · Pajak Karbon Tinggi",
                    sourceName = "Komisi Statistik Halvenia",
                    dataDate = "${dPrev1}",
                    calculationMethod = "Audit karbon lintas batas (CBAM) & indeks demografi lansia.",
                    uncertaintyAndBiasNote = "Data sangat akurat. Jika pemasok Tier-2 Anda memakai buruh anak atau melanggar emisi, Halvenia memboikot merek Anda seketika.",
                    confidence = ConfidenceTier.HIGH
                )
            ),
            Country(
                id = "meridia",
                name = "Meridia",
                currencyCode = "MRD",
                archetype = "Pusat keuangan kecil, surga pajak",
                opportunity = "Pajak badan rendah (8,5%), akses SPV & modal mudah",
                mainRisk = "Gelembung properti, shadow banking, audit transfer pricing",
                centralBankStyle = "Dovish",
                inflationRange = "2,5–3,1%",
                policyRate = "3,25%",
                gdpGrowth = "3,6%",
                tensionScore = 24,
                electionCountdown = "33 bulan lagi",
                electricityReliability = "97% (Stabil)",
                climateRiskLabel = "Sedang (42/100 - Risiko permukaan laut)",
                supplyChainRole = "Pusat pendanaan non-bank, asuransi kargo, & entitas SPV",
                polygonPoints = listOf(0.33f to 0.37f, 0.52f to 0.38f, 0.50f to 0.58f, 0.32f to 0.57f),
                pieces = emptyList(),
                originInfo = DataOriginInfo(
                    title = "Data Keuangan Meridia",
                    displayedValue = "Pajak 8,5% · Rasio Properti/Pendapatan 15,6x",
                    sourceName = "Otoritas Moneter Meridia",
                    dataDate = "${dPrev1}",
                    calculationMethod = "Survei perbankan lepas pantai & valuasi properti komersial.",
                    uncertaintyAndBiasNote = "Banyak perusahaan AI menyembunyikan utang macet lewat SPV cangkang di Meridia.",
                    confidence = ConfidenceTier.MEDIUM
                )
            ),
            Country(
                id = "sundara",
                name = "Sundara",
                currencyCode = "RS",
                archetype = "Negara berkembang, asal pemain (PT Benang Sundara)",
                opportunity = "Pasar domestik tumbuh cepat, bonus urbanisasi",
                mainRisk = "Inflasi tinggi, korupsi, listrik padam bergilir, kurs RS mudah anjlok",
                centralBankStyle = "Politis (Rentan tekanan jelang pemilu)",
                inflationRange = "7–9%",
                policyRate = "8,25%",
                gdpGrowth = "5,1–5,6%",
                tensionScore = 38,
                electionCountdown = "9 bulan lagi (Tahun Politik!)",
                electricityReliability = "68% (Pemadaman bergilir di Kalimas)",
                climateRiskLabel = "Tinggi (54/100 - Banjir pelabuhan)",
                supplyChainRole = "Markas utama pabrik tekstil, gudang, dan jaringan ritel pemain",
                polygonPoints = listOf(0.47f to 0.63f, 0.72f to 0.60f, 0.74f to 0.90f, 0.45f to 0.91f),
                pieces = mapPieces().filter { it.countryId == "sundara" },
                originInfo = DataOriginInfo(
                    title = "Data Makro Sundara",
                    displayedValue = "Inflasi 7–9% · Bunga 8,25% · Pemilu 9 bln lagi",
                    sourceName = "Biro Statistik & Bank Sentral Sundara",
                    dataDate = "Data ${dPrev2} (Terlambat 2 bulan)",
                    calculationMethod = "Aturan Taylor dengan bias politik menjelang pemilu presiden.",
                    uncertaintyAndBiasNote = "PERINGATAN SIKLUS POLITIK: Presiden menekan bank sentral menahan bunga dan mengobral subsidi jelang pemilu; berisiko memicu lonjakan inflasi 12 bulan setelah pemilu.",
                    confidence = ConfidenceTier.LOW
                )
            ),
            Country(
                id = "tarsis",
                name = "Tarsis",
                currencyCode = "TRS",
                archetype = "Agraris, populasi raksasa",
                opportunity = "Bonus demografi, kapas & pangan murah, pasar massal besar",
                mainRisk = "El Niño, kelangkaan air, kerusuhan karena inflasi pangan",
                centralBankStyle = "Dovish",
                inflationRange = "6,8–8,4%",
                policyRate = "6,75%",
                gdpGrowth = "5,9%",
                tensionScore = 44,
                electionCountdown = "14 bulan lagi",
                electricityReliability = "72% (Sedang)",
                climateRiskLabel = "Sangat Tinggi (72/100 - Kekeringan El Niño)",
                supplyChainRole = "Pemasok utama kapas mentah Tier-1 & pangan regional",
                polygonPoints = listOf(0.10f to 0.68f, 0.41f to 0.65f, 0.40f to 0.92f, 0.08f to 0.90f),
                pieces = mapPieces().filter { it.countryId == "tarsis" },
                originInfo = DataOriginInfo(
                    title = "Data Komoditas & Demografi Tarsis",
                    displayedValue = "Inflasi 6,8–8,4% · Risiko Iklim 72/100",
                    sourceName = "Kementerian Pertanian Tarsis",
                    dataDate = "${dPrev2}",
                    calculationMethod = "Indeks cadangan air irigasi lembah kapas & sensus angkatan kerja muda.",
                    uncertaintyAndBiasNote = "Gagal panen di Tarsis memicu rantai: harga beras naik → tuntutan upah Sundara naik → bunga bank sentral naik.",
                    confidence = ConfidenceTier.LOW
                )
            ),
            Country(
                id = "velmora",
                name = "Velmora",
                currencyCode = "VLM",
                archetype = "Rezim otoriter kaya mineral & logam tanah jarang",
                opportunity = "Bahan baku teknologi tinggi, pigmen, & katalis murah",
                mainRisk = "Kudeta, sanksi berlapis Aurelia, nasionalisasi aset mendadak",
                centralBankStyle = "Tidak independen",
                inflationRange = "9–12%",
                policyRate = "10,00%",
                gdpGrowth = "2,5%",
                tensionScore = 76,
                electionCountdown = "Tidak ada pemilu bebas",
                electricityReliability = "74% (Prioritas tambang negara)",
                climateRiskLabel = "Sedang (38/100)",
                supplyChainRole = "Pemasok Tier-3 mineral tanah jarang & bahan kimia pewarna",
                polygonPoints = listOf(0.76f to 0.50f, 0.95f to 0.48f, 0.94f to 0.88f, 0.77f to 0.86f),
                pieces = emptyList(),
                originInfo = DataOriginInfo(
                    title = "Data Risiko & Sanksi Velmora",
                    displayedValue = "Ketegangan 76/100 · Sanksi Ekonomi Aktif",
                    sourceName = "Intelijen Risiko Politik & Lensa Independen",
                    dataDate = "${dPrev1}",
                    calculationMethod = "Skor eskalasi sanksi Aurelia–Velmora.",
                    uncertaintyAndBiasNote = "Angka ekonomi resmi Velmora dimanipulasi rezim. Bahaya utama bagi pemain adalah sanksi menular lewat pemasok Tier-2 di Kestrel.",
                    confidence = ConfidenceTier.LOW
                )
            )
        )
    }

    fun marketInstruments(): List<MarketInstrument> {
        return listOf(
            MarketInstrument(
                id = "mkt_kapas",
                category = "Komoditas",
                name = "Kapas Mentah (Tarsis Grade A)",
                ticker = "CTN-SPOT",
                priceText = "1.850 A$/ton",
                changeText = "+4,2% bulan ini",
                tone = SemanticTone.WARNING_YELLOW,
                sparklineHistory = listOf(1680f, 1710f, 1695f, 1740f, 1790f, 1850f),
                fanProjectionMedian = listOf(1880f, 1910f, 1930f, 1950f),
                fanProjectionSpread = listOf(60f, 120f, 190f, 260f),
                originInfo = DataOriginInfo(
                    title = "Harga Spot & Futures Kapas",
                    displayedValue = "1.850 A$/ton (+4,2%)",
                    sourceName = "Bursa Komoditas Tarsis–Sundara",
                    dataDate = "1 ${dNow}",
                    calculationMethod = "Terbentuk dari buku pesanan pelaku pasar, cadangan air irigasi Tarsis, dan stok gudang global.",
                    uncertaintyAndBiasNote = "Gejala El Niño di Tarsis melebarkan pita proyeksi FanChart ke atas dalam 6 bulan ke depan.",
                    confidence = ConfidenceTier.HIGH
                )
            ),
            MarketInstrument(
                id = "mkt_minyak",
                category = "Komoditas",
                name = "Minyak Mentah Boreal (Norvaldia)",
                ticker = "OIL-BRN",
                priceText = "84,5 A$/bbl",
                changeText = "+6,8% bulan ini",
                tone = SemanticTone.DANGER_RED,
                sparklineHistory = listOf(74f, 76f, 75.5f, 78f, 80.2f, 84.5f),
                fanProjectionMedian = listOf(86f, 88f, 89f, 90f),
                fanProjectionSpread = listOf(5f, 11f, 18f, 25f),
                originInfo = DataOriginInfo(
                    title = "Harga Minyak Mentah Boreal",
                    displayedValue = "84,5 A$/bbl (+6,8%)",
                    sourceName = "Bursa Energi Norvaldia–Halvenia",
                    dataDate = "1 ${dNow}",
                    calculationMethod = "Harga kesetimbangan order book energi + premi risiko Selat Norvaldia.",
                    uncertaintyAndBiasNote = "Rantai transmisi: Minyak naik → inflasi Sundara naik 3 bln lagi → Bank Sentral menaikkan bunga → cicilan mengambang Bank B naik.",
                    confidence = ConfidenceTier.HIGH
                )
            ),
            MarketInstrument(
                id = "mkt_baja",
                category = "Komoditas",
                name = "Baja Industri & Komponen Mesin (Kestrel)",
                ticker = "STL-KST",
                priceText = "760 A$/ton",
                changeText = "+5,5% bulan ini",
                tone = SemanticTone.WARNING_YELLOW,
                sparklineHistory = listOf(690f, 700f, 715f, 710f, 730f, 760f),
                fanProjectionMedian = listOf(780f, 800f, 815f, 825f),
                fanProjectionSpread = listOf(30f, 65f, 105f, 145f),
                originInfo = DataOriginInfo(
                    title = "Harga Baja & Komponen Kestrel",
                    displayedValue = "760 A$/ton (+5,5%)",
                    sourceName = "Indeks Manufaktur Kestrel",
                    dataDate = "${dPrev1}",
                    calculationMethod = "Matriks Leontief x = (I - A)^-1 d; kenaikan harga baja langsung menaikkan anggaran konstruksi Pabrik Pemintalan.",
                    uncertaintyAndBiasNote = "Pembatasan ekspor logam langka Kestrel memicu kelangkaan baja presisi.",
                    confidence = ConfidenceTier.MEDIUM
                )
            ),
            MarketInstrument(
                id = "mkt_kurs",
                category = "Kurs Valas",
                name = "Kurs Rial Sundara / Dolar Aurelia",
                ticker = "RS / A$",
                priceText = "15.450 RS",
                changeText = "Melemah -2,3%",
                tone = SemanticTone.DANGER_RED,
                sparklineHistory = listOf(14800f, 14900f, 15050f, 15100f, 15220f, 15450f),
                fanProjectionMedian = listOf(15600f, 15800f, 16050f, 16300f),
                fanProjectionSpread = listOf(350f, 750f, 1200f, 1750f),
                originInfo = DataOriginInfo(
                    title = "Nilai Tukar RS terhadap A$",
                    displayedValue = "15.450 RS per 1 A$ (Melemah 2,3%)",
                    sourceName = "Pasar Valas Antarbank Sundara",
                    dataDate = "1 ${dNow}",
                    calculationMethod = "Dipengaruhi selisih suku bunga riil, defisit neraca perdagangan, cadangan devisa, dan risiko pemilu Sundara.",
                    uncertaintyAndBiasNote = "Waspadai currency mismatch jika mengambil pinjaman dalam mata uang A$ sementara pendapatan dalam RS.",
                    confidence = ConfidenceTier.HIGH
                )
            ),
            MarketInstrument(
                id = "mkt_saham_mahkota",
                category = "Saham & Korporasi Pesaing",
                name = "Grup Mahkota Tekstil Tbk (Pesaing Kroni)",
                ticker = "MKTX",
                priceText = "2.450 RS",
                changeText = "-1,8% · M-Score Mencurigakan",
                tone = SemanticTone.WARNING_YELLOW,
                sparklineHistory = listOf(2300f, 2420f, 2550f, 2510f, 2490f, 2450f),
                fanProjectionMedian = listOf(2350f, 2200f, 2050f, 1900f),
                fanProjectionSpread = listOf(180f, 360f, 540f, 720f),
                forensicMScoreNote = "Beneish M-Score: -1,15 s.d. -0,85 (Ambang manipulasi > -1,78). Laba bersih dilaporkan naik, tetapi arus kas operasi negatif (indikasi Channel Stuffing)!",
                originInfo = DataOriginInfo(
                    title = "Analisis Forensik Grup Mahkota Tekstil (MKTX)",
                    displayedValue = "Harga 2.450 RS · Beneish M-Score [-1,15 .. -0,85]",
                    sourceName = "Laporan Publik Bursa Sundara & Terminal Forensik",
                    dataDate = "Laporan Kuartal IV 2032",
                    calculationMethod = "8 Rasio Beneish M-Score (DSRI piutang melonjak, TATA akrual total tinggi, Kualitas Laba CFO/NI = -0,28x).",
                    uncertaintyAndBiasNote = "Peluang Activist Short-Selling: Anda dapat menyewa penyelidik swasta untuk memeriksa gudang mereka dan menyerahkan bukti ke Lensa Independen.",
                    confidence = ConfidenceTier.MEDIUM
                )
            ),
            MarketInstrument(
                id = "mkt_saham_kestrel_loom",
                category = "Saham & Korporasi Pesaing",
                name = "Kestrel Loom & Fiber Corp (Perampas Harga)",
                ticker = "KLFC",
                priceText = "38,4 A$",
                changeText = "+3,1% · Likuiditas Kuat",
                tone = SemanticTone.GOOD_GREEN,
                sparklineHistory = listOf(34.5f, 35.2f, 36.0f, 36.8f, 37.2f, 38.4f),
                fanProjectionMedian = listOf(39.0f, 39.8f, 40.5f, 41.2f),
                fanProjectionSpread = listOf(1.5f, 3.0f, 4.8f, 6.5f),
                forensicMScoreNote = "Beneish M-Score: -2,55 s.d. -2,15 (Aman < -1,78). Kualitas laba sehat (CFO/NI = 1,14x).",
                originInfo = DataOriginInfo(
                    title = "Analisis Kestrel Loom & Fiber (KLFC)",
                    displayedValue = "38,4 A$ (+3,1%) · M-Score Aman",
                    sourceName = "Bursa Efek Kestrel",
                    dataDate = "${dPrev1}",
                    calculationMethod = "Valuasi buku pesanan 5.000 agen investor institusional.",
                    uncertaintyAndBiasNote = "Arketipe Perampas Harga: sedang mengintai jadwal jatuh tempo utang Bank B Anda untuk memicu perang harga.",
                    confidence = ConfidenceTier.HIGH
                )
            )
        )
    }

    fun pairwiseTensions(): List<PairwiseTension> {
        return listOf(
            PairwiseTension(
                pairLabel = "Aurelia ↔ Kestrel",
                straitOrBorder = "Selat Kestrel (Jalur 80% Chip & Komponen Mesin)",
                score = 64,
                escalationStage = "Sanksi Ekonomi & Perang Tarif (Skor 60–70)",
                businessEffect = "Bea masuk naik, kuota ekspor logam langka diperketat, waktu tempuh kapal +2 minggu.",
                tone = SemanticTone.DANGER_RED
            ),
            PairwiseTension(
                pairLabel = "Norvaldia ↔ Halvenia",
                straitOrBorder = "Selat Norvaldia–Halvenia (Jalur Energi Minyak & Gas)",
                score = 52,
                escalationStage = "Perang Tarif & Retorika Militer (Skor 45–60)",
                businessEffect = "Harga minyak Boreal berfluktuasi tinggi; premi asuransi tanker naik 35%.",
                tone = SemanticTone.WARNING_YELLOW
            ),
            PairwiseTension(
                pairLabel = "Aurelia ↔ Velmora",
                straitOrBorder = "Sanksi Ekstrateritorial Mineral Tanah Jarang",
                score = 76,
                escalationStage = "Blokade Terbatas & Pembekuan Aset (Skor 70–80)",
                businessEffect = "Memakai pemasok Tier-2 yang diam-diam menggunakan mineral Velmora memicu risiko denda dari Aurelia.",
                tone = SemanticTone.DANGER_RED
            ),
            PairwiseTension(
                pairLabel = "Tarsis ↔ Sundara",
                straitOrBorder = "Selat Tarsis–Sundara (Jalur Pangan & Kapas)",
                score = 28,
                escalationStage = "Normal & Terbuka (Skor <30)",
                businessEffect = "Pelayaran kapas lancar, namun sensitif terhadap larangan ekspor beras saat El Niño.",
                tone = SemanticTone.GOOD_GREEN
            )
        )
    }

}
