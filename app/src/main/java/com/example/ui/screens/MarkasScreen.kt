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
    onOpenEventDialog: (String) -> Unit,
    onRejectProposal: (String) -> Unit,
    onEquityCure: () -> Unit,
    onNewGame: () -> Unit,
    onInspectOrigin: (DataOriginInfo) -> Unit,
    onAdvanceTurn: () -> Unit,
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
                    modifier = Modifier.weight(1.05f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (gameState.gameOverReason != null) {
                        GameOverBanner(
                            reason = gameState.gameOverReason,
                            isVictory = gameState.isVictory,
                            evaluation = gameState.campaignEvaluation,
                            onNewGame = onNewGame
                        )
                    }

                    ScenarioDifficultyBanner(gameState = gameState)

                    CovenantWarningBanner(
                        title = gameState.covenantWarningTitle,
                        subtitle = gameState.covenantWarningSubtitle,
                        tone = gameState.covenantTone,
                        canEquityCure = gameState.canEquityCure,
                        equityCureAmountText = gameState.equityCureAmountText,
                        onEquityCure = onEquityCure,
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

                    if (gameState.memos.isNotEmpty()) {
                        MemosSection(memos = gameState.memos)
                    }
                }

                Column(
                    modifier = Modifier.weight(0.95f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PendingDecisionsSection(
                        decisions = gameState.pendingDecisions,
                        scheduledChainCount = gameState.scheduledChainCount,
                        onDecisionClick = { decision ->
                            if (decision.opensEventDialog) {
                                onOpenEventDialog(decision.id)
                            } else {
                                onOpenJurnalKeputusan(decision)
                            }
                        },
                        onReject = onRejectProposal
                    )

                    FanChart(
                        title = "Proyeksi Kas & Ketidakpastian (FanChart)",
                        subtitle = "Pita keyakinan 50% & 90% dalam 6 giliran ke depan (Kesulitan: ${gameState.difficultyLabel})",
                        historyPoints = gameState.cashHistoryPoints,
                        medianFuturePoints = gameState.cashFanMedianPoints,
                        spread50Points = gameState.cashFanBand50Points,
                        spread90Points = gameState.cashFanBand90Points,
                        unitLabel = "juta RS"
                    )

                    if (gameState.campaignEvaluation != null) {
                        CampaignScoreCard(evaluation = gameState.campaignEvaluation)
                    }

                    CognitiveCalibrationCard(
                        brierScore = gameState.brierScore,
                        biases = gameState.detectedBiases,
                        completedJournals = gameState.completedJournals
                    )

                    EndTurnPrimaryButton(
                        turn = gameState.turn,
                        gameOver = gameState.gameOverReason != null,
                        onAdvanceTurn = if (gameState.gameOverReason != null) onNewGame else onAdvanceTurn
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
                if (gameState.gameOverReason != null) {
                    GameOverBanner(
                        reason = gameState.gameOverReason,
                        isVictory = gameState.isVictory,
                        evaluation = gameState.campaignEvaluation,
                        onNewGame = onNewGame
                    )
                }

                ScenarioDifficultyBanner(gameState = gameState)

                CovenantWarningBanner(
                    title = gameState.covenantWarningTitle,
                    subtitle = gameState.covenantWarningSubtitle,
                    tone = gameState.covenantTone,
                    canEquityCure = gameState.canEquityCure,
                    equityCureAmountText = gameState.equityCureAmountText,
                    onEquityCure = onEquityCure,
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

                if (gameState.memos.isNotEmpty()) {
                    MemosSection(memos = gameState.memos)
                }

                PendingDecisionsSection(
                    decisions = gameState.pendingDecisions,
                    scheduledChainCount = gameState.scheduledChainCount,
                    onDecisionClick = { decision ->
                        if (decision.opensEventDialog) {
                            onOpenEventDialog(decision.id)
                        } else {
                            onOpenJurnalKeputusan(decision)
                        }
                    },
                    onReject = onRejectProposal
                )

                FanChart(
                    title = "Proyeksi Kas & Ketidakpastian (FanChart)",
                    subtitle = "Pita keyakinan 50% & 90% dalam 6 giliran ke depan (Kesulitan: ${gameState.difficultyLabel})",
                    historyPoints = gameState.cashHistoryPoints,
                    medianFuturePoints = gameState.cashFanMedianPoints,
                    spread50Points = gameState.cashFanBand50Points,
                    spread90Points = gameState.cashFanBand90Points,
                    unitLabel = "juta RS"
                )

                if (gameState.campaignEvaluation != null) {
                    CampaignScoreCard(evaluation = gameState.campaignEvaluation)
                }

                CognitiveCalibrationCard(
                    brierScore = gameState.brierScore,
                    biases = gameState.detectedBiases,
                    completedJournals = gameState.completedJournals
                )

                EndTurnPrimaryButton(
                    turn = gameState.turn,
                    gameOver = gameState.gameOverReason != null,
                    onAdvanceTurn = if (gameState.gameOverReason != null) onNewGame else onAdvanceTurn
                )
            }
        }
    }
}

@Composable
private fun ScenarioDifficultyBanner(gameState: GameState) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Skenario: ${gameState.scenarioTitle.ifEmpty { "Warisan Bangkrut" }} · Seed #${gameState.seed}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${gameState.eraName} · PPh Efektif ${gameState.effectiveTaxRateText}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = if (gameState.isIronman) KapitalSemanticColors.redContainer else KapitalSemanticColors.blueContainer,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "Mode: ${gameState.difficultyLabel}${if (gameState.isIronman) " (Ironman)" else ""}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (gameState.isIronman) KapitalSemanticColors.red else KapitalSemanticColors.blue,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
    tone: SemanticTone,
    canEquityCure: Boolean,
    equityCureAmountText: String,
    onEquityCure: () -> Unit,
    originInfo: DataOriginInfo,
    onInspectOrigin: (DataOriginInfo) -> Unit
) {
    val fg = semanticToneColor(tone)
    val bg = semanticToneContainerColor(tone)
    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, fg.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .combinedClickable(
                onClick = { onInspectOrigin(originInfo) },
                onLongClick = { onInspectOrigin(originInfo) }
            )
            .testTag("covenant_warning_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = "Peringatan Covenant",
                        tint = fg,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
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
                }
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Lihat Asal Data Covenant",
                    tint = fg,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (canEquityCure) {
                OutlinedButton(
                    onClick = onEquityCure,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text("Suntikan Ekuitas Pemegang Saham / Equity Cure (+$equityCureAmountText · 2 AP)")
                }
            }
        }
    }
}

@Composable
private fun GameOverBanner(
    reason: String,
    isVictory: Boolean,
    evaluation: CampaignEvaluationUi?,
    onNewGame: () -> Unit
) {
    val fg = if (isVictory) KapitalSemanticColors.green else KapitalSemanticColors.red
    val bg = if (isVictory) KapitalSemanticColors.greenContainer else KapitalSemanticColors.redContainer
    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, fg, RoundedCornerShape(6.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (isVictory) "KAMPANYE 40 TAHUN BERHASIL DISELESAIKAN" else "PERMAINAN BERAKHIR",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold),
                color = fg
            )
            Text(reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            if (evaluation != null) {
                Text(
                    text = "Evaluasi Akhir: Kelas ${evaluation.grade} (${evaluation.totalScore}/100) · ${evaluation.archetypeTitle}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = fg
                )
            }
            Button(
                onClick = onNewGame,
                colors = ButtonDefaults.buttonColors(containerColor = fg, contentColor = Color.White),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text("Mulai Permainan Baru")
            }
        }
    }
}

@Composable
private fun CampaignScoreCard(evaluation: CampaignEvaluationUi) {
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Evaluasi Kepemimpinan 5 Komponen",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = evaluation.archetypeTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = KapitalSemanticColors.blue
                    )
                }
                Surface(
                    color = KapitalSemanticColors.blueContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Kelas ${evaluation.grade} · ${evaluation.totalScore}/100",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = KapitalSemanticColors.blue,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ScoreMetricPill("Nilai Riil (30%)", "${evaluation.nilaiPerusahaanScore}")
                ScoreMetricPill("Ketahanan (20%)", "${evaluation.ketahananScore}")
                ScoreMetricPill("Kalibrasi (20%)", "${evaluation.kualitasKeputusanScore}")
                ScoreMetricPill("Etika (15%)", "${evaluation.etikaReputasiScore}")
                ScoreMetricPill("Sosial (15%)", "${evaluation.dampakSosialScore}")
            }

            Text(
                text = "Kepercayaan Dewan: ${evaluation.boardTrust}/100 · Jejak Etika: ${evaluation.ethicsRiskLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScoreMetricPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MemosSection(memos: List<MemoItem>) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Memo Meja Direksi Bulan Ini (masing-masing punya kepentingan)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            memos.forEach { m ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "${m.from} · ${m.role}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = KapitalSemanticColors.blue
                    )
                    Text(
                        text = "“${m.text}”",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
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
                        text = source.uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ConfidenceBadge(confidence = confidence)
            }

            Text(
                text = headline,
                style = MaterialTheme.typography.headlineMedium.copy(
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
    val rows = indicators.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { ind ->
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
                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PendingDecisionsSection(
    decisions: List<Decision>,
    scheduledChainCount: Int,
    onDecisionClick: (Decision) -> Unit,
    onReject: (String) -> Unit
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
                Text(
                    text = "Menunggu keputusan",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (scheduledChainCount > 0) "${decisions.size} aktif · $scheduledChainCount rantai mengintai"
                    else "${decisions.count { !it.isCompleted }} aktif",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                    color = if (scheduledChainCount > 0) KapitalSemanticColors.yellow else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (decisions.isEmpty()) {
                Text(
                    text = "Semua agenda keputusan bulan ini sudah diselesaikan. Anda dapat meninjau laporan atau mengakhiri giliran.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            decisions.forEach { decision ->
                val toneColor = semanticToneColor(decision.tone)
                val containerBg = if (decision.isCompleted) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.surface
                }

                Surface(
                    color = containerBg,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
                        .clickable { onDecisionClick(decision) }
                        .defaultMinSize(minHeight = 56.dp)
                        .testTag("decision_card_${decision.id}")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (decision.isCompleted) {
                                        Icon(
                                            imageVector = Icons.Outlined.CheckCircle,
                                            contentDescription = "Selesai",
                                            tint = KapitalSemanticColors.green,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(
                                        text = decision.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = decision.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Surface(
                                color = semanticToneContainerColor(decision.tone),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.border(
                                    width = 1.dp,
                                    color = toneColor.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                            ) {
                                Text(
                                    text = decision.badgeText,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = MonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = toneColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (decision.canReject) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { onReject(decision.id) }) {
                                    Text("Tolak proposal (0 AP)", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
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
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Kalibrasi Kognitif & Jurnal Keputusan",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Skor Brier: ${String.format("%.2f", brierScore)}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = KapitalSemanticColors.green
                )
            }

            biases.forEach { bias ->
                Text(
                    text = "• $bias",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (completedJournals.isNotEmpty()) {
                HorizontalDivider(color = KapitalSemanticColors.border)
                val latest = completedJournals.last()
                Text(
                    text = "Jurnal Terakhir (Giliran ${latest.turnSubmitted}): ${latest.decisionTitle} — Keyakinan ${latest.confidencePct}% (Evaluasi pada Giliran ${latest.evaluateAtTurn})",
                    style = MaterialTheme.typography.labelSmall,
                    color = KapitalSemanticColors.blue
                )
            }
        }
    }
}

@Composable
private fun EndTurnPrimaryButton(
    turn: Int,
    gameOver: Boolean,
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
            text = if (gameOver) "Mulai Permainan Baru" else "Akhiri giliran (Lanjut ke Giliran ${turn + 1})",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
    }
}
