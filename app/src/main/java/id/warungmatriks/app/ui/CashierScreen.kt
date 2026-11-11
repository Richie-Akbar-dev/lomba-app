package id.warungmatriks.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import id.warungmatriks.app.data.QrisSettings
import id.warungmatriks.app.data.CheckoutResult
import id.warungmatriks.app.data.CashSummary
import id.warungmatriks.app.data.cashWeek
import id.warungmatriks.app.data.InsufficientStockException
import id.warungmatriks.app.data.ProductChangedException
import id.warungmatriks.app.data.LocalReceivable
import id.warungmatriks.app.data.matchesCatalogFilter
import id.warungmatriks.app.data.needsRestock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import id.warungmatriks.app.ui.theme.*

// Catalog, sales and outstanding debts are stored locally for this account.
internal class CashierState {
    val quantities = mutableStateMapOf<String, Int>()
    val catalog = mutableStateListOf<Product>()
    val receivables = mutableStateListOf<LocalReceivable>()
    var ready by mutableStateOf(false)
    var weeklyCash by mutableStateOf<CashSummary?>(null)
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
internal fun CashierScreen(state: CashierState, ownerId: String, cashierName: String, shopName: String, onNotifications: () -> Unit, onAccount: () -> Unit, onCash: () -> Unit, onInventory: () -> Unit, onAddProduct: () -> Unit, onCredit: () -> Unit, onCreditCheckout: (PendingCredit) -> Unit) {
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
    LaunchedEffect(store, inspection) {
        while (true) {
            try { state.weeklyCash = if (inspection) CashSummary(0, 0) else store.cashSummary(cashWeek()) }
            catch (e: CancellationException) { throw e } catch (_: Exception) { state.weeklyCash = null }
            delay(30_000)
        }
    }
    val products = state.catalog.filter { it.active }
    val quantities = state.quantities
    val items = products.filter { (quantities[it.id] ?: 0) > 0 }
    val count = quantities.values.sumOf { it.toLong() }
    val totals = calculateCartTotals(products, quantities)
    val total = totals.subtotal
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val scanBarcode = rememberBarcodeScanner(onScanned = { state.query = it; state.exactBarcode = true; category = null }, onError = { state.notice = it })
    val visible = products.filter { it.matchesCatalogFilter(state.query, state.exactBarcode, category) }
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
        Header(cashierName = cashierName, shopName = shopName, lowStockCount = if (state.ready) state.catalog.count { it.needsRestock() } else 0, onNotifications = onNotifications, onAccount = onAccount)
        Spacer(Modifier.height(18.dp))
        DemoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = PaleBlue, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Blue, modifier = Modifier.padding(10.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Arus kas minggu ini", color = Muted, fontSize = 12.sp)
                    Text(state.weeklyCash?.let { cashNetText(it.net) } ?: "—", color = Ink, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            DemoNote("Kas masuk dikurangi kas keluar sejak Senin; bukan saldo fisik laci.")
            TextButton(onClick = onCash, contentPadding = PaddingValues(0.dp)) { Text("Lihat kas masuk & keluar →", fontWeight = FontWeight.SemiBold) }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.query, onValueChange = { state.query = it; state.exactBarcode = false }, modifier = Modifier.weight(1f),
                placeholder = { Text("Cari nama atau barcode...", fontSize = 13.sp, color = Muted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Muted) },
                trailingIcon = { IconButton(onClick = scanBarcode) { Icon(Icons.Default.QrCodeScanner, "Pindai barcode barang", tint = Blue) } },
                singleLine = true, shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = White, focusedContainerColor = White, unfocusedBorderColor = Line),
            )
            Button(onClick = onAddProduct, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(56.dp)) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp)); Text(" Barang", fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = category == null, onClick = { category = null }, label = { Text("Semua") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Blue, selectedLabelColor = White))
            products.map { it.category }.filter { it.isNotBlank() }.distinct().sorted().forEach { value ->
                FilterChip(selected = category == value, onClick = { category = value }, label = { Text(value) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Blue, selectedLabelColor = White))
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Storefront, contentDescription = null, tint = Blue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Daftar produk", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onInventory) { Text("Lihat barang", fontSize = 12.sp) }
        }
        if (!inspection && state.ready && state.catalog.any { product -> DemoData.products.any { it.id == product.id } }) {
            DemoNote("Barang dari versi sebelumnya tetap tersimpan. Pastikan nama, harga, dan stok sesuai sebelum digunakan.")
        }
        Spacer(Modifier.height(8.dp))
        if (!state.ready) {
            Text("Menyiapkan stok lokal...", color = Muted, modifier = Modifier.padding(vertical = 16.dp))
        } else if (products.isEmpty()) {
            DemoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = LightSurface, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.Inventory2, null, tint = Blue, modifier = Modifier.padding(10.dp).size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Belum ada barang aktif", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Tambahkan barang baru atau aktifkan kembali barang di halaman Barang.", fontSize = 12.sp, color = Muted, lineHeight = 18.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onAddProduct, contentPadding = PaddingValues(0.dp)) { Text("+ Tambah barang", fontWeight = FontWeight.SemiBold) }
            }
        } else if (visible.isEmpty()) {
            DemoCard {
                Text("Barang tidak ditemukan", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Coba nama, barcode, atau kategori lain.", color = Muted, fontSize = 12.sp)
                TextButton(onClick = { state.query = ""; state.exactBarcode = false; category = null }, contentPadding = PaddingValues(0.dp)) { Text("Hapus pencarian dan filter") }
            }
        }
        (if (state.ready) visible else emptyList()).chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { product ->
                    ProductCard(product, (quantities[product.id] ?: 0) < product.stock, Modifier.weight(1f), quantities[product.id] ?: 0) { changeQuantity(product, 1) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Keranjang Aktif", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
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
            Text("Lanjutkan ke halaman Kasbon untuk melengkapi nama dan nomor WhatsApp.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (debt > 0) onCreditCheckout(PendingCredit(state.checkoutId, items.map { CashierLine(it, quantities[it.id] ?: 0) }, cash))
                    else state.showCheckout = true
                },
                enabled = state.ready && total > 0 && !totals.exceedsLimit && state.cashText.isNotBlank() && !state.processing,
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(13.dp), contentPadding = PaddingValues(horizontal = 4.dp),
            ) { Icon(Icons.Default.Payments, null, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("Bayar tunai", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            OutlinedButton(onClick = {
                state.qrisTotal = total
                state.qrisLines = items.map { CashierLine(it, quantities[it.id] ?: 0) }
                state.qrisSaleId = state.checkoutId
                try {
                    state.qrisSource = if (inspection) "" else QrisSettings(context, ownerId).payload().orEmpty()
                    state.showQris = true
                } catch (_: Exception) { state.notice = "Pengaturan QRIS belum dapat dibaca. Coba lagi." }
            }, enabled = state.ready && total > 0 && !totals.exceedsLimit && !state.processing,
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(13.dp), contentPadding = PaddingValues(horizontal = 4.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = Sky, contentColor = White)) {
                Icon(Icons.Default.QrCode, null, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("Tampilkan QRIS", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        Text("Penjualan dan kas tunai tersimpan lokal. Periksa pembayaran QRIS di aplikasi penyedia.", modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp, bottom = 16.dp), color = Muted, fontSize = 11.sp)
        if (state.receivables.isEmpty()) TextButton(onClick = onCredit) { Text("Buka kasbon pelanggan →") }
        if (state.receivables.isNotEmpty()) {
            SectionHeading("Kasbon lokal", "${state.receivables.size} catatan")
            Spacer(Modifier.height(8.dp))
            DemoCard {
                state.receivables.take(5).forEach { entry ->
                    InfoLine(entry.customer, rupiah(entry.remaining)); Spacer(Modifier.height(8.dp))
                }
                DemoAction("Buka kasbon pelanggan", onCredit)
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
                Spacer(Modifier.height(8.dp))
                DemoNote("Transaksi disimpan di perangkat, termasuk kas tunai dan riwayat Pesanan.")
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
                            state.weeklyCash = store.cashSummary(cashWeek())
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
            text = { Text("Total: ${rupiah(result.total)}\nTunai diterima: ${rupiah(result.received)}\nKembalian: ${rupiah(result.change)}\nSisa kasbon: ${rupiah(result.debt)}" + (result.customer?.let { "\nPelanggan: $it" } ?: "") + "\n\nRiwayat tersedia di Pesanan, dan kas tunai tercatat secara lokal.") },
            confirmButton = { TextButton(onClick = { state.receipt = null }) { Text("Selesai") } },
        )
    }
}

@Composable
private fun Header(cashierName: String, shopName: String, lowStockCount: Int, onNotifications: () -> Unit, onAccount: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Image(painterResource(R.drawable.warung_matriks_logo), contentDescription = "Logo Warung Matriks", modifier = Modifier.size(43.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("Warung Matriks • $shopName", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Kasir: $cashierName", fontSize = 11.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onNotifications, modifier = Modifier.size(40.dp)) {
            BadgedBox(badge = { if (lowStockCount > 0) Badge { Text(if (lowStockCount > 99) "99+" else lowStockCount.toString()) } }) {
                Icon(Icons.Default.NotificationsNone, contentDescription = "Notifikasi stok: $lowStockCount barang", tint = Ink)
            }
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onAccount, modifier = Modifier.size(38.dp).background(PaleBlue, CircleShape)) {
            Icon(Icons.Default.PersonOutline, contentDescription = "Buka akun", tint = Blue, modifier = Modifier.size(22.dp))
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
private fun ProductCard(product: Product, canAdd: Boolean, modifier: Modifier = Modifier, selected: Int = 0, onAdd: () -> Unit) {
    Surface(modifier = modifier, color = Color.White, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(10.dp)) {
            Box(Modifier.fillMaxWidth().height(86.dp).clip(RoundedCornerShape(10.dp)).background(Background), contentAlignment = Alignment.Center) {
                ProductPhoto(product, modifier = Modifier.fillMaxSize().padding(4.dp))
                if (selected > 0) Surface(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp), color = PaleBlue, shape = RoundedCornerShape(7.dp)) {
                    Text("$selected dipilih", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(product.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Text(rupiah(product.price), color = Blue, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(5.dp))
            Text(if (product.stock == 0) "Stok habis" else "Stok ${product.stock} pcs", color = if (product.needsRestock()) Danger else Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onAdd, enabled = canAdd, modifier = Modifier.fillMaxWidth().height(36.dp), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(9.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (canAdd) Blue else Line)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(if (canAdd) " Tambah" else if (product.stock == 0) " Habis" else " Maks. stok", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
