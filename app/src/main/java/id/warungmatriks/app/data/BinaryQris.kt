package id.warungmatriks.app.data

/** Binary digits are grouped into big-endian eight-bit ASCII bytes. Whitespace is ignored. */
internal fun decodeBinaryQris(input: String): String {
    require(input.length <= 24_576) { "Kode biner terlalu panjang." }
    require(input.all { it == '0' || it == '1' || it.isWhitespace() }) { "Kode hanya boleh berisi angka 0 dan 1." }
    val bits = input.filterNot { it.isWhitespace() }
    require(bits.isNotEmpty() && bits.length % 8 == 0 && bits.length <= 16_384) { "Kode harus lengkap, dengan 8 bit untuk setiap karakter." }
    val payload = bits.chunked(8).map { it.toInt(2) }.map { value ->
        require(value in 32..126) { "Kode harus menghasilkan karakter ASCII QRIS yang dapat dibaca." }
        value.toChar()
    }.joinToString("")
    // Check the same static QRIS format and CRC used by the cashier, without recording a sale.
    QrisPayload.withAmount(payload, 1)
    return payload
}

internal fun encodeBinaryQris(payload: String): String =
    payload.map { it.code.toString(2).padStart(8, '0') }.joinToString(" ")

/** Accept either the full printable QRIS payload or its eight-bit binary representation. */
internal fun decodeQrisInput(input: String): String {
    require(input.length <= 24_576) { "Kode QRIS terlalu panjang." }
    val trimmed = input.trim()
    require(trimmed.isNotEmpty()) { "Masukkan kode QRIS." }
    if (trimmed.all { it == '0' || it == '1' || it.isWhitespace() }) return decodeBinaryQris(trimmed)
    require(trimmed.length <= 2048) { "Payload QRIS terlalu panjang." }
    // Only a complete static QRIS payload with valid CRC is accepted; arbitrary text is not.
    QrisPayload.withAmount(trimmed, 1)
    return trimmed
}
