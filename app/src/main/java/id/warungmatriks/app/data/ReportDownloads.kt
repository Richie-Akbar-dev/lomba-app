package id.warungmatriks.app.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Publishes only a fully written PDF. Older Android versions use the document picker. */
@RequiresApi(Build.VERSION_CODES.Q)
internal suspend fun saveReportToDownloads(context: Context, pdf: File): String = withContext(Dispatchers.IO) {
    val name = "Laporan-Warung-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}-${UUID.randomUUID()}.pdf"
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Warung Matriks")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: throw IllegalStateException("Lokasi unduhan tidak tersedia.")
    try {
        val output = resolver.openOutputStream(uri) ?: throw IllegalStateException("File unduhan tidak dapat dibuka.")
        output.use { out -> pdf.inputStream().use { it.copyTo(out) } }
        check(resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) == 1)
    } catch (e: Exception) {
        try { resolver.delete(uri, null, null) } catch (_: Exception) { }
        throw e
    }
    name
}
