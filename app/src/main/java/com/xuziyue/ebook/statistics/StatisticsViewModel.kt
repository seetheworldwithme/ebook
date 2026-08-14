package com.xuziyue.ebook.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xuziyue.ebook.data.AppSettingsRepository
import com.xuziyue.ebook.data.ReadingSessionRepository
import com.xuziyue.ebook.data.computeStreak
import com.xuziyue.ebook.ui.dayBoundsFor
import com.xuziyue.ebook.ui.weekStartFor
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 阅读统计页 UiState（DATA-04）。
 *
 * - [todaySeconds] / [weekSeconds]：今日 / 本周总活跃秒数。
 * - [streak]：连续阅读天数（今天还没断口径）。
 * - [daily]：最近 7 天每日秒数（柱状趋势），最早在前。
 */
data class StatisticsUiState(
    val todaySeconds: Long = 0,
    val weekSeconds: Long = 0,
    val streak: Int = 0,
    val daily: List<DailyStat> = emptyList(),
    val statsEnabled: Boolean = true,
)

/** 单日统计（日期串 + 秒数），最早在前。 */
data class DailyStat(val date: String, val seconds: Long)

/**
 * 阅读统计页 ViewModel（DATA-04）。
 *
 * 数据来自 [ReadingSessionRepository]（reading_sessions 表聚合）。统计开关关闭时仍可进页面
 * （展示 0 + 提示），便于用户重新开启。
 */
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val sessionRepository: ReadingSessionRepository,
    private val appSettings: AppSettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { appSettings.readingStatsEnabled.collect { refresh(it) } }
    }

    /** 拉取最新统计数据。进入页面 / 开关变化时触发。 */
    private suspend fun refresh(statsEnabled: Boolean) {
        val now = System.currentTimeMillis()
        val bounds = dayBoundsFor(now)
        val weekStart = weekStartFor(now)
        val sevenDaysAgo = now - 7L * 86_400_000L

        val today = sessionRepository.todayTotalSeconds(bounds.startMs, bounds.endMs)
        val week = sessionRepository.weekTotalSeconds(weekStart)
        val dailyRaw = sessionRepository.dailyTotals(sevenDaysAgo).associate { it.d to it.s }
        val days = sessionRepository.distinctRecentDays(0L) // 全部历史算连续天数
        val streak = computeStreak(days, LocalDate.now().toString())

        // 补齐最近 7 天（无阅读的日子填 0），最早在前。
        val daily = (6 downTo 0).map { offset ->
            val date = LocalDate.now().minusDays(offset.toLong()).toString()
            DailyStat(date, dailyRaw[date] ?: 0)
        }

        _uiState.value = StatisticsUiState(
            todaySeconds = today,
            weekSeconds = week,
            streak = streak,
            daily = daily,
            statsEnabled = statsEnabled,
        )
    }

    /** 设置统计开关。 */
    fun setStatsEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettings.setReadingStatsEnabled(enabled) }
    }

    /** 清空全部阅读统计（DATA-04「统计可清空」）。 */
    fun clearStats() {
        viewModelScope.launch {
            sessionRepository.clearAll()
            refresh(_uiState.value.statsEnabled)
        }
    }
}
