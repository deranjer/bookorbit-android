package com.bookorbit.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bookorbit.R
import com.bookorbit.core.model.BookCard
import com.bookorbit.core.model.BookRecommendation
import com.bookorbit.ui.LocalImageUrls
import com.bookorbit.ui.theme.BookOrbitTheme

private val ShelfCoverWidth = 124.dp

/**
 * A titled row of covers in an accent-bordered panel, the web client's home shelf: an icon tile, the
 * title and a count above a row that scrolls sideways.
 */
@Composable
fun Shelf(
    title: String,
    icon: ImageVector,
    count: Int,
    modifier: Modifier = Modifier,
    action: (@Composable RowScope.() -> Unit)? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Panel(modifier = modifier, accent = true, contentPadding = PaddingValues(vertical = 14.dp)) {
        PanelHeader(
            title = title,
            icon = icon,
            count = count,
            action = action,
            modifier = Modifier.padding(start = 16.dp, end = if (action != null) 4.dp else 16.dp, bottom = 12.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

/** A [Shelf] of book cards. */
@Composable
fun BookShelf(
    title: String,
    icon: ImageVector,
    books: List<BookCard>,
    onBookClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Shelf(title = title, icon = icon, count = books.size, modifier = modifier) {
        items(books, key = { it.id }) { book ->
            BookCard(book = book, onClick = { onBookClick(book.id) }, modifier = Modifier.width(ShelfCoverWidth))
        }
    }
}

/** A [Shelf] of the lighter recommendation shape: "More by this author", "Similar books". */
@Composable
fun RecommendationShelf(
    title: String,
    icon: ImageVector,
    books: List<BookRecommendation>,
    onBookClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageUrls = LocalImageUrls.current
    val tokens = BookOrbitTheme.tokens
    Shelf(title = title, icon = icon, count = books.size, modifier = modifier) {
        items(books, key = { it.id }) { rec ->
            Column(
                Modifier
                    .width(ShelfCoverWidth - 12.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onBookClick(rec.id) },
            ) {
                BookCover(
                    model = if (rec.hasCover) imageUrls.cover(rec.id) else null,
                    title = rec.title ?: stringResource(R.string.card_unknown),
                    titleSize = MaterialTheme.typography.titleMedium.fontSize,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                )
                Text(
                    rec.title ?: stringResource(R.string.card_unknown),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
                rec.authors.firstOrNull()?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
