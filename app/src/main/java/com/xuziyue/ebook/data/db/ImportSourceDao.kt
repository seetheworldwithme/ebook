package com.xuziyue.ebook.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * 目录导入来源 DAO（IMP-06）。
 *
 * 写入只在导入成功后 upsert（失败不落记录，下次扫描可重试）；
 * 扫描期按 sourceUri 批量读快照做增量判定。
 */
@Dao
interface ImportSourceDao {

    /** 按源文件 Uri 查记录（增量判定主入口）。 */
    @Query("SELECT * FROM import_sources WHERE sourceUri = :sourceUri LIMIT 1")
    suspend fun findBySourceUri(sourceUri: String): ImportSourceEntity?

    /** 扫描结束刷新快照（sourceUri 唯一索引，UPSERT 覆盖旧记录）。 */
    @Upsert
    suspend fun upsert(entity: ImportSourceEntity)

    /** 全表快照（诊断 / 测试用）。 */
    @Query("SELECT * FROM import_sources")
    suspend fun snapshotAll(): List<ImportSourceEntity>

    /** 记录数（诊断 / 测试用）。 */
    @Query("SELECT COUNT(*) FROM import_sources")
    suspend fun count(): Int

    /** 观察记录数（测试用）。 */
    @Query("SELECT COUNT(*) FROM import_sources")
    fun observeCount(): Flow<Int>
}
