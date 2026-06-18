package com.bookorbit.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookorbit.core.db.DownloadEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repo: DownloadsRepository,
) : ViewModel() {
    val downloads = repo.downloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<DownloadEntity>())

    fun delete(bookId: Int) {
        viewModelScope.launch { repo.delete(bookId) }
    }
}
