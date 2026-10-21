package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GameState
import com.example.ui.theme.KapitalSemanticColors
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.NotoSerifFontFamily

@Composable
fun MenuScreen(
    gameState: GameState,
    onContinue: () -> Unit,
    onNewGame: () -> Unit,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showHow by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var confirmNew by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                "KAPITAL",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = NotoSerifFontFamily, fontWeight = FontWeight.Bold, letterSpacing = 6.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "Simulasi ekonomi dunia dan strategi korporasi",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))

            if (gameState.hasSavedGame) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(8.dp))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Permainan tersimpan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${gameState.activeRole} · ${gameState.dateText}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Kas ${gameState.markasIndicators.firstOrNull()?.valueText ?: "-"} · Ekuitas ${gameState.companyEquityText}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily)
                        )
                        if (gameState.gameOverReason != null) {
                            Text("Permainan berakhir. Mulai permainan baru untuk bermain lagi.", style = MaterialTheme.typography.bodySmall,
                                color = KapitalSemanticColors.red)
                        }
                    }
                }
                Button(
                    onClick = onContinue,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("menu_continue")
                ) { Text("Lanjutkan permainan", style = MaterialTheme.typography.titleMedium) }
            }

            if (gameState.hasSavedGame) {
                OutlinedButton(
                    onClick = { confirmNew = true },
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("menu_new_game")
                ) { Text("Permainan baru", style = MaterialTheme.typography.titleMedium) }
            } else {
                Button(
                    onClick = onNewGame,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("menu_new_game")
                ) { Text("Mulai permainan", style = MaterialTheme.typography.titleMedium) }
            }
            OutlinedButton(
                onClick = { showHow = true },
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("menu_how")
            ) { Text("Cara bermain", style = MaterialTheme.typography.titleMedium) }
            OutlinedButton(
                onClick = onToggleTheme,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Text("Ganti tema terang/gelap", style = MaterialTheme.typography.titleMedium) }
            TextButton(onClick = { showAbout = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Tentang permainan") }
        }
    }

    if (confirmNew) {
        AlertDialog(
            onDismissRequest = { confirmNew = false },
            title = { Text("Mulai permainan baru?") },
            text = { Text("Permainan tersimpan pada ${gameState.dateText} akan dihapus dan diganti dunia baru dengan seed berbeda.") },
            confirmButton = { Button(onClick = { confirmNew = false; onNewGame() }) { Text("Ya, mulai baru") } },
            dismissButton = { OutlinedButton(onClick = { confirmNew = false }) { Text("Tidak") } }
        )
    }
    if (showHow) {
        AlertDialog(
            onDismissRequest = { showHow = false },
            title = { Text("Cara bermain") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HowLine("Anda CEO PT Benang Sundara. Tiap permainan baru dimulai dari premis, tanggal, angka perusahaan, dan kejadian yang berbeda. Satu giliran = satu bulan.")
                    HowLine("Setiap tindakan memakai Attention Points (AP). AP habis tiap bulan; gunakan untuk hal yang paling penting.")
                    HowLine("Tujuan: bertahan, menjaga kas dan covenant bank, lalu bertumbuh lewat ekspansi dan investasi saham.")
                    HowLine("Laba bukan kas. Perhatikan arus kas operasi, piutang, dan persediaan di laporan keuangan.")
                    HowLine("Data tidak sempurna: inflasi terlambat, berita bisa bias, bawahan bisa berbohong. Periksa silang dan audit.")
                    HowLine("Ketuk atau tahan angka untuk melihat asal data dan tingkat keyakinannya.")
                    HowLine("Permainan tersimpan otomatis. Menu utama bisa dibuka dari ikon menu di kiri atas.")
                }
            },
            confirmButton = { Button(onClick = { showHow = false }) { Text("Mengerti") } }
        )
    }
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("Tentang KAPITAL") },
            text = {
                Text(
                    "Simulasi deterministik: dunia ditentukan seed, jadi Anda bisa memutar ulang ('Bagaimana jika') tanpa curang. " +
                        "Negara, perusahaan, dan tokoh bersifat fiksi. Parameter ekonomi dikalibrasi kasar ke kondisi Indonesia 2026 dan bukan prediksi nyata.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = { Button(onClick = { showAbout = false }) { Text("Tutup") } }
        )
    }
}

@Composable
private fun HowLine(text: String) {
    Text("• $text", style = MaterialTheme.typography.bodyMedium)
}
