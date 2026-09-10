package fr.nexoratv.tv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.nexoratv.tv.core.model.Channel
import fr.nexoratv.tv.player.PlayerActivity
import fr.nexoratv.tv.player.PlayerQueue
import fr.nexoratv.tv.ui.ConnectScreen
import fr.nexoratv.tv.ui.HomeScreen
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

    // Sur l'écran Connexion : Retour revient à l'accueil si un compte existe.
    BackHandler(enabled = screen == Screen.Connect && sources.isNotEmpty()) {
        vm.goHome()
    }

    when (screen) {
        Screen.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator()
        }
        Screen.Connect -> ConnectScreen(vm)
        Screen.Home -> HomeScreen(vm, onPlay)
    }
}
