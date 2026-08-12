package com.xuziyue.ebook.data

import java.io.File
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * EPUB 安全校验器单测（CLAUDE.md 强制：导入安全用例 Zip Slip / 压缩炸弹 / 损坏文件 / 空间不足 必须覆盖）。
 *
 * 用 [ZipOutputStream] 程序化生成 ZIP 夹具（自包含、无外部依赖）。
 * 限制超限用低 [EpubSecurityConfig] 阈值精确触发，无需创建大文件。
 */
class EpubSecurityValidatorTest {

    private val validator = EpubSecurityValidator()
    private lateinit var tempDir: File

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "epub-security-test-${System.nanoTime()}")
        tempDir.mkdirs()
    }

    @After
    fun teardown() {
        tempDir.deleteRecursively()
    }

    // ===== Helper =====

    private fun newFile(name: String): File = File(tempDir, name).apply { createNewFile() }

    /** 创建 ZIP 文件（默认 DEFLATE BEST_COMPRESSION）。 */
    private fun createZip(
        file: File,
        entries: Map<String, ByteArray>,
        level: Int = Deflater.BEST_COMPRESSION,
    ) {
        ZipOutputStream(file.outputStream()).use { zos ->
            zos.setLevel(level)
            entries.forEach { (name, data) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }
        }
    }

    /** 创建合法最小 EPUB 结构（安全条目名 + 合理大小）。 */
    private fun createValidEpub(file: File) {
        createZip(
            file,
            mapOf(
                "mimetype" to "application/epub+zip".toByteArray(),
                "META-INF/container.xml" to "<container/>".toByteArray(),
                "OEBPS/content.xhtml" to "<html><body>Hello</body></html>".toByteArray(),
            ),
        )
    }

    private fun unsafeError(result: EpubSecurityResult): EpubSecurityError {
        assertTrue("期望 Unsafe 但得到 $result", result is EpubSecurityResult.Unsafe)
        return (result as EpubSecurityResult.Unsafe).error
    }

    // ===== 正常用例 =====

    @Test
    fun `合法最小 EPUB 通过校验`() {
        val zip = newFile("valid.epub")
        createValidEpub(zip)
        assertTrue(validator.validate(zip) is EpubSecurityResult.Safe)
    }

    // ===== Zip Slip（CLAUDE.md 强制场景 1）=====

    @Test
    fun `Zip Slip - 相对路径遍历`() {
        val zip = newFile("slip1.epub")
        createZip(zip, mapOf("../evil.txt" to "malware".toByteArray()))
        val error = unsafeError(validator.validate(zip))
        assertTrue(error is EpubSecurityError.ZipSlip)
    }

    @Test
    fun `Zip Slip - 绝对路径`() {
        val zip = newFile("slip2.epub")
        createZip(zip, mapOf("/etc/passwd" to "malware".toByteArray()))
        val error = unsafeError(validator.validate(zip))
        assertTrue(error is EpubSecurityError.ZipSlip)
    }

    @Test
    fun `Zip Slip - 嵌套遍历逃逸`() {
        val zip = newFile("slip3.epub")
        createZip(zip, mapOf("foo/../../evil.txt" to "malware".toByteArray()))
        val error = unsafeError(validator.validate(zip))
        assertTrue(error is EpubSecurityError.ZipSlip)
    }

    @Test
    fun `Zip Slip - Windows 反斜杠`() {
        val zip = newFile("slip4.epub")
        createZip(zip, mapOf("..\\evil.txt" to "malware".toByteArray()))
        val error = unsafeError(validator.validate(zip))
        assertTrue(error is EpubSecurityError.ZipSlip)
    }

    // ===== 压缩炸弹（CLAUDE.md 强制场景 2）=====

    @Test
    fun `压缩炸弹 - 全零高压缩比`() {
        val zip = newFile("bomb.epub")
        // 1MB 全零，DEFLATE 压缩比 ~900:1（远超 103:1 限制）
        createZip(zip, mapOf("zeros.bin" to ByteArray(1_000_000)))
        val error = unsafeError(validator.validate(zip))
        assertTrue(error is EpubSecurityError.CompressionBomb)
    }

    @Test
    fun `压缩炸弹 - 解压总量超限`() {
        val strictValidator = EpubSecurityValidator(
            EpubSecurityConfig(maxTotalUncompressedSize = 100),
        )
        val zip = newFile("big.epub")
        // 2 条目各 60 字节 = 120 > 100
        createZip(zip, mapOf("a.txt" to ByteArray(60), "b.txt" to ByteArray(60)))
        val error = unsafeError(strictValidator.validate(zip))
        assertTrue(error is EpubSecurityError.TotalSizeExceeded)
    }

    @Test
    fun `压缩炸弹 - 单条目超限`() {
        val strictValidator = EpubSecurityValidator(
            EpubSecurityConfig(maxSingleEntrySize = 50),
        )
        val zip = newFile("entry.epub")
        createZip(zip, mapOf("big.txt" to ByteArray(60)))
        val error = unsafeError(strictValidator.validate(zip))
        assertTrue(error is EpubSecurityError.EntryTooLarge)
    }

    @Test
    fun `压缩炸弹 - 条目数超限`() {
        val strictValidator = EpubSecurityValidator(
            EpubSecurityConfig(maxEntryCount = 3),
        )
        val zip = newFile("many.epub")
        createZip(zip, mapOf(
            "a.txt" to "a".toByteArray(),
            "b.txt" to "b".toByteArray(),
            "c.txt" to "c".toByteArray(),
            "d.txt" to "d".toByteArray(),
        ))
        val error = unsafeError(strictValidator.validate(zip))
        assertTrue(error is EpubSecurityError.TooManyEntries)
    }

    // ===== 损坏文件（CLAUDE.md 强制场景 3）=====

    @Test
    fun `损坏文件 - 垃圾字节`() {
        val zip = newFile("garbage.epub")
        zip.writeBytes(ByteArray(100) { (it % 256).toByte() })
        val error = unsafeError(validator.validate(zip))
        assertTrue(error is EpubSecurityError.CorruptArchive)
    }

    @Test
    fun `损坏文件 - 截断 ZIP`() {
        val zip = newFile("truncated.epub")
        createValidEpub(zip)
        val full = zip.readBytes()
        zip.writeBytes(full.copyOf(full.size / 2))
        val error = unsafeError(validator.validate(zip))
        assertTrue(error is EpubSecurityError.CorruptArchive)
    }

    @Test
    fun `损坏文件 - 空文件`() {
        val zip = newFile("empty.epub")
        // newFile 已创建空文件，直接校验
        val error = unsafeError(validator.validate(zip))
        assertTrue(error is EpubSecurityError.CorruptArchive)
    }

    @Test
    fun `损坏文件 - 文件不存在`() {
        val zip = File(tempDir, "nonexistent.epub")
        val error = unsafeError(validator.validate(zip))
        assertTrue(error is EpubSecurityError.CorruptArchive)
    }

    // ===== 空间不足（CLAUDE.md 强制场景 4）=====

    @Test
    fun `空间不足 - 需求大于可用`() {
        val error = unsafeError(
            EpubSecurityValidator.checkImportPreconditions(sourceSize = 100L, availableSpace = 50L),
        )
        assertTrue(error is EpubSecurityError.InsufficientSpace)
    }

    @Test
    fun `空间充足 - 预检通过`() {
        val result = EpubSecurityValidator.checkImportPreconditions(sourceSize = 50L, availableSpace = 100L)
        assertTrue(result is EpubSecurityResult.Safe)
    }

    @Test
    fun `未知大小 - 跳过预检`() {
        val result = EpubSecurityValidator.checkImportPreconditions(sourceSize = -1L, availableSpace = 0L)
        assertTrue(result is EpubSecurityResult.Safe)
    }

    @Test
    fun `文件过大 - 超过导入上限`() {
        val error = unsafeError(
            EpubSecurityValidator.checkImportPreconditions(
                sourceSize = EpubSecurityValidator.MAX_IMPORT_SIZE + 1,
                availableSpace = EpubSecurityValidator.MAX_IMPORT_SIZE * 2,
            ),
        )
        assertTrue(error is EpubSecurityError.FileTooLarge)
    }

    // ===== isZipSlip 纯函数 =====

    @Test
    fun `isZipSlip - 正常路径不触发`() {
        assertFalse(EpubSecurityValidator.isZipSlip("OEBPS/content.xhtml"))
        assertFalse(EpubSecurityValidator.isZipSlip("mimetype"))
        assertFalse(EpubSecurityValidator.isZipSlip("META-INF/container.xml"))
    }

    @Test
    fun `isZipSlip - 合法子目录往返不触发`() {
        // OEBPS/../OEBPS/content.xhtml → 解析后仍 OEBPS/content.xhtml（在目标目录内）
        assertFalse(EpubSecurityValidator.isZipSlip("OEBPS/../OEBPS/content.xhtml"))
    }

    @Test
    fun `isZipSlip - 路径遍历触发`() {
        assertTrue(EpubSecurityValidator.isZipSlip("../evil"))
        assertTrue(EpubSecurityValidator.isZipSlip("/etc/passwd"))
        assertTrue(EpubSecurityValidator.isZipSlip("foo/../../evil"))
        assertTrue(EpubSecurityValidator.isZipSlip("..\\evil"))
    }
}
