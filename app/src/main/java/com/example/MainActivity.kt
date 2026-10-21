package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.model.*
import com.example.ui.components.DataOriginBottomSheet
import com.example.ui.components.EventCardDialogModal
import com.example.ui.components.semanticToneColor
import com.example.ui.components.semanticToneContainerColor
import com.example.ui.screens.*
import com.example.ui.theme.KapitalSemanticColors
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NotoSerifFontFamily
import com.example.viewmodel.KapitalViewModel

object KapitalRoutes {
    const val MARKAS = "markas"
    const val PETA = "peta"
    const val PERUSAHAAN = "perusahaan"
    const val PASAR = "pasar"
    const val DUNIA = "dunia"
    const val KORAN_PAGI = "koran_pagi"
    const val JURNAL_KEPUTUSAN = "jurnal_keputusan/{decisionId}"
    const val KRONIK_DUNIA = "kronik_dunia"

    fun jurnalRoute(decisionId: String) = "jurnal_keputusan/$decisionId"
}

data class TopLevelTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: KapitalViewModel = viewModel()
            val gameState by viewModel.gameState.collectAsStateWithLifecycle()

            MyApplicationTheme(darkTheme = gameState.isDarkTheme) {
                KapitalAppRoot(
                    gameState = gameState,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
fun KapitalAppRoot(
    gameState: GameState,
    viewModel: KapitalViewModel
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: KapitalRoutes.MARKAS

    val tabs = remember {
        listOf(
            TopLevelTab(
                route = KapitalRoutes.MARKAS,
                label = "Markas",
                selectedIcon = Icons.Filled.Home,
                unselectedIcon = Icons.Outlined.Home,
                testTag = "nav_tab_markas"
            ),
            TopLevelTab(
                route = KapitalRoutes.PETA,
                label = "Peta",
                selectedIcon = Icons.Filled.Map,
                unselectedIcon = Icons.Outlined.Map,
                testTag = "nav_tab_peta"
            ),
            TopLevelTab(
                route = KapitalRoutes.PERUSAHAAN,
                label = "Perusahaan",
                selectedIcon = Icons.Filled.Business,
                unselectedIcon = Icons.Outlined.Business,
                testTag = "nav_tab_perusahaan"
            ),
            TopLevelTab(
                route = KapitalRoutes.PASAR,
                label = "Pasar",
                selectedIcon = Icons.Filled.AccountBalance,
                unselectedIcon = Icons.Outlined.AccountBalance,
                testTag = "nav_tab_pasar"
            ),
            TopLevelTab(
                route = KapitalRoutes.DUNIA,
                label = "Dunia",
                selectedIcon = Icons.Filled.Public,
                unselectedIcon = Icons.Outlined.Public,
                testTag = "nav_tab_dunia"
            )
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscapeOrExpanded = maxWidth >= 700.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                KapitalTopAppBar(
                    gameState = gameState,
                    onToggleTheme = { viewModel.toggleTheme() },
                    onSwitchRole = { viewModel.switchRole(it) },
                    onOpenSaveDialog = { viewModel.openSaveDialog(true) }
                )
            },
            bottomBar = {
                if (!isLandscapeOrExpanded) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, KapitalSemanticColors.border)
                    ) {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(navController, tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = KapitalSemanticColors.blue,
                                    selectedTextColor = KapitalSemanticColors.blue,
                                    indicatorColor = KapitalSemanticColors.blueContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isLandscapeOrExpanded) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .border(1.dp, KapitalSemanticColors.border)
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(navController, tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = KapitalSemanticColors.blue,
                                    selectedTextColor = KapitalSemanticColors.blue,
                                    indicatorColor = KapitalSemanticColors.blueContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier
                                    .padding(vertical = 2.dp)
                                    .testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        gameState.statusBanner?.let { msg ->
                            StatusToastBar(message = msg, onDismiss = { viewModel.clearBanner() })
                        }
                        NavHost(
                            navController = navController,
                            startDestination = KapitalRoutes.MARKAS,
                            modifier = Modifier.weight(1f)
                        ) {
                            composable(KapitalRoutes.MARKAS) {
                                MarkasScreen(
                                    gameState = gameState,
                                    onOpenKoranPagi = {
                                        navController.navigate(KapitalRoutes.KORAN_PAGI) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onOpenJurnalKeputusan = { decision ->
                                        navController.navigate(KapitalRoutes.jurnalRoute(decision.id)) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onOpenEventDialog = { eventId -> viewModel.openEventDialog(true, eventId) },
                                    onRejectProposal = { decisionId -> viewModel.rejectProposal(decisionId) },
                                    onEquityCure = { viewModel.executeContractAction("equity_cure") },
                                    onNewGame = { viewModel.newRandomGame() },
                                    onInspectOrigin = { viewModel.inspectDataOrigin(it) },
                                    onAdvanceTurn = { viewModel.advanceTurn() }
                                )
                            }

                            composable(KapitalRoutes.PETA) {
                                PetaScreen(
                                    gameState = gameState,
                                    onSelectLayer = { viewModel.setMapLayer(it) },
                                    onSelectCountry = { viewModel.selectCountryForBottomSheet(it) },
                                    onInspectOrigin = { viewModel.inspectDataOrigin(it) }
                                )
                            }

                            composable(KapitalRoutes.PERUSAHAAN) {
                                PerusahaanScreen(
                                    gameState = gameState,
                                    onInspectOrigin = { viewModel.inspectDataOrigin(it) },
                                    onChangeIncentiveScheme = { viewModel.changeIncentiveScheme(it) },
                                    onChangeFactoryScheme = { viewModel.changeFactoryScheme(it) },
                                    onExecuteContractAction = { viewModel.executeContractAction(it) },
                                    onAuditExec = { viewModel.auditExecutive(it) },
                                    onConfrontExec = { viewModel.confrontExecutive(it) },
                                    onFireExec = { viewModel.fireExecutive(it) },
                                    onStartInvestment = { viewModel.startInvestment(it) },
                                    onOpenSubsidiary = { viewModel.openSubsidiary(it) },
                                    onRepatriateSubsidiary = { viewModel.repatriateSubsidiary(it) },
                                    onSetTransferPricing = { viewModel.setTransferPricingMode(it) },
                                    onBorrowForeignSub = { id, amt -> viewModel.borrowForeignSub(id, amt) },
                                    onIssueCorporateBond = { amt, tenor -> viewModel.issueCorporateBond(amt, tenor) },
                                    onExecuteIpo = { pct -> viewModel.executeIpo(pct) },
                                    onOpenCaptiveBank = { viewModel.openCaptiveBank() },
                                    onMediaLobby = { intensity -> viewModel.mediaLobby(intensity) },
                                    onCarbonAction = { type -> viewModel.carbonAction(type) },
                                    onAppointSuccessor = { id -> viewModel.appointSuccessor(id) },
                                    onApplyCredit = { bankId, amount, tenor, purpose, floating, bullet ->
                                        viewModel.applyCredit(bankId, amount, tenor, purpose, floating, bullet)
                                    },
                                    onReviewCreditOffer = { viewModel.reviewCreditOffer(it) },
                                    onAcceptCreditOffer = { viewModel.acceptCreditOffer(it) },
                                    onDeclineCreditOffer = { viewModel.declineCreditOffer(it) }
                                )
                            }

                            composable(KapitalRoutes.PASAR) {
                                PasarScreen(
                                    gameState = gameState,
                                    onInspectOrigin = { viewModel.inspectDataOrigin(it) },
                                    onAdjustPrice = { viewModel.adjustPrice(it) },
                                    onHedgeCotton = { viewModel.executeContractAction("hedge_cotton") },
                                    onHedgeFx = { viewModel.executeContractAction("hedge_fx") },
                                    onAcquireRival = { viewModel.acquireRival(it) },
                                    onBuyStock = { id, lots -> viewModel.buyStock(id, lots) },
                                    onSellStock = { id, lots -> viewModel.sellStock(id, lots) }
                                )
                            }

                            composable(KapitalRoutes.DUNIA) {
                                DuniaScreen(
                                    gameState = gameState,
                                    onOpenKronikDunia = {
                                        navController.navigate(KapitalRoutes.KRONIK_DUNIA) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onSelectCountry = { country ->
                                        viewModel.selectCountryForBottomSheet(country)
                                        navigateTopLevel(navController, KapitalRoutes.PETA)
                                    },
                                    onSetDifficulty = { viewModel.setDifficulty(it) }
                                )
                            }

                            composable(KapitalRoutes.KORAN_PAGI) {
                                KoranPagiScreen(
                                    gameState = gameState,
                                    onBack = { navController.popBackStack() },
                                    onCrossCheckNews = { viewModel.crossCheckNewsItem(it) },
                                    onInspectOrigin = { viewModel.inspectDataOrigin(it) }
                                )
                            }

                            composable(KapitalRoutes.JURNAL_KEPUTUSAN) { entry ->
                                val decisionId = entry.arguments?.getString("decisionId") ?: "dec_1"
                                val decision = gameState.pendingDecisions.find { it.id == decisionId }
                                    ?: gameState.pendingDecisions.first()
                                val currentCash = gameState.markasIndicators.firstOrNull { it.id == "ind_cash" }?.valueText ?: ""

                                JurnalKeputusanScreen(
                                    decision = decision,
                                    currentCashText = currentCash,
                                    onBack = { navController.popBackStack() },
                                    onExecuteDecision = { optionId, reason, pred, conf, fail ->
                                        viewModel.submitDecisionJournal(
                                            decisionId = decision.id,
                                            optionId = optionId,
                                            strategicReason = reason,
                                            numericPrediction = pred,
                                            confidencePct = conf,
                                            failureScenario = fail
                                        )
                                        navController.popBackStack()
                                    }
                                )
                            }

                            composable(KapitalRoutes.KRONIK_DUNIA) {
                                KronikDuniaScreen(
                                    chronicles = gameState.worldChronicles,
                                    onBack = { navController.popBackStack() },
                                    onBranchCounterfactual = { actionIdx ->
                                        viewModel.branchFromAction(actionIdx)
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }
                    }

                    // Global Bottom Sheet: "Asal data" (provenance & formula)
                    gameState.inspectedDataOrigin?.let { origin ->
                        DataOriginBottomSheet(
                            originInfo = origin,
                            onDismiss = { viewModel.inspectDataOrigin(null) }
                        )
                    }

                    // Global Modal: Kartu Event 3 Pilihan
                    if (gameState.showEventDialog) {
                        EventCardDialogModal(
                            eventDialog = gameState.activeEventDialog,
                            onDismiss = { viewModel.openEventDialog(false) },
                            onSelectOption = { viewModel.resolveEventOption(it) }
                        )
                    }

                    // Global Modal: Ringkasan pergantian giliran
                    if (gameState.showTurnReportDialog) {
                        TurnReportDialogModal(
                            title = gameState.turnReportTitle,
                            items = gameState.turnReport,
                            gameOverReason = gameState.gameOverReason,
                            isVictory = gameState.isVictory,
                            campaignEvaluation = gameState.campaignEvaluation,
                            onDismiss = { viewModel.dismissTurnReport() },
                            onNewGame = { viewModel.newRandomGame() }
                        )
                    }

                    // Global Modal: Simpan / Muat 3 Slot + Seed Baru
                    if (gameState.showSaveDialog) {
                        SaveLoadDialogModal(
                            gameState = gameState,
                            onDismiss = { viewModel.openSaveDialog(false) },
                            onSaveSlot = { viewModel.saveToSlot(it) },
                            onLoadSlot = { viewModel.loadFromSlot(it) },
                            onNewSeed = { viewModel.newGame(it) },
                            onNewRandom = { viewModel.newRandomGame() },
                            onSetDifficulty = { viewModel.setDifficulty(it) }
                        )
                    }
                }
            }
        }
    }
}

private fun navigateTopLevel(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun KapitalTopAppBar(
    gameState: GameState,
    onToggleTheme: () -> Unit,
    onSwitchRole: (String) -> Unit,
    onOpenSaveDialog: () -> Unit
) {
    var roleMenuExpanded by remember { mutableStateOf(false) }
    val roles = listOf(
        "CEO PT Benang Sundara",
        "Direktur Utama Holding",
        "Ketua Komite Risiko",
        "Komisaris Utama (Era 5)"
    )

    val runwayTone = when {
        gameState.runwayMonths < 6 -> SemanticTone.DANGER_RED
        gameState.runwayMonths <= 12 -> SemanticTone.WARNING_YELLOW
        else -> SemanticTone.GOOD_GREEN
    }
    val runwayColor = semanticToneColor(runwayTone)
    val runwayBg = semanticToneContainerColor(runwayTone)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, KapitalSemanticColors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Date & Turn/Era/Role
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = gameState.dateText,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("top_bar_date")
                    )

                    Box {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { roleMenuExpanded = true }
                                .testTag("role_switcher_chip")
                        ) {
                            Text(
                                text = gameState.activeRole,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = roleMenuExpanded,
                            onDismissRequest = { roleMenuExpanded = false }
                        ) {
                            roles.forEach { role ->
                                DropdownMenuItem(
                                    text = { Text(role, style = MaterialTheme.typography.bodySmall) },
                                    onClick = {
                                        onSwitchRole(role)
                                        roleMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Giliran ${gameState.turn} · ${gameState.eraName} · ${gameState.difficultyLabel}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("top_bar_turn_era")
                )
            }

            // Right: AP chip, Runway chip, Save & Theme toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = KapitalSemanticColors.blueContainer,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .border(1.dp, KapitalSemanticColors.blue.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                        .testTag("chip_ap")
                ) {
                    Text(
                        text = "AP ${gameState.apCurrent}/${gameState.apMax}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = KapitalSemanticColors.blue,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }

                Surface(
                    color = runwayBg,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .border(1.dp, runwayColor.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                        .testTag("chip_runway")
                ) {
                    Text(
                        text = if (gameState.runwayMonths >= 99) "Kas +/bln" else "Runway ${gameState.runwayMonths} bln",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = runwayColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }

                IconButton(
                    onClick = onOpenSaveDialog,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("open_save_dialog_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Save,
                        contentDescription = "Simpan / Muat / Seed Skenario",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("toggle_theme_button")
                ) {
                    Icon(
                        imageVector = if (gameState.isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                        contentDescription = "Ganti Mode Terang / Gelap",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusToastBar(message: String, onDismiss: () -> Unit) {
    Surface(
        color = KapitalSemanticColors.blueContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDismiss() }
            .border(1.dp, KapitalSemanticColors.blue.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Tutup",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = KapitalSemanticColors.blue,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

@Composable
private fun TurnReportDialogModal(
    title: String,
    items: List<TurnReportItem>,
    gameOverReason: String?,
    isVictory: Boolean,
    campaignEvaluation: CampaignEvaluationUi?,
    onDismiss: () -> Unit,
    onNewGame: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(8.dp))
                .testTag("turn_report_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Laporan Pergantian Bulan",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                    color = KapitalSemanticColors.blue
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium.copy(fontFamily = NotoSerifFontFamily),
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (gameOverReason != null) {
                    Surface(
                        color = KapitalSemanticColors.redContainer,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = gameOverReason,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = KapitalSemanticColors.red,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (isVictory || gameOverReason != null) {
                    campaignEvaluation?.let { ev ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Evaluasi Kampanye: Nilai ${ev.grade} (${ev.totalScore}/100) · ${ev.archetypeTitle}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = ev.summaryText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = KapitalSemanticColors.border)

                items.forEach { item ->
                    val col = semanticToneColor(item.tone)
                    val bg = semanticToneContainerColor(item.tone)
                    Surface(
                        color = bg,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Medium
                            ),
                            color = col,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (gameOverReason != null) {
                        OutlinedButton(
                            onClick = onNewGame,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Text("Permainan Baru")
                        }
                    }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KapitalSemanticColors.blue,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("dismiss_turn_report_button")
                    ) {
                        Text("Lanjut ke Markas")
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveLoadDialogModal(
    gameState: GameState,
    onDismiss: () -> Unit,
    onSaveSlot: (Int) -> Unit,
    onLoadSlot: (Int) -> Unit,
    onNewSeed: (Long) -> Unit,
    onNewRandom: () -> Unit,
    onSetDifficulty: (String) -> Unit
) {
    var seedInput by rememberSaveable { mutableStateOf(gameState.seed.toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(8.dp))
                .testTag("save_load_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Simpan, Muat & Skenario (Deterministik)",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = NotoSerifFontFamily),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Skenario Aktif (Seed ${gameState.seed}): ${gameState.scenarioTitle}",
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                            color = KapitalSemanticColors.blue
                        )
                        Text(
                            text = gameState.scenarioStory,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Difficulty Quick Selector
                Text(
                    text = "Tingkat Kesulitan: ${gameState.difficultyLabel}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "MAGANG" to "Magang",
                        "MANAJER" to "Manajer",
                        "DIREKTUR" to "Direktur",
                        "KRISIS_ABADI" to "Krisis"
                    ).forEach { (code, lbl) ->
                        FilterChip(
                            selected = gameState.difficultyName == code,
                            onClick = { onSetDifficulty(code) },
                            label = { Text(lbl, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                gameState.saveSlots.forEach { slot ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Slot ${slot.slot}", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    slot.summary,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { onSaveSlot(slot.slot) },
                                    modifier = Modifier.heightIn(min = 40.dp)
                                ) {
                                    Text("Simpan", style = MaterialTheme.typography.labelSmall)
                                }
                                if (slot.exists) {
                                    Button(
                                        onClick = { onLoadSlot(slot.slot) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = KapitalSemanticColors.blue,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.heightIn(min = 40.dp)
                                    ) {
                                        Text("Muat", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = KapitalSemanticColors.border)

                Text(
                    text = "Mulai Skenario Baru (6 Premis & Pesaing Acak dari Seed)",
                    style = MaterialTheme.typography.labelLarge
                )
                OutlinedTextField(
                    value = seedInput,
                    onValueChange = { seedInput = it.filter { ch -> ch.isDigit() || ch == '-' } },
                    label = { Text("Angka Seed") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val parsed = seedInput.toLongOrNull() ?: 203303L
                            onNewSeed(parsed)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Mulai Seed Ini")
                    }
                    Button(
                        onClick = onNewRandom,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KapitalSemanticColors.blue,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Skenario Acak")
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Tutup")
                }
            }
        }
    }
}
