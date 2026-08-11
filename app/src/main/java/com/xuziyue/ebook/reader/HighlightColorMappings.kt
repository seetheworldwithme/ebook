package com.xuziyue.ebook.reader

import android.graphics.Color
import androidx.compose.ui.graphics.Color as ComposeColor
import com.xuziyue.ebook.model.HighlightColor

/**
 * [HighlightColor] → Readium Decoration tint（design.md §6.4 → P0V-02 渲染通路）。
 *
 * Readium `Decoration.Style.Highlight(tint)` 取 `android.graphics.Color`（Int），
 * 与现有 ReaderViewModel 高亮渲染一致。本刀 UI 暂只用 YELLOW；调色板接入时直接扩这里。
 */
fun HighlightColor.toTintColor(): Int = when (this) {
    HighlightColor.YELLOW -> Color.YELLOW
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

