package com.bookorbit.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.bookorbit.core.network.ImageUrls

/**
 * Provides [ImageUrls] to the composable tree so cards/details can build cover URLs without
 * threading the dependency through every screen. Set once in the authenticated app root.
 */
val LocalImageUrls = staticCompositionLocalOf<ImageUrls> {
    error("LocalImageUrls not provided")
}
