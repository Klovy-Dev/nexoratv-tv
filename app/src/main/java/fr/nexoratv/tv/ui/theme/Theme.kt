package fr.nexoratv.tv.ui.theme

import android.app.Activity
import androidx.compose.material3.LocalContentColor as M3LocalContentColor
import androidx.compose.material3.LocalTextStyle as M3LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.tv.material3.LocalContentColor as TvLocalContentColor
import androidx.tv.material3.LocalTextStyle as TvLocalTextStyle
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme

// Dégradé signature NexoraTV
val NexoraPink = Color(0xFFFF3D8B)
val NexoraPurple = Color(0xFF8B44FF)
val NexoraBlue = Color(0xFF3D82FF)

// Fonds
val NexoraNight = Color(0xFF0B1220)
val NexoraSurface = Color(0xFF141A28)
val NexoraSurface2 = Color(0xFF1B2336)
val NexoraSurface3 = Color(0xFF232C44)
val NexoraSurfaceHi = NexoraSurface2

// Texte / traits / états
val NexoraInk = Color(0xFFEAEEF7)
val NexoraInkDim = Color(0xFF9098B3)
val NexoraInkFaint = Color(0xFF5C6684)
val NexoraLine = Color(0xFF1A2136)
val NexoraLineStrong = Color(0xFF262F49)
val NexoraOk = Color(0xFF37D9A0)
val NexoraWarn = Color(0xFFFFB454)
val NexoraBad = Color(0xFFFF6B6B)

val NexoraGradient = Brush.linearGradient(listOf(NexoraPink, NexoraPurple, NexoraBlue))

/** Verre dépoli pour les tuiles posées sur un backdrop. */
val NexoraGlass = Brush.verticalGradient(
    listOf(Color(0xD91B2336), Color(0xF2141A28)),
)

/** Fond général : bleu nuit avec des lueurs colorées. */
val NexoraBackdrop = Brush.radialGradient(
    colors = listOf(Color(0xFF1C1442), NexoraNight),
    center = Offset(300f, 200f),
    radius = 1700f,
)

private val DarkColors = darkColorScheme(
    primary = NexoraPurple,
    secondary = NexoraPink,
    tertiary = NexoraBlue,
    background = NexoraNight,
    surface = NexoraSurface,
    surfaceVariant = NexoraSurface2,
    onPrimary = Color.White,
    onBackground = NexoraInk,
    onSurface = NexoraInk,
)

private val TvDarkColors = tvDarkColorScheme(
    primary = NexoraPurple,
    secondary = NexoraPink,
    tertiary = NexoraBlue,
    background = NexoraNight,
    surface = NexoraSurface,
    surfaceVariant = NexoraSurface2,
    onPrimary = Color.White,
    onBackground = NexoraInk,
    onSurface = NexoraInk,
    border = NexoraPurple,
)

/**
 * Thème unique TV + téléphone. On applique les deux MaterialTheme (phone +
 * TV). Hors d'un `Surface` tv-material3, `Text` prend `LocalContentColor` (noir
 * par défaut) et `LocalTextStyle` (police système) : on force ici la couleur
 * claire + Manrope.
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
    MaterialTheme(colorScheme = DarkColors, typography = NexoraTypography) {
        TvMaterialTheme(colorScheme = TvDarkColors) {
            CompositionLocalProvider(
                TvLocalContentColor provides NexoraInk,
                M3LocalContentColor provides NexoraInk,
                TvLocalTextStyle provides NexoraBaseTextStyle,
                M3LocalTextStyle provides NexoraBaseTextStyle,
            ) {
                content()
            }
        }
    }
}
