package com.bookorbit.core.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.bookorbit.core.model.AuthorSummary
import com.bookorbit.core.model.BookCard
import com.bookorbit.core.model.SeriesSummary

const val DEFAULT_PAGE_SIZE = 50

/**
 * Generic [PagingSource] over the server's 0-indexed `{ items, total, page, size }` envelope: keep
 * requesting the next page until `(page + 1) * size >= total`.
 */
class BookPagingSource(
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val loadPage: suspend (page: Int, size: Int) -> Triple<List<BookCard>, Int, Int>,
) : PagingSource<Int, BookCard>() {

    override fun getRefreshKey(state: PagingState<Int, BookCard>): Int? =
        state.anchorPosition?.let { anchor ->
            val closest = state.closestPageToPosition(anchor)
            closest?.prevKey?.plus(1) ?: closest?.nextKey?.minus(1)
        }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, BookCard> {
        val page = params.key ?: 0
        return try {
            val (items, total, size) = loadPage(page, pageSize)
            val loaded = (page + 1) * size
            LoadResult.Page(
                data = items,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (loaded < total) page + 1 else null,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}

/** Paging over `GET /authors`. */
class AuthorsPagingSource(
    private val pageSize: Int = 100,
    private val loadPage: suspend (page: Int, size: Int) -> Triple<List<AuthorSummary>, Int, Int>,
) : PagingSource<Int, AuthorSummary>() {

    override fun getRefreshKey(state: PagingState<Int, AuthorSummary>): Int? =
        state.anchorPosition?.let { anchor ->
            val closest = state.closestPageToPosition(anchor)
            closest?.prevKey?.plus(1) ?: closest?.nextKey?.minus(1)
        }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AuthorSummary> {
        val page = params.key ?: 0
        return try {
            val (items, total, size) = loadPage(page, pageSize)
            val loaded = (page + 1) * size
            LoadResult.Page(
                data = items,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (loaded < total) page + 1 else null,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}

/** Paging over `GET /series`. */
class SeriesPagingSource(
    private val pageSize: Int = 100,
    private val loadPage: suspend (page: Int, size: Int) -> Triple<List<SeriesSummary>, Int, Int>,
) : PagingSource<Int, SeriesSummary>() {

    override fun getRefreshKey(state: PagingState<Int, SeriesSummary>): Int? =
        state.anchorPosition?.let { anchor ->
            val closest = state.closestPageToPosition(anchor)
            closest?.prevKey?.plus(1) ?: closest?.nextKey?.minus(1)
        }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SeriesSummary> {
        val page = params.key ?: 0
        return try {
            val (items, total, size) = loadPage(page, pageSize)
            val loaded = (page + 1) * size
            LoadResult.Page(
                data = items,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (loaded < total) page + 1 else null,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
