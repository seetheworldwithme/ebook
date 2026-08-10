package com.xuziyue.ebook.reader.readium.txt

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 《万相之王》（校对版）本地样本集成测试。
 *
 * **版权书不入库**：`samples/local/` 被 `.gitignore` 屏蔽，故本测试代码 committed
 * 但样本文件只在徐先生本地存在。文件不存在时 [assumeTrue] 优雅跳过（CI 无该文件 → 跳过，
 * 不报错）；文件存在时才跑，验证真实 10MB 大文件的端到端正确性（「用《万相之王》压」）。
 *
 * 注：测试 workingDir 由 `reader/readium/build.gradle.kts` 配为仓库根，
 * 故相对路径 `samples/local/...` 能正确解析。
 */
class WanxiangzhiwangLocalSampleTest {

    private val sample = File("samples/local/《万相之王》（校对版）.txt")

    @Test
    fun `编码探测为 GB18030 无 BOM`() {
        assumeTrue("本地样本不存在，跳过", sample.exists())

        val result = TxtEncodingDetector.detect(sample)

        assertTrue(result is TxtEncodingResult.Detected)
        result as TxtEncodingResult.Detected
        assertEquals("GB18030", result.charset.name())
        assertEquals(false, result.hadBom)
    }

    @Test
    fun `解析端到端 - 章节切分正确`() {
        assumeTrue("本地样本不存在，跳过", sample.exists())

        val outcome = TxtParser().parse(sample)

        assertTrue(outcome is TxtParseOutcome.Success)
        val book = (outcome as TxtParseOutcome.Success).book

        // front-matter（书名/作者头）单独成 index 0 的简介章
        assertTrue("应至少有 front-matter + 第1章", book.chapters.size > 1)
        assertEquals("万相之王", book.chapters[0].title)

        // 第1章内容
        val chapter1 = book.chapters[1]
        assertEquals("第1章 我有三个相宫", chapter1.title)
        assertTrue("首章正文非空", chapter1.body.isNotBlank())
        assertTrue("首章正文含首段地名「大夏国」", chapter1.body.contains("大夏国"))

        // 原始字节数（10_253_493）
        assertEquals(10_253_493L, book.rawLength)
    }
}
