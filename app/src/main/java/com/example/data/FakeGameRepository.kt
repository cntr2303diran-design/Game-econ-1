package com.example.data

import com.example.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakeGameRepository {

    private val _gameState = MutableStateFlow(buildInitialDummyGameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    fun toggleTheme() {
        _gameState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
    }

    fun setMapLayer(layer: MapLayerFilter) {
        _gameState.update { it.copy(activeMapLayer = layer) }
    }

    fun selectCountryForBottomSheet(country: Country?) {
        _gameState.update { it.copy(selectedCountryForSheet = country) }
    }

    fun inspectDataOrigin(origin: DataOriginInfo?) {
        _gameState.update { it.copy(inspectedDataOrigin = origin) }
    }

    fun openEventDialog(show: Boolean) {
        _gameState.update { it.copy(showEventDialog = show) }
    }

    fun switchRole(roleName: String) {
        _gameState.update { it.copy(activeRole = roleName) }
    }

    fun changeIncentiveScheme(schemeName: String) {
        _gameState.update { state ->
            if (state.apCurrent < 1) return@update state
            state.copy(
                apCurrent = state.apCurrent - 1,
                activeIncentiveScheme = schemeName
            )
        }
    }

    fun crossCheckNewsItem(newsId: String) {
        _gameState.update { state ->
            if (state.apCurrent < 1) return@update state
            val updatedNews = state.morningPaperEdition.map { item ->
                if (item.id == newsId && !item.crossChecked) {
                    val upgradedConfidence = when (item.confidence) {
                        ConfidenceTier.LOW -> ConfidenceTier.MEDIUM
                        ConfidenceTier.MEDIUM -> ConfidenceTier.HIGH
                        ConfidenceTier.HIGH -> ConfidenceTier.HIGH
                    }
                    item.copy(
                        crossChecked = true,
                        confidence = if (item.isRumor) ConfidenceTier.LOW else upgradedConfidence
                    )
                } else item
            }
            state.copy(
                apCurrent = (state.apCurrent - 1).coerceAtLeast(0),
                morningPaperEdition = updatedNews
            )
        }
    }

    fun submitDecisionJournal(
        decisionId: String,
        strategicReason: String,
        numericPrediction: String,
        confidencePct: Int,
        failureScenario: String
    ) {
        _gameState.update { state ->
            val targetDecision = state.pendingDecisions.firstOrNull { it.id == decisionId }
            val apCost = targetDecision?.apCost ?: 2
            val newJournal = CompletedJournalEntry(
                id = "jrn_${state.turn}_${state.completedJournals.size + 1}",
                decisionTitle = targetDecision?.title ?: "Keputusan Strategis Giliran ${state.turn}",
                turnSubmitted = state.turn,
                evaluateAtTurn = state.turn + 6,
                strategicReason = strategicReason,
                numericPrediction = numericPrediction,
                confidencePct = confidencePct,
                failureScenario = failureScenario
            )
            val updatedDecisions = state.pendingDecisions.map {
                if (it.id == decisionId) it.copy(isCompleted = true, badgeText = "Dieksekusi", tone = SemanticTone.GOOD_GREEN)
                else it
            }
            state.copy(
                apCurrent = (state.apCurrent - apCost).coerceAtLeast(0),
                pendingDecisions = updatedDecisions,
                completedJournals = listOf(newJournal) + state.completedJournals
            )
        }
    }

    fun resolveEventOption(option: EventOptionItem) {
        _gameState.update { state ->
            val updatedDecisions = state.pendingDecisions.map {
                if (it.id == "dec_serikat") {
                    it.copy(
                        isCompleted = true,
                        badgeText = "Selesai (${option.title})",
                        tone = SemanticTone.GOOD_GREEN
                    )
                } else it
            }
            state.copy(
                showEventDialog = false,
                apCurrent = (state.apCurrent - option.apCost).coerceAtLeast(0),
                pendingDecisions = updatedDecisions
            )
        }
    }

    fun executeContractAction(contractId: String) {
        _gameState.update { state ->
            val target = state.contracts.firstOrNull { it.id == contractId } ?: return@update state
            if (state.apCurrent < target.apCostToAct) return@update state
            val updatedContracts = state.contracts.map { item ->
                if (item.id == contractId) {
                    item.copy(
                        tone = SemanticTone.GOOD_GREEN,
                        actionLabel = "Telah Dieksekusi",
                        apCostToAct = 0
                    )
                } else item
            }
            state.copy(
                apCurrent = (state.apCurrent - target.apCostToAct).coerceAtLeast(0),
                contracts = updatedContracts
            )
        }
    }

    fun advanceTurn() {
        _gameState.update { state ->
            val nextTurn = state.turn + 1
            val months = listOf(
                "Januari", "Februari", "Maret", "April", "Mei", "Juni",
                "Juli", "Agustus", "September", "Oktober", "November", "Desember"
            )
            val totalMonthsFromStart = (nextTurn - 1)
            val year = 2030 + (totalMonthsFromStart / 12)
            val monthName = months[totalMonthsFromStart % 12]

            val newCashVal = if (state.pendingDecisions.any { it.isCompleted }) "6.450 jt RS" else "5.100 jt RS"
            val newCashDelta = if (state.pendingDecisions.any { it.isCompleted }) "+250/bulan" else "-1.100/bulan"
            val newCashTone = if (state.pendingDecisions.any { it.isCompleted }) SemanticTone.GOOD_GREEN else SemanticTone.DANGER_RED
            val newRunway = if (state.pendingDecisions.any { it.isCompleted }) 7 else (state.runwayMonths - 1).coerceAtLeast(1)

            val updatedIndicators = state.markasIndicators.map { ind ->
                if (ind.id == "ind_kas") {
                    ind.copy(
                        valueText = newCashVal,
                        deltaOrSubText = newCashDelta,
                        tone = newCashTone,
                        dataDate = "Tutup buku $monthName $year"
                    )
                } else ind
            }

            val newChronicle = ChronicleEntry(
                turn = state.turn,
                dateLabel = state.dateText,
                eraLabel = state.eraName,
                title = "Tutup Buku ${state.dateText} (Giliran ${state.turn})",
                description = "Keputusan dieksekusi: ${state.pendingDecisions.count { it.isCompleted }} tindakan. Kas akhir menjadi $newCashVal.",
                causalChain = "Pembatasan ekspor Kestrel → harga komponen mesin naik → margin tekstil tertekan → uji covenant Bank B."
            )

            state.copy(
                dateText = "$monthName $year",
                turn = nextTurn,
                apCurrent = state.apMax,
                runwayMonths = newRunway,
                covenantWarningSubtitle = if (nextTurn % 3 == 0) "Uji kuartalan Bank B sedang berlangsung bulan ini" else "Uji kuartalan Bank B dalam ${3 - (nextTurn % 3)} giliran",
                markasIndicators = updatedIndicators,
                worldChronicles = listOf(newChronicle) + state.worldChronicles
            )
        }
    }

    private fun buildInitialDummyGameState(): GameState {
        val covenantOrigin = DataOriginInfo(
            title = "Jarak ke Covenant Bank B",
            displayedValue = "Jarak 6% (ICR 2,65x vs Batas 2,50x)",
            sourceName = "Divisi Treasury & Perjanjian Kredit Bank B",
            dataDate = "Estimasi Maret 2033",
            calculationMethod = "Interest Coverage Ratio (ICR) = EBIT / Beban Bunga. Covenant Bank B mewajibkan ICR >= 2,50x dan Utang Bersih / EBITDA <= 3,50x.",
            uncertaintyAndBiasNote = "Direktur Sales melaporkan EBIT akrual tinggi, namun jika piutang ritel macet di-write-off oleh auditor, ICR riil turun ke 2,38x (Technical Default!).",
            confidence = ConfidenceTier.MEDIUM
        )

        val markasIndicators = listOf(
            Indicator(
                id = "ind_kas",
                label = "Kas",
                valueText = "6.200 jt RS",
                deltaOrSubText = "-1.100/bulan",
                tone = SemanticTone.DANGER_RED,
                dataDate = "Data akhir Februari 2033",
                source = "Rekening Bank B & Kas Internal",
                confidence = ConfidenceTier.HIGH,
                originInfo = DataOriginInfo(
                    title = "Saldo Kas & Setara Kas",
                    displayedValue = "6.200 jt RS (-1.100 jt RS/bulan)",
                    sourceName = "Mutasi Rekening Koran Bank B",
                    dataDate = "28 Februari 2033 (Tepat waktu)",
                    calculationMethod = "Kas Awal + Arus Kas Operasi (CFO) + Arus Kas Investasi + Arus Kas Pendanaan. Saat ini CFO negatif karena penjualan kredit tertahan di piutang ritel.",
                    uncertaintyAndBiasNote = "Angka kas di bank memiliki keyakinan tinggi, kecuali jika terdapat window dressing sesaat sebelum tanggal laporan kuartal.",
                    confidence = ConfidenceTier.HIGH
                )
            ),
            Indicator(
                id = "ind_utilisasi",
                label = "Utilisasi pabrik",
                valueText = "88%",
                deltaOrSubText = "+3 poin",
                tone = SemanticTone.GOOD_GREEN,
                dataDate = "Laporan Februari 2033",
                source = "Laporan Kepala Pabrik",
                confidence = ConfidenceTier.MEDIUM,
                originInfo = DataOriginInfo(
                    title = "Utilisasi Kapasitas Pabrik Kalimas",
                    displayedValue = "88% (+3 poin dari bulan lalu)",
                    sourceName = "Ir. Hendra Gunawan (Kepala Pabrik)",
                    dataDate = "Februari 2033",
                    calculationMethod = "Output Aktual (Meter Kain) / Kapasitas Terpasang Maksimum × 100%.",
                    uncertaintyAndBiasNote = "PERINGATAN AGEN EGOIS: Kepala Pabrik menunda perawatan rutin selama 3 bulan agar utilisasi dan biaya kuartal tampak bagus. Risiko kerusakan mesin naik eksponensial.",
                    confidence = ConfidenceTier.MEDIUM
                )
            ),
            Indicator(
                id = "ind_ccc",
                label = "Siklus kas",
                valueText = "80 hari",
                deltaOrSubText = "dari 125",
                tone = SemanticTone.GOOD_GREEN,
                dataDate = "Kuartal I 2033",
                source = "Akuntansi Manajemen CFO",
                confidence = ConfidenceTier.MEDIUM,
                originInfo = DataOriginInfo(
                    title = "Siklus Konversi Kas (Cash Conversion Cycle)",
                    displayedValue = "80 hari (Membaik dari 125 hari di 2030)",
                    sourceName = "Laporan Modal Kerja CFO Ratna",
                    dataDate = "Akhir Februari 2033",
                    calculationMethod = "CCC = DSO (Hari Piutang: 62) + DIO (Hari Persediaan: 53) - DPO (Hari Utang Dagang: 35) = 80 hari.",
                    uncertaintyAndBiasNote = "Jika Direktur Sales melakukan channel stuffing di akhir kuartal demi mengejar bonus omzet, DSO sebenarnya bisa lebih panjang 10–14 hari.",
                    confidence = ConfidenceTier.MEDIUM
                )
            ),
            Indicator(
                id = "ind_inflasi",
                label = "Inflasi Sundara",
                valueText = "7–9%",
                deltaOrSubText = "data Januari",
                tone = SemanticTone.NEUTRAL_GRAY,
                dataDate = "data Januari (terlambat 2 bln)",
                source = "Biro Statistik Sundara",
                confidence = ConfidenceTier.LOW,
                originInfo = DataOriginInfo(
                    title = "Inflasi Tahunan (CPI) Sundara",
                    displayedValue = "7–9% (Estimasi Rentang)",
                    sourceName = "Rilis Resmi Biro Statistik Sundara vs Survei Pasar",
                    dataDate = "Januari 2033 (Terlambat 2 giliran, rawan revisi)",
                    calculationMethod = "Perubahan Indeks Harga Konsumen (IHK) tahun-ke-tahun ditambah margin kesalahan sampel dan koreksi harga pangan.",
                    uncertaintyAndBiasNote = "Jelang pemilu Sundara, pemerintah politis cenderung mempercantik angka inflasi resmi. Survei independen menunjukkan inflasi pangan sudah mendekati 9,2%.",
                    confidence = ConfidenceTier.LOW
                )
            )
        )

        val pendingDecisions = listOf(
            Decision(
                id = "dec_pemintalan",
                title = "Proposal pabrik pemintalan",
                subtitle = "Integrasi hulu benang di Kalimas (>10% total aset · Wajib Jurnal Keputusan)",
                apCost = 3,
                badgeText = "3 AP",
                tone = SemanticTone.PRIMARY_BLUE,
                assetSharePct = 18,
                defaultContextNote = "Investasi 2.400 jt RS untuk membangun lini pemintalan sendiri agar tidak tergantung impor benang Kestrel."
            ),
            Decision(
                id = "dec_serikat",
                title = "Tuntutan upah serikat",
                subtitle = "Serikat Pekerja Tekstil Kalimas menuntut kenaikan upah 11% mengikuti harga beras",
                apCost = 1,
                badgeText = "tenggat 2 giliran",
                tone = SemanticTone.DANGER_RED,
                assetSharePct = 8,
                defaultContextNote = "Jika diabaikan lewat 2 giliran, peluang mogok massal melonjak tepat saat pengiriman kontrak ritel.",
                opensEventDialog = true
            ),
            Decision(
                id = "dec_termsheet",
                title = "Term sheet dari 3 bank",
                subtitle = "Pilih fasilitas refinancing utang jatuh tempo (Bunga tetap vs mengambang & covenant)",
                apCost = 2,
                badgeText = "2 AP",
                tone = SemanticTone.PRIMARY_BLUE,
                assetSharePct = 22,
                defaultContextNote = "Membandingkan tawaran Bank Niaga Sundara (tetap 10,5%), Aurelia CitiTrust (valas A$ 5,2% rawan kurs), dan Meridia Shadow Fund."
            )
        )

        val morningPaper = listOf(
            NewsItem(
                id = "news_bisnis",
                mediaSlant = MediaSlant.BISNIS,
                headline = "Kestrel dikabarkan batasi ekspor logam langka",
                summary = "Kementerian Perdagangan Kestrel menyiapkan kuota ekspor mineral magnet dan komponen mesin presisi mulai kuartal depan. Biaya suku cadang mesin tekstil dan semikonduktor diperkirakan naik 14–22%.",
                dateLabel = "1 Maret 2033 · Laporan Utama",
                confidence = ConfidenceTier.MEDIUM,
                originInfo = DataOriginInfo(
                    title = "Berita Pembatasan Ekspor Kestrel",
                    displayedValue = "Keyakinan sedang (2 sumber perdagangan)",
                    sourceName = "Harian Bisnis (Koresponden Pelabuhan Kestrel)",
                    dataDate = "1 Maret 2033",
                    calculationMethod = "Verifikasi dokumen manifes pelayaran Selat Kestrel dan wawancara asosiasi importir mesin.",
                    uncertaintyAndBiasNote = "Harian Bisnis akurat pada data logistik, namun pengiklan utamanya adalah bank investasi yang menjual instrumen lindung nilai komoditas.",
                    confidence = ConfidenceTier.MEDIUM
                )
            ),
            NewsItem(
                id = "news_pro_gov",
                mediaSlant = MediaSlant.PRO_PEMERINTAH,
                headline = "Pemerintah pastikan pasokan industri Sundara aman dari gejolak Kestrel",
                summary = "Menteri Perindustrian menegaskan cadangan komponen strategis nasional cukup untuk 6 bulan. Pelaku usaha diminta tenang dan tidak menaikkan harga jual menjelang tahun politik.",
                dateLabel = "1 Maret 2033 · Rilis Resmi",
                confidence = ConfidenceTier.LOW,
                isMacroRelease = true,
                originInfo = DataOriginInfo(
                    title = "Klaim Cadangan Komponen Pemerintah",
                    displayedValue = "Keyakinan rendah (1 sumber politis)",
                    sourceName = "Suara Sundara (Media Pro-Pemerintah)",
                    dataDate = "1 Maret 2033",
                    calculationMethod = "Siaran pers Kementerian Perindustrian tanpa audit gudang fisik independen.",
                    uncertaintyAndBiasNote = "Bias tinggi: pemerintah sedang menjaga sentimen konsumen dan nilai tukar RS menjelang pemilu.",
                    confidence = ConfidenceTier.LOW
                )
            ),
            NewsItem(
                id = "news_oposisi",
                mediaSlant = MediaSlant.OPOSISI,
                headline = "Ketergantungan impor Kestrel bukti gagalnya industrialisasi hulu Sundara",
                summary = "Ketika Kestrel menahan pasokan komponen mesin dan harga beras Tarsis naik, pabrik lokal terjepit antara biaya suku cadang impor dan pemadaman listrik bergilir di Kalimas.",
                dateLabel = "1 Maret 2033 · Opini Investigasi",
                confidence = ConfidenceTier.MEDIUM,
                originInfo = DataOriginInfo(
                    title = "Analisis Kerentanan Hulu Industri",
                    displayedValue = "Keyakinan sedang",
                    sourceName = "Mimbar Rakyat (Media Oposisi)",
                    dataDate = "1 Maret 2033",
                    calculationMethod = "Wawancara serikat pekerja dan data pemadaman gardu induk Kalimas.",
                    uncertaintyAndBiasNote = "Fakta lapangan terkait pemadaman listrik akurat, namun nada artikel membesar-besarkan risiko kebangkrutan massal.",
                    confidence = ConfidenceTier.MEDIUM
                )
            ),
            NewsItem(
                id = "news_sensasional",
                mediaSlant = MediaSlant.SENSASIONAL,
                headline = "PANIK LOGAM LANGKA! Pabrik-pabrik tekstil terancam berhenti total bulan depan!",
                summary = "Rumor beredar bahwa spekulan di Meridia telah memborong seluruh kontrak pengiriman suku cadang Kestrel. Sejumlah distributor dikabarkan siap membatalkan kontrak secara sepihak!",
                dateLabel = "1 Maret 2033 · Kolom Rumor",
                confidence = ConfidenceTier.LOW,
                isRumor = true,
                originInfo = DataOriginInfo(
                    title = "Rumor Borongan Spekulan Meridia",
                    displayedValue = "Keyakinan rendah (Rumor belum terverifikasi)",
                    sourceName = "Kilat Sore (Tabloid Sensasional)",
                    dataDate = "1 Maret 2033",
                    calculationMethod = "Bocoran anonim dari lantai bursa komoditas.",
                    uncertaintyAndBiasNote = "Media sensasional kerap dibayar oleh short-seller atau pemasok yang ingin memicu panic buying agar harga spot melonjak.",
                    confidence = ConfidenceTier.LOW
                )
            ),
            NewsItem(
                id = "news_independen",
                mediaSlant = MediaSlant.INDEPENDEN,
                headline = "Jejak rantai pasok: 80% sensor mesin tenun Sundara bergantung pada komponen Velmora–Kestrel",
                summary = "Penelusuran dokumen pengapalan menunjukkan pembatasan Kestrel dipicu sanksi ekstrateritorial Aurelia terhadap tambang Velmora. Perusahaan yang memakai pemasok Tier-2 tanpa uji tuntas berisiko terkena denda.",
                dateLabel = "1 Maret 2033 · Laporan Forensik",
                confidence = ConfidenceTier.HIGH,
                originInfo = DataOriginInfo(
                    title = "Audit Rantai Pasok Tier-2 Velmora–Kestrel",
                    displayedValue = "Keyakinan tinggi (Verifikasi silang 3 pelabuhan)",
                    sourceName = "Lensa Independen (Konsorsium Jurnalis Investigatif)",
                    dataDate = "1 Maret 2033",
                    calculationMethod = "Pencocokan kode HS bea cukai Velmora, pabrik perakitan Kestrel, dan daftar sanksi OFAC Aurelia.",
                    uncertaintyAndBiasNote = "Sumber paling independen. Tidak memiliki sponsor korporat maupun afiliasi partai.",
                    confidence = ConfidenceTier.HIGH
                )
            )
        )

        val mapPieces = listOf(
            MapPiece("pc_pabrik_1", MapTokenType.PABRIK, "Pabrik Kalimas", "sundara", 0.56f, 0.72f, "Utilisasi 88% · Listrik rawan padam"),
            MapPiece("pc_gudang_1", MapTokenType.GUDANG, "Gudang Tanjung", "sundara", 0.62f, 0.66f, "Stok kapas 53 hari (DIO)"),
            MapPiece("pc_toko_1", MapTokenType.TOKO, "Ritel Kota Raya", "sundara", 0.51f, 0.77f, "Piutang ritel DSO 62 hari"),
            MapPiece("pc_kapal_1", MapTokenType.KAPAL, "KM Nusantara I", "kestrel", 0.69f, 0.48f, "Melintasi Selat Kestrel (+2 hari tunda)"),
            MapPiece("pc_gudang_2", MapTokenType.GUDANG, "Depo Kapas Tarsis", "tarsis", 0.34f, 0.74f, "Kontrak pasokan kapas Tier-1"),
            MapPiece("pc_kapal_2", MapTokenType.KAPAL, "MT Boreal Star", "norvaldia", 0.36f, 0.28f, "Jalur energi Selat Norvaldia–Halvenia")
        )

        val countries = listOf(
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
                    dataDate = "Februari 2033 (Tepat waktu)",
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
                pieces = mapPieces.filter { it.countryId == "norvaldia" },
                originInfo = DataOriginInfo(
                    title = "Data Makro Norvaldia",
                    displayedValue = "Inflasi 5,0–6,5% · Ketegangan 66/100",
                    sourceName = "Kementerian Energi Norvaldia",
                    dataDate = "Januari 2033",
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
                pieces = mapPieces.filter { it.countryId == "kestrel" },
                originInfo = DataOriginInfo(
                    title = "Data Makro & Rantai Pasok Kestrel",
                    displayedValue = "PDB 4,9% · Kuasai 80% rantai komponen",
                    sourceName = "Biro Industri Kestrel & Lensa Independen",
                    dataDate = "Januari 2033",
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
                    dataDate = "Februari 2033",
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
                    dataDate = "Februari 2033",
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
                pieces = mapPieces.filter { it.countryId == "sundara" },
                originInfo = DataOriginInfo(
                    title = "Data Makro Sundara",
                    displayedValue = "Inflasi 7–9% · Bunga 8,25% · Pemilu 9 bln lagi",
                    sourceName = "Biro Statistik & Bank Sentral Sundara",
                    dataDate = "Data Januari 2033 (Terlambat 2 bulan)",
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
                pieces = mapPieces.filter { it.countryId == "tarsis" },
                originInfo = DataOriginInfo(
                    title = "Data Komoditas & Demografi Tarsis",
                    displayedValue = "Inflasi 6,8–8,4% · Risiko Iklim 72/100",
                    sourceName = "Kementerian Pertanian Tarsis",
                    dataDate = "Januari 2033",
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
                    dataDate = "Februari 2033",
                    calculationMethod = "Skor eskalasi sanksi Aurelia–Velmora.",
                    uncertaintyAndBiasNote = "Angka ekonomi resmi Velmora dimanipulasi rezim. Bahaya utama bagi pemain adalah sanksi menular lewat pemasok Tier-2 di Kestrel.",
                    confidence = ConfidenceTier.LOW
                )
            )
        )

        val executives = listOf(
            Executive(
                id = "exec_coo",
                initials = "SA",
                name = "Surya Atmaja",
                roleTitle = "Direktur Operasi (COO)",
                loyaltyEstimate = "Tinggi?",
                ambitionEstimate = "Sedang?",
                skillEstimate = "Kuat di logistik",
                horizonNote = "Horizon karier >3 tahun di perusahaan",
                incentiveScheme = "Bonus efisiensi & ketepatan kirim",
                suspectedBehaviorNote = "Cenderung meminta cadangan kas tinggi dan melebih-lebihkan dampak tuntutan serikat agar operasional pabrik tidak terganggu.",
                loyaltyTone = SemanticTone.GOOD_GREEN,
                originInfo = DataOriginInfo(
                    title = "Penilaian Profil Direktur Operasi",
                    displayedValue = "Loyalitas: Tinggi? · Ambisi: Sedang?",
                    sourceName = "Observasi HRD & Rekam Jejak Rapat",
                    dataDate = "Evaluasi Tahunan 2032",
                    calculationMethod = "Tidak ada bar persentase pasti di KAPITAL; estimasi didasarkan pada konsistensi laporan operasi vs audit fisik.",
                    uncertaintyAndBiasNote = "Setiap eksekutif memaksimalkan fungsi utilitas pribadinya: U(a) = Σ w_k G_k(a) - λ P_ketahuan C_hukuman.",
                    confidence = ConfidenceTier.MEDIUM
                )
            ),
            Executive(
                id = "exec_sales",
                initials = "BW",
                name = "Baskoro Wijaya",
                roleTitle = "Direktur Penjualan (Sales)",
                loyaltyEstimate = "Rendah?",
                ambitionEstimate = "Sangat tinggi?",
                skillEstimate = "Agresif membuka pasar ritel",
                horizonNote = "Berencana pindah kerja dalam <18 bulan ('bakar lalu kabur')",
                incentiveScheme = "Bonus berbasis omzet kotor",
                suspectedBehaviorNote = "PERINGATAN FORENSIK: Diduga mengobral kredit 90 hari ke peritel rapuh (channel stuffing) agar omzet & bonus kuartal ini naik; berisiko macet 6 bulan lagi.",
                loyaltyTone = SemanticTone.DANGER_RED,
                originInfo = DataOriginInfo(
                    title = "Penilaian Profil Direktur Penjualan",
                    displayedValue = "Loyalitas: Rendah? · Ambisi: Sangat tinggi?",
                    sourceName = "Audit Piutang & Rumor Headhunter",
                    dataDate = "Februari 2033",
                    calculationMethod = "Perbandingan pertumbuhan omzet akrual (+14%) vs kas tertagih riil (-6%).",
                    uncertaintyAndBiasNote = "Ubah skema insentif ke 'Bonus berbasis kas tertagih + clawback 3 tahun' untuk menghentikan manipulasi KPI ini.",
                    confidence = ConfidenceTier.LOW
                )
            ),
            Executive(
                id = "exec_factory",
                initials = "HG",
                name = "Ir. Hendra Gunawan",
                roleTitle = "Kepala Pabrik Kalimas",
                loyaltyEstimate = "Sedang?",
                ambitionEstimate = "Tinggi?",
                skillEstimate = "Ahli mesin tenun",
                horizonNote = "Horizon karier ~2 tahun",
                incentiveScheme = "KPI penghematan biaya kuartalan",
                suspectedBehaviorNote = "Menunda penggantian bantalan mesin tenun tua selama 3 bulan agar biaya kuartal terlihat hemat (menumpuk utang teknis tersembunyi).",
                loyaltyTone = SemanticTone.WARNING_YELLOW,
                originInfo = DataOriginInfo(
                    title = "Penilaian Profil Kepala Pabrik",
                    displayedValue = "Loyalitas: Sedang? · Utang Teknis Tersembunyi",
                    sourceName = "Inspeksi Suku Cadang Gudang Kalimas",
                    dataDate = "Februari 2033",
                    calculationMethod = "Peluang kecelakaan/kerusakan pabrik = dasar 0,2% × (1 + 0,5 per bulan perawatan ditunda).",
                    uncertaintyAndBiasNote = "Laporan Kepala Pabrik menekan angka keausan mesin agar target penghematan kuartal tercapai.",
                    confidence = ConfidenceTier.MEDIUM
                )
            ),
            Executive(
                id = "exec_cfo",
                initials = "RP",
                name = "Ratna Pramudita",
                roleTitle = "Direktur Keuangan (CFO)",
                loyaltyEstimate = "Tinggi?",
                ambitionEstimate = "Sedang?",
                skillEstimate = "Akuntansi akrual & negosiasi bank",
                horizonNote = "Kandidat suksesi internal jangka panjang",
                incentiveScheme = "Bonus stabilitas likuiditas & audit bersih",
                suspectedBehaviorNote = "Secara konsisten memperingatkan bahaya jebakan akrual (CCC) dan risiko technical default covenant Bank B.",
                loyaltyTone = SemanticTone.GOOD_GREEN,
                originInfo = DataOriginInfo(
                    title = "Penilaian Profil CFO",
                    displayedValue = "Loyalitas: Tinggi? · Takut Risiko (λ) Tinggi",
                    sourceName = "Hasil Audit Eksternal 2032",
                    dataDate = "Januari 2033",
                    calculationMethod = "Rekonsiliasi kas bank terhadap buku besar akrual tanpa selisih material.",
                    uncertaintyAndBiasNote = "Sangat berhati-hati; cenderung menolak ekspansi agresif yang membebani rasio Utang Bersih / EBITDA.",
                    confidence = ConfidenceTier.HIGH
                )
            )
        )

        val incomeRows = listOf(
            FinancialRowItem("Pendapatan Akrual (Penjualan)", "14.800 jt RS", "14.100 jt RS", "Termasuk penjualan kredit ritel 60–90 hari", false, SemanticTone.GOOD_GREEN, covenantOrigin),
            FinancialRowItem("Harga Pokok Penjualan (COGS)", "(9.950 jt RS)", "(9.400 jt RS)", "Kapas Tarsis + suku cadang Kestrel", false, SemanticTone.NEUTRAL_GRAY, covenantOrigin),
            FinancialRowItem("Laba Kotor", "4.850 jt RS", "4.700 jt RS", "Margin kotor 32,7%", true, SemanticTone.NEUTRAL_GRAY, covenantOrigin),
            FinancialRowItem("Beban Operasional & Upah (340 pekerja)", "(2.450 jt RS)", "(2.380 jt RS)", "Tekanan inflasi pangan Sundara", false, SemanticTone.NEUTRAL_GRAY, covenantOrigin),
            FinancialRowItem("Beban Perawatan & R&D", "(420 jt RS)", "(610 jt RS)", "Kuning: perawatan ditunda Kepala Pabrik!", false, SemanticTone.WARNING_YELLOW, covenantOrigin),
            FinancialRowItem("EBITDA", "1.980 jt RS", "1.710 jt RS", "Acuan covenant Utang Bersih/EBITDA", true, SemanticTone.GOOD_GREEN, covenantOrigin),
            FinancialRowItem("Penyusutan Mesin & Pabrik", "(520 jt RS)", "(520 jt RS)", "Mesin tenun tua Kalimas", false, SemanticTone.NEUTRAL_GRAY, covenantOrigin),
            FinancialRowItem("EBIT (Laba Operasi)", "1.460 jt RS", "1.190 jt RS", "Acuan covenant ICR >= 2,50x", true, SemanticTone.NEUTRAL_GRAY, covenantOrigin),
            FinancialRowItem("Beban Bunga Pinjaman Bank B", "(550 jt RS)", "(510 jt RS)", "Bunga mengambang naik mengikuti Bank Sentral", false, SemanticTone.DANGER_RED, covenantOrigin),
            FinancialRowItem("Pajak Penghasilan Badan (PPh)", "(200 jt RS)", "(150 jt RS)", "Dikenakan atas laba akrual meski kas belum masuk!", false, SemanticTone.WARNING_YELLOW, covenantOrigin),
            FinancialRowItem("Laba Bersih (Net Income)", "710 jt RS", "530 jt RS", "Positif di atas kertas, tetapi cek Arus Kas!", true, SemanticTone.GOOD_GREEN, covenantOrigin)
        )

        val balanceRows = listOf(
            FinancialRowItem("Kas & Setara Kas", "6.200 jt RS", "7.300 jt RS", "Merah: Runway 5 bulan (-1.100/bln)", true, SemanticTone.DANGER_RED, covenantOrigin),
            FinancialRowItem("Piutang Usaha (DSO 62 hari)", "30.580 jt RS", "28.400 jt RS", "1 pelanggan ritel besar menunggak", false, SemanticTone.WARNING_YELLOW, covenantOrigin),
            FinancialRowItem("Persediaan (DIO 53 hari)", "17.580 jt RS", "16.900 jt RS", "Kapas & kain jadi di gudang", false, SemanticTone.NEUTRAL_GRAY, covenantOrigin),
            FinancialRowItem("Aset Tetap Bersih (Tanah & Pabrik)", "64.200 jt RS", "64.720 jt RS", "Agunan utama pinjaman Bank B", false, SemanticTone.NEUTRAL_GRAY, covenantOrigin),
            FinancialRowItem("TOTAL ASET (Stock-Flow Consistent)", "118.560 jt RS", "117.320 jt RS", "Aset == Liabilitas + Ekuitas", true, SemanticTone.PRIMARY_BLUE, covenantOrigin),
            FinancialRowItem("Utang Dagang Pemasok (DPO 35 hari)", "11.600 jt RS", "11.200 jt RS", "Pemasok kapas Tarsis & suku cadang", false, SemanticTone.NEUTRAL_GRAY, covenantOrigin),
            FinancialRowItem("Utang Bank Jatuh Tempo <12 Bulan", "24.000 jt RS", "24.000 jt RS", "Bullet loan Bank B jatuh tempo sebentar lagi", false, SemanticTone.DANGER_RED, covenantOrigin),
            FinancialRowItem("Utang Bank Jangka Panjang", "18.000 jt RS", "17.870 jt RS", "Cicilan merata Bank Pembangunan", false, SemanticTone.NEUTRAL_GRAY, covenantOrigin),
            FinancialRowItem("Total Ekuitas (Modal & Laba Ditahan)", "64.960 jt RS", "64.250 jt RS", "Kepemilikan pendiri 68%", true, SemanticTone.GOOD_GREEN, covenantOrigin)
        )

        val cashFlowRows = listOf(
            FinancialRowItem("Laba Bersih Akrual", "710 jt RS", "530 jt RS", "Titik awal rekonsiliasi kas", false, SemanticTone.NEUTRAL_GRAY, covenantOrigin),
            FinancialRowItem("Penyusutan Non-Kas", "+520 jt RS", "+520 jt RS", "Ditambahkan kembali ke CFO", false, SemanticTone.NEUTRAL_GRAY, covenantOrigin),
            FinancialRowItem("Perubahan Modal Kerja (Piutang & Stok)", "(2.010 jt RS)", "(1.680 jt RS)", "Jebakan akrual: kas tersedot di piutang ritel!", false, SemanticTone.DANGER_RED, covenantOrigin),
            FinancialRowItem("Arus Kas Operasi (CFO)", "(780 jt RS)", "(630 jt RS)", "Negatif walau Laba Bersih positif! (Kualitas Laba < 0)", true, SemanticTone.DANGER_RED, covenantOrigin),
            FinancialRowItem("Arus Kas Investasi (CapEx)", "(120 jt RS)", "(200 jt RS)", "Persiapan lahan pemintalan", false, SemanticTone.NEUTRAL_GRAY, covenantOrigin),
            FinancialRowItem("Arus Kas Pendanaan (Cicilan Pokok)", "(200 jt RS)", "(200 jt RS)", "Cicilan rutin pinjaman", false, SemanticTone.NEUTRAL_GRAY, covenantOrigin),
            FinancialRowItem("Perubahan Kas Bersih Bulanan", "(1.100 jt RS)", "(1.030 jt RS)", "Sesuai kartu indikator Kas di Markas", true, SemanticTone.DANGER_RED, covenantOrigin)
        )

        val ganttTasks = listOf(
            GanttTask(
                id = "gantt_1",
                projectName = "Pabrik pemintalan",
                phaseName = "1. Akuisisi lahan & izin AMDAL Kalimas",
                startMonth = 1,
                durationMonths = 3,
                progressMonth = 3,
                budgetRsMillion = 450,
                realizedRsMillion = 480,
                statusLabel = "Selesai (Overbudget +30 jt RS)",
                tone = SemanticTone.WARNING_YELLOW,
                noteFromStaff = "Biaya izin membengkak karena birokrasi daerah tanpa uang pelicin."
            ),
            GanttTask(
                id = "gantt_2",
                projectName = "Pabrik pemintalan",
                phaseName = "2. Konstruksi sipil & instalasi gardu listrik",
                startMonth = 3,
                durationMonths = 5,
                progressMonth = 2,
                budgetRsMillion = 950,
                realizedRsMillion = 520,
                statusLabel = "Berjalan (Bulan 2/5)",
                tone = SemanticTone.PRIMARY_BLUE,
                noteFromStaff = "Sesuai jadwal; membutuhkan pasokan baja Kestrel."
            ),
            GanttTask(
                id = "gantt_3",
                projectName = "Pabrik pemintalan",
                phaseName = "3. Impor & perakitan mesin pintal presisi",
                startMonth = 7,
                durationMonths = 4,
                progressMonth = 0,
                budgetRsMillion = 1000,
                realizedRsMillion = 0,
                statusLabel = "Rawan tertunda (Pembatasan ekspor Kestrel)",
                tone = SemanticTone.DANGER_RED,
                noteFromStaff = "Harga mesin berisiko naik +18% akibat kuota logam langka Kestrel."
            ),
            GanttTask(
                id = "gantt_4",
                projectName = "Pabrik pemintalan",
                phaseName = "4. Uji coba produksi & pelatihan operator",
                startMonth = 10,
                durationMonths = 3,
                progressMonth = 0,
                budgetRsMillion = 300,
                realizedRsMillion = 0,
                statusLabel = "Menunggu tahap 3",
                tone = SemanticTone.NEUTRAL_GRAY,
                noteFromStaff = "Target menurunkan ketergantungan benang impor sebesar 60%."
            )
        )

        val contracts = listOf(
            ContractItem(
                id = "cnt_bank_b",
                category = "Kredit Bank Aktif",
                counterparty = "Bank B (Bank Niaga Sundara)",
                countryName = "Sundara",
                valueText = "24.000 jt RS (Bunga Mengambang 11,5%)",
                maturityOrTenor = "Uji Covenant dalam 1 giliran · Jatuh tempo 6 bln",
                covenantOrClause = "Wajib ICR (EBIT/Bunga) >= 2,50x dan Utang Bersih/EBITDA <= 3,50x. Saat ini jarak ke batas covenant tinggal 6%.",
                hiddenRiskNote = "Mengandung klausul Cross-Default & hak menyita Pabrik Kalimas jika melanggar covenant walau cicilan lancar.",
                tone = SemanticTone.WARNING_YELLOW,
                apCostToAct = 2,
                actionLabel = "Ajukan Refinancing (2 AP)",
                originInfo = covenantOrigin
            ),
            ContractItem(
                id = "cnt_ts_1",
                category = "Term Sheet Tawaran #1",
                counterparty = "Bank Pembangunan Sundara",
                countryName = "Sundara",
                valueText = "25.000 jt RS (Bunga Tetap 10,8%)",
                maturityOrTenor = "Tenor 48 bulan · Cicilan Merata (Amortizing)",
                covenantOrClause = "Covenant longgar: ICR >= 2,20x · Haircut agunan pabrik 25%.",
                hiddenRiskNote = "Aman dari gejolak kurs dan kenaikan suku bunga acuan, namun pencairan butuh 2 giliran.",
                tone = SemanticTone.GOOD_GREEN,
                apCostToAct = 2,
                actionLabel = "Pilih Term Sheet Ini (2 AP)",
                originInfo = covenantOrigin
            ),
            ContractItem(
                id = "cnt_ts_2",
                category = "Term Sheet Tawaran #2 (Jebakan Valas)",
                counterparty = "Aurelia Global CitiTrust",
                countryName = "Aurelia",
                valueText = "1,6 Juta A$ (Setara 24.000 jt RS · Bunga 5,2%)",
                maturityOrTenor = "Tenor 36 bulan · Bullet Loan (Pokok di akhir)",
                covenantOrClause = "Covenant ketat: ICR >= 2,80x + Klausul Material Adverse Change (MAC).",
                hiddenRiskNote = "JEBAKAN TERM SHEET: Terlihat murah (5,2%), tetapi berdenominasi A$. Jika kurs RS anjlok 20% saat pemilu Sundara, beban utang meledak (Currency Mismatch)!",
                tone = SemanticTone.DANGER_RED,
                apCostToAct = 2,
                actionLabel = "Tinjau Term Sheet Valas (2 AP)",
                originInfo = covenantOrigin
            ),
            ContractItem(
                id = "cnt_sup_kestrel",
                category = "Pemasok Tier-2 (Rantai Pasok Leontief)",
                counterparty = "Kestrel Precision Loom Parts",
                countryName = "Kestrel",
                valueText = "Kontrak Suku Cadang & Sensor Tenun (1.800 jt RS/thn)",
                maturityOrTenor = "Kontrak Just-In-Time · Tanpa Denda Keterlambatan",
                covenantOrClause = "Memasok 80% kebutuhan sensor dan jarum mesin tenun Kalimas.",
                hiddenRiskNote = "RISIKO SANKSI BERLAPIS & HOLD-UP: Diam-diam memakai magnet logam tanah jarang dari Velmora. Rawan membatalkan kiriman ('mesin rusak') demi menjual ke pasar spot 3x lipat!",
                tone = SemanticTone.WARNING_YELLOW,
                apCostToAct = 1,
                actionLabel = "Aktifkan Dual Sourcing (1 AP)",
                originInfo = covenantOrigin
            )
        )

        val marketInstruments = listOf(
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
                    dataDate = "1 Maret 2033",
                    calculationMethod = "Terbentuk dari buku pesanan 207.000 agen, cadangan air irigasi Tarsis, dan stok gudang global.",
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
                    dataDate = "1 Maret 2033",
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
                    dataDate = "Februari 2033",
                    calculationMethod = "Matriks Leontief x = (I - A)^-1 d; kenaikan harga baja langsung menaikkan anggaran konstruksi Pabrik Pemintalan.",
                    uncertaintyAndBiasNote = "Pembatasan ekspor logam langka Kestrel memicu kelangkaan baja presisi.",
                    confidence = ConfidenceTier.MEDIUM
                )
            ),
            MarketInstrument(
                id = "mkt_kurs",
                category = "Kurs Valas",
                name = "Kurs Rupiah Sundara / Dolar Aurelia",
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
                    dataDate = "1 Maret 2033",
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
                    dataDate = "Februari 2033",
                    calculationMethod = "Valuasi buku pesanan 5.000 agen investor institusional.",
                    uncertaintyAndBiasNote = "Arketipe Perampas Harga: sedang mengintai jadwal jatuh tempo utang Bank B Anda untuk memicu perang harga.",
                    confidence = ConfidenceTier.HIGH
                )
            )
        )

        val pairwiseTensions = listOf(
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

        val eventDialog = EventDialogModel(
            id = "evt_union_ultimatum",
            title = "Ultimatum Serikat Pekerja Tekstil Kalimas",
            contextDescription = "Harga beras yang melonjak akibat cuaca ekstrem di Tarsis memicu keresahan 340 pekerja PT Benang Sundara. Pengurus serikat memberikan tenggat 2 giliran sebelum menggelar mogok massal tepat saat pabrik mengejar target pengiriman ritel.",
            earlyWarningNote = "Mimbar Rakyat (Januari 2033) telah memberitakan lonjakan harga beras 14% di kawasan buruh Kalimas.",
            options = listOf(
                EventOptionItem(
                    id = "opt_union_1",
                    title = "Setujui kenaikan upah pokok 8% + tunjangan beras",
                    apCost = 1,
                    impactEstimate = "Biaya operasi naik 180 jt RS/bulan, risiko mogok batal, moral pekerja pulih",
                    advisorAttribution = "menurut Direktur Operasi",
                    tone = SemanticTone.GOOD_GREEN
                ),
                EventOptionItem(
                    id = "opt_union_2",
                    title = "Tawarkan bagi hasil kuartalan berbasis penurunan tingkat cacat kain",
                    apCost = 2,
                    impactEstimate = "Kas keluar awal hanya 60 jt RS/bulan, kualitas kain naik, namun serikat masih skeptis",
                    advisorAttribution = "menurut Direktur Operasi",
                    tone = SemanticTone.PRIMARY_BLUE
                ),
                EventOptionItem(
                    id = "opt_union_3",
                    title = "Tolak tuntutan & sewa tenaga kontrak luar untuk berjaga-jaga",
                    apCost = 1,
                    impactEstimate = "Hemat kas bulan ini, tetapi risiko mogok dan kerusakan mesin naik tajam",
                    advisorAttribution = "menurut Direktur Operasi",
                    tone = SemanticTone.DANGER_RED
                )
            )
        )

        val initialJournals = listOf(
            CompletedJournalEntry(
                id = "jrn_31",
                decisionTitle = "Pengetatan Syarat Kredit Pelanggan Ritel (Giliran 31)",
                turnSubmitted = 31,
                evaluateAtTurn = 37,
                strategicReason = "Menurunkan Siklus Konversi Kas (CCC) dari 125 hari agar perusahaan tidak mati kehabisan kas akibat jebakan akrual.",
                numericPrediction = "CCC turun ke bawah 85 hari dalam 6 giliran tanpa menurunkan omzet lebih dari 5%.",
                confidencePct = 75,
                failureScenario = "Peritel besar pindah ke Grup Mahkota Tekstil yang masih mengobral kredit 90 hari.",
                evaluated = true,
                brierScoreContribution = 0.0625
            )
        )

        val chronicles = listOf(
            ChronicleEntry(
                turn = 39,
                dateLabel = "Maret 2033",
                eraLabel = "Era 1: Bertahan Hidup",
                title = "Pembatasan Ekspor Logam Langka Kestrel & Tekanan Covenant Bank B",
                description = "Kestrel mulai membatasi ekspor komponen presisi sebagai balasan tarif Aurelia. Di dalam negeri, jarak ke covenant Bank B menyempit ke 6%.",
                causalChain = "Sanksi Aurelia ke Velmora → Kestrel tahan ekspor komponen → biaya perawatan pabrik Kalimas naik → EBIT tertekan."
            ),
            ChronicleEntry(
                turn = 31,
                dateLabel = "Juli 2032",
                eraLabel = "Era 1: Bertahan Hidup",
                title = "Reformasi Modal Kerja: Siklus Kas Turun dari 125 ke 80 Hari",
                description = "Penagihan tunggakan ritel dan negosiasi ulang DPO pemasok berhasil menurunkan CCC sebesar 45 hari.",
                causalChain = "Restrukturisasi piutang ritel → DSO turun dari 85 ke 62 hari → kas operasi tertolong."
            ),
            ChronicleEntry(
                turn = 1,
                dateLabel = "Januari 2030",
                eraLabel = "Era 1: Bertahan Hidup",
                title = "Awal Warisan PT Benang Sundara (340 Pekerja)",
                description = "Mewarisi pabrik tekstil bermesin tua di Sundara dengan CCC 125 hari, kas cukup untuk 4 bulan, dan utang bank jatuh tempo.",
                causalChain = "Titik awal simulasi deterministik (Seed 20300101)."
            )
        )

        return GameState(
            covenantOrigin = covenantOrigin,
            markasIndicators = markasIndicators,
            cashHistoryPoints = listOf(9800f, 8900f, 8200f, 7600f, 7300f, 6200f),
            cashFanMedianPoints = listOf(5400f, 4800f, 4500f, 4300f, 4400f, 4700f),
            cashFanBand50Points = listOf(450f, 800f, 1150f, 1500f, 1850f, 2200f),
            cashFanBand90Points = listOf(900f, 1650f, 2350f, 3100f, 3800f, 4500f),
            pendingDecisions = pendingDecisions,
            completedJournals = initialJournals,
            morningPaperEdition = morningPaper,
            countries = countries,
            mapPieces = mapPieces,
            pairwiseTensions = pairwiseTensions,
            incomeStatementRows = incomeRows,
            balanceSheetRows = balanceRows,
            cashFlowRows = cashFlowRows,
            executives = executives,
            ganttTasks = ganttTasks,
            contracts = contracts,
            marketInstruments = marketInstruments,
            detectedBiases = listOf(
                "Peringatan Confirmation Bias: 2 giliran terakhir Anda hanya membuka Harian Bisnis tanpa memeriksa silang Mimbar Rakyat atau Lensa Independen.",
                "Kalibrasi Brier Score (0,16): Prediksi modal kerja Anda cukup terkalibrasi, namun waspadai overconfidence pada jadwal proyek konstruksi."
            ),
            worldChronicles = chronicles,
            activeEventDialog = eventDialog
        )
    }
}
