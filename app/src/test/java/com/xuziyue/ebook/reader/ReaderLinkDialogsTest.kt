package com.xuziyue.ebook.reader

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Href
import org.robolectric.RobolectricTestRunner

/**
 * internalLinkDialog 折叠纯函数单测（READ-09：脚注弹层 / 内链确认分流）。
 */
@RunWith(RobolectricTestRunner::class)
class ReaderLinkDialogsTest {

    private fun link(href: String = "notes.xhtml#n1", title: String? = "1"): Link =
        Link(href = Href(href)!!, title = title)

    @Test
    fun `有脚注内容时折叠为 Footnote 弹层`() {
        val dialog = internalLinkDialog(link(), footnoteContent = "<p>脚注正文</p>")
        assertTrue(dialog is LinkDialog.Footnote)
        assertEquals("<p>脚注正文</p>", (dialog as LinkDialog.Footnote).contentHtml)
    }

    @Test
    fun `脚注内容为空白时降级内链确认`() {
        assertTrue(internalLinkDialog(link(), footnoteContent = "   ") is LinkDialog.InternalLink)
        assertTrue(internalLinkDialog(link(), footnoteContent = null) is LinkDialog.InternalLink)
    }

    @Test
    fun `无上下文的普通内链走确认`() {
        val dialog = internalLinkDialog(link(href = "chapter-2.xhtml"), footnoteContent = null)
        assertTrue(dialog is LinkDialog.InternalLink)
        assertEquals("chapter-2.xhtml", (dialog as LinkDialog.InternalLink).link.href.toString())
    }
}
