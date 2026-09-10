package fr.nexoratv.tv.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import fr.nexoratv.tv.core.DebugLog
import fr.nexoratv.tv.core.Net
import fr.nexoratv.tv.core.m3u.M3uParser
import fr.nexoratv.tv.core.model.Channel
import fr.nexoratv.tv.core.model.LoadProgress
import fr.nexoratv.tv.core.model.LoadedPlaylist
import fr.nexoratv.tv.core.model.MediaKind
import fr.nexoratv.tv.core.model.PlaylistSource
import fr.nexoratv.tv.core.model.Series
import fr.nexoratv.tv.core.model.SourceKind
import fr.nexoratv.tv.core.xtream.XtreamClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Charge la playlist d'une source et la met en cache sur disque
 * (`<cache>/catalog_<id>.json.gz`). Lecture **et** écriture en streaming
 * (`JsonReader` / `JsonWriter` branchés sur le flux gzip) : on ne construit
 * jamais tout le JSON en mémoire — c'est ce qui faisait planter le Fire TV
 * Stick sur les gros catalogues (33k+ séries).
 */
class CatalogRepository(private val context: Context) {

    private val cacheMaxAgeMs = 12 * 60 * 60 * 1000L

    private fun cacheFile(sourceId: String) =
        File(context.cacheDir, "catalog_$sourceId.json.gz")

    /** Cache frais → rendu ; sinon réseau puis cache. */
    suspend fun load(
        source: PlaylistSource,
        forceRefresh: Boolean = false,
        onProgress: (LoadProgress) -> Unit = {},
    ): LoadedPlaylist {
        val f = cacheFile(source.id)
        DebugLog.section("Chargement « ${source.name} » (${source.kind})" + if (forceRefresh) " · forcé" else "")
        if (!forceRefresh && f.exists() &&
            System.currentTimeMillis() - f.lastModified() < cacheMaxAgeMs
        ) {
            readCache(f)?.let {
                DebugLog.line("cache utilisé : ${it.live.size} chaînes · ${it.movies.size} films · ${it.series.size} séries")
                return it
            }
        }
        val fresh = fetch(source, onProgress)
        DebugLog.line("réseau OK : ${fresh.live.size} chaînes · ${fresh.movies.size} films · ${fresh.series.size} séries")
        writeCache(f, fresh)
        return fresh
    }

    suspend fun cachedOrNull(source: PlaylistSource): LoadedPlaylist? =
        cacheFile(source.id).takeIf { it.exists() }?.let { readCache(it) }

    private suspend fun fetch(
        source: PlaylistSource,
        onProgress: (LoadProgress) -> Unit,
    ): LoadedPlaylist = when (source.kind) {
        SourceKind.XTREAM ->
            XtreamClient(source, Net.http, log = { DebugLog.line(it) }).loadAll(onProgress)
        SourceKind.M3U_URL -> withContext(Dispatchers.IO) {
            onProgress(LoadProgress(connected = false))
            DebugLog.line("M3U : téléchargement…")
            val body = Net.http.newCall(Request.Builder().url(source.m3uUrl!!).build())
                .execute().use { it.body?.string().orEmpty() }
            DebugLog.line("M3U : ${body.length} octets reçus")
            onProgress(LoadProgress(connected = true))
            val channels = withContext(Dispatchers.Default) { M3uParser.parse(body) }
            onProgress(LoadProgress(connected = true, live = channels.size))
            LoadedPlaylist(live = channels)
        }
    }

    // --------------------------------------------------------- cache (stream)
    private suspend fun readCache(f: File): LoadedPlaylist? = withContext(Dispatchers.IO) {
        runCatching {
            JsonReader(GZIPInputStream(f.inputStream()).bufferedReader()).use { r ->
                var expiresAt: Long? = null
                val live = ArrayList<Channel>()
                val movies = ArrayList<Channel>()
                val series = ArrayList<Series>()
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "expiresAt" -> expiresAt =
                            if (r.peek() == JsonToken.NULL) { r.nextNull(); null } else r.nextLong()
                        "live" -> readChannels(r, live)
                        "movies" -> readChannels(r, movies)
                        "series" -> readSeries(r, series)
                        else -> r.skipValue()
                    }
                }
                r.endObject()
                LoadedPlaylist(live, movies, series, expiresAt)
            }
        }.onFailure { DebugLog.line("cache illisible : ${it.javaClass.simpleName}") }.getOrNull()
    }

    private suspend fun writeCache(f: File, pl: LoadedPlaylist) = withContext(Dispatchers.IO) {
        val tmp = File(f.parentFile, "${f.name}.tmp")
        runCatching {
            JsonWriter(GZIPOutputStream(tmp.outputStream()).bufferedWriter()).use { w ->
                w.beginObject()
                w.name("savedAt").value(System.currentTimeMillis())
                w.name("expiresAt")
                if (pl.expiresAt != null) w.value(pl.expiresAt) else w.nullValue()
                w.name("live").beginArray(); pl.live.forEach { writeChannel(w, it) }; w.endArray()
                w.name("movies").beginArray(); pl.movies.forEach { writeChannel(w, it) }; w.endArray()
                w.name("series").beginArray(); pl.series.forEach { writeSeries(w, it) }; w.endArray()
                w.endObject()
            }
            if (f.exists()) f.delete()
            if (!tmp.renameTo(f)) throw java.io.IOException("renommage cache impossible")
        }.onFailure {
            DebugLog.line("écriture cache échouée : ${it.javaClass.simpleName} ${it.message ?: ""}")
            tmp.delete()
        }
        Unit
    }

    private fun writeChannel(w: JsonWriter, c: Channel) {
        w.beginObject()
        w.opt("id", c.id); w.opt("name", c.name); w.opt("url", c.url)
        w.opt("number", c.number); w.opt("logo", c.logo); w.opt("group", c.group)
        w.opt("epgChannelId", c.epgChannelId); w.opt("streamId", c.streamId)
        w.opt("kind", c.kind.name); w.opt("rating", c.rating); w.opt("year", c.year)
        w.opt("genre", c.genre); w.opt("containerExt", c.containerExt)
        w.endObject()
    }

    private fun writeSeries(w: JsonWriter, s: Series) {
        w.beginObject()
        w.opt("id", s.id); w.opt("seriesId", s.seriesId); w.opt("name", s.name)
        w.opt("cover", s.cover); w.opt("group", s.group); w.opt("genre", s.genre)
        w.opt("rating", s.rating); w.opt("year", s.year)
        w.endObject()
    }

    private fun readChannels(r: JsonReader, out: MutableList<Channel>) {
        r.beginArray()
        while (r.hasNext()) {
            var id = ""; var name = ""; var url = ""; var number: Int? = null
            var logo: String? = null; var group: String? = null; var epg: String? = null
            var streamId: String? = null; var kind = MediaKind.LIVE
            var rating: Double? = null; var year: Int? = null
            var genre: String? = null; var container: String? = null
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "id" -> id = str(r).orEmpty()
                    "name" -> name = str(r).orEmpty()
                    "url" -> url = str(r).orEmpty()
                    "number" -> number = intN(r)
                    "logo" -> logo = str(r)
                    "group" -> group = str(r)
                    "epgChannelId" -> epg = str(r)
                    "streamId" -> streamId = str(r)
                    "kind" -> kind = runCatching { MediaKind.valueOf(str(r).orEmpty()) }.getOrDefault(MediaKind.LIVE)
                    "rating" -> rating = dblN(r)
                    "year" -> year = intN(r)
                    "genre" -> genre = str(r)
                    "containerExt" -> container = str(r)
                    else -> r.skipValue()
                }
            }
            r.endObject()
            if (id.isNotEmpty()) out.add(
                Channel(
                    id = id, name = name, url = url, number = number, logo = logo,
                    group = group, epgChannelId = epg, streamId = streamId, kind = kind,
                    rating = rating, year = year, genre = genre, containerExt = container,
                )
            )
        }
        r.endArray()
    }

    private fun readSeries(r: JsonReader, out: MutableList<Series>) {
        r.beginArray()
        while (r.hasNext()) {
            var id = ""; var seriesId = ""; var name = ""
            var cover: String? = null; var group: String? = null; var genre: String? = null
            var rating: Double? = null; var year: Int? = null
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "id" -> id = str(r).orEmpty()
                    "seriesId" -> seriesId = str(r).orEmpty()
                    "name" -> name = str(r).orEmpty()
                    "cover" -> cover = str(r)
                    "group" -> group = str(r)
                    "genre" -> genre = str(r)
                    "rating" -> rating = dblN(r)
                    "year" -> year = intN(r)
                    else -> r.skipValue()
                }
            }
            r.endObject()
            if (id.isNotEmpty()) out.add(
                Series(
                    id = id, seriesId = seriesId, name = name, cover = cover,
                    group = group, genre = genre, rating = rating, year = year,
                )
            )
        }
        r.endArray()
    }

    // -------------------------------------------------------------- helpers
    private fun JsonWriter.opt(name: String, v: String?) { name(name); value(v) }
    private fun JsonWriter.opt(name: String, v: Int?) { name(name); if (v != null) value(v.toLong()) else nullValue() }
    private fun JsonWriter.opt(name: String, v: Double?) { name(name); if (v != null) value(v) else nullValue() }

    private fun str(r: JsonReader): String? =
        if (r.peek() == JsonToken.NULL) { r.nextNull(); null } else r.nextString().ifEmpty { null }

    private fun intN(r: JsonReader): Int? =
        if (r.peek() == JsonToken.NULL) { r.nextNull(); null } else r.nextInt()

    private fun dblN(r: JsonReader): Double? =
        if (r.peek() == JsonToken.NULL) { r.nextNull(); null } else r.nextDouble()
}
