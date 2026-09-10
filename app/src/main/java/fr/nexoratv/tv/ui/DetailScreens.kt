package fr.nexoratv.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import fr.nexoratv.tv.DetailState
import fr.nexoratv.tv.core.model.Channel
import fr.nexoratv.tv.core.model.Episode
import fr.nexoratv.tv.core.model.MediaKind
import fr.nexoratv.tv.core.model.VodInfo
import fr.nexoratv.tv.ui.theme.NexoraBackdrop
import fr.nexoratv.tv.ui.theme.NexoraGradient
import fr.nexoratv.tv.ui.theme.NexoraInk
import fr.nexoratv.tv.ui.theme.NexoraInkDim
import fr.nexoratv.tv.ui.theme.NexoraInkFaint
import fr.nexoratv.tv.ui.theme.NexoraNight
import fr.nexoratv.tv.ui.theme.NexoraPurple
import fr.nexoratv.tv.ui.theme.NexoraSurface

// ---------------------------------------------------------------- Film

@Composable
fun MovieDetailScreen(
    state: DetailState,
    sourceId: String,
    onPlay: (String, List<Channel>, Int) -> Unit,
    onBack: () -> Unit,
) {
    when (state) {
        is DetailState.Loading -> Loader()
        is DetailState.Error -> ErrorScreen(state.message, onBack)
        is DetailState.Movie -> MovieBody(state.channel, state.info, sourceId, onPlay, onBack)
        is DetailState.Series -> Loader()
    }
}

@Composable
private fun MovieBody(
    channel: Channel,
    info: VodInfo,
    sourceId: String,
    onPlay: (String, List<Channel>, Int) -> Unit,
    onBack: () -> Unit,
) {
    val play = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { play.requestFocus() } }

    Backdrop(info.backdrop ?: channel.logo) {
        Column(Modifier.fillMaxSize().padding(40.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().weight(1f)) {
                Poster(info.poster ?: channel.logo, Modifier.width(210.dp))
                Spacer(Modifier.width(28.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Text(channel.name, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = NexoraInk)
                    Spacer(Modifier.height(6.dp))
                    MetaLine(
                        listOfNotNull(
                            info.year?.toString(),
                            info.durationSecs?.let { formatDuration(it) },
                            info.rating?.let { "★ %.1f".format(it) },
                            info.genre,
                        )
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        info.plot ?: "Pas de synopsis disponible.",
                        color = NexoraInkDim,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        modifier = Modifier.weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                    )
                    info.cast?.let {
                        Spacer(Modifier.height(12.dp))
                        Text("Avec $it", color = NexoraInkFaint, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { onPlay(sourceId, listOf(channel), 0) },
                        modifier = Modifier.focusRequester(play),
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Regarder maintenant")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Série

@Composable
fun SeriesDetailScreen(
    state: DetailState,
    sourceId: String,
    onPlay: (String, List<Channel>, Int) -> Unit,
    onBack: () -> Unit,
) {
    when (state) {
        is DetailState.Loading -> Loader()
        is DetailState.Error -> ErrorScreen(state.message, onBack)
        is DetailState.Movie -> Loader()
        is DetailState.Series -> SeriesBody(state, sourceId, onPlay, onBack)
    }
}

@Composable
private fun SeriesBody(
    s: DetailState.Series,
    sourceId: String,
    onPlay: (String, List<Channel>, Int) -> Unit,
    onBack: () -> Unit,
) {
    val bundle = s.bundle
    val seasons = bundle.seasons
    var season by remember { mutableIntStateOf(seasons.firstOrNull() ?: 1) }
    val episodes = remember(bundle, season) { bundle.episodesOf(season) }

    val firstSeason = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstSeason.requestFocus() } }

    Backdrop(bundle.backdrop ?: bundle.cover ?: s.cover) {
        Column(Modifier.fillMaxSize().padding(40.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                Spacer(Modifier.width(16.dp))
                Poster(bundle.cover ?: s.cover, Modifier.width(120.dp))
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = NexoraInk)
                    Spacer(Modifier.height(4.dp))
                    MetaLine(
                        listOfNotNull(
                            bundle.year?.toString(),
                            bundle.rating?.let { "★ %.1f".format(it) },
                            bundle.genre,
                            "${seasons.size} saison${if (seasons.size > 1) "s" else ""}",
                        )
                    )
                    bundle.plot?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = NexoraInkDim, fontSize = 13.sp, lineHeight = 19.sp,
                            maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            if (bundle.episodes.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Aucun épisode renvoyé par le serveur.", color = NexoraInkFaint)
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    // Colonne saisons
                    LazyColumn(
                        Modifier.width(150.dp).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(seasons) { i, sn ->
                            RowItem(
                                text = "Saison $sn",
                                selected = sn == season,
                                modifier = if (i == 0) Modifier.focusRequester(firstSeason) else Modifier,
                                onFocus = { season = sn },
                                onClick = { season = sn },
                            )
                        }
                    }
                    Spacer(Modifier.width(20.dp))
                    // Colonne épisodes
                    LazyColumn(
                        Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(episodes, key = { it.id }) { ep ->
                            RowItem(
                                text = "${ep.episode}. ${ep.title}",
                                selected = false,
                                onFocus = {},
                                onClick = {
                                    val queue = episodes.map { it.toChannel() }
                                    onPlay(sourceId, queue, episodes.indexOf(ep).coerceAtLeast(0))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Episode.toChannel() = Channel(
    id = id,
    name = "S$season E$episode · $title",
    url = url,
    logo = cover,
    kind = MediaKind.MOVIE,
)

// ---------------------------------------------------------------- commun

@Composable
private fun Loader() {
    Box(Modifier.fillMaxSize().background(NexoraBackdrop), Alignment.Center) {
        CircularProgressIndicator(color = NexoraPurple)
    }
}

@Composable
private fun Backdrop(url: String?, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(NexoraNight)) {
        if (!url.isNullOrEmpty()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.22f,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xCC0B1220), Color(0xF20B1220)))
            )
        )
        content()
    }
}

@Composable
private fun Poster(url: String?, modifier: Modifier) {
    Box(
        modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(NexoraSurface),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrEmpty()) {
            Text("?", color = NexoraInkFaint, fontSize = 24.sp)
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun MetaLine(parts: List<String>) {
    if (parts.isEmpty()) return
    Text(
        parts.joinToString("   ·   "),
        color = NexoraInkFaint,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun RowItem(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) NexoraPurple else NexoraSurface,
            focusedContainerColor = NexoraPurple,
        ),
    ) {
        Text(
            text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp,
            color = NexoraInk,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
        )
    }
}

private fun formatDuration(secs: Int): String {
    val h = secs / 3600
    val m = (secs % 3600) / 60
    return if (h > 0) "${h}h${m.toString().padStart(2, '0')}" else "${m} min"
}
