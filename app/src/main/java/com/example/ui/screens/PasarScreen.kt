package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    onAdjustPrice: (Int) -> Unit = {},
    onHedgeCotton: () -> Unit = {},
    onHedgeFx: () -> Unit = {},
    onAcquireRival: (String) -> Unit = {},
    onBuyStock: (String, Int) -> Unit = { _, _ -> },
    onSellStock: (String, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val subTabs = listOf("Harga, Pesaing AI & Komoditas", "Bursa Efek Sundara (7 Emiten)")
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            divider = { HorizontalDivider(color = KapitalSemanticColors.border) }
        ) {
            subTabs.forEachIndexed { idx, label ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("pasar_subtab_$idx"),
                    text = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (selectedTab == idx) KapitalSemanticColors.blue else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> KomoditasDanPersainganTab(
                gameState = gameState,
                onInspectOrigin = onInspectOrigin,
                onAdjustPrice = onAdjustPrice,
                onHedgeCotton = onHedgeCotton,
                onHedgeFx = onHedgeFx,
                onAcquireRival = onAcquireRival
            )
            1 -> BursaSahamTab(
                gameState = gameState,
                onBuyStock = onBuyStock,
                onSellStock = onSellStock
            )
        }
    }
}

@Composable
private fun KomoditasDanPersainganTab(
    gameState: GameState,
    onInspectOrigin: (DataOriginInfo) -> Unit,
    onAdjustPrice: (Int) -> Unit,
    onHedgeCotton: () -> Unit,
    onHedgeFx: () -> Unit,
    onAcquireRival: (String) -> Unit
) {
    var selectedInstrumentId by remember {
        mutableStateOf(gameState.marketInstruments.firstOrNull()?.id ?: "mkt_kapas")
    }
    val selectedInstrument = gameState.marketInstruments.firstOrNull { it.id == selectedInstrumentId }
        ?: gameState.marketInstruments.first()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
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
                    modifier = Modifier.weight(1.08f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PricingAndRivalsCard(
                        gameState = gameState,
                        onAdjustPrice = onAdjustPrice,
                        onAcquireRival = onAcquireRival
                    )
                }

                Column(
                    modifier = Modifier.weight(0.92f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Daftar Komoditas & Kurs RS/A$ (Ketuk untuk FanChart; tahan untuk Asal data)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    gameState.marketInstruments.forEach { inst ->
                        MarketInstrumentRowCard(
                            instrument = inst,
                            isSelected = inst.id == selectedInstrument.id,
                            onSelect = { selectedInstrumentId = inst.id },
                            onAction = {
                                if (inst.id == "mkt_kapas") onHedgeCotton()
                                else if (inst.id == "mkt_kurs") onHedgeFx()
                            },
                            onInspectOrigin = onInspectOrigin
                        )
                    }

                    FanChart(
                        title = "FanChart Proyeksi: ${selectedInstrument.name}",
                        subtitle = "Pita ketidakpastian 50% & 90% dalam 6 giliran ke depan",
                        historyPoints = selectedInstrument.sparklineHistory,
                        medianFuturePoints = selectedInstrument.fanProjectionMedian,
                        spread50Points = selectedInstrument.fanProjectionSpread.map { it * 0.5f },
                        spread90Points = selectedInstrument.fanProjectionSpread,
                        unitLabel = selectedInstrument.ticker
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
                PricingAndRivalsCard(
                    gameState = gameState,
                    onAdjustPrice = onAdjustPrice,
                    onAcquireRival = onAcquireRival
                )

                Text(
                    text = "Daftar Komoditas & Kurs RS/A$",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                gameState.marketInstruments.forEach { inst ->
                    MarketInstrumentRowCard(
                        instrument = inst,
                        isSelected = inst.id == selectedInstrument.id,
                        onSelect = { selectedInstrumentId = inst.id },
                        onAction = {
                            if (inst.id == "mkt_kapas") onHedgeCotton()
                            else if (inst.id == "mkt_kurs") onHedgeFx()
                        },
                        onInspectOrigin = onInspectOrigin
                    )
                }

                FanChart(
                    title = "FanChart Proyeksi: ${selectedInstrument.name}",
                    subtitle = "Pita ketidakpastian 50% & 90% dalam 6 giliran ke depan",
                    historyPoints = selectedInstrument.sparklineHistory,
                    medianFuturePoints = selectedInstrument.fanProjectionMedian,
                    spread50Points = selectedInstrument.fanProjectionSpread.map { it * 0.5f },
                    spread90Points = selectedInstrument.fanProjectionSpread,
                    unitLabel = selectedInstrument.ticker
                )
            }
        }
    }
}

@Composable
private fun PricingAndRivalsCard(
    gameState: GameState,
    onAdjustPrice: (Int) -> Unit,
    onAcquireRival: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Kebijakan Harga Jual & Pesaing AI yang Belajar", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Harga Anda: ${if (gameState.priceAdjustmentPct >= 0) "+${gameState.priceAdjustmentPct}%" else "${gameState.priceAdjustmentPct}%"} · Pangsa ${gameState.playerShareText}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold),
                    color = KapitalSemanticColors.blue
                )
            }

            if (gameState.rivalLearningProfile.isNotBlank()) {
                Surface(
                    color = KapitalSemanticColors.yellowContainer,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Model Pembelajaran AI Pesaing (Evaluasi 12 Giliran): ${gameState.rivalLearningProfile}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold),
                        color = KapitalSemanticColors.yellow,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Text(
                text = "Pesaing mengamati harga, leverage utang, dan ketergantungan pemasok Anda. Mereka memberi sinyal peringatan dini 2 giliran sebelum meluncurkan perang harga, borongan pasokan, atau lobi regulasi. Anda juga dapat mengakuisisi (M&A) pesaing yang sedang tertekan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(-5 to "−5%", -2 to "−2%", 2 to "+2%", 5 to "+5%").forEach { (d, label) ->
                    OutlinedButton(
                        onClick = { onAdjustPrice(d) },
                        enabled = gameState.apCurrent >= 1,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("$label (1 AP)", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (gameState.rivals.isNotEmpty()) {
                HorizontalDivider(color = KapitalSemanticColors.border)
                gameState.rivals.forEach { rv ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, KapitalSemanticColors.border.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1.3f)) {
                                Text(rv.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text("${rv.archetype} · ${rv.statusText}", style = MaterialTheme.typography.labelSmall, color = semanticToneColor(rv.tone))
                            }
                            Text(
                                text = "Harga ${rv.priceRelText} · Pangsa ${rv.shareText}",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                                color = semanticToneColor(rv.tone)
                            )
                        }

                        if (rv.warningSignalText.isNotBlank()) {
                            Surface(
                                color = KapitalSemanticColors.redContainer,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = rv.warningSignalText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = KapitalSemanticColors.red,
                                    modifier = Modifier.padding(6.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Akuisisi M&A: ${rv.acquireCostText}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = { onAcquireRival(rv.id) },
                                enabled = rv.canAcquire,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.heightIn(min = 40.dp)
                            ) {
                                Text(if (rv.canAcquire) "Akuisisi (3 AP)" else "Belum Tersedia", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BursaSahamTab(
    gameState: GameState,
    onBuyStock: (String, Int) -> Unit,
    onSellStock: (String, Int) -> Unit
) {
    var reportStock by remember { mutableStateOf<StockCard?>(null) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Indeks Bursa Efek Sundara (BES)", style = MaterialTheme.typography.titleMedium)
                        Text("${gameState.bourseIndexText} (${gameState.bourseChangeText} bln ini)",
                            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold),
                            color = KapitalSemanticColors.blue)
                    }
                    if (gameState.bourseSparkline.size >= 2) {
                        Sparkline(points = gameState.bourseSparkline, tone = SemanticTone.PRIMARY_BLUE, modifier = Modifier.width(110.dp).height(38.dp))
                    }
                }
                HorizontalDivider(color = KapitalSemanticColors.border)
                Text("Nilai Portofolio Saham: ${gameState.portfolioValueText} · Dividen diterima: ${gameState.dividendsTotalText}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold))
                Text(gameState.portfolioPnlText, style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Aturan bursa: 1 lot = 100.000 lembar (nilai 0,1 × harga dalam jt RS). Biaya pialang 0,15% saat beli & jual + pajak 0,1% saat jual. Emiten merilis laporan keuangan setiap akhir triwulan.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (gameState.holdings.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Portofolio Kepemilikan Anda", style = MaterialTheme.typography.titleMedium)
                    gameState.holdings.forEach { h ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${h.ticker} · ${h.lots} lot (beli rata-rata ${h.avgPriceText})", style = MaterialTheme.typography.bodySmall)
                            Text("${h.valueText} · ${h.pnlText}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold),
                                color = semanticToneColor(h.pnlTone))
                        }
                    }
                }
            }
        }

        Text("Emiten Tercatat di BES (7 Perusahaan)", style = MaterialTheme.typography.titleLarge)
        gameState.stocks.forEach { s ->
            val col = semanticToneColor(s.tone)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1.2f)) {
                            Text("${s.ticker} · ${s.sector.uppercase()}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold),
                                color = KapitalSemanticColors.blue)
                            Text(s.name, style = MaterialTheme.typography.titleLarge)
                        }
                        if (s.sparkline.size >= 2) {
                            Sparkline(points = s.sparkline, tone = s.tone, modifier = Modifier.width(80.dp).height(32.dp))
                        }
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(0.9f)) {
                            Text(s.priceText, style = MaterialTheme.typography.titleLarge.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold))
                            Text(s.changePctText, style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.SemiBold), color = col)
                        }
                    }
                    Text(s.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Risiko: ${s.riskNote}", style = MaterialTheme.typography.labelSmall, color = KapitalSemanticColors.yellow)

                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("PER ${s.perText} · PBV ${s.pbvText} · Div. Yield ${s.divYieldText} · ROE ${s.roeText} · Utang/EBITDA ${s.debtEbitdaText} · Kap. Pasar ${s.marketCapText}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily))
                            Text("Rilis terakhir (${s.lastQuarterLabel}): ${s.surpriseNote}", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (s.heldLots > 0) {
                        Text("Dimiliki: ${s.heldLots} lot (${s.heldValueText}) · Harga beli rata-rata ${s.avgCostText} · L/R belum realisasi ${s.unrealizedText}",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold),
                            color = semanticToneColor(s.unrealizedTone))
                    } else {
                        Text("Harga 1 lot: ${s.lotCostText}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily))
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { onBuyStock(s.id, 1) }, enabled = s.canBuy1,
                            colors = ButtonDefaults.buttonColors(containerColor = KapitalSemanticColors.blue, contentColor = Color.White),
                            shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) { Text("Beli 1 lot", style = MaterialTheme.typography.labelSmall) }
                        Button(
                            onClick = { onBuyStock(s.id, 10) }, enabled = s.canBuy10,
                            colors = ButtonDefaults.buttonColors(containerColor = KapitalSemanticColors.blue, contentColor = Color.White),
                            shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) { Text("Beli 10 lot", style = MaterialTheme.typography.labelSmall) }
                        if (s.heldLots > 0) {
                            OutlinedButton(
                                onClick = { onSellStock(s.id, 1) }, enabled = s.canSell1,
                                shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) { Text("Jual 1", style = MaterialTheme.typography.labelSmall) }
                            OutlinedButton(
                                onClick = { onSellStock(s.id, s.heldLots) }, enabled = s.canSellAll,
                                shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) { Text("Jual semua", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                    OutlinedButton(
                        onClick = { reportStock = s },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                    ) { Text("Lihat Laporan Keuangan Triwulanan ${s.ticker}") }
                }
            }
        }
    }

    reportStock?.let { st ->
        StockReportDialog(stock = st, onDismiss = { reportStock = null })
    }
}

@Composable
private fun StockReportDialog(stock: StockCard, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Laporan Keuangan ${stock.name} (${stock.ticker})") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Dalam juta Rial Sundara (jt RS) · PER ${stock.perText} · PBV ${stock.pbvText} · ROE ${stock.roeText}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily))
                if (stock.quarters.isEmpty()) {
                    Text("Laporan triwulan pertama akan terbit pada akhir triwulan berjalan (Maret / Juni / September / Desember).",
                        style = MaterialTheme.typography.bodySmall)
                } else {
                    stock.quarters.forEach { q ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(q.label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = KapitalSemanticColors.blue)
                                Text("LABA RUGI TRIWULAN", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                FinLine("Pendapatan", q.revenue)
                                FinLine("HPP", q.cogs)
                                FinLine("Beban Operasional", q.opex)
                                FinLine("Penyusutan", q.dep)
                                FinLine("Laba Usaha (EBIT)", q.ebit, bold = true)
                                FinLine("Beban Bunga", q.interest)
                                FinLine("Pajak", q.tax)
                                FinLine("Laba Bersih", q.ni, bold = true)
                                HorizontalDivider(color = KapitalSemanticColors.border)
                                Text("NERACA AKHIR TRIWULAN", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                FinLine("Kas", q.cashEnd)
                                FinLine("Piutang Usaha", q.ar)
                                FinLine("Persediaan", q.inv)
                                FinLine("Aset Tetap Bersih", q.fa)
                                FinLine("Utang Dagang", q.ap)
                                FinLine("Utang Bank", q.debt)
                                FinLine("Ekuitas", q.equity, bold = true)
                                HorizontalDivider(color = KapitalSemanticColors.border)
                                Text("ARUS KAS TRIWULAN", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                FinLine("Arus Kas Operasi (CFO)", q.cfo, bold = true)
                                FinLine("Belanja Modal (CapEx)", q.capex)
                                FinLine("Perubahan Utang Bank", q.dDebt)
                                FinLine("Dividen Dibayar", q.dividend)
                                FinLine("Kas Awal → Kas Akhir", "${q.cashStart} → ${q.cashEnd}", bold = true)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

@Composable
private fun FinLine(label: String, value: String, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = if (bold) MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MarketInstrumentRowCard(
    instrument: MarketInstrument,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onAction: () -> Unit,
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

            if (instrument.actionLabel != null) {
                OutlinedButton(
                    onClick = onAction,
                    enabled = instrument.actionEnabled,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                ) {
                    Text(instrument.actionLabel)
                }
            }
        }
    }
}
