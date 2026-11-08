package id.warungmatriks.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import id.warungmatriks.app.ui.theme.WarungMatriksTheme
import id.warungmatriks.app.auth.ShopProfile
import id.warungmatriks.app.auth.SignedInAccount

internal enum class Screen(val title: String, val icon: ImageVector? = null) {
    KASIR("Kasir", Icons.Default.PointOfSale), PESANAN("Pesanan", Icons.Default.ReceiptLong),
    BARANG("Barang", Icons.Default.Inventory2), LAPORAN("Laporan", Icons.Default.BarChart),
    AKUN("Akun", Icons.Default.PersonOutline), NOTIFIKASI("Notifikasi"), KAS("Kas Laci"),
    KASBON("Kasbon"), TAMBAH("Tambah Barang"),
}

@Composable
internal fun WarungMatriksApp(account: SignedInAccount, onSignOut: () -> Unit, signOutBusy: Boolean = false, signOutError: String? = null) {
    val mainScreens = listOf(Screen.KASIR, Screen.PESANAN, Screen.BARANG, Screen.LAPORAN, Screen.AKUN)
    var screen by rememberSaveable(account.uid) { mutableStateOf(Screen.KASIR) }
    val backStack = rememberSaveable(account.uid, saver = listSaver<SnapshotStateList<Screen>, String>(
        save = { list -> list.map { it.name } },
        restore = { names -> names.map { Screen.valueOf(it) }.toMutableStateList() },
    )) { mutableStateListOf() }
    val cashierState = remember { CashierState() }
    var editingProductId by rememberSaveable(account.uid) { mutableStateOf<String?>(null) }
    fun navigate(next: Screen) { if (screen != next) { backStack.add(screen); screen = next } }
    fun back() { screen = if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex) else Screen.KASIR }
    BackHandler(enabled = screen !in mainScreens) { back() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (screen in mainScreens) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                mainScreens.forEach { destination ->
                    NavigationBarItem(selected = screen == destination, onClick = { backStack.clear(); screen = destination },
                        icon = { Icon(destination.icon!!, contentDescription = null) }, label = { Text(destination.title, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)))
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                Screen.KASIR -> CashierScreen(cashierState, ownerId = account.uid, cashierName = account.shop.ownerName, onNotifications = { navigate(Screen.NOTIFIKASI) }, onAccount = { navigate(Screen.AKUN) }, onCash = { navigate(Screen.KAS) }, onInventory = { navigate(Screen.BARANG) }, onAddProduct = { editingProductId = null; navigate(Screen.TAMBAH) })
                Screen.PESANAN -> OrdersScreen(ownerId = account.uid)
                Screen.BARANG -> InventoryScreen(ownerId = account.uid, onAdd = { editingProductId = null; navigate(Screen.TAMBAH) }, onEdit = { editingProductId = it; navigate(Screen.TAMBAH) })
                Screen.LAPORAN -> ReportsScreen()
                Screen.AKUN -> AccountScreen(account = account, onSignOut = onSignOut, signOutBusy = signOutBusy, signOutError = signOutError, onNotifications = { navigate(Screen.NOTIFIKASI) })
                Screen.NOTIFIKASI -> NotificationsScreen(ownerId = account.uid, onBack = { back() }, onCredit = { navigate(Screen.KASBON) })
                Screen.KAS -> CashLedgerScreen(onBack = { back() })
                Screen.KASBON -> CreditScreen(onBack = { back() })
                Screen.TAMBAH -> AddProductScreen(ownerId = account.uid, productId = editingProductId, onBack = { back() })
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "Kasir")
@Composable
fun CashierScreenPreview() { WarungMatriksTheme { WarungMatriksApp(SignedInAccount("demo", "", "", ShopProfile("Pemilik Demo", "Warung Matriks", "Warung")), {}) } }
