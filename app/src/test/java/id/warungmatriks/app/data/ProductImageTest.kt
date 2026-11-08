package id.warungmatriks.app.data

import id.warungmatriks.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductImageTest {
    @Test
    fun missingImageUsesPackagePlaceholder() {
        val product = Product("demo", "Barang contoh", 1000, 800, 1)
        assertEquals(R.drawable.product_placeholder, product.imageResource)
    }

    @Test
    fun existingImageIsKept() {
        val product = Product("demo", "Barang contoh", 1000, 800, 1, R.drawable.product_oil)
        assertEquals(R.drawable.product_oil, product.imageResource)
    }
}
