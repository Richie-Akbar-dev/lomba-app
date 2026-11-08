package id.warungmatriks.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/** Uses an embedded offline scanner. Cancellation or denied permission leaves values unchanged. */
@Composable
internal fun rememberBarcodeScanner(onScanned: (String) -> Unit, onError: (String) -> Unit): () -> Unit {
    val latestScanned = rememberUpdatedState(onScanned)
    val latestError = rememberUpdatedState(onError)
    val launcher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.originalIntent?.getBooleanExtra(Intents.Scan.MISSING_CAMERA_PERMISSION, false) == true) {
            latestError.value("Izin kamera ditolak. Izinkan kamera atau masukkan barcode secara manual.")
        }
        result.contents?.let { contents ->
            val code = contents.trim()
            if (code.isEmpty() || code.length > 80) latestError.value("Barcode kosong atau terlalu panjang (maksimal 80 karakter).")
            else latestScanned.value(code)
        }
    }
    return {
        try {
            launcher.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.ONE_D_CODE_TYPES)
                .setPrompt("Arahkan kamera ke barcode barang").setBeepEnabled(false).setOrientationLocked(false))
        } catch (_: Exception) { latestError.value("Pemindai kamera belum tersedia. Masukkan barcode secara manual.") }
    }
}
