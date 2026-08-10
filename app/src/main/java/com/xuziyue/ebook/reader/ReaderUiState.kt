package com.xuziyue.ebook.reader

import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

/**
 * Reader 屏幕 UI 状态。
 *
 * - [Loading]：正在打开 Publication（首次打开 / 进程重建后重 open）。
 * - [Ready]：打开成功，可创建 EpubNavigatorFragment。
 * - [Error]：打开失败，给用户可理解的提示（CLAUDE.md：建立明确错误类型）。
 */
sealed interface ReaderUiState {

    data object Loading : ReaderUiState

    data class Ready(
        val publication: Publication,
        val navigatorFactory: EpubNavigatorFactory,
        /** 恢复位置（CLAUDE.md 红线 #1：Locator 为主数据）。null 从头读。 */
        val initialLocator: Locator?,
        val preferences: EpubPreferences,
    ) : ReaderUiState

    data class Error(val message: String) : ReaderUiState
}
