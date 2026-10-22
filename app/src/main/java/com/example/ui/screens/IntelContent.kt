package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.theme.KapitalSemanticColors
import com.example.ui.theme.MonoFontFamily

@Composable
private fun IntelCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(8.dp))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** B1: tab Intelijen: riset berbayar dengan margin error dan rapat dewan kuartalan. */
@Composable
fun IntelTabContent(
    intel: IntelView,
    ap: Int,
    onBuy: (kind: String, subject: String, tier: Int) -> Unit,
    onAnswer: (String) -> Unit
) {
    var kindIdx by rememberSaveable { mutableStateOf(0) }
    var subjectIdx by rememberSaveable { mutableStateOf(0) }

    Text("Intelijen dan dewan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

    IntelCard {
        Text("Rapat dewan", style = MaterialTheme.typography.titleMedium)
        Text("Kepercayaan dewan ${intel.boardTrust}/100 · ${intel.boardRecord}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val b = intel.board
        if (b == null) {
            Text("Belum ada rapat. Dewan bertemu tiap tiga bulan.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text(b.member, style = MaterialTheme.typography.labelMedium)
            Text(b.text, style = MaterialTheme.typography.bodyMedium)
            Text("Metrik: ${b.metricLabel} · ${b.dueText}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (b.answered != null) {
                Text("Jawaban Anda: ${b.answered}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("naik" to "Naik", "turun" to "Turun", "tetap" to "Tetap", "abstain" to "Tidak tahu").forEach { (code, label) ->
                        OutlinedButton(
                            onClick = { onAnswer(code) },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("dewan_$code")
                        ) { Text(label, style = MaterialTheme.typography.labelMedium) }
                    }
                }
                Text("Benar +6, salah −6, tidak dijawab −4, mengelak netral (tiga kali berturut-turut mulai merugikan).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    IntelCard {
        Text("Beli laporan riset (1 poin aksi)", style = MaterialTheme.typography.titleMedium)
        Text("Perkiraan selalu berderau; anggaran lebih besar memperkecil galat tetapi tidak pernah menghilangkannya.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (intel.kinds.isNotEmpty()) {
            val k = intel.kinds[kindIdx.coerceIn(0, intel.kinds.size - 1)]
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                intel.kinds.forEachIndexed { i, kk ->
                    FilterChip(selected = i == kindIdx, onClick = { kindIdx = i; subjectIdx = 0 }, label = { Text(kk.second, style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.testTag("intel_kind_$i"))
                }
            }
            val subjects = k.third
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { subjectIdx = (subjectIdx - 1 + subjects.size) % subjects.size }, shape = RoundedCornerShape(6.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text("◀") }
                Text(subjects[subjectIdx.coerceIn(0, subjects.size - 1)].second, modifier = Modifier.weight(1f).align(androidx.compose.ui.Alignment.CenterVertically), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Button(onClick = { subjectIdx = (subjectIdx + 1) % subjects.size }, shape = RoundedCornerShape(6.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text("▶") }
            }
            intel.tierCosts.forEachIndexed { i, label ->
                Button(
                    onClick = { onBuy(k.first, subjects[subjectIdx.coerceIn(0, subjects.size - 1)].first, i + 1) },
                    enabled = ap >= 1,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("intel_buy_${i + 1}")
                ) { Text(label, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }

    IntelCard {
        Text("Laporan yang sudah dibeli", style = MaterialTheme.typography.titleMedium)
        if (intel.reports.isEmpty()) Text("Belum ada laporan.", style = MaterialTheme.typography.bodySmall)
        intel.reports.forEach { r ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${r.title} · ${r.tierText} · ${r.whenText}", style = MaterialTheme.typography.labelMedium)
                Text("${r.metric}: ${r.estimateText} ${r.bandText}", style = MaterialTheme.typography.bodySmall, fontFamily = MonoFontFamily)
                HorizontalDivider(color = KapitalSemanticColors.border.copy(alpha = 0.5f))
            }
        }
    }
}
