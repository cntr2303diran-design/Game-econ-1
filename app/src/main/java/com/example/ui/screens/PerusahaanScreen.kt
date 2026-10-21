package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
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
import com.example.ui.components.InspectableNumberText
import com.example.ui.components.semanticToneColor
import com.example.ui.components.semanticToneContainerColor
import com.example.ui.theme.KapitalSemanticColors
import com.example.ui.theme.MonoFontFamily

@Composable
fun PerusahaanScreen(
    gameState: GameState,
    onInspectOrigin: (DataOriginInfo) -> Unit,
    onChangeIncentiveScheme: (String) -> Unit,
    onExecuteContractAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val subTabs = listOf("Laporan keuangan", "Organisasi", "Proyek", "Kontrak")
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            edgePadding = 16.dp,
            divider = { HorizontalDivider(color = KapitalSemanticColors.border) }
        ) {
            subTabs.forEachIndexed { idx, label ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("perusahaan_subtab_$idx"),
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

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isLandscapeWide = maxWidth >= 700.dp
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (selectedTab) {
                    0 -> LaporanKeuanganTabContent(
                        gameState = gameState,
                        isLandscapeWide = isLandscapeWide,
                        onInspectOrigin = onInspectOrigin
                    )
                    1 -> OrganisasiTabContent(
                        gameState = gameState,
                        isLandscapeWide = isLandscapeWide,
                        onInspectOrigin = onInspectOrigin,
                        onChangeIncentiveScheme = onChangeIncentiveScheme
                    )
                    2 -> ProyekGanttTabContent(
                        tasks = gameState.ganttTasks
                    )
                    3 -> KontrakTabContent(
                        contracts = gameState.contracts,
                        apCurrent = gameState.apCurrent,
                        isLandscapeWide = isLandscapeWide,
                        onInspectOrigin = onInspectOrigin,
                        onExecuteContractAction = onExecuteContractAction
                    )
                }
            }
        }
    }
}

@Composable
private fun LaporanKeuanganTabContent(
    gameState: GameState,
    isLandscapeWide: Boolean,
    onInspectOrigin: (DataOriginInfo) -> Unit
) {
    // Jebakan Akrual Callout
    Surface(
        color = KapitalSemanticColors.yellowContainer,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, KapitalSemanticColors.yellow.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = KapitalSemanticColors.yellow,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Peringatan Jebakan Akrual: Laba Bersih positif (+710 jt RS), namun Arus Kas Operasi negatif (-780 jt RS) karena penjualan kredit tertahan di piutang ritel. Ketuk angka mana pun untuk melihat Asal data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    if (isLandscapeWide) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                FinancialStatementTableCard(
                    title = "1. Laporan Laba Rugi (Akrual)",
                    subtitle = "Dalam juta Rupiah Sundara (jt RS) · Maret 2033",
                    rows = gameState.incomeStatementRows,
                    onInspectOrigin = onInspectOrigin
                )
                FinancialStatementTableCard(
                    title = "3. Laporan Arus Kas (Riil)",
                    subtitle = "Rekonsiliasi Laba Bersih terhadap Kas Bank",
                    rows = gameState.cashFlowRows,
                    onInspectOrigin = onInspectOrigin
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                FinancialStatementTableCard(
                    title = "2. Neraca (Stock-Flow Consistent)",
                    subtitle = "Aset == Liabilitas + Ekuitas (118.560 jt RS)",
                    rows = gameState.balanceSheetRows,
                    onInspectOrigin = onInspectOrigin
                )
            }
        }
    } else {
        FinancialStatementTableCard(
            title = "1. Laporan Laba Rugi (Akrual)",
            subtitle = "Dalam juta Rupiah Sundara (jt RS) · Maret 2033",
            rows = gameState.incomeStatementRows,
            onInspectOrigin = onInspectOrigin
        )
        FinancialStatementTableCard(
            title = "2. Neraca (Stock-Flow Consistent)",
            subtitle = "Aset == Liabilitas + Ekuitas (118.560 jt RS)",
            rows = gameState.balanceSheetRows,
            onInspectOrigin = onInspectOrigin
        )
        FinancialStatementTableCard(
            title = "3. Laporan Arus Kas (Riil)",
            subtitle = "Rekonsiliasi Laba Bersih terhadap Kas Bank",
            rows = gameState.cashFlowRows,
            onInspectOrigin = onInspectOrigin
        )
    }
}

@Composable
private fun FinancialStatementTableCard(
    title: String,
    subtitle: String,
    rows: List<FinancialRowItem>,
    onInspectOrigin: (DataOriginInfo) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Pos Akuntansi",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1.3f)
                )
                Text(
                    text = "Maret 2033",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Bulan Lalu",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            rows.forEach { row ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (row.isHeaderOrTotal) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            else Color.Transparent
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = row.label,
                                style = if (row.isHeaderOrTotal) MaterialTheme.typography.titleMedium
                                else MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = row.note,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (row.tone == SemanticTone.NEUTRAL_GRAY) MaterialTheme.colorScheme.onSurfaceVariant
                                else semanticToneColor(row.tone)
                            )
                        }
                        InspectableNumberText(
                            text = row.currentVal,
                            originInfo = row.originInfo.copy(
                                title = row.label,
                                displayedValue = "${row.currentVal} (Sebelumnya: ${row.priorVal})"
                            ),
                            onInspectOrigin = onInspectOrigin,
                            tone = row.tone,
                            isBold = row.isHeaderOrTotal
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        InspectableNumberText(
                            text = row.priorVal,
                            originInfo = row.originInfo.copy(
                                title = "${row.label} (Periode Lalu)",
                                displayedValue = row.priorVal
                            ),
                            onInspectOrigin = onInspectOrigin,
                            tone = SemanticTone.NEUTRAL_GRAY,
                            isBold = false
                        )
                    }
                    HorizontalDivider(color = KapitalSemanticColors.border.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OrganisasiTabContent(
    gameState: GameState,
    isLandscapeWide: Boolean,
    onInspectOrigin: (DataOriginInfo) -> Unit,
    onChangeIncentiveScheme: (String) -> Unit
) {
    // Panel Desain Insentif Eksekutif (Fitur PDF Bagian 12)
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
            Text(
                text = "Desain Insentif Eksekutif & Tata Kelola (1 AP)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Skema aktif: ${gameState.activeIncentiveScheme}. Di KAPITAL, bawahan memiliki agenda pribadi (U(a) = Σ w_k G_k(a) - λ P C) dan tidak memiliki bar loyalitas pasti.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val schemes = listOf(
                "Bonus berbasis omzet (Rawan obral kredit)",
                "Bonus berbasis kas tertagih + clawback 3 tahun",
                "KPI penghematan biaya & audit perawatan"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                schemes.forEach { scheme ->
                    val selected = gameState.activeIncentiveScheme == scheme
                    FilterChip(
                        selected = selected,
                        onClick = { onChangeIncentiveScheme(scheme) },
                        label = { Text(scheme, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = KapitalSemanticColors.blueContainer,
                            selectedLabelColor = KapitalSemanticColors.blue
                        ),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    )
                }
            }
        }
    }

    val execPairs = if (isLandscapeWide) gameState.executives.chunked(2) else gameState.executives.chunked(1)
    execPairs.forEach { rowExecs ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            rowExecs.forEach { exec ->
                ExecutiveProfileCard(
                    executive = exec,
                    onInspectOrigin = onInspectOrigin,
                    modifier = Modifier.weight(1f)
                )
            }
            if (isLandscapeWide && rowExecs.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExecutiveProfileCard(
    executive: Executive,
    onInspectOrigin: (DataOriginInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val badgeColor = semanticToneColor(executive.loyaltyTone)
    val badgeBg = semanticToneContainerColor(executive.loyaltyTone)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
            .combinedClickable(
                onClick = { onInspectOrigin(executive.originInfo) },
                onLongClick = { onInspectOrigin(executive.originInfo) }
            )
            .testTag("executive_card_${executive.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Foto Inisial
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(badgeBg)
                        .border(1.dp, badgeColor, CircleShape)
                ) {
                    Text(
                        text = executive.initials,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = badgeColor
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = executive.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = executive.roleTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Asal data penilaian eksekutif",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Loyalitas dan Ambisi tampil sebagai perkiraan ("Tinggi?", "Rendah?"), bukan angka pasti
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Perkiraan Loyalitas",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = executive.loyaltyEstimate,
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                            color = badgeColor
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Perkiraan Ambisi",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = executive.ambitionEstimate,
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Text(
                text = "Keahlian: ${executive.skillEstimate} · ${executive.horizonNote}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = executive.suspectedBehaviorNote,
                style = MaterialTheme.typography.bodySmall,
                color = if (executive.loyaltyTone == SemanticTone.DANGER_RED) KapitalSemanticColors.red
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProyekGanttTabContent(
    tasks: List<GanttTask>
) {
    val totalBudget = tasks.sumOf { it.budgetRsMillion }
    val totalRealized = tasks.sumOf { it.realizedRsMillion }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Jadwal Gantt: Pabrik pemintalan",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Integrasi hulu benang Kalimas · Garis waktu 12 bulan konstruksi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Anggaran: $totalBudget jt RS",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Realisasi: $totalRealized jt RS",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                        color = KapitalSemanticColors.blue
                    )
                }
            }

            // Month Ruler 1..12
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                    .padding(vertical = 4.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (m in 1..12) {
                    Text(
                        text = "B$m",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            tasks.forEach { task ->
                val barColor = semanticToneColor(task.tone)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = task.phaseName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = task.statusLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = barColor
                        )
                    }

                    // 12-Month Gantt Bar Grid
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        for (m in 1..12) {
                            val inWindow = m >= task.startMonth && m < (task.startMonth + task.durationMonths)
                            val isDoneMonth = inWindow && (m - task.startMonth) < task.progressMonth
                            val cellColor = when {
                                isDoneMonth -> barColor
                                inWindow -> barColor.copy(alpha = 0.28f)
                                else -> Color.Transparent
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(cellColor)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Anggaran: ${task.budgetRsMillion} jt RS vs Realisasi: ${task.realizedRsMillion} jt RS",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Bulan ${task.startMonth}–${task.startMonth + task.durationMonths - 1}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = task.noteFromStaff,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun KontrakTabContent(
    contracts: List<ContractItem>,
    apCurrent: Int,
    isLandscapeWide: Boolean,
    onInspectOrigin: (DataOriginInfo) -> Unit,
    onExecuteContractAction: (String) -> Unit
) {
    val rows = if (isLandscapeWide) contracts.chunked(2) else contracts.chunked(1)
    rows.forEach { pair ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            pair.forEach { item ->
                ContractCardItem(
                    contract = item,
                    apCurrent = apCurrent,
                    onInspectOrigin = onInspectOrigin,
                    onExecuteAction = { onExecuteContractAction(item.id) },
                    modifier = Modifier.weight(1f)
                )
            }
            if (isLandscapeWide && pair.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContractCardItem(
    contract: ContractItem,
    apCurrent: Int,
    onInspectOrigin: (DataOriginInfo) -> Unit,
    onExecuteAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val toneCol = semanticToneColor(contract.tone)
    val toneBg = semanticToneContainerColor(contract.tone)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
            .combinedClickable(
                onClick = { onInspectOrigin(contract.originInfo) },
                onLongClick = { onInspectOrigin(contract.originInfo) }
            )
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
                Surface(
                    color = toneBg,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = contract.category,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = toneCol,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = contract.countryName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = contract.counterparty,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = contract.valueText,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                color = toneCol
            )

            Text(
                text = contract.maturityOrTenor,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Klausul & Covenant: ${contract.covenantOrClause}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = contract.hiddenRiskNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = toneCol,
                    modifier = Modifier.padding(8.dp)
                )
            }

            Button(
                onClick = onExecuteAction,
                enabled = contract.apCostToAct > 0 && apCurrent >= contract.apCostToAct,
                colors = ButtonDefaults.buttonColors(
                    containerColor = KapitalSemanticColors.blue,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("contract_action_${contract.id}")
            ) {
                Text(contract.actionLabel)
            }
        }
    }
}
