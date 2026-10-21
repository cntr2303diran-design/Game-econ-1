package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
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
import com.example.model.Decision
import com.example.ui.components.semanticToneColor
import com.example.ui.theme.KapitalSemanticColors
import com.example.ui.theme.MonoFontFamily
import kotlin.math.roundToInt

@Composable
fun JurnalKeputusanScreen(
    decision: Decision,
    currentCashText: String = "",
    onBack: () -> Unit,
    onExecuteDecision: (optionId: String, reason: String, prediction: String, confidencePct: Int, failureScenario: String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var selectedOptionId by rememberSaveable(decision.id) {
        mutableStateOf(decision.options.firstOrNull()?.id ?: "approve")
    }
    var strategicReason by rememberSaveable(decision.id) { mutableStateOf("") }
    var numericPrediction by rememberSaveable(decision.id) { mutableStateOf("") }
    var confidenceSlider by rememberSaveable(decision.id) { mutableFloatStateOf(65f) }
    var failureScenario by rememberSaveable(decision.id) { mutableStateOf("") }
    var hasAdjustedSlider by rememberSaveable(decision.id) { mutableStateOf(false) }

    val isFormComplete = strategicReason.trim().isNotEmpty() &&
        numericPrediction.trim().isNotEmpty() &&
        failureScenario.trim().isNotEmpty() &&
        (hasAdjustedSlider || confidenceSlider.roundToInt() in 0..100)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("jurnal_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Jurnal Keputusan (Wajib ≥10% Aset)",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${decision.title} · Biaya ${decision.apCost} AP · ${decision.assetSharePct}% Aset",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider(color = KapitalSemanticColors.border)
            }
        }

        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
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
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = decision.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = decision.defaultContextNote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Catatan Sistem: Jurnal ini akan dibuka kembali 6 giliran mendatang untuk menguji kalibrasi prediksi Anda (Skor Brier).${if (currentCashText.isNotEmpty()) " Kas saat ini: $currentCashText." else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = KapitalSemanticColors.blue
                    )
                }
            }

            if (decision.options.size > 1) {
                Text(
                    text = "Pilih Opsi Keputusan",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                decision.options.forEach { opt ->
                    val selected = opt.id == selectedOptionId
                    val borderCol = if (selected) KapitalSemanticColors.blue else KapitalSemanticColors.border
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .border(if (selected) 1.5.dp else 1.dp, borderCol, RoundedCornerShape(6.dp))
                            .clickable { selectedOptionId = opt.id }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (selected) Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (selected) KapitalSemanticColors.blue else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(opt.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(opt.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = opt.badge,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold),
                                color = semanticToneColor(opt.tone)
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = strategicReason,
                onValueChange = { strategicReason = it },
                label = { Text("1. Alasan strategis") },
                placeholder = { Text("Contoh: Mengamankan margin benang atau menghindari jatuh tempo Bank B...") },
                minLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_alasan_strategis")
            )

            OutlinedTextField(
                value = numericPrediction,
                onValueChange = { numericPrediction = it },
                label = { Text("2. Prediksi kas minimal 6 giliran lagi (dalam jt RS)") },
                placeholder = { Text("Contoh: 5000 (artinya Anda yakin kas ≥ 5.000 jt RS dalam 6 bln)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_prediksi_angka")
            )

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "3. Tingkat keyakinan (0–100%)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${confidenceSlider.roundToInt()}%",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = KapitalSemanticColors.blue
                        )
                    }
                    Slider(
                        value = confidenceSlider,
                        onValueChange = {
                            confidenceSlider = it
                            hasAdjustedSlider = true
                        },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = KapitalSemanticColors.blue,
                            activeTrackColor = KapitalSemanticColors.blue
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("slider_keyakinan")
                    )
                    Text(
                        text = "Jangan pilih 95% kecuali Anda punya bukti kuat; terlalu percaya diri saat meleset merusak Skor Brier Anda.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = failureScenario,
                onValueChange = { failureScenario = it },
                label = { Text("4. Skenario gagal (Pre-Mortem)") },
                placeholder = { Text("Apa yang bisa membuat keputusan ini gagal? (Misal: kurs Rial jatuh atau mesin tertahan di Kestrel)") },
                minLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_skenario_gagal")
            )

            Button(
                onClick = {
                    onExecuteDecision(
                        selectedOptionId,
                        strategicReason.trim(),
                        numericPrediction.trim(),
                        confidenceSlider.roundToInt(),
                        failureScenario.trim()
                    )
                },
                enabled = isFormComplete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = KapitalSemanticColors.blue,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("eksekusi_jurnal_button")
            ) {
                Text(
                    text = if (isFormComplete) {
                        "Eksekusi Keputusan (${decision.apCost} AP)"
                    } else {
                        "Isi Ke-4 Kolom Jurnal untuk Mengeksekusi"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
