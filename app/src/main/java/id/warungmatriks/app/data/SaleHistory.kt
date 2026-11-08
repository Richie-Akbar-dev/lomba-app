package id.warungmatriks.app.data

internal enum class SaleHistoryFilter { ALL, PAID_AT_SALE, CREDIT_AT_SALE }
internal data class SaleCursor(val createdAt: Long, val id: String)
internal data class SaleRecord(
    val id: String,
    val createdAt: Long,
    val total: Int,
    val received: Int,
    val change: Int,
    val initialDebt: Int,
    val customer: String?,
) {
    val cashApplied: Int get() = received - change
}
internal data class SaleHistoryPage(val sales: List<SaleRecord>, val next: SaleCursor?)
internal data class SaleHistorySummary(val count: Long, val total: Long, val cashApplied: Long, val initialDebt: Long)
internal data class SaleItemSnapshot(val productName: String, val quantity: Int, val price: Int, val cost: Int?)

/** No estimate is shown for an empty item list, unknown historical cost, or overflow. */
internal fun grossProfitForSale(items: List<SaleItemSnapshot>): Long? {
    if (items.isEmpty() || items.any { it.cost == null }) return null
    return try {
        items.fold(0L) { total, item ->
            Math.addExact(total, Math.multiplyExact(item.price.toLong() - item.cost!!.toLong(), item.quantity.toLong()))
        }
    } catch (_: ArithmeticException) { null }
}
