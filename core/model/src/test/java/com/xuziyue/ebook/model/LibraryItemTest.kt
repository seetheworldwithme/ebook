package com.xuziyue.ebook.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [LibraryItem] 轻量单测：字段持有 + progression null / 非 null 语义。
 */
class LibraryItemTest {

    private fun book(id: String = "b1", title: String = "书名") = Book(
        id = id,
        contentHash = "hash-$id",
        title = title,
        authors = listOf("作者"),
        format = "EPUB",
        mediaType = "application/epub+zip",
        filePath = "/data/x.epub",
        fileSize = 100L,
        importedAt = 1L,
    )

    @Test
    fun `progression null 表示未读`() {
        val item = LibraryItem(book = book(), progression = null)
        assertNull(item.progression)
    }

    @Test
    fun `progression 持有全书进度`() {
        val item = LibraryItem(book = book(), progression = 0.42)
        assertEquals(0.42, item.progression!!, 0.0001)
    }

    @Test
    fun `copy book 字段互不影响`() {
        val item = LibraryItem(book = book(title = "原"), progression = 0.1)
        val renamed = item.copy(book = item.book.copy(title = "新"))
        assertEquals("原", item.book.title)
        assertEquals("新", renamed.book.title)
        assertEquals(0.1, renamed.progression!!, 0.0001)
    }
}
