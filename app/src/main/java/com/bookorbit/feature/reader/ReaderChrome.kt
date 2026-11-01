package com.bookorbit.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bookorbit.R
import com.bookorbit.ui.theme.Motion
import kotlin.math.roundToInt

/** The page's own colours, which the reader's bars wear instead of a fixed dark strip. */
data class ReaderChromeColors(val background: Color, val content: Color) {
    val muted: Color get() = content.copy(alpha = 0.62f)
    val hairline: Color get() = content.copy(alpha = 0.12f)

    companion object {
        fun of(settings: ReaderSettings) = ReaderChromeColors(
            background = themeBackgroundColor(settings.themeName, settings.isDark),
            content = themeForegroundColor(settings.themeName, settings.isDark),
        )
    }
}

/** The bar along the top: back, the chapter over the book's title, then search, highlights, contents, settings. */
@Composable
fun BoxScope.ReaderTopBar(
    visible: Boolean,
    title: String?,
    chapter: String?,
    colors: ReaderChromeColors,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onHighlights: () -> Unit,
    onContents: () -> Unit,
    onSettings: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(Motion.enter()) + slideInVertically(Motion.enter()) { -it / 3 },
        exit = fadeOut(Motion.exit()) + slideOutVertically(Motion.exit()) { -it / 3 },
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        Column(Modifier.fillMaxWidth().background(colors.background.copy(alpha = 0.97f))) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = colors.content)
                }
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(
                        chapter ?: title.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (chapter != null && title != null && chapter != title) {
                        Text(title, style = MaterialTheme.typography.bodySmall, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                ChromeIcon(Icons.Outlined.Search, stringResource(R.string.search_in_book), colors, onSearch)
                ChromeIcon(Icons.Outlined.FormatQuote, stringResource(R.string.highlights), colors, onHighlights)
                ChromeIcon(Icons.AutoMirrored.Outlined.List, stringResource(R.string.contents), colors, onContents)
                ChromeIcon(Icons.Outlined.TextFields, stringResource(R.string.settings), colors, onSettings)
            }
            HorizontalDivider(color = colors.hairline)
        }
    }
}

/**
 * The bar along the bottom: the footer line (tap to change what it shows), and a scrubber with a
 * tick where each chapter starts, between previous- and next-chapter buttons.
 */
@Composable
fun BoxScope.ReaderBottomBar(
    visible: Boolean,
    fraction: Double,
    sectionFractions: List<Double>,
    footer: Pair<String, String>,
    colors: ReaderChromeColors,
    onSeek: (Double) -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onCycleFooter: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(Motion.enter()) + slideInVertically(Motion.enter()) { it / 3 },
        exit = fadeOut(Motion.exit()) + slideOutVertically(Motion.exit()) { it / 3 },
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        Column(Modifier.fillMaxWidth().background(colors.background.copy(alpha = 0.97f))) {
            HorizontalDivider(color = colors.hairline)
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 4.dp)) {
                FooterLine(footer, colors, onCycleFooter, Modifier.padding(horizontal = 16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChromeIcon(Icons.Outlined.SkipPrevious, stringResource(R.string.reader_previous_chapter), colors, onPreviousChapter)
                    Scrubber(fraction, sectionFractions, colors, onSeek, Modifier.weight(1f))
                    ChromeIcon(Icons.Outlined.SkipNext, stringResource(R.string.reader_next_chapter), colors, onNextChapter)
                }
            }
        }
    }
}

/** The footer on its own, quiet, while the bars are hidden, as a printed book's running foot. */
@Composable
fun BoxScope.ReaderRunningFoot(visible: Boolean, footer: Pair<String, String>, colors: ReaderChromeColors, onCycle: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(Motion.enter()),
        exit = fadeOut(Motion.exit()),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        FooterLine(
            footer,
            colors.copy(content = colors.content.copy(alpha = 0.55f)),
            onCycle,
            Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun FooterLine(footer: Pair<String, String>, colors: ReaderChromeColors, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.reader_footer_mode)
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(footer.first, style = MaterialTheme.typography.labelMedium, color = colors.muted, maxLines = 1, modifier = Modifier.weight(1f))
        Text(footer.second, style = MaterialTheme.typography.labelMedium, color = colors.muted, maxLines = 1)
    }
}

/** A slider over the whole book. The thumb follows a drag; the book jumps when it's let go. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Scrubber(
    fraction: Double,
    sectionFractions: List<Double>,
    colors: ReaderChromeColors,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val description = stringResource(R.string.reader_position)
    Box(modifier.height(40.dp), contentAlignment = Alignment.Center) {
        // Chapter starts, as small marks on the track.
        Canvas(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(10.dp)) {
            sectionFractions.drop(1).forEach { f ->
                val x = (f.toFloat().coerceIn(0f, 1f)) * size.width
                drawLine(colors.content.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            }
        }
        Slider(
            value = dragging ?: fraction.toFloat(),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek(it.toDouble()) }
                dragging = null
            },
            colors = SliderDefaults.colors(
                thumbColor = colors.content,
                activeTrackColor = colors.content.copy(alpha = 0.75f),
                inactiveTrackColor = colors.content.copy(alpha = 0.15f),
            ),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
        )
        dragging?.let {
            Text(
                "${(it * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = colors.content,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 0.dp),
            )
        }
    }
}

@Composable
private fun ChromeIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, colors: ReaderChromeColors, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = label, tint = colors.content) }
}

/** What the footer shows for [mode]: left and right text, as the web reader's footer modes. */
@Composable
fun footerText(mode: Int, position: ReadingPosition, percentage: Int, minutesRead: Long): Pair<String, String> {
    val percent = "$percentage%"
    return when (mode) {
        1 -> stringResource(R.string.reader_footer_reading, formatMinutes(minutesRead.toDouble())) to
            (if (position.minutesLeftInBook > 0) stringResource(R.string.reader_footer_left, formatMinutes(position.minutesLeftInBook)) else percent)
        2 -> (if (position.sectionTotal > 0) stringResource(R.string.reader_footer_chapter, position.section + 1, position.sectionTotal) else "") to
            (if (position.minutesLeftInSection > 0) stringResource(R.string.reader_footer_left_in_chapter, formatMinutes(position.minutesLeftInSection)) else percent)
        else -> (if (position.locationTotal > 0) stringResource(R.string.reader_footer_page, position.location + 1, position.locationTotal) else "") to percent
    }
}

@Composable
private fun formatMinutes(minutes: Double): String {
    val total = minutes.roundToInt()
    return when {
        total < 1 -> stringResource(R.string.reader_footer_less_than_minute)
        total < 60 -> "${total}m"
        else -> "${total / 60}h ${total % 60}m"
    }
}
