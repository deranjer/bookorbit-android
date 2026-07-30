package com.bookorbit.core.appinfo

import com.bookorbit.core.model.AppInfo
import com.bookorbit.core.network.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Single source of truth for server-reported app info (version, update availability). */
@Singleton
class AppInfoRepository @Inject constructor(
    private val api: ApiService,
) {
    private val _appInfo = MutableStateFlow<AppInfo?>(null)
    val appInfo: StateFlow<AppInfo?> = _appInfo.asStateFlow()

    /** Fetches once; a failed call leaves the previously-known value in place. */
    suspend fun refresh() {
        runCatching { api.getAppInfo() }.getOrNull()?.let { _appInfo.value = it }
    }
}
