package com.xuziyue.ebook.data

import com.xuziyue.ebook.data.db.BookTypographyDao
import com.xuziyue.ebook.data.db.BookTypographyEntity
import com.xuziyue.ebook.model.ReaderTypography
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 按书排版覆盖仓库（design.md §4.4 TYPE-05「按书保存排版偏好」）。
 *
 * - [observe]：本书覆盖（Flow，DB 驱动回流；无行 = [BookTypographyOverrides.Empty]）。
 * - [update]：基于当前覆盖原子修改（书内排版 setter 用；clock 注入可测）。
 * - [clear]：删行 = 「恢复全局默认」。
 *
 * 覆盖只存显式改过的字段（partial override），合并语义见 [mergeTypography]。
 */
class BookTypographyRepository(
    private val dao: BookTypographyDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun observe(bookId: String): Flow<BookTypographyOverrides> =
        dao.observe(bookId).map { entity ->
            BookTypographyOverrides.fromJsonString(entity?.overridesJson)
        }

    suspend fun update(bookId: String, transform: (BookTypographyOverrides) -> BookTypographyOverrides) {
        val current = dao.get(bookId)?.let { BookTypographyOverrides.fromJsonString(it.overridesJson) }
            ?: BookTypographyOverrides.Empty
        val next = transform(current)
        dao.upsert(
            BookTypographyEntity(
                bookId = bookId,
                overridesJson = next.toJsonString(),
                updatedAt = clock(),
            ),
        )
    }

    /** 「恢复全局默认」（TYPE-05 验收）：删覆盖行，本书回到纯全局排版。 */
    suspend fun clear(bookId: String) {
        dao.delete(bookId)
    }

    /** 全表快照（全量备份 DATA-03 用）。 */
    suspend fun snapshotAll(): List<BookTypographyEntity> = dao.snapshotAll()
}
