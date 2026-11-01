package com.bookorbit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bookorbit.ui.theme.BookOrbitTheme

// The web client's buttons (shadcn `Button`): 8dp corners rather than Material's pill, a 40dp row,
// and a 16dp icon beside the label. Primary is the near-black (light) or near-white (dark) fill;
// secondary is an outline on the page colour; ghost is text only.

private val ButtonHeight = 40.dp
private val ButtonPadding = PaddingValues(horizontal = 16.dp)

@Composable
private fun RowScope.ButtonContent(text: String, icon: ImageVector?) {
    if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = if (icon != null) Modifier.padding(start = 8.dp) else Modifier,
    )
}

/** The one action a screen is for: Read, Continue, Sign in. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        contentPadding = ButtonPadding,
        modifier = modifier.defaultMinSize(minHeight = ButtonHeight),
    ) { ButtonContent(text, icon) }
}

/** An action beside the primary one: Listen, Download, Add to collection. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val tokens = BookOrbitTheme.tokens
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        contentPadding = ButtonPadding,
        border = BorderStroke(1.dp, tokens.input),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = tokens.background, contentColor = tokens.foreground),
        modifier = modifier.defaultMinSize(minHeight = ButtonHeight),
    ) { ButtonContent(text, icon) }
}

/** A quiet text action: Retry, Remove, See all. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 12.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = BookOrbitTheme.tokens.foreground),
        modifier = modifier.defaultMinSize(minHeight = 36.dp),
    ) { ButtonContent(text, icon) }
}
