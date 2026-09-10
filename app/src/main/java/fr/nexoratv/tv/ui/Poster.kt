package fr.nexoratv.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import fr.nexoratv.tv.core.model.Channel
import fr.nexoratv.tv.core.model.MediaKind
import fr.nexoratv.tv.ui.theme.NexoraGradient
import fr.nexoratv.tv.ui.theme.NexoraInkFaint
import fr.nexoratv.tv.ui.theme.NexoraSurface2

/**
 * Tuile d'affiche : cadre verrouillé (2:3 VOD/séries, 16:9 live), fondu doux
 * de la couleur de fond vers l'image (plus de « carré gris qui pop »), titre
 * sur 2 lignes de hauteur fixe. Zoom + contour dégradé au focus.
 */
@Composable
fun PosterCard(ch: Channel, onClick: () -> Unit) {
    val live = ch.kind == MediaKind.LIVE
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, NexoraGradient),
                shape = RoundedCornerShape(12.dp),
            ),
        ),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (live) 16f / 9f else 2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NexoraSurface2),
                contentAlignment = Alignment.Center,
            ) {
                if (ch.logo.isNullOrEmpty()) {
                    Text(
                        ch.name.take(1).uppercase(),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = NexoraInkFaint,
                    )
                } else {
                    val ph = ColorPainter(NexoraSurface2)
                    AsyncImage(
                        model = ch.logo,
                        contentDescription = ch.name,
                        contentScale = if (live) ContentScale.Fit else ContentScale.Crop,
                        placeholder = ph,
                        error = ph,
                        fallback = ph,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(if (live) 10.dp else 0.dp),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 6.dp, start = 2.dp, end = 2.dp).height(38.dp)) {
                Text(
                    ch.name,
                    maxLines = 2,
                    minLines = 2,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = Color(0xFFC7CEDE),
                )
            }
        }
    }
}
