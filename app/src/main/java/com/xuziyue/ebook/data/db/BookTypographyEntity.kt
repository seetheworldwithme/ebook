package com.xuziyue.ebook.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * 按书排版覆盖实体（Room，design.md §4.4 TYPE-05「按书保存排版偏好」）。
 *
 * - 与 [BookEntity] 1:1，[bookId] 即主键；ForeignKey CASCADE 删书连带删，不留孤儿。
 * - [overridesJson] 存 [com.xuziyue.ebook.data.BookTypographyOverrides] 的 JSON（含 schemaVersion），
 *   **只存本书显式改过的字段**（partial override，非全量快照）——未动字段继续跟随全局值。
 * - 「恢复全局默认」= 删本行，语义干净（见 BookTypographyRepository.clear）。
 */
@Entity(
    tableName = "book_typography",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BookTypographyEntity(
    @PrimaryKey val bookId: String,
    val overridesJson: String,
    val updatedAt: Long,
)
