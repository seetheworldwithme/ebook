package com.xuziyue.ebook.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.xuziyue.ebook.model.ReadingStatus

/**
 * 书籍持久化实体（Room，design.md §6.4）。
 *
 * - [id] 主键（UUID，应用层生成）。
 * - [contentHash] 唯一索引（SHA-256，导入去重依据，红线 #4）。
 * - [authors] / [status] 经 [BookTypeConverters] 与 String 互转。
 *
 * 与 domain [com.xuziyue.ebook.model.Book] 字段一一对应（映射见 [toDomain]）。
 */
@Entity(
    tableName = "books",
    indices = [Index(value = ["contentHash"], unique = true)],
)
data class BookEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "contentHash") val contentHash: String,
    val title: String,
    val authors: List<String>,
    val description: String?,
    val language: String?,
    @ColumnInfo(name = "format") val format: String,
    @ColumnInfo(name = "mediaType") val mediaType: String,
    @ColumnInfo(name = "filePath") val filePath: String,
    @ColumnInfo(name = "fileSize") val fileSize: Long,
    @ColumnInfo(name = "coverPath") val coverPath: String?,
    @ColumnInfo(name = "importedAt") val importedAt: Long,
    @ColumnInfo(name = "lastOpenedAt") val lastOpenedAt: Long?,
    @ColumnInfo(name = "status") val status: ReadingStatus,
)
