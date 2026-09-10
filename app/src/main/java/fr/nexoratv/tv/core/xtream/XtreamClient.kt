package fr.nexoratv.tv.core.xtream

import fr.nexoratv.tv.core.model.Channel
import fr.nexoratv.tv.core.model.Episode
import fr.nexoratv.tv.core.model.LoadedPlaylist
import fr.nexoratv.tv.core.model.MediaKind
import fr.nexoratv.tv.core.model.PlaylistSource
import fr.nexoratv.tv.core.model.Series
import fr.nexoratv.tv.core.model.XtreamOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

class XtreamException(message: String) : Exception(message)

data class XtreamAccount(
    val status: String?,
    val expiresAt: Long?,
    val isTrial: Boolean,
    val maxConnections: Int?,
    val activeConnections: Int?,
)

/**
 * Client de l'API Xtream Codes (`player_api.php`). Beaucoup de panneaux
 * renvoient le JSON avec un `Content-Type` erroné : on récupère la réponse en
 * texte brut et on la parse nous-mêmes. Tout le travail lourd est en
 * `Dispatchers.Default` (jamais sur le thread UI).
 */
class XtreamClient(
    private val source: PlaylistSource,
    private val http: OkHttpClient,
    private val log: (String) -> Unit = {},
) {
    init {
        require(source.isXtream) { "source non-Xtream" }
    }

    private val host = source.host!!.trimEnd('/')
    private val user = source.username!!
    private val pass = source.password!!
    private val liveExt = if (source.xtreamOutput == XtreamOutput.M3U8) "m3u8" else "ts"

    private fun api(params: Map<String, String>): String {
        val b = "$host/player_api.php".toHttpUrl().newBuilder()
            .addQueryParameter("username", user)
            .addQueryParameter("password", pass)
        params.forEach { (k, v) -> b.addQueryParameter(k, v) }
        return b.build().toString()
    }

    private suspend fun getRaw(params: Map<String, String>): String = withContext(Dispatchers.IO) {
        val action = params["action"] ?: "auth"
        val req = Request.Builder().url(api(params))
            .header("User-Agent", USER_AGENT)
            .build()
        try {
            http.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                log("$action : HTTP ${res.code}, ${body.length} octets")
                if (body.isBlank()) throw XtreamException("Réponse vide du serveur.")
                val head = body.trimStart().take(1)
                if (head == "<") {
                    throw XtreamException(
                        "Le serveur a renvoyé une page web au lieu de données — " +
                            "souvent la limite de connexions simultanées de " +
                            "l'abonnement, ou un blocage temporaire. Ferme les " +
                            "autres lectures et réessaie."
                    )
                }
                body
            }
        } catch (e: XtreamException) {
            throw e
        } catch (e: Exception) {
            log("$action : ERREUR RÉSEAU ${e.message}")
            throw XtreamException("Connexion au serveur impossible : ${e.message}")
        }
    }

    private suspend fun getJson(params: Map<String, String>): Any = withContext(Dispatchers.Default) {
        val raw = getRaw(params)
        try {
            JSONTokener(raw).nextValue()
        } catch (_: Exception) {
            throw XtreamException("Réponse illisible du serveur (JSON invalide).")
        }
    }

    // ---------------------------------------------------------------- Auth
    suspend fun authenticate(): XtreamAccount {
        val data = getJson(emptyMap())
        val info = (data as? JSONObject)?.optJSONObject("user_info")
            ?: throw XtreamException("Serveur inattendu (pas une API Xtream Codes ?).")
        val auth = info.opt("auth")
        if (auth == 0 || auth == "0") throw XtreamException("Identifiant ou mot de passe incorrect.")
        val statusRaw = info.optString("status", "")
        if (statusRaw.isNotEmpty() && !statusRaw.equals("active", true)) {
            throw XtreamException("Compte $statusRaw.")
        }
        val exp = parseEpoch(info.optString("exp_date"))
        if (exp != null && exp < System.currentTimeMillis()) {
            throw XtreamException("Abonnement expiré.")
        }
        return XtreamAccount(
            status = statusRaw.ifEmpty { null },
            expiresAt = exp,
            isTrial = info.optString("is_trial") == "1",
            maxConnections = info.optString("max_connections").toIntOrNull(),
            activeConnections = info.optString("active_cons").toIntOrNull(),
        )
    }

    // --------------------------------------------------------- Chargement
    suspend fun loadAll(): LoadedPlaylist = coroutineScope {
        authenticate()
        // Live d'abord (fait échouer vite si le compte pose problème), puis
        // films + séries en parallèle.
        val live = fetchStreams("get_live_categories", "get_live_streams", "live", liveExt, MediaKind.LIVE)
        if (live.isEmpty()) throw XtreamException("Aucune chaîne renvoyée par le serveur.")
        val moviesJob = async {
            runCatching {
                fetchStreams("get_vod_categories", "get_vod_streams", "movie", null, MediaKind.MOVIE)
            }.onFailure { log("films : échec — ${it.message}") }.getOrDefault(emptyList())
        }
        val seriesJob = async {
            runCatching { fetchSeries() }
                .onFailure { log("séries : échec — ${it.message}") }
                .getOrDefault(emptyList())
        }
        val movies = moviesJob.await()
        val series = seriesJob.await()
        log("terminé : ${live.size} chaînes · ${movies.size} films · ${series.size} séries")
        LoadedPlaylist(live, movies, series)
    }

    private suspend fun fetchStreams(
        catAction: String,
        streamsAction: String,
        segment: String,
        ext: String?,
        kind: MediaKind,
    ): List<Channel> = withContext(Dispatchers.Default) {
        val cats = asArray(getJson(mapOf("action" to catAction)))
        val names = categoryNames(cats)
        val rank = categoryRank(cats)
        val streams = asArray(getJson(mapOf("action" to streamsAction)))

        val out = ArrayList<Channel>(streams.length())
        for (i in 0 until streams.length()) {
            val s = streams.optJSONObject(i) ?: continue
            val sid = s.opt("stream_id")?.toString().orEmpty()
            if (sid.isEmpty() || sid == "null") continue
            val container = s.optString("container_extension").ifEmpty { "mp4" }
            val effExt = ext ?: container
            val name = s.optString("name").trim().ifEmpty { "Sans nom" }
            out += Channel(
                id = "${segment}_$sid",
                name = name,
                url = "$host/$segment/$user/$pass/$sid.$effExt",
                number = s.optString("num").toIntOrNull(),
                logo = nullIfEmpty(s.optString("stream_icon")),
                group = names[s.opt("category_id")?.toString()] ?: "Non classé",
                epgChannelId = nullIfEmpty(s.optString("epg_channel_id")),
                streamId = sid,
                kind = kind,
                rating = parseRating(s.opt("rating")),
                year = parseYear(s.optString("year").ifEmpty { s.optString("releaseDate") }),
                addedAt = parseEpoch(s.optString("added")),
                plot = if (kind == MediaKind.MOVIE)
                    nullIfEmpty(firstNonEmpty(s, "plot", "description", "overview")) else null,
                genre = if (kind == MediaKind.MOVIE) nullIfEmpty(s.optString("genre")) else null,
                containerExt = if (kind == MediaKind.MOVIE) container else null,
            )
        }
        sortByCategory(out, rank, names) { it.groupOrDefault }
        out
    }

    private suspend fun fetchSeries(): List<Series> = withContext(Dispatchers.Default) {
        val cats = asArray(getJson(mapOf("action" to "get_series_categories")))
        val names = categoryNames(cats)
        val rank = categoryRank(cats)
        val list = asArray(getJson(mapOf("action" to "get_series")))
        val out = ArrayList<Series>(list.length())
        for (i in 0 until list.length()) {
            val s = list.optJSONObject(i) ?: continue
            val sid = s.opt("series_id")?.toString().orEmpty()
            if (sid.isEmpty() || sid == "null") continue
            out += Series(
                id = "series_$sid",
                seriesId = sid,
                name = s.optString("name").trim().ifEmpty { "Sans nom" },
                cover = nullIfEmpty(s.optString("cover")),
                backdrop = firstBackdrop(s.opt("backdrop_path")),
                group = names[s.opt("category_id")?.toString()] ?: "Non classé",
                plot = nullIfEmpty(s.optString("plot")),
                genre = nullIfEmpty(s.optString("genre")),
                cast = nullIfEmpty(s.optString("cast")),
                director = nullIfEmpty(s.optString("director")),
                rating = parseRating(s.opt("rating")),
                year = parseYear(s.optString("releaseDate").ifEmpty { s.optString("release_date") }),
                addedAt = parseEpoch(s.optString("last_modified")),
            )
        }
        sortByCategory(out, rank, names) { it.groupOrDefault }
        out
    }

    /** Saisons/épisodes d'une série. */
    suspend fun loadEpisodes(seriesId: String): List<Episode> = withContext(Dispatchers.Default) {
        val data = getJson(mapOf("action" to "get_series_info", "series_id" to seriesId))
        val root = data as? JSONObject ?: return@withContext emptyList()
        val episodes = root.optJSONObject("episodes") ?: return@withContext emptyList()
        val out = ArrayList<Episode>()
        for (seasonKey in episodes.keys()) {
            val arr = episodes.optJSONArray(seasonKey) ?: continue
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val eid = e.opt("id")?.toString().orEmpty()
                if (eid.isEmpty()) continue
                val ext = e.optString("container_extension").ifEmpty { "mp4" }
                val infoObj = e.optJSONObject("info")
                out += Episode(
                    id = "ep_$eid",
                    episodeId = eid,
                    title = e.optString("title").ifEmpty { "Épisode ${e.optString("episode_num")}" },
                    season = seasonKey.toIntOrNull() ?: e.optString("season").toIntOrNull() ?: 1,
                    episode = e.optString("episode_num").toIntOrNull() ?: (i + 1),
                    url = "$host/series/$user/$pass/$eid.$ext",
                    plot = nullIfEmpty(infoObj?.optString("plot")),
                    cover = nullIfEmpty(infoObj?.optString("movie_image")),
                    durationSecs = infoObj?.optString("duration_secs")?.toIntOrNull(),
                )
            }
        }
        out.sortWith(compareBy({ it.season }, { it.episode }))
        out
    }

    // ------------------------------------------------------------ helpers
    private fun asArray(v: Any?): JSONArray = when (v) {
        is JSONArray -> v
        is JSONObject -> JSONArray().apply { v.keys().forEach { put(v.get(it)) } }
        else -> JSONArray()
    }

    private fun categoryNames(cats: JSONArray): Map<String, String> = buildMap {
        for (i in 0 until cats.length()) {
            val c = cats.optJSONObject(i) ?: continue
            val n = c.optString("category_name").trim().ifEmpty { "Non classé" }
            put(c.opt("category_id")?.toString().orEmpty(), n)
        }
    }

    private fun categoryRank(cats: JSONArray): Map<String, Int> = buildMap {
        for (i in 0 until cats.length()) {
            val c = cats.optJSONObject(i) ?: continue
            put(c.opt("category_id")?.toString().orEmpty(), i)
        }
    }

    /** Tri stable par ordre de catégorie de la source (non classées à la fin). */
    private fun <T> sortByCategory(
        items: MutableList<T>,
        rankById: Map<String, Int>,
        namesById: Map<String, String>,
        groupOf: (T) -> String,
    ) {
        if (rankById.isEmpty()) return
        val big = 1 shl 20
        val rankByName = HashMap<String, Int>()
        namesById.forEach { (id, name) -> rankByName.putIfAbsent(name, rankById[id] ?: big) }
        val indexed = items.mapIndexed { i, it -> it to i }
        val sorted = indexed.sortedWith(
            compareBy({ rankByName[groupOf(it.first)] ?: big }, { it.second })
        )
        for (i in items.indices) items[i] = sorted[i].first
    }

    private fun firstNonEmpty(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = o.optString(k)
            if (v.isNotEmpty() && v != "null") return v
        }
        return ""
    }

    private fun firstBackdrop(v: Any?): String? = when (v) {
        is JSONArray -> if (v.length() > 0) nullIfEmpty(v.optString(0)) else null
        else -> nullIfEmpty(v?.toString())
    }

    companion object {
        const val USER_AGENT =
            "NexoraTV/1.0 (Android) Dalvik/2.1.0"

        fun nullIfEmpty(v: String?): String? =
            if (v.isNullOrEmpty() || v == "null") null else v

        fun parseRating(v: Any?): Double? {
            val d = v?.toString()?.toDoubleOrNull() ?: return null
            if (d <= 0) return null
            return if (d > 10) d / 10 else d
        }

        private val YEAR = Regex("(19|20)\\d{2}")
        fun parseYear(v: String?): Int? =
            v?.let { YEAR.find(it)?.value?.toIntOrNull() }

        fun parseEpoch(v: String?): Long? {
            val ts = v?.toLongOrNull() ?: return null
            return if (ts <= 0) null else ts * 1000
        }
    }
}
