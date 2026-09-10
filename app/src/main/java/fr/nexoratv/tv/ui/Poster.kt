package fr.nexoratv.tv.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import fr.nexoratv.tv.core.model.Channel
import fr.nexoratv.tv.core.model.MediaKind
import fr.nexoratv.tv.ui.theme.NexoraGradient
import fr.nexoratv.tv.ui.theme.NexoraInkFaint
import fr.nexoratv.tv.ui.theme.NexoraSurface2
import fr.nexoratv.tv.ui.theme.NexoraSurface3

/**
 * Tuile d'affiche : cadre verrouillé (2:3 VOD/séries, 16:9 live), skelette
 * pulsé pendant le chargement puis fondu (220 ms) vers l'image, titre sur
 * 2 lignes de hauteur fixe. Zoom + contour dégradé au focus.
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
                val model = ch.logo
                if (model.isNullOrEmpty()) {
                    Fallback(ch.name)
                } else {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(model).crossfade(220).build(),
                        contentDescription = ch.name,
                        contentScale = if (live) ContentScale.Fit else ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().padding(if (live) 10.dp else 0.dp),
                    ) {
                        when (painter.state) {
                            is AsyncImagePainter.State.Loading,
                            is AsyncImagePainter.State.Empty -> Skeleton()
                            is AsyncImagePainter.State.Error -> Fallback(ch.name)
                            is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                        }
                    }
                }
            }
            Box(Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp).height(32.dp)) {
                Text(
                    ch.name,
                    maxLines = 2,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = Color(0xFFC7CEDE),
                )
            }
        }
    }
}

@Composable
private fun Fallback(name: String) {
    Text(
        name.take(1).uppercase(),
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        color = NexoraInkFaint,
    )
}

@Composable
private fun Skeleton() {
    val t = rememberInfiniteTransition(label = "skel")
    val a by t.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "a",
    )
    Box(Modifier.fillMaxSize().alpha(a).background(NexoraSurface3))
}
