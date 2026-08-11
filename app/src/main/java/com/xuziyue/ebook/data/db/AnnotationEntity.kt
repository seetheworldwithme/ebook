package com.xuziyue.ebook.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.xuziyue.ebook.model.HighlightColor

/**
 * 高亮 / 笔记批注实体（Room，design.md §6.4 / READ-07 / 红线 #9：批注先落盘再呈现）。
 *
 * - [bookId] 指向 [BookEntity.id]，ForeignKey CASCADE：删书连带删批注。
 * - [locatorJson] 存 [com.xuziyue.ebook.data.PersistedLocator] 的 JSON（含 schemaVersion，红线 #1）；必须来自文本选择（含 DOM 文本范围），页级 locator 渲染不出高亮。
 * - [selectedText] 用户选中的文字（取自 `Locator.text.highlight`），用于列表展示 / 未来导出。
 * - [note] 用户笔记，可空；[color] 高亮颜色（[HighlightColor] 枚举，经 BookTypeConverters 存 name 字符串）。
 * - [deletedAt] 软删除标记：非 null 表示已删除，observe 查询过滤；为未来回收站 / 同步保留。
 *
 * 渲染数据流：DB 先事务落盘 → [com.xuziyue.ebook.data.AnnotationRepository].observe 回流 →
 * ViewModel 派生 decorations → Fragment applyDecorations（红线 #9，不乐观更新内存）。
 */
@Entity(
    tableName = "annotations",
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
data class AnnotationEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val locatorJson: String,
    val selectedText: String,
    val note: String?,
    val color: HighlightColor,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
