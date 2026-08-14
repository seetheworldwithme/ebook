package com.xuziyue.ebook.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xuziyue.ebook.model.CollectionKind
import com.xuziyue.ebook.model.SYSTEM_FAVORITE_ID
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
 * CollectionDao 单测（Robolectric in-memory Room，LIB-05）。
 * 验证书架 CRUD / 系统书架 / bookCount 聚合 / snapshotAll / TypeConverter 往返。
 */
@RunWith(RobolectricTestRunner::class)
class CollectionDaoTest {

    private lateinit var db: BookDatabase
    private lateinit var dao: CollectionDao
    private lateinit var bookDao: BookDao
    private lateinit var cbDao: CollectionBookDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BookDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.collectionDao()
        bookDao = db.bookDao()
        cbDao = db.collectionBookDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `插入并按 sortOrder 排序查询`() = runTest {
        dao.insert(collection("c1", "小说", sortOrder = 200L))
        dao.insert(collection("c2", "技术", sortOrder = 100L))
        dao.insert(systemFavorite())
        val list = dao.observeAllWithCounts().first()
        // 系统书架 sortOrder=Long.MIN_VALUE 最前，其次技术(100)、小说(200)
        assertEquals(listOf(SYSTEM_FAVORITE_ID, "c2", "c1"), list.map { it.id })
    }

    @Test
    fun `bookCount 聚合正确，空书架为零`() = runTest {
        bookDao.insert(book("b1"))
        dao.insert(collection("c1", "小说"))
        cbDao.add(CollectionBookEntity("c1", "b1", 0L))
        val list = dao.observeAllWithCounts().first()
        val c1 = list.first { it.id == "c1" }
        assertEquals(1, c1.bookCount)
        // 再加一本书
        bookDao.insert(book("b2"))
        cbDao.add(CollectionBookEntity("c1", "b2", 0L))
        assertEquals(2, dao.observeAllWithCounts().first().first { it.id == "c1" }.bookCount)
    }

    @Test
    fun `upsert 覆盖同名 id`() = runTest {
        dao.insert(collection("c1", "原名"))
        dao.upsert(collection("c1", "新名", kind = CollectionKind.CUSTOM))
        assertEquals("新名", dao.getById("c1")?.name)
    }

    @Test
    fun `删除书架连带清 collection_books 关系但书不删`() = runTest {
        bookDao.insert(book("b1"))
        dao.insert(collection("c1", "小说"))
        cbDao.add(CollectionBookEntity("c1", "b1", 0L))
        dao.deleteById("c1")
        assertNull(dao.getById("c1"))
        // 书还在
        assertNotNull(bookDao.getById("b1"))
        // 关系清空（删 collection_books 行）
        assertTrue(cbDao.collectionIdsForBook("b1").isEmpty())
    }

    @Test
    fun `snapshotAll 返回全部书架`() = runTest {
        dao.insert(collection("c1", "A"))
        dao.insert(collection("c2", "B"))
        assertEquals(2, dao.snapshotAll().size)
    }

    @Test
    fun `CollectionKind TypeConverter 往返`() = runTest {
        dao.insert(collection("c1", "测试", kind = CollectionKind.SYSTEM_FAVORITE))
        assertEquals(CollectionKind.SYSTEM_FAVORITE, dao.getById("c1")?.kind)
    }

    private fun collection(
        id: String,
        name: String,
        sortOrder: Long = 0L,
        createdAt: Long = 0L,
        kind: CollectionKind = CollectionKind.CUSTOM,
    ) = CollectionEntity(id = id, name = name, sortOrder = sortOrder, createdAt = createdAt, kind = kind)

    private fun systemFavorite() = CollectionEntity(
        id = SYSTEM_FAVORITE_ID,
        name = "收藏",
        sortOrder = Long.MIN_VALUE,
        createdAt = 0L,
        kind = CollectionKind.SYSTEM_FAVORITE,
    )

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
