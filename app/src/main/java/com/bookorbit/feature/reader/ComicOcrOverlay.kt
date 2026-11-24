package com.bookorbit.feature.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput

/** Coordinates are in the fitted image's unscaled parent, then share its zoom/pan transform. */
@Composable
fun ComicOcrOverlay(page: OcrPage, modifier: Modifier, selected: IntRange?, selectedWords: Set<Int>? = null,
    onExtend: (Int, Int) -> Unit = { _, _ -> }, onDoubleTap: (Offset) -> Unit = {}, onSelect: (Int, Boolean) -> Unit) {
    val select by rememberUpdatedState(onSelect)
    val doubleTap by rememberUpdatedState(onDoubleTap)
    val extend by rememberUpdatedState(onExtend)
    fun hit(point: Offset, width: Int, height: Int): Int? {
            val factor = minOf(width.toFloat() / page.width, height.toFloat() / page.height)
            val x = (point.x - (width - page.width * factor) / 2) / factor
            val y = (point.y - (height - page.height * factor) / 2) / factor
            return page.words.indexOfFirst { x >= it.left && x <= it.right && y >= it.top && y <= it.bottom }.takeIf { it >= 0 }
    }
    Canvas(modifier.pointerInput(page) {
        detectTapGestures(onTap = { hit(it, size.width, size.height)?.let { index -> select(index, false) } },
            onDoubleTap = { doubleTap(it) },
            onLongPress = { hit(it, size.width, size.height)?.let { index -> select(index, true) } })
    }.pointerInput(page) {
        // Observe before the tap detector consumes a held gesture. A normal swipe never selects.
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val anchor = hit(down.position, size.width, size.height)
            var movedEarly = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed || event.changes.count { it.pressed } > 1) break
                val moved = (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                val held = change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis
                if (moved && !held) movedEarly = true
                if (anchor != null && held && moved && !movedEarly) {
                    hit(change.position, size.width, size.height)?.let { extend(anchor, it) }
                    change.consume()
                }
            }
        }
    }) {
        val factor = minOf(size.width / page.width, size.height / page.height)
        val dx = (size.width - page.width * factor) / 2
        val dy = (size.height - page.height * factor) / 2
        page.words.forEachIndexed { index, word ->
            drawRect(if (selectedWords?.contains(index) == true || selected?.contains(index) == true) Color(0x9980CBC4) else Color(0x227EADFF),
                Offset(dx + word.left * factor, dy + word.top * factor), Size((word.right - word.left) * factor, (word.bottom - word.top) * factor))
        }
    }
}
