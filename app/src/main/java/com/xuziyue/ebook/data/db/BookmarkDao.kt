package com.xuziyue.ebook.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 书签 DAO（design.md §6.4 / READ-06）。
 *
 * [upsert] 用 REPLACE（id 冲突覆盖）；删书时本表行由 ForeignKey CASCADE 连带删除。
 * 去重（重复位置不重复生成）在 Repository 层按 locator 等价判定，[forBook] 供其读取同书全部书签做比较。
 */
@Dao
interface BookmarkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BookmarkEntity)

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observe(bookId: String): Flow<List<BookmarkEntity>>

    /** 取同书全部书签（去重判定用，非响应式）。 */
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId")
    suspend fun forBook(bookId: String): List<BookmarkEntity>

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteAllForBook(bookId: String)
}
