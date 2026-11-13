package id.warungmatriks.app.ui

import android.content.ClipData
import android.content.Intent
import android.content.ActivityNotFoundException
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
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
import androidx.core.content.FileProvider
import id.warungmatriks.app.data.*
import id.warungmatriks.app.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun ReportsScreen(ownerId: String, shopName: String, openExport: Boolean = false, onExportRequestHandled: () -> Unit = {}) {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val store = remember(context, ownerId) { CashierStore(context, ownerId) }
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<ReportSnapshot?>(if (inspection) previewReport() else null) }
    var revision by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var actions by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    val download = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val path = pendingPath; pendingPath = null
        if (path != null) {
            if (uri == null) File(path).delete()
            else {
                busy = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use { out -> File(path).inputStream().use { it.copyTo(out) } } ?: throw IllegalStateException("Tidak dapat membuka lokasi penyimpanan")
                        }
                        error = "PDF berhasil disimpan di lokasi yang Anda pilih."
                    } catch (e: CancellationException) { throw e } catch (_: Exception) { error = "PDF tidak dapat disimpan. Coba lagi." }
                    finally { File(path).delete(); busy = false }
                }
            }
        }
    }
    LaunchedEffect(store, revision, inspection) {
        if (!inspection) try { report = store.report() } catch (e: CancellationException) { throw e } catch (_: Exception) { error = "Laporan belum dapat dimuat." }
    }
    LaunchedEffect(openExport) {
        if (openExport) { if (!inspection) actions = true; onExportRequestHandled() }
    }
    fun export(share: Boolean) {
        if (busy) return
        actions = false; busy = true
        scope.launch {
            var file: File? = null
            try {
                val latest = store.report()
                val credits = store.creditDetails().map { ExportCredit(it, store.saleItemSnapshots(it.saleId)) }
                file = makeReportPdf(context, latest, shopName, credits)
                report = latest
                if (share) {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    fun sendTo(packageName: String) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newRawUri("Laporan PDF", uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            setPackage(packageName)
                        }
                        context.startActivity(intent)
                    }
                    try { sendTo("com.whatsapp") } catch (_: ActivityNotFoundException) { sendTo("com.whatsapp.w4b") }
                    // The recipient reads asynchronously; keep the cache file until later cleanup.
                    file = null
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val savedName = saveReportToDownloads(context, file)
                    error = "PDF tersimpan di Download/Warung Matriks: $savedName"
                } else {
                    pendingPath = file.absolutePath
                    download.launch("Laporan-Warung-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.pdf")
                    file = null
                }
            } catch (e: CancellationException) { throw e } catch (_: ActivityNotFoundException) { error = "WhatsApp atau aplikasi penyimpan file tidak tersedia." } catch (_: Exception) { error = "Laporan PDF belum dapat dibuat atau dibagikan." } finally { file?.delete(); busy = false }
        }
    }
    DemoPage("Laporan Warung", "Ringkasan bulan ini • jam perangkat", badgeText = if (inspection) "PREVIEW" else "DATA LOKAL") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Bulan ini", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { revision++ }, enabled = !busy) { Icon(Icons.Default.Refresh, contentDescription = "Muat ulang laporan", tint = Blue) }
        }
        Spacer(Modifier.height(12.dp))
        val r = report
        if (r == null) DemoNote(if (inspection) "Preview laporan." else "Memuat data laporan...")
        else {
            val range = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
            Text("${range.format(Date(r.start))} – ${range.format(Date(r.end - 1))}", color = Muted)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReportMetric("Uang Masuk", rupiah(r.sales), "Nilai penjualan", Icons.Default.ArrowDownward, Modifier.weight(1f))
                ReportMetric("Laba Kotor", r.profit?.let { rupiah(it) } ?: "Tidak lengkap", "Perkiraan", Icons.Default.TrendingUp, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReportMetric("Modal Diputar", r.modal?.let { rupiah(it) } ?: "Tidak lengkap", "Barang terjual", Icons.Default.Sync, Modifier.weight(1f))
                ReportMetric("Potensi Stok", rupiah(r.activeStockValue), "Barang aktif", Icons.Default.Inventory2, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            DemoCard {
                InfoLine("Jumlah transaksi", r.count.toString()); Spacer(Modifier.height(8.dp))
                InfoLine("QRIS dicatat", rupiah(r.qris))
                if (r.missingCost > 0 || r.missingItems > 0) DemoNote("Modal atau rincian sebagian barang tidak tersedia; total modal/laba tidak ditebak.")
            }
            SectionGap(); GroupTitle("Uang tunai")
            DemoCard {
                InfoLine("Kas masuk", rupiah(r.cashIn)); Spacer(Modifier.height(8.dp))
                InfoLine("Kas keluar", rupiah(r.cashOut)); Spacer(Modifier.height(8.dp))
                InfoLine("Tunai dari penjualan", rupiah(r.salesCash)); Spacer(Modifier.height(8.dp))
                InfoLine("Angsuran diterima", rupiah(r.installmentCash)); Spacer(Modifier.height(8.dp))
                InfoLine("Modal awal / setoran", rupiah(r.deposits))
            }
            SectionGap(); GroupTitle("Statistik Sirkulasi Uang")
            DemoCard { ReportGraph(r.days) }
            SectionGap(); GroupTitle("Kategori Paling Cuan")
            DemoCard {
                if (r.categories.isEmpty()) DemoNote("Belum ada kategori dengan data penjualan.")
                r.categories.filter { it.profit != null }.sortedByDescending { it.profit }.forEach { ReportListRow(it.name, rupiah(it.profit!!)) }
                r.categories.filter { it.profit == null }.forEach { ReportListRow(it.name, "Modal tidak lengkap") }
                if (r.unknownCategory > 0) DemoNote("${r.unknownCategory} pcs dari transaksi lama tidak memiliki kategori historis dan tidak dimasukkan ke peringkat.")
            }
            SectionGap(); GroupTitle("Produk Paling Laris")
            DemoCard {
                if (r.bestProducts.isEmpty()) DemoNote("Belum ada barang terjual.")
                r.bestProducts.forEachIndexed { i, item -> ReportListRow("${i + 1}. ${item.name}", "${item.quantity} pcs") }
            }
            SectionGap(); GroupTitle("Pola Jam Ramai")
            DemoCard {
                ReportListRow("Pagi – Sore • 06.00–17.59", "${r.morning} transaksi")
                ReportListRow("Sore – Pagi • 18.00–05.59", "${r.evening} transaksi")
                DemoNote(if (r.morning == r.evening) "Jumlah transaksi sama." else if (r.morning > r.evening) "Lebih ramai pagi–sore." else "Lebih ramai sore–pagi.")
            }
            SectionGap(); GroupTitle("Data Stok Barang")
            DemoCard {
                if (r.inventory.isEmpty()) DemoNote("Belum ada barang.")
                r.inventory.forEach { item -> InfoLine(item.name + if (item.active) "" else " (nonaktif)", "${item.stock} pcs"); Spacer(Modifier.height(8.dp)) }
            }
            SectionGap(); Button(onClick = { actions = true }, enabled = !busy && !inspection, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Sky)) { Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (busy) "Menyiapkan PDF..." else "Bagikan / unduh laporan PDF") }
        }
    }
    if (actions) AlertDialog(onDismissRequest = { actions = false }, title = { Text("Laporan PDF") }, text = { Text("PDF berisi total dan rincian kasbon, termasuk nama dan nomor pelanggan. Pastikan penerima tepercaya.") }, confirmButton = { TextButton(onClick = { export(true) }) { Text("Bagikan WhatsApp") } }, dismissButton = { TextButton(onClick = { export(false) }) { Text("Unduh PDF") } })
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text("Informasi") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text("Tutup") } }) }
}

// Synthetic data is used only in Android Studio Preview, never in a running report.
private fun previewReport(): ReportSnapshot {
    val now = System.currentTimeMillis()
    val start = reportMonthWindow(now).first
    return ReportSnapshot(
        start = start, end = now + 1, sales = 120000, qris = 20000, count = 4,
        cashIn = 90000, cashOut = 30000, salesCash = 80000, installmentCash = 5000, deposits = 5000,
        modal = 80000, profit = 40000, missingCost = 0, missingItems = 0, unknownCategory = 0,
        categories = listOf(ReportCategory("Sembako", 40000, 0)),
        bestProducts = listOf(ReportProduct("Barang contoh", 4)), morning = 3, evening = 1,
        days = listOf(ReportDay(start, "Hari ini", 120000, 30000)),
        inventory = listOf(ReportInventory("Barang contoh", 10, 5000, true)),
    )
}

@Composable
private fun ReportMetric(title: String, value: String, detail: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = White, shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Surface(color = PaleBlue, shape = RoundedCornerShape(50)) {
                    Icon(icon, null, tint = Blue, modifier = Modifier.padding(6.dp).size(16.dp))
                }
            }
            Spacer(Modifier.height(9.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Blue, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(detail, color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ReportListRow(label: String, value: String) {
    Surface(color = LightSurface, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Spacer(Modifier.width(8.dp))
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Blue)
        }
    }
}

@Composable
private fun ReportGraph(days: List<ReportDay>) {
    if (days.isEmpty()) { DemoNote("Belum ada data."); return }
    val max = days.maxOf { maxOf(it.sales, it.cashOut) }.coerceAtLeast(1)
    Text("Biru: penjualan • biru muda: seluruh kas keluar", color = Muted, fontSize = 11.sp)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        days.forEach { day ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Row(Modifier.height(115.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                    Box(Modifier.width(10.dp).fillMaxHeight((day.sales.toFloat() / max.toFloat()).coerceIn(0f, 1f)).background(Blue))
                    Spacer(Modifier.width(3.dp))
                    Box(Modifier.width(10.dp).fillMaxHeight((day.cashOut.toFloat() / max.toFloat()).coerceIn(0f, 1f)).background(Sky))
                }
                Text(day.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    days.forEach { InfoLine(it.label, "${rupiah(it.sales)} / ${rupiah(it.cashOut)}") }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun ReportsPreview() { WarungMatriksTheme { ReportsScreen("preview", "Warung") } }
