package com.xuziyue.ebook.data.export

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 导出文件 DTO（DATA-01，design.md L150）。
 *
 * - 顶层 [ExportDto.schemaVersion] 是**导出文件结构版本**（[EXPORT_SCHEMA_VERSION]），
 *   与 [com.xuziyue.ebook.data.PersistedLocator] 内层的 Locator schema 版本**区分、独立**。
 *   未来导出结构演进（增删字段）时递增，读取方按版本兼容。
 * - `locator` 字段一律保留 [PersistedLocator] 包装原文（含其内层 schemaVersion），经
 *   `Json.parseToJsonElement(rawLocatorJson)` 解析为 [JsonElement] 嵌入，确保定位信息原样可迁（红线 #1）。
 * - `color` 存 [com.xuziyue.ebook.model.HighlightColor] 的 name 字符串（与 Room 持久化口径一致），
 *   避免 core/model 模块未启用 kotlinx.serialization plugin 的跨模块序列化依赖。
 *
 * JSON 结构对齐 DATA-01 验收口径：稳定 schema 版本 + 书籍 ID + Locator + 时间戳。
 */
@Serializable
data class ExportDto(
    val schemaVersion: Int = EXPORT_SCHEMA_VERSION,
    val exportedAt: Long,
    val book: BookDto,
    val progress: ProgressDto? = null,
    val bookmarks: List<BookmarkDto> = emptyList(),
    val annotations: List<AnnotationDto> = emptyList(),
)

@Serializable
data class BookDto(
    val id: String,
    val title: String,
    val authors: List<String> = emptyList(),
    val format: String,
)

@Serializable
data class ProgressDto(
    val locator: JsonElement,
    val progression: Double? = null,
    val updatedAt: Long,
)

@Serializable
data class BookmarkDto(
    val id: String,
    val locator: JsonElement,
    val excerpt: String? = null,
    val createdAt: Long,
)

@Serializable
data class AnnotationDto(
    val id: String,
    val locator: JsonElement,
    val selectedText: String,
    val note: String? = null,
    val color: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** 导出文件结构版本（独立于 [com.xuziyue.ebook.data.PersistedLocator.CURRENT_SCHEMA_VERSION]）。 */
const val EXPORT_SCHEMA_VERSION: Int = 1
