package com.xuziyue.ebook.data.db

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 校验 Room schema 已导出（CLAUDE.md 红线 #6）。
 *
 * CI 主战场（纯 JVM，无需 Robolectric）。schema 由 KSP 写入 `app/schemas/`，
 * 经 `sourceSets.test.resources` 进 classpath，这里从 classpath 读取断言。
 */
class SchemaExportedTest {

    @Test
    fun `schema 1 json 已导出且含 books 与 reading_progress 实体`() {
        // sourceSets.test.resources.srcDir(schemas) 把 schemas 内容映射到 classpath 根，
        // 故路径去掉 schemas/ 前缀。
        val path = "com.xuziyue.ebook.data.db.BookDatabase/1.json"
        val json = javaClass.classLoader!!.getResourceAsStream(path)
            ?.bufferedReader()?.use { it.readText() }
            ?: error("未找到 classpath:$path；确认 ksp room.schemaLocation 配置且已先跑 assembleDebug 生成 schema")
        val root = JSONObject(json)
        assertEquals(1, root.getInt("formatVersion"))
        val entities = root.getJSONObject("database").getJSONArray("entities")
        assertEquals(2, entities.length())
        val names = (0 until entities.length()).map { entities.getJSONObject(it).getString("tableName") }
        assertTrue("缺少 books 表", names.contains("books"))
        assertTrue("缺少 reading_progress 表", names.contains("reading_progress"))
    }
}
