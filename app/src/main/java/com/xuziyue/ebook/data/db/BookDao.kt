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

    /** 打开时一次写最近阅读时间 + 状态（READ-01/READ-08）。 */
    @Query("UPDATE books SET lastOpenedAt = :time, status = :status WHERE id = :id")
    suspend fun touchOpened(id: String, time: Long, status: ReadingStatus)

    @Query("UPDATE books SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: ReadingStatus)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: String)
}
