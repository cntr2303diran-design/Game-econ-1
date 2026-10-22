package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.model.*
import com.example.ui.theme.KapitalSemanticColors
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.NotoSerifFontFamily

@Composable
fun semanticToneColor(tone: SemanticTone): Color = when (tone) {
    SemanticTone.DANGER_RED -> KapitalSemanticColors.red
    SemanticTone.WARNING_YELLOW -> KapitalSemanticColors.yellow
    SemanticTone.GOOD_GREEN -> KapitalSemanticColors.green
    SemanticTone.PRIMARY_BLUE -> KapitalSemanticColors.blue
    SemanticTone.NEUTRAL_GRAY -> KapitalSemanticColors.grayText
}

@Composable
fun semanticToneContainerColor(tone: SemanticTone): Color = when (tone) {
    SemanticTone.DANGER_RED -> KapitalSemanticColors.redContainer
    SemanticTone.WARNING_YELLOW -> KapitalSemanticColors.yellowContainer
    SemanticTone.GOOD_GREEN -> KapitalSemanticColors.greenContainer
    SemanticTone.PRIMARY_BLUE -> KapitalSemanticColors.blueContainer
    SemanticTone.NEUTRAL_GRAY -> MaterialTheme.colorScheme.surfaceVariant
}

/**
 * KOMPONEN WAJIB 1: ConfidenceBadge (Rendah / Sedang / Tinggi)
 */
@Composable
fun ConfidenceBadge(
    confidence: ConfidenceTier,
    modifier: Modifier = Modifier
) {
    val fg = semanticToneColor(confidence.tone)
    val bg = semanticToneContainerColor(confidence.tone)

    Surface(
        color = bg,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier.border(1.dp, fg.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
    ) {
        Text(
            text = confidence.label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/**
 * KOMPONEN WAJIB 2: UncertainValue
 * Menampilkan angka atau rentang, tanggal data, sumber, dan label keyakinan.
 * Ketuk lama (long-press) atau ketuk ikon info membuka bottom sheet "Asal data".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UncertainValue(
    label: String,
    valueText: String,
    deltaOrSubText: String,
    tone: SemanticTone,
    dataDate: String,
    source: String,
    confidence: ConfidenceTier,
    originInfo: DataOriginInfo,
    onInspectOrigin: (DataOriginInfo) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val accentColor = semanticToneColor(tone)
    val borderColor = KapitalSemanticColors.border

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .combinedClickable(
                onClick = { onInspectOrigin(originInfo) },
                onLongClick = { onInspectOrigin(originInfo) }
            )
            .defaultMinSize(minHeight = 48.dp)
            .testTag("uncertain_value_${label.lowercase().replace(" ", "_")}")
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 10.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Asal data $label",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = valueText,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = deltaOrSubText,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = accentColor
            )

            if (!compact) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$source · $dataDate",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
                ConfidenceBadge(confidence = confidence)
            }
        }
    }
}

/**
 * Wrapper untuk angka di tabel agar ketuk lama (long press) atau ketuk membuka Bottom Sheet "Asal data".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InspectableNumberText(
    text: String,
    originInfo: DataOriginInfo,
    onInspectOrigin: (DataOriginInfo) -> Unit,
    modifier: Modifier = Modifier,
    tone: SemanticTone = SemanticTone.NEUTRAL_GRAY,
    isBold: Boolean = false
) {
    val textColor = if (tone == SemanticTone.NEUTRAL_GRAY) {
        MaterialTheme.colorScheme.onSurface
    } else {
        semanticToneColor(tone)
    }

    Box(
        contentAlignment = Alignment.CenterEnd,
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 36.dp)
            .combinedClickable(
                onClick = { onInspectOrigin(originInfo) },
                onLongClick = { onInspectOrigin(originInfo) }
            )
            .padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = MonoFontFamily,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium
            ),
            color = textColor
        )
    }
}

/**
 * KOMPONEN WAJIB 3: FanChart
 * Grafik proyeksi yang melebar ke masa depan (pita ketidakpastian 50% dan 90%), bukan satu garis.
 */
@Composable
fun FanChart(
    title: String,
    subtitle: String,
    historyPoints: List<Float>,
    medianFuturePoints: List<Float>,
    spread50Points: List<Float>,
    spread90Points: List<Float>,
    unitLabel: String,
    modifier: Modifier = Modifier,
    dangerLevel: Float? = null
) {
    val lineColor = KapitalSemanticColors.blue
    val dangerColor = KapitalSemanticColors.red
    val gridColor = KapitalSemanticColors.border

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, gridColor, RoundedCornerShape(6.dp))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = unitLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val fanDescription = run {
                fun f(v: Float) = String.format(java.util.Locale.forLanguageTag("id-ID"), "%.1f", v)
                if (historyPoints.isEmpty() || medianFuturePoints.isEmpty()) "Grafik proyeksi $title belum punya data."
                else {
                    val last = medianFuturePoints.last()
                    val s50 = spread50Points.lastOrNull(); val s90 = spread90Points.lastOrNull()
                    "Grafik proyeksi $title, satuan $unitLabel. Nilai terakhir ${f(historyPoints.last())}. " +
                        "Median di ujung proyeksi ${f(last)}" +
                        (if (s50 != null) ", rentang 50 persen ${f(last - s50)} sampai ${f(last + s50)}" else "") +
                        (if (s90 != null) ", rentang 90 persen ${f(last - s90)} sampai ${f(last + s90)}" else "") + "." +
                        (if (dangerLevel != null) " Batas bahaya ${f(dangerLevel)}." else "")
                }
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .padding(vertical = 4.dp)
                    .semantics { contentDescription = fanDescription }
            ) {
                val w = size.width
                val h = size.height
                if (historyPoints.isEmpty() || medianFuturePoints.isEmpty()) return@Canvas

                val totalSteps = historyPoints.size + medianFuturePoints.size - 1
                val lastHistVal = historyPoints.last()

                // Compute global min and max including 90% fan spread
                var minVal = historyPoints.minOrNull() ?: 0f
                var maxVal = historyPoints.maxOrNull() ?: 100f
                medianFuturePoints.forEachIndexed { i, med ->
                    val s90 = spread90Points.getOrElse(i) { 10f }
                    if (med - s90 < minVal) minVal = med - s90
                    if (med + s90 > maxVal) maxVal = med + s90
                }
                val span = (maxVal - minVal).coerceAtLeast(1f)

                fun mapX(stepIndex: Int): Float =
                    (stepIndex.toFloat() / totalSteps.coerceAtLeast(1)) * w

                fun mapY(v: Float): Float =
                    h - (((v - minVal) / span).coerceIn(0f, 1f) * (h * 0.82f) + h * 0.09f)

                // Horizontal reference lines
                val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                for (row in 0..2) {
                    val y = (h / 2f) * row
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f,
                        pathEffect = dash
                    )
                }

                // Vertical "Hari Ini (Giliran 39)" divider
                val splitStep = historyPoints.lastIndex
                val splitX = mapX(splitStep)
                drawLine(
                    color = gridColor,
                    start = Offset(splitX, 0f),
                    end = Offset(splitX, h),
                    strokeWidth = 1.5f,
                    pathEffect = dash
                )

                // Build Outer 90% Fan Polygon
                val path90 = Path()
                path90.moveTo(splitX, mapY(lastHistVal))
                medianFuturePoints.forEachIndexed { idx, med ->
                    val step = splitStep + idx + 1
                    val s90 = spread90Points.getOrElse(idx) { 10f }
                    path90.lineTo(mapX(step), mapY(med + s90))
                }
                for (idx in medianFuturePoints.indices.reversed()) {
                    val step = splitStep + idx + 1
                    val med = medianFuturePoints[idx]
                    val s90 = spread90Points.getOrElse(idx) { 10f }
                    path90.lineTo(mapX(step), mapY(med - s90))
                }
                path90.close()
                drawPath(path = path90, color = lineColor.copy(alpha = 0.14f))

                // Build Inner 50% Fan Polygon
                val path50 = Path()
                path50.moveTo(splitX, mapY(lastHistVal))
                medianFuturePoints.forEachIndexed { idx, med ->
                    val step = splitStep + idx + 1
                    val s50 = spread50Points.getOrElse(idx) { 5f }
                    path50.lineTo(mapX(step), mapY(med + s50))
                }
                for (idx in medianFuturePoints.indices.reversed()) {
                    val step = splitStep + idx + 1
                    val med = medianFuturePoints[idx]
                    val s50 = spread50Points.getOrElse(idx) { 5f }
                    path50.lineTo(mapX(step), mapY(med - s50))
                }
                path50.close()
                drawPath(path = path50, color = lineColor.copy(alpha = 0.28f))

                // Historical Solid Line
                val histPath = Path()
                historyPoints.forEachIndexed { idx, v ->
                    val x = mapX(idx)
                    val y = mapY(v)
                    if (idx == 0) histPath.moveTo(x, y) else histPath.lineTo(x, y)
                }
                drawPath(
                    path = histPath,
                    color = lineColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                // Future Median Dashed Line
                val medPath = Path()
                medPath.moveTo(splitX, mapY(lastHistVal))
                medianFuturePoints.forEachIndexed { idx, med ->
                    medPath.lineTo(mapX(splitStep + idx + 1), mapY(med))
                }
                drawPath(
                    path = medPath,
                    color = lineColor,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                    )
                )

                // Highlight current point
                drawCircle(
                    color = lineColor,
                    radius = 4.dp.toPx(),
                    center = Offset(splitX, mapY(lastHistVal))
                )

                // Garis bahaya (misalnya kas nol), hanya bila diminta dan berada dalam rentang grafik
                if (dangerLevel != null && dangerLevel > minVal && dangerLevel < maxVal) {
                    val dangerY = mapY(dangerLevel)
                    drawLine(
                        color = dangerColor.copy(alpha = 0.65f),
                        start = Offset(0f, dangerY),
                        end = Offset(w, dangerY),
                        strokeWidth = 1.5f,
                        pathEffect = dash
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "← Data Historis (Terealisasi)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Proyeksi Rentang 50% & 90% →",
                    style = MaterialTheme.typography.labelSmall,
                    color = KapitalSemanticColors.blue
                )
            }
        }
    }
}

/**
 * Grafik garis kecil (Sparkline) untuk Layar Pasar.
 */
@Composable
fun Sparkline(
    points: List<Float>,
    tone: SemanticTone,
    modifier: Modifier = Modifier
) {
    val strokeColor = semanticToneColor(tone)
    val sparkDescription = if (points.size < 2) "Grafik tren belum punya data."
    else {
        val first = points.first(); val last = points.last()
        val change = if (kotlin.math.abs(first) > 1e-6f) (last - first) / kotlin.math.abs(first) * 100f else 0f
        val dir = when { change > 0.05f -> "naik"; change < -0.05f -> "turun"; else -> "datar" }
        "Grafik tren ${points.size} titik: dari ${String.format(java.util.Locale.forLanguageTag("id-ID"), "%.1f", first)} ke ${String.format(java.util.Locale.forLanguageTag("id-ID"), "%.1f", last)}, $dir ${String.format(java.util.Locale.forLanguageTag("id-ID"), "%.1f", kotlin.math.abs(change))} persen."
    }
    Canvas(modifier = modifier.semantics { contentDescription = sparkDescription }) {
        if (points.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val minV = points.minOrNull() ?: 0f
        val maxV = points.maxOrNull() ?: 1f
        val span = (maxV - minV).coerceAtLeast(0.01f)

        val path = Path()
        points.forEachIndexed { idx, v ->
            val x = (idx.toFloat() / (points.size - 1)) * w
            val y = h - (((v - minV) / span) * (h * 0.8f) + h * 0.1f)
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            if (idx == points.lastIndex) {
                drawCircle(color = strokeColor, radius = 3.dp.toPx(), center = Offset(x, y))
            }
        }
        drawPath(
            path = path,
            color = strokeColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * KOMPONEN WAJIB 4: Bottom Sheet "Asal data" saat pengguna mengetuk lama angka mana pun.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataOriginBottomSheet(
    originInfo: DataOriginInfo,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Asal data",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                ConfidenceBadge(confidence = originInfo.confidence)
            }

            HorizontalDivider(color = KapitalSemanticColors.border)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Indikator:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(12.dp))
                Text(originInfo.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Nilai Tercatat:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = originInfo.displayedValue,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Sumber:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(originInfo.sourceName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Tanggal Data:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(originInfo.dataDate, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
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
                        text = "Cara hitung & Rumus:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = originInfo.calculationMethod,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                color = KapitalSemanticColors.yellowContainer,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, KapitalSemanticColors.yellow.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Catatan bias, keterlambatan & akuntansi forensik:",
                        style = MaterialTheme.typography.labelLarge,
                        color = KapitalSemanticColors.yellow
                    )
                    Text(
                        text = originInfo.uncertaintyAndBiasNote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("Tutup")
            }
        }
    }
}

/**
 * LAYAR TAMBAHAN: Kartu Event Dialog dengan 3 pilihan respons & perkiraan dampak "menurut Direktur Operasi".
 */
@Composable
fun EventCardDialogModal(
    eventDialog: EventDialogModel,
    onDismiss: () -> Unit,
    onSelectOption: (EventOptionItem) -> Unit,
    apCurrent: Int = 99
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(8.dp))
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = KapitalSemanticColors.redContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Kartu Event & Krisis",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = KapitalSemanticColors.red,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = eventDialog.title,
                    style = MaterialTheme.typography.headlineLarge.copy(fontFamily = NotoSerifFontFamily),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = eventDialog.contextDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Jejak peringatan dini: ${eventDialog.earlyWarningNote}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                eventDialog.options.forEach { opt ->
                    val borderCol = semanticToneColor(opt.tone)
                    val affordable = apCurrent >= opt.apCost
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(6.dp),
                        enabled = affordable,
                        onClick = { onSelectOption(opt) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                            .border(1.dp, borderCol.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .testTag("event_option_${opt.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = opt.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    color = KapitalSemanticColors.blueContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "${opt.apCost} AP",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = KapitalSemanticColors.blue,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (affordable) "${opt.impactEstimate} (${opt.advisorAttribution})"
                                else "AP tidak cukup (sisa $apCurrent AP). ${opt.impactEstimate}",
                                style = MaterialTheme.typography.bodySmall,
                                color = borderCol
                            )
                        }
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Tunda / Tutup", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * Laporan akhir bulan: apa yang berubah dan mengapa.
 */
@Composable
fun MonthlyReportDialog(
    title: String,
    lines: List<ReportLineItem>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(8.dp))
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                lines.forEach { line ->
                    val col = if (line.tone == SemanticTone.NEUTRAL_GRAY) MaterialTheme.colorScheme.onSurfaceVariant
                    else semanticToneColor(line.tone)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(col)
                        )
                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("monthly_report_close")
                ) {
                    Text("Lanjutkan")
                }
            }
        }
    }
}

/**
 * Layar akhir permainan dengan post-mortem singkat.
 */
@Composable
fun GameOverPanel(
    reason: String,
    turn: Int,
    dateText: String,
    onNewGame: () -> Unit,
    onOpenChronicle: () -> Unit,
    modifier: Modifier = Modifier,
    evaluationTitle: String = "",
    evaluationRows: List<Pair<String, String>> = emptyList(),
    postMortem: List<String> = emptyList()
) {
    Surface(
        color = KapitalSemanticColors.redContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, KapitalSemanticColors.red.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Permainan berakhir · $dateText (giliran ${turn - 1})",
                style = MaterialTheme.typography.headlineMedium,
                color = KapitalSemanticColors.red
            )
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (evaluationRows.isNotEmpty()) {
                Text(
                    text = evaluationTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                evaluationRows.forEach { (k, v) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            if (postMortem.isNotEmpty()) {
                Text(
                    text = "Post-mortem",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                postMortem.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = "Buka Kronik Dunia untuk melihat rantai keputusan yang membawa ke sini, lalu coba lagi dengan dunia baru.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onOpenChronicle,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) { Text("Kronik") }
                Button(
                    onClick = onNewGame,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("new_game_button")
                ) { Text("Permainan baru") }
            }
        }
    }
}
