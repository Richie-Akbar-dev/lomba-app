package id.warungmatriks.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CartTotalsTest {
    @Test
    fun initialCartHasExpectedTotalAndGrossProfit() {
        val totals = calculateCartTotals(DemoData.products, DemoData.initialCart)
        assertEquals(29_500, totals.subtotal)
        assertEquals(6_000, totals.grossProfit ?: -1)
    }

    @Test
    fun emptyCartHasZeroTotals() {
        val totals = calculateCartTotals(DemoData.products, emptyMap())
        assertEquals(0, totals.subtotal)
        assertEquals(0, totals.grossProfit ?: -1)
    }
    @Test
    fun unknownCostDoesNotPretendToBeZero() {
        val product = Product("unknown", "Sabun", 5000, null, 2)
        val totals = calculateCartTotals(listOf(product), mapOf("unknown" to 1))
        org.junit.Assert.assertNull(totals.grossProfit)
        assertEquals(5000, totals.subtotal)
    }

    @Test
    fun tooLargeTotalCannotBeUsedForCheckout() {
        val product = Product("large", "Barang", 999_999_999, null, 10)
        val totals = calculateCartTotals(listOf(product), mapOf("large" to 3))
        org.junit.Assert.assertTrue(totals.exceedsLimit)
    }

}
