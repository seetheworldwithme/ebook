package com.xuziyue.ebook.reader.readium.txt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 章节切分器单测（纯正则，无 I/O）。
 *
 * 重点验证：阿拉伯/中文/大写数字、特殊标题、front-matter、无标题兜底、
 * 行尾归一化、全角空格、正文里的「第三章」不误切（行首锚定的核心收益）。
 */
class TxtChapterSplitterTest {

    private val splitter = TxtChapterSplitter()

    @Test
    fun `阿拉伯数字章节`() {
        val text = "第1章 标题一\n正文一\n第2章 标题二\n正文二"
        val chapters = splitter.split(text)

        assertEquals(2, chapters.size)
        assertEquals("第1章 标题一", chapters[0].title)
        assertEquals("正文一", chapters[0].body)
        assertEquals("第2章 标题二", chapters[1].title)
        assertEquals("正文二", chapters[1].body)
    }

    @Test
    fun `中文数字章节`() {
        val text = "第一章 标题\n正文\n第十章 标题\n正文\n第一百二十三章 标题\n正文"
        val chapters = splitter.split(text)

        assertEquals(3, chapters.size)
        assertEquals("第一章 标题", chapters[0].title)
        assertEquals("第十章 标题", chapters[1].title)
        assertEquals("第一百二十三章 标题", chapters[2].title)
    }

    @Test
    fun `大写中文数字章节`() {
        val text = "第壹章 标题\n正文\n第拾贰章 标题\n正文"
        val chapters = splitter.split(text)

        assertEquals(2, chapters.size)
        assertEquals("第壹章 标题", chapters[0].title)
        assertEquals("第拾贰章 标题", chapters[1].title)
    }

    @Test
    fun `序章楔子番外等特殊标题`() {
        val text = "序章 引子\n正文A\n楔子\n正文B\n番外篇 额外\n正文C"
        val chapters = splitter.split(text)

        assertEquals(3, chapters.size)
        assertEquals("序章 引子", chapters[0].title)
        assertEquals("正文A", chapters[0].body)
        assertEquals("楔子", chapters[1].title)
        assertEquals("正文B", chapters[1].body)
        assertEquals("番外篇 额外", chapters[2].title)
        assertEquals("正文C", chapters[2].body)
    }

    @Test
    fun `无标题行兜底为单章`() {
        val text = "这是一段没有任何章节标题的纯文本内容。"
        val chapters = splitter.split(text)

        assertEquals(1, chapters.size)
        assertEquals("正文", chapters[0].title)
        assertEquals("这是一段没有任何章节标题的纯文本内容。", chapters[0].body)
    }

    @Test
    fun `front-matter 单独成简介章`() {
        val text = "万相之王\n作者:张三\n\n第1章 开端\n正文"
        val chapters = splitter.split(text)

        assertEquals(2, chapters.size)
        // index 0 = front-matter（书名作标题）
        assertEquals("万相之王", chapters[0].title)
        assertTrue("front-matter 正文含作者行", chapters[0].body.contains("作者:张三"))
        // index 1 = 第1章
        assertEquals("第1章 开端", chapters[1].title)
        assertEquals("正文", chapters[1].body)
    }

    @Test
    fun `首行即第1章则无 front-matter`() {
        val text = "第1章 开端\n正文"
        val chapters = splitter.split(text)

        assertEquals(1, chapters.size)
        assertEquals("第1章 开端", chapters[0].title)
    }

    @Test
    fun `CRLF 行尾归一化`() {
        val text = "第1章 A\r\n正文\r\n第2章 B\r\n正文"
        val chapters = splitter.split(text)

        assertEquals(2, chapters.size)
        assertEquals("正文", chapters[0].body)
        assertFalse("正文不应残留 \\r", chapters[0].body.contains("\r"))
    }

    @Test
    fun `全角空格缩进的行首标题`() {
        val text = "　　第1章 缩进标题\n　　正文内容"
        val chapters = splitter.split(text)

        assertEquals(1, chapters.size)
        assertEquals("第1章 缩进标题", chapters[0].title)
        assertEquals("正文内容", chapters[0].body)
    }

    @Test
    fun `正文含第三章不误切`() {
        val text = "第1章 标题\n他说第三章的内容很重要。\n第2章 标题二\n正文二"
        val chapters = splitter.split(text)

        // 行首锚定排除正文里的「第三章」→ 仅 2 个真实边界
        assertEquals(2, chapters.size)
        assertEquals("第1章 标题", chapters[0].title)
        assertEquals("第2章 标题二", chapters[1].title)
    }

    @Test
    fun `校对版书名章节粘连合并行不误切`() {
        // 校对版有时把书名前缀与章节标题粘连在一行（行首非「第」），属数据质量问题。
        // 行首锚定会漏匹配这类行——这是有意的「宁漏不误切」下限。
        val text = "第1章 A\n正文\n万相之王第2章 B 粘连正文\n正文"
        val chapters = splitter.split(text)

        assertEquals(1, chapters.size)
        assertEquals("第1章 A", chapters[0].title)
    }

    @Test
    fun `无分隔符标题不识别为边界（精度取舍）`() {
        // 「第1章标题一」标题与文字间无空白分隔符。当前正则要求标题前有空白分隔符，
        // 故不识别为边界，整篇兜底为单章——避免正文里行首「第X章…」被误切。
        // 与《万相之王》实测零误切一致（该样本所有标题均有空格分隔）。
        val text = "第1章标题一\n正文内容"
        val chapters = splitter.split(text)

        assertEquals(1, chapters.size)
        assertEquals("正文", chapters[0].title)
    }

    @Test
    fun `matchTitle 命中返回整行标题`() {
        assertEquals("第1章 测试", splitter.matchTitle("  第1章 测试  "))
        assertEquals("序章", splitter.matchTitle("序章"))
        assertEquals(null, splitter.matchTitle("这是正文"))
        assertEquals(null, splitter.matchTitle(""))
    }
}
