package id.warungmatriks.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SettlementTest {
    @Test fun partialCashCreatesDebt() {
        assertEquals(Settlement(20_000, 0, 9_500), calculateSettlement(29_500, 20_000))
    }
    @Test fun zeroCashCreatesFullDebt() {
        assertEquals(Settlement(0, 0, 29_500), calculateSettlement(29_500, 0))
    }
    @Test fun excessCashGivesChange() {
        assertEquals(Settlement(29_500, 20_500, 0), calculateSettlement(29_500, 50_000))
    }
    @Test(expected = IllegalArgumentException::class) fun negativeCashIsRejected() {
        calculateSettlement(29_500, -1)
    }
}
