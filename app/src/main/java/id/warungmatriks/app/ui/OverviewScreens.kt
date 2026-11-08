package id.warungmatriks.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import id.warungmatriks.app.data.CreditDetail
import id.warungmatriks.app.data.rupiah
import id.warungmatriks.app.data.needsRestock
import id.warungmatriks.app.data.stockNotificationsEnabled
import id.warungmatriks.app.data.setStockNotificationsEnabled
import id.warungmatriks.app.data.scheduleStockReminders
import kotlinx.coroutines.CancellationException

@Composable
internal fun NotificationsScreen(ownerId: String, onBack: () -> Unit, onCredit: () -> Unit) {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val store = remember(context, ownerId) { CashierStore(context, ownerId) }
    var filter by remember { mutableStateOf("Semua") }
    var stockAlerts by remember(context, ownerId) { mutableStateOf(if (inspection) false else stockNotificationsEnabled(context, ownerId)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var revision by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<Product>>(emptyList()) }
    var dueCredits by remember { mutableStateOf<List<CreditDetail>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(store, inspection, revision) {
        loading = true; error = null
        try { items = (if (inspection) DemoData.products else store.products()).filter { it.needsRestock() }; dueCredits = if (inspection) emptyList() else store.creditDetails().filter { it.remind && it.dueAt != null && it.dueAt <= System.currentTimeMillis() } }
        catch (e: CancellationException) { throw e } catch (_: Exception) { error = "Peringatan stok belum dapat dimuat." } finally { loading = false }
    }
    DemoPage("Notifikasi", "Pengingat stok dan kasbon lokal", onBack, badgeText = "DATA LOKAL") {
        GroupTitle("Pengingat toko", "Stok barang aktif yang sama dengan atau di bawah batas minimum")
        DemoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Notifikasi stok perangkat", fontWeight = FontWeight.Bold); Text("Pengingat lokal untuk akun ini", fontSize = 12.sp, color = Muted) }
                Switch(checked = stockAlerts, onCheckedChange = { enabled ->
                    stockAlerts = enabled
                    if (!inspection) {
                        setStockNotificationsEnabled(context, ownerId, enabled)
                        if (enabled) {
                            scheduleStockReminders(context)
                            if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                })
            }
            DemoNote("Notifikasi sistem memerlukan izin dan dapat terlambat. Peringatan di halaman ini tetap tersedia.")
            TextButton(onClick = {
                try {
                    val intent = if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    context.startActivity(intent)
                } catch (_: Exception) { error = "Pengaturan notifikasi perangkat tidak tersedia." }
            }) { Text("Pengaturan notifikasi perangkat") }
        }
        Spacer(Modifier.height(12.dp))
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
            Spacer(Modifier.height(8.dp)); Text("Kasbon jatuh tempo", fontWeight = FontWeight.Bold)
            if (loading) DemoNote("Memuat pengingat...")
            else if (error != null) Text("Pengingat belum dapat dimuat.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            else if (dueCredits.isEmpty()) DemoNote("Tidak ada pengingat kasbon yang jatuh tempo.")
            dueCredits.forEach { credit ->
                NotificationCard("KASBON JATUH TEMPO", credit.customer, "Sisa ${rupiah(credit.remaining)}", Icons.Default.NotificationsNone)
                Spacer(Modifier.height(10.dp))
            }
            TextButton(onClick = onCredit) { Text("Buka buku kasbon →") }
        }
        Spacer(Modifier.height(12.dp)); DemoNote("Pengingat kasbon muncul saat atau setelah jatuh tempo jika diaktifkan pada kasbon. Android dapat menunda pekerjaan latar belakang; izin notifikasi diperlukan.")
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
@Composable private fun NotificationsPreview() { WarungMatriksTheme { NotificationsScreen("preview", {}, {}) } }
