package com.bookorbit.feature.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import com.bookorbit.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class ReadingTool { TRANSLATE, DICTIONARY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingToolsSheet(text: String, initialTool: ReadingTool, repository: ReadingToolsRepository, onDismiss: () -> Unit,
    dictionaries: com.bookorbit.feature.dictionary.DictionaryRepository? = null) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val preferences = remember { context.getSharedPreferences("reading_tools", android.content.Context.MODE_PRIVATE) }
    var language by rememberSaveable(initialTool) { mutableStateOf(preferences.getString(initialTool.name, if (initialTool == ReadingTool.TRANSLATE) "ru" else "en")
        ?.takeIf { it in ReadingToolsRepository.LANGUAGES } ?: "en") }
    var menu by remember { mutableStateOf(false) }
    var input by rememberSaveable(text) { mutableStateOf(text) }
    var result by remember { mutableStateOf<ReadingToolResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    var offline by rememberSaveable { mutableStateOf(preferences.getBoolean("dictionary_offline", false)) }
    val scope = rememberCoroutineScope()
    val title = stringResource(if (initialTool == ReadingTool.TRANSLATE) R.string.reader_translate else R.string.reader_dictionary)
    val limit = if (initialTool == ReadingTool.TRANSLATE) 5000 else 120
    fun invalidate() { generation++; job?.cancel(); result = null; loading = false; failed = false }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.reader_settings_done)) }
            }
            OutlinedTextField(input, onValueChange = { invalidate(); input = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.reader_lookup_text)) }, minLines = 1, maxLines = 5)
            if (initialTool == ReadingTool.DICTIONARY && dictionaries != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !offline, onClick = { invalidate(); offline = false; preferences.edit().putBoolean("dictionary_offline", false).apply() },
                        label = { Text(stringResource(R.string.dictionary_online)) })
                    FilterChip(selected = offline, onClick = { invalidate(); offline = true; preferences.edit().putBoolean("dictionary_offline", true).apply() },
                        label = { Text(stringResource(R.string.dictionary_offline)) })
                }
            }
            if (initialTool == ReadingTool.DICTIONARY && offline && dictionaries != null) {
                com.bookorbit.feature.dictionary.OfflineDictionaryPanel(input, dictionaries)
            } else {
            Box {
                OutlinedButton(onClick = { menu = true }) { Text(ReadingToolsRepository.LANGUAGES[language].orEmpty()) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    ReadingToolsRepository.LANGUAGES.forEach { (code, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            invalidate(); language = code; preferences.edit().putString(initialTool.name, code).apply(); menu = false
                        })
                    }
                }
            }
            Text(stringResource(if (initialTool == ReadingTool.TRANSLATE) R.string.reader_translate_notice else R.string.reader_dictionary_notice),
                style = MaterialTheme.typography.bodySmall)
            if (input.length > limit) Text(stringResource(R.string.reader_lookup_limit, limit), color = MaterialTheme.colorScheme.error)
            Button(enabled = !loading && input.isNotBlank() && input.length <= limit, onClick = {
                invalidate(); loading = true
                val request = generation
                val requestedText = input.trim()
                val requestedLanguage = language
                job = scope.launch {
                    try {
                        val answer = if (initialTool == ReadingTool.TRANSLATE) repository.translate(requestedText, requestedLanguage) else repository.dictionary(requestedText, requestedLanguage)
                        if (request == generation) result = answer
                    }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { if (request == generation) failed = true }
                    finally { if (request == generation) loading = false }
                }
            }) { Text(title) }
            if (loading) CircularProgressIndicator()
            if (failed) Text(stringResource(R.string.reader_lookup_failed), color = MaterialTheme.colorScheme.error)
            result?.let { value ->
                SelectionContainer { Text(if (initialTool == ReadingTool.DICTIONARY) HtmlCompat.fromHtml(value.text, HtmlCompat.FROM_HTML_MODE_LEGACY).toString() else value.text) }
                TextButton(onClick = { uriHandler.openUri(value.sourceUrl) }) { Text(value.source) }
            }
            }
        }
    }
}
