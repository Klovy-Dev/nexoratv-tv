package fr.nexoratv.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import fr.nexoratv.tv.AppViewModel
import fr.nexoratv.tv.CatalogState
import fr.nexoratv.tv.core.model.Channel
import fr.nexoratv.tv.core.model.LoadedPlaylist
import fr.nexoratv.tv.core.model.MediaKind
import fr.nexoratv.tv.core.model.UNCATEGORIZED

private enum class Tab(val label: String) { TV("TV"), MOVIES("Films"), SERIES("Séries") }

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onPlay: (sourceId: String, queue: List<Channel>, startIndex: Int) -> Unit,
) {
    val state by vm.catalogState.collectAsState()
    val sourceId = vm.currentSource?.id ?: ""

    when (val s = state) {
        is CatalogState.Ready -> Catalog(s.playlist, sourceId, onPlay) { vm.loadCatalog(true) }
        is CatalogState.Error -> Center {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Chargement impossible", style = MaterialTheme.typography.titleMedium)
                Text(s.message, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
                Button(onClick = { vm.loadCatalog(true) }, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Réessayer")
                }
            }
        }
        else -> Center {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text("Chargement du catalogue…", modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}

@Composable
private fun Catalog(
    pl: LoadedPlaylist,
    sourceId: String,
    onPlay: (String, List<Channel>, Int) -> Unit,
    onRefresh: () -> Unit,
) {
    var tab by remember { mutableStateOf(Tab.TV) }
    var group by remember { mutableStateOf<String?>(null) }

    val items: List<Channel> = when (tab) {
        Tab.TV -> pl.live
        Tab.MOVIES -> pl.movies
        Tab.SERIES -> pl.series.map { s ->
            // On réutilise Channel comme vignette (les séries s'ouvriront en
            // détail plus tard ; pour l'instant elles ne sont pas jouables).
            Channel(id = s.id, name = s.name, url = "", logo = s.cover, group = s.group, kind = MediaKind.SERIES)
        }
    }

    val groups = remember(items) {
        buildList {
            add(null)
            items.map { it.group ?: UNCATEGORIZED }.distinct().forEach { add(it) }
        }
    }
    val visible = remember(items, group) {
        if (group == null) items else items.filter { (it.group ?: UNCATEGORIZED) == group }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { t ->
                Button(
                    onClick = { tab = t; group = null },
                    colors = if (t == tab) ButtonDefaults.colors()
                    else ButtonDefaults.colors(containerColor = Color.Transparent),
                ) { Text(t.label) }
            }
            Box(Modifier.weight(1f))
            Button(onClick = onRefresh) { Text("Rafraîchir") }
        }

        Row(Modifier.fillMaxSize().padding(top = 16.dp)) {
            LazyColumn(
                modifier = Modifier.width(220.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(groups) { g ->
                    val label = g ?: "Tout"
                    Button(
                        onClick = { group = g },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (g == group) ButtonDefaults.colors()
                        else ButtonDefaults.colors(containerColor = Color.Transparent),
                    ) {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier.fillMaxSize().padding(start = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
private fun PosterCard(ch: Channel, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        border = CardDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.border),
                shape = RoundedCornerShape(10.dp),
            ),
        ),
    ) {
        Column(Modifier.width(150.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (ch.kind == MediaKind.LIVE) 16f / 9f else 2f / 3f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (ch.logo != null) {
                    AsyncImage(
                        model = ch.logo,
                        contentDescription = ch.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Text(
                ch.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) { content() }
}
