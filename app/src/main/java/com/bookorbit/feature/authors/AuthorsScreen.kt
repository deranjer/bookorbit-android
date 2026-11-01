package com.bookorbit.feature.authors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.bookorbit.R
import com.bookorbit.core.model.AuthorSummary
import com.bookorbit.feature.main.LibrarySection
import com.bookorbit.ui.LocalImageUrls
import com.bookorbit.ui.components.BookGrid
import com.bookorbit.ui.components.EmptyState
import com.bookorbit.ui.components.PageHeader
import com.bookorbit.ui.theme.BookOrbitTheme

/** Every author; tapping one opens that author's books ([AuthorBooksScreen]) as its own route. */
@Composable
fun AuthorsScreen(
    onAuthorClick: (id: Int, name: String) -> Unit,
    tabs: @Composable () -> Unit,
    vm: AuthorsViewModel = hiltViewModel(),
) {
    val authors = vm.authors.collectAsLazyPagingItems()
    AuthorsContent(authors = authors, tabs = tabs, onSelect = { onAuthorClick(it.id, it.name) })
}

/** The authors as a grid of portraits, each with a name and a book count. */
@Composable
internal fun AuthorsContent(
    authors: LazyPagingItems<AuthorSummary>,
    tabs: @Composable () -> Unit,
    onSelect: (AuthorSummary) -> Unit,
) {
    LibrarySection(
        header = { PageHeader(title = stringResource(R.string.lib_chip_authors), eyebrow = stringResource(R.string.title_library)) },
        tabs = tabs,
    ) {
        if (authors.loadState.refresh is LoadState.NotLoading && authors.itemCount == 0) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { EmptyState(Icons.Outlined.Person, stringResource(R.string.no_authors_found)) }
            return@LibrarySection
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(count = authors.itemCount, key = authors.itemKey { it.id }) { index ->
                val author = authors[index] ?: return@items
                Column(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onSelect(author) }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AuthorPortrait(author, size = 76.dp)
                    Text(
                        author.name,
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        pluralStringResource(R.plurals.lib_book_count, author.bookCount, author.bookCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = BookOrbitTheme.tokens.mutedForeground,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** An author's photo in a circle, or their initial in the serif when there is none. */
@Composable
fun AuthorPortrait(author: AuthorSummary?, size: Dp, name: String = author?.name.orEmpty()) {
    val imageUrls = LocalImageUrls.current
    val tokens = BookOrbitTheme.tokens
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(tokens.muted)
            .border(1.dp, tokens.border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase(),
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = (size.value * 0.4f).sp),
            color = tokens.mutedForeground,
        )
        if (author != null && !author.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrls.authorThumbnail(author.id),
                contentDescription = author.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** One author's books, as a grid under their name. */
@Composable
fun AuthorBooksScreen(
    authorId: Int,
    name: String,
    onBookClick: (Int) -> Unit,
    vm: AuthorsViewModel = hiltViewModel(),
) {
    val books = remember(authorId) { vm.authorBooks(authorId) }.collectAsLazyPagingItems()
    BookGrid(
        items = books,
        onBookClick = onBookClick,
        emptyText = stringResource(R.string.no_books_for_this_author),
        header = {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AuthorPortrait(null, size = 64.dp, name = name)
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(stringResource(R.string.lib_chip_authors), style = MaterialTheme.typography.bodyMedium, color = BookOrbitTheme.tokens.mutedForeground)
                        Text(name, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
    )
}
