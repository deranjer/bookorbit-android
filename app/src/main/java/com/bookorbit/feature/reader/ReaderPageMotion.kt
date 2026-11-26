package com.bookorbit.feature.reader

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import kotlin.math.floor

const val READER_PAGE_TURN_MS = 300

/** Velocity is in CSS px / dp per second, not physical pixels; mirrored layouts normalize it first. */
fun readerSnapDelta(displacement: Double, velocity: Double): Int {
    val drag = displacement.takeIf(Double::isFinite)?.coerceIn(-1.0, 1.0) ?: 0.0
    val speed = velocity.takeIf(Double::isFinite) ?: 0.0
    return floor(drag + speed / 1000.0 + 0.5).coerceIn(-1.0, 1.0).toInt()
}

fun readerPageEase(fraction: Float): Float = fraction.coerceIn(0f, 1f).let { 1f - (1f - it) * (1f - it) }

fun readerPageAnimation(animated: Boolean): AnimationSpec<Float> =
    if (animated) tween(READER_PAGE_TURN_MS, easing = Easing(::readerPageEase)) else snap()

@OptIn(ExperimentalFoundationApi::class)
private class ReaderPageFling(private val state: PagerState, private val density: Float, private val animated: Boolean) : TargetedFlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float, onRemainingDistanceUpdated: (Float) -> Unit): Float {
        val size = state.layoutInfo.pageSize + state.layoutInfo.pageSpacing
        if (size <= 0 || state.pageCount == 0) return 0f
        val origin = state.settledPage
        val displacement = state.currentPage - origin + state.currentPageOffsetFraction.toDouble()
        val target = (origin + readerSnapDelta(displacement, initialVelocity / density.toDouble())).coerceIn(0, state.pageCount - 1)
        val distance = ((target - state.currentPage) - state.currentPageOffsetFraction) * size
        var consumed = 0f
        onRemainingDistanceUpdated(distance)
        AnimationState(0f).animateTo(distance, animationSpec = readerPageAnimation(animated)) {
            val requested = value - consumed
            val moved = scrollBy(requested)
            consumed += moved
            onRemainingDistanceUpdated(distance - consumed)
            if (abs(requested - moved) > 0.5f) cancelAnimation()
        }
        onRemainingDistanceUpdated(0f)
        return 0f
    }
}

/** Native comic/PDF paging uses the same projection, one-page cap and easing as the EPUB renderer. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderPager(
    state: PagerState,
    modifier: Modifier = Modifier,
    rightToLeft: Boolean = false,
    userScrollEnabled: Boolean = true,
    animated: Boolean = true,
    content: @Composable PagerScope.(Int) -> Unit,
) {
    val density = LocalDensity.current.density
    val fling = remember(state, density, animated) { ReaderPageFling(state, density, animated) }
    HorizontalPager(state = state, modifier = modifier, reverseLayout = rightToLeft,
        userScrollEnabled = userScrollEnabled, flingBehavior = fling, beyondViewportPageCount = 1,
        key = { it }, pageContent = content)
}
