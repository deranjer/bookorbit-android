package com.bookorbit.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.auth.SessionState
import com.bookorbit.core.network.ImageUrls
import com.bookorbit.feature.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Exposes top-level [SessionState] for navigation gating, the sign-out action, and [ImageUrls]. */
@HiltViewModel
class RootViewModel @Inject constructor(
    session: SessionManager,
    private val repo: AuthRepository,
    val imageUrls: ImageUrls,
) : ViewModel() {
    val state: StateFlow<SessionState> = session.state

    fun signOut() {
        viewModelScope.launch { repo.logout() }
    }
}
