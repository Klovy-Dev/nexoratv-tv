package fr.nexoratv.tv.player

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.Text
import fr.nexoratv.tv.core.Net
import fr.nexoratv.tv.core.model.Channel
import fr.nexoratv.tv.core.model.MediaKind
import fr.nexoratv.tv.ui.theme.NexoraTheme
import kotlinx.coroutines.delay

@UnstableApi
class PlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val queue = PlayerQueue.items
        if (queue.isEmpty()) { finish(); return }

        setContent {
            NexoraTheme {
                PlayerScreen(
                    items = queue,
                    startIndex = PlayerQueue.startIndex,
                    onExit = { finish() },
                )
            }
        }
    }
}

@UnstableApi
@Composable
private fun PlayerScreen(
    items: List<Channel>,
    startIndex: Int,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    var index by remember { mutableStateOf(startIndex) }
    val current = items[index.coerceIn(0, items.lastIndex)]
    val isLive = current.kind == MediaKind.LIVE

    var controlsVisible by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastPos by remember { mutableLongStateOf(0L) }
    var stalledTicks by remember { mutableStateOf(0) }

    val exo = remember {
        PlayerEngine.build(context, Net.http).apply {
            playWhenReady = true
        }
    }

    // Ouvre le média courant.
    LaunchedEffect(index) {
        error = null
        buffering = true
        stalledTicks = 0
        exo.setMediaItem(PlayerEngine.mediaItem(current.url, isLive))
        exo.prepare()
        exo.play()
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(v: Boolean) { playing = v }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED && isLive) {
                    // Un live ne "finit" jamais : le serveur a coupé -> on relance.
                    exo.seekToDefaultPosition(); exo.prepare(); exo.play()
                }
            }
            override fun onPlayerError(e: PlaybackException) {
                if (isLive && PlayerEngine.isRecoverable(e)) {
                    exo.prepare(); exo.play()
                } else {
                    error = e.localizedMessage ?: "Lecture impossible"
                }
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    // Masquage auto des commandes.
    LaunchedEffect(controlsVisible, playing) {
        if (controlsVisible && playing && error == null) {
            delay(4500)
            controlsVisible = false
        }
    }

    // Chien de garde live : position figée -> on relance.
    LaunchedEffect(isLive) {
        if (!isLive) return@LaunchedEffect
        while (true) {
            delay(4000)
            if (error != null) { stalledTicks = 0; continue }
            val pos = exo.currentPosition
            if (playing && pos <= lastPos) {
                stalledTicks++
                if (stalledTicks >= 3) {
                    stalledTicks = 0
                    exo.prepare(); exo.play()
                }
            } else stalledTicks = 0
            lastPos = pos
        }
    }

    fun show() { controlsVisible = true }
    fun zap(delta: Int) {
        if (items.size < 2) return
        index = ((index + delta) % items.size + items.size) % items.size
        show()
    }
    fun seek(ms: Long) {
        if (!isLive) { exo.seekTo((exo.currentPosition + ms).coerceAtLeast(0)); show() }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key.nativeKeyCode) {
                    KeyEvent.KEYCODE_BACK -> {
                        if (controlsVisible) { controlsVisible = false; true } else { onExit(); true }
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (!controlsVisible) show() else exo.playWhenReady = !exo.playWhenReady
                        show(); true
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                        if (!controlsVisible && isLive) { zap(-1); true } else { show(); false }
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        if (!controlsVisible && isLive) { zap(1); true } else { show(); false }
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        if (!controlsVisible) { seek(10_000); true } else { show(); false }
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        if (!controlsVisible) { seek(-10_000); true } else { show(); false }
                    }
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setKeepContentOnPlayerReset(true)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = exo
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (buffering && error == null) {
            Text(
                "Chargement…",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        error?.let { msg ->
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Lecture impossible", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(msg, color = Color.White.copy(alpha = .7f))
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ControlsBar(
                title = current.name,
                subtitle = "${index + 1} / ${items.size}" + (current.group?.let { "  ·  $it" } ?: ""),
                playing = playing,
                canZap = items.size > 1,
                onPlayPause = { exo.playWhenReady = !exo.playWhenReady; show() },
                onPrev = { zap(-1) },
                onNext = { zap(1) },
                onTracks = { /* TODO M1.1 : feuille pistes */ },
            )
        }
    }

    // Pause quand l'app passe en arrière-plan (abonnements 1 connexion).
    val lifecycle = (context as ComponentActivity).lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> exo.pause()
                Lifecycle.Event.ON_START -> exo.play()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
}

@Composable
private fun ControlsBar(
    title: String,
    subtitle: String,
    playing: Boolean,
    canZap: Boolean,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTracks: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = .55f))
            .padding(horizontal = 32.dp, vertical = 20.dp),
    ) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(subtitle, color = Color.White.copy(alpha = .7f), fontSize = 13.sp, maxLines = 1)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canZap) IconButton(onClick = onPrev) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Précédent")
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Lecture / pause",
                )
            }
            if (canZap) IconButton(onClick = onNext) {
                Icon(Icons.Default.SkipNext, contentDescription = "Suivant")
            }
            IconButton(onClick = onTracks) {
                Icon(Icons.Default.Tune, contentDescription = "Pistes")
            }
        }
    }
}

/**
 * Touches télécommande sur la zone vidéo. Commandes masquées : flèches =
 * zap (live) / avance (VOD). Commandes visibles : on laisse le focus Compose
 * naviguer les boutons (on ne consomme que Play/Pause et Retour).
 */
private fun Modifier.onPlayerKey(
    isLive: Boolean,
    controlsVisible: Boolean,
    onShow: () -> Unit,
    onHide: () -> Unit,
    onExit: () -> Unit,
    onPlayPause: () -> Unit,
    onZapUp: () -> Unit,
    onZapDown: () -> Unit,
    onSeek: (Long) -> Unit,
): Modifier = this.then(
    Modifier.androidx.compose.ui.input.key.onKeyEvent { event ->
        if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
        when (event.nativeKeyEvent.keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (controlsVisible) { onHide(); true } else { onExit(); true }
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (!controlsVisible) { onShow(); true } else { onPlayPause(); true }
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                if (!controlsVisible && isLive) { onZapUp(); true } else { onShow(); false }
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (!controlsVisible && isLive) { onZapDown(); true } else { onShow(); false }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (!controlsVisible) { onSeek(10_000); true } else { onShow(); false }
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (!controlsVisible) { onSeek(-10_000); true } else { onShow(); false }
            }
            else -> false
        }
    },
)
