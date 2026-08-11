package com.xuziyue.ebook.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [ReaderTypography] 引擎无关模型单测：默认值、不可变性、枚举可读性。
 *
 * 映射逻辑（→ Readium EpubPreferences）的单测在 :reader:readium 模块
 * （`TypographyMappingsTest`，因依赖 Readium 类型）。
 */
class ReaderTypographyTest {

    @Test
    fun `Default 主题为 SYSTEM 跟随系统，其余字段为 null 走引擎默认`() {
        val d = ReaderTypography.Default

        assertEquals(ReaderTheme.SYSTEM, d.theme)
        assertNull(d.fontSize)
        assertNull(d.fontFamily)
        assertNull(d.fontWeight)
        assertNull(d.lineHeight)
        assertNull(d.paragraphSpacing)
        assertNull(d.pageMargins)
        assertNull(d.textAlign)
    }

    @Test
    fun `copy 修改单字段后其余字段保持`() {
        val base = ReaderTypography.Default
        val tuned = base.copy(fontSize = 1.2, lineHeight = 1.6)

        assertEquals(1.2, tuned.fontSize!!, 1e-9)
        assertEquals(1.6, tuned.lineHeight!!, 1e-9)
        // 未动字段保留
        assertEquals(ReaderTheme.SYSTEM, tuned.theme)
        assertNull(tuned.pageMargins)
    }

    @Test
    fun `ReaderTheme 与 ReaderTextAlign 的 name 稳定，可作持久化 key`() {
        // 持久化层用 enum.name 存取，name 不可随意改名（会破坏已落盘偏好）。
        assertEquals("LIGHT", ReaderTheme.LIGHT.name)
        assertEquals("SEPIA", ReaderTheme.SEPIA.name)
        assertEquals("DARK", ReaderTheme.DARK.name)
        assertEquals("SYSTEM", ReaderTheme.SYSTEM.name)
        assertEquals("START", ReaderTextAlign.START.name)
        assertEquals("JUSTIFY", ReaderTextAlign.JUSTIFY.name)
    }

    @Test
    fun `ReaderTheme valueOf 可从 name 还原`() {
        // 读回持久化值时用 valueOf(name)。
        assertEquals(ReaderTheme.SYSTEM, ReaderTheme.valueOf("SYSTEM"))
        assertEquals(ReaderTextAlign.JUSTIFY, ReaderTextAlign.valueOf("JUSTIFY"))
    }
}
