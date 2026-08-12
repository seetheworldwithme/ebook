package com.xuziyue.ebook

import com.xuziyue.ebook.log.CrashLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SET-05 崩溃日志脱敏防回退测试（红线 #8：日志不得包含完整文件路径）。
 *
 * 把「路径被脱敏」固化成测试——将来谁改 [CrashLogger.sanitize] 导致路径泄漏，
 * 对应断言即红。纯 JVM 单测（[sanitize] 是纯函数，无需 Robolectric）。
 */
class CrashLogSanitizerTest {

    private val filesDir = "/data/user/0/com.xuziyue.ebook/files"
    private val cacheDir = "/data/user/0/com.xuziyue.ebook/cache"

    @Test
    fun `filesDir 前缀替换为占位符`() {
        val input = "FileNotFoundException: $filesDir/books/abc123.epub"
        val result = CrashLogger.sanitize(input, filesDir, cacheDir)
        assertFalse("脱敏后不应含 filesDir 实际路径", result.contains(filesDir))
        assertTrue("应含占位符 <app-dir>", result.contains("<app-dir>"))
    }

    @Test
    fun `cacheDir 前缀替换为占位符`() {
        val input = "Writing to $cacheDir/txt-converted/xyz.epub"
        val result = CrashLogger.sanitize(input, filesDir, cacheDir)
        assertFalse(result.contains(cacheDir))
        assertTrue(result.contains("<app-cache>"))
    }

    @Test
    fun `epub 文件路径被脱敏`() {
        val input = "at com.xuziyue.ebook.OpenBook(FileNotFound: /sdcard/Downloads/novel.epub)"
        val result = CrashLogger.sanitize(input, filesDir, cacheDir)
        assertFalse("不应含 .epub 路径", result.contains("novel.epub"))
        assertTrue("应含 <file> 占位符", result.contains("<file>"))
    }

    @Test
    fun `txt 与 pdf 文件路径被脱敏`() {
        val input = "/storage/emulated/0/test.txt and /data/data/other/book.pdf"
        val result = CrashLogger.sanitize(input, filesDir, cacheDir)
        assertFalse(result.contains("test.txt"))
        assertFalse(result.contains("book.pdf"))
    }

    @Test
    fun `通用 data 路径兜底脱敏`() {
        // filesDir 未精确命中的其他 App 路径段（/data/user/0/com.other.app）
        val input = "/data/user/0/com.other.app/files/data.db"
        val result = CrashLogger.sanitize(input, filesDir, cacheDir)
        assertFalse(result.contains("/data/user/0/com.other.app"))
    }

    @Test
    fun `storage 路径兜底脱敏`() {
        val input = "Copied from /storage/emulated/0/Download/sample.epub"
        val result = CrashLogger.sanitize(input, filesDir, cacheDir)
        assertFalse("不应含 /storage 路径", result.contains("/storage/emulated"))
    }

    @Test
    fun `stack trace 源码行号保留（非文件路径）`() {
        // Kotlin 源码引用（BookFileImporter.kt:29）不是用户文件路径，不应被误删。
        val input = "at com.xuziyue.ebook.data.BookFileImporter.copy(BookFileImporter.kt:29)"
        val result = CrashLogger.sanitize(input, filesDir, cacheDir)
        assertTrue("源码行号应保留", result.contains("BookFileImporter.kt:29"))
    }

    @Test
    fun `模拟完整崩溃日志脱敏后不含完整路径`() {
        val raw = buildString {
            appendLine("Time: Tue Aug 12 10:00:00 CST 2026")
            appendLine("Thread: main")
            appendLine("java.io.FileNotFoundException: $filesDir/books/a1b2c3.epub (No such file)")
            appendLine("\tat com.xuziyue.ebook.data.BookFileImporter.copy(BookFileImporter.kt:29)")
            appendLine("\tat com.xuziyue.ebook.reader.ReaderViewModel.openPublication(ReaderViewModel.kt:243)")
        }
        val result = CrashLogger.sanitize(raw, filesDir, cacheDir)
        // 红线 #8：完整文件路径不得出现
        assertFalse("脱敏后不应含 filesDir", result.contains(filesDir))
        assertFalse("脱敏后不应含 .epub 路径", result.contains("a1b2c3.epub"))
        // 正常信息应保留
        assertTrue("时间戳保留", result.contains("Time:"))
        assertTrue("源码引用保留", result.contains("ReaderViewModel.kt:243"))
    }
}
