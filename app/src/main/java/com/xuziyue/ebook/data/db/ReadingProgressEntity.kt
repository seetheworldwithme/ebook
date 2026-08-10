package com.xuziyue.ebook.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * 阅读进度实体（Room，design.md §6.4 / 红线 #1：Locator 为主进度数据）。
 *
 * - 与 [BookEntity] 1:1，[bookId] 即主键（DB 层强制唯一）。
 * - ForeignKey CASCADE：删书时进度随之删除，不留孤儿。
 * - [locatorJson] 存 [com.xuziyue.ebook.data.PersistedLocator] 的 JSON（含 schemaVersion，CLAUDE.md L90）。
 * - [progression] 冗余列：书库进度条一次查询拿全，不必反序列化 Locator。
 * - [deviceId] 同步预留，MVP 存 null。
 */
@Entity(
    tableName = "reading_progress",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ReadingProgressEntity(
    @PrimaryKey val bookId: String,
    val locatorJson: String,
    val progression: Double?,
    val updatedAt: Long,
    val deviceId: String?,
)
