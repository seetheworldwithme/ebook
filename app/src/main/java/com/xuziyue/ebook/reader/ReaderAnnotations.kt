package com.xuziyue.ebook.reader

import com.xuziyue.ebook.model.HighlightColor
import org.readium.r2.shared.publication.Locator

/**
 * 书签 UI 项（READ-06）。Repository 由 [com.xuziyue.ebook.data.db.BookmarkEntity] 映射产出。
 *
 * [locator] 经 [com.xuziyue.ebook.data.PersistedLocator] 反序列化重建，用于"跳回原文"导航。
 * [excerpt] 列表展示用的位置上下文摘录（可空）。id 对应 DB 主键，删除 / 跳转寻址用。
 */
data class BookmarkItem(
    val id: String,
    val locator: Locator,
    val excerpt: String?,
    val createdAt: Long,
)

/**
 * 高亮 / 笔记 UI 项（READ-07）。Repository 由 [com.xuziyue.ebook.data.db.AnnotationEntity] 映射产出。
 *
 * [locator] 同上重建，既是跳转寻址又是 Decoration 渲染源（ViewModel 派生 decorations 时用 id + locator + color）。
 * [selectedText] = 选中的文字（`Locator.text.highlight`）；[note] 可空；[color] 决定高亮底色。
 */
data class AnnotationItem(
    val id: String,
    val locator: Locator,
    val selectedText: String,
    val note: String?,
    val color: HighlightColor,
    val createdAt: Long,
    val updatedAt: Long,
)
