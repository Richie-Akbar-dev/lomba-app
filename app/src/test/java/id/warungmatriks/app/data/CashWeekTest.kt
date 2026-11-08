package id.warungmatriks.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.GregorianCalendar
import java.util.TimeZone

class CashWeekTest {
    private fun instant(zone: TimeZone, year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        GregorianCalendar(zone).apply { clear(); set(year, month - 1, day, hour, minute) }.timeInMillis

    @Test fun mondayStartsNewCalendarWeekWithoutCarryover() {
        val zone = TimeZone.getTimeZone("Asia/Jakarta")
        val monday = instant(zone, 2026, 11, 2, 0, 0)
        val nextMonday = instant(zone, 2026, 11, 9, 0, 0)
        assertEquals(CashWeek(monday, nextMonday), cashWeek(instant(zone, 2026, 11, 8, 23, 59), zone))
        assertEquals(CashWeek(nextMonday, instant(zone, 2026, 11, 16, 0, 0)), cashWeek(nextMonday, zone))
    }

    @Test fun weekUsesCalendarDaysAcrossDaylightSavingChange() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val week = cashWeek(instant(zone, 2026, 3, 8, 12, 0), zone)
        assertEquals(instant(zone, 2026, 3, 2, 0, 0), week.start)
        assertEquals(instant(zone, 2026, 3, 9, 0, 0), week.end)
        assertEquals(167L * 60 * 60 * 1000, week.end - week.start)
    }

    @Test fun negativeNetIsPossibleAfterWeeklyReset() {
        assertEquals(-5000L, CashSummary(0, 5000).net)
    }
}
