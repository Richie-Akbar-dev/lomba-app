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
}
