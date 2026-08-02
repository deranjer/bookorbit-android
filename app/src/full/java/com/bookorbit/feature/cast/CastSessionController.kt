package com.bookorbit.feature.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks whether casting is available on this device and, if so, whether a Cast session is
 * currently connected - the source of the human-readable device name shown in the player UI.
 * Registered once for the process lifetime, matching this app's existing pattern of not tearing
 * down other app-singleton listeners (e.g. PersistentCookieJar).
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

    private val _state = MutableStateFlow(CastUiInfo(isAvailable = isCastAvailable(context)))
    val state = _state.asStateFlow()

    private val listener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = updateConnected(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = updateConnected(session)
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionStartFailed(session: CastSession, error: Int) = updateDisconnected()
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionEnded(session: CastSession, error: Int) = updateDisconnected()
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumeFailed(session: CastSession, error: Int) = updateDisconnected()
        override fun onSessionSuspended(session: CastSession, reason: Int) = updateDisconnected()
    }

    init {
        sharedCastContextOrNull(context)?.sessionManager?.addSessionManagerListener(listener, CastSession::class.java)
    }

    private fun updateConnected(session: CastSession) {
        _state.update { it.copy(isConnected = true, deviceName = session.castDevice?.friendlyName) }
    }

    private fun updateDisconnected() {
        _state.update { it.copy(isConnected = false, deviceName = null) }
    }
}
