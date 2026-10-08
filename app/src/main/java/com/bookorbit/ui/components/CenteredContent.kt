package com.bookorbit.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Default cap for single-column screens (reading-width lists and forms). */
val ReadableWidth = 720.dp

/**
 * Keeps single-column content at a readable width and centred on wide windows (tablets, unfolded
 * foldables, landscape), instead of stretching text and cards across the whole screen. On phones the
 * content is narrower than [maxWidth] already, so this changes nothing.
 */
@Composable
fun CenteredContent(
    modifier: Modifier = Modifier,
    maxWidth: Dp = ReadableWidth,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxWidth().fillMaxHeight()) { content() }
    }
}
