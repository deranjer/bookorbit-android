package com.bookorbit.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bookorbit.R
import com.bookorbit.core.network.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DeviceVerificationPanelTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun failureRemainsVisibleWithSafeCodeAndExplicitRetry() {
        var retries = 0
        val error = attestationErrorText(AttestationFailure(AttestationStage.REGISTRATION, AttestationProblem.SERVER, 403, "APP_ATTEST_INVALID"))
        compose.setContent { MaterialTheme { DeviceVerificationPanel(false, false, error, { retries++ }, {}) } }
        compose.onNodeWithText("HTTP 403", substring = true).assertIsDisplayed()
        compose.onNodeWithText("APP_ATTEST_INVALID", substring = true).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.attest_status_off)).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.attest_register)).performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithText("HTTP 403", substring = true).assertExists()
    }
}
