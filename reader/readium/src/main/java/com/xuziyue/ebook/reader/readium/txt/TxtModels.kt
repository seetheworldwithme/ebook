package com.xuziyue.ebook.reader.readium.txt

import java.nio.charset.Charset

/**
 * TXT 单个章节。
 *
 * @param index 从 0 开始的序号（稳定，作为章节主键）。
 * @param title 章节标题（已 trim；如「第1章 我有三个相宫」）；
 *   front-matter 章取书名 / 「简介」；无标题兜底章取「正文」。
 * @param body 章节正文（已 trim；不含标题行本身）。
 */
data class TxtChapter(
    val index: Int,
    val title: String,
    val body: String,
)

/**
 * TXT 全书解析结果。
 *
 * @param charset 最终采用的编码（便于调试与回显）。
 * @param hadBom 原文件是否存在 BOM。
 * @param chapters 章节列表（至少 1 个；无标题的纯文本兜底为单章）。
 * @param rawLength 原始字节数（用于校验 / 展示）。
 */
data class TxtBook(
    val charset: Charset,
    val hadBom: Boolean,
    val chapters: List<TxtChapter>,
    val rawLength: Long,
)
