package com.xuziyue.ebook.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * 应用主数据库（Room，design.md §6.4/6.5）。
 *
 * version=5：v1（books + reading_progress）→ v2 加 bookmarks + annotations（READ-06/07）
 * → v3 加 reading_sessions（DATA-04 阅读时长）→ v4 加 collections + collection_books（LIB-05 书架/标签/收藏）
 * → v5 加 import_sources（IMP-06 目录增量扫描来源映射）。
 * exportSchema=true（红线 #6：schema 导出 + migration 测试）。
 * schema JSON 落 `app/schemas/.../BookDatabase/{1,2,3,4,5}.json`，提交进仓库供 migration 测试读取。
 *
 * 升级路径见 [MIGRATION_1_2] / [MIGRATION_2_3] / [MIGRATION_3_4] / [MIGRATION_4_5]；DI 在 provideBookDatabase 注册，绝不 fallbackToDestructive（红线 #6）。
 */
@Database(
    entities = [
        BookEntity::class,
        ReadingProgressEntity::class,
        BookmarkEntity::class,
        AnnotationEntity::class,
        ReadingSessionEntity::class,
        CollectionEntity::class,
        CollectionBookEntity::class,
        ImportSourceEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(BookTypeConverters::class)
abstract class BookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun readingSessionDao(): ReadingSessionDao
    abstract fun collectionDao(): CollectionDao
    abstract fun collectionBookDao(): CollectionBookDao
    abstract fun importSourceDao(): ImportSourceDao

    companion object {
        const val DB_NAME = "ebook.db"
    }
}
