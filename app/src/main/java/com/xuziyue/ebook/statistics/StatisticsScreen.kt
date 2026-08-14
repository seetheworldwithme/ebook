package com.xuziyue.ebook.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuziyue.ebook.R
import com.xuziyue.ebook.ui.formatDuration
import com.xuziyue.ebook.ui.resolve

/**
 * 阅读统计页（DATA-04）：今日 / 本周总时长 + 最近 7 天柱状趋势 + 连续天数 + 清空。
 *
 * 范式照抄 [com.xuziyue.ebook.settings.SettingsScreen]（Scaffold + TopAppBar + LazyColumn）。
 * 柱状图用 Canvas 手绘（不引图表库，符合「不随手拆依赖」）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onBack: () -> Unit,
    viewModel: StatisticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.statistics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.statistics_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.statistics_clear))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp)) {
            // 统计开关（DATA-04：统计可关闭；关时阅读器不创建会话）
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = state.statsEnabled,
                            onValueChange = { viewModel.setStatsEnabled(it) },
                            role = Role.Switch,
                        )
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.statistics_enable), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = state.statsEnabled, onCheckedChange = null)
                }
                HorizontalDivider()
                Spacer(Modifier.size(12.dp))
            }
            // 今日 / 本周大字
            item {
                StatCard(
                    label = stringResource(R.string.statistics_today),
                    value = formatDuration(state.todaySeconds).resolve(context),
                )
                Spacer(Modifier.size(8.dp))
                StatCard(
                    label = stringResource(R.string.statistics_week),
                    value = formatDuration(state.weekSeconds).resolve(context),
                )
            }

            item {
                Spacer(Modifier.size(16.dp))
                HorizontalDivider()
                Spacer(Modifier.size(16.dp))
                Text(
                    stringResource(R.string.statistics_streak, state.streak),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }

            // 最近 7 天趋势
            item {
                Spacer(Modifier.size(16.dp))
                Text(
                    stringResource(R.string.statistics_daily_trend),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.size(12.dp))
                DailyBarChart(daily = state.daily)
            }

            // 统计关闭时提示
            if (!state.statsEnabled) {
                item {
                    Spacer(Modifier.size(16.dp))
                    Text(
                        stringResource(R.string.statistics_disabled_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.statistics_clear)) },
            text = { Text(stringResource(R.string.statistics_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    viewModel.clearStats()
                }) { Text(stringResource(R.string.statistics_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

/** 单个统计卡片：label + 大字值。 */
@Composable
private fun StatCard(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * 最近 7 天柱状图（Canvas 手绘）。最高柱按可用高度等比缩放；无数据（全 0）时画等高细柱占位。
 * 每根柱下方标日期（M/d）。
 */
@Composable
private fun DailyBarChart(daily: List<DailyStat>) {
    val barColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxSeconds = (daily.maxOfOrNull { it.seconds } ?: 0L).coerceAtLeast(1L)

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            val barCount = daily.size.coerceAtLeast(1)
            val slot = size.width / barCount
            val barWidth = slot * 0.5f
            val baseY = size.height
            daily.forEachIndexed { i, d ->
                val ratio = (d.seconds.toFloat() / maxSeconds.toFloat()).coerceIn(0f, 1f)
                val barHeight = if (d.seconds <= 0) 0f else ratio * size.height
                val x = i * slot + (slot - barWidth) / 2
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, baseY - barHeight),
                    size = Size(barWidth, barHeight),
                )
            }
            // 基线
            drawLine(axisColor, Offset(0f, baseY), Offset(size.width, baseY), strokeWidth = 1f)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            daily.forEach { d ->
                val (m, day) = parseMonthDay(d.date)
                Text(
                    "$m/$day",
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 把 `yyyy-MM-dd` 拆成 (月, 日) 整数，用于柱状图标签。 */
private fun parseMonthDay(date: String): Pair<Int, Int> {
    val parts = date.split("-")
    return if (parts.size == 3) parts[1].toInt() to parts[2].toInt() else 0 to 0
}
