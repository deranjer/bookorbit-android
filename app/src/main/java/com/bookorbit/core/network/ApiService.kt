package com.bookorbit.core.network

import com.bookorbit.core.model.AppInfo
import com.bookorbit.core.model.AudioProgress
import com.bookorbit.core.model.AuthUser
import com.bookorbit.core.model.AuthorsPage
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookQuery
import com.bookorbit.core.model.BookRecommendation
import com.bookorbit.core.model.BooksPage
import com.bookorbit.core.model.Collection
import com.bookorbit.core.model.CollectionBookIds
import com.bookorbit.core.model.CollectionWithMembership
import com.bookorbit.core.model.FileProgress
import com.bookorbit.core.model.Library
import com.bookorbit.core.model.LoginRequest
import com.bookorbit.core.model.LoginResponse
import com.bookorbit.core.model.NamedResult
import com.bookorbit.core.model.OidcCallbackRequest
import com.bookorbit.core.model.OidcCallbackResponse
import com.bookorbit.core.model.OidcProviderPublic
import com.bookorbit.core.model.OidcStateResponse
import com.bookorbit.core.model.SaveAudioProgress
import com.bookorbit.core.model.SaveFileProgress
import com.bookorbit.core.model.SearchResult
import com.bookorbit.core.model.SeriesBooksPage
import com.bookorbit.core.model.SeriesPage
import com.bookorbit.core.model.SetRatingRequest
import com.bookorbit.core.model.SetReadStatusRequest
import com.bookorbit.core.model.SmartScope
import com.bookorbit.core.model.UserBookStatus
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Retrofit surface for the BookOrbit server. Paths are relative and DO NOT carry the `/api/v1`
 * prefix or a host — [BaseUrlInterceptor] prepends the live server URL + `api/v1` at request time.
 * Endpoints mirror the server's `/api/v1` REST surface one-to-one.
 */
interface ApiService {

    // --- Auth ---
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("auth/logout")
    suspend fun logout()

    @GET("auth/me")
    suspend fun getMe(): AuthUser

    @GET("auth/setup-status")
    suspend fun getSetupStatus(): com.bookorbit.core.model.SetupStatus

    @GET("app-settings/oidc/providers/public")
    suspend fun getPublicOidcProviders(): List<OidcProviderPublic>

    @POST("auth/oidc/{slug}/state")
    suspend fun generateOidcState(@Path("slug") slug: String): OidcStateResponse

    @POST("auth/oidc/callback")
    suspend fun oidcCallback(@Body body: OidcCallbackRequest): OidcCallbackResponse

    // --- Books ---
    @GET("books/search")
    suspend fun searchBooks(
        @Query("q") q: String,
        @Query("limit") limit: Int = 20,
    ): List<SearchResult>

    @POST("books/query")
    suspend fun queryBooks(@Body query: BookQuery): BooksPage

    @GET("books/{id}")
    suspend fun getBookDetail(@Path("id") id: Int): BookDetail

    @PATCH("books/{id}/status")
    suspend fun setReadStatus(
        @Path("id") id: Int,
        @Body body: SetReadStatusRequest,
    ): UserBookStatus

    @POST("books/bulk-set-rating")
    suspend fun setRating(@Body body: SetRatingRequest)

    @GET("books/{id}/recommendations")
    suspend fun getRecommendations(@Path("id") id: Int): List<BookRecommendation>

    @GET("books/{id}/author-books")
    suspend fun getAuthorBooksForBook(@Path("id") id: Int): List<BookRecommendation>

    @GET("books/{id}/series-books")
    suspend fun getSeriesBooksForBook(@Path("id") id: Int): List<BookRecommendation>

    // --- Files / progress ---
    @GET("books/files/{fileId}/progress")
    suspend fun getFileProgress(@Path("fileId") fileId: Int): FileProgress?

    @POST("books/files/{fileId}/progress")
    suspend fun saveFileProgress(
        @Path("fileId") fileId: Int,
        @Body body: SaveFileProgress,
    )

    /** Raw file bytes (epub/audio/etc). Streamed so large files don't buffer in memory. */
    @Streaming
    @GET("books/files/{fileId}/serve")
    suspend fun serveFile(@Path("fileId") fileId: Int): ResponseBody

    /** Cover image bytes, for caching a downloaded book's cover offline. */
    @Streaming
    @GET("books/{id}/cover")
    suspend fun serveCover(@Path("id") id: Int): ResponseBody

    @GET("books/{id}/audio-progress")
    suspend fun getAudioProgress(@Path("id") id: Int): AudioProgress?

    @PATCH("books/{id}/audio-progress")
    suspend fun saveAudioProgress(
        @Path("id") id: Int,
        @Body body: SaveAudioProgress,
    )

    // --- Libraries ---
    @GET("libraries")
    suspend fun getLibraries(): List<Library>

    @POST("libraries/{id}/books")
    suspend fun getLibraryBooks(
        @Path("id") id: Int,
        @Body query: BookQuery,
    ): BooksPage

    // --- Collections ---
    @GET("collections")
    suspend fun getCollections(): List<Collection>

    @GET("collections")
    suspend fun getCollectionsForBook(@Query("bookIds") bookIds: String): List<CollectionWithMembership>

    @GET("collections/{id}/books")
    suspend fun getCollectionBooks(
        @Path("id") id: Int,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50,
        @Query("q") q: String? = null,
    ): BooksPage

    @POST("collections/{id}/books")
    suspend fun addBooksToCollection(
        @Path("id") id: Int,
        @Body body: CollectionBookIds,
    )

    @HTTP(method = "DELETE", path = "collections/{id}/books", hasBody = true)
    suspend fun removeBooksFromCollection(
        @Path("id") id: Int,
        @Body body: CollectionBookIds,
    )

    // --- Smart scopes ---
    @GET("smart-scopes")
    suspend fun getSmartScopes(): List<SmartScope>

    @GET("smart-scopes/{id}/books")
    suspend fun getSmartScopeBooks(
        @Path("id") id: Int,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50,
        @Query("q") q: String? = null,
    ): BooksPage

    // --- Authors ---
    @GET("authors")
    suspend fun getAuthors(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 100,
        @Query("sort") sort: String = "name",
        @Query("order") order: String = "asc",
        @Query("q") q: String? = null,
    ): AuthorsPage

    @GET("authors/{id}/books")
    suspend fun getAuthorBooks(
        @Path("id") id: Int,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 100,
    ): BooksPage

    // --- Series ---
    @GET("series")
    suspend fun getSeries(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 100,
        @Query("sort") sort: String = "name",
        @Query("order") order: String = "asc",
        @Query("q") q: String? = null,
    ): SeriesPage

    @GET("series/{name}/books")
    suspend fun getSeriesBooks(
        @Path("name") name: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 100,
    ): SeriesBooksPage

    // --- Catalog (filter typeaheads) ---
    @GET("metadata/{kind}")
    suspend fun searchCatalog(
        @Path("kind") kind: String,
        @Query("q") q: String,
    ): List<NamedResult>

    // --- Dashboard ---
    @GET("dashboard/scrollers/{type}")
    suspend fun getScroller(
        @Path("type") type: String,
        @Query("limit") limit: Int = 20,
        @Query("smartScopeId") smartScopeId: Int? = null,
    ): List<com.bookorbit.core.model.BookCard>

    // --- App info ---
    @GET("app-info")
    suspend fun getAppInfo(): AppInfo
}
