package com.bookorbit.feature.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * Devices without (or with broken) Google Play Services must never crash trying to touch the Cast
 * SDK. Every [CastContext.getSharedInstance] call site should guard with this first, and still wrap
 * the call itself in `runCatching` since `getSharedInstance` can throw even when this check passes
 * (e.g. corrupted GMS state).
 */
fun isCastAvailable(context: Context): Boolean =
    GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

fun sharedCastContextOrNull(context: Context): CastContext? {
    if (!isCastAvailable(context)) return null
    return runCatching { CastContext.getSharedInstance(context) }.getOrNull()
}
