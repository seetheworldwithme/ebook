package com.xuziyue.ebook.reader

import com.xuziyue.ebook.ui.UserMessage
import org.readium.r2.shared.publication.Publication

/**
 * Reader 屏幕 UI 状态。
 *
 * - [Loading]：正在打开 Publication（首次打开 / 进程重建后重 open）。
 * - [Ready]：打开成功，可按 [NavigatorSpec] 创建对应格式的 Navigator Fragment。
 * - [Error]：打开失败，给用户可理解的提示（CLAUDE.md：建立明确错误类型）。
 */
sealed interface ReaderUiState {

    data object Loading : ReaderUiState

    data class Ready(
        /** 此 Publication 对应的书籍，供 Fragment 判定 Navigator 能否安全复用。 */
        val bookId: String,
        val publication: Publication,
        /** Navigator 创建规约（V1 PDF/CBZ：按格式封装差异化创建参数）。 */
        val navigatorSpec: NavigatorSpec,
    ) : ReaderUiState

    data class Error(val message: UserMessage) : ReaderUiState
}
