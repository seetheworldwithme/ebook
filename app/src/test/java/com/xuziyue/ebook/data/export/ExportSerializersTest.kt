package com.xuziyue.ebook.data.export

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导出序列化单测（DATA-01）。
 * 验证 JSON 含稳定 schema 版本 + 书籍 ID + Locator（嵌套 schemaVersion）+ 时间戳；Markdown 人可读。
 */
class ExportSerializersTest {

    private val sampleLocator = Json.parseToJsonElement(
        """{"schemaVersion":1,"locatorJson":"{\"href\":\"ch1\",\"type\":\"text/html\"}"}""",
    )

    private fun sampleDto() = ExportDto(
        schemaVersion = EXPORT_SCHEMA_VERSION,
        exportedAt = 1_000_000L,
        book = BookDto(id = "b1", title = "测试书", authors = listOf("作者甲"), format = "EPUB"),
        progress = ProgressDto(locator = sampleLocator, progression = 0.42, updatedAt = 900_000L),
        bookmarks = listOf(
            BookmarkDto(id = "bm1", locator = sampleLocator, excerpt = "书签摘录", createdAt = 500L),
        ),
        annotations = listOf(
            AnnotationDto(
                id = "an1",
                locator = sampleLocator,
                selectedText = "高亮文字",
                note = "好句",
                color = "YELLOW",
                createdAt = 100L,
                updatedAt = 200L,
            ),
        ),
    )

    @Test
    fun `toJson 含顶层 schemaVersion 与书籍字段`() {
        val parsed = Json.parseToJsonElement(sampleDto().toJson()).jsonObject
        assertEquals(EXPORT_SCHEMA_VERSION, parsed["schemaVersion"]!!.jsonPrimitive.int)
        assertEquals(1_000_000L, parsed["exportedAt"]!!.jsonPrimitive.content.toLong())
        assertEquals("b1", parsed["book"]!!.jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("测试书", parsed["book"]!!.jsonObject["title"]!!.jsonPrimitive.content)
    }

    @Test
    fun `toJson 的 locator 原样保留 PersistedLocator 嵌套 schemaVersion`() {
        val parsed = Json.parseToJsonElement(sampleDto().toJson()).jsonObject
        val ann = parsed["annotations"]!!.jsonArray[0].jsonObject
        val locator = ann["locator"]!!.jsonObject
        assertTrue(locator.containsKey("schemaVersion"))
        assertTrue(locator.containsKey("locatorJson"))
        assertEquals("高亮文字", ann["selectedText"]!!.jsonPrimitive.content)
    }

    @Test
    fun `toMarkdown 含书名标题与高亮笔记`() {
        val md = sampleDto().toMarkdown()
        assertTrue(md.contains("# 《测试书》批注"))
        assertTrue(md.contains("作者甲"))
        assertTrue(md.contains("高亮文字"))
        assertTrue(md.contains("笔记：好句"))
        assertTrue(md.contains("书签摘录"))
    }
}
