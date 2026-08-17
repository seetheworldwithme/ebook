package com.xuziyue.ebook.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xuziyue.ebook.model.ReadingStatus
import kotlinx.coroutines.flow.first
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
 * BookTypographyDao 单测（Robolectric in-memory Room，TYPE-05）。
 * 验证 upsert 覆盖语义 / observe 响应式 / 删行（恢复全局默认）/ 删书 CASCADE。
 */
@RunWith(RobolectricTestRunner::class)
class BookTypographyDaoTest {

    private lateinit var db: BookDatabase
    private lateinit var dao: BookTypographyDao
    private lateinit var bookDao: BookDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BookDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.bookTypographyDao()
        bookDao = db.bookDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `upsert 新增并查询`() = runTest {
        bookDao.insert(book("b1"))
        dao.upsert(BookTypographyEntity("b1", """{"schemaVersion":1,"fontSize":1.5}""", 1000L))
        val found = dao.get("b1")
        assertNotNull(found)
        assertEquals("""{"schemaVersion":1,"fontSize":1.5}""", found?.overridesJson)
        assertEquals(1000L, found?.updatedAt)
    }

    @Test
    fun `upsert 同 bookId 覆盖旧覆盖`() = runTest {
        bookDao.insert(book("b1"))
        dao.upsert(BookTypographyEntity("b1", """{"schemaVersion":1}""", 1000L))
        dao.upsert(BookTypographyEntity("b1", """{"schemaVersion":1,"theme":"DARK"}""", 2000L))
        assertEquals(1, dao.snapshotAll().size)
        assertEquals(2000L, dao.get("b1")?.updatedAt)
    }

    @Test
    fun `observe 无行返回 null 有行回流`() = runTest {
        bookDao.insert(book("b1"))
        assertNull(dao.observe("b1").first())
        dao.upsert(BookTypographyEntity("b1", """{"schemaVersion":1}""", 1000L))
        assertNotNull(dao.observe("b1").first())
    }

    @Test
    fun `delete 删行恢复全局默认语义`() = runTest {
        bookDao.insert(book("b1"))
        dao.upsert(BookTypographyEntity("b1", """{"schemaVersion":1}""", 1000L))
        dao.delete("b1")
        assertNull(dao.get("b1"))
        assertTrue(dao.snapshotAll().isEmpty())
    }

    @Test
    fun `删书 CASCADE 连带清按书排版`() = runTest {
        bookDao.insert(book("b1"))
        dao.upsert(BookTypographyEntity("b1", """{"schemaVersion":1}""", 1000L))
        bookDao.deleteById("b1")
        assertTrue(dao.snapshotAll().isEmpty())
    }

    @Test
    fun `不同书各自成行互不干扰`() = runTest {
        bookDao.insert(book("b1"))
        bookDao.insert(book("b2"))
        dao.upsert(BookTypographyEntity("b1", """{"schemaVersion":1,"fontSize":1.5}""", 1000L))
        dao.upsert(BookTypographyEntity("b2", """{"schemaVersion":1,"theme":"DARK"}""", 2000L))
        assertEquals(2, dao.snapshotAll().size)
        assertTrue(dao.get("b1")?.overridesJson?.contains("fontSize") == true)
        assertTrue(dao.get("b2")?.overridesJson?.contains("DARK") == true)
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
        status = ReadingStatus.UNREAD,
    )
}
