package fr.nexoratv.tv.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import fr.nexoratv.tv.core.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Une version disponible, lue depuis `update.json`. */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val url: String,
    val notes: String,
)

/**
 * Mises à jour intégrées. Le manifeste `update.json` vit à la racine du dépôt
 * `nexoratv-tv` (mis à jour dans le commit qui monte la version). L'APK est
 * téléchargé dans le cache puis installé via un `Intent` (l'utilisateur
 * confirme « sources inconnues » au besoin — permission déjà déclarée).
 */
object UpdateChecker {

    // API GitHub = toujours à jour (raw.githubusercontent est mis en cache CDN
    // jusqu'à 5 min et ignore les query-strings). Repli sur raw en cas de
    // limite de débit de l'API.
    private const val MANIFEST_API =
        "https://api.github.com/repos/Klovy-Dev/nexoratv-tv/contents/update.json?ref=main"
    private const val MANIFEST_RAW =
        "https://raw.githubusercontent.com/Klovy-Dev/nexoratv-tv/main/update.json"

    private fun apkFile(context: Context) = File(context.cacheDir, "NexoraTV-update.apk")

    suspend fun fetch(): UpdateInfo? = withContext(Dispatchers.IO) {
        parse(load(MANIFEST_API, github = true)) ?: parse(load(MANIFEST_RAW, github = false))
    }

    private fun load(url: String, github: Boolean): String? = runCatching {
        val b = Request.Builder().url(url)
            .header("User-Agent", "NexoraTV")
            .header("Cache-Control", "no-cache")
        if (github) b.header("Accept", "application/vnd.github.raw")
        Net.http.newCall(b.build()).execute().use { res ->
            if (!res.isSuccessful) null else res.body?.string()
        }
    }.getOrNull()

    private fun parse(body: String?): UpdateInfo? = body?.let {
        runCatching {
            val o = JSONObject(it)
            UpdateInfo(
                versionName = o.optString("versionName"),
                versionCode = o.optInt("versionCode"),
                url = o.getString("url"),
                notes = o.optString("notes"),
            )
        }.getOrNull()
    }

    /** Télécharge l'APK dans le cache. `onPercent` n'est appelé que si la
     *  taille est connue, et seulement quand le pourcentage change. */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onPercent: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val out = apkFile(context)
        if (out.exists()) out.delete()
        Net.http.newCall(Request.Builder().url(info.url).build()).execute().use { res ->
            if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
            val body = res.body ?: throw IOException("Réponse vide")
            val total = body.contentLength()
            var lastPct = -1
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(128 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        downloaded += n
                        if (total > 0) {
                            val pct = ((downloaded * 100) / total).toInt()
                            if (pct != lastPct) { lastPct = pct; onPercent(pct) }
                        }
                    }
                }
            }
        }
        out
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
