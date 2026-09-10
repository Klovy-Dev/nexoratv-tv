package fr.nexoratv.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import fr.nexoratv.tv.core.model.LoadProgress
import fr.nexoratv.tv.ui.theme.Bricolage
import fr.nexoratv.tv.ui.theme.NexoraBackdrop
import fr.nexoratv.tv.ui.theme.NexoraGradient
import fr.nexoratv.tv.ui.theme.NexoraInk
import fr.nexoratv.tv.ui.theme.NexoraInkDim
import fr.nexoratv.tv.ui.theme.NexoraInkFaint
import fr.nexoratv.tv.ui.theme.NexoraOk
import fr.nexoratv.tv.ui.theme.NexoraPurple

/** Écran de démarrage : logo + progression par catégorie sur fond bleu nuit. */
@Composable
fun LoadingScreen(progress: LoadProgress?, expectVod: Boolean) {
    Box(Modifier.fillMaxSize().background(NexoraBackdrop), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(26.dp),
            modifier = Modifier.padding(48.dp).widthIn(max = 440.dp),
        ) {
            Text(
                "NexoraTV",
                fontFamily = Bricolage,
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                style = TextStyle(brush = NexoraGradient),
            )

            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(0.8f),
                color = NexoraPurple,
                trackColor = Color.White.copy(alpha = 0.09f),
            )

            if (progress == null) {
                Text("Démarrage…", color = NexoraInkDim, fontSize = 14.sp)
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    StepRow(
                        "Connexion au serveur",
                        done = progress.connected,
                        active = !progress.connected,
                        value = if (progress.connected) "OK" else null,
                    )
                    StepRow(
                        "Chaînes en direct",
                        done = progress.live != null,
                        active = progress.connected && progress.live == null,
                        value = progress.live?.toString(),
                    )
                    if (expectVod) {
                        StepRow(
                            "Films",
                            done = progress.movies != null,
                            active = progress.live != null && progress.movies == null,
                            value = progress.movies?.toString(),
                        )
                        StepRow(
                            "Séries",
                            done = progress.series != null,
                            active = progress.live != null && progress.series == null,
                            value = progress.series?.toString(),
                        )
                    }
                }
                Text(
                    "Première fois : la préparation du catalogue peut prendre un moment.",
                    color = NexoraInkFaint,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun StepRow(label: String, done: Boolean, active: Boolean, value: String?) {
    val color = when {
        active -> NexoraInk
        done -> NexoraInkDim
        else -> NexoraInkFaint
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = color,
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, color = NexoraOk, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

