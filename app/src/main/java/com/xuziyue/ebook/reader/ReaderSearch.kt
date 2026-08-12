package com.xuziyue.ebook.reader

import com.xuziyue.ebook.ui.UserMessage
import org.readium.r2.shared.publication.Locator

/**
 * 书内搜索单条结果（READ-05）。
 *
 * 由 [mapLocators] 从 Readium 搜索返回的 [Locator] 映射：
 * - [locator]：命中位置的精确定位（含 href / DOM 范围），点结果时 `navigator.go(locator)` 跳转。
 * - [before] / [highlight] / [after]：取自 `Locator.text`，命中词 [highlight] 用主题色高亮，
 *   [before] / [after] 是上下文，满足 design.md READ-05「显示上下文」。
 */
data class SearchResultItem(
    val locator: Locator,
    val before: String,
    val highlight: String,
    val after: String,
)

/**
 * 书内搜索 UI 状态（READ-05）。
 *
 * - [Idle]：未搜索（首次打开 / 已清空）。
 * - [Loading]：正在执行 `Publication.search` + 取首批结果。
 * - [Results]：已拿到结果（[resultCount] 总数来自 iterator；[items] 已加载批次；
 *   [loadingMore] 分批拉取中；[exhausted] iterator 已遍历完）。
 * - [Error]：搜索失败（如格式不支持 / 解析错误），给用户可理解的消息。
 */
sealed interface SearchUiState {
    data object Idle : SearchUiState

    data class Loading(val query: String) : SearchUiState

    data class Results(
        val query: String,
        val resultCount: Int?,
        val items: List<SearchResultItem>,
        val loadingMore: Boolean,
        val exhausted: Boolean,
    ) : SearchUiState

    data class Error(val message: UserMessage) : SearchUiState
}

/**
 * 把 Readium 搜索返回的 `LocatorCollection.locators` 映射为 [SearchResultItem] 列表（READ-05）。
 *
 * 纯函数，便于单测。`Locator.text` 可空（无文本范围的 Locator），缺失字段兜底空串，
 * UI 渲染 before + 高亮 highlight + after 时不会因 null 崩溃。
 */
fun mapLocators(locators: List<Locator>): List<SearchResultItem> = locators.map { locator ->
    val text = locator.text
    SearchResultItem(
        locator = locator,
        before = text?.before.orEmpty(),
        highlight = text?.highlight.orEmpty(),
        after = text?.after.orEmpty(),
    )
}
