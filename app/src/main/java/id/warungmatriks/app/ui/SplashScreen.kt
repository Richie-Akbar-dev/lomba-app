package id.warungmatriks.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import id.warungmatriks.app.R
import id.warungmatriks.app.ui.theme.WarungMatriksTheme

@Composable
fun SplashScreen() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.warung_matriks_logo),
                contentDescription = "Logo Warung Matriks",
                modifier = Modifier.size(180.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "Layar pembuka")
@Composable
private fun SplashScreenPreview() {
    WarungMatriksTheme { SplashScreen() }
}
