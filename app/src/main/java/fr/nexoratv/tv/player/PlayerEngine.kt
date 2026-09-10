package fr.nexoratv.tv.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import okhttp3.OkHttpClient

/**
 * Construit l'ExoPlayer « façon IBOGOLD » :
 * - `NextRenderersFactory` = décodeurs **FFmpeg** (AC3/EAC3/DTS/TrueHD + vidéo
 *   de secours) en plus des décodeurs MediaCodec matériels ;
 * - `setEnableDecoderFallback` = repli logiciel si le décodeur matériel cale ;
 * - `DefaultLoadControl` avec de gros tampons (encaisse les à-coups réseau
 *   et les panneaux qui limitent le débit) ;
 * - HTTP via OkHttp, redirections http/https autorisées (courant en IPTV).
 */
object PlayerEngine {

    fun build(context: Context, http: OkHttpClient): ExoPlayer {
        val renderers = NextRenderersFactory(context).apply {
            // MODE_ON (pas PREFER) : la vidéo passe par le décodeur MATÉRIEL
            // (MediaCodec) ; FFmpeg ne sert qu'en secours et pour les codecs
            // audio non gérés (AC3/EAC3/DTS…). PREFER faisait décoder la vidéo
            // en logiciel -> saccades en FHD sur Fire TV Stick.
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            setEnableDecoderFallback(true)
        }

        val httpFactory = OkHttpDataSource.Factory(http)
            .setUserAgent("NexoraTV/1.0 (Android)")
        val dataSource = DefaultDataSource.Factory(context, httpFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 30_000,
                /* maxBufferMs = */ 120_000,
                /* bufferForPlaybackMs = */ 2_500,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000,
            )
            .setBackBuffer(30_000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    // Tunneling désactivé : fragile sur Fire TV Stick
                    // (écran noir / vidéo qui saute sur certains flux).
                    .setTunnelingEnabled(false)
                    .setPreferredAudioLanguage("fra")
            )
        }

        return ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    fun mediaItem(url: String, isLive: Boolean): MediaItem {
        val b = MediaItem.Builder().setUri(url)
        if (isLive) {
            b.setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(4_000)
                    .build()
            )
            if (url.contains(".m3u8")) b.setMimeType(MimeTypes.APPLICATION_M3U8)
            else b.setMimeType(MimeTypes.VIDEO_MP2T)
        }
        return b.build()
    }

    /** Une erreur de flux live vaut le coup d'être retentée (vs. VOD). */
    fun isRecoverable(e: PlaybackException): Boolean = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> true
        else -> false
    }

    fun stateName(state: Int) = when (state) {
        Player.STATE_IDLE -> "idle"
        Player.STATE_BUFFERING -> "buffering"
        Player.STATE_READY -> "ready"
        Player.STATE_ENDED -> "ended"
        else -> "?"
    }
}
