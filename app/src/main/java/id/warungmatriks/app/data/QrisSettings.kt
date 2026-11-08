package id.warungmatriks.app.data

import android.content.Context

/** Stored locally per account; no payment-provider verification or server sync. */
internal class QrisSettings(context: Context, private val ownerId: String, name: String = "qris_settings") {
    private val preferences = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val key = "$ownerId.payload"
    init { require(ownerId.isNotBlank()) }

    fun payload(): String? = preferences.getString(key, null)

    fun saveBinary(input: String): Boolean {
        val payload = decodeBinaryQris(input) // Validate before replacing an existing configuration.
        return preferences.edit().putString(key, payload).commit()
    }

    fun clear(): Boolean = preferences.edit().remove(key).commit()
}
