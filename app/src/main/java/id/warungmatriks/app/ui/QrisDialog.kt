package id.warungmatriks.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import id.warungmatriks.app.data.InsufficientStockException
import id.warungmatriks.app.data.ProductChangedException
import id.warungmatriks.app.data.QrisPayload
import id.warungmatriks.app.data.rupiah
import id.warungmatriks.app.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Showing the QR does not record the sale; Selesai records it without verifying payment. */
@Composable
internal fun QrisDialog(total: Int, source: String, onRecord: suspend () -> Unit, onRecorded: () -> Unit, onClose: () -> Unit) {
    var image by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var generating by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(source, total) {
        generating = true; image = null; error = null
        try {
            require(source.isNotBlank()) { "Atur kode QRIS statis terlebih dahulu di Akun → QRIS toko." }
            image = withContext(Dispatchers.Default) {
                val content = QrisPayload.withAmount(source, total)
                val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 480, 480, mapOf(EncodeHintType.MARGIN to 4))
                val pixels = IntArray(matrix.width * matrix.height) { i -> if (matrix[i % matrix.width, i / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
                Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
            }
        } catch (e: CancellationException) { throw e } catch (e: IllegalArgumentException) {
            error = e.message ?: "Kode QRIS tidak valid. Periksa pengaturan QRIS toko."
        } catch (_: Exception) { error = "QR belum dapat dibuat. Periksa pengaturan QRIS toko dan coba lagi." }
        finally { generating = false }
    }
    DisposableEffect(image) {
        val current = image
        onDispose { current?.recycle() }
    }
    fun requestClose() {
        if (!busy) {
            if (image != null) confirmCancel = true else onClose()
        }
    }
    AlertDialog(
        onDismissRequest = { requestClose() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = PaleBlue, shape = RoundedCornerShape(10.dp)) { Icon(Icons.Default.QrCode, null, tint = Blue, modifier = Modifier.padding(8.dp).size(20.dp)) }
                Spacer(Modifier.width(10.dp))
                Text("QRIS • ${rupiah(total)}", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = White,
        shape = RoundedCornerShape(18.dp),
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                if (generating) Text("Membuat QRIS...", fontSize = 12.sp, color = Muted)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                image?.let { Image(it.asImageBitmap(), contentDescription = "QRIS untuk ${rupiah(total)}", modifier = Modifier.sizeIn(maxWidth = 240.dp, maxHeight = 240.dp).fillMaxWidth().aspectRatio(1f)) }
            }
        },
        confirmButton = {
            TextButton(enabled = image != null && !generating && !busy, onClick = {
                busy = true; error = null
                scope.launch {
                    try {
                        // Do not interrupt an in-progress commit on screen recreation.
                        withContext(NonCancellable) { onRecord() }
                        onRecorded()
                    } catch (e: CancellationException) { throw e } catch (_: InsufficientStockException) {
                        error = "Stok tidak cukup. Batalkan QR dan periksa keranjang."
                    } catch (_: ProductChangedException) {
                        error = "Data barang berubah. Batalkan QR dan pilih barang lagi."
                    } catch (_: Exception) {
                        error = "Penjualan belum dapat disimpan. Periksa penyimpanan dan coba lagi."
                    } finally { busy = false }
                }
            }) { Text(if (busy) "Menyimpan..." else "Selesai") }
        },
        dismissButton = { TextButton(onClick = { requestClose() }, enabled = !busy) { Text("Batal") } },
    )
    if (confirmCancel) AlertDialog(
        onDismissRequest = { confirmCancel = false },
        title = { Text("Batalkan transaksi QRIS?") },
        text = { Text("QR sudah ditampilkan. Pelanggan mungkin sudah memindainya, tetapi penjualan belum dicatat. Periksa pembayaran sebelum membatalkan.") },
        confirmButton = { TextButton(onClick = { confirmCancel = false; onClose() }) { Text("Batalkan transaksi") } },
        dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Kembali") } },
    )
}
