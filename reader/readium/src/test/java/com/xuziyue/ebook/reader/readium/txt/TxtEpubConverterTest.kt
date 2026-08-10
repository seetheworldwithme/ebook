package com.xuziyue.ebook.reader.readium.txt

import java.io.ByteArrayInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TxtEpubConverter] 单元测试。
 *
 * 生成 EPUB 字节 → 用 [ZipInputStream] 解开 → 逐项断言结构正确性。
 * 这是 P0V-04 方案 A 的关键风险点（生成 EPUB 必须被 Readium [EpubParser] 接受），
 * 覆盖 mimetype/章节数/container.xml/content.opf manifest+spine/nav epub:type/ncx navPoint/
 * 中文 UTF-8/XML 转义/单章兜底。构造 [TxtBook] 对象，不依赖文件 I/O。
 */
class TxtEpubConverterTest {

    private val converter = TxtEpubConverter()

    private fun book(chapters: List<TxtChapter>): TxtBook =
        TxtBook(charset = Charsets.UTF_8, hadBom = false, chapters = chapters, rawLength = 100L)

    /** 解开 EPUB 字节为「路径 → UTF-8 内容」映射。 */
    private fun unzip(bytes: ByteArray): Map<String, String> {
        val result = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                result[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        return result
    }

    @Test
    fun `mimetype 是首项且 STORED 且内容正确`() {
        val bytes = converter.convert(book(listOf(TxtChapter(0, "测试", "正文"))), "书名", "id-1")

        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            val first = zip.nextEntry
            assertEquals("mimetype", first.name)
            assertEquals("mimetype 必须 STORED", ZipEntry.STORED, first.method)
            val content = zip.readBytes().toString(Charsets.US_ASCII)
            assertEquals("application/epub+zip", content)
        }
    }

    @Test
    fun `生成的章节 xhtml 数等于 chapters 数`() {
        val chapters = listOf(
            TxtChapter(0, "简介", "简介正文"),
            TxtChapter(1, "第1章", "第1章正文"),
            TxtChapter(2, "第2章", "第2章正文"),
        )
        val entries = unzip(converter.convert(book(chapters), "书", "id"))

        val chapterCount = entries.keys.count { it.startsWith("OEBPS/chapter-") }
        assertEquals(3, chapterCount)
    }

    @Test
    fun `container_xml 含指向 content_opf 的 rootfile`() {
        val entries = unzip(converter.convert(book(listOf(TxtChapter(0, "x", "y"))), "书", "id"))

        val container = entries["META-INF/container.xml"]
        assertNotNull(container)
        assertTrue(container!!.contains("full-path=\"OEBPS/content.opf\""))
        assertTrue(container.contains("urn:oasis:names:tc:opendocument:xmlns:container"))
    }

    @Test
    fun `content_opf 含 manifest 每章 item 与 spine itemref 及 nav 标记`() {
        val chapters = listOf(TxtChapter(0, "简介", "x"), TxtChapter(1, "第1章", "y"))
        val opf = unzip(converter.convert(book(chapters), "书名", "id-abc"))["OEBPS/content.opf"]!!

        assertTrue("unique-identifier", opf.contains("unique-identifier=\"pub-id\""))
        assertTrue("dc:title", opf.contains("<dc:title>书名</dc:title>"))
        assertTrue("dc:identifier", opf.contains("<dc:identifier id=\"pub-id\">id-abc</dc:identifier>"))
        assertTrue("nav properties", opf.contains("properties=\"nav\""))
        assertTrue("manifest chap-0 href 含 .xhtml", opf.contains("<item id=\"chap-0\" href=\"chapter-0.xhtml\""))
        assertTrue("manifest chap-1 href 含 .xhtml", opf.contains("<item id=\"chap-1\" href=\"chapter-1.xhtml\""))
        assertTrue("spine chap-0", opf.contains("<itemref idref=\"chap-0\"/>"))
        assertTrue("spine chap-1", opf.contains("<itemref idref=\"chap-1\"/>"))
        assertTrue("OPF 命名空间", opf.contains("xmlns=\"http://www.idpf.org/2007/opf\""))
    }

    @Test
    fun `nav_xhtml 含 epub type toc 与每章链接`() {
        val chapters = listOf(TxtChapter(0, "简介", "x"), TxtChapter(1, "第1章", "y"))
        val nav = unzip(converter.convert(book(chapters), "书名", "id"))["OEBPS/nav.xhtml"]!!

        assertTrue("epub:type=toc", nav.contains("epub:type=\"toc\""))
        assertTrue("OPS 命名空间", nav.contains("xmlns:epub=\"http://www.idpf.org/2007/ops\""))
        assertTrue("chap-0 链接", nav.contains("href=\"chapter-0.xhtml\">简介"))
        assertTrue("chap-1 链接", nav.contains("href=\"chapter-1.xhtml\">第1章"))
    }

    @Test
    fun `toc_ncx 含每章 navPoint 且数量正确`() {
        val chapters = listOf(TxtChapter(0, "简介", "x"), TxtChapter(1, "第1章", "y"))
        val ncx = unzip(converter.convert(book(chapters), "书", "id"))["OEBPS/toc.ncx"]!!

        assertEquals(2, Regex("<navPoint ").findAll(ncx).count())
        assertTrue(ncx.contains("content src=\"chapter-0.xhtml\""))
        assertTrue(ncx.contains("content src=\"chapter-1.xhtml\""))
    }

    @Test
    fun `章节正文中文以 UTF-8 不乱码且按行段落化`() {
        val chapters = listOf(TxtChapter(0, "第一章", "大夏国 李洛\n第二行\n"))
        val ch = unzip(converter.convert(book(chapters), "书", "id"))["OEBPS/chapter-0.xhtml"]!!

        assertTrue("中文不乱码", ch.contains("大夏国"))
        assertTrue(ch.contains("李洛"))
        assertTrue("段落化", ch.contains("<p>大夏国 李洛</p>"))
        assertTrue("第二段", ch.contains("<p>第二行</p>"))
        assertTrue("XHTML 命名空间", ch.contains("xmlns=\"http://www.w3.org/1999/xhtml\""))
        assertTrue("XML 声明", ch.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
    }

    @Test
    fun `正文与标题特殊字符被 XML 转义`() {
        val chapters = listOf(TxtChapter(0, "章<题>", "a<b>&\"c"))
        val ch = unzip(converter.convert(book(chapters), "书", "id"))["OEBPS/chapter-0.xhtml"]!!

        assertTrue("标题转义", ch.contains("<h1>章&lt;题&gt;</h1>"))
        assertTrue("正文转义", ch.contains("<p>a&lt;b&gt;&amp;&quot;c</p>"))
    }

    @Test
    fun `单章也不抛异常且生成完整结构`() {
        val chapters = listOf(TxtChapter(0, "唯一章", "正文"))
        val entries = unzip(converter.convert(book(chapters), "书", "id"))

        assertNotNull(entries["OEBPS/chapter-0.xhtml"])
        assertNotNull(entries["OEBPS/content.opf"])
        assertNotNull(entries["OEBPS/nav.xhtml"])
        assertNotNull(entries["OEBPS/toc.ncx"])
        assertNotNull(entries["META-INF/container.xml"])
    }
}
