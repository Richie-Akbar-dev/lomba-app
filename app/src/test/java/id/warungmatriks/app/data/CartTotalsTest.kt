package id.warungmatriks.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CartTotalsTest {
    @Test
    fun initialCartHasExpectedTotalAndGrossProfit() {
        val totals = calculateCartTotals(DemoData.products, DemoData.initialCart)
        assertEquals(29_500, totals.subtotal)
        assertEquals(6_000, totals.grossProfit)
    }

    @Test
    fun emptyCartHasZeroTotals() {
        val totals = calculateCartTotals(DemoData.products, emptyMap())
        assertEquals(0, totals.subtotal)
        assertEquals(0, totals.grossProfit)
    }
}
