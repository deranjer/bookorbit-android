package com.bookorbit.feature.reader.pdf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderSettingsSheet(
    settings: PdfReaderSettings,
    onChange: (PdfReaderSettings) -> Unit,
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
            Label("Scroll")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.scrollMode == PdfReaderSettings.ScrollMode.CONTINUOUS,
                    onClick = { onChange(settings.copy(scrollMode = PdfReaderSettings.ScrollMode.CONTINUOUS)) },
                    label = { Text("Continuous") },
                )
                FilterChip(
                    selected = settings.scrollMode == PdfReaderSettings.ScrollMode.PAGINATED,
                    onClick = { onChange(settings.copy(scrollMode = PdfReaderSettings.ScrollMode.PAGINATED)) },
                    label = { Text("Paginated") },
                )
            }

            Label("Spread")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.spread == PdfReaderSettings.Spread.NONE,
                    onClick = { onChange(settings.copy(spread = PdfReaderSettings.Spread.NONE)) },
                    label = { Text("Single") },
                )
                FilterChip(
                    selected = settings.spread == PdfReaderSettings.Spread.ODD,
                    onClick = { onChange(settings.copy(spread = PdfReaderSettings.Spread.ODD)) },
                    label = { Text("Odd") },
                )
                FilterChip(
                    selected = settings.spread == PdfReaderSettings.Spread.EVEN,
                    onClick = { onChange(settings.copy(spread = PdfReaderSettings.Spread.EVEN)) },
                    label = { Text("Even") },
                )
            }

            Label("Zoom")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.zoomMode == PdfReaderSettings.ZoomMode.FIT_WIDTH,
                    onClick = { onChange(settings.copy(zoomMode = PdfReaderSettings.ZoomMode.FIT_WIDTH)) },
                    label = { Text("Fit width") },
                )
                FilterChip(
                    selected = settings.zoomMode == PdfReaderSettings.ZoomMode.FIT_PAGE,
                    onClick = { onChange(settings.copy(zoomMode = PdfReaderSettings.ZoomMode.FIT_PAGE)) },
                    label = { Text("Fit page") },
                )
                FilterChip(
                    selected = settings.zoomMode == PdfReaderSettings.ZoomMode.CUSTOM,
                    onClick = { onChange(settings.withCustomScale(settings.customScale)) },
                    label = { Text("Custom") },
                )
            }

            if (settings.zoomMode == PdfReaderSettings.ZoomMode.CUSTOM) {
                Stepper(
                    label = "Scale",
                    value = String.format("%.2fx", settings.customScale),
                    onDecrement = { onChange(settings.withCustomScale(settings.customScale - 0.25)) },
                    onIncrement = { onChange(settings.withCustomScale(settings.customScale + 0.25)) },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Rotation", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("${settings.rotation}°", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 8.dp))
                IconButton(onClick = { onChange(settings.rotatedCw()) }) {
                    Icon(Icons.Filled.RotateRight, contentDescription = "Rotate")
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
