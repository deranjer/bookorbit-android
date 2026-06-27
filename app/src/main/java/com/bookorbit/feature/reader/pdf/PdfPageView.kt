package com.bookorbit.feature.reader.pdf

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Renders a single PDF page [index] at the width chosen by [settings], drawing search highlights and
 * forwarding taps on links / long-press to copy. Bitmaps are produced off the main thread by
 * [PdfRenderCore]; a white aspect-ratio placeholder shows until the bitmap arrives.
 */
@Composable
fun PdfPageView(
    core: PdfRenderCore,
    index: Int,
    baseWidthPx: Int,
    containerHeightPx: Int,
    settings: PdfReaderSettings,
    highlights: List<RectF>,
    activeHighlight: RectF?,
    onLink: (PdfLink) -> Unit,
    onLongPress: (Int) -> Unit,
    onToggleZoom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    val pageSize by produceState(PdfRenderCore.PageSize(1, 1), core, index) {
        value = core.pageSize(index)
    }
    val renderWidth = PdfLayout.renderWidthPx(pageSize.width, pageSize.height, baseWidthPx, containerHeightPx, settings)
    val rotation = settings.rotation

    val bitmap by produceState<Bitmap?>(null, core, index, renderWidth, rotation) {
        value = null
        value = core.renderPage(index, renderWidth, rotation)
    }

    val links by produceState<List<PdfLink>>(emptyList(), core, index, settings.hasTextLayer()) {
        value = if (settings.hasTextLayer()) core.pageLinks(index) else emptyList()
    }

    val (dispWpx, dispHpx) = PdfLayout.displayedSize(pageSize.width, pageSize.height, renderWidth, rotation)
    val widthDp = with(density) { dispWpx.toDp() }
    val heightDp = with(density) { dispHpx.toDp() }

    Box(
        modifier = modifier
            .size(widthDp, heightDp)
            .background(Color.White)
            .pointerInput(links, dispWpx, dispHpx) {
                detectTapGestures(
                    onLongPress = { onLongPress(index) },
                    onDoubleTap = { onToggleZoom() },
                    onTap = { offset -> hitLink(links, offset, pageSize, renderWidth, rotation)?.let(onLink) },
                )
            },
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth())
        }
        if (highlights.isNotEmpty() || activeHighlight != null) {
            // The bitmap fills the box width 1:1, so bitmap px == this DrawScope's px (device px).
            Canvas(modifier = Modifier.size(widthDp, heightDp)) {
                highlights.forEach { r ->
                    drawHighlight(r, pageSize, renderWidth, rotation, Color(0x55FFEB3B))
                }
                activeHighlight?.let { r ->
                    drawHighlight(r, pageSize, renderWidth, rotation, Color(0x88FF9800))
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHighlight(
    pageRect: RectF,
    pageSize: PdfRenderCore.PageSize,
    renderWidth: Int,
    rotation: Int,
    color: Color,
) {
    val px = PdfRenderCore.mapPageRect(pageRect, pageSize.width, pageSize.height, renderWidth, rotation)
    drawRect(
        color = color,
        topLeft = Offset(px.left, px.top),
        size = Size(px.width(), px.height()),
    )
}

private fun hitLink(
    links: List<PdfLink>,
    tap: Offset,
    pageSize: PdfRenderCore.PageSize,
    renderWidth: Int,
    rotation: Int,
): PdfLink? {
    // tap is in composable px (device px); link page-rects map into the same 1:1 px space.
    return links.firstOrNull { link ->
        link.rects.any { r ->
            val px = PdfRenderCore.mapPageRect(r, pageSize.width, pageSize.height, renderWidth, rotation)
            tap.x >= px.left && tap.x <= px.right && tap.y >= px.top && tap.y <= px.bottom
        }
    }
}

private fun PdfReaderSettings.hasTextLayer(): Boolean = android.os.Build.VERSION.SDK_INT >= 35
