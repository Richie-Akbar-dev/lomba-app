package id.warungmatriks.app.data

import androidx.annotation.DrawableRes
import id.warungmatriks.app.R
import java.text.NumberFormat
import java.util.Locale

data class Product(
    val id: String,
    val name: String,
    val price: Int,
    val cost: Int?,
    val stock: Int,
    @DrawableRes val image: Int? = null,
    val category: String = "Sembako",
    val minimumStock: Int = 0,
    val photoFile: String? = null,
    val barcode: String? = null,
    val active: Boolean = true,
) {
    // Local drawable IDs only; a missing image uses a universal package icon.
    @get:DrawableRes
    val imageResource: Int get() = image?.takeIf { it != 0 } ?: R.drawable.product_placeholder
}

object DemoData {
    val products = listOf(
        Product("indomie", "Indomie Goreng", 3500, 2700, 6, R.drawable.product_noodles),
        Product("water", "Le Minerale 600ml", 4000, 2600, 8, R.drawable.product_water),
        Product("oil", "Minyak Kita 1L", 16000, 14100, 2, R.drawable.product_oil),
        Product("eggs", "Telur Ayam 1/2 Kg", 15000, 12800, 10, R.drawable.product_eggs),
    )
    val initialCart = mapOf("indomie" to 3, "water" to 1, "eggs" to 1)
}

fun rupiah(amount: Int): String = rupiah(amount.toLong())
fun rupiah(amount: Long): String = "Rp" + NumberFormat.getNumberInstance(Locale("id", "ID")).format(amount)

data class CartTotals(val subtotal: Int, val grossProfit: Int?, val exceedsLimit: Boolean = false)

/** Unknown cost is never treated as zero. Values above the supported total must not be sold. */
fun calculateCartTotals(products: List<Product>, quantities: Map<String, Int>): CartTotals {
    val selected = products.filter { (quantities[it.id] ?: 0) > 0 }
    val limit = Int.MAX_VALUE.toLong() + 1
    val total = selected.fold(0L) { acc, item ->
        (acc + item.price.toLong() * (quantities[item.id] ?: 0)).coerceAtMost(limit)
    }
    val gross = if (selected.any { it.cost == null } || total >= limit) null else selected.sumOf {
        (it.price.toLong() - it.cost!!.toLong()) * (quantities[it.id] ?: 0)
    }
    return CartTotals(total.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(), gross?.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt(), total > Int.MAX_VALUE)
}

/** Scans use exact barcode matching; typed queries also support partial names and codes. */
internal fun Product.matchesSearch(query: String, exactBarcode: Boolean = false): Boolean {
    val search = query.trim()
    if (search.isEmpty()) return !exactBarcode
    return if (exactBarcode) barcode == search
    else name.contains(search, ignoreCase = true) || barcode?.contains(search, ignoreCase = true) == true
}

/** Category chips filter alongside the existing name/barcode search. Null selects all. */
internal fun Product.matchesCatalogFilter(query: String, exactBarcode: Boolean, category: String?): Boolean =
    matchesSearch(query, exactBarcode) && (category == null || this.category == category)

internal fun Product.needsRestock(): Boolean = active && stock <= minimumStock
