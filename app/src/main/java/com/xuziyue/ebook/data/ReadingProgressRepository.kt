package com.xuziyue.ebook.data

import com.xuziyue.ebook.data.db.ReadingProgressDao
import com.xuziyue.ebook.data.db.ReadingProgressEntity
import org.readium.r2.shared.publication.Locator

/**
 * 阅读进度仓库（design.md §6.5，红线 #1：Locator 为主进度数据）。
 *
 * - [getLocator] 从 Room 取 Locator 恢复阅读位置。
 * - [save] 防抖/强制落盘时调用，存 [PersistedLocator] JSON（含 schemaVersion）+ 全书 progression。
 * - [clock] 注入便于单测固定时间。
 */
class ReadingProgressRepository(
    private val dao: ReadingProgressDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** 取恢复位置；无记录或损坏返回 null。 */
    suspend fun getLocator(bookId: String): Locator? {
        val raw = dao.get(bookId)?.locatorJson ?: return null
        return PersistedLocator.fromJsonString(raw)?.toLocator()
    }

    /** 保存阅读位置（Locator 原样 + 全书进度派生列）。 */
    suspend fun save(bookId: String, locator: Locator) {
        dao.upsert(
            ReadingProgressEntity(
                bookId = bookId,
                locatorJson = PersistedLocator.from(locator).toJsonString(),
                progression = locator.locations.totalProgression,
                updatedAt = clock(),
                deviceId = null, // 同步预留，MVP 不填
            ),
        )
    }

    suspend fun delete(bookId: String) = dao.delete(bookId)
}
