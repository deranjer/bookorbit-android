package com.bookorbit.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Label + selected chips + debounced typeahead suggestions, backed by a suspend search function.
 * Used by the filter sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TypeaheadMultiSelect(
    label: String,
    selected: List<String>,
    onSelectedChange: (List<String>) -> Unit,
    search: suspend (String) -> List<String>,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(query, selected) {
        if (query.isBlank()) {
            suggestions = emptyList()
        } else {
            delay(300)
            suggestions = runCatching { search(query) }.getOrDefault(emptyList())
                .filter { it !in selected }
        }
    }

    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)

        if (selected.isNotEmpty()) {
            FlowRow(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                selected.forEach { value ->
                    InputChip(
                        selected = true,
                        onClick = { onSelectedChange(selected - value) },
                        label = { Text(value) },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove") },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Add $label…") },
        )

        suggestions.take(8).forEach { suggestion ->
            Text(
                suggestion,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSelectedChange(selected + suggestion)
                        query = ""
                    }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
