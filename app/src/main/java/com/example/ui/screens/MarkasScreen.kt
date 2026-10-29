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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    onOpenEventDialog: (String) -> Unit,
    onInspectOrigin: (DataOriginInfo) -> Unit,
    onAdvanceTurn: () -> Unit,
    onAdjustPrice: (Int) -> Unit,
    onShowLastReport: () -> Unit,
    onNewGame: () -> Unit,
    onOpenChronicle: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenMenu: () -> Unit = {},
    onDismissSaveNotice: () -> Unit = {}
) {
    val gameOver = gameState.gameOverReason
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
                    gameState.saveNotice?.let { SaveNoticeBanner(it, onDismissSaveNotice) }
                    if (gameOver != null) {
                        GameOverPanel(
                            reason = gameOver,
                            turn = gameState.turn,
                            dateText = gameState.dateText,
                            onNewGame = onNewGame,
                            onOpenChronicle = onOpenChronicle,
                            evaluationTitle = gameState.evaluationTitle,
                            evaluationRows = gameState.evaluationRows,
                            postMortem = gameState.postMortemLines
                        )
                    }

                    CovenantWarningBanner(
                        tone = gameState.covenantTone,
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

                    ExecutiveMemosCard(memos = gameState.memos)

                    PriceLeverCard(
                        priceAdjPct = gameState.priceAdjPct,
                        apCurrent = gameState.apCurrent,
                        enabled = gameOver == null,
                        onAdjustPrice = onAdjustPrice
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
                                onOpenEventDialog(decision.id)
                            } else {
                                onOpenJurnalKeputusan(decision)
                            }
                        }
                    )

                    FanChart(
                        title = "Proyeksi kas dan ketidakpastian",
                        subtitle = "Pita 50% dan 90% untuk 6 giliran ke depan · garis merah = kas nol",
                        historyPoints = gameState.cashHistoryPoints,
                        medianFuturePoints = gameState.cashFanMedianPoints,
                        spread50Points = gameState.cashFanBand50Points,
                        spread90Points = gameState.cashFanBand90Points,
                        unitLabel = "juta RS",
                        dangerLevel = 0f
                    )

                    CognitiveCalibrationCard(
                        evaluatedCount = gameState.evaluatedJournals,
                        brierScore = gameState.brierScore,
                        biases = gameState.detectedBiases,
                        completedJournals = gameState.completedJournals
                    )

                    EndTurnPrimaryButton(
                        enabled = gameOver == null,
                        gameState = gameState,
                        onAdvanceTurn = onAdvanceTurn,
                        onShowLastReport = onShowLastReport
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
                gameState.saveNotice?.let { SaveNoticeBanner(it, onDismissSaveNotice) }
                if (gameOver != null) {
                    GameOverPanel(
                        reason = gameOver,
                        turn = gameState.turn,
                        dateText = gameState.dateText,
                        onNewGame = onNewGame,
                        onOpenChronicle = onOpenChronicle,
                        evaluationTitle = gameState.evaluationTitle,
                        evaluationRows = gameState.evaluationRows,
                        postMortem = gameState.postMortemLines
                    )
                }

                CovenantWarningBanner(
                    tone = gameState.covenantTone,
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

                ExecutiveMemosCard(memos = gameState.memos)

                PendingDecisionsSection(
                    decisions = gameState.pendingDecisions,
                    onDecisionClick = { decision ->
                        if (decision.opensEventDialog) {
                            onOpenEventDialog(decision.id)
                        } else {
                            onOpenJurnalKeputusan(decision)
                        }
                    }
                )

                PriceLeverCard(
                    priceAdjPct = gameState.priceAdjPct,
                    apCurrent = gameState.apCurrent,
                    enabled = gameOver == null,
                    onAdjustPrice = onAdjustPrice
                )

                FanChart(
                    title = "Proyeksi kas dan ketidakpastian",
                    subtitle = "Pita 50% dan 90% untuk 6 giliran ke depan · garis merah = kas nol",
                    historyPoints = gameState.cashHistoryPoints,
                    medianFuturePoints = gameState.cashFanMedianPoints,
                    spread50Points = gameState.cashFanBand50Points,
                    spread90Points = gameState.cashFanBand90Points,
                    unitLabel = "juta RS",
                    dangerLevel = 0f
                )

                CognitiveCalibrationCard(
                    evaluatedCount = gameState.evaluatedJournals,
                    brierScore = gameState.brierScore,
                    biases = gameState.detectedBiases,
                    completedJournals = gameState.completedJournals
                )

                EndTurnPrimaryButton(
                    enabled = gameOver == null,
                    gameState = gameState,
                    onAdvanceTurn = onAdvanceTurn,
                    onShowLastReport = onShowLastReport
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CovenantWarningBanner(
    tone: SemanticTone,
    title: String,
    subtitle: String,
    originInfo: DataOriginInfo,
    onInspectOrigin: (DataOriginInfo) -> Unit
) {
    val fg = semanticToneColor(tone)
    Surface(
        color = semanticToneContainerColor(tone),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, fg.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
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
                tint = fg,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = fg
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
                tint = fg,
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
                    modifier = Modifier.weight(1f),
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
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
                text = "Indikator utama",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
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
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Keputusan besar wajib jurnal",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (decisions.isEmpty()) {
            Text(
                text = "Tidak ada keputusan mendesak. Gunakan AP untuk audit, tinjauan kontrak, atau periksa silang berita.",
                style = MaterialTheme.typography.bodySmall,
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
    evaluatedCount: Int,
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
                    text = "Kalibrasi dan jurnal",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                val brierTone = when {
                    brierScore <= 0.15 -> SemanticTone.GOOD_GREEN
                    brierScore <= 0.25 -> SemanticTone.WARNING_YELLOW
                    else -> SemanticTone.DANGER_RED
                }
                Surface(
                    color = semanticToneContainerColor(brierTone),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Brier ${String.format(java.util.Locale.forLanguageTag("id-ID"), "%.2f", brierScore)} · $evaluatedCount dinilai",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = semanticToneColor(brierTone),
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
    enabled: Boolean,
    gameState: GameState,
    onAdvanceTurn: () -> Unit,
    onShowLastReport: () -> Unit
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    if (confirming) {
        EndTurnConfirmDialog(
            gameState = gameState,
            onConfirm = { confirming = false; onAdvanceTurn() },
            onCancel = { confirming = false }
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(
            onClick = { confirming = true },
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
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
        TextButton(
            onClick = onShowLastReport,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text("Lihat laporan bulan lalu")
        }
    }
}

@Composable
private fun EndTurnConfirmDialog(
    gameState: GameState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val pending = gameState.pendingDecisions.filter { !it.isCompleted }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Akhiri giliran ${gameState.dateText}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Setelah giliran diakhiri, bulan berjalan: pendapatan, biaya, bunga, dan kejadian acak diproses. Keputusan tidak bisa ditarik kembali.",
                    style = MaterialTheme.typography.bodyMedium
                )
                HorizontalDivider()
                Text("AP tersisa: ${gameState.apCurrent} dari ${gameState.apMax}" +
                    if (gameState.apCurrent > 0) " (AP tidak dibawa ke bulan depan)" else "",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text("Kas: ${gameState.markasIndicators.firstOrNull()?.valueText ?: "-"} · ${gameState.covenantWarningTitle}",
                    style = MaterialTheme.typography.bodySmall)
                if (pending.isNotEmpty()) {
                    Text("Keputusan belum diambil (${pending.size}):", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    pending.take(5).forEach { Text("• ${it.title}", style = MaterialTheme.typography.bodySmall) }
                    Text("Keputusan yang dibiarkan bisa menimbulkan akibat (mis. tenggat serikat).", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, modifier = Modifier.testTag("confirm_end_turn")) { Text("Ya, akhiri giliran") }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel, modifier = Modifier.testTag("cancel_end_turn")) { Text("Tidak, kembali") }
        }
    )
}

@Composable
private fun ExecutiveMemosCard(memos: List<MemoItem>) {
    if (memos.isEmpty()) return
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Memo eksekutif",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            memos.forEach { memo ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${memo.from} · ${memo.role}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "\u201C${memo.text}\u201D",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = NotoSerifFontFamily),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Text(
                text = "Memo mencerminkan kepentingan penulisnya. Cocokkan dengan data sebelum percaya.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PriceLeverCard(
    priceAdjPct: Int,
    apCurrent: Int,
    enabled: Boolean,
    onAdjustPrice: (Int) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Harga jual: ${if (priceAdjPct >= 0) "+" else ""}$priceAdjPct% dari harga pasar",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Harga naik = margin naik, pangsa turun. Pesaing menyamai potongan harga, tapi tidak ikut kenaikan. 1 AP per perubahan.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = { onAdjustPrice(-3) },
                enabled = enabled && apCurrent >= 1 && priceAdjPct > -15,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text("−3%") }
            OutlinedButton(
                onClick = { onAdjustPrice(3) },
                enabled = enabled && apCurrent >= 1 && priceAdjPct < 15,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text("+3%") }
        }
    }
}


/** Peringatan saat simpanan dimuat dari versi mesin lain atau sebagian tindakannya tak bisa diputar ulang. */
@Composable
private fun SaveNoticeBanner(text: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().testTag("save_notice")
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text, style = MaterialTheme.typography.bodySmall, color = KapitalSemanticColors.yellow)
            TextButton(onClick = onDismiss) { Text("Mengerti") }
        }
    }
}
