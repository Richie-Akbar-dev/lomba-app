package id.warungmatriks.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class InsufficientStockException : Exception()
internal class ProductChangedException : Exception()
internal data class LocalReceivable(val customer: String, val remaining: Int)
internal data class CashierLine(val product: Product, val quantity: Int)
internal data class CheckoutResult(val total: Int, val received: Int, val change: Int, val debt: Int, val customer: String?)

/** One per-account local catalogue, stock and prototype transaction store. */
internal class CashierStore(context: Context, private val ownerId: String, databaseName: String = "cashier_prototype.db") :
    SQLiteOpenHelper(context.applicationContext, databaseName, null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE stock (owner_id TEXT NOT NULL, product_id TEXT NOT NULL, quantity INTEGER NOT NULL CHECK(quantity >= 0), PRIMARY KEY(owner_id, product_id))")
        db.execSQL("CREATE TABLE sales (id TEXT PRIMARY KEY NOT NULL, owner_id TEXT NOT NULL, created_at INTEGER NOT NULL, total INTEGER NOT NULL, received INTEGER NOT NULL, change_amount INTEGER NOT NULL, debt INTEGER NOT NULL, customer TEXT, payment_method TEXT NOT NULL DEFAULT 'cash')")
        createSaleItems(db)
        db.execSQL("CREATE TABLE cash_movements (sale_id TEXT PRIMARY KEY NOT NULL, owner_id TEXT NOT NULL, amount INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE receivables (sale_id TEXT PRIMARY KEY NOT NULL, owner_id TEXT NOT NULL, customer TEXT NOT NULL, amount_remaining INTEGER NOT NULL)")
        createProducts(db)
    }

    private fun createSaleItems(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE sale_items (sale_id TEXT NOT NULL, product_id TEXT NOT NULL, product_name TEXT NOT NULL, quantity INTEGER NOT NULL, price INTEGER NOT NULL, cost INTEGER)")
    }
    private fun createProducts(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE products (owner_id TEXT NOT NULL, product_id TEXT NOT NULL, name TEXT NOT NULL, price INTEGER NOT NULL, cost INTEGER, category TEXT NOT NULL, minimum_stock INTEGER NOT NULL, photo_file TEXT, barcode TEXT, active INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(owner_id, product_id))")
        db.execSQL("CREATE UNIQUE INDEX products_barcode_per_owner ON products(owner_id, barcode) WHERE barcode IS NOT NULL")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createProducts(db)
            // Preserve historical sale lines; nullable cost means unknown, not zero.
            db.execSQL("ALTER TABLE sale_items RENAME TO sale_items_v1")
            createSaleItems(db)
            db.execSQL("INSERT INTO sale_items (sale_id, product_id, product_name, quantity, price, cost) SELECT sale_id, product_id, product_name, quantity, price, cost FROM sale_items_v1")
            db.execSQL("DROP TABLE sale_items_v1")
        }
        if (oldVersion < 3) db.execSQL("ALTER TABLE sales ADD COLUMN payment_method TEXT NOT NULL DEFAULT 'cash'")
    }

    private fun seed(db: SQLiteDatabase) {
        DemoData.products.forEach { product ->
            db.execSQL("INSERT OR IGNORE INTO products (owner_id, product_id, name, price, cost, category, minimum_stock, active) VALUES (?, ?, ?, ?, ?, ?, ?, 1)", arrayOf(ownerId, product.id, product.name, product.price, product.cost, product.category, product.minimumStock))
            db.execSQL("INSERT OR IGNORE INTO stock (owner_id, product_id, quantity) VALUES (?, ?, ?)", arrayOf(ownerId, product.id, product.stock))
        }
    }

    suspend fun products(): List<Product> = withContext(Dispatchers.IO) {
        val db = writableDatabase
        seed(db)
        val items = mutableListOf<Product>()
        db.rawQuery("SELECT p.product_id,p.name,p.price,p.cost,s.quantity,p.category,p.minimum_stock,p.photo_file,p.barcode,p.active FROM products p JOIN stock s ON p.owner_id=s.owner_id AND p.product_id=s.product_id WHERE p.owner_id=? ORDER BY p.name COLLATE NOCASE", arrayOf(ownerId)).use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                items.add(Product(id = id, name = c.getString(1), price = c.getInt(2), cost = if (c.isNull(3)) null else c.getInt(3), stock = c.getInt(4),
                    image = DemoData.products.firstOrNull { it.id == id }?.image, category = c.getString(5), minimumStock = c.getInt(6),
                    photoFile = c.getString(7), barcode = c.getString(8), active = c.getInt(9) == 1))
            }
        }
        items
    }

    suspend fun saveProduct(product: Product) = withContext(Dispatchers.IO) {
        require(product.id.isNotBlank() && product.name.isNotBlank() && product.category.isNotBlank())
        require(product.price > 0 && (product.cost == null || product.cost >= 0) && product.stock >= 0 && product.minimumStock >= 0)
        val db = writableDatabase
        db.beginTransaction()
        try {
            seed(db)
            db.execSQL("INSERT INTO products (owner_id, product_id, name, price, cost, category, minimum_stock, photo_file, barcode, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", arrayOf(ownerId, product.id, product.name.trim(), product.price, product.cost, product.category, product.minimumStock, product.photoFile, product.barcode?.trim()?.takeIf { it.isNotEmpty() }, if (product.active) 1 else 0))
            db.execSQL("INSERT INTO stock (owner_id, product_id, quantity) VALUES (?, ?, ?)", arrayOf(ownerId, product.id, product.stock))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    suspend fun updateProduct(product: Product) = withContext(Dispatchers.IO) {
        require(product.id.isNotBlank() && product.name.isNotBlank() && product.category.isNotBlank())
        require(product.price > 0 && (product.cost == null || product.cost >= 0) && product.minimumStock >= 0)
        val db = writableDatabase
        val update = db.compileStatement("UPDATE products SET name=?, price=?, cost=?, category=?, minimum_stock=?, photo_file=?, barcode=? WHERE owner_id=? AND product_id=?")
        try {
            update.bindString(1, product.name.trim()); update.bindLong(2, product.price.toLong())
            if (product.cost == null) update.bindNull(3) else update.bindLong(3, product.cost.toLong())
            update.bindString(4, product.category); update.bindLong(5, product.minimumStock.toLong())
            if (product.photoFile == null) update.bindNull(6) else update.bindString(6, product.photoFile)
            val barcode = product.barcode?.trim()?.takeIf { it.isNotEmpty() }
            if (barcode == null) update.bindNull(7) else update.bindString(7, barcode)
            update.bindString(8, ownerId); update.bindString(9, product.id)
            check(update.executeUpdateDelete() == 1) { "Barang tidak ditemukan." }
        } finally { update.close() }
    }

    suspend fun setProductActive(id: String, active: Boolean) = withContext(Dispatchers.IO) {
        val update = writableDatabase.compileStatement("UPDATE products SET active=? WHERE owner_id=? AND product_id=?")
        try {
            update.bindLong(1, if (active) 1L else 0L); update.bindString(2, ownerId); update.bindString(3, id)
            check(update.executeUpdateDelete() == 1) { "Barang tidak ditemukan." }
        } finally { update.close() }
    }

    suspend fun stocks(): Map<String, Int> = products().associate { it.id to it.stock }

    suspend fun receivables(): List<LocalReceivable> = withContext(Dispatchers.IO) {
        val result = mutableListOf<LocalReceivable>()
        readableDatabase.rawQuery("SELECT customer, amount_remaining FROM receivables WHERE owner_id = ? AND amount_remaining > 0 ORDER BY rowid DESC", arrayOf(ownerId)).use { cursor ->
            while (cursor.moveToNext()) result.add(LocalReceivable(cursor.getString(0), cursor.getInt(1)))
        }
        result
    }

    /** Reads only this owner's immutable transaction snapshots; filters describe the original payment. */
    suspend fun saleHistory(
        start: Long?, end: Long?, filter: SaleHistoryFilter, after: SaleCursor? = null, limit: Int = 25,
    ): SaleHistoryPage = withContext(Dispatchers.IO) {
        require(limit in 1..100)
        HistoryRetention.clean(readableDatabase, System.currentTimeMillis(), ownerId)
        require((start == null && end == null) || (start != null && end != null && start < end))
        val conditions = mutableListOf("owner_id = ?")
        val args = mutableListOf(ownerId)
        if (start != null && end != null) { conditions += "created_at >= ? AND created_at < ?"; args += start.toString(); args += end.toString() }
        when (filter) {
            SaleHistoryFilter.ALL -> Unit
            SaleHistoryFilter.PAID_AT_SALE -> conditions += "debt = 0 AND payment_method = 'cash'"
            SaleHistoryFilter.CREDIT_AT_SALE -> conditions += "debt > 0"
            SaleHistoryFilter.QRIS -> conditions += "payment_method = 'qris'"
        }
        if (after != null) {
            conditions += "(created_at < ? OR (created_at = ? AND id < ?))"
            args += after.createdAt.toString(); args += after.createdAt.toString(); args += after.id
        }
        args += (limit + 1).toString()
        val records = mutableListOf<SaleRecord>()
        readableDatabase.rawQuery("SELECT id,created_at,total,received,change_amount,debt,customer,payment_method FROM sales WHERE ${conditions.joinToString(" AND ")} ORDER BY created_at DESC,id DESC LIMIT ?", args.toTypedArray()).use { c ->
            while (c.moveToNext()) records += SaleRecord(c.getString(0), c.getLong(1), c.getInt(2), c.getInt(3), c.getInt(4), c.getInt(5), c.getString(6), c.getString(7))
        }
        val page = records.take(limit)
        SaleHistoryPage(page, if (records.size > limit) page.last().let { SaleCursor(it.createdAt, it.id) } else null)
    }

    suspend fun saleHistorySummary(start: Long, end: Long): SaleHistorySummary = withContext(Dispatchers.IO) {
        require(start < end)
        HistoryRetention.clean(readableDatabase, System.currentTimeMillis(), ownerId)
        readableDatabase.rawQuery("SELECT COUNT(*), COALESCE(SUM(total),0), COALESCE(SUM(received-change_amount),0), COALESCE(SUM(debt),0), COALESCE(SUM(CASE WHEN payment_method='qris' THEN total ELSE 0 END),0) FROM sales WHERE owner_id=? AND created_at>=? AND created_at<?", arrayOf(ownerId, start.toString(), end.toString())).use { c ->
            check(c.moveToFirst())
            SaleHistorySummary(c.getLong(0), c.getLong(1), c.getLong(2), c.getLong(3), c.getLong(4))
        }
    }

    suspend fun saleItemSnapshots(saleId: String): List<SaleItemSnapshot> = withContext(Dispatchers.IO) {
        val items = mutableListOf<SaleItemSnapshot>()
        readableDatabase.rawQuery("SELECT i.product_name,i.quantity,i.price,i.cost FROM sale_items i JOIN sales s ON s.id=i.sale_id WHERE s.owner_id=? AND s.id=? ORDER BY i.rowid", arrayOf(ownerId, saleId)).use { c ->
            while (c.moveToNext()) items += SaleItemSnapshot(c.getString(0), c.getInt(1), c.getInt(2), if (c.isNull(3)) null else c.getInt(3))
        }
        items
    }

    suspend fun checkout(id: String, lines: List<CashierLine>, received: Int, customer: String): CheckoutResult =
        recordSale(id, lines, received, customer, "cash")

    // QR generation records a full-value sale, but does not verify that payment occurred.
    suspend fun checkoutQris(id: String, lines: List<CashierLine>): CheckoutResult =
        recordSale(id, lines, 0, "", "qris")

    private suspend fun recordSale(id: String, lines: List<CashierLine>, received: Int, customer: String, method: String): CheckoutResult = withContext(Dispatchers.IO) {
        require(method == "cash" || method == "qris")
        require(lines.isNotEmpty() && received >= 0 && lines.all { it.quantity > 0 })
        require(lines.map { it.product.id }.distinct().size == lines.size)
        val sum = lines.sumOf { it.product.price.toLong() * it.quantity }
        require(sum > 0L && sum <= Int.MAX_VALUE.toLong())
        val total = sum.toInt()
        val settlement = if (method == "cash") calculateSettlement(total, received) else Settlement(paid = 0, change = 0, debt = 0)
        val debt = settlement.debt
        val change = settlement.change
        val name = customer.trim()
        require(debt == 0 || name.isNotEmpty())
        val db = writableDatabase
        db.beginTransaction()
        try {
            seed(db)
            db.rawQuery("SELECT total, received, change_amount, debt, customer, payment_method FROM sales WHERE id = ? AND owner_id = ?", arrayOf(id, ownerId)).use { cursor ->
                if (cursor.moveToFirst()) {
                    check(cursor.getString(5) == method) { "ID transaksi sudah digunakan untuk metode lain." }
                    val existing = CheckoutResult(cursor.getInt(0), cursor.getInt(1), cursor.getInt(2), cursor.getInt(3), cursor.getString(4))
                    db.setTransactionSuccessful()
                    return@withContext existing
                }
            }
            lines.forEach { line ->
                db.rawQuery("SELECT name, price, cost, active FROM products WHERE owner_id=? AND product_id=?", arrayOf(ownerId, line.product.id)).use { c ->
                    if (!c.moveToFirst() || c.getString(0) != line.product.name || c.getInt(1) != line.product.price || (if (c.isNull(2)) null else c.getInt(2)) != line.product.cost || c.getInt(3) != 1) throw ProductChangedException()
                }
                val update = db.compileStatement("UPDATE stock SET quantity = quantity - ? WHERE owner_id = ? AND product_id = ? AND quantity >= ?")
                try {
                    update.bindLong(1, line.quantity.toLong()); update.bindString(2, ownerId)
                    update.bindString(3, line.product.id); update.bindLong(4, line.quantity.toLong())
                    if (update.executeUpdateDelete() != 1) throw InsufficientStockException()
                } finally { update.close() }
            }
            db.execSQL("INSERT INTO sales (id, owner_id, created_at, total, received, change_amount, debt, customer, payment_method) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", arrayOf(id, ownerId, System.currentTimeMillis(), total, received, change, debt, if (debt > 0) name else null, method))
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
