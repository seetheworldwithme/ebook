package com.xuziyue.ebook.library

import com.xuziyue.ebook.model.Book
import com.xuziyue.ebook.model.LibraryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [sortItems] 纯函数单测（LIB-03 排序）：3 种排序 + 未读末尾 + 空列表。
 */
class SortItemsTest {

    private fun item(
        id: String,
        title: String = id,
        lastOpenedAt: Long? = null,
        importedAt: Long = 0L,
    ) = LibraryItem(
        book = Book(
            id = id, contentHash = "h-$id", title = title, authors = emptyList(),
            description = null, language = null, format = "EPUB",
            mediaType = "application/epub+zip", filePath = "/$id.epub",
            fileSize = 0L, coverPath = null, importedAt = importedAt, lastOpenedAt = lastOpenedAt,
        ),
        progression = null,
    )

    @Test
    fun `LAST_OPENED 按最近阅读降序，未读排末尾`() {
        val items = listOf(
            item("a", lastOpenedAt = 100L),
            item("b", lastOpenedAt = null), // 未读
            item("c", lastOpenedAt = 300L),
        )
        assertEquals(
            listOf("c", "a", "b"),
            sortItems(items, LibrarySort.LAST_OPENED).map { it.book.id },
        )
    }

    @Test
    fun `IMPORTED 按导入时间降序`() {
        val items = listOf(
            item("a", importedAt = 100L),
            item("b", importedAt = 300L),
            item("c", importedAt = 200L),
        )
        assertEquals(
            listOf("b", "c", "a"),
            sortItems(items, LibrarySort.IMPORTED).map { it.book.id },
        )
    }

    @Test
    fun `TITLE 按书名不区分大小写升序`() {
        val items = listOf(
            item("a", title = "banana"),
            item("b", title = "Apple"),
            item("c", title = "cherry"),
        )
        // Apple(b) → banana(a) → cherry(c)
        assertEquals(
            listOf("b", "a", "c"),
            sortItems(items, LibrarySort.TITLE).map { it.book.id },
        )
    }

    @Test
    fun `TITLE 中文按 Unicode 序`() {
        val items = listOf(item("a", title = "山海经"), item("b", title = "红楼梦"))
        // 「红」U+7EA2 < 「山」U+5C71？实际「山」(0x5C71) < 「红」(0x7EA2) → 山海经 在前
        assertEquals(
            listOf("a", "b"),
            sortItems(items, LibrarySort.TITLE).map { it.book.id },
        )
    }

    @Test
    fun `空列表排序返回空`() {
        assertTrue(sortItems(emptyList(), LibrarySort.TITLE).isEmpty())
    }
}
