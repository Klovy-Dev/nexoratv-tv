package fr.nexoratv.tv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.nexoratv.tv.core.DeviceId
import fr.nexoratv.tv.core.Net
import fr.nexoratv.tv.core.mac.MacPortalClient
import fr.nexoratv.tv.core.mac.MacPortalException
import fr.nexoratv.tv.core.model.LoadProgress
import fr.nexoratv.tv.core.model.LoadedPlaylist
import fr.nexoratv.tv.core.model.PlaylistSource
import fr.nexoratv.tv.core.model.SourceKind
import fr.nexoratv.tv.core.xtream.XtreamClient
import fr.nexoratv.tv.core.xtream.XtreamException
import fr.nexoratv.tv.data.CatalogRepository
import fr.nexoratv.tv.data.SourceStore
import fr.nexoratv.tv.data.UpdateChecker
import fr.nexoratv.tv.data.UpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Écran affiché. `Home` = hub (3 blocs) ; `Catalog` = grille d'une section. */
sealed interface Screen {
    data object Loading : Screen
    data object Connect : Screen
    data object Home : Screen
    data class Catalog(val section: Section) : Screen
    data object Settings : Screen
}

enum class Section(val label: String) { TV("TV"), MOVIES("Films"), SERIES("Séries") }

sealed interface CatalogState {
    data object Idle : CatalogState
    data object Loading : CatalogState
    data class Ready(val playlist: LoadedPlaylist) : CatalogState
    data class Error(val message: String) : CatalogState
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    data class ReadyToInstall(val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val appContext = app
    private val store = SourceStore.get(app)
    private val catalog = CatalogRepository(app)

    /** Adresse MAC virtuelle unique de cet appareil (portail d'activation). */
    val deviceMac: String = DeviceId.mac(app)

    private val _screen = MutableStateFlow<Screen>(Screen.Loading)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _catalog = MutableStateFlow<CatalogState>(CatalogState.Idle)
    val catalogState: StateFlow<CatalogState> = _catalog.asStateFlow()

    private val _loadProgress = MutableStateFlow<LoadProgress?>(null)
    val loadProgress: StateFlow<LoadProgress?> = _loadProgress.asStateFlow()

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _update.asStateFlow()

    val sources: StateFlow<List<PlaylistSource>> = store.sources
    val currentSource: PlaylistSource? get() = store.selected

    /** L'écran de démarrage attend-il des films/séries (Xtream) ? */
    val loadExpectVod: Boolean get() = store.selected?.isXtream ?: true

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
    fun openSection(section: Section) { _screen.value = Screen.Catalog(section) }
    fun openSettings() { _screen.value = Screen.Settings }

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
            _loadProgress.value = null
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
            runCatching {
                catalog.load(src, forceRefresh) { p -> _loadProgress.value = p }
            }
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

    // ------------------------------------------------------------ Mises à jour
    fun checkUpdate() {
        val s = _update.value
        if (s is UpdateState.Checking || s is UpdateState.Downloading) return
        viewModelScope.launch {
            _update.value = UpdateState.Checking
            val info = UpdateChecker.fetch()
            _update.value = when {
                info == null -> UpdateState.Failed("Vérification impossible. Réessaie plus tard.")
                info.versionCode > BuildConfig.VERSION_CODE -> UpdateState.Available(info)
                else -> UpdateState.UpToDate
            }
        }
    }

    fun installUpdate(info: UpdateInfo) {
        if (_update.value is UpdateState.Downloading) return
        viewModelScope.launch {
            _update.value = UpdateState.Downloading(0)
            val file = runCatching {
                UpdateChecker.download(appContext, info) { pct ->
                    _update.value = UpdateState.Downloading(pct)
                }
            }.getOrElse {
                _update.value = UpdateState.Failed("Téléchargement échoué : ${it.message}")
                return@launch
            }
            _update.value = UpdateState.ReadyToInstall(file)
            UpdateChecker.install(appContext, file)
        }
    }

    fun launchInstall(file: File) = UpdateChecker.install(appContext, file)

    private fun normalizeHost(raw: String): String {
        var h = raw.trim()
        if (!h.startsWith("http://") && !h.startsWith("https://")) h = "http://$h"
        return h.trimEnd('/')
    }
}
