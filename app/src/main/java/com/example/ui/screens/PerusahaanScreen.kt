package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
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
import com.example.ui.components.semanticToneColor
import com.example.ui.components.semanticToneContainerColor
import com.example.ui.theme.KapitalSemanticColors
import com.example.ui.theme.MonoFontFamily

@Composable
fun PerusahaanScreen(
    gameState: GameState,
    onInspectOrigin: (DataOriginInfo) -> Unit,
    onChangeIncentiveScheme: (String) -> Unit,
    onChangeFactoryScheme: (String) -> Unit,
    onExecuteContractAction: (String) -> Unit,
    onAuditExec: (String) -> Unit,
    onConfrontExec: (String) -> Unit,
    onFireExec: (String) -> Unit,
    onStartInvestment: (String) -> Unit,
    onOpenSubsidiary: (String) -> Unit,
    onRepatriateSubsidiary: (String) -> Unit,
    onSetTransferPricing: (String) -> Unit,
    onApplyCredit: (bankId: String, amount: Int, tenor: Int, purpose: String, floating: Boolean, bullet: Boolean) -> Unit,
    onReviewCreditOffer: (String) -> Unit,
    onAcceptCreditOffer: (String) -> Unit,
    onDeclineCreditOffer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val subTabs = listOf("Laporan keuangan", "Organisasi", "Proyek", "Ekspansi & Era 2", "Kontrak", "Bank")
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
                        onChangeIncentiveScheme = onChangeIncentiveScheme,
                        onChangeFactoryScheme = onChangeFactoryScheme,
                        onAuditExec = onAuditExec,
                        onConfrontExec = onConfrontExec,
                        onFireExec = onFireExec
                    )
                    2 -> ProyekGanttTabContent(
                        tasks = gameState.ganttTasks
                    )
                    3 -> EkspansiMultinasionalTabContent(
                        gameState = gameState,
                        onStartInvestment = onStartInvestment,
                        onOpenSubsidiary = onOpenSubsidiary,
                        onRepatriateSubsidiary = onRepatriateSubsidiary,
                        onSetTransferPricing = onSetTransferPricing
                    )
                    4 -> KontrakTabContent(
                        contracts = gameState.contracts,
                        apCurrent = gameState.apCurrent,
                        isLandscapeWide = isLandscapeWide,
                        onInspectOrigin = onInspectOrigin,
                        onExecuteContractAction = onExecuteContractAction
                    )
                    5 -> BankTabContent(
                        gameState = gameState,
                        onApply = onApplyCredit,
                        onReview = onReviewCreditOffer,
                        onAccept = onAcceptCreditOffer,
                        onDecline = onDeclineCreditOffer
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
    val bannerBg = if (gameState.accrualTrapActive) KapitalSemanticColors.yellowContainer else KapitalSemanticColors.greenContainer
    val bannerFg = if (gameState.accrualTrapActive) KapitalSemanticColors.yellow else KapitalSemanticColors.green
    Surface(
        color = bannerBg,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, bannerFg.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (gameState.accrualTrapActive) Icons.Outlined.WarningAmber else Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = bannerFg,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = gameState.accrualTrapSummary.ifEmpty { "Ketuk atau tahan baris angka mana pun untuk melihat Asal data." },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    gameState.reportCheck?.let { chk ->
        val allOk = chk.balanceOk && chk.cashFlowOk && chk.equityOk
        val fg = if (allOk) KapitalSemanticColors.green else KapitalSemanticColors.red
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, fg.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (allOk) "VERIFIKASI IDENTITAS AKUNTANSI: COCOK" else "PERINGATAN SELISIH AKUNTANSI",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold),
                    color = fg
                )
                Text("• Neraca: Aset ${chk.assetsText} = Liabilitas + Ekuitas ${chk.liabPlusEqText}", style = MaterialTheme.typography.labelSmall)
                Text("• Arus Kas: A + B + C = ${chk.cfSumText} | Kas Akhir − Kas Awal = ${chk.dCashText}", style = MaterialTheme.typography.labelSmall)
                Text("• Ekuitas: Ekuitas Awal + Mutasi = ${chk.eqCalcText} | Ekuitas Neraca = ${chk.eqActualText}", style = MaterialTheme.typography.labelSmall)
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
                    title = "1. Laporan Laba Rugi (Akrual)",
                    subtitle = "Dalam juta Rial Sundara (jt RS)",
                    colCur = gameState.periodLabel,
                    colPrev = gameState.priorPeriodLabel,
                    rows = gameState.incomeStatementRows,
                    onInspectOrigin = onInspectOrigin
                )
                FinancialStatementTableCard(
                    title = "3. Laporan Arus Kas (Metode Tidak Langsung)",
                    subtitle = "Rekonsiliasi Laba Bersih terhadap Kas Bank (A + B + C = ΔKas)",
                    colCur = gameState.periodLabel,
                    colPrev = gameState.priorPeriodLabel,
                    rows = gameState.cashFlowRows,
                    onInspectOrigin = onInspectOrigin
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                FinancialStatementTableCard(
                    title = "2. Laporan Posisi Keuangan (Neraca)",
                    subtitle = "Aset = Liabilitas + Ekuitas",
                    colCur = gameState.dateText,
                    colPrev = gameState.periodLabel,
                    rows = gameState.balanceSheetRows,
                    onInspectOrigin = onInspectOrigin
                )
                if (gameState.equityStatementRows.isNotEmpty()) {
                    FinancialStatementTableCard(
                        title = "4. Laporan Perubahan Ekuitas",
                        subtitle = "Rekonsiliasi Ekuitas Awal ke Ekuitas Akhir",
                        colCur = gameState.periodLabel,
                        colPrev = gameState.priorPeriodLabel,
                        rows = gameState.equityStatementRows,
                        onInspectOrigin = onInspectOrigin
                    )
                }
            }
        }
    } else {
        FinancialStatementTableCard(
            title = "1. Laporan Laba Rugi (Akrual)",
            subtitle = "Dalam juta Rial Sundara (jt RS)",
            colCur = gameState.periodLabel,
            colPrev = gameState.priorPeriodLabel,
            rows = gameState.incomeStatementRows,
            onInspectOrigin = onInspectOrigin
        )
        FinancialStatementTableCard(
            title = "2. Laporan Posisi Keuangan (Neraca)",
            subtitle = "Aset = Liabilitas + Ekuitas",
            colCur = gameState.dateText,
            colPrev = gameState.periodLabel,
            rows = gameState.balanceSheetRows,
            onInspectOrigin = onInspectOrigin
        )
        FinancialStatementTableCard(
            title = "3. Laporan Arus Kas (Metode Tidak Langsung)",
            subtitle = "Rekonsiliasi Laba Bersih terhadap Kas Bank (A + B + C = ΔKas)",
            colCur = gameState.periodLabel,
            colPrev = gameState.priorPeriodLabel,
            rows = gameState.cashFlowRows,
            onInspectOrigin = onInspectOrigin
        )
        if (gameState.equityStatementRows.isNotEmpty()) {
            FinancialStatementTableCard(
                title = "4. Laporan Perubahan Ekuitas",
                subtitle = "Rekonsiliasi Ekuitas Awal ke Ekuitas Akhir",
                colCur = gameState.periodLabel,
                colPrev = gameState.priorPeriodLabel,
                rows = gameState.equityStatementRows,
                onInspectOrigin = onInspectOrigin
            )
        }
    }

    if (gameState.financialNotes.isNotEmpty()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("5. Catatan atas Laporan Keuangan (CaLK)", style = MaterialTheme.typography.titleLarge)
                gameState.financialNotes.forEach { n ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Catatan ${n.number} — ${n.title}", style = MaterialTheme.typography.titleMedium, color = KapitalSemanticColors.blue)
                        Text(n.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FinancialStatementTableCard(
    title: String,
    subtitle: String,
    colCur: String,
    colPrev: String,
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
                    text = colCur,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = colPrev,
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
                        .combinedClickable(
                            onClick = { onInspectOrigin(row.originInfo) },
                            onLongClick = { onInspectOrigin(row.originInfo) }
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = row.label,
                            style = if (row.isHeaderOrTotal) {
                                MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1.3f)
                        )

                        Text(
                            text = row.currentVal,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = MonoFontFamily,
                                fontWeight = if (row.isHeaderOrTotal) FontWeight.Bold else FontWeight.SemiBold
                            ),
                            color = if (row.tone == SemanticTone.NEUTRAL_GRAY) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                semanticToneColor(row.tone)
                            }
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Text(
                            text = row.priorVal,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (row.note.isNotBlank()) {
                        Text(
                            text = row.note,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider(color = KapitalSemanticColors.border.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun OrganisasiTabContent(
    gameState: GameState,
    isLandscapeWide: Boolean,
    onInspectOrigin: (DataOriginInfo) -> Unit,
    onChangeIncentiveScheme: (String) -> Unit,
    onChangeFactoryScheme: (String) -> Unit,
    onAuditExec: (String) -> Unit,
    onConfrontExec: (String) -> Unit,
    onFireExec: (String) -> Unit
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
            Text(
                text = "1. Skema Insentif Penjualan (1 AP untuk mengubah)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Skema aktif: ${gameState.activeIncentiveScheme}. Bonus omzet memicu obral kredit 90 hari; bonus kas tertagih menghentikan obral tetapi menurunkan omzet ±3%.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val salesSchemes = listOf(
                "Bonus berbasis omzet",
                "Bonus kas tertagih + clawback 3 tahun"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                salesSchemes.forEach { scheme ->
                    val selected = gameState.activeIncentiveScheme == scheme
                    FilterChip(
                        selected = selected,
                        onClick = { onChangeIncentiveScheme(scheme) },
                        label = { Text(scheme, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    )
                }
            }

            HorizontalDivider(color = KapitalSemanticColors.border)

            Text(
                text = "2. KPI Kepala Pabrik (1 AP untuk mengubah)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Skema aktif: ${gameState.activeFactoryScheme}. KPI penghematan menghemat 190 jt RS/bln tetapi menumpuk risiko mesin rusak; KPI keandalan mengikis tunggakan perawatan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val facSchemes = listOf(
                "KPI penghematan biaya kuartalan",
                "KPI keandalan mesin + audit perawatan"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                facSchemes.forEach { scheme ->
                    val selected = gameState.activeFactoryScheme == scheme
                    FilterChip(
                        selected = selected,
                        onClick = { onChangeFactoryScheme(scheme) },
                        label = { Text(scheme, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    )
                }
            }
        }
    }

    if (isLandscapeWide) {
        val pairs = gameState.executives.chunked(2)
        pairs.forEach { rowExecs ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowExecs.forEach { exec ->
                    ExecutiveProfileCard(
                        exec = exec,
                        onInspectOrigin = onInspectOrigin,
                        onAudit = { onAuditExec(exec.id) },
                        onConfront = { onConfrontExec(exec.id) },
                        onFire = { onFireExec(exec.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    } else {
        gameState.executives.forEach { exec ->
            ExecutiveProfileCard(
                exec = exec,
                onInspectOrigin = onInspectOrigin,
                onAudit = { onAuditExec(exec.id) },
                onConfront = { onConfrontExec(exec.id) },
                onFire = { onFireExec(exec.id) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExecutiveProfileCard(
    exec: Executive,
    onInspectOrigin: (DataOriginInfo) -> Unit,
    onAudit: () -> Unit,
    onConfront: () -> Unit,
    onFire: () -> Unit,
    modifier: Modifier = Modifier
) {
    val loyColor = semanticToneColor(exec.loyaltyTone)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
            .combinedClickable(
                onClick = { onInspectOrigin(exec.originInfo) },
                onLongClick = { onInspectOrigin(exec.originInfo) }
            )
            .testTag("exec_card_${exec.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(KapitalSemanticColors.blueContainer)
                        .border(1.dp, KapitalSemanticColors.blue.copy(alpha = 0.5f), CircleShape)
                ) {
                    Text(
                        text = exec.initials,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = KapitalSemanticColors.blue
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exec.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = exec.roleTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EstimateAttributeChip("Loyalitas", exec.loyaltyEstimate, loyColor, Modifier.weight(1f))
                EstimateAttributeChip("Ambisi", exec.ambitionEstimate, KapitalSemanticColors.yellow, Modifier.weight(1f))
                EstimateAttributeChip("Keahlian", exec.skillEstimate, KapitalSemanticColors.blue, Modifier.weight(1f))
            }

            Text(
                text = "Status Audit: ${exec.auditStatus}",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                color = KapitalSemanticColors.blue
            )

            Surface(
                color = semanticToneContainerColor(exec.loyaltyTone),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (exec.present) exec.suspectedBehaviorNote else exec.vacantNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(10.dp)
                )
            }

            if (exec.present && exec.id != "exec_cfo") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = onAudit,
                        enabled = exec.canAudit,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Audit (2 AP)", style = MaterialTheme.typography.labelSmall)
                    }
                    if (exec.canConfront) {
                        Button(
                            onClick = onConfront,
                            colors = ButtonDefaults.buttonColors(containerColor = KapitalSemanticColors.yellow, contentColor = Color.White),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Text("Konfrontasi (1 AP)", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    OutlinedButton(
                        onClick = onFire,
                        enabled = exec.canFire,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Pecat (1 AP)", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun EstimateAttributeChip(
    label: String,
    estimateValue: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = estimateValue,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = accentColor
            )
        }
    }
}

@Composable
private fun ProyekGanttTabContent(
    tasks: List<GanttTask>
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Jadwal Gantt: Pabrik Pemintalan & Proyek Lintas Batas",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Integrasi hulu benang & konstruksi anak usaha multinasional",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val totalBudget = tasks.sumOf { it.budgetRsMillion }
                val totalRealized = tasks.sumOf { it.realizedRsMillion }
                Surface(
                    color = KapitalSemanticColors.yellowContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Realisasi $totalRealized / $totalBudget jt RS",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = KapitalSemanticColors.yellow,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            tasks.forEach { task ->
                val toneColor = semanticToneColor(task.tone)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = task.projectName,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                color = KapitalSemanticColors.blue
                            )
                            Text(
                                text = task.phaseName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = task.statusLabel,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = toneColor
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        for (m in 1..15) {
                            val inRange = m >= task.startMonth && m < task.startMonth + task.durationMonths
                            val completed = inRange && (m - task.startMonth) < task.progressMonth
                            val barColor = when {
                                completed -> toneColor
                                inRange -> toneColor.copy(alpha = 0.28f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(barColor)
                            ) {
                                Text(
                                    text = "B$m",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                    color = if (completed) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Anggaran: ${task.budgetRsMillion} jt RS",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Realisasi: ${task.realizedRsMillion} jt RS",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = toneColor
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
    if (isLandscapeWide) {
        val pairs = contracts.chunked(2)
        pairs.forEach { rowContracts ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowContracts.forEach { contract ->
                    ContractCardItem(
                        contract = contract,
                        apCurrent = apCurrent,
                        onInspectOrigin = onInspectOrigin,
                        onExecuteContractAction = onExecuteContractAction,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowContracts.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    } else {
        contracts.forEach { contract ->
            ContractCardItem(
                contract = contract,
                apCurrent = apCurrent,
                onInspectOrigin = onInspectOrigin,
                onExecuteContractAction = onExecuteContractAction,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContractCardItem(
    contract: ContractItem,
    apCurrent: Int,
    onInspectOrigin: (DataOriginInfo) -> Unit,
    onExecuteContractAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val toneColor = semanticToneColor(contract.tone)

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
                Text(
                    text = "${contract.category.uppercase()} · ${contract.countryName.uppercase()}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = toneColor
                )
                Text(
                    text = contract.maturityOrTenor,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
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
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = KapitalSemanticColors.blue
            )

            Text(
                text = "Klausul Utama: ${contract.covenantOrClause}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                color = semanticToneContainerColor(contract.tone),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = contract.hiddenRiskNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = toneColor,
                    modifier = Modifier.padding(8.dp)
                )
            }

            OutlinedButton(
                onClick = { onExecuteContractAction(contract.id) },
                enabled = contract.canAct && apCurrent >= contract.apCostToAct,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("contract_action_${contract.id}")
            ) {
                Text(text = contract.actionLabel)
            }
        }
    }
}

@Composable
private fun EkspansiMultinasionalTabContent(
    gameState: GameState,
    onStartInvestment: (String) -> Unit,
    onOpenSubsidiary: (String) -> Unit,
    onRepatriateSubsidiary: (String) -> Unit,
    onSetTransferPricing: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Kapasitas, Permintaan & Struktur Grup Multinasional", style = MaterialTheme.typography.titleMedium)
            Text(gameState.capacitySummary, style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily), color = KapitalSemanticColors.blue)
            Text("Prinsip: menambah kapasitas tanpa permintaan baru hanya menurunkan utilisasi. Membuka anak usaha di negara lain memberi efisiensi rantai pasok, akses pasar valas, dan arbitrase pajak lintas batas.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    // Bagian 1: Kebijakan Transfer Pricing Lintas Negara
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Kebijakan Transfer Pricing & Pajak Lintas Batas (1 AP)", style = MaterialTheme.typography.titleMedium)
                    Text("Mode aktif: ${gameState.transferPricingLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(color = KapitalSemanticColors.blueContainer, shape = RoundedCornerShape(4.dp)) {
                    Text(
                        text = "PPh Efektif ${gameState.effectiveTaxRateText}",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold),
                        color = KapitalSemanticColors.blue,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                text = "Mendirikan Holding SPV di Meridia (PPh 8,5%) memungkinkan penghematan PPh badan dan memangkas separuh pajak pemotongan dividen repatriasi, namun mode Agresif berisiko diaudit kantor pajak gabungan.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val modes = listOf(
                "WAJAR" to "Wajar (Arm's Length)",
                "OPTIMAL" to "Optimalisasi Sah",
                "AGRESIF" to "Agresif via Meridia"
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                modes.forEach { (code, label) ->
                    FilterChip(
                        selected = gameState.transferPricingModeName == code,
                        onClick = { onSetTransferPricing(code) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    )
                }
            }
        }
    }

    // Bagian 2: Anak Usaha Multinasional (Era 2)
    Text(
        text = "Anak Usaha Lintas Negara (Era 2 Multinasional — 4 Negara Tujuan)",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface
    )

    gameState.subsidiaries.forEach { sub ->
        val toneCol = semanticToneColor(sub.tone)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
                .testTag("subsidiary_card_${sub.countryId}")
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${sub.countryName.uppercase()} (${sub.currencyCode})",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold),
                            color = toneCol
                        )
                        Text(sub.title, style = MaterialTheme.typography.titleLarge)
                    }
                    Surface(color = semanticToneContainerColor(sub.tone), shape = RoundedCornerShape(4.dp)) {
                        Text(
                            text = if (sub.isActive) "BEROPERASI" else if (sub.isBuilding) "KONSTRUKSI" else sub.costText,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold),
                            color = toneCol,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(sub.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Manfaat: ${sub.benefitText}", style = MaterialTheme.typography.labelMedium, color = KapitalSemanticColors.green)
                Text("Risiko Lintas Negara: ${sub.riskText}", style = MaterialTheme.typography.labelSmall, color = KapitalSemanticColors.yellow)
                Text("Status: ${sub.statusText}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily), color = MaterialTheme.colorScheme.onSurface)

                if (sub.isActive) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Laba Kas Ditahan di ${sub.countryName}: ${sub.retainedForeignText} (${sub.retainedRsEquivalentText})",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold)
                                )
                                Text(sub.withholdingTaxText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { onRepatriateSubsidiary(sub.countryId) },
                                enabled = sub.canRepatriate,
                                colors = ButtonDefaults.buttonColors(containerColor = KapitalSemanticColors.blue, contentColor = Color.White),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Repatriasi (1 AP)")
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = { onOpenSubsidiary(sub.countryId) },
                        enabled = sub.canOpen,
                        colors = ButtonDefaults.buttonColors(containerColor = KapitalSemanticColors.blue, contentColor = Color.White),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(sub.openBlockReason ?: "Dirikan Anak Usaha di ${sub.countryName} (${sub.apCost} AP)")
                    }
                }
            }
        }
    }

    // Bagian 3: Proyek Investasi & Ekspansi Domestik / Era 3-5
    Text(
        text = "Proyek Investasi & Modernisasi (Domestik & Era 3–5)",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface
    )

    gameState.investments.forEach { inv ->
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(inv.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text("${inv.apCost} AP · ${inv.durationText}", style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily), color = KapitalSemanticColors.blue)
                }
                Text(inv.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Biaya: ${inv.costText}", style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily))
                Text("Efek: ${inv.benefitText}", style = MaterialTheme.typography.labelMedium, color = KapitalSemanticColors.green)
                Text("Perlakuan akuntansi: ${inv.accountingNote}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Status: ${inv.statusText}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily))
                Button(
                    onClick = { onStartInvestment(inv.id) },
                    enabled = inv.canStart,
                    colors = ButtonDefaults.buttonColors(containerColor = KapitalSemanticColors.blue, contentColor = Color.White),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(inv.blockReason ?: "Mulai ${inv.title} (${inv.apCost} AP)")
                }
            }
        }
    }
}

@Composable
private fun BankTabContent(
    gameState: GameState,
    onApply: (bankId: String, amount: Int, tenor: Int, purpose: String, floating: Boolean, bullet: Boolean) -> Unit,
    onReview: (String) -> Unit,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit
) {
    var selectedBank by remember { mutableStateOf<BankCard?>(null) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Meja Kredit Korporasi", style = MaterialTheme.typography.titleLarge)
            Text(gameState.creditMetricsSummary, style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily), color = KapitalSemanticColors.blue)
            Text("Cara kerja: pilih bank → tentukan jumlah, tenor, tujuan, struktur bunga & pokok (2 AP) → tunggu telaah komite kredit 1–3 giliran → tinjau klausul tersembunyi (1 AP) → terima atau tolak penawaran.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (gameState.creditApps.isNotEmpty()) {
        Text("Pengajuan & Penawaran Kredit", style = MaterialTheme.typography.titleLarge)
        gameState.creditApps.forEach { a ->
            val col = semanticToneColor(a.statusTone)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, col.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(a.bankName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text(a.banker, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(color = semanticToneContainerColor(a.statusTone), shape = RoundedCornerShape(4.dp)) {
                            Text(a.statusLabel, style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold),
                                color = col, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                    Text("Permohonan: ${a.amountText} · ${a.tenorText} · ${a.purposeText} · ${a.structureText}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily))
                    Text(a.stageText, style = MaterialTheme.typography.bodySmall)
                    if (a.officialReason.isNotEmpty()) {
                        Text("Alasan resmi bank: “${a.officialReason}”", style = MaterialTheme.typography.bodySmall, color = KapitalSemanticColors.red)
                    }
                    if (a.informalNote.isNotEmpty()) {
                        Text(a.informalNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (a.offerSummary.isNotEmpty()) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(a.offerSummary, style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold))
                                a.offerTerms.forEach { Text("• Syarat: $it", style = MaterialTheme.typography.labelSmall, color = KapitalSemanticColors.yellow) }
                                if (a.reviewed) {
                                    if (a.hiddenTerms.isEmpty()) Text("• Tinjauan hukum: tidak ada klausul jebakan tambahan.", style = MaterialTheme.typography.labelSmall, color = KapitalSemanticColors.green)
                                    else a.hiddenTerms.forEach { Text("• KLAUSUL TERSEMBUNYI: $it", style = MaterialTheme.typography.labelSmall, color = KapitalSemanticColors.red) }
                                } else {
                                    Text("• Draf perjanjian belum ditinjau konsultan hukum (bisa berisi klausul MAC, bunga promosi naik, atau callable).",
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (a.expiresText.isNotEmpty()) Text(a.expiresText, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (a.canAccept || a.canDecline) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!a.reviewed) {
                                    OutlinedButton(onClick = { onReview(a.id) }, enabled = a.canReview, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                                        Text("Tinjau hukum (1 AP)", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Button(
                                    onClick = { onAccept(a.id) }, enabled = a.canAccept,
                                    colors = ButtonDefaults.buttonColors(containerColor = KapitalSemanticColors.green, contentColor = Color.White),
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                ) { Text("Terima (1 AP)", style = MaterialTheme.typography.labelSmall) }
                                OutlinedButton(onClick = { onDecline(a.id) }, enabled = a.canDecline, modifier = Modifier.weight(0.8f).heightIn(min = 48.dp)) {
                                    Text("Tolak", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Text("Daftar Bank & Pemberi Pinjaman", style = MaterialTheme.typography.titleLarge)
    gameState.banks.forEach { b ->
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${b.kindLabel.uppercase()} · HUBUNGAN ${b.relationshipScore}/100",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily), color = KapitalSemanticColors.blue)
                        Text(b.name, style = MaterialTheme.typography.titleLarge)
                        Text("${b.banker} — ${b.bankerRole}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("Bunga indikatif: ${b.indicativeRateText} · ${b.feeText} · Tenor maks ${b.maxTenor} bln · Proses ±${b.reviewTurns} giliran",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily))
                Text("Covenant: ${b.covenantText} · Plafon: ${b.maxAmountText}", style = MaterialTheme.typography.labelSmall)
                Text("Karakter: ${b.style} Insentif bankir: ${b.incentive}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Catatan pasar: ${b.tell}", style = MaterialTheme.typography.labelSmall, color = KapitalSemanticColors.yellow)
                Text("Perkiraan peluang (untuk 5.000 jt RS): ${b.estimatedChance} · titik terlemah Anda: ${b.weakestFactor}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily), color = KapitalSemanticColors.blue)
                Button(
                    onClick = { selectedBank = b },
                    enabled = b.canApply,
                    colors = ButtonDefaults.buttonColors(containerColor = KapitalSemanticColors.blue, contentColor = Color.White),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(b.blockReason ?: "Ajukan Kredit ke ${b.name} (2 AP)")
                }
            }
        }
    }

    selectedBank?.let { b ->
        CreditApplyDialog(
            bank = b,
            onDismiss = { selectedBank = null },
            onSubmit = { amt, ten, purp, fl, bul ->
                onApply(b.id, amt, ten, purp, fl, bul)
                selectedBank = null
            }
        )
    }
}

@Composable
private fun CreditApplyDialog(
    bank: BankCard,
    onDismiss: () -> Unit,
    onSubmit: (amount: Int, tenor: Int, purpose: String, floating: Boolean, bullet: Boolean) -> Unit
) {
    val amounts = if (bank.id == "bk_dana") listOf(1000, 2000, 3000) else listOf(2000, 5000, 10000, 14000, 20000)
    val tenors = listOf(12, 24, 36, 48, 60).filter { it <= bank.maxTenor }.ifEmpty { listOf(bank.maxTenor) }
    val purposes = listOf(
        "MODAL_KERJA" to "Modal kerja",
        "EKSPANSI" to "Belanja modal & ekspansi",
        "PEMINTALAN" to "Proyek pabrik pemintalan",
        "REFINANCING" to "Refinancing utang lama"
    )
    var amount by remember { mutableIntStateOf(amounts.first()) }
    var tenor by remember { mutableIntStateOf(tenors.last()) }
    var purpose by remember { mutableStateOf(purposes.first().first) }
    var floating by remember { mutableStateOf(!bank.foreign) }
    var bullet by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pengajuan Kredit: ${bank.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("${bank.banker} akan menelaah laporan keuangan, agunan bebas, dan rekam jejak covenant Anda. Keputusan keluar dalam ±${bank.reviewTurns} giliran.",
                    style = MaterialTheme.typography.bodySmall)

                Text("1. Jumlah Pinjaman (jt RS)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    amounts.forEach { a ->
                        FilterChip(selected = amount == a, onClick = { amount = a }, label = { Text("${a / 1000}rb") })
                    }
                }

                Text("2. Tenor (bulan)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    tenors.forEach { t ->
                        FilterChip(selected = tenor == t, onClick = { tenor = t }, label = { Text("$t bln") })
                    }
                }

                Text("3. Penggunaan Dana", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                purposes.forEach { (k, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = purpose == k, onClick = { purpose = k })
                        Text(label, style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (!bank.foreign && bank.id != "bk_dana") {
                    Text("4. Struktur Suku Bunga", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = floating, onClick = { floating = true }, label = { Text("Mengambang") })
                        FilterChip(selected = !floating, onClick = { floating = false }, label = { Text("Tetap (+0,6%)") })
                    }
                }

                if (bank.id != "bk_dana") {
                    Text("5. Jadwal Pembayaran Pokok", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !bullet, onClick = { bullet = false }, label = { Text("Cicilan bulanan") })
                        FilterChip(selected = bullet, onClick = { bullet = true }, label = { Text("Bullet saat jatuh tempo") })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(amount, tenor, purpose, floating, bullet) },
                colors = ButtonDefaults.buttonColors(containerColor = KapitalSemanticColors.blue, contentColor = Color.White)
            ) { Text("Kirim Proposal (2 AP)") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
