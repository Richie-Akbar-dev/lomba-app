package id.warungmatriks.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class QrisSettingsTest {
    @Test fun storesValidPayloadPerAccountAndNeverReplacesItWithInvalidBits() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "qris-settings-test-" + UUID.randomUUID()
        val a = QrisSettings(context, "owner-a", name)
        val b = QrisSettings(context, "owner-b", name)
        val body = "00020101021126180014ID.CO.QRIS.WWW5204000053033605802ID5904DEMO6007JAKARTA6304"
        val payload = body + QrisPayload.crc16(body)
        try {
            assertTrue(a.saveQrisInput(encodeBinaryQris(payload)))
            assertEquals(payload, QrisSettings(context, "owner-a", name).payload())
            assertNull(b.payload())
            assertTrue(b.saveQrisInput(payload))
            assertEquals(payload, b.payload())
            try { b.saveQrisInput(payload.dropLast(4) + "0000"); fail("Invalid CRC must be rejected") } catch (_: IllegalArgumentException) { }
            assertEquals(payload, b.payload())
            assertTrue(b.clear())
            try { a.saveQrisInput("01001101"); fail("Invalid QRIS must be rejected") } catch (_: IllegalArgumentException) { }
            assertEquals(payload, a.payload())
            assertTrue(a.clear()); assertNull(a.payload()); assertNull(b.payload())
        } finally { context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit() }
    }
}
