package com.xuziyue.ebook.model

/**
 * 书籍领域模型（最小骨架）。
 *
 * 字段对齐 design.md §6.4 的 Book 实体；本步仅占位，接入 Room 时再补 @Entity / 主键 / 索引等。
 */
data class Book(
    val id: String,
    val contentHash: String,
    val title: String,
    val authors: List<String> = emptyList(),
    val description: String? = null,
    val language: String? = null,
    val format: String,
    val mediaType: String,
    val filePath: String,
    val fileSize: Long,
    val coverPath: String? = null,
    val importedAt: Long,
    val lastOpenedAt: Long? = null,
    val status: ReadingStatus = ReadingStatus.UNREAD,
)

/** 阅读状态。对齐 design.md §6.4。 */
enum class ReadingStatus { UNREAD, READING, FINISHED }
