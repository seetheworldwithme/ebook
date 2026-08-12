package com.xuziyue.ebook.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.xuziyue.ebook.model.ReaderDisplaySettings
import com.xuziyue.ebook.model.ReaderOrientation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 阅读显示/环境设置的持久化仓库（design.md §4.4 TYPE-03）。
 *
 * 与 [ReaderTypographyRepository] 共享同一个全局 DataStore（reader_settings.preferences_pb），
 * 但用独立的 key 前缀（display_*）避免与排版字段冲突。
 *
 * 包装 [ReaderDisplaySettings] 双向映射到 preference keys：
 * - [observe] 暴露 Flow，订阅即拿到当前持久化值；ReaderScreen 据此 apply 到 Window。
 * - [update] 在协程里原子读写（基于当前值做变换），用于各 setter。
 *
 * null 字段（brightness / orientation）不写入（读取返回 null = 跟随系统）；
 * keepScreenOn 非 nullable，未设取 false（产品默认不常亮）。
 */
class ReaderDisplaySettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {

    fun observe(): Flow<ReaderDisplaySettings> = dataStore.data.map { it.toDisplaySettings() }

    /** 基于当前持久化值原子修改（DataStore.edit 保证单写） */
    suspend fun update(transform: (ReaderDisplaySettings) -> ReaderDisplaySettings) {
        dataStore.edit { mutable ->
            mutable.writeDisplaySettings(transform(mutable.toDisplaySettings()))
        }
    }

    private fun Preferences.toDisplaySettings(): ReaderDisplaySettings {
        val orientation = this[KEY_ORIENTATION]
            ?.let { runCatching { ReaderOrientation.valueOf(it) }.getOrNull() }
        return ReaderDisplaySettings(
            brightness = this[KEY_BRIGHTNESS],
            // TYPE-03：常亮开关，未设取 false（产品默认不常亮）。
            keepScreenOn = this@toDisplaySettings[KEY_KEEP_SCREEN_ON] ?: false,
            orientation = orientation,
        )
    }

    private fun MutablePreferences.writeDisplaySettings(d: ReaderDisplaySettings) {
        putFloatOrNull(KEY_BRIGHTNESS, d.brightness)
        // TYPE-03：非 nullable 布尔，用 set operator 写入。
        this[KEY_KEEP_SCREEN_ON] = d.keepScreenOn
        putStringOrNull(KEY_ORIENTATION, d.orientation?.name)
    }

    private fun MutablePreferences.putFloatOrNull(key: Preferences.Key<Float>, value: Float?) {
        if (value == null) remove(key) else this[key] = value
    }

    private fun MutablePreferences.putStringOrNull(key: Preferences.Key<String>, value: String?) {
        if (value == null) remove(key) else this[key] = value
    }

    private companion object {
        val KEY_BRIGHTNESS = floatPreferencesKey("display_brightness")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("display_keep_screen_on")
        val KEY_ORIENTATION = stringPreferencesKey("display_orientation")
    }
}
