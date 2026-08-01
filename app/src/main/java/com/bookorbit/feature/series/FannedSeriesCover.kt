package com.bookorbit.feature.series

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bookorbit.ui.LocalImageUrls
import kotlin.math.abs

private const val FAN_ROTATION_DEGREES = 10f
private const val FAN_OFFSET_FRACTION = 0.16f
private const val COVER_WIDTH_FRACTION = 0.48f

/**
 * A series "card deck" preview: up to 3 overlapping, fanned book covers, or a colored
 * initial-letter tile when the series has no covers to show.
 */
@Composable
fun FannedSeriesCover(
    coverBookIds: List<Int>,
    seriesName: String,
    modifier: Modifier = Modifier,
) {
    val imageUrls = LocalImageUrls.current
    val covers = coverBookIds.take(3)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (covers.isEmpty()) {
            val (background, foreground) = seriesInitialStyle(seriesName)
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.82f)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(background),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    seriesName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = foreground,
                )
            }
        } else {
            val mid = (covers.size - 1) / 2f
            covers.forEachIndexed { index, bookId ->
                val distanceFromCenter = index - mid
                AsyncImage(
                    model = imageUrls.cover(bookId),
                    contentDescription = seriesName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth(COVER_WIDTH_FRACTION)
                        .aspectRatio(2f / 3f)
                        .align(Alignment.Center)
                        .graphicsLayer {
                            translationX = distanceFromCenter * FAN_OFFSET_FRACTION * size.width * 3f
                            rotationZ = distanceFromCenter * FAN_ROTATION_DEGREES
                            // Center covers draw on top of the ones fanned out behind them.
                            shadowElevation = (3f - abs(distanceFromCenter)) * 2f
                        }
                        .clip(RoundedCornerShape(4.dp)),
                )
            }
        }
    }
}

private fun seriesInitialStyle(seriesName: String): Pair<Color, Color> {
    val hue = (seriesName.hashCode().let { abs(it) } % 360).toFloat()
    val background = Color.hsv(hue, 0.45f, 0.55f)
    val foreground = Color.hsv(hue, 0.15f, 0.98f)
    return background to foreground
}
