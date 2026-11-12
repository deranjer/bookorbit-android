package com.bookorbit.feature.player

import com.bookorbit.core.db.BookOrbitDatabase
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Instrumentation fixtures must write through the same Room instance observed by the service. */
@EntryPoint
@InstallIn(SingletonComponent::class)
@OptIn(UnstableApi::class)
interface AutoTestDependencies {
    fun database(): BookOrbitDatabase
    fun playerManager(): PlayerManager
    fun activeBookPlayer(): ActiveBookPlayer
}
