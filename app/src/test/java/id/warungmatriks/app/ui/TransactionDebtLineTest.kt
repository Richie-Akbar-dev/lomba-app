package id.warungmatriks.app.ui

import id.warungmatriks.app.data.rupiah
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionDebtLineTest {
    @Test fun hidesZeroAndShowsPositiveDebt() {
        assertEquals("", transactionDebtLine(0))
        assertEquals("\nSisa kasbon: ${rupiah(12000)}", transactionDebtLine(12000))
    }
}
