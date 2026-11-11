package id.warungmatriks.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.warungmatriks.app.data.*
import id.warungmatriks.app.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// DateRangePicker days are UTC midnights; turn their calendar dates into local day boundaries.
private val pickerTimeZone: TimeZone = TimeZone.getTimeZone("UTC")
private fun todayPickerDay(): Long {
    val local = Calendar.getInstance()
    return Calendar.getInstance(pickerTimeZone).apply {
        clear(); set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}
private fun localDayFromPicker(utcDay: Long, nextDay: Boolean = false): Long {
    val utc = Calendar.getInstance(pickerTimeZone).apply { timeInMillis = utcDay }
    return Calendar.getInstance().apply {
        clear(); set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
        if (nextDay) add(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis
}
private fun dateText(time: Long, pattern: String) = SimpleDateFormat(pattern, Locale("id", "ID")).format(Date(time))

@Composable
internal fun OrdersScreen(ownerId: String, shopName: String = "Warung Matriks", onNotifications: () -> Unit = {}, onAccount: () -> Unit = {}) {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val store = remember(context, ownerId) { CashierStore(context, ownerId) }
    val scope = rememberCoroutineScope()
    var section by rememberSaveable { mutableStateOf("Penjualan") }
    var startDay by rememberSaveable { mutableLongStateOf(todayPickerDay()) }
    var endDay by rememberSaveable { mutableLongStateOf(todayPickerDay()) }
    var showCalendar by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    val start = localDayFromPicker(startDay)
    val end = localDayFromPicker(endDay, nextDay = true)
    var creditTotal by remember { mutableStateOf<Long?>(null) }
    var summary by remember { mutableStateOf<SaleHistorySummary?>(null) }
    var entries by remember { mutableStateOf<List<SaleRecord>>(emptyList()) }
    var next by remember { mutableStateOf<SaleCursor?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<SaleRecord?>(null) }
    var itemSnapshots by remember { mutableStateOf<List<SaleItemSnapshot>?>(null) }
    var detailError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(store, start, end, revision, inspection) {
        loading = true; loadingMore = false; error = null; pageError = null; entries = emptyList(); next = null; summary = null; creditTotal = null
        try {
            if (inspection) {
                entries = listOf(SaleRecord("contoh-preview", System.currentTimeMillis(), 10500, 4000, 0, 6500, "Bu RT", displayNumber = 1))
                summary = SaleHistorySummary(1, 10500, 4000, 6500); creditTotal = 6500
            } else {
                val now = System.currentTimeMillis()
                val rangeSummary = store.visibleSaleHistorySummary(start, end, now)
                val page = store.saleHistory(start, end, SaleHistoryFilter.ALL, now = now)
                val outstanding = store.outstandingCreditTotal()
                summary = rangeSummary; creditTotal = outstanding; entries = page.sales; next = page.next
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { error = "Riwayat belum dapat dimuat. Coba lagi." } finally { loading = false }
    }
    LaunchedEffect(store, selected?.id, inspection) {
        itemSnapshots = null; detailError = null
        val id = selected?.id
        if (id != null) {
            try {
                itemSnapshots = if (inspection) listOf(SaleItemSnapshot("Indomie Goreng", 3, 3500, 2700)) else store.saleItemSnapshots(id)
            } catch (e: CancellationException) { throw e } catch (_: Exception) { detailError = "Rincian belum dapat dimuat." }
        }
    }
    fun loadMore() {
        val cursor = next ?: return
        if (loadingMore) return
        val requestedStart = start; val requestedEnd = end; val requestedStartDay = startDay; val requestedEndDay = endDay; val requestedRevision = revision
        loadingMore = true; pageError = null
        scope.launch {
            try {
                val page = store.saleHistory(requestedStart, requestedEnd, SaleHistoryFilter.ALL, cursor)
                if (startDay == requestedStartDay && endDay == requestedEndDay && revision == requestedRevision) {
                    entries = entries + page.sales; next = page.next
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
                if (startDay == requestedStartDay && endDay == requestedEndDay && revision == requestedRevision) pageError = "Riwayat berikutnya belum dapat dimuat."
            } finally {
                if (startDay == requestedStartDay && endDay == requestedEndDay && revision == requestedRevision) loadingMore = false
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize().background(Background), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Blue, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.ReceiptLong, null, tint = White, modifier = Modifier.padding(10.dp).size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Warung Matriks • $shopName", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1)
                }
                IconButton(onClick = onNotifications) { Icon(Icons.Default.NotificationsNone, "Notifikasi", tint = Ink) }
                IconButton(onClick = onAccount, modifier = Modifier.size(38.dp).background(PaleBlue, CircleShape)) { Icon(Icons.Default.PersonOutline, "Buka akun", tint = Blue, modifier = Modifier.size(22.dp)) }
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ringkasan Penjualan", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
                    Text(if (startDay == endDay) dateText(start, "dd MMM yyyy") else "${dateText(start, "dd MMM yyyy")} – ${dateText(localDayFromPicker(endDay), "dd MMM yyyy")}", fontSize = 11.sp, color = Muted)
                }
                IconButton(onClick = { showCalendar = true }) { Icon(Icons.Default.CalendarToday, "Pilih rentang tanggal", tint = Blue) }
            }
            Spacer(Modifier.height(10.dp))
            val current = summary
            if (current == null) DemoCard { DemoNote(if (error != null) "Ringkasan belum tersedia." else "Memuat ringkasan...") }
            else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OrdersMetric("Penjualan", rupiah(current.total), Icons.Default.ReceiptLong, Modifier.weight(1f))
                    OrdersMetric("Sisa kasbon saat ini", creditTotal?.let { rupiah(it) } ?: "—", Icons.Default.AccountBalanceWallet, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OrdersMetric("Tunai masuk", rupiah(current.cashApplied), Icons.Default.AccountBalanceWallet, Modifier.weight(1f))
                    OrdersMetric("QRIS dicatat", rupiah(current.qrisTotal), Icons.Default.QrCode, Modifier.weight(1f))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Penjualan", "Kulakan").forEach { label -> FilterChip(selected = section == label, onClick = { section = label }, label = { Text(label) }, modifier = Modifier.weight(1f), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Blue, selectedLabelColor = White)) }
            }
        }
        if (section == "Kulakan") {
            item { DemoCard { Text("Kulakan belum tersedia", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); DemoNote("Pencatatan kulakan dan stok masuk belum dibuat. Tidak ada transaksi kulakan yang ditampilkan sebagai data nyata.") } }
        } else {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Riwayat penjualan", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    val rotation = rememberInfiniteTransition(label = "Muat ulang").animateFloat(
                        initialValue = 0f, targetValue = 360f,
                        animationSpec = infiniteRepeatable(animation = tween(900, easing = LinearEasing), repeatMode = RepeatMode.Restart), label = "Putar"
                    )
                    IconButton(onClick = { revision++ }, enabled = !loading) {
                        Icon(Icons.Default.Refresh, "Muat ulang", tint = Blue, modifier = Modifier.rotate(if (loading) rotation.value else 0f))
                    }
                }
                if (inspection) DemoNote("Data berikut adalah contoh Preview.")
                if (loading) DemoNote("Memuat riwayat...")
                else if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                else if (entries.isEmpty()) DemoNote("Tidak ada penjualan yang tersedia pada tanggal ini.")
            }
            items(entries, key = { it.id }) { sale -> SaleCard(sale) { selected = sale } }
            if (next != null || pageError != null) item {
                pageError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                OutlinedButton(onClick = { loadMore() }, enabled = !loadingMore, modifier = Modifier.fillMaxWidth()) { Text(if (loadingMore) "Memuat..." else "Muat lebih banyak") }
            }
        }
    }
    if (showCalendar) OrdersDateRangeDialog(startDay, endDay, onDismiss = { showCalendar = false }, onApply = { from, to ->
        startDay = from; endDay = to; showCalendar = false
    })
    selected?.let { sale -> SaleDetails(sale, itemSnapshots, detailError) { selected = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrdersDateRangeDialog(startDay: Long, endDay: Long, onDismiss: () -> Unit, onApply: (Long, Long) -> Unit) {
    val state = rememberDateRangePickerState(initialSelectedStartDateMillis = startDay, initialSelectedEndDateMillis = endDay)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(18.dp), color = White, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Column {
                DateRangePicker(state = state, modifier = Modifier.weight(1f), title = { Text("Pilih rentang tanggal", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
                Text("Hanya data yang masih tersedia yang ditampilkan.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 20.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Batal") }
                    TextButton(enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null, onClick = {
                        val from = state.selectedStartDateMillis
                        val to = state.selectedEndDateMillis
                        if (from != null && to != null) onApply(from, to)
                    }) { Text("Terapkan") }
                }
            }
        }
    }
}

@Composable
private fun OrdersMetric(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = White, shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = PaleBlue, shape = RoundedCornerShape(8.dp)) { Icon(icon, null, tint = Blue, modifier = Modifier.padding(6.dp).size(16.dp)) }
                Spacer(Modifier.width(7.dp))
                Text(title, color = Muted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(value, color = Blue, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2)
        }
    }
}

@Composable
private fun SaleCard(sale: SaleRecord, onDetails: () -> Unit) {
    DemoCard {
        Row(verticalAlignment = Alignment.Top) {
            Surface(color = LightSurface, shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Default.ReceiptLong, null, tint = Blue, modifier = Modifier.padding(9.dp).size(19.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Penjualan ${saleDisplayNumber(sale.displayNumber)}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(dateText(sale.createdAt, "dd MMM yyyy • HH:mm"), color = Muted, fontSize = 11.sp)
            }
            Text(rupiah(sale.total), color = Blue, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(10.dp))
        DemoLabel(if (sale.paymentMethod == "qris") "QRIS dicatat • belum diverifikasi" else if (sale.initialDebt > 0) "Ada kasbon saat transaksi" else "Tunai lunas saat transaksi")
        sale.customer?.let { Spacer(Modifier.height(8.dp)); Text("Pelanggan: $it", fontSize = 12.sp, color = Muted) }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onDetails, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { Text("Lihat rincian", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun SaleDetails(sale: SaleRecord, snapshots: List<SaleItemSnapshot>?, error: String?, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text("Rincian penjualan") }, text = {
        Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())) {
            Text("ID: ${saleDisplayNumber(sale.displayNumber)}", fontSize = 11.sp, color = Muted)
            Text(dateText(sale.createdAt, "dd MMMM yyyy • HH:mm"), fontSize = 12.sp, color = Muted)
            sale.customer?.let { Text("Pelanggan: $it", fontSize = 12.sp, color = Muted) }
            Spacer(Modifier.height(10.dp))
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            else if (snapshots == null) DemoNote("Memuat rincian...")
            else if (snapshots.isEmpty()) DemoNote("Rincian barang tidak tersedia untuk transaksi ini.")
            else snapshots.forEach { item ->
                Text(item.productName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                InfoLine("${item.quantity} pcs × ${rupiah(item.price)}", rupiah(item.quantity.toLong() * item.price))
                Spacer(Modifier.height(8.dp))
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            val method = if (sale.paymentMethod == "qris") "QRIS • belum diverifikasi" else if (sale.initialDebt == 0) "Tunai" else if (sale.cashApplied > 0) "Tunai + kasbon" else "Kasbon"
            InfoLine("Metode saat transaksi", method); Spacer(Modifier.height(6.dp))
            InfoLine("Total penjualan", rupiah(sale.total)); Spacer(Modifier.height(6.dp))
            if (sale.paymentMethod == "qris") {
                InfoLine("Nominal QRIS dicatat", rupiah(sale.total)); Spacer(Modifier.height(6.dp))
                DemoNote("Pencatatan QRIS bukan bukti bahwa pembayaran berhasil.")
            } else {
                InfoLine("Tunai diserahkan", rupiah(sale.received)); Spacer(Modifier.height(6.dp))
                val changeOrShortfall = if (sale.initialDebt > 0) -sale.initialDebt else sale.change
                InfoLine("Kembalian / kekurangan saat transaksi", rupiah(changeOrShortfall)); Spacer(Modifier.height(6.dp))
            }
            val gross = snapshots?.let { grossProfitForSale(it) }
            InfoLine("Laba kotor estimasi", gross?.let { rupiah(it) } ?: "Tidak diketahui")
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Tutup") } })
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun OrdersPreview() { WarungMatriksTheme { OrdersScreen("preview") } }
