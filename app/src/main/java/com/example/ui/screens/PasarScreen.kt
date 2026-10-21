package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.components.FanChart
import com.example.ui.components.Sparkline
import com.example.ui.components.semanticToneColor
import com.example.ui.components.semanticToneContainerColor
import com.example.ui.theme.KapitalSemanticColors
import com.example.ui.theme.MonoFontFamily

@Composable
fun PasarScreen(
    gameState: GameState,
    onInspectOrigin: (DataOriginInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedInstrumentId by rememberSaveable {
        mutableStateOf(gameState.marketInstruments.firstOrNull()?.id ?: "mkt_kapas")
    }
    val selectedInstrument = gameState.marketInstruments.firstOrNull { it.id == selectedInstrumentId }
        ?: gameState.marketInstruments.first()

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
                    modifier = Modifier.weight(1.1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Komoditas, kurs, dan saham (ketuk untuk grafik, tahan untuk asal data)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    gameState.marketInstruments.forEach { inst ->
                        MarketInstrumentRowCard(
                            instrument = inst,
                            isSelected = inst.id == selectedInstrument.id,
                            onSelect = { selectedInstrumentId = inst.id },
                            onInspectOrigin = onInspectOrigin
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(0.9f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FanChart(
                        title = "FanChart Proyeksi: ${selectedInstrument.name}",
                        subtitle = "Buku pesanan 207.000 agen · Pita ketidakpastian 50% & 90%",
                        historyPoints = selectedInstrument.sparklineHistory,
                        medianFuturePoints = selectedInstrument.fanProjectionMedian,
                        spread50Points = selectedInstrument.fanProjectionSpread.map { it * 0.5f },
                        spread90Points = selectedInstrument.fanProjectionSpread,
                        unitLabel = selectedInstrument.ticker
                    )

                    ForensicMarketPanelCard(
                        selectedInstrument = selectedInstrument,
                        onInspectOrigin = onInspectOrigin
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
                Text(
                    text = "Daftar Komoditas, Kurs RS/A$, & Saham",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                gameState.marketInstruments.forEach { inst ->
                    MarketInstrumentRowCard(
                        instrument = inst,
                        isSelected = inst.id == selectedInstrument.id,
                        onSelect = { selectedInstrumentId = inst.id },
                        onInspectOrigin = onInspectOrigin
                    )
                }

                FanChart(
                    title = "FanChart Proyeksi: ${selectedInstrument.name}",
                    subtitle = "Buku pesanan 207.000 agen · Pita ketidakpastian 50% & 90%",
                    historyPoints = selectedInstrument.sparklineHistory,
                    medianFuturePoints = selectedInstrument.fanProjectionMedian,
                    spread50Points = selectedInstrument.fanProjectionSpread.map { it * 0.5f },
                    spread90Points = selectedInstrument.fanProjectionSpread,
                    unitLabel = selectedInstrument.ticker
                )

                ForensicMarketPanelCard(
                    selectedInstrument = selectedInstrument,
                    onInspectOrigin = onInspectOrigin
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MarketInstrumentRowCard(
    instrument: MarketInstrument,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onInspectOrigin: (DataOriginInfo) -> Unit
) {
    val toneColor = semanticToneColor(instrument.tone)
    val borderColor = if (isSelected) KapitalSemanticColors.blue else KapitalSemanticColors.border

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(6.dp))
            .combinedClickable(
                onClick = onSelect,
                onLongClick = { onInspectOrigin(instrument.originInfo) }
            )
            .defaultMinSize(minHeight = 64.dp)
            .testTag("market_instrument_${instrument.id}")
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
                Column(modifier = Modifier.weight(1.2f)) {
                    Text(
                        text = "${instrument.category} · ${instrument.ticker}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = instrument.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Grafik garis kecil (Sparkline)
                Sparkline(
                    points = instrument.sparklineHistory,
                    tone = instrument.tone,
                    modifier = Modifier
                        .width(84.dp)
                        .height(34.dp)
                        .padding(horizontal = 6.dp)
                )

                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(0.9f)
                ) {
                    Text(
                        text = instrument.priceText,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = instrument.changeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = toneColor
                    )
                }
            }

            if (instrument.forensicMScoreNote != null) {
                Surface(
                    color = semanticToneContainerColor(instrument.tone),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = instrument.forensicMScoreNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = toneColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ForensicMarketPanelCard(
    selectedInstrument: MarketInstrument,
    onInspectOrigin: (DataOriginInfo) -> Unit
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
                    text = "Rincian Pasar & Akuntansi Forensik",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(
                    onClick = { onInspectOrigin(selectedInstrument.originInfo) },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Asal data")
                }
            }

            Text(
                text = "${selectedInstrument.name} (${selectedInstrument.ticker}) — ${selectedInstrument.priceText}",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = selectedInstrument.originInfo.calculationMethod,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = selectedInstrument.originInfo.uncertaintyAndBiasNote,
                style = MaterialTheme.typography.bodySmall,
                color = KapitalSemanticColors.blue
            )
        }
    }
}
