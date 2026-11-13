package id.warungmatriks.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class CreditDueDateTest {
    @Test fun showsDashOrDayMonthYear() {
        assertEquals("-", creditDueDateText(null))
        val date = Calendar.getInstance().apply {
            clear()
            set(2025, Calendar.JANUARY, 15, 14, 30)
        }
        assertEquals("15 Jan 2025", creditDueDateText(date.timeInMillis))
    }
}
