package com.bookorbit.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.bookorbit.core.settings.AppSettingsStore
import com.bookorbit.core.settings.ThemeMode
import com.bookorbit.feature.downloads.DownloadsRepository
import com.bookorbit.feature.player.AudioSettingsStore
import com.bookorbit.feature.player.DEFAULT_SPEED
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadsSummary(val count: Int = 0, val totalBytes: Long = 0)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appSettings: AppSettingsStore,
    private val downloadsRepo: DownloadsRepository,
    private val audioSettingsStore: AudioSettingsStore,
    private val imageLoader: ImageLoader,
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = appSettings.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    val wifiOnlyDownloads: StateFlow<Boolean> = appSettings.wifiOnlyDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val downloadsSummary: StateFlow<DownloadsSummary> = downloadsRepo.downloads
        .map { downloads -> DownloadsSummary(count = downloads.size, totalBytes = downloads.sumOf { it.sizeBytes }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsSummary())

    private val _defaultSpeed = MutableStateFlow(DEFAULT_SPEED)
    val defaultSpeed: StateFlow<Float> = _defaultSpeed.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch { _defaultSpeed.value = audioSettingsStore.load().speed }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { appSettings.setThemeMode(mode) }
    }

    fun setWifiOnlyDownloads(enabled: Boolean) {
        viewModelScope.launch { appSettings.setWifiOnlyDownloads(enabled) }
    }

    fun setDefaultSpeed(value: Float) {
        _defaultSpeed.value = value
        viewModelScope.launch { audioSettingsStore.saveSpeed(value) }
    }

    fun clearImageCache() {
        imageLoader.diskCache?.clear()
        imageLoader.memoryCache?.clear()
        _message.value = "Image cache cleared"
    }

    fun clearAllDownloads() {
        viewModelScope.launch {
            downloadsRepo.deleteAll()
            _message.value = "All downloads removed"
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
