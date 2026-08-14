package com.xuziyue.ebook.data

import com.xuziyue.ebook.data.db.ReadingSessionDao
import com.xuziyue.ebook.data.db.ReadingSessionEntity
import java.util.UUID
import kotlinx.coroutines.flow.first

/**
 * 阅读会话仓库（design.md §6.4 / DATA-04：阅读时长统计）。
 *
 * 计时方案：**时间戳差值法 + 静止封顶**（不用协程 delay 循环——进程被杀时循环会丢，
 * 差值法在生命周期事件点一次性结算更可靠）。
 *
 * - [startSession] 在阅读器打开一本书时调用，记录 [startedAt]；统计开关关闭时返回 null（VM 据此不计时）。
 * - [touchActive] 在翻页 / 滚动（Locator 更新）时调用，刷新 [lastActiveAt]（仅内存，不写库）——这是「活跃信号」。
 * - [endSession] 在 onPause / 切书 / onCleared 时调用，按差值法结算 [activeSeconds] 落盘：
 *   有效活跃区间 = min(结束时刻, lastActiveAt + [INACTIVITY_LIMIT_MS])，超过阈值的静止尾巴被裁掉
 *   （满足 design.md「无长时间静止时计时」）。
 * - [clock] / [idGenerator] 注入便于单测固定时间与 id。
 *
 * 注：内存态 [active] 只在单进程内有效；onPause 即落盘（不依赖 onCleared，强杀时 onPause 几乎必然触发）。
 */
class ReadingSessionRepository(
    private val dao: ReadingSessionDao,
    private val appSettings: AppSettingsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {

    /** 单次会话最长计入活跃的时间（超过即视为用户已离开，design.md「长时间静止」）。 */
    private val inactivityLimitMs: Long = INACTIVITY_LIMIT_MS

    /** 内存态活跃会话（sessionId → 元组）。 */
    private data class Active(
        val bookId: String,
        val startedAt: Long,
        var lastActiveAt: Long,
    )

    private var active: Active? = null

    /**
     * 开始会话，返回 sessionId 供 VM 持有；统计关闭时返回 null（VM 据此后续全部 no-op）。
     */
    suspend fun startSession(bookId: String): String? {
        if (!appSettings.readingStatsEnabled.first()) return null
        val now = clock()
        val id = idGenerator()
        active = Active(bookId = bookId, startedAt = now, lastActiveAt = now)
        return id
    }

    /** 翻页 / 滚动时刷新活跃时间（仅内存，不写库）。 */
    fun touchActive(sessionId: String?) {
        if (sessionId == null) return
        active?.let { if (it.lastActiveAt < it.startedAt) return } // 防御
        active?.lastActiveAt = clock()
    }

    /**
     * 结束会话并落盘：按差值法结算 [activeSeconds]（封顶静止段），写 [ReadingSessionEntity]。
     * 结算后清空内存态。
     */
    suspend fun endSession(sessionId: String?) {
        val a = active ?: return
        if (sessionId == null) {
            active = null
            return
        }
        val endedAt = clock()
        val activeSeconds = computeActiveSeconds(
            startedAt = a.startedAt,
            lastActiveAt = a.lastActiveAt,
            endedAt = endedAt,
            inactivityLimitMs = inactivityLimitMs,
        )
        if (activeSeconds > 0) {
            dao.upsert(
                ReadingSessionEntity(
                    id = sessionId,
                    bookId = a.bookId,
                    startedAt = a.startedAt,
                    endedAt = endedAt,
                    activeSeconds = activeSeconds,
                ),
            )
        }
        active = null
    }

    /** 清空全部会话（DATA-04「统计可清空」）。 */
    suspend fun clearAll() {
        active = null
        dao.deleteAll()
    }

    // ===== 聚合查询透传（详情页 / 统计页用） =====

    suspend fun bookTotalSeconds(bookId: String): Long = dao.totalActiveSecondsForBook(bookId)

    suspend fun bookTodaySeconds(bookId: String, dayStartMs: Long, dayEndMs: Long): Long =
        dao.todaySecondsForBook(bookId, dayStartMs, dayEndMs)

    suspend fun todayTotalSeconds(dayStartMs: Long, dayEndMs: Long): Long =
        dao.todayTotalSeconds(dayStartMs, dayEndMs)

    suspend fun weekTotalSeconds(weekStartMs: Long): Long = dao.weekTotalSeconds(weekStartMs)

    suspend fun dailyTotals(sinceMs: Long) = dao.dailyTotals(sinceMs)

    suspend fun distinctRecentDays(sinceMs: Long) = dao.distinctRecentDays(sinceMs)

    private companion object {
        /** 静止封顶阈值：5 分钟（可调，真机观察后调整）。 */
        const val INACTIVITY_LIMIT_MS = 5L * 60 * 1000
    }
}

/**
 * 差值法计时纯函数：有效活跃秒数 = min(endedAt, lastActiveAt + inactivityLimitMs) - startedAt。
 *
 * - 若 lastActiveAt 在 startedAt 之前（异常 / 时钟回退），以 startedAt 兜底，结果 0。
 * - 阈值内的静止段计入，超阈值的尾巴裁掉。
 */
fun computeActiveSeconds(
    startedAt: Long,
    lastActiveAt: Long,
    endedAt: Long,
    inactivityLimitMs: Long,
): Long {
    val effectiveEnd = minOf(endedAt, maxOf(lastActiveAt, startedAt) + inactivityLimitMs)
    return ((effectiveEnd - startedAt) / 1000).coerceAtLeast(0)
}
