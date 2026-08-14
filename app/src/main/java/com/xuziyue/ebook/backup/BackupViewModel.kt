package com.xuziyue.ebook.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xuziyue.ebook.data.backup.BackupUseCase
import com.xuziyue.ebook.data.backup.RestoreUseCase
import com.xuziyue.ebook.ui.UserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * 备份与恢复页 ViewModel（DATA-03）。
 *
 * - [exporting]：导出 / 预览 / 恢复进行中（UI 进度条）。
 * - [exportEvents]：导出结果 Toast 反馈（照搬 DATA-01 exportEvents 范式）。
 * - [preview]：导入后调 [RestoreUseCase.preview] 产出冲突报告；[pendingPreview] 持有供对话框渲染。
 * - [restore]：用户选策略后执行 [RestoreUseCase.restore]。
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupUseCase: BackupUseCase,
    private val restoreUseCase: RestoreUseCase,
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    /** 待用户确认的恢复预览（导入后产出；null = 无待确认）。 */
    private val _pendingPreview = MutableStateFlow<RestoreUseCase.PreviewResult?>(null)
    val pendingPreview = _pendingPreview.asStateFlow()

    /** 恢复操作对应的源 URI（预览时暂存，restore 时用）。 */
    private var pendingUri: Uri? = null

    private val _events = Channel<BackupEvent>(Channel.BUFFERED)
    val events: Flow<BackupEvent> = _events.receiveAsFlow()

    /** 导出全量备份到 SAF 目标 [destUri]。 */
    fun export(destUri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            _events.trySend(BackupEvent.ExportResult(backupUseCase.backup(destUri)))
            _busy.value = false
        }
    }

    /** 导入备份并预览冲突（不写）。 */
    fun preview(srcUri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            pendingUri = srcUri
            val result = runCatching { restoreUseCase.preview(srcUri) }
            result.onSuccess { _pendingPreview.value = it }
                .onFailure { _events.trySend(BackupEvent.Failed(UserMessage.Raw(it.message ?: ""))) }
            _busy.value = false
        }
    }

    /** 用户确认策略后执行恢复。 */
    fun restore(strategy: RestoreUseCase.Strategy) {
        val uri = pendingUri ?: return
        viewModelScope.launch {
            _busy.value = true
            _pendingPreview.value = null
            _events.trySend(BackupEvent.RestoreResult(restoreUseCase.restore(uri, strategy)))
            _busy.value = false
        }
    }

    /** 取消预览（关闭对话框）。 */
    fun dismissPreview() {
        _pendingPreview.value = null
        pendingUri = null
    }
}

/** 备份 / 恢复事件（Toast 反馈）。 */
sealed class BackupEvent {
    data class ExportResult(val outcome: BackupUseCase.Outcome) : BackupEvent()
    data class RestoreResult(val outcome: RestoreUseCase.Outcome) : BackupEvent()
    data class Failed(val message: UserMessage) : BackupEvent()
}
