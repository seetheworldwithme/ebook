package com.xuziyue.ebook.data

import com.xuziyue.ebook.data.db.ImportSourceDao
import com.xuziyue.ebook.data.db.ImportSourceEntity

/**
 * 目录导入来源仓库（IMP-06）。
 *
 * 薄封装 [ImportSourceDao]，供 [com.xuziyue.ebook.data.scan.DirectoryScanner] 以
 * lambda 注入的方式使用（scanner 保持纯编排、不感知 Room）。
 */
class ImportSourceRepository(
    private val dao: ImportSourceDao,
) {
    suspend fun findBySourceUri(sourceUri: String): ImportSourceEntity? = dao.findBySourceUri(sourceUri)

    suspend fun upsert(entity: ImportSourceEntity) = dao.upsert(entity)

    suspend fun snapshotAll(): List<ImportSourceEntity> = dao.snapshotAll()

    suspend fun count(): Int = dao.count()
}
