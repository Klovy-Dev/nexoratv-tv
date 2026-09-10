package fr.nexoratv.tv.data

import android.content.Context
import fr.nexoratv.tv.core.model.PlaylistSource
import fr.nexoratv.tv.core.model.SourceKind
import fr.nexoratv.tv.core.model.XtreamOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Stockage des sources IPTV dans un simple fichier JSON (`sources.json`).
 * En attendant Room. Suffisant : quelques sources max.
 */
class SourceStore private constructor(private val file: File) {

    private val _sources = MutableStateFlow<List<PlaylistSource>>(emptyList())
    val sources: StateFlow<List<PlaylistSource>> = _sources.asStateFlow()

    private var selectedId: String? = null

    val selected: PlaylistSource?
        get() = _sources.value.firstOrNull { it.id == selectedId } ?: _sources.value.firstOrNull()

    suspend fun load() = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext
        runCatching {
            val root = JSONObject(file.readText())
            selectedId = root.optString("selected").ifEmpty { null }
            val arr = root.optJSONArray("sources") ?: JSONArray()
            _sources.value = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.toSource() }
        }
        Unit
    }

    suspend fun add(source: PlaylistSource) = withContext(Dispatchers.IO) {
        _sources.value = _sources.value.filter { it.id != source.id } + source
        selectedId = source.id
        persist()
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        _sources.value = _sources.value.filter { it.id != id }
        if (selectedId == id) selectedId = _sources.value.firstOrNull()?.id
        persist()
    }

    suspend fun select(id: String) = withContext(Dispatchers.IO) {
        selectedId = id
        persist()
    }

    private fun persist() {
        val arr = JSONArray()
        _sources.value.forEach { arr.put(it.toJson()) }
        file.writeText(JSONObject().put("selected", selectedId ?: "").put("sources", arr).toString())
    }

    private fun PlaylistSource.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("kind", kind.name)
        put("m3uUrl", m3uUrl); put("epgUrl", epgUrl)
        put("host", host); put("username", username); put("password", password)
        put("xtreamOutput", xtreamOutput.name)
        put("activationMac", activationMac); put("createdAt", createdAt)
    }

    private fun JSONObject.toSource() = PlaylistSource(
        id = optString("id"),
        name = optString("name"),
        kind = SourceKind.valueOf(optString("kind", "XTREAM")),
        m3uUrl = optStringOrNull("m3uUrl"),
        epgUrl = optStringOrNull("epgUrl"),
        host = optStringOrNull("host"),
        username = optStringOrNull("username"),
        password = optStringOrNull("password"),
        xtreamOutput = XtreamOutput.valueOf(optString("xtreamOutput", "TS")),
        activationMac = optStringOrNull("activationMac"),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
    )

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).ifEmpty { null }

    companion object {
        @Volatile private var instance: SourceStore? = null
        fun get(context: Context): SourceStore = instance ?: synchronized(this) {
            instance ?: SourceStore(File(context.filesDir, "sources.json")).also { instance = it }
        }
    }
}
