package com.xuziyue.ebook.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Href
import org.readium.r2.shared.publication.Link
import org.robolectric.RobolectricTestRunner

/**
 * [flattenTableOfContents] 单测：扁平化、嵌套 depth 递增、空标题兜底、空列表。
 *
 * Robolectric：[Link] / [Href] / [Url] 依赖 android.net.Uri，纯 JVM stub 会 "not mocked"
 * （与 TypographyMappingsTest 同源，app 模块固定 sdk=34）。
 */
@RunWith(RobolectricTestRunner::class)
class TocFlattenTest {

    private fun link(href: String, title: String?, children: List<Link> = emptyList()): Link =
        Link(href = Href(href)!!, title = title, children = children)

    @Test
    fun `平面目录 depth 全为 0`() {
        val flat = flattenTableOfContents(
            listOf(link("c1.xhtml", "第一章"), link("c2.xhtml", "第二章")),
        )
        assertEquals(listOf("第一章", "第二章"), flat.map { it.title })
        assertTrue(flat.all { it.depth == 0 })
    }

    @Test
    fun `嵌套 children 递增 depth`() {
        val tree = listOf(
            link("c1.xhtml", "第一章", children = listOf(
                link("c1-1.xhtml", "1.1 节"),
                link("c1-2.xhtml", "1.2 节", children = listOf(link("c1-2-1.xhtml", "1.2.1"))),
            )),
            link("c2.xhtml", "第二章"),
        )
        val flat = flattenTableOfContents(tree)
        assertEquals(
            listOf("第一章", "1.1 节", "1.2 节", "1.2.1", "第二章"),
            flat.map { it.title },
        )
        assertEquals(listOf(0, 1, 1, 2, 0), flat.map { it.depth })
    }

    @Test
    fun `空标题用 href 兜底`() {
        val flat = flattenTableOfContents(listOf(link("c1.xhtml", "   ")))
        assertEquals(1, flat.size)
        assertTrue("实际: ${flat[0].title}", flat[0].title.contains("c1.xhtml"))
    }

    @Test
    fun `空目录扁平化为空`() {
        assertTrue(flattenTableOfContents(emptyList()).isEmpty())
    }
}
