package com.xuziyue.ebook.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xuziyue.ebook.model.ReadingStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BookmarkDao 单测（Robolectric in-memory Room，READ-06）。
 * 验证 upsert / observe Flow 排序 / forBook 查询 / delete / ForeignKey CASCADE 删书连带删书签。
 */
@RunWith(RobolectricTestRunner::class)
class BookmarkDaoTest {

    private lateinit var db: BookDatabase
    private lateinit var bookDao: BookDao
    private lateinit var bookmarkDao: BookmarkDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BookDatabase::class.java,
        ).allowMainThreadQueries().build()
        bookDao = db.bookDao()
        bookmarkDao = db.bookmarkDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seedBook(id: String) {
        bookDao.insert(
            BookEntity(
                id = id, contentHash = "hash-$id", title = id, authors = emptyList(),
                description = null, language = null, format = "EPUB",
                mediaType = "application/epub+zip", filePath = "/$id.epub",
                fileSize = 0L, coverPath = null, importedAt = 0L,
                lastOpenedAt = null, status = ReadingStatus.UNREAD,
            ),
        )
    }

    private fun bookmark(id: String, bookId: String, createdAt: Long = 0L) =
        BookmarkEntity(id = id, bookId = bookId, locatorJson = """{"href":"$id"}""", excerpt = "ex-$id", createdAt = createdAt)

    @Test
    fun `upsert 后 forBook 命中`() = runTest {
        seedBook("b1")
        bookmarkDao.upsert(bookmark("m1", "b1"))
        assertEquals(1, bookmarkDao.forBook("b1").size)
    }

    @Test
    fun `observe 按 createdAt 倒序`() = runTest {
        seedBook("b1")
        bookmarkDao.upsert(bookmark("m1", "b1", createdAt = 1000L))
        bookmarkDao.upsert(bookmark("m2", "b1", createdAt = 2000L))
        val list = bookmarkDao.observe("b1").first()
        assertEquals(listOf("m2", "m1"), list.map { it.id })
    }

    @Test
    fun `deleteById 删除指定书签`() = runTest {
        seedBook("b1")
        bookmarkDao.upsert(bookmark("m1", "b1"))
        bookmarkDao.upsert(bookmark("m2", "b1"))
        bookmarkDao.deleteById("m1")
        val ids = bookmarkDao.forBook("b1").map { it.id }
        assertEquals(listOf("m2"), ids)
    }

    @Test
    fun `deleteAllForBook 清空同书全部`() = runTest {
        seedBook("b1")
        bookmarkDao.upsert(bookmark("m1", "b1"))
        bookmarkDao.upsert(bookmark("m2", "b1"))
        bookmarkDao.deleteAllForBook("b1")
        assertTrue(bookmarkDao.forBook("b1").isEmpty())
    }

    @Test
    fun `CASCADE 删书连带删书签`() = runTest {
        seedBook("b1")
        bookmarkDao.upsert(bookmark("m1", "b1"))
        bookDao.deleteById("b1")
        assertTrue(bookmarkDao.forBook("b1").isEmpty())
    }

    @Test
    fun `不同书的书签互不影响`() = runTest {
        seedBook("b1"); seedBook("b2")
        bookmarkDao.upsert(bookmark("m1", "b1"))
        bookmarkDao.upsert(bookmark("m2", "b2"))
        assertEquals(1, bookmarkDao.forBook("b1").size)
        assertEquals(1, bookmarkDao.forBook("b2").size)
    }
}
