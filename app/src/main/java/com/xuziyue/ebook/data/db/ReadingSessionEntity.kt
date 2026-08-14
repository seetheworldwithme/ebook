package com.xuziyue.ebook.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 阅读会话实体（Room，design.md §6.4 / DATA-04：阅读时长统计）。
 *
 * - [bookId] 指向 [BookEntity.id]，ForeignKey CASCADE：删书连带删会话。
 * - [startedAt] / [endedAt] 毫秒时间戳；[activeSeconds] 本次会话的有效活跃秒数
 *   （已扣除长时间静止段，design.md「无长时间静止时计时」，算法见 [com.xuziyue.ebook.data.ReadingSessionRepository]）。
 * - 一本书可有多条会话（每次打开→退出一条），聚合 SUM 即得该书总时长。
 */
@Entity(
    tableName = "reading_sessions",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class ReadingSessionEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val startedAt: Long,
    val endedAt: Long,
    val activeSeconds: Long,
)
