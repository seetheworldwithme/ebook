package com.xuziyue.ebook.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room schema 迁移（红线 #6：升级不丢数据，必须写 migration + 测试，绝不 fallbackToDestructive）。
 *
 * [MIGRATION_1_2]：v1（books + reading_progress）→ v2 加 bookmarks + annotations（READ-06/07）。
 * CREATE TABLE / INDEX 的 SQL 逐字取自 Room 生成的 `app/schemas/.../2.json`，保证列序 / 类型亲和 / 约束名与 Room 期望一致
 * （否则运行时抛 `Migration didn't properly handle`）。
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `bookmarks` (
                `id` TEXT NOT NULL,
                `bookId` TEXT NOT NULL,
                `locatorJson` TEXT NOT NULL,
                `excerpt` TEXT,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_bookmarks_bookId` ON `bookmarks` (`bookId`)",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `annotations` (
                `id` TEXT NOT NULL,
                `bookId` TEXT NOT NULL,
                `locatorJson` TEXT NOT NULL,
                `selectedText` TEXT NOT NULL,
                `note` TEXT,
                `color` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `deletedAt` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_annotations_bookId` ON `annotations` (`bookId`)",
        )
    }
}
