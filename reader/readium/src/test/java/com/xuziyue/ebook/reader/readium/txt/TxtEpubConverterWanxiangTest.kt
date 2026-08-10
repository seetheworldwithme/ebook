package com.xuziyue.ebook.reader.readium.txt

import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 《万相之王》（校对版）本地样本端到端转换测试。
 *
 * **版权书不入库**：`samples/local/` 被 `.gitignore` 屏蔽，样本只在徐先生本地存在。
 * 文件不存在时 [assumeTrue] 优雅跳过（CI 无该文件 → 跳过不报错）；存在时验证
 * 真实 10MB 大文件 txt → epub 的端到端正确性。
 */
class TxtEpubConverterWanxiangTest {

    private val sample = File("samples/local/《万相之王》（校对版）.txt")

    @Test
    fun `万相之王 转 epub - 章节数一致且 mimetype STORED 且首章中文不乱码`() {
        assumeTrue("本地样本不存在，跳过", sample.exists())

        val outcome = TxtParser().parse(sample)
        assertTrue("txt 解析应成功", outcome is TxtParseOutcome.Success)
        val book = (outcome as TxtParseOutcome.Success).book

        val bytes = TxtEpubConverter().convert(book, "万相之王", "wanxiang-test-id")

        // mimetype STORED 首项
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            val first = zip.nextEntry
            assertEquals("mimetype", first.name)
            assertEquals(ZipEntry.STORED, first.method)
        }

        // 章节数 = txt 解析出的章节数（动态值，避免硬编码漂移）
        val entries = unzip(bytes)
        val chapterCount = entries.keys.count { it.startsWith("OEBPS/chapter-") }
        assertEquals(book.chapters.size, chapterCount)

        // chapters[1] 是「第1章 我有三个相宫」，正文含「大夏国」→ chapter-1.xhtml 应含
        val firstChapter = entries["OEBPS/chapter-1.xhtml"]
        assertNotNull(firstChapter)
        assertTrue("首章中文 UTF-8 不乱码", firstChapter!!.contains("大夏国"))
    }

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
}
