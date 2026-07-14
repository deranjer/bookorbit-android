package com.bookorbit.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookorbit.core.settings.AppSettingsStore
import com.bookorbit.core.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Exposes the persisted [ThemeMode] so MainActivity can resolve it to a darkTheme boolean for BookOrbitTheme. */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    settings: AppSettingsStore,
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
}
