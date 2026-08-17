package com.xuziyue.ebook.reader.readium.formats

import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V1 PDF/CBZ 格式回归样本 · 结构校验（JVM，CI 友好）。
 *
 * 样本由 `scripts/gen_format_fixtures.py` 生成，落 `samples/public/formats/`：
 * - `minimal.pdf`  手写最小 3 页文本型 PDF
 * - `sample.cbz`   4 页彩色 PNG（DEFLATE ZIP）
 *
 * 本测试只做**结构层**校验（magic / xref / 页数 / ZIP 条目），锁定「样本集存在且合法」，
 * 防样本被改坏时真机回归静默失效。**阅读体验（翻页/缩放/进度）属真机回归**，
 * 见 `samples/public/formats/README.md` 清单，本测试不替代。
 *
 * 工作目录：`reader/readium` 模块 Test 任务 workingDir = rootProject.projectDir（见该模块 build.gradle.kts）。
 */
class FormatSamplesTest {

    @Test
    fun `PDF 样本 magic 与 xref 结构合法且含 3 页`() {
        val file = File("samples/public/formats/minimal.pdf")
        assertTrue("样本缺失，先跑 python3 scripts/gen_format_fixtures.py", file.exists())
        val bytes = file.readBytes()
        assertTrue("PDF magic 必须 %PDF-", String(bytes, 0, 5, Charsets.US_ASCII) == "%PDF-")
        val text = String(bytes, Charsets.US_ASCII)
        assertTrue("必须有 xref 表", text.contains("xref"))
        assertTrue("必须有 trailer + startxref", text.contains("trailer") && text.contains("startxref"))
        assertTrue("必须以 %%EOF 结尾", text.trimEnd().endsWith("%%EOF"))
        assertTrue("页树 Count 应为 3", Regex("/Count\\s+3").containsMatchIn(text))
        assertEquals("页对象应为 3 个", 3, Regex("/Type /Page \\D").findAll(text).count())
        assertTrue("内嵌文本对象应含页码标记", text.contains("Page 1"))
    }

    @Test
    fun `CBZ 样本是合法 ZIP 且含 4 张按序命名的 PNG`() {
        val file = File("samples/public/formats/sample.cbz")
        assertTrue("样本缺失，先跑 python3 scripts/gen_format_fixtures.py", file.exists())
        ZipFile(file).use { zf ->
            val names = zf.entries().toList().map { it.name }.sorted()
            assertEquals("应为 4 页", listOf("page-001.png", "page-002.png", "page-003.png", "page-004.png"), names)
            names.forEach { name ->
                val bytes = zf.getInputStream(zf.getEntry(name)).use { it.readBytes() }
                assertTrue("$name 必须是 PNG（magic 89PNG）", bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte())
            }
        }
    }
}
