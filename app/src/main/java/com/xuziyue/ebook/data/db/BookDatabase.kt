package com.xuziyue.ebook.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * 应用主数据库（Room，design.md §6.4/6.5）。
 *
 * version=1，exportSchema=true（红线 #6：schema 导出 + migration 测试）。
 * schema JSON 落 `app/schemas/.../BookDatabase/1.json`，提交进仓库供 migration 测试读取。
 */
@Database(
    entities = [BookEntity::class, ReadingProgressEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(BookTypeConverters::class)
abstract class BookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun readingProgressDao(): ReadingProgressDao

    companion object {
        const val DB_NAME = "ebook.db"
    }
}
