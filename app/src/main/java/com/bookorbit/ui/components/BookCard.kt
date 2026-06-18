package com.bookorbit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bookorbit.core.model.BookCard
import com.bookorbit.ui.LocalImageUrls
import com.bookorbit.ui.theme.Accent

private val FORMAT_COLORS: Map<String, Color> = mapOf(
    "epub" to Color(0xFF2F9E44),
    "mobi" to Color(0xFF1971C2),
    "azw" to Color(0xFFE8590C),
    "azw3" to Color(0xFFE67700),
    "cbz" to Color(0xFF7048E8),
    "cbr" to Color(0xFF7048E8),
    "fb2" to Color(0xFF1098AD),
    "pdf" to Color(0xFFC92A2A),
    "mp3" to Color(0xFF0CA678),
    "m4b" to Color(0xFF0CA678),
)

/** Cover card used in grids and scrollers. */
@Composable
fun BookCard(
    book: BookCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageUrls = LocalImageUrls.current
    val primaryFormat = book.files.firstOrNull()?.format?.lowercase()
    val progress = book.readingProgress

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        ) {
            if (book.hasCover) {
                AsyncImage(
                    model = imageUrls.cover(book.id),
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        book.title ?: "Unknown",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (primaryFormat != null) {
                Text(
                    primaryFormat.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(FORMAT_COLORS[primaryFormat] ?: Accent)
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        }

        if (progress != null && progress > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.outline),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = (progress / 100.0).coerceIn(0.0, 1.0).toFloat())
                        .background(Accent)
                        .padding(vertical = 1.5.dp),
                )
            }
        }

        Column(modifier = Modifier.padding(6.dp)) {
            Text(
                book.title ?: "Unknown Title",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.authors.isNotEmpty()) {
                Text(
                    book.authors.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
