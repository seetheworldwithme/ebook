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
 * 2. **无 BOM**：先做 NUL 字节检测——无 BOM UTF-16 的字节含大量 `0x00`（LE 纯 ASCII 是
 *    `41 00 42 00…`，NUL 本身是合法 UTF-8 字节，严格 UTF-8 校验拦不住），呈奇偶交替分布
 *    即判 UTF-16LE/BE（修复审查严重问题 #7）。
 * 3. **无 BOM**：整流严格 UTF-8 校验通过（`CharsetDecoder` + `REPORT`，全字节）→ UTF-8；
 *    通过后再检查解码结果中 `\u0000` 占比（>5% 视为无 BOM UTF-16 特征 → 手选，不猜）。
 * 4. UTF-8 失败 → **GB18030**（GBK 超集，含 4 字节区）整流校验通过后**二次确认**：
 *    GB18030 对几乎任意字节序列都能干净解码（Big5/EUC-KR/随机数据大概率通过），
 *    统计解码结果中 CJK 汉字占比与乱码特征字符（PUA 私有区）频率，置信度不足 → 手选（修复审查严重问题 #8）。
 * 5. 都失败 → [TxtEncodingResult.NeedsUserChoice]（调用方弹手选）。
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

    /** UTF-8 解码结果 NUL 占比上限（超过视为无 BOM UTF-16 特征，转手选）。 */
    private const val NUL_RATIO_LIMIT = 0.05

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

        // 2. 无 BOM：先做 NUL 字节奇偶交替检测（无 BOM UTF-16 强特征）。
        //    LE 纯 ASCII 是 `41 00 42 00…`——NUL 是合法 UTF-8 字节，UTF-8 校验拦不住，
        //    不先检测会把 UTF-16 误判成 UTF-8，正文夹 NUL 乱码。
        detectUtf16WithoutBom(bytes)?.let { return it }

        // 3. 无 BOM：严格 UTF-8 整流校验
        if (isCleanDecode(bytes, StandardCharsets.UTF_8)) {
            // 双保险：解码结果 NUL 占比超阈值同样是无 BOM UTF-16 特征 → 手选（不猜）。
            val decoded = decode(bytes, StandardCharsets.UTF_8)
            if (nulRatio(decoded) <= NUL_RATIO_LIMIT) {
                return TxtEncodingResult.Detected(StandardCharsets.UTF_8, hadBom = false)
            }
            return TxtEncodingResult.NeedsUserChoice(DEFAULT_CANDIDATES)
        }

        // 4. UTF-8 失败 → GB18030（GBK 超集）+ 二次置信确认
        if (isCleanDecode(bytes, GB18030)) {
            val decoded = String(bytes, GB18030)
            if (isPlausibleChineseText(decoded)) {
                return TxtEncodingResult.Detected(GB18030, hadBom = false)
            }
            // 校验通过但解码结果不像正常中文文本（可能是 Big5 / EUC-KR / 二进制误入）→ 手选。
            return TxtEncodingResult.NeedsUserChoice(DEFAULT_CANDIDATES)
        }

        // 5. 都失败 → 让用户手选（红线 #5：绝不静默乱码）
        return TxtEncodingResult.NeedsUserChoice(DEFAULT_CANDIDATES)
    }

    /**
     * 无 BOM 的 UTF-16 检测：NUL 字节呈奇偶交替分布即判 UTF-16LE/BE。
     *
     * - LE：ASCII/常用区字符的高字节是 NUL（`41 00`）→ 偶数位为 NUL；
     * - BE：低字节是 NUL（`00 41`）→ 奇数位为 NUL。
     * 要求样本足够（≥16 字节）、NUL 占比达 1/8 以上且集中于同一奇偶位，
     * 正常 UTF-8/GB18030 文本几乎不含 NUL，误判率可忽略。
     * 检测不出返回 null（继续走 UTF-8 / GB18030 流程）。
     */
    internal fun detectUtf16WithoutBom(bytes: ByteArray): TxtEncodingResult? {
        if (bytes.size < 16) return null
        var evenNul = 0
        var oddNul = 0
        val sampleSize = minOf(bytes.size, 8192)
        for (i in 0 until sampleSize) {
            if (bytes[i] == 0.toByte()) {
                if (i % 2 == 0) evenNul++ else oddNul++
            }
        }
        val sampleUnits = sampleSize / 2
        if (evenNul >= sampleUnits / 8 && evenNul > oddNul * 4) {
            return TxtEncodingResult.Detected(StandardCharsets.UTF_16LE, hadBom = false)
        }
        if (oddNul >= sampleUnits / 8 && oddNul > evenNul * 4) {
            return TxtEncodingResult.Detected(StandardCharsets.UTF_16BE, hadBom = false)
        }
        return null
    }

    /** 解码结果中 `\u0000` 的占比（0.0~1.0）。 */
    private fun nulRatio(text: String): Double {
        if (text.isEmpty()) return 0.0
        var nul = 0
        for (ch in text) if (ch == '\u0000') nul++
        return nul.toDouble() / text.length
    }

    /**
     * GB18030 干净解码后的二次置信确认：解码结果是否像正常中文文本。
     *
     * GB18030 对几乎任意字节序列都能干净解码（Big5 / EUC-KR / 随机二进制大概率通过），
     * 只靠「无坏字节」不足以判定。启发式：
     * - 出现 PUA 私有区字符（`\uE000`-`\uF8FF`，GB18030 4 字节区映射的生僻位）视为乱码特征；
     * - CJK 汉字 + ASCII 占比过低（<30%）视为可疑——正常中文小说的汉字密度远高于此。
     */
    internal fun isPlausibleChineseText(text: String): Boolean {
        if (text.isEmpty()) return false
        val sample = if (text.length > 4096) text.substring(0, 4096) else text
        var cjk = 0
        for (ch in sample) {
            // PUA 私有区 / 部分兼容表意区是 GB18030 误解码的高频乱码落点，直接否决。
            if (ch in '\uE000'..'\uF8FF') return false
            if (ch in '\u4E00'..'\u9FFF' || ch in '\u3400'..'\u4DBF') cjk++
        }
        // 全 ASCII 时 GB18030 与 UTF-8 等价，且 UTF-8 分支已拦截，到这里说明有非 ASCII 字节；
        // CJK 占比过低说明「干净解码」大概率是 GB18030 兜底误吸，交给用户手选。
        return cjk.toDouble() / sample.length >= 0.3
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
