package id.warungmatriks.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.warungmatriks.app.R
import id.warungmatriks.app.data.DemoData
import id.warungmatriks.app.data.Product
import id.warungmatriks.app.data.rupiah
import id.warungmatriks.app.data.calculateCartTotals
import id.warungmatriks.app.ui.theme.*

internal data class DemoReceipt(val total: Int, val cash: Int)

// Demo state lives in memory only. It is not a transaction store.
internal class CashierState {
    val quantities = mutableStateMapOf<String, Int>().apply { putAll(DemoData.initialCart) }
    var query by mutableStateOf("")
    var cash by mutableIntStateOf(0)
    var receipt by mutableStateOf<DemoReceipt?>(null)
    var notice by mutableStateOf<String?>(null)
}

@Composable
internal fun CashierScreen(state: CashierState, cashierName: String, onNotifications: () -> Unit, onAccount: () -> Unit, onCash: () -> Unit, onInventory: () -> Unit) {
    val products = DemoData.products
    val quantities = state.quantities

    val items = products.filter { (quantities[it.id] ?: 0) > 0 }
    val count = quantities.values.sum()
    val totals = calculateCartTotals(products, quantities)
    val total = totals.subtotal
    val grossProfit = totals.grossProfit
    val visible = products.filter { it.name.contains(state.query.trim(), ignoreCase = true) }

    fun changeQuantity(product: Product, delta: Int) {
        val newQuantity = ((quantities[product.id] ?: 0) + delta).coerceIn(0, product.stock)
        if (newQuantity == 0) quantities.remove(product.id) else quantities[product.id] = newQuantity
    }

    Column(
        Modifier.fillMaxSize().background(Background).verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Header(cashierName = cashierName, onNotifications = onNotifications, onAccount = onAccount)
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Kasir", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Siap melayani hari ini?", color = Muted, fontSize = 13.sp)
            }
            Surface(color = PaleBlue, shape = RoundedCornerShape(50)) {
                Text("●  DATA DEMO", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(18.dp))
        DemoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Blue)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Kas laci kasir", color = Muted, fontSize = 12.sp)
                    Text("Rp1.450.000", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                DemoLabel("Contoh")
            }
            Spacer(Modifier.height(8.dp))
            DemoNote("Angka ilustrasi, bukan saldo yang dihitung dari transaksi.")
            DemoAction("Lihat kas masuk / keluar", onCash)
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.query, onValueChange = { state.query = it }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Cari nama produk...", fontSize = 14.sp, color = Muted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Muted) },
            singleLine = true, shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White, unfocusedBorderColor = Line),
        )
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Daftar produk", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onInventory) { Text("Lihat barang", fontSize = 12.sp) }
        }
        Spacer(Modifier.height(12.dp))
        if (visible.isEmpty()) {
            Text("Produk tidak ditemukan.", color = Muted, modifier = Modifier.padding(vertical = 16.dp))
        }
        visible.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { product ->
                    ProductCard(product, (quantities[product.id] ?: 0) < product.stock, Modifier.weight(1f)) { changeQuantity(product, 1) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Keranjang", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("$count barang dipilih", fontSize = 12.sp, color = Muted)
            }
            TextButton(onClick = { quantities.clear(); state.cash = 0 }, enabled = count > 0) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text("Kosongkan", fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Surface(color = Color.White, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                if (items.isEmpty()) {
                    Text("Keranjang masih kosong. Tambahkan produk untuk memulai.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    items.forEachIndexed { index, product ->
                        CartItem(product, quantities[product.id] ?: 0, { changeQuantity(product, -1) }, { changeQuantity(product, 1) }, { quantities.remove(product.id) })
                        if (index < items.lastIndex) HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Line)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Line)
                AmountRow("Subtotal", rupiah(total))
                Spacer(Modifier.height(8.dp))
                AmountRow("Laba kotor (demo)", rupiah(grossProfit), valueColor = Blue)
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Line)
                Spacer(Modifier.height(12.dp))
                AmountRow("Total pembayaran", rupiah(total), bold = true, valueColor = Blue)
            }
        }
        Spacer(Modifier.height(24.dp))
        SectionHeading("Uang tunai", "Pilih nominal")
        Spacer(Modifier.height(10.dp))
        val options = listOf("Uang pas" to total, "Rp20.000" to 20000, "Rp50.000" to 50000, "Rp100.000" to 100000)
        options.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { (label, amount) ->
                    val selected = amount > 0 && state.cash == amount
                    Surface(
                        modifier = Modifier.weight(1f).height(44.dp).clickable(enabled = total > 0) { state.cash = amount },
                        shape = RoundedCornerShape(10.dp), color = if (selected) PaleBlue else Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Blue else Line),
                    ) { Box(contentAlignment = Alignment.Center) { Text(label, fontSize = 13.sp, color = if (selected) Blue else Ink, fontWeight = FontWeight.SemiBold) } }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        AmountRow("Tunai dipilih", rupiah(state.cash))
        Spacer(Modifier.height(8.dp))
        AmountRow("Kembalian", rupiah((state.cash - total).coerceAtLeast(0)), bold = true)
        if (total > 0 && state.cash in 1 until total) {
            Text("Uang tunai kurang ${rupiah(total - state.cash)}", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { state.receipt = DemoReceipt(total, state.cash) },
            enabled = total > 0 && state.cash >= total,
            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(13.dp),
        ) {
            Icon(Icons.Default.PointOfSale, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text("Bayar (demo)", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { state.notice = "QRIS belum tersedia pada prototipe ini. Tidak ada kode atau pembayaran yang diproses." }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(13.dp)) {
            Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text("Bayar QRIS (belum tersedia)")
        }
        Text("Transaksi simulasi, tidak ada pembayaran sungguhan.", modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp, bottom = 24.dp), color = Muted, fontSize = 11.sp)
    }

    state.notice?.let { message ->
        AlertDialog(onDismissRequest = { state.notice = null }, title = { Text("Informasi demo") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { state.notice = null }) { Text("Mengerti") } })
    }
    state.receipt?.let { result ->
        AlertDialog(
            onDismissRequest = { state.receipt = null },
            title = { Text("Pembayaran demo berhasil") },
            text = { Text("Simulasi selesai, tidak ada pembayaran yang diproses atau transaksi yang disimpan.\n\nTotal: ${rupiah(result.total)}\nTunai: ${rupiah(result.cash)}\nKembalian: ${rupiah(result.cash - result.total)}") },
            confirmButton = { TextButton(onClick = { quantities.clear(); state.cash = 0; state.receipt = null }) { Text("Selesai & kosongkan") } },
            dismissButton = { TextButton(onClick = { state.receipt = null }) { Text("Tutup") } },
        )
    }
}

@Composable
private fun Header(cashierName: String, onNotifications: () -> Unit, onAccount: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Image(painterResource(R.drawable.warung_matriks_logo), contentDescription = "Logo Warung Matriks", modifier = Modifier.size(43.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("Warung Matriks", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1)
            Text("Kasir: $cashierName", fontSize = 11.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onNotifications, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.NotificationsNone, contentDescription = "Notifikasi demo", tint = Ink)
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onAccount, modifier = Modifier.size(38.dp).background(PaleBlue, CircleShape)) {
            Icon(Icons.Default.PersonOutline, contentDescription = "Profil demo", tint = Blue, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SectionHeading(title: String, detail: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(detail, fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun ProductCard(product: Product, canAdd: Boolean, modifier: Modifier = Modifier, onAdd: () -> Unit) {
    Surface(modifier = modifier, color = Color.White, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(10.dp)) {
            Box(Modifier.fillMaxWidth().height(86.dp).clip(RoundedCornerShape(10.dp)).background(Background), contentAlignment = Alignment.Center) {
                Image(painterResource(product.image), contentDescription = null, modifier = Modifier.fillMaxSize().padding(4.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(product.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Text(rupiah(product.price), color = Blue, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(5.dp))
            Text("Stok ${product.stock}  ·  Modal ${rupiah(product.cost)}", color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onAdd, enabled = canAdd, modifier = Modifier.fillMaxWidth().height(36.dp), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(9.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (canAdd) Blue else Line)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(if (canAdd) " Tambah" else " Maks. stok", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CartItem(product: Product, quantity: Int, onMinus: () -> Unit, onPlus: () -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Image(painterResource(product.image), contentDescription = null, modifier = Modifier.size(43.dp).clip(RoundedCornerShape(8.dp)).background(Background))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(product.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(rupiah(product.price * quantity), fontSize = 12.sp, color = Blue, fontWeight = FontWeight.Bold)
        }
        QuantityButton("Kurangi ${product.name}", Icons.Default.Remove, onMinus)
        Text("$quantity", modifier = Modifier.padding(horizontal = 5.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        QuantityButton("Tambah ${product.name}", Icons.Default.Add, onPlus, enabled = quantity < product.stock)
        IconButton(onClick = onRemove, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Hapus ${product.name}", tint = Muted, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun QuantityButton(description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(28.dp).border(1.dp, Line, RoundedCornerShape(7.dp))) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(16.dp), tint = if (enabled) Blue else Muted)
    }
}

@Composable
private fun AmountRow(label: String, amount: String, bold: Boolean = false, valueColor: Color = Ink) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = if (bold) 14.sp else 13.sp, color = if (bold) Ink else Muted, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(amount, fontSize = if (bold) 16.sp else 13.sp, color = valueColor, fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold)
    }
}
