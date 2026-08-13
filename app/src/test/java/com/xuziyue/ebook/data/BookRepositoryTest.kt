package com.xuziyue.ebook.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xuziyue.ebook.data.db.AnnotationEntity
import com.xuziyue.ebook.data.db.BookDatabase
import com.xuziyue.ebook.data.db.BookEntity
import com.xuziyue.ebook.data.db.BookmarkEntity
import com.xuziyue.ebook.data.db.ReadingProgressEntity
import com.xuziyue.ebook.data.db.toDomain
import com.xuziyue.ebook.model.HighlightColor
import com.xuziyue.ebook.model.ReadingStatus
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BookRepository 单测（IMP-07 删书，重点验 [BookRepository.deleteBook]）。
 *
 * 覆盖：① 删 books 主行；② ForeignKey CASCADE 连带清 reading_progress / bookmarks / annotations；
 * ③ best-effort 删书源文件 + 封面；④ 文件不存在时不抛异常（runCatching 兜底）。
 * Robolectric in-memory Room（同 [BookmarkRepositoryTest] / [AnnotationDaoTest] 范式）。
 */
@RunWith(RobolectricTestRunner::class)
class BookRepositoryTest {

    private lateinit var db: BookDatabase
    private lateinit var repo: BookRepository
    private lateinit var booksDir: File

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BookDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = BookRepository(db.bookDao())
        val context = ApplicationProvider.getApplicationContext<Context>()
        booksDir = File(context.filesDir, "test-books").apply { mkdirs() }
    }

    @After
    fun tearDown() = db.close()

    /** 创建真实文件（写入内容）并返回绝对路径，作书源 / 封面。 */
    private fun realFile(name: String): String =
        File(booksDir, name).apply { writeText("content") }.absolutePath

    /** 插入一本带真实文件的书，返回 domain Book（经 toDomain，验证真实映射路径）。 */
    private suspend fun seedBook(
        id: String = "book-1",
        filePath: String = realFile("$id.epub"),
        coverPath: String? = realFile("$id.png"),
    ): com.xuziyue.ebook.model.Book {
        db.bookDao().insert(
            BookEntity(
                id = id, contentHash = "hash-$id", title = "测试书$id", authors = listOf("作者"),
                description = null, language = "zh", format = "EPUB",
                mediaType = "application/epub+zip", filePath = filePath,
                fileSize = 1024L, coverPath = coverPath, importedAt = 0L,
                lastOpenedAt = null, status = ReadingStatus.UNREAD,
            ),
        )
        return db.bookDao().getById(id)!!.toDomain()
    }

    @Test
    fun `deleteBook 删除 books 主行`() = runTest {
        val book = seedBook("b1")
        repo.deleteBook(book)
        assertNull(db.bookDao().getById("b1"))
    }

    @Test
    fun `deleteBook CASCADE 连带清进度_书签_批注`() = runTest {
        val book = seedBook("b1")
        db.readingProgressDao().upsert(ReadingProgressEntity("b1", """{"href":"ch1"}""", 0.5, 1L, null))
        db.bookmarkDao().upsert(BookmarkEntity("bm1", "b1", """{"href":"ch1"}""", "摘录", 0L))
        db.annotationDao().upsert(
            AnnotationEntity("an1", "b1", """{"href":"ch1"}""", "选中文字", null, HighlightColor.YELLOW, 0L, 0L, null),
        )

        repo.deleteBook(book)

        // 三张子表行均被 ForeignKey CASCADE 物理删除（含 annotation 软删行）
        assertNull(db.readingProgressDao().get("b1"))
        assertTrue(db.bookmarkDao().forBook("b1").isEmpty())
        assertTrue(db.annotationDao().snapshotForBook("b1").isEmpty())
    }

    @Test
    fun `deleteBook 删除书源文件和封面文件`() = runTest {
        val bookFile = realFile("b1.epub")
        val coverFile = realFile("b1.png")
        val book = seedBook("b1", filePath = bookFile, coverPath = coverFile)
        assertTrue(File(bookFile).exists())
        assertTrue(File(coverFile).exists())

        repo.deleteBook(book)

        assertFalse(File(bookFile).exists())
        assertFalse(File(coverFile).exists())
    }

    @Test
    fun `deleteBook 文件不存在时不抛异常`() = runTest {
        // filePath/coverPath 指向不存在路径——runCatching 兜底，DB 删除仍成功
        val book = seedBook("b1", filePath = "/no/such/book.epub", coverPath = "/no/such/cover.png")
        repo.deleteBook(book)
        assertNull(db.bookDao().getById("b1"))
    }
}
