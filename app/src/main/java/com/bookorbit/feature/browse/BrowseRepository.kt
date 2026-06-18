package com.bookorbit.feature.browse

import com.bookorbit.core.model.AppInfo
import com.bookorbit.core.model.AuthorsPage
import com.bookorbit.core.model.BookCard
import com.bookorbit.core.model.BookQuery
import com.bookorbit.core.model.BooksPage
import com.bookorbit.core.model.Collection
import com.bookorbit.core.model.Library
import com.bookorbit.core.model.SearchResult
import com.bookorbit.core.model.SeriesBooksPage
import com.bookorbit.core.model.SeriesPage
import com.bookorbit.core.model.SmartScope
import com.bookorbit.core.network.ApiService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-side operations for the browse tabs. Thin wrapper over [ApiService] covering the
 * libraries/collections/smartScopes/authors/series/dashboard/catalog endpoints.
 */
@Singleton
class BrowseRepository @Inject constructor(
    private val api: ApiService,
) {
    suspend fun libraries(): List<Library> = api.getLibraries()

    suspend fun libraryBooks(libraryId: Int, query: BookQuery): BooksPage =
        api.getLibraryBooks(libraryId, query)

    suspend fun search(q: String, limit: Int = 20): List<SearchResult> = api.searchBooks(q, limit)

    suspend fun smartScopes(): List<SmartScope> = api.getSmartScopes()

    suspend fun smartScopeBooks(id: Int, page: Int, size: Int): BooksPage =
        api.getSmartScopeBooks(id, page, size)

    suspend fun collections(): List<Collection> = api.getCollections()

    suspend fun collectionBooks(id: Int, page: Int, size: Int): BooksPage =
        api.getCollectionBooks(id, page, size)

    suspend fun authors(page: Int, size: Int): AuthorsPage = api.getAuthors(page = page, size = size)

    suspend fun authorBooks(id: Int, page: Int, size: Int): BooksPage =
        api.getAuthorBooks(id, page, size)

    suspend fun series(page: Int, size: Int): SeriesPage = api.getSeries(page = page, size = size)

    suspend fun seriesBooks(name: String, page: Int, size: Int): SeriesBooksPage =
        api.getSeriesBooks(name, page, size)

    suspend fun scroller(type: String, limit: Int = 20, smartScopeId: Int? = null): List<BookCard> =
        api.getScroller(type, limit, smartScopeId)

    suspend fun appInfo(): AppInfo = api.getAppInfo()

    /** Catalog typeahead for the filter sheet (authors/genres/tags/languages). */
    suspend fun searchCatalog(kind: String, q: String): List<String> =
        if (q.isBlank()) emptyList()
        else api.searchCatalog(kind, q.trim()).mapNotNull { it.name }.filter { it.isNotBlank() }
}
