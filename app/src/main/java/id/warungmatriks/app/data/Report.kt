package id.warungmatriks.app.data

import java.util.Calendar

internal data class ReportProduct(val name: String, val quantity: Long)
internal data class ReportCategory(val name: String, val profit: Long?, val missingCost: Long)
internal data class ReportDay(val start: Long, val label: String, val sales: Long, val cashOut: Long)
internal data class ReportInventory(val name: String, val stock: Int, val price: Int, val active: Boolean)
internal data class ReportSnapshot(
    val start: Long, val end: Long, val sales: Long, val qris: Long, val count: Long,
    val cashIn: Long, val cashOut: Long, val salesCash: Long, val installmentCash: Long, val deposits: Long,
    val modal: Long?, val profit: Long?, val missingCost: Long, val missingItems: Long,
    val unknownCategory: Long, val categories: List<ReportCategory>, val bestProducts: List<ReportProduct>,
    val morning: Long, val evening: Long, val days: List<ReportDay>, val inventory: List<ReportInventory>,
) {
    val activeStockValue: Long get() = inventory.filter { it.active }.sumOf { it.stock.toLong() * it.price }
}
internal fun reportMonthWindow(now: Long): Pair<Long, Long> {
    val start = HistoryRetention.monthStart(now)
    return start to (now + 1)
}
internal fun reportDayStarts(now: Long, monthStart: Long): List<Long> {
    val cal = Calendar.getInstance().apply {
        timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_MONTH, -6)
    }
    return (0..6).map { val t = cal.timeInMillis; cal.add(Calendar.DAY_OF_MONTH, 1); t }.filter { it >= monthStart }
}
