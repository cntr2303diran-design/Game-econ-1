package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.engine.Challenge
import com.example.engine.FundGame
import com.example.engine.GovernorGame
import com.example.engine.Leaderboard
import com.example.engine.Role
import com.example.engine.RoleAction
import com.example.engine.RoleGame
import com.example.engine.RoleGames
import com.example.engine.Submission
import com.example.ui.theme.KapitalSemanticColors
import com.example.ui.theme.MonoFontFamily

/**
 * Mode peran alternatif (Gubernur Bank Sentral, Manajer Dana) dan tantangan asinkron.
 *
 * Seluruh keadaan permainan disimpan di layar ini (tidak disimpan ke berkas simpanan CEO) dan
 * seluruhnya dihitung oleh mesin deterministik di paket engine. Layar ini baru terbukti
 * lolos kompilasi lewat CI; tampilannya belum diuji di emulator (lihat docs/UJI_MANUAL_UI.md).
 */
@Composable
fun PeranScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var game by remember { mutableStateOf<RoleGame?>(null) }
    var challenge by remember { mutableStateOf<Challenge?>(null) }
    // Mesin bersifat mutabel di luar Compose; penghitung ini memaksa gambar ulang setelah tiap aksi.
    var version by remember { mutableIntStateOf(0) }
    var showBoard by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp).testTag("peran_back")) { Text("Kembali") }
            Text("Peran dan tantangan", style = MaterialTheme.typography.titleLarge)
        }
        val g = game
        if (showBoard) {
            LeaderboardPanel(challenge = challenge, onClose = { showBoard = false })
        } else if (g == null) {
            PilihPeran(onStart = { c ->
                challenge = c
                game = RoleGames.create(c.role, c.seed, c.horizon)
                version++
            })
        } else {
            // Membaca version agar Compose tahu bagian ini bergantung pada keadaan mesin.
            val tick = version
            key(tick) {
                if (g.finished) {
                    Selesai(g, challenge, onAgain = { game = null; challenge = null }, onBoard = { showBoard = true })
                } else when (g) {
                    is GovernorGame -> MainGubernur(g) { version++ }
                    is FundGame -> MainDana(g) { version++ }
                    else -> Text("Peran ini belum punya layar.")
                }
            }
            if (!g.finished) {
                TextButton(onClick = { game = null; challenge = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Tinggalkan sesi ini")
                }
            }
        }
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, KapitalSemanticColors.border, RoundedCornerShape(8.dp))
    ) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
}

@Composable
private fun PilihPeran(onStart: (Challenge) -> Unit) {
    var role by remember { mutableStateOf(Role.GUBERNUR) }
    var seedText by remember { mutableStateOf("") }
    var horizon by remember { mutableIntStateOf(60) }
    var importText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }

    Panel {
        Text("Pilih peran", style = MaterialTheme.typography.titleMedium)
        listOf(Role.GUBERNUR, Role.DANA).forEach { r ->
            val sel = role == r
            val label = "${r.label}: ${r.goal}"
            if (sel) Button(onClick = { role = r }, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("peran_${r.code}")) { Text(label) }
            else OutlinedButton(onClick = { role = r }, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("peran_${r.code}")) { Text(label) }
        }
        Text("Peran Menteri belum tersedia.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = seedText,
            onValueChange = { seedText = it.filter { c -> c.isDigit() }.take(9) },
            label = { Text("Seed (kosong = acak)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("peran_seed")
        )
        Text("Lama permainan", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(24, 60, 120).forEach { h ->
                val label = "$h bln"
                if (horizon == h) Button(onClick = { horizon = h }, shape = RoundedCornerShape(6.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
                else OutlinedButton(onClick = { horizon = h }, shape = RoundedCornerShape(6.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
            }
        }
        Button(
            onClick = {
                val seed = seedText.toLongOrNull() ?: (System.currentTimeMillis() % 1_000_000_000L)
                onStart(Challenge(role, seed, horizon))
            },
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("peran_mulai")
        ) { Text("Mulai") }
    }

    Panel {
        Text("Terima tantangan", style = MaterialTheme.typography.titleMedium)
        Text("Tempel kode tantangan dari pemain lain; Anda dan dia memainkan dunia yang persis sama.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = importText,
            onValueChange = { importText = it; importError = null },
            label = { Text("Kode tantangan") },
            modifier = Modifier.fillMaxWidth().testTag("peran_impor")
        )
        importError?.let { Text(it, color = KapitalSemanticColors.red, style = MaterialTheme.typography.bodySmall) }
        OutlinedButton(
            onClick = {
                val c = Challenge.decode(importText)
                when {
                    c == null -> importError = "Kode tantangan tidak sah."
                    c.role == Role.CEO -> importError = "Tantangan CEO belum punya layar di mode ini."
                    else -> onStart(c)
                }
            },
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text("Mainkan tantangan") }
    }
}

@Composable
private fun MainGubernur(g: GovernorGame, onChanged: () -> Unit) {
    var rate by remember(g.months) { mutableDoubleStateOf(g.policy) }
    var info by remember { mutableStateOf<String?>(null) }

    fun advance(months: Int) {
        info = null
        repeat(months) {
            if (g.finished) return@repeat
            if (rate != g.policy && !g.apply(RoleAction.SetPolicy(rate))) {
                info = "Perubahan suku bunga ditolak: batas ${GovernorGame.MAX_MOVE} poin per bulan dan rentang 1 sampai 16%."
                onChanged()
                return
            }
            g.apply(RoleAction.NextMonth)
        }
        onChanged()
    }

    Panel {
        Text(Role.GUBERNUR.label, style = MaterialTheme.typography.titleMedium)
        g.summary().forEach { Text(it, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily)) }
    }
    Panel {
        Text("Suku bunga acuan bulan depan", style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { rate = (rate - 0.25).coerceAtLeast(1.0) }, modifier = Modifier.heightIn(min = 48.dp).testTag("gub_turun")) { Text("-0,25") }
            Text("${"%.2f".format(rate)}%", style = MaterialTheme.typography.headlineSmall.copy(fontFamily = MonoFontFamily))
            OutlinedButton(onClick = { rate = (rate + 0.25).coerceAtMost(16.0) }, modifier = Modifier.heightIn(min = 48.dp).testTag("gub_naik")) { Text("+0,25") }
        }
        info?.let { Text(it, color = KapitalSemanticColors.red, style = MaterialTheme.typography.bodySmall) }
        Button(onClick = { advance(1) }, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("gub_lanjut")) { Text("Tetapkan dan lanjut 1 bulan") }
        OutlinedButton(onClick = { advance(6) }, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Tahan suku bunga dan lanjut 6 bulan") }
    }
}

@Composable
private fun MainDana(g: FundGame, onChanged: () -> Unit) {
    var info by remember { mutableStateOf<String?>(null) }
    fun act(a: RoleAction) {
        info = if (g.apply(a)) null else "Aksi ditolak: kas kurang, saham tidak cukup, atau bobot satu saham melebihi ${(FundGame.MAX_WEIGHT * 100).toInt()}% dana."
        onChanged()
    }

    Panel {
        Text(Role.DANA.label, style = MaterialTheme.typography.titleMedium)
        g.summary().forEach { Text(it, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily)) }
        info?.let { Text(it, color = KapitalSemanticColors.red, style = MaterialTheme.typography.bodySmall) }
        Button(onClick = { act(RoleAction.NextMonth) }, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("dana_lanjut")) { Text("Lanjut 1 bulan") }
    }
    g.sim.bourse.companies.filter { it.listed }.forEach { c ->
        val have = g.holdings[c.spec.id] ?: 0
        Panel {
            Text("${c.spec.ticker} · ${c.spec.name}", style = MaterialTheme.typography.titleSmall)
            Text(
                "Harga ${"%.0f".format(c.price)} · punya $have lot",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { act(RoleAction.Buy(c.spec.id, 1)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("beli_${c.spec.id}")) { Text("Beli 1 lot") }
                OutlinedButton(onClick = { act(RoleAction.Sell(c.spec.id, 1)) }, enabled = have > 0, modifier = Modifier.heightIn(min = 48.dp).testTag("jual_${c.spec.id}")) { Text("Jual 1 lot") }
            }
        }
    }
}

@Composable
private fun Selesai(g: RoleGame, challenge: Challenge?, onAgain: () -> Unit, onBoard: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    Panel {
        Text("Selesai: ${g.role.label}", style = MaterialTheme.typography.titleMedium)
        g.summary().forEach { Text(it, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily)) }
    }
    if (challenge != null) {
        Panel {
            Text("Bagikan", style = MaterialTheme.typography.titleMedium)
            Text(
                "Kode tantangan memuat seed, sehingga lawan memainkan dunia yang sama. Hasil memuat seluruh log aksi Anda; penerima memutar ulangnya sendiri untuk membuktikan skor, sehingga skor yang dikarang ditolak. " +
                    "Log yang disalin dari pemain lain tidak bisa dicegah.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(challenge.encode())) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("salin_tantangan")
            ) { Text("Salin kode tantangan") }
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(24) },
                label = { Text("Nama Anda") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("peran_nama")
            )
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(Submission(name, challenge, g.log.toList(), g.score()).encode())) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("salin_hasil")
            ) { Text("Salin hasil saya") }
            OutlinedButton(onClick = onBoard, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Papan peringkat") }
        }
    }
    Button(onClick = onAgain, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Main lagi") }
}

@Composable
private fun LeaderboardPanel(challenge: Challenge?, onClose: () -> Unit) {
    if (challenge == null) {
        Panel { Text("Tidak ada tantangan aktif.") }
        TextButton(onClick = onClose) { Text("Tutup") }
        return
    }
    val board = remember(challenge.id) { Leaderboard(challenge) }
    var pasted by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableIntStateOf(0) }

    Panel {
        Text("Papan peringkat ${challenge.role.label}", style = MaterialTheme.typography.titleMedium)
        Text("Tempel hasil dari pemain lain (satu per satu). Skor dihitung ulang dari log aksinya; yang tidak cocok ditolak.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = pasted, onValueChange = { pasted = it },
            label = { Text("Hasil pemain") },
            modifier = Modifier.fillMaxWidth().testTag("papan_tempel")
        )
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Button(
            onClick = {
                val sub = Submission.decode(pasted)
                message = if (sub == null) "Format hasil tidak sah."
                else {
                    val v = board.submit(sub)
                    pasted = ""
                    version++
                    if (v.ok) "Diterima: skor terverifikasi ${v.score}." else "Ditolak: ${v.reason}"
                }
            },
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("papan_tambah")
        ) { Text("Verifikasi dan tambahkan") }
    }
    Panel {
        val tick = version
        val rows = board.ranking()
        if (rows.isEmpty()) Text("Belum ada hasil terverifikasi.", style = MaterialTheme.typography.bodySmall)
        key(tick) {
            rows.forEachIndexed { i, r ->
                Text(
                    "${i + 1}. ${r.player} · skor ${r.score} · ${r.actions} aksi",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily)
                )
            }
        }
    }
    TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("Tutup") }
}
