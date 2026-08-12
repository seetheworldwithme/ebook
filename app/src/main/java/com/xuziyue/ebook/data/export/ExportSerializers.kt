package com.xuziyue.ebook.data.export

import android.content.Context
import com.xuziyue.ebook.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.json.Json

/**
 * 导出序列化（DATA-01）。
 *
 * - [ExportDto.toJson]：kotlinx.serialization 输出结构化 JSON（机器可读，含 schemaVersion / locator 嵌套）。
 * - [ExportDto.toMarkdown]：手拼 Markdown（人可读）；标签经字符串资源本地化，时间戳格式化为本地时间。
 *
 * 两个格式都从同一份 [ExportDto] 派生，保证数据口径一致。
 */

private val exportJson = Json {
    prettyPrint = true
    encodeDefaults = true // 显式输出 schemaVersion 等默认字段，便于读取方解析
}

/** 序列化为 JSON 字符串（含顶层 schemaVersion + 嵌套 locator schemaVersion）。 */
fun ExportDto.toJson(): String = exportJson.encodeToString(ExportDto.serializer(), this)

/** 序列化为 Markdown 字符串（人可读；标签随系统语言）。 */
fun ExportDto.toMarkdown(context: Context): String = buildString {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    appendLine(context.getString(R.string.export_md_title, book.title))
    appendLine()
    if (book.authors.isNotEmpty()) {
        appendLine(context.getString(R.string.export_md_author, book.authors.joinToString("、")))
    }
    appendLine(context.getString(R.string.export_md_format, book.format))
    appendLine(context.getString(R.string.export_md_exported_at, fmt.format(Date(exportedAt))))
    appendLine()

    progress?.let { p ->
        appendLine(context.getString(R.string.export_md_progress))
        val pct = p.progression?.let { "${(it * 100).toInt()}%" }
            ?: context.getString(R.string.export_md_progress_unknown)
        appendLine("- $pct")
        appendLine()
    }

    if (bookmarks.isNotEmpty()) {
        appendLine(context.getString(R.string.export_md_bookmarks, bookmarks.size))
        bookmarks.forEachIndexed { i, b ->
            val excerpt = b.excerpt?.takeIf { it.isNotBlank() }?.let { "　$it" } ?: ""
            appendLine("${i + 1}. ${fmt.format(Date(b.createdAt))}$excerpt")
        }
        appendLine()
    }

    if (annotations.isNotEmpty()) {
        appendLine(context.getString(R.string.export_md_annotations, annotations.size))
        annotations.forEachIndexed { i, a ->
            appendLine("${i + 1}. > ${a.selectedText.ifBlank { context.getString(R.string.annotation_empty_selection) }}")
            a.note?.takeIf { it.isNotBlank() }?.let {
                appendLine("   - ${context.getString(R.string.note_label, it)}")
            }
            appendLine("   - ${fmt.format(Date(a.createdAt))}　${a.color.toColorLabel(context)}")
        }
        appendLine()
    }
}

/** HighlightColor.name → 本地化色名（Markdown 可读性；未知值原样返回）。 */
private fun String.toColorLabel(context: Context): String {
    val resId = when (lowercase()) {
        "yellow" -> R.string.color_yellow
        "green" -> R.string.color_green
        "blue" -> R.string.color_blue
        "pink" -> R.string.color_pink
        else -> return this
    }
    return context.getString(resId)
}
