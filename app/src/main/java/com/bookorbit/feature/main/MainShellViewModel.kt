package com.bookorbit.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookorbit.feature.browse.BrowseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainShellViewModel @Inject constructor(
    private val repo: BrowseRepository,
) : ViewModel() {
    private val _serverVersion = MutableStateFlow<String?>(null)
    val serverVersion = _serverVersion.asStateFlow()

    init {
        viewModelScope.launch {
            _serverVersion.value = runCatching { repo.appInfo().version }.getOrNull()
        }
    }
}
