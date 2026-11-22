package com.bookorbit.feature.storyguide

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bookorbit.R
import com.bookorbit.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryGuideSheet(book: BookDetail, repository: StoryGuideRepository, onDismiss: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val editions = book.files.filter { it.format?.lowercase() in setOf("epub", "mobi", "azw3", "fb2", "txt") }
    var selected by remember(book.id) { mutableStateOf(editions.firstOrNull()?.id) }
    var snapshot by remember(selected) { mutableStateOf<StoryGuideRepository.Snapshot?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var working by remember(selected) { mutableStateOf(false) }
    var failed by remember(selected) { mutableStateOf(false) }
    var confirm by remember(selected) { mutableStateOf<String?>(null) }
    var completedSection by remember(selected, snapshot?.content?.textKey) { mutableIntStateOf(-1) }
    var query by remember(selected, snapshot?.content?.textKey) { mutableStateOf("") }
    val projection = remember(snapshot?.content, completedSection) {
        snapshot?.content?.let { StoryGuideSpoilers.project(it, completedSection) } ?: StoryGuideSpoilers.Projection()
    }
    val entries = remember(projection, query) { projection.entries.filter { query.isBlank() || it.name.contains(query, true) || it.text.contains(query, true) } }
    val relationships = remember(projection, query) { projection.relationships.filter {
        query.isBlank() || it.from.contains(query, true) || it.to.contains(query, true) || it.label.contains(query, true)
    } }
    val scope = rememberCoroutineScope()
    val fileId = selected
    LaunchedEffect(fileId, refresh) {
        if (fileId == null) return@LaunchedEffect
        do {
            snapshot = repository.load(book.id, fileId)
            val pending = snapshot?.status?.job?.state in setOf("queued", "running")
            if (pending) delay(5_000)
        } while (pending)
    }
    fun action(name: String) {
        if (fileId == null || working) return
        scope.launch {
            working = true; failed = false
            try {
                when (name) {
                    "build", "update" -> repository.build(book.id, fileId, name)
                    "retry" -> repository.retry(book.id, fileId)
                    "cancel" -> repository.cancel(book.id, fileId)
                }
                refresh++
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failed = true; refresh++ }
            finally { working = false }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
          item { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.guide_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.guide_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (editions.isEmpty()) Text(stringResource(R.string.guide_text_only))
            else {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { expanded = true }) {
                        Text(editions.firstOrNull { it.id == fileId }?.filename?.substringBeforeLast('.') ?: stringResource(R.string.edition_text))
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        editions.forEach { edition -> DropdownMenuItem(text = { Text(edition.filename?.substringBeforeLast('.') ?: edition.format.orEmpty().uppercase()) },
                            onClick = { selected = edition.id; expanded = false }) }
                    }
                }
                if (snapshot == null) CircularProgressIndicator()
                val current = snapshot
                current?.problem?.let { problem ->
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large) {
                        Text(stringResource(when (problem) {
                            StoryGuideRepository.Problem.RESTRICTED -> R.string.guide_restricted
                            StoryGuideRepository.Problem.UNSUPPORTED -> R.string.guide_unsupported
                            StoryGuideRepository.Problem.AUTH -> R.string.guide_auth
                            StoryGuideRepository.Problem.FORMAT -> R.string.guide_format
                            StoryGuideRepository.Problem.NETWORK -> R.string.guide_network
                        }), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                val status = current?.status
                status?.build?.takeIf { !it.allowed }?.let { build ->
                    Text(stringResource(when (build.reason) {
                        "off" -> R.string.guide_off
                        "language" -> R.string.guide_language
                        "no-text-file" -> R.string.guide_text_only
                        else -> R.string.guide_build_unavailable
                    }), style = MaterialTheme.typography.bodyMedium)
                }
                status?.job?.let { job ->
                    Text(stringResource(R.string.guide_job_status, job.state, job.phase.orEmpty()), style = MaterialTheme.typography.titleSmall)
                    if (job.total != null && job.total > 0 && job.done != null) LinearProgressIndicator(
                        progress = { (job.done.toFloat() / job.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    if (status.canManage && job.state in setOf("queued", "running", "paused")) {
                        TextButton(onClick = { action("cancel") }, enabled = !working) { Text(stringResource(R.string.cancel)) }
                    }
                    if (status.canManage && current.canRead && job.state in setOf("failed", "paused")) {
                        Button(onClick = { confirm = "retry" }, enabled = !working) { Text(stringResource(R.string.retry)) }
                    }
                }
                if (status != null && status.canManage && status.build.allowed && current.canRead && status.job == null) {
                    Button(onClick = { confirm = if (status.guide == null) "build" else "update" }, enabled = !working, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (status.guide == null) R.string.guide_build else R.string.guide_update))
                    }
                }
                val content = current?.content
                if (content != null) {
                    Text(stringResource(R.string.guide_spoiler_hint), style = MaterialTheme.typography.bodyMedium)
                    var chapterMenu by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { chapterMenu = true }) {
                            Text(content.sections.firstOrNull { it.s == completedSection }?.title ?: stringResource(
                                if (completedSection < 0) R.string.guide_no_chapter else R.string.guide_chapter, completedSection + 1))
                        }
                        DropdownMenu(expanded = chapterMenu, onDismissRequest = { chapterMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.guide_no_chapter)) }, onClick = { completedSection = -1; chapterMenu = false })
                            content.sections.forEach { section -> DropdownMenuItem(text = { Text(section.title ?: stringResource(R.string.guide_chapter, section.s + 1)) },
                                onClick = { completedSection = section.s; chapterMenu = false }) }
                        }
                    }
                    if (completedSection >= 0) OutlinedTextField(value = query, onValueChange = { query = it },
                        label = { Text(stringResource(R.string.guide_search_visible)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); focus.clearFocus() }))
                }
                if (failed) Text(stringResource(R.string.guide_action_failed), color = MaterialTheme.colorScheme.error)
                if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(onClick = { refresh++ }, enabled = !working) { Text(stringResource(R.string.guide_refresh)) }
            }
          } }
          if (query.isNotBlank() && entries.isEmpty() && relationships.isEmpty()) item {
              Text(stringResource(R.string.guide_no_visible_matches), style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          items(entries, key = { "entity:${it.key}" }) { entry ->
              Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                  Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                      Text(stringResource(when (entry.kind) {
                          "character" -> R.string.guide_kind_character
                          "place" -> R.string.guide_kind_place
                          "group" -> R.string.guide_kind_group
                          "event" -> R.string.guide_kind_event
                          "object" -> R.string.guide_kind_object
                          else -> R.string.guide_kind_term
                      }), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                      Text(entry.name, style = MaterialTheme.typography.titleMedium)
                      Text(entry.text, style = MaterialTheme.typography.bodyMedium)
                  }
              }
          }
          if (relationships.isNotEmpty()) item {
              Text(stringResource(R.string.guide_relationships), style = MaterialTheme.typography.titleLarge)
          }
          items(relationships, key = { "tie:${it.key}" }) { tie ->
              Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                  Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                      Text(tie.from, style = MaterialTheme.typography.titleMedium)
                      Text(relationshipLabel(tie), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                      Text(tie.to, style = MaterialTheme.typography.titleMedium)
                  }
              }
          }
          if (query.isBlank() && projection.recaps.isNotEmpty()) {
              item { Text(stringResource(R.string.guide_recaps), style = MaterialTheme.typography.titleLarge) }
              items(projection.recaps) { Text(it, style = MaterialTheme.typography.bodyMedium) }
          }
          item { Spacer(Modifier.height(24.dp)) }
        }
    }
    confirm?.let { name ->
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text(stringResource(R.string.guide_confirm_title)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.guide_confirm_provider))
                snapshot?.status?.estimate?.cost?.let { Text(stringResource(R.string.guide_estimated_cost, it.toString())) }
            } },
            confirmButton = { TextButton(onClick = { confirm = null; action(name) }) { Text(stringResource(R.string.guide_confirm)) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
private fun relationshipLabel(tie: StoryGuideSpoilers.Relationship): String = when (tie.type) {
    "parent_of" -> stringResource(R.string.guide_tie_parent)
    "grandparent_of" -> stringResource(R.string.guide_tie_grandparent)
    "aunt_or_uncle_of" -> stringResource(R.string.guide_tie_aunt_uncle)
    "sibling_of" -> stringResource(R.string.guide_tie_sibling)
    "spouse_of" -> stringResource(R.string.guide_tie_spouse)
    "betrothed_to" -> stringResource(R.string.guide_tie_betrothed)
    "cousin_of" -> stringResource(R.string.guide_tie_cousin)
    "relative_of" -> stringResource(R.string.guide_tie_relative)
    "loves" -> stringResource(R.string.guide_tie_loves)
    "bonded_to" -> stringResource(R.string.guide_tie_bonded)
    "friend_of" -> stringResource(R.string.guide_tie_friend)
    "enemy_of" -> stringResource(R.string.guide_tie_enemy)
    "rival_of" -> stringResource(R.string.guide_tie_rival)
    "teaches" -> stringResource(R.string.guide_tie_teaches)
    "works_for" -> stringResource(R.string.guide_tie_works)
    "studies_at" -> stringResource(R.string.guide_tie_studies)
    "member_of" -> stringResource(R.string.guide_tie_member)
    "owns" -> stringResource(R.string.guide_tie_owns)
    "lives_at" -> stringResource(R.string.guide_tie_lives)
    else -> tie.label // The format contract requires unknown types to retain the server fallback.
}
