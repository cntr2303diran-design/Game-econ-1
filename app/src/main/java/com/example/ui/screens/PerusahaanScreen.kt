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
import androidx.compose.ui.text.style.TextAlign
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
    onSetSalesScheme: (String) -> Unit,
    onSetFactoryScheme: (String) -> Unit,
    onAudit: (String) -> Unit,
    onConfront: (String) -> Unit,
    onFire: (String) -> Unit,
    onContractAction: (contractId: String, actionId: String) -> Unit,
    onApplyCredit: (String, Int, Int, String, Boolean, Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onReviewOffer: (String) -> Unit = {},
    onAcceptOffer: (String) -> Unit = {},
    onDeclineOffer: (String) -> Unit = {},
    onInvest: (String) -> Unit = {},
    onCorporateAction: (String) -> Unit = {},
    onBuyIntel: (kind: String, subject: String, tier: Int) -> Unit = { _, _, _ -> },
    onAnswerBoard: (String) -> Unit = {},
    onSetSegmentFocus: (segment: String, level: Int) -> Unit = { _, _ -> },
    onHireStaff: (role: String, tier: Int) -> Unit = { _, _ -> },
    onFireStaff: (String) -> Unit = {},
    onRaiseSalary: (String) -> Unit = {},
    onSetRdLevel: (Int) -> Unit = {},
    onSetLogistics: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val subTabs = listOf("Laporan keuangan", "Organisasi", "Proyek", "Kontrak", "Bank", "Pelaku", "Investasi", "Intelijen", "Segmen", "Operasi")
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
                        onSetSalesScheme = onSetSalesScheme,
                        onSetFactoryScheme = onSetFactoryScheme,
                        onAudit = onAudit,
                        onConfront = onConfront,
                        onFire = onFire
                    )
                    2 -> ProyekGanttTabContent(
                        title = gameState.ganttTitle,
                        totalMonths = gameState.ganttTotalMonths,
                        tasks = gameState.ganttTasks
                    )
                    3 -> KontrakTabContent(
                        contracts = gameState.contracts,
                        isLandscapeWide = isLandscapeWide,
                        onInspectOrigin = onInspectOrigin,
                        onContractAction = onContractAction
                    )
                    4 -> BankTabContent(
                        credit = gameState.credit,
                        ap = gameState.actionPoints,
                        onApply = onApplyCredit,
                        onReview = onReviewOffer,
                        onAccept = onAcceptOffer,
                        onDecline = onDeclineOffer
                    )
                    5 -> PelakuTabContent(gameState.actors)
                    6 -> InvestTabContent(gameState.investments, gameState.investSummary, gameState.actionPoints, onInvest, onCorporateAction)
                    7 -> IntelTabContent(gameState.intel, gameState.actionPoints, onBuyIntel, onAnswerBoard)
                    8 -> SegmenTabContent(gameState.productMarket, gameState.actionPoints, onSetSegmentFocus)
                    9 -> OperasiTabContent(gameState.operations, gameState.actionPoints, onHireStaff, onFireStaff, onRaiseSalary, onSetRdLevel, onSetLogistics)
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
    // Peringatan jebakan akrual (hanya muncul bila laba positif tetapi arus kas operasi negatif)
    val accrual = gameState.accrualWarning
    if (accrual != null) {
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
                    text = accrual,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
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
                    title = "1. Laporan laba rugi (akrual)",
                    subtitle = "Dalam juta Rial Sundara (jt RS) · ${gameState.periodLabel}",
                    rows = gameState.incomeStatementRows,
                    onInspectOrigin = onInspectOrigin
                )
                FinancialStatementTableCard(
                    title = "3. Laporan arus kas",
                    subtitle = "Rekonsiliasi laba bersih terhadap kas bank · ${gameState.periodLabel}",
                    rows = gameState.cashFlowRows,
                    onInspectOrigin = onInspectOrigin
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                FinancialStatementTableCard(
                    title = "2. Neraca",
                    subtitle = "Posisi akhir ${gameState.periodLabel} · aset = liabilitas + ekuitas",
                    rows = gameState.balanceSheetRows,
                    onInspectOrigin = onInspectOrigin
                )
                FinancialStatementTableCard(
                    title = "4. Laporan perubahan ekuitas",
                    subtitle = "Dari ekuitas awal ke akhir · ${gameState.periodLabel}",
                    rows = gameState.equityRows,
                    onInspectOrigin = onInspectOrigin
                )
                FinancialStatementTableCard(
                    title = "5. Catatan atas laporan keuangan",
                    subtitle = "Umur piutang, utang bank, karyawan, rasio",
                    rows = gameState.notesRows,
                    onInspectOrigin = onInspectOrigin
                )
                if (gameState.groupRows.isNotEmpty()) {
                    FinancialStatementTableCard(
                        title = "6. Laporan konsolidasi grup",
                        subtitle = "Induk + anak usaha, setelah eliminasi · jt RS",
                        rows = gameState.groupRows,
                        onInspectOrigin = onInspectOrigin
                    )
                    FinancialStatementTableCard(
                        title = "7. Anak usaha per entitas",
                        subtitle = "Pembukuan mata uang lokal dan terjemahan ke RS",
                        rows = gameState.entityRows,
                        onInspectOrigin = onInspectOrigin
                    )
                }
            }
        }
    } else {
        FinancialStatementTableCard(
            title = "1. Laporan laba rugi (akrual)",
            subtitle = "Dalam juta Rial Sundara (jt RS) · ${gameState.periodLabel}",
            rows = gameState.incomeStatementRows,
            onInspectOrigin = onInspectOrigin
        )
        FinancialStatementTableCard(
            title = "2. Neraca",
            subtitle = "Posisi akhir ${gameState.periodLabel} · aset = liabilitas + ekuitas",
            rows = gameState.balanceSheetRows,
            onInspectOrigin = onInspectOrigin
        )
        FinancialStatementTableCard(
            title = "3. Laporan arus kas",
            subtitle = "Rekonsiliasi laba bersih terhadap kas bank · ${gameState.periodLabel}",
            rows = gameState.cashFlowRows,
            onInspectOrigin = onInspectOrigin
        )
        FinancialStatementTableCard(
            title = "4. Laporan perubahan ekuitas",
            subtitle = "Dari ekuitas awal ke akhir · ${gameState.periodLabel}",
            rows = gameState.equityRows,
            onInspectOrigin = onInspectOrigin
        )
        FinancialStatementTableCard(
            title = "5. Catatan atas laporan keuangan",
            subtitle = "Umur piutang, utang bank, karyawan, rasio",
            rows = gameState.notesRows,
            onInspectOrigin = onInspectOrigin
        )
        if (gameState.groupRows.isNotEmpty()) {
            FinancialStatementTableCard(
                title = "6. Laporan konsolidasi grup",
                subtitle = "Induk + anak usaha, setelah eliminasi · jt RS",
                rows = gameState.groupRows,
                onInspectOrigin = onInspectOrigin
            )
            FinancialStatementTableCard(
                title = "7. Anak usaha per entitas",
                subtitle = "Pembukuan mata uang lokal dan terjemahan ke RS",
                rows = gameState.entityRows,
                onInspectOrigin = onInspectOrigin
            )
        }
    }
}

@Composable
private fun FinancialStatementTableCard(
    title: String,
    subtitle: String,
    rows: List<FinancialRowItem>,
    onInspectOrigin: (DataOriginInfo) -> Unit,
    currentLabel: String = "Bulan ini",
    priorLabel: String = "Bulan lalu"
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
                    text = "Pos",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(96.dp),
                    textAlign = TextAlign.End
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = priorLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(96.dp),
                    textAlign = TextAlign.End
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
                            isBold = row.isHeaderOrTotal,
                            modifier = Modifier.width(96.dp)
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
                            isBold = false,
                            modifier = Modifier.width(96.dp)
                        )
                    }
                    HorizontalDivider(color = KapitalSemanticColors.border.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun SchemeSelector(
    title: String,
    note: String,
    options: List<Pair<String, String>>,
    selectedId: String,
    apCurrent: Int,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(text = note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (id, label) ->
                val selected = id == selectedId
                FilterChip(
                    selected = selected,
                    enabled = selected || apCurrent >= 1,
                    onClick = { if (!selected) onSelect(id) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
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

@Composable
private fun OrganisasiTabContent(
    gameState: GameState,
    isLandscapeWide: Boolean,
    onInspectOrigin: (DataOriginInfo) -> Unit,
    onSetSalesScheme: (String) -> Unit,
    onSetFactoryScheme: (String) -> Unit,
    onAudit: (String) -> Unit,
    onConfront: (String) -> Unit,
    onFire: (String) -> Unit
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Desain insentif (1 AP per perubahan)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Insentif membentuk perilaku siapa pun yang menduduki jabatan, termasuk penggantinya. Tidak ada angka loyalitas pasti; perilaku sebenarnya hanya terlihat lewat audit.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SchemeSelector(
                title = "Direktur Penjualan",
                note = "Bonus omzet mendorong penjualan kredit ke peritel rapuh.",
                options = gameState.salesSchemeOptions,
                selectedId = gameState.salesSchemeLabel,
                apCurrent = gameState.apCurrent,
                onSelect = onSetSalesScheme
            )
            SchemeSelector(
                title = "Kepala Pabrik",
                note = "KPI penghematan biaya menggoda untuk menunda perawatan.",
                options = gameState.factorySchemeOptions,
                selectedId = gameState.factorySchemeLabel,
                apCurrent = gameState.apCurrent,
                onSelect = onSetFactoryScheme
            )
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
                    onAudit = { onAudit(exec.id) },
                    onConfront = { onConfront(exec.id) },
                    onFire = { onFire(exec.id) },
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
    onAudit: () -> Unit,
    onConfront: () -> Unit,
    onFire: () -> Unit,
    modifier: Modifier = Modifier
) {
    val badgeColor = semanticToneColor(executive.loyaltyTone)
    val badgeBg = semanticToneContainerColor(executive.loyaltyTone)
    var confirmFire by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
            .testTag("executive_card_${executive.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.combinedClickable(
                    onClick = { onInspectOrigin(executive.originInfo) },
                    onLongClick = { onInspectOrigin(executive.originInfo) }
                )
            ) {
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

            if (executive.present) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(color = badgeBg, shape = RoundedCornerShape(4.dp), modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Perkiraan loyalitas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(executive.loyaltyEstimate, style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily), color = badgeColor)
                        }
                    }
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp), modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Perkiraan ambisi", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(executive.ambitionEstimate, style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily), color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                Text(
                    text = "Insentif: ${executive.incentiveScheme}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = executive.signalNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = executive.auditStatus,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (executive.auditStatus.contains("indikasi")) KapitalSemanticColors.red else KapitalSemanticColors.blue
            )
            if (executive.lastOutcome.isNotBlank()) {
                Text(
                    text = executive.lastOutcome,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (executive.present) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onAudit,
                        enabled = executive.canAudit,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("audit_${executive.id}")
                    ) { Text("Audit (2 AP)", style = MaterialTheme.typography.labelMedium) }
                    OutlinedButton(
                        onClick = onConfront,
                        enabled = executive.canConfront,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) { Text("Konfrontasi (1)", style = MaterialTheme.typography.labelMedium) }
                    OutlinedButton(
                        onClick = { confirmFire = true },
                        enabled = executive.canFire,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) { Text("Pecat (1)", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }

    if (confirmFire) {
        AlertDialog(
            onDismissRequest = { confirmFire = false },
            title = { Text("Pecat ${executive.name}?") },
            text = { Text("Pesangon 300 jt RS, posisi kosong 3 giliran, dan AP maksimum turun 1 selama kosong. Pengganti tetap mengikuti skema insentif yang berlaku.") },
            confirmButton = {
                TextButton(onClick = { confirmFire = false; onFire() }) { Text("Pecat") }
            },
            dismissButton = {
                TextButton(onClick = { confirmFire = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun ProyekGanttTabContent(
    title: String,
    totalMonths: Int,
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
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (tasks.isEmpty()) {
                Text(
                    text = "Setujui proposal pabrik pemintalan di Markas untuk memulai proyek. Jadwal, anggaran, dan realisasinya akan muncul di sini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }
            Text(
                text = "Anggaran ${"%,d".format(totalBudget).replace(',', '.')} jt RS · realisasi ${"%,d".format(totalRealized).replace(',', '.')} jt RS",
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Penggaris bulan memakai bobot yang sama dengan sel batang agar sejajar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                for (m in 1..totalMonths) {
                    Text(
                        text = if (m == 1 || m % 3 == 0) "$m" else "",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        for (m in 1..totalMonths) {
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

                    Text(
                        text = "Anggaran ${task.budgetRsMillion} jt RS · realisasi ${task.realizedRsMillion} jt RS · bulan ${task.startMonth}–${task.startMonth + task.durationMonths - 1}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurface
                    )
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
    isLandscapeWide: Boolean,
    onInspectOrigin: (DataOriginInfo) -> Unit,
    onContractAction: (contractId: String, actionId: String) -> Unit
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
                    onInspectOrigin = onInspectOrigin,
                    onAction = { actionId -> onContractAction(item.id, actionId) },
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
    onInspectOrigin: (DataOriginInfo) -> Unit,
    onAction: (String) -> Unit,
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(color = toneBg, shape = RoundedCornerShape(4.dp)) {
                    Text(
                        text = contract.category,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = toneCol,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = contract.countryName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(text = contract.counterparty, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(text = contract.valueText, style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily), color = toneCol)
            Text(text = contract.maturityOrTenor, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = "Klausul dan covenant: ${contract.covenantOrClause}",
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
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(8.dp)
                )
            }

            contract.actions.forEach { action ->
                val primary = action.id == "choose"
                if (primary) {
                    Button(
                        onClick = { onAction(action.id) },
                        enabled = action.enabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("contract_${contract.id}_${action.id}")
                    ) { Text(action.label) }
                } else {
                    OutlinedButton(
                        onClick = { onAction(action.id) },
                        enabled = action.enabled,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("contract_${contract.id}_${action.id}")
                    ) { Text(action.label) }
                }
            }
        }
    }
}
