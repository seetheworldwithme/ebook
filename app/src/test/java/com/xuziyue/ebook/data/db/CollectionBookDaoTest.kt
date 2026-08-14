package com.xuziyue.ebook.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
 * CollectionBookDao 单测（Robolectric in-memory Room，LIB-05）。
 * 验证加入/移除 / 联合主键去重 / JOIN 查询 / 双向 FK CASCADE。
 */
@RunWith(RobolectricTestRunner::class)
class CollectionBookDaoTest {

    private lateinit var db: BookDatabase
    private lateinit var cbDao: CollectionBookDao
    private lateinit var collectionDao: CollectionDao
    private lateinit var bookDao: BookDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BookDatabase::class.java,
        ).allowMainThreadQueries().build()
        cbDao = db.collectionBookDao()
        collectionDao = db.collectionDao()
        bookDao = db.bookDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `重复加入同一书架幂等（联合主键 IGNORE）`() = runTest {
        bookDao.insert(book("b1"))
        collectionDao.insert(collection("c1"))
        cbDao.add(CollectionBookEntity("c1", "b1", 0L))
        cbDao.add(CollectionBookEntity("c1", "b1", 0L)) // 重复，IGNORE
        assertEquals(1, cbDao.collectionIdsForBook("b1").size)
    }

    @Test
    fun `observeBooksInCollection 返回书架内书籍带进度`() = runTest {
        bookDao.insert(book("b1"))
        bookDao.insert(book("b2"))
        collectionDao.insert(collection("c1"))
        cbDao.add(CollectionBookEntity("c1", "b1", 0L))
        cbDao.add(CollectionBookEntity("c1", "b2", 0L))
        val books = cbDao.observeBooksInCollection("c1", "").first()
        assertEquals(2, books.size)
    }

    @Test
    fun `observeBooksInCollection 支持按书名搜索`() = runTest {
        bookDao.insert(book("b1", title = "红楼梦"))
        bookDao.insert(book("b2", title = "西游记"))
        collectionDao.insert(collection("c1"))
        cbDao.add(CollectionBookEntity("c1", "b1", 0L))
        cbDao.add(CollectionBookEntity("c1", "b2", 0L))
        val result = cbDao.observeBooksInCollection("c1", "红楼").first()
        assertEquals(1, result.size)
        assertEquals("红楼梦", result[0].book.title)
    }

    @Test
    fun `删书连带清该书所有书架归属（FK CASCADE）`() = runTest {
        bookDao.insert(book("b1"))
        collectionDao.insert(collection("c1"))
        collectionDao.insert(collection("c2"))
        cbDao.add(CollectionBookEntity("c1", "b1", 0L))
        cbDao.add(CollectionBookEntity("c2", "b1", 0L))
        bookDao.deleteById("b1")
        assertTrue(cbDao.collectionIdsForBook("b1").isEmpty())
    }

    @Test
    fun `remove 显式移除单条关系`() = runTest {
        bookDao.insert(book("b1"))
        collectionDao.insert(collection("c1"))
        cbDao.add(CollectionBookEntity("c1", "b1", 0L))
        cbDao.remove("c1", "b1")
        assertTrue(cbDao.collectionIdsForBook("b1").isEmpty())
    }

    private fun collection(id: String, name: String = "书架") =
        CollectionEntity(id = id, name = name, sortOrder = 0L, createdAt = 0L, kind = com.xuziyue.ebook.model.CollectionKind.CUSTOM)

    private fun book(id: String, title: String = "书") = BookEntity(
        id = id,
        contentHash = "hash-$id",
        title = title,
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
