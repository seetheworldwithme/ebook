package com.xuziyue.ebook.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PrivacyTip
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuziyue.ebook.R

/**
 * 设置页（design.md §4.6 SET-05）。
 *
 * 三个入口：隐私说明 / 开源许可证 / 崩溃日志开关。
 * 崩溃日志开关默认关（红线 #8：明确同意后才启用）；开启后若存在崩溃记录，显示「分享」按钮
 * （纯本地 ACTION_SEND 文本分享，无网络——红线 #8 + SET-04）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenFolderImport: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val crashLogEnabled by viewModel.crashLogEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 最近崩溃日志（开关开 + 有记录时才显示分享按钮）。
    val crashLog = remember(crashLogEnabled) { viewModel.latestCrashLog() }
    val shareText = stringResource(R.string.settings_crash_log_share)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(padding)) {
            // 隐私说明
            item {
                SettingsNavRow(
                    icon = Icons.Default.PrivacyTip,
                    iconDesc = stringResource(R.string.settings_privacy),
                    title = stringResource(R.string.settings_privacy),
                    onClick = onOpenPrivacy,
                )
                HorizontalDivider()
            }
            // 阅读统计（DATA-04）
            item {
                SettingsNavRow(
                    icon = Icons.Default.Insights,
                    iconDesc = stringResource(R.string.settings_statistics),
                    title = stringResource(R.string.settings_statistics),
                    onClick = onOpenStatistics,
                )
                HorizontalDivider()
            }
            // 备份与恢复（DATA-03）
            item {
                SettingsNavRow(
                    icon = Icons.Default.CloudSync,
                    iconDesc = stringResource(R.string.settings_backup),
                    title = stringResource(R.string.settings_backup),
                    onClick = onOpenBackup,
                )
                HorizontalDivider()
            }
            // 目录导入（IMP-06）
            item {
                SettingsNavRow(
                    icon = Icons.Default.FolderOpen,
                    iconDesc = stringResource(R.string.settings_folder_import),
                    title = stringResource(R.string.settings_folder_import),
                    onClick = onOpenFolderImport,
                )
                HorizontalDivider()
            }
            // 开源许可证
            item {
                SettingsNavRow(
                    icon = Icons.Default.Description,
                    iconDesc = stringResource(R.string.settings_licenses),
                    title = stringResource(R.string.settings_licenses),
                    onClick = onOpenLicenses,
                )
                HorizontalDivider()
            }
            // 崩溃日志开关
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        stringResource(R.string.settings_crash_log),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        stringResource(R.string.settings_crash_log_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = crashLogEnabled,
                                onValueChange = { viewModel.setCrashLogEnabled(it) },
                                role = Role.Switch,
                            )
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.settings_crash_log_enable))
                        Switch(checked = crashLogEnabled, onCheckedChange = null)
                    }
                    // 有崩溃记录时显示分享按钮（纯本地 ACTION_SEND，无网络）。
                    if (crashLogEnabled && crashLog != null && crashLog.exists()) {
                        TextButton(onClick = {
                            val content = runCatching { crashLog.readText() }.getOrDefault("")
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, shareText)
                                putExtra(Intent.EXTRA_TEXT, content)
                            }
                            context.startActivity(Intent.createChooser(intent, shareText))
                        }) {
                            Text(shareText)
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

/** 设置列表导航行：图标 + 标题，点击导航。 */
@Composable
private fun SettingsNavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconDesc: String,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = iconDesc, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
