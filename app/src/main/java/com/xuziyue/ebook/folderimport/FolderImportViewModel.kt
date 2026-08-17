package com.xuziyue.ebook.folderimport

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xuziyue.ebook.data.AppSettingsRepository
import com.xuziyue.ebook.data.scan.ScanDirectoryUseCase
import com.xuziyue.ebook.data.scan.ScanReport
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 目录导入 ViewModel（IMP-06）。
 *
 * 持有授权目录 tree Uri + 自动扫描开关（[AppSettingsRepository] 持久化），
 * 触发手动扫描（[ScanDirectoryUseCase]）；扫描结果经 [scanEvents] 一次性发给 UI Toast。
 */
@HiltViewModel
class FolderImportViewModel @Inject constructor(
    private val scanUseCase: ScanDirectoryUseCase,
    private val appSettings: AppSettingsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** 授权目录 tree Uri（null=未授权）。 */
    val treeUri: StateFlow<String?> = appSettings.importTreeUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 冷启动自动扫描开关。 */
    val autoScan: StateFlow<Boolean> = appSettings.importAutoScan
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** 扫描中标记（进度条）。 */
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    /** 最近一次扫描报告（null=还没扫过）。 */
    private val _lastReport = MutableStateFlow<ScanReport?>(null)
    val lastReport: StateFlow<ScanReport?> = _lastReport

    /**
     * 用户在 SAF 目录选择器选定目录后的回调：
     * 释放旧授权（如有）→ takePersistableUriPermission 跨重启保活 → 持久化 tree Uri。
     */
    fun onDirectoryPicked(uri: Uri) {
        viewModelScope.launch {
            // 释放旧授权（若换了目录），再对新目录 take 持久授权。
            releasePersistedPermission()
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            appSettings.setImportTreeUri(uri.toString())
        }
    }

    /** 解除授权：release + 清空持久化 Uri（import_sources 记录保留，重扫时按新目录重建）。 */
    fun revokeDirectory() {
        viewModelScope.launch {
            releasePersistedPermission()
            appSettings.setImportTreeUri(null)
        }
    }

    /** 切换自动扫描开关。 */
    fun setAutoScan(enabled: Boolean) {
        viewModelScope.launch { appSettings.setImportAutoScan(enabled) }
    }

    /** 立即扫描授权目录。 */
    fun scanNow() {
        val uriStr = treeUri.value ?: return
        viewModelScope.launch {
            _scanning.value = true
            val report = runCatching { scanUseCase.scan(Uri.parse(uriStr)) }.getOrNull()
            _scanning.value = false
            _lastReport.value = report
        }
    }

    /** 释放当前持久授权（无授权时 no-op，失败静默——授权可能已被系统清掉）。 */
    private suspend fun releasePersistedPermission() {
        val uriStr = appSettings.importTreeUri.firstOrNull() ?: return
        val uri = runCatching { Uri.parse(uriStr) }.getOrNull() ?: return
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
}
