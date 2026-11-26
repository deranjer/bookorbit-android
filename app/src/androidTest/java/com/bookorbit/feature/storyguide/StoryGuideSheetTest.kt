package com.bookorbit.feature.storyguide

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.bookorbit.R
import com.bookorbit.core.model.*
import com.bookorbit.core.network.ApiService
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Synthetic local HTTP only. No real server policy, account, provider or guide is modified. */
class StoryGuideSheetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun completedChapterControlsRevealedIdentitiesAndSearchWithoutProviderCalls() {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        fun anchor(s: Int) = StoryGuideAnchor(s, end = true)
        val guide = StoryGuidePayload(1, "synthetic-revision", listOf(StoryGuideSection(0, "Early chapter"), StoryGuideSection(1, "Later chapter")),
            entities = listOf(
                StoryGuideEntity("traveller", "Future identity", "character", names = listOf(StoryGuideName("Traveller", anchor(0))),
                    lines = listOf(StoryGuideLine("A known traveller", anchor(0))), joins = listOf(StoryGuideJoin("masked", "Hidden identity", anchor(1),
                        names = listOf(StoryGuideName("Masked rider", anchor(0))), lines = listOf(StoryGuideLine("A separate rider", anchor(0)))))),
                StoryGuideEntity("companion", "Companion", "character", names = listOf(StoryGuideName("Companion", anchor(0))),
                    lines = listOf(StoryGuideLine("A known companion", anchor(0)))),
            ), ties = listOf(StoryGuideTie("friends", "masked", "friend_of", "companion", "friend of", anchor(0))))
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"fileId":7,"canManage":false,"build":{"allowed":true},"textKey":"synthetic-revision"}"""))
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(json.encodeToString(StoryGuidePayload.serializer(), guide)))
            val api = Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(ApiService::class.java)
            val repository = StoryGuideRepository(api, json)
            val book = BookDetail(1, 1, "Synthetic", "READY", "2026-11-23", title = "Synthetic guide",
                files = listOf(BookFileRef(7, "epub", "PRIMARY", filename = "Synthetic.epub")))
            compose.setContent { MaterialTheme { StoryGuideSheet(book, repository) {} } }
            val noChapter = compose.activity.getString(R.string.guide_no_chapter)
            compose.waitUntil(10_000) { compose.onAllNodesWithText(noChapter).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithText("Traveller").assertCountEquals(0)
            compose.onAllNodesWithText("Future identity").assertCountEquals(0)
            compose.onNodeWithText(noChapter).performClick()
            compose.onNodeWithText("Early chapter").performClick()
            compose.onNodeWithText("A separate rider").performScrollTo().assertIsDisplayed()
            val searchLabel = compose.activity.getString(R.string.guide_search_visible)
            compose.onNodeWithText(searchLabel).performScrollTo().performTextReplacement("Masked rider")
            compose.onNodeWithText(searchLabel).performImeAction()
            compose.onNodeWithText("A separate rider").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Early chapter").performScrollTo().performClick()
            compose.onNodeWithText("Later chapter").performClick()
            compose.onAllNodesWithText("A separate rider").assertCountEquals(0)
            compose.onAllNodesWithText("Future identity").assertCountEquals(0)
            compose.onAllNodesWithText("Hidden identity").assertCountEquals(0)
            assertEquals(2, server.requestCount)
        }
    }
}
