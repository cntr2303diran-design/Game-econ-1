package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.components.semanticToneColor
import com.example.ui.theme.KapitalSemanticColors
import com.example.ui.theme.MonoFontFamily

@Composable
private fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(8.dp))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun PairRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(v, style = MaterialTheme.typography.bodySmall, fontFamily = MonoFontFamily, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun BankTabContent(
    credit: CreditViewState,
    ap: Int,
    onApply: (bankId: String, amount: Int, tenor: Int, purpose: String, floating: Boolean, bullet: Boolean) -> Unit,
    onReview: (String) -> Unit,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit
) {
    var formBank by rememberSaveable { mutableStateOf<String?>(null) }
    var amount by rememberSaveable { mutableStateOf(6000) }
    var tenor by rememberSaveable { mutableStateOf(36) }
    var purpose by rememberSaveable { mutableStateOf("MODAL_KERJA") }
    var floating by rememberSaveable { mutableStateOf(true) }
    var bullet by rememberSaveable { mutableStateOf(false) }
    val purposes = listOf(
        "MODAL_KERJA" to "Modal kerja", "EKSPANSI" to "Belanja modal / ekspansi",
        "PEMINTALAN" to "Proyek pemintalan", "REFINANCING" to "Refinancing"
    )

    Text("Bank dan pembiayaan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(credit.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    PanelCard {
        Text("Yang dilihat bank tentang Anda", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        credit.ratios.forEach { PairRow(it.first, it.second) }
    }
    PanelCard {
        Text("Agunan yang tersedia (setelah haircut bank)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        credit.collateral.forEach { PairRow(it.first, it.second) }
    }

    if (credit.applications.isNotEmpty()) {
        Text("Pengajuan Anda", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        credit.applications.forEach { a ->
            PanelCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(a.bankName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(a.statusLabel, color = semanticToneColor(a.statusTone), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
                Text("${a.purposeText} · ${a.amountText}", style = MaterialTheme.typography.bodySmall)
                Text(a.stageText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                a.offer?.let { o ->
                    HorizontalDivider(color = KapitalSemanticColors.border)
                    PairRow("Plafon", o.amountText)
                    PairRow("Bunga", o.rateText)
                    PairRow("Tenor", o.tenorText)
                    PairRow("Biaya", o.feeText)
                    PairRow("Struktur", o.structureText)
                    PairRow("Covenant", o.covenantText)
                    o.extraTerms.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    if (o.hiddenKnown) {
                        o.hiddenTerms.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = KapitalSemanticColors.red) }
                        if (o.hiddenTerms.isEmpty()) Text("Penasihat hukum tidak menemukan klausul berbahaya tambahan.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("Dokumen lengkap memuat klausul lain yang belum Anda baca. Tinjauan penasihat hukum (1 AP, 50 jt RS) mengungkapnya.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (o.validText.isNotEmpty()) Text(o.validText, style = MaterialTheme.typography.labelMedium)
                }
                if (a.officialReason.isNotEmpty()) {
                    Text("Alasan resmi: ${a.officialReason}", style = MaterialTheme.typography.bodySmall, color = KapitalSemanticColors.red)
                    Text(a.informalNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (a.canReview || a.canAccept || a.canDecline) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (a.canReview) OutlinedButton(onClick = { onReview(a.id) }, modifier = Modifier.testTag("review_${a.id}")) { Text("Tinjau hukum") }
                        if (a.canAccept) Button(onClick = { onAccept(a.id) }, modifier = Modifier.testTag("accept_${a.id}")) { Text("Terima (1 AP)") }
                        if (a.canDecline) OutlinedButton(onClick = { onDecline(a.id) }) { Text("Tolak") }
                    }
                }
            }
        }
    }

    Text("Pilih bank untuk mengajukan kredit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    credit.banks.forEach { b ->
        PanelCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(b.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("${b.banker} · ${b.bankerRole}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(b.relationText, color = semanticToneColor(b.relationTone), style = MaterialTheme.typography.labelMedium)
            }
            Text(b.style, style = MaterialTheme.typography.bodySmall)
            Text("Kepentingan bankir: ${b.incentive}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (b.tellKnown) Text("Pola yang Anda amati: ${b.tell}", style = MaterialTheme.typography.bodySmall, color = KapitalSemanticColors.yellow)
            PairRow("Bunga", b.rateText)
            PairRow("Kecepatan", b.speedText)
            PairRow("Tenor maks / plafon", "${b.maxTenor} bln · ${b.maxAmountText}")
            if (formBank == b.id) {
                HorizontalDivider(color = KapitalSemanticColors.border)
                Text("Pengajuan baru", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { amount = (amount - 1000).coerceAtLeast(1000) }) { Text("−1.000") }
                    Text("${amount} jt RS", fontFamily = MonoFontFamily)
                    OutlinedButton(onClick = { amount = (amount + 1000).coerceAtMost(40000) }) { Text("+1.000") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(12, 24, 36, 60, 84).filter { it <= b.maxTenor }.forEach { t ->
                        FilterChip(selected = tenor == t, onClick = { tenor = t }, label = { Text("$t bln") })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    purposes.take(2).forEach { (k, v) -> FilterChip(selected = purpose == k, onClick = { purpose = k }, label = { Text(v) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    purposes.drop(2).forEach { (k, v) -> FilterChip(selected = purpose == k, onClick = { purpose = k }, label = { Text(v) }) }
                }
                if (!b.foreign) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = floating, onClick = { floating = true }, label = { Text("Bunga mengambang") })
                    FilterChip(selected = !floating, onClick = { floating = false }, label = { Text("Bunga tetap (+0,6)") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = !bullet, onClick = { bullet = false }, label = { Text("Cicilan merata") })
                    FilterChip(selected = bullet, onClick = { bullet = true }, label = { Text("Bullet (−0,3)") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onApply(b.id, amount, tenor.coerceAtMost(b.maxTenor), purpose, floating, bullet); formBank = null },
                        enabled = b.canApply, modifier = Modifier.testTag("submit_credit")
                    ) { Text("Kirim pengajuan (2 AP, 30 jt RS)") }
                    TextButton(onClick = { formBank = null }) { Text("Batal") }
                }
            } else {
                Button(onClick = { formBank = b.id }, enabled = b.canApply && ap >= 2, modifier = Modifier.testTag("apply_${b.id}")) { Text("Ajukan kredit") }
                if (!b.canApply) Text(b.blockReason, style = MaterialTheme.typography.bodySmall, color = KapitalSemanticColors.red)
            }
        }
    }

    Text("Pinjaman berjalan", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    credit.loans.forEach { l ->
        PanelCard {
            Text(l.lender, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            PairRow("Saldo", l.balanceText)
            PairRow("Bunga", l.rateText)
            PairRow("Jatuh tempo", l.maturityText)
            PairRow("Pembayaran", l.repaymentText)
            PairRow("Covenant", l.covenantText)
            if (l.flags.isNotEmpty()) Text(l.flags.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = KapitalSemanticColors.yellow)
        }
    }
}

@Composable
fun PelakuTabContent(actors: List<ActorItem>) {
    Text("Pelaku di dunia Anda", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(
        "Setiap pihak di bawah ini mengejar kepentingannya sendiri, bukan melayani Anda. Mereka hanya bertindak dari informasi yang wajar mereka ketahui, dan setiap manuver meninggalkan jejak yang bisa Anda baca.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    actors.groupBy { it.group }.forEach { (group, items) ->
        Text(group, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        items.forEach { a ->
            var open by rememberSaveable(a.id) { mutableStateOf(false) }
            Surface(
                onClick = { open = !open },
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(8.dp)).testTag("actor_${a.id}")
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(a.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = semanticToneColor(a.tone))
                    Text(a.role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Yang diincar: ${a.goal}", style = MaterialTheme.typography.bodySmall)
                    Text("Terakhir: ${a.lastAction}", style = MaterialTheme.typography.bodySmall)
                    if (open) {
                        Text("Tanda yang bisa dibaca", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        a.signals.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        Text("Cara menghadapi: ${a.advice}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Ketuk untuk melihat tanda dan cara menghadapi", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}


@Composable
fun InvestTabContent(
    items: List<InvestItem>,
    summary: List<Pair<String, String>>,
    ap: Int,
    onInvest: (String) -> Unit,
    onCorporateAction: (String) -> Unit = {}
) {
    Text("Ekspansi dan investasi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(
        "Anda bebas memilih. Kapasitas pabrik dan permintaan pasar harus tumbuh bersama: lini baru tanpa tambahan permintaan hanya menambah biaya, " +
            "sedangkan permintaan tanpa kapasitas berarti pesanan tertolak. Dana keluar bertahap dari kas; ajukan kredit di tab Bank bila perlu. AP sekarang: $ap.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    PanelCard {
        Text("Kondisi kapasitas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        summary.forEach { (k, v) -> PairRow(k, v) }
    }
    PanelCard {
        Text("Multinasional dan korporasi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            "Anak usaha punya pembukuan lokal dan dikonsolidasikan ke laporan grup. Hasil tindakan tampil di Koran Pagi dan laporan keuangan; " +
                "tindakan yang syaratnya belum terpenuhi (AP, kas, era) ditolak tanpa biaya.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val actions = listOf(
            "Bangun anak usaha Tarsis" to "SUB|tarsis",
            "Bangun anak usaha Kestrel" to "SUB|kestrel",
            "Bangun anak usaha Halvenia" to "SUB|halvenia",
            "Bangun anak usaha Meridia" to "SUB|meridia",
            "Repatriasi dividen Tarsis" to "REP|tarsis",
            "Repatriasi dividen Kestrel" to "REP|kestrel",
            "Repatriasi dividen Halvenia" to "REP|halvenia",
            "Repatriasi dividen Meridia" to "REP|meridia",
            "Transfer pricing: wajar" to "TPR|WAJAR",
            "Transfer pricing: optimal" to "TPR|OPTIMAL",
            "Transfer pricing: agresif (berisiko audit)" to "TPR|AGRESIF",
            "Forward valas 6 bulan" to "HFX",
            "Terbitkan obligasi 3.000 jt RS, 36 bulan" to "BND|3000|36",
            "IPO 20% saham" to "IPO|20|false",
            "Beli kembali saham 1.500 jt RS" to "BYB|1500",
            "Terbitkan saham lanjutan 3.000 jt RS" to "SPO|3000",
            "Jual 15% saham anak usaha Tarsis" to "NCI|tarsis|15",
            "Jual 15% saham anak usaha Kestrel" to "NCI|kestrel|15",
            "Akuisisi Importir Velmora" to "ACQ|riv_importir",
            "Akuisisi Cahaya Weaving" to "ACQ|riv_cahaya",
            "Akuisisi Prima Garmen" to "ACQ|riv_prima",
            "Beli media" to "LOB|media",
            "Lobi parlemen (tarif turun, berisiko etika)" to "LOB|parliament",
            "Beli 5 kredit karbon" to "CRB|buy|5",
            "Jual 3 kredit karbon" to "CRB|sell|3",
            "Tunjuk CFO sebagai penerus CEO" to "SUC|exec_cfo",
            "Tunjuk COO sebagai penerus CEO" to "SUC|exec_coo",
            "Tunjuk anggota keluarga sebagai penerus" to "SUC|family",
            "Dirikan bank dan asuransi grup" to "CBK"
        )
        actions.forEach { (label, code) ->
            OutlinedButton(
                onClick = { onCorporateAction(code) },
                modifier = Modifier.fillMaxWidth().testTag("corp_$code")
            ) { Text(label) }
        }
    }
    items.forEach { inv ->
        PanelCard {
            Text(inv.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(inv.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PairRow("Anggaran", inv.costText)
            PairRow("Durasi", inv.durationText)
            PairRow("Manfaat", inv.benefitText)
            PairRow("Status", inv.statusText)
            Button(
                onClick = { onInvest(inv.id) },
                enabled = inv.enabled,
                modifier = Modifier.fillMaxWidth().testTag("invest_${inv.id}")
            ) { Text("Mulai (${inv.apCost} AP)") }
            if (!inv.enabled && inv.blockReason != null) {
                Text(inv.blockReason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
