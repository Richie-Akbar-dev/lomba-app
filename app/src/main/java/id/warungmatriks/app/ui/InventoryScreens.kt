package id.warungmatriks.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.warungmatriks.app.data.*
import id.warungmatriks.app.ui.theme.*

@Composable
internal fun InventoryScreen(onAdd: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var edit by remember { mutableStateOf<Product?>(null) }
    val list = DemoData.products.filter { it.name.contains(query.trim(), ignoreCase = true) }
    DemoPage("Daftar Barang", "Inventaris & stok • data contoh") {
        OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Cari nama barang...") })
        Spacer(Modifier.height(12.dp))
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Add, null); Text(" Tambah barang") }
        SectionGap(); GroupTitle("Melewati batas stok", "Contoh daftar restok otomatis")
        DemoCard {
            DemoLabel("2 perlu kulak")
            Spacer(Modifier.height(10.dp))
            InfoLine("Indomie Goreng", "6 / minimum 15")
            Spacer(Modifier.height(8.dp)); InfoLine("Minyak Kita 1L", "2 / minimum 5")
            Spacer(Modifier.height(10.dp)); DemoNote("Ini hanya contoh tampilan; restok otomatis belum berjalan.")
        }
        SectionGap(); GroupTitle("Daftar inventaris", "${list.size} produk demo")
        if (list.isEmpty()) Text("Barang tidak ditemukan.", color = Muted)
        list.forEach { product ->
            DemoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(product.imageResource), null, modifier = Modifier.size(60.dp).background(Background, RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(product.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Stok ${product.stock} • Demo", fontSize = 12.sp, color = Muted)
                        Spacer(Modifier.height(4.dp))
                        Text("Jual ${rupiah(product.price)}", color = Blue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Modal ${rupiah(product.cost)} • Laba kotor ${rupiah(product.price - product.cost)} / unit", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                    IconButton(onClick = { edit = product }) { Icon(Icons.Default.Edit, "Edit ${product.name}", tint = Blue) }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        DemoNote("Harga modal dan laba terlihat hanya untuk demonstrasi; kontrol akses belum diterapkan.")
    }
    edit?.let { product -> EditProductDialog(product, onClose = { edit = null }) }
}

@Composable
private fun EditProductDialog(product: Product, onClose: () -> Unit) {
    var name by remember(product) { mutableStateOf(product.name) }
    var buy by remember(product) { mutableStateOf(product.cost.toString()) }
    var sell by remember(product) { mutableStateOf(product.price.toString()) }
    AlertDialog(onDismissRequest = onClose, title = { Text("Edit data barang") },
        text = {
            Column {
                DemoNote("Pratinjau formulir. Perubahan tidak akan disimpan.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(name, { name = it }, label = { Text("Nama barang") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(buy, { buy = it.filter(Char::isDigit) }, label = { Text("Harga modal (Rp)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(sell, { sell = it.filter(Char::isDigit) }, label = { Text("Harga jual (Rp)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        }, confirmButton = { TextButton(onClick = onClose) { Text("Tutup pratinjau") } })
}

@Composable
internal fun AddProductScreen(onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var buy by remember { mutableStateOf("") }
    var sell by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("") }
    var minimum by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Sembako") }
    var info by remember { mutableStateOf(false) }
    DemoPage("Tambah Menu & Barang", "Formulir contoh • belum disimpan", onBack) {
        DemoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AddPhotoAlternate, null, tint = Blue, modifier = Modifier.size(36.dp))
                Spacer(Modifier.width(12.dp)); Column { Text("Foto produk", fontWeight = FontWeight.Bold); DemoNote("Unggah foto belum tersedia") }
            }
        }
        SectionGap(); GroupTitle("Detail barang")
        DemoCard {
            FormField("Nama barang", name, { name = it }, "Contoh: Minyak Kita 1L")
            Spacer(Modifier.height(12.dp)); Text("Kategori", fontSize = 12.sp, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Sembako", "Minuman", "Makanan").forEach { FilterChip(selected = category == it, onClick = { category = it }, label = { Text(it, fontSize = 11.sp) }) }
            }
        }
        SectionGap(); GroupTitle("Harga & laba")
        DemoCard {
            FormField("Modal per unit (Rp)", buy, { buy = it.filter(Char::isDigit) }, "0", true)
            Spacer(Modifier.height(10.dp)); FormField("Harga jual per unit (Rp)", sell, { sell = it.filter(Char::isDigit) }, "0", true)
            val cost = buy.toIntOrNull(); val price = sell.toIntOrNull()
            Spacer(Modifier.height(12.dp))
            Text(if (cost != null && price != null) "Selisih harga jual − modal: ${rupiah(price - cost)} / unit" else "Laba kotor per unit dihitung dari harga jual − modal", color = Blue, fontSize = 12.sp)
        }
        SectionGap(); GroupTitle("Stok & satuan")
        DemoCard {
            FormField("Stok awal", stock, { stock = it.filter(Char::isDigit) }, "0", true)
            Spacer(Modifier.height(10.dp)); FormField("Batas stok minimum", minimum, { minimum = it.filter(Char::isDigit) }, "0", true)
        }
        SectionGap()
        Button(onClick = { info = true }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) { Text("Simpan (demo)") }
        Spacer(Modifier.height(8.dp)); DemoNote()
    }
    if (info) DemoDialog("Belum menyimpan", "Formulir ini hanya rancangan. Data barang tidak disimpan.") { info = false }
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
@Composable private fun InventoryPreview() { WarungMatriksTheme { InventoryScreen({}) } }
@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun AddProductPreview() { WarungMatriksTheme { AddProductScreen({}) } }
