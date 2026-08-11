package com.xuziyue.ebook.reader

import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

/** 目录单项（扁平化自 publication.tableOfContents，[depth] 表示嵌套层级，UI 据此缩进）。 */
data class TocItem(
    val title: String,
    val link: Link,
    val depth: Int,
)

/**
 * 把 publication.tableOfContents（可能嵌套 [Link.getChildren]）扁平化为带 [TocItem.depth] 的列表。
 * [depth] 用于 UI 缩进；标题缺失（空）时用 href 兜底，避免空白行。
 */
fun flattenTableOfContents(links: List<Link>, depth: Int = 0): List<TocItem> {
    val out = ArrayList<TocItem>(links.size)
    for (link in links) {
        val title = link.title?.takeIf { it.isNotBlank() } ?: link.href.toString()
        out.add(TocItem(title = title, link = link, depth = depth))
        val children = link.children
        if (children.isNotEmpty()) {
            out += flattenTableOfContents(children, depth + 1)
        }
    }
    return out
}

/**
 * Reader 跳转指令（VM 发出 → [ReaderFragment] 执行 navigator 调用）。
 *
 * navigator 与 publication 分居 Fragment / VM，用单向事件流解耦（沿用 preferences / decorations 范式）。
 * READ-02：目录跳转 / 进度拖动 / 返回上一阅读位置。
 */
sealed interface ReaderNavCommand {
    /** 目录章节跳转：navigator.go(link)。 */
    data class GoToLink(val link: Link) : ReaderNavCommand

    /** 全书进度拖动（0.0..1.0）：publication.locateProgression → navigator.go。 */
    data class GoToProgression(val progress: Double) : ReaderNavCommand

    /** 返回上一阅读位置（跳转前已 push 到 history 栈的 Locator）。 */
    data class GoBack(val locator: Locator) : ReaderNavCommand
}
