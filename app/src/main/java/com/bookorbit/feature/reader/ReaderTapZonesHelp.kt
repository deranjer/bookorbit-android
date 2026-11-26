package com.bookorbit.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bookorbit.R

/** Modal so help taps, swipes and Back cannot turn the page underneath it. */
@Composable
fun ReaderTapZonesHelp(
    onDismiss: () -> Unit,
    paginated: Boolean = true,
    rightToLeft: Boolean = false,
    comic: Boolean = false,
    pdf: Boolean = false,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))) {
            Row(Modifier.fillMaxSize()) {
                listOf(0.1 to 0.3f, 0.5 to 0.4f, 0.9 to 0.3f).forEach { (position, weight) ->
                    val action = readerTapAction(position, paginated, rightToLeft)
                    Box(Modifier.weight(weight).fillMaxHeight()
                        .background(if (action == ReaderTapAction.MENU) Color(0x5548A9A6) else Color(0x555B76D5))
                        .border(1.dp, Color.White.copy(alpha = 0.6f)).padding(8.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(when (action) {
                            ReaderTapAction.PREVIOUS -> R.string.previous_page
                            ReaderTapAction.NEXT -> R.string.next_page
                            ReaderTapAction.MENU -> R.string.tap_for_menu
                        }), color = Color.White, textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Surface(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp), shape = MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.reader_zones_title), Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            }
            Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(when {
                        comic -> R.string.reader_zones_comic
                        pdf && paginated -> R.string.reader_zones_pdf
                        paginated -> R.string.reader_zones_book
                        else -> R.string.reader_zones_scroll
                    }), textAlign = TextAlign.Center)
                    Button(onClick = onDismiss) { Text(stringResource(R.string.reader_zones_close)) }
                }
            }
        }
    }
}
