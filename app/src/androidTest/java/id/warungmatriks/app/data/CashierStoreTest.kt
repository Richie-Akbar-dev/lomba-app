package id.warungmatriks.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CashierStoreTest {
    @Test fun partialPaymentSavesDebtAndDeduplicatesRetry() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = CashierStore(context, "test-" + UUID.randomUUID())
        val product = DemoData.products.first()
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
            assertEquals(null, saved.cost)
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
            db.execSQL("INSERT INTO sale_items VALUES ('old','indomie','Indomie Goreng',3,3500,2700)")
            db.version = 1
        } finally { db.close() }
        val store = CashierStore(context, "owner", dbName)
        try {
            assertEquals(3, store.stocks().getValue("indomie"))
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

}
