package com.xuziyue.ebook.data.db

import androidx.room.ColumnInfo

/**
 * 书架列表查询结果 POJO（LIB-05 书架 Tab）：Collection 全字段 + 关联书籍数。
 *
 * LEFT JOIN collection_books + GROUP BY + COUNT：空书架 bookCount=0（与 LibraryItemEntity 的
 * LEFT JOIN progression 同款范式）。书架 Tab 展示「书架名 (N 本)」。
 */
data class CollectionWithCountEntity(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "sortOrder") val sortOrder: Long,
    @ColumnInfo(name = "createdAt") val createdAt: Long,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "bookCount") val bookCount: Int,
)
