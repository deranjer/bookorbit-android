package com.bookorbit.feature.dictionary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bookorbit.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun OfflineDictionaryPanel(word: String, repository: DictionaryRepository, showLookup: Boolean = true) {
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("reading_tools", android.content.Context.MODE_PRIVATE) }
    var installed by remember { mutableStateOf<List<InstalledDictionary>>(emptyList()) }
    var catalog by remember { mutableStateOf(BUNDLED_DICTIONARY_CATALOG) }
    var selected by rememberSaveable { mutableStateOf(preferences.getString("dictionary_id", "eng-rus")?.takeIf { it in DICTIONARY_IDS } ?: "eng-rus") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var result by remember(word, selected) { mutableStateOf<String?>(null) }
    var notFound by remember(word, selected) { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var license by remember { mutableStateOf<InstalledDictionary?>(null) }
    var removing by remember { mutableStateOf(false) }
    var lookupRequest by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(repository) {
        installed = repository.installed()
        catalog = catalog.map { pack -> installed.firstOrNull { it.pack.id == pack.id }?.pack ?: pack }
    }
    val pack = catalog.first { it.id == selected }
    val local = installed.firstOrNull { it.pack.id == selected }
    val input = word
    LaunchedEffect(word, selected) {
        if (lookupRequest != null && lookupRequest != (word to selected)) { job?.cancel(); failed = false; lookupRequest = null }
    }
    fun operation(lookup: Boolean = false, block: suspend () -> Unit) {
        lookupRequest = if (lookup) word to selected else null
        busy = true; failed = false; result = null; notFound = false; progress = null
        job = scope.launch {
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { failed = true }
            finally { busy = false; progress = null }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.dictionary_offline_notice), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DICTIONARY_IDS.forEach { id -> FilterChip(selected = id == selected, enabled = !busy,
                onClick = { selected = id; preferences.edit().putString("dictionary_id", id).apply(); failed = false }, label = { Text(dictionaryName(id)) }) }
        }
        Text("FreeDict / WikDict · ${pack.version} · CC BY-SA 3.0", style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.dictionary_size, pack.bytes / 1024, pack.headwords), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.dictionary_attribution), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { uri.openUri("https://creativecommons.org/licenses/by-sa/3.0/") }) { Text(stringResource(R.string.dictionary_license)) }
        local?.let {
            Text(stringResource(R.string.dictionary_installed, it.pack.version, it.diskBytes / 1024), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { license = it }) { Text(stringResource(R.string.dictionary_source_notice)) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy, onClick = {
                operation {
                    repository.install(pack) { current, total -> progress = current to total }
                    installed = repository.installed()
                }
            }) { Text(stringResource(if (local == null) R.string.dictionary_download else R.string.dictionary_update)) }
            if (local != null) OutlinedButton(enabled = !busy, onClick = { removing = true }) { Text(stringResource(R.string.dictionary_remove)) }
            TextButton(enabled = !busy, onClick = { operation { catalog = repository.catalog().let { remote ->
                DICTIONARY_IDS.map { id -> remote.firstOrNull { it.id == id } ?: catalog.first { it.id == id } }
            } } }) { Text(stringResource(R.string.dictionary_catalog_refresh)) }
        }
        if (busy) {
            CircularProgressIndicator()
            progress?.let { (current, total) -> Text(if (current == total) stringResource(R.string.dictionary_indexing)
                else stringResource(R.string.dictionary_progress, current / 1024, total / 1024)) }
            TextButton(onClick = { job?.cancel() }) { Text(stringResource(R.string.dictionary_cancel)) }
        }
        if (showLookup) Button(enabled = local != null && !busy && input.isNotBlank() && input.length <= 120, onClick = {
            operation(lookup = true) {
                val answer = repository.lookup(selected, input)
                // A changed input cannot display a late result for the old word.
                result = answer; notFound = answer == null
            }
        }) { Text(stringResource(R.string.dictionary_find_offline)) }
        if (failed) Text(stringResource(R.string.dictionary_failed), color = MaterialTheme.colorScheme.error)
        if (notFound) Text(stringResource(R.string.dictionary_not_found))
        result?.let { SelectionContainer { Text(it) } }
    }
    if (removing) AlertDialog(onDismissRequest = { removing = false },
        title = { Text(stringResource(R.string.dictionary_remove)) }, text = { Text(dictionaryName(selected)) },
        confirmButton = { TextButton(onClick = { removing = false; operation { repository.remove(selected); installed = repository.installed() } }) { Text(stringResource(R.string.dictionary_remove)) } },
        dismissButton = { TextButton(onClick = { removing = false }) { Text(stringResource(R.string.dictionary_cancel)) } })
    license?.let { value -> AlertDialog(onDismissRequest = { license = null },
        title = { Text(value.title) }, text = { SelectionContainer { Text(value.notice + "\n\n" + value.pack.url + "\nSHA-512: " + value.pack.sha512,
            Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) } },
        confirmButton = { TextButton(onClick = { license = null }) { Text(stringResource(R.string.reader_settings_done)) } }) }
}

@Composable
internal fun dictionaryName(id: String): String = stringResource(if (id == "rus-eng") R.string.dictionary_rus_eng else R.string.dictionary_eng_rus)
