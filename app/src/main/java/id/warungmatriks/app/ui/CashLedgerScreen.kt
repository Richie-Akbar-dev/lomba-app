package id.warungmatriks.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
    var pendingDirection by rememberSaveable(ownerId) { mutableStateOf<String?>(null) }
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
            if (inspection) summary = CashSummary(0, 0)
            else {
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

    DemoPage("Kas masuk & keluar", "Sirkulasi tunai mingguan", onBack, badgeText = "DATA LOKAL") {
        DemoCard {
            Row {
                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Blue)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Saldo arus kas minggu ini", color = Muted, fontSize = 12.sp)
                    Text(summary?.let { cashNetText(it.net) } ?: "—", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Blue)
                    Text("${cashDate(week.start)} hingga sebelum ${cashDate(week.end)}", color = Muted, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            DemoNote("Dimulai dari Rp0 setiap Senin. Ini selisih arus kas minggu ini, bukan saldo uang fisik di laci; dapat bernilai negatif.")
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(Modifier.weight(1f), shape = RoundedCornerShape(14.dp), color = PaleBlue) { Column(Modifier.padding(14.dp)) { Text("Kas masuk", color = Muted, fontSize = 12.sp); Text(summary?.let { rupiah(it.cashIn) } ?: "—", color = Blue, fontWeight = FontWeight.Bold) } }
            Surface(Modifier.weight(1f), shape = RoundedCornerShape(14.dp), color = Background) { Column(Modifier.padding(14.dp)) { Text("Kas keluar", color = Muted, fontSize = 12.sp); Text(summary?.let { rupiah(it.cashOut) } ?: "—", color = Ink, fontWeight = FontWeight.Bold) } }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { pendingDirection = "in" }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Catat masuk", fontSize = 12.sp) }
            OutlinedButton(onClick = { pendingDirection = "out" }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Catat keluar", fontSize = 12.sp) }
        }
        Spacer(Modifier.height(8.dp)); DemoNote("Penukaran uang besar dan kecil tidak mengubah jumlah kas, jadi tidak perlu dicatat sebagai masuk atau keluar.")
        SectionGap(); GroupTitle("Riwayat kas", "Ringkasan di atas selalu untuk minggu berjalan.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Minggu ini", "Semua riwayat").forEach { label -> FilterChip(selected = period == label, onClick = { period = label }, label = { Text(label, fontSize = 12.sp) }) }
            TextButton(onClick = { week = cashWeek(); revision++ }) { Text("Muat ulang", fontSize = 12.sp) }
        }
        if (loading) DemoNote("Memuat kas...")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        notice?.let { Text(it, color = Blue, fontSize = 12.sp) }
        Spacer(Modifier.height(12.dp)); GroupTitle("Uang masuk")
        DemoCard {
            if (!loading && error == null && ins.isEmpty()) DemoNote("Belum ada catatan uang masuk.")
            ins.forEach { CashMovementRow(it) }
            if (moreIn) TextButton(onClick = { loadMore("in") }, enabled = !loadingMore) { Text(if (loadingMore) "Memuat..." else "Muat lebih banyak") }
        }
        SectionGap(); GroupTitle("Uang keluar")
        DemoCard {
            if (!loading && error == null && outs.isEmpty()) DemoNote("Belum ada catatan uang keluar.")
            outs.forEach { CashMovementRow(it) }
            if (moreOut) TextButton(onClick = { loadMore("out") }, enabled = !loadingMore) { Text(if (loadingMore) "Memuat..." else "Muat lebih banyak") }
        }
        SectionGap(); DemoNote("QRIS disimpan terpisah dan tidak masuk kas tunai. Catatan lama tanpa tanggal tidak dihitung dalam minggu tertentu. Kas tersimpan lokal; backup belum tersedia.")
    }
    pendingDirection?.let { direction ->
        CashEntryDialog(direction, store, onClose = { pendingDirection = null }, onSaved = {
            pendingDirection = null; notice = "Catatan kas tersimpan di perangkat."; week = cashWeek(); revision++
        })
    }
}

@Composable
private fun CashMovementRow(movement: CashMovement) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(cashCategoryLabel(movement.category), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(movement.timestamp?.let { cashDate(it) } ?: "Tanggal tidak tersedia • data lama", fontSize = 11.sp, color = Muted)
            movement.note?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 11.sp, color = Muted) }
        }
        Spacer(Modifier.width(8.dp))
        Text((if (movement.direction == "in") "+" else "−") + rupiah(movement.amount), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CashEntryDialog(direction: String, store: CashierStore, onClose: () -> Unit, onSaved: () -> Unit) {
    val categories = if (direction == "in") listOf("initial", "deposit") else listOf("purchase", "withdrawal", "other")
    var category by rememberSaveable(direction) { mutableStateOf(categories.first()) }
    var amountText by rememberSaveable(direction) { mutableStateOf("") }
    var note by rememberSaveable(direction) { mutableStateOf("") }
    val id = rememberSaveable(direction) { UUID.randomUUID().toString() }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val amount = amountText.toIntOrNull()
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, title = { Text(if (direction == "in") "Catat uang masuk" else "Catat uang keluar") }, text = {
        Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(value = amountText, onValueChange = { amountText = it.filter { c -> c in '0'..'9' }.take(9); error = null },
                label = { Text("Nominal tunai (Rp)") }, singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp)); Text("Kategori", fontSize = 12.sp, color = Muted)
            categories.forEach { value ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = category == value, onClick = { category = value }, enabled = !busy)
                    Text(cashCategoryLabel(value), fontSize = 13.sp)
                }
            }
            OutlinedTextField(value = note, onValueChange = { note = it.take(120) }, label = { Text("Keterangan (opsional)") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            DemoNote(if (direction == "in") "Catat hanya uang yang benar-benar ditambahkan. Tukar pecahan dengan nominal sama tidak perlu dicatat." else "Kas keluar boleh melebihi arus kas minggu ini; angka mingguan bisa negatif. Ini tidak menambah stok atau mencatat kulakan barang.")
            error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        }
    }, confirmButton = { TextButton(enabled = !busy && amount != null && amount > 0, onClick = {
        val value = amount ?: return@TextButton
        busy = true; error = null
        scope.launch {
            try {
                withContext(NonCancellable) { store.addManualCash(id, direction, category, value, note) }
                onSaved()
            } catch (e: CancellationException) { throw e } catch (_: Exception) { error = "Catatan belum dapat disimpan. Coba lagi." } finally { busy = false }
        }
    }) { Text(if (busy) "Menyimpan..." else "Simpan") } }, dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text("Batal") } })
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun CashLedgerPreview() { WarungMatriksTheme { CashLedgerScreen("preview", {}) } }
