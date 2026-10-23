package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.components.Sparkline
import com.example.ui.components.semanticToneColor
import com.example.ui.theme.KapitalSemanticColors
import com.example.ui.theme.MonoFontFamily

@Composable
fun BursaScreen(
    gameState: GameState,
    onBuy: (String, Int) -> Unit,
    onSell: (String, Int) -> Unit,
    onInspectOrigin: (DataOriginInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val b = gameState.bourse
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = b.stocks.firstOrNull { it.id == selectedId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (selected != null) {
            StockDetail(selected, gameState.actionPoints, onBuy, onSell, onBack = { selectedId = null }, onInspectOrigin = onInspectOrigin)
        } else {
            BourseOverview(b, gameState.actionPoints) { selectedId = it }
        }
    }
}

@Composable
private fun BoxCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(8.dp))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun BourseOverview(b: BourseState, ap: Int, onOpen: (String) -> Unit) {
    Text("Bursa Efek Sundara", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    BoxCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Indeks BES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(b.indexText, style = MaterialTheme.typography.headlineSmall, fontFamily = MonoFontFamily)
                Text(b.indexChangeText, color = semanticToneColor(b.indexTone), style = MaterialTheme.typography.bodySmall)
            }
            if (b.indexSpark.size >= 2) Sparkline(b.indexSpark, b.indexTone, Modifier.width(120.dp).height(44.dp))
        }
    }
    BoxCard {
        Text("Portofolio Anda", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        KV("Nilai pasar", b.portfolioText)
        KV("Untung/rugi belum direalisasi", b.unrealizedText)
        KV("Dividen diterima (total)", b.dividendText)
        KV("Kas perusahaan", b.cashText)
        Text("Setiap jual atau beli memakai 1 poin aksi (tersisa $ap). Dana berasal dari kas perusahaan, bukan kantong pribadi, jadi kas yang terlalu tipis bisa memicu krisis likuiditas.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (b.headlines.isNotEmpty()) {
        BoxCard {
            Text("Kabar bursa bulan ini", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            b.headlines.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
    Text("Emiten tercatat (ketuk untuk laporan keuangan)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    b.stocks.forEach { s ->
        Surface(
            onClick = { onOpen(s.id) },
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(8.dp)).testTag("stock_${s.id}")
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${s.ticker} · ${s.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(s.sector + " · " + s.holdingText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (s.spark.size >= 2) Sparkline(s.spark, s.changeTone, Modifier.width(64.dp).height(30.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(s.priceText, fontFamily = MonoFontFamily, style = MaterialTheme.typography.bodyMedium)
                    Text(s.changeText, color = semanticToneColor(s.changeTone), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun KV(k: String, v: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(v, style = MaterialTheme.typography.bodySmall, fontFamily = MonoFontFamily, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StockDetail(
    s: StockItem,
    ap: Int,
    onBuy: (String, Int) -> Unit,
    onSell: (String, Int) -> Unit,
    onBack: () -> Unit,
    onInspectOrigin: (DataOriginInfo) -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var lots by rememberSaveable { mutableStateOf(1) }
    TextButton(onClick = onBack) { Text("← Semua emiten") }
    Text("${s.ticker} · ${s.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(s.sector + " · " + s.blurb, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    BoxCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(s.priceText, style = MaterialTheme.typography.headlineSmall, fontFamily = MonoFontFamily)
                Text(s.changeText + " bulan ini", color = semanticToneColor(s.changeTone), style = MaterialTheme.typography.bodySmall)
            }
            if (s.spark.size >= 2) Sparkline(s.spark, s.changeTone, Modifier.width(140.dp).height(50.dp))
        }
        KV("Kepemilikan Anda", s.holdingText)
        if (s.holdingLots > 0) KV("Untung/rugi belum direalisasi", s.unrealizedText)
        KV("Harga per lot", s.lotCostText)
        Text("Risiko: " + s.riskNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    BoxCard {
        Text("Transaksi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { lots = (lots - 1).coerceAtLeast(1) }) { Text("−") }
            Text("$lots lot", fontFamily = MonoFontFamily)
            OutlinedButton(onClick = { lots = (lots + 1).coerceAtMost(50) }) { Text("+") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onBuy(s.id, lots) },
                enabled = s.canBuy && ap >= 1,
                modifier = Modifier.weight(1f).testTag("buy_${s.id}")
            ) { Text("Beli $lots lot") }
            OutlinedButton(
                onClick = { onSell(s.id, lots.coerceAtMost(s.holdingLots)) },
                enabled = s.holdingLots > 0 && ap >= 1,
                modifier = Modifier.weight(1f).testTag("sell_${s.id}")
            ) { Text("Jual ${lots.coerceAtMost(s.holdingLots.coerceAtLeast(1))} lot") }
        }
        if (!s.canBuy) Text(s.buyBlockReason, style = MaterialTheme.typography.bodySmall, color = KapitalSemanticColors.red)
        else if (ap < 1) Text("Poin aksi habis bulan ini.", style = MaterialTheme.typography.bodySmall, color = KapitalSemanticColors.red)
        Text("Biaya broker 0,15% beli dan 0,25% jual. Harga yang berlaku adalah harga penutupan bulan ini; jika Anda baru membeli lalu mengakhiri giliran, harga bisa sudah berubah.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(s.reportNote, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TabRow(selectedTabIndex = tab) {
        listOf("Laba rugi", "Neraca", "Arus kas", "Rasio").forEachIndexed { i, t ->
            Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
        }
    }
    when (tab) {
        0 -> StatementCard("Laporan laba rugi · ${s.reportLabel} (jt RS)", s.income, onInspectOrigin)
        1 -> StatementCard("Neraca akhir periode (jt RS)", s.balance, onInspectOrigin)
        2 -> StatementCard("Laporan arus kas · ${s.reportLabel} (jt RS)", s.cashFlow, onInspectOrigin)
        else -> BoxCard {
            Text("Rasio dan valuasi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            s.ratios.forEach { KV(it.first, it.second) }
        }
    }
}

@Composable
private fun StatementCard(title: String, rows: List<FinancialRowItem>, onInspectOrigin: (DataOriginInfo) -> Unit) {
    BoxCard {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (rows.isEmpty()) {
            Text("Belum ada laporan resmi. Emiten merilis laporan tiap akhir triwulan (Maret, Juni, September, Desember).",
                style = MaterialTheme.typography.bodySmall)
        } else {
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                Text("Periode ini", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(90.dp))
                Text("Sebelumnya", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(90.dp))
            }
            rows.forEach { r ->
                Column(Modifier.fillMaxWidth().background(if (r.isHeaderOrTotal) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(r.label, style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (r.isHeaderOrTotal) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                        Text(r.currentVal, fontFamily = MonoFontFamily, style = MaterialTheme.typography.bodySmall,
                            color = semanticToneColor(r.tone), modifier = Modifier.width(90.dp))
                        Text(r.priorVal, fontFamily = MonoFontFamily, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(90.dp))
                    }
                    if (r.note.isNotEmpty()) Text(r.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
