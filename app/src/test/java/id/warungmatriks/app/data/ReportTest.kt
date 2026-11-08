package id.warungmatriks.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ReportTest {
    @Test fun cutoffUsesLocalFirstOfMonth() {
        val zone = TimeZone.getTimeZone("Asia/Jakarta")
        val cal = Calendar.getInstance(zone).apply { clear(); set(2025, Calendar.FEBRUARY, 8, 16, 0) }
        val expected = Calendar.getInstance(zone).apply { clear(); set(2025, Calendar.FEBRUARY, 1, 0, 0) }.timeInMillis
        assertEquals(expected, HistoryRetention.monthStart(cal.timeInMillis, zone))
    }
}
