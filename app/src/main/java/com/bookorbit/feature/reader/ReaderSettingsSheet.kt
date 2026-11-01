package com.bookorbit.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bookorbit.R
import com.bookorbit.ui.components.ChoiceChip
import com.bookorbit.ui.components.GhostButton
import com.bookorbit.ui.components.SecondaryButton
import com.bookorbit.ui.components.SectionLabel
import com.bookorbit.ui.theme.BookOrbitTheme
import kotlin.math.roundToInt

/**
 * The reader's settings, laid out as the web reader's settings panel: page colour and mode, flow
 * (and, on this app only, how pages turn), text size and spacing, the font (built in, the server's, the user's), then spacing and layout
 * folded away, the footer, and where the settings are kept.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderSettingsSheet(
    settings: ReaderSettings,
    pageTurn: PageTurn,
    uploadedFonts: List<UploadedFontFamily>,
    bookCustomized: Boolean,
    sync: Boolean,
    onChange: (ReaderSettings) -> Unit,
    onPageTurn: (PageTurn) -> Unit,
    onSync: (Boolean) -> Unit,
    onSaveAsDefaults: () -> Unit,
    onResetBook: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = BookOrbitTheme.tokens
    var advanced by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = tokens.card) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(stringResource(R.string.rs_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp))

            SectionLabel(stringResource(R.string.rs_page_color))
            FlowRow(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                READER_THEMES.forEach { theme ->
                    val bg = Color(if (settings.isDark) theme.darkBg else theme.lightBg)
                    val fg = Color(if (settings.isDark) theme.darkFg else theme.lightFg)
                    val selected = settings.themeName == theme.name
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(bg)
                            .border(if (selected) 2.dp else 1.dp, if (selected) tokens.foreground else tokens.border, CircleShape)
                            .clickable(onClickLabel = stringResource(theme.label)) { onChange(settings.copy(themeName = theme.name)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                        else Text(stringResource(R.string.a), color = fg, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Segments(
                listOf(stringResource(R.string.light) to !settings.isDark, stringResource(R.string.dark) to settings.isDark),
                onSelect = { onChange(settings.copy(isDark = it == 1)) },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )

            SectionLabel(stringResource(R.string.rs_reading_flow))
            Segments(
                listOf(stringResource(R.string.paginated) to (settings.flow == "paginated"), stringResource(R.string.scrolled) to (settings.flow == "scrolled")),
                onSelect = { onChange(settings.copy(flow = if (it == 0) "paginated" else "scrolled")) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (settings.flow == "paginated") {
                SectionLabel(stringResource(R.string.rs_page_turn))
                Segments(
                    PageTurn.entries.map { stringResource(it.label) to (it == pageTurn) },
                    onSelect = { onPageTurn(PageTurn.entries[it]) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            SectionLabel(stringResource(R.string.rs_text_size))
            Stepper(
                value = "${settings.fontSize}",
                onDecrement = { onChange(settings.copy(fontSize = clampFontSize(settings.fontSize - 1))) },
                onIncrement = { onChange(settings.copy(fontSize = clampFontSize(settings.fontSize + 1))) },
            )
            LabeledSlider(
                label = stringResource(R.string.rs_line_spacing),
                value = settings.lineHeight.toFloat(),
                range = LINE_HEIGHT_RANGE.start.toFloat()..LINE_HEIGHT_RANGE.endInclusive.toFloat(),
                display = "%.1f".format(settings.lineHeight),
                onChange = { onChange(settings.copy(lineHeight = clampLineHeight(it.toDouble()))) },
            )

            SectionLabel(stringResource(R.string.rs_font))
            FontGrid(settings.fontFamily, onSelect = { onChange(settings.copy(fontFamily = it)) })
            val server = uploadedFonts.filter { it.fromServer }
            val yours = uploadedFonts.filterNot { it.fromServer }
            if (server.isNotEmpty()) UploadedFonts(stringResource(R.string.rs_font_server), server, settings.fontFamily) { onChange(settings.copy(fontFamily = it)) }
            if (yours.isNotEmpty()) UploadedFonts(stringResource(R.string.rs_font_yours), yours, settings.fontFamily) { onChange(settings.copy(fontFamily = it)) }

            SectionLabel(stringResource(R.string.rs_font_weight))
            Segments(
                listOf(300 to R.string.rs_weight_light, 400 to R.string.rs_weight_regular, 500 to R.string.rs_weight_medium, 700 to R.string.rs_weight_bold)
                    .map { (w, label) -> stringResource(label) to (settings.fontWeight == w) },
                onSelect = { onChange(settings.copy(fontWeight = listOf(300, 400, 500, 700)[it])) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SwitchRow(stringResource(R.string.rs_italic), settings.fontStyle == "italic") { onChange(settings.copy(fontStyle = if (it) "italic" else "normal")) }
            SwitchRow(stringResource(R.string.rs_justify), settings.justify) { onChange(settings.copy(justify = it)) }
            SwitchRow(stringResource(R.string.rs_hyphenation), settings.hyphenate) { onChange(settings.copy(hyphenate = it)) }

            Row(
                Modifier.fillMaxWidth().clickable { advanced = !advanced }.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.rs_advanced), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(if (advanced) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = tokens.mutedForeground)
            }
            AnimatedVisibility(advanced) {
                Column {
                    LabeledSlider(
                        label = stringResource(R.string.rs_paragraph_spacing),
                        value = settings.paragraphSpacing.toFloat(),
                        range = 0f..2f,
                        display = if (settings.paragraphSpacing == 0.0) stringResource(R.string.rs_book_default) else "%.1f em".format(settings.paragraphSpacing),
                        onChange = { onChange(settings.copy(paragraphSpacing = round(it, 10))) },
                    )
                    OptionalSlider(stringResource(R.string.rs_letter_spacing), settings.letterSpacing, 0.2f, 100) { onChange(settings.copy(letterSpacing = it)) }
                    OptionalSlider(stringResource(R.string.rs_word_spacing), settings.wordSpacing, 0.5f, 20) { onChange(settings.copy(wordSpacing = it)) }
                    OptionalSlider(stringResource(R.string.rs_text_indent), settings.textIndent, 4f, 4) { onChange(settings.copy(textIndent = it)) }
                    LabeledSlider(
                        label = stringResource(R.string.rs_page_width),
                        value = settings.maxInlineSize.toFloat(),
                        range = PAGE_WIDTH_RANGE.first.toFloat()..PAGE_WIDTH_RANGE.last.toFloat(),
                        display = "${settings.maxInlineSize}px",
                        onChange = { onChange(settings.copy(maxInlineSize = (it / 20).roundToInt() * 20)) },
                    )
                    SectionLabel(stringResource(R.string.rs_columns))
                    Segments(
                        COLUMN_COUNT_RANGE.map { "$it" to (settings.maxColumnCount == it) },
                        onSelect = { onChange(settings.copy(maxColumnCount = it + 1)) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    LabeledSlider(
                        label = stringResource(R.string.rs_column_gap),
                        value = settings.gap.toFloat(),
                        range = COLUMN_GAP_RANGE.start.toFloat()..COLUMN_GAP_RANGE.endInclusive.toFloat(),
                        display = "${(settings.gap * 100).roundToInt()}%",
                        onChange = { onChange(settings.copy(gap = round(it, 100))) },
                    )
                }
            }

            SectionLabel(stringResource(R.string.rs_footer))
            Segments(
                listOf(R.string.rs_footer_progress, R.string.rs_footer_book_time, R.string.rs_footer_chapter_time)
                    .mapIndexed { i, label -> stringResource(label) to (settings.footerDisplayMode == i) },
                onSelect = { onChange(settings.copy(footerDisplayMode = it)) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            HorizontalDivider(Modifier.padding(top = 20.dp), color = tokens.border)
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.rs_sync), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(if (sync) R.string.rs_sync_on else R.string.rs_sync_off),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.mutedForeground,
                    )
                }
                Switch(checked = sync, onCheckedChange = onSync)
            }
            if (bookCustomized) {
                Text(
                    stringResource(R.string.rs_customized),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.mutedForeground,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(stringResource(R.string.rs_save_defaults), onSaveAsDefaults, Modifier.weight(1f))
                GhostButton(stringResource(R.string.rs_reset_book), onResetBook, enabled = bookCustomized)
            }
        }
    }
}

private fun round(value: Float, steps: Int): Double = (value * steps).roundToInt().toDouble() / steps

/** A row of equal segments with one selected, the web's segmented toggles. */
@Composable
private fun Segments(items: List<Pair<String, Boolean>>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val tokens = BookOrbitTheme.tokens
    val shape = MaterialTheme.shapes.small
    Row(
        modifier.fillMaxWidth().clip(shape).background(tokens.muted).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items.forEachIndexed { index, (label, selected) ->
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(if (selected) tokens.background else Color.Transparent)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) tokens.foreground else tokens.mutedForeground, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Stepper(value: String, onDecrement: () -> Unit, onIncrement: () -> Unit) {
    val tokens = BookOrbitTheme.tokens
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(MaterialTheme.shapes.small).border(1.dp, tokens.input, MaterialTheme.shapes.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDecrement) { Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.decrease)) }
        Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = onIncrement) { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.increase)) }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp).padding(top = 10.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(display, style = MaterialTheme.typography.bodyMedium, color = BookOrbitTheme.tokens.mutedForeground)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range)
    }
}

/** A spacing that can be left to the book: the slider's far left means "the book's own" (null). */
@Composable
private fun OptionalSlider(label: String, value: Double?, max: Float, steps: Int, onChange: (Double?) -> Unit) {
    LabeledSlider(
        label = label,
        value = (value ?: 0.0).toFloat(),
        range = 0f..max,
        display = if (value == null) stringResource(R.string.rs_book_default) else "%.2f em".format(value),
        onChange = { v -> onChange(round(v, steps).takeIf { it > 0.0 }) },
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** The built-in faces as two columns of tiles, each name set in its own face where the app can draw it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FontGrid(selected: String?, onSelect: (String?) -> Unit) {
    val assets = LocalContext.current.assets
    val previews = remember(assets) {
        mapOf(
            "serif" to FontFamily(Font("reader/fonts/Literata.ttf", assets)),
            "sans-serif" to FontFamily(Font("reader/fonts/AtkinsonHyperlegibleNext.ttf", assets)),
            "monospace" to FontFamily.Monospace,
            "\"BO Bitter\", serif" to FontFamily(Font("reader/fonts/Bitter.ttf", assets)),
        )
    }
    FlowRow(
        Modifier.padding(horizontal = 16.dp),
        maxItemsInEachRow = 2,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FONT_FAMILIES.forEach { font ->
            FontTile(stringResource(font.label), selected == font.value, previews[font.value], Modifier.weight(1f)) { onSelect(font.value) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UploadedFonts(title: String, families: List<UploadedFontFamily>, selected: String?, onSelect: (String) -> Unit) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = BookOrbitTheme.tokens.mutedForeground, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 6.dp))
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        families.forEach { family -> ChoiceChip(family.name, selected == family.cssFamily, onClick = { onSelect(family.cssFamily) }) }
    }
}

@Composable
private fun FontTile(label: String, selected: Boolean, family: FontFamily?, modifier: Modifier, onClick: () -> Unit) {
    val tokens = BookOrbitTheme.tokens
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier
            .height(44.dp)
            .clip(shape)
            .background(if (selected) tokens.primary else Color.Transparent)
            .border(1.dp, if (selected) tokens.primary else tokens.border, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = family ?: MaterialTheme.typography.bodyLarge.fontFamily),
            color = if (selected) tokens.primaryForeground else tokens.foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
