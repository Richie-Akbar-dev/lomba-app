package id.warungmatriks.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.warungmatriks.app.ui.theme.*
import id.warungmatriks.app.auth.ShopProfile
import id.warungmatriks.app.auth.SignedInAccount
import id.warungmatriks.app.data.QrisSettings

@Composable
internal fun AccountScreen(account: SignedInAccount, onSignOut: () -> Unit, signOutBusy: Boolean, signOutError: String?, onNotifications: () -> Unit, onSaveProfile: (ShopProfile) -> Boolean = { false }, onExportReport: () -> Unit = {}) {
    val context = LocalContext.current
    var info by remember { mutableStateOf<String?>(null) }
    var binary by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    DemoPage("Akun", subtitle = "", badgeText = "") {
        DemoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = PaleBlue, shape = RoundedCornerShape(50)) { Icon(Icons.Default.Person, null, tint = Blue, modifier = Modifier.padding(14.dp).size(32.dp)) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(account.shop.ownerName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storefront, contentDescription = null, tint = Blue, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(account.shop.shopName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Blue)
                    }
                    if (account.email.isNotBlank()) Text(account.email, fontSize = 12.sp, color = Muted, maxLines = 1)
                }
            }
        }
        SectionGap(); GroupTitle("Manajemen toko & kasir")
        AccountMenuTile(Icons.Default.Storefront, "Profil toko", "Ubah pemilik, nama warung & jenis usaha") { editingProfile = true }
        Spacer(Modifier.height(8.dp))
        AccountMenuTile(Icons.Default.QrCode, "QRIS toko", "Atur kode QRIS statis") { binary = true }
        Spacer(Modifier.height(8.dp))
        AccountMenuTile(Icons.Default.NotificationsNone, "Notifikasi", "Pengingat & aktivitas toko") { onNotifications() }
        SectionGap(); GroupTitle("Data & keuangan")
        AccountMenuTile(Icons.Default.FileDownload, "Ekspor laporan", "Unduh atau bagikan PDF") { onExportReport() }
        SectionGap(); GroupTitle("Bantuan & keamanan")
        AccountMenuTile(Icons.Default.SupportAgent, "Pusat bantuan", "Hubungi 0812 3580 6240") {
            val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", "081235806240", null))
            try { context.startActivity(intent) } catch (_: Exception) {
                info = "Aplikasi telepon tidak dapat dibuka. Hubungi 0812 3580 6240."
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { if (!signOutBusy) confirmSignOut = true }, enabled = !signOutBusy,
            modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
            Icon(Icons.Default.Logout, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Keluar akun", fontWeight = FontWeight.Bold)
        }
        if (signOutBusy) Text("Sedang keluar...", color = Muted, fontSize = 12.sp)
        signOutError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        Spacer(Modifier.height(16.dp)); DemoNote("Profil, kasir, dan kas disimpan di perangkat sebagai prototipe. Beberapa fitur keuangan masih dalam tahap pengembangan.")
    }
    info?.let { text -> DemoDialog("Informasi", text) { info = null } }
    if (editingProfile) EditShopProfileDialog(profile = account.shop, onClose = { editingProfile = false }, onSave = { profile ->
        val saved = onSaveProfile(profile)
        if (saved) { editingProfile = false; info = "Profil toko berhasil disimpan di perangkat." }
        saved
    })
    if (binary) BinaryDialog(ownerId = account.uid, onClose = { binary = false }, onSaved = {
        binary = false; info = "Kode QRIS tersimpan di perangkat untuk akun ini. Periksa identitas penerima dan uji dengan penyedia sebelum digunakan."
    })
    if (confirmSignOut) AlertDialog(
        onDismissRequest = { confirmSignOut = false }, title = { Text("Keluar akun?") },
        text = { Text("Anda memerlukan internet dan akun Google untuk masuk kembali. Profil warung tetap tersimpan di perangkat ini; belum ada sinkronisasi atau backup.") },
        confirmButton = { TextButton(onClick = { confirmSignOut = false; onSignOut() }) { Text("Keluar") } },
        dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Batal") } },
    )
}

@Composable
private fun AccountMenuTile(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(color = White, shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = LightSurface, shape = RoundedCornerShape(10.dp)) {
                Icon(icon, null, tint = Blue, modifier = Modifier.padding(10.dp).size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink)
                Text(subtitle, fontSize = 11.sp, color = Muted, lineHeight = 16.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Muted)
        }
    }
}

@Composable
private fun EditShopProfileDialog(profile: ShopProfile, onClose: () -> Unit, onSave: (ShopProfile) -> Boolean) {
    var owner by remember(profile) { mutableStateOf(profile.ownerName) }
    var shop by remember(profile) { mutableStateOf(profile.shopName) }
    var kind by remember(profile) { mutableStateOf(profile.businessType) }
    var saveError by remember(profile) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onClose, title = { Text("Edit Profil") }, text = {
        Column {
            OutlinedTextField(owner, { owner = it.take(80) }, label = { Text("Nama pemilik") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(shop, { shop = it.take(80) }, label = { Text("Nama warung") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text("Jenis usaha", fontSize = 12.sp, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("Warung", "Minuman", "Lainnya").forEach { value ->
                    FilterChip(selected = kind == value, onClick = { kind = value }, label = { Text(value, fontSize = 11.sp) })
                }
            }
            if (saveError) Text("Profil belum dapat disimpan. Coba lagi.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }, confirmButton = { TextButton(enabled = owner.isNotBlank() && shop.isNotBlank() && kind.isNotBlank(), onClick = { saveError = !onSave(ShopProfile(owner.trim(), shop.trim(), kind)) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Batal") } })
}

@Composable
private fun BinaryDialog(ownerId: String, onClose: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val settings = remember(context, ownerId) { QrisSettings(context, ownerId) }
    var hasSaved by remember(settings) { mutableStateOf(!inspection && settings.payload() != null) }
    var input by remember(settings) { mutableStateOf(if (inspection) "" else settings.payload().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    fun updateInput(value: String) {
        if (value.length <= 24_576) { input = value; error = null }
        else error = "Kode QRIS terlalu panjang."
    }
    Dialog(onDismissRequest = onClose) {
        Surface(color = White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = PaleBlue, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.QrCode, null, tint = Blue, modifier = Modifier.padding(9.dp).size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("Masukkan Kode QRIS", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.Close, "Tutup", tint = Muted) }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Line)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Masukkan Payload QRIS", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (hasSaved) confirmReset = true else { input = ""; error = null } }) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset kode QRIS", tint = Blue)
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = input, onValueChange = ::updateInput,
                    label = { Text("Kode QRIS") }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 220.dp), maxLines = 8,
                    shape = RoundedCornerShape(10.dp),
                    trailingIcon = { IconButton(onClick = { clipboard.getText()?.text?.let(::updateInput) }) { Icon(Icons.Default.ContentPaste, "Tempel kode QRIS") } },
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = LightSurface, focusedContainerColor = LightSurface, unfocusedBorderColor = Line))
                if (error != null) { Spacer(Modifier.height(8.dp)); Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                Spacer(Modifier.height(12.dp))
                Button(enabled = input.isNotBlank(), modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(10.dp), onClick = {
                    try {
                        if (settings.saveQrisInput(input)) onSaved() else error = "Kode belum dapat disimpan. Coba lagi."
                    } catch (e: IllegalArgumentException) { error = e.message ?: "Kode tidak sesuai format QRIS." } catch (_: Exception) { error = "Kode belum dapat disimpan. Coba lagi." }
                }) { Text("Periksa format & simpan", fontWeight = FontWeight.Bold) }
            }
        }
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text("Hapus kode QRIS tersimpan?") },
        text = { Text("Kode akan dihapus dari perangkat untuk akun ini. Riwayat penjualan tidak berubah.") },
        confirmButton = { TextButton(onClick = {
            try {
                if (settings.clear()) { input = ""; hasSaved = false; error = null; confirmReset = false }
                else { confirmReset = false; error = "Kode belum dapat dihapus." }
            } catch (_: Exception) { confirmReset = false; error = "Kode belum dapat dihapus." }
        }) { Text("Hapus") } }, dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Batal") } })
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun AccountPreview() {
    WarungMatriksTheme { AccountScreen(SignedInAccount("demo", "Pemilik Demo", "", ShopProfile("Pemilik Demo", "Warung Matriks", "Warung")), {}, false, null, {}) }
}
