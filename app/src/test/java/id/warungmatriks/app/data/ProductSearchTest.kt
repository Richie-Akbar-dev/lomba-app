package id.warungmatriks.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductSearchTest {
    private val product = Product("one", "Sabun Mandi", 5000, null, 5, barcode = "0012345678905", minimumStock = 5)

    @Test fun manualSearchMatchesNamesOrPartialBarcode() {
        assertTrue(product.matchesSearch(" mandi "))
        assertTrue(product.matchesSearch("12345"))
        assertTrue(product.matchesSearch(""))
        assertFalse(product.matchesSearch("lainnya"))
    }

    @Test fun scanMatchesOnlyTheCompleteBarcodeIncludingLeadingZeroes() {
        assertTrue(product.matchesSearch("0012345678905", exactBarcode = true))
        assertFalse(product.matchesSearch("12345678905", exactBarcode = true))
        assertFalse(product.matchesSearch("Sabun Mandi", exactBarcode = true))
        assertFalse(product.matchesSearch("", exactBarcode = true))
    }

    @Test fun categoryAndSearchFilterTogether() {
        assertTrue(product.matchesCatalogFilter("mandi", false, null))
        assertTrue(product.matchesCatalogFilter("12345", false, "Sembako"))
        assertFalse(product.matchesCatalogFilter("mandi", false, "Minuman"))
        assertFalse(product.matchesCatalogFilter("lainnya", false, "Sembako"))
        assertTrue(product.matchesCatalogFilter("0012345678905", true, null))
        assertFalse(product.matchesCatalogFilter("12345678905", true, null))
    }

    @Test fun lowStockIncludesThresholdOnlyForActiveProducts() {
        assertTrue(product.needsRestock())
        assertTrue(product.copy(stock = 4).needsRestock())
        assertFalse(product.copy(stock = 6).needsRestock())
        assertFalse(product.copy(active = false).needsRestock())
    }
}
