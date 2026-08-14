package com.xuziyue.ebook.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 全量备份 DTO（DATA-03，design.md L152）。
 *
 * 备份范围：数据库全表（books / reading_progress / bookmarks / annotations / reading_sessions）
 * + 设置快照（DataStore preferences）。书籍文件 / 封面作为 ZIP 内独立条目打包（见 [BackupUseCase]）。
 *
 * - 顶层 [schemaVersion] 是**备份文件结构版本**（[BACKUP_SCHEMA_VERSION]），与数据库 migration 版本区分。
 *   未来备份结构演进时递增，读取方按版本兼容。
 * - `locatorJson` 一律**原样存 raw 字符串**（含 PersistedLocator 内层 schemaVersion），备份是全量可逆还原，
 *   保留原始 JSON 字符串最安全（与 DATA-01 导出把 locator 解析成 JsonElement 的取舍不同——备份要原样塞回 DAO）。
 * - `settings` 存 DataStore 的 `asMap()` 快照：key 名 → 值（JsonElement 兼容 bool/double/string）。
 *
 * @property books 全部书籍行。
 * @property readingProgress 全部进度行。
 * @property bookmarks 全部书签行。
 * @property annotations 全部批注行（含软删）。
 * @property readingSessions 全部阅读会话行（DATA-04）。
 * @property settings DataStore preferences 快照（排版 / 显示 / 应用设置）。
 */
@Serializable
data class BackupDto(
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    val exportedAt: Long,
    val books: List<BookRow>,
    val readingProgress: List<ProgressRow>,
    val bookmarks: List<BookmarkRow>,
    val annotations: List<AnnotationRow>,
    val readingSessions: List<SessionRow>,
    val collections: List<CollectionRow> = emptyList(),
    val collectionBooks: List<CollectionBookRow> = emptyList(),
    val settings: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class BookRow(
    val id: String,
    val contentHash: String,
    val title: String,
    val authors: List<String>,
    val description: String?,
    val language: String?,
    val format: String,
    val mediaType: String,
    val filePath: String, // 含 {hash}.{ext} 文件名，恢复时按此定位 ZIP 内 books/ 条目
    val fileSize: Long,
    val coverPath: String?,
    val importedAt: Long,
    val lastOpenedAt: Long?,
    val status: String,
)

@Serializable
data class ProgressRow(
    val bookId: String,
    val locatorJson: String,
    val progression: Double?,
    val updatedAt: Long,
    val deviceId: String?,
)

@Serializable
data class BookmarkRow(
    val id: String,
    val bookId: String,
    val locatorJson: String,
    val excerpt: String?,
    val createdAt: Long,
)

@Serializable
data class AnnotationRow(
    val id: String,
    val bookId: String,
    val locatorJson: String,
    val selectedText: String,
    val note: String?,
    val color: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Serializable
data class SessionRow(
    val id: String,
    val bookId: String,
    val startedAt: Long,
    val endedAt: Long,
    val activeSeconds: Long,
)

@Serializable
data class CollectionRow(
    val id: String,
    val name: String,
    val sortOrder: Long,
    val createdAt: Long,
    val kind: String, // CollectionKind.name（SYSTEM_FAVORITE / CUSTOM）
)

@Serializable
data class CollectionBookRow(
    val collectionId: String,
    val bookId: String,
    val addedAt: Long,
)

/** 备份文件结构版本（独立于数据库 migration 版本与 PersistedLocator schema 版本）。 */
const val BACKUP_SCHEMA_VERSION: Int = 1
