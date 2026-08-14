package com.xuziyue.ebook.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xuziyue.ebook.model.ReadingStatus
import kotlinx.coroutines.flow.Flow

/**
 * 书籍 DAO（design.md §6.5）。
 *
 * 去重：[insert] 用 ABORT，contentHash uniqueIndex 冲突即抛；调用方先 [getByContentHash] 查重避免走到冲突。
 */
@Dao
interface BookDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(book: BookEntity)

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: String): BookEntity?

    /** 单本书响应式（LIB-04 详情页：从阅读器返回时 lastOpenedAt/状态变化能实时反映）。 */
    @Query("SELECT * FROM books WHERE id = :id")
    fun observeById(id: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE contentHash = :hash")
    suspend fun getByContentHash(hash: String): BookEntity?

    /** 书库列表：未读的（lastOpenedAt 为空）排末尾，其余按最近阅读降序，再按导入降序。 */
    @Query(
        """
        SELECT * FROM books
        ORDER BY lastOpenedAt IS NULL, lastOpenedAt DESC, importedAt DESC
        """,
    )
    fun observeAll(): Flow<List<BookEntity>>

    /**
     * 书库列表（带进度 + 搜索 + 三入口筛选，LIB-01 / LIB-03 / LIB-02）。
     *
     * - LEFT JOIN reading_progress 取 progression（无进度 = null 未读）；一次查询拿全书库进度条。
     * - [query] 空串 = 全量；否则按书名 / 作者 LIKE 子串匹配。authors 是 JSON 数组串，
     *   `LIKE '%作者%'` 能命中其中任一作者。SQLite LIKE 对 ASCII 大小写不敏感、中文直接匹配
     *   （满足「搜索忽略大小写；中文书名可直接匹配」）。
     * - [filter] 三入口（LIB-02）：`RECENT`=打开过即算（lastOpenedAt 非空）、
     *   `FINISHED`=status='FINISHED'（[com.xuziyue.ebook.model.ReadingStatus.FINISHED.name]，
     *   TypeConverter 存 name 字符串）、`ALL`/其它=放行。CASE 在 WHERE 段筛选，Flow 自动回推
     *   满足「阅读/完成状态变化后列表实时更新」硬验收）。
     * - 排序基准同 [observeAll]（最近阅读 / 导入），其它排序在应用层内存排序（见 [com.xuziyue.ebook.library.sortItems]）。
     */
    @Query(
        """
        SELECT b.*, rp.progression AS progress
        FROM books b
        LEFT JOIN reading_progress rp ON rp.bookId = b.id
        WHERE (:query = '' OR b.title LIKE '%' || :query || '%' OR b.authors LIKE '%' || :query || '%')
          AND CASE :filter
                WHEN 'RECENT' THEN b.lastOpenedAt IS NOT NULL
                WHEN 'FINISHED' THEN b.status = 'FINISHED'
                ELSE 1
              END
        ORDER BY b.lastOpenedAt IS NULL, b.lastOpenedAt DESC, b.importedAt DESC
        """,
    )
    fun observeLibraryItems(query: String, filter: String): Flow<List<LibraryItemEntity>>

    /** 打开时一次写最近阅读时间 + 状态（READ-01/READ-08）。 */
    @Query("UPDATE books SET lastOpenedAt = :time, status = :status WHERE id = :id")
    suspend fun touchOpened(id: String, time: Long, status: ReadingStatus)

    @Query("UPDATE books SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: ReadingStatus)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: String)

    /** 全表快照（全量备份 DATA-03 用，非响应式）。 */
    @Query("SELECT * FROM books")
    suspend fun snapshotAll(): List<BookEntity>
}
