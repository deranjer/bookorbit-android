package com.bookorbit.core.di

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.feature.player.DEFAULT_SKIP_BACK
import com.bookorbit.feature.player.DEFAULT_SKIP_FORWARD
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Media3 wiring. Remote audio streams over an HTTP data source that injects the current bearer
 * token (read per request so it tracks token refresh); local downloads play via the file data
 * source. ExoPlayer is built per service instance (not a singleton — it owns a Looper).
 */
@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @UnstableApi
    @Provides
    @Singleton
    fun provideMediaSourceFactory(
        @ApplicationContext context: Context,
        session: SessionManager,
    ): MediaSource.Factory {
        // Per-request auth header so streaming survives token refresh.
        val httpFactory = DataSource.Factory {
            val token = session.accessToken
            DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .apply {
                    if (!token.isNullOrBlank()) {
                        setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
                    }
                }
                .createDataSource()
        }
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        return DefaultMediaSourceFactory(dataSourceFactory)
    }

    @UnstableApi
    @Provides
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        mediaSourceFactory: MediaSource.Factory,
    ): ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        // Android Auto's standard rewind/fast-forward buttons use these increments.
        .setSeekBackIncrementMs(DEFAULT_SKIP_BACK * 1000L)
        .setSeekForwardIncrementMs(DEFAULT_SKIP_FORWARD * 1000L)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
}
