package fr.nexoratv.tv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.nexoratv.tv.core.DeviceId
import fr.nexoratv.tv.core.Net
import fr.nexoratv.tv.core.mac.MacPortalClient
import fr.nexoratv.tv.core.mac.MacPortalException
import fr.nexoratv.tv.core.model.LoadedPlaylist
import fr.nexoratv.tv.core.model.PlaylistSource
import fr.nexoratv.tv.core.model.SourceKind
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
    data object Connect : Screen
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

    /** Adresse MAC virtuelle unique de cet appareil (portail d'activation). */
    val deviceMac: String = DeviceId.mac(app)

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
                _screen.value = Screen.Connect
            } else {
                _screen.value = Screen.Home
                loadCatalog()
            }
        }
    }

    fun goConnect() { _screen.value = Screen.Connect }
    fun goHome() { _screen.value = Screen.Home }

    // --------------------------------------------------- Activation par MAC
    fun activateByMac(onResult: (ok: Boolean, error: String?) -> Unit) {
        viewModelScope.launch {
            val resolved = try {
                MacPortalClient.resolve(deviceMac)
            } catch (e: MacPortalException) {
                onResult(false, e.message); return@launch
            }
            store.add(resolved)
            onResult(true, null)
            _screen.value = Screen.Home
            loadCatalog(forceRefresh = true)
        }
    }

    // ------------------------------------------------------ Xtream manuel
    fun addXtream(
        name: String, host: String, username: String, password: String,
        onResult: (ok: Boolean, error: String?) -> Unit,
    ) {
        viewModelScope.launch {
            val src = PlaylistSource(
                name = name.ifBlank { username },
                kind = SourceKind.XTREAM,
                host = normalizeHost(host), username = username.trim(), password = password.trim(),
            )
            val err = runCatching { XtreamClient(src, Net.http).authenticate() }.exceptionOrNull()
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

    // ------------------------------------------------------------ Catalogue
    fun loadCatalog(forceRefresh: Boolean = false) {
        val base = store.selected ?: return
        viewModelScope.launch {
            _catalog.value = CatalogState.Loading
            // Source activée par MAC : on re-résout auprès du portail (l'admin
            // a pu changer le lien). En cas d'échec, on garde la config connue.
            val src = if (base.activationMac != null) {
                runCatching { MacPortalClient.resolve(base.activationMac!!, base.id) }
                    .getOrDefault(base)
                    .also { if (it != base) store.add(it) }
            } else base

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
