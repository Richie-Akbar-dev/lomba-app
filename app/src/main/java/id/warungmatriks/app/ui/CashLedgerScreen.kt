package id.warungmatriks.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.warungmatriks.app.data.*
import id.warungmatriks.app.ui.theme.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

internal fun cashHistorySelection(current: String?, selected: String): String? = if (current == selected) null else selected

internal fun cashNetText(amount: Long): String = if (amount < 0) "-" + rupiah(-amount) else rupiah(amount)
private fun cashDate(time: Long): String = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale("id", "ID")).format(Date(time))

@Composable
internal fun CashLedgerScreen(ownerId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val store = remember(context, ownerId) { CashierStore(context, ownerId) }
    val scope = rememberCoroutineScope()
    var week by remember { mutableStateOf(cashWeek()) }
    var period by rememberSaveable { mutableStateOf("Minggu ini") }
    var revision by remember { mutableIntStateOf(0) }
    var summary by remember { mutableStateOf<CashSummary?>(null) }
    var ins by remember { mutableStateOf<List<CashMovement>>(emptyList()) }
    var outs by remember { mutableStateOf<List<CashMovement>>(emptyList()) }
    var moreIn by remember { mutableStateOf(false) }
    var moreOut by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var historyDirection by rememberSaveable(ownerId) { mutableStateOf<String?>(null) }
    var entryDirection by rememberSaveable(ownerId) { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            val current = cashWeek()
            if (current != week) { week = current; revision++ }
        }
    }
    LaunchedEffect(store, week, period, revision, inspection) {
        loading = true; loadingMore = false; error = null; summary = null; ins = emptyList(); outs = emptyList(); moreIn = false; moreOut = false
        try {
            if (inspection) {
                summary = CashSummary(200000, 45000)
                ins = listOf(CashMovement("preview-in", week.start, "in", "sale", 200000, null))
                outs = listOf(CashMovement("preview-out", week.start, "out", "purchase", 45000, null))
            } else {
                val selectedWeek = if (period == "Minggu ini") week else null
                val sums = store.cashSummary(week)
                val inPage = store.cashMovements("in", selectedWeek)
                val outPage = store.cashMovements("out", selectedWeek)
                summary = sums; ins = inPage.movements; moreIn = inPage.hasMore; outs = outPage.movements; moreOut = outPage.hasMore
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { error = "Data kas belum dapat dimuat. Coba muat ulang." } finally { loading = false }
    }
    fun loadMore(direction: String) {
        if (loadingMore) return
        val currentPeriod = period; val currentWeek = week; val currentRevision = revision
        val offset = if (direction == "in") ins.size else outs.size
        loadingMore = true
        scope.launch {
            try {
                val page = store.cashMovements(direction, if (currentPeriod == "Minggu ini") currentWeek else null, offset)
                if (period == currentPeriod && week == currentWeek && revision == currentRevision) {
                    if (direction == "in") { ins = ins + page.movements; moreIn = page.hasMore }
                    else { outs = outs + page.movements; moreOut = page.hasMore }
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
                if (period == currentPeriod && week == currentWeek && revision == currentRevision) notice = "Riwayat berikutnya belum dapat dimuat."
            } finally { if (period == currentPeriod && week == currentWeek && revision == currentRevision) loadingMore = false }
        }
    }

    DemoPage("Sirkulasi Kas", subtitle = "", onBack = onBack, badgeText = "") {
        DemoCard {
            Text("Kas Saat Ini", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.height(12.dp))
            Surface(color = LightSurface, shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = PaleBlue, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Blue, modifier = Modifier.padding(10.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(summary?.let { cashNetText(it.net) } ?: "—", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Blue)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CashSummaryCard("Kas masuk", summary?.let { rupiah(it.cashIn) } ?: "—", true, Modifier.weight(1f)) { entryDirection = "in" }
            CashSummaryCard("Kas keluar", summary?.let { rupiah(it.cashOut) } ?: "—", false, Modifier.weight(1f)) { entryDirection = "out" }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = historyDirection == "in", onClick = { historyDirection = cashHistorySelection(historyDirection, "in") },
                modifier = Modifier.weight(1f), label = { Text("Uang masuk", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) })
            FilterChip(selected = historyDirection == "out", onClick = { historyDirection = cashHistorySelection(historyDirection, "out") },
                modifier = Modifier.weight(1f), label = { Text("Uang keluar", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) })
        }
        SectionGap(); GroupTitle("Riwayat kas")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Minggu ini", "Semua riwayat").forEach { label -> FilterChip(selected = period == label, onClick = { period = label }, label = { Text(label, fontSize = 12.sp) }) }
            IconButton(onClick = { week = cashWeek(); revision++ }) { Icon(Icons.Default.Refresh, contentDescription = "Muat ulang kas", tint = Blue) }
        }
        if (loading) DemoNote("Memuat kas...")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        notice?.let { Text(it, color = Blue, fontSize = 12.sp) }
        if (historyDirection != "out") {
            Spacer(Modifier.height(12.dp)); GroupTitle("Riwayat Uang Masuk")
            DemoCard {
                if (!loading && error == null && ins.isEmpty()) DemoNote("Belum ada catatan uang masuk.")
                ins.forEach { CashMovementRow(it) }
                if (moreIn) TextButton(onClick = { loadMore("in") }, enabled = !loadingMore) { Text(if (loadingMore) "Memuat..." else "Muat lebih banyak") }
            }
        }
        if (historyDirection != "in") {
            SectionGap()
            Text("Riwayat Uang Keluar", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            DemoCard {
                if (!loading && error == null && outs.isEmpty()) Text("Belum ada catatan uang keluar.", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 12.sp, color = Muted, lineHeight = 17.sp)
                outs.forEach { CashMovementRow(it) }
                if (moreOut) TextButton(onClick = { loadMore("out") }, enabled = !loadingMore) { Text(if (loadingMore) "Memuat..." else "Muat lebih banyak") }
            }
        }
    }
    entryDirection?.let { direction ->
        CashEntryDialog(direction, store, onClose = { entryDirection = null }, onSaved = {
            entryDirection = null; notice = "Catatan kas tersimpan."; week = cashWeek(); revision++
        })
    }
}

@Composable
private fun CashSummaryCard(title: String, amount: String, incoming: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, color = White, shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Surface(color = if (incoming) PaleBlue else MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp)) {
                    Icon(if (incoming) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, null, tint = if (incoming) Blue else Danger, modifier = Modifier.padding(7.dp).size(17.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(amount, color = if (incoming) Blue else Danger, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CashMovementRow(movement: CashMovement) {
    val incoming = movement.direction == "in"
    Surface(color = LightSurface, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = if (incoming) PaleBlue else MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(50)) {
                Icon(if (incoming) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, null, tint = if (incoming) Blue else Danger, modifier = Modifier.padding(7.dp).size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(cashCategoryLabel(movement.category), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(movement.timestamp?.let { cashDate(it) } ?: "Tanggal tidak tersedia • data lama", fontSize = 11.sp, color = Muted)
                movement.note?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 11.sp, color = Muted) }
            }
            Spacer(Modifier.width(8.dp))
            Text((if (incoming) "+" else "−") + rupiah(movement.amount), fontSize = 13.sp, color = if (incoming) Blue else Danger, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CashEntryDialog(direction: String, store: CashierStore, onClose: () -> Unit, onSaved: () -> Unit) {
    var amountText by rememberSaveable(direction) { mutableStateOf("") }
    var note by rememberSaveable(direction) { mutableStateOf("") }
    val id = rememberSaveable(direction) { UUID.randomUUID().toString() }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val amount = amountText.toIntOrNull()
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!busy) onClose() },
        title = { Text(if (direction == "in") "Catat uang masuk" else "Catat uang keluar") },
        text = {
            Column {
                OutlinedTextField(amountText, { amountText = it.filter { c -> c in '0'..'9' }.take(9); error = null },
                    label = { Text("Nominal tunai (Rp)") }, singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(note, { note = it.take(120); error = null }, label = { Text("Keterangan") },
                    singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = { TextButton(enabled = !busy && amount != null && amount > 0 && note.isNotBlank(), onClick = {
            val value = amount ?: return@TextButton
            busy = true; error = null
            scope.launch {
                try {
                    withContext(NonCancellable) { store.addManualCash(id, direction, if (direction == "in") "deposit" else "other", value, note) }
                    onSaved()
                } catch (e: CancellationException) { throw e } catch (_: Exception) { error = "Catatan belum dapat disimpan. Periksa riwayat sebelum mencoba lagi." } finally { busy = false }
            }
        }) { Text(if (busy) "Menyimpan..." else "Simpan") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text("Batal") } },
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun CashLedgerPreview() { WarungMatriksTheme { CashLedgerScreen("preview", {}) } }
