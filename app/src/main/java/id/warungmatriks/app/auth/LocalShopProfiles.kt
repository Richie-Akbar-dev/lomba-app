package id.warungmatriks.app.auth

import android.content.Context

/** Profiles remain private to this device and keyed by Firebase UID. */
internal class LocalShopProfiles(context: Context, preferencesName: String = "shop_profiles") {
    private val preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun get(uid: String): ShopProfile? {
        if (uid.isBlank()) return null
        val prefix = "$uid."
        val owner = preferences.getString(prefix + "owner", null) ?: return null
        val shop = preferences.getString(prefix + "shop", null) ?: return null
        val kind = preferences.getString(prefix + "type", null) ?: return null
        if (owner.isBlank() || shop.isBlank() || kind.isBlank()) return null
        return ShopProfile(owner, shop, kind)
    }

    fun save(uid: String, profile: ShopProfile): Boolean {
        if (uid.isBlank() || profile.ownerName.isBlank() || profile.shopName.isBlank() || profile.businessType.isBlank()) return false
        val prefix = "$uid."
        return preferences.edit().putString(prefix + "owner", profile.ownerName.trim())
            .putString(prefix + "shop", profile.shopName.trim()).putString(prefix + "type", profile.businessType.trim()).commit()
    }
}
