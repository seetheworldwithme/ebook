package com.xuziyue.ebook.reader.readium.txt

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * TXT 编码探测器（纯 `java.nio.charset`，零 Android / Readium / Compose 依赖，可独立单测）。
 *
 * ## 算法（对齐 CLAUDE.md 红线 #5：绝不静默乱码打开）
 *
 * 1. **BOM 优先**（强信号、零歧义）：UTF-8 BOM(`EF BB BF`) / UTF-16 BE(`FE FF`) /
 *    LE(`FF FE`) 命中即定。
 * 2. **无 BOM**：整流严格 UTF-8 校验通过（`CharsetDecoder` + `REPORT`，全字节）→ UTF-8。
 * 3. UTF-8 失败 → **GB18030**（GBK 超集，含 4 字节区）整流校验通过 → GB18030。
 * 4. 都失败 → [TxtEncodingResult.NeedsUserChoice]（调用方弹手选）。
 *
 * ## 正确性依据
 *
 * GBK / GB18030 的双字节结构（lead `0x81–0xFE` + trail）几乎必然违反 UTF-8 续字节规则
 * （续字节须 `10xxxxxx`）。实测《万相之王》第 0 字节 `0xCD` 即非法 UTF-8 续字节，
 * 整流校验立刻失败 → 走 GB18030 兜底。纯 ASCII 是 UTF-8 / GB18030 共同子集，
 * UTF-8 优先为合理默认。混码 / 坏字节 → 两种编码都校验失败 → 手选（不猜）。
 */
object TxtEncodingDetector {

    /** UTF-8 BOM。 */
    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    /** UTF-16 BE BOM。 */
    private val UTF16_BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
    /** UTF-16 LE BOM。 */
    private val UTF16_LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())

    /** GB18030（GBK 超集，含 4 字节区），中文 TXT 最常见的非 UTF-8 编码。 */
    val GB18030: Charset = Charset.forName("GB18030")

    /** GBK（GB18030 子集），手选候选。 */
    val GBK: Charset = Charset.forName("GBK")

    /** Big5（繁体中文），手选候选。 */
    val BIG5: Charset = Charset.forName("Big5")

    /** 默认手选候选列表（顺序即推荐度）。 */
    val DEFAULT_CANDIDATES: List<TxtEncodingResult.EncodingCandidate> = listOf(
        TxtEncodingResult.EncodingCandidate(StandardCharsets.UTF_8, "UTF-8"),
        TxtEncodingResult.EncodingCandidate(GB18030, "GB18030（简体中文）"),
        TxtEncodingResult.EncodingCandidate(GBK, "GBK"),
        TxtEncodingResult.EncodingCandidate(BIG5, "Big5（繁体中文）"),
        TxtEncodingResult.EncodingCandidate(StandardCharsets.UTF_16, "UTF-16"),
    )

    /**
     * 探测字节数组的编码。
     *
     * @param bytes 已读入的字节（调用方负责大小上限检查）。
     */
    fun detect(bytes: ByteArray): TxtEncodingResult {
        // 1. BOM 优先
        if (bytes.startsWith(UTF8_BOM)) {
            return TxtEncodingResult.Detected(StandardCharsets.UTF_8, hadBom = true)
        }
        if (bytes.startsWith(UTF16_BE_BOM)) {
            return TxtEncodingResult.Detected(StandardCharsets.UTF_16BE, hadBom = true)
        }
        if (bytes.startsWith(UTF16_LE_BOM)) {
            return TxtEncodingResult.Detected(StandardCharsets.UTF_16LE, hadBom = true)
        }

        // 2. 无 BOM：严格 UTF-8 整流校验
        if (isCleanDecode(bytes, StandardCharsets.UTF_8)) {
            return TxtEncodingResult.Detected(StandardCharsets.UTF_8, hadBom = false)
        }

        // 3. UTF-8 失败 → GB18030（GBK 超集）
        if (isCleanDecode(bytes, GB18030)) {
            return TxtEncodingResult.Detected(GB18030, hadBom = false)
        }

        // 4. 都失败 → 让用户手选（红线 #5：绝不静默乱码）
        return TxtEncodingResult.NeedsUserChoice(DEFAULT_CANDIDATES)
    }

    /** 探测文件的编码（内部读取全部字节，调用方应先做大小校验）。 */
    fun detect(file: File): TxtEncodingResult = detect(file.readBytes())

    /**
     * 去除 BOM 并按指定 [charset] 解码为正文 String。
     *
     * BOM 是元数据，不进正文（Java 标准 UTF-8 / UTF-16BE/LE 解码器不会自动吞掉 BOM，
     * 会解码出 `\uFEFF`，故这里手动按 [charset] 对应的 BOM 剥离前缀字节）。
     */
    fun decode(bytes: ByteArray, charset: Charset): String {
        val bom = bomOf(charset)
        val payload = if (bom != null && bytes.startsWith(bom)) {
            bytes.copyOfRange(bom.size, bytes.size)
        } else {
            bytes
        }
        return String(payload, charset)
    }

    /** [bytes] 是否以 [charset] 对应的 BOM 起头（UTF-8 / UTF-16 BE / LE），其余编码恒为 false。 */
    fun hasBom(bytes: ByteArray, charset: Charset): Boolean {
        val bom = bomOf(charset) ?: return false
        return bytes.startsWith(bom)
    }

    /** 返回 [charset] 对应的 BOM（UTF-8 / UTF-16 BE / LE），其余编码返回 null。 */
    private fun bomOf(charset: Charset): ByteArray? = when (charset) {
        StandardCharsets.UTF_8 -> UTF8_BOM
        StandardCharsets.UTF_16BE -> UTF16_BE_BOM
        StandardCharsets.UTF_16LE -> UTF16_LE_BOM
        else -> null
    }

    /** 严格校验 [bytes] 能否被 [charset] 干净解码（遇坏字节即 REPORT 抛异常 → 返回 false）。 */
    private fun isCleanDecode(bytes: ByteArray, charset: Charset): Boolean {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes))
            true
        } catch (e: CharacterCodingException) {
            false
        }
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) return false
        }
        return true
    }
}
