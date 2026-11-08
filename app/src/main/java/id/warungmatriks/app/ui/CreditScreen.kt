package id.warungmatriks.app.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import id.warungmatriks.app.ui.theme.WarungMatriksTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.warungmatriks.app.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

internal data class PendingCredit(val id: String, val lines: List<CashierLine>, val received: Int) {
    val total: Int get() = lines.sumOf { it.product.price.toLong() * it.quantity }.toInt()
}
private fun dateText(time: Long): String = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(time))

@Composable
internal fun CreditScreen(ownerId: String, pending: PendingCredit?, onBack: () -> Unit, onSaved: (CheckoutResult) -> Unit) {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val store = remember(context, ownerId) { CashierStore(context, ownerId) }
    val scope = rememberCoroutineScope()
    var name by remember(pending?.id) { mutableStateOf("") }
    var phone by remember(pending?.id) { mutableStateOf("") }
    var due by remember(pending?.id) { mutableStateOf<Long?>(null) }
    var remind by remember(pending?.id) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var credits by remember { mutableStateOf<List<CreditDetail>>(emptyList()) }
    var creditItems by remember { mutableStateOf<Map<String, List<SaleItemSnapshot>>>(emptyMap()) }
    var revision by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<CreditDetail?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    BackHandler(enabled = busy) { }
    LaunchedEffect(store, revision, inspection) {
        if (inspection) return@LaunchedEffect
        try { val loaded = store.creditDetails(); credits = loaded; creditItems = loaded.associate { it.saleId to store.saleItemSnapshots(it.saleId) } } catch (e: CancellationException) { throw e } catch (_: Exception) { error = "Kasbon belum dapat dimuat." }
    }
    DemoPage("Kasbon Pelanggan", if (pending != null) "Lengkapi data pelanggan" else "Kasbon belum lunas", onBack = { if (!busy) onBack() }, badgeText = "DATA LOKAL") {
        if (pending != null) {
            GroupTitle("Catat kasbon pelanggan")
            DemoCard {
                Text("Barang", fontWeight = FontWeight.Bold)
                pending.lines.forEach { Text("${it.product.name} × ${it.quantity} • ${rupiah(it.product.price.toLong() * it.quantity)}") }
                Spacer(Modifier.height(8.dp))
                InfoLine("Total belanja", rupiah(pending.total))
                InfoLine("Dibayar di awal", rupiah(pending.received))
                InfoLine("Sisa kasbon", rupiah(pending.total - pending.received))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(name, { name = it.take(80) }, label = { Text("Nama pembeli (wajib)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(phone, { phone = it.take(22) }, label = { Text("Nomor WhatsApp (wajib)") }, placeholder = { Text("08... atau +62...") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(enabled = !busy, onClick = {
                    val cal = Calendar.getInstance().apply { timeInMillis = due ?: System.currentTimeMillis() }
                    DatePickerDialog(context, { _, y, m, d ->
                        cal.set(Calendar.YEAR, y); cal.set(Calendar.MONTH, m); cal.set(Calendar.DAY_OF_MONTH, d)
                        TimePickerDialog(context, { _, h, minute ->
                            cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, minute); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                            due = cal.timeInMillis
                        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                }) { Text(due?.let { "Batas: ${dateText(it)}" } ?: "Pilih batas pelunasan (opsional)") }
                if (due != null) {
                    TextButton(enabled = !busy, onClick = { due = null; remind = false }) { Text("Hapus batas waktu") }
                    Row { Checkbox(checked = remind, onCheckedChange = { remind = it }, enabled = !busy); Text("Ingatkan saat jatuh tempo") }
                }
                DemoNote("Kembali sebelum menyimpan membatalkan transaksi; stok dan kas belum berubah.")
                Spacer(Modifier.height(10.dp))
                Button(enabled = !busy && name.isNotBlank() && normalizeWhatsapp(phone) != null && (due == null || due!! > System.currentTimeMillis()), modifier = Modifier.fillMaxWidth(), onClick = {
                    val checkedDue = due
                    if (checkedDue != null && checkedDue <= System.currentTimeMillis()) { error = "Batas pelunasan harus di masa depan."; return@Button }
                    if (remind && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    busy = true
                    scope.launch {
                        try {
                            val result = withContext(NonCancellable) { store.checkoutCredit(pending.id, pending.lines, pending.received, name.trim(), phone, checkedDue, remind) }
                            if (remind) try { scheduleCreditReminders(context) } catch (_: Exception) { /* Committed; startup will retry scheduling. */ }
                            onSaved(result)
                        } catch (e: CancellationException) { throw e } catch (_: InsufficientStockException) { error = "Stok berubah atau tidak cukup. Kembali ke Kasir dan periksa keranjang." } catch (_: ProductChangedException) { error = "Data barang berubah. Kembali ke Kasir dan periksa keranjang." } catch (_: Exception) { error = "Transaksi belum dapat disimpan. Periksa data dan coba lagi." } finally { busy = false }
                    }
                }) { Text(if (busy) "Menyimpan..." else "Simpan Kasbon") }
                if (phone.isNotBlank() && normalizeWhatsapp(phone) == null) Text("Masukkan nomor WhatsApp yang valid.", color = MaterialTheme.colorScheme.error)
                if (due != null && due!! <= System.currentTimeMillis()) Text("Batas pelunasan harus di masa depan.", color = MaterialTheme.colorScheme.error)
            }
            SectionGap()
        }
        GroupTitle("Total kasbon belum lunas")
        DemoCard { Text(rupiah(credits.sumOf { it.remaining.toLong() }), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall); Text("${credits.size} transaksi") }
        SectionGap(); GroupTitle("Riwayat kasbon berjalan")
        if (credits.isEmpty()) DemoNote("Belum ada kasbon yang belum lunas.")
        credits.forEach { credit ->
            DemoCard {
                Text(credit.customer, fontWeight = FontWeight.Bold)
                Text("Sisa: ${rupiah(credit.remaining)} • Total belanja: ${rupiah(credit.total)}")
                Text("Dibayar di awal: ${rupiah(credit.received)}")
                creditItems[credit.saleId]?.forEach { Text("${it.productName} × ${it.quantity}") }
                credit.dueAt?.let { Text("Batas: ${dateText(it)}") }
                Text(credit.phone?.let { "+$it" } ?: "Nomor WhatsApp tidak tersedia (data lama)")
                Row {
                    TextButton(enabled = !busy, onClick = { selected = credit }) { Text("Potong / Angsuran") }
                    TextButton(enabled = !busy && credit.phone != null, onClick = {
                        try {
                            val message = creditMessage(credit.customer, credit.remaining, credit.dueAt?.let(::dateText))
                            val uri = Uri.parse("https://wa.me/${credit.phone}?text=${Uri.encode(message)}")
                            try { context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp")) }
                            catch (_: android.content.ActivityNotFoundException) { context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp.w4b")) }
                        } catch (_: Exception) { error = "WhatsApp tidak tersedia. Pasang aplikasi WhatsApp untuk mengirim pesan." }
                    }) { Text("Tagih WA") }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
    selected?.let { credit ->
        var amount by remember(credit.saleId) { mutableStateOf("") }
        var capped by remember(credit.saleId) { mutableStateOf(false) }
        val paymentId = remember(credit.saleId) { UUID.randomUUID().toString() }
        AlertDialog(onDismissRequest = { if (!busy) selected = null }, title = { Text("Angsuran ${credit.customer}") }, text = {
            Column {
                Text("Sisa kasbon: ${rupiah(credit.remaining)}")
                OutlinedTextField(amount, { raw ->
                    val digits = raw.filter { it in '0'..'9' }.take(18)
                    val value = digits.toLongOrNull() ?: 0L
                    capped = value > credit.remaining
                    amount = if (capped) credit.remaining.toString() else digits
                }, label = { Text("Nominal angsuran") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, enabled = !busy)
                if (capped) Text("Nominal disesuaikan dengan sisa kasbon.")
            }
        }, confirmButton = { TextButton(enabled = !busy && (amount.toIntOrNull() ?: 0) > 0, onClick = {
            busy = true
            scope.launch {
                try {
                    withContext(NonCancellable) { store.payCredit(paymentId, credit.saleId, amount.toInt()) }
                    selected = null; revision++
                } catch (e: CancellationException) { throw e } catch (_: Exception) { error = "Angsuran gagal disimpan. Sisa kasbon mungkin telah berubah; muat ulang."; selected = null; revision++ }
                finally { busy = false }
            }
        }) { Text(if (busy) "Menyimpan..." else "Simpan") } }, dismissButton = { TextButton(enabled = !busy, onClick = { selected = null }) { Text("Batal") } })
    }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text("Informasi") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text("Tutup") } }) }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun CreditScreenPreview() {
    WarungMatriksTheme { CreditScreen("preview", null, {}, {}) }
}
