package com.xuziyue.ebook.data

/**
 * 连续阅读天数纯函数（DATA-04）。
 *
 * 输入是按日期去重后的阅读日期列表（`yyyy-MM-dd`，本地时区，倒序——来自 [ReadingSessionDao.distinctRecentDays]），
 * 输出从 [today] 起向前回溯的最长连续天数。
 *
 * 口径：「今天还没断」——若今天读了，从今天起计；若今天还没读但昨天读了，streak 仍算昨天起的连续（今天尚未结束，不算断）。
 */
fun computeStreak(
    /** 倒序的阅读日期列表（最新在前）。 */
    days: List<String>,
    /** 今日日期串 `yyyy-MM-dd`。 */
    today: String,
): Int {
    if (days.isEmpty()) return 0
    val set = days.toSet()
    // 起点：今天读了就从今天起；否则从昨天起（今天还没结束，允许今天空缺）。
    val startDay = if (set.contains(today)) today else shiftDay(today, -1)
    if (!set.contains(startDay)) return 0
    var streak = 0
    var cursor = startDay
    while (set.contains(cursor)) {
        streak++
        cursor = shiftDay(cursor, -1)
    }
    return streak
}

/**
 * 把 `yyyy-MM-dd` 串平移 [deltaDays] 天（用 java.time，避免手算跨月 / 闰年）。
 * [deltaDays] 正为未来，负为过去。
 */
private fun shiftDay(dateStr: String, deltaDays: Int): String {
    val date = java.time.LocalDate.parse(dateStr)
    return date.plusDays(deltaDays.toLong()).toString()
}
