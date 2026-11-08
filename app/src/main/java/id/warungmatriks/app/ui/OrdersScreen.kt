package id.warungmatriks.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

private fun dayWindow(now: Long = System.currentTimeMillis()): Pair<Long, Long> {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val start = calendar.timeInMillis
    calendar.add(Calendar.DAY_OF_YEAR, 1)
    return start to calendar.timeInMillis
}
private fun dateText(time: Long, pattern: String) = SimpleDateFormat(pattern, Locale("id", "ID")).format(Date(time))

@Composable
internal fun OrdersScreen(ownerId: String) {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val store = remember(context, ownerId) { CashierStore(context, ownerId) }
    val scope = rememberCoroutineScope()
    var section by rememberSaveable { mutableStateOf("Penjualan") }
    var period by rememberSaveable { mutableStateOf("Hari ini") }
    var filter by rememberSaveable { mutableStateOf(SaleHistoryFilter.ALL.name) }
    var revision by remember { mutableIntStateOf(0) }
    val window = remember(revision) { dayWindow() }
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

    LaunchedEffect(store, period, filter, revision, inspection) {
        loading = true; loadingMore = false; error = null; pageError = null; entries = emptyList(); next = null; summary = null
        try {
            if (inspection) {
                entries = listOf(SaleRecord("contoh-preview", System.currentTimeMillis(), 10500, 4000, 0, 6500, "Bu RT"))
                summary = SaleHistorySummary(1, 10500, 4000, 6500)
            } else {
                val daySummary = store.saleHistorySummary(window.first, window.second)
                val page = store.saleHistory(if (period == "Hari ini") window.first else null, if (period == "Hari ini") window.second else null, SaleHistoryFilter.valueOf(filter))
                summary = daySummary; entries = page.sales; next = page.next
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
        val requestedPeriod = period; val requestedFilter = filter; val requestedRevision = revision
        loadingMore = true; pageError = null
        scope.launch {
            try {
                val page = store.saleHistory(if (requestedPeriod == "Hari ini") window.first else null, if (requestedPeriod == "Hari ini") window.second else null, SaleHistoryFilter.valueOf(requestedFilter), cursor)
                if (period == requestedPeriod && filter == requestedFilter && revision == requestedRevision) {
                    entries = entries + page.sales; next = page.next
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
                if (period == requestedPeriod && filter == requestedFilter && revision == requestedRevision) pageError = "Riwayat berikutnya belum dapat dimuat."
            } finally {
                if (period == requestedPeriod && filter == requestedFilter && revision == requestedRevision) loadingMore = false
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize().background(Background), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Pesanan", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("Riwayat tersimpan di perangkat", fontSize = 12.sp, color = Muted)
                }
                DemoLabel(if (inspection) "PREVIEW" else "DATA LOKAL")
            }
        }
        item {
            GroupTitle("Ringkasan penjualan hari ini", dateText(window.first, "dd MMMM yyyy") + " • sesuai jam perangkat")
            DemoCard {
                if (summary == null) DemoNote(if (error != null) "Ringkasan belum tersedia." else "Memuat ringkasan...")
                else {
                    val current = summary!!
                    InfoLine("Jumlah transaksi", current.count.toString()); Spacer(Modifier.height(8.dp))
                    InfoLine("Nilai penjualan tercatat", rupiah(current.total)); Spacer(Modifier.height(8.dp))
                    InfoLine("QRIS dicatat, belum terverifikasi", rupiah(current.qrisTotal)); Spacer(Modifier.height(8.dp))
                    InfoLine("Tunai masuk dari penjualan", rupiah(current.cashApplied)); Spacer(Modifier.height(8.dp))
                    InfoLine("Kasbon saat transaksi", rupiah(current.initialDebt))
                    Spacer(Modifier.height(8.dp))
                    DemoNote("Bukan saldo laci atau sisa kasbon saat ini. Nilai QRIS dicatat tanpa bukti pembayaran dan bukan saldo bank.")
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Penjualan", "Kulakan").forEach { label -> FilterChip(selected = section == label, onClick = { section = label }, label = { Text(label) }, modifier = Modifier.weight(1f)) }
            }
        }
        if (section == "Kulakan") {
            item { DemoCard { Text("Kulakan belum tersedia", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); DemoNote("Pencatatan kulakan dan stok masuk belum dibuat. Tidak ada transaksi kulakan yang ditampilkan sebagai data nyata.") } }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Hari ini", "Semua tanggal").forEach { label -> FilterChip(selected = period == label, onClick = { period = label }, label = { Text(label, fontSize = 12.sp) }) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(SaleHistoryFilter.ALL to "Semua", SaleHistoryFilter.PAID_AT_SALE to "Tunai lunas", SaleHistoryFilter.CREDIT_AT_SALE to "Kasbon", SaleHistoryFilter.QRIS to "QRIS").forEach { (value, label) ->
                        FilterChip(selected = filter == value.name, onClick = { filter = value.name }, label = { Text(label, fontSize = 11.sp) })
                    }
                }
                DemoNote("Filter tunai lunas/kasbon berdasarkan kondisi saat transaksi, bukan status pelunasan terbaru. QRIS terpisah karena pembayaran tidak diverifikasi.")
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Riwayat penjualan", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { revision++ }) { Text("Muat ulang") }
                }
                DemoNote("Penjualan disembunyikan setelah 7 × 24 jam, lalu dibersihkan pada jadwal tanggal 1. Kasbon belum lunas tetap tersedia.")
                if (inspection) DemoNote("Data berikut adalah contoh Preview.")
                if (loading) DemoNote("Memuat riwayat...")
                else if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                else if (entries.isEmpty()) DemoNote("Belum ada penjualan pada filter ini.")
            }
            items(entries, key = { it.id }) { sale -> SaleCard(sale) { selected = sale } }
            if (next != null || pageError != null) item {
                pageError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                OutlinedButton(onClick = { loadMore() }, enabled = !loadingMore, modifier = Modifier.fillMaxWidth()) { Text(if (loadingMore) "Memuat..." else "Muat lebih banyak") }
            }
        }
    }
    selected?.let { sale -> SaleDetails(sale, itemSnapshots, detailError) { selected = null } }
}

@Composable
private fun SaleCard(sale: SaleRecord, onDetails: () -> Unit) {
    DemoCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Penjualan #${sale.id.take(8)}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(dateText(sale.createdAt, "dd MMM yyyy • HH:mm"), color = Muted, fontSize = 12.sp)
            }
            Text(rupiah(sale.total), color = Blue, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(8.dp))
        DemoLabel(if (sale.paymentMethod == "qris") "QRIS dicatat • belum diverifikasi" else if (sale.initialDebt > 0) "Ada kasbon saat transaksi" else "Tunai lunas saat transaksi")
        sale.customer?.let { Spacer(Modifier.height(6.dp)); Text("Pelanggan: $it", fontSize = 12.sp, color = Muted) }
        TextButton(onClick = onDetails) { Text("Lihat rincian") }
    }
}

@Composable
private fun SaleDetails(sale: SaleRecord, snapshots: List<SaleItemSnapshot>?, error: String?, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text("Rincian penjualan") }, text = {
        Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())) {
            Text("ID: ${sale.id}", fontSize = 11.sp, color = Muted)
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
                InfoLine("Kembalian", rupiah(sale.change)); Spacer(Modifier.height(6.dp))
                InfoLine("Tunai untuk penjualan", rupiah(sale.cashApplied)); Spacer(Modifier.height(6.dp))
            }
            InfoLine("Kasbon saat transaksi", rupiah(sale.initialDebt)); Spacer(Modifier.height(6.dp))
            val gross = snapshots?.let { grossProfitForSale(it) }
            InfoLine("Laba kotor estimasi", gross?.let { rupiah(it) } ?: "Tidak diketahui")
            Spacer(Modifier.height(8.dp))
            DemoNote("Nama, harga dan modal berdasarkan saat transaksi. Laba kotor belum dikurangi biaya operasional; nilai kasbon adalah saat transaksi, bukan saldo terbaru.")
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Tutup") } })
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun OrdersPreview() { WarungMatriksTheme { OrdersScreen("preview") } }
