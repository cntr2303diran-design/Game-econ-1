package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.Decision
import com.example.ui.theme.KapitalSemanticColors
import com.example.ui.theme.MonoFontFamily
import kotlin.math.roundToInt

/**
 * LAYAR TAMBAHAN: Jurnal Keputusan
 * Formulir dengan 4 isian:
 * 1. Alasan strategis
 * 2. Prediksi angka
 * 3. Tingkat keyakinan memakai slider 0–100%
 * 4. Skenario gagal
 * Tombol "Eksekusi" baru aktif setelah semua terisi.
 */
@Composable
fun JurnalKeputusanScreen(
    decision: Decision,
    onBack: () -> Unit,
    onExecuteDecision: (
        strategicReason: String,
        numericPrediction: String,
        confidencePct: Int,
        failureScenario: String
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var strategicReason by rememberSaveable { mutableStateOf("") }
    var numericPrediction by rememberSaveable { mutableStateOf("") }
    var confidenceSlider by rememberSaveable { mutableFloatStateOf(70f) }
    var hasTouchedConfidence by rememberSaveable { mutableStateOf(true) }
    var failureScenario by rememberSaveable { mutableStateOf("") }

    // Tombol "Eksekusi" baru aktif setelah semua 4 isian terisi
    val isFormComplete = strategicReason.isNotBlank() &&
        numericPrediction.isNotBlank() &&
        hasTouchedConfidence &&
        failureScenario.isNotBlank()

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
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("jurnal_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Kembali"
                            )
                        }
                        Column {
                            Text(
                                text = "Jurnal Keputusan (Wajib >10% Aset)",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${decision.title} · Biaya ${decision.apCost} AP",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            strategicReason = "Mengamankan pasokan benang hulu di Kalimas dan menekan COGS jangka menengah."
                            numericPrediction = "Margin kotor naik +4,5% dan ROI tercapai dalam 18 giliran."
                            confidenceSlider = 75f
                            hasTouchedConfidence = true
                            failureScenario = "Pembatasan ekspor Kestrel menunda impor mesin pintal 3 bulan dan menguras kas."
                        },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("jurnal_fill_sample_button")
                    ) {
                        Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Isi Contoh")
                    }
                }
                HorizontalDivider(color = KapitalSemanticColors.border)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
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
                        text = "Konteks Proposal (${decision.assetSharePct}% dari Total Aset):",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = decision.defaultContextNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Isian 1: Alasan strategis
                    OutlinedTextField(
                        value = strategicReason,
                        onValueChange = { strategicReason = it },
                        label = { Text("1. Alasan strategis") },
                        placeholder = { Text("Mengapa keputusan ini diambil sekarang?") },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_alasan_strategis")
                    )

                    // Isian 2: Prediksi angka
                    OutlinedTextField(
                        value = numericPrediction,
                        onValueChange = { numericPrediction = it },
                        label = { Text("2. Prediksi angka (Dievaluasi 6–12 giliran lagi)") },
                        placeholder = { Text("Contoh: Margin kotor naik ke 37%, kas +450 jt RS/bln") },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_prediksi_angka")
                    )

                    // Isian 3: Tingkat keyakinan memakai slider 0–100%
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "3. Tingkat keyakinan (Untuk kalibrasi Brier Score)",
                                style = MaterialTheme.typography.labelLarge,
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
                                hasTouchedConfidence = true
                            },
                            valueRange = 0f..100f,
                            steps = 19,
                            colors = SliderDefaults.colors(
                                thumbColor = KapitalSemanticColors.blue,
                                activeTrackColor = KapitalSemanticColors.blue
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("slider_tingkat_keyakinan")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("0% (Tebakan murni)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("100% (Pasti terjadi)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Isian 4: Skenario gagal
                    OutlinedTextField(
                        value = failureScenario,
                        onValueChange = { failureScenario = it },
                        label = { Text("4. Skenario gagal (Pre-Mortem)") },
                        placeholder = { Text("Jika keputusan ini hancur 1 tahun lagi, apa penyebab utamanya?") },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_skenario_gagal")
                    )

                    if (!isFormComplete) {
                        Text(
                            text = "* Isi seluruh 4 kolom di atas untuk mengaktifkan tombol Eksekusi.",
                            style = MaterialTheme.typography.labelSmall,
                            color = KapitalSemanticColors.yellow
                        )
                    }

                    Button(
                        onClick = {
                            onExecuteDecision(
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
                            .testTag("jurnal_execute_button")
                    ) {
                        Text(
                            text = "Eksekusi (${decision.apCost} AP)",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}
