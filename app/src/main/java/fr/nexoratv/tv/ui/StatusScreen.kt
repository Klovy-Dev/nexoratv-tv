package fr.nexoratv.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import fr.nexoratv.tv.ui.theme.Bricolage
import fr.nexoratv.tv.ui.theme.NexoraBackdrop
import fr.nexoratv.tv.ui.theme.NexoraInk
import fr.nexoratv.tv.ui.theme.NexoraInkDim
import fr.nexoratv.tv.ui.theme.NexoraLineStrong
import fr.nexoratv.tv.ui.theme.NexoraPurple
import fr.nexoratv.tv.ui.theme.NexoraSurface2

enum class StatusKind { EMPTY, SEARCH, NETWORK, LOADING }

/** Écran centré réutilisé partout : rien ici, aucun résultat, serveur
 *  injoignable, chargement… Pastille lumineuse + message + action. */
@Composable
fun StatusScreen(
    title: String,
    subtitle: String? = null,
    kind: StatusKind = StatusKind.EMPTY,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(onAction != null) {
        if (onAction != null) runCatching { focus.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(NexoraBackdrop), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.padding(48.dp).widthIn(max = 460.dp),
        ) {
            Orb(
                when (kind) {
                    StatusKind.SEARCH -> Icons.Default.SearchOff
                    StatusKind.NETWORK -> Icons.Default.WifiOff
                    StatusKind.EMPTY -> Icons.Default.SentimentDissatisfied
                    StatusKind.LOADING -> Icons.Default.Refresh
                },
                loading = kind == StatusKind.LOADING,
            )
            Text(
                title,
                fontFamily = Bricolage,
                fontSize = 22.sp,
                color = NexoraInk,
                textAlign = TextAlign.Center,
            )
            if (subtitle != null) {
                Text(subtitle, color = NexoraInkDim, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(4.dp))
                Button(onClick = onAction, modifier = Modifier.focusRequester(focus)) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun Orb(icon: ImageVector, loading: Boolean) {
    Box(
        Modifier
            .size(96.dp)
            .background(
                Brush.radialGradient(
                    0f to Color(0x668B44FF),
                    1f to NexoraSurface2,
                ),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = NexoraPurple, strokeWidth = 3.dp)
        } else {
            Icon(icon, null, tint = NexoraInkDim, modifier = Modifier.size(40.dp))
        }
    }
}
