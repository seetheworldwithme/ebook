package com.xuziyue.ebook.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xuziyue.ebook.model.HighlightColor
import com.xuziyue.ebook.model.ReadingStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * AnnotationDao 单测（Robolectric in-memory Room，READ-07）。
 * 验证 upsert / observe 过滤软删 / updateNote / softDeleteAllForBook / ForeignKey CASCADE。
 */
@RunWith(RobolectricTestRunner::class)
class AnnotationDaoTest {

    private lateinit var db: BookDatabase
    private lateinit var bookDao: BookDao
    private lateinit var annotationDao: AnnotationDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BookDatabase::class.java,
        ).allowMainThreadQueries().build()
        bookDao = db.bookDao()
        annotationDao = db.annotationDao()
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

    private fun annotation(id: String, bookId: String, note: String? = null, color: HighlightColor = HighlightColor.YELLOW) =
        AnnotationEntity(
            id = id, bookId = bookId, locatorJson = """{"href":"$id"}""",
            selectedText = "text-$id", note = note, color = color,
            createdAt = 0L, updatedAt = 0L, deletedAt = null,
        )

    @Test
    fun `observe 返回活跃批注且按 createdAt 倒序`() = runTest {
        seedBook("b1")
        annotationDao.upsert(annotation("a1", "b1").copy(createdAt = 1000L))
        annotationDao.upsert(annotation("a2", "b1").copy(createdAt = 2000L))
        val list = annotationDao.observe("b1").first()
        assertEquals(listOf("a2", "a1"), list.map { it.id })
    }

    @Test
    fun `softDelete 后 observe 不再返回`() = runTest {
        seedBook("b1")
        annotationDao.upsert(annotation("a1", "b1"))
        annotationDao.softDelete("a1", 9999L)
        assertTrue(annotationDao.observe("b1").first().isEmpty())
        // 软删：行仍在表内（getById 可读）
        assertNotNull(annotationDao.getById("a1"))
    }

    @Test
    fun `updateNote 覆盖笔记并刷新 updatedAt`() = runTest {
        seedBook("b1")
        annotationDao.upsert(annotation("a1", "b1"))
        annotationDao.updateNote("a1", "我的笔记", 5000L)
        val got = annotationDao.getById("a1")!!
        assertEquals("我的笔记", got.note)
        assertEquals(5000L, got.updatedAt)
    }

    @Test
    fun `softDeleteAllForBook 清空活跃批注`() = runTest {
        seedBook("b1")
        annotationDao.upsert(annotation("a1", "b1"))
        annotationDao.upsert(annotation("a2", "b1"))
        annotationDao.softDeleteAllForBook("b1", 9999L)
        assertTrue(annotationDao.observe("b1").first().isEmpty())
    }

    @Test
    fun `CASCADE 删书连带删批注`() = runTest {
        seedBook("b1")
        annotationDao.upsert(annotation("a1", "b1"))
        bookDao.deleteById("b1")
        assertTrue(annotationDao.observe("b1").first().isEmpty())
    }

    @Test
    fun `color 枚举经 TypeConverter 往返`() = runTest {
        seedBook("b1")
        annotationDao.upsert(annotation("a1", "b1", color = HighlightColor.GREEN))
        val got = annotationDao.getById("a1")!!
        assertEquals(HighlightColor.GREEN, got.color)
    }
}
