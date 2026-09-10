package fr.nexoratv.tv.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import fr.nexoratv.tv.R

private fun axis(weight: Int) = FontVariation.Settings(FontVariation.weight(weight))

/** Interface / texte courant. */
val Manrope = FontFamily(
    Font(R.font.manrope, FontWeight.Normal, variationSettings = axis(400)),
    Font(R.font.manrope, FontWeight.Medium, variationSettings = axis(500)),
    Font(R.font.manrope, FontWeight.SemiBold, variationSettings = axis(600)),
    Font(R.font.manrope, FontWeight.Bold, variationSettings = axis(700)),
    Font(R.font.manrope, FontWeight.ExtraBold, variationSettings = axis(800)),
)

/** Titres / affichage. */
val Bricolage = FontFamily(
    Font(R.font.bricolage_grotesque, FontWeight.SemiBold, variationSettings = axis(600)),
    Font(R.font.bricolage_grotesque, FontWeight.Bold, variationSettings = axis(700)),
    Font(R.font.bricolage_grotesque, FontWeight.ExtraBold, variationSettings = axis(800)),
)

/** Style de texte de base (hérité par tous les `Text` sans famille explicite). */
val NexoraBaseTextStyle = TextStyle(fontFamily = Manrope, color = Color(0xFFEAEEF7))

/** Échelle Material 3 : titres en Bricolage, le reste en Manrope. */
val NexoraTypography: Typography = Typography().let { d ->
    d.copy(
        displayLarge = d.displayLarge.copy(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold),
        displayMedium = d.displayMedium.copy(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold),
        displaySmall = d.displaySmall.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold),
        headlineLarge = d.headlineLarge.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold),
        headlineMedium = d.headlineMedium.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold),
        headlineSmall = d.headlineSmall.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold),
        titleLarge = d.titleLarge.copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold),
        titleMedium = d.titleMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
        titleSmall = d.titleSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
        bodyLarge = d.bodyLarge.copy(fontFamily = Manrope),
        bodyMedium = d.bodyMedium.copy(fontFamily = Manrope),
        bodySmall = d.bodySmall.copy(fontFamily = Manrope),
        labelLarge = d.labelLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
        labelMedium = d.labelMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.Medium),
        labelSmall = d.labelSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.Medium),
    )
}
