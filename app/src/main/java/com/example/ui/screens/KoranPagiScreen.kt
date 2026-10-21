package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.components.ConfidenceBadge
import com.example.ui.theme.KapitalSemanticColors
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.NotoSerifFontFamily
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KoranPagiScreen(
    gameState: GameState,
    onBack: () -> Unit,
    onCrossCheckNews: (String) -> Unit,
    onInspectOrigin: (DataOriginInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val editions = gameState.morningPaperEdition
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { editions.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header Koran Pagi
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("koran_pagi_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Kembali ke Markas"
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Koran pagi · 5 sudut pandang",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Geser untuk membandingkan peristiwa yang sama",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        color = KapitalSemanticColors.blueContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "AP ${gameState.apCurrent}/${gameState.apMax}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = KapitalSemanticColors.blue,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Tab 5 Media (Bisnis, Pro-Pemerintah, Oposisi, Sensasional, Independen)
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.surface,
                    edgePadding = 16.dp,
                    divider = { HorizontalDivider(color = KapitalSemanticColors.border) }
                ) {
                    editions.forEachIndexed { idx, item ->
                        Tab(
                            selected = pagerState.currentPage == idx,
                            onClick = { scope.launch { pagerState.animateScrollToPage(idx) } },
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("media_tab_${idx}"),
                            text = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = item.mediaSlant.outletName,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (pagerState.currentPage == idx) KapitalSemanticColors.blue
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = item.mediaSlant.slantLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        // HorizontalPager antara 5 media
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { pageIdx ->
            val news = editions[pageIdx]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${news.mediaSlant.outletName} · ${news.mediaSlant.slantLabel}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = KapitalSemanticColors.blue
                                )
                                Text(
                                    text = news.dateLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ConfidenceBadge(confidence = news.confidence)
                        }

                        if (news.isRumor) {
                            Surface(
                                color = KapitalSemanticColors.redContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "KOLOM RUMOR: Dapat berasal dari bocoran nyata atau pesanan spekulan/short-seller",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = KapitalSemanticColors.red,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = KapitalSemanticColors.border)

                        // Judul berita memakai huruf Serif (Noto Serif)
                        Text(
                            text = news.headline,
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontFamily = NotoSerifFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = news.summary,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Profil Redaksi & Kepentingan Tersembunyi:",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = news.mediaSlant.editorialProfile,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onInspectOrigin(news.originInfo) },
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Asal data")
                            }

                            Button(
                                onClick = { onCrossCheckNews(news.id) },
                                enabled = !news.crossChecked && gameState.apCurrent >= 1,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .heightIn(min = 48.dp)
                                    .testTag("crosscheck_news_${news.id}")
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.FactCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (news.crossChecked) "Sudah diperiksa silang" else "Periksa silang (1 AP)")
                            }
                        }
                    }
                }

                // Matriks Perbandingan Cepat 5 Media atas Peristiwa yang Sama
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
                            text = "Topik yang sama di 5 media",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        editions.forEachIndexed { idx, ed ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${idx + 1}. ${ed.mediaSlant.outletName} (${ed.mediaSlant.slantLabel})",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (idx == pageIdx) KapitalSemanticColors.blue else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = ed.headline,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = NotoSerifFontFamily),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                ConfidenceBadge(confidence = ed.confidence)
                            }
                        }
                    }
                }
            }
        }
    }
}
