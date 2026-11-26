package com.bookorbit.feature.readinghelp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bookorbit.R
import com.bookorbit.core.model.ReadingHelpRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Sends only the visible, user-selected excerpt. Never automatically sends surrounding chapters. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingHelpSheet(fileId: Int, passage: String, repository: ReadingHelpRepository, onDismiss: () -> Unit) {
    var access by remember { mutableStateOf<ReadingHelpRepository.Access?>(null) }
    var kind by remember { mutableStateOf("recap-note") }
    var term by remember { mutableStateOf("") }
    var careful by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<ReadingHelpRepository.Problem?>(null) }
    var running by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    var cancelled by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val answerLanguage = LocalConfiguration.current.locales[0].toLanguageTag()
    LaunchedEffect(repository) { access = repository.access() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) job?.cancel() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); job?.cancel() }
    }
    val valid = passage.isNotBlank() && passage.length <= 12_000 && (kind != "term" || term.isNotBlank() && term.length <= 120)
    fun start() {
        if (running || !valid) return
        job = scope.launch {
            running = true; problem = null; answer = ""; completed = false; cancelled = false
            try {
                val language = answerLanguage.takeIf { it.matches(Regex("[A-Za-z]{2,3}(-[A-Za-z0-9]{1,8})*")) } ?: "en"
                repository.answer(ReadingHelpRequest(kind, fileId, language, listOf(passage),
                    term = term.takeIf { kind == "term" }, scope = "chapter".takeIf { kind == "term" }, careful = careful), expectedRevision = access?.revision, expectedOwner = access?.owner).collect { event ->
                    when (event.type) {
                        "delta" -> answer += event.text
                        "done" -> { answer = event.text; completed = true }
                    }
                }
            } catch (e: CancellationException) { cancelled = true; throw e }
            catch (e: ReadingHelpRepository.Failure) { problem = e.problem }
            catch (e: Exception) { problem = ReadingHelpRepository.Problem.NETWORK }
            finally { running = false }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.help_title), style = MaterialTheme.typography.headlineMedium)
            access?.let { available -> Text(when {
                available.problem == ReadingHelpRepository.Problem.CONFIGURATION -> stringResource(R.string.ai_configuration_required)
                available.connection?.route == com.bookorbit.feature.ai.AiRoute.DIRECT -> stringResource(R.string.ai_active_direct, available.connection.provider.displayName, available.connection.helpModel) + "\n" + com.bookorbit.feature.ai.aiEndpointLabel(available.connection)
                else -> stringResource(R.string.ai_active_server)
            }, style = MaterialTheme.typography.bodySmall) }
            Text(stringResource(R.string.help_context), style = MaterialTheme.typography.bodyMedium)
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                SelectionContainer { Text(passage, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
            }
            if (access == null) CircularProgressIndicator()
            access?.problem?.let { Text(stringResource(helpProblemString(it)), color = MaterialTheme.colorScheme.error) }
            if (access?.enabled == true) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == "recap-note", onClick = { kind = "recap-note" }, enabled = !running, label = { Text(stringResource(R.string.help_recap)) })
                    FilterChip(selected = kind == "term", onClick = { kind = "term" }, enabled = !running, label = { Text(stringResource(R.string.help_term)) })
                }
                if (kind == "term") OutlinedTextField(value = term, onValueChange = { term = it }, enabled = !running,
                    label = { Text(stringResource(R.string.help_term_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (access?.careful == true) Row {
                    Checkbox(checked = careful, onCheckedChange = { careful = it }, enabled = !running)
                    Text(stringResource(R.string.help_careful), Modifier.padding(top = 12.dp))
                }
                if (!valid) Text(stringResource(R.string.help_invalid), color = MaterialTheme.colorScheme.error)
                if (running) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { job?.cancel() }) { Text(stringResource(R.string.cancel)) }
                } else Button(onClick = { confirm = true }, enabled = valid, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.help_ask)) }
            }
            if (answer.isNotEmpty()) SelectionContainer { Text(answer, style = MaterialTheme.typography.bodyLarge) }
            if (completed) Text(stringResource(R.string.help_completed), style = MaterialTheme.typography.labelMedium)
            if (cancelled) Text(stringResource(R.string.help_cancelled), style = MaterialTheme.typography.bodySmall)
            problem?.let { Text(stringResource(helpProblemString(it)), color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.help_confirm_title)) },
        text = { Text(if (access?.connection?.route == com.bookorbit.feature.ai.AiRoute.DIRECT)
            stringResource(R.string.ai_direct_confirm, access?.connection?.provider?.displayName.orEmpty(), access?.connection?.helpModel.orEmpty()) + "\n\n" +
                access?.connection?.let { com.bookorbit.feature.ai.aiEndpointLabel(it) }.orEmpty() +
                (if (access?.connection?.baseUrl?.startsWith("http:") == true) "\n" + stringResource(R.string.ai_http_warning) else "")
            else stringResource(R.string.help_confirm_body)) },
        confirmButton = { TextButton(onClick = { confirm = false; start() }) { Text(stringResource(R.string.help_send)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } })
}

private fun helpProblemString(problem: ReadingHelpRepository.Problem) = when (problem) {
    ReadingHelpRepository.Problem.RESTRICTED -> R.string.help_restricted
    ReadingHelpRepository.Problem.FORBIDDEN -> R.string.help_forbidden
    ReadingHelpRepository.Problem.DISABLED -> R.string.help_disabled
    ReadingHelpRepository.Problem.UNSUPPORTED -> R.string.help_unsupported
    ReadingHelpRepository.Problem.AUTH -> R.string.guide_auth
    ReadingHelpRepository.Problem.BUDGET -> R.string.help_budget
    ReadingHelpRepository.Problem.READER_CAP -> R.string.help_reader_cap
    ReadingHelpRepository.Problem.CAREFUL_OFF -> R.string.help_careful_off
    ReadingHelpRepository.Problem.TIMEOUT -> R.string.help_timeout
    ReadingHelpRepository.Problem.REFUSED -> R.string.help_refused
    ReadingHelpRepository.Problem.TOO_LONG -> R.string.help_too_long
    ReadingHelpRepository.Problem.NETWORK -> R.string.guide_network
    ReadingHelpRepository.Problem.PROVIDER -> R.string.help_provider
    ReadingHelpRepository.Problem.CONFIGURATION -> R.string.ai_configuration_required
}
