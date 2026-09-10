package fr.nexoratv.tv.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import fr.nexoratv.tv.Section
import fr.nexoratv.tv.core.Dates
import fr.nexoratv.tv.core.model.LoadedPlaylist
import fr.nexoratv.tv.ui.theme.Bricolage
import fr.nexoratv.tv.ui.theme.NexoraBad
import fr.nexoratv.tv.ui.theme.NexoraGlass
import fr.nexoratv.tv.ui.theme.NexoraGradient
import fr.nexoratv.tv.ui.theme.NexoraInk
import fr.nexoratv.tv.ui.theme.NexoraInkDim
import fr.nexoratv.tv.ui.theme.NexoraLine
import fr.nexoratv.tv.ui.theme.NexoraNight
import fr.nexoratv.tv.ui.theme.NexoraOk
import fr.nexoratv.tv.ui.theme.NexoraPink
import fr.nexoratv.tv.ui.theme.NexoraWarn
import kotlinx.coroutines.delay

private data class Featured(val title: String, val subtitle: String, val image: String?)

@Composable
fun HubScreen(
    sourceName: String,
    playlist: LoadedPlaylist,
    expiresAt: Long?,
    onOpenSection: (Section) -> Unit,
    onSettings: () -> Unit,
) {
    val featured = remember(playlist) {
        buildList {
            playlist.movies.asSequence().filter { !it.logo.isNullOrEmpty() }.take(30).forEach {
                add(Featured(it.name, listOfNotNull(it.year?.toString(), it.genre, "Film").joinToString(" · "), it.logo))
            }
            playlist.series.asSequence().filter { !it.cover.isNullOrEmpty() }.take(30).forEach {
                add(Featured(it.name, listOfNotNull(it.year?.toString(), it.genre, "Série").joinToString(" · "), it.cover))
            }
        }.shuffled().take(6)
    }
    var featIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(featured) {
        if (featured.isEmpty()) return@LaunchedEffect
        while (true) { delay(15_000); featIndex = (featIndex + 1) % featured.size }
    }
    val feat = featured.getOrNull(featIndex)

    val firstCard = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstCard.requestFocus() } }

    Box(Modifier.fillMaxSize().background(NexoraNight)) {
        // fond cinéma
        Crossfade(feat?.image, animationSpec = tween(600), label = "backdrop") { img ->
            if (img != null) {
                AsyncImage(
                    model = img,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.35f,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0x59080D18),
                    0.55f to Color(0xC7080D18),
                    1f to Color(0xF5080D18),
                )
            )
        )

        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 34.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "NexoraTV",
                    fontFamily = Bricolage,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = TextStyle(brush = NexoraGradient),
                )
                Spacer(Modifier.weight(1f))
                Text(sourceName, color = NexoraInkDim, fontSize = 14.sp)
            }

            Spacer(Modifier.weight(1f))

            // Hauteur fixe : le titre « à la une » qui passe de 1 à 2 lignes ne
            // doit pas décaler les 3 blocs en dessous.
            Column(Modifier.widthIn(max = 560.dp).height(104.dp)) {
                if (feat != null) {
                    Text(
                        "À LA UNE",
                        color = NexoraPink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        feat.title,
                        fontFamily = Bricolage,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 32.sp,
                        lineHeight = 34.sp,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (feat.subtitle.isNotBlank()) {
                        Text(feat.subtitle, color = NexoraInkDim, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.size(24.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                HubCard(
                    Icons.Default.LiveTv, "TV", "${playlist.live.size} chaînes",
                    Modifier.weight(1f).focusRequester(firstCard),
                ) { onOpenSection(Section.TV) }
                HubCard(
                    Icons.Default.Movie, "Films", "${playlist.movies.size} titres",
                    Modifier.weight(1f),
                ) { onOpenSection(Section.MOVIES) }
                HubCard(
                    Icons.Default.VideoLibrary, "Séries", "${playlist.series.size} titres",
                    Modifier.weight(1f),
                ) { onOpenSection(Section.SERIES) }
            }

            Spacer(Modifier.size(20.dp))

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
    icon: ImageVector,
    label: String,
    sub: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.widthIn(max = 280.dp).aspectRatio(1.6f),
        scale = CardDefaults.scale(focusedScale = 1.05f),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, NexoraGradient),
                shape = RoundedCornerShape(16.dp),
            ),
        ),
    ) {
        Column(
            Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(NexoraGlass).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, tint = Color(0xFFCBD3E6), modifier = Modifier.size(66.dp))
            Spacer(Modifier.size(12.dp))
            Text(label, fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = NexoraInk)
            Text(sub, fontSize = 12.sp, color = NexoraInkDim)
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
            .background(Color.White.copy(alpha = 0.045f), RoundedCornerShape(999.dp))
            .border(1.dp, NexoraLine, RoundedCornerShape(999.dp))
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        if (dot != null) Box(Modifier.size(9.dp).background(dot, CircleShape))
        Text(text, color = NexoraInkDim, fontSize = 13.sp)
    }
}
