package fr.nexoratv.tv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.nexoratv.tv.core.model.Channel
import fr.nexoratv.tv.player.PlayerActivity
import fr.nexoratv.tv.player.PlayerQueue
import fr.nexoratv.tv.ui.CatalogScreen
import fr.nexoratv.tv.ui.ConnectScreen
import fr.nexoratv.tv.ui.ErrorScreen
import fr.nexoratv.tv.ui.HubScreen
import fr.nexoratv.tv.ui.LoadingScreen
import fr.nexoratv.tv.ui.SettingsScreen
import fr.nexoratv.tv.ui.theme.NexoraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NexoraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Root(onPlay = ::openPlayer)
                }
            }
        }
    }

    private fun openPlayer(sourceId: String, queue: List<Channel>, startIndex: Int) {
        PlayerQueue.set(sourceId, queue, startIndex)
        startActivity(Intent(this, PlayerActivity::class.java))
    }
}

@Composable
private fun Root(onPlay: (sourceId: String, queue: List<Channel>, startIndex: Int) -> Unit) {
    val vm: AppViewModel = viewModel()
    val screen by vm.screen.collectAsState()
    val sources by vm.sources.collectAsState()
    val catalog by vm.catalogState.collectAsState()
    val loadProgress by vm.loadProgress.collectAsState()
    val update by vm.updateState.collectAsState()

    BackHandler(enabled = screen is Screen.Catalog || screen == Screen.Settings) { vm.goHome() }
    BackHandler(enabled = screen == Screen.Connect && sources.isNotEmpty()) { vm.goHome() }

    val ready = catalog as? CatalogState.Ready

    when (val s = screen) {
        Screen.Loading -> LoadingScreen(progress = null, expectVod = true)

        Screen.Connect -> ConnectScreen(vm)

        Screen.Settings -> SettingsScreen(
            deviceMac = vm.deviceMac,
            sourceName = vm.currentSource?.name ?: "—",
            expiresAt = ready?.playlist?.expiresAt,
            update = update,
            onCheckUpdate = vm::checkUpdate,
            onInstall = vm::installUpdate,
            onLaunchInstall = vm::launchInstall,
            onChangePlaylist = vm::goConnect,
            onBack = vm::goHome,
        )

        is Screen.Catalog ->
            if (ready != null) CatalogScreen(
                pl = ready.playlist,
                section = s.section,
                sourceId = vm.currentSource?.id ?: "",
                sourceName = vm.currentSource?.name ?: "NexoraTV",
                onPlay = onPlay,
                onBack = vm::goHome,
            ) else LoadingScreen(progress = loadProgress, expectVod = vm.loadExpectVod)

        Screen.Home -> when (val cs = catalog) {
            is CatalogState.Ready -> HubScreen(
                sourceName = vm.currentSource?.name ?: "NexoraTV",
                liveCount = cs.playlist.live.size,
                movieCount = cs.playlist.movies.size,
                seriesCount = cs.playlist.series.size,
                expiresAt = cs.playlist.expiresAt,
                onOpenSection = vm::openSection,
                onSettings = vm::openSettings,
            )
            is CatalogState.Error -> ErrorScreen(cs.message) { vm.loadCatalog(true) }
            else -> LoadingScreen(progress = loadProgress, expectVod = vm.loadExpectVod)
        }
    }
}
