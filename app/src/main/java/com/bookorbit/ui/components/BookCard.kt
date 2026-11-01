package com.bookorbit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.bookorbit.R
import com.bookorbit.core.model.BookCard
import com.bookorbit.core.model.BookFiles
import com.bookorbit.ui.LocalImageUrls
import com.bookorbit.ui.theme.BookOrbitTheme

/**
 * Cover card for grids and scrollers, after the web client's: the cover is the card, rounded with a
 * hairline edge so pale covers don't melt into the page, and the title and author sit below it.
 * Format and reading progress are laid over the art.
 */
@Composable
fun BookCard(
    book: BookCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageUrls = LocalImageUrls.current
    val tokens = BookOrbitTheme.tokens
    val shape = MaterialTheme.shapes.medium
    val primaryFormat = BookFiles.badgeFormat(book.files)
    val progress = book.readingProgress

    Column(
        modifier = modifier
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(tokens.muted)
                .border(1.dp, tokens.border, shape),
        ) {
            if (book.hasCover) {
                AsyncImage(
                    model = imageUrls.cover(book.id),
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    book.title ?: stringResource(R.string.card_unknown),
                    // The serif title on a blank cover, small enough that long words fit a grid cell.
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 17.sp,
                        lineHeight = 21.sp,
                        hyphens = Hyphens.Auto,
                        lineBreak = LineBreak.Paragraph,
                    ),
                    color = tokens.mutedForeground,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(12.dp),
                )
            }

            if (primaryFormat != null) {
                FormatBadge(primaryFormat, Modifier.align(Alignment.TopEnd).padding(6.dp))
            }

            if (progress != null && progress > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(tokens.coverChip),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = (progress / 100.0).coerceIn(0.0, 1.0).toFloat())
                            .height(4.dp)
                            .background(tokens.coverChipForeground),
                    )
                }
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
