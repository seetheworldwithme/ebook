package com.xuziyue.ebook.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xuziyue.ebook.model.CollectionKind
import com.xuziyue.ebook.model.HighlightColor
import com.xuziyue.ebook.model.SYSTEM_FAVORITE_ID
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 数据库迁移测试（READ-06/07 + DATA-04 + 红线 #6：升级不丢数据，必须写 migration 测试）。
 *
 * 覆盖：
 * - v1→v2（MIGRATION_1_2，加 bookmarks/annotations）。
 * - v2→v3（MIGRATION_2_3，加 reading_sessions）。
 * - v3→v4（MIGRATION_3_4，加 collections/collection_books + 系统书架）。
 * - v4→v5（MIGRATION_4_5，加 import_sources）。
 *
 * 做法（Robolectric JVM，CI 友好）：
 * 1. 用裸 [SQLiteDatabase] 造一份旧版库并种数据，置对应 user_version；
 * 2. 用 [Room.databaseBuilder] + 迁移打开同一文件——Room 会跑迁移 **并校验** 结果 schema 与
 *    其编译期期望的版本 schema 完全一致（不一致抛 `Migration didn't properly handle`）；
 * 3. 断言旧数据未丢、新表可写读、ForeignKey CASCADE 在迁移后库仍生效。
 *
 * 设备级 `MigrationTestHelper` 等价测试见 `BookDatabaseMigrationInstrumentedTest`（connectedAndroidTest）。
 */
@RunWith(RobolectricTestRunner::class)
class BookDatabaseMigrationTest {

    private var dbFile: File? = null

    @After
    fun tearDown() {
        dbFile?.delete()
    }

    @Test
    fun `v1 升 v2 不丢数据且建出 bookmarks annotations 表且 CASCADE 生效`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "migration-test.db").also { it.delete() }
        dbFile = file

        // 1. 造 v1 库 + 种子数据
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(V1_BOOKS_SQL)
            db.execSQL(V1_BOOKS_CONTENTHASH_INDEX_SQL)
            db.execSQL(V1_READING_PROGRESS_SQL)
            db.execSQL(
                "INSERT INTO books(id,contentHash,title,authors,description,language,format,mediaType,filePath,fileSize,coverPath,importedAt,lastOpenedAt,status) " +
                    "VALUES('b1','h1','书名','[]',NULL,NULL,'EPUB','application/epub+zip','/b1.epub',0,NULL,0,NULL,'UNREAD')",
            )
            db.execSQL(
                "INSERT INTO reading_progress(bookId,locatorJson,progression,updatedAt,deviceId) " +
                    "VALUES('b1','loc-json',0.5,1000,NULL)",
            )
            db.version = 1
        }

        // 2. Room 打开（触发迁移 + schema 校验）。起点 v1 文件，当前 Room 期望 v4，故需完整迁移链 1→2→3→4。
        val db = Room.databaseBuilder(context, BookDatabase::class.java, file.absolutePath)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()

        // 3. 旧数据未丢
        assertEquals("书名", db.bookDao().getById("b1")?.title)
        assertEquals("loc-json", db.readingProgressDao().get("b1")?.locatorJson)

        // 4. 新表 bookmarks / annotations 可写读
        db.bookmarkDao().upsert(BookmarkEntity("bm1", "b1", "locbm", "excerpt", 1000L))
        assertEquals(1, db.bookmarkDao().forBook("b1").size)
        db.annotationDao().upsert(
            AnnotationEntity("an1", "b1", "locan", "sel", null, HighlightColor.YELLOW, 1000L, 1000L, null),
        )
        assertNotNull(db.annotationDao().getById("an1"))

        // 5. ForeignKey CASCADE 在迁移后的库仍生效（删书连带删书签 / 批注）
        db.bookDao().deleteById("b1")
        assertTrue(db.bookmarkDao().forBook("b1").isEmpty())

        db.close()
    }

    @Test
    fun `v2 升 v3 不丢数据且建出 reading_sessions 表且 CASCADE 生效`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "migration-v3-test.db").also { it.delete() }
        dbFile = file

        // 1. 造 v2 库（books + reading_progress + bookmarks + annotations）并种数据
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(V1_BOOKS_SQL)
            db.execSQL(V1_BOOKS_CONTENTHASH_INDEX_SQL)
            db.execSQL(V1_READING_PROGRESS_SQL)
            db.execSQL(V2_BOOKMARKS_SQL)
            db.execSQL(V2_BOOKMARKS_INDEX_SQL)
            db.execSQL(V2_ANNOTATIONS_SQL)
            db.execSQL(V2_ANNOTATIONS_INDEX_SQL)
            db.execSQL(
                "INSERT INTO books(id,contentHash,title,authors,description,language,format,mediaType,filePath,fileSize,coverPath,importedAt,lastOpenedAt,status) " +
                    "VALUES('b2','h2','统计书','[]',NULL,NULL,'EPUB','application/epub+zip','/b2.epub',0,NULL,0,NULL,'READING')",
            )
            db.execSQL(
                "INSERT INTO reading_progress(bookId,locatorJson,progression,updatedAt,deviceId) " +
                    "VALUES('b2','loc2',0.3,2000,NULL)",
            )
            db.execSQL(
                "INSERT INTO bookmarks(id,bookId,locatorJson,excerpt,createdAt) VALUES('bm2','b2','locbm2','ex',2000)",
            )
            db.execSQL(
                "INSERT INTO annotations(id,bookId,locatorJson,selectedText,note,color,createdAt,updatedAt,deletedAt) " +
                    "VALUES('an2','b2','locan2','选中词',NULL,'YELLOW',2000,2000,NULL)",
            )
            db.version = 2
        }

        // 2. Room 打开（触发迁移 + schema 校验）。起点 v2，当前期望 v4，需迁移链 2→3→4。
        val db = Room.databaseBuilder(context, BookDatabase::class.java, file.absolutePath)
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()

        // 3. 旧数据未丢（书 / 进度 / 书签 / 批注）
        assertEquals("统计书", db.bookDao().getById("b2")?.title)
        assertEquals("loc2", db.readingProgressDao().get("b2")?.locatorJson)
        assertEquals(1, db.bookmarkDao().forBook("b2").size)
        assertNotNull(db.annotationDao().getById("an2"))

        // 4. 新表 reading_sessions 可写读 + 聚合查询生效
        db.readingSessionDao().upsert(ReadingSessionEntity("s1", "b2", 1000L, 2000L, 30L))
        assertEquals(30L, db.readingSessionDao().totalActiveSecondsForBook("b2"))

        // 5. ForeignKey CASCADE 在迁移后的库仍生效（删书连带删会话）
        db.bookDao().deleteById("b2")
        assertEquals(0L, db.readingSessionDao().totalActiveSecondsForBook("b2"))

        db.close()
    }

    @Test
    fun `v3 升 v4 不丢数据且建出 collections collection_books 表且系统书架已插入且 CASCADE 生效`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "migration-v4-test.db").also { it.delete() }
        dbFile = file

        // 1. 造 v3 库（books + progress + bookmarks + annotations + sessions）并种数据
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(V1_BOOKS_SQL)
            db.execSQL(V1_BOOKS_CONTENTHASH_INDEX_SQL)
            db.execSQL(V1_READING_PROGRESS_SQL)
            db.execSQL(V2_BOOKMARKS_SQL)
            db.execSQL(V2_BOOKMARKS_INDEX_SQL)
            db.execSQL(V2_ANNOTATIONS_SQL)
            db.execSQL(V2_ANNOTATIONS_INDEX_SQL)
            db.execSQL(V3_SESSIONS_SQL)
            db.execSQL(V3_SESSIONS_INDEX_SQL)
            db.execSQL(
                "INSERT INTO books(id,contentHash,title,authors,description,language,format,mediaType,filePath,fileSize,coverPath,importedAt,lastOpenedAt,status) " +
                    "VALUES('b4','h4','书架测试书','[]',NULL,NULL,'EPUB','application/epub+zip','/b4.epub',0,NULL,0,NULL,'UNREAD')",
            )
            db.version = 3
        }

        // 2. Room 打开（触发 v3→v4 迁移 + schema 校验；完整迁移链 3→4）
        val db = Room.databaseBuilder(context, BookDatabase::class.java, file.absolutePath)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()

        // 3. 旧数据未丢
        assertEquals("书架测试书", db.bookDao().getById("b4")?.title)

        // 4. 系统书架「收藏」已由迁移插入（固定 id、kind=SYSTEM_FAVORITE）
        val favorite = db.collectionDao().getById(SYSTEM_FAVORITE_ID)
        assertNotNull(favorite)
        assertEquals(CollectionKind.SYSTEM_FAVORITE, favorite?.kind)

        // 5. 新表可写读
        db.collectionDao().insert(
            CollectionEntity(id = "c1", name = "小说", sortOrder = 0L, createdAt = 0L, kind = CollectionKind.CUSTOM),
        )
        db.collectionBookDao().add(CollectionBookEntity("c1", "b4", 0L))
        assertEquals("c1", db.collectionBookDao().collectionIdsForBook("b4").first())

        // 6. 双向 FK CASCADE：删书连带清书架关系
        db.bookDao().deleteById("b4")
        assertTrue(db.collectionBookDao().collectionIdsForBook("b4").isEmpty())
        // 删书架连带清关系（书不删）
        db.collectionDao().deleteById("c1")
        assertTrue(db.collectionDao().snapshotAll().none { it.id == "c1" })

        db.close()
    }

    @Test
    fun `v4 升 v5 不丢数据且建出 import_sources 表且 sourceUri 唯一且 CASCADE 生效`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "migration-v5-test.db").also { it.delete() }
        dbFile = file

        // 1. 造 v4 库（七表）并种数据
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(V1_BOOKS_SQL)
            db.execSQL(V1_BOOKS_CONTENTHASH_INDEX_SQL)
            db.execSQL(V1_READING_PROGRESS_SQL)
            db.execSQL(V2_BOOKMARKS_SQL)
            db.execSQL(V2_BOOKMARKS_INDEX_SQL)
            db.execSQL(V2_ANNOTATIONS_SQL)
            db.execSQL(V2_ANNOTATIONS_INDEX_SQL)
            db.execSQL(V3_SESSIONS_SQL)
            db.execSQL(V3_SESSIONS_INDEX_SQL)
            db.execSQL(V4_COLLECTIONS_SQL)
            db.execSQL(V4_COLLECTIONS_INDEX_SQL)
            db.execSQL(V4_COLLECTION_BOOKS_SQL)
            db.execSQL(V4_COLLECTION_BOOKS_COLLECTIONID_INDEX_SQL)
            db.execSQL(V4_COLLECTION_BOOKS_BOOKID_INDEX_SQL)
            db.execSQL(
                "INSERT INTO books(id,contentHash,title,authors,description,language,format,mediaType,filePath,fileSize,coverPath,importedAt,lastOpenedAt,status) " +
                    "VALUES('b5','h5','目录导入测试书','[]',NULL,NULL,'EPUB','application/epub+zip','/b5.epub',0,NULL,0,NULL,'UNREAD')",
            )
            db.version = 4
        }

        // 2. Room 打开（触发 v4→v5 迁移 + schema 校验）
        val db = Room.databaseBuilder(context, BookDatabase::class.java, file.absolutePath)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()

        // 3. 旧数据未丢
        assertEquals("目录导入测试书", db.bookDao().getById("b5")?.title)

        // 4. 新表 import_sources 可写读
        db.importSourceDao().upsert(
            ImportSourceEntity("is1", "content://tree/x/document/y", "b5", 100L, 200L, 300L),
        )
        assertEquals("is1", db.importSourceDao().findBySourceUri("content://tree/x/document/y")?.id)

        // 5. upsert 同 sourceUri 覆盖（唯一索引语义）
        db.importSourceDao().upsert(
            ImportSourceEntity("is1", "content://tree/x/document/y", "b5", 101L, 201L, 301L),
        )
        assertEquals(1, db.importSourceDao().count())
        assertEquals(101L, db.importSourceDao().findBySourceUri("content://tree/x/document/y")?.fileSize)

        // 6. FK CASCADE：删书连带清 import_sources 记录
        db.bookDao().deleteById("b5")
        assertTrue(db.importSourceDao().snapshotAll().isEmpty())

        db.close()
    }

    private companion object {
        // v1 schema 的 CREATE TABLE（取自 app/schemas/.../1.json，v1 已冻结，逐字硬编码）。
        const val V1_BOOKS_SQL =
            "CREATE TABLE IF NOT EXISTS `books` (`id` TEXT NOT NULL, `contentHash` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, `authors` TEXT NOT NULL, `description` TEXT, `language` TEXT, " +
                "`format` TEXT NOT NULL, `mediaType` TEXT NOT NULL, `filePath` TEXT NOT NULL, " +
                "`fileSize` INTEGER NOT NULL, `coverPath` TEXT, `importedAt` INTEGER NOT NULL, " +
                "`lastOpenedAt` INTEGER, `status` TEXT NOT NULL, PRIMARY KEY(`id`))"

        const val V1_BOOKS_CONTENTHASH_INDEX_SQL =
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_books_contentHash` ON `books` (`contentHash`)"

        const val V1_READING_PROGRESS_SQL =
            "CREATE TABLE IF NOT EXISTS `reading_progress` (`bookId` TEXT NOT NULL, " +
                "`locatorJson` TEXT NOT NULL, `progression` REAL, `updatedAt` INTEGER NOT NULL, " +
                "`deviceId` TEXT, PRIMARY KEY(`bookId`), " +
                "FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"

        // v2 schema 的 CREATE TABLE / INDEX（取自 app/schemas/.../2.json，v2 已冻结，逐字硬编码）。
        const val V2_BOOKMARKS_SQL =
            "CREATE TABLE IF NOT EXISTS `bookmarks` (`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, " +
                "`locatorJson` TEXT NOT NULL, `excerpt` TEXT, `createdAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"

        const val V2_BOOKMARKS_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS `index_bookmarks_bookId` ON `bookmarks` (`bookId`)"

        const val V2_ANNOTATIONS_SQL =
            "CREATE TABLE IF NOT EXISTS `annotations` (`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, " +
                "`locatorJson` TEXT NOT NULL, `selectedText` TEXT NOT NULL, `note` TEXT, " +
                "`color` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`deletedAt` INTEGER, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"

        const val V2_ANNOTATIONS_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS `index_annotations_bookId` ON `annotations` (`bookId`)"

        // v3 schema 的 reading_sessions（取自 app/schemas/.../3.json，v3 已冻结，逐字硬编码）。
        const val V3_SESSIONS_SQL =
            "CREATE TABLE IF NOT EXISTS `reading_sessions` (`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, " +
                "`startedAt` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL, `activeSeconds` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"

        const val V3_SESSIONS_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS `index_reading_sessions_bookId` ON `reading_sessions` (`bookId`)"

        // v4 schema 的 collections / collection_books（取自 app/schemas/.../4.json，v4 已冻结，逐字硬编码）。
        const val V4_COLLECTIONS_SQL =
            "CREATE TABLE IF NOT EXISTS `collections` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `kind` TEXT NOT NULL, PRIMARY KEY(`id`))"

        const val V4_COLLECTIONS_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS `index_collections_name` ON `collections` (`name`)"

        const val V4_COLLECTION_BOOKS_SQL =
            "CREATE TABLE IF NOT EXISTS `collection_books` (`collectionId` TEXT NOT NULL, " +
                "`bookId` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`collectionId`, `bookId`), " +
                "FOREIGN KEY(`collectionId`) REFERENCES `collections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"

        const val V4_COLLECTION_BOOKS_COLLECTIONID_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS `index_collection_books_collectionId` ON `collection_books` (`collectionId`)"

        const val V4_COLLECTION_BOOKS_BOOKID_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS `index_collection_books_bookId` ON `collection_books` (`bookId`)"
    }
}
