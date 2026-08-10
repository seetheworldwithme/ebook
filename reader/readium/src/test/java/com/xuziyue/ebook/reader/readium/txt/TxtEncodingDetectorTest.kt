package com.xuziyue.ebook.reader.readium.txt

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 编码探测器单测（纯 JVM，GB18030/Big5 在主机 JDK 均为标准字符集，无 android.jar stub 问题）。
 *
 * 重点验证红线 #5：UTF-8/GB18030 自动探测，无法判定时返回 [TxtEncodingResult.NeedsUserChoice]，
 * 绝不静默乱码。
 */
class TxtEncodingDetectorTest {

    private val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val utf16BeBom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
    private val utf16LeBom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())

    @Test
    fun `UTF-8 BOM 命中`() {
        val bytes = utf8Bom + "正文测试".toByteArray(StandardCharsets.UTF_8)
        val result = TxtEncodingDetector.detect(bytes)

        assertTrue(result is TxtEncodingResult.Detected)
        result as TxtEncodingResult.Detected
        assertEquals(StandardCharsets.UTF_8, result.charset)
        assertTrue(result.hadBom)
    }

    @Test
    fun `UTF-16 BE BOM 命中`() {
        val bytes = utf16BeBom + "正文".toByteArray(StandardCharsets.UTF_16BE)
        val result = TxtEncodingDetector.detect(bytes)

        assertTrue(result is TxtEncodingResult.Detected)
        assertEquals(StandardCharsets.UTF_16BE, (result as TxtEncodingResult.Detected).charset)
    }

    @Test
    fun `UTF-16 LE BOM 命中`() {
        val bytes = utf16LeBom + "正文".toByteArray(StandardCharsets.UTF_16LE)
        val result = TxtEncodingDetector.detect(bytes)

        assertTrue(result is TxtEncodingResult.Detected)
        assertEquals(StandardCharsets.UTF_16LE, (result as TxtEncodingResult.Detected).charset)
    }

    @Test
    fun `UTF-8 无 BOM 中文`() {
        val bytes = "正文中文内容".toByteArray(StandardCharsets.UTF_8)
        val result = TxtEncodingDetector.detect(bytes)

        assertTrue(result is TxtEncodingResult.Detected)
        result as TxtEncodingResult.Detected
        assertEquals(StandardCharsets.UTF_8, result.charset)
        assertFalse(result.hadBom)
    }

    @Test
    fun `GB18030 中文`() {
        // 「天蚕土豆」GB18030 字节（首字节 0xCD 即非法 UTF-8 续字节 → UTF-8 校验立刻失败）
        val bytes = "天蚕土豆".toByteArray(TxtEncodingDetector.GB18030)
        val result = TxtEncodingDetector.detect(bytes)

        assertTrue(result is TxtEncodingResult.Detected)
        result as TxtEncodingResult.Detected
        assertEquals(TxtEncodingDetector.GB18030, result.charset)
        assertFalse(result.hadBom)
    }

    @Test
    fun `纯 ASCII 判 UTF-8`() {
        // ASCII 是 UTF-8 / GB18030 共同子集，UTF-8 优先为合理默认
        val bytes = "Chapter 1 Hello 123".toByteArray(StandardCharsets.UTF_8)
        val result = TxtEncodingDetector.detect(bytes)

        assertTrue(result is TxtEncodingResult.Detected)
        assertEquals(StandardCharsets.UTF_8, (result as TxtEncodingResult.Detected).charset)
    }

    @Test
    fun `彻底乱码返回需手选`() {
        // 全 0xFF：既非法 UTF-8（0xFF 非 UTF-8 起始字节），也非 GB18030（0xFF 非法字节）
        // 且不以任何 BOM 起头 → 两种编码都校验失败 → NeedsUserChoice（红线 #5）
        val bytes = ByteArray(4) { 0xFF.toByte() }
        val result = TxtEncodingDetector.detect(bytes)

        assertTrue(result is TxtEncodingResult.NeedsUserChoice)
        assertTrue((result as TxtEncodingResult.NeedsUserChoice).candidates.isNotEmpty())
    }

    @Test
    fun `decode 去除 UTF-8 BOM`() {
        val bytes = utf8Bom + "abc".toByteArray(StandardCharsets.UTF_8)
        val text = TxtEncodingDetector.decode(bytes, StandardCharsets.UTF_8)

        assertEquals("abc", text)
    }

    @Test
    fun `decode 只剥前缀 BOM 不误删中间`() {
        // BOM 字节出现在文本中间（非前缀）时应保留（验证剥离逻辑只删前缀）
        val bytes = "a".toByteArray(StandardCharsets.UTF_8) +
            utf8Bom +
            "b".toByteArray(StandardCharsets.UTF_8)
        val text = TxtEncodingDetector.decode(bytes, StandardCharsets.UTF_8)

        assertEquals("a﻿b", text)
    }

    @Test
    fun `hasBom 判定 UTF-8 BOM 存在`() {
        assertTrue(TxtEncodingDetector.hasBom(utf8Bom + "x".toByteArray(), StandardCharsets.UTF_8))
        assertFalse(TxtEncodingDetector.hasBom("x".toByteArray(), StandardCharsets.UTF_8))
        // GB18030 无 BOM 概念 → 恒 false
        assertFalse(
            TxtEncodingDetector.hasBom("中".toByteArray(TxtEncodingDetector.GB18030), TxtEncodingDetector.GB18030),
        )
    }
}
