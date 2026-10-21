package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.ShowChart
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.model.GameState
import com.example.ui.components.DataOriginBottomSheet
import com.example.ui.components.EventCardDialogModal
import com.example.ui.screens.*
import com.example.ui.theme.KapitalSemanticColors
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.KapitalViewModel

const val ROUTE_MARKAS = "markas"
const val ROUTE_PETA = "peta"
const val ROUTE_PERUSAHAAN = "perusahaan"
const val ROUTE_PASAR = "pasar"
const val ROUTE_DUNIA = "dunia"
const val ROUTE_KORAN_PAGI = "koran_pagi"
const val ROUTE_JURNAL_KEPUTUSAN = "jurnal_keputusan/{decisionId}"
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

            MyApplicationTheme(darkTheme = gameState.isDarkTheme) {
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
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_MARKAS

    val bottomTabs = remember {
        listOf(
            BottomNavTab(ROUTE_MARKAS, "Markas", Icons.Filled.HomeWork, Icons.Outlined.HomeWork),
            BottomNavTab(ROUTE_PETA, "Peta", Icons.Filled.Map, Icons.Outlined.Map),
            BottomNavTab(ROUTE_PERUSAHAAN, "Perusahaan", Icons.Filled.Apartment, Icons.Outlined.Apartment),
            BottomNavTab(ROUTE_PASAR, "Pasar", Icons.Filled.ShowChart, Icons.Outlined.ShowChart),
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
                    onToggleTheme = { viewModel.toggleTheme() },
                    onSwitchRole = { viewModel.switchRole(it) }
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
                                    popUpTo(navController.graph.findStartDestination().id) {
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
            startDestination = ROUTE_MARKAS,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            composable(ROUTE_MARKAS) {
                MarkasScreen(
                    gameState = gameState,
                    onOpenKoranPagi = { navController.navigate(ROUTE_KORAN_PAGI) },
                    onOpenJurnalKeputusan = { decision ->
                        navController.navigate("jurnal_keputusan/${decision.id}")
                    },
                    onOpenEventDialog = { viewModel.openEventDialog(true) },
                    onInspectOrigin = { viewModel.inspectDataOrigin(it) },
                    onAdvanceTurn = { viewModel.advanceTurn() }
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
                    onChangeIncentiveScheme = { viewModel.changeIncentiveScheme(it) },
                    onExecuteContractAction = { viewModel.executeContractAction(it) }
                )
            }

            composable(ROUTE_PASAR) {
                PasarScreen(
                    gameState = gameState,
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
                arguments = listOf(navArgument("decisionId") { type = NavType.StringType })
            ) { backStackEntry ->
                val decId = backStackEntry.arguments?.getString("decisionId") ?: "dec_pemintalan"
                val decision = gameState.pendingDecisions.firstOrNull { it.id == decId }
                    ?: gameState.pendingDecisions.first()

                JurnalKeputusanScreen(
                    decision = decision,
                    onBack = { navController.popBackStack() },
                    onExecuteDecision = { reason, prediction, conf, failure ->
                        viewModel.submitDecisionJournal(
                            decisionId = decision.id,
                            strategicReason = reason,
                            numericPrediction = prediction,
                            confidencePct = conf,
                            failureScenario = failure
                        )
                        navController.popBackStack()
                    }
                )
            }

            composable(ROUTE_KRONIK_DUNIA) {
                KronikDuniaScreen(
                    chronicles = gameState.worldChronicles,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // Dialog Kartu Event (3 pilihan respons "menurut Direktur Operasi")
        if (gameState.showEventDialog) {
            EventCardDialogModal(
                eventDialog = gameState.activeEventDialog,
                onDismiss = { viewModel.openEventDialog(false) },
                onSelectOption = { viewModel.resolveEventOption(it) }
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
 * Bilah atas sesuai spesifikasi:
 * Kiri: "Maret 2033", di bawahnya "Giliran 39 · Era 1".
 * Kanan: chip "AP 4/7" dan chip "Runway 5 bln", serta tombol mode Terang/Gelap.
 */
@Composable
fun KapitalTopTurnBar(
    gameState: GameState,
    onToggleTheme: () -> Unit,
    onSwitchRole: (String) -> Unit
) {
    var showRoleMenu by remember { mutableStateOf(false) }
    val roles = listOf(
        "CEO PT Benang Sundara",
        "Gubernur Bank Sentral",
        "Menteri Keuangan",
        "Kepala Bappenas"
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Kiri: "Maret 2033", di bawahnya "Giliran 39 · Era 1"
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { showRoleMenu = true }
                ) {
                    Text(
                        text = gameState.dateText,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Giliran ${gameState.turn} · Era ${gameState.eraNumber} · ${gameState.activeRole}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    DropdownMenu(
                        expanded = showRoleMenu,
                        onDismissRequest = { showRoleMenu = false }
                    ) {
                        roles.forEach { role ->
                            DropdownMenuItem(
                                text = { Text(role) },
                                onClick = {
                                    onSwitchRole(role)
                                    showRoleMenu = false
                                }
                            )
                        }
                    }
                }

                // Kanan: chip "AP 4/7" dan chip "Runway 5 bln" + Tombol Mode Terang/Gelap
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Chip AP 4/7
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
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    // Chip Runway 5 bln
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
                            text = "Runway ${gameState.runwayMonths} bln",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = runwayFg,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            HorizontalDivider(color = KapitalSemanticColors.border)
        }
    }
}
