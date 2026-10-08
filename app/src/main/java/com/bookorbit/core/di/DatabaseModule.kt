package com.bookorbit.core.di

import android.content.Context
import androidx.room.Room
import com.bookorbit.core.db.AudioProgressDao
import com.bookorbit.core.db.BookOrbitDatabase
import com.bookorbit.core.db.DownloadDao
import com.bookorbit.core.db.AnnotationCacheDao
import com.bookorbit.core.db.MIGRATION_5_6
import com.bookorbit.core.db.MIGRATION_6_7
import com.bookorbit.core.db.PendingAnnotationOpDao
import com.bookorbit.core.db.PendingRatingDao
import com.bookorbit.core.db.PendingReadStatusDao
import com.bookorbit.core.db.PendingReadingSessionDao
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.db.ReaderProgressDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context, session: SessionManager): BookOrbitDatabase {
        // One database per server: book and file ids mean different books on different servers.
        val scope = session.dataScope
        session.activeDataScope = scope
        val name = if (scope.isEmpty()) "bookorbit.db" else "bookorbit-$scope.db"
        return Room.databaseBuilder(context, BookOrbitDatabase::class.java, name)
            .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideReaderProgressDao(db: BookOrbitDatabase): ReaderProgressDao = db.readerProgressDao()

    @Provides
    fun provideAudioProgressDao(db: BookOrbitDatabase): AudioProgressDao = db.audioProgressDao()

    @Provides
    fun provideDownloadDao(db: BookOrbitDatabase): DownloadDao = db.downloadDao()

    @Provides
    fun providePendingRatingDao(db: BookOrbitDatabase): PendingRatingDao = db.pendingRatingDao()

    @Provides
    fun providePendingAnnotationOpDao(db: BookOrbitDatabase): PendingAnnotationOpDao = db.pendingAnnotationOpDao()

    @Provides
    fun provideAnnotationCacheDao(db: BookOrbitDatabase): AnnotationCacheDao = db.annotationCacheDao()

    @Provides
    fun providePendingReadingSessionDao(db: BookOrbitDatabase): PendingReadingSessionDao = db.pendingReadingSessionDao()

    @Provides
    fun providePendingReadStatusDao(db: BookOrbitDatabase): PendingReadStatusDao = db.pendingReadStatusDao()
}
