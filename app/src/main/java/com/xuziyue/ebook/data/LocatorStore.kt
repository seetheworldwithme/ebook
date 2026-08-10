package com.xuziyue.ebook.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import org.readium.r2.shared.publication.Locator

/**
 * Locator 持久化存储（Phase 0 用 DataStore Preferences；Room 留 Phase 1）。
 *
 * 以 [contentHash] 为索引：一本书一条 Locator + 一条文件路径。
 * - Locator 存为 [PersistedLocator]（带 schemaVersion）的 JSON 字符串。
 * - 文件路径用于进程重建后重新 [打开][org.readium.r2.streamer.PublicationOpener]。
 */
class LocatorStore(private val dataStore: DataStore<Preferences>) {

    private fun locatorKey(contentHash: String) = stringPreferencesKey("locator_$contentHash")
    private fun filePathKey(contentHash: String) = stringPreferencesKey("filepath_$contentHash")

    /** 保存阅读位置（Locator）。 */
    suspend fun saveLocator(contentHash: String, locator: Locator) {
        val persisted = PersistedLocator.from(locator).toJsonString()
        dataStore.edit { it[locatorKey(contentHash)] = persisted }
    }

    /** 读取阅读位置；无记录返回 null。 */
    suspend fun readLocator(contentHash: String): Locator? {
        val raw = dataStore.data.first()[locatorKey(contentHash)] ?: return null
        return PersistedLocator.fromJsonString(raw)?.toLocator()
    }

    /** 保存 EPUB 文件绝对路径（进程重建后据此重 open）。 */
    suspend fun saveFilePath(contentHash: String, path: String) {
        dataStore.edit { it[filePathKey(contentHash)] = path }
    }

    /** 读取 EPUB 文件绝对路径；无记录返回 null。 */
    suspend fun readFilePath(contentHash: String): String? =
        dataStore.data.first()[filePathKey(contentHash)]
}
