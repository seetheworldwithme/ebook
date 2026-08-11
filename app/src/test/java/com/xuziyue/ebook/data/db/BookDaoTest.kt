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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BookDao 单测（Robolectric in-memory Room，design.md:349）。
 * 验证 CRUD / contentHash 去重 / 排序 / touchOpened / TypeConverter 往返。
 */
@RunWith(RobolectricTestRunner::class)
class BookDaoTest {

    private lateinit var db: BookDatabase
    private lateinit var dao: BookDao
    private lateinit var progressDao: ReadingProgressDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BookDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.bookDao()
        progressDao = db.readingProgressDao()
    }

    @After
    fun tearDown() = db.close()

    private fun book(
        id: String = "book-1",
        hash: String = "hash-1",
        title: String = "测试书",
        authors: List<String> = listOf("作者甲", "作者乙"),
        lastOpenedAt: Long? = null,
        importedAt: Long = 1000L,
        status: ReadingStatus = ReadingStatus.UNREAD,
    ) = BookEntity(
        id = id, contentHash = hash, title = title, authors = authors,
        description = "简介", language = "zh", format = "EPUB",
        mediaType = "application/epub+zip", filePath = "/data/books/$hash.epub",
        fileSize = 1024L, coverPath = null, importedAt = importedAt,
        lastOpenedAt = lastOpenedAt, status = status,
    )

    @Test
    fun `insert 后 getById 命中且 authors 往返`() = runTest {
        dao.insert(book())
        val got = dao.getById("book-1")
        assertNotNull(got)
        assertEquals("测试书", got!!.title)
        assertEquals(listOf("作者甲", "作者乙"), got.authors)
    }

    @Test
    fun `getByContentHash 命中`() = runTest {
        dao.insert(book())
        assertEquals("book-1", dao.getByContentHash("hash-1")?.id)
    }

    @Test(expected = Exception::class)
    fun `contentHash 重复 insert 抛约束异常`() = runTest {
        dao.insert(book(id = "b1", hash = "dup"))
        dao.insert(book(id = "b2", hash = "dup")) // 同 hash 不同 id → unique 索引冲突
    }

    @Test
    fun `observeAll 按 lastOpenedAt desc 未读排末尾`() = runTest {
        dao.insert(book(id = "a", hash = "ha", lastOpenedAt = null, importedAt = 3000L))
        dao.insert(book(id = "b", hash = "hb", lastOpenedAt = 2000L))
        dao.insert(book(id = "c", hash = "hc", lastOpenedAt = 1000L))
        val all = dao.observeAll().first()
        assertEquals(listOf("b", "c", "a"), all.map { it.id })
    }

    @Test
    fun `touchOpened 同时写 lastOpenedAt 与 status`() = runTest {
        dao.insert(book(lastOpenedAt = null, status = ReadingStatus.UNREAD))
        dao.touchOpened("book-1", 5000L, ReadingStatus.READING)
        val got = dao.getById("book-1")!!
        assertEquals(5000L, got.lastOpenedAt)
        assertEquals(ReadingStatus.READING, got.status)
    }

    @Test
    fun `status 经 TypeConverter name 往返`() = runTest {
        dao.insert(book(status = ReadingStatus.FINISHED))
        assertEquals(ReadingStatus.FINISHED, dao.getById("book-1")!!.status)
    }

    // ===== observeLibraryItems（LIB-01 进度 JOIN + LIB-03 搜索）=====

    @Test
    fun `observeLibraryItems LEFT JOIN 进度，无进度为 null`() = runTest {
        dao.insert(book(id = "a", hash = "ha"))
        dao.insert(book(id = "b", hash = "hb", lastOpenedAt = 2000L))
        progressDao.upsert(ReadingProgressEntity("b", "loc", 0.65, 1L, null))
        val items = dao.observeLibraryItems("").first()
        assertEquals(2, items.size)
        assertEquals(0.65, items.first { it.book.id == "b" }.progression!!, 0.0001)
        assertNull(items.first { it.book.id == "a" }.progression)
    }

    @Test
    fun `observeLibraryItems 按书名子串搜索`() = runTest {
        dao.insert(book(id = "a", hash = "ha", title = "山海经"))
        dao.insert(book(id = "b", hash = "hb", title = "红楼梦"))
        assertEquals(listOf("a"), dao.observeLibraryItems("山海").first().map { it.book.id })
    }

    @Test
    fun `observeLibraryItems 按 authors JSON 子串搜索`() = runTest {
        dao.insert(book(id = "a", hash = "ha", authors = listOf("曹雪芹", "高鹗")))
        dao.insert(book(id = "b", hash = "hb", authors = listOf("吴承恩")))
        assertEquals(listOf("a"), dao.observeLibraryItems("曹雪").first().map { it.book.id })
    }

    @Test
    fun `observeLibraryItems 搜索忽略 ASCII 大小写`() = runTest {
        dao.insert(book(id = "a", hash = "ha", title = "Alice"))
        dao.insert(book(id = "b", hash = "hb", title = "Bob"))
        assertEquals(listOf("a"), dao.observeLibraryItems("alice").first().map { it.book.id })
    }

    @Test
    fun `observeLibraryItems 空查询返回全部并保持最近阅读排序`() = runTest {
        dao.insert(book(id = "a", hash = "ha", lastOpenedAt = null, importedAt = 3000L))
        dao.insert(book(id = "b", hash = "hb", lastOpenedAt = 2000L))
        dao.insert(book(id = "c", hash = "hc", lastOpenedAt = 1000L))
        assertEquals(listOf("b", "c", "a"), dao.observeLibraryItems("").first().map { it.book.id })
    }
}
