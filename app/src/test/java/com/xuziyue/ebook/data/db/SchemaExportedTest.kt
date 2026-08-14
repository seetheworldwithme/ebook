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
        val names = tableNames("com.xuziyue.ebook.data.db.BookDatabase/1.json")
        // v1 基线（迁移起点，必须随仓库提交供 migration 测试读取）。
        assertTrue("缺少 books 表", names.contains("books"))
        assertTrue("缺少 reading_progress 表", names.contains("reading_progress"))
    }

    @Test
    fun `schema 2 json 已导出且含 bookmarks 与 annotations 实体`() {
        val names = tableNames("com.xuziyue.ebook.data.db.BookDatabase/2.json")
        assertEquals(4, names.size) // books + reading_progress + bookmarks + annotations
        assertTrue("缺少 bookmarks 表（READ-06）", names.contains("bookmarks"))
        assertTrue("缺少 annotations 表（READ-07）", names.contains("annotations"))
    }

    @Test
    fun `schema 3 json 已导出且含 reading_sessions 实体`() {
        val names = tableNames("com.xuziyue.ebook.data.db.BookDatabase/3.json")
        assertEquals(5, names.size) // books + reading_progress + bookmarks + annotations + reading_sessions
        assertTrue("缺少 reading_sessions 表（DATA-04）", names.contains("reading_sessions"))
    }

    @Test
    fun `schema 4 json 已导出且含 collections 与 collection_books 实体`() {
        val names = tableNames("com.xuziyue.ebook.data.db.BookDatabase/4.json")
        assertEquals(7, names.size) // 五表 + collections + collection_books
        assertTrue("缺少 collections 表（LIB-05）", names.contains("collections"))
        assertTrue("缺少 collection_books 表（LIB-05）", names.contains("collection_books"))
    }

    private fun tableNames(path: String): List<String> {
        // sourceSets.test.resources.srcDir(schemas) 把 schemas 内容映射到 classpath 根，故路径去掉 schemas/ 前缀。
        val json = javaClass.classLoader!!.getResourceAsStream(path)
            ?.bufferedReader()?.use { it.readText() }
            ?: error("未找到 classpath:$path；确认 ksp room.schemaLocation 配置且已先跑 assembleDebug 生成 schema")
        val entities = JSONObject(json).getJSONObject("database").getJSONArray("entities")
        return (0 until entities.length()).map { entities.getJSONObject(it).getString("tableName") }
    }
}
