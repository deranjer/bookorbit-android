package com.bookorbit.feature.main

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.bookorbit.core.appinfo.AppInfoRepository
import com.bookorbit.core.model.AppInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import com.bookorbit.core.settings.AppSettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainShellViewModel @Inject constructor(
    private val appInfoRepository: AppInfoRepository,
    appSettings: AppSettingsStore,
) : ViewModel() {
    /** The user's bottom-bar items; null for the brief moment before the setting has loaded. */
    val navItems: StateFlow<List<NavItem>?> = appSettings.navItems
        .map { NavConfig.parse(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val appInfo: StateFlow<AppInfo?> = appInfoRepository.appInfo

    init {
        viewModelScope.launch {
            ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                appInfoRepository.refresh()
            }
        }
    }
}
