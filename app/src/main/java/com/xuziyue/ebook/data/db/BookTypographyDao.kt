package com.xuziyue.ebook.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 按书排版覆盖 DAO（TYPE-05）。
 *
 * [upsert] 用 REPLACE：PK=bookId 天然覆盖。删书时本表行由 ForeignKey CASCADE 连带删除。
 */
@Dao
interface BookTypographyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BookTypographyEntity)

    @Query("SELECT * FROM book_typography WHERE bookId = :bookId")
    suspend fun get(bookId: String): BookTypographyEntity?

    @Query("SELECT * FROM book_typography WHERE bookId = :bookId")
    fun observe(bookId: String): Flow<BookTypographyEntity?>

    @Query("DELETE FROM book_typography WHERE bookId = :bookId")
    suspend fun delete(bookId: String)

    /** 全表快照（全量备份 DATA-03 用，非响应式）。 */
    @Query("SELECT * FROM book_typography")
    suspend fun snapshotAll(): List<BookTypographyEntity>
}
