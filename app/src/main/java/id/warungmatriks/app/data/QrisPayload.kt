package id.warungmatriks.app.data

import java.util.Locale

/** Generates an amount-bearing EMV QR payload from a static QRIS payload. No payment verification. */
internal object QrisPayload {
    private data class Field(val tag: String, val value: String)

    private fun parse(payload: String): List<Field> {
        require(payload.isNotEmpty() && payload.all { it.code in 32..126 }) { "Payload harus berupa teks QRIS ASCII yang lengkap." }
        val fields = mutableListOf<Field>()
        var offset = 0
        while (offset < payload.length) {
            require(offset + 4 <= payload.length) { "Format payload QRIS tidak lengkap." }
            val tag = payload.substring(offset, offset + 2)
            val lengthText = payload.substring(offset + 2, offset + 4)
            require(tag.all(Char::isDigit) && lengthText.all(Char::isDigit)) { "Format payload QRIS tidak valid." }
            val length = lengthText.toInt()
            require(offset + 4 + length <= payload.length) { "Panjang data QRIS tidak sesuai." }
            fields.add(Field(tag, payload.substring(offset + 4, offset + 4 + length)))
            offset += 4 + length
        }
        require(fields.map { it.tag }.distinct().size == fields.size) { "Payload memiliki tag ganda." }
        return fields
    }

    internal fun crc16(value: String): String {
        var crc = 0xFFFF
        value.toByteArray(Charsets.US_ASCII).forEach { byte ->
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) { crc = if ((crc and 0x8000) != 0) ((crc shl 1) xor 0x1021) and 0xFFFF else (crc shl 1) and 0xFFFF }
        }
        return String.format(Locale.US, "%04X", crc)
    }

    private fun encode(field: Field): String {
        require(field.value.length <= 99)
        return field.tag + field.value.length.toString().padStart(2, '0') + field.value
    }

    fun withAmount(staticPayload: String, amount: Int): String {
        require(amount > 0) { "Total belanja harus lebih dari nol." }
        val source = staticPayload.trim()
        val fields = parse(source)
        fun value(tag: String) = fields.firstOrNull { it.tag == tag }?.value
        require(fields.lastOrNull()?.tag == "63" && value("63")?.length == 4 && source.endsWith("6304" + value("63"))) { "CRC QRIS tidak ditemukan." }
        require(crc16(source.dropLast(4)) == value("63")?.uppercase(Locale.US)) { "CRC QRIS tidak sesuai." }
        require(fields.firstOrNull()?.tag == "00" && value("00") == "01") { "Format QRIS tidak didukung." }
        require(value("01") == null || value("01") == "11") { "Masukkan QRIS statis, bukan QRIS transaksi lain." }
        require(value("53") == "360" && value("58") == "ID") { "Mata uang atau negara QRIS tidak sesuai." }
        require(value("52") != null && !value("59").isNullOrBlank() && !value("60").isNullOrBlank()) { "Identitas merchant QRIS belum lengkap." }
        require(fields.any { it.tag.toInt() in 26..51 && it.value.contains("ID.CO.QRIS.WWW") }) { "Identitas merchant QRIS tidak ditemukan." }
        require(fields.none { it.tag in listOf("54", "55", "56", "57") }) { "Payload dengan nominal atau biaya tambahan belum didukung." }
        val result = buildString {
            fields.filterNot { it.tag == "01" || it.tag == "63" }.forEach { field ->
                append(encode(field))
                if (field.tag == "00") append(encode(Field("01", "12")))
                if (field.tag == "53") append(encode(Field("54", amount.toString())))
            }
            append("6304")
        }
        return result + crc16(result)
    }
}
