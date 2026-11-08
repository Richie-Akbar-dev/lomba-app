package id.warungmatriks.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import id.warungmatriks.app.R
import id.warungmatriks.app.data.Product
import id.warungmatriks.app.data.ProductPhotoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ProductPhoto(product: Product, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val photo by produceState<ImageBitmap?>(null, product.photoFile) {
        value = product.photoFile?.let { filename ->
            withContext(Dispatchers.IO) {
                try {
                    val path = ProductPhotoStorage.file(context, filename).path
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(path, bounds)
                    var sample = 1
                    while (bounds.outWidth / sample > 256 || bounds.outHeight / sample > 256) sample *= 2
                    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
                } catch (_: Exception) { null }
            }
        }
    }
    if (photo != null) Image(photo!!, contentDescription = null, modifier = modifier)
    else Image(painterResource(if (product.photoFile == null) product.imageResource else R.drawable.product_placeholder), contentDescription = null, modifier = modifier)
}
