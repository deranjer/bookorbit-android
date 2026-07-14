package com.bookorbit.core.di

import android.content.Context
import androidx.room.Room
import com.bookorbit.core.db.AudioProgressDao
import com.bookorbit.core.db.BookOrbitDatabase
import com.bookorbit.core.db.DownloadDao
import com.bookorbit.core.db.PendingRatingDao
import com.bookorbit.core.db.PendingReadStatusDao
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
    fun provideDatabase(@ApplicationContext context: Context): BookOrbitDatabase =
        Room.databaseBuilder(context, BookOrbitDatabase::class.java, "bookorbit.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideReaderProgressDao(db: BookOrbitDatabase): ReaderProgressDao = db.readerProgressDao()

    @Provides
    fun provideAudioProgressDao(db: BookOrbitDatabase): AudioProgressDao = db.audioProgressDao()

    @Provides
    fun provideDownloadDao(db: BookOrbitDatabase): DownloadDao = db.downloadDao()

    @Provides
    fun providePendingRatingDao(db: BookOrbitDatabase): PendingRatingDao = db.pendingRatingDao()

    @Provides
    fun providePendingReadStatusDao(db: BookOrbitDatabase): PendingReadStatusDao = db.pendingReadStatusDao()
}
