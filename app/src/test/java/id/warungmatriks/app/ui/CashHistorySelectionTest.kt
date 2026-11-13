package id.warungmatriks.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CashHistorySelectionTest {
    @Test fun selectsSwitchesAndClearsHistoryFilter() {
        assertEquals("in", cashHistorySelection(null, "in"))
        assertEquals("out", cashHistorySelection("in", "out"))
        assertEquals("in", cashHistorySelection("out", "in"))
        assertNull(cashHistorySelection("in", "in"))
        assertNull(cashHistorySelection("out", "out"))
    }
}
