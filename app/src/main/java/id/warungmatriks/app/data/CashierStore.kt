package id.warungmatriks.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class InsufficientStockException : Exception()
internal class ProductChangedException : Exception()
internal class StockAdditionException : Exception()
internal data class LocalReceivable(val customer: String, val remaining: Int)
internal data class CashierLine(val product: Product, val quantity: Int)
internal data class CheckoutResult(val total: Int, val received: Int, val change: Int, val debt: Int, val customer: String?)

/** One per-account local catalogue, stock and prototype transaction store. */
internal class CashierStore(context: Context, private val ownerId: String, databaseName: String = "cashier_prototype.db") :
    SQLiteOpenHelper(context.applicationContext, databaseName, null, 7) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE stock (owner_id TEXT NOT NULL, product_id TEXT NOT NULL, quantity INTEGER NOT NULL CHECK(quantity >= 0), PRIMARY KEY(owner_id, product_id))")
        db.execSQL("CREATE TABLE sales (id TEXT PRIMARY KEY NOT NULL, owner_id TEXT NOT NULL, created_at INTEGER NOT NULL, total INTEGER NOT NULL, received INTEGER NOT NULL, change_amount INTEGER NOT NULL, debt INTEGER NOT NULL, customer TEXT, payment_method TEXT NOT NULL DEFAULT 'cash')")
        createSaleItems(db)
        db.execSQL("CREATE TABLE cash_movements (sale_id TEXT PRIMARY KEY NOT NULL, owner_id TEXT NOT NULL, amount INTEGER NOT NULL, created_at INTEGER, direction TEXT NOT NULL DEFAULT 'in', category TEXT NOT NULL DEFAULT 'sale', note TEXT)")
        createQrisRecords(db)
        db.execSQL("CREATE TABLE receivables (sale_id TEXT PRIMARY KEY NOT NULL, owner_id TEXT NOT NULL, customer TEXT NOT NULL, amount_remaining INTEGER NOT NULL)")
        createProducts(db)
        createStockAdditions(db)
        upgradeCredit(db)
        addSaleCategory(db)
    }

    private fun addSaleCategory(db: SQLiteDatabase) {
        // Existing snapshots did not store historical categories; do not invent them.
        db.execSQL("ALTER TABLE sale_items ADD COLUMN category TEXT")
    }

    private fun upgradeCredit(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE receivables ADD COLUMN phone TEXT")
        db.execSQL("ALTER TABLE receivables ADD COLUMN due_at INTEGER")
        db.execSQL("ALTER TABLE receivables ADD COLUMN remind INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE receivables ADD COLUMN notified INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE credit_payments (id TEXT PRIMARY KEY NOT NULL, owner_id TEXT NOT NULL, sale_id TEXT NOT NULL, amount INTEGER NOT NULL, created_at INTEGER NOT NULL)")
    }

    private fun createQrisRecords(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE qris_records (sale_id TEXT PRIMARY KEY NOT NULL, owner_id TEXT NOT NULL, created_at INTEGER NOT NULL, amount INTEGER NOT NULL)")
    }

    private fun createStockAdditions(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE stock_additions (owner_id TEXT NOT NULL, id TEXT NOT NULL, product_id TEXT NOT NULL, quantity INTEGER NOT NULL CHECK(quantity > 0), before_quantity INTEGER NOT NULL, after_quantity INTEGER NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY(owner_id,id))")
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
            // Version 1 had stock but no product table. Restore only products with
            // an existing stock row, for every owner; never create new stock here.
            DemoData.products.forEach { product ->
                db.execSQL(
                    "INSERT OR IGNORE INTO products (owner_id, product_id, name, price, cost, category, minimum_stock, active) SELECT owner_id, product_id, ?, ?, ?, ?, ?, 1 FROM stock WHERE product_id = ?",
                    arrayOf(product.name, product.price, product.cost, product.category, product.minimumStock, product.id),
                )
            }
            // Preserve historical sale lines; nullable cost means unknown, not zero.
            db.execSQL("ALTER TABLE sale_items RENAME TO sale_items_v1")
            createSaleItems(db)
            db.execSQL("INSERT INTO sale_items (sale_id, product_id, product_name, quantity, price, cost) SELECT sale_id, product_id, product_name, quantity, price, cost FROM sale_items_v1")
            db.execSQL("DROP TABLE sale_items_v1")
        }
        if (oldVersion < 3) db.execSQL("ALTER TABLE sales ADD COLUMN payment_method TEXT NOT NULL DEFAULT 'cash'")
        if (oldVersion < 4) createStockAdditions(db)
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE cash_movements ADD COLUMN created_at INTEGER")
            db.execSQL("ALTER TABLE cash_movements ADD COLUMN direction TEXT NOT NULL DEFAULT 'in'")
            db.execSQL("ALTER TABLE cash_movements ADD COLUMN category TEXT NOT NULL DEFAULT 'sale'")
            db.execSQL("ALTER TABLE cash_movements ADD COLUMN note TEXT")
            // Preserve unknown dates if the corresponding sale has already expired.
            db.execSQL("UPDATE cash_movements SET created_at=(SELECT s.created_at FROM sales s WHERE s.id=cash_movements.sale_id AND s.owner_id=cash_movements.owner_id)")
            createQrisRecords(db)
            db.execSQL("INSERT INTO qris_records(sale_id,owner_id,created_at,amount) SELECT id,owner_id,created_at,total FROM sales WHERE payment_method='qris'")
        }
        if (oldVersion < 6) upgradeCredit(db)
        if (oldVersion < 7) addSaleCategory(db)
    }

    suspend fun products(): List<Product> = withContext(Dispatchers.IO) {
        val db = writableDatabase
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

    /** Add stock atomically and idempotently; this is not a purchase or cash expense. */
    suspend fun addStock(id: String, productId: String, quantity: Int): Int = withContext(Dispatchers.IO) {
        require(id.isNotBlank() && productId.isNotBlank() && quantity > 0)
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.rawQuery("SELECT product_id,quantity,after_quantity FROM stock_additions WHERE owner_id=? AND id=?", arrayOf(ownerId, id)).use { c ->
                if (c.moveToFirst()) {
                    require(c.getString(0) == productId && c.getInt(1) == quantity) { "ID penyesuaian sudah dipakai." }
                    val previousResult = c.getInt(2)
                    db.setTransactionSuccessful()
                    return@withContext previousResult
                }
            }
            val update = db.compileStatement("UPDATE stock SET quantity=quantity+? WHERE owner_id=? AND product_id=? AND quantity<=?")
            try {
                update.bindLong(1, quantity.toLong()); update.bindString(2, ownerId); update.bindString(3, productId)
                update.bindLong(4, Int.MAX_VALUE.toLong() - quantity.toLong())
                if (update.executeUpdateDelete() != 1) throw StockAdditionException()
            } finally { update.close() }
            val after = db.rawQuery("SELECT quantity FROM stock WHERE owner_id=? AND product_id=?", arrayOf(ownerId, productId)).use { c ->
                check(c.moveToFirst()); c.getInt(0)
            }
            db.execSQL("INSERT INTO stock_additions(owner_id,id,product_id,quantity,before_quantity,after_quantity,created_at) VALUES(?,?,?,?,?,?,?)", arrayOf(ownerId,id,productId,quantity,after-quantity,after,System.currentTimeMillis()))
            db.setTransactionSuccessful()
            after
        } finally { db.endTransaction() }
    }

    suspend fun stocks(): Map<String, Int> = products().associate { it.id to it.stock }

    suspend fun cashSummary(week: CashWeek): CashSummary = withContext(Dispatchers.IO) {
        require(week.start < week.end)
        readableDatabase.rawQuery("SELECT COALESCE(SUM(CASE WHEN direction='in' THEN amount ELSE 0 END),0), COALESCE(SUM(CASE WHEN direction='out' THEN amount ELSE 0 END),0) FROM cash_movements WHERE owner_id=? AND created_at>=? AND created_at<?", arrayOf(ownerId, week.start.toString(), week.end.toString())).use { c ->
            check(c.moveToFirst()); CashSummary(c.getLong(0), c.getLong(1))
        }
    }

    suspend fun cashMovements(direction: String, week: CashWeek?, offset: Int = 0, limit: Int = 25): CashMovementPage = withContext(Dispatchers.IO) {
        require(direction in setOf("in", "out") && offset >= 0 && limit in 1..100)
        val condition = if (week == null) "" else " AND created_at>=? AND created_at<?"
        val args = mutableListOf(ownerId, direction)
        if (week != null) { require(week.start < week.end); args += week.start.toString(); args += week.end.toString() }
        args += (limit + 1).toString(); args += offset.toString()
        val items = mutableListOf<CashMovement>()
        readableDatabase.rawQuery("SELECT sale_id,created_at,direction,category,amount,note FROM cash_movements WHERE owner_id=? AND direction=?$condition ORDER BY created_at IS NULL,created_at DESC,sale_id DESC LIMIT ? OFFSET ?", args.toTypedArray()).use { c ->
            while (c.moveToNext()) items += CashMovement(c.getString(0), if (c.isNull(1)) null else c.getLong(1), c.getString(2), c.getString(3), c.getInt(4), c.getString(5))
        }
        CashMovementPage(items.take(limit), items.size > limit)
    }

    /** Manual entries represent actual cash movement; changing denominations is not an entry. */
    suspend fun addManualCash(id: String, direction: String, category: String, amount: Int, note: String) = withContext(Dispatchers.IO) {
        require(id.isNotBlank() && amount > 0 && note.length <= 120)
        require((direction == "in" && category in setOf("initial", "deposit")) || (direction == "out" && category in setOf("purchase", "withdrawal", "other")))
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.rawQuery("SELECT owner_id,direction,category,amount,note FROM cash_movements WHERE sale_id=?", arrayOf(id)).use { c ->
                if (c.moveToFirst()) {
                    require(c.getString(0) == ownerId && c.getString(1) == direction && c.getString(2) == category && c.getInt(3) == amount && (c.getString(4) ?: "") == note.trim()) { "ID catatan sudah digunakan." }
                    db.setTransactionSuccessful()
                    return@withContext
                }
            }
            db.execSQL("INSERT INTO cash_movements(sale_id,owner_id,amount,created_at,direction,category,note) VALUES(?,?,?,?,?,?,?)", arrayOf(id,ownerId,amount,System.currentTimeMillis(),direction,category,note.trim().ifEmpty { null }))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    suspend fun receivables(): List<LocalReceivable> = withContext(Dispatchers.IO) {
        val result = mutableListOf<LocalReceivable>()
        readableDatabase.rawQuery("SELECT customer, amount_remaining FROM receivables WHERE owner_id = ? AND amount_remaining > 0 ORDER BY rowid DESC", arrayOf(ownerId)).use { cursor ->
            while (cursor.moveToNext()) result.add(LocalReceivable(cursor.getString(0), cursor.getInt(1)))
        }
        result
    }

    suspend fun creditDetails(): List<CreditDetail> = withContext(Dispatchers.IO) {
        val result = mutableListOf<CreditDetail>()
        readableDatabase.rawQuery("SELECT r.sale_id,r.customer,r.amount_remaining,r.phone,r.due_at,r.remind,s.total,s.received,s.created_at FROM receivables r JOIN sales s ON s.id=r.sale_id AND s.owner_id=r.owner_id WHERE r.owner_id=? AND r.amount_remaining>0 ORDER BY s.created_at DESC", arrayOf(ownerId)).use { c ->
            while (c.moveToNext()) result.add(CreditDetail(c.getString(0), c.getString(1), c.getInt(2), c.getString(3), if (c.isNull(4)) null else c.getLong(4), c.getInt(5) == 1, c.getInt(6), c.getInt(7), c.getLong(8)))
        }
        result
    }

    /** Idempotent payment and cash entry commit together. A completed credit leaves the active list. */
    suspend fun payCredit(paymentId: String, saleId: String, amount: Int): Int = withContext(Dispatchers.IO) {
        require(paymentId.isNotBlank() && amount > 0)
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.rawQuery("SELECT sale_id,amount FROM credit_payments WHERE id=? AND owner_id=?", arrayOf(paymentId,ownerId)).use { c ->
                if (c.moveToFirst()) {
                    require(c.getString(0) == saleId && c.getInt(1) == amount)
                    val remaining = db.rawQuery("SELECT amount_remaining FROM receivables WHERE sale_id=? AND owner_id=?", arrayOf(saleId,ownerId)).use { r -> if (r.moveToFirst()) r.getInt(0) else 0 }
                    db.setTransactionSuccessful()
                    return@withContext remaining
                }
            }
            val remaining = db.rawQuery("SELECT amount_remaining FROM receivables WHERE sale_id=? AND owner_id=?", arrayOf(saleId,ownerId)).use { c -> check(c.moveToFirst()) { "Kasbon tidak ditemukan atau sudah lunas." }; c.getInt(0) }
            require(amount <= remaining) { "Angsuran melebihi sisa kasbon." }
            val now = System.currentTimeMillis()
            db.execSQL("INSERT INTO credit_payments(id,owner_id,sale_id,amount,created_at) VALUES(?,?,?,?,?)", arrayOf(paymentId,ownerId,saleId,amount,now))
            db.execSQL("INSERT INTO cash_movements(sale_id,owner_id,amount,created_at,direction,category,note) VALUES(?,?,?,?,'in','credit',?)", arrayOf(paymentId,ownerId,amount,now,"Angsuran kasbon"))
            if (amount == remaining) db.execSQL("DELETE FROM receivables WHERE sale_id=? AND owner_id=?", arrayOf(saleId,ownerId))
            else db.execSQL("UPDATE receivables SET amount_remaining=amount_remaining-? WHERE sale_id=? AND owner_id=?", arrayOf(amount,saleId,ownerId))
            db.setTransactionSuccessful()
            remaining - amount
        } finally { db.endTransaction() }
    }

    /** Monthly figures are separate from the seven-day history list. */
    suspend fun report(now: Long = System.currentTimeMillis()): ReportSnapshot = withContext(Dispatchers.IO) {
        val db = writableDatabase
        HistoryRetention.clean(db, now, ownerId)
        val (start, end) = reportMonthWindow(now)
        val args = arrayOf(ownerId, start.toString(), end.toString())
        db.beginTransaction()
        try {
            val sales = LongArray(3)
            db.rawQuery("SELECT COALESCE(SUM(total),0),COALESCE(SUM(CASE WHEN payment_method='qris' THEN total ELSE 0 END),0),COUNT(*) FROM sales WHERE owner_id=? AND created_at>=? AND created_at<?", args).use { c -> c.moveToFirst(); for (i in 0..2) sales[i] = c.getLong(i) }
            val cash = LongArray(5)
            db.rawQuery("SELECT COALESCE(SUM(CASE WHEN direction='in' THEN amount ELSE 0 END),0),COALESCE(SUM(CASE WHEN direction='out' THEN amount ELSE 0 END),0),COALESCE(SUM(CASE WHEN direction='in' AND category='sale' THEN amount ELSE 0 END),0),COALESCE(SUM(CASE WHEN direction='in' AND category='credit' THEN amount ELSE 0 END),0),COALESCE(SUM(CASE WHEN direction='in' AND category IN ('initial','deposit') THEN amount ELSE 0 END),0) FROM cash_movements WHERE owner_id=? AND created_at>=? AND created_at<?", args).use { c -> c.moveToFirst(); for (i in 0..4) cash[i] = c.getLong(i) }
            var modal = 0L; var profit = 0L; var missing = 0L; var unknownCategory = 0L
            db.rawQuery("SELECT COALESCE(SUM(i.cost*i.quantity),0), COALESCE(SUM((i.price-i.cost)*i.quantity),0), COALESCE(SUM(CASE WHEN i.cost IS NULL THEN i.quantity ELSE 0 END),0), COALESCE(SUM(CASE WHEN i.category IS NULL THEN i.quantity ELSE 0 END),0) FROM sale_items i JOIN sales s ON s.id=i.sale_id WHERE s.owner_id=? AND s.created_at>=? AND s.created_at<?", args).use { c -> c.moveToFirst(); modal=c.getLong(0); profit=c.getLong(1); missing=c.getLong(2); unknownCategory=c.getLong(3) }
            val missingItems = db.rawQuery("SELECT COUNT(*) FROM sales s WHERE s.owner_id=? AND s.created_at>=? AND s.created_at<? AND NOT EXISTS (SELECT 1 FROM sale_items i WHERE i.sale_id=s.id)", args).use { c -> c.moveToFirst(); c.getLong(0) }
            val categories = mutableListOf<ReportCategory>()
            db.rawQuery("SELECT i.category, COALESCE(SUM((i.price-i.cost)*i.quantity),0), SUM(CASE WHEN i.cost IS NULL THEN i.quantity ELSE 0 END) FROM sale_items i JOIN sales s ON s.id=i.sale_id WHERE s.owner_id=? AND s.created_at>=? AND s.created_at<? AND i.category IS NOT NULL GROUP BY i.category ORDER BY 2 DESC", args).use { c -> while(c.moveToNext()) categories.add(ReportCategory(c.getString(0), if (c.getLong(2) == 0L) c.getLong(1) else null, c.getLong(2))) }
            val products = mutableListOf<ReportProduct>()
            db.rawQuery("SELECT MAX(i.product_name), SUM(i.quantity) FROM sale_items i JOIN sales s ON s.id=i.sale_id WHERE s.owner_id=? AND s.created_at>=? AND s.created_at<? GROUP BY i.product_id ORDER BY SUM(i.quantity) DESC, MAX(i.product_name) LIMIT 20", args).use { c -> while(c.moveToNext()) products.add(ReportProduct(c.getString(0),c.getLong(1))) }
            var morning=0L; var evening=0L
            db.rawQuery("SELECT created_at FROM sales WHERE owner_id=? AND created_at>=? AND created_at<?", args).use { c -> while (c.moveToNext()) { val cal=java.util.Calendar.getInstance().apply { timeInMillis=c.getLong(0) }; if (cal.get(java.util.Calendar.HOUR_OF_DAY) in 6..17) morning++ else evening++ } }
            val formatter = java.text.SimpleDateFormat("dd MMM", java.util.Locale("id", "ID"))
            val days = reportDayStarts(now, start).map { day ->
                val next = java.util.Calendar.getInstance().apply { timeInMillis=day; add(java.util.Calendar.DAY_OF_MONTH,1) }.timeInMillis.coerceAtMost(end)
                val dayArgs=arrayOf(ownerId,day.toString(),next.toString())
                val value=db.rawQuery("SELECT COALESCE(SUM(total),0) FROM sales WHERE owner_id=? AND created_at>=? AND created_at<?",dayArgs).use { c -> c.moveToFirst(); c.getLong(0) }
                val out=db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM cash_movements WHERE owner_id=? AND direction='out' AND created_at>=? AND created_at<?",dayArgs).use { c -> c.moveToFirst(); c.getLong(0) }
                ReportDay(day,formatter.format(java.util.Date(day)),value,out)
            }
            val inventory = mutableListOf<ReportInventory>()
            db.rawQuery("SELECT p.name,s.quantity,p.price,p.active FROM products p JOIN stock s ON s.owner_id=p.owner_id AND s.product_id=p.product_id WHERE p.owner_id=? ORDER BY p.name COLLATE NOCASE",arrayOf(ownerId)).use { c -> while(c.moveToNext()) inventory.add(ReportInventory(c.getString(0),c.getInt(1),c.getInt(2),c.getInt(3)==1)) }
            db.setTransactionSuccessful()
            ReportSnapshot(start,end,sales[0],sales[1],sales[2],cash[0],cash[1],cash[2],cash[3],cash[4],if(missing==0L && missingItems==0L) modal else null,if(missing==0L && missingItems==0L) profit else null,missing,missingItems,unknownCategory,categories,products,morning,evening,days,inventory)
        } finally { db.endTransaction() }
    }

    /** Reads only this owner's immutable transaction snapshots; filters describe the original payment. */
    suspend fun saleHistory(
        start: Long?, end: Long?, filter: SaleHistoryFilter, after: SaleCursor? = null, limit: Int = 25, now: Long = System.currentTimeMillis(),
    ): SaleHistoryPage = withContext(Dispatchers.IO) {
        require(limit in 1..100)
        HistoryRetention.clean(readableDatabase, now, ownerId)
        require((start == null && end == null) || (start != null && end != null && start < end))
        val conditions = mutableListOf("owner_id = ?", "(created_at > ? OR EXISTS (SELECT 1 FROM receivables r WHERE r.sale_id=sales.id AND r.owner_id=sales.owner_id AND r.amount_remaining>0))")
        val args = mutableListOf(ownerId, (now - HistoryRetention.RETENTION_MS).toString())
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

    suspend fun checkoutCredit(id: String, lines: List<CashierLine>, received: Int, customer: String, phone: String, dueAt: Long?, remind: Boolean): CheckoutResult {
        require(normalizeWhatsapp(phone) != null && customer.isNotBlank())
        require(lines.sumOf { it.product.price.toLong() * it.quantity } > received)
        require(!remind || dueAt != null)
        return recordSale(id, lines, received, customer, "cash", normalizeWhatsapp(phone), dueAt, remind)
    }

    // QR generation records a full-value sale, but does not verify that payment occurred.
    suspend fun checkoutQris(id: String, lines: List<CashierLine>): CheckoutResult =
        recordSale(id, lines, 0, "", "qris")

    private suspend fun recordSale(id: String, lines: List<CashierLine>, received: Int, customer: String, method: String, phone: String? = null, dueAt: Long? = null, remind: Boolean = false): CheckoutResult = withContext(Dispatchers.IO) {
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
            db.rawQuery("SELECT total, received, change_amount, debt, customer, payment_method FROM sales WHERE id = ? AND owner_id = ?", arrayOf(id, ownerId)).use { cursor ->
                if (cursor.moveToFirst()) {
                    check(cursor.getString(5) == method) { "ID transaksi sudah digunakan untuk metode lain." }
                    val existing = CheckoutResult(cursor.getInt(0), cursor.getInt(1), cursor.getInt(2), cursor.getInt(3), cursor.getString(4))
                    db.setTransactionSuccessful()
                    return@withContext existing
                }
            }
            lines.forEach { line ->
                db.rawQuery("SELECT name, price, cost, active, category FROM products WHERE owner_id=? AND product_id=?", arrayOf(ownerId, line.product.id)).use { c ->
                    if (!c.moveToFirst() || c.getString(0) != line.product.name || c.getInt(1) != line.product.price || (if (c.isNull(2)) null else c.getInt(2)) != line.product.cost || c.getInt(3) != 1 || c.getString(4) != line.product.category) throw ProductChangedException()
                }
                val update = db.compileStatement("UPDATE stock SET quantity = quantity - ? WHERE owner_id = ? AND product_id = ? AND quantity >= ?")
                try {
                    update.bindLong(1, line.quantity.toLong()); update.bindString(2, ownerId)
                    update.bindString(3, line.product.id); update.bindLong(4, line.quantity.toLong())
                    if (update.executeUpdateDelete() != 1) throw InsufficientStockException()
                } finally { update.close() }
            }
            val createdAt = System.currentTimeMillis()
            db.execSQL("INSERT INTO sales (id, owner_id, created_at, total, received, change_amount, debt, customer, payment_method) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", arrayOf(id, ownerId, createdAt, total, received, change, debt, if (debt > 0) name else null, method))
            lines.forEach { line ->
                db.execSQL("INSERT INTO sale_items (sale_id, product_id, product_name, quantity, price, cost, category) VALUES (?, ?, ?, ?, ?, ?, ?)", arrayOf(id, line.product.id, line.product.name, line.quantity, line.product.price, line.product.cost, line.product.category))
            }
            if (received > 0) db.execSQL("INSERT INTO cash_movements (sale_id, owner_id, amount, created_at, direction, category) VALUES (?, ?, ?, ?, 'in', 'sale')", arrayOf(id, ownerId, settlement.paid, createdAt))
            if (method == "qris") db.execSQL("INSERT INTO qris_records (sale_id, owner_id, created_at, amount) VALUES (?, ?, ?, ?)", arrayOf(id, ownerId, createdAt, total))
            if (debt > 0) db.execSQL("INSERT INTO receivables (sale_id, owner_id, customer, amount_remaining, phone, due_at, remind) VALUES (?, ?, ?, ?, ?, ?, ?)", arrayOf(id, ownerId, name, debt, phone, dueAt, if (remind) 1 else 0))
            db.setTransactionSuccessful()
            CheckoutResult(total, received, change, debt, if (debt > 0) name else null)
        } finally { db.endTransaction() }
    }
}
