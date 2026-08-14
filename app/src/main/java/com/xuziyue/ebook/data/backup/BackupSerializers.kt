package com.xuziyue.ebook.data.backup

import kotlinx.serialization.json.Json

/**
 * 备份序列化（DATA-03，范式照抄 [com.xuziyue.ebook.data.export.ExportSerializers]）。
 *
 * - [backupJson] 配 `prettyPrint + encodeDefaults`：确保 schemaVersion 等默认字段显式输出。
 * - [BackupDto.toJson] / [String.parseBackupDto] 往返序列化，备份写 / 恢复读共用同一 Json 实例。
 */
private val backupJson = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true // 向后兼容：未来加字段时旧备份可读
}

/** 序列化为 JSON 字符串。 */
fun BackupDto.toJson(): String = backupJson.encodeToString(BackupDto.serializer(), this)

/** 从 JSON 字符串反序列化。 */
fun String.parseBackupDto(): BackupDto = backupJson.decodeFromString(BackupDto.serializer(), this)
