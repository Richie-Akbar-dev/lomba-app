package id.warungmatriks.app.auth

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import id.warungmatriks.app.BuildConfig
import id.warungmatriks.app.data.CREDIT_REMINDER_NOTIFICATION_ID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

data class ShopProfile(val ownerName: String, val shopName: String, val businessType: String)
data class SignedInAccount(val uid: String, val displayName: String, val email: String, val shop: ShopProfile)

// Coordinates local sign-out with the credit reminder's final session check and update.
internal val creditReminderSessionLock = Any()

/** Firebase owns and persists the auth session; the app never stores Google tokens itself. */
class AuthRepository(private val context: Context) {
    private val profiles = LocalShopProfiles(context)
    val isConfigured: Boolean = listOf(
        BuildConfig.FIREBASE_API_KEY, BuildConfig.FIREBASE_APP_ID,
        BuildConfig.FIREBASE_PROJECT_ID, BuildConfig.GOOGLE_WEB_CLIENT_ID,
    ).all { it.isNotBlank() }

    private fun auth(): FirebaseAuth {
        check(isConfigured) { "Google Sign-In belum dikonfigurasi." }
        val app = FirebaseApp.getApps(context).find { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?: FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .build())
        return FirebaseAuth.getInstance(app)
    }

    // Firebase restores its cached user without a network request; this is not an online revocation check.
    fun currentUser(): FirebaseUser? = if (isConfigured) auth().currentUser else null

    fun profile(user: FirebaseUser): ShopProfile? = profiles.get(user.uid)

    fun account(user: FirebaseUser, profile: ShopProfile) = SignedInAccount(
        uid = user.uid, displayName = user.displayName.orEmpty(), email = user.email.orEmpty(), shop = profile,
    )

    fun saveProfile(user: FirebaseUser, profile: ShopProfile): Boolean = profiles.save(user.uid, profile)

    suspend fun signIn(activityContext: Context): FirebaseUser {
        check(isConfigured) { "Google Sign-In belum dikonfigurasi." }
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = CredentialManager.create(activityContext).getCredential(activityContext, request)
        val credential = result.credential
        require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Kredensial Google tidak tersedia."
        }
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        // Firebase verifies the Google ID token online before creating/restoring a session.
        val user = auth().signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)).await().user
        return checkNotNull(user) { "Akun Google tidak tersedia." }
    }

    suspend fun signOut(activityContext: Context) {
        synchronized(creditReminderSessionLock) {
            auth().signOut()
            // An already displayed generic reminder belongs to the previous session.
            try { NotificationManagerCompat.from(context).cancel(CREDIT_REMINDER_NOTIFICATION_ID) } catch (_: Exception) { }
        }
        try {
            CredentialManager.create(activityContext).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Local Firebase sign-out already succeeded; clearing the provider state is best-effort.
        }
    }
}
