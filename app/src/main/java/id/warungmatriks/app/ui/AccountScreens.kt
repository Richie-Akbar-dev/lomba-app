package id.warungmatriks.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.warungmatriks.app.ui.theme.*
import id.warungmatriks.app.auth.ShopProfile
import id.warungmatriks.app.auth.SignedInAccount

@Composable
internal fun AccountScreen(account: SignedInAccount, onSignOut: () -> Unit, signOutBusy: Boolean, signOutError: String?, onNotifications: () -> Unit) {
    var info by remember { mutableStateOf<String?>(null) }
    var binary by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    DemoPage("Akun & Pengaturan", "Profil tersimpan di perangkat") {
        DemoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = PaleBlue, shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Person, null, tint = Blue, modifier = Modifier.padding(14.dp).size(32.dp)) }
                Spacer(Modifier.width(14.dp))
                Column { Text(account.shop.ownerName, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(account.shop.shopName, fontSize = 13.sp, color = Muted); if (account.email.isNotBlank()) Text(account.email, fontSize = 12.sp, color = Muted); Spacer(Modifier.height(5.dp)); DemoLabel(account.shop.businessType) }
            }
        }
        SectionGap(); GroupTitle("Manajemen toko & kasir")
        DemoCard {
            MenuRow(Icons.Default.Storefront, "Profil toko", "Identitas & pengaturan warung") { info = "Pengaturan profil belum tersedia." }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Line)
            MenuRow(Icons.Default.QrCode, "Metode pembayaran", "QRIS, transfer & tunai") { binary = true }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Line)
            MenuRow(Icons.Default.NotificationsNone, "Notifikasi", "Pengingat & aktivitas toko") { onNotifications() }
        }
        SectionGap(); GroupTitle("Data & keuangan")
        DemoCard {
            MenuRow(Icons.Default.CloudOff, "Cadangkan & pulihkan", "Belum ada sinkronisasi atau backup") { info = "Backup dan sinkronisasi belum tersedia." }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Line)
            MenuRow(Icons.Default.FileDownload, "Ekspor laporan", "PDF melalui halaman Laporan") { info = "Buka halaman Laporan untuk mengunduh atau membagikan PDF." }
        }
        SectionGap(); GroupTitle("Bantuan & keamanan")
        DemoCard {
            MenuRow(Icons.Default.SupportAgent, "Pusat bantuan", "Informasi bantuan") { info = "Layanan bantuan belum tersedia." }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Line)
            MenuRow(Icons.Default.Logout, "Keluar akun", "Masuk kembali memerlukan internet") { if (!signOutBusy) confirmSignOut = true }
        }
        if (signOutBusy) Text("Sedang keluar...", color = Muted, fontSize = 12.sp)
        signOutError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        Spacer(Modifier.height(16.dp)); DemoNote("Profil, kasir, dan kas disimpan di perangkat sebagai prototipe. Beberapa fitur keuangan masih dalam tahap pengembangan.")
    }
    info?.let { text -> DemoDialog("Informasi demo", text) { info = null } }
    if (binary) BinaryDialog(onClose = { binary = false })
    if (confirmSignOut) AlertDialog(
        onDismissRequest = { confirmSignOut = false }, title = { Text("Keluar akun?") },
        text = { Text("Anda memerlukan internet dan akun Google untuk masuk kembali. Profil warung tetap tersimpan di perangkat ini; belum ada sinkronisasi atau backup.") },
        confirmButton = { TextButton(onClick = { confirmSignOut = false; onSignOut() }) { Text("Keluar") } },
        dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Batal") } },
    )
}

@Composable
private fun BinaryDialog(onClose: () -> Unit) {
    var input by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose,
        title = { Text("Masukkan kode biner") },
        text = {
            Column {
                DemoNote("Pratinjau desain dari referensi. Fungsi dan kaitannya dengan QRIS belum ditentukan; jangan masukkan data pembayaran.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = input, onValueChange = { input = it.filter { c -> c == '0' || c == '1' || c == ' ' }.take(64) }, label = { Text("Kode biner contoh") }, singleLine = true)
            }
        }, confirmButton = { TextButton(onClick = onClose) { Text("Tutup") } })
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun AccountPreview() {
    WarungMatriksTheme { AccountScreen(SignedInAccount("demo", "Pemilik Demo", "", ShopProfile("Pemilik Demo", "Warung Matriks", "Warung")), {}, false, null, {}) }
}
