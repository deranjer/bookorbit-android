package com.bookorbit.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically

/**
 * One set of durations and curves for every transition, so the app moves the same way everywhere.
 * The curves are Tailwind's (`ease-in-out`, `ease-out`, `ease-in`), which the web client's
 * transitions use; the durations are its `duration-150/200/300` steps.
 */
object Motion {
    const val FAST = 150
    const val NORMAL = 200
    const val SLOW = 300

    val Standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val Decelerate = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    val Accelerate = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    fun <T> standard(durationMillis: Int = NORMAL): FiniteAnimationSpec<T> = tween(durationMillis, easing = Standard)

    /** Something appearing: it decelerates in. */
    fun <T> enter(durationMillis: Int = NORMAL): FiniteAnimationSpec<T> = tween(durationMillis, easing = Decelerate)

    /** Something leaving: it accelerates out, a little faster than it came in. */
    fun <T> exit(durationMillis: Int = FAST): FiniteAnimationSpec<T> = tween(durationMillis, easing = Accelerate)

    /** For overlays such as reader chrome and toolbars. */
    val FadeIn: EnterTransition = fadeIn(enter())
    val FadeOut: ExitTransition = fadeOut(exit())

    /**
     * Moving between screens: Material's fade-through. The old screen fades out quickly, the new
     * one fades in from 98% scale after it. It has no direction, so it reads the same going
     * forward, going back and in right-to-left layouts.
     */
    val ScreenEnter: EnterTransition =
        fadeIn(tween(NORMAL, delayMillis = FAST / 2, easing = Decelerate)) +
            scaleIn(tween(NORMAL, delayMillis = FAST / 2, easing = Decelerate), initialScale = 0.98f)
    val ScreenExit: ExitTransition = fadeOut(tween(FAST / 2 + 25, easing = Accelerate))

    /** For content that pushes the layout, such as an expanding section or an inline error. */
    val Reveal: EnterTransition = fadeIn(enter()) + expandVertically(enter())
    val Conceal: ExitTransition = fadeOut(exit()) + shrinkVertically(exit())
}
