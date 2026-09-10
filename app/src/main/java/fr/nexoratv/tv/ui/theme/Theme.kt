package fr.nexoratv.tv.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme

// Dégradé signature NexoraTV
val NexoraPink = Color(0xFFE8348C)
val NexoraPurple = Color(0xFF8B5CF6)
val NexoraBlue = Color(0xFF2D7DF6)
val NexoraNight = Color(0xFF0B1220)
val NexoraSurface = Color(0xFF141A28)
val NexoraSurfaceHi = Color(0xFF1E2740)

private val DarkColors = darkColorScheme(
    primary = NexoraPurple,
    secondary = NexoraPink,
    tertiary = NexoraBlue,
    background = NexoraNight,
    surface = NexoraSurface,
    surfaceVariant = NexoraSurfaceHi,
    onPrimary = Color.White,
    onBackground = Color(0xFFEDEFF5),
    onSurface = Color(0xFFEDEFF5),
)

private val TvDarkColors = tvDarkColorScheme(
    primary = NexoraPurple,
    secondary = NexoraPink,
    tertiary = NexoraBlue,
    background = NexoraNight,
    surface = NexoraSurface,
    surfaceVariant = NexoraSurfaceHi,
    onPrimary = Color.White,
    onBackground = Color(0xFFEDEFF5),
    onSurface = Color(0xFFEDEFF5),
    border = NexoraPurple,
)

/**
 * Thème unique TV + téléphone. On applique les deux MaterialTheme (phone +
 * TV) : les composants `androidx.tv.material3` lisent le second, les
 * `androidx.compose.material3` le premier.
 */
@Composable
fun NexoraTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = NexoraNight.toArgb()
            window.navigationBarColor = NexoraNight.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                NexoraNight.luminance() > 0.5f
        }
    }
    MaterialTheme(colorScheme = DarkColors, typography = Typography()) {
        TvMaterialTheme(colorScheme = TvDarkColors) {
            content()
        }
    }
}
