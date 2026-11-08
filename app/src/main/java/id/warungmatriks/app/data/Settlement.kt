package id.warungmatriks.app.data

internal data class Settlement(val paid: Int, val change: Int, val debt: Int)

internal fun calculateSettlement(total: Int, received: Int): Settlement {
    require(total > 0 && received >= 0)
    return Settlement(paid = minOf(total, received), change = (received - total).coerceAtLeast(0), debt = (total - received).coerceAtLeast(0))
}
