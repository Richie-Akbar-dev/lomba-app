package id.warungmatriks.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.firebase.auth.FirebaseUser
import id.warungmatriks.app.R
import id.warungmatriks.app.auth.AuthRepository
import id.warungmatriks.app.auth.ShopProfile
import id.warungmatriks.app.auth.SignedInAccount
import id.warungmatriks.app.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface EntryState {
    data object SignedOut : EntryState
    data class Setup(val user: FirebaseUser) : EntryState
    data class Ready(val account: SignedInAccount) : EntryState
}

@Composable
fun AuthEntry() {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) { AuthRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    fun stateFor(user: FirebaseUser?): EntryState {
        if (user == null) return EntryState.SignedOut
        val profile = repository.profile(user) ?: return EntryState.Setup(user)
        return EntryState.Ready(repository.account(user, profile))
    }
    var state by remember(repository) {
        mutableStateOf(try { stateFor(repository.currentUser()) } catch (_: Exception) { EntryState.SignedOut })
    }
    var showSplash by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { if (showSplash) { delay(2_000); showSplash = false } }

    if (showSplash) {
        SplashScreen()
        return
    }
    when (val current = state) {
        EntryState.SignedOut -> GoogleLoginScreen(
            configured = repository.isConfigured, busy = busy, error = message,
            onSignIn = {
                if (!busy) {
                    busy = true; message = null
                    scope.launch {
                        try {
                            state = stateFor(repository.signIn(context))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: GetCredentialCancellationException) {
                            message = "Masuk dibatalkan. Silakan coba lagi."
                        } catch (_: Exception) {
                            message = "Tidak dapat masuk. Periksa internet, layanan Google Play, dan konfigurasi Google Sign-In, lalu coba lagi."
                        } finally { busy = false }
                    }
                }
            },
        )
        is EntryState.Setup -> ShopSetupScreen(
            ownerSuggestion = current.user.displayName.orEmpty(), busy = busy, error = message,
            onSave = { profile ->
                try {
                    if (repository.saveProfile(current.user, profile)) {
                        message = null
                        state = EntryState.Ready(repository.account(current.user, profile))
                    } else message = "Profil belum dapat disimpan di perangkat. Coba lagi."
                } catch (_: Exception) { message = "Profil belum dapat disimpan di perangkat. Coba lagi." }
            },
            onSignOut = {
                if (!busy) {
                    busy = true
                    scope.launch {
                        try { repository.signOut(context); state = EntryState.SignedOut; message = null }
                        catch (e: CancellationException) { throw e } catch (_: Exception) { message = "Tidak dapat keluar. Coba lagi." } finally { busy = false }
                    }
                }
            },
        )
        is EntryState.Ready -> WarungMatriksApp(account = current.account, onSignOut = {
            if (!busy) {
                busy = true
                scope.launch {
                    try { repository.signOut(context); state = EntryState.SignedOut; message = null }
                    catch (e: CancellationException) { throw e } catch (_: Exception) { message = "Tidak dapat keluar. Coba lagi." } finally { busy = false }
                }
            }
        }, signOutBusy = busy, signOutError = message, onSaveProfile = { profile ->
            try {
                val user = repository.currentUser()
                if (user == null || user.uid != current.account.uid || profile.ownerName.isBlank() || profile.shopName.isBlank() || profile.businessType.isBlank()) false
                else if (repository.saveProfile(user, profile)) {
                    state = EntryState.Ready(current.account.copy(shop = profile)); true
                } else false
            } catch (_: Exception) { false }
        })
    }
}

@Composable
private fun AuthLayout(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Background).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Image(painterResource(R.drawable.warung_matriks_logo), "Logo Warung Matriks", modifier = Modifier.size(88.dp))
        Spacer(Modifier.height(10.dp))
        Text("Warung Matriks", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Ink)
        Text("Aplikasi Kasir & Pembukuan Warung", fontSize = 13.sp, color = Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Surface(color = White, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line), shadowElevation = 1.dp) {
            Column(Modifier.fillMaxWidth().padding(22.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(subtitle, fontSize = 14.sp, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), lineHeight = 21.sp)
                Spacer(Modifier.height(24.dp))
                content()
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("Warung Matriks • Kasir & pembukuan warung", fontSize = 11.sp, color = Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
internal fun GoogleLoginScreen(configured: Boolean, busy: Boolean, error: String?, onSignIn: () -> Unit) {
    AuthLayout("Selamat Datang", "Silakan masuk\nmenggunakan akun Google untuk memulai") {
        if (!configured) {
            Surface(color = LightSurface, shape = RoundedCornerShape(10.dp)) {
                Text("Google Sign-In belum dikonfigurasi. Ikuti langkah konfigurasi pada README sebelum masuk.", Modifier.padding(12.dp), color = Ink, fontSize = 12.sp, lineHeight = 17.sp)
            }
            Spacer(Modifier.height(12.dp))
        }
        OutlinedButton(onClick = onSignIn, enabled = configured && !busy, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
            if (busy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)) }
            else { Text("G", color = Blue, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp); Spacer(Modifier.width(12.dp)) }
            Text(if (busy) "Menghubungkan..." else "Masuk dengan Google", fontWeight = FontWeight.Bold, color = if (configured && !busy) Ink else Muted)
        }
        error?.let { Spacer(Modifier.height(12.dp)); Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, lineHeight = 17.sp) }
        Spacer(Modifier.height(18.dp))
        Text("Masuk pertama memerlukan internet. Setelah masuk, aplikasi dapat dibuka kembali secara offline pada perangkat ini.", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 12.sp, color = Muted, lineHeight = 18.sp)
    }
}

@Composable
internal fun ShopSetupScreen(ownerSuggestion: String, busy: Boolean, error: String?, onSave: (ShopProfile) -> Unit, onSignOut: () -> Unit) {
    var owner by rememberSaveable { mutableStateOf(ownerSuggestion.take(80)) }
    var shop by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("Warung") }
    AuthLayout("Siapkan Warung", "Lengkapi profil warung untuk perangkat ini. Data profil disimpan di perangkat.") {
        FormField("Nama pemilik *", owner, { owner = it.take(80) }, "Contoh: Bu Sri")
        Spacer(Modifier.height(12.dp))
        FormField("Nama warung / toko *", shop, { shop = it.take(80) }, "Contoh: Warung Barokah")
        Spacer(Modifier.height(18.dp))
        Text("Pilih jenis usaha", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Warung", "Minuman", "Lainnya").forEach { type ->
                FilterChip(selected = kind == type, onClick = { kind = type }, label = { Text(type, fontSize = 11.sp) })
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onSave(ShopProfile(owner.trim(), shop.trim(), kind)) }, enabled = !busy && owner.isNotBlank() && shop.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.Storefront, null, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("Simpan & Buka Kasir", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onSignOut, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Gunakan akun lain") }
        error?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "Masuk Google")
@Composable private fun GoogleLoginPreview() { WarungMatriksTheme { GoogleLoginScreen(true, false, null, {}) } }
@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "Profil warung")
@Composable private fun ShopSetupPreview() { WarungMatriksTheme { ShopSetupScreen("", false, null, {}, {}) } }
