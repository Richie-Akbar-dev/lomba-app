package id.warungmatriks.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import id.warungmatriks.app.data.QrisPayload
import id.warungmatriks.app.data.InsufficientStockException
import id.warungmatriks.app.data.ProductChangedException
import id.warungmatriks.app.data.rupiah
import id.warungmatriks.app.ui.theme.Muted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** QR creation records a sale, but never verifies an actual payment. */
@Composable
internal fun QrisDialog(total: Int, source: String, onSourceChange: (String) -> Unit, onRecord: suspend () -> Unit, onClose: () -> Unit) {
    var image by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    var showReminder by remember { mutableStateOf(false) }
    LaunchedEffect(showReminder) { if (showReminder) { delay(5_000); showReminder = false } }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("QRIS • ${rupiah(total)}") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Payload QRIS statis dari pengaturan akun terisi otomatis jika tersedia. Perubahan di sini hanya untuk transaksi ini dan tidak mengubah kode tersimpan.", fontSize = 12.sp, color = Muted, lineHeight = 18.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = source, onValueChange = { onSourceChange(it.take(2048)); image = null; error = null }, label = { Text("Payload QRIS statis") }, enabled = !busy && !recorded, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 160.dp), maxLines = 4)
                Spacer(Modifier.height(12.dp))
                Text("Saat QR berhasil dibuat, penjualan langsung dicatat dan stok berkurang, meskipun pembayaran belum diverifikasi.", fontSize = 12.sp, color = Muted)
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    busy = true; error = null; image = null
                    scope.launch {
                        try {
                            val generated = withContext(Dispatchers.Default) {
                                val content = QrisPayload.withAmount(source, total)
                                val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 480, 480, mapOf(EncodeHintType.MARGIN to 4))
                                val pixels = IntArray(matrix.width * matrix.height) { i -> if (matrix[i % matrix.width, i / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
                                Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
                            }
                            try {
                                // A completed database commit must not be abandoned midway on screen recreation.
                                withContext(NonCancellable) { onRecord() }
                            } catch (e: Exception) { generated.recycle(); throw e }
                            recorded = true
                            image = generated
                            showReminder = true
                        } catch (e: CancellationException) { throw e } catch (_: InsufficientStockException) { error = "Stok tidak cukup. Tutup QR dan periksa keranjang." } catch (_: ProductChangedException) { error = "Data barang berubah. Tutup QR dan pilih barang lagi." } catch (e: IllegalArgumentException) { error = e.message ?: "Payload QRIS tidak valid." } catch (_: Exception) { error = "QR atau penjualan belum dapat disimpan. Periksa penyimpanan dan coba lagi." } finally { busy = false }
                    }
                }, enabled = source.isNotBlank() && !busy && !recorded, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Membuat QR dan mencatat..." else if (recorded) "Penjualan sudah dicatat" else "Tampilkan QR & catat penjualan") }
                error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                image?.let { Spacer(Modifier.height(12.dp)); Image(it.asImageBitmap(), contentDescription = "QRIS untuk ${rupiah(total)}", modifier = Modifier.sizeIn(maxWidth = 240.dp, maxHeight = 240.dp).fillMaxWidth().aspectRatio(1f)) }
                Spacer(Modifier.height(12.dp))
                Text("Penjualan QRIS akan dicatat tanpa kasbon saat QR dibuat. QR bukan bukti pembayaran dan aplikasi tidak memverifikasi keberhasilannya. Periksa kompatibilitas dengan penyedia QRIS sebelum digunakan.", fontSize = 12.sp, color = Muted, lineHeight = 18.sp, textAlign = TextAlign.Start)
            }
        },
        confirmButton = { TextButton(onClick = onClose, enabled = !busy) { Text("Tutup") } },
    )
    if (showReminder) AlertDialog(
        onDismissRequest = { showReminder = false }, title = { Text("Transaksi QRIS dicatat") },
        text = { Text("Silakan periksa dan verifikasi pembayaran pada aplikasi penyedia QRIS.") },
        confirmButton = { TextButton(onClick = { showReminder = false }) { Text("Mengerti") } },
    )
}
