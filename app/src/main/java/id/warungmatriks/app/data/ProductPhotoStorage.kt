package id.warungmatriks.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

internal object ProductPhotoStorage {
    private fun imageDirectory(context: Context) = File(context.filesDir, "product_photos")
    fun file(context: Context, name: String): File = File(imageDirectory(context), File(name).name)

    fun cameraUri(context: Context): Uri {
        val dir = File(context.cacheDir, "product_camera").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun deleteCamera(context: Context, uri: Uri) {
        if (uri.authority == "${context.packageName}.fileprovider") {
            uri.lastPathSegment?.let { File(File(context.cacheDir, "product_camera"), File(it).name).delete() }
        }
    }

    // Decode and re-encode to private storage; do not keep gallery URI permissions or metadata.
    suspend fun import(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Foto tidak dapat dibaca." }
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: throw IllegalArgumentException("Foto tidak dapat dibaca.")
        val orientation = try { resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } } catch (_: Exception) { null }
        val degrees = when (orientation) { ExifInterface.ORIENTATION_ROTATE_90 -> 90f; ExifInterface.ORIENTATION_ROTATE_180 -> 180f; ExifInterface.ORIENTATION_ROTATE_270 -> 270f; else -> 0f }
        val normalized = if (degrees == 0f) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
        val dir = imageDirectory(context).apply { check(exists() || mkdirs()) }
        val name = "${UUID.randomUUID()}.jpg"
        val output = File(dir, name)
        try {
            output.outputStream().use { check(normalized.compress(Bitmap.CompressFormat.JPEG, 85, it)) }
        } catch (e: Exception) { output.delete(); throw e }
        finally { if (normalized !== bitmap) normalized.recycle(); bitmap.recycle() }
        name
    }

    fun delete(context: Context, name: String?) { if (name != null) file(context, name).delete() }
}
