package com.bookorbit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bookorbit.R
import com.bookorbit.ui.theme.BookOrbitTheme

/**
 * The web client's search box: a filled, borderless 44dp field with a search icon and a clear
 * button, rather than Material's outlined text field.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val tokens = BookOrbitTheme.tokens
    val shape = MaterialTheme.shapes.medium
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = tokens.foreground),
        cursorBrush = SolidColor(tokens.foreground),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier,
        decorationBox = { field ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(shape)
                    .background(tokens.muted.copy(alpha = 0.6f))
                    .border(1.dp, tokens.border, shape)
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = tokens.mutedForeground, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = tokens.mutedForeground, maxLines = 1)
                    }
                    field()
                }
                if (value.isNotEmpty()) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(onClickLabel = stringResource(R.string.clear_search)) { onValueChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.clear_search), tint = tokens.mutedForeground, modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
    )
}

/**
 * A round selectable chip, the web's `FilterChip`: outlined when off, filled with the primary colour
 * when on. Use these in a [ChoiceChipRow] to switch between views of the same content.
 */
@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = BookOrbitTheme.tokens
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) tokens.primaryForeground else tokens.foreground,
        maxLines = 1,
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) tokens.primary else tokens.background)
            .border(1.dp, if (selected) tokens.primary else tokens.input, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/** A sideways-scrolling row of [ChoiceChip]s with exactly one selected. */
@Composable
fun ChoiceChipRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
) {
    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(labels) { index, label ->
            ChoiceChip(label, selected = index == selectedIndex, onClick = { onSelect(index) })
        }
    }
}
