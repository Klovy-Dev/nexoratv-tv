package fr.nexoratv.tv.core.xtream

import fr.nexoratv.tv.core.model.Channel
import fr.nexoratv.tv.core.model.Episode
import fr.nexoratv.tv.core.model.LoadProgress
import fr.nexoratv.tv.core.model.LoadedPlaylist
import fr.nexoratv.tv.core.model.MediaKind
import fr.nexoratv.tv.core.model.PlaylistSource
import fr.nexoratv.tv.core.model.Series
import fr.nexoratv.tv.core.model.SeriesBundle
import fr.nexoratv.tv.core.model.VodInfo
import fr.nexoratv.tv.core.model.XtreamOutput
import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.Dispatchers
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
     * Récupère une action qui renvoie une liste d'objets, **en streaming**, et
     * appelle `onRow` pour chaque objet (`Map<String,String>`). On ne garde
     * jamais ni la réponse ni la liste des objets en mémoire — un seul objet
     * vit à la fois. C'est ce qui faisait planter (OOM) les gros `get_series`
     * sur Fire TV Stick. Retourne le nombre d'objets lus.
     */
    private suspend fun streamRows(
        params: Map<String, String>,
        onRow: (Map<String, String>) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val action = params["action"] ?: "?"
        val req = Request.Builder().url(api(params)).header("User-Agent", USER_AGENT).build()
        var count = 0
        try {
            listHttp.newCall(req).execute().use { res ->
                if (!res.isSuccessful) throw XtreamException("$action : HTTP ${res.code}")
                val body = res.body ?: throw XtreamException("$action : réponse vide")
                JsonReader(body.charStream().buffered()).use { r ->
                    r.isLenient = true
                    streamValue(r) { row -> count++; onRow(row) }
                }
            }
        } catch (e: XtreamException) {
            throw e
        } catch (e: Throwable) {
            log("$action : échec — ${e.javaClass.simpleName} ${e.message ?: ""}")
            throw XtreamException("$action : ${e.message ?: e.javaClass.simpleName}")
        }
        log("$action : $count objets reçus")
        count
    }

    private fun streamValue(r: JsonReader, onRow: (Map<String, String>) -> Unit) {
        when (r.peek()) {
            JsonToken.BEGIN_ARRAY -> {
                r.beginArray()
                while (r.hasNext()) streamOne(r, onRow)
                r.endArray()
            }
            JsonToken.BEGIN_OBJECT -> {
                // panneau qui renvoie {"0":{…}} ou {"data":[…]}
                r.beginObject()
                while (r.hasNext()) {
                    r.nextName()
                    when (r.peek()) {
                        JsonToken.BEGIN_OBJECT -> streamOne(r, onRow)
                        JsonToken.BEGIN_ARRAY -> {
                            r.beginArray()
                            while (r.hasNext()) streamOne(r, onRow)
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

    private fun streamOne(r: JsonReader, onRow: (Map<String, String>) -> Unit) {
        if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); return }
        val m = HashMap<String, String>(16)
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
        onRow(m)
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
    suspend fun loadAll(onProgress: (LoadProgress) -> Unit = {}): LoadedPlaylist {
        var prog = LoadProgress()
        fun emit(f: (LoadProgress) -> LoadProgress) { prog = f(prog); onProgress(prog) }

        log("connexion à $host")
        val account = authenticate()
        log("compte OK" + (account.expiresAt?.let { " · expire le ${java.util.Date(it)}" } ?: ""))
        emit { it.copy(connected = true) }

        // Séquentiel (live → films → séries) : sur un catalogue énorme, faire
        // films + séries en parallèle doublait le pic mémoire et faisait
        // crasher le Fire TV Stick.
        val live = fetchStreams("get_live_categories", "get_live_streams", "live", liveExt, MediaKind.LIVE)
        if (live.isEmpty()) throw XtreamException("Aucune chaîne renvoyée par le serveur.")
        emit { it.copy(live = live.size) }

        val movies = runCatching {
            fetchStreams("get_vod_categories", "get_vod_streams", "movie", null, MediaKind.MOVIE)
        }.onFailure { log("films : échec — ${it.message}") }.getOrDefault(emptyList())
        emit { it.copy(movies = movies.size) }

        val series = runCatching { fetchSeries() }
            .onFailure { log("séries : échec — ${it.message}") }
            .getOrDefault(emptyList())
        emit { it.copy(series = series.size) }

        log("terminé : ${live.size} chaînes · ${movies.size} films · ${series.size} séries")
        return LoadedPlaylist(live, movies, series, expiresAt = account.expiresAt)
    }

    private suspend fun fetchStreams(
        catAction: String,
        streamsAction: String,
        segment: String,
        ext: String?,
        kind: MediaKind,
    ): List<Channel> {
        val cats = ArrayList<Map<String, String>>()
        streamRows(mapOf("action" to catAction)) { cats.add(it) }
        val names = categoryNames(cats)
        val rank = categoryRank(cats)

        val out = ArrayList<Channel>()
        streamRows(mapOf("action" to streamsAction)) { s ->
            val sid = s["stream_id"].orEmpty()
            if (sid.isEmpty() || sid == "null") return@streamRows
            val container = s["container_extension"].orEmpty().ifEmpty { "mp4" }
            val effExt = ext ?: container
            out.add(
                Channel(
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
                    genre = if (kind == MediaKind.MOVIE) nullIfEmpty(s["genre"]) else null,
                    containerExt = if (kind == MediaKind.MOVIE) container else null,
                )
            )
        }
        withContext(Dispatchers.Default) { sortByCategory(out, rank, names) { it.groupOrDefault } }
        log("$streamsAction : ${out.size} gardés · ${names.size} catégories")
        return out
    }

    private suspend fun fetchSeries(): List<Series> {
        val cats = ArrayList<Map<String, String>>()
        streamRows(mapOf("action" to "get_series_categories")) { cats.add(it) }
        val names = categoryNames(cats)
        val rank = categoryRank(cats)

        // On ne garde que le strict nécessaire à la grille : sur 30k+ séries,
        // embarquer plot/cast/director sature la mémoire du Fire TV Stick. Le
        // détail sera récupéré via get_series_info.
        val out = ArrayList<Series>()
        streamRows(mapOf("action" to "get_series")) { s ->
            val sid = (s["series_id"] ?: s["id"] ?: s["num"]).orEmpty()
            if (sid.isEmpty() || sid == "null") return@streamRows
            out.add(
                Series(
                    id = "series_$sid",
                    seriesId = sid,
                    name = s["name"]?.trim().orEmpty().ifEmpty { "Sans nom" },
                    cover = nullIfEmpty(s["cover"]),
                    group = names[s["category_id"]] ?: "Non classé",
                    genre = nullIfEmpty(s["genre"]),
                    rating = parseRating(s["rating"]),
                    year = parseYear(s["releaseDate"].orEmpty().ifEmpty { s["release_date"].orEmpty() }),
                )
            )
        }
        withContext(Dispatchers.Default) { sortByCategory(out, rank, names) { it.groupOrDefault } }
        log("get_series : ${out.size} séries · ${names.size} catégories")
        return out
    }

    /** Fiche d'un film : synopsis, casting, durée, affiche… (`get_vod_info`). */
    suspend fun loadVodInfo(streamId: String): VodInfo = withContext(Dispatchers.Default) {
        val root = getJson(mapOf("action" to "get_vod_info", "vod_id" to streamId)) as? JSONObject
            ?: return@withContext VodInfo()
        val info = root.optJSONObject("info") ?: JSONObject()
        VodInfo(
            plot = nullIfEmpty(firstNonEmptyJ(info, "plot", "description", "overview")),
            genre = nullIfEmpty(info.optString("genre")),
            cast = nullIfEmpty(firstNonEmptyJ(info, "cast", "actors")),
            director = nullIfEmpty(info.optString("director")),
            year = parseYear(info.optString("releaseDate").ifEmpty { info.optString("release_date") }
                .ifEmpty { info.optString("year") }),
            rating = parseRating(info.opt("rating")),
            durationSecs = info.optString("duration_secs").toIntOrNull()
                ?: hmsToSecs(info.optString("duration")),
            backdrop = firstBackdropJ(info.opt("backdrop_path")),
            poster = nullIfEmpty(firstNonEmptyJ(info, "movie_image", "cover_big", "cover")),
        )
    }

    /** Fiche d'une série + tous ses épisodes (`get_series_info`). */
    suspend fun loadSeries(seriesId: String): SeriesBundle = withContext(Dispatchers.Default) {
        val root = getJson(mapOf("action" to "get_series_info", "series_id" to seriesId)) as? JSONObject
            ?: return@withContext SeriesBundle()
        val info = root.optJSONObject("info") ?: JSONObject()
        val episodes = root.optJSONObject("episodes")
        val out = ArrayList<Episode>()
        if (episodes != null) {
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
        }
        out.sortWith(compareBy({ it.season }, { it.episode }))
        SeriesBundle(
            plot = nullIfEmpty(info.optString("plot")),
            genre = nullIfEmpty(info.optString("genre")),
            cast = nullIfEmpty(firstNonEmptyJ(info, "cast", "actors")),
            director = nullIfEmpty(info.optString("director")),
            year = parseYear(info.optString("releaseDate").ifEmpty { info.optString("release_date") }),
            rating = parseRating(info.opt("rating")),
            backdrop = firstBackdropJ(info.opt("backdrop_path")),
            cover = nullIfEmpty(info.optString("cover")),
            episodes = out,
        )
    }

    private fun firstNonEmptyJ(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = o.optString(k)
            if (v.isNotEmpty() && v != "null") return v
        }
        return ""
    }

    private fun firstBackdropJ(v: Any?): String? = when (v) {
        is JSONArray -> if (v.length() > 0) nullIfEmpty(v.optString(0)) else null
        else -> nullIfEmpty(v?.toString())
    }

    /** "1:38:20" ou "01:38" → secondes. */
    private fun hmsToSecs(s: String): Int? {
        val parts = s.split(":").mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            else -> null
        }
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
