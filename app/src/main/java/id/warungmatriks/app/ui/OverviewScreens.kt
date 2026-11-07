package id.warungmatriks.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import id.warungmatriks.app.ui.theme.*

@Composable
internal fun OrdersScreen(onCash: () -> Unit, onCredit: () -> Unit) {
    var filter by remember { mutableStateOf("Penjualan") }
    DemoPage("Pesanan", "Riwayat penjualan & kulakan • demo") {
        GroupTitle("Arus kas hari ini", "Angka ilustrasi, bukan transaksi yang tersimpan")
        SummaryCard("Modal awal", "Rp500.000", "Shift pagi • uang awal laci", Icons.Default.AccountBalanceWallet)
        Spacer(Modifier.height(9.dp))
        SummaryCard("Belanja modal", "Rp386.000", "Contoh pengeluaran tunai", Icons.Default.ShoppingBasket)
        Spacer(Modifier.height(9.dp))
        SummaryCard("Kas di laci", "Rp1.450.000", "Contoh uang fisik • lihat rincian kas", Icons.Default.PointOfSale)
        TextButton(onClick = onCash) { Text("Lihat kas masuk & keluar →") }
        SectionGap()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Penjualan", "Kulakan").forEach { item ->
                FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(item) }, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))
        if (filter == "Penjualan") {
            TransactionCard("Kasir #TRX-042", "Bu RT • Tunai", "+Rp29.500", "3× Indomie Goreng  •  1× Le Minerale\n1× Telur Ayam 1/2 Kg", "Laba kotor Rp6.000")
            Spacer(Modifier.height(10.dp))
            TransactionCard("Kasbon #TRX-040", "Mas Doni • Belum lunas", "Rp19.500", "Sisa piutang contoh", "Kasbon bukan uang di laci")
            TextButton(onClick = onCredit) { Text("Buka buku kasbon →") }
        } else {
            TransactionCard("Kulakan modal", "Agen Barokah Jaya • Tunai", "-Rp386.000", "2 dus Indomie Goreng  •  Rp216.000\n1 karton Minyak Kita  •  Rp170.000", "Restok contoh")
        }
        SectionGap()
        DemoNote()
    }
}

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
private fun TransactionCard(title: String, subtitle: String, amount: String, details: String, extra: String) {
    DemoCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold); Text(subtitle, fontSize = 12.sp, color = Muted) }
            Text(amount, fontSize = 15.sp, color = Blue, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Line)
        Text(details, fontSize = 13.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(8.dp))
        Text(extra, color = Muted, fontSize = 12.sp)
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
internal fun NotificationsScreen(onBack: () -> Unit, onCredit: () -> Unit) {
    var filter by remember { mutableStateOf("Semua") }
    DemoPage("Notifikasi", "Pengingat toko • contoh", onBack) {
        GroupTitle("Pengingat toko", "Belum ada notifikasi sistem sungguhan")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Semua", "Stok", "Kasbon").forEach { FilterChip(selected = filter == it, onClick = { filter = it }, label = { Text(it, fontSize = 12.sp) }) }
        }
        Spacer(Modifier.height(10.dp))
        if (filter != "Kasbon") {
            NotificationCard("PERHATIAN STOK", "Minyak Kita 1L menipis", "Contoh: sisa 2 pouch; batas minimum 5 pouch.", Icons.Default.Inventory2)
            Spacer(Modifier.height(10.dp))
            NotificationCard("STOK MENIPIS", "Indomie perlu diperiksa", "Contoh: sisa 6 bungkus; batas minimum 15 bungkus.", Icons.Default.Notifications)
            Spacer(Modifier.height(10.dp))
        }
        if (filter != "Stok") {
            NotificationCard("JATUH TEMPO", "Kasbon Mas Doni", "Contoh sisa tagihan Rp19.500.", Icons.Default.Event)
            TextButton(onClick = onCredit) { Text("Buka buku kasbon →") }
        }
        Spacer(Modifier.height(12.dp)); DemoNote("Peringatan dan pengingat otomatis belum diaktifkan.")
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
@Composable private fun OrdersPreview() { WarungMatriksTheme { OrdersScreen({}, {}) } }
@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun ReportsPreview() { WarungMatriksTheme { ReportsScreen() } }
@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun CashLedgerPreview() { WarungMatriksTheme { CashLedgerScreen({}) } }
@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun NotificationsPreview() { WarungMatriksTheme { NotificationsScreen({}, {}) } }
