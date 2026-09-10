package fr.nexoratv.tv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.nexoratv.tv.core.Net
import fr.nexoratv.tv.core.model.LoadedPlaylist
import fr.nexoratv.tv.core.model.PlaylistSource
import fr.nexoratv.tv.core.xtream.XtreamClient
import fr.nexoratv.tv.core.xtream.XtreamException
import fr.nexoratv.tv.data.CatalogRepository
import fr.nexoratv.tv.data.SourceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Loading : Screen
    data object AddSource : Screen
    data object Home : Screen
}

sealed interface CatalogState {
    data object Idle : CatalogState
    data object Loading : CatalogState
    data class Ready(val playlist: LoadedPlaylist) : CatalogState
    data class Error(val message: String) : CatalogState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SourceStore.get(app)
    private val catalog = CatalogRepository(app)

    private val _screen = MutableStateFlow<Screen>(Screen.Loading)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _catalog = MutableStateFlow<CatalogState>(CatalogState.Idle)
    val catalogState: StateFlow<CatalogState> = _catalog.asStateFlow()

    val sources: StateFlow<List<PlaylistSource>> = store.sources
    val currentSource: PlaylistSource? get() = store.selected

    init {
        viewModelScope.launch {
            store.load()
            if (store.sources.value.isEmpty()) {
                _screen.value = Screen.AddSource
            } else {
                _screen.value = Screen.Home
                loadCatalog()
            }
        }
    }

    fun goAddSource() { _screen.value = Screen.AddSource }
    fun goHome() { _screen.value = Screen.Home }

    /** Valide + enregistre une source Xtream, puis charge le catalogue. */
    fun addXtream(
        name: String,
        host: String,
        username: String,
        password: String,
        onResult: (ok: Boolean, error: String?) -> Unit,
    ) {
        viewModelScope.launch {
            val normHost = normalizeHost(host)
            val src = PlaylistSource(
                name = name.ifBlank { username },
                kind = fr.nexoratv.tv.core.model.SourceKind.XTREAM,
                host = normHost, username = username.trim(), password = password.trim(),
            )
            val err = runCatching { XtreamClient(src, Net.http).authenticate() }
                .exceptionOrNull()
            if (err != null) {
                onResult(false, (err as? XtreamException)?.message ?: err.message ?: "Échec de connexion")
                return@launch
            }
            store.add(src)
            onResult(true, null)
            _screen.value = Screen.Home
            loadCatalog()
        }
    }

    fun loadCatalog(forceRefresh: Boolean = false) {
        val src = store.selected ?: return
        viewModelScope.launch {
            _catalog.value = CatalogState.Loading
            // Sert le cache tout de suite s'il existe (perçu instantané).
            if (!forceRefresh) {
                catalog.cachedOrNull(src)?.let { _catalog.value = CatalogState.Ready(it) }
            }
            runCatching { catalog.load(src, forceRefresh) }
                .onSuccess { _catalog.value = CatalogState.Ready(it) }
                .onFailure { e ->
                    if (_catalog.value !is CatalogState.Ready) {
                        _catalog.value = CatalogState.Error(
                            (e as? XtreamException)?.message ?: e.message ?: "Chargement impossible"
                        )
                    }
                }
        }
    }

    private fun normalizeHost(raw: String): String {
        var h = raw.trim()
        if (!h.startsWith("http://") && !h.startsWith("https://")) h = "http://$h"
        return h.trimEnd('/')
    }
}
