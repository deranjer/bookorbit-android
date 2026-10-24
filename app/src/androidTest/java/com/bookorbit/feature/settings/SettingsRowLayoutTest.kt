package com.bookorbit.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Long supporting text must wrap without clipping or overlapping its trailing switch. */
@RunWith(AndroidJUnit4::class)
class SettingsRowLayoutTest {
    @Test fun normalText() = checkLayout(1f, LayoutDirection.Ltr)
    @Test fun largeText() = checkLayout(2f, LayoutDirection.Ltr)
    @Test fun largeTextRightToLeft() = checkLayout(2f, LayoutDirection.Rtl)

    private fun checkLayout(fontScale: Float, direction: LayoutDirection) {
        val title = "Full-screen reading"
        val subtitle = "Hide the status and navigation bars in ebooks, PDFs, and comics. Swipe from the screen edge to reveal them."
        val rowBounds = AtomicReference<Rect>()
        val switchBounds = AtomicReference<Rect>()
        val positioned = CountDownLatch(2)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    val density = LocalDensity.current.density
                    CompositionLocalProvider(
                        LocalDensity provides Density(density, fontScale),
                        LocalLayoutDirection provides direction,
                    ) {
                        MaterialTheme {
                            Box(Modifier.width(280.dp).onGloballyPositioned {
                                if (rowBounds.getAndSet(it.boundsInRoot()) == null) positioned.countDown()
                            }) {
                                SettingsRow(title, subtitle) {
                                    Switch(checked = true, onCheckedChange = {}, modifier = Modifier.onGloballyPositioned {
                                        if (switchBounds.getAndSet(it.boundsInRoot()) == null) positioned.countDown()
                                    })
                                }
                            }
                        }
                    }
                }
            }
            assertTrue("The settings row must be laid out", positioned.await(10, TimeUnit.SECONDS))
            val bounds = rowBounds.get()
            val toggle = switchBounds.get()
            assertTrue("Switch must fit horizontally inside the row: $toggle in $bounds", toggle.left >= bounds.left && toggle.right <= bounds.right)
            assertTrue("Switch must fit vertically inside the row", toggle.top >= bounds.top && toggle.bottom <= bounds.bottom)
        }
    }
}
