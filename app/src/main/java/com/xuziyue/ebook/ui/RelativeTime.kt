package com.xuziyue.ebook.ui

import com.xuziyue.ebook.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 1 分钟（毫秒）。 */
private const val MINUTE = 60_000L
/** 1 小时（毫秒）。 */
private const val HOUR = 3_600_000L
/** 1 天（毫秒）。 */
private const val DAY = 86_400_000L

/**
 * 把时间戳格式化为相对时间文案（书库「最近阅读」展示，LIB-01）。
 *
 * 返回 [UserMessage]：可译文案（刚刚 / N 分钟前 / 昨天 …）走字符串资源，日期串（不可译）
 * 走 [UserMessage.Raw]。调用方（Compose）用 [UserMessage.resolve] 解析。
 *
 * 纯 JVM：[nowMillis] 显式注入，单测可固定「当前时间」断言边界（断言 [UserMessage.Res.resId]）。
 * 未来时间（diff < 0）返回空串，避免显示异常。
 */
fun relativeTime(timeMillis: Long?, nowMillis: Long): UserMessage {
    if (timeMillis == null) return UserMessage.Raw("")
    val diff = nowMillis - timeMillis
    if (diff < 0) return UserMessage.Raw("") // 未来时间，异常，避免显示「刚刚」
    if (diff < MINUTE) return UserMessage.Res(R.string.time_just_now)
    if (diff < HOUR) return UserMessage.Res(R.string.time_minutes_ago, listOf(diff / MINUTE))
    if (diff < DAY) return UserMessage.Res(R.string.time_hours_ago, listOf(diff / HOUR))
    if (diff < 2 * DAY) return UserMessage.Res(R.string.time_yesterday)
    if (diff < 30 * DAY) return UserMessage.Res(R.string.time_days_ago, listOf(diff / DAY))
    // 超过 30 天显示日期；Locale.US 固定 pattern，避免本地化数字差异。日期串不可译，走 Raw。
    return UserMessage.Raw(SimpleDateFormat("yyyy/M/d", Locale.US).format(Date(timeMillis)))
}
