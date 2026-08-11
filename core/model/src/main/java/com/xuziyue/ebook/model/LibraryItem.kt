package com.xuziyue.ebook.model

/**
 * 书库列表项（LIB-01）：领域 [Book] + 派生阅读进度。
 *
 * - [progression] 来自 reading_progress 冗余列（0.0..1.0 全书进度，红线 #1 派生展示），null 表示未读 / 无进度。
 * - 与 [Book] 分离：[Book] 是纯领域实体不带查询派生字段，[LibraryItem] 是书库展示模型。
 */
data class LibraryItem(
    val book: Book,
    val progression: Double?,
)
