package com.bookorbit.feature.ai

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bookorbit.R
import com.bookorbit.core.auth.SecureStorage
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.model.AuthUser
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.KeyStore
import java.util.UUID

class AiConnectionSecurityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun tokenIsEncryptedAndNeverRepopulatedIntoTheDialog() {
        val context = compose.activity
        val name = "ai-connection-test-${UUID.randomUUID()}"
        val alias = "$name-key"
        val ownerPrefs = "$name-owner"
        try {
            val storage = SecureStorage(context, name, alias, ownerPrefs)
            val session = SessionManager(storage, Json).apply { setServerUrl("https://fixture.example"); signIn("fake-bookorbit-bearer", AuthUser(1, "fixture")) }
            val owner = session.owner()!!
            val store = AiConnectionStore(storage, session)
            var show by mutableStateOf(true)
            compose.setContent { MaterialTheme { if (show) AiConnectionDialog(store, owner) { show = false } } }
            compose.onNodeWithText(context.getString(R.string.ai_route_direct)).performScrollTo().performClick()
            compose.onNodeWithText(context.getString(R.string.ai_token_replacement)).performScrollTo().performTextInput("fake-provider-private-token")
            compose.onNodeWithText(context.getString(R.string.ai_help_model)).performScrollTo().performTextInput("fixture-model")
            compose.onNodeWithText(context.getString(R.string.ai_save)).performClick()
            compose.runOnIdle { assertTrue(store.connection(owner).hasToken); assertEquals(AiRoute.DIRECT, store.connection(owner).route) }
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().commit()
            val encrypted = File(context.applicationInfo.dataDir, "shared_prefs/$name.xml").readText()
            assertFalse(encrypted.contains("fake-provider-private-token"))
            assertFalse(encrypted.contains("fixture-model"))
            compose.runOnIdle { show = true }
            compose.onNodeWithText("fake-provider-private-token").assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.ai_token_remove)).performScrollTo().performClick()
            compose.runOnIdle { assertFalse(store.connection(owner).hasToken); assertEquals(AiRoute.DIRECT, store.connection(owner).route) }
        } finally {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            context.deleteSharedPreferences(name); context.deleteSharedPreferences(ownerPrefs)
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        }
    }
}
