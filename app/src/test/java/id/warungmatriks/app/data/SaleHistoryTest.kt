package id.warungmatriks.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SaleHistoryTest {
    @Test fun grossProfitUsesHistoricalCosts() {
        val items = listOf(
            SaleItemSnapshot("Indomie", 3, 3500, 2700),
            SaleItemSnapshot("Air", 1, 4000, 2600),
            SaleItemSnapshot("Telur", 1, 15000, 12800),
        )
        assertEquals(6000L, grossProfitForSale(items) ?: -1L)
    }

    @Test fun missingCostsOrItemsDoNotInventProfit() {
        assertNull(grossProfitForSale(listOf(SaleItemSnapshot("Barang", 1, 5000, null))))
        assertNull(grossProfitForSale(emptyList()))
        val extreme = SaleItemSnapshot("Extreme", Int.MAX_VALUE, 0, Int.MAX_VALUE)
        assertNull(grossProfitForSale(listOf(extreme, extreme, extreme)))
    }
}
