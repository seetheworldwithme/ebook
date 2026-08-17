package com.xuziyue.ebook.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 目录导入来源实体（Room，IMP-06 增量扫描）。
 *
 * 记录「授权目录里的某个源文件 → 已导入的书籍」映射，供下次扫描做增量判定
 * （design.md：按 URI、大小、修改时间和哈希增量处理）：
 * - [sourceUri] 唯一——同一源文件只一条记录，重扫 upsert 刷新。
 * - [fileSize] / [lastModified] 是扫描时该源文件的快照；下次扫描两者均未变则跳过（不重读内容），
 *   任一变化则重新走导入链路（导入内部仍有 contentHash 去重兜底）。
 * - [bookId] 指向 [BookEntity.id]，ForeignKey CASCADE：app 内删书 → 记录自动清 →
 *   下次扫描视该文件为新文件重新导入（目录同步语义：删书后重扫会「复活」，
 *   若未来要做「删了不回来」再加 tombstone，当前不做）。
 */
@Entity(
    tableName = "import_sources",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sourceUri"], unique = true), Index("bookId")],
)
data class ImportSourceEntity(
    @PrimaryKey val id: String,
    val sourceUri: String,
    val bookId: String,
    val fileSize: Long,
    val lastModified: Long,
    val scannedAt: Long,
)
