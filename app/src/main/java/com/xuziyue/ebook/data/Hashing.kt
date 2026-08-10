package com.xuziyue.ebook.data

import java.io.InputStream
import java.security.MessageDigest

/**
 * SHA-256 计算工具。
 *
 * 用于 contentHash（对齐 design.md §6.4 `Book.contentHash`，作为书籍唯一标识，
 * 也是 [LocatorStore] 索引 Locator / 文件路径的 key）。
 */
object Hashing {

    /** 计算字节数组的 SHA-256，返回小写十六进制字符串。 */
    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    /** 流式计算 SHA-256（大文件不一次性载入内存，CLAUDE.md 红线 #4）。 */
    fun sha256(stream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = stream.read(buf)
            if (n <= 0) break
            digest.update(buf, 0, n)
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
