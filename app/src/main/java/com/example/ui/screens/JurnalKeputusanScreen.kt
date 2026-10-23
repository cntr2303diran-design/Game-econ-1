package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.Decision
import com.example.ui.theme.KapitalSemanticColors
import com.example.ui.theme.MonoFontFamily
import kotlin.math.roundToInt

/**
 * Jurnal keputusan untuk keputusan besar (>10% aset).
 * Pemain wajib menulis alasan, prediksi, tingkat keyakinan, prediksi kas yang bisa diuji,
 * dan skenario gagal. Prediksi kas dinilai otomatis 6 giliran kemudian (skor Brier).
 */
@Composable
fun JurnalKeputusanScreen(
    decision: Decision,
    apCurrent: Int,
    currentCashText: String,
    preselectedOption: String,
    onBack: () -> Unit,
    onReject: () -> Unit,
    onExecuteDecision: (
        optionId: String,
        strategicReason: String,
        numericPrediction: String,
        confidencePct: Int,
        predictedCash: Double,
        failureScenario: String
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var selectedOption by rememberSaveable {
        mutableStateOf(decision.options.firstOrNull { it.first == preselectedOption }?.first ?: "")
    }
    var strategicReason by rememberSaveable { mutableStateOf("") }
    var numericPrediction by rememberSaveable { mutableStateOf("") }
    var confidenceSlider by rememberSaveable { mutableFloatStateOf(50f) }
    var predictedCashText by rememberSaveable { mutableStateOf("") }
    var failureScenario by rememberSaveable { mutableStateOf("") }

    val predictedCash = predictedCashText.replace(".", "").replace(",", ".").toDoubleOrNull()
    val needsOption = decision.options.isNotEmpty()
    val enoughAp = apCurrent >= decision.apCost
    val isFormComplete = strategicReason.isNotBlank() &&
        numericPrediction.isNotBlank() &&
        predictedCash != null &&
        failureScenario.isNotBlank() &&
        (!needsOption || selectedOption.isNotEmpty())

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
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Jurnal keputusan",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${decision.title} · ${decision.apCost} AP · sisa $apCurrent AP",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                        text = "Konteks (${decision.assetSharePct}% dari total aset)",
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

            if (needsOption) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(6.dp))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Pilih satu (tinjau kontraknya dulu di Perusahaan › Kontrak)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(8.dp)
                        )
                        decision.options.forEach { (id, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedOption = id }
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selectedOption == id, onClick = { selectedOption = id })
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
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

                    OutlinedTextField(
                        value = numericPrediction,
                        onValueChange = { numericPrediction = it },
                        label = { Text("2. Prediksi hasil") },
                        placeholder = { Text("Contoh: margin kotor naik 3 poin dalam 12 bulan") },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_prediksi_angka")
                    )

                    OutlinedTextField(
                        value = predictedCashText,
                        onValueChange = { v -> predictedCashText = v.filter { it.isDigit() || it == '.' || it == ',' } },
                        label = { Text("3. Prediksi teruji: kas 6 giliran lagi ≥ (jt RS)") },
                        supportingText = { Text("Kas sekarang $currentCashText. Dinilai otomatis untuk skor kalibrasi.") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_prediksi_kas")
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "4. Seberapa yakin prediksi kas itu benar?",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
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
                            onValueChange = { confidenceSlider = it },
                            valueRange = 0f..100f,
                            steps = 19,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("slider_tingkat_keyakinan")
                        )
                    }

                    OutlinedTextField(
                        value = failureScenario,
                        onValueChange = { failureScenario = it },
                        label = { Text("5. Skenario gagal (pre-mortem)") },
                        placeholder = { Text("Jika keputusan ini gagal setahun lagi, apa penyebabnya?") },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_skenario_gagal")
                    )

                    if (!enoughAp) {
                        Text(
                            text = "AP tidak cukup. Butuh ${decision.apCost} AP, sisa $apCurrent AP. Akhiri giliran untuk mengisi ulang.",
                            style = MaterialTheme.typography.labelSmall,
                            color = KapitalSemanticColors.red
                        )
                    } else if (!isFormComplete) {
                        Text(
                            text = "Isi semua kolom${if (needsOption) " dan pilih salah satu opsi" else ""} untuk mengaktifkan tombol eksekusi.",
                            style = MaterialTheme.typography.labelSmall,
                            color = KapitalSemanticColors.yellow
                        )
                    }

                    Button(
                        onClick = {
                            onExecuteDecision(
                                selectedOption,
                                strategicReason.trim(),
                                numericPrediction.trim(),
                                confidenceSlider.roundToInt(),
                                predictedCash ?: 0.0,
                                failureScenario.trim()
                            )
                        },
                        enabled = isFormComplete && enoughAp,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
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

                    if (decision.canReject) {
                        OutlinedButton(
                            onClick = onReject,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("jurnal_reject_button")
                        ) {
                            Text("Tolak proposal (0 AP)")
                        }
                    }
                }
            }
        }
    }
}
