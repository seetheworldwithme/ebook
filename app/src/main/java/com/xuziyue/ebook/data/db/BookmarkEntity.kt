package com.xuziyue.ebook.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 书签实体（Room，design.md §6.4 / READ-06）。
 *
 * - [bookId] 指向 [BookEntity.id]，ForeignKey CASCADE：删书连带删书签。
 * - [locatorJson] 存 [com.xuziyue.ebook.data.PersistedLocator] 的 JSON（含 schemaVersion，CLAUDE.md L90 / 红线 #1）。
 * - [excerpt] 书签位置的上下文摘录（页级，非文本选择），用于列表展示。
 * - [createdAt] 创建时间；无 updatedAt/deletedAt——书签是轻量记录，删除即物理删（READ-06 toggle）。
 *
 * 去重（READ-06「重复位置不生成重复书签」）在 [com.xuziyue.ebook.data.BookmarkRepository] 按 locator 等价判定，
 * 不靠 DB unique 索引（locator JSON 字符串精确相等无法覆盖"同位置微小 progression 差"）。
 */
@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val locatorJson: String,
    val excerpt: String?,
    val createdAt: Long,
)
