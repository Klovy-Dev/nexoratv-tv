package fr.nexoratv.tv.data

import android.content.Context
import fr.nexoratv.tv.core.Net
import fr.nexoratv.tv.core.m3u.M3uParser
import fr.nexoratv.tv.core.model.Channel
import fr.nexoratv.tv.core.model.LoadedPlaylist
import fr.nexoratv.tv.core.model.MediaKind
import fr.nexoratv.tv.core.model.PlaylistSource
import fr.nexoratv.tv.core.model.Series
import fr.nexoratv.tv.core.model.SourceKind
import fr.nexoratv.tv.core.xtream.XtreamClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Charge la playlist d'une source (réseau) et la met en cache sur disque
 * (`<cache>/catalog_<id>.json.gz`). Tout le décodage est en
 * `Dispatchers.Default` — jamais sur le thread UI (le bug qui tuait la
 * version Flutter sur Fire TV Stick).
 */
class CatalogRepository(private val context: Context) {

    private val cacheMaxAgeMs = 12 * 60 * 60 * 1000L

    private fun cacheFile(sourceId: String) =
        File(context.cacheDir, "catalog_$sourceId.json.gz")

    /** Cache frais → rendu ; sinon réseau puis cache. */
    suspend fun load(source: PlaylistSource, forceRefresh: Boolean = false): LoadedPlaylist {
        val f = cacheFile(source.id)
        if (!forceRefresh && f.exists() &&
            System.currentTimeMillis() - f.lastModified() < cacheMaxAgeMs
        ) {
            readCache(f)?.let { return it }
        }
        val fresh = fetch(source)
        writeCache(f, fresh)
        return fresh
    }

    suspend fun cachedOrNull(source: PlaylistSource): LoadedPlaylist? =
        cacheFile(source.id).takeIf { it.exists() }?.let { readCache(it) }

    private suspend fun fetch(source: PlaylistSource): LoadedPlaylist = when (source.kind) {
        SourceKind.XTREAM -> XtreamClient(source, Net.http).loadAll()
        SourceKind.M3U_URL -> withContext(Dispatchers.IO) {
            val body = Net.http.newCall(Request.Builder().url(source.m3uUrl!!).build())
                .execute().use { it.body?.string().orEmpty() }
            LoadedPlaylist(live = withContext(Dispatchers.Default) { M3uParser.parse(body) })
        }
    }

    // ------------------------------------------------------------ cache
    private suspend fun readCache(f: File): LoadedPlaylist? = withContext(Dispatchers.Default) {
        runCatching {
            val text = GZIPInputStream(f.inputStream()).bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            LoadedPlaylist(
                live = root.optJSONArray("live").toChannels(),
                movies = root.optJSONArray("movies").toChannels(),
                series = root.optJSONArray("series").toSeries(),
            )
        }.getOrNull()
    }

    private suspend fun writeCache(f: File, pl: LoadedPlaylist) = withContext(Dispatchers.Default) {
        runCatching {
            val root = JSONObject()
                .put("savedAt", System.currentTimeMillis())
                .put("live", pl.live.toJsonArray { it.toJson() })
                .put("movies", pl.movies.toJsonArray { it.toJson() })
                .put("series", pl.series.toJsonArray { it.toJson() })
            GZIPOutputStream(f.outputStream()).bufferedWriter().use { it.write(root.toString()) }
        }
        Unit
    }

    private fun <T> List<T>.toJsonArray(map: (T) -> JSONObject) =
        JSONArray().also { a -> forEach { a.put(map(it)) } }

    private fun Channel.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("url", url); put("number", number)
        put("logo", logo); put("group", group); put("epgChannelId", epgChannelId)
        put("streamId", streamId); put("kind", kind.name); put("rating", rating)
        put("year", year); put("addedAt", addedAt); put("plot", plot); put("genre", genre)
        put("containerExt", containerExt)
    }

    private fun Series.toJson() = JSONObject().apply {
        put("id", id); put("seriesId", seriesId); put("name", name); put("cover", cover)
        put("backdrop", backdrop); put("group", group); put("plot", plot); put("genre", genre)
        put("cast", cast); put("director", director); put("rating", rating); put("year", year)
        put("addedAt", addedAt)
    }

    private fun JSONArray?.toChannels(): List<Channel> {
        val a = this ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            Channel(
                id = o.optString("id"), name = o.optString("name"), url = o.optString("url"),
                number = o.optIntOrNull("number"), logo = o.optStringOrNull("logo"),
                group = o.optStringOrNull("group"), epgChannelId = o.optStringOrNull("epgChannelId"),
                streamId = o.optStringOrNull("streamId"),
                kind = runCatching { MediaKind.valueOf(o.optString("kind")) }.getOrDefault(MediaKind.LIVE),
                rating = o.optDoubleOrNull("rating"), year = o.optIntOrNull("year"),
                addedAt = o.optLongOrNull("addedAt"), plot = o.optStringOrNull("plot"),
                genre = o.optStringOrNull("genre"), containerExt = o.optStringOrNull("containerExt"),
            )
        }
    }

    private fun JSONArray?.toSeries(): List<Series> {
        val a = this ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            Series(
                id = o.optString("id"), seriesId = o.optString("seriesId"), name = o.optString("name"),
                cover = o.optStringOrNull("cover"), backdrop = o.optStringOrNull("backdrop"),
                group = o.optStringOrNull("group"), plot = o.optStringOrNull("plot"),
                genre = o.optStringOrNull("genre"), cast = o.optStringOrNull("cast"),
                director = o.optStringOrNull("director"), rating = o.optDoubleOrNull("rating"),
                year = o.optIntOrNull("year"), addedAt = o.optLongOrNull("addedAt"),
            )
        }
    }

    private fun JSONObject.optStringOrNull(k: String) =
        if (isNull(k)) null else optString(k).ifEmpty { null }
    private fun JSONObject.optIntOrNull(k: String) = if (isNull(k)) null else optInt(k)
    private fun JSONObject.optLongOrNull(k: String) = if (isNull(k)) null else optLong(k)
    private fun JSONObject.optDoubleOrNull(k: String) = if (isNull(k)) null else optDouble(k)
}
