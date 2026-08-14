package com.xuziyue.ebook.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 阅读会话 DAO（design.md §6.4 / DATA-04）。
 *
 * 聚合查询用 COALESCE(SUM,0) 保证无数据时返回 0 而非 null。
 * 日期分组用 `strftime(...,'unixepoch','localtime')` 按设备本地时区折算（真机验跨日不串）。
 * 删书时本表行由 ForeignKey CASCADE 连带删除。
 */
@Dao
interface ReadingSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReadingSessionEntity)

    /** 某书累计活跃秒数（详情页第 6 区块用）。 */
    @Query("SELECT COALESCE(SUM(activeSeconds), 0) FROM reading_sessions WHERE bookId = :bookId")
    suspend fun totalActiveSecondsForBook(bookId: String): Long

    /** 某书今日（本地时区 [dayStartMs, dayEndMs)）活跃秒数。 */
    @Query(
        "SELECT COALESCE(SUM(activeSeconds), 0) FROM reading_sessions " +
            "WHERE bookId = :bookId AND endedAt >= :dayStartMs AND endedAt < :dayEndMs",
    )
    suspend fun todaySecondsForBook(bookId: String, dayStartMs: Long, dayEndMs: Long): Long

    /** 全局今日活跃秒数（统计页用）。 */
    @Query(
        "SELECT COALESCE(SUM(activeSeconds), 0) FROM reading_sessions " +
            "WHERE endedAt >= :dayStartMs AND endedAt < :dayEndMs",
    )
    suspend fun todayTotalSeconds(dayStartMs: Long, dayEndMs: Long): Long

    /** 全局本周（自 [weekStartMs] 起）活跃秒数。 */
    @Query("SELECT COALESCE(SUM(activeSeconds), 0) FROM reading_sessions WHERE endedAt >= :weekStartMs")
    suspend fun weekTotalSeconds(weekStartMs: Long): Long

    /** 最近（自 [sinceMs] 起）每天的活跃秒数（趋势柱状图）。 */
    @Query(
        """
        SELECT strftime('%Y-%m-%d', endedAt / 1000, 'unixepoch', 'localtime') AS d,
               SUM(activeSeconds) AS s
        FROM reading_sessions
        WHERE endedAt >= :sinceMs
        GROUP BY d
        ORDER BY d ASC
        """,
    )
    suspend fun dailyTotals(sinceMs: Long): List<DailyTotal>

    /** 最近（自 [sinceMs] 起）有阅读的日期去重列表（算连续阅读天数用，倒序）。 */
    @Query(
        """
        SELECT DISTINCT strftime('%Y-%m-%d', endedAt / 1000, 'unixepoch', 'localtime') AS d
        FROM reading_sessions
        WHERE endedAt >= :sinceMs
        ORDER BY d DESC
        """,
    )
    suspend fun distinctRecentDays(sinceMs: Long): List<String>

    /** 全量快照（全表备份 DATA-03 用）。 */
    @Query("SELECT * FROM reading_sessions")
    suspend fun snapshotAll(): List<ReadingSessionEntity>

    /** 清空全部会话（DATA-04「统计可清空」）。 */
    @Query("DELETE FROM reading_sessions")
    suspend fun deleteAll()
}

/** 日聚合结果（日期字符串 → 当日活跃秒数）。Room POJO：列别名 d/s 对齐字段名。 */
data class DailyTotal(
    val d: String,
    val s: Long,
)
