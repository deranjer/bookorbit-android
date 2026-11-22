package com.bookorbit.feature.visionscan

import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.bookorbit.R
import com.bookorbit.core.model.VisionScanResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisionScanSheet(repository: VisionScanRepository, onBookClick: (Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var access by remember { mutableStateOf<VisionScanRepository.Access?>(null) }
    var image by remember { mutableStateOf<ByteArray?>(null) }
    var kind by remember { mutableStateOf("cover") }
    var result by remember { mutableStateOf<VisionScanResponse?>(null) }
    var problem by remember { mutableStateOf<VisionScanRepository.Problem?>(null) }
    var working by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var cancelled by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(repository) { access = repository.access() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) job?.cancel() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); job?.cancel() }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) job = scope.launch {
            working = true; problem = null; result = null; image = null; cancelled = false
            try { image = VisionPhoto.prepare(context, uri) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { problem = VisionScanRepository.Problem.IMAGE }
            finally { working = false }
        }
    }
    fun scan() {
        val photo = image ?: return
        if (working) return
        job = scope.launch {
            working = true; problem = null; result = null; cancelled = false
            try { result = repository.scan(kind, Base64.encodeToString(photo, Base64.NO_WRAP)) }
            catch (e: CancellationException) { cancelled = true; throw e }
            catch (e: VisionScanRepository.Failure) { problem = e.problem }
            catch (_: Exception) { problem = VisionScanRepository.Problem.NETWORK }
            finally { working = false }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.vision_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.vision_description), style = MaterialTheme.typography.bodyMedium)
            if (access == null) CircularProgressIndicator()
            access?.problem?.let { Text(stringResource(visionProblemString(it)), color = MaterialTheme.colorScheme.error) }
            if (access?.enabled == true) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == "cover", onClick = { kind = "cover"; result = null }, enabled = !working, label = { Text(stringResource(R.string.vision_cover)) })
                    FilterChip(selected = kind == "shelf", onClick = { kind = "shelf"; result = null }, enabled = !working, label = { Text(stringResource(R.string.vision_shelf)) })
                }
                image?.let { photo ->
                    AsyncImage(model = photo, contentDescription = stringResource(R.string.vision_preview),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp), contentScale = ContentScale.Fit)
                    Text(stringResource(R.string.vision_privacy), style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !working) {
                    Text(stringResource(R.string.vision_pick))
                }
                if (working) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    TextButton(onClick = { job?.cancel() }) { Text(stringResource(R.string.cancel)) }
                } else Button(onClick = { confirm = true }, enabled = image != null, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.vision_scan)) }
            }
            if (cancelled) Text(stringResource(R.string.vision_cancelled))
            problem?.let { Text(stringResource(visionProblemString(it)), color = MaterialTheme.colorScheme.error) }
            result?.let { response ->
                if (response.books.isEmpty()) Text(stringResource(R.string.vision_empty))
                else Text(stringResource(R.string.vision_check), style = MaterialTheme.typography.bodySmall)
                response.books.forEach { book ->
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(book.title, style = MaterialTheme.typography.titleMedium)
                            book.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            if (book.matches.isEmpty()) Text(stringResource(R.string.vision_no_match), style = MaterialTheme.typography.bodySmall)
                            book.matches.forEach { match -> TextButton(onClick = { onDismiss(); onBookClick(match.book.id) }) {
                                Text(stringResource(R.string.vision_open_match, match.book.title ?: book.title))
                            } }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(stringResource(R.string.vision_confirm_title)) },
        text = { Text(stringResource(R.string.vision_confirm_body)) },
        confirmButton = { TextButton(onClick = { confirm = false; scan() }) { Text(stringResource(R.string.vision_scan)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } })
}

private fun visionProblemString(problem: VisionScanRepository.Problem) = when (problem) {
    VisionScanRepository.Problem.RESTRICTED -> R.string.vision_restricted
    VisionScanRepository.Problem.FORBIDDEN -> R.string.help_forbidden
    VisionScanRepository.Problem.DISABLED -> R.string.vision_disabled
    VisionScanRepository.Problem.UNSUPPORTED -> R.string.vision_unsupported
    VisionScanRepository.Problem.AUTH -> R.string.guide_auth
    VisionScanRepository.Problem.BUDGET -> R.string.help_budget
    VisionScanRepository.Problem.RATE_LIMIT -> R.string.vision_rate_limit
    VisionScanRepository.Problem.TIMEOUT -> R.string.vision_timeout
    VisionScanRepository.Problem.REFUSED -> R.string.vision_refused
    VisionScanRepository.Problem.IMAGE -> R.string.vision_image_error
    VisionScanRepository.Problem.NETWORK -> R.string.guide_network
    VisionScanRepository.Problem.PROVIDER -> R.string.vision_provider
}
