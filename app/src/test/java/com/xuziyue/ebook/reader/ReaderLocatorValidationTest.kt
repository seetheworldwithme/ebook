package com.xuziyue.ebook.reader

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Href
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReaderLocatorValidationTest {

    private fun locator(href: String): Locator = Locator.fromJSON(
        JSONObject().apply {
            put("href", href)
            put("type", "application/xhtml+xml")
            put("locations", JSONObject().apply { put("totalProgression", 0.1) })
        },
    ) ?: error("测试 Locator 构造失败")

    private fun link(href: String): Link = Link(href = Href(href)!!)

    @Test
    fun `readingOrder 内的 href 可以恢复`() {
        assertTrue(
            isLocatorInReadingOrder(
                locator("OEBPS/chapter-1.xhtml"),
                listOf(link("OEBPS/cover.xhtml"), link("OEBPS/chapter-1.xhtml")),
            ),
        )
    }

    @Test
    fun `同资源带 fragment 的 href 可以恢复`() {
        assertTrue(
            isLocatorInReadingOrder(
                locator("OEBPS/chapter-1.xhtml#paragraph-3"),
                listOf(link("OEBPS/chapter-1.xhtml")),
            ),
        )
    }

    @Test
    fun `另一部书的 href 不可恢复`() {
        assertFalse(
            isLocatorInReadingOrder(
                locator("OEBPS/chapter-52.xhtml"),
                listOf(link("OEBPS/6260297267691793459_11-h-1.htm.html")),
            ),
        )
    }

    @Test
    fun `空 readingOrder 不可恢复`() {
        assertFalse(isLocatorInReadingOrder(locator("chapter-1.xhtml"), emptyList()))
    }
}
