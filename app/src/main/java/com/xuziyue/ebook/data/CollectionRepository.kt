package com.xuziyue.ebook.data

import com.xuziyue.ebook.data.db.BookDao
import com.xuziyue.ebook.data.db.CollectionBookDao
import com.xuziyue.ebook.data.db.CollectionBookEntity
import com.xuziyue.ebook.data.db.CollectionDao
import com.xuziyue.ebook.data.db.CollectionEntity
import com.xuziyue.ebook.data.db.CollectionWithCountEntity
import com.xuziyue.ebook.data.db.toDomain
import com.xuziyue.ebook.model.Collection
import com.xuziyue.ebook.model.CollectionKind
import com.xuziyue.ebook.model.LibraryItem
import com.xuziyue.ebook.model.SYSTEM_FAVORITE_ID
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * 书架仓库（design.md §6.4 / LIB-05 / LIB-06）。
 *
 * 统一抽象「书架」「标签」「收藏」——三者本质都是「一本书属于多个分组」：
 * - [toggleFavorite] / [isFavoriteFlow]：快捷操作「收藏」（系统书架 [SYSTEM_FAVORITE_ID]），底层仍是加入/移除该书架。
 * - [createCollection] / [renameCollection] / [deleteCollection]：用户书架 CRUD；系统书架拒删拒改名（[ensureMutable]）。
 * - [toggleBookInCollection]：单本加入/移除书架（toggle，重复加入幂等由 DAO IGNORE 覆盖，这里返回最终态）。
 * - [addBooksToCollection]：批量加入（LIB-06「移动到书架」）。
 *
 * [clock] / [idGenerator] 注入便于单测固定时间与 id（沿用 [BookmarkRepository] 范式）。
 */
class CollectionRepository(
    private val collectionDao: CollectionDao,
    private val collectionBookDao: CollectionBookDao,
    private val bookDao: BookDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {

    /** 全部书架（含书籍数，系统书架排最前），书架 Tab 主查询。 */
    fun observeCollectionsWithCounts(): Flow<List<Collection>> =
        collectionDao.observeAllWithCounts().map { list -> list.map { it.toDomain() } }

    /** 书架内书籍（带进度 + 搜索）。 */
    fun observeBooksInCollection(collectionId: String, query: String): Flow<List<LibraryItem>> =
        collectionBookDao.observeBooksInCollection(collectionId, query)
            .map { list -> list.map { it.toDomain() } }

    /** 某书所属书架 id 流（详情页展示 chips / 收藏态判断）。 */
    fun observeCollectionIdsForBook(bookId: String): Flow<List<String>> =
        collectionBookDao.observeCollectionIdsForBook(bookId)

    /** 新建用户书架，返回新 id。 */
    suspend fun createCollection(name: String): String {
        val id = idGenerator()
        collectionDao.insert(
            CollectionEntity(
                id = id,
                name = name.trim(),
                sortOrder = clock(),
                createdAt = clock(),
                kind = CollectionKind.CUSTOM,
            ),
        )
        return id
    }

    /** 改名（系统书架拒绝）。 */
    suspend fun renameCollection(id: String, name: String) {
        ensureMutable(id)
        collectionDao.rename(id, name.trim())
    }

    /** 删除书架（系统书架拒绝；删书架不删书籍——collection_books 由 CASCADE 连带清）。 */
    suspend fun deleteCollection(id: String) {
        ensureMutable(id)
        collectionDao.deleteById(id)
    }

    /**
     * 切换某书与某书架的归属关系。返回 true=已加入，false=已移除。
     * 重复加入幂等（DAO IGNORE），但本方法保证返回态与当前一致。
     */
    suspend fun toggleBookInCollection(collectionId: String, bookId: String): Boolean {
        val exists = collectionBookDao.collectionIdsForBook(bookId).contains(collectionId)
        if (exists) {
            collectionBookDao.remove(collectionId, bookId)
            return false
        }
        collectionBookDao.add(
            CollectionBookEntity(collectionId = collectionId, bookId = bookId, addedAt = clock()),
        )
        return true
    }

    /** 批量加入多本书到某书架（LIB-06「移动到书架」）。重复加入幂等。 */
    suspend fun addBooksToCollection(collectionId: String, bookIds: Iterable<String>) {
        val now = clock()
        bookIds.forEach { bookId ->
            collectionBookDao.add(
                CollectionBookEntity(collectionId = collectionId, bookId = bookId, addedAt = now),
            )
        }
    }

    /** 切换收藏（系统书架快捷操作）。返回 true=已收藏。 */
    suspend fun toggleFavorite(bookId: String): Boolean =
        toggleBookInCollection(SYSTEM_FAVORITE_ID, bookId)

    /** 某书当前是否在收藏书架。 */
    suspend fun isFavorite(bookId: String): Boolean =
        collectionBookDao.collectionIdsForBook(bookId).contains(SYSTEM_FAVORITE_ID)

    private suspend fun ensureMutable(id: String) {
        val entity = collectionDao.getById(id)
        check(entity != null) { "书架不存在: $id" }
        check(entity.kind != CollectionKind.SYSTEM_FAVORITE) { "系统书架「收藏」不可修改或删除" }
    }
}
