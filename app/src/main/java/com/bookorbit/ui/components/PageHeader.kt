package com.bookorbit.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bookorbit.ui.theme.BookOrbitTheme

/**
 * A page's title in the serif at headline size, with a muted line over and under it and actions at
 * the end, like the web client's `PageTitleRow`. The tab screens use this instead of a top app bar.
 * With [onTitleClick] the title becomes a switcher (a chevron beside it); [titleMenu] is anchored to
 * it, for the dropdown that opens.
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    eyebrow: String? = null,
    onTitleClick: (() -> Unit)? = null,
    titleMenu: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val tokens = BookOrbitTheme.tokens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = if (actions != null) 8.dp else 20.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) {
                Text(eyebrow, style = MaterialTheme.typography.bodyMedium, color = tokens.mutedForeground, maxLines = 1)
            }
            Box {
                Row(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .then(if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (onTitleClick != null) {
                        Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = tokens.mutedForeground, modifier = Modifier.padding(start = 4.dp).size(22.dp))
                    }
                }
                titleMenu?.invoke()
            }
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = tokens.mutedForeground, maxLines = 1)
            }
        }
        actions?.invoke(this)
    }
}

/** The widest a single column of text and controls gets before it reads badly. */
val ReadableWidth: Dp = 720.dp

/** Caps a single column at [maxWidth]; centre it with the parent's alignment. */
fun Modifier.readableWidth(maxWidth: Dp = ReadableWidth): Modifier = this.widthIn(max = maxWidth).fillMaxWidth()
