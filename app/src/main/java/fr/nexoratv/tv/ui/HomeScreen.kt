package fr.nexoratv.tv.ui

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Refresh
import coil3.compose.AsyncImage
import fr.nexoratv.tv.AppViewModel
import fr.nexoratv.tv.CatalogState
import fr.nexoratv.tv.core.model.Channel
import fr.nexoratv.tv.core.model.LoadedPlaylist
import fr.nexoratv.tv.core.model.MediaKind
import fr.nexoratv.tv.core.model.UNCATEGORIZED
import fr.nexoratv.tv.ui.theme.NexoraBackdrop
import fr.nexoratv.tv.ui.theme.NexoraGradient

private enum class Section(val label: String) { TV("TV"), MOVIES("Films"), SERIES("Séries") }

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onPlay: (sourceId: String, queue: List<Channel>, startIndex: Int) -> Unit,
) {
    val state by vm.catalogState.collectAsState()
    val sourceId = vm.currentSource?.id ?: ""
    val sourceName = vm.currentSource?.name ?: "NexoraTV"

    Box(Modifier.fillMaxSize().background(NexoraBackdrop)) {
        when (val s = state) {
            is CatalogState.Ready ->
                Catalog(s.playlist, sourceId, sourceName, onPlay,
                    onRefresh = { vm.loadCatalog(true) },
                    onAccount = { vm.goConnect() })
            is CatalogState.Error -> StatusView("Chargement impossible", s.message) {
                androidx.tv.material3.Button(onClick = { vm.loadCatalog(true) }) { Text("Réessayer") }
            }
            else -> StatusView("Chargement du catalogue…", "Première fois : ça peut prendre un moment.")
        }
    }
}

@Composable
private fun StatusView(title: String, subtitle: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Wordmark(28)
        Spacer(Modifier.height(28.dp))
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
        action?.let { Spacer(Modifier.height(20.dp)); it() }
    }
}

@Composable
private fun Catalog(
    pl: LoadedPlaylist,
    sourceId: String,
    sourceName: String,
    onPlay: (String, List<Channel>, Int) -> Unit,
    onRefresh: () -> Unit,
    onAccount: () -> Unit,
) {
    var section by remember { mutableStateOf(Section.TV) }
    var groupIndex by remember { mutableIntStateOf(0) }

    val items: List<Channel> = when (section) {
        Section.TV -> pl.live
        Section.MOVIES -> pl.movies
        Section.SERIES -> pl.series.map {
            Channel(id = it.id, name = it.name, url = "", logo = it.cover, group = it.group, kind = MediaKind.SERIES)
        }
    }
    val groups = remember(items) {
        listOf<String?>(null) + items.map { it.group ?: UNCATEGORIZED }.distinct()
    }
    val group = groups.getOrElse(groupIndex) { null }
    val visible = remember(items, group) {
        if (group == null) items else items.filter { (it.group ?: UNCATEGORIZED) == group }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Wordmark(22)
            Spacer(Modifier.width(20.dp))
            Text(sourceName, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Rafraîchir") }
            IconButton(onClick = onAccount) { Icon(Icons.Default.AccountCircle, "Compte") }
        }

        Spacer(Modifier.height(18.dp))

        TabRow(
            selectedTabIndex = section.ordinal,
            modifier = Modifier.width(360.dp),
        ) {
            Section.entries.forEach { s ->
                Tab(
                    selected = s == section,
                    onFocus = { section = s; groupIndex = 0 },
                    onClick = { section = s; groupIndex = 0 },
                ) {
                    Text(s.label, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (groups.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(groups.size) { i ->
                    Chip(
                        label = groups[i] ?: "Tout",
                        selected = i == groupIndex,
                        onClick = { groupIndex = i },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Rien ici.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 40.dp),
            ) {
                items(visible, key = { it.id }) { ch ->
                    PosterCard(ch) {
                        if (ch.kind == MediaKind.SERIES) return@PosterCard
                        val queue = if (ch.kind == MediaKind.LIVE) visible else listOf(ch)
                        onPlay(sourceId, queue, queue.indexOf(ch).coerceAtLeast(0))
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Text(
            label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PosterCard(ch: Channel, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = 1.06f),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
            ),
        ),
    ) {
        Column(Modifier.width(160.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (ch.kind == MediaKind.LIVE) 16f / 9f else 2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (ch.logo.isNullOrEmpty()) {
                    Text(ch.name.take(1), fontSize = 28.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .4f))
                } else {
                    AsyncImage(
                        model = ch.logo,
                        contentDescription = ch.name,
                        contentScale = if (ch.kind == MediaKind.LIVE) ContentScale.Fit else ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().padding(if (ch.kind == MediaKind.LIVE) 12.dp else 0.dp),
                    )
                }
            }
            Text(
                ch.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
            )
        }
    }
}

@Composable
private fun Wordmark(size: Int) {
    Text(
        "NexoraTV",
        fontSize = size.sp,
        fontWeight = FontWeight.ExtraBold,
        style = androidx.compose.ui.text.TextStyle(brush = NexoraGradient),
    )
}
