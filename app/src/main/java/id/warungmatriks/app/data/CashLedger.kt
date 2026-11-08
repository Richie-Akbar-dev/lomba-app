package id.warungmatriks.app.data

import java.util.Calendar
import java.util.TimeZone

internal data class CashWeek(val start: Long, val end: Long)
internal data class CashSummary(val cashIn: Long, val cashOut: Long) {
    val net: Long get() = cashIn - cashOut
}
internal data class CashMovement(val id: String, val timestamp: Long?, val direction: String, val category: String, val amount: Int, val note: String?)
internal data class CashMovementPage(val movements: List<CashMovement>, val hasMore: Boolean)

/** Device-local Monday 00:00 through the next Monday, with no carryover. */
internal fun cashWeek(now: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): CashWeek {
    val calendar = Calendar.getInstance(zone).apply { timeInMillis = now }
    val daysSinceMonday = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
    calendar.add(Calendar.DAY_OF_MONTH, -daysSinceMonday)
    calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
    val start = calendar.timeInMillis
    calendar.add(Calendar.DAY_OF_MONTH, 7)
    return CashWeek(start, calendar.timeInMillis)
}

internal fun cashCategoryLabel(category: String): String = when (category) {
    "sale" -> "Penjualan tunai"
    "initial" -> "Modal awal"
    "deposit" -> "Setoran tunai"
    "purchase" -> "Kulakan"
    "withdrawal" -> "Penarikan pemilik"
    "other" -> "Lainnya"
    else -> "Catatan kas"
}
