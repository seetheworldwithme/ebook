package com.xuziyue.ebook.reader

import android.graphics.Color
import androidx.compose.ui.graphics.Color as ComposeColor
import com.xuziyue.ebook.model.HighlightColor

/**
 * [HighlightColor] → Readium Decoration tint（design.md §6.4 → P0V-02 渲染通路）。
 *
 * Readium `Decoration.Style.Highlight(tint)` 取 `android.graphics.Color`（Int），
 * 与现有 ReaderViewModel 高亮渲染一致。
 *
 * SET-02：YELLOW tint 原为纯 `Color.YELLOW`（#FFFF00）与 [toComposeColor] 的 `#FFEB3B` 不一致，
 * 统一为 Material Yellow 500（#FFEB3B），与其它三色同源（避免正文高亮过亮刺眼）。
 */
fun HighlightColor.toTintColor(): Int = when (this) {
    HighlightColor.YELLOW -> Color.parseColor("#FFEB3B")
    HighlightColor.GREEN -> Color.parseColor("#66BB6A") // 柔绿，避免纯绿刺眼
    HighlightColor.BLUE -> Color.parseColor("#42A5F5")
    HighlightColor.PINK -> Color.parseColor("#EC407A")
}

/** [HighlightColor] → Compose Color（批注列表色点用）。色值与 [toTintColor] 对齐。 */
fun HighlightColor.toComposeColor(): ComposeColor = when (this) {
    HighlightColor.YELLOW -> ComposeColor(0xFFFFEB3B)
    HighlightColor.GREEN -> ComposeColor(0xFF66BB6A)
    HighlightColor.BLUE -> ComposeColor(0xFF42A5F5)
    HighlightColor.PINK -> ComposeColor(0xFFEC407A)
}

