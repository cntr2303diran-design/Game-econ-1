package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.HistoryEdu
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.components.semanticToneColor
import com.example.ui.components.semanticToneContainerColor
import com.example.ui.theme.KapitalSemanticColors
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.NotoSerifFontFamily
import java.util.Locale

@Composable
fun DuniaScreen(
    gameState: GameState,
    onOpenKronikDunia: () -> Unit,
    onSelectCountry: (Country) -> Unit,
    onSetDifficulty: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isLandscapeWide = maxWidth >= 700.dp
        val scrollState = rememberScrollState()

        if (isLandscapeWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KronikDuniaBannerCard(
                        chronicleCount = gameState.worldChronicles.size,
                        onOpenKronikDunia = onOpenKronikDunia
                    )
                    if (gameState.campaignEvaluation != null) {
                        CampaignEvaluationDetailCard(eval = gameState.campaignEvaluation)
                    }
                    DifficultySelectorCard(
                        currentDifficulty = gameState.difficultyName,
                        difficultyLabel = gameState.difficultyLabel,
                        difficultyDesc = gameState.difficultyDesc,
                        onSetDifficulty = onSetDifficulty
                    )
                    BrierCalibrationCard(
                        brierScore = gameState.brierScore,
                        detectedBiases = gameState.detectedBiases,
                        completedJournals = gameState.completedJournals
                    )
                }

                Column(
                    modifier = Modifier.weight(1.1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GeopoliticalTensionTable(tensions = gameState.pairwiseTensions)
                    WorldDirectoryMacroTable(
                        countries = gameState.countries,
                        onSelectCountry = onSelectCountry
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KronikDuniaBannerCard(
                    chronicleCount = gameState.worldChronicles.size,
                    onOpenKronikDunia = onOpenKronikDunia
                )
                if (gameState.campaignEvaluation != null) {
                    CampaignEvaluationDetailCard(eval = gameState.campaignEvaluation)
                }
                DifficultySelectorCard(
                    currentDifficulty = gameState.difficultyName,
                    difficultyLabel = gameState.difficultyLabel,
                    difficultyDesc = gameState.difficultyDesc,
                    onSetDifficulty = onSetDifficulty
                )
                BrierCalibrationCard(
                    brierScore = gameState.brierScore,
                    detectedBiases = gameState.detectedBiases,
                    completedJournals = gameState.completedJournals
                )
                GeopoliticalTensionTable(tensions = gameState.pairwiseTensions)
                WorldDirectoryMacroTable(
                    countries = gameState.countries,
                    onSelectCountry = onSelectCountry
                )
            }
        }
    }
}

@Composable
private fun KronikDuniaBannerCard(
    chronicleCount: Int,
    onOpenKronikDunia: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, KapitalSemanticColors.blue.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .clickable { onOpenKronikDunia() }
            .defaultMinSize(minHeight = 64.dp)
            .testTag("open_kronik_dunia_card")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.HistoryEdu,
                    contentDescription = "Kronik Dunia",
                    tint = KapitalSemanticColors.blue,
                    modifier = Modifier.size(26.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Kronik Dunia & Cabang 'Bagaimana Jika'",
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = NotoSerifFontFamily),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$chronicleCount babak sejarah tercatat · Putar ulang keputusan masa lalu dari seed deterministik",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = "Buka Kronik Dunia",
                tint = KapitalSemanticColors.blue
            )
        }
    }
}

@Composable
private fun CampaignEvaluationDetailCard(eval: CampaignEvaluationUi) {
    val gradeColor = when (eval.grade) {
        "S", "A" -> KapitalSemanticColors.green
        "B", "C" -> KapitalSemanticColors.blue
        "D" -> KapitalSemanticColors.yellow
        else -> KapitalSemanticColors.red
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
            .testTag("dunia_campaign_evaluation_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VerifiedUser,
                        contentDescription = null,
                        tint = gradeColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Evaluasi Kampanye 5 Komponen & Skor Etika",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = eval.archetypeTitle,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = gradeColor
                        )
                    }
                }
                Surface(
                    color = gradeColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.border(1.dp, gradeColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                ) {
                    Text(
                        text = "Nilai ${eval.grade} (${eval.totalScore}/100)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = gradeColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = eval.summaryText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val pillars: List<Triple<String, Int, String>> = listOf(
                Triple("1. Nilai Perusahaan & Solvabilitas", eval.nilaiPerusahaanScore, "Pertumbuhan ekuitas, kas, dan kepatuhan covenant bank"),
                Triple("2. Ketahanan Operasional & Rantai Pasok", eval.ketahananScore, "Utilisasi kapasitas, cadangan kas, dan diversifikasi anak usaha"),
                Triple("3. Kualitas Keputusan & Kalibrasi Brier", eval.kualitasKeputusanScore, "Kepercayaan dewan (${eval.boardTrust}/100) & akurasi prediksi jurnal"),
                Triple("4. Etika Korporasi & Reputasi Publik", eval.etikaReputasiScore, "${eval.ethicsRiskLabel} · Bukti: ${eval.ethicsEvidencesCount} · Skandal: ${eval.scandalsCount}"),
                Triple("5. Dampak Sosial, Pekerja & Lingkungan", eval.dampakSosialScore, "Kesejahteraan 50.000 rumah tangga Sundara, K3, dan dekarbonisasi")
            )

            pillars.forEach { (title, score, subtitle) ->
                val tone = when {
                    score >= 75 -> SemanticTone.GOOD_GREEN
                    score >= 50 -> SemanticTone.WARNING_YELLOW
                    else -> SemanticTone.DANGER_RED
                }
                val barCol = semanticToneColor(tone)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "$score/100",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold),
                            color = barCol
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (score / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(5.dp),
                        color = barCol,
                        trackColor = KapitalSemanticColors.border
                    )
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DifficultySelectorCard(
    currentDifficulty: String,
    difficultyLabel: String,
    difficultyDesc: String,
    onSetDifficulty: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
            .testTag("difficulty_selector_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tingkat Kesulitan Simulasi",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = difficultyLabel,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = KapitalSemanticColors.blue
                )
            }
            Text(
                text = difficultyDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val options = listOf(
                    "MAGANG" to "Magang",
                    "MANAJER" to "Manajer",
                    "DIREKTUR" to "Direktur",
                    "KRISIS_ABADI" to "Krisis Abadi"
                )
                options.forEach { (code, label) ->
                    val selected = currentDifficulty == code
                    FilterChip(
                        selected = selected,
                        onClick = { onSetDifficulty(code) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("difficulty_chip_${code.lowercase()}")
                    )
                }
            }
        }
    }
}

@Composable
private fun BrierCalibrationCard(
    brierScore: Double,
    detectedBiases: List<String>,
    completedJournals: List<CompletedJournalEntry>
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Psychology,
                        contentDescription = null,
                        tint = KapitalSemanticColors.blue,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Kalibrasi Prediksi (Skor Brier) & Bias Kognitif",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    color = KapitalSemanticColors.yellowContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "Brier %.2f", brierScore),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = KapitalSemanticColors.yellow,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "Skor Brier 0.00 = kalibrasi sempurna; >0.20 = terlalu percaya diri (overconfidence). Diukur dari Jurnal Keputusan yang telah jatuh tempo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = KapitalSemanticColors.border)

            Text(
                text = "Bias Pengambilan Keputusan yang Terdeteksi:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            detectedBiases.forEach { bias ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "• $bias",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (completedJournals.isNotEmpty()) {
                HorizontalDivider(color = KapitalSemanticColors.border)
                Text(
                    text = "Evaluasi Jurnal Keputusan Terdahulu:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                completedJournals.forEach { entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(4.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(entry.decisionTitle, style = MaterialTheme.typography.labelLarge)
                            Text(
                                "Giliran ${entry.turnSubmitted} · Keyakinan ${entry.confidencePct}%",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("Prediksi Anda: ${entry.numericPrediction}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Kenyataan: ${entry.outcomeText ?: "Menunggu jatuh tempo evaluasi (Giliran ${entry.evaluateAtTurn})"}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = KapitalSemanticColors.yellow
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GeopoliticalTensionTable(tensions: List<PairwiseTension>) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Skor Ketegangan Antarnegara (0–100) & Sanksi",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            tensions.forEach { item ->
                val toneColor = semanticToneColor(item.tone)
                val toneBg = semanticToneContainerColor(item.tone)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(4.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${item.pairLabel} (${item.straitOrBorder})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            color = toneBg,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Skor ${item.score}/100 · ${item.escalationStage}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = MonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = toneColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text(
                        text = item.businessEffect,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun WorldDirectoryMacroTable(
    countries: List<Country>,
    onSelectCountry: (Country) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Kalender Pemilu, Tarif & Suku Bunga 8 Negara",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            countries.forEach { country ->
                val tone = when {
                    country.tensionScore >= 70 -> SemanticTone.DANGER_RED
                    country.tensionScore >= 45 -> SemanticTone.WARNING_YELLOW
                    else -> SemanticTone.GOOD_GREEN
                }
                val toneColor = semanticToneColor(tone)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onSelectCountry(country) }
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                        .testTag("dunia_country_row_${country.id}"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text(
                            text = "${country.name} (${country.currencyCode})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = country.electionCountdown,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Bunga ${country.policyRate} · Tarif ${country.tariffText}",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Kurs ${country.fxToRsText}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                            color = toneColor
                        )
                    }
                }
                HorizontalDivider(color = KapitalSemanticColors.border)
            }
        }
    }
}
