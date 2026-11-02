package com.bookorbit.feature.reader

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bookorbit.R
import com.bookorbit.ui.asString

/** The selected word's definitions, grouped by the word they came from when a lemma was followed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefineSheet(
    state: ReaderViewModel.DefineState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            val result = state.result
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    result?.word ?: state.word,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false),
                )
                result?.phonetic?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                result?.audioUrl?.let { url -> PronunciationButton(url) }
            }
            Spacer(Modifier.size(12.dp))
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
                state.failed -> LookupError(stringResource(R.string.dictionary_load_error), onRetry)
                result == null -> Text(
                    stringResource(R.string.dictionary_no_definition),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    result.entries.groupBy { it.sourceWord }.entries.forEachIndexed { i, (word, entries) ->
                        if (i > 0) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            Text(word, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.size(4.dp))
                        }
                        entries.forEach { entry -> DictionarySenses(entry) }
                    }
                    Text(
                        stringResource(R.string.dictionary_source, result.provider.label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DictionarySenses(entry: DictionaryEntry) {
    if (entry.partOfSpeech.isNotBlank()) {
        Text(
            entry.partOfSpeech.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        )
    }
    entry.definitions.forEachIndexed { i, def ->
        Row(modifier = Modifier.padding(vertical = 3.dp)) {
            Text(
                "${i + 1}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
            Column {
                Text(def.definition, style = MaterialTheme.typography.bodyMedium)
                def.example?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Plays the dictionary's recording of the word; the player lives only as long as the sheet. */
@Composable
private fun PronunciationButton(url: String) {
    val player = remember(url) { arrayOfNulls<MediaPlayer>(1) }
    DisposableEffect(url) { onDispose { player[0]?.release() } }
    IconButton(onClick = {
        player[0]?.release()
        player[0] = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                setDataSource(url)
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        }.getOrNull()
    }) {
        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.play_pronunciation))
    }
}

/** The selection translated into the chosen language, with the language picker and a copy button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationSheet(
    state: ReaderViewModel.TranslationState,
    onTarget: (String) -> Unit,
    onCopy: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.translate_into),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReaderTranslator.LANGUAGES.forEach { code ->
                    FilterChip(
                        selected = code == state.target,
                        onClick = { if (code != state.target) onTarget(code) },
                        label = { Text(ReaderTranslator.displayName(code)) },
                    )
                }
            }
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Text(
                    state.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                val result = state.result
                when {
                    state.loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
                    state.error != null -> LookupError(state.error.asString(), onRetry)
                    result != null -> {
                        Text(result.translatedText, style = MaterialTheme.typography.bodyLarge)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                stringResource(R.string.translate_provider, result.provider),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onCopy(result.translatedText) }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.copy_text))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LookupError(message: String, onRetry: () -> Unit) {
    Column {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

/** Floating control while the book is read aloud: pause or resume, and stop. */
@Composable
fun ReadAloudBar(
    paused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        shadowElevation = 6.dp,
        color = Color(0xFF1E1E1E),
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
            Icon(Icons.Filled.Headphones, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                stringResource(if (paused) R.string.read_aloud_paused else R.string.read_aloud_playing),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 8.dp, end = 4.dp),
            )
            Box {
                if (paused) {
                    IconButton(onClick = onResume) { Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.resume_reading)) }
                } else {
                    IconButton(onClick = onPause) { Icon(Icons.Filled.Pause, contentDescription = stringResource(R.string.pause_reading)) }
                }
            }
            IconButton(onClick = onStop) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.stop_reading)) }
        }
    }
}
