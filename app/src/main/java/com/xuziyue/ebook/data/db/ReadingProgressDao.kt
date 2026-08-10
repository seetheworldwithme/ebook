package com.xuziyue.ebook.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 阅读进度 DAO（design.md §6.5）。
 *
 * [upsert] 用 REPLACE：PK=bookId 天然覆盖。删书时本表行由 ForeignKey CASCADE 连带删除（见 [ReadingProgressEntity]）。
 */
@Dao
interface ReadingProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReadingProgressEntity)

    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    suspend fun get(bookId: String): ReadingProgressEntity?

    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    fun observe(bookId: String): Flow<ReadingProgressEntity?>

    @Query("DELETE FROM reading_progress WHERE bookId = :bookId")
    suspend fun delete(bookId: String)
}
