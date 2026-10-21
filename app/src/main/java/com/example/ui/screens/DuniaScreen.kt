package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HistoryEdu
import androidx.compose.material.icons.outlined.HowToVote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.components.ConfidenceBadge
import com.example.ui.components.semanticToneColor
import com.example.ui.components.semanticToneContainerColor
import com.example.ui.theme.KapitalSemanticColors
import com.example.ui.theme.MonoFontFamily

@Composable
fun DuniaScreen(
    gameState: GameState,
    onOpenKronikDunia: () -> Unit,
    onInspectOrigin: (DataOriginInfo) -> Unit,
    modifier: Modifier = Modifier
) {
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Indikator Makro 8 Negara",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Ketuk untuk Asal data",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    gameState.countries.forEach { country ->
                        WorldCountryMacroCard(
                            country = country,
                            onInspectOrigin = onInspectOrigin
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(0.9f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KronikDuniaActionBanner(onOpenKronikDunia = onOpenKronikDunia)

                    PairwiseTensionSection(tensions = gameState.pairwiseTensions)

                    ElectionCalendarSection(countries = gameState.countries)
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
                KronikDuniaActionBanner(onOpenKronikDunia = onOpenKronikDunia)

                PairwiseTensionSection(tensions = gameState.pairwiseTensions)

                ElectionCalendarSection(countries = gameState.countries)

                Text(
                    text = "Daftar Negara & Indikator Makro (Inflasi, Bunga, Pertumbuhan)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                gameState.countries.forEach { country ->
                    WorldCountryMacroCard(
                        country = country,
                        onInspectOrigin = onInspectOrigin
                    )
                }
            }
        }
    }
}

@Composable
private fun KronikDuniaActionBanner(
    onOpenKronikDunia: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Arsip Sejarah & Pohon Sebab-Akibat",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Telusuri rantai transmisi makro-mikro sejak Januari 2030",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = onOpenKronikDunia,
                colors = ButtonDefaults.buttonColors(
                    containerColor = KapitalSemanticColors.blue,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("kronik_dunia_button")
            ) {
                Icon(Icons.Outlined.HistoryEdu, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Kronik Dunia")
            }
        }
    }
}

@Composable
private fun PairwiseTensionSection(
    tensions: List<PairwiseTension>
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
                text = "Skor Ketegangan Antarnegara (0–100) & Selat Strategis",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            tensions.forEach { item ->
                val col = semanticToneColor(item.tone)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.pairLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${item.score} / 100",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = col
                        )
                    }

                    // Bar 0..100
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = (item.score / 100f).coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(col)
                        )
                    }

                    Text(
                        text = "${item.straitOrBorder} · ${item.escalationStage}",
                        style = MaterialTheme.typography.labelSmall,
                        color = col
                    )
                    Text(
                        text = item.businessEffect,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(
                        color = KapitalSemanticColors.border.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ElectionCalendarSection(
    countries: List<Country>
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.HowToVote,
                    contentDescription = null,
                    tint = KapitalSemanticColors.yellow,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Kalender Pemilu & Siklus Politik",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            countries.forEach { country ->
                val isUrgent = country.id == "sundara"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${country.name} (${country.centralBankStyle})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = if (isUrgent) KapitalSemanticColors.yellowContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = country.electionCountdown,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = MonoFontFamily,
                                fontWeight = if (isUrgent) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isUrgent) KapitalSemanticColors.yellow else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorldCountryMacroCard(
    country: Country,
    onInspectOrigin: (DataOriginInfo) -> Unit
) {
    val tensionTone = when {
        country.tensionScore >= 60 -> SemanticTone.DANGER_RED
        country.tensionScore >= 40 -> SemanticTone.WARNING_YELLOW
        else -> SemanticTone.GOOD_GREEN
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
            .combinedClickable(
                onClick = { onInspectOrigin(country.originInfo) },
                onLongClick = { onInspectOrigin(country.originInfo) }
            )
            .testTag("world_country_card_${country.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${country.name} (${country.currencyCode})",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = country.archetype,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ConfidenceBadge(confidence = country.originInfo.confidence)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MacroStatCell("Inflasi", country.inflationRange, Modifier.weight(1f))
                MacroStatCell("Bunga Acuan", country.policyRate, Modifier.weight(1f))
                MacroStatCell("Pertumbuhan", country.gdpGrowth, Modifier.weight(1f))
                MacroStatCell(
                    label = "Ketegangan",
                    value = "${country.tensionScore}/100",
                    modifier = Modifier.weight(1f),
                    valueColor = semanticToneColor(tensionTone)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pemilu: ${country.electionCountdown} · Listrik: ${country.electricityReliability}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Asal data",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun MacroStatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = valueColor
            )
        }
    }
}
