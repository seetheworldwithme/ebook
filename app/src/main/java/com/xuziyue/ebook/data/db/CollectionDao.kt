package com.xuziyue.ebook.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 书架 DAO（design.md §6.4 / LIB-05）。
 *
 * - [observeAllWithCounts]：书架 Tab 主查询，LEFT JOIN collection_books 聚合 bookCount，
 *   按 sortOrder、createdAt 排序（系统书架 sortOrder=0 自然靠前，或迁移插入时给极小值）。
 * - [insert] ABORT（id 冲突抛，调用方先查重）。
 * - 删书架由 [deleteById] 物理删 collections 行，collection_books 关系由 ForeignKey CASCADE 连带清。
 */
@Dao
interface CollectionDao {

    /**
     * 书架列表（含书籍数，LIB-05 书架 Tab）。空书架 bookCount=0。
     * 系统书架「收藏」固定排最前（迁移插入时 sortOrder = Long.MIN_VALUE）。
     */
    @Query(
        """
        SELECT c.id AS id, c.name AS name, c.sortOrder AS sortOrder, c.createdAt AS createdAt,
               c.kind AS kind, COUNT(cb.bookId) AS bookCount
        FROM collections c
        LEFT JOIN collection_books cb ON cb.collectionId = c.id
        GROUP BY c.id
        ORDER BY c.sortOrder ASC, c.createdAt ASC
        """,
    )
    fun observeAllWithCounts(): Flow<List<CollectionWithCountEntity>>

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun getById(id: String): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: CollectionEntity)

    /** upsert（恢复用）：已存在同 id 覆盖，不抛冲突。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CollectionEntity)

    @Query("UPDATE collections SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteById(id: String)

    /** 全表快照（全量备份 DATA-03 用，非响应式）。 */
    @Query("SELECT * FROM collections")
    suspend fun snapshotAll(): List<CollectionEntity>
}
