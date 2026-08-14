package com.xuziyue.ebook.backup

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuziyue.ebook.R
import com.xuziyue.ebook.data.backup.RestoreUseCase
import com.xuziyue.ebook.ui.resolve
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 备份与恢复页（DATA-03，范式照抄 [com.xuziyue.ebook.settings.SettingsScreen]）。
 *
 * 导出：SAF CreateDocument(application/zip)，默认文件名 `ebook-backup-{日期}.zip`。
 * 导入：SAF OpenDocument(application/zip) → 先 [BackupViewModel.preview] 弹恢复预览对话框
 * （冲突分类 + 三策略单选）→ 用户确认后 [BackupViewModel.restore]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val pendingPreview by viewModel.pendingPreview.collectAsStateWithLifecycle()

    val exportDesc = stringResource(R.string.backup_export_desc)
    val importDesc = stringResource(R.string.backup_import_desc)
    val successTemplate = stringResource(R.string.backup_export_success)
    val restoreSuccessTemplate = stringResource(R.string.backup_restore_success)
    val failedTemplate = stringResource(R.string.backup_failed)

    // SAF CreateDocument：导出 ZIP
    val createDoc = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let { viewModel.export(it) } }

    // SAF OpenDocument：导入 ZIP（先预览）
    val openDoc = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.preview(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.backup_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp)) {
            // 导出
            item {
                Text(stringResource(R.string.backup_export), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.size(8.dp))
                Text(exportDesc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(12.dp))
                Button(
                    onClick = {
                        val name = "ebook-backup-" + SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date()) + ".zip"
                        createDoc.launch(name)
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.backup_export))
                    }
                }
            }
            // 导入（恢复）
            item {
                Spacer(Modifier.size(24.dp))
                Text(stringResource(R.string.backup_import), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.size(8.dp))
                Text(importDesc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(12.dp))
                OutlinedButton(
                    onClick = { openDoc.launch(arrayOf("application/zip", "*/*")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.backup_import))
                    }
                }
            }
            if (busy) {
                item {
                    Spacer(Modifier.size(16.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.backup_exporting), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.size(4.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    // 恢复预览对话框
    val preview = pendingPreview
    if (preview != null) {
        RestorePreviewDialog(
            preview = preview,
            onConfirm = { strategy -> viewModel.restore(strategy) },
            onDismiss = { viewModel.dismissPreview() },
        )
    }

    // 统一事件 Toast
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is BackupEvent.ExportResult -> when (val o = event.outcome) {
                    is com.xuziyue.ebook.data.backup.BackupUseCase.Outcome.Success ->
                        Toast.makeText(context, successTemplate.format(o.bookCount, o.fileCount), Toast.LENGTH_SHORT).show()
                    is com.xuziyue.ebook.data.backup.BackupUseCase.Outcome.Failed ->
                        Toast.makeText(context, failedTemplate.format(o.message.resolve(context)), Toast.LENGTH_LONG).show()
                }
                is BackupEvent.RestoreResult -> when (val o = event.outcome) {
                    is RestoreUseCase.Outcome.Restored ->
                        Toast.makeText(context, restoreSuccessTemplate.format(o.newBooks, o.overwritten, o.skipped), Toast.LENGTH_LONG).show()
                    is RestoreUseCase.Outcome.Failed ->
                        Toast.makeText(context, failedTemplate.format(o.message.resolve(context)), Toast.LENGTH_LONG).show()
                }
                is BackupEvent.Failed ->
                    Toast.makeText(context, failedTemplate.format(event.message.resolve(context)), Toast.LENGTH_LONG).show()
            }
        }
    }
}

/** 恢复预览对话框：冲突汇总 + 三策略单选 + 恢复按钮。 */
@Composable
private fun RestorePreviewDialog(
    preview: RestoreUseCase.PreviewResult,
    onConfirm: (RestoreUseCase.Strategy) -> Unit,
    onDismiss: () -> Unit,
) {
    var strategy by remember { mutableStateOf(RestoreUseCase.Strategy.SKIP_CONFLICTS) }
    val strategies = listOf(
        Triple(RestoreUseCase.Strategy.SKIP_CONFLICTS, R.string.backup_strategy_skip, null),
        Triple(RestoreUseCase.Strategy.OVERWRITE_ALL, R.string.backup_strategy_overwrite, null),
        Triple(RestoreUseCase.Strategy.MERGE_KEEP_NEWER, R.string.backup_strategy_merge, null),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_preview_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.backup_preview_summary, preview.totalBooks, preview.newCount, preview.conflictCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.size(12.dp))
                strategies.forEach { (s, labelRes, _) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = strategy == s, onClick = { strategy = s })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = strategy == s, onClick = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(strategy) }) { Text(stringResource(R.string.backup_restore)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
