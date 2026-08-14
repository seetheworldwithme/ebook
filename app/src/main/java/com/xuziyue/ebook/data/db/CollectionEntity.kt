package com.xuziyue.ebook.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.xuziyue.ebook.model.CollectionKind

/**
 * 书架实体（Room，design.md §6.4 的 Collection / LIB-05）。
 *
 * - [id] 主键（UUID，应用层生成；系统书架「收藏」固定 [com.xuziyue.ebook.model.SYSTEM_FAVORITE_ID]）。
 * - [kind] 区分系统书架（不可删不可改名）与用户书架，存 [CollectionKind]，经 [BookTypeConverters]
 *   与 name 字符串互转（复用 books.status / HighlightColor 的同款机制）。
 * - [sortOrder] 排序权重；[createdAt] 用于 sortOrder 相同时的稳定次序。
 * - [name] 不加唯一约束——用户可建同名书架（与书名/作者一样不唯一），查重在 Repository 层做提醒而非硬约束。
 */
@Entity(
    tableName = "collections",
    indices = [Index("name")],
)
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "sortOrder") val sortOrder: Long,
    @ColumnInfo(name = "createdAt") val createdAt: Long,
    val kind: CollectionKind,
)
