package com.bookorbit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bookorbit.R
import com.bookorbit.core.model.BookCard
import com.bookorbit.core.model.BookFiles
import com.bookorbit.ui.LocalImageUrls
import com.bookorbit.ui.theme.BookOrbitTheme

/**
 * Cover card for grids and shelves, after the web client's: the cover is the card, and the title and
 * author sit below it. Format and reading progress are laid over the art.
 */
@Composable
fun BookCard(
    book: BookCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageUrls = LocalImageUrls.current
    val tokens = BookOrbitTheme.tokens
    val primaryFormat = BookFiles.badgeFormat(book.files)
    val progress = book.readingProgress

    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
    ) {
        BookCover(
            model = if (book.hasCover) imageUrls.cover(book.id) else null,
            title = book.title ?: stringResource(R.string.card_unknown),
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
        ) {
            if (primaryFormat != null) {
                FormatBadge(primaryFormat, Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
            if (progress != null && progress > 0) {
                CoverProgress(progress, Modifier.align(Alignment.BottomCenter))
            }
        }

        Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 2.dp, end = 2.dp)) {
            Text(
                book.title ?: stringResource(R.string.card_unknown_title),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.authors.isNotEmpty()) {
                Text(
                    book.authors.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Reading progress (0-100) as a thin bar along the bottom edge of a cover. */
@Composable
fun CoverProgress(percent: Double, modifier: Modifier = Modifier) {
    val tokens = BookOrbitTheme.tokens
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(tokens.coverChip),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction = (percent / 100.0).coerceIn(0.0, 1.0).toFloat())
                .height(4.dp)
                .background(tokens.coverChipForeground),
        )
    }
}
