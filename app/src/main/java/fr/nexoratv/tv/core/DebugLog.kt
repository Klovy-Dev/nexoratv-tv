package fr.nexoratv.tv.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Journal en mémoire des derniers événements de chargement (réseau Xtream /
 * M3U). Affiché dans Paramètres → Diagnostic pour comprendre un souci de
 * catalogue sans avoir à brancher un câble.
 */
object DebugLog {

    private const val MAX = 120
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.FRANCE)

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    @Synchronized
    fun line(message: String) {
        val entry = "${clock.format(Date())}  $message"
        val next = (_lines.value + entry)
        _lines.value = if (next.size > MAX) next.takeLast(MAX) else next
    }

    @Synchronized
    fun section(title: String) = line("——— $title ———")

    @Synchronized
    fun clear() {
        _lines.value = emptyList()
    }
}
