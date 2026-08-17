package com.xuziyue.ebook.data

import com.xuziyue.ebook.model.ReaderTypography
import org.json.JSONObject

/**
 * 按书排版覆盖的持久化包装层（TYPE-05，沿用 [PersistedLocator] 范式）。
 *
 * 只序列化**非 null 字段**（partial override）：某字段在这本书上没被显式改过就不落盘，
 * 合并时继续跟随全局值——全局改字号，所有未覆盖字号的书一起变，符合直觉。
 *
 * 持久化格式（`overridesJson` 列）：
 * ```
 * {"schemaVersion": 1, "fontSize": 1.3, "theme": "DARK", ...}   // 仅含本书覆盖的字段
 * ```
 * 枚举存 name（与 ReaderTypographyRepository 一致）；解析失败的字段静默丢弃（降级跟随全局，不崩）。
 *
 * 「无效排版不导致书籍无法打开」（TYPE-05 验收）：本层任何解析错误都吞掉返回部分结果，
 * 最终防线是 [merge] 对 null 字段回退 global 的语义。
 */
data class BookTypographyOverrides(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val values: ReaderTypography,
) {

    fun toJsonString(): String = JSONObject().apply {
        put(SCHEMA_VERSION_KEY, schemaVersion)
        with(values) {
            fontSize?.let { put(FONT_SIZE, it) }
            fontFamily?.let { put(FONT_FAMILY, it) }
            fontWeight?.let { put(FONT_WEIGHT, it) }
            lineHeight?.let { put(LINE_HEIGHT, it) }
            paragraphSpacing?.let { put(PARAGRAPH_SPACING, it) }
            pageMargins?.let { put(PAGE_MARGINS, it) }
            textAlign?.let { put(TEXT_ALIGN, it.name) }
            theme?.let { put(THEME, it.name) }
            scroll?.let { put(SCROLL, it.name) }
            // 非 nullable 布尔开关：覆盖时必落盘（与其它字段不同，false 也是有效覆盖值）。
            put(VOLUME_KEY_PAGING, volumeKeyPaging)
        }
    }.toString()

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        private const val SCHEMA_VERSION_KEY = "schemaVersion"
        private const val FONT_SIZE = "fontSize"
        private const val FONT_FAMILY = "fontFamily"
        private const val FONT_WEIGHT = "fontWeight"
        private const val LINE_HEIGHT = "lineHeight"
        private const val PARAGRAPH_SPACING = "paragraphSpacing"
        private const val PAGE_MARGINS = "pageMargins"
        private const val TEXT_ALIGN = "textAlign"
        private const val THEME = "theme"
        private const val SCROLL = "scroll"
        private const val VOLUME_KEY_PAGING = "volumeKeyPaging"

        /** 空覆盖（本书未改过任何字段；等价无行）。 */
        val Empty = BookTypographyOverrides(values = ReaderTypography())

        /** 解析存储字符串；格式不合法返回 [Empty]（TYPE-05 验收：坏数据不挡书打开）。 */
        fun fromJsonString(raw: String?): BookTypographyOverrides {
            if (raw.isNullOrBlank()) return Empty
            return runCatching {
                val obj = JSONObject(raw)
                BookTypographyOverrides(
                    schemaVersion = obj.optInt(SCHEMA_VERSION_KEY, CURRENT_SCHEMA_VERSION),
                    values = ReaderTypography(
                        fontSize = if (obj.has(FONT_SIZE)) obj.getDouble(FONT_SIZE) else null,
                        fontFamily = if (obj.has(FONT_FAMILY)) obj.getString(FONT_FAMILY) else null,
                        fontWeight = if (obj.has(FONT_WEIGHT)) obj.getDouble(FONT_WEIGHT) else null,
                        lineHeight = if (obj.has(LINE_HEIGHT)) obj.getDouble(LINE_HEIGHT) else null,
                        paragraphSpacing = if (obj.has(PARAGRAPH_SPACING)) obj.getDouble(PARAGRAPH_SPACING) else null,
                        pageMargins = if (obj.has(PAGE_MARGINS)) obj.getDouble(PAGE_MARGINS) else null,
                        // 枚举用 name 存取，改名 / 坏值 → null（降级跟随全局，不崩）。
                        textAlign = if (obj.has(TEXT_ALIGN)) obj.getString(TEXT_ALIGN).toEnum<com.xuziyue.ebook.model.ReaderTextAlign>() else null,
                        theme = if (obj.has(THEME)) obj.getString(THEME).toEnum<com.xuziyue.ebook.model.ReaderTheme>() else null,
                        scroll = if (obj.has(SCROLL)) obj.getString(SCROLL).toEnum<com.xuziyue.ebook.model.ReaderScrollMode>() else null,
                        volumeKeyPaging = obj.optBoolean(VOLUME_KEY_PAGING, true),
                    ),
                )
            }.getOrElse { Empty }
        }

        private inline fun <reified E : Enum<E>> String?.toEnum(): E? =
            this?.let { runCatching { enumValueOf<E>(it) }.getOrNull() }
    }
}

/**
 * 全局排版 + 本书覆盖 → 本书生效排版的**合并纯函数**（TYPE-05 核心）。
 *
 * 语义：覆盖里非默认的字段覆盖全局同名字段；`volumeKeyPaging` 是非 nullable 布尔，
 * 覆盖层存的是「是否显式改过」——[BookTypographyOverrides.Empty] 或未覆盖时跟全局 true。
 */
fun mergeTypography(global: ReaderTypography, override: ReaderTypography): ReaderTypography =
    ReaderTypography(
        fontSize = override.fontSize ?: global.fontSize,
        fontFamily = override.fontFamily ?: global.fontFamily,
        fontWeight = override.fontWeight ?: global.fontWeight,
        lineHeight = override.lineHeight ?: global.lineHeight,
        paragraphSpacing = override.paragraphSpacing ?: global.paragraphSpacing,
        pageMargins = override.pageMargins ?: global.pageMargins,
        textAlign = override.textAlign ?: global.textAlign,
        theme = override.theme ?: global.theme,
        scroll = override.scroll ?: global.scroll,
        volumeKeyPaging = override.volumeKeyPaging,
    )
