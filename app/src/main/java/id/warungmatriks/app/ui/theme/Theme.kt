package id.warungmatriks.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand palette: primary blue, sky blue, slate background, and white surfaces.
val Blue = Color(0xFF2563EB)
val Sky = Color(0xFF38BDF8)
val Background = Color(0xFFF1F5F9)
val White = Color(0xFFFFFFFF)

// Neutral text colors provide accessible contrast on the light palette.
val Ink = Color(0xFF1E2B3C)
val Muted = Color(0xFF52657A)
val Line = Background
val PaleBlue = Sky.copy(alpha = 0.12f)

private val Colors = lightColorScheme(
    primary = Blue,
    onPrimary = White,
    primaryContainer = PaleBlue,
    onPrimaryContainer = Ink,
    secondary = Sky,
    onSecondary = Ink,
    secondaryContainer = PaleBlue,
    onSecondaryContainer = Ink,
    tertiary = Blue,
    onTertiary = White,
    tertiaryContainer = PaleBlue,
    onTertiaryContainer = Ink,
    background = Background,
    surface = White,
    surfaceVariant = Background,
    onSurface = Ink,
    onSurfaceVariant = Muted,
    onBackground = Ink,
    outline = Muted,
    outlineVariant = Line,
    surfaceTint = Blue,
    error = Blue,
    onError = White,
    errorContainer = Background,
    onErrorContainer = Ink,
)

@Composable
fun WarungMatriksTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
