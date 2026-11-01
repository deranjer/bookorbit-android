package com.bookorbit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bookorbit.ui.theme.BookOrbitTheme
import com.bookorbit.ui.theme.darkFormatColor

/**
 * A label that wears its colour the way the web client's format and status chips do: the text in
 * the colour, over a 10% wash of it, inside a 40% border. [color] should be a pill token
 * ([com.bookorbit.ui.theme.BookOrbitTokens.pills] or a semantic one), which is tuned to clear AA as
 * text in the current theme.
 */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.extraSmall
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.40f), shape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * A file format laid over cover art. Covers can be any colour, so the chip is the dark, mostly
 * opaque cover-chip fill in both themes, and the format's colour is a dot beside the name.
 */
@Composable
fun FormatBadge(format: String, modifier: Modifier = Modifier) {
    val tokens = BookOrbitTheme.tokens
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(tokens.coverChip)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The dark theme's pill colours are the ones tuned for a dark ground, which the chip always is.
        Box(Modifier.size(6.dp).clip(CircleShape).background(darkFormatColor(format)))
        Text(format.uppercase(), style = MaterialTheme.typography.labelSmall, color = tokens.coverChipForeground)
    }
}
