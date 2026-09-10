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

    private const val MANIFEST =
        "https://raw.githubusercontent.com/Klovy-Dev/nexoratv-tv/main/update.json"

    private fun apkFile(context: Context) = File(context.cacheDir, "NexoraTV-update.apk")

    suspend fun fetch(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(MANIFEST)
                .header("Cache-Control", "no-cache")
                .build()
            val body = Net.http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@runCatching null
                res.body?.string().orEmpty()
            }
            val o = JSONObject(body)
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
