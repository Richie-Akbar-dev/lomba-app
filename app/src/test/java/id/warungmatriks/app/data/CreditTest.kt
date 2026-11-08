package id.warungmatriks.app.data

import org.junit.Assert.*
import org.junit.Test

class CreditTest {
    @Test fun indonesianAndInternationalPhoneNumbers() {
        assertEquals("628123456789", normalizeWhatsapp("0812-3456-789"))
        assertEquals("628123456789", normalizeWhatsapp("+62 8123456789"))
        assertNull(normalizeWhatsapp("abc"))
        assertNull(normalizeWhatsapp("012"))
    }
    @Test fun greetingChangesWithDeviceHour() {
        assertEquals("Selamat pagi", greeting(8))
        assertEquals("Selamat siang", greeting(12))
        assertEquals("Selamat sore", greeting(16))
        assertEquals("Selamat malam", greeting(20))
        assertTrue(creditMessage("Siti", 20000, null, 8).contains("Selamat pagi, Siti"))
    }
}
