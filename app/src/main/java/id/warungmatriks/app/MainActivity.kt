package id.warungmatriks.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import id.warungmatriks.app.ui.AuthEntry
import id.warungmatriks.app.data.scheduleHistoryCleanup
import id.warungmatriks.app.ui.theme.WarungMatriksTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleHistoryCleanup(applicationContext)
        setContent { WarungMatriksTheme { AuthEntry() } }
    }
}
