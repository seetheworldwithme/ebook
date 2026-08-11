package com.xuziyue.ebook.reader

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReaderSessionTest {

    private fun locator(href: String): Locator = Locator.fromJSON(
        JSONObject().apply {
            put("href", href)
            put("type", "application/xhtml+xml")
            put("locations", JSONObject())
        },
    ) ?: error("测试 Locator 构造失败")

    @Test
    fun `没有 navigator 时创建当前书 navigator`() {
        assertEquals(
            NavigatorUpdate.CREATE,
            navigatorUpdate(boundBookId = null, readyBookId = "book-b"),
        )
    }

    @Test
    fun `同一本书恢复时复用 navigator`() {
        assertEquals(
            NavigatorUpdate.KEEP,
            navigatorUpdate(boundBookId = "book-a", readyBookId = "book-a"),
        )
    }

    @Test
    fun `切换书籍时替换旧 navigator`() {
        assertEquals(
            NavigatorUpdate.REPLACE,
            navigatorUpdate(boundBookId = "book-a", readyBookId = "book-b"),
        )
    }

    @Test
    fun `只接受当前书 navigator 发出的 locator`() {
        assertTrue(acceptsLocator(activeBookId = "book-b", sourceBookId = "book-b"))
        assertFalse(acceptsLocator(activeBookId = "book-b", sourceBookId = "book-a"))
        assertFalse(acceptsLocator(activeBookId = null, sourceBookId = "book-a"))
    }

    @Test
    fun `同一本书退出重进时优先恢复最新 locator`() {
        val initial = locator("chapter-1.xhtml")
        val latest = locator("chapter-2.xhtml")

        assertEquals(
            latest,
            selectNavigatorRestoreLocator(
                activeBookId = "book-a",
                readyBookId = "book-a",
                latestLocator = latest,
                initialLocator = initial,
            ),
        )
    }

    @Test
    fun `没有最新 locator 时仍使用首次打开位置`() {
        val initial = locator("chapter-1.xhtml")

        assertEquals(
            initial,
            selectNavigatorRestoreLocator(
                activeBookId = "book-a",
                readyBookId = "book-a",
                latestLocator = null,
                initialLocator = initial,
            ),
        )
    }
}
