package com.xuziyue.ebook.reader.readium.pdf

import com.xuziyue.ebook.model.ReaderScrollMode
import com.xuziyue.ebook.model.ReaderTypography
import com.xuziyue.ebook.reader.readium.toPdfiumPreferences
import org.junit.Assert.assertEquals
import org.junit.Test
import org.readium.r2.navigator.preferences.Axis

/**
 * PDF 偏好映射单测（V1 PDF 基础浏览）。
 *
 * 断言口径：翻页方式是唯一映射字段（scroll→scrollAxis）；
 * 未设置（null）与 PAGINATED 都按单页横滑（引擎默认方向）。
 */
class PdfPreferencesMappingsTest {

    @Test
    fun `scroll 模式映射为纵向连续滚动`() {
        val prefs = ReaderTypography.Default.copy(scroll = ReaderScrollMode.SCROLL)
            .toPdfiumPreferences()
        assertEquals(Axis.VERTICAL, prefs.scrollAxis)
    }

    @Test
    fun `分页模式映射为横向单页`() {
        val prefs = ReaderTypography.Default.copy(scroll = ReaderScrollMode.PAGINATED)
            .toPdfiumPreferences()
        assertEquals(Axis.HORIZONTAL, prefs.scrollAxis)
    }

    @Test
    fun `未设置 scroll 时默认单页横滑`() {
        // ReaderTypography.Default.scroll == null（未设置跟引擎默认）
        val prefs = ReaderTypography.Default.toPdfiumPreferences()
        assertEquals(Axis.HORIZONTAL, prefs.scrollAxis)
    }
}
