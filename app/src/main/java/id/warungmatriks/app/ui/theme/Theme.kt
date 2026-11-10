package id.warungmatriks.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import id.warungmatriks.app.R

// Fidelity Modern palette from DESIGN.md. Keep semantic error colours distinct from blue actions.
val Blue = Color(0xFF003670)
val Sky = Color(0xFF425F8C)
val Background = Color(0xFFF9F9FF)
val White = Color(0xFFFFFFFF)
val Ink = Color(0xFF171C24)
val Muted = Color(0xFF424751)
val Line = Color(0xFFE5E8F4)
val PaleBlue = Color(0xFFD6E3FF)
val LightSurface = Color(0xFFF1F3FF)
val DeepBlue = Color(0xFF074D98)
val Danger = Color(0xFFBA1A1A)

private val Colors = lightColorScheme(
    primary = Blue,
    onPrimary = White,
    primaryContainer = PaleBlue,
    onPrimaryContainer = Blue,
    secondary = Sky,
    onSecondary = White,
    secondaryContainer = Color(0xFFADCAFE),
    onSecondaryContainer = Color(0xFF375582),
    tertiary = Color(0xFF512F0A),
    onTertiary = White,
    tertiaryContainer = Color(0xFFFFDCC0),
    onTertiaryContainer = Color(0xFF643E19),
    background = Background,
    surface = White,
    surfaceVariant = LightSurface,
    onSurface = Ink,
    onSurfaceVariant = Muted,
    onBackground = Ink,
    outline = Color(0xFF737782),
    outlineVariant = Color(0xFFC2C6D3),
    surfaceTint = Blue,
    error = Danger,
    onError = White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
)

// Inter is bundled locally for a consistent appearance without network access.
private val inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)
private val typography = Typography().let { defaults ->
    defaults.copy(
        displayLarge = defaults.displayLarge.copy(fontFamily = inter),
        displayMedium = defaults.displayMedium.copy(fontFamily = inter),
        displaySmall = defaults.displaySmall.copy(fontFamily = inter),
        headlineLarge = defaults.headlineLarge.copy(fontFamily = inter),
        headlineMedium = defaults.headlineMedium.copy(fontFamily = inter),
        headlineSmall = defaults.headlineSmall.copy(fontFamily = inter),
        titleLarge = defaults.titleLarge.copy(fontFamily = inter),
        titleMedium = defaults.titleMedium.copy(fontFamily = inter),
        titleSmall = defaults.titleSmall.copy(fontFamily = inter),
        bodyLarge = defaults.bodyLarge.copy(fontFamily = inter),
        bodyMedium = defaults.bodyMedium.copy(fontFamily = inter),
        bodySmall = defaults.bodySmall.copy(fontFamily = inter),
        labelLarge = defaults.labelLarge.copy(fontFamily = inter),
        labelMedium = defaults.labelMedium.copy(fontFamily = inter),
        labelSmall = defaults.labelSmall.copy(fontFamily = inter),
    )
}

@Composable
fun WarungMatriksTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = typography, content = content)
}
