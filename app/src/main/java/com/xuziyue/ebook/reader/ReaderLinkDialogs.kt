package com.xuziyue.ebook.reader

import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.AbsoluteUrl

/**
 * READ-09 链接交互的 UI 状态（脚注弹层 / 内链确认 / 外链确认）。
 *
 * Readium HyperlinkNavigator 的事件路由（源码级调研结论）：
 * - 点脚注（`a[epub:type=noteref]`）→ 库已用 jsoup 取出目标 `aside#id` 内容并经
 *   `Jsoup.clean(Safelist.relaxed())` 清洗 → `shouldFollowInternalLink(link, FootnoteContext)`：
 *   返回 true 走页内跳转；返回 false 交 app 自行展示 → 我们拦截展示弹层。
 * - 点普通内链 → `shouldFollowInternalLink(link, LinkContext)`（无脚注内容）→ 拦截后弹确认
 *   （EPUB2 无 epub:type 的旧式脚注也走这条路）。
 * - 点外链 → `onExternalLinkActivated(url)` → 必须用户确认后才交系统浏览器（design.md §7，
 *   「外链由系统浏览器在用户确认后打开」，红线 #4 不在 WebView 内打开）。
 */
sealed interface LinkDialog {

    /** 脚注弹层：[contentHtml] 是库清洗后的脚注 HTML 片段（Safelist.relaxed，非可信全网页）。 */
    data class Footnote(val contentHtml: String) : LinkDialog

    /** 内链确认：跳转到 [link] 指向的书内位置（如旧式脚注 / 交叉引用）。 */
    data class InternalLink(val link: Link) : LinkDialog

    /** 外链确认：[url] 经用户确认后交系统浏览器打开。 */
    data class ExternalLink(val url: AbsoluteUrl) : LinkDialog
}

/**
 * 把 Readium 的内链询问折叠成 UI 状态：
 * 带 `FootnoteContext.noteContent` → 脚注弹层（内容空时降级内链确认，覆盖 EPUB2 旧式脚注）；
 * 无上下文 → 内链确认。
 */
internal fun internalLinkDialog(link: Link, footnoteContent: String?): LinkDialog =
    if (!footnoteContent.isNullOrBlank()) {
        LinkDialog.Footnote(contentHtml = footnoteContent)
    } else {
        LinkDialog.InternalLink(link = link)
    }
