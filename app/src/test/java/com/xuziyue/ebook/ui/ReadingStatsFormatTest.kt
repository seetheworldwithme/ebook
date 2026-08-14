package com.xuziyue.ebook.ui

import com.xuziyue.ebook.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [formatDuration] 时长格式化测试（DATA-04，纯 JVM）。
 *
 * 断言 [UserMessage.Res.resId] 与 args（不依赖 Android Context，纯逻辑分支）。
 */
class ReadingStatsFormatTest {

    @Test
    fun `不足一分钟`() {
        val m = formatDuration(0) as UserMessage.Res
        assertEquals(R.string.duration_minute_zero, m.resId)
    }

    @Test
    fun `59 秒仍算不足一分钟`() {
        val m = formatDuration(59) as UserMessage.Res
        assertEquals(R.string.duration_minute_zero, m.resId)
    }

    @Test
    fun `一分钟到一小时内显示分钟`() {
        val m = formatDuration(120) as UserMessage.Res
        assertEquals(R.string.duration_minute, m.resId)
        assertEquals(listOf("2"), m.args)
    }

    @Test
    fun `超过一小时显示小时分钟`() {
        val m = formatDuration(5400) as UserMessage.Res // 1h30m
        assertEquals(R.string.duration_hour_minute, m.resId)
        assertEquals(listOf("1", "30"), m.args)
    }

    @Test
    fun `刚好一小时分钟为 0`() {
        val m = formatDuration(3600) as UserMessage.Res // 1h0m
        assertEquals(R.string.duration_hour_minute, m.resId)
        assertEquals(listOf("1", "0"), m.args)
    }
}
