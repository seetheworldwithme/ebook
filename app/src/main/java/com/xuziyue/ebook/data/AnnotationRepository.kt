package com.xuziyue.ebook.data

import com.xuziyue.ebook.data.db.AnnotationDao
import com.xuziyue.ebook.data.db.AnnotationEntity
import com.xuziyue.ebook.model.HighlightColor
import com.xuziyue.ebook.reader.AnnotationItem
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.readium.r2.shared.publication.Locator

/**
 * 高亮 / 笔记仓库（design.md §6.4 / READ-07 / 红线 #9：批注先落盘再呈现）。
 *
 * - [add] 先 Room 事务落盘（selectedText 取自 `Locator.text.highlight`），再由 [observe] 回流驱动渲染；
 *   不让内存态跑在 DB 前面。
 * - [observe] 仅活跃批注（DAO 已过滤 deletedAt IS NULL），损坏记录跳过。
 * - [softDelete] / [softDeleteAllForBook] 置 deletedAt，回流自动清 UI；表内保留供未来回收站 / 同步。
 * - [clock] / [idGenerator] 注入便于单测固定时间与 id。
 */
class AnnotationRepository(
    private val dao: AnnotationDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {

    /** 当前书活跃批注（按创建时间倒序）；损坏记录跳过。 */
    fun observe(bookId: String): Flow<List<AnnotationItem>> =
        dao.observe(bookId).map { list -> list.mapNotNull { it.toItem() } }

    /**
     * 新增高亮 / 笔记（红线 #9：先落盘）。[locator] 必须来自文本选择（含 DOM 文本范围）。
     * [selectedText] 取自 `locator.text.highlight`（Readium Selection.locator 携带）。
     * @return 新建批注 id。
     */
    suspend fun add(
        bookId: String,
        locator: Locator,
        color: HighlightColor = HighlightColor.Default,
    ): String {
        val id = idGenerator()
        val now = clock()
        dao.upsert(
            AnnotationEntity(
                id = id,
                bookId = bookId,
                locatorJson = PersistedLocator.from(locator).toJsonString(),
                selectedText = locator.text.highlight ?: "",
                note = null,
                color = color,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            ),
        )
        return id
    }

    suspend fun updateNote(id: String, note: String?) = dao.updateNote(id, note, clock())

    suspend fun softDelete(id: String) = dao.softDelete(id, clock())

    suspend fun softDeleteAllForBook(bookId: String) = dao.softDeleteAllForBook(bookId, clock())

    private fun AnnotationEntity.toItem(): AnnotationItem? {
        val locator = PersistedLocator.fromJsonString(locatorJson)?.toLocator() ?: return null
        return AnnotationItem(
            id = id,
            locator = locator,
            selectedText = selectedText,
            note = note,
            color = color,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}
