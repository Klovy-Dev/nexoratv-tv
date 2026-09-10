package fr.nexoratv.tv.player

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import fr.nexoratv.tv.core.Net
import fr.nexoratv.tv.core.model.Channel
import fr.nexoratv.tv.core.model.MediaKind
import fr.nexoratv.tv.ui.theme.NexoraTheme
import kotlinx.coroutines.delay

class PlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (PlayerQueue.items.isEmpty()) { finish(); return }
        setContent {
            NexoraTheme {
                PlayerScreen(PlayerQueue.items, PlayerQueue.startIndex) { finish() }
            }
        }
    }
}

@Composable
private fun PlayerScreen(items: List<Channel>, startIndex: Int, onExit: () -> Unit) {
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, items.lastIndex)) }
    val current = items[index]
    val isLive = current.kind == MediaKind.LIVE

    var controlsVisible by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tracksOpen by remember { mutableStateOf(false) }
    var interaction by remember { mutableLongStateOf(0L) }
    var lastCheckedPos by remember { mutableLongStateOf(0L) }
    var stalledChecks by remember { mutableIntStateOf(0) }
    var tracks by remember { mutableStateOf<Tracks>(Tracks.EMPTY) }

    val exo = remember { PlayerEngine.build(context, Net.http).apply { playWhenReady = true } }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }

    fun bump() { interaction = System.currentTimeMillis() }
    fun show() { controlsVisible = true; bump() }
    fun hide() { controlsVisible = false; rootFocus.requestFocus() }

    LaunchedEffect(index) {
        error = null; buffering = true; stalledChecks = 0
        exo.setMediaItem(PlayerEngine.mediaItem(current.url, isLive))
        exo.prepare(); exo.play()
    }

    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(v: Boolean) { playing = v }
            override fun onPlaybackStateChanged(s: Int) {
                buffering = s == Player.STATE_BUFFERING
                if (s == Player.STATE_ENDED && isLive) { exo.seekToDefaultPosition(); exo.prepare(); exo.play() }
            }
            override fun onPlayerError(e: PlaybackException) {
                if (isLive && PlayerEngine.isRecoverable(e)) { exo.prepare(); exo.play() }
                else error = e.localizedMessage ?: "Lecture impossible"
            }
            override fun onTracksChanged(t: Tracks) { tracks = t }
        }
        exo.addListener(l)
        onDispose { exo.removeListener(l); exo.release() }
    }

    // Masquage auto (5 s après la dernière interaction).
    LaunchedEffect(controlsVisible, playing, interaction) {
        if (controlsVisible && playing && error == null && !tracksOpen) {
            delay(5000); if (System.currentTimeMillis() - interaction >= 4800) hide()
        }
    }
    // Focus sur Lecture/Pause quand la barre s'affiche.
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) { delay(50); runCatching { playFocus.requestFocus() } }
    }
    LaunchedEffect(Unit) { delay(100); runCatching { rootFocus.requestFocus() } }

    // Chien de garde live.
    LaunchedEffect(isLive, index) {
        if (!isLive) return@LaunchedEffect
        while (true) {
            delay(4000)
            if (error != null) { stalledChecks = 0; continue }
            val p = exo.currentPosition
            if (playing && p <= lastCheckedPos) {
                if (++stalledChecks >= 3) { stalledChecks = 0; exo.prepare(); exo.play() }
            } else stalledChecks = 0
            lastCheckedPos = p
        }
    }

    fun zap(d: Int) { if (items.size > 1) { index = ((index + d) % items.size + items.size) % items.size; show() } }
    fun seek(ms: Long) { if (!isLive) { exo.seekTo((exo.currentPosition + ms).coerceAtLeast(0)); show() } }
    fun togglePlay() { exo.playWhenReady = !exo.playWhenReady; show() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val k = ev.key.nativeKeyCode
                if (tracksOpen) {
                    if (k == KeyEvent.KEYCODE_BACK) { tracksOpen = false; show(); return@onPreviewKeyEvent true }
                    return@onPreviewKeyEvent false
                }
                when (k) {
                    KeyEvent.KEYCODE_BACK -> {
                        if (controlsVisible) hide() else onExit(); true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { togglePlay(); true }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> { exo.play(); show(); true }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> { exo.pause(); show(); true }
                    KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { zap(-1); true }
                    KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT -> { zap(1); true }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seek(10_000); true }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> { seek(-10_000); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (!controlsVisible) { show(); true } else { bump(); false }
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> if (!controlsVisible) {
                        if (isLive) zap(-1) else show(); true
                    } else { bump(); false }
                    KeyEvent.KEYCODE_DPAD_DOWN -> if (!controlsVisible) {
                        if (isLive) zap(1) else show(); true
                    } else { bump(); false }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> if (!controlsVisible) { seek(10_000); true } else { bump(); false }
                    KeyEvent.KEYCODE_DPAD_LEFT -> if (!controlsVisible) { seek(-10_000); true } else { bump(); false }
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
            Text("Chargement…", color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
        error?.let {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Lecture impossible", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(it, color = Color.White.copy(alpha = .7f))
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && !tracksOpen,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ControlsBar(
                title = current.name,
                subtitle = "${index + 1} / ${items.size}" + (current.group?.let { "  ·  $it" } ?: ""),
                playing = playing,
                canZap = items.size > 1,
                playFocus = playFocus,
                onBack = onExit,
                onPlayPause = ::togglePlay,
                onPrev = { zap(-1) },
                onNext = { zap(1) },
                onTracks = { tracksOpen = true; bump() },
            )
        }

        if (tracksOpen) {
            TracksPanel(
                tracks = tracks,
                onSelect = { override ->
                    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                        .clearOverridesOfType(override.type)
                        .addOverride(override)
                        .build()
                    tracksOpen = false; show()
                },
                onClose = { tracksOpen = false; show() },
            )
        }
    }

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
    playFocus: FocusRequester,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTracks: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = .6f))
            .padding(horizontal = 40.dp, vertical = 24.dp),
    ) {
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(subtitle, color = Color.White.copy(alpha = .7f), fontSize = 13.sp, maxLines = 1)
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Retour") }
            if (canZap) IconButton(onClick = onPrev) { Icon(Icons.Default.SkipPrevious, "Précédent") }
            IconButton(onClick = onPlayPause, modifier = Modifier.focusRequester(playFocus)) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Lecture / pause")
            }
            if (canZap) IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, "Suivant") }
            IconButton(onClick = onTracks) { Icon(Icons.Default.Tune, "Pistes") }
        }
    }
}

@Composable
private fun TracksPanel(
    tracks: Tracks,
    onSelect: (TrackSelectionOverride) -> Unit,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .7f)), Alignment.CenterEnd) {
        Surface(
            modifier = Modifier.width(420.dp).fillMaxSize().padding(24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = androidx.tv.material3.SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Pistes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
                var first = true
                tracks.groups.forEach { g ->
                    if (g.type != C.TRACK_TYPE_AUDIO && g.type != C.TRACK_TYPE_TEXT) return@forEach
                    val header = if (g.type == C.TRACK_TYPE_AUDIO) "Audio" else "Sous-titres"
                    Text(header.uppercase(), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
                        fontSize = 12.sp, modifier = Modifier.padding(top = if (first) 0.dp else 12.dp, bottom = 4.dp))
                    first = false
                    for (i in 0 until g.length) {
                        val f = g.getTrackFormat(i)
                        val label = f.label ?: f.language ?: "Piste ${i + 1}"
                        ListItem(
                            selected = g.isTrackSelected(i),
                            onClick = { onSelect(TrackSelectionOverride(g.mediaTrackGroup, i)) },
                            headlineContent = { Text(label) },
                        )
                    }
                }
                androidx.tv.material3.Button(
                    onClick = onClose,
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text("Fermer") }
            }
        }
    }
}
