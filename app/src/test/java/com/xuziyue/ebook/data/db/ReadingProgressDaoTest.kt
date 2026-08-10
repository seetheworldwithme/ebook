package com.xuziyue.ebook.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xuziyue.ebook.model.ReadingStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ReadingProgressDao 单测（Robolectric in-memory Room，design.md:349）。
 * 验证 upsert REPLACE 覆盖 / get 未命中 / ForeignKey CASCADE 删书连带删进度。
 */
@RunWith(RobolectricTestRunner::class)
class ReadingProgressDaoTest {

    private lateinit var db: BookDatabase
    private lateinit var bookDao: BookDao
    private lateinit var progressDao: ReadingProgressDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BookDatabase::class.java,
        ).allowMainThreadQueries().build()
        bookDao = db.bookDao()
        progressDao = db.readingProgressDao()
    }

    @After
    fun tearDown() = db.close()

    /** FK 约束：插 progress 前必须先插 parent book。 */
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

    @Test
    fun `upsert REPLACE 覆盖旧进度`() = runTest {
        seedBook("b1")
        progressDao.upsert(ReadingProgressEntity("b1", """{"v":1}""", 0.1, 1000L, null))
        progressDao.upsert(ReadingProgressEntity("b1", """{"v":2}""", 0.2, 2000L, null))
        val got = progressDao.get("b1")!!
        assertEquals("""{"v":2}""", got.locatorJson)
        assertEquals(0.2, got.progression!!, 0.0001)
    }

    @Test
    fun `get 未命中返回 null`() = runTest {
        seedBook("b1")
        assertNull(progressDao.get("b1"))
    }

    @Test
    fun `CASCADE 删书连带删进度`() = runTest {
        seedBook("b1")
        progressDao.upsert(ReadingProgressEntity("b1", "loc", 0.5, 1000L, null))
        bookDao.deleteById("b1")
        assertNull(progressDao.get("b1"))
    }
}
