package com.xuziyue.ebook.reader.readium.typography

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TYPE-04 排版回归样本集 · 结构校验（JVM，CI 友好）。
 *
 * 样本集由 `scripts/gen_typography_fixtures.py` 生成，落 `samples/public/typography/`：
 * - `ruby.epub`      拼音注音（HTML5 `<ruby>/<rt>`）
 * - `rtl.epub`       RTL 阿拉伯文（`dir="rtl"` + OPF `page-progression-direction="rtl"`）
 * - `vertical.epub`  竖排（CSS `writing-mode: vertical-rl`）
 * - CJK 横排复用 `samples/public/chinese-shanhaijing.epub`
 *
 * 本测试只做**结构层**校验（解压 + 文本断言），锁定「样本集存在且排版标记完整、是合法 EPUB」，
 * 防 Readium 升级或样本被改坏时静默丢失特征。**视觉正确性（注音在上 / 右起 / 竖排走向）属真机肉眼回归**，
 * 见 `samples/public/typography/README.md` 清单，本测试不替代。
 *
 * 工作目录：`reader/readium` 模块 Test 任务 workingDir = rootProject.projectDir（见该模块 build.gradle.kts），
 * 故相对路径 `samples/public/...` 可解析。
 */
class TypographySamplesTest {

    @Test
    fun `ruby 样本含 ruby 注音标记且语言为 zh-CN`() {
        val entries = epubEntries("samples/public/typography/ruby.epub")
        assertValidEpub(entries)
        val all = entries.values.joinToString("") { String(it, UTF_8) }
        assertTrue("ruby 样本必须含 <ruby>", all.contains("<ruby>"))
        assertTrue("ruby 样本必须含 <rt>", all.contains("<rt>"))
        assertTrue("ruby 样本语言应为 zh-CN", opfText(entries).contains("<dc:language>zh-CN</dc:language>"))
    }

    @Test
    fun `rtl 样本声明 dir rtl 与 page-progression-direction rtl 且语言为 ar`() {
        val entries = epubEntries("samples/public/typography/rtl.epub")
        assertValidEpub(entries)
        val opf = opfText(entries)
        assertTrue("OPF spine 应声明 page-progression-direction=rtl", opf.contains("page-progression-direction=\"rtl\""))
        val anyRtlHtml = entries.values.any { String(it, UTF_8).contains("dir=\"rtl\"") }
        assertTrue("至少一个 XHTML 应声明 dir=rtl", anyRtlHtml)
        assertTrue("rtl 样本语言应为 ar", opf.contains("<dc:language>ar</dc:language>"))
    }

    @Test
    fun `vertical 样本含 writing-mode vertical-rl 且链接样式表`() {
        val entries = epubEntries("samples/public/typography/vertical.epub")
        assertValidEpub(entries)
        val hasVerticalCss = entries.values.any { String(it, UTF_8).contains("writing-mode: vertical-rl") }
        assertTrue("vertical 样本必须含 writing-mode: vertical-rl（CSS）", hasVerticalCss)
        assertNotNull("vertical 样本应含 style.css 条目", entries.entries.firstOrNull { it.key.endsWith("style.css") })
        val linksCss = entries.values.any { String(it, UTF_8).contains("href=\"style.css\"") }
        assertTrue("XHTML 应通过 <link> 引用 style.css", linksCss)
    }

    @Test
    fun `山海经样本为中文 CJK 且是合法 EPUB`() {
        val entries = epubEntries("samples/public/chinese-shanhaijing.epub")
        assertValidEpub(entries)
        val opf = opfText(entries)
        // Gutenberg EPUB2 带属性：<dc:language xsi:type="dcterms:RFC4646">zh</dc:language>，用正则兼容。
        assertTrue(
            "山海经 OPF 应声明 dc:language=zh",
            Regex("<dc:language[^>]*>zh</dc:language>").containsMatchIn(opf),
        )
    }

    // ===== 辅助 =====

    /** 解析 EPUB 全部条目为 路径→字节（仅文本断言用，二进制按 UTF-8 读不影响特征子串匹配）。 */
    private fun epubEntries(relativePath: String): Map<String, ByteArray> {
        val file = File(relativePath)
        assertTrue("样本文件应存在：$relativePath（samples/public 为 git 跟踪，CI 必有）", file.exists())
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(file.inputStream()).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                if (!e.isDirectory) entries[e.name] = zis.readBytes()
                zis.closeEntry()
            }
        }
        return entries
    }

    /** 读 container.xml 找 rootfile → 读 OPF 文本。 */
    private fun opfText(entries: Map<String, ByteArray>): String {
        val container = String(entries.getValue("META-INF/container.xml"), UTF_8)
        val rootfilePath = Regex("""full-path="([^"]+)"""").find(container)?.groupValues?.get(1)
            ?: error("container.xml 未声明 rootfile full-path")
        return String(entries.getValue(rootfilePath), UTF_8)
    }

    /** EPUB 合法性最低校验：mimetype 首项且内容正确、有 container.xml、有 OPF。 */
    private fun assertValidEpub(entries: Map<String, ByteArray>) {
        val names = entries.keys.toList()
        assertEquals("mimetype 必须是 ZIP 首项", "mimetype", names.first())
        assertEquals("mimetype 内容必须为 application/epub+zip", "application/epub+zip", String(entries.getValue("mimetype"), UTF_8).trim())
        assertNotNull("必须有 META-INF/container.xml", entries["META-INF/container.xml"])
        assertNotNull("OPF 路径必须可解析", runCatching { opfText(entries) }.getOrNull())
    }
}
