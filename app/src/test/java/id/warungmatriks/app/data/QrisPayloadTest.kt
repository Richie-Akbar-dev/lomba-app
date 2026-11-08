package id.warungmatriks.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrisPayloadTest {
    // Synthetic data only: this is not a real merchant QR and must not be used for payment.
    private fun sample(): String {
        val beforeCrc = "00020101021126180014ID.CO.QRIS.WWW5204000053033605802ID5904DEMO6007JAKARTA6304"
        return beforeCrc + QrisPayload.crc16(beforeCrc)
    }
    @Test fun crcMatchesStandardCheckValue() { assertEquals("29B1", QrisPayload.crc16("123456789")) }
    @Test fun amountAndChecksumAreUpdated() {
        val qr = QrisPayload.withAmount(sample(), 29500)
        assertTrue(qr.contains("010212"))
        assertTrue(qr.contains("540529500"))
        assertEquals(QrisPayload.crc16(qr.dropLast(4)), qr.takeLast(4))
    }
    @Test(expected = IllegalArgumentException::class) fun zeroAmountIsRejected() {
        QrisPayload.withAmount(sample(), 0)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidPayloadIsRejected() {
        QrisPayload.withAmount("not-a-qr", 29500)
    }
    @Test(expected = IllegalArgumentException::class) fun corruptChecksumIsRejected() {
        QrisPayload.withAmount(sample().dropLast(4) + "0000", 29500)
    }
}
