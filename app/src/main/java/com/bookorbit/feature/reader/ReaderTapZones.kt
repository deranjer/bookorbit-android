package com.bookorbit.feature.reader

enum class ReaderTapAction { PREVIOUS, MENU, NEXT }

/** Shared hit zones for reflowable books and image comics; independent of Android layout direction. */
fun readerTapAction(fraction: Double, paginated: Boolean = true, rightToLeft: Boolean = false): ReaderTapAction {
    if (!paginated || !fraction.isFinite() || fraction !in 0.0..1.0) return ReaderTapAction.MENU
    val action = when {
        fraction < 0.3 -> ReaderTapAction.PREVIOUS
        fraction > 0.7 -> ReaderTapAction.NEXT
        else -> ReaderTapAction.MENU
    }
    return if (!rightToLeft) action else when (action) {
        ReaderTapAction.PREVIOUS -> ReaderTapAction.NEXT
        ReaderTapAction.NEXT -> ReaderTapAction.PREVIOUS
        ReaderTapAction.MENU -> ReaderTapAction.MENU
    }
}
