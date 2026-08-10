package com.xuziyue.ebook.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * PersistedLocator 包装层单测：schemaVersion 保留、序列化往返、坏 JSON 返回 null。
 */
class PersistedLocatorTest {

    // 模拟一段 Readium Locator JSON（结构对齐 Locator.toJSON，无需真实可解析）。
    private val sampleLocatorJson =
        """{"href":"chapter1.xhtml","type":"text/html","locations":{"progression":0.5}}"""

    @Test
    fun `toJsonString 包含 schemaVersion=1 与原样 locatorJson`() {
        val persisted = PersistedLocator(locatorJson = sampleLocatorJson)
        val obj = JSONObject(persisted.toJsonString())

        assertEquals(1, obj.getInt("schemaVersion"))
        assertEquals(sampleLocatorJson, obj.getString("locatorJson"))
    }

    @Test
    fun `fromJsonString 往返一致`() {
        val original = PersistedLocator(schemaVersion = 1, locatorJson = sampleLocatorJson)
        val roundTripped = PersistedLocator.fromJsonString(original.toJsonString())

        assertEquals(original, roundTripped)
    }

    @Test
    fun `fromJsonString 坏 JSON 返回 null`() {
        assertNull(PersistedLocator.fromJsonString("not a json"))
        assertNull(PersistedLocator.fromJsonString("{broken"))
    }

    @Test
    fun `fromJsonString 缺 schemaVersion 时默认 1`() {
        val raw = """{"locatorJson":"{}"}"""
        val parsed = PersistedLocator.fromJsonString(raw)

        assertNotNull(parsed)
        assertEquals(1, parsed!!.schemaVersion)
        assertEquals("{}", parsed.locatorJson)
    }

    @Test
    fun `schemaVersion 升级假设 v2 仍能解析 locatorJson`() {
        // 模拟未来 v2 数据：schemaVersion 不同，但 locatorJson 字段名不变，解析不丢。
        // 注意 locatorJson 是「Locator JSON 字符串」，须用 JSONObject 正确转义内层引号。
        val raw = JSONObject().apply {
            put("schemaVersion", 2)
            put("locatorJson", sampleLocatorJson)
        }.toString()
        val parsed = PersistedLocator.fromJsonString(raw)

        assertNotNull(parsed)
        assertEquals(2, parsed!!.schemaVersion)
        assertEquals(sampleLocatorJson, parsed.locatorJson)
    }
}
