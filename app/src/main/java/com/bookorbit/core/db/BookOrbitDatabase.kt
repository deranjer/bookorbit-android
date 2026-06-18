package com.bookorbit.core.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * App database. Pre-launch we use destructive migration (see DatabaseModule) so schema changes
 * during the build-out don't require hand-written migrations; downloads tables are added later.
 */
@Database(
    entities = [ReaderProgressEntity::class, AudioProgressEntity::class, DownloadEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class BookOrbitDatabase : RoomDatabase() {
    abstract fun readerProgressDao(): ReaderProgressDao
    abstract fun audioProgressDao(): AudioProgressDao
    abstract fun downloadDao(): DownloadDao
}
