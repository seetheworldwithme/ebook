package com.xuziyue.ebook.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.xuziyue.ebook.model.ReaderScrollMode
import com.xuziyue.ebook.model.ReaderTextAlign
import com.xuziyue.ebook.model.ReaderTheme
import com.xuziyue.ebook.model.ReaderTypography
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 阅读排版偏好的持久化仓库（design.md §4.4 TYPE-01/02）。
 *
 * 包装全局 [DataStore]（reader_settings.preferences_pb），把 [ReaderTypography] 各字段
 * 双向映射到 preference keys：
 * - [observe] 暴露 Flow，订阅即拿到当前持久化值；UI / VM 据此驱动 Readium 偏好。
 * - [update] 在协程里原子读写（基于当前值做变换），用于各排版 setter。
 *
 * null 字段不写入（读取返回 null = 走引擎默认）。**theme 例外**：未设时返回 [ReaderTheme.SYSTEM]
 * （跟随系统是产品默认），保证「首次打开即跟随系统」且不随其它字段写入而漂移。
 *
 * 全局偏好（跨书共享）；按书保存是 P1 的 TYPE-05，本仓库不含 bookId 维度。
 */
class ReaderTypographyRepository(
    private val dataStore: DataStore<Preferences>,
) {

    fun observe(): Flow<ReaderTypography> = dataStore.data.map { it.toTypography() }

    /** 基于当前持久化值原子修改（DataStore.edit 保证单写） */
    suspend fun update(transform: (ReaderTypography) -> ReaderTypography) {
        dataStore.edit { mutable -> mutable.writeTypography(transform(mutable.toTypography())) }
    }

    private fun Preferences.toTypography(): ReaderTypography {
        val theme = this[KEY_THEME]?.let { runCatching { ReaderTheme.valueOf(it) }.getOrNull() }
        return ReaderTypography(
            fontSize = this[KEY_FONT_SIZE],
            fontFamily = this[KEY_FONT_FAMILY],
            fontWeight = this[KEY_FONT_WEIGHT],
            lineHeight = this[KEY_LINE_HEIGHT],
            paragraphSpacing = this[KEY_PARAGRAPH_SPACING],
            pageMargins = this[KEY_PAGE_MARGINS],
            // 枚举用 name 存取；未来改名时 valueOf 失败 → null（降级为引擎默认），不崩。
            textAlign = this[KEY_TEXT_ALIGN]?.let { runCatching { ReaderTextAlign.valueOf(it) }.getOrNull() },
            scroll = this[KEY_SCROLL]?.let { runCatching { ReaderScrollMode.valueOf(it) }.getOrNull() },
            // theme 未设默认跟随系统（产品默认，design.md §4.4 TYPE-02）。
            theme = theme ?: ReaderTheme.SYSTEM,
        )
    }

    private fun MutablePreferences.writeTypography(t: ReaderTypography) {
        putDoubleOrNull(KEY_FONT_SIZE, t.fontSize)
        putStringOrNull(KEY_FONT_FAMILY, t.fontFamily)
        putDoubleOrNull(KEY_FONT_WEIGHT, t.fontWeight)
        putDoubleOrNull(KEY_LINE_HEIGHT, t.lineHeight)
        putDoubleOrNull(KEY_PARAGRAPH_SPACING, t.paragraphSpacing)
        putDoubleOrNull(KEY_PAGE_MARGINS, t.pageMargins)
        putStringOrNull(KEY_TEXT_ALIGN, t.textAlign?.name)
        putStringOrNull(KEY_SCROLL, t.scroll?.name)
        putStringOrNull(KEY_THEME, t.theme?.name)
    }

    private fun MutablePreferences.putDoubleOrNull(key: Preferences.Key<Double>, value: Double?) {
        if (value == null) remove(key) else this[key] = value
    }

    private fun MutablePreferences.putStringOrNull(key: Preferences.Key<String>, value: String?) {
        if (value == null) remove(key) else this[key] = value
    }

    private companion object {
        val KEY_FONT_SIZE = doublePreferencesKey("font_size")
        val KEY_FONT_FAMILY = stringPreferencesKey("font_family")
        val KEY_FONT_WEIGHT = doublePreferencesKey("font_weight")
        val KEY_LINE_HEIGHT = doublePreferencesKey("line_height")
        val KEY_PARAGRAPH_SPACING = doublePreferencesKey("paragraph_spacing")
        val KEY_PAGE_MARGINS = doublePreferencesKey("page_margins")
        val KEY_TEXT_ALIGN = stringPreferencesKey("text_align")
        val KEY_SCROLL = stringPreferencesKey("scroll")
        val KEY_THEME = stringPreferencesKey("theme")
    }
}
