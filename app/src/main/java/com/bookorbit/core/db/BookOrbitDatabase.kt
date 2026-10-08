package com.bookorbit.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * App database. Add a hand-written migration (see [MIGRATION_5_6]) for every schema bump from 6 on:
 * the destructive fallback in DatabaseModule would wipe the downloads table.
 */
@Database(
    entities = [
        ReaderProgressEntity::class,
        AudioProgressEntity::class,
        DownloadEntity::class,
        PendingRatingEntity::class,
        PendingReadStatusEntity::class,
        PendingReadingSessionEntity::class,
        PendingAnnotationOpEntity::class,
        AnnotationCacheEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class BookOrbitDatabase : RoomDatabase() {
    abstract fun readerProgressDao(): ReaderProgressDao
    abstract fun audioProgressDao(): AudioProgressDao
    abstract fun downloadDao(): DownloadDao
    abstract fun pendingRatingDao(): PendingRatingDao
    abstract fun pendingReadStatusDao(): PendingReadStatusDao
    abstract fun pendingReadingSessionDao(): PendingReadingSessionDao
    abstract fun pendingAnnotationOpDao(): PendingAnnotationOpDao
    abstract fun annotationCacheDao(): AnnotationCacheDao
}

/** Adds the offline highlight queue and the per-book highlight cache; existing tables are untouched. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pending_annotation_ops` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` INTEGER NOT NULL, " +
                "`kind` TEXT NOT NULL, `annotationId` INTEGER NOT NULL, `cfi` TEXT, `text` TEXT, " +
                "`color` TEXT, `note` TEXT, `noteSet` INTEGER NOT NULL, `chapterTitle` TEXT, " +
                "`bookFileId` INTEGER, `createdAt` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `annotation_cache` (" +
                "`bookId` INTEGER NOT NULL, `json` TEXT NOT NULL, PRIMARY KEY(`bookId`))",
        )
    }
}

/** Adds the offline queue for reading/listening sessions without touching existing tables. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pending_reading_sessions` (" +
                "`sessionId` TEXT NOT NULL, `fileId` INTEGER NOT NULL, `sessionType` TEXT NOT NULL, " +
                "`startedAt` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL, `durationSeconds` INTEGER NOT NULL, " +
                "`progressDelta` REAL, `endProgress` REAL, PRIMARY KEY(`sessionId`))",
        )
    }
}
