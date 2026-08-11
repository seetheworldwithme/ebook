package com.xuziyue.ebook.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xuziyue.ebook.data.db.BookDatabase
import com.xuziyue.ebook.data.db.BookmarkDao
import com.xuziyue.ebook.data.db.BookEntity
import com.xuziyue.ebook.model.ReadingStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.readium.r2.shared.publication.Locator

/**
 * BookmarkRepository 单测（READ-06，重点验 toggle 去重口径）。
 */
@RunWith(RobolectricTestRunner::class)
class BookmarkRepositoryTest {

    private lateinit var db: BookDatabase
    private lateinit var dao: BookmarkDao
    private lateinit var repo: BookmarkRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BookDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.bookmarkDao()
        repo = BookmarkRepository(dao, clock = { 1000L }, idGenerator = ::nextId)
    }

    @After
    fun tearDown() = db.close()

    private var seq = 0
    private fun nextId(): String = "bm-${seq++}"

    private suspend fun seedBook(id: String) {
        db.bookDao().insert(
            BookEntity(
                id = id, contentHash = "hash-$id", title = id, authors = emptyList(),
                description = null, language = null, format = "EPUB",
                mediaType = "application/epub+zip", filePath = "/$id.epub",
                fileSize = 0L, coverPath = null, importedAt = 0L,
                lastOpenedAt = null, status = ReadingStatus.UNREAD,
            ),
        )
    }

    private fun locator(href: String, progression: Double?, after: String? = null): Locator {
        val json = JSONObject().apply {
            put("href", href)
            put("type", "text/html")
            // 用 totalProgression（Locator.Locations 的全书进度字段，与 VM 去重/进度判定一致）
            put("locations", JSONObject().apply { progression?.let { put("totalProgression", it) } })
            after?.let { put("text", JSONObject().apply { put("after", it) }) }
        }
        return Locator.fromJSON(json) ?: error("测试 Locator 构造失败")
    }

    @Test
    fun `toggle 首次添加返回 true 且持久化`() = runTest {
        seedBook("b1")
        val added = repo.toggleBookmark("b1", locator("ch1", 0.5), excerpt = "摘录")
        assertTrue(added)
        val list = repo.observe("b1").first()
        assertEquals(1, list.size)
        assertEquals("摘录", list[0].excerpt)
    }

    @Test
    fun `toggle 同位置再次调用撤销并返回 false`() = runTest {
        seedBook("b1")
        repo.toggleBookmark("b1", locator("ch1", 0.5), null)
        val removed = repo.toggleBookmark("b1", locator("ch1", 0.5), null)
        assertFalse(removed)
        assertTrue(repo.observe("b1").first().isEmpty())
    }

    @Test
    fun `toggle 不同 href 各自保留`() = runTest {
        seedBook("b1")
        repo.toggleBookmark("b1", locator("ch1", 0.1), null)
        repo.toggleBookmark("b1", locator("ch2", 0.1), null)
        assertEquals(2, repo.observe("b1").first().size)
    }

    @Test
    fun `toggle 同 href 且 progression 在容差内视为同位置`() = runTest {
        seedBook("b1")
        repo.toggleBookmark("b1", locator("ch1", 0.5000), null)
        // 0.5005 与 0.5000 差 0.0005 < 1e-3 → 同位置 → toggle off
        val removed = repo.toggleBookmark("b1", locator("ch1", 0.5005), null)
        assertFalse(removed)
        assertTrue(repo.observe("b1").first().isEmpty())
    }

    @Test
    fun `toggle 同 href 但 progression 超出容差各自保留`() = runTest {
        seedBook("b1")
        repo.toggleBookmark("b1", locator("ch1", 0.40), null)
        val added = repo.toggleBookmark("b1", locator("ch1", 0.60), null)
        assertTrue(added)
        assertEquals(2, repo.observe("b1").first().size)
    }

    @Test
    fun `locator 经 PersistedLocator 往返`() = runTest {
        seedBook("b1")
        val original = locator("ch1", 0.33, after = "上下文")
        repo.toggleBookmark("b1", original, "摘录")
        val restored = repo.observe("b1").first()[0].locator
        assertEquals(original.href, restored.href)
        assertEquals(0.33, restored.locations.totalProgression!!, 1e-6)
    }

    @Test
    fun `delete 与 deleteAllForBook`() = runTest {
        seedBook("b1")
        repo.toggleBookmark("b1", locator("ch1", 0.1), null)
        repo.toggleBookmark("b1", locator("ch2", 0.1), null)
        val first = repo.observe("b1").first()[0]
        repo.delete(first.id)
        assertEquals(1, repo.observe("b1").first().size)
        repo.deleteAllForBook("b1")
        assertTrue(repo.observe("b1").first().isEmpty())
    }

    @Test
    fun `损坏 locatorJson 的记录被 observe 跳过`() = runTest {
        seedBook("b1")
        // 注入一条 locatorJson 损坏的记录（绕过 Repository 直接写 DAO）
        dao.upsert(
            com.xuziyue.ebook.data.db.BookmarkEntity(
                id = "bad", bookId = "b1", locatorJson = "not-json", excerpt = null, createdAt = 0L,
            ),
        )
        assertNull(repo.observe("b1").first().firstOrNull { it.id == "bad" })
    }
}
