package com.bookorbit.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/** Bounds and selection are local pixels. Prefer a free side; never place outside the viewport. */
internal fun selectionActionsTop(height: Int, panelHeight: Int, top: Float, bottom: Float, gap: Int): Int {
    val above = top - gap - panelHeight
    val below = bottom + gap
    return when {
        above >= gap -> above.toInt()
        below + panelHeight <= height - gap -> below.toInt()
        top > height - bottom -> gap
        else -> height - gap - panelHeight
    }.coerceIn(0, (height - panelHeight).coerceAtLeast(0))
}

/** Measures real content, including font scale, instead of assuming a one-row toolbar height. */
@Composable
internal fun SelectionActions(selection: Rect?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val insets = WindowInsets.safeDrawing
    Layout(modifier = modifier, content = { Box(Modifier.widthIn(max = 360.dp)) { content() } }) { measurables, constraints ->
        val gap = 12.dp.roundToPx()
        val topInset = insets.getTop(this)
        val bottomInset = insets.getBottom(this)
        val leftInset = insets.getLeft(this, layoutDirection)
        val rightInset = insets.getRight(this, layoutDirection)
        val availableHeight = (constraints.maxHeight - topInset - bottomInset).coerceAtLeast(0)
        val availableWidth = (constraints.maxWidth - leftInset - rightInset).coerceAtLeast(0)
        val panel = measurables.single().measure(Constraints(
            maxWidth = (availableWidth - gap * 2).coerceAtLeast(0),
            maxHeight = (availableHeight - gap * 2).coerceAtLeast(0)))
        val center = selection?.center?.x ?: (constraints.maxWidth / 2f)
        val minX = leftInset + gap.coerceAtMost(availableWidth - panel.width)
        val maxX = (constraints.maxWidth - rightInset - panel.width - gap).coerceAtLeast(minX)
        val x = (center - panel.width / 2).toInt().coerceIn(minX, maxX)
        val y = topInset + selectionActionsTop(availableHeight, panel.height,
            (selection?.top ?: 0f) - topInset, (selection?.bottom ?: 0f) - topInset, gap)
        layout(constraints.maxWidth, constraints.maxHeight) { panel.place(x, y) }
    }
}
