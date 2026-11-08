package id.warungmatriks.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import id.warungmatriks.app.ui.theme.*
import id.warungmatriks.app.data.CashierStore
import id.warungmatriks.app.data.DemoData
import id.warungmatriks.app.data.Product
import id.warungmatriks.app.data.needsRestock
import kotlinx.coroutines.CancellationException

@Composable
private fun SummaryCard(title: String, value: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DemoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = PaleBlue, shape = RoundedCornerShape(12.dp)) { Icon(icon, null, tint = Blue, modifier = Modifier.padding(11.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(title, fontSize = 13.sp, color = Muted); Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink); Text(subtitle, fontSize = 11.sp, color = Muted) }
        }
    }
}

@Composable
internal fun ReportsScreen() {
    DemoPage("Laporan Warung", "Ringkasan usaha • data ilustrasi") {
        DemoLabel("Bulan ini • Contoh")
        Spacer(Modifier.height(16.dp))
        GroupTitle("Metrik finansial")
        SummaryCard("Total omzet penjualan", "Rp48,75 juta", "Penjualan bruto • contoh", Icons.Default.TrendingUp)
        Spacer(Modifier.height(9.dp))
        SummaryCard("Estimasi laba kotor", "Rp13,9 juta", "Sebelum biaya operasional • contoh", Icons.Default.BarChart)
        Spacer(Modifier.height(9.dp))
        SummaryCard("Belanja kulakan", "Rp34,85 juta", "Pembelian persediaan • contoh", Icons.Default.Inventory2)
        SectionGap()
        GroupTitle("Posisi uang & piutang", "Ketiga pos ini tidak boleh dijumlahkan sebagai uang fisik")
        DemoCard {
            InfoLine("Uang fisik di laci", "Rp1.450.000"); Spacer(Modifier.height(12.dp))
            InfoLine("QRIS menunggu penyelesaian", "Rp320.000"); Spacer(Modifier.height(12.dp))
            InfoLine("Kasbon belum lunas", "Rp79.500")
        }
        SectionGap()
        GroupTitle("Tren usaha", "Perbandingan visual contoh, bukan data aktual")
        DemoCard {
            Row(Modifier.fillMaxWidth().height(116.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                listOf(0.45f, 0.68f, 0.52f, 0.82f, 0.6f, 0.95f, 0.7f).forEach { h ->
                    Surface(color = Blue.copy(alpha = 0.7f), shape = RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp), modifier = Modifier.width(22.dp).fillMaxHeight(h)) {}
                }
            }
            Spacer(Modifier.height(8.dp)); Text("Sn         Sl         Rb        Km        Jm        Sb        Mg", fontSize = 11.sp, color = Muted)
        }
        SectionGap()
        GroupTitle("Produk pilihan")
        DemoCard { InfoLine("Indomie Goreng", "Rp3.500"); Spacer(Modifier.height(10.dp)); InfoLine("Minyak Kita 1L", "Rp16.000") }
        Spacer(Modifier.height(16.dp)); DemoNote("Grafik, nilai dan tren hanya rancangan. Ekspor dan laporan nyata belum tersedia.")
    }
}

@Composable
internal fun CashLedgerScreen(onBack: () -> Unit) {
    var showIn by remember { mutableStateOf(false) }
    var showOut by remember { mutableStateOf(false) }
    DemoPage("Kas masuk & keluar", "Kas laci kasir • contoh", onBack) {
        SummaryCard("Saldo kas di laci", "Rp1.450.000", "Saldo ilustrasi sejak 06:30", Icons.Default.AccountBalanceWallet)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(Modifier.weight(1f), shape = RoundedCornerShape(14.dp), color = PaleBlue) { Column(Modifier.padding(14.dp)) { Text("Kas masuk", color = Muted, fontSize = 12.sp); Text("+Rp1.836.000", color = Blue, fontSize = 14.sp, fontWeight = FontWeight.Bold) } }
            Surface(Modifier.weight(1f), shape = RoundedCornerShape(14.dp), color = Background) { Column(Modifier.padding(14.dp)) { Text("Kas keluar", color = Muted, fontSize = 12.sp); Text("-Rp386.000", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold) } }
        }
        SectionGap(); GroupTitle("Riwayat uang masuk", "+Rp1.836.000 • contoh")
        DemoCard {
            LedgerLine("Modal awal kasir", "06:30 • Laci", "+Rp500.000")
            LedgerLine("Penjualan tunai", "06:45–11:20", "+Rp986.000")
            if (showIn) { LedgerLine("Pelunasan kasbon", "09:14 • Tunai", "+Rp250.000"); LedgerLine("Penjualan tunai", "10:42 • Tunai", "+Rp100.000") }
            TextButton(onClick = { showIn = !showIn }) { Text(if (showIn) "Tampilkan lebih sedikit" else "Muat lebih banyak (+2 transaksi)") }
        }
        SectionGap(); GroupTitle("Riwayat uang keluar", "-Rp386.000 • contoh")
        DemoCard {
            LedgerLine("Kulakan telur & terigu", "07:15 • Restok", "-Rp240.000")
            LedgerLine("Beli token listrik", "08:40 • Operasional", "-Rp50.000")
            if (showOut) LedgerLine("Es & air galon", "10:05 • Operasional", "-Rp96.000")
            TextButton(onClick = { showOut = !showOut }) { Text(if (showOut) "Tampilkan lebih sedikit" else "Muat lebih banyak (+1 pengeluaran)") }
        }
        SectionGap(); DemoNote("Saldo dan riwayat hanya contoh; belum ada pencatatan atau rekonsiliasi.")
    }
}

@Composable
private fun LedgerLine(title: String, subtitle: String, amount: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); Text(subtitle, fontSize = 11.sp, color = Muted) }
        Text(amount, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun NotificationsScreen(ownerId: String, onBack: () -> Unit, onCredit: () -> Unit) {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val store = remember(context, ownerId) { CashierStore(context, ownerId) }
    var filter by remember { mutableStateOf("Semua") }
    var revision by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<Product>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(store, inspection, revision) {
        loading = true; error = null
        try { items = (if (inspection) DemoData.products else store.products()).filter { it.needsRestock() } }
        catch (e: CancellationException) { throw e } catch (_: Exception) { error = "Peringatan stok belum dapat dimuat." } finally { loading = false }
    }
    DemoPage("Notifikasi", "Pengingat stok tersimpan lokal", onBack, badgeText = "DATA LOKAL") {
        GroupTitle("Pengingat toko", "Stok barang aktif yang sama dengan atau di bawah batas minimum")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Semua", "Stok", "Kasbon").forEach { FilterChip(selected = filter == it, onClick = { filter = it }, label = { Text(it, fontSize = 12.sp) }) }
        }
        if (filter != "Kasbon") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Peringatan stok", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { revision++ }) { Text("Muat ulang") }
            }
            if (loading) DemoNote("Memuat stok...")
            else if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            else if (items.isEmpty()) DemoNote("Tidak ada barang yang mencapai batas stok minimum.")
            else items.forEach { product ->
                NotificationCard("STOK MENIPIS", product.name, "Sisa ${product.stock} pcs • batas minimum ${product.minimumStock} pcs.", Icons.Default.Inventory2)
                Spacer(Modifier.height(10.dp))
            }
        }
        if (filter != "Stok") {
            Spacer(Modifier.height(8.dp)); DemoNote("Pengingat kasbon otomatis belum tersedia; buku kasbon masih berupa contoh.")
            TextButton(onClick = onCredit) { Text("Buka buku kasbon (demo) →") }
        }
        Spacer(Modifier.height(12.dp)); DemoNote("Peringatan dihitung dari stok terbaru saat halaman dibuka atau dimuat ulang. Belum ada notifikasi sistem di luar aplikasi.")
    }
}

@Composable
private fun NotificationCard(type: String, title: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DemoCard {
        Row(verticalAlignment = Alignment.Top) {
            Surface(color = PaleBlue, shape = RoundedCornerShape(10.dp)) { Icon(icon, null, tint = Blue, modifier = Modifier.padding(10.dp)) }
            Spacer(Modifier.width(12.dp))
            Column { Text(type, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Blue); Spacer(Modifier.height(5.dp)); Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(detail, color = Muted, fontSize = 12.sp, lineHeight = 18.sp) }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun ReportsPreview() { WarungMatriksTheme { ReportsScreen() } }
@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun CashLedgerPreview() { WarungMatriksTheme { CashLedgerScreen({}) } }
@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun NotificationsPreview() { WarungMatriksTheme { NotificationsScreen("preview", {}, {}) } }
