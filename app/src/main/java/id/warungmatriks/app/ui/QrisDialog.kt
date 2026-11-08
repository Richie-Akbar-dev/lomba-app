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
import id.warungmatriks.app.data.rupiah
import id.warungmatriks.app.ui.theme.Muted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Displays an offline QR only. Creating/scanning it does not mark a payment or save a sale. */
@Composable
internal fun QrisDialog(total: Int, source: String, onSourceChange: (String) -> Unit, onClose: () -> Unit) {
    var image by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("QRIS • ${rupiah(total)}") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Masukkan teks payload QRIS statis milik toko (bukan foto QR). Data digunakan di perangkat dan tidak dikirim oleh aplikasi.", fontSize = 12.sp, color = Muted, lineHeight = 18.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = source, onValueChange = { onSourceChange(it.take(2048)); image = null; error = null }, label = { Text("Payload QRIS statis") }, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 160.dp), maxLines = 4)
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    busy = true; error = null; image = null
                    scope.launch {
                        try {
                            image = withContext(Dispatchers.Default) {
                                val content = QrisPayload.withAmount(source, total)
                                val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 480, 480, mapOf(EncodeHintType.MARGIN to 4))
                                val pixels = IntArray(matrix.width * matrix.height) { i -> if (matrix[i % matrix.width, i / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
                                Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
                            }
                        } catch (e: CancellationException) { throw e } catch (e: IllegalArgumentException) { error = e.message ?: "Payload QRIS tidak valid." } catch (_: Exception) { error = "QR belum dapat dibuat. Periksa payload QRIS." } finally { busy = false }
                    }
                }, enabled = source.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Membuat QR..." else "Tampilkan QR") }
                error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                image?.let { Spacer(Modifier.height(12.dp)); Image(it.asImageBitmap(), contentDescription = "QRIS untuk ${rupiah(total)}", modifier = Modifier.sizeIn(maxWidth = 240.dp, maxHeight = 240.dp).fillMaxWidth().aspectRatio(1f)) }
                Spacer(Modifier.height(12.dp))
                Text("QR hanya menampilkan nominal, bukan bukti pembayaran. Tidak ada konfirmasi pembayaran atau transaksi QRIS yang dicatat. Periksa kompatibilitas dengan penyedia QRIS sebelum digunakan.", fontSize = 12.sp, color = Muted, lineHeight = 18.sp, textAlign = TextAlign.Start)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Tutup") } },
    )
}
