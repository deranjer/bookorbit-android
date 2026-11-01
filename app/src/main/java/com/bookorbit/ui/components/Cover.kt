package com.bookorbit.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.bookorbit.ui.theme.BookOrbitTheme

/**
 * A book cover, the same everywhere a book appears: the art clipped to [shape] with a hairline edge
 * so pale covers don't melt into the page. Under the art sits the title in the serif on a muted
 * ground, which shows while the art loads, when it fails, and when [model] is null (no cover).
 * [overlay] draws on top, for badges and progress.
 */
@Composable
fun BookCover(
    model: Any?,
    title: String?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    elevated: Boolean = false,
    titleSize: TextUnit = 17.sp,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val tokens = BookOrbitTheme.tokens
    Box(
        modifier = modifier
            .then(if (elevated) Modifier.shadow(12.dp, shape, ambientColor = tokens.scrim, spotColor = tokens.scrim) else Modifier)
            .clip(shape)
            .background(tokens.muted)
            .border(1.dp, tokens.border, shape),
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = titleSize,
                    lineHeight = titleSize * 1.24f,
                    hyphens = Hyphens.Auto,
                    lineBreak = LineBreak.Paragraph,
                ),
                color = tokens.mutedForeground,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(10.dp),
            )
        }
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        overlay()
    }
}

/**
 * The cover blown up and blurred behind a hero, fading into the page at the bottom, as the web's
 * book page does behind its cover. Blur needs Android 12; older versions get the art faded instead.
 */
@Composable
fun CoverBackdrop(model: Any?, modifier: Modifier = Modifier) {
    val background = BookOrbitTheme.tokens.background
    // Clipped, because the art is scaled up past the box to hide the blur's soft edges.
    Box(modifier.clipToBounds()) {
        if (model != null) {
            val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.3f
                        scaleY = 1.3f
                        alpha = if (canBlur) 0.55f else 0.25f
                    }
                    .then(if (canBlur) Modifier.blur(48.dp) else Modifier),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to background.copy(alpha = 0.2f), 1f to background)),
        )
    }
}
