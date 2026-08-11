package com.xuziyue.ebook.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * 应用主数据库（Room，design.md §6.4/6.5）。
 *
 * version=2：v1（books + reading_progress）→ v2 加 bookmarks + annotations（READ-06/07）。
 * exportSchema=true（红线 #6：schema 导出 + migration 测试）。
 * schema JSON 落 `app/schemas/.../BookDatabase/{1,2}.json`，提交进仓库供 migration 测试读取。
 *
 * 升级路径见 [MIGRATION_1_2]；DI 在 provideBookDatabase 注册，绝不 fallbackToDestructive（红线 #6）。
 */
@Database(
    entities = [
        BookEntity::class,
        ReadingProgressEntity::class,
        BookmarkEntity::class,
        AnnotationEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(BookTypeConverters::class)
abstract class BookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun annotationDao(): AnnotationDao

    companion object {
        const val DB_NAME = "ebook.db"
    }
}
