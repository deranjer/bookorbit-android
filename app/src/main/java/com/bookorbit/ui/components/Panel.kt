package com.bookorbit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bookorbit.ui.theme.BookOrbitTheme

/** The web client's `rounded-2xl` panel corner. */
val PanelShape = RoundedCornerShape(16.dp)

/**
 * A bordered card on the page, the web client's panel. [accent] draws the border in the primary
 * colour at 30%, as the web does around its home shelves, so a panel reads as a featured block.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = BookOrbitTheme.tokens
    Column(
        modifier = modifier
            .clip(PanelShape)
            .background(tokens.card)
            .border(1.dp, if (accent) tokens.accentLine else tokens.border, PanelShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/** A small icon in a bordered square, the web's marker beside a panel title. */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = BookOrbitTheme.tokens.foreground) {
    val tokens = BookOrbitTheme.tokens
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(tokens.muted.copy(alpha = 0.5f))
            .border(1.dp, tokens.border, MaterialTheme.shapes.extraSmall),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
    }
}

/** A number in a round muted pill, for how many items a panel holds. */
@Composable
fun CountPill(count: Int, modifier: Modifier = Modifier) {
    val tokens = BookOrbitTheme.tokens
    Text(
        count.toString(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = tokens.foreground,
        modifier = modifier
            .clip(CircleShape)
            .background(tokens.muted)
            .border(1.dp, tokens.border, CircleShape)
            .padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

/** A panel's title row: icon tile, bold title, optional count, and an action at the end. */
@Composable
fun PanelHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    count: Int? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) IconTile(icon, Modifier.padding(end = 10.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (count != null && count > 0) CountPill(count, Modifier.padding(start = 8.dp))
        }
        action?.invoke(this)
    }
}

/**
 * Rows grouped in one bordered panel with hairlines between them, the way the web lays out settings
 * and the You page. Each entry of [rows] draws one row.
 */
@Composable
fun GroupedRows(
    modifier: Modifier = Modifier,
    rows: List<@Composable () -> Unit>,
) {
    Panel(modifier = modifier, contentPadding = PaddingValues(0.dp)) {
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = BookOrbitTheme.tokens.border)
            row()
        }
    }
}
