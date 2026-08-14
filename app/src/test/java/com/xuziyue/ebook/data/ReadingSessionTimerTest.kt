package com.xuziyue.ebook.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 计时纯函数 [computeActiveSeconds] 测试（DATA-04：差值法 + 静止封顶）。
 *
 * 纯 JVM（无 Android 依赖），验证 design.md「无长时间静止时计时」算法边界。
 */
class ReadingSessionTimerTest {

    /** 静止封顶阈值（与 Repository 的 INACTIVITY_LIMIT_MS 一致）。 */
    private val limit = 5L * 60 * 1000

    @Test
    fun `未超过静止阈值时整段计入`() {
        // 0→120s 活动，60s 前最后一次活跃，结束于 120s，120-60=60 < 300s 阈值 → 全计 120s。
        val s = computeActiveSeconds(
            startedAt = 0,
            lastActiveAt = 60_000,
            endedAt = 120_000,
            inactivityLimitMs = limit,
        )
        assertEquals(120L, s)
    }

    @Test
    fun `超过静止阈值的尾巴被裁掉`() {
        // 0 开始，30s 时最后一次翻页，400s 才退出 → 30s + 300s 阈值 = 330s 计入，后 70s 裁掉。
        val s = computeActiveSeconds(
            startedAt = 0,
            lastActiveAt = 30_000,
            endedAt = 400_000,
            inactivityLimitMs = limit,
        )
        assertEquals(330L, s)
    }

    @Test
    fun `从未活动（lastActiveAt 等于 startedAt）也封顶阈值`() {
        // 开了书但一页没翻就退出很久 → 只计阈值上限（5 分钟）。
        val s = computeActiveSeconds(
            startedAt = 0,
            lastActiveAt = 0,
            endedAt = 3_600_000,
            inactivityLimitMs = limit,
        )
        assertEquals(300L, s)
    }

    @Test
    fun `lastActiveAt 早于 startedAt（时钟异常）兜底为 0`() {
        val s = computeActiveSeconds(
            startedAt = 100_000,
            lastActiveAt = 50_000, // 异常早
            endedAt = 200_000,
            inactivityLimitMs = limit,
        )
        // effectiveEnd = min(200000, max(50000,100000)+300000) = 200000; (200000-100000)/1000 = 100
        assertEquals(100L, s)
    }

    @Test
    fun `结束时刻早于起点（时钟回退）coerce 为 0`() {
        val s = computeActiveSeconds(
            startedAt = 100_000,
            lastActiveAt = 100_000,
            endedAt = 50_000, // 回退到起点前
            inactivityLimitMs = limit,
        )
        assertEquals(0L, s)
    }

    @Test
    fun `活动后立即退出阈值封顶不生效全计`() {
        // 60s 活动到 120s 最后活跃，120s 立即退出 → min(120000, 120000+300000)=120000 → 60s。
        val s = computeActiveSeconds(
            startedAt = 60_000,
            lastActiveAt = 120_000,
            endedAt = 120_000,
            inactivityLimitMs = limit,
        )
        assertEquals(60L, s)
    }
}
