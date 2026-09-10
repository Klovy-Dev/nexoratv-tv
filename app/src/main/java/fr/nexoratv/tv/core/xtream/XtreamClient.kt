package fr.nexoratv.tv.core.xtream

import fr.nexoratv.tv.core.model.Channel
import fr.nexoratv.tv.core.model.Episode
import fr.nexoratv.tv.core.model.LoadProgress
import fr.nexoratv.tv.core.model.LoadedPlaylist
import fr.nexoratv.tv.core.model.MediaKind
import fr.nexoratv.tv.core.model.PlaylistSource
import fr.nexoratv.tv.core.model.Series
import fr.nexoratv.tv.core.model.XtreamOutput
import android.util.JsonReader
import android.util.JsonToken
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
import java.util.concurrent.TimeUnit

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

    /** Client dédié aux grosses listes (séries / VOD volumineuses sur des
     *  panneaux lents) : timeouts larges. */
    private val listHttp: OkHttpClient by lazy {
        http.newBuilder()
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(4, TimeUnit.MINUTES)
            .build()
    }

    /**
     * Récupère une action qui renvoie une liste d'objets, **en streaming**
     * (`JsonReader` branché sur le flux réseau). On ne charge jamais toute la
     * réponse en mémoire — c'est ce qui faisait planter en silence (OOM) les
     * gros `get_series` sur Fire TV Stick. Chaque objet → `Map<String,String>`.
     */
    private suspend fun getRows(params: Map<String, String>): List<Map<String, String>> =
        withContext(Dispatchers.IO) {
            val action = params["action"] ?: "?"
            val req = Request.Builder().url(api(params)).header("User-Agent", USER_AGENT).build()
            try {
                listHttp.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) throw XtreamException("$action : HTTP ${res.code}")
                    val body = res.body ?: throw XtreamException("$action : réponse vide")
                    val rows = ArrayList<Map<String, String>>()
                    JsonReader(body.charStream().buffered()).use { r ->
                        r.isLenient = true
                        readRowsInto(r, rows)
                    }
                    log("$action : ${rows.size} objets reçus")
                    rows
                }
            } catch (e: XtreamException) {
                throw e
            } catch (e: Throwable) {
                log("$action : échec — ${e.javaClass.simpleName} ${e.message ?: ""}")
                throw XtreamException("$action : ${e.message ?: e.javaClass.simpleName}")
            }
        }

    private fun readRowsInto(r: JsonReader, rows: MutableList<Map<String, String>>) {
        when (r.peek()) {
            JsonToken.BEGIN_ARRAY -> {
                r.beginArray()
                while (r.hasNext()) readOneRow(r, rows)
                r.endArray()
            }
            JsonToken.BEGIN_OBJECT -> {
                // panneau qui renvoie {"0":{…}} ou {"data":[…]}
                r.beginObject()
                while (r.hasNext()) {
                    r.nextName()
                    when (r.peek()) {
                        JsonToken.BEGIN_OBJECT -> readOneRow(r, rows)
                        JsonToken.BEGIN_ARRAY -> {
                            r.beginArray()
                            while (r.hasNext()) readOneRow(r, rows)
                            r.endArray()
                        }
                        else -> r.skipValue()
                    }
                }
                r.endObject()
            }
            else -> r.skipValue()
        }
    }

    private fun readOneRow(r: JsonReader, rows: MutableList<Map<String, String>>) {
        if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); return }
        val m = HashMap<String, String>()
        r.beginObject()
        while (r.hasNext()) {
            val key = r.nextName()
            when (r.peek()) {
                JsonToken.STRING, JsonToken.NUMBER -> m[key] = r.nextString()
                JsonToken.BOOLEAN -> m[key] = r.nextBoolean().toString()
                JsonToken.NULL -> r.nextNull()
                JsonToken.BEGIN_ARRAY -> m[key] = readFirstString(r)
                else -> r.skipValue()
            }
        }
        r.endObject()
        rows.add(m)
    }

    private fun readFirstString(r: JsonReader): String {
        var first = ""
        r.beginArray()
        var i = 0
        while (r.hasNext()) {
            if (i == 0 && r.peek() == JsonToken.STRING) first = r.nextString() else r.skipValue()
            i++
        }
        r.endArray()
        return first
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
    suspend fun loadAll(onProgress: (LoadProgress) -> Unit = {}): LoadedPlaylist = coroutineScope {
        val progLock = Any()
        var prog = LoadProgress()
        // `movies` et `series` se terminent en parallèle : accès synchronisé.
        fun emit(f: (LoadProgress) -> LoadProgress) {
            val next = synchronized(progLock) { prog = f(prog); prog }
            onProgress(next)
        }

        log("connexion à $host")
        val account = authenticate()
        log("compte OK" + (account.expiresAt?.let { " · expire le ${java.util.Date(it)}" } ?: ""))
        emit { it.copy(connected = true) }

        // Live d'abord (fait échouer vite si le compte pose problème), puis
        // films + séries en parallèle.
        val live = fetchStreams("get_live_categories", "get_live_streams", "live", liveExt, MediaKind.LIVE)
        if (live.isEmpty()) throw XtreamException("Aucune chaîne renvoyée par le serveur.")
        emit { it.copy(live = live.size) }

        val moviesJob = async {
            runCatching {
                fetchStreams("get_vod_categories", "get_vod_streams", "movie", null, MediaKind.MOVIE)
            }.onFailure { log("films : échec — ${it.message}") }.getOrDefault(emptyList())
                .also { m -> emit { p -> p.copy(movies = m.size) } }
        }
        val seriesJob = async {
            runCatching { fetchSeries() }
                .onFailure { log("séries : échec — ${it.message}") }
                .getOrDefault(emptyList())
                .also { s -> emit { p -> p.copy(series = s.size) } }
        }
        val movies = moviesJob.await()
        val series = seriesJob.await()
        log("terminé : ${live.size} chaînes · ${movies.size} films · ${series.size} séries")
        LoadedPlaylist(live, movies, series, expiresAt = account.expiresAt)
    }

    private suspend fun fetchStreams(
        catAction: String,
        streamsAction: String,
        segment: String,
        ext: String?,
        kind: MediaKind,
    ): List<Channel> {
        val cats = getRows(mapOf("action" to catAction))
        val names = categoryNames(cats)
        val rank = categoryRank(cats)
        val streams = getRows(mapOf("action" to streamsAction))

        val out = withContext(Dispatchers.Default) {
            val list = ArrayList<Channel>(streams.size)
            for (s in streams) {
                val sid = s["stream_id"].orEmpty()
                if (sid.isEmpty() || sid == "null") continue
                val container = s["container_extension"].orEmpty().ifEmpty { "mp4" }
                val effExt = ext ?: container
                list += Channel(
                    id = "${segment}_$sid",
                    name = s["name"]?.trim().orEmpty().ifEmpty { "Sans nom" },
                    url = "$host/$segment/$user/$pass/$sid.$effExt",
                    number = s["num"]?.toIntOrNull(),
                    logo = nullIfEmpty(s["stream_icon"]),
                    group = names[s["category_id"]] ?: "Non classé",
                    epgChannelId = nullIfEmpty(s["epg_channel_id"]),
                    streamId = sid,
                    kind = kind,
                    rating = parseRating(s["rating"]),
                    year = parseYear(s["year"].orEmpty().ifEmpty { s["releaseDate"].orEmpty() }),
                    addedAt = parseEpoch(s["added"]),
                    plot = if (kind == MediaKind.MOVIE)
                        nullIfEmpty(firstNonEmpty(s, "plot", "description", "overview")) else null,
                    genre = if (kind == MediaKind.MOVIE) nullIfEmpty(s["genre"]) else null,
                    containerExt = if (kind == MediaKind.MOVIE) container else null,
                )
            }
            sortByCategory(list, rank, names) { it.groupOrDefault }
            list
        }
        log("$streamsAction : ${out.size} gardés / ${streams.size} · ${names.size} catégories")
        return out
    }

    private suspend fun fetchSeries(): List<Series> {
        val cats = getRows(mapOf("action" to "get_series_categories"))
        val names = categoryNames(cats)
        val rank = categoryRank(cats)
        val list = getRows(mapOf("action" to "get_series"))

        val out = withContext(Dispatchers.Default) {
            val acc = ArrayList<Series>(list.size)
            for (s in list) {
                val sid = (s["series_id"] ?: s["id"] ?: s["num"]).orEmpty()
                if (sid.isEmpty() || sid == "null") continue
                acc += Series(
                    id = "series_$sid",
                    seriesId = sid,
                    name = s["name"]?.trim().orEmpty().ifEmpty { "Sans nom" },
                    cover = nullIfEmpty(s["cover"]),
                    backdrop = nullIfEmpty(s["backdrop_path"]),
                    group = names[s["category_id"]] ?: "Non classé",
                    plot = nullIfEmpty(s["plot"]),
                    genre = nullIfEmpty(s["genre"]),
                    cast = nullIfEmpty(s["cast"]),
                    director = nullIfEmpty(s["director"]),
                    rating = parseRating(s["rating"]),
                    year = parseYear(s["releaseDate"].orEmpty().ifEmpty { s["release_date"].orEmpty() }),
                    addedAt = parseEpoch(s["last_modified"]),
                )
            }
            sortByCategory(acc, rank, names) { it.groupOrDefault }
            acc
        }
        log("get_series : ${out.size} séries / ${list.size} · ${names.size} catégories")
        return out
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
    private fun categoryNames(cats: List<Map<String, String>>): Map<String, String> = buildMap {
        for (c in cats) {
            val id = c["category_id"].orEmpty()
            if (id.isEmpty()) continue
            put(id, c["category_name"]?.trim().orEmpty().ifEmpty { "Non classé" })
        }
    }

    private fun categoryRank(cats: List<Map<String, String>>): Map<String, Int> = buildMap {
        cats.forEachIndexed { i, c ->
            val id = c["category_id"].orEmpty()
            if (id.isNotEmpty() && id !in this) put(id, i)
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
        namesById.forEach { (id, name) ->
            if (name !in rankByName) rankByName[name] = rankById[id] ?: big
        }
        val indexed = items.mapIndexed { i, it -> it to i }
        val sorted = indexed.sortedWith(
            compareBy({ rankByName[groupOf(it.first)] ?: big }, { it.second })
        )
        for (i in items.indices) items[i] = sorted[i].first
    }

    private fun firstNonEmpty(m: Map<String, String>, vararg keys: String): String {
        for (k in keys) {
            val v = m[k].orEmpty()
            if (v.isNotEmpty() && v != "null") return v
        }
        return ""
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
