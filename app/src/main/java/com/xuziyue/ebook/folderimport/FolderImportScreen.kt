package com.xuziyue.ebook.folderimport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuziyue.ebook.R
import com.xuziyue.ebook.data.scan.ScanReport

/**
 * 目录导入页（IMP-06：用户授权指定目录并增量扫描）。
 *
 * 交互流：授权/更换目录（OpenDocumentTree，SAF 系统目录选择器）→ 立即扫描（或等冷启动自动扫描）
 * → 报告展示（新增 / 已在库 / 跳过 / 失败）。
 * 解除授权需确认（不影响已导入的书，只断开目录关联）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderImportScreen(
    onBack: () -> Unit,
    viewModel: FolderImportViewModel = hiltViewModel(),
) {
    val treeUri by viewModel.treeUri.collectAsStateWithLifecycle()
    val autoScan by viewModel.autoScan.collectAsStateWithLifecycle()
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()
    val lastReport by viewModel.lastReport.collectAsStateWithLifecycle()
    var showRevokeDialog by remember { mutableStateOf(false) }

    // SAF 目录选择器（红线 #3：不申请所有文件访问，只用 SAF 目录授权）。
    val pickDirectory = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) viewModel.onDirectoryPicked(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.folder_import_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // ── 授权目录 ──
            Text(
                stringResource(R.string.folder_import_directory),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp).semantics { heading() },
            )
            Spacer(Modifier.size(4.dp))
            Text(
                stringResource(R.string.folder_import_directory_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))
            if (treeUri == null) {
                // 未授权：只有一个主按钮
                Button(
                    onClick = { pickDirectory.launch(null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.folder_import_pick))
                }
            } else {
                // 已授权：显示目录 + 更换 + 解除
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = folderDisplayName(treeUri),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.size(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { pickDirectory.launch(null) },
                        modifier = Modifier.weight(1f),
                        enabled = !scanning,
                    ) {
                        Text(stringResource(R.string.folder_import_change))
                    }
                    OutlinedButton(
                        onClick = { showRevokeDialog = true },
                        modifier = Modifier.weight(1f),
                        enabled = !scanning,
                    ) {
                        Text(stringResource(R.string.folder_import_revoke))
                    }
                }
                // ── 扫描 ──
                Spacer(Modifier.size(24.dp))
                Text(
                    stringResource(R.string.folder_import_scan),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.size(12.dp))
                Button(
                    onClick = viewModel::scanNow,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !scanning,
                ) {
                    Text(
                        if (scanning) stringResource(R.string.folder_import_scanning)
                        else stringResource(R.string.folder_import_scan_now),
                    )
                }
                if (scanning) {
                    Spacer(Modifier.size(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                lastReport?.let { report ->
                    if (report is ScanReport) {
                        Spacer(Modifier.size(16.dp))
                        ScanReportCard(report)
                    }
                }
                // ── 自动扫描开关 ──
                Spacer(Modifier.size(24.dp))
            }
            HorizontalDivider()
            Spacer(Modifier.size(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = autoScan,
                        onValueChange = { viewModel.setAutoScan(it) },
                        role = Role.Switch,
                    )
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.folder_import_auto_scan))
                    Spacer(Modifier.size(2.dp))
                    Text(
                        stringResource(R.string.folder_import_auto_scan_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(checked = autoScan, onCheckedChange = null)
            }
            Spacer(Modifier.size(24.dp))
        }
    }

    // 解除授权确认框
    if (showRevokeDialog) {
        AlertDialog(
            onDismissRequest = { showRevokeDialog = false },
            title = { Text(stringResource(R.string.folder_import_revoke_title)) },
            text = { Text(stringResource(R.string.folder_import_revoke_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showRevokeDialog = false
                    viewModel.revokeDirectory()
                }) {
                    Text(stringResource(R.string.folder_import_revoke_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/** 扫描报告卡（新增 / 已在库 / 跳过 / 失败 + 截断提示）。 */
@Composable
private fun ScanReportCard(report: ScanReport) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            stringResource(R.string.folder_import_report_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.size(8.dp))
        ReportRow(stringResource(R.string.folder_import_report_imported), report.imported)
        ReportRow(stringResource(R.string.folder_import_report_exists), report.alreadyExists)
        ReportRow(stringResource(R.string.folder_import_report_skipped), report.skippedUnchanged)
        ReportRow(stringResource(R.string.folder_import_report_failed), report.failed)
        if (report.truncated) {
            Spacer(Modifier.size(4.dp))
            Text(
                stringResource(R.string.folder_import_report_truncated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** 报告行：标签 + 数字。 */
@Composable
private fun ReportRow(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text("$count", style = MaterialTheme.typography.bodyMedium)
    }
}

/** 从 tree Uri 提取可读目录名（最后一段 document id）。 */
private fun folderDisplayName(treeUri: String?): String {
    if (treeUri == null) return ""
    return runCatching {
        val docId = android.net.Uri.parse(treeUri)
            ?.getQueryParameter("documentId")
            ?: android.net.Uri.parse(treeUri)?.pathSegments?.lastOrNull()
        docId?.substringAfterLast(':') ?: treeUri
    }.getOrDefault(treeUri)
}
