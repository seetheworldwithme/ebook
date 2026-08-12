package com.xuziyue.ebook.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xuziyue.ebook.data.AppSettingsRepository
import com.xuziyue.ebook.log.CrashLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置页 ViewModel（design.md §4.6 SET-05）。
 *
 * 持有崩溃日志开关（DataStore 持久化，默认关——红线 #8：明确同意后才启用）
 * 与最近崩溃日志文件（供「分享崩溃日志」用）。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appSettings: AppSettingsRepository,
) : ViewModel() {

    /** 崩溃日志开关（默认 false）。 */
    val crashLogEnabled: StateFlow<Boolean> = appSettings.crashLogEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 设置崩溃日志开关。 */
    fun setCrashLogEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettings.setCrashLogEnabled(enabled) }
    }

    /** 取最近一条崩溃日志文件（无则 null）。 */
    fun latestCrashLog(): File? = CrashLogger.latestCrashLog(context)
}
