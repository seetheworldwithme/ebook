package com.xuziyue.ebook.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xuziyue.ebook.data.db.AnnotationEntity
import com.xuziyue.ebook.data.db.BookDatabase
import com.xuziyue.ebook.data.db.BookEntity
import com.xuziyue.ebook.model.HighlightColor
import com.xuziyue.ebook.model.ReadingStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.readium.r2.shared.publication.Locator

/**
 * AnnotationRepository 单测（READ-07 / 红线 #9：先落盘再呈现）。
 */
@RunWith(RobolectricTestRunner::class)
class AnnotationRepositoryTest {

    private lateinit var db: BookDatabase
    private lateinit var repo: AnnotationRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BookDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = AnnotationRepository(db.annotationDao(), clock = { 1000L }, idGenerator = ::nextId)
    }

    private var seq = 0
    private fun nextId(): String = "an-${seq++}"

    @After
    fun tearDown() = db.close()

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

    /** 构造带 text.highlight 的 Locator（模拟文本选择产生的 selection.locator）。 */
    private fun selectionLocator(href: String, highlight: String, progression: Double? = 0.5): Locator {
        val json = JSONObject().apply {
            put("href", href)
            put("type", "text/html")
            put("locations", JSONObject().apply { progression?.let { put("totalProgression", it) } })
            put("text", JSONObject().apply { put("highlight", highlight) })
        }
        return Locator.fromJSON(json) ?: error("测试 Locator 构造失败")
    }

    @Test
    fun `add 返回新 id 且 selectedText 取自 locator text highlight`() = runTest {
        seedBook("b1")
        val id = repo.add("b1", selectionLocator("ch1", "被选中的文字"))
        assertEquals("an-0", id)
        val item = repo.observe("b1").first()[0]
        assertEquals("被选中的文字", item.selectedText)
        assertEquals(HighlightColor.Default, item.color)
        assertEquals(null, item.note)
    }

    @Test
    fun `add 后 observe 的 locator 可往返`() = runTest {
        seedBook("b1")
        val original = selectionLocator("ch1", "hi", progression = 0.42)
        repo.add("b1", original)
        val restored = repo.observe("b1").first()[0].locator
        assertEquals(original.href, restored.href)
        assertEquals(0.42, restored.locations.totalProgression!!, 1e-6)
    }

    @Test
    fun `updateNote 覆盖笔记`() = runTest {
        seedBook("b1")
        val id = repo.add("b1", selectionLocator("ch1", "sel"))
        repo.updateNote(id, "读后感")
        assertEquals("读后感", repo.observe("b1").first()[0].note)
    }

    @Test
    fun `softDelete 使 observe 清空`() = runTest {
        seedBook("b1")
        val id = repo.add("b1", selectionLocator("ch1", "sel"))
        repo.softDelete(id)
        assertTrue(repo.observe("b1").first().isEmpty())
    }

    @Test
    fun `softDeleteAllForBook 清空多批注`() = runTest {
        seedBook("b1")
        repo.add("b1", selectionLocator("ch1", "a"))
        repo.add("b1", selectionLocator("ch2", "b"))
        assertEquals(2, repo.observe("b1").first().size)
        repo.softDeleteAllForBook("b1")
        assertTrue(repo.observe("b1").first().isEmpty())
    }

    @Test
    fun `color 参数持久化且往返`() = runTest {
        seedBook("b1")
        repo.add("b1", selectionLocator("ch1", "sel"), color = HighlightColor.BLUE)
        val item = repo.observe("b1").first()[0]
        assertEquals(HighlightColor.BLUE, item.color)
    }

    @Test
    fun `损坏 locatorJson 的记录被 observe 跳过`() = runTest {
        seedBook("b1")
        db.annotationDao().upsert(
            AnnotationEntity(
                id = "bad", bookId = "b1", locatorJson = "not-json",
                selectedText = "x", note = null, color = HighlightColor.YELLOW,
                createdAt = 0L, updatedAt = 0L, deletedAt = null,
            ),
        )
        assertTrue(repo.observe("b1").first().isEmpty())
    }
}
