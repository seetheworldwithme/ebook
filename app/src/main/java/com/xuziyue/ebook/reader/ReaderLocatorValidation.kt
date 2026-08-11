package com.xuziyue.ebook.reader

import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

/** 仅允许恢复属于当前 Publication 阅读顺序的 Locator。 */
internal fun isLocatorInReadingOrder(locator: Locator, readingOrder: List<Link>): Boolean {
    val locatorHref = locator.href.toString().normalizedResourceHref()
    return readingOrder.any { link ->
        link.href.toString().normalizedResourceHref() == locatorHref
    }
}

private fun String.normalizedResourceHref(): String =
    substringBefore('#')
        .substringBefore('?')
        .removePrefix("./")
        .removePrefix("/")
