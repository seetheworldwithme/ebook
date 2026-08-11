package com.xuziyue.ebook.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded

/**
 * 书库列表查询结果 POJO（LIB-01）：Book 全字段（@Embedded）+ reading_progress.progression。
 *
 * LEFT JOIN reading_progress，无进度的书 progression=null（未读）。
 * authors 经 [BookTypeConverters] 转换：Database 级注册的 converter 对 @Embedded 字段同样生效。
 */
data class LibraryItemEntity(
    @Embedded val book: BookEntity,
    @ColumnInfo(name = "progress") val progression: Double?,
)
