package com.xuziyue.ebook.data.export

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xuziyue.ebook.R
import com.xuziyue.ebook.data.db.AnnotationEntity
import com.xuziyue.ebook.data.db.BookDatabase
import com.xuziyue.ebook.data.db.BookEntity
import com.xuziyue.ebook.data.db.BookmarkEntity
import com.xuziyue.ebook.data.db.ReadingProgressEntity
import com.xuziyue.ebook.model.HighlightColor
import com.xuziyue.ebook.model.ReadingStatus
import com.xuziyue.ebook.ui.UserMessage
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ExportBookDataUseCase 单测（DATA-01）。
 * Robolectric in-memory Room + file:// Uri（openOutputStream 走 FileOutputStream，可端到端验证写入）。
 */
@RunWith(RobolectricTestRunner::class)
class ExportBookDataUseCaseTest {

    private lateinit var db: BookDatabase
    private lateinit var useCase: ExportBookDataUseCase
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, BookDatabase::class.java)
            .allowMainThreadQueries().build()
        useCase = ExportBookDataUseCase(
            bookDao = db.bookDao(),
            annotationDao = db.annotationDao(),
            bookmarkDao = db.bookmarkDao(),
            progressDao = db.readingProgressDao(),
            context = context,
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seedBook(id: String) {
        db.bookDao().insert(
            BookEntity(
                id = id, contentHash = "hash-$id", title = "书$id", authors = listOf("作者"),
                description = null, language = null, format = "EPUB",
                mediaType = "application/epub+zip", filePath = "/$id.epub",
                fileSize = 0L, coverPath = null, importedAt = 0L,
                lastOpenedAt = null, status = ReadingStatus.UNREAD,
            ),
        )
    }

    /** 合法 PersistedLocator JSON（含 schemaVersion + locatorJson 内层）。 */
    private val locatorJson = """{"schemaVersion":1,"locatorJson":"{\"href\":\"ch1\"}"}"""

    @Test
    fun `书不存在返回 Failed`() = runTest {
        val tmp = FileTemp()
        val r = useCase.export("nope", ExportBookDataUseCase.Format.JSON, tmp.uri)
        assertEquals(
            R.string.error_book_not_found,
            ((r as ExportBookDataUseCase.Outcome.Failed).message as UserMessage.Res).resId,
        )
    }

    @Test
    fun `三件套与进度全空返回 Failed`() = runTest {
        seedBook("b1") // 只种书，无批注/书签/进度
        val tmp = FileTemp()
        val r = useCase.export("b1", ExportBookDataUseCase.Format.JSON, tmp.uri)
        assertTrue(r is ExportBookDataUseCase.Outcome.Failed)
        assertEquals(
            R.string.error_export_empty,
            ((r as ExportBookDataUseCase.Outcome.Failed).message as UserMessage.Res).resId,
        )
    }

    @Test
    fun `导出 JSON 含 schemaVersion 与高亮文字`() = runTest {
        seedBook("b1")
        db.annotationDao().upsert(
            AnnotationEntity(
                id = "a1", bookId = "b1", locatorJson = locatorJson,
                selectedText = "鬼哭", note = "goodpassage", color = HighlightColor.YELLOW,
                createdAt = 100L, updatedAt = 200L, deletedAt = null,
            ),
        )
        val tmp = FileTemp()
        val r = useCase.export("b1", ExportBookDataUseCase.Format.JSON, tmp.uri)
        assertTrue(r is ExportBookDataUseCase.Outcome.Success)
        val content = tmp.file.readText()
        assertTrue(content.contains("\"schemaVersion\""))
        assertTrue(content.contains("鬼哭"))
        assertTrue(content.contains("goodpassage"))
    }

    @Test
    fun `导出 Markdown 含书名与笔记`() = runTest {
        seedBook("b1")
        db.bookmarkDao().upsert(
            BookmarkEntity(
                id = "bm1", bookId = "b1", locatorJson = locatorJson,
                excerpt = "书签摘录", createdAt = 500L,
            ),
        )
        val tmp = FileTemp()
        val r = useCase.export("b1", ExportBookDataUseCase.Format.MARKDOWN, tmp.uri)
        assertTrue(r is ExportBookDataUseCase.Outcome.Success)
        val content = tmp.file.readText()
        assertTrue(content.contains(context.getString(R.string.export_md_title, "书b1")))
        assertTrue(content.contains("书签摘录"))
    }

    @Test
    fun `损坏 locatorJson 的批注被跳过`() = runTest {
        seedBook("b1")
        db.annotationDao().upsert(
            AnnotationEntity(
                id = "bad", bookId = "b1", locatorJson = "not-json",
                selectedText = "x", note = null, color = HighlightColor.YELLOW,
                createdAt = 0L, updatedAt = 0L, deletedAt = null,
            ),
        )
        db.bookmarkDao().upsert(
            BookmarkEntity(
                id = "bm1", bookId = "b1", locatorJson = locatorJson,
                excerpt = "有效书签", createdAt = 1L,
            ),
        )
        val tmp = FileTemp()
        val r = useCase.export("b1", ExportBookDataUseCase.Format.JSON, tmp.uri)
        // 书签仍可导出（批注损坏被跳过，但有书签 → 非空 → Success）
        assertTrue(r is ExportBookDataUseCase.Outcome.Success)
        val content = tmp.file.readText()
        assertTrue(content.contains("有效书签"))
        assertTrue(!content.contains("\"not-json\"")) // 损坏批注不进导出
    }

    /** file:// Uri 临时文件（Robolectric ContentResolver openOutputStream 走 FileOutputStream）。 */
    private inner class FileTemp {
        val file: java.io.File =
            java.io.File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "export-test-${System.nanoTime()}.tmp")
        val uri: Uri = Uri.fromFile(file)
    }
}
