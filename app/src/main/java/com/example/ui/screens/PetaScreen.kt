package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsBoat
import androidx.compose.material.icons.outlined.Factory
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Warehouse
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.components.ConfidenceBadge
import com.example.ui.components.semanticToneColor
import com.example.ui.theme.KapitalSemanticColors
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.SansFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetaScreen(
    gameState: GameState,
    onSelectLayer: (MapLayerFilter) -> Unit,
    onSelectCountry: (Country?) -> Unit,
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
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1.35f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MapLayerFilterBar(
                        activeLayer = gameState.activeMapLayer,
                        onSelectLayer = onSelectLayer
                    )

                    WorldPolygonCanvasCard(
                        countries = gameState.countries,
                        pieces = gameState.mapPieces,
                        activeLayer = gameState.activeMapLayer,
                        onCountryTapped = { onSelectCountry(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )

                    MapTokenLegendBar()
                }

                Column(
                    modifier = Modifier
                        .weight(0.85f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "8 Negara Inti (Ketuk untuk detail)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    gameState.countries.forEach { country ->
                        CompactCountryRowCard(
                            country = country,
                            activeLayer = gameState.activeMapLayer,
                            onClick = { onSelectCountry(country) }
                        )
                    }
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
                MapLayerFilterBar(
                    activeLayer = gameState.activeMapLayer,
                    onSelectLayer = onSelectLayer
                )

                WorldPolygonCanvasCard(
                    countries = gameState.countries,
                    pieces = gameState.mapPieces,
                    activeLayer = gameState.activeMapLayer,
                    onCountryTapped = { onSelectCountry(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                )

                MapTokenLegendBar()

                Text(
                    text = "Daftar Cepat 8 Negara (Ketuk negara di peta atau di bawah)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                gameState.countries.forEach { country ->
                    CompactCountryRowCard(
                        country = country,
                        activeLayer = gameState.activeMapLayer,
                        onClick = { onSelectCountry(country) }
                    )
                }
            }
        }

        // Bottom Sheet Ringkasan Negara saat Negara Diketuk
        val selectedCountry = gameState.selectedCountryForSheet
        if (selectedCountry != null) {
            ModalBottomSheet(
                onDismissRequest = { onSelectCountry(null) },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                CountrySummaryBottomSheetContent(
                    country = selectedCountry,
                    onInspectOrigin = { origin ->
                        onSelectCountry(null)
                        onInspectOrigin(origin)
                    },
                    onDismiss = { onSelectCountry(null) }
                )
            }
        }
    }
}

@Composable
private fun MapLayerFilterBar(
    activeLayer: MapLayerFilter,
    onSelectLayer: (MapLayerFilter) -> Unit
) {
    val chipScroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(chipScroll),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MapLayerFilter.entries.forEach { layer ->
            val selected = layer == activeLayer
            FilterChip(
                selected = selected,
                onClick = { onSelectLayer(layer) },
                label = {
                    Text(
                        text = layer.label,
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = KapitalSemanticColors.blueContainer,
                    selectedLabelColor = KapitalSemanticColors.blue
                ),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("map_layer_chip_${layer.name.lowercase()}")
            )
        }
    }
}

@Composable
private fun WorldPolygonCanvasCard(
    countries: List<Country>,
    pieces: List<MapPiece>,
    activeLayer: MapLayerFilter,
    onCountryTapped: (Country) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val surfaceColor = MaterialTheme.colorScheme.surface
    val borderColor = KapitalSemanticColors.border
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    val redCol = KapitalSemanticColors.red
    val redBg = KapitalSemanticColors.redContainer
    val yellowCol = KapitalSemanticColors.yellow
    val yellowBg = KapitalSemanticColors.yellowContainer
    val greenCol = KapitalSemanticColors.green
    val greenBg = KapitalSemanticColors.greenContainer
    val blueCol = KapitalSemanticColors.blue
    val blueBg = KapitalSemanticColors.blueContainer
    val neutralBg = MaterialTheme.colorScheme.surfaceVariant

    Surface(
        color = surfaceColor,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .testTag("world_polygon_canvas")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(countries) {
                    detectTapGestures { tapOffset ->
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val nx = tapOffset.x / w
                        val ny = tapOffset.y / h

                        val tapped = countries.firstOrNull { c ->
                            isPointInPolygon(nx, ny, c.polygonPoints)
                        }
                        if (tapped != null) {
                            onCountryTapped(tapped)
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height

            // Subtle coordinate grid lines
            val gridDash = PathEffect.dashPathEffect(floatArrayOf(4f, 8f))
            for (i in 1..3) {
                drawLine(
                    color = borderColor.copy(alpha = 0.5f),
                    start = Offset((w / 4f) * i, 0f),
                    end = Offset((w / 4f) * i, h),
                    strokeWidth = 1f,
                    pathEffect = gridDash
                )
                drawLine(
                    color = borderColor.copy(alpha = 0.5f),
                    start = Offset(0f, (h / 4f) * i),
                    end = Offset(w, (h / 4f) * i),
                    strokeWidth = 1f,
                    pathEffect = gridDash
                )
            }

            // Sea lanes / Strategic Straits lines when Pelayaran or Rantai Pasok or Zona Konflik is active
            val straitDash = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
            // 1. Selat Kestrel (Kestrel <-> Sundara/Aurelia)
            drawLine(
                color = if (activeLayer == MapLayerFilter.ZONA_KONFLIK) redCol else blueCol,
                start = Offset(0.72f * w, 0.36f * h),
                end = Offset(0.60f * w, 0.62f * h),
                strokeWidth = 2.5.dp.toPx(),
                pathEffect = straitDash
            )
            // 2. Selat Energi (Norvaldia <-> Halvenia)
            drawLine(
                color = if (activeLayer == MapLayerFilter.ZONA_KONFLIK) yellowCol else blueCol,
                start = Offset(0.42f * w, 0.28f * h),
                end = Offset(0.24f * w, 0.44f * h),
                strokeWidth = 2.dp.toPx(),
                pathEffect = straitDash
            )
            // 3. Selat Pangan & Kapas (Tarsis <-> Sundara)
            drawLine(
                color = greenCol,
                start = Offset(0.38f * w, 0.77f * h),
                end = Offset(0.49f * w, 0.76f * h),
                strokeWidth = 2.dp.toPx(),
                pathEffect = straitDash
            )

            // Draw 8 Country Polygons
            countries.forEach { country ->
                val path = Path()
                country.polygonPoints.forEachIndexed { idx, (px, py) ->
                    if (idx == 0) path.moveTo(px * w, py * h)
                    else path.lineTo(px * w, py * h)
                }
                path.close()

                val (fillCol, strokeCol) = when (activeLayer) {
                    MapLayerFilter.ZONA_KONFLIK -> when {
                        country.tensionScore >= 60 -> redBg to redCol
                        country.tensionScore >= 40 -> yellowBg to yellowCol
                        else -> greenBg to greenCol
                    }
                    MapLayerFilter.RISIKO_IKLIM -> when {
                        country.climateRiskLabel.contains("Tinggi") -> redBg to redCol
                        country.climateRiskLabel.contains("Sedang") -> yellowBg to yellowCol
                        else -> greenBg to greenCol
                    }
                    MapLayerFilter.RANTAI_PASOK -> when (country.id) {
                        "sundara" -> blueBg to blueCol
                        "kestrel", "velmora" -> yellowBg to yellowCol
                        "tarsis" -> greenBg to greenCol
                        else -> neutralBg to borderColor
                    }
                    MapLayerFilter.PELAYARAN -> when (country.id) {
                        "sundara" -> blueBg to blueCol
                        "kestrel", "norvaldia" -> yellowBg to yellowCol
                        else -> neutralBg to borderColor
                    }
                }

                drawPath(path = path, color = fillCol)
                drawPath(
                    path = path,
                    color = strokeCol,
                    style = Stroke(width = if (country.id == "sundara") 2.5.dp.toPx() else 1.5.dp.toPx())
                )

                // Compute centroid for country label
                val cx = country.polygonPoints.map { it.first }.average().toFloat() * w
                val cy = country.polygonPoints.map { it.second }.average().toFloat() * h

                val nameLayout = textMeasurer.measure(
                    text = "${country.name} (${country.currencyCode})",
                    style = TextStyle(
                        fontFamily = SansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = onSurfaceColor
                    )
                )
                drawText(
                    textLayoutResult = nameLayout,
                    topLeft = Offset(cx - nameLayout.size.width / 2f, cy - 18.dp.toPx())
                )

                val subLabel = when (activeLayer) {
                    MapLayerFilter.ZONA_KONFLIK -> "Konflik ${country.tensionScore}/100"
                    MapLayerFilter.RISIKO_IKLIM -> country.climateRiskLabel.substringBefore(" (")
                    MapLayerFilter.RANTAI_PASOK -> if (country.id == "sundara") "Markas Pemain" else "Tier Pasok"
                    MapLayerFilter.PELAYARAN -> "Bunga ${country.policyRate}"
                }
                val subLayout = textMeasurer.measure(
                    text = subLabel,
                    style = TextStyle(
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = mutedColor
                    )
                )
                drawText(
                    textLayoutResult = subLayout,
                    topLeft = Offset(cx - subLayout.size.width / 2f, cy - 2.dp.toPx())
                )
            }

            // Draw Map Pieces (Bidak: Pabrik, Gudang, Toko, Kapal)
            pieces.forEach { piece ->
                val px = piece.normX * w
                val py = piece.normY * h
                val tokenSize = 18.dp.toPx()

                val tokenColor = when (piece.type) {
                    MapTokenType.PABRIK -> blueCol
                    MapTokenType.GUDANG -> greenCol
                    MapTokenType.TOKO -> yellowCol
                    MapTokenType.KAPAL -> redCol
                }

                // Draw distinct geometric board piece shape + initial letter
                drawRoundRect(
                    color = surfaceColor,
                    topLeft = Offset(px - tokenSize / 2f, py - tokenSize / 2f),
                    size = Size(tokenSize, tokenSize),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                )
                drawRoundRect(
                    color = tokenColor,
                    topLeft = Offset(px - tokenSize / 2f, py - tokenSize / 2f),
                    size = Size(tokenSize, tokenSize),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )

                val code = when (piece.type) {
                    MapTokenType.PABRIK -> "P"
                    MapTokenType.GUDANG -> "G"
                    MapTokenType.TOKO -> "T"
                    MapTokenType.KAPAL -> "K"
                }
                val codeLayout = textMeasurer.measure(
                    text = code,
                    style = TextStyle(
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = tokenColor
                    )
                )
                drawText(
                    textLayoutResult = codeLayout,
                    topLeft = Offset(
                        px - codeLayout.size.width / 2f,
                        py - codeLayout.size.height / 2f
                    )
                )
            }
        }
    }
}

@Composable
private fun MapTokenLegendBar() {
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(icon = Icons.Outlined.Factory, code = "[P]", label = "Pabrik", color = KapitalSemanticColors.blue)
            LegendItem(icon = Icons.Outlined.Warehouse, code = "[G]", label = "Gudang", color = KapitalSemanticColors.green)
            LegendItem(icon = Icons.Outlined.Storefront, code = "[T]", label = "Toko", color = KapitalSemanticColors.yellow)
            LegendItem(icon = Icons.Outlined.DirectionsBoat, code = "[K]", label = "Kapal", color = KapitalSemanticColors.red)
        }
    }
}

@Composable
private fun LegendItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    code: String,
    label: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "$code $label",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CompactCountryRowCard(
    country: Country,
    activeLayer: MapLayerFilter,
    onClick: () -> Unit
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
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .testTag("country_row_${country.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${country.name} (${country.currencyCode})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when (activeLayer) {
                        MapLayerFilter.PELAYARAN -> country.archetype
                        MapLayerFilter.ZONA_KONFLIK -> country.mainRisk
                        MapLayerFilter.RISIKO_IKLIM -> "Iklim: ${country.climateRiskLabel} · Listrik: ${country.electricityReliability}"
                        MapLayerFilter.RANTAI_PASOK -> country.supplyChainRole
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Inflasi ${country.inflationRange}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Konflik ${country.tensionScore}/100",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                    color = semanticToneColor(tensionTone)
                )
            }
        }
    }
}

@Composable
private fun CountrySummaryBottomSheetContent(
    country: Country,
    onInspectOrigin: (DataOriginInfo) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${country.name} (${country.currencyCode})",
                    style = MaterialTheme.typography.headlineMedium,
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

        HorizontalDivider(color = KapitalSemanticColors.border)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CountryMetricPill("Inflasi", country.inflationRange, Modifier.weight(1f))
            CountryMetricPill("Suku Bunga", country.policyRate, Modifier.weight(1f))
            CountryMetricPill("PDB Riil", country.gdpGrowth, Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CountryMetricPill("Bank Sentral", country.centralBankStyle, Modifier.weight(1f))
            CountryMetricPill("Pemilu", country.electionCountdown, Modifier.weight(1f))
        }

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
                    text = "Peluang Bisnis: ${country.opportunity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = KapitalSemanticColors.green
                )
                Text(
                    text = "Risiko Utama: ${country.mainRisk}",
                    style = MaterialTheme.typography.bodySmall,
                    color = KapitalSemanticColors.red
                )
                Text(
                    text = "Peran Rantai Pasok: ${country.supplyChainRole}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (country.pieces.isNotEmpty()) {
            Text(
                text = "Bidak & Aset di ${country.name}:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            country.pieces.forEach { pc ->
                Text(
                    text = "• [${pc.type.label}] ${pc.label} — ${pc.statusNote}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { onInspectOrigin(country.originInfo) },
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
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) {
                Text("Tutup")
            }
        }
    }
}

@Composable
private fun CountryMetricPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier.border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun isPointInPolygon(x: Float, y: Float, polygon: List<Pair<Float, Float>>): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val xi = polygon[i].first
        val yi = polygon[i].second
        val xj = polygon[j].first
        val yj = polygon[j].second
        val intersect = ((yi > y) != (yj > y)) &&
            (x < (xj - xi) * (y - yi) / ((yj - yi).let { if (it == 0f) 0.0001f else it }) + xi)
        if (intersect) inside = !inside
        j = i
    }
    return inside
}
