package com.xuziyue.ebook.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 书架-书籍 关联 DAO（design.md §6.4 CollectionBook / LIB-05）。
 *
 * - [add] IGNORE（collectionId+bookId 联合主键冲突即忽略——重复加入同一书架幂等，不抛异常）。
 * - 删书 / 删书架时本表行由 ForeignKey CASCADE 连带删除。
 * - [observeBooksInCollection]：书架内浏览，JOIN books + LEFT JOIN reading_progress 复用
 *   [BookDao.observeLibraryItems] 的进度投影范式。
 */
@Dao
interface CollectionBookDao {

    /** 书架内书籍（带进度，LIB-05 书架内浏览）。按书自身最近阅读序。 */
    @Query(
        """
        SELECT b.*, rp.progression AS progress
        FROM books b
        INNER JOIN collection_books cb ON cb.bookId = b.id
        LEFT JOIN reading_progress rp ON rp.bookId = b.id
        WHERE cb.collectionId = :collectionId
          AND (:query = '' OR b.title LIKE '%' || :query || '%' OR b.authors LIKE '%' || :query || '%')
        ORDER BY b.lastOpenedAt IS NULL, b.lastOpenedAt DESC, b.importedAt DESC
        """,
    )
    fun observeBooksInCollection(collectionId: String, query: String): Flow<List<LibraryItemEntity>>

    /** 某书所属全部书架 id（详情页/收藏判断用，响应式）。 */
    @Query("SELECT collectionId FROM collection_books WHERE bookId = :bookId")
    fun observeCollectionIdsForBook(bookId: String): Flow<List<String>>

    /** 某书所属全部书架 id（非响应式快照，判断收藏用）。 */
    @Query("SELECT collectionId FROM collection_books WHERE bookId = :bookId")
    suspend fun collectionIdsForBook(bookId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(entity: CollectionBookEntity)

    @Query("DELETE FROM collection_books WHERE collectionId = :collectionId AND bookId = :bookId")
    suspend fun remove(collectionId: String, bookId: String)

    /** 删书时清该书所有书架归属（CASCADE 已覆盖，此方法供显式批量场景兜底）。 */
    @Query("DELETE FROM collection_books WHERE bookId = :bookId")
    suspend fun removeBookFromAll(bookId: String)

    /** 全表快照（全量备份 DATA-03 用，非响应式）。 */
    @Query("SELECT * FROM collection_books")
    suspend fun snapshotAll(): List<CollectionBookEntity>
}
