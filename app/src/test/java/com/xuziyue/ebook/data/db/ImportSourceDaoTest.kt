package com.xuziyue.ebook.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ImportSourceDao 单测（Robolectric in-memory Room，IMP-06）。
 * 验证 upsert 覆盖语义 / sourceUri 唯一 / findBySourceUri / 删书 CASCADE。
 */
@RunWith(RobolectricTestRunner::class)
class ImportSourceDaoTest {

    private lateinit var db: BookDatabase
    private lateinit var dao: ImportSourceDao
    private lateinit var bookDao: BookDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BookDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.importSourceDao()
        bookDao = db.bookDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `upsert 新增并按 sourceUri 查询`() = runTest {
        bookDao.insert(book("b1"))
        dao.upsert(ImportSourceEntity("s1", "content://tree/x/doc/1", "b1", 100L, 200L, 300L))
        val found = dao.findBySourceUri("content://tree/x/doc/1")
        assertNotNull(found)
        assertEquals("b1", found?.bookId)
        assertEquals(100L, found?.fileSize)
        assertEquals(200L, found?.lastModified)
        assertEquals(1, dao.count())
    }

    @Test
    fun `upsert 同 sourceUri 覆盖旧记录`() = runTest {
        bookDao.insert(book("b1"))
        dao.upsert(ImportSourceEntity("s1", "content://tree/x/doc/1", "b1", 100L, 200L, 300L))
        // 同 sourceUri 再 upsert（id 复用旧 id）：唯一索引语义下覆盖
        dao.upsert(ImportSourceEntity("s1", "content://tree/x/doc/1", "b1", 999L, 888L, 777L))
        assertEquals(1, dao.count())
        assertEquals(999L, dao.findBySourceUri("content://tree/x/doc/1")?.fileSize)
    }

    @Test
    fun `查无记录返回 null`() = runTest {
        assertNull(dao.findBySourceUri("content://tree/x/doc/none"))
    }

    @Test
    fun `删书 CASCADE 连带清来源记录`() = runTest {
        bookDao.insert(book("b1"))
        dao.upsert(ImportSourceEntity("s1", "content://tree/x/doc/1", "b1", 100L, 200L, 300L))
        bookDao.deleteById("b1")
        assertTrue(dao.snapshotAll().isEmpty())
        assertNull(dao.findBySourceUri("content://tree/x/doc/1"))
    }

    @Test
    fun `不同 sourceUri 各自成行`() = runTest {
        bookDao.insert(book("b1"))
        dao.upsert(ImportSourceEntity("s1", "content://tree/x/doc/1", "b1", 100L, 200L, 300L))
        dao.upsert(ImportSourceEntity("s2", "content://tree/x/doc/2", "b1", 100L, 200L, 300L))
        assertEquals(2, dao.count())
    }

    private fun book(id: String) = BookEntity(
        id = id,
        contentHash = "hash-$id",
        title = "书$id",
        authors = emptyList(),
        description = null,
        language = null,
        format = "EPUB",
        mediaType = "application/epub+zip",
        filePath = "/$id.epub",
        fileSize = 0L,
        coverPath = null,
        importedAt = 0L,
        lastOpenedAt = null,
        status = com.xuziyue.ebook.model.ReadingStatus.UNREAD,
    )
}
