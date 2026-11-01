package com.bookorbit.ui.screenshot

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.bookorbit.core.model.BookCard
import com.bookorbit.core.model.BookFileRef
import com.bookorbit.core.network.ImageUrls
import com.bookorbit.feature.bookdetail.READ_STATUS_META
import com.bookorbit.feature.bookdrop.BookDockStatusBadge
import com.bookorbit.ui.LocalImageUrls
import com.bookorbit.ui.components.BookCard
import com.bookorbit.ui.components.SectionLabel
import com.bookorbit.ui.components.SectionHeader
import com.bookorbit.ui.components.SectionAction
import com.bookorbit.ui.components.ErrorState
import com.bookorbit.ui.components.EmptyState
import androidx.compose.material.icons.outlined.EditNote
import com.bookorbit.ui.components.FormatBadge
import com.bookorbit.ui.components.Pill
import com.bookorbit.ui.components.StarRating
import com.bookorbit.ui.theme.BookOrbitTheme
import com.github.takahirom.roborazzi.captureRoboImage
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The design system on one page: colour tokens, the type scale, Material components on the new
 * theme, and the component kit. Recorded at each [ScreenshotDevice] in light and dark.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevice.SDK], application = Application::class)
class ThemeCatalogScreenshotTest(private val device: ScreenshotDevice, private val dark: Boolean) {

    @Test
    fun tokens() = capture("theme-tokens") { TokensPage() }

    @Test
    fun components() = capture("theme-components") { ComponentsPage() }

    @Test
    fun states() = capture("theme-states") { StatesPage() }

    private fun capture(name: String, content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers(device.qualifiers(dark))
        captureRoboImage(screenshotPath(name, device, dark)) {
            BookOrbitTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalImageUrls provides mockk<ImageUrls>(relaxed = true)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) { content() }
                }
            }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} dark={1}")
        fun params() = ScreenshotDevice.entries.flatMap { d -> listOf(arrayOf<Any>(d, false), arrayOf<Any>(d, true)) }
    }
}

/** Colour tokens, the type scale and Material controls on the theme. */
@Composable
private fun TokensPage() {
    val tokens = BookOrbitTheme.tokens
    Text("Library", style = MaterialTheme.typography.headlineSmall)

    Section("Surfaces") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "bg" to tokens.background, "card" to tokens.card, "s2" to tokens.surface2,
                "s3" to tokens.surface3, "s4" to tokens.surface4, "muted" to tokens.muted,
                "tint" to tokens.accentTint, "border" to tokens.border,
            ).forEach { (name, color) -> Swatch(name, color) }
        }
    }

    Section("Ink") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "fg" to tokens.foreground, "muted" to tokens.mutedForeground, "primary" to tokens.primary,
                "success" to tokens.success, "warning" to tokens.warning, "info" to tokens.info,
                "danger" to tokens.destructive, "star" to tokens.starHighlight,
            ).forEach { (name, color) -> Swatch(name, color) }
        }
    }

    Section("Type") {
        TypeSample("Display", MaterialTheme.typography.displaySmall)
        TypeSample("Headline", MaterialTheme.typography.headlineSmall)
        TypeSample("Page title", MaterialTheme.typography.titleLarge)
        TypeSample("Title medium", MaterialTheme.typography.titleMedium)
        TypeSample("Body large: It is a truth universally acknowledged", MaterialTheme.typography.bodyLarge)
        TypeSample("Body: that a single man in possession of a good fortune", MaterialTheme.typography.bodyMedium)
        TypeSample("Caption · 12 books · 3h 20m", MaterialTheme.typography.bodySmall, tokens.mutedForeground)
        TypeSample("LABEL", MaterialTheme.typography.labelSmall, tokens.mutedForeground)
    }

    Section("Controls") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {}) { Text("Read") }
            OutlinedButton(onClick = {}) { Text("Download") }
            TextButton(onClick = {}) { Text("Cancel") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = true, onClick = {}, label = { Text("Books") })
            FilterChip(selected = false, onClick = {}, label = { Text("Series") })
            AssistChip(onClick = {}, label = { Text("Fantasy") })
            Switch(checked = true, onCheckedChange = {})
        }
        OutlinedTextField(
            value = "",
            onValueChange = {},
            placeholder = { Text("Search your library") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        LinearProgressIndicator(progress = { 0.42f }, modifier = Modifier.fillMaxWidth())
    }
}

/** The component kit. */
@Composable
private fun ComponentsPage() {
    val tokens = BookOrbitTheme.tokens
    Section("Pills") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("epub", "pdf", "cbz", "m4b", "mobi", "fb2").forEach { Pill(it.uppercase(), tokens.formatColor(it)) }
            listOf("pending", "extracting", "fetching", "ready", "error").forEach { BookDockStatusBadge(it) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            READ_STATUS_META.forEach { meta ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(meta.icon, contentDescription = null, tint = meta.color, modifier = Modifier.size(18.dp))
                    Text(meta.value, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        StarRating(value = 3.5, size = 22.dp)
    }

    Section("Covers") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SampleBooks.forEach { book -> BookCard(book, onClick = {}, modifier = Modifier.width(112.dp)) }
        }
        Box(Modifier.background(Color(0xFFF4E9C8)).padding(8.dp)) { FormatBadge("epub") }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Continue reading", style = MaterialTheme.typography.titleMedium)
            Text("Pride and Prejudice · 42%", style = MaterialTheme.typography.bodyMedium, color = tokens.mutedForeground)
        }
    }
}

/** Section headers and the empty and error states. */
@Composable
private fun StatesPage() {
    Column {
        SectionHeader("Continue reading", action = { SectionAction("See all", onClick = {}) })
        SectionHeader("Recently added")
        SectionLabel("Appearance")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.weight(1f)) {
            EmptyState(Icons.Outlined.EditNote, "No highlights yet", message = "Highlight a passage while reading and it shows up here.")
        }
        Box(Modifier.weight(1f)) {
            ErrorState("Couldn't load your libraries", onRetry = {}, message = "Check your connection; downloaded books still open.")
        }
    }
}

private val SampleBooks = listOf(
    BookCard(id = 1, title = "Pride and Prejudice", authors = listOf("Jane Austen"), addedAt = "", readingProgress = 42.0,
        files = listOf(BookFileRef(id = 1, format = "epub", role = "primary"))),
    BookCard(id = 2, title = "The Count of Monte Cristo", authors = listOf("Alexandre Dumas"), addedAt = "",
        files = listOf(BookFileRef(id = 2, format = "m4b", role = "primary"))),
    BookCard(id = 3, title = "Frankenstein", authors = listOf("Mary Shelley"), addedAt = "",
        files = listOf(BookFileRef(id = 3, format = "pdf", role = "primary"))),
)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = BookOrbitTheme.tokens.mutedForeground)
        content()
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    Column {
        Box(
            Modifier
                .size(48.dp)
                .background(color, MaterialTheme.shapes.small)
                .border(1.dp, BookOrbitTheme.tokens.border, MaterialTheme.shapes.small),
        )
        Text(name, style = MaterialTheme.typography.labelSmall, color = BookOrbitTheme.tokens.mutedForeground)
    }
}

@Composable
private fun TypeSample(text: String, style: TextStyle, color: Color = Color.Unspecified) {
    Text(text, style = style, color = color, maxLines = 1)
}
