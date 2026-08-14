package com.xuziyue.ebook.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xuziyue.ebook.model.HighlightColor
import kotlinx.coroutines.flow.Flow

/**
 * 高亮 / 笔记批注 DAO（design.md §6.4 / READ-07 / 红线 #9）。
 *
 * - [observe] 过滤 `deletedAt IS NULL`：软删除行不进渲染列表，但表内保留供未来回收站 / 同步。
 * - [softDelete] / [softDeleteAllForBook] 仅置 [deletedAt]，不物理删；通过 [observe] 回流驱动 UI 清除。
 * - [update] 覆盖笔记 / 颜色并刷新 [updatedAt]。
 * - 删书时本表行（含软删）由 ForeignKey CASCADE 物理连带删除。
 */
@Dao
interface AnnotationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AnnotationEntity)

    /** 活跃批注（未软删），按创建时间倒序供列表展示。 */
    @Query("SELECT * FROM annotations WHERE bookId = :bookId AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun observe(bookId: String): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE id = :id")
    suspend fun getById(id: String): AnnotationEntity?

    /** 导出快照：同书全部活跃批注（含原始 locatorJson，非响应式，DATA-01 导出用）。 */
    @Query("SELECT * FROM annotations WHERE bookId = :bookId AND deletedAt IS NULL ORDER BY createdAt DESC")
    suspend fun snapshotForBook(bookId: String): List<AnnotationEntity>

    /** 编辑笔记（覆盖 + 刷新 updatedAt）。 */
    @Query("UPDATE annotations SET note = :note, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateNote(id: String, note: String?, updatedAt: Long)

    /** 切换高亮颜色（覆盖 color + 刷新 updatedAt；TypeConverter 自动处理 HighlightColor↔name 字符串）。 */
    @Query("UPDATE annotations SET color = :color, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateColor(id: String, color: HighlightColor, updatedAt: Long)

    /** 软删除单条（置 deletedAt；observe 不再返回）。 */
    @Query("UPDATE annotations SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)

    /** 软删除同书全部活跃批注（"清空"操作；observe 回流清 UI）。 */
    @Query("UPDATE annotations SET deletedAt = :deletedAt WHERE bookId = :bookId AND deletedAt IS NULL")
    suspend fun softDeleteAllForBook(bookId: String, deletedAt: Long)

    /** 全表快照（全量备份 DATA-03 用，含软删行；非响应式）。 */
    @Query("SELECT * FROM annotations")
    suspend fun snapshotAll(): List<AnnotationEntity>
}
