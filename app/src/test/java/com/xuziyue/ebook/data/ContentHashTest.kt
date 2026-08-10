package com.xuziyue.ebook.data

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Hashing（SHA-256）单测：用已知输入断言标准哈希值，验证哈希算法与十六进制编码正确。
 * 这是 contentHash（书籍唯一标识、Locator 索引 key）的根基，必须确定性可复现。
 */
class ContentHashTest {

    @Test
    fun `字节数组 sha256 - abc`() {
        // 标准值：SHA-256("abc")
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Hashing.sha256("abc".toByteArray()),
        )
    }

    @Test
    fun `字节数组 sha256 - 空输入`() {
        // 标准值：SHA-256("")
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Hashing.sha256(ByteArray(0)),
        )
    }

    @Test
    fun `流式 sha256 与字节数组结果一致`() {
        val data = "中文内容也应有稳定哈希 ABC123".toByteArray()
        val fromBytes = Hashing.sha256(data)
        val fromStream = Hashing.sha256(ByteArrayInputStream(data))

        assertEquals(fromBytes, fromStream)
    }

    @Test
    fun `哈希为 64 位小写十六进制`() {
        val hex = Hashing.sha256("x".toByteArray())
        assertEquals(64, hex.length)
        assert(hex.all { it in '0'..'9' || it in 'a'..'f' }) { "应为小写十六进制：$hex" }
    }
}
