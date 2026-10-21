package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.components.*
import com.example.ui.theme.KapitalSemanticColors
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.NotoSerifFontFamily

@Composable
fun MarkasScreen(
    gameState: GameState,
    onOpenKoranPagi: () -> Unit,
    onOpenJurnalKeputusan: (Decision) -> Unit,
    onOpenEventDialog: () -> Unit,
    onInspectOrigin: (DataOriginInfo) -> Unit,
    onAdvanceTurn: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isLandscapeWide = maxWidth >= 700.dp
        val scrollState = rememberScrollState()

        if (isLandscapeWide) {
            // Mode Landscape / Tablet: 2 Kolom Terminal Eksekutif (Kiri: Peringatan, Koran Pagi, Indikator; Kanan: FanChart, Menunggu Keputusan, Kalibrasi, Akhiri Giliran)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1.05f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CovenantWarningBanner(
                        title = gameState.covenantWarningTitle,
                        subtitle = gameState.covenantWarningSubtitle,
                        originInfo = gameState.covenantOrigin,
                        onInspectOrigin = onInspectOrigin
                    )

                    MorningPaperPreviewCard(
                        source = gameState.featuredNewsSource,
                        confidence = gameState.featuredNewsConfidence,
                        headline = gameState.featuredNewsHeadline,
                        footer = gameState.featuredNewsFooter,
                        onClick = onOpenKoranPagi
                    )

                    MarkasIndicatorsGrid(
                        indicators = gameState.markasIndicators,
                        onInspectOrigin = onInspectOrigin
                    )
                }

                Column(
                    modifier = Modifier.weight(0.95f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PendingDecisionsSection(
                        decisions = gameState.pendingDecisions,
                        onDecisionClick = { decision ->
                            if (decision.opensEventDialog) {
                                onOpenEventDialog()
                            } else {
                                onOpenJurnalKeputusan(decision)
                            }
                        }
                    )

                    FanChart(
                        title = "Proyeksi Kas & Ketidakpastian (FanChart)",
                        subtitle = "Pita keyakinan 50% & 90% dalam 6 giliran ke depan",
                        historyPoints = gameState.cashHistoryPoints,
                        medianFuturePoints = gameState.cashFanMedianPoints,
                        spread50Points = gameState.cashFanBand50Points,
                        spread90Points = gameState.cashFanBand90Points,
                        unitLabel = "juta RS"
                    )

                    CognitiveCalibrationCard(
                        brierScore = gameState.brierScore,
                        biases = gameState.detectedBiases,
                        completedJournals = gameState.completedJournals
                    )

                    EndTurnPrimaryButton(
                        turn = gameState.turn,
                        onAdvanceTurn = onAdvanceTurn
                    )
                }
            }
        } else {
            // Mode Portrait Satu Tangan
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CovenantWarningBanner(
                    title = gameState.covenantWarningTitle,
                    subtitle = gameState.covenantWarningSubtitle,
                    originInfo = gameState.covenantOrigin,
                    onInspectOrigin = onInspectOrigin
                )

                MorningPaperPreviewCard(
                    source = gameState.featuredNewsSource,
                    confidence = gameState.featuredNewsConfidence,
                    headline = gameState.featuredNewsHeadline,
                    footer = gameState.featuredNewsFooter,
                    onClick = onOpenKoranPagi
                )

                MarkasIndicatorsGrid(
                    indicators = gameState.markasIndicators,
                    onInspectOrigin = onInspectOrigin
                )

                PendingDecisionsSection(
                    decisions = gameState.pendingDecisions,
                    onDecisionClick = { decision ->
                        if (decision.opensEventDialog) {
                            onOpenEventDialog()
                        } else {
                            onOpenJurnalKeputusan(decision)
                        }
                    }
                )

                FanChart(
                    title = "Proyeksi Kas & Ketidakpastian (FanChart)",
                    subtitle = "Pita keyakinan 50% & 90% dalam 6 giliran ke depan",
                    historyPoints = gameState.cashHistoryPoints,
                    medianFuturePoints = gameState.cashFanMedianPoints,
                    spread50Points = gameState.cashFanBand50Points,
                    spread90Points = gameState.cashFanBand90Points,
                    unitLabel = "juta RS"
                )

                CognitiveCalibrationCard(
                    brierScore = gameState.brierScore,
                    biases = gameState.detectedBiases,
                    completedJournals = gameState.completedJournals
                )

                EndTurnPrimaryButton(
                    turn = gameState.turn,
                    onAdvanceTurn = onAdvanceTurn
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CovenantWarningBanner(
    title: String,
    subtitle: String,
    originInfo: DataOriginInfo,
    onInspectOrigin: (DataOriginInfo) -> Unit
) {
    Surface(
        color = KapitalSemanticColors.yellowContainer,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, KapitalSemanticColors.yellow.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .combinedClickable(
                onClick = { onInspectOrigin(originInfo) },
                onLongClick = { onInspectOrigin(originInfo) }
            )
            .defaultMinSize(minHeight = 56.dp)
            .testTag("covenant_warning_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = "Peringatan Covenant",
                tint = KapitalSemanticColors.yellow,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = KapitalSemanticColors.yellow
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "Asal data covenant",
                tint = KapitalSemanticColors.yellow,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun MorningPaperPreviewCard(
    source: String,
    confidence: ConfidenceTier,
    headline: String,
    footer: String,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .testTag("morning_paper_card")
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Newspaper,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = source,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ConfidenceBadge(confidence = confidence)
            }

            Text(
                text = headline,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = NotoSerifFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = footer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Buka Koran Pagi",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = KapitalSemanticColors.blue
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        tint = KapitalSemanticColors.blue,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkasIndicatorsGrid(
    indicators: List<Indicator>,
    onInspectOrigin: (DataOriginInfo) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Indikator Utama",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Ketuk / tahan angka untuk Asal data",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val chunked = indicators.chunked(2)
        chunked.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { ind ->
                    UncertainValue(
                        label = ind.label,
                        valueText = ind.valueText,
                        deltaOrSubText = ind.deltaOrSubText,
                        tone = ind.tone,
                        dataDate = ind.dataDate,
                        source = ind.source,
                        confidence = ind.confidence,
                        originInfo = ind.originInfo,
                        onInspectOrigin = onInspectOrigin,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PendingDecisionsSection(
    decisions: List<Decision>,
    onDecisionClick: (Decision) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Menunggu keputusan",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Wajib Jurnal Keputusan / Respons Krisis",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        decisions.forEach { dec ->
            val badgeFg = semanticToneColor(dec.tone)
            val badgeBg = semanticToneContainerColor(dec.tone)

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .border(
                        width = 1.dp,
                        color = if (dec.tone == SemanticTone.DANGER_RED) badgeFg.copy(alpha = 0.55f) else KapitalSemanticColors.border,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { onDecisionClick(dec) }
                    .defaultMinSize(minHeight = 56.dp)
                    .testTag("decision_item_${dec.id}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (dec.isCompleted) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = "Selesai",
                                    tint = KapitalSemanticColors.green,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = dec.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = dec.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.border(1.dp, badgeFg.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    ) {
                        Text(
                            text = dec.badgeText,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = badgeFg,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CognitiveCalibrationCard(
    brierScore: Double,
    biases: List<String>,
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
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Kalibrasi Kognitif & Jurnal Hipotesis",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    color = KapitalSemanticColors.greenContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Brier Score: ${String.format("%.2f", brierScore)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = KapitalSemanticColors.green,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            biases.forEach { note ->
                Text(
                    text = "• $note",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (completedJournals.isNotEmpty()) {
                HorizontalDivider(color = KapitalSemanticColors.border, modifier = Modifier.padding(vertical = 2.dp))
                val latest = completedJournals.first()
                Text(
                    text = "Jurnal Terakhir: ${latest.decisionTitle} (Keyakinan ${latest.confidencePct}% · Evaluasi Giliran ${latest.evaluateAtTurn})",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                    color = KapitalSemanticColors.blue
                )
            }
        }
    }
}

@Composable
private fun EndTurnPrimaryButton(
    turn: Int,
    onAdvanceTurn: () -> Unit
) {
    Button(
        onClick = onAdvanceTurn,
        colors = ButtonDefaults.buttonColors(
            containerColor = KapitalSemanticColors.blue,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .testTag("end_turn_button")
    ) {
        Text(
            text = "Akhiri giliran",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
    }
}
