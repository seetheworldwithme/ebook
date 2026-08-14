package com.xuziyue.ebook.ui

import com.xuziyue.ebook.R
import java.util.Calendar

/**
 * 阅读统计 UI 辅助（DATA-04）。
 *
 * - [formatDuration] 把秒数格式化为「X 小时 Y 分钟 / Y 分钟 / N 分钟内」可读文案（返回 [UserMessage] 可本地化）。
 * - [DayBounds] / [dayBoundsFor] / [weekStartFor] 计算本地时区的今日 / 本周起止毫秒，供 DAO 区间聚合查询。
 *
 * 纯 JVM：[now] 显式注入，单测可固定「当前时间」断言边界。
 */

/** 一天的毫秒数。 */
private const val DAY_MS = 86_400_000L

/** 把秒数格式化为人可读时长（≤1 分钟显示「不足 1 分钟」；1 小时内显示分钟；超 1 小时显示「X 小时 Y 分钟」）。 */
fun formatDuration(seconds: Long, now: () -> Long = System::currentTimeMillis): UserMessage = when {
    seconds < 60 -> UserMessage.Res(R.string.duration_minute_zero)
    seconds < 3_600 -> UserMessage.Res(R.string.duration_minute, listOf((seconds / 60).toString()))
    else -> UserMessage.Res(
        R.string.duration_hour_minute,
        listOf((seconds / 3_600).toString(), ((seconds % 3_600) / 60).toString()),
    )
}

/** 一天在本地时区的 [start, end) 毫秒区间。 */
data class DayBounds(val startMs: Long, val endMs: Long)

/** 计算包含 [nowMillis] 的当天在本地时区的 [0点, 次日0点) 区间。 */
fun dayBoundsFor(nowMillis: Long): DayBounds {
    val cal = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val start = cal.timeInMillis
    return DayBounds(startMs = start, endMs = start + DAY_MS)
}

/** 计算包含 [nowMillis] 的本周（周一为周首）在本地时区的起点毫秒。 */
fun weekStartFor(nowMillis: Long): Long {
    val cal = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    }
    return cal.timeInMillis
}
