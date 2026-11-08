package id.warungmatriks.app.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LocalShopProfilesTest {
    @Test fun editsPersistAndRemainSeparatedByAccount() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "profiles-test-" + UUID.randomUUID()
        val profiles = LocalShopProfiles(context, name)
        try {
            assertTrue(profiles.save("uid-a", ShopProfile("Siti", "Warung A", "Warung")))
            assertTrue(profiles.save("uid-b", ShopProfile("Doni", "Warung B", "Minuman")))
            assertTrue(profiles.save("uid-a", ShopProfile(" Siti Baru ", " Toko Baru ", "Lainnya")))
            assertEquals(ShopProfile("Siti Baru", "Toko Baru", "Lainnya"), LocalShopProfiles(context, name).get("uid-a"))
            assertEquals(ShopProfile("Doni", "Warung B", "Minuman"), profiles.get("uid-b"))
            assertFalse(profiles.save("uid-a", ShopProfile("", "B", "Warung")))
            assertEquals("Toko Baru", profiles.get("uid-a")?.shopName)
        } finally { context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit() }
    }
}
