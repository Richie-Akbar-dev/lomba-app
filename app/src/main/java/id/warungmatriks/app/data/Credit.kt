package id.warungmatriks.app.data

import java.util.Calendar

internal data class CreditDetail(val saleId: String, val customer: String, val remaining: Int, val phone: String?, val dueAt: Long?, val remind: Boolean, val total: Int, val received: Int, val createdAt: Long)

/** Indonesian local numbers and international numbers in E.164-like format, without '+'. */
internal fun normalizeWhatsapp(input: String): String? {
    val trimmed = input.trim()
    if (!trimmed.matches(Regex("\\+?[0-9 ()-]+"))) return null
    val digits = trimmed.filter { it in '0'..'9' }
    val normalized = when {
        digits.startsWith("00") -> digits.drop(2)
        digits.startsWith("0") -> "62" + digits.drop(1)
        else -> digits
    }
    return normalized.takeIf { it.length in 8..15 && !it.startsWith("0") }
}

internal fun greeting(hour: Int): String = when {
    hour >= 4 && hour <= 10 -> "Selamat pagi"
    hour >= 11 && hour <= 14 -> "Selamat siang"
    hour >= 15 && hour <= 17 -> "Selamat sore"
    else -> "Selamat malam"
}
internal fun creditMessage(name: String, remaining: Int, due: String?, hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): String =
    "${greeting(hour)}, $name. Sisa kasbon Anda di warung kami adalah ${rupiah(remaining)}." +
        (due?.let { " Batas pelunasan: $it." } ?: "") + " Terima kasih."
