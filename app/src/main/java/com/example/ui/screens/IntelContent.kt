package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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

/** B4c: grafik garis harga saham (maks. 60 bulan terakhir) dengan garis acuan pada harga awal jendela. */
@Composable
private fun StockChartCard(series: List<Double>, caption: String) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outline
    val first = series.first(); val last = series.last()
    IntelCard {
        Text("Harga saham", style = MaterialTheme.typography.titleMedium)
        Text("$caption · ${series.size} bulan: ${"%+.1f".format((last / first - 1.0) * 100.0)}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(Modifier.fillMaxWidth().height(140.dp).testTag("grafik_saham")) {
            val lo = series.min(); val hi = series.max()
            val span = (hi - lo).coerceAtLeast(1e-9)
            fun y(v: Double) = (size.height - ((v - lo) / span).toFloat() * size.height * 0.84f - size.height * 0.08f)
            drawLine(grid, Offset(0f, y(first)), Offset(size.width, y(first)), strokeWidth = 1f)
            val path = Path()
            series.forEachIndexed { i, v ->
                val x = size.width * i / (series.size - 1)
                if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
            }
            drawPath(path, line, style = Stroke(width = 3f))
        }
    }
}

/** B4c: jual saham minoritas anak usaha dengan tawar-menawar diskon. */
@Composable
private fun StakeCard(stakes: List<StakeRowView>, ap: Int, onSell: (String, Int, Int) -> Unit) {
    var pct by rememberSaveable { mutableStateOf(10) }
    var ask by rememberSaveable { mutableStateOf(20) }
    IntelCard {
        Text("Saham minoritas anak usaha", style = MaterialTheme.typography.titleMedium)
        Text("Anda menawarkan diskon dari nilai wajar. Investor menolak bila diskon terlalu sempit (batasnya 8–34% dan tidak diketahui), lalu menghilang tiga bulan. Porsi 20% ke atas memberi hak veto atas transfer pricing agresif. Pajak tertahan atas dividen minoritas ditanggung mereka.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Porsi dijual", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(5, 10, 20).forEach { p -> FilterChip(selected = pct == p, onClick = { pct = p }, label = { Text("$p%") }, modifier = Modifier.testTag("nci_pct_$p")) }
        }
        Text("Diskon yang ditawarkan", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(10, 20, 30).forEach { a -> FilterChip(selected = ask == a, onClick = { ask = a }, label = { Text("$a%") }, modifier = Modifier.testTag("nci_ask_$a")) }
        }
        stakes.forEach { s ->
            Text(s.name, style = MaterialTheme.typography.titleSmall)
            Text(s.statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { onSell(s.countryId, pct, ask) }, enabled = s.blockText == null && ap >= 2, shape = RoundedCornerShape(6.dp),
                modifier = Modifier.heightIn(min = 48.dp).testTag("nci_jual_${s.countryId}")) { Text("Tawarkan $pct% dengan diskon $ask% (2 AP)") }
            s.blockText?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
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

/** B2: tab Segmen: lima segmen konsumen, tren (survei berderau), merek, dan tingkat fokus. */
@Composable
fun SegmenTabContent(
    view: ProductMarketView,
    ap: Int,
    onSetFocus: (segment: String, level: Int) -> Unit
) {
    Text("Pasar produk: lima segmen", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    IntelCard {
        Text("${view.brandText} · ${view.focusText}", style = MaterialTheme.typography.bodyMedium)
        Text(view.costText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Mengabaikan segmen mengurangi cakupan 15%; fokus menambah cakupan 18% tetapi menambah biaya dan membangun merek. " +
                "Survei konsumen berderau; riset Intelijen memberi angka lebih tepat. Perubahan memakan 1 poin aksi dan ada jeda 6 bulan.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    view.rows.forEach { row ->
        IntelCard {
            Text(row.label, style = MaterialTheme.typography.titleMedium)
            Text("${row.weightText} · ${row.surveyText} · ${row.adoptionText}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Abaikan", "Layani", "Fokus").forEachIndexed { lv, label ->
                    val blocked = row.blocks[lv]
                    FilterChip(
                        selected = row.level == lv,
                        onClick = { if (blocked == null && ap >= 1) onSetFocus(row.code, lv) },
                        enabled = row.level == lv || (blocked == null && ap >= 1),
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.testTag("segmen_${row.code}_$lv")
                    )
                }
            }
            val why = row.blocks.filterNotNull().firstOrNull()
            if (why != null && row.level == 1) Text(why, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** B3: tab Operasi: karyawan kunci, R&D berkelanjutan, logistik, armada. */
@Composable
fun OperasiTabContent(
    view: OperationsView,
    ap: Int,
    onHire: (role: String, tier: Int) -> Unit,
    onFire: (String) -> Unit,
    onRaise: (String) -> Unit,
    onSetRd: (Int) -> Unit,
    onSetLogistics: (Int) -> Unit,
    onSetInsurance: (Int) -> Unit = {},
    onSetTradeFinance: (Boolean) -> Unit = {},
    onSaleLeaseback: (Int) -> Unit = {},
    onBuyDerivative: (String, Int) -> Unit = { _, _ -> },
    onSellStake: (String, Int, Int) -> Unit = { _, _, _ -> },
    onWindowDress: (Int) -> Unit = {},
    onSetAuditor: (Int) -> Unit = {},
    onOpenSpv: (Int) -> Unit = {}
) {
    var hireTier by rememberSaveable { mutableStateOf(2) }
    Text("Operasi: karyawan kunci, R&D, logistik", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    IntelCard {
        Text(view.costText, style = MaterialTheme.typography.bodyMedium)
        Text("Pesaing bisa membajak karyawan berloyalitas rendah atau bergaji di bawah pasar. Jabatan kosong menghilangkan efeknya; perekrutan baru berisiko karena skill asli baru terlihat setelah 3 bulan.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            view.hireCosts.forEachIndexed { i, label ->
                FilterChip(selected = hireTier == i + 1, onClick = { hireTier = i + 1 }, label = { Text(label, style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.testTag("operasi_tier_${i + 1}"))
            }
        }
    }
    view.staff.forEach { st ->
        IntelCard {
            Text(st.label, style = MaterialTheme.typography.titleMedium)
            Text("${st.skillText} · ${st.loyaltyText} · ${st.salaryText}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!st.present) {
                    Button(onClick = { onHire(st.code, hireTier) }, enabled = st.canHire && ap >= 1, shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("operasi_hire_${st.code}")) { Text("Rekrut") }
                } else {
                    OutlinedButton(onClick = { onRaise(st.code) }, enabled = st.canRaise && ap >= 1, shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("operasi_raise_${st.code}")) { Text("Naikkan gaji 10%") }
                    OutlinedButton(onClick = { onFire(st.code) }, enabled = st.canFire && ap >= 1, shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("operasi_fire_${st.code}")) { Text("Pecat") }
                }
            }
        }
    }
    IntelCard {
        Text(view.rdText, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..3).forEach { lv ->
                FilterChip(selected = view.rdLevel == lv, onClick = { if (ap >= 1) onSetRd(lv) }, enabled = view.rdLevel == lv || ap >= 1,
                    label = { Text(if (lv == 0) "Mati" else "Tingkat $lv") }, modifier = Modifier.testTag("operasi_rd_$lv"))
            }
        }
        Text(view.logisticsText, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..2).forEach { lv ->
                FilterChip(selected = view.logisticsLevel == lv, onClick = { if (ap >= 1) onSetLogistics(lv) }, enabled = view.logisticsLevel == lv || ap >= 1,
                    label = { Text(if (lv == 0) "Dasar" else "Tingkat $lv") }, modifier = Modifier.testTag("operasi_log_$lv"))
            }
        }
        Text(view.fleetText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    IntelCard {
        Text("Keuangan lanjut", style = MaterialTheme.typography.titleMedium)
        Text(view.insuranceText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..2).forEach { lv ->
                FilterChip(selected = view.insuranceLevel == lv, onClick = { if (ap >= 1) onSetInsurance(lv) }, enabled = view.insuranceLevel == lv || ap >= 1,
                    label = { Text(if (lv == 0) "Tanpa" else "Tingkat $lv") }, modifier = Modifier.testTag("keu_ins_$lv"))
            }
        }
        Text(view.tradeFinanceText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { onSetTradeFinance(!view.tradeFinance) }, enabled = ap >= 1, shape = RoundedCornerShape(6.dp),
            modifier = Modifier.heightIn(min = 48.dp).testTag("keu_lc")) { Text(if (view.tradeFinance) "Matikan L/C" else "Aktifkan L/C") }
        Text(view.leaseText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(5, 10, 20).forEachIndexed { i, pct ->
                OutlinedButton(onClick = { onSaleLeaseback(pct) }, enabled = view.leaseBlocks.getOrNull(i) == null, shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("keu_slb_$pct")) { Text("Jual-sewa $pct%") }
            }
        }
        view.leaseBlocks.filterNotNull().firstOrNull()?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    IntelCard {
        Text("Forensik dan audit", style = MaterialTheme.typography.titleMedium)
        Text(view.mScoreText, style = MaterialTheme.typography.bodyMedium,
            color = if (view.mScoreFlag) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        Text(view.auditText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..2).forEach { lv ->
                FilterChip(selected = view.auditorLevel == lv, onClick = { if (ap >= 1) onSetAuditor(lv) }, enabled = view.auditorLevel == lv || ap >= 1,
                    label = { Text(listOf("Tanpa", "Regional", "Internasional")[lv]) }, modifier = Modifier.testTag("aud_$lv"))
            }
        }
        Text(view.dressText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(10, 25, 50).forEachIndexed { i, pct ->
                OutlinedButton(onClick = { onWindowDress(pct) }, enabled = view.dressBlocks.getOrNull(i) == null, shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("wdr_$pct")) { Text("Percantik $pct%") }
            }
        }
        view.dressBlocks.filterNotNull().firstOrNull()?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(view.spvText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(2000, 5000, 10000).forEachIndexed { i, amt ->
                OutlinedButton(onClick = { onOpenSpv(amt) }, enabled = view.spvBlocks.getOrNull(i) == null, shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("spv_$amt")) { Text("SPV $amt") }
            }
        }
        view.spvBlocks.filterNotNull().firstOrNull()?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(view.lawText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (view.stockSeries.size >= 2) StockChartCard(view.stockSeries, view.stockText)
    if (view.stakes.isNotEmpty()) StakeCard(view.stakes, ap, onSellStake)
    IntelCard {
        Text("Derivatif lindung nilai", style = MaterialTheme.typography.titleMedium)
        Text("Diselesaikan tunai saat jatuh tempo. Premi dibayar di muka dan sudah memuat marjin; derivatif bukan sumber untung rata-rata, tetapi meredam guncangan. Satu kontrak per jenis dan hanya sampai besar eksposur.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        view.derivs.forEach { d ->
            Text(d.label, style = MaterialTheme.typography.titleSmall)
            Text(d.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (d.activeText != null) {
                Text("Aktif: ${d.activeText}", style = MaterialTheme.typography.bodySmall)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(25, 50, 100).forEachIndexed { i, pct ->
                        OutlinedButton(onClick = { onBuyDerivative(d.code, pct) }, enabled = d.blocks.getOrNull(i) == null, shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("drv_${d.code}_$pct")) { Text("$pct% · premi ${"%.0f".format(d.premiums.getOrElse(i) { 0.0 })}") }
                    }
                }
                d.blocks.filterNotNull().firstOrNull()?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
