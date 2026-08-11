package com.xuziyue.ebook.model

/**
 * 高亮颜色（design.md §6.4 Annotation.color；READ-07）。
 *
 * 引擎无关枚举，存 Room 时取 [name] 字符串（见 BookTypeConverters，改名安全 + 反序列化兜底）。
 * 映射为 Readium Decoration 的 tint 由 `:app` 侧 `toTintColor()` 扩展完成（不在此处依赖 Android Color）。
 * 本刀 UI 暂只用 [YELLOW]（默认），调色板切换留后。
 */
enum class HighlightColor {
    YELLOW,
    GREEN,
    BLUE,
    PINK;

    companion object {
        /** 新建高亮的默认颜色。 */
        val Default: HighlightColor = YELLOW
    }
}
