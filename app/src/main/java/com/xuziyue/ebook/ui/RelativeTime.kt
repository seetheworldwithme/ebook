package com.xuziyue.ebook.ui

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
 * 纯 JVM：[nowMillis] 显式注入，单测可固定「当前时间」断言边界。
 * 未来时间（diff < 0）返回空串，避免显示异常。
 */
fun relativeTime(timeMillis: Long?, nowMillis: Long): String {
    if (timeMillis == null) return ""
    val diff = nowMillis - timeMillis
    if (diff < 0) return "" // 未来时间，异常，避免显示「刚刚」
    if (diff < MINUTE) return "刚刚"
    if (diff < HOUR) return "${diff / MINUTE}分钟前"
    if (diff < DAY) return "${diff / HOUR}小时前"
    if (diff < 2 * DAY) return "昨天"
    if (diff < 30 * DAY) return "${diff / DAY}天前"
    // 超过 30 天显示日期；Locale.US 固定 pattern，避免本地化数字差异。
    return SimpleDateFormat("yyyy/M/d", Locale.US).format(Date(timeMillis))
}
