package com.xuziyue.ebook.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DATA-02 迁移测试框架（设备级，红线 #6 / REL-03 前置）。
 *
 * 与 `app/src/test` 下的 [BookDatabaseMigrationTest]（Robolectric，CI 友好、挡数据丢失回归）互补：
 * 本测试用 Room 官方 [MigrationTestHelper] 做 **schema 精确校验**——
 *  1. [createDatabase] 由已导出的 `app/schemas/.../1.json` 建出真实 v1 库（取代手写 CREATE TABLE）；
 *  2. [runMigrationsAndValidate] 跑 [MIGRATION_1_2] 后，把结果 schema 与 `2.json` 逐字段比对，
 *     不一致直接抛 `Migration didn't properly handle ...`；
 *  3. 再断言旧数据（books / reading_progress）未丢、新表 bookmarks/annotations 已建可写、FK CASCADE 仍生效。
 *
 * 全程跑在真机真实 SQLite 上（比单测更强：真实 v1 数据 + 真实 SQLite + Room 运行时校验）。
 *
 * 运行：`./gradlew :app:connectedDebugAndroidTest`（需真机/模拟器；CI 无设备跳过）。
 *
 * ## 扩展指南（加 v2→v3 时）
 * 1. 改 [BookDatabase] version → 3，KSP 自动生成 `3.json` 并提交；
 * 2. 在 `Migrations.kt` 新增 `MIGRATION_2_3`；
 * 3. 复制下面的测试，把 `runMigrationsAndValidate` 的目标版本改 3、迁移列表追加 `MIGRATION_2_3`，
 *    并在 [seedV1Data] 之后按需补 v2 种子（bookmarks/annotations）。
 * 即为「可扩展框架」：新增版本 = 新增一行 runMigrationsAndValidate。
 */
@RunWith(AndroidJUnit4::class)
class BookDatabaseMigrationInstrumentedTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BookDatabase::class.java,
    )

    @Test
    fun migrate1To2_保数据_建新表_schema与2json一致() {
        // 1. 由 1.json 建真实 v1 库 + 种子（books + reading_progress，v1 仅这两张表）
        helper.createDatabase(TEST_DB, 1).use { db -> seedV1Data(db) }

        // 2. 跑 MIGRATION_1_2 并与 2.json 逐字段校验（核心：schema 精确匹配）
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        // 3. 旧数据未丢
        assertEquals("书名", string(db, "SELECT title FROM books WHERE id = ?", "b1"))
        assertEquals("loc-v1", string(db, "SELECT locatorJson FROM reading_progress WHERE bookId = ?", "b1"))

        // 4. 新表 bookmarks / annotations 已建且为空
        assertEquals(0L, count(db, "bookmarks"))
        assertEquals(0L, count(db, "annotations"))

        // 5. 新表可写
        db.execSQL(
            "INSERT INTO bookmarks(id,bookId,locatorJson,excerpt,createdAt) VALUES(?,?,?,?,?)",
            arrayOf<Any?>("bm1", "b1", "locbm", "excerpt", 1000L),
        )
        db.execSQL(
            "INSERT INTO annotations(id,bookId,locatorJson,selectedText,note,color,createdAt,updatedAt,deletedAt) " +
                "VALUES(?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>("an1", "b1", "locan", "seltext", null, "YELLOW", 1000L, 1000L, null),
        )
        assertEquals(1L, count(db, "bookmarks"))
        assertEquals(1L, count(db, "annotations"))

        // 6. FK CASCADE 在迁移后库仍生效（删书连带删书签 / 批注）。
        //    注意：MigrationTestHelper 返回的连接默认 foreign_keys=OFF（SQLite 默认；Room 运行时才开），
        //    需手动开启该 pragma 才能验证级联——FK 的 schema 定义已由 runMigrationsAndValidate 保证。
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM books WHERE id = ?", arrayOf<Any?>("b1"))
        assertEquals(0L, count(db, "bookmarks"))
        assertEquals(0L, count(db, "annotations"))
        assertTrue("书已被删", string(db, "SELECT title FROM books WHERE id = ?", "b1") == null)

        db.close()
    }

    // ===== 种子 / 查询小工具 =====

    /** 灌 v1 种子：1 本书 + 1 条进度（列对齐 1.json / 当前 BookEntity，v2 未改这两张表）。 */
    private fun seedV1Data(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO books(id,contentHash,title,authors,description,language,format,mediaType,filePath,fileSize,coverPath,importedAt,lastOpenedAt,status) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>(
                "b1", "hash1", "书名", "[\"作者\"]",
                null, null, "EPUB", "application/epub+zip", "/b1.epub",
                0L, null, 0L, null, "UNREAD",
            ),
        )
        db.execSQL(
            "INSERT INTO reading_progress(bookId,locatorJson,progression,updatedAt,deviceId) VALUES(?,?,?,?,?)",
            arrayOf<Any?>("b1", "loc-v1", 0.5, 1000L, null),
        )
    }

    private fun count(db: SupportSQLiteDatabase, table: String): Long =
        db.query("SELECT COUNT(*) FROM `$table`").use { c -> c.moveToFirst(); c.getLong(0) }

    private fun string(db: SupportSQLiteDatabase, sql: String, vararg args: Any): String? =
        db.query(sql, args).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
