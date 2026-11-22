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
    version = 11,
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

/** Persist explicit download plans without modifying existing content or progress. */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `planId` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `storageId` TEXT NOT NULL DEFAULT ''")
    }
}

/** Persist snapshot IDs without clearing queued positions. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `reader_progress` ADD COLUMN `pageNumber` INTEGER")
        db.execSQL("ALTER TABLE `reader_progress` ADD COLUMN `writeId` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `audio_progress` ADD COLUMN `writeId` TEXT NOT NULL DEFAULT ''")
    }
}

/** Persist explicit download plans without modifying existing content or progress. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `selectedFileIdsJson` TEXT")
    }
}

/**
 * Records which account queued each offline highlight change and reading session. Rows already queued
 * keep a null owner, so they still go out with whoever is signed in.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `pending_annotation_ops` ADD COLUMN `userId` INTEGER")
        db.execSQL("ALTER TABLE `pending_reading_sessions` ADD COLUMN `userId` INTEGER")
    }
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
