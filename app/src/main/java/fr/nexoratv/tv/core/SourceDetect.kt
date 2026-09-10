package fr.nexoratv.tv.core

import android.net.Uri
import fr.nexoratv.tv.core.model.PlaylistSource
import fr.nexoratv.tv.core.model.SourceKind
import fr.nexoratv.tv.core.model.XtreamOutput

/**
 * Un lien M3U `.../get.php?username=..&password=..` est en fait un compte
 * Xtream : on le convertit pour débloquer films / séries (un M3U brut ne
 * charge que le direct). Porté de la version Flutter.
 */
fun PlaylistSource.upgradedToXtreamIfPossible(): PlaylistSource {
    if (kind != SourceKind.M3U_URL || m3uUrl.isNullOrBlank()) return this
    val x = parseXtreamGetUrl(m3uUrl) ?: return this
    val lower = m3uUrl.lowercase()
    val output = if ("output=hls" in lower || "m3u8" in lower) XtreamOutput.M3U8 else XtreamOutput.TS
    return PlaylistSource(
        id = id, name = name, kind = SourceKind.XTREAM,
        host = x.host, username = x.username, password = x.password,
        xtreamOutput = output, epgUrl = epgUrl,
        activationMac = activationMac, createdAt = createdAt,
    )
}

data class XtreamGet(val host: String, val username: String, val password: String)

fun parseXtreamGetUrl(url: String): XtreamGet? {
    val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return null
    if (uri.scheme.isNullOrEmpty() || uri.host.isNullOrEmpty()) return null
    if (uri.path?.lowercase()?.contains("get.php") != true) return null
    val user = uri.getQueryParameter("username")?.takeIf { it.isNotEmpty() } ?: return null
    val pass = uri.getQueryParameter("password")?.takeIf { it.isNotEmpty() } ?: return null
    val port = if (uri.port > 0) ":${uri.port}" else ""
    return XtreamGet("${uri.scheme}://${uri.host}$port", user, pass)
}
