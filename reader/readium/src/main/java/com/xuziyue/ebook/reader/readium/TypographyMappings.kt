package com.xuziyue.ebook.reader.readium

import com.xuziyue.ebook.model.ReaderScrollMode
import com.xuziyue.ebook.model.ReaderTextAlign
import com.xuziyue.ebook.model.ReaderTheme
import com.xuziyue.ebook.model.ReaderTypography
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * 引擎无关 [ReaderTypography] → Readium [EpubPreferences] 映射。
 *
 * Readium 知识内聚在 :reader:readium 模块（沿用 `Publication.toReaderCapabilities()` 先例）；
 * :app / :core:model 不感知 Readium 类型。
 *
 * 关键点：[ReaderTheme.SYSTEM] 无对应 Readium 枚举——在此据 [isSystemDark]
 * 解析为 [Theme.DARK] / [Theme.LIGHT]。SYSTEM 持久化是稳定值，运行时每次解析，
 * 故系统暗色切换后重新调用本映射即可得到正确主题（design.md §4.4 TYPE-02「跟随系统」）。
 *
 * @param isSystemDark 当前系统是否暗色模式（由 UI 层用 `isSystemInDarkTheme()` 传入）。
 */
@OptIn(ExperimentalReadiumApi::class)
fun ReaderTypography.toEpubPreferences(isSystemDark: Boolean): EpubPreferences =
    EpubPreferences(
        fontSize = fontSize,
        fontFamily = fontFamily?.let { FontFamily(it) },
        fontWeight = fontWeight,
        lineHeight = lineHeight,
        paragraphSpacing = paragraphSpacing,
        pageMargins = pageMargins,
        textAlign = textAlign?.toReadium(),
        theme = theme?.toReadium(isSystemDark),
        // READ-04：scroll=true 纵向滚动 / null·false 分页（引擎默认）。
        scroll = scroll?.toReadium(),
    )

@OptIn(ExperimentalReadiumApi::class)
private fun ReaderTextAlign.toReadium(): TextAlign = when (this) {
    ReaderTextAlign.START -> TextAlign.START
    ReaderTextAlign.JUSTIFY -> TextAlign.JUSTIFY
}

@OptIn(ExperimentalReadiumApi::class)
private fun ReaderTheme.toReadium(isSystemDark: Boolean): Theme = when (this) {
    ReaderTheme.LIGHT -> Theme.LIGHT
    ReaderTheme.SEPIA -> Theme.SEPIA
    ReaderTheme.DARK -> Theme.DARK
    // 跟随系统：运行时据系统暗色解析为 LIGHT / DARK（SEPIA 无系统语义，不参与）。
    ReaderTheme.SYSTEM -> if (isSystemDark) Theme.DARK else Theme.LIGHT
}

/**
 * READ-04 翻页方式映射：PAGINATED→分页（scroll=false）、SCROLL→纵向滚动（scroll=true）。
 */
@OptIn(ExperimentalReadiumApi::class)
private fun ReaderScrollMode.toReadium(): Boolean = when (this) {
    ReaderScrollMode.PAGINATED -> false
    ReaderScrollMode.SCROLL -> true
}
