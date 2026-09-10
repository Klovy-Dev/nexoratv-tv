package fr.nexoratv.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon as M3Icon
import androidx.compose.material3.IconButton as M3IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text as M3Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import fr.nexoratv.tv.Section
import fr.nexoratv.tv.core.model.Channel
import fr.nexoratv.tv.core.model.LoadedPlaylist
import fr.nexoratv.tv.core.model.MediaKind
import fr.nexoratv.tv.ui.theme.NexoraBackdrop
import fr.nexoratv.tv.ui.theme.NexoraGradient
import fr.nexoratv.tv.ui.theme.NexoraInk
import fr.nexoratv.tv.ui.theme.NexoraInkDim
import fr.nexoratv.tv.ui.theme.NexoraInkFaint
import fr.nexoratv.tv.ui.theme.NexoraPurple
import fr.nexoratv.tv.ui.theme.NexoraSurface
import fr.nexoratv.tv.ui.theme.NexoraSurfaceHi

/** Grille d'une section (TV / Films / Séries) : recherche, catégories,
 *  colonnes fixes selon l'écran, cadre de jaquette verrouillé. */
@Composable
fun CatalogScreen(
    pl: LoadedPlaylist,
    section: Section,
    sourceId: String,
    sourceName: String,
    onPlay: (String, List<Channel>, Int) -> Unit,
    onOpenMovie: (Channel) -> Unit,
    onOpenSeries: (seriesId: String, name: String, cover: String?) -> Unit,
    onBack: () -> Unit,
) {
    val items: List<Channel> = remember(pl, section) {
        when (section) {
            Section.TV -> pl.live
            Section.MOVIES -> pl.movies
            Section.SERIES -> pl.series.map {
                Channel(
                    id = it.id, name = it.name, url = "", streamId = it.seriesId,
                    logo = it.cover, group = it.group, kind = MediaKind.SERIES,
                )
            }
        }
    }
    val lowerNames = remember(items) { items.map { it.name.lowercase() } }

    var query by remember(section) { mutableStateOf("") }
    var groupIndex by remember(section) { mutableIntStateOf(0) }

    val groups = remember(items) {
        listOf<String?>(null) + items.map { it.groupOrDefault }.distinct()
    }
    val group = groups.getOrElse(groupIndex) { null }

    val searching = query.trim().length >= 2
    val visible = remember(items, group, query) {
        if (searching) {
            val q = query.trim().lowercase()
            items.filterIndexed { i, _ -> lowerNames[i].contains(q) }
        } else if (group == null) items else items.filter { it.groupOrDefault == group }
    }

    val widthDp = LocalConfiguration.current.screenWidthDp
    val columns = gridColumns(section, widthDp)

    // Focus initial : 1re catégorie (jamais le champ de recherche — sinon le
    // clavier TV s'ouvre tout seul à l'arrivée sur l'écran).
    val firstChip = remember { FocusRequester() }
    LaunchedEffect(section) { runCatching { firstChip.requestFocus() } }

    Box(Modifier.fillMaxSize().background(NexoraBackdrop)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.focusRequester(backFocus)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
                }
                Spacer(Modifier.width(12.dp))
                Text(section.label, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = NexoraInk)
                Spacer(Modifier.weight(1f))
                Text(
                    "$sourceName · ${if (searching) visible.size else items.size}",
                    color = NexoraInkFaint,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { M3Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        M3IconButton(onClick = { query = "" }) { M3Icon(Icons.Default.Close, "Effacer") }
                    }
                },
                placeholder = { M3Text("Rechercher dans ${section.label.lowercase()}") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = NexoraSurface,
                    unfocusedContainerColor = NexoraSurface,
                    focusedTextColor = NexoraInk,
                    unfocusedTextColor = NexoraInk,
                ),
            )

            Spacer(Modifier.height(14.dp))

            if (!searching && groups.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(groups.size) { i ->
                        Chip(
                            label = groups[i] ?: "Tout",
                            selected = i == groupIndex,
                            modifier = if (i == 0) Modifier.focusRequester(firstChip) else Modifier,
                        ) { groupIndex = i }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        if (searching) "Aucun résultat pour « ${query.trim()} »." else "Rien ici.",
                        color = NexoraInkFaint,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp),
                ) {
                    items(visible, key = { it.id }) { ch ->
                        PosterCard(ch) {
                            when (ch.kind) {
                                MediaKind.SERIES -> onOpenSeries(ch.streamId.orEmpty(), ch.name, ch.logo)
                                MediaKind.MOVIE -> onOpenMovie(ch)
                                MediaKind.LIVE -> onPlay(sourceId, visible, visible.indexOf(ch).coerceAtLeast(0))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun gridColumns(section: Section, widthDp: Int): Int {
    val big = widthDp >= 900
    val med = widthDp >= 600
    return if (section == Section.TV) {
        if (big) 4 else if (med) 3 else 2
    } else {
        if (big) 6 else if (med) 5 else 3
    }
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) NexoraPurple else NexoraSurface,
            focusedContainerColor = NexoraPurple,
        ),
    ) {
        Text(
            label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp,
            color = NexoraInk,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PosterCard(ch: Channel, onClick: () -> Unit) {
    val live = ch.kind == MediaKind.LIVE
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, NexoraGradient),
                shape = RoundedCornerShape(12.dp),
            ),
        ),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (live) 16f / 9f else 2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NexoraSurfaceHi),
                contentAlignment = Alignment.Center,
            ) {
                if (ch.logo.isNullOrEmpty()) {
                    Text(
                        ch.name.take(1).uppercase(),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = NexoraInkDim,
                    )
                } else {
                    AsyncImage(
                        model = ch.logo,
                        contentDescription = ch.name,
                        contentScale = if (live) ContentScale.Fit else ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(if (live) 10.dp else 0.dp),
                    )
                }
            }
            Box(Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp).height(32.dp)) {
                Text(
                    ch.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = Color(0xFFC7CEDE),
                )
            }
        }
    }
}
