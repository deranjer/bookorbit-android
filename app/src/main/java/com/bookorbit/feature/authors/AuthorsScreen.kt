package com.bookorbit.feature.authors

import androidx.compose.ui.res.stringResource
import com.bookorbit.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import com.bookorbit.core.model.AuthorSummary
import com.bookorbit.ui.LocalImageUrls
import com.bookorbit.ui.components.BookGrid

/** Every author; tapping one opens that author's books ([AuthorBooksScreen]) as its own route. */
@Composable
fun AuthorsScreen(
    onAuthorClick: (id: Int, name: String) -> Unit,
    vm: AuthorsViewModel = hiltViewModel(),
) {
    val authors = vm.authors.collectAsLazyPagingItems()
    AuthorList(authors = authors, onSelect = { onAuthorClick(it.id, it.name) })
}

/** One author's books, as a grid. */
@Composable
fun AuthorBooksScreen(
    authorId: Int,
    onBookClick: (Int) -> Unit,
    vm: AuthorsViewModel = hiltViewModel(),
) {
    val books = remember(authorId) { vm.authorBooks(authorId) }.collectAsLazyPagingItems()
    BookGrid(items = books, onBookClick = onBookClick, emptyText = stringResource(R.string.no_books_for_this_author))
}

@Composable
private fun AuthorList(authors: LazyPagingItems<AuthorSummary>, onSelect: (AuthorSummary) -> Unit) {
    val imageUrls = LocalImageUrls.current
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(count = authors.itemCount, key = authors.itemKey { it.id }) { index ->
            val author = authors[index] ?: return@items
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(author) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!author.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUrls.authorThumbnail(author.id),
                        contentDescription = author.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            author.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(author.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${author.bookCount} ${if (author.bookCount == 1) "book" else "books"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
        }
    }
}
