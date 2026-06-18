package com.bookorbit.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.bookorbit.core.auth.SessionManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. Hilt owns the object graph; WorkManager is configured here so download
 * workers can be constructed with injected dependencies (OkHttp, repositories). Also supplies Coil's
 * default [ImageLoader] (backed by the authed image OkHttp client) so cover/thumbnail requests carry
 * the bearer token.
 */
@HiltAndroidApp
class BookOrbitApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var imageLoader: ImageLoader

    override fun onCreate() {
        super.onCreate()
        // Load persisted server URL / token / user so the nav graph can gate on the result.
        sessionManager.bootstrap()
    }

    override fun newImageLoader(): ImageLoader = imageLoader

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
