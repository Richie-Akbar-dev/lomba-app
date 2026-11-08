package id.warungmatriks.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class InsufficientStockException : Exception()

internal data class LocalReceivable(val customer: String, val remaining: Int)

internal data class CashierLine(val product: Product, val quantity: Int)
internal data class CheckoutResult(val total: Int, val received: Int, val change: Int, val debt: Int, val customer: String?)

/** Local prototype store. Only the cashier screen reads these records for now. */
internal class CashierStore(context: Context, private val ownerId: String) :
    SQLiteOpenHelper(context.applicationContext, "cashier_prototype.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE stock (owner_id TEXT NOT NULL, product_id TEXT NOT NULL, quantity INTEGER NOT NULL CHECK(quantity >= 0), PRIMARY KEY(owner_id, product_id))")
        db.execSQL("CREATE TABLE sales (id TEXT PRIMARY KEY NOT NULL, owner_id TEXT NOT NULL, created_at INTEGER NOT NULL, total INTEGER NOT NULL, received INTEGER NOT NULL, change_amount INTEGER NOT NULL, debt INTEGER NOT NULL, customer TEXT)")
        db.execSQL("CREATE TABLE sale_items (sale_id TEXT NOT NULL, product_id TEXT NOT NULL, product_name TEXT NOT NULL, quantity INTEGER NOT NULL, price INTEGER NOT NULL, cost INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE cash_movements (sale_id TEXT PRIMARY KEY NOT NULL, owner_id TEXT NOT NULL, amount INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE receivables (sale_id TEXT PRIMARY KEY NOT NULL, owner_id TEXT NOT NULL, customer TEXT NOT NULL, amount_remaining INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private fun seed(db: SQLiteDatabase) {
        DemoData.products.forEach { product ->
            db.execSQL("INSERT OR IGNORE INTO stock (owner_id, product_id, quantity) VALUES (?, ?, ?)", arrayOf(ownerId, product.id, product.stock))
        }
    }

    suspend fun stocks(): Map<String, Int> = withContext(Dispatchers.IO) {
        val db = writableDatabase
        seed(db)
        val result = mutableMapOf<String, Int>()
        db.rawQuery("SELECT product_id, quantity FROM stock WHERE owner_id = ?", arrayOf(ownerId)).use { cursor ->
            while (cursor.moveToNext()) result[cursor.getString(0)] = cursor.getInt(1)
        }
        result
    }

    suspend fun receivables(): List<LocalReceivable> = withContext(Dispatchers.IO) {
        val result = mutableListOf<LocalReceivable>()
        readableDatabase.rawQuery("SELECT customer, amount_remaining FROM receivables WHERE owner_id = ? AND amount_remaining > 0 ORDER BY rowid DESC", arrayOf(ownerId)).use { cursor ->
            while (cursor.moveToNext()) result.add(LocalReceivable(cursor.getString(0), cursor.getInt(1)))
        }
        result
    }

    suspend fun checkout(id: String, lines: List<CashierLine>, received: Int, customer: String): CheckoutResult = withContext(Dispatchers.IO) {
        require(lines.isNotEmpty() && received >= 0)
        require(lines.all { it.quantity > 0 && DemoData.products.any { p -> p.id == it.product.id && p.price == it.product.price && p.cost == it.product.cost } })
        require(lines.map { it.product.id }.distinct().size == lines.size)
        val sum = lines.sumOf { it.product.price.toLong() * it.quantity }
        require(sum > 0L && sum <= Int.MAX_VALUE.toLong())
        val total = sum.toInt()
        val settlement = calculateSettlement(total, received)
        val debt = settlement.debt
        val change = settlement.change
        val name = customer.trim()
        require(debt == 0 || name.isNotEmpty())
        val db = writableDatabase
        db.beginTransaction()
        try {
            seed(db)
            db.rawQuery("SELECT total, received, change_amount, debt, customer FROM sales WHERE id = ? AND owner_id = ?", arrayOf(id, ownerId)).use { cursor ->
                if (cursor.moveToFirst()) {
                    val existing = CheckoutResult(cursor.getInt(0), cursor.getInt(1), cursor.getInt(2), cursor.getInt(3), cursor.getString(4))
                    db.setTransactionSuccessful()
                    return@withContext existing
                }
            }
            lines.forEach { line ->
                val update = db.compileStatement("UPDATE stock SET quantity = quantity - ? WHERE owner_id = ? AND product_id = ? AND quantity >= ?")
                try {
                    update.bindLong(1, line.quantity.toLong()); update.bindString(2, ownerId)
                    update.bindString(3, line.product.id); update.bindLong(4, line.quantity.toLong())
                    if (update.executeUpdateDelete() != 1) throw InsufficientStockException()
                } finally { update.close() }
            }
            db.execSQL("INSERT INTO sales (id, owner_id, created_at, total, received, change_amount, debt, customer) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", arrayOf(id, ownerId, System.currentTimeMillis(), total, received, change, debt, if (debt > 0) name else null))
            lines.forEach { line ->
                db.execSQL("INSERT INTO sale_items (sale_id, product_id, product_name, quantity, price, cost) VALUES (?, ?, ?, ?, ?, ?)", arrayOf(id, line.product.id, line.product.name, line.quantity, line.product.price, line.product.cost))
            }
            if (received > 0) db.execSQL("INSERT INTO cash_movements (sale_id, owner_id, amount) VALUES (?, ?, ?)", arrayOf(id, ownerId, settlement.paid))
            if (debt > 0) db.execSQL("INSERT INTO receivables (sale_id, owner_id, customer, amount_remaining) VALUES (?, ?, ?, ?)", arrayOf(id, ownerId, name, debt))
            db.setTransactionSuccessful()
            CheckoutResult(total, received, change, debt, if (debt > 0) name else null)
        } finally { db.endTransaction() }
    }
}
