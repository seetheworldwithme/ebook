package com.xuziyue.ebook.reader.readium.txt

import java.nio.charset.Charset

/**
 * TXT 编码探测结果（值类型，密封类，对齐 CLAUDE.md 红线 #5：
 * 编码探测失败 / 无法判定时**必须让用户手选**，绝不静默用错误编码乱码打开）。
 *
 * 与 [com.xuziyue.ebook.reader.readium.OpenBookError] 同风格——值而非异常。
 */
sealed class TxtEncodingResult {

    /**
     * 手选候选编码（用于 UI 编码手选列表的展示与回传）。
     *
     * @param charset 字符集。
     * @param displayName 用户可读名称，如「UTF-8」「GB18030（简体中文）」。
     */
    data class EncodingCandidate(
        val charset: Charset,
        val displayName: String,
    )

    /**
     * 已确定编码。
     *
     * @param charset 探测到的字符集。
     * @param hadBom 是否存在 BOM（UTF-8/UTF-16 的 BOM 是强信号，记录便于调试；
     *   解码时 BOM 本身不进正文）。
     */
    data class Detected(
        val charset: Charset,
        val hadBom: Boolean,
    ) : TxtEncodingResult()

    /**
     * 无法自动判定（UTF-8 与 GB18030 均无法干净解码，或为混码 / 二进制内容）。
     *
     * 调用方（UI 层，不在本核心层）须弹出编码手选框，用户选定后回传 [Charset]
     * 重新解码。候选列表按可能性排序，含常见中文编码。
     */
    data class NeedsUserChoice(
        val candidates: List<EncodingCandidate>,
    ) : TxtEncodingResult()
}
