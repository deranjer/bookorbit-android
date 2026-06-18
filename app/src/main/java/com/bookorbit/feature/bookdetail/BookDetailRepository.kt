package com.bookorbit.feature.bookdetail

import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookRecommendation
import com.bookorbit.core.model.CollectionBookIds
import com.bookorbit.core.model.CollectionWithMembership
import com.bookorbit.core.model.SetRatingRequest
import com.bookorbit.core.model.SetReadStatusRequest
import com.bookorbit.core.network.ApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookDetailRepository @Inject constructor(
    private val api: ApiService,
) {
    suspend fun detail(id: Int): BookDetail = api.getBookDetail(id)

    suspend fun authorBooks(id: Int): List<BookRecommendation> = api.getAuthorBooksForBook(id)

    suspend fun recommendations(id: Int): List<BookRecommendation> = api.getRecommendations(id)

    suspend fun setReadStatus(id: Int, status: String) =
        api.setReadStatus(id, SetReadStatusRequest(status))

    /** Single-book rating reuses the bulk endpoint with one id, matching the web client. */
    suspend fun setRating(id: Int, rating: Int?) =
        api.setRating(SetRatingRequest(bookIds = listOf(id), rating = rating))

    suspend fun collectionsForBook(id: Int): List<CollectionWithMembership> =
        api.getCollectionsForBook(id.toString())

    suspend fun addToCollection(collectionId: Int, bookId: Int) =
        api.addBooksToCollection(collectionId, CollectionBookIds(listOf(bookId)))

    suspend fun removeFromCollection(collectionId: Int, bookId: Int) =
        api.removeBooksFromCollection(collectionId, CollectionBookIds(listOf(bookId)))
}
