package fr.nexoratv.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.Text
import fr.nexoratv.tv.BuildConfig
import fr.nexoratv.tv.UpdateState
import fr.nexoratv.tv.core.Dates
import fr.nexoratv.tv.data.UpdateInfo
import fr.nexoratv.tv.ui.theme.NexoraBackdrop
import fr.nexoratv.tv.ui.theme.NexoraBad
import fr.nexoratv.tv.ui.theme.NexoraGradient
import fr.nexoratv.tv.ui.theme.NexoraInk
import fr.nexoratv.tv.ui.theme.NexoraInkDim
import fr.nexoratv.tv.ui.theme.NexoraInkFaint
import fr.nexoratv.tv.ui.theme.NexoraLine
import fr.nexoratv.tv.ui.theme.NexoraNight
import fr.nexoratv.tv.ui.theme.NexoraOk
import fr.nexoratv.tv.ui.theme.NexoraPurple
import fr.nexoratv.tv.ui.theme.NexoraSurface
import fr.nexoratv.tv.ui.theme.NexoraSurfaceHi
import java.io.File

/** Panneau Paramètres (glisse depuis la droite) : mise à jour, compte,
 *  réglages à venir. */
@Composable
fun SettingsScreen(
    deviceMac: String,
    sourceName: String,
    expiresAt: Long?,
    update: UpdateState,
    onCheckUpdate: () -> Unit,
    onInstall: (UpdateInfo) -> Unit,
    onLaunchInstall: (File) -> Unit,
    onChangePlaylist: () -> Unit,
    onBack: () -> Unit,
) {
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { backFocus.requestFocus() } }

    Box(Modifier.fillMaxSize().background(NexoraBackdrop)) {
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .widthIn(max = 560.dp)
                .background(NexoraSurface)
                .verticalScroll(rememberScrollState())
                .padding(36.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.focusRequester(backFocus)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
                }
                Spacer(Modifier.width(10.dp))
                Text("Paramètres", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }

            Group("Mise à jour") {
                UpdateBlock(update, onCheckUpdate, onInstall, onLaunchInstall)
            }

            Group("Compte") {
                InfoRows(
                    listOf(
                        "Playlist" to sourceName,
                        "Appareil" to deviceMac,
                        "Expire le" to (expiresAt?.let { Dates.frenchDate(it) } ?: "—"),
                    )
                )
                Spacer(Modifier.height(2.dp))
                Button(onClick = onChangePlaylist) { Text("Changer de playlist") }
            }

            Group("Lecture & affichage") {
                SoonRows(
                    listOf(
                        "Qualité vidéo",
                        "Sous-titres par défaut",
                        "Démarrer sur",
                        "Vider le cache du catalogue",
                    )
                )
            }

            Text(
                "NexoraTV TV ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE}) · nexoratv.fr",
                color = NexoraInkFaint,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title.uppercase(),
            fontSize = 12.sp,
            letterSpacing = 1.4.sp,
            color = NexoraInkFaint,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

@Composable
private fun UpdateBlock(
    update: UpdateState,
    onCheck: () -> Unit,
    onInstall: (UpdateInfo) -> Unit,
    onLaunchInstall: (File) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NexoraSurfaceHi)
            .border(2.dp, NexoraGradient, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KeyValue("Version installée", "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")

        when (update) {
            is UpdateState.Idle -> Button(onClick = onCheck) { Text("Rechercher une mise à jour") }
            is UpdateState.Failed -> {
                Text(update.message, color = NexoraBad, fontSize = 13.sp)
                Button(onClick = onCheck) { Text("Réessayer") }
            }
            is UpdateState.Checking -> Text("Recherche…", color = NexoraInkDim, fontSize = 14.sp)
            is UpdateState.UpToDate ->
                Text("Vous êtes à jour.", color = NexoraOk, fontSize = 14.sp)
            is UpdateState.Available -> {
                KeyValue("Dernière version", "${update.info.versionName} disponible", NexoraOk)
                if (update.info.notes.isNotBlank()) {
                    Text(
                        update.info.notes,
                        color = NexoraInkDim,
                        fontSize = 13.sp,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(onClick = { onInstall(update.info) }) { Text("Installer la mise à jour") }
                Text(
                    "Téléchargement puis installation. Autorisez « sources inconnues » si l'appareil le demande.",
                    color = NexoraInkFaint,
                    fontSize = 12.sp,
                )
            }
            is UpdateState.Downloading -> {
                Text("Téléchargement… ${update.percent} %", color = NexoraInkDim, fontSize = 14.sp)
                LinearProgressIndicator(
                    progress = { update.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = NexoraPurple,
                    trackColor = Color.White.copy(alpha = 0.08f),
                )
            }
            is UpdateState.ReadyToInstall -> {
                Text("Téléchargement terminé.", color = NexoraOk, fontSize = 14.sp)
                Button(onClick = { onLaunchInstall(update.file) }) { Text("Installer maintenant") }
            }
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String, valueColor: Color = NexoraInkDim) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, fontSize = 14.sp, color = NexoraInk)
        Text(value, fontSize = 13.sp, color = valueColor, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun InfoRows(rows: List<Pair<String, String>>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, NexoraLine, RoundedCornerShape(14.dp)),
    ) {
        rows.forEachIndexed { i, (key, value) ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(NexoraLine))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(NexoraNight)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(key, fontSize = 14.sp, color = NexoraInk)
                Text(value, fontSize = 13.sp, color = NexoraInkDim, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun SoonRows(labels: List<String>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, NexoraLine, RoundedCornerShape(14.dp)),
    ) {
        labels.forEachIndexed { i, label ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(NexoraLine))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(NexoraNight)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, fontSize = 14.sp, color = NexoraInkFaint)
                Text(
                    "à venir",
                    fontSize = 11.sp,
                    color = NexoraPurple,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .border(1.dp, Color(0xFF27314C), RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}
