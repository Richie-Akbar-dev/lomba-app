package id.warungmatriks.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StockReminderTest {
    @Test fun notifyOnlyNewLowStockTransitions() {
        assertEquals(setOf("a"), newStockAlerts(setOf("a"), emptySet()))
        assertEquals(emptySet<String>(), newStockAlerts(setOf("a"), setOf("a")))
        assertEquals(emptySet<String>(), newStockAlerts(emptySet(), setOf("a")))
        assertEquals(setOf("a"), newStockAlerts(setOf("a"), emptySet()))
        assertEquals(setOf("b"), newStockAlerts(setOf("a", "b"), setOf("a")))
    }
}
