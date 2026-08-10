package com.xuziyue.ebook.reader.readium.txt

/**
 * TXT 章节切分器（纯正则、无 I/O，可独立单测）。
 *
 * 输入：已解码（BOM 已去除）的全文 String；输出：[List]<[TxtChapter]>。
 *
 * ## 设计要点
 *
 * - **行首锚定 + 整行匹配**（[Regex.matches] 等价 matchEntire）：正文段落里的「第三章」
 *   几乎从不出现在行首，故零误切（实测《万相之王》20 行正文含「第...章」无一在行首）。
 * - 兼容阿拉伯数字与中文数字（小写 一二…十百千万 + 大写 壹贰…拾佰仟 + 「两」）。
 * - 兼容序章 / 楔子 / 番外 / 前言 / 后记 等无「第N」前缀的特殊起止章。
 * - **front-matter**（正文前的书名 / 作者头）单独成 index=0 的「简介」章；首行即标题则不生成。
 * - 无任何标题 → 整篇作单章兜底，绝不返回空列表。
 * - 只切「章」级，不切「卷 / 部 / 篇」（更高级别，避免产生含数百章的巨型章）。
 *
 * @param patterns 章节标题正则集，一行匹配任一即视为边界。默认 [DEFAULT_PATTERNS]。
 * @param frontMatterTitle front-matter 章的兜底标题（首行非空时优先取首行）。
 */
class TxtChapterSplitter(
    private val patterns: List<Regex> = DEFAULT_PATTERNS,
    private val frontMatterTitle: String = "简介",
) {

    /** 边界：所在行号 + 标题文字。 */
    private data class Boundary(val line: Int, val title: String)

    companion object {
        /** 数字字符类：阿拉伯 + 中文小写 + 中文大写 + 「两」。 */
        const val NUMBER = """[\d零一二三四五六七八九十百千万两壹贰叁肆伍陆柒捌玖拾佰仟]+"""

        /**
         * 主章节标题正则：`第` + 数字 + `章` + 可选标题文字。
         *
         * 示例命中：`第1章 我有三个相宫` / `第一千八百三十七章 大结局` / `第三章`。
         * 分隔符容错：半角空格 / 全角空格（`　`）/ 无空格直连。
         */
        val CHAPTER_PATTERN: Regex =
            Regex("""^\s*第\s*$NUMBER\s*章(?:[\s${'　'}]+(.*))?$""")

        /**
         * 特殊标题正则：序章 / 楔子 / 番外 / 前言 / 后记 等无「第N」前缀的起止章。
         *
         * 左起**严格整行枚举**（非子串匹配）：`后记` 须独占一行，正文「……后记一笔」不会在行首
         * 独占，故不误匹配。长形枚举在前（如 `序章` 先于 `序`）以避免短前缀抢先。
         */
        val SPECIAL_PATTERN: Regex =
            Regex(
                """^\s*(?:序章|序言|楔子|番外篇|番外|引子|引言|前言|尾声|终章|后记|跋|""" +
                    """内容简介|内容提要|简介|序)(?:[\s${'　'}]+(.*))?$""",
            )

        /** 默认正则集：章 + 特殊标题。 */
        val DEFAULT_PATTERNS: List<Regex> = listOf(CHAPTER_PATTERN, SPECIAL_PATTERN)
    }

    /**
     * 切分全文为章节列表。
     *
     * @param fullText 已解码（BOM 已去除）的全文。
     * @return 至少 1 个章节（无标题时整篇作单章）。
     */
    fun split(fullText: String): List<TxtChapter> {
        // 归一化三种行尾（CRLF / CR / LF）
        val lines = fullText.split(Regex("\\r\\n|\\r|\\n"))

        // 找所有章节边界
        val boundaries = mutableListOf<Boundary>()
        for (i in lines.indices) {
            val title = matchTitle(lines[i]) ?: continue
            boundaries.add(Boundary(i, title))
        }

        // 无边界 → 整篇作单章兜底
        if (boundaries.isEmpty()) {
            return listOf(TxtChapter(0, "正文", fullText.trim()))
        }

        val chapters = mutableListOf<TxtChapter>()

        // front-matter：第一个边界之前的内容单独成「简介」章
        val firstBoundaryLine = boundaries.first().line
        if (firstBoundaryLine > 0) {
            val frontLines = lines.subList(0, firstBoundaryLine)
            val frontBody = frontLines.joinToString("\n").trim()
            if (frontBody.isNotBlank()) {
                val title = frontLines.firstOrNull { it.trim().isNotBlank() }?.trim()
                    ?: frontMatterTitle
                chapters.add(TxtChapter(chapters.size, title, frontBody))
            }
        }

        // 各章正文（不含标题行）
        for (b in boundaries.indices) {
            val start = boundaries[b].line + 1
            val end = if (b + 1 < boundaries.size) boundaries[b + 1].line else lines.size
            val body = lines.subList(start, end).joinToString("\n").trim()
            chapters.add(TxtChapter(chapters.size, boundaries[b].title, body))
        }

        return chapters
    }

    /**
     * 判断单行（trim 后）是否为章节标题边界。
     *
     * @return 命中返回标题文字（整行 trim），否则 null。
     */
    internal fun matchTitle(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        return patterns.firstOrNull { it.matches(trimmed) }?.let { trimmed }
    }
}
