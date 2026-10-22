package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.model.GameState
import com.example.ui.components.DataOriginBottomSheet
import com.example.ui.components.EventCardDialogModal
import com.example.ui.components.MonthlyReportDialog
import com.example.ui.screens.*
import com.example.ui.theme.KapitalSemanticColors
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.KapitalViewModel

const val ROUTE_MENU = "menu"
const val ROUTE_MARKAS = "markas"
const val ROUTE_PETA = "peta"
const val ROUTE_PERUSAHAAN = "perusahaan"
const val ROUTE_PASAR = "pasar"
const val ROUTE_BURSA = "bursa"
const val ROUTE_DUNIA = "dunia"
const val ROUTE_KORAN_PAGI = "koran_pagi"
const val ROUTE_JURNAL_KEPUTUSAN = "jurnal_keputusan/{decisionId}?option={option}"
const val ROUTE_KRONIK_DUNIA = "kronik_dunia"

data class BottomNavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: KapitalViewModel = viewModel()
            val gameState by viewModel.gameState.collectAsStateWithLifecycle()
            val dark = gameState.isDarkTheme ?: isSystemInDarkTheme()

            MyApplicationTheme(darkTheme = dark) {
                KapitalAppScaffold(
                    gameState = gameState,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
fun KapitalAppScaffold(
    gameState: GameState,
    viewModel: KapitalViewModel
) {
    val navController = rememberNavController()
    val isDarkNow = KapitalSemanticColors.isDark
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_MENU

    val bottomTabs = remember {
        listOf(
            BottomNavTab(ROUTE_MARKAS, "Markas", Icons.Filled.HomeWork, Icons.Outlined.HomeWork),
            BottomNavTab(ROUTE_PETA, "Peta", Icons.Filled.Map, Icons.Outlined.Map),
            BottomNavTab(ROUTE_PERUSAHAAN, "Perusahaan", Icons.Filled.Apartment, Icons.Outlined.Apartment),
            BottomNavTab(ROUTE_PASAR, "Pasar", Icons.AutoMirrored.Filled.ShowChart, Icons.AutoMirrored.Outlined.ShowChart),
            BottomNavTab(ROUTE_BURSA, "Bursa", Icons.Filled.AccountBalance, Icons.Outlined.AccountBalance),
            BottomNavTab(ROUTE_DUNIA, "Dunia", Icons.Filled.Public, Icons.Outlined.Public)
        )
    }

    val isMainTabRoute = bottomTabs.any { it.route == currentRoute }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            if (isMainTabRoute) {
                KapitalTopTurnBar(
                    gameState = gameState,
                    isDark = KapitalSemanticColors.isDark,
                    onToggleTheme = { viewModel.toggleTheme(it) },
                    onOpenMenu = { navController.navigate(ROUTE_MENU) { launchSingleTop = true } }
                )
            }
        },
        bottomBar = {
            if (isMainTabRoute) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.border(1.dp, KapitalSemanticColors.border)
                ) {
                    bottomTabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(ROUTE_MARKAS) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
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
                            modifier = Modifier.testTag("bottom_nav_${tab.route}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_MENU,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            composable(ROUTE_MENU) {
                MenuScreen(
                    gameState = gameState,
                    onContinue = {
                        navController.navigate(ROUTE_MARKAS) {
                            popUpTo(ROUTE_MENU) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onNewGame = { dif ->
                        viewModel.newGame(dif)
                        navController.navigate(ROUTE_MARKAS) {
                            popUpTo(ROUTE_MENU) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onToggleTheme = { viewModel.toggleTheme(isDarkNow) }
                )
            }

            composable(ROUTE_MARKAS) {
                MarkasScreen(
                    gameState = gameState,
                    onOpenKoranPagi = { navController.navigate(ROUTE_KORAN_PAGI) },
                    onOpenJurnalKeputusan = { decision ->
                        navController.navigate("jurnal_keputusan/${decision.id}")
                    },
                    onOpenEventDialog = { id -> viewModel.openEventDialog(true, id) },
                    onInspectOrigin = { viewModel.inspectDataOrigin(it) },
                    onAdvanceTurn = { viewModel.advanceTurn() },
                    onAdjustPrice = { viewModel.adjustPrice(it) },
                    onShowLastReport = { viewModel.showMonthlyReport() },
                    onNewGame = { viewModel.newGame() },
                    onOpenChronicle = { navController.navigate(ROUTE_KRONIK_DUNIA) }
                )
            }

            composable(ROUTE_PETA) {
                PetaScreen(
                    gameState = gameState,
                    onSelectLayer = { viewModel.setMapLayer(it) },
                    onSelectCountry = { viewModel.selectCountryForBottomSheet(it) },
                    onInspectOrigin = { viewModel.inspectDataOrigin(it) }
                )
            }

            composable(ROUTE_PERUSAHAAN) {
                PerusahaanScreen(
                    gameState = gameState,
                    onInspectOrigin = { viewModel.inspectDataOrigin(it) },
                    onSetSalesScheme = { viewModel.setSalesScheme(it) },
                    onSetFactoryScheme = { viewModel.setFactoryScheme(it) },
                    onAudit = { viewModel.auditExecutive(it) },
                    onConfront = { viewModel.confrontExecutive(it) },
                    onFire = { viewModel.fireExecutive(it) },
                    onContractAction = { contractId, actionId ->
                        if (actionId == "choose") navController.navigate("jurnal_keputusan/dec_termsheet?option=$contractId")
                        else viewModel.contractAction(contractId, actionId)
                    },
                    onApplyCredit = { b, a, t, p, f, bl -> viewModel.applyCredit(b, a, t, p, f, bl) },
                    onReviewOffer = { viewModel.reviewOffer(it) },
                    onAcceptOffer = { viewModel.acceptOffer(it) },
                    onDeclineOffer = { viewModel.declineOffer(it) },
                    onInvest = { viewModel.invest(it) },
                    onCorporateAction = { viewModel.corporateAction(it) }
                )
            }

            composable(ROUTE_PASAR) {
                PasarScreen(
                    gameState = gameState,
                    onInspectOrigin = { viewModel.inspectDataOrigin(it) }
                )
            }

            composable(ROUTE_BURSA) {
                BursaScreen(
                    gameState = gameState,
                    onBuy = { id, lots -> viewModel.buyStock(id, lots) },
                    onSell = { id, lots -> viewModel.sellStock(id, lots) },
                    onInspectOrigin = { viewModel.inspectDataOrigin(it) }
                )
            }

            composable(ROUTE_DUNIA) {
                DuniaScreen(
                    gameState = gameState,
                    onOpenKronikDunia = { navController.navigate(ROUTE_KRONIK_DUNIA) },
                    onInspectOrigin = { viewModel.inspectDataOrigin(it) }
                )
            }

            composable(ROUTE_KORAN_PAGI) {
                KoranPagiScreen(
                    gameState = gameState,
                    onBack = { navController.popBackStack() },
                    onCrossCheckNews = { viewModel.crossCheckNewsItem(it) },
                    onInspectOrigin = { viewModel.inspectDataOrigin(it) }
                )
            }

            composable(
                route = ROUTE_JURNAL_KEPUTUSAN,
                arguments = listOf(
                    navArgument("decisionId") { type = NavType.StringType },
                    navArgument("option") { type = NavType.StringType; defaultValue = "" }
                )
            ) { backStackEntry ->
                val decId = backStackEntry.arguments?.getString("decisionId") ?: ""
                val preselect = backStackEntry.arguments?.getString("option") ?: ""
                val decision = gameState.pendingDecisions.firstOrNull { it.id == decId }

                if (decision == null) {
                    // Keputusan sudah dieksekusi atau tidak lagi tersedia: kembali ke layar sebelumnya.
                    LaunchedEffect(decId) { navController.popBackStack() }
                } else {
                    JurnalKeputusanScreen(
                        decision = decision,
                        apCurrent = gameState.apCurrent,
                        currentCashText = gameState.markasIndicators.firstOrNull()?.valueText ?: "",
                        preselectedOption = preselect,
                        onBack = { navController.popBackStack() },
                        onReject = {
                            viewModel.rejectDecision(decision.id)
                            navController.popBackStack()
                        },
                        onExecuteDecision = { optionId, reason, prediction, conf, predictedCash, failure ->
                            val ok = viewModel.submitDecisionJournal(
                                decisionId = decision.id,
                                optionId = optionId,
                                strategicReason = reason,
                                numericPrediction = prediction,
                                confidencePct = conf,
                                predictedCash = predictedCash,
                                failureScenario = failure
                            )
                            if (ok) navController.popBackStack()
                        }
                    )
                }
            }

            composable(ROUTE_KRONIK_DUNIA) {
                KronikDuniaScreen(
                    chronicles = gameState.worldChronicles,
                    counterfactualText = gameState.counterfactualText,
                    onRunCounterfactual = { viewModel.runCounterfactual(it) },
                    onBack = {
                        viewModel.clearCounterfactual()
                        navController.popBackStack()
                    }
                )
            }
        }

        // Dialog Kartu Event (3 pilihan respons "menurut Direktur Operasi")
        if (gameState.showEventDialog) {
            EventCardDialogModal(
                eventDialog = gameState.activeEventDialog,
                onDismiss = { viewModel.openEventDialog(false) },
                onSelectOption = { viewModel.resolveEventOption(it) },
                apCurrent = gameState.apCurrent
            )
        }

        // Hasil keputusan kartu kejadian
        gameState.eventOutcomeText?.let { txt ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { viewModel.clearEventOutcome() },
                title = { androidx.compose.material3.Text("Hasil keputusan") },
                text = { androidx.compose.material3.Text(txt) },
                confirmButton = { androidx.compose.material3.Button(onClick = { viewModel.clearEventOutcome() }) { androidx.compose.material3.Text("Mengerti") } }
            )
        }

        // Laporan akhir bulan setelah "Akhiri giliran"
        if (gameState.showMonthlyReport) {
            MonthlyReportDialog(
                title = gameState.monthlyReportTitle,
                lines = gameState.monthlyReportLines,
                onDismiss = { viewModel.dismissMonthlyReport() }
            )
        }

        // Bottom Sheet "Asal data" saat angka diketuk / ditahan
        val inspectedOrigin = gameState.inspectedDataOrigin
        if (inspectedOrigin != null) {
            DataOriginBottomSheet(
                originInfo = inspectedOrigin,
                onDismiss = { viewModel.inspectDataOrigin(null) }
            )
        }
    }
}

/**
 * Bilah atas: tanggal dan giliran di kiri (mengambil sisa lebar), chip AP, runway, dan tombol tema di kanan.
 */
@Composable
fun KapitalTopTurnBar(
    gameState: GameState,
    isDark: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onOpenMenu: () -> Unit = {}
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenMenu, modifier = Modifier.size(48.dp).testTag("open_menu_button")) {
                    Icon(Icons.Filled.Menu, contentDescription = "Menu utama", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = gameState.dateText,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = "Giliran ${gameState.turn} · Era ${gameState.eraNumber}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = KapitalSemanticColors.blueContainer,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .border(1.dp, KapitalSemanticColors.blue.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                            .testTag("chip_ap")
                    ) {
                        Text(
                            text = "AP ${gameState.apCurrent}/${gameState.apMax}",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = KapitalSemanticColors.blue,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }

                    val runwayIsDanger = gameState.runwayMonths <= 5
                    val runwayFg = if (runwayIsDanger) KapitalSemanticColors.red else KapitalSemanticColors.green
                    val runwayBg = if (runwayIsDanger) KapitalSemanticColors.redContainer else KapitalSemanticColors.greenContainer

                    Surface(
                        color = runwayBg,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .border(1.dp, runwayFg.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                            .testTag("chip_runway")
                    ) {
                        Text(
                            text = if (gameState.runwayMonths >= 99) "Kas stabil" else "Runway ${gameState.runwayMonths} bln",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = runwayFg,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }

                    IconButton(
                        onClick = { onToggleTheme(isDark) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("toggle_theme_button")
                    ) {
                        Icon(
                            imageVector = if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = "Ganti mode terang atau gelap",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            HorizontalDivider(color = KapitalSemanticColors.border)
        }
    }
}
