package com.xuziyue.ebook.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 应用级设置的持久化仓库（design.md §4.6 SET-05）。
 *
 * 与 [ReaderTypographyRepository] / [ReaderDisplaySettingsRepository] 共享同一个全局 DataStore
 *（reader_settings.preferences_pb），用 `app_*` key 前缀避免与排版字段冲突。
 *
 * 目前仅持有一个开关：**崩溃日志**（红线 #8：仅在用户明确同意后启用，默认关闭）。
 * 该开关由 [com.xuziyue.ebook.EbookApp] 缓存到 @Volatile 字段，崩溃时同步读取决定是否落盘。
 */
class AppSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {

    /** 崩溃日志开关（默认 false——红线 #8：明确同意后才启用）。 */
    val crashLogEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_CRASH_LOG] ?: false }

    suspend fun setCrashLogEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_CRASH_LOG] = enabled }
    }

    private companion object {
        val KEY_CRASH_LOG = booleanPreferencesKey("app_crash_log_enabled")
    }
}
