package com.xuziyue.ebook.reader

import org.readium.r2.shared.publication.Locator

/** 当前 Ready 状态到来时，ReaderFragment 应如何处理已绑定的 Navigator。 */
internal enum class NavigatorUpdate {
    CREATE,
    KEEP,
    REPLACE,
}

/**
 * Navigator 与 Publication 必须属于同一本书；跨书复用会让正文、进度和持久化目标互相串写。
 */
internal fun navigatorUpdate(boundBookId: String?, readyBookId: String): NavigatorUpdate = when {
    boundBookId == null -> NavigatorUpdate.CREATE
    boundBookId == readyBookId -> NavigatorUpdate.KEEP
    else -> NavigatorUpdate.REPLACE
}

/** 旧 Navigator 在切书后的迟到回调不得更新当前书进度。 */
internal fun acceptsLocator(activeBookId: String?, sourceBookId: String): Boolean =
    activeBookId != null && activeBookId == sourceBookId

/** 同一本书退出重进时，新的 Navigator 应从本次会话的最新位置恢复。 */
internal fun selectNavigatorRestoreLocator(
    activeBookId: String?,
    readyBookId: String,
    latestLocator: Locator?,
    initialLocator: Locator?,
): Locator? = if (acceptsLocator(activeBookId, readyBookId)) {
    latestLocator ?: initialLocator
} else {
    initialLocator
}
