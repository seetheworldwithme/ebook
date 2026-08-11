package com.xuziyue.ebook.reader

import com.xuziyue.ebook.model.ReaderTextAlign
import com.xuziyue.ebook.model.ReaderTheme
import com.xuziyue.ebook.model.ReaderTypography
import com.xuziyue.ebook.reader.readium.toEpubPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.robolectric.RobolectricTestRunner

/**
 * [toEpubPreferences] 映射单测。
 *
 * 放 app 模块（而非 :reader:readium）的原因：Readium [Theme] 枚举每个值带 color int，
 * `<clinit>` 调 `android.graphics.Color.parseColor()`，纯 JVM unit test 的 android.jar stub
 * 会抛 "not mocked"（与 P0V-01 `Locator.fromJSON` 用 android.net.Uri 同类问题）。
 * app 模块已配 Robolectric（sdk=34，见 BookDaoTest），提供真实 Color 实现。
 *
 * EpubPreferences init require 约束（Readium 源码）：fontWeight ∈ 0.0..2.5、fontSize/pageMargins/
 * paragraphSpacing/letterSpacing/typeScale/wordSpacing >= 0——测试值须在范围内。
 */
@OptIn(ExperimentalReadiumApi::class)
@RunWith(RobolectricTestRunner::class)
class TypographyMappingsTest {

    @Test
    fun `全 null 偏好映射为全 null 的 EpubPreferences（走引擎默认）`() {
        val prefs = ReaderTypography().toEpubPreferences(isSystemDark = false)

        assertNull(prefs.fontSize)
        assertNull(prefs.fontFamily)
        assertNull(prefs.fontWeight)
        assertNull(prefs.lineHeight)
        assertNull(prefs.paragraphSpacing)
        assertNull(prefs.pageMargins)
        assertNull(prefs.textAlign)
        assertNull(prefs.theme)
    }

    @Test
    fun `TYPE-01 各数值与字符串字段直映射`() {
        val typo = ReaderTypography(
            fontSize = 1.3,
            fontFamily = "sans-serif",
            fontWeight = 1.5, // Readium fontWeight 是 0.0–2.5 倍率，非 CSS 100–900
            lineHeight = 1.6,
            paragraphSpacing = 1.2,
            pageMargins = 2.0,
        )
        val prefs = typo.toEpubPreferences(isSystemDark = false)

        assertEquals(1.3, prefs.fontSize!!, 1e-9)
        assertEquals("sans-serif", prefs.fontFamily?.name)
        assertEquals(1.5, prefs.fontWeight!!, 1e-9)
        assertEquals(1.6, prefs.lineHeight!!, 1e-9)
        assertEquals(1.2, prefs.paragraphSpacing!!, 1e-9)
        assertEquals(2.0, prefs.pageMargins!!, 1e-9)
    }

    @Test
    fun `textAlign 映射 START 与 JUSTIFY`() {
        assertEquals(
            TextAlign.START,
            ReaderTypography(textAlign = ReaderTextAlign.START).toEpubPreferences(false).textAlign,
        )
        assertEquals(
            TextAlign.JUSTIFY,
            ReaderTypography(textAlign = ReaderTextAlign.JUSTIFY).toEpubPreferences(false).textAlign,
        )
    }

    @Test
    fun `显式 LIGHT_SEPIA_DARK 不受系统暗色影响`() {
        val cases = listOf(
            ReaderTheme.LIGHT to Theme.LIGHT,
            ReaderTheme.SEPIA to Theme.SEPIA,
            ReaderTheme.DARK to Theme.DARK,
        )
        for ((readerTheme, readiumTheme) in cases) {
            assertEquals(
                readiumTheme,
                ReaderTypography(theme = readerTheme).toEpubPreferences(isSystemDark = false).theme,
            )
            assertEquals(
                readiumTheme,
                ReaderTypography(theme = readerTheme).toEpubPreferences(isSystemDark = true).theme,
            )
        }
    }

    @Test
    fun `SYSTEM 跟随系统暗色解析为 DARK 或 LIGHT`() {
        assertEquals(
            Theme.DARK,
            ReaderTypography(theme = ReaderTheme.SYSTEM).toEpubPreferences(isSystemDark = true).theme,
        )
        assertEquals(
            Theme.LIGHT,
            ReaderTypography(theme = ReaderTheme.SYSTEM).toEpubPreferences(isSystemDark = false).theme,
        )
    }

    @Test
    fun `Default 偏好在系统暗色下解析为 DARK`() {
        // 首次默认 theme=SYSTEM，暗色环境应落到 DARK（防首次白屏的关键链路）。
        assertEquals(
            Theme.DARK,
            ReaderTypography.Default.toEpubPreferences(isSystemDark = true).theme,
        )
    }
}
