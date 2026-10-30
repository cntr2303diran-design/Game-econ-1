package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.engine.Challenge
import com.example.engine.Difficulty
import com.example.engine.ENGINE_VERSION
import com.example.engine.FundGame
import com.example.engine.GovernorGame
import com.example.engine.Leaderboard
import com.example.engine.MinisterGame
import com.example.engine.Role
import com.example.engine.RoleAction
import com.example.engine.RoleGame
import com.example.engine.RoleGames
import com.example.engine.Submission
import com.example.ui.theme.KapitalSemanticColors
import com.example.ui.theme.MonoFontFamily

/** Sesi mode peran: tantangan + permainan. Disimpan sebagai teks (tantangan + log aksi) dan dibangun ulang dengan putar ulang. */
private class Session(val challenge: Challenge, val game: RoleGame)

private val SessionSaver: Saver<Session?, String> = Saver(
    save = { s -> if (s == null) "" else s.challenge.encode() + "\n" + s.game.log.joinToString("\n") },
    restore = { text ->
        if (text.isBlank()) null
        else {
            val lines = text.split("\n")
            val c = Challenge.decode(lines[0])
            val g = if (c == null) null else RoleGames.replay(c.role, c.seed, c.horizon, lines.drop(1).filter { it.isNotBlank() })
            if (c != null && g != null) Session(c, g) else null
        }
    }
)

private val ROLES_WITH_SCREEN = listOf(Role.GUBERNUR, Role.MENTERI, Role.DANA)

/**
 * Mode peran alternatif (Gubernur, Menteri Keuangan, Manajer Dana) dan tantangan asinkron (termasuk tantangan CEO).
 *
 * Sesi dan papan peringkat bertahan saat layar diputar atau aplikasi dimatikan sistem sebentar (rememberSaveable), tetapi
 * tidak ikut berkas simpanan CEO. Seluruh perhitungan ada di mesin deterministik di paket engine. Layar ini baru terbukti
 * lolos kompilasi lewat CI; tampilannya belum diuji di emulator (lihat docs/UJI_MANUAL_UI.md).
 *
 * @param onPlayCeo memulai tantangan CEO pada seed dan kesulitan yang diberikan (pemanggil membuka Markas).
 * @param ceoShare menghasilkan teks hasil CEO untuk nama tertentu, atau null bila kampanye CEO belum berakhir.
 */
@Composable
fun PeranScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayCeo: (Challenge) -> Unit = {},
    ceoShare: (String) -> String? = { null }
) {
    var session by rememberSaveable(stateSaver = SessionSaver) { mutableStateOf<Session?>(null) }
    // Mesin bersifat mutabel di luar Compose; penghitung ini memaksa gambar ulang setelah tiap aksi.
    var version by remember { mutableIntStateOf(0) }
    var showBoard by rememberSaveable { mutableStateOf(false) }
    var boardChallenge by rememberSaveable { mutableStateOf("") }
    var boardRows by rememberSaveable { mutableStateOf("") }
    var playerName by rememberSaveable { mutableStateOf("") }

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
        val s = session
        if (showBoard) {
            LeaderboardPanel(
                challengeText = boardChallenge,
                rows = boardRows,
                onChange = { c, r -> boardChallenge = c; boardRows = r },
                onClose = { showBoard = false }
            )
        } else if (s == null) {
            PilihPeran(
                playerName = playerName,
                onName = { playerName = it },
                onStart = { c ->
                    val g = RoleGames.create(c.role, c.seed, c.horizon)
                    if (g != null) { session = Session(c, g); version++ }
                },
                onPlayCeo = onPlayCeo,
                ceoShare = ceoShare,
                onBoard = { showBoard = true }
            )
        } else {
            val g = s.game
            // Membaca version agar Compose tahu bagian ini bergantung pada keadaan mesin.
            val tick = version
            key(tick) {
                if (g.finished) {
                    Selesai(
                        g = g, challenge = s.challenge, name = playerName, onName = { playerName = it },
                        onAgain = { session = null },
                        onBoard = {
                            // Hasil sendiri otomatis masuk papan tantangan ini.
                            val b = Leaderboard(s.challenge)
                            if (boardChallenge == s.challenge.encode()) b.restore(boardRows)
                            b.submit(Submission(playerName, s.challenge, g.log.toList(), g.score()))
                            boardChallenge = s.challenge.encode(); boardRows = b.export()
                            showBoard = true
                        }
                    )
                } else when (g) {
                    is GovernorGame -> MainGubernur(g) { version++ }
                    is MinisterGame -> MainMenteri(g) { version++ }
                    is FundGame -> MainDana(g) { version++ }
                    else -> Text("Peran ini belum punya layar.")
                }
            }
            if (!g.finished) {
                TextButton(onClick = { session = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Tinggalkan sesi ini") }
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
private fun ChoiceButton(selected: Boolean, label: String, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (selected) Button(onClick = onClick, shape = RoundedCornerShape(6.dp), modifier = modifier.heightIn(min = 52.dp).testTag(tag)) { Text(label) }
    else OutlinedButton(onClick = onClick, shape = RoundedCornerShape(6.dp), modifier = modifier.heightIn(min = 52.dp).testTag(tag)) { Text(label) }
}

@Composable
private fun PilihPeran(
    playerName: String,
    onName: (String) -> Unit,
    onStart: (Challenge) -> Unit,
    onPlayCeo: (Challenge) -> Unit,
    ceoShare: (String) -> String?,
    onBoard: () -> Unit
) {
    var role by rememberSaveable { mutableStateOf(Role.GUBERNUR.code) }
    var seedText by rememberSaveable { mutableStateOf("") }
    var horizon by rememberSaveable { mutableIntStateOf(60) }
    var importText by rememberSaveable { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    var ceoSeedText by rememberSaveable { mutableStateOf("") }
    var ceoDifficulty by rememberSaveable { mutableStateOf(Difficulty.MANAJER.name) }
    var ceoInfo by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val chosen = Role.of(role) ?: Role.GUBERNUR

    Panel {
        Text("Pilih peran", style = MaterialTheme.typography.titleMedium)
        ROLES_WITH_SCREEN.forEach { r ->
            ChoiceButton(chosen == r, "${r.label}: ${r.goal}", "peran_${r.code}", { role = r.code }, Modifier.fillMaxWidth())
        }
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
            listOf(24, 60, 120).forEach { h -> ChoiceButton(horizon == h, "$h bln", "peran_h$h", { horizon = h }) }
        }
        Button(
            onClick = {
                val seed = seedText.toLongOrNull() ?: (System.currentTimeMillis() % 1_000_000_000L)
                onStart(Challenge(chosen, seed, horizon))
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
                    c.engineVersion != null && c.engineVersion != ENGINE_VERSION ->
                        importError = "Kode tantangan ini dibuat di mesin versi ${c.engineVersion}, aplikasi Anda versi $ENGINE_VERSION: " +
                            "dunia dan skor tidak sebanding. Minta kode baru dari pembuatnya atau samakan versi aplikasi."
                    c.role == Role.CEO -> onPlayCeo(c)
                    else -> onStart(c)
                }
            },
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text("Mainkan tantangan") }
    }

    Panel {
        Text("Tantangan CEO", style = MaterialTheme.typography.titleMedium)
        Text(
            "Satu kampanye penuh sebagai CEO pada seed dan kesulitan yang sama (permainan CEO yang sedang berjalan akan diganti). Setelah kampanye berakhir, kembali ke sini untuk membagikan hasilnya.",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = ceoSeedText,
            onValueChange = { ceoSeedText = it.filter { c -> c.isDigit() }.take(9) },
            label = { Text("Seed (kosong = acak)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("ceo_seed")
        )
        Difficulty.values().toList().chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { d ->
                    ChoiceButton(ceoDifficulty == d.name, d.label, "ceo_dif_${d.name}", { ceoDifficulty = d.name }, Modifier.weight(1f))
                }
            }
        }
        Button(
            onClick = {
                val seed = ceoSeedText.toLongOrNull() ?: (System.currentTimeMillis() % 1_000_000_000L)
                val dif = Difficulty.values().firstOrNull { it.name == ceoDifficulty } ?: Difficulty.MANAJER
                val c = Challenge(Role.CEO, seed, com.example.data.CEO_CHALLENGE_HORIZON, dif)
                clipboard.setText(AnnotatedString(c.encode()))
                onPlayCeo(c)
            },
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("ceo_mulai")
        ) { Text("Mulai tantangan CEO (kode disalin otomatis)") }
        OutlinedTextField(
            value = playerName, onValueChange = { onName(it.take(24)) },
            label = { Text("Nama Anda") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("peran_nama_ceo")
        )
        ceoInfo?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        OutlinedButton(
            onClick = {
                val text = ceoShare(playerName)
                if (text == null) ceoInfo = "Kampanye CEO terakhir belum berakhir; selesaikan dulu."
                else { clipboard.setText(AnnotatedString(text)); ceoInfo = "Hasil kampanye CEO disalin. Tempel di papan peringkat atau kirim ke lawan." }
            },
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("ceo_bagikan")
        ) { Text("Salin hasil kampanye CEO terakhir") }
    }

    OutlinedButton(onClick = onBoard, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("peran_papan")) {
        Text("Papan peringkat")
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
private fun MainMenteri(g: MinisterGame, onChanged: () -> Unit) {
    var spend by remember(g.months) { mutableDoubleStateOf(g.spending) }
    var tax by remember(g.months) { mutableDoubleStateOf(g.tax) }
    var info by remember { mutableStateOf<String?>(null) }

    fun advance(months: Int) {
        info = null
        repeat(months) {
            if (g.finished) return@repeat
            if (spend != g.spending && !g.apply(RoleAction.SetSpending(spend))) {
                info = "Belanja ditolak: perubahan maksimum ${MinisterGame.MAX_MOVE.toInt()} poin PDB per bulan, rentang ${MinisterGame.MIN_SPEND.toInt()} sampai ${MinisterGame.MAX_SPEND.toInt()}%."
                onChanged(); return
            }
            if (tax != g.tax && !g.apply(RoleAction.SetTax(tax))) {
                info = "Pajak ditolak: perubahan maksimum ${MinisterGame.MAX_MOVE.toInt()} poin PDB per bulan, rentang ${MinisterGame.MIN_TAX.toInt()} sampai ${MinisterGame.MAX_TAX.toInt()}%."
                onChanged(); return
            }
            g.apply(RoleAction.NextMonth)
        }
        onChanged()
    }

    Panel {
        Text(Role.MENTERI.label, style = MaterialTheme.typography.titleMedium)
        g.summary().forEach { Text(it, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily)) }
        Text(
            "Defisit struktural di atas 2% PDB mendorong permintaan, tetapi utang yang tinggi menaikkan premi risiko dan bunga. Utang 120% PDB berarti gagal bayar.",
            style = MaterialTheme.typography.bodySmall
        )
    }
    Panel {
        Text("Belanja pemerintah (% PDB)", style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { spend = (spend - 0.5).coerceAtLeast(MinisterGame.MIN_SPEND) }, modifier = Modifier.heightIn(min = 48.dp).testTag("men_belanja_turun")) { Text("-0,5") }
            Text("${"%.1f".format(spend)}%", style = MaterialTheme.typography.headlineSmall.copy(fontFamily = MonoFontFamily))
            OutlinedButton(onClick = { spend = (spend + 0.5).coerceAtMost(MinisterGame.MAX_SPEND) }, modifier = Modifier.heightIn(min = 48.dp).testTag("men_belanja_naik")) { Text("+0,5") }
        }
        Text("Penerimaan pajak (% PDB)", style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { tax = (tax - 0.5).coerceAtLeast(MinisterGame.MIN_TAX) }, modifier = Modifier.heightIn(min = 48.dp).testTag("men_pajak_turun")) { Text("-0,5") }
            Text("${"%.1f".format(tax)}%", style = MaterialTheme.typography.headlineSmall.copy(fontFamily = MonoFontFamily))
            OutlinedButton(onClick = { tax = (tax + 0.5).coerceAtMost(MinisterGame.MAX_TAX) }, modifier = Modifier.heightIn(min = 48.dp).testTag("men_pajak_naik")) { Text("+0,5") }
        }
        info?.let { Text(it, color = KapitalSemanticColors.red, style = MaterialTheme.typography.bodySmall) }
        Button(onClick = { advance(1) }, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("men_lanjut")) { Text("Tetapkan anggaran dan lanjut 1 bulan") }
        OutlinedButton(onClick = { advance(6) }, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Tahan anggaran dan lanjut 6 bulan") }
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
private fun Selesai(g: RoleGame, challenge: Challenge, name: String, onName: (String) -> Unit, onAgain: () -> Unit, onBoard: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Panel {
        Text("Selesai: ${g.role.label}", style = MaterialTheme.typography.titleMedium)
        g.summary().forEach { Text(it, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily)) }
    }
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
            value = name, onValueChange = { onName(it.take(24)) },
            label = { Text("Nama Anda") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("peran_nama")
        )
        OutlinedButton(
            onClick = { clipboard.setText(AnnotatedString(Submission(name, challenge, g.log.toList(), g.score()).encode())) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("salin_hasil")
        ) { Text("Salin hasil saya") }
        OutlinedButton(onClick = onBoard, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Papan peringkat (hasil Anda masuk otomatis)") }
    }
    Button(onClick = onAgain, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Main lagi") }
}

@Composable
private fun LeaderboardPanel(challengeText: String, rows: String, onChange: (String, String) -> Unit, onClose: () -> Unit) {
    var codeField by remember { mutableStateOf("") }
    var pasted by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val challenge = Challenge.decode(challengeText)
    val board = remember(challengeText, rows) { challenge?.let { c -> Leaderboard(c).also { it.restore(rows) } } }

    Panel {
        Text("Papan peringkat", style = MaterialTheme.typography.titleMedium)
        if (challenge == null) {
            Text("Belum ada tantangan di papan. Tempel kode tantangan, atau langsung tempel hasil pemain (papan memakai tantangan dari hasil pertama).", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = codeField, onValueChange = { codeField = it; message = null },
                label = { Text("Kode tantangan") },
                modifier = Modifier.fillMaxWidth().testTag("papan_kode")
            )
            OutlinedButton(
                onClick = {
                    val c = Challenge.decode(codeField)
                    if (c == null) message = "Kode tantangan tidak sah." else { onChange(c.encode(), ""); message = null }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("Pakai tantangan ini") }
        } else {
            Text("${challenge.role.label} · seed ${challenge.seed} · ${challenge.horizon} bulan · ${challenge.difficulty.label}", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily))
        }
        Text("Tempel hasil pemain satu per satu. Skor dihitung ulang dari log aksinya; yang tidak cocok ditolak. Hasil CEO butuh beberapa detik.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = pasted, onValueChange = { pasted = it },
            label = { Text("Hasil pemain") },
            modifier = Modifier.fillMaxWidth().testTag("papan_tempel")
        )
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Button(
            onClick = {
                val sub = Submission.decode(pasted)
                if (sub == null) message = "Format hasil tidak sah."
                else {
                    val b = Leaderboard(challenge ?: sub.challenge).also { if (challenge != null) it.restore(rows) }
                    val v = b.submit(sub)
                    if (v.ok) {
                        onChange((challenge ?: sub.challenge).encode(), b.export())
                        pasted = ""
                        message = "Diterima: skor terverifikasi ${v.score}."
                    } else message = "Ditolak: ${v.reason}"
                }
            },
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("papan_tambah")
        ) { Text("Verifikasi dan tambahkan") }
    }
    Panel {
        val ranking = board?.ranking().orEmpty()
        if (ranking.isEmpty()) Text("Belum ada hasil terverifikasi.", style = MaterialTheme.typography.bodySmall)
        ranking.forEachIndexed { i, r ->
            Text(
                "${i + 1}. ${r.player} · skor ${r.score} · ${r.actions} aksi",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily)
            )
        }
        if (challenge != null) {
            TextButton(onClick = { onChange("", "") }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Kosongkan papan") }
        }
    }
    TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("Tutup") }
}
