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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import java.util.Locale
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
    var selected by rememberSaveable { mutableStateOf(preferences.getString("dictionary_id", "eng-rus")?.takeIf(::validDictionaryId) ?: "eng-rus") }
    var loadingCatalog by remember { mutableStateOf(true) }
    var choosing by remember { mutableStateOf(false) }
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
        try {
            installed = repository.installed()
            catalog = mergeDictionaryCatalog(repository.availableCatalog(), installed)
            if (catalog.none { it.id == selected }) selected = catalog.first().id
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { failed = true }
        finally { loadingCatalog = false }
    }
    val pack = catalog.firstOrNull { it.id == selected } ?: catalog.first()
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
        TextButton(enabled = !busy && !loadingCatalog, onClick = { choosing = true }) { Text(dictionaryName(pack.id)) }
        Text(stringResource(R.string.dictionary_catalog_count, catalog.size), style = MaterialTheme.typography.bodySmall)
        Text("FreeDict · ${pack.version} · ${pack.id}", style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.dictionary_size, pack.bytes / 1024, pack.headwords), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.dictionary_package_license_notice), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { uri.openUri("https://freedict.org/downloads/") }) { Text(stringResource(R.string.dictionary_catalog_source)) }
        local?.let {
            Text(stringResource(R.string.dictionary_installed, it.pack.version, it.diskBytes / 1024), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { license = it }) { Text(stringResource(R.string.dictionary_source_notice)) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy && !loadingCatalog, onClick = {
                operation {
                    repository.install(pack) { current, total -> progress = current to total }
                    installed = repository.installed()
                }
            }) { Text(stringResource(if (local == null) R.string.dictionary_download else R.string.dictionary_update)) }
            if (local != null) OutlinedButton(enabled = !busy && !loadingCatalog, onClick = { removing = true }) { Text(stringResource(R.string.dictionary_remove)) }
            TextButton(enabled = !busy && !loadingCatalog, onClick = { operation { catalog = mergeDictionaryCatalog(repository.catalog(), installed) } }) { Text(stringResource(R.string.dictionary_catalog_refresh)) }
        }
        if (busy || loadingCatalog) {
            CircularProgressIndicator()
            progress?.let { (current, total) -> Text(if (current == total) stringResource(R.string.dictionary_indexing)
                else stringResource(R.string.dictionary_progress, current / 1024, total / 1024)) }
            if (busy) TextButton(onClick = { job?.cancel() }) { Text(stringResource(R.string.dictionary_cancel)) }
        }
        if (showLookup) Button(enabled = local != null && !busy && !loadingCatalog && input.isNotBlank() && input.length <= 1024, onClick = {
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
    if (choosing) DictionaryCatalogDialog(catalog, installed.map { it.pack.id }.toSet(), selected, { id ->
        selected = id; preferences.edit().putString("dictionary_id", id).apply(); failed = false; choosing = false
    }, { choosing = false })
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
internal fun dictionaryName(id: String): String {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    return dictionaryLanguage(id.substringBefore('-'), locale) + " → " + dictionaryLanguage(id.substringAfter('-'), locale)
}

private val dictionaryLanguages by lazy {
    Locale.getISOLanguages().mapNotNull { code -> runCatching { Locale.forLanguageTag(code).isO3Language to code }.getOrNull() }.toMap()
}

internal fun dictionaryLanguage(code: String, locale: Locale): String = Locale.forLanguageTag(dictionaryLanguages[code] ?: code)
    .getDisplayLanguage(locale).ifBlank { code }.replaceFirstChar { it.titlecase(locale) }

internal fun mergeDictionaryCatalog(catalog: List<DictionaryPackage>, installed: List<InstalledDictionary>): List<DictionaryPackage> =
    (catalog + installed.map { it.pack }).distinctBy { it.id }.sortedBy { it.id }

@Composable
internal fun DictionaryCatalogDialog(packs: List<DictionaryPackage>, installed: Set<String>, selected: String,
    onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var onlyInstalled by rememberSaveable { mutableStateOf(false) }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val rows = remember(packs, installed, search, onlyInstalled, locale) {
        val tokens = search.lowercase(locale).split(Regex("\\s+")).filter { it.isNotBlank() }
        packs.filter { pack -> (!onlyInstalled || pack.id in installed) && tokens.all { token ->
            val name = pack.id + " " + dictionaryLanguage(pack.id.substringBefore('-'), locale) + " " + dictionaryLanguage(pack.id.substringAfter('-'), locale)
            token in name.lowercase(locale)
        } }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.dictionary_choose)) },
        text = { Column(Modifier.heightIn(max = 500.dp)) {
            OutlinedTextField(search, { search = it }, singleLine = true, label = { Text(stringResource(R.string.dictionary_search_language)) })
            Row { Checkbox(onlyInstalled, { onlyInstalled = it }); Text(stringResource(R.string.dictionary_only_installed)) }
            Text(stringResource(R.string.dictionary_catalog_count, rows.size))
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(rows, key = { it.id }) { pack ->
                    TextButton(onClick = { onSelect(pack.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(dictionaryName(pack.id), color = if (pack.id == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            Text(pack.id + " · " + pack.version + if (pack.id in installed) " · " + stringResource(R.string.dictionary_on_device) else "",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.reader_settings_done)) } })
}
