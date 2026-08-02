package com.bookorbit.feature.cast

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * F-Droid flavor: no-op. This build excludes Google's proprietary Cast SDK entirely (see the
 * "full" flavor's CastButton for the real implementation), so there is nothing to render.
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) = Unit
