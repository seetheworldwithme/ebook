package com.xuziyue.ebook.data.db

import androidx.room.TypeConverter
import com.xuziyue.ebook.model.ReadingStatus
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room 类型转换器（design.md §6.4）。
 *
 * - authors：`List<String>` ↔ JSON 字符串（kotlinx.serialization，domain 保持 List）。
 * - status：[ReadingStatus] ↔ name 字符串（改名安全；反序列化失败兜底 [ReadingStatus.UNREAD]）。
 */
class BookTypeConverters {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun authorsToJson(authors: List<String>): String = json.encodeToString(authors)

    @TypeConverter
    fun jsonToAuthors(raw: String): List<String> =
        if (raw.isBlank()) emptyList() else json.decodeFromString(raw)

    @TypeConverter
    fun statusToString(status: ReadingStatus): String = status.name

    @TypeConverter
    fun stringToStatus(raw: String): ReadingStatus =
        runCatching { ReadingStatus.valueOf(raw) }.getOrDefault(ReadingStatus.UNREAD)
}
