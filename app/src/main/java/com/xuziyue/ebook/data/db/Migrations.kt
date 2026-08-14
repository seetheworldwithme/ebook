package com.xuziyue.ebook.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room schema 迁移（红线 #6：升级不丢数据，必须写 migration + 测试，绝不 fallbackToDestructive）。
 *
 * [MIGRATION_1_2]：v1（books + reading_progress）→ v2 加 bookmarks + annotations（READ-06/07）。
 * [MIGRATION_2_3]：v2 → v3 加 reading_sessions（DATA-04 阅读时长）。
 * [MIGRATION_3_4]：v3 → v4 加 collections + collection_books（LIB-05 书架/标签/收藏）+ 插入系统书架「收藏」。
 * CREATE TABLE / INDEX 的 SQL 逐字取自 Room 生成的 `app/schemas/.../{2,3,4}.json`，保证列序 / 类型亲和 / 约束名与 Room 期望一致
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

/**
 * v2 → v3：加 reading_sessions 表（DATA-04 阅读时长）。
 *
 * CREATE TABLE / INDEX 的 SQL 逐字取自 Room 生成的 `app/schemas/.../3.json`。
 * 若 index 名与生成的不一致，运行时 `runMigrationsAndValidate` 会报错——以 3.json 为准。
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reading_sessions` (
                `id` TEXT NOT NULL,
                `bookId` TEXT NOT NULL,
                `startedAt` INTEGER NOT NULL,
                `endedAt` INTEGER NOT NULL,
                `activeSeconds` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_reading_sessions_bookId` ON `reading_sessions` (`bookId`)",
        )
    }
}

/**
 * v3 → v4：加 collections + collection_books 两表（LIB-05 书架/标签/收藏）+ 插入系统书架「收藏」。
 *
 * CREATE TABLE / INDEX 的 SQL 逐字取自 Room 生成的 `app/schemas/.../4.json`。
 * 系统书架「收藏」在迁移末尾插入（固定 id [SYSTEM_FAVORITE_ID]、sortOrder=Long.MIN_VALUE 排最前），
 * 保证老用户升级后立即可用——不在首次启动时 lazy 插入，避免与首屏查询竞态。
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `collections` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `sortOrder` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `kind` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_collections_name` ON `collections` (`name`)",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `collection_books` (
                `collectionId` TEXT NOT NULL,
                `bookId` TEXT NOT NULL,
                `addedAt` INTEGER NOT NULL,
                PRIMARY KEY(`collectionId`, `bookId`),
                FOREIGN KEY(`collectionId`) REFERENCES `collections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_collection_books_collectionId` ON `collection_books` (`collectionId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_collection_books_bookId` ON `collection_books` (`bookId`)",
        )
        // 系统书架「收藏」：固定 id，排最前，不可删改（kind=SYSTEM_FAVORITE）。
        db.execSQL(
            """
            INSERT INTO collections(id, name, sortOrder, createdAt, kind)
            VALUES('system-favorite', '收藏', ${Long.MIN_VALUE}, 0, 'SYSTEM_FAVORITE')
            """.trimIndent(),
        )
    }
}
