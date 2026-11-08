package id.warungmatriks.app.data

import androidx.annotation.DrawableRes
import id.warungmatriks.app.R
import java.text.NumberFormat
import java.util.Locale

data class Product(
    val id: String,
    val name: String,
    val price: Int,
    val cost: Int,
    val stock: Int,
    @DrawableRes val image: Int? = null,
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

fun rupiah(amount: Int): String = "Rp" + NumberFormat.getNumberInstance(Locale("id", "ID")).format(amount)

data class CartTotals(val subtotal: Int, val grossProfit: Int)

/** Demo calculation: excludes operating expenses, taxes, and discounts. */
fun calculateCartTotals(products: List<Product>, quantities: Map<String, Int>): CartTotals =
    CartTotals(
        subtotal = products.sumOf { it.price * (quantities[it.id] ?: 0) },
        grossProfit = products.sumOf { (it.price - it.cost) * (quantities[it.id] ?: 0) },
    )
