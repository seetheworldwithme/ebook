package com.xuziyue.ebook.data

import java.io.File
import com.xuziyue.ebook.data.scan.isSupported
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V1 PDF/CBZ 导入链路纯函数单测：
 * - [mediaTypeForExtension] 四格式 mediaType 派生；
 * - [ScanConfig][com.xuziyue.ebook.data.scan.ScanConfig] 扩展名白名单收 pdf/cbz。
 */
class ImportMediaTypeTest {

    @Test
    fun `mediaType 四格式派生`() {
        assertEquals("application/epub+zip", mediaTypeForExtension("epub"))
        assertEquals("text/plain", mediaTypeForExtension("txt"))
        assertEquals("application/pdf", mediaTypeForExtension("pdf"))
        assertEquals("application/vnd.comic+zip", mediaTypeForExtension("cbz"))
    }

    @Test
    fun `mediaType 大小写不敏感与未知兜底`() {
        assertEquals("application/pdf", mediaTypeForExtension("PDF"))
        assertEquals("application/vnd.comic+zip", mediaTypeForExtension("CBZ"))
        // 未知扩展名兜底 epub（importer 层无扩展名时按 .epub 落盘）。
        assertEquals("application/epub+zip", mediaTypeForExtension("weird"))
    }

    @Test
    fun `扫描白名单收 pdf 与 cbz（IMP-06 此前跳过 pdf 的行为反转）`() {
        val config = com.xuziyue.ebook.data.scan.ScanConfig.DEFAULT
        assertTrue(config.isSupported("book.epub"))
        assertTrue(config.isSupported("book.txt"))
        assertTrue(config.isSupported("book.pdf"))
        assertTrue(config.isSupported("book.cbz"))
        assertFalse(config.isSupported("book.mobi"))
        assertFalse(config.isSupported("book.cbr"))
        assertFalse(config.isSupported("noext"))
    }

    @Test
    fun `ZIP 校验适用面：合法 CBZ 能过校验器（cbz 与 epub 同为 ZIP 容器走同一预检）`() {
        // 造一个最小合法 CBZ（两张图 + STORED 即可过压缩比/条目限制）。
        val dir = File(System.getProperty("java.io.tmpdir"), "cbz-validate-test-${System.nanoTime()}")
        dir.mkdirs()
        try {
            val cbz = File(dir, "sample.cbz")
            java.util.zip.ZipOutputStream(cbz.outputStream()).use { zos ->
                zos.putNextEntry(java.util.zip.ZipEntry("001.png"))
                zos.write(ByteArray(1024) { it.toByte() })
                zos.closeEntry()
                zos.putNextEntry(java.util.zip.ZipEntry("002.png"))
                zos.write(ByteArray(1024) { (it + 1).toByte() })
                zos.closeEntry()
            }
            val validator = EpubSecurityValidator()
            assertTrue(validator.validate(cbz) is EpubSecurityResult.Safe)
        } finally {
            dir.deleteRecursively()
        }
    }
}
