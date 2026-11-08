package id.warungmatriks.app.data

import org.junit.Assert.*
import org.junit.Test

class BinaryQrisTest {
    private fun sample(): String {
        val body = "00020101021126180014ID.CO.QRIS.WWW5204000053033605802ID5904DEMO6007JAKARTA6304"
        return body + QrisPayload.crc16(body)
    }
    @Test fun decodesEightBitBinaryAndAcceptsWhitespace() {
        val payload = sample()
        val bits = encodeBinaryQris(payload)
        assertEquals(payload, decodeBinaryQris(bits.replace(" ", "\n")))
        assertEquals(payload, decodeBinaryQris(bits.replace(" ", "")))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsOtherCharacters() { decodeBinaryQris("0100x000") }
    @Test(expected = IllegalArgumentException::class) fun rejectsIncompleteByte() { decodeBinaryQris("0101010") }
    @Test(expected = IllegalArgumentException::class) fun rejectsNonAsciiByte() { decodeBinaryQris("11111111") }
    @Test(expected = IllegalArgumentException::class) fun rejectsNonQrisData() { decodeBinaryQris("01001101 01001111") }
    @Test(expected = IllegalArgumentException::class) fun rejectsWrongCrc() { decodeBinaryQris(encodeBinaryQris(sample().dropLast(4) + "0000")) }
}
