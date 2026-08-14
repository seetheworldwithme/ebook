package com.xuziyue.ebook.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 书架-书籍 关联实体（Room，design.md §6.4 的 CollectionBook / LIB-05）。
 *
 * 联合主键 (collectionId, bookId)——一本书可属于多个书架、一个书架含多本书。
 * 双向 ForeignKey CASCADE：
 * - 删**书架** → 连带删该书架所有关系（**不删书籍**，满足 LIB-05「删除书架不删除书籍」）。
 * - 删**书** → 连带删该书所有书架归属（不留孤儿关系，红线 #4 精神）。
 *
 * [addedAt] 用于书架内按加入时间排序（可选，首版按书自身最近阅读序）。
 */
@Entity(
    tableName = "collection_books",
    primaryKeys = ["collectionId", "bookId"],
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("collectionId"), Index("bookId")],
)
data class CollectionBookEntity(
    @ColumnInfo(name = "collectionId") val collectionId: String,
    @ColumnInfo(name = "bookId") val bookId: String,
    @ColumnInfo(name = "addedAt") val addedAt: Long,
)
