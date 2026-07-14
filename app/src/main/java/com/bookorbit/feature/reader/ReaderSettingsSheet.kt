package com.bookorbit.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Label("Theme")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                READER_THEMES.forEach { theme ->
                    val bg = Color(if (settings.isDark) theme.darkBg else theme.lightBg)
                    val fg = Color(if (settings.isDark) theme.darkFg else theme.lightFg)
                    val selected = settings.themeName == theme.name
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(bg)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            )
                            .clickable { onChange(settings.copy(themeName = theme.name)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("A", color = fg, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Label("Mode")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !settings.isDark, onClick = { onChange(settings.copy(isDark = false)) }, label = { Text("Light") })
                FilterChip(selected = settings.isDark, onClick = { onChange(settings.copy(isDark = true)) }, label = { Text("Dark") })
            }

            Label("Layout")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = settings.flow == "paginated", onClick = { onChange(settings.copy(flow = "paginated")) }, label = { Text("Paginated") })
                FilterChip(selected = settings.flow == "scrolled", onClick = { onChange(settings.copy(flow = "scrolled")) }, label = { Text("Scrolled") })
            }

            Label("Page turn")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = settings.pageTurnMode == "tap", onClick = { onChange(settings.copy(pageTurnMode = "tap")) }, label = { Text("Tap") })
                FilterChip(selected = settings.pageTurnMode == "swipe", onClick = { onChange(settings.copy(pageTurnMode = "swipe")) }, label = { Text("Swipe") })
            }

            Stepper(
                label = "Font size",
                value = "${settings.fontSize}",
                onDecrement = { onChange(settings.copy(fontSize = clampFontSize(settings.fontSize - 1))) },
                onIncrement = { onChange(settings.copy(fontSize = clampFontSize(settings.fontSize + 1))) },
            )
            Stepper(
                label = "Line height",
                value = String.format("%.1f", settings.lineHeight),
                onDecrement = { onChange(settings.copy(lineHeight = clampLineHeight(settings.lineHeight - 0.1))) },
                onIncrement = { onChange(settings.copy(lineHeight = clampLineHeight(settings.lineHeight + 0.1))) },
            )

            Label("Font")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FONT_FAMILIES.forEach { font ->
                    FilterChip(
                        selected = settings.fontFamily == font.value,
                        onClick = { onChange(settings.copy(fontFamily = font.value)) },
                        label = { Text(font.label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Stepper(label: String, value: String, onDecrement: () -> Unit, onIncrement: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onDecrement) { Icon(Icons.Filled.Remove, contentDescription = "Decrease") }
        Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 8.dp))
        IconButton(onClick = onIncrement) { Icon(Icons.Filled.Add, contentDescription = "Increase") }
    }
}
