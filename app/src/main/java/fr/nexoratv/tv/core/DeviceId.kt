package fr.nexoratv.tv.core

import android.content.Context
import java.io.File
import java.security.SecureRandom

/**
 * Adresse MAC virtuelle, **stable par installation** — sert d'identifiant
 * pour le portail d'activation `nexoratv.fr/api/playlist` (comme un boîtier
 * MAG). Générée une fois, conservée dans un fichier local.
 * Préfixe OUI `00:1A:79` (identique à la version Flutter).
 */
object DeviceId {
    private const val OUI = "00:1A:79"

    fun mac(context: Context): String {
        val f = File(context.filesDir, "device_mac")
        f.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val rnd = SecureRandom()
        val tail = (0 until 3).joinToString(":") { "%02X".format(rnd.nextInt(256)) }
        val mac = "$OUI:$tail"
        runCatching { f.writeText(mac) }
        return mac
    }
}
