package fr.nexoratv.tv.core.mac

import fr.nexoratv.tv.core.Net
import fr.nexoratv.tv.core.model.PlaylistSource
import fr.nexoratv.tv.core.model.SourceKind
import fr.nexoratv.tv.core.upgradedToXtreamIfPossible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject

class MacPortalException(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { NOT_FOUND, INVALID, NETWORK }
}

/**
 * Portail d'activation par adresse MAC (`nexoratv.fr/api/playlist?mac=`).
 * L'admin assigne un lien M3U / Xtream à un MAC côté site ; l'app le résout
 * ici. Ré-appelé à chaque chargement du catalogue (l'admin peut changer le
 * lien).
 */
object MacPortalClient {

    private const val ENDPOINT = "https://nexoratv.fr/api/playlist"

    /** Résout le MAC en source lisible. Lève [MacPortalException] sinon. */
    suspend fun resolve(mac: String, existingId: String? = null): PlaylistSource =
        withContext(Dispatchers.IO) {
            val url = ENDPOINT.toHttpUrl().newBuilder().addQueryParameter("mac", mac).build()
            val res = try {
                Net.http.newCall(Request.Builder().url(url).build()).execute()
            } catch (e: Exception) {
                throw MacPortalException(MacPortalException.Kind.NETWORK, "Portail injoignable : ${e.message}")
            }
            res.use {
                val body = it.body?.string().orEmpty()
                when (it.code) {
                    200 -> {
                        val o = JSONObject(body)
                        PlaylistSource(
                            id = existingId ?: java.util.UUID.randomUUID().toString(),
                            name = o.optString("name").ifEmpty { "Ma playlist" },
                            kind = SourceKind.M3U_URL,
                            m3uUrl = o.optString("m3uUrl").ifEmpty { null }
                                ?: throw MacPortalException(MacPortalException.Kind.NOT_FOUND, "Aucun lien assigné à cet appareil."),
                            epgUrl = o.optString("epgUrl").ifEmpty { null },
                            activationMac = mac,
                        ).upgradedToXtreamIfPossible()
                    }
                    404 -> throw MacPortalException(
                        MacPortalException.Kind.NOT_FOUND,
                        "Cet appareil n'est pas encore activé. Communiquez votre adresse au support NexoraTV.",
                    )
                    400 -> throw MacPortalException(MacPortalException.Kind.INVALID, "Adresse invalide.")
                    else -> throw MacPortalException(
                        MacPortalException.Kind.NETWORK, "Réponse inattendue du portail (${it.code}).",
                    )
                }
            }
        }
}
