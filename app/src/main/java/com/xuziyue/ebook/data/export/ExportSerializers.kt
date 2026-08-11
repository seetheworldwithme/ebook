package com.xuziyue.ebook.data.export

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.json.Json

/**
 * 导出序列化（DATA-01）。
 *
 * - [ExportDto.toJson]：kotlinx.serialization 输出结构化 JSON（机器可读，含 schemaVersion / locator 嵌套）。
 * - [ExportDto.toMarkdown]：手拼 Markdown（人可读）；时间戳格式化为本地时间，JSON 里仍存 epoch。
 *
 * 两个格式都从同一份 [ExportDto] 派生，保证数据口径一致。
 */

private val exportJson = Json {
    prettyPrint = true
    encodeDefaults = true // 显式输出 schemaVersion 等默认字段，便于读取方解析
}

/** 序列化为 JSON 字符串（含顶层 schemaVersion + 嵌套 locator schemaVersion）。 */
fun ExportDto.toJson(): String = exportJson.encodeToString(ExportDto.serializer(), this)

/** 序列化为 Markdown 字符串（人可读）。 */
fun ExportDto.toMarkdown(): String = buildString {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    appendLine("# 《${book.title}》批注")
    appendLine()
    if (book.authors.isNotEmpty()) {
        appendLine("- 作者：${book.authors.joinToString("、")}")
    }
    appendLine("- 格式：${book.format}")
    appendLine("- 导出时间：${fmt.format(Date(exportedAt))}")
    appendLine()

    progress?.let { p ->
        appendLine("## 阅读进度")
        val pct = p.progression?.let { "${(it * 100).toInt()}%" } ?: "未知"
        appendLine("- $pct")
        appendLine()
    }

    if (bookmarks.isNotEmpty()) {
        appendLine("## 书签（${bookmarks.size}）")
        bookmarks.forEachIndexed { i, b ->
            val excerpt = b.excerpt?.takeIf { it.isNotBlank() }?.let { "　$it" } ?: ""
            appendLine("${i + 1}. ${fmt.format(Date(b.createdAt))}$excerpt")
        }
        appendLine()
    }

    if (annotations.isNotEmpty()) {
        appendLine("## 高亮与笔记（${annotations.size}）")
        annotations.forEachIndexed { i, a ->
            appendLine("${i + 1}. > ${a.selectedText.ifBlank { "（空选区）" }}")
            a.note?.takeIf { it.isNotBlank() }?.let { appendLine("   - 笔记：$it") }
            appendLine("   - ${fmt.format(Date(a.createdAt))}　${a.color.toColorLabel()}")
        }
        appendLine()
    }
}

/** HighlightColor.name → 中文色名（Markdown 可读性；未知值原样返回）。 */
private fun String.toColorLabel(): String = when (lowercase()) {
    "yellow" -> "黄"
    "green" -> "绿"
    "blue" -> "蓝"
    "pink" -> "粉"
    else -> this
}
