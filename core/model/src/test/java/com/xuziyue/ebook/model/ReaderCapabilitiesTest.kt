package com.xuziyue.ebook.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 能力矩阵单测（design.md:348 要求单元测试覆盖能力矩阵）。
 *
 * 断言口径：EPUB 全能力 / PDF 浏览+搜索+书签无批注（issue #823）/ isSearchable 探针生效 /
 * TXT 经 EPUB Publication 推导等同 EPUB（红线 #2：不按扩展名单独算）/ EPUB 与 PDF 批注类字段严格不同（防回归）。
 */
class ReaderCapabilitiesTest {

    @Test
    fun `forEpub 默认全能力`() {
        val caps = ReaderCapabilities.forEpub()
        assertEquals(ReaderFormat.EPUB, caps.format)
        assertTrue(caps.canOpen)
        assertTrue(caps.canNavigate)
        assertTrue(caps.canToc)
        assertTrue(caps.canSearch)
        assertTrue(caps.canBookmark)
        assertTrue(caps.canRestorePosition)
        assertTrue(caps.canHighlight)
        assertTrue(caps.canAnnotate)
        assertTrue(caps.canCopyShare)
        assertTrue(caps.canTts)
        assertTrue(caps.canAdjustTypography)
        assertTrue(caps.canSwitchPagingMode)
    }

    @Test
    fun `forPdf 浏览_搜索_书签可用，文字选择类与 TTS 不支持`() {
        // issue #823：PDF 无原生文字选择/高亮/批注支持（design.md:48/130）
        val caps = ReaderCapabilities.forPdf()
        assertEquals(ReaderFormat.PDF, caps.format)
        // 浏览 / 搜索 / 书签可用
        assertTrue(caps.canOpen)
        assertTrue(caps.canNavigate)
        assertTrue(caps.canToc)
        assertTrue(caps.canSearch) // 判定标准 PDF=搜索；spec 意图为 true
        assertTrue(caps.canBookmark)
        assertTrue(caps.canRestorePosition)
        // 文字选择类与 TTS 不支持
        assertFalse(caps.canHighlight)
        assertFalse(caps.canAnnotate)
        assertFalse(caps.canCopyShare)
        assertFalse(caps.canTts)
        // 固定版式：排版控件不适用；但支持连续滚动/单页切换（scrollAxis）
        assertFalse(caps.canAdjustTypography)
        assertTrue(caps.canSwitchPagingMode)
    }

    @Test
    fun `forCbz 浏览_书签_恢复可用，目录搜索批注排版全不可用`() {
        val caps = ReaderCapabilities.forCbz()
        assertEquals(ReaderFormat.CBZ, caps.format)
        // 浏览 / 书签 / 恢复可用
        assertTrue(caps.canOpen)
        assertTrue(caps.canNavigate)
        assertTrue(caps.canBookmark)
        assertTrue(caps.canRestorePosition)
        // 漫画图片序列：目录（无 outline）/ 搜索 / 批注 / TTS 全不可用
        assertFalse(caps.canToc)
        assertFalse(caps.canSearch)
        assertFalse(caps.canHighlight)
        assertFalse(caps.canAnnotate)
        assertFalse(caps.canCopyShare)
        assertFalse(caps.canTts)
        // ImageNavigator 无 Configurable：排版与翻页方式均不可调
        assertFalse(caps.canAdjustTypography)
        assertFalse(caps.canSwitchPagingMode)
    }

    @Test
    fun `isSearchable 探针生效`() {
        // EPUB 搜索探针
        assertFalse(ReaderCapabilities.from(ReaderFormat.EPUB, isSearchable = false).canSearch)
        assertTrue(ReaderCapabilities.from(ReaderFormat.EPUB, isSearchable = true).canSearch)
        // PDF 搜索探针（V1 真开 PDF 时按 isSearchable 实测）
        assertFalse(ReaderCapabilities.from(ReaderFormat.PDF, isSearchable = false).canSearch)
        assertTrue(ReaderCapabilities.from(ReaderFormat.PDF, isSearchable = true).canSearch)
    }

    @Test
    fun `TXT 经 EPUB Publication 推导后能力等同 EPUB`() {
        // TXT 转 EPUB 后 conformsTo(EPUB)=true → from(EPUB, ...)，
        // 能力层无需也不应按 .txt 扩展名单独算（红线 #2）。
        assertEquals(
            ReaderCapabilities.forEpub(),
            ReaderCapabilities.from(ReaderFormat.EPUB, isSearchable = true),
        )
    }

    @Test
    fun `EPUB 与 PDF 批注类字段严格不同（防回归）`() {
        val epub = ReaderCapabilities.forEpub()
        val pdf = ReaderCapabilities.forPdf()
        // 批注类字段：EPUB 全 true / PDF 全 false；任一回退都会让此断言失败。
        assertTrue(epub.canHighlight && epub.canAnnotate && epub.canCopyShare && epub.canTts)
        assertFalse(pdf.canHighlight || pdf.canAnnotate || pdf.canCopyShare || pdf.canTts)
    }
}
