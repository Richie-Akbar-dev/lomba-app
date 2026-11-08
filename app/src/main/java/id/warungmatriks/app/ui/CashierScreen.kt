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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.warungmatriks.app.R
import id.warungmatriks.app.data.DemoData
import id.warungmatriks.app.data.Product
import id.warungmatriks.app.data.rupiah
import id.warungmatriks.app.data.calculateCartTotals
import id.warungmatriks.app.data.CashierLine
import id.warungmatriks.app.data.CashierStore
import id.warungmatriks.app.data.CheckoutResult
import id.warungmatriks.app.data.InsufficientStockException
import id.warungmatriks.app.data.ProductChangedException
import id.warungmatriks.app.data.LocalReceivable
import id.warungmatriks.app.data.matchesSearch
import id.warungmatriks.app.data.needsRestock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID
import id.warungmatriks.app.ui.theme.*

// Catalog, sales and outstanding debts are stored locally for this account.
internal class CashierState {
    val quantities = mutableStateMapOf<String, Int>()
    val catalog = mutableStateListOf<Product>()
    val receivables = mutableStateListOf<LocalReceivable>()
    var ready by mutableStateOf(false)
    var query by mutableStateOf("")
    var exactBarcode by mutableStateOf(false)
    var cashText by mutableStateOf("")
    var customer by mutableStateOf("")
    var showCheckout by mutableStateOf(false)
    var showQris by mutableStateOf(false)
    var qrisTotal by mutableIntStateOf(0)
    var qrisLines by mutableStateOf<List<CashierLine>>(emptyList())
    var qrisSaleId by mutableStateOf("")
    var qrisSource by mutableStateOf("")
    var processing by mutableStateOf(false)
    var receipt by mutableStateOf<CheckoutResult?>(null)
    var notice by mutableStateOf<String?>(null)
    var checkoutId by mutableStateOf(UUID.randomUUID().toString())
}

@Composable
internal fun CashierScreen(state: CashierState, ownerId: String, cashierName: String, onNotifications: () -> Unit, onAccount: () -> Unit, onCash: () -> Unit, onInventory: () -> Unit, onAddProduct: () -> Unit) {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val store = remember(context, ownerId) { CashierStore(context, ownerId) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(store, inspection) {
        try {
            val catalog = if (inspection) DemoData.products else store.products()
            val debts = if (inspection) emptyList() else store.receivables()
            val previous = state.catalog.associateBy { it.id }
            val current = catalog.associateBy { it.id }
            var adjusted = false
            state.quantities.keys.toList().forEach { id ->
                val item = current[id]; val old = previous[id]; val count = state.quantities[id] ?: 0
                if (item == null || !item.active || item.stock == 0) { state.quantities.remove(id); adjusted = true }
                else {
                    if (count > item.stock) { state.quantities[id] = item.stock; adjusted = true }
                    if (old != null && (old.price != item.price || old.name != item.name || old.cost != item.cost)) adjusted = true
                }
            }
            state.catalog.clear(); state.catalog.addAll(catalog)
            if (adjusted) state.notice = "Data barang atau stok berubah. Periksa kembali isi dan harga keranjang."
            state.receivables.clear(); state.receivables.addAll(debts); state.ready = true
        } catch (e: CancellationException) { throw e } catch (_: Exception) { state.ready = false; state.notice = "Stok lokal belum dapat dibuka. Mulai ulang aplikasi dan coba lagi." }
    }
    val products = state.catalog.filter { it.active }
    val quantities = state.quantities
    val items = products.filter { (quantities[it.id] ?: 0) > 0 }
    val count = quantities.values.sumOf { it.toLong() }
    val totals = calculateCartTotals(products, quantities)
    val total = totals.subtotal
    val scanBarcode = rememberBarcodeScanner(onScanned = { state.query = it; state.exactBarcode = true }, onError = { state.notice = it })
    val visible = products.filter { it.matchesSearch(state.query, state.exactBarcode) }
    val cash = state.cashText.toIntOrNull() ?: 0
    val debt = (total - cash).coerceAtLeast(0)
    fun changeQuantity(product: Product, delta: Int) {
        val newQuantity = ((quantities[product.id] ?: 0) + delta).coerceIn(0, product.stock)
        if (newQuantity == 0) quantities.remove(product.id) else quantities[product.id] = newQuantity
    }

    Column(
        Modifier.fillMaxSize().background(Background).verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Header(cashierName = cashierName, lowStockCount = if (state.ready) state.catalog.count { it.needsRestock() } else 0, onNotifications = onNotifications, onAccount = onAccount)
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Kasir", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Siap melayani hari ini?", color = Muted, fontSize = 13.sp)
            }
            Surface(color = PaleBlue, shape = RoundedCornerShape(50)) {
                Text("●  DATA LOKAL", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
            value = state.query, onValueChange = { state.query = it; state.exactBarcode = false }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Cari nama atau barcode...", fontSize = 14.sp, color = Muted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Muted) },
            trailingIcon = { IconButton(onClick = scanBarcode) { Icon(Icons.Default.QrCodeScanner, "Pindai barcode barang", tint = Blue) } },
            singleLine = true, shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White, unfocusedBorderColor = Line),
        )
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Daftar produk", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onInventory) { Text("Lihat barang", fontSize = 12.sp) }
        }
        TextButton(onClick = onAddProduct) { Icon(Icons.Default.Add, null); Text(" Tambah barang") }
        DemoNote("Katalog awal berisi barang contoh. Periksa, ubah, atau nonaktifkan di Barang sebelum digunakan.")
        Spacer(Modifier.height(12.dp))
        if (!state.ready) {
            Text("Menyiapkan stok lokal...", color = Muted, modifier = Modifier.padding(vertical = 16.dp))
        } else if (visible.isEmpty()) {
            Text("Produk tidak ditemukan.", color = Muted, modifier = Modifier.padding(vertical = 16.dp))
        }
        (if (state.ready) visible else emptyList()).chunked(2).forEach { row ->
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
            TextButton(onClick = { quantities.clear(); state.cashText = ""; state.customer = "" }, enabled = count > 0) {
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

                HorizontalDivider(color = Line)
                Spacer(Modifier.height(12.dp))
                AmountRow("Total pembayaran", rupiah(total), bold = true, valueColor = Blue)
            }
        }
        Spacer(Modifier.height(24.dp))
        SectionHeading("Uang tunai", "Pilih nominal")
        Spacer(Modifier.height(10.dp))
        val options = listOf("Belum bayar" to 0, "Uang pas" to total, "Rp20.000" to 20000, "Rp50.000" to 50000, "Rp100.000" to 100000)
        options.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { (label, amount) ->
                    val selected = state.cashText.isNotBlank() && cash == amount
                    Surface(
                        modifier = Modifier.weight(1f).height(44.dp).clickable(enabled = total > 0) { state.cashText = amount.toString() },
                        shape = RoundedCornerShape(10.dp), color = if (selected) PaleBlue else Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Blue else Line),
                    ) { Box(contentAlignment = Alignment.Center) { Text(label, fontSize = 13.sp, color = if (selected) Blue else Ink, fontWeight = FontWeight.SemiBold) } }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(
            value = state.cashText,
            onValueChange = { state.cashText = it.filter { c -> c in '0'..'9' }.take(9) },
            label = { Text("Uang tunai diterima (Rp)") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(12.dp))
        AmountRow("Kembalian", rupiah((cash - total).coerceAtLeast(0)), bold = true)
        Spacer(Modifier.height(8.dp))
        AmountRow("Sisa kasbon", if (state.cashText.isBlank()) "—" else rupiah(debt), bold = state.cashText.isNotBlank() && debt > 0, valueColor = Blue)
        if (totals.exceedsLimit) Text("Total melebihi batas yang didukung. Kurangi isi keranjang.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        if (total > 0 && state.cashText.isNotBlank() && debt > 0) {
            Text("Sisa akan dicatat sebagai kasbon. Nama pelanggan wajib diisi.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { state.customer = ""; state.showCheckout = true },
                enabled = state.ready && total > 0 && !totals.exceedsLimit && state.cashText.isNotBlank() && !state.processing,
                modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(13.dp), contentPadding = PaddingValues(horizontal = 4.dp),
            ) { Text("Bayar tunai", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            OutlinedButton(onClick = {
                state.qrisTotal = total
                state.qrisLines = items.map { CashierLine(it, quantities[it.id] ?: 0) }
                state.qrisSaleId = state.checkoutId
                state.showQris = true
            }, enabled = state.ready && total > 0 && !totals.exceedsLimit && !state.processing,
                modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(13.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text("Tampilkan QRIS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        Text("Penjualan tersimpan lokal dan tampil di Pesanan; pembayaran QRIS tidak diverifikasi. Kas dan laporan masih demo.", modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp, bottom = 16.dp), color = Muted, fontSize = 11.sp)
        if (state.receivables.isNotEmpty()) {
            SectionHeading("Kasbon lokal", "${state.receivables.size} catatan")
            Spacer(Modifier.height(8.dp))
            DemoCard {
                state.receivables.take(5).forEach { entry ->
                    InfoLine(entry.customer, rupiah(entry.remaining)); Spacer(Modifier.height(8.dp))
                }
                DemoNote("Kasbon tersimpan di prototipe lokal. Pelunasan dan halaman Kasbon belum terhubung.")
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (state.showQris) QrisDialog(total = state.qrisTotal, source = state.qrisSource, onSourceChange = { state.qrisSource = it }, onRecord = {
        state.processing = true
        try {
            store.checkoutQris(state.qrisSaleId, state.qrisLines)
            // The QRIS sale is committed. Refresh errors cannot undo or duplicate it.
            quantities.clear(); state.cashText = ""; state.customer = ""
            state.checkoutId = UUID.randomUUID().toString()
            try {
                val freshCatalog = store.products(); val freshDebts = store.receivables()
                state.catalog.clear(); state.catalog.addAll(freshCatalog)
                state.receivables.clear(); state.receivables.addAll(freshDebts)
            } catch (_: Exception) { state.ready = false; state.notice = "Penjualan QRIS tersimpan, tetapi data gagal dimuat ulang. Mulai ulang aplikasi." }
        } finally { state.processing = false }
    }, onClose = { state.showQris = false })
    if (state.showCheckout) {
        AlertDialog(
            onDismissRequest = { if (!state.processing) state.showCheckout = false },
            title = { Text(if (debt > 0) "Bayar & catat kasbon" else "Konfirmasi tunai") },
            text = { Column {
                Text("Total: ${rupiah(total)}\nTunai diterima: ${rupiah(cash)}\nKembalian: ${rupiah((cash - total).coerceAtLeast(0))}\nSisa kasbon: ${rupiah(debt)}")
                if (debt > 0) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = state.customer, onValueChange = { state.customer = it.take(80) }, label = { Text("Nama pelanggan (wajib)") }, singleLine = true)
                }
                Spacer(Modifier.height(8.dp))
                DemoNote("Simpan lokal saja. Riwayat tersedia di Pesanan; kas dan halaman kasbon masih demo.")
            } },
            confirmButton = { TextButton(enabled = !state.processing && (debt == 0 || state.customer.isNotBlank()), onClick = {
                val lines = items.map { CashierLine(it, quantities[it.id] ?: 0) }
                val submittedCash = cash
                val submittedCustomer = state.customer.trim()
                val saleId = state.checkoutId
                state.processing = true
                scope.launch {
                    try {
                        val result = store.checkout(saleId, lines, submittedCash, submittedCustomer)
                        // The sale is committed at this point; never report it as failed due to a later refresh.
                        state.receipt = result; state.showCheckout = false
                        quantities.clear(); state.cashText = ""; state.customer = ""
                        state.checkoutId = UUID.randomUUID().toString()
                        try {
                            val freshCatalog = store.products(); val freshDebts = store.receivables()
                            state.catalog.clear(); state.catalog.addAll(freshCatalog)
                            state.receivables.clear(); state.receivables.addAll(freshDebts)
                        } catch (e: CancellationException) { throw e } catch (_: Exception) { state.ready = false; state.notice = "Transaksi tersimpan, tetapi data gagal dimuat ulang. Mulai ulang aplikasi." }
                    } catch (e: CancellationException) { throw e } catch (_: InsufficientStockException) {
                        state.showCheckout = false
                        state.notice = "Stok berubah atau tidak cukup. Periksa kembali keranjang."
                        try { val freshCatalog = store.products(); state.catalog.clear(); state.catalog.addAll(freshCatalog) } catch (e: CancellationException) { throw e } catch (_: Exception) { state.ready = false }
                    } catch (_: ProductChangedException) {
                        state.showCheckout = false
                        quantities.clear(); state.cashText = ""
                        state.notice = "Data barang berubah. Keranjang dikosongkan; periksa harga dan pilih ulang barang."
                        try { val freshCatalog = store.products(); state.catalog.clear(); state.catalog.addAll(freshCatalog) } catch (e: CancellationException) { throw e } catch (_: Exception) { state.ready = false }
                    } catch (_: Exception) { state.notice = "Transaksi tidak dapat disimpan. Periksa penyimpanan dan coba lagi." } finally { state.processing = false }
                }
            }) { Text(if (state.processing) "Menyimpan..." else "Simpan lokal") } },
            dismissButton = { TextButton(enabled = !state.processing, onClick = { state.showCheckout = false }) { Text("Batal") } },
        )
    }

    state.notice?.let { message ->
        AlertDialog(onDismissRequest = { state.notice = null }, title = { Text("Informasi") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { state.notice = null }) { Text("Mengerti") } })
    }
    state.receipt?.let { result ->
        AlertDialog(
            onDismissRequest = { state.receipt = null },
            title = { Text("Tersimpan di perangkat") },
            text = { Text("Total: ${rupiah(result.total)}\nTunai diterima: ${rupiah(result.received)}\nKembalian: ${rupiah(result.change)}\nSisa kasbon: ${rupiah(result.debt)}" + (result.customer?.let { "\nPelanggan: $it" } ?: "") + "\n\nPrototipe: riwayat tersedia di Pesanan; kas dan laporan masih demo.") },
            confirmButton = { TextButton(onClick = { state.receipt = null }) { Text("Selesai") } },
        )
    }
}

@Composable
private fun Header(cashierName: String, lowStockCount: Int, onNotifications: () -> Unit, onAccount: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Image(painterResource(R.drawable.warung_matriks_logo), contentDescription = "Logo Warung Matriks", modifier = Modifier.size(43.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("Warung Matriks", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1)
            Text("Kasir: $cashierName", fontSize = 11.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onNotifications, modifier = Modifier.size(40.dp)) {
            BadgedBox(badge = { if (lowStockCount > 0) Badge { Text(if (lowStockCount > 99) "99+" else lowStockCount.toString()) } }) {
                Icon(Icons.Default.NotificationsNone, contentDescription = "Notifikasi stok: $lowStockCount barang", tint = Ink)
            }
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
                ProductPhoto(product, modifier = Modifier.fillMaxSize().padding(4.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(product.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Text(rupiah(product.price), color = Blue, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(5.dp))
            Text("Stok ${product.stock} pcs", color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        ProductPhoto(product, modifier = Modifier.size(43.dp).clip(RoundedCornerShape(8.dp)).background(Background))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(product.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(rupiah(product.price.toLong() * quantity), fontSize = 12.sp, color = Blue, fontWeight = FontWeight.Bold)
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

@Preview(showBackground = true, widthDp = 180, name = "Barang tanpa foto")
@Composable
private fun ProductWithoutImagePreview() {
    WarungMatriksTheme {
        ProductCard(Product("preview", "Barang tanpa foto", 3500, 2700, 10), true, Modifier.padding(8.dp)) {}
    }
}
