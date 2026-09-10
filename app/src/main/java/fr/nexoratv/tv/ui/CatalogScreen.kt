package fr.nexoratv.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import fr.nexoratv.tv.AppViewModel
import fr.nexoratv.tv.Section
import fr.nexoratv.tv.core.model.Channel
import fr.nexoratv.tv.core.model.LoadedPlaylist
import fr.nexoratv.tv.core.model.MediaKind
import fr.nexoratv.tv.ui.theme.Bricolage
import fr.nexoratv.tv.ui.theme.NexoraBackdrop
import fr.nexoratv.tv.ui.theme.NexoraInk
import fr.nexoratv.tv.ui.theme.NexoraInkFaint
import fr.nexoratv.tv.ui.theme.NexoraInkDim
import fr.nexoratv.tv.ui.theme.NexoraPurple
import fr.nexoratv.tv.ui.theme.NexoraSurface
import fr.nexoratv.tv.ui.theme.NexoraSurface3
import kotlinx.coroutines.delay

/** Grille d'une section : recherche, catégories, colonnes fixes, place
 *  conservée au retour (état hissé dans le ViewModel). */
@Composable
fun CatalogScreen(
    vm: AppViewModel,
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

    var query by remember(section) { mutableStateOf(vm.catalogQuery[section] ?: "") }
    var groupIndex by remember(section) { mutableIntStateOf(vm.catalogCategory[section] ?: 0) }
    val gridState = rememberLazyGridState(vm.catalogScroll[section] ?: 0)
    LaunchedEffect(gridState, section) {
        snapshotFlow { gridState.firstVisibleItemIndex }.collect { vm.catalogScroll[section] = it }
    }

    val groups = remember(items) {
        listOf<String?>(null) + items.map { it.groupOrDefault }.distinct()
    }
    val safeIndex = groupIndex.coerceIn(0, (groups.size - 1).coerceAtLeast(0))
    val group = groups.getOrElse(safeIndex) { null }

    val searching = query.trim().length >= 2
    val visible = remember(items, group, query) {
        if (searching) {
            val q = query.trim().lowercase()
            items.filterIndexed { i, _ -> lowerNames[i].contains(q) }
        } else if (group == null) items else items.filter { it.groupOrDefault == group }
    }

    val widthDp = LocalConfiguration.current.screenWidthDp
    val columns = gridColumns(section, widthDp)

    val firstChip = remember { FocusRequester() }
    LaunchedEffect(section) { runCatching { firstChip.requestFocus() } }

    // Recherche : le champ n'est JAMAIS dans le chemin de navigation D-pad. On
    // affiche une barre-bouton ; OK dessus ouvre l'édition (et le clavier), et
    // seulement là. Retour / Rechercher / descendre = on referme.
    var searchOpen by remember(section) { mutableStateOf(false) }
    var fieldTouched by remember { mutableStateOf(false) }
    val searchBar = remember { FocusRequester() }
    val searchField = remember { FocusRequester() }
    fun closeSearch() { searchOpen = false; runCatching { searchBar.requestFocus() } }
    BackHandler(enabled = searchOpen) { closeSearch() }
    LaunchedEffect(searchOpen) {
        if (searchOpen) { fieldTouched = false; delay(80); runCatching { searchField.requestFocus() } }
    }

    Box(Modifier.fillMaxSize().background(NexoraBackdrop)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                Spacer(Modifier.width(12.dp))
                Text(section.label, fontFamily = Bricolage, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = NexoraInk)
                Spacer(Modifier.weight(1f))
                Text(
                    "$sourceName · ${if (searching) visible.size else items.size}",
                    color = NexoraInkFaint,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(Modifier.height(14.dp))

            if (searchOpen) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; vm.catalogQuery[section] = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(searchField)
                        .onFocusChanged {
                            if (it.isFocused) fieldTouched = true
                            else if (fieldTouched && searchOpen) searchOpen = false
                        },
                    singleLine = true,
                    leadingIcon = { M3Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        M3IconButton(onClick = {
                            if (query.isEmpty()) closeSearch()
                            else { query = ""; vm.catalogQuery[section] = "" }
                        }) { M3Icon(Icons.Default.Close, "Fermer") }
                    },
                    placeholder = { M3Text("Rechercher dans ${section.label.lowercase()}") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { closeSearch() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = NexoraSurface,
                        unfocusedContainerColor = NexoraSurface,
                        focusedTextColor = NexoraInk,
                        unfocusedTextColor = NexoraInk,
                    ),
                )
            } else {
                Surface(
                    onClick = { searchOpen = true },
                    modifier = Modifier.fillMaxWidth().focusRequester(searchBar),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = NexoraSurface,
                        focusedContainerColor = NexoraSurface3,
                    ),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Search, null, tint = NexoraInkFaint, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            query.ifBlank { "Rechercher dans ${section.label.lowercase()}" },
                            color = if (query.isBlank()) NexoraInkFaint else NexoraInk,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (query.isNotBlank()) {
                            Text("appuyez pour modifier", color = NexoraInkDim, fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            if (searching) {
                Chip(
                    label = "✕  Effacer la recherche",
                    selected = false,
                    modifier = Modifier.focusRequester(firstChip),
                ) { query = ""; vm.catalogQuery[section] = "" }
                Spacer(Modifier.height(16.dp))
            } else if (groups.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(groups.size) { i ->
                        Chip(
                            label = groups[i] ?: "Tout",
                            selected = i == safeIndex,
                            modifier = if (i == 0) Modifier.focusRequester(firstChip) else Modifier,
                        ) { groupIndex = i; vm.catalogCategory[section] = i }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (visible.isEmpty()) {
                    if (searching) StatusScreen(
                        title = "Aucun résultat pour « ${query.trim()} »",
                        subtitle = "Vérifie l'orthographe, ou parcours les catégories.",
                        kind = StatusKind.SEARCH,
                        actionLabel = "Effacer la recherche",
                        onAction = { query = ""; vm.catalogQuery[section] = "" },
                    ) else StatusScreen(
                        title = "Rien dans cette catégorie",
                        kind = StatusKind.EMPTY,
                    )
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(columns),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        // marge = débordement du focusedScale (1.06×) → carte jamais coupée
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 48.dp),
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
            // sélectionné = fond gris plein · focalisé = fond violet (le curseur)
            containerColor = if (selected) NexoraSurface3 else NexoraSurface,
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
