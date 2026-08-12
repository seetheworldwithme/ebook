package com.xuziyue.ebook.ui

import com.xuziyue.ebook.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [relativeTime] 边界单测：null / 刚刚 / 分 / 时 / 昨天 / N天前 / 日期。
 * now 固定为 2024-06-15 12:00（毫秒）。
 *
 * SET-01 后返回 [UserMessage]：可译文案断言 [UserMessage.Res.resId]（+ args），
 * 不再脆性依赖字面文案；日期串走 [UserMessage.Raw]。
 */
class RelativeTimeTest {

    private val now = 1_718_445_600_000L // 2024-06-15 12:00:00 UTC 区间内的稳定锚点

    @Test
    fun `null 返回空串`() {
        assertEquals(UserMessage.Raw(""), relativeTime(null, now))
    }

    @Test
    fun `30 秒内显示刚刚`() {
        val result = relativeTime(now - 29_000, now)
        assertEquals(R.string.time_just_now, (result as UserMessage.Res).resId)
    }

    @Test
    fun `分钟段`() {
        val result = relativeTime(now - 5 * 60_000, now) as UserMessage.Res
        assertEquals(R.string.time_minutes_ago, result.resId)
        assertEquals(5L, result.args[0])
    }

    @Test
    fun `小时段`() {
        val result = relativeTime(now - 3 * 3_600_000, now) as UserMessage.Res
        assertEquals(R.string.time_hours_ago, result.resId)
        assertEquals(3L, result.args[0])
    }

    @Test
    fun `跨一天显示昨天`() {
        assertEquals(R.string.time_yesterday, (relativeTime(now - 26 * 3_600_000, now) as UserMessage.Res).resId)
    }

    @Test
    fun `N 天前`() {
        val result = relativeTime(now - 5L * 86_400_000, now) as UserMessage.Res
        assertEquals(R.string.time_days_ago, result.resId)
        assertEquals(5L, result.args[0])
    }

    @Test
    fun `超过 30 天显示日期`() {
        // 2024-04-10 左右（now - 66 天）
        val old = now - 66L * 86_400_000
        val result = relativeTime(old, now) as UserMessage.Raw
        // 只断言含年份分隔符格式，避免时区精确日漂移
        assertTrue("实际: ${result.text}", result.text.matches(Regex("\\d{4}/\\d{1,2}/\\d{1,2}")))
    }

    @Test
    fun `未来时间返回空串`() {
        assertEquals(UserMessage.Raw(""), relativeTime(now + 10_000, now))
    }
}
