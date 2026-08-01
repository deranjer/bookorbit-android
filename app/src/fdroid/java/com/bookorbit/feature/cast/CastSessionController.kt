package com.bookorbit.feature.cast

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F-Droid flavor: no-op. This build excludes Google's proprietary Cast SDK entirely (see the
 * "full" flavor's CastSessionController for the real implementation), so casting is simply never
 * available -- [state] never changes from its default.
 */
@Singleton
class CastSessionController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class CastUiInfo(
        val isAvailable: Boolean = false,
        val isConnected: Boolean = false,
        val deviceName: String? = null,
    )

    private val _state = MutableStateFlow(CastUiInfo())
    val state = _state.asStateFlow()
}
