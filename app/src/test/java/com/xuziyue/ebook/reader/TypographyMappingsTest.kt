package com.xuziyue.ebook.reader

import com.xuziyue.ebook.model.ReaderScrollMode
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

        // SET-03：fontSize 不再为 null——默认 systemFontScale=1f 时折算为 1.0（与 null 视觉等价，
        // ReadiumCSS 基础字号 × 1.0 = 基础字号）。其他字段仍为 null（引擎默认）。
        assertEquals(1.0, prefs.fontSize!!, 1e-9)
        assertNull(prefs.fontFamily)
        assertNull(prefs.fontWeight)
        assertNull(prefs.lineHeight)
        assertNull(prefs.paragraphSpacing)
        assertNull(prefs.pageMargins)
        assertNull(prefs.textAlign)
        assertNull(prefs.theme)
        assertNull(prefs.scroll) // READ-04
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
    fun `READ-04 scroll 映射 SCROLL 为 true、PAGINATED 为 false、null 为 null`() {
        assertEquals(
            true,
            ReaderTypography(scroll = ReaderScrollMode.SCROLL).toEpubPreferences(false).scroll,
        )
        assertEquals(
            false,
            ReaderTypography(scroll = ReaderScrollMode.PAGINATED).toEpubPreferences(false).scroll,
        )
        assertNull(ReaderTypography(scroll = null).toEpubPreferences(false).scroll)
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

    // ===== SET-03：正文跟随系统字号（fontSize × systemFontScale）=====

    @Test
    fun `SET-03 默认 systemFontScale 不改变 fontSize（向后兼容）`() {
        // 不传 systemFontScale（默认 1f）时行为与改前一致。
        assertEquals(1.3, ReaderTypography(fontSize = 1.3).toEpubPreferences(false).fontSize!!, 1e-9)
    }

    @Test
    fun `SET-03 fontSize=null 时正文直接跟随系统字号`() {
        // 滑块未设（引擎默认 1.0），系统字号 1.3 → 正文 1.3 倍。
        // delta=1e-5：systemFontScale 是 Float，乘入 Double 后存在 Float 精度差（~5e-8）。
        assertEquals(1.3, ReaderTypography(fontSize = null).toEpubPreferences(false, 1.3f).fontSize!!, 1e-5)
    }

    @Test
    fun `SET-03 滑块与系统字号相乘`() {
        // 滑块 2.0 × 系统 1.3 = 2.6（应用内微调叠在全局基线上）。
        assertEquals(2.6, ReaderTypography(fontSize = 2.0).toEpubPreferences(false, 1.3f).fontSize!!, 1e-5)
    }
}
