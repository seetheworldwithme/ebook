package com.xuziyue.ebook.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [relativeTime] 边界单测：null / 刚刚 / 分 / 时 / 昨天 / N天前 / 日期。
 * now 固定为 2024-06-15 12:00（毫秒）。
 */
class RelativeTimeTest {

    private val now = 1_718_445_600_000L // 2024-06-15 12:00:00 UTC 区间内的稳定锚点

    @Test
    fun `null 返回空串`() {
        assertEquals("", relativeTime(null, now))
    }

    @Test
    fun `30 秒内显示刚刚`() {
        assertEquals("刚刚", relativeTime(now - 29_000, now))
    }

    @Test
    fun `分钟段`() {
        assertEquals("5分钟前", relativeTime(now - 5 * 60_000, now))
    }

    @Test
    fun `小时段`() {
        assertEquals("3小时前", relativeTime(now - 3 * 3_600_000, now))
    }

    @Test
    fun `跨一天显示昨天`() {
        assertEquals("昨天", relativeTime(now - 26 * 3_600_000, now))
    }

    @Test
    fun `N 天前`() {
        assertEquals("5天前", relativeTime(now - 5L * 86_400_000, now))
    }

    @Test
    fun `超过 30 天显示日期`() {
        // 2024-04-10 左右（now - 66 天）
        val old = now - 66L * 86_400_000
        // 只断言含年份分隔符格式，避免时区精确日漂移
        val result = relativeTime(old, now)
        assert(result.matches(Regex("\\d{4}/\\d{1,2}/\\d{1,2}"))) { "实际: $result" }
    }

    @Test
    fun `未来时间返回空串`() {
        assertEquals("", relativeTime(now + 10_000, now))
    }
}
