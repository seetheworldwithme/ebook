package com.xuziyue.ebook.data

import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

/**
 * Locator 持久化包装层。
 *
 * Readium 的 [Locator.toJSON] 输出的 JSON 不带 schemaVersion（对齐 CLAUDE.md 红线 #1
 * 与数据模型要求「Locator JSON 一律原样保存并附带 schema version，便于未来迁移与同步」）。
 * 本类在外层补上 schemaVersion，未来 Readium 升级若改了 Locator 结构，可按 schemaVersion 迁移。
 *
 * 持久化格式：
 * ```
 * {"schemaVersion": 1, "locatorJson": "<Readium Locator 的 toJSON 字符串>"}
 * ```
 */
data class PersistedLocator(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val locatorJson: String,
) {

    /** 序列化为存储用字符串。 */
    fun toJsonString(): String = JSONObject().apply {
        put(SCHEMA_VERSION_KEY, schemaVersion)
        put(LOCATOR_JSON_KEY, locatorJson)
    }.toString()

    /** 反序列化回 Readium [Locator]；locatorJson 缺失或格式错返回 null。 */
    fun toLocator(): Locator? = runCatching {
        Locator.fromJSON(JSONObject(locatorJson))
    }.getOrNull()

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        private const val SCHEMA_VERSION_KEY = "schemaVersion"
        private const val LOCATOR_JSON_KEY = "locatorJson"

        /** 从 Readium [Locator] 构造持久化对象。 */
        fun from(locator: Locator): PersistedLocator =
            PersistedLocator(locatorJson = locator.toJSON().toString())

        /** 解析存储字符串；格式不合法返回 null。 */
        fun fromJsonString(raw: String): PersistedLocator? = runCatching {
            val obj = JSONObject(raw)
            PersistedLocator(
                schemaVersion = obj.optInt(SCHEMA_VERSION_KEY, CURRENT_SCHEMA_VERSION),
                locatorJson = obj.optString(LOCATOR_JSON_KEY),
            )
        }.getOrNull()
    }
}
