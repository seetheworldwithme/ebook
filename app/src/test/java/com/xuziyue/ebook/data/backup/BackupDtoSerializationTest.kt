package com.xuziyue.ebook.data.backup

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BackupDto] 序列化往返测试（DATA-03，纯 JVM）。
 *
 * 验证 toJson / parseBackupDto 往返无损：schemaVersion、各表行、settings 快照、
 * 可空字段（note/deletedAt/lastOpenedAt）、空列表向后兼容。
 */
class BackupDtoSerializationTest {

    @Test
    fun `完整 BackupDto 往返无损`() {
        val dto = BackupDto(
            exportedAt = 1_700_000_000_000L,
            books = listOf(
                BookRow(
                    id = "b1", contentHash = "h1", title = "书名", authors = listOf("作者甲", "作者乙"),
                    description = "简介", language = "zh", format = "EPUB",
                    mediaType = "application/epub+zip", filePath = "files/books/h1.epub",
                    fileSize = 4096L, coverPath = "files/covers/b1.png", importedAt = 1000L,
                    lastOpenedAt = 2000L, status = "READING",
                ),
            ),
            readingProgress = listOf(
                ProgressRow("b1", """{"href":"x","progression":0.3}""", 0.3, 5000L, null),
            ),
            bookmarks = listOf(
                BookmarkRow("m1", "b1", """{"href":"y"}""", "摘录", 0L),
            ),
            annotations = listOf(
                AnnotationRow(
                    id = "a1", bookId = "b1", locatorJson = """{"href":"z"}""",
                    selectedText = "选中", note = "笔记", color = "YELLOW",
                    createdAt = 0L, updatedAt = 100L, deletedAt = null,
                ),
            ),
            readingSessions = listOf(
                SessionRow("s1", "b1", 0L, 60000L, 60L),
            ),
            settings = mapOf(
                "app_reading_stats_enabled" to JsonPrimitive(true),
                "reader_font_size" to JsonPrimitive(1.3),
            ),
        )

        val json = dto.toJson()
        val back = json.parseBackupDto()

        assertEquals(BACKUP_SCHEMA_VERSION, back.schemaVersion)
        assertEquals(1_700_000_000_000L, back.exportedAt)
        // 书
        assertEquals(1, back.books.size)
        assertEquals("书名", back.books[0].title)
        assertEquals(listOf("作者甲", "作者乙"), back.books[0].authors)
        assertEquals("READING", back.books[0].status)
        assertEquals(2000L, back.books[0].lastOpenedAt)
        // 进度 / 书签 / 批注 / 会话 locatorJson 原样
        assertEquals("""{"href":"x","progression":0.3}""", back.readingProgress[0].locatorJson)
        assertEquals("摘录", back.bookmarks[0].excerpt)
        assertEquals("笔记", back.annotations[0].note)
        assertEquals(60L, back.readingSessions[0].activeSeconds)
        // settings
        assertEquals(true, (back.settings["app_reading_stats_enabled"] as JsonPrimitive).content.toBooleanStrict())
    }

    @Test
    fun `可空字段 null 往返保持 null`() {
        val dto = BackupDto(
            exportedAt = 0L,
            books = listOf(
                BookRow(
                    id = "b2", contentHash = "h2", title = "无封面书", authors = emptyList(),
                    description = null, language = null, format = "TXT", mediaType = "text/plain",
                    filePath = "files/books/h2.txt", fileSize = 0L, coverPath = null,
                    importedAt = 0L, lastOpenedAt = null, status = "UNREAD",
                ),
            ),
            readingProgress = emptyList(),
            bookmarks = emptyList(),
            annotations = listOf(
                AnnotationRow(
                    id = "a2", bookId = "b2", locatorJson = "{}",
                    selectedText = "x", note = null, color = "GREEN",
                    createdAt = 0L, updatedAt = 0L, deletedAt = 999L,
                ),
            ),
            readingSessions = emptyList(),
        )

        val back = dto.toJson().parseBackupDto()
        assertNull(back.books[0].lastOpenedAt)
        assertNull(back.books[0].coverPath)
        assertNull(back.books[0].description)
        assertNull(back.annotations[0].note)
        assertEquals(999L, back.annotations[0].deletedAt)
    }

    @Test
    fun `空库备份往返不崩`() {
        val dto = BackupDto(
            exportedAt = 0L,
            books = emptyList(),
            readingProgress = emptyList(),
            bookmarks = emptyList(),
            annotations = emptyList(),
            readingSessions = emptyList(),
        )
        val back = dto.toJson().parseBackupDto()
        assertTrue(back.books.isEmpty())
        assertTrue(back.readingSessions.isEmpty())
    }

    @Test
    fun `向后兼容忽略未知字段`() {
        // 未来加字段时，旧解析器（ignoreUnknownKeys=true）应能读含未知字段的备份
        val json = """
        {
          "schemaVersion": 1,
          "exportedAt": 123,
          "books": [],
          "readingProgress": [],
          "bookmarks": [],
          "annotations": [],
          "readingSessions": [],
          "futureUnknownField": "ignored"
        }
        """.trimIndent()
        val back = json.parseBackupDto()
        assertEquals(123L, back.exportedAt)
        assertNotNull(back)
    }
}
