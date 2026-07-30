package com.bookorbit.feature.main

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.bookorbit.core.appinfo.AppInfoRepository
import com.bookorbit.core.model.AppInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainShellViewModel @Inject constructor(
    private val appInfoRepository: AppInfoRepository,
) : ViewModel() {
    val appInfo: StateFlow<AppInfo?> = appInfoRepository.appInfo

    init {
        viewModelScope.launch {
            ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                appInfoRepository.refresh()
            }
        }
    }
}
