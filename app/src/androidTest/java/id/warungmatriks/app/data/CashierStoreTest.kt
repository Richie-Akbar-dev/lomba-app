package id.warungmatriks.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CashierStoreTest {
    @Test fun newCatalogStaysEmptyAndExistingProductsArePreserved() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "empty-catalog-" + UUID.randomUUID() + ".db"
        val owner = CashierStore(context, "owner", dbName)
        val other = CashierStore(context, "other", dbName)
        try {
            assertTrue(owner.products().isEmpty())
            owner.report()
            assertTrue(owner.products().isEmpty())
            val existing = DemoData.products.first().copy(name = "Barang lama diubah", stock = 4)
            owner.saveProduct(existing)
            owner.updateProduct(existing.copy(price = 4500))
            owner.report()
            assertEquals(listOf("Barang lama diubah"), owner.products().map { it.name })
            assertEquals(4500, owner.products().single().price)
            assertEquals(4, owner.products().single().stock)
            assertTrue(other.products().isEmpty())
        } finally { owner.close(); other.close(); context.deleteDatabase(dbName) }
    }

    @Test fun displayNumbersAreStableAccountScopedAndWrap() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "numbers-" + UUID.randomUUID() + ".db"
        val owner = CashierStore(context, "owner", dbName)
        val other = CashierStore(context, "other", dbName)
        try {
            val item = Product("item", "Barang", 5000, 3000, 6)
            owner.saveProduct(item); other.saveProduct(item)
            owner.checkout("one", listOf(CashierLine(item, 1)), 5000, "")
            owner.checkout("one", listOf(CashierLine(item, 1)), 5000, "")
            other.checkout("other", listOf(CashierLine(item, 1)), 5000, "")
            owner.checkout("two", listOf(CashierLine(item, 1)), 5000, "")
            assertEquals(mapOf("one" to 1, "two" to 2), owner.saleHistory(null, null, SaleHistoryFilter.ALL).sales.associate { it.id to it.displayNumber })
            assertEquals(1, other.saleHistory(null, null, SaleHistoryFilter.ALL).sales.single().displayNumber)
            owner.writableDatabase.execSQL("UPDATE sale_number_counters SET last_number=999999 WHERE owner_id='owner'")
            owner.checkout("wrapped", listOf(CashierLine(item, 1)), 5000, "")
            assertEquals(1, owner.saleHistory(null, null, SaleHistoryFilter.ALL).sales.first { it.id == "wrapped" }.displayNumber)
            assertEquals(1, owner.saleHistory(null, null, SaleHistoryFilter.ALL).sales.first { it.id == "one" }.displayNumber)
        } finally { owner.close(); other.close(); context.deleteDatabase(dbName) }
    }

    @Test fun failedSaleDoesNotConsumeNumberOrStock() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "number-rollback-" + UUID.randomUUID() + ".db"
        val store = CashierStore(context, "owner", dbName)
        try {
            val item = Product("item", "Barang", 5000, 3000, 3)
            store.saveProduct(item)
            store.writableDatabase.execSQL("CREATE TRIGGER fail_sale BEFORE INSERT ON sales BEGIN SELECT RAISE(ABORT, 'test failure'); END")
            try { store.checkout("failed", listOf(CashierLine(item, 1)), 5000, ""); fail("Sale must fail") } catch (_: android.database.sqlite.SQLiteException) { }
            assertEquals(3, store.stocks().getValue(item.id))
            store.writableDatabase.execSQL("DROP TRIGGER fail_sale")
            store.checkout("success", listOf(CashierLine(item, 1)), 5000, "")
            assertEquals(1, store.saleHistory(null, null, SaleHistoryFilter.ALL).sales.single().displayNumber)
        } finally { store.close(); context.deleteDatabase(dbName) }
    }

    @Test fun upgradeV7NumbersExistingSalesAndPreservesInternalIds() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "numbers-v7-" + UUID.randomUUID() + ".db"
        val before = CashierStore(context, "owner", dbName)
        val item = Product("item", "Barang", 5000, 3000, 5)
        before.saveProduct(item)
        before.checkout("old-a", listOf(CashierLine(item, 1)), 5000, "")
        before.checkout("old-b", listOf(CashierLine(item, 1)), 5000, "")
        val now = System.currentTimeMillis()
        before.writableDatabase.execSQL("UPDATE sales SET created_at=? WHERE id='old-a'", arrayOf(now - 2000))
        before.writableDatabase.execSQL("UPDATE sales SET created_at=? WHERE id='old-b'", arrayOf(now - 1000))
        downgradeNumbersToV7(before); before.writableDatabase.version = 7; before.close()
        val after = CashierStore(context, "owner", dbName)
        try {
            assertEquals(mapOf("old-a" to 1, "old-b" to 2), after.saleHistory(null, null, SaleHistoryFilter.ALL).sales.associate { it.id to it.displayNumber })
            after.checkout("new", listOf(CashierLine(item, 1)), 5000, "")
            assertEquals(3, after.saleHistory(null, null, SaleHistoryFilter.ALL).sales.first { it.id == "new" }.displayNumber)
            assertEquals(1, after.saleItemSnapshots("old-a").size)
        } finally { after.close(); context.deleteDatabase(dbName) }
    }

    @Test fun dateSummaryUsesVisibleSalesAndOutstandingIsCurrent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "range-" + UUID.randomUUID() + ".db"
        val owner = CashierStore(context, "owner", dbName)
        val other = CashierStore(context, "other", dbName)
        try {
            val item = Product("item", "Barang", 5000, 3000, 6)
            owner.saveProduct(item); other.saveProduct(item)
            owner.checkout("old-paid", listOf(CashierLine(item, 1)), 5000, "")
            owner.checkout("old-credit", listOf(CashierLine(item, 1)), 0, "Nama")
            owner.checkout("recent", listOf(CashierLine(item, 1)), 5000, "")
            other.checkout("other-credit", listOf(CashierLine(item, 1)), 0, "Lain")
            val now = System.currentTimeMillis()
            owner.writableDatabase.execSQL("UPDATE sales SET created_at=? WHERE id IN ('old-paid','old-credit')", arrayOf(now - 8L * 24 * 60 * 60 * 1000))
            val start = now - 10L * 24 * 60 * 60 * 1000
            val end = now + 60000
            val visible = owner.saleHistory(start, end, SaleHistoryFilter.ALL, now = now).sales
            val summary = owner.visibleSaleHistorySummary(start, end, now)
            assertEquals(setOf("old-credit", "recent"), visible.map { it.id }.toSet())
            assertEquals(visible.size.toLong(), summary.count)
            assertEquals(visible.sumOf { it.total.toLong() }, summary.total)
            assertEquals(5000L, owner.outstandingCreditTotal())
            owner.payCredit("part", "old-credit", 1000)
            assertEquals(4000L, owner.outstandingCreditTotal())
            assertEquals(5000L, other.outstandingCreditTotal())
        } finally { owner.close(); other.close(); context.deleteDatabase(dbName) }
    }

    @Test fun partialPaymentSavesDebtAndDeduplicatesRetry() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = CashierStore(context, "test-" + UUID.randomUUID())
        val product = DemoData.products.first()
        store.saveProduct(product)
        val before = store.stocks().getValue(product.id)
        val id = UUID.randomUUID().toString()
        val lines = listOf(CashierLine(product, 3))
        val result = store.checkout(id, lines, 4_000, "Bu RT")
        assertEquals(10_500, result.total)
        assertEquals(6_500, result.debt)
        assertEquals(before - 3, store.stocks().getValue(product.id))
        assertEquals(listOf(LocalReceivable("Bu RT", 6_500)), store.receivables())
        store.checkout(id, lines, 4_000, "Bu RT")
        assertEquals(before - 3, store.stocks().getValue(product.id))
        assertEquals(1, store.receivables().size)
        store.close()
    }

    @Test fun zeroCashCreatesFullDebtForNamedCustomer() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = CashierStore(context, "test-" + UUID.randomUUID())
        val product = DemoData.products.first()
        store.saveProduct(product)
        val result = store.checkout(UUID.randomUUID().toString(), listOf(CashierLine(product, 1)), 0, "Pak Doni")
        assertEquals(product.price, result.debt)
        assertEquals(0, result.received)
        assertEquals(listOf(LocalReceivable("Pak Doni", product.price)), store.receivables())
        store.close()
    }

    @Test fun fullDebtRequiresNameAndInsufficientStockDoesNotCreateDebt() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = CashierStore(context, "test-" + UUID.randomUUID())
        val product = DemoData.products.first()
        store.saveProduct(product)
        val before = store.stocks().getValue(product.id)
        try {
            store.checkout(UUID.randomUUID().toString(), listOf(CashierLine(product, 1)), 0, "  ")
            fail("Missing customer must be rejected")
        } catch (_: IllegalArgumentException) { }
        try {
            store.checkout(UUID.randomUUID().toString(), listOf(CashierLine(product, before + 1)), 0, "Bu RT")
            fail("Insufficient stock must be rejected")
        } catch (_: InsufficientStockException) { }
        assertEquals(before, store.stocks().getValue(product.id))
        assertTrue(store.receivables().isEmpty())
        store.close()
    }
    @Test fun addedProductWithUnknownCostCanBeSold() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "catalog-" + UUID.randomUUID() + ".db"
        val store = CashierStore(context, "owner", dbName)
        try {
            val product = Product("item-1", "Sabun", 5000, null, 4, category = "Sembako", minimumStock = 1)
            store.saveProduct(product)
            val saved = store.products().first { it.id == product.id }
            assertNull(saved.cost)
            assertEquals(4, saved.stock)
            store.checkout(UUID.randomUUID().toString(), listOf(CashierLine(saved, 1)), 5000, "")
            assertEquals(3, store.stocks().getValue(product.id))
            val cursor = store.readableDatabase.rawQuery("SELECT cost FROM sale_items WHERE product_id=?", arrayOf(product.id))
            cursor.use { assertTrue(it.moveToFirst()); assertTrue(it.isNull(0)) }
        } finally { store.close(); context.deleteDatabase(dbName) }
    }

    @Test fun upgradeKeepsExistingStockAndSaleItems() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-" + UUID.randomUUID() + ".db"
        val db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        try {
            db.execSQL("CREATE TABLE stock (owner_id TEXT NOT NULL, product_id TEXT NOT NULL, quantity INTEGER NOT NULL, PRIMARY KEY(owner_id, product_id))")
            db.execSQL("CREATE TABLE sales (id TEXT PRIMARY KEY NOT NULL, owner_id TEXT NOT NULL, created_at INTEGER NOT NULL, total INTEGER NOT NULL, received INTEGER NOT NULL, change_amount INTEGER NOT NULL, debt INTEGER NOT NULL, customer TEXT)")
            db.execSQL("CREATE TABLE sale_items (sale_id TEXT NOT NULL, product_id TEXT NOT NULL, product_name TEXT NOT NULL, quantity INTEGER NOT NULL, price INTEGER NOT NULL, cost INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE cash_movements (sale_id TEXT PRIMARY KEY NOT NULL, owner_id TEXT NOT NULL, amount INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE receivables (sale_id TEXT PRIMARY KEY NOT NULL, owner_id TEXT NOT NULL, customer TEXT NOT NULL, amount_remaining INTEGER NOT NULL)")
            db.execSQL("INSERT INTO stock VALUES ('owner','indomie',3)")
            db.execSQL("INSERT INTO stock VALUES ('other','water',4)")
            db.execSQL("INSERT INTO sales VALUES ('old','owner',123,10500,4000,0,6500,'Bu RT')")
            db.execSQL("INSERT INTO sale_items VALUES ('old','indomie','Indomie Goreng',3,3500,2700)")
            db.execSQL("INSERT INTO cash_movements VALUES ('old','owner',4000)")
            db.execSQL("INSERT INTO receivables VALUES ('old','owner','Bu RT',6500)")
            db.version = 1
        } finally { db.close() }
        val store = CashierStore(context, "owner", dbName)
        try {
            assertEquals(mapOf("indomie" to 3), store.stocks())
            val other = CashierStore(context, "other", dbName)
            try { assertEquals(mapOf("water" to 4), other.stocks()) } finally { other.close() }
            assertEquals("old", store.saleHistory(null, null, SaleHistoryFilter.ALL).sales.single().id)
            assertEquals(6500, store.receivables().single().remaining)
            assertEquals("Indomie Goreng", store.saleItemSnapshots("old").single().productName)
            store.readableDatabase.rawQuery("SELECT price,cost FROM sale_items WHERE sale_id='old'", null).use {
                assertTrue(it.moveToFirst()); assertEquals(3500, it.getInt(0)); assertEquals(2700, it.getInt(1))
            }
        } finally { store.close(); context.deleteDatabase(dbName) }
    }

    @Test fun changedPriceAndInactiveProductPreservePreviousSale() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "edits-" + UUID.randomUUID() + ".db"
        val store = CashierStore(context, "owner", dbName)
        try {
            val product = Product("custom", "Sabun", 5000, 3000, 4, category = "Sembako")
            store.saveProduct(product)
            store.checkout("sale-before-edit", listOf(CashierLine(product, 1)), 5000, "")
            val changed = product.copy(price = 6000, cost = 4000)
            store.updateProduct(changed)
            store.readableDatabase.rawQuery("SELECT price,cost FROM sale_items WHERE sale_id='sale-before-edit'", null).use {
                assertTrue(it.moveToFirst()); assertEquals(5000, it.getInt(0)); assertEquals(3000, it.getInt(1))
            }
            store.setProductActive(product.id, false)
            assertTrue(!store.products().first { it.id == product.id }.active)
            try {
                store.checkout("inactive-sale", listOf(CashierLine(changed, 1)), 6000, "")
                fail("Inactive products cannot be sold")
            } catch (_: ProductChangedException) { }
            assertEquals(3, store.stocks().getValue(product.id))
            store.setProductActive(product.id, true)
            assertTrue(store.products().first { it.id == product.id }.active)
        } finally { store.close(); context.deleteDatabase(dbName) }
    }

    @Test fun historyIsScopedPaginatedAndUsesOriginalSnapshots() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "history-" + UUID.randomUUID() + ".db"
        val owner = CashierStore(context, "owner", dbName)
        val other = CashierStore(context, "other", dbName)
        try {
            val product = Product("item", "Sabun lama", 5000, 3000, 5)
            owner.saveProduct(product); other.saveProduct(product)
            owner.checkout("owner-a", listOf(CashierLine(product, 1)), 10000, "")
            owner.checkout("owner-b", listOf(CashierLine(product, 1)), 0, "Bu RT")
            other.checkout("other-a", listOf(CashierLine(product, 1)), 5000, "")
            val timestamp = System.currentTimeMillis()
            owner.writableDatabase.execSQL("UPDATE sales SET created_at=?", arrayOf(timestamp))
            owner.updateProduct(product.copy(name = "Sabun baru", price = 6000, cost = 4000))
            owner.setProductActive(product.id, false)

            val first = owner.saleHistory(timestamp, timestamp + 1, SaleHistoryFilter.ALL, limit = 1)
            assertEquals(listOf("owner-b"), first.sales.map { it.id })
            assertTrue(first.next != null)
            val second = owner.saleHistory(timestamp, timestamp + 1, SaleHistoryFilter.ALL, first.next, 1)
            assertEquals(listOf("owner-a"), second.sales.map { it.id })
            assertNull(second.next)
            assertEquals(listOf("owner-a"), owner.saleHistory(null, null, SaleHistoryFilter.PAID_AT_SALE).sales.map { it.id })
            assertEquals(listOf("owner-b"), owner.saleHistory(null, null, SaleHistoryFilter.CREDIT_AT_SALE).sales.map { it.id })
            assertTrue(owner.saleHistory(timestamp + 1, timestamp + 1000, SaleHistoryFilter.ALL).sales.isEmpty())
            assertEquals(SaleHistorySummary(2, 10000, 5000, 5000), owner.saleHistorySummary(timestamp, timestamp + 1))
            assertEquals(5000, owner.saleHistory(null, null, SaleHistoryFilter.PAID_AT_SALE).sales.single().cashApplied)
            assertEquals(SaleItemSnapshot("Sabun lama", 1, 5000, 3000), owner.saleItemSnapshots("owner-a").single())
            assertTrue(other.saleItemSnapshots("owner-a").isEmpty())
            assertEquals(listOf("other-a"), other.saleHistory(null, null, SaleHistoryFilter.ALL).sales.map { it.id })
        } finally { owner.close(); other.close(); context.deleteDatabase(dbName) }
    }

    @Test fun qrisRecordsSaleOnceWithoutCashOrDebt() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "qris-sale-" + UUID.randomUUID() + ".db"
        val store = CashierStore(context, "owner", dbName)
        try {
            val product = Product("qris-item", "Sabun", 5000, 3000, 4)
            store.saveProduct(product)
            val result = store.checkoutQris("qris-id", listOf(CashierLine(product, 2)))
            assertEquals(10000, result.total); assertEquals(0, result.debt); assertEquals(0, result.received)
            store.checkoutQris("qris-id", listOf(CashierLine(product, 2)))
            assertEquals(2, store.stocks().getValue(product.id))
            assertTrue(store.receivables().isEmpty())
            val record = store.saleHistory(null, null, SaleHistoryFilter.QRIS).sales.single()
            assertEquals("qris", record.paymentMethod)
            assertEquals(0, record.cashApplied)
            assertTrue(store.saleHistory(null, null, SaleHistoryFilter.PAID_AT_SALE).sales.isEmpty())
            try {
                store.checkoutQris("qris-too-many", listOf(CashierLine(product, 3)))
                fail("QRIS checkout must reject insufficient stock")
            } catch (_: InsufficientStockException) { }
            assertEquals(2, store.stocks().getValue(product.id))
            assertEquals(1, store.saleHistory(null, null, SaleHistoryFilter.QRIS).sales.size)
            val now = System.currentTimeMillis()
            val summary = store.saleHistorySummary(now - 60000, now + 60000)
            assertEquals(10000L, summary.total); assertEquals(10000L, summary.qrisTotal)
            assertEquals(0L, summary.cashApplied); assertEquals(0L, summary.initialDebt)
            store.readableDatabase.rawQuery("SELECT COUNT(*) FROM cash_movements WHERE sale_id='qris-id'", null).use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
            }
        } finally { store.close(); context.deleteDatabase(dbName) }
    }

    @Test fun retentionHidesAfterSevenDaysAndCleansOnMonthlyCutoff() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "retention-" + UUID.randomUUID() + ".db"
        val owner = CashierStore(context, "owner", dbName)
        val other = CashierStore(context, "other", dbName)
        fun at(year: Int, month: Int, day: Int): Long = java.util.Calendar.getInstance().apply {
            clear(); set(year, month - 1, day, 12, 0, 0)
        }.timeInMillis
        try {
            val product = Product("item", "Sabun", 5000, 3000, 10)
            owner.saveProduct(product); other.saveProduct(product)
            owner.checkout("old-cash", listOf(CashierLine(product, 1)), 5000, "")
            owner.checkout("young-cash", listOf(CashierLine(product, 1)), 5000, "")
            owner.checkout("old-debt", listOf(CashierLine(product, 1)), 1000, "Bu RT")
            other.checkout("other-old", listOf(CashierLine(product, 1)), 5000, "")
            val db = owner.writableDatabase
            db.execSQL("UPDATE sales SET created_at=? WHERE id IN ('old-cash','old-debt','other-old')", arrayOf(at(2025,1,20)))
            db.execSQL("UPDATE sales SET created_at=? WHERE id='young-cash'", arrayOf(at(2025,1,30)))
            val feb1 = at(2025,2,1)
            HistoryRetention.clean(db, feb1, "owner")
            fun count(table: String, saleId: String): Int {
                val column = if (table == "sales") "id" else "sale_id"
                db.rawQuery("SELECT COUNT(*) FROM $table WHERE $column=?", arrayOf(saleId)).use { it.moveToFirst(); return it.getInt(0) }
            }
            assertEquals(0, count("sales", "old-cash")); assertEquals(1, count("cash_movements", "old-cash"))
            assertEquals(1, count("sales", "young-cash")); assertEquals(1, count("sales", "old-debt")); assertEquals(1, count("sales", "other-old"))
            val feb8 = at(2025,2,8)
            val visible = owner.saleHistory(null, null, SaleHistoryFilter.ALL, now = feb8).sales.map { it.id }
            assertTrue("young-cash" !in visible); assertTrue("old-debt" in visible)
            assertEquals(1, count("sales", "young-cash")) // wait until next first
            HistoryRetention.clean(db, at(2025,3,1), "owner")
            assertEquals(0, count("sales", "young-cash")); assertEquals(1, count("sales", "old-debt"))
            assertEquals(1, count("sales", "other-old"))
        } finally { owner.close(); other.close(); context.deleteDatabase(dbName) }
    }

    @Test fun stockAdditionIsScopedAtomicAndIdempotent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "add-stock-" + UUID.randomUUID() + ".db"
        val owner = CashierStore(context, "owner", dbName)
        val other = CashierStore(context, "other", dbName)
        try {
            val item = Product("soap", "Sabun", 5000, 3000, 3, barcode = "0012345")
            owner.saveProduct(item); other.saveProduct(item)
            assertEquals(8, owner.addStock("add-1", item.id, 5))
            assertEquals(8, owner.addStock("add-1", item.id, 5))
            assertEquals(8, owner.stocks().getValue(item.id))
            assertEquals(3, other.stocks().getValue(item.id))
            assertEquals("0012345", owner.products().first { it.id == item.id }.barcode)
            owner.readableDatabase.rawQuery("SELECT quantity,before_quantity,after_quantity FROM stock_additions WHERE owner_id='owner' AND id='add-1'", null).use {
                assertTrue(it.moveToFirst()); assertEquals(5, it.getInt(0)); assertEquals(3, it.getInt(1)); assertEquals(8, it.getInt(2)); assertTrue(!it.moveToNext())
            }
            owner.readableDatabase.rawQuery("SELECT COUNT(*) FROM cash_movements WHERE owner_id='owner'", null).use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            try { owner.addStock("add-1", item.id, 6); fail("A retry with different data must be rejected") } catch (_: IllegalArgumentException) { }
            assertEquals(8, owner.stocks().getValue(item.id))
        } finally { owner.close(); other.close(); context.deleteDatabase(dbName) }
    }

    @Test fun stockAdditionRejectsInvalidAndOverflowWithoutPartialWrites() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "add-stock-limit-" + UUID.randomUUID() + ".db"
        val store = CashierStore(context, "owner", dbName)
        try {
            val product = Product("full", "Barang", 1000, 500, Int.MAX_VALUE - 1)
            store.saveProduct(product)
            try { store.addStock("zero", product.id, 0); fail("Zero must be rejected") } catch (_: IllegalArgumentException) { }
            try { store.addStock("missing", "missing-product", 1); fail("Missing product must be rejected") } catch (_: StockAdditionException) { }
            try { store.addStock("overflow", product.id, 2); fail("Overflow must be rejected") } catch (_: StockAdditionException) { }
            assertEquals(Int.MAX_VALUE - 1, store.stocks().getValue(product.id))
            assertEquals(Int.MAX_VALUE, store.addStock("max", product.id, 1))
            store.readableDatabase.rawQuery("SELECT COUNT(*) FROM stock_additions WHERE owner_id='owner'", null).use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        } finally { store.close(); context.deleteDatabase(dbName) }
    }

    @Test fun stockAdditionRollsBackWhenAuditInsertFails() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "add-stock-rollback-" + UUID.randomUUID() + ".db"
        val store = CashierStore(context, "owner", dbName)
        try {
            val product = Product("soap", "Sabun", 5000, null, 3)
            store.saveProduct(product)
            store.writableDatabase.execSQL("CREATE TRIGGER fail_add BEFORE INSERT ON stock_additions BEGIN SELECT RAISE(ABORT, 'test failure'); END")
            try { store.addStock("attempt", product.id, 5); fail("Insert failure must roll back stock") } catch (_: android.database.sqlite.SQLiteException) { }
            assertEquals(3, store.stocks().getValue(product.id))
        } finally { store.close(); context.deleteDatabase(dbName) }
    }

    @Test fun upgradeV3PreservesDataAndAddsStockAdjustments() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-v3-" + UUID.randomUUID() + ".db"
        val before = CashierStore(context, "owner", dbName)
        val product = Product("legacy", "Barang Lama", 5000, 3000, 4, barcode = "000123")
        before.saveProduct(product)
        before.checkout("old-sale", listOf(CashierLine(product, 1)), 0, "Bu RT")
        before.writableDatabase.execSQL("DROP TABLE stock_additions")
        before.writableDatabase.execSQL("DROP TABLE qris_records")
        before.writableDatabase.execSQL("ALTER TABLE cash_movements RENAME TO cash_movements_v5")
        before.writableDatabase.execSQL("CREATE TABLE cash_movements (sale_id TEXT PRIMARY KEY NOT NULL, owner_id TEXT NOT NULL, amount INTEGER NOT NULL)")
        before.writableDatabase.execSQL("INSERT INTO cash_movements SELECT sale_id,owner_id,amount FROM cash_movements_v5")
        before.writableDatabase.execSQL("DROP TABLE cash_movements_v5")
        downgradeCreditToV5(before)
        before.writableDatabase.version = 3
        before.close()
        val after = CashierStore(context, "owner", dbName)
        try {
            assertEquals(3, after.stocks().getValue(product.id))
            assertEquals("000123", after.products().first { it.id == product.id }.barcode)
            assertEquals(5000, after.receivables().single().remaining)
            assertEquals(5, after.addStock("new-adjustment", product.id, 2))
        } finally { after.close(); context.deleteDatabase(dbName) }
    }

    @Test fun barcodesAreUniquePerOwnerAndKeepLeadingZeros() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "barcode-" + UUID.randomUUID() + ".db"
        val owner = CashierStore(context, "owner", dbName)
        val other = CashierStore(context, "other", dbName)
        try {
            val item = Product("one", "Sabun", 5000, null, 3, barcode = "0012345")
            owner.saveProduct(item)
            try { owner.saveProduct(item.copy(id = "two")); fail("Duplicate barcode must be rejected") }
            catch (_: android.database.sqlite.SQLiteConstraintException) { }
            other.saveProduct(item.copy(id = "two"))
            assertEquals("0012345", owner.products().first { it.id == "one" }.barcode)
            assertEquals("0012345", other.products().first { it.id == "two" }.barcode)
            assertTrue(owner.products().none { it.id == "two" })
        } finally { owner.close(); other.close(); context.deleteDatabase(dbName) }
    }

    @Test fun weeklyCashIsScopedDeduplicatedAndSeparateFromQris() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "weekly-cash-" + UUID.randomUUID() + ".db"
        val owner = CashierStore(context, "owner", dbName)
        val other = CashierStore(context, "other", dbName)
        try {
            val product = Product("soap", "Sabun", 5000, 3000, 10)
            owner.saveProduct(product); other.saveProduct(product)
            owner.checkout("cash-sale", listOf(CashierLine(product, 1)), 10000, "")
            owner.checkout("cash-sale", listOf(CashierLine(product, 1)), 10000, "")
            owner.checkout("partial-sale", listOf(CashierLine(product, 1)), 1000, "Bu RT")
            owner.checkout("credit-sale", listOf(CashierLine(product, 1)), 0, "Pak Doni")
            owner.checkoutQris("qris-sale", listOf(CashierLine(product, 1)))
            other.checkout("other-sale", listOf(CashierLine(product, 1)), 5000, "")
            owner.addManualCash("deposit", "in", "deposit", 10000, "Tambahan uang")
            owner.addManualCash("deposit", "in", "deposit", 10000, "Tambahan uang")
            owner.addManualCash("expense", "out", "purchase", 3000, "Kulakan tunai")
            val week = CashWeek(0, System.currentTimeMillis() + 60000)
            assertEquals(CashSummary(16000, 3000), owner.cashSummary(week))
            assertEquals(13000L, owner.cashSummary(week).net)
            assertEquals(3, owner.cashMovements("in", week).movements.size)
            assertEquals(1, owner.cashMovements("out", week).movements.size)
            assertEquals(CashSummary(5000, 0), other.cashSummary(week))
            assertEquals(CashSummary(0, 0), owner.cashSummary(CashWeek(week.end, week.end + 604800000L)))
            try { owner.addManualCash("deposit", "in", "deposit", 20000, "Tambahan uang"); fail("Same ID cannot change amount") } catch (_: IllegalArgumentException) { }
            assertEquals(CashSummary(16000, 3000), owner.cashSummary(week))
            owner.addManualCash("more-out", "out", "withdrawal", 20000, "")
            assertEquals(-7000L, owner.cashSummary(week).net)
            owner.readableDatabase.rawQuery("SELECT amount FROM qris_records WHERE owner_id='owner' AND sale_id='qris-sale'", null).use {
                assertTrue(it.moveToFirst()); assertEquals(5000, it.getInt(0)); assertTrue(!it.moveToNext())
            }
            owner.writableDatabase.execSQL("UPDATE sales SET created_at=1 WHERE id='qris-sale'")
            HistoryRetention.clean(owner.writableDatabase, System.currentTimeMillis(), "owner")
            owner.readableDatabase.rawQuery("SELECT COUNT(*) FROM qris_records WHERE sale_id='qris-sale'", null).use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            try { owner.addManualCash("invalid", "out", "operational", 1000, ""); fail("Operational category is not supported") } catch (_: IllegalArgumentException) { }
        } finally { owner.close(); other.close(); context.deleteDatabase(dbName) }
    }

    @Test fun upgradeV4KeepsDatedAndUndatedCashAndArchivesExistingQris() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-v4-" + UUID.randomUUID() + ".db"
        val before = CashierStore(context, "owner", dbName)
        val product = Product("legacy", "Barang", 5000, 3000, 5)
        before.saveProduct(product)
        before.checkout("dated", listOf(CashierLine(product, 1)), 5000, "")
        before.checkout("undated", listOf(CashierLine(product, 1)), 5000, "")
        before.checkoutQris("old-qris", listOf(CashierLine(product, 1)))
        val db = before.writableDatabase
        db.execSQL("DELETE FROM sales WHERE id='undated'")
        db.execSQL("DROP TABLE qris_records")
        db.execSQL("ALTER TABLE cash_movements RENAME TO cash_movements_v5")
        db.execSQL("CREATE TABLE cash_movements (sale_id TEXT PRIMARY KEY NOT NULL, owner_id TEXT NOT NULL, amount INTEGER NOT NULL)")
        db.execSQL("INSERT INTO cash_movements SELECT sale_id,owner_id,amount FROM cash_movements_v5")
        db.execSQL("DROP TABLE cash_movements_v5")
        downgradeCreditToV5(before)
        db.version = 4
        before.close()
        val after = CashierStore(context, "owner", dbName)
        try {
            val all = after.cashMovements("in", null).movements
            assertEquals(2, all.size)
            assertTrue(all.first { it.id == "dated" }.timestamp != null)
            assertNull(all.first { it.id == "undated" }.timestamp)
            assertEquals(1, after.cashMovements("in", CashWeek(0, System.currentTimeMillis() + 60000)).movements.size)
            after.readableDatabase.rawQuery("SELECT amount FROM qris_records WHERE sale_id='old-qris'", null).use { assertTrue(it.moveToFirst()); assertEquals(5000, it.getInt(0)) }
            assertEquals(2, after.stocks().getValue("legacy"))
        } finally { after.close(); context.deleteDatabase(dbName) }
    }

    private fun downgradeNumbersToV7(store: CashierStore) {
        val db = store.writableDatabase
        db.execSQL("DROP TABLE sale_number_counters")
        db.execSQL("ALTER TABLE sales RENAME TO sales_v8")
        db.execSQL("CREATE TABLE sales (id TEXT PRIMARY KEY NOT NULL, owner_id TEXT NOT NULL, created_at INTEGER NOT NULL, total INTEGER NOT NULL, received INTEGER NOT NULL, change_amount INTEGER NOT NULL, debt INTEGER NOT NULL, customer TEXT, payment_method TEXT NOT NULL DEFAULT 'cash')")
        db.execSQL("INSERT INTO sales SELECT id,owner_id,created_at,total,received,change_amount,debt,customer,payment_method FROM sales_v8")
        db.execSQL("DROP TABLE sales_v8")
    }

    // Build an actual pre-v6 credit schema when exercising old-version migrations.
    private fun downgradeCreditToV5(store: CashierStore) {
        downgradeNumbersToV7(store)
        val db = store.writableDatabase
        db.execSQL("DROP TABLE credit_payments")
        db.execSQL("ALTER TABLE receivables RENAME TO receivables_v6")
        db.execSQL("CREATE TABLE receivables (sale_id TEXT PRIMARY KEY NOT NULL, owner_id TEXT NOT NULL, customer TEXT NOT NULL, amount_remaining INTEGER NOT NULL)")
        db.execSQL("INSERT INTO receivables SELECT sale_id,owner_id,customer,amount_remaining FROM receivables_v6")
        db.execSQL("DROP TABLE receivables_v6")
        db.execSQL("ALTER TABLE sale_items RENAME TO sale_items_v7")
        db.execSQL("CREATE TABLE sale_items (sale_id TEXT NOT NULL, product_id TEXT NOT NULL, product_name TEXT NOT NULL, quantity INTEGER NOT NULL, price INTEGER NOT NULL, cost INTEGER)")
        db.execSQL("INSERT INTO sale_items SELECT sale_id,product_id,product_name,quantity,price,cost FROM sale_items_v7")
        db.execSQL("DROP TABLE sale_items_v7")
    }

    @Test fun creditCheckoutAndInstallmentsAreAtomicAndAccountScoped() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "credit-" + UUID.randomUUID() + ".db"
        val owner = CashierStore(context, "owner", dbName)
        val other = CashierStore(context, "other", dbName)
        try {
            val item = Product("credit-item", "Barang", 5000, null, 5)
            owner.saveProduct(item)
            try { owner.checkoutCredit("bad", listOf(CashierLine(item, 1)), 1000, "Nama", "", null, false); fail("Phone required") } catch (_: IllegalArgumentException) { }
            assertEquals(5, owner.stocks().getValue(item.id))
            owner.checkoutCredit("sale", listOf(CashierLine(item, 1)), 1000, "Nama", "08123456789", null, false)
            owner.checkoutCredit("sale", listOf(CashierLine(item, 1)), 1000, "Nama", "08123456789", null, false)
            assertEquals(4, owner.stocks().getValue(item.id))
            assertEquals("628123456789", owner.creditDetails().single().phone)
            assertTrue(other.creditDetails().isEmpty())
            try { other.payCredit("other-payment", "sale", 500); fail("Other owner cannot pay") } catch (_: IllegalStateException) { }
            try { owner.payCredit("too-much", "sale", 4001); fail("Overpayment rejected") } catch (_: IllegalArgumentException) { }
            assertEquals(4000, owner.creditDetails().single().remaining)
            assertEquals(3000, owner.payCredit("p1", "sale", 1000))
            assertEquals(3000, owner.payCredit("p1", "sale", 1000))
            assertEquals(0, owner.payCredit("p2", "sale", 3000))
            assertEquals(0, owner.payCredit("p2", "sale", 3000))
            assertTrue(owner.creditDetails().isEmpty())
            val cash = owner.cashSummary(CashWeek(0, System.currentTimeMillis() + 60000))
            assertEquals(5000L, cash.cashIn)
        } finally { owner.close(); other.close(); context.deleteDatabase(dbName) }
    }

    @Test fun upgradeV5PreservesLegacyCreditWithoutInventingPhone() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "credit-v5-" + UUID.randomUUID() + ".db"
        val before = CashierStore(context, "owner", dbName)
        val item = Product("old", "Barang", 5000, null, 2)
        before.saveProduct(item)
        before.checkout("old-sale", listOf(CashierLine(item, 1)), 0, "Pelanggan")
        downgradeCreditToV5(before); before.writableDatabase.version = 5; before.close()
        val after = CashierStore(context, "owner", dbName)
        try {
            assertEquals(5000, after.creditDetails().single().remaining)
            assertNull(after.creditDetails().single().phone)
            assertEquals(0, after.payCredit("legacy-pay", "old-sale", 5000))
        } finally { after.close(); context.deleteDatabase(dbName) }
    }

    @Test fun creditPaymentRollsBackIfCashInsertFails() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "credit-rollback-" + UUID.randomUUID() + ".db"
        val store = CashierStore(context, "owner", dbName)
        try {
            val product = Product("credit", "Barang", 5000, null, 2)
            store.saveProduct(product)
            store.checkoutCredit("sale", listOf(CashierLine(product, 1)), 0, "Siti", "08123456789", null, false)
            store.writableDatabase.execSQL("CREATE TRIGGER fail_credit_cash BEFORE INSERT ON cash_movements WHEN NEW.category='credit' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
            try { store.payCredit("pay", "sale", 1000); fail("Payment must roll back") } catch (_: android.database.sqlite.SQLiteException) { }
            assertEquals(5000, store.creditDetails().single().remaining)
            store.readableDatabase.rawQuery("SELECT COUNT(*) FROM credit_payments", null).use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        } finally { store.close(); context.deleteDatabase(dbName) }
    }

    @Test fun monthlyReportSeparatesSalesCashCreditQrisAndStock() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "report-" + UUID.randomUUID() + ".db"
        val store = CashierStore(context, "owner", dbName)
        val other = CashierStore(context, "other", dbName)
        try {
            val item = Product("p", "Barang", 5000, 3000, 5, category = "Uji")
            store.saveProduct(item); other.saveProduct(item)
            store.checkout("cash", listOf(CashierLine(item, 1)), 5000, "")
            store.checkoutCredit("credit", listOf(CashierLine(item, 1)), 1000, "Siti", "08123456789", null, false)
            store.checkoutQris("qris", listOf(CashierLine(item, 1)))
            store.payCredit("pay", "credit", 1000)
            store.addManualCash("deposit", "in", "deposit", 2000, "")
            store.addManualCash("out", "out", "withdrawal", 4000, "")
            other.checkout("other-sale", listOf(CashierLine(item, 1)), 5000, "")
            store.updateProduct(item.copy(category = "Baru"))
            val r = store.report()
            assertEquals(15000L, r.sales); assertEquals(5000L, r.qris); assertEquals(3L, r.count)
            assertEquals(9000L, r.cashIn); assertEquals(4000L, r.cashOut)
            assertEquals(1000L, r.installmentCash); assertEquals(9000L, r.modal ?: -1L); assertEquals(6000L, r.profit ?: -1L)
            assertEquals(10000L, r.activeStockValue); assertEquals("Uji", r.categories.single().name)
            assertEquals(3L, r.bestProducts.single().quantity)
            assertEquals(5000L, other.report().sales)
        } finally { store.close(); other.close(); context.deleteDatabase(dbName) }
    }

    @Test fun reportDoesNotInventUnknownCosts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "report-unknown-" + UUID.randomUUID() + ".db"
        val store = CashierStore(context, "owner", dbName)
        try {
            val item = Product("p", "Barang", 5000, null, 2)
            store.saveProduct(item); store.checkout("sale", listOf(CashierLine(item,1)), 5000, "")
            val r = store.report(); assertNull(r.modal); assertNull(r.profit); assertEquals(1L, r.missingCost)
        } finally { store.close(); context.deleteDatabase(dbName) }
    }

    @Test fun reportPdfCreatesARealPdfWithCreditDetails() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "report-pdf-" + UUID.randomUUID() + ".db"
        val store = CashierStore(context, "owner", dbName)
        var pdf: java.io.File? = null
        try {
            val item = Product("p", "Barang", 5000, 3000, 2)
            store.saveProduct(item)
            store.checkoutCredit("sale", listOf(CashierLine(item, 1)), 1000, "Siti", "08123456789", null, false)
            val credit = store.creditDetails().single()
            pdf = makeReportPdf(context, store.report(), "Warung", listOf(ExportCredit(credit, store.saleItemSnapshots(credit.saleId))))
            assertTrue(pdf.exists()); assertTrue(pdf.length() > 0)
            assertEquals("%PDF", pdf.inputStream().use { input -> String(ByteArray(4).also { input.read(it) }, Charsets.US_ASCII) })
        } finally { pdf?.delete(); store.close(); context.deleteDatabase(dbName) }
    }

}
