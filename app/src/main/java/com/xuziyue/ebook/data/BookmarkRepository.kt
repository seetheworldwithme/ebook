package com.xuziyue.ebook.data

import com.xuziyue.ebook.data.db.BookmarkDao
import com.xuziyue.ebook.data.db.BookmarkEntity
import com.xuziyue.ebook.reader.BookmarkItem
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.readium.r2.shared.publication.Locator

/**
 * 书签仓库（design.md §6.4 / READ-06 / 红线 #1：Locator 为主定位键）。
 *
 * - [observe] 回流驱动 UI 列表。
 * - [toggleBookmark] 实现 READ-06 去重口径「重复位置不生成重复书签」：按 locator 等价判定
 *   （href 相同 + progression 在 [PROGRESSION_EPS] 内），命中则删除（toggle off），否则新增。
 *   不靠 DB unique 索引——locator JSON 字符串精确相等无法覆盖"同位置微小 progression 差"。
 * - [clock] / [idGenerator] 注入便于单测固定时间与 id。
 */
class BookmarkRepository(
    private val dao: BookmarkDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {

    /** 当前书全部书签（按创建时间倒序）；损坏记录跳过。 */
    fun observe(bookId: String): Flow<List<BookmarkItem>> =
        dao.observe(bookId).map { list -> list.mapNotNull { it.toItem() } }

    /**
     * 在 [locator] 处切换书签。返回 true=新增，false=已存在同位置书签并已删除。
     *
     * 等价判定：[Locator.locations] href 相同且全书 progression 差 < [PROGRESSION_EPS]
     * （任一方 progression 为 null 时仅当双方都为 null 才视为匹配）。
     */
    suspend fun toggleBookmark(bookId: String, locator: Locator, excerpt: String?): Boolean {
        val existing = dao.forBook(bookId)
        val match = existing.firstOrNull { it matches locator }
        if (match != null) {
            dao.deleteById(match.id)
            return false
        }
        dao.upsert(
            BookmarkEntity(
                id = idGenerator(),
                bookId = bookId,
                locatorJson = PersistedLocator.from(locator).toJsonString(),
                excerpt = excerpt,
                createdAt = clock(),
            ),
        )
        return true
    }

    suspend fun delete(id: String) = dao.deleteById(id)

    suspend fun deleteAllForBook(bookId: String) = dao.deleteAllForBook(bookId)

    private fun BookmarkEntity.toItem(): BookmarkItem? {
        val locator = PersistedLocator.fromJsonString(locatorJson)?.toLocator() ?: return null
        return BookmarkItem(id = id, locator = locator, excerpt = excerpt, createdAt = createdAt)
    }

    /** locator 等价判定（READ-06 去重）。 */
    private infix fun BookmarkEntity.matches(other: Locator): Boolean {
        val self = PersistedLocator.fromJsonString(locatorJson)?.toLocator() ?: return false
        if (self.href != other.href) return false
        val sp = self.locations.totalProgression
        val op = other.locations.totalProgression
        return when {
            sp == null && op == null -> true
            sp != null && op != null -> kotlin.math.abs(sp - op) < PROGRESSION_EPS
            else -> false
        }
    }

    private companion object {
        const val PROGRESSION_EPS = 1e-3
    }
}
