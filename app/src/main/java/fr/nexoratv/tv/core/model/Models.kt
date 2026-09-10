package fr.nexoratv.tv.core.model

import java.util.UUID

/** Nature d'un flux : chaîne en direct, film (VOD) ou épisode de série. */
enum class MediaKind { LIVE, MOVIE, SERIES }

/** Type de source IPTV. */
enum class SourceKind { M3U_URL, XTREAM }

/** Format demandé au serveur Xtream pour les flux live. */
enum class XtreamOutput(val ext: String) { TS("ts"), M3U8("m3u8") }

/** Une source enregistrée : playlist M3U (URL) ou compte Xtream Codes. */
data class PlaylistSource(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: SourceKind,
    val m3uUrl: String? = null,
    val epgUrl: String? = null,
    /** Inclut schéma + port, ex. `http://exemple.com:8080`. */
    val host: String? = null,
    val username: String? = null,
    val password: String? = null,
    val xtreamOutput: XtreamOutput = XtreamOutput.TS,
    /** Non nul si ajoutée par activation MAC (re-résolue à chaque chargement). */
    val activationMac: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isXtream get() = kind == SourceKind.XTREAM
}

/** Un élément lisible : chaîne TV en direct ou contenu VOD. */
data class Channel(
    val id: String,
    val name: String,
    val url: String,
    val number: Int? = null,
    val logo: String? = null,
    val group: String? = null,
    val epgChannelId: String? = null,
    val streamId: String? = null,
    val kind: MediaKind = MediaKind.LIVE,
    val rating: Double? = null,
    val year: Int? = null,
    val addedAt: Long? = null,
    val plot: String? = null,
    val genre: String? = null,
    /** `container_extension` Xtream (mp4/mkv/avi) pour reconstruire l'URL VOD. */
    val containerExt: String? = null,
) {
    val groupOrDefault: String get() = group ?: UNCATEGORIZED
    val isLive: Boolean get() = kind == MediaKind.LIVE
}

/** Une série (catalogue seul ; saisons/épisodes chargés à la demande). */
data class Series(
    val id: String,
    val seriesId: String,
    val name: String,
    val cover: String? = null,
    val backdrop: String? = null,
    val group: String? = null,
    val plot: String? = null,
    val genre: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val rating: Double? = null,
    val year: Int? = null,
    val addedAt: Long? = null,
) {
    val groupOrDefault: String get() = group ?: UNCATEGORIZED
}

/** Un épisode d'une série (résolu via `get_series_info`). */
data class Episode(
    val id: String,
    val episodeId: String,
    val title: String,
    val season: Int,
    val episode: Int,
    val url: String,
    val plot: String? = null,
    val cover: String? = null,
    val durationSecs: Int? = null,
)

/** Résultat du chargement d'une source : direct + films + séries. */
data class LoadedPlaylist(
    val live: List<Channel> = emptyList(),
    val movies: List<Channel> = emptyList(),
    val series: List<Series> = emptyList(),
    /** Fin d'abonnement (epoch ms) si le serveur la fournit (Xtream). */
    val expiresAt: Long? = null,
) {
    val isEmpty get() = live.isEmpty() && movies.isEmpty() && series.isEmpty()
}

/**
 * Avancement du chargement d'un catalogue, pour l'écran de démarrage.
 * Un compteur non nul = catégorie terminée ; `connected` = serveur joint.
 */
data class LoadProgress(
    val connected: Boolean = false,
    val live: Int? = null,
    val movies: Int? = null,
    val series: Int? = null,
)

const val UNCATEGORIZED = "Non classé"
