package com.xuziyue.ebook.reader

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.robolectric.RobolectricTestRunner

/**
 * [mapLocators] 纯函数单测（READ-05）：Locator.text → before/highlight/after 映射 + 空兜底。
 *
 * 用 `Locator.fromJSON` 构造测试 Locator（依赖 android.net.Uri，放 app 模块用 Robolectric sdk=34，
 * 沿用 AnnotationRepositoryTest 同款范式）。
 */
@RunWith(RobolectricTestRunner::class)
class ReaderSearchTest {

    /** 构造带可选 text 字段的 Locator（任一为 null 则该键不写入 JSON）。 */
    private fun locator(
        href: String = "ch1",
        before: String? = null,
        highlight: String? = null,
        after: String? = null,
    ): Locator {
        val json = JSONObject().apply {
            put("href", href)
            put("type", "text/html")
            put("text", JSONObject().apply {
                before?.let { put("before", it) }
                highlight?.let { put("highlight", it) }
                after?.let { put("after", it) }
            })
        }
        return Locator.fromJSON(json) ?: error("测试 Locator 构造失败")
    }

    @Test
    fun `mapLocators 完整 text 映射 before highlight after`() {
        val items = mapLocators(listOf(locator(before = "前面的文字", highlight = "命中词", after = "后面的文字")))

        assertEquals(1, items.size)
        assertEquals("前面的文字", items[0].before)
        assertEquals("命中词", items[0].highlight)
        assertEquals("后面的文字", items[0].after)
    }

    @Test
    fun `mapLocators 部分字段缺失时其余兜底空串`() {
        // 只有 highlight，before/after 缺失（text 键存在但子键不全）。
        val items = mapLocators(listOf(locator(highlight = "词")))

        assertEquals("", items[0].before)
        assertEquals("词", items[0].highlight)
        assertEquals("", items[0].after)
    }

    @Test
    fun `mapLocators Locator 无 text 字段时三字段兜底空串`() {
        // Locator 完全无 text → locator.text = null，三字段兜底 ""。
        val noText = Locator.fromJSON(
            JSONObject().apply { put("href", "ch1"); put("type", "text/html") },
        ) ?: error("测试 Locator 构造失败")

        val items = mapLocators(listOf(noText))

        assertEquals("", items[0].before)
        assertEquals("", items[0].highlight)
        assertEquals("", items[0].after)
    }

    @Test
    fun `mapLocators 空列表返回空`() {
        assertEquals(emptyList<SearchResultItem>(), mapLocators(emptyList()))
    }

    @Test
    fun `mapLocators 保留原 locator 供跳转寻址`() {
        val src = locator(href = "chapter-2.xhtml", highlight = "x")
        val items = mapLocators(listOf(src))

        // 映射后 locator 与源同对象，点结果时 navigator.go(item.locator) 能定位。
        assertEquals("chapter-2.xhtml", items[0].locator.href.toString())
    }
}
