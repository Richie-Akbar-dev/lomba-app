package id.warungmatriks.app.ui

import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.warungmatriks.app.data.*
import id.warungmatriks.app.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
internal fun InventoryScreen(ownerId: String, onAdd: () -> Unit, onEdit: (String) -> Unit) {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val store = remember(context, ownerId) { CashierStore(context, ownerId) }
    var query by remember { mutableStateOf("") }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmActive by remember { mutableStateOf<Product?>(null) }
    var updating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(store, inspection) {
        try { products = if (inspection) DemoData.products else store.products() } catch (e: CancellationException) { throw e } catch (_: Exception) { message = "Daftar barang belum dapat dibuka." }
    }
    val list = products.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val low = products.filter { it.active && it.stock <= it.minimumStock }
    DemoPage("Daftar Barang", "Inventaris tersimpan di perangkat") {
        OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Cari nama barang...") })
        Spacer(Modifier.height(12.dp))
        DemoNote("Katalog awal berisi barang contoh, bukan data toko Anda. Periksa, ubah, atau nonaktifkan sebelum digunakan.")
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Add, null); Text(" Tambah barang") }
        SectionGap(); GroupTitle("Melewati batas stok", "Stok ≤ batas minimum")
        DemoCard {
            DemoLabel("${low.size} perlu kulak")
            if (low.isEmpty()) { Spacer(Modifier.height(8.dp)); DemoNote("Belum ada barang yang perlu kulak.") }
            low.take(5).forEach { product -> Spacer(Modifier.height(8.dp)); InfoLine(product.name, "${product.stock} / min ${product.minimumStock} pcs") }
        }
        SectionGap(); GroupTitle("Daftar inventaris", "${list.size} barang")
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        if (list.isEmpty()) Text("Barang tidak ditemukan.", color = Muted)
        list.forEach { product ->
            DemoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProductPhoto(product, modifier = Modifier.size(60.dp).background(Background, RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(product.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Stok ${product.stock} pcs • ${product.category}", fontSize = 12.sp, color = Muted)
                        Spacer(Modifier.height(4.dp))
                        Text("Jual ${rupiah(product.price)} / pcs", color = Blue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(product.cost?.let { "Modal ${rupiah(it)} / pcs" } ?: "Modal belum diisi", color = Muted, fontSize = 11.sp)
                    }
                    IconButton(onClick = { onEdit(product.id) }) { Icon(Icons.Default.Edit, "Edit ${product.name}", tint = Blue) }
                }
                TextButton(onClick = { confirmActive = product }, enabled = !updating) { Text(if (product.active) "Nonaktifkan" else "Aktifkan kembali") }
                if (!product.active) DemoLabel("Tidak dijual")
            }
            Spacer(Modifier.height(10.dp))
        }
        DemoNote("Barang nonaktif tetap tersimpan, tetapi tidak muncul di kasir.")
    }
    confirmActive?.let { product ->
        AlertDialog(onDismissRequest = { if (!updating) confirmActive = null },
            title = { Text(if (product.active) "Nonaktifkan barang?" else "Aktifkan barang?") },
            text = { Text(if (product.active) "${product.name} tidak dapat dijual, tetapi data dan riwayat tetap tersimpan." else "${product.name} akan kembali muncul di kasir.") },
            confirmButton = { TextButton(enabled = !updating, onClick = {
                updating = true
                scope.launch {
                    try { store.setProductActive(product.id, !product.active); products = store.products(); confirmActive = null } catch (e: CancellationException) { throw e } catch (_: Exception) { message = "Status barang belum dapat diubah."; confirmActive = null } finally { updating = false }
                }
            }) { Text(if (product.active) "Nonaktifkan" else "Aktifkan") } },
            dismissButton = { TextButton(enabled = !updating, onClick = { confirmActive = null }) { Text("Batal") } })
    }
}

@Composable
internal fun AddProductScreen(ownerId: String, productId: String? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context, ownerId) { CashierStore(context, ownerId) }
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var buy by rememberSaveable { mutableStateOf("") }
    var sell by rememberSaveable { mutableStateOf("") }
    var stock by rememberSaveable { mutableStateOf("0") }
    var minimum by rememberSaveable { mutableStateOf("0") }
    var barcode by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Sembako") }
    var customCategory by rememberSaveable { mutableStateOf("") }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var originalPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    var loadedProductId by rememberSaveable { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(productId != null && loadedProductId != productId) }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(store, productId) {
        if (productId != null && loadedProductId != productId) {
            try {
                val existing = store.products().firstOrNull { it.id == productId }
                if (existing == null) { message = "Barang tidak ditemukan."; loading = true }
                else {
                    name = existing.name; buy = existing.cost?.toString().orEmpty(); sell = existing.price.toString()
                    stock = existing.stock.toString(); minimum = existing.minimumStock.toString(); barcode = existing.barcode.orEmpty()
                    if (existing.category in listOf("Sembako", "Minuman", "Makanan", "Rokok")) category = existing.category
                    else { category = "Lainnya"; customCategory = existing.category }
                    photo = existing.photoFile; originalPhoto = existing.photoFile; loadedProductId = productId; loading = false
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { message = "Data barang belum dapat dibuka."; loading = true }
        }
    }
    fun importPhoto(uri: Uri, fromCamera: Boolean = false) {
        busy = true; message = null
        scope.launch {
            try {
                val next = ProductPhotoStorage.import(context, uri)
                if (photo != originalPhoto) ProductPhotoStorage.delete(context, photo)
                photo = next
            } catch (e: CancellationException) { throw e } catch (_: Exception) { message = "Foto tidak dapat dibaca atau disimpan. Coba foto lain." } finally { if (fromCamera) ProductPhotoStorage.deleteCamera(context, uri); busy = false }
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) importPhoto(uri) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        cameraUri?.let { value ->
            val uri = Uri.parse(value)
            if (success) importPhoto(uri, fromCamera = true) else ProductPhotoStorage.deleteCamera(context, uri)
        }
        cameraUri = null
    }
    val selectedCategory = if (category == "Lainnya") customCategory.trim() else category
    val cost = buy.toIntOrNull()
    val price = sell.toIntOrNull()
    val quantity = stock.toIntOrNull()
    val threshold = minimum.toIntOrNull()
    val valid = !loading && name.isNotBlank() && selectedCategory.isNotBlank() && price != null && price > 0 &&
        (buy.isBlank() || cost != null) && quantity != null && threshold != null
    val previewProduct = Product("preview", name.ifBlank { "Barang" }, price ?: 0, cost, quantity ?: 0, photoFile = photo)

    fun save(again: Boolean) {
        if (!valid || busy) return
        busy = true; message = null
        val product = Product(id = productId ?: UUID.randomUUID().toString(), name = name.trim(), price = price!!, cost = cost,
            stock = quantity!!, category = selectedCategory, minimumStock = threshold!!, photoFile = photo, barcode = barcode.trim().ifEmpty { null })
        scope.launch {
            try {
                if (productId == null) store.saveProduct(product) else {
                    store.updateProduct(product)
                    if (originalPhoto != photo) ProductPhotoStorage.delete(context, originalPhoto)
                }
                saved = true
                // 50 milliseconds follows the requested duration (three frames at 60 Hz).
                delay(50)
                saved = false
                if (again) {
                    name = ""; buy = ""; sell = ""; stock = "0"; minimum = "0"; barcode = ""; category = "Sembako"; customCategory = ""; photo = null
                } else onBack()
            } catch (e: CancellationException) { throw e } catch (_: SQLiteConstraintException) { message = "Barcode sudah digunakan. Gunakan barcode lain atau kosongkan." } catch (_: Exception) { message = "Barang belum dapat disimpan. Periksa penyimpanan dan coba lagi." } finally { busy = false }
        }
    }

    DemoPage(if (productId == null) "Tambah Barang" else "Edit Barang", "Data disimpan di perangkat", onBack) {
        DemoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProductPhoto(previewProduct, Modifier.size(72.dp).background(Background, RoundedCornerShape(10.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text("Foto barang", fontWeight = FontWeight.Bold); DemoNote("Opsional • tanpa foto memakai ikon barang") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { gallery.launch("image/*") }, enabled = !busy, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("Pilih foto", fontSize = 12.sp) }
                OutlinedButton(onClick = {
                    try { val uri = ProductPhotoStorage.cameraUri(context); cameraUri = uri.toString(); camera.launch(uri) } catch (_: Exception) { message = "Kamera belum tersedia di perangkat ini." }
                }, enabled = !busy, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("Ambil foto", fontSize = 12.sp) }
            }
            if (photo != null) TextButton(onClick = { if (photo != originalPhoto) ProductPhotoStorage.delete(context, photo); photo = null }, enabled = !busy) { Text("Hapus foto") }
        }
        SectionGap(); GroupTitle("Detail barang")
        DemoCard {
            FormField("Nama barang *", name, { name = it.take(80) }, "Contoh: Minyak Kita 1L")
            Spacer(Modifier.height(10.dp)); FormField("Barcode (opsional)", barcode, { barcode = it.take(80) })
            Spacer(Modifier.height(12.dp)); Text("Kategori", fontSize = 12.sp, color = Muted)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Sembako", "Minuman", "Makanan", "Rokok", "Lainnya").forEach { FilterChip(selected = category == it, onClick = { category = it }, label = { Text(it, fontSize = 11.sp) }) }
            }
            if (category == "Lainnya") FormField("Nama kategori *", customCategory, { customCategory = it.take(40) })
            Spacer(Modifier.height(8.dp)); Text("Satuan jual: pcs", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        SectionGap(); GroupTitle("Harga & laba")
        DemoCard {
            FormField("Modal per pcs (Rp, opsional)", buy, { buy = it.filter { c -> c in '0'..'9' }.take(9) }, "Kosongkan jika belum diketahui", true)
            Spacer(Modifier.height(10.dp)); FormField("Harga jual per pcs (Rp) *", sell, { sell = it.filter { c -> c in '0'..'9' }.take(9) }, "0", true)
            Spacer(Modifier.height(12.dp))
            Text(if (cost != null && price != null) "Estimasi laba kotor: ${rupiah(price - cost)} / pcs" else "Laba kotor belum diketahui jika modal belum diisi.", color = Blue, fontSize = 12.sp)
            if (cost != null && price != null && price < cost) Text("Harga jual lebih rendah daripada modal.", color = Muted, fontSize = 12.sp)
        }
        SectionGap(); GroupTitle("Stok & satuan")
        DemoCard {
            if (productId == null) FormField("Stok awal (pcs)", stock, { stock = it.filter { c -> c in '0'..'9' }.take(8) }, "0", true)
            else Text("Stok saat ini: $stock pcs • perubahan stok melalui kulakan/koreksi nanti.", fontSize = 12.sp, color = Muted)
            Spacer(Modifier.height(10.dp)); FormField("Batas stok minimum (pcs)", minimum, { minimum = it.filter { c -> c in '0'..'9' }.take(8) }, "0", true)
            Spacer(Modifier.height(10.dp)); DemoNote(if (productId == null) "Stok awal tidak otomatis dicatat sebagai kulakan atau kas keluar." else "Edit harga tidak mengubah penjualan sebelumnya.")
        }
        SectionGap()
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp); Spacer(Modifier.height(8.dp)) }
        Button(onClick = { save(false) }, enabled = valid && !busy, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) { Text(if (busy) "Memproses..." else if (productId == null) "Simpan & Tampilkan di Kasir" else "Simpan Perubahan") }
        Spacer(Modifier.height(8.dp))
        if (productId == null) OutlinedButton(onClick = { save(true) }, enabled = valid && !busy, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) { Text("Simpan & Tambah Barang Lagi") }
        Spacer(Modifier.height(8.dp)); DemoNote("Data hanya tersimpan di perangkat. Backup belum tersedia.")
    }
    if (saved) AlertDialog(onDismissRequest = {}, title = { Text("Berhasil disimpan") }, text = { Text("Barang tersimpan di perangkat.") }, confirmButton = {})
}

@Composable
internal fun FormField(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String = "", numeric: Boolean = false) {
    OutlinedTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, placeholder = { Text(placeholder) },
        singleLine = true, shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text))
}

@Composable
internal fun DemoDialog(title: String, message: String, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text(title) }, text = { Text(message) }, confirmButton = { TextButton(onClick = onClose) { Text("Mengerti") } })
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun InventoryPreview() { WarungMatriksTheme { InventoryScreen("preview", {}, {}) } }
@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun AddProductPreview() { WarungMatriksTheme { AddProductScreen("preview", onBack = {}) } }
