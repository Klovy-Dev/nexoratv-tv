package fr.nexoratv.tv.core.m3u

import fr.nexoratv.tv.core.model.Channel
import fr.nexoratv.tv.core.model.MediaKind
import java.security.MessageDigest

/**
 * Parseur M3U / M3U8 étendu (`#EXTINF`, `#EXTGRP`, attributs `tvg-*`).
 * Porté du parseur Flutter.
 */
object M3uParser {

    private val ATTR = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(content: String): List<Channel> {
        val out = ArrayList<Channel>()
        var pendingName: String? = null
        var attrs: Map<String, String> = emptyMap()
        var group: String? = null

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("#EXTINF:", true) -> {
                    val comma = line.indexOf(',')
                    val meta = if (comma >= 0) line.substring(8, comma) else line.substring(8)
                    pendingName = if (comma >= 0) line.substring(comma + 1).trim() else null
                    attrs = ATTR.findAll(meta).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    group = attrs["group-title"]?.takeIf { it.isNotBlank() }
                }
                line.startsWith("#EXTGRP:", true) -> {
                    group = line.substring(8).trim().takeIf { it.isNotBlank() } ?: group
                }
                line.isEmpty() || line.startsWith("#") -> Unit
                else -> {
                    val url = line
                    val name = (attrs["tvg-name"]?.takeIf { it.isNotBlank() }
                        ?: pendingName ?: "Sans nom")
                    out += Channel(
                        id = stableId(url),
                        name = name,
                        url = url,
                        number = attrs["tvg-chno"]?.toIntOrNull(),
                        logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
                        group = group,
                        epgChannelId = attrs["tvg-id"]?.takeIf { it.isNotBlank() },
                        kind = MediaKind.LIVE,
                    )
                    pendingName = null
                    attrs = emptyMap()
                    group = null
                }
            }
        }
        return out
    }

    private fun stableId(url: String): String {
        val md = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
        return "m3u_" + md.take(8).joinToString("") { "%02x".format(it) }
    }
}
