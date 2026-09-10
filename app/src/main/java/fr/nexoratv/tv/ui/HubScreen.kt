package fr.nexoratv.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.Text
import fr.nexoratv.tv.Section
import fr.nexoratv.tv.core.Dates
import fr.nexoratv.tv.ui.theme.NexoraBackdrop
import fr.nexoratv.tv.ui.theme.NexoraBad
import fr.nexoratv.tv.ui.theme.NexoraGradient
import fr.nexoratv.tv.ui.theme.NexoraInk
import fr.nexoratv.tv.ui.theme.NexoraInkDim
import fr.nexoratv.tv.ui.theme.NexoraLine
import fr.nexoratv.tv.ui.theme.NexoraOk
import fr.nexoratv.tv.ui.theme.NexoraWarn

/** Accueil : 3 blocs TV / Films / Séries, expiration en bas à gauche,
 *  Paramètres en bas à droite. */
@Composable
fun HubScreen(
    sourceName: String,
    liveCount: Int,
    movieCount: Int,
    seriesCount: Int,
    expiresAt: Long?,
    onOpenSection: (Section) -> Unit,
    onSettings: () -> Unit,
) {
    val firstCard = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstCard.requestFocus() } }

    Box(Modifier.fillMaxSize().background(NexoraBackdrop)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 34.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "NexoraTV",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = TextStyle(brush = NexoraGradient),
                )
                Spacer(Modifier.weight(1f))
                Text(sourceName, color = NexoraInkDim, fontSize = 14.sp)
            }

            Spacer(Modifier.weight(1f))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            ) {
                HubCard(
                    Icons.Default.LiveTv, "TV", "$liveCount chaînes",
                    Modifier.weight(1f).focusRequester(firstCard),
                ) { onOpenSection(Section.TV) }
                HubCard(
                    Icons.Default.Movie, "Films", "$movieCount titres",
                    Modifier.weight(1f),
                ) { onOpenSection(Section.MOVIES) }
                HubCard(
                    Icons.Default.VideoLibrary, "Séries", "$seriesCount titres",
                    Modifier.weight(1f),
                ) { onOpenSection(Section.SERIES) }
            }

            Spacer(Modifier.weight(1f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                ExpiryPill(expiresAt)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSettings, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Default.Settings, "Paramètres")
                }
            }
        }
    }
}

@Composable
private fun HubCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    sub: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.widthIn(max = 260.dp).aspectRatio(0.92f),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, NexoraGradient),
                shape = RoundedCornerShape(12.dp),
            ),
        ),
    ) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Icon(icon, null, tint = Color(0xFFB9C2D9), modifier = Modifier.size(40.dp))
            Spacer(Modifier.weight(1f))
            Text(label, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = NexoraInk)
            Text(sub, fontSize = 13.sp, color = NexoraInkDim)
        }
    }
}

@Composable
private fun ExpiryPill(expiresAt: Long?) {
    val (text, dot) = when {
        expiresAt == null -> "Playlist M3U" to null
        expiresAt < System.currentTimeMillis() -> "Playlist expirée" to NexoraBad
        else -> {
            val days = Dates.daysUntil(expiresAt)
            val date = Dates.frenchDate(expiresAt)
            if (days <= 7) "Expire dans $days j — le $date" to NexoraWarn
            else "Playlist active — expire le $date" to NexoraOk
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(999.dp))
            .border(1.dp, NexoraLine, RoundedCornerShape(999.dp))
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        if (dot != null) Box(Modifier.size(9.dp).background(dot, CircleShape))
        Text(text, color = NexoraInkDim, fontSize = 13.sp)
    }
}
