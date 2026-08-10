package com.xuziyue.ebook.reader.readium.txt

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 解析门面单测（用临时文件，验证 [TxtParser] 的三态结果 + 大小/空文件/异常防御）。
 */
class TxtParserTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    @Test
    fun `解析 UTF-8 BOM 文件成功`() {
        val file = tmp.newFile("a.txt")
        file.writeBytes(utf8Bom + "第1章 测试\n正文".toByteArray(StandardCharsets.UTF_8))

        val outcome = TxtParser().parse(file)

        assertTrue(outcome is TxtParseOutcome.Success)
        val book = (outcome as TxtParseOutcome.Success).book
        assertEquals(StandardCharsets.UTF_8, book.charset)
        assertTrue(book.hadBom)
        assertEquals(1, book.chapters.size)
        assertEquals("第1章 测试", book.chapters[0].title)
    }

    @Test
    fun `解析 GB18030 合成文件成功`() {
        val file = tmp.newFile("b.txt")
        file.writeBytes("第1章 测试\n正文".toByteArray(TxtEncodingDetector.GB18030))

        val outcome = TxtParser().parse(file)

        assertTrue(outcome is TxtParseOutcome.Success)
        assertEquals(
            TxtEncodingDetector.GB18030,
            (outcome as TxtParseOutcome.Success).book.charset,
        )
    }

    @Test
    fun `解析乱码返回需手选编码`() {
        val file = tmp.newFile("c.bin")
        file.writeBytes(ByteArray(4) { 0xFF.toByte() })

        val outcome = TxtParser().parse(file)

        assertTrue(outcome is TxtParseOutcome.NeedsEncodingChoice)
        assertTrue(
            (outcome as TxtParseOutcome.NeedsEncodingChoice).candidates.isNotEmpty(),
        )
    }

    @Test
    fun `超限文件报 FileTooLarge`() {
        val file = tmp.newFile("d.txt")
        file.writeBytes(ByteArray(200)) // 200 字节，上限设 100

        val outcome = TxtParser(TxtParserConfig(maxFileSizeBytes = 100)).parse(file)

        assertTrue(outcome is TxtParseOutcome.Failure)
        val error = (outcome as TxtParseOutcome.Failure).error
        assertTrue(error is TxtParseError.FileTooLarge)
        assertTrue("错误文案可读", error.message.contains("文件过大"))
    }

    @Test
    fun `空文件报 EmptyFile`() {
        val file = tmp.newFile("e.txt") // 0 字节

        val outcome = TxtParser().parse(file)

        assertTrue(outcome is TxtParseOutcome.Failure)
        val error = (outcome as TxtParseOutcome.Failure).error
        assertTrue(error is TxtParseError.EmptyFile)
        assertEquals("文件为空", error.message)
    }

    @Test
    fun `不存在文件报 FileNotFound`() {
        val outcome = TxtParser().parse(tmp.root.resolve("nope.txt"))

        assertTrue(outcome is TxtParseOutcome.Failure)
        assertTrue(
            (outcome as TxtParseOutcome.Failure).error is TxtParseError.FileNotFound,
        )
    }

    @Test
    fun `parseWithEncoding 强制 GB18030 解码`() {
        val file = tmp.newFile("f.txt")
        file.writeBytes("第1章 测试\n正文".toByteArray(TxtEncodingDetector.GB18030))

        val book = TxtParser().parseWithEncoding(file, TxtEncodingDetector.GB18030)

        assertEquals(TxtEncodingDetector.GB18030, book.charset)
        assertEquals(1, book.chapters.size)
        assertEquals("第1章 测试", book.chapters[0].title)
    }
}
