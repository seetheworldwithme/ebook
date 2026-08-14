package com.xuziyue.ebook.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 连续阅读天数纯函数 [computeStreak] 测试（DATA-04）。
 *
 * 纯 JVM（java.time 可用），覆盖今天读了 / 今天没读昨天读了 / 连续 / 断 / 跨月等边界。
 */
class StreakCalculatorTest {

    @Test
    fun `今天读了且昨天读了计数 2`() {
        // 倒序（最新在前）
        val days = listOf("2026-08-14", "2026-08-13")
        assertEquals(2, computeStreak(days, "2026-08-14"))
    }

    @Test
    fun `今天没读但昨天读了不算断（从昨天起计）`() {
        // 「今天还没断」口径
        val days = listOf("2026-08-13", "2026-08-12")
        assertEquals(2, computeStreak(days, "2026-08-14"))
    }

    @Test
    fun `今天和昨天都没读则断`() {
        val days = listOf("2026-08-12", "2026-08-11")
        assertEquals(0, computeStreak(days, "2026-08-14"))
    }

    @Test
    fun `连续 7 天`() {
        val days = (0 until 7).map { i ->
            java.time.LocalDate.of(2026, 8, 14).minusDays(i.toLong()).toString()
        }
        assertEquals(7, computeStreak(days, "2026-08-14"))
    }

    @Test
    fun `空列表为 0`() {
        assertEquals(0, computeStreak(emptyList(), "2026-08-14"))
    }

    @Test
    fun `跨月边界（7月底连续到8月初）`() {
        // 7/30, 7/31, 8/1, 8/2，今天 8/2 → 连续 4 天
        val days = listOf("2026-08-02", "2026-08-01", "2026-07-31", "2026-07-30")
        assertEquals(4, computeStreak(days, "2026-08-02"))
    }

    @Test
    fun `中间断了一天只计到断点`() {
        // 8/14, 8/13, 缺 8/12, 8/11（不连续）
        val days = listOf("2026-08-14", "2026-08-13", "2026-08-11")
        assertEquals(2, computeStreak(days, "2026-08-14"))
    }

    @Test
    fun `列表乱序但包含连续日期仍正确`() {
        // computeStreak 用 set 查询，顺序不影响（传入虽约定倒序，但实现基于集合）
        val days = listOf("2026-08-13", "2026-08-14")
        assertEquals(2, computeStreak(days, "2026-08-14"))
    }
}
