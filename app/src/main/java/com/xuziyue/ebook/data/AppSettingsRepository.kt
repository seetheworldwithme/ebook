package com.xuziyue.ebook.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 应用级设置的持久化仓库（design.md §4.6 SET-05 / DATA-04 / IMP-06）。
 *
 * 与 [ReaderTypographyRepository] / [ReaderDisplaySettingsRepository] 共享同一个全局 DataStore
 *（reader_settings.preferences_pb），用 `app_*` key 前缀避免与排版字段冲突。
 *
 * 持有：
 * - **崩溃日志**开关（红线 #8：仅在用户明确同意后启用，默认关闭）。由 [com.xuziyue.ebook.EbookApp]
 *   缓存到 @Volatile 字段，崩溃时同步读取决定是否落盘。
 * - **阅读统计**开关（DATA-04：默认开——统计对自用有价值；用户可关 / 清空）。
 * - **目录导入**（IMP-06）：授权目录 tree Uri（null=未授权）+ 冷启动自动扫描开关（默认 true）。
 */
class AppSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {

    /** 崩溃日志开关（默认 false——红线 #8：明确同意后才启用）。 */
    val crashLogEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_CRASH_LOG] ?: false }

    suspend fun setCrashLogEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_CRASH_LOG] = enabled }
    }

    /** 阅读统计开关（默认 true——DATA-04，对自用有价值；关时阅读器不创建会话、不计时）。 */
    val readingStatsEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_READING_STATS] ?: true }

    suspend fun setReadingStatsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_READING_STATS] = enabled }
    }

    /** 目录导入授权的 tree Uri（IMP-06；null=未授权）。 */
    val importTreeUri: Flow<String?> = dataStore.data.map { it[KEY_IMPORT_TREE_URI] }

    suspend fun setImportTreeUri(uri: String?) {
        dataStore.edit {
            if (uri == null) it.remove(KEY_IMPORT_TREE_URI) else it[KEY_IMPORT_TREE_URI] = uri
        }
    }

    /** 冷启动自动扫描开关（IMP-06；默认 true——授权后每次启动静默增量扫描）。 */
    val importAutoScan: Flow<Boolean> = dataStore.data.map { it[KEY_IMPORT_AUTO_SCAN] ?: true }

    suspend fun setImportAutoScan(enabled: Boolean) {
        dataStore.edit { it[KEY_IMPORT_AUTO_SCAN] = enabled }
    }

    private companion object {
        val KEY_CRASH_LOG = booleanPreferencesKey("app_crash_log_enabled")
        val KEY_READING_STATS = booleanPreferencesKey("app_reading_stats_enabled")
        val KEY_IMPORT_TREE_URI = stringPreferencesKey("app_import_tree_uri")
        val KEY_IMPORT_AUTO_SCAN = booleanPreferencesKey("app_import_auto_scan")
    }
}
