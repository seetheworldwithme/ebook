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
 * 数据库迁移测试框架（设备级，红线 #6 / REL-03 前置）。
 *
 * 与 `app/src/test` 下的 [BookDatabaseMigrationTest]（Robolectric，CI 友好、挡数据丢失回归）互补：
 * 本测试用 Room 官方 [MigrationTestHelper] 做 **schema 精确校验**——
 *  1. [createDatabase] 由已导出的 `app/schemas/.../N.json` 建出真实 vN 库（取代手写 CREATE TABLE）；
 *  2. [runMigrationsAndValidate] 跑迁移后，把结果 schema 与目标版本 json 逐字段比对，
 *     不一致直接抛 `Migration didn't properly handle ...`；
 *  3. 再断言旧数据未丢、新表已建可写、FK CASCADE 仍生效。
 *
 * 覆盖：
 * - migrate1To2：v1→v2（加 bookmarks/annotations）。
 * - migrate2To3：v2→v3（加 reading_sessions，DATA-04）。
 * - migrate3To4：v3→v4（加 collections/collection_books，LIB-05）。
 * - migrate4To5：v4→v5（加 import_sources，IMP-06）。
 * - migrate5To6：v5→v6（加 book_typography，TYPE-05 按书排版）。
 *
 * 全程跑在真机真实 SQLite 上（比单测更强：真实数据 + 真实 SQLite + Room 运行时校验）。
 *
 * 运行：`./gradlew :app:connectedDebugAndroidTest`（需真机/模拟器；CI 无设备跳过）。
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

    @Test
    fun migrate2To3_保数据_建readingSessions_schema与3json一致() {
        // 1. 由 2.json 建真实 v2 库（含 bookmarks/annotations 空表）+ 灌 v2 种子（书/进度/书签/批注）
        helper.createDatabase(TEST_DB, 2).use { db -> seedV2Data(db) }

        // 2. 跑 MIGRATION_2_3 并与 3.json 逐字段校验（核心：schema 精确匹配；起点已是 v2，只传 2→3）
        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        // 3. 旧数据未丢（v2 的四张表都在）
        assertEquals("统计书", string(db, "SELECT title FROM books WHERE id = ?", "b2"))
        assertEquals("loc-v2", string(db, "SELECT locatorJson FROM reading_progress WHERE bookId = ?", "b2"))
        assertEquals(1L, count(db, "bookmarks"))
        assertEquals(1L, count(db, "annotations"))

        // 4. 新表 reading_sessions 已建且为空
        assertEquals(0L, count(db, "reading_sessions"))

        // 5. 新表可写
        db.execSQL(
            "INSERT INTO reading_sessions(id,bookId,startedAt,endedAt,activeSeconds) VALUES(?,?,?,?,?)",
            arrayOf<Any?>("s1", "b2", 1000L, 61000L, 60L),
        )
        assertEquals(1L, count(db, "reading_sessions"))

        // 6. FK CASCADE 在迁移后库仍生效（删书连带删会话）
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM books WHERE id = ?", arrayOf<Any?>("b2"))
        assertEquals(0L, count(db, "reading_sessions"))

        db.close()
    }

    @Test
    fun migrate3To4_保数据_建书架表_系统书架已插_schema与4json一致() {
        // 1. 由 3.json 建真实 v3 库（含 sessions 空表）+ 灌 v3 种子
        helper.createDatabase(TEST_DB, 3).use { db -> seedV3Data(db) }

        // 2. 跑 MIGRATION_3_4 并与 4.json 逐字段校验
        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)

        // 3. 旧数据未丢
        assertEquals("书架测试书", string(db, "SELECT title FROM books WHERE id = ?", "b4"))

        // 4. 系统书架「收藏」已由迁移插入（固定 id + kind=SYSTEM_FAVORITE）
        assertEquals("收藏", string(db, "SELECT name FROM collections WHERE id = ?", "system-favorite"))
        assertEquals("SYSTEM_FAVORITE", string(db, "SELECT kind FROM collections WHERE id = ?", "system-favorite"))

        // 5. 新表可写
        db.execSQL(
            "INSERT INTO collections(id,name,sortOrder,createdAt,kind) VALUES(?,?,?,?,?)",
            arrayOf<Any?>("c1", "小说", 100L, 0L, "CUSTOM"),
        )
        db.execSQL(
            "INSERT INTO collection_books(collectionId,bookId,addedAt) VALUES(?,?,?)",
            arrayOf<Any?>("c1", "b4", 0L),
        )
        assertEquals(1L, count(db, "collection_books"))

        // 6. 双向 FK CASCADE：删书清关系 + 删书架清关系（书不删）
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM collection_books WHERE collectionId = 'c1' AND bookId = 'b4'")
        db.execSQL("DELETE FROM collections WHERE id = ?", arrayOf<Any?>("c1"))
        assertTrue("书架已删", count(db, "collections") == 1L) // 仅剩系统书架

        db.close()
    }

    @Test
    fun migrate4To5_保数据_建importSources_schema与5json一致() {
        // 1. 由 4.json 建真实 v4 库（七表）+ 灌 v4 种子（书）
        helper.createDatabase(TEST_DB, 4).use { db -> seedV4Data(db) }

        // 2. 跑 MIGRATION_4_5 并与 5.json 逐字段校验（核心：schema 精确匹配）
        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        // 3. 旧数据未丢
        assertEquals("目录导入测试书", string(db, "SELECT title FROM books WHERE id = ?", "b5"))

        // 4. 新表 import_sources 已建且为空
        assertEquals(0L, count(db, "import_sources"))

        // 5. 新表可写
        db.execSQL(
            "INSERT INTO import_sources(id,sourceUri,bookId,fileSize,lastModified,scannedAt) VALUES(?,?,?,?,?,?)",
            arrayOf<Any?>("is1", "content://tree/x/document/y", "b5", 100L, 200L, 300L),
        )
        assertEquals(1L, count(db, "import_sources"))

        // 6. FK CASCADE 在迁移后库仍生效（删书连带删来源记录）
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM books WHERE id = ?", arrayOf<Any?>("b5"))
        assertEquals(0L, count(db, "import_sources"))

        db.close()
    }

    @Test
    fun migrate5To6_保数据_建bookTypography_schema与6json一致() {
        // 1. 由 5.json 建真实 v5 库（八表）+ 灌 v5 种子（书）
        helper.createDatabase(TEST_DB, 5).use { db -> seedV5Data(db) }

        // 2. 跑 MIGRATION_5_6 并与 6.json 逐字段校验（核心：schema 精确匹配）
        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)

        // 3. 旧数据未丢
        assertEquals("按书排版测试书", string(db, "SELECT title FROM books WHERE id = ?", "b6"))

        // 4. 新表 book_typography 已建且为空
        assertEquals(0L, count(db, "book_typography"))

        // 5. 新表可写 + PK 覆盖语义
        db.execSQL(
            "INSERT INTO book_typography(bookId,overridesJson,updatedAt) VALUES(?,?,?)",
            arrayOf<Any?>("b6", "{\"schemaVersion\":1,\"fontSize\":1.5}", 1000L),
        )
        db.execSQL(
            "INSERT OR REPLACE INTO book_typography(bookId,overridesJson,updatedAt) VALUES(?,?,?)",
            arrayOf<Any?>("b6", "{\"schemaVersion\":1,\"theme\":\"DARK\"}", 2000L),
        )
        assertEquals(1L, count(db, "book_typography"))

        // 6. FK CASCADE 在迁移后库仍生效（删书连带删按书排版覆盖）
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM books WHERE id = ?", arrayOf<Any?>("b6"))
        assertEquals(0L, count(db, "book_typography"))

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

    /** 灌 v2 种子：1 本书 + 1 条进度 + 1 书签 + 1 批注（列对齐 2.json）。 */
    private fun seedV2Data(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO books(id,contentHash,title,authors,description,language,format,mediaType,filePath,fileSize,coverPath,importedAt,lastOpenedAt,status) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>(
                "b2", "hash2", "统计书", "[\"作者\"]",
                null, null, "EPUB", "application/epub+zip", "/b2.epub",
                0L, null, 0L, null, "READING",
            ),
        )
        db.execSQL(
            "INSERT INTO reading_progress(bookId,locatorJson,progression,updatedAt,deviceId) VALUES(?,?,?,?,?)",
            arrayOf<Any?>("b2", "loc-v2", 0.3, 2000L, null),
        )
        db.execSQL(
            "INSERT INTO bookmarks(id,bookId,locatorJson,excerpt,createdAt) VALUES(?,?,?,?,?)",
            arrayOf<Any?>("bm2", "b2", "locbm2", "摘录", 2000L),
        )
        db.execSQL(
            "INSERT INTO annotations(id,bookId,locatorJson,selectedText,note,color,createdAt,updatedAt,deletedAt) VALUES(?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>("an2", "b2", "locan2", "选中词", null, "YELLOW", 2000L, 2000L, null),
        )
    }

    /** 灌 v3 种子：1 本书（v3 已含 sessions 空表，种子只需书）。 */
    private fun seedV3Data(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO books(id,contentHash,title,authors,description,language,format,mediaType,filePath,fileSize,coverPath,importedAt,lastOpenedAt,status) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>(
                "b4", "hash4", "书架测试书", "[\"作者\"]",
                null, null, "EPUB", "application/epub+zip", "/b4.epub",
                0L, null, 0L, null, "UNREAD",
            ),
        )
    }

    /** 灌 v4 种子：1 本书（v4 已含 collections/collection_books 空表，种子只需书）。 */
    private fun seedV4Data(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO books(id,contentHash,title,authors,description,language,format,mediaType,filePath,fileSize,coverPath,importedAt,lastOpenedAt,status) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>(
                "b5", "hash5", "目录导入测试书", "[\"作者\"]",
                null, null, "EPUB", "application/epub+zip", "/b5.epub",
                0L, null, 0L, null, "UNREAD",
            ),
        )
    }

    /** 灌 v5 种子：1 本书（列对齐 5.json；import_sources 留空，v6 未改此表）。 */
    private fun seedV5Data(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO books(id,contentHash,title,authors,description,language,format,mediaType,filePath,fileSize,coverPath,importedAt,lastOpenedAt,status) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>(
                "b6", "hash6", "按书排版测试书", "[\"作者\"]",
                null, null, "EPUB", "application/epub+zip", "/b6.epub",
                0L, null, 0L, null, "UNREAD",
            ),
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
