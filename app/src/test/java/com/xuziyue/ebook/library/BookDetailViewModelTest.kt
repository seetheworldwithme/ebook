package com.xuziyue.ebook.library

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xuziyue.ebook.data.AppSettingsRepository
import com.xuziyue.ebook.data.BookRepository
import com.xuziyue.ebook.data.ReadingSessionRepository
import com.xuziyue.ebook.data.db.AnnotationEntity
import com.xuziyue.ebook.data.db.BookDatabase
import com.xuziyue.ebook.data.db.BookEntity
import com.xuziyue.ebook.data.db.BookmarkEntity
import com.xuziyue.ebook.data.db.ReadingProgressEntity
import com.xuziyue.ebook.model.HighlightColor
import com.xuziyue.ebook.model.ReadingStatus
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BookDetailViewModel 单测（LIB-04 跨表聚合）。
 *
 * 用 in-memory Room（真实 DAO/Repository）+ [SavedStateHandle]，验证四路 combine：
 * book（[BookRepository.observeBookById]）+ 进度 + 书签数 + 笔记数 → [BookDetailUiState] 字段映射。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BookDetailViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: BookDatabase
    private lateinit var sessionRepo: ReadingSessionRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(
            ctx,
            BookDatabase::class.java,
        ).allowMainThreadQueries().build()
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(ctx.cacheDir, "detail-test.preferences_pb").also { it.delete() } },
        )
        sessionRepo = ReadingSessionRepository(db.readingSessionDao(), AppSettingsRepository(dataStore))
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun seedBook(id: String = "b1") {
        db.bookDao().insert(
            BookEntity(
                id = id, contentHash = "hash-$id", title = "测试书-$id", authors = listOf("作者"),
                description = "简介", language = "zh", format = "EPUB",
                mediaType = "application/epub+zip", filePath = "/$id.epub",
                fileSize = 1024L, coverPath = null, importedAt = 1000L,
                lastOpenedAt = 2000L, status = ReadingStatus.READING,
            ),
        )
    }

    private fun vm(bookId: String) = BookDetailViewModel(
        BookRepository(db.bookDao()),
        db.readingProgressDao(),
        db.bookmarkDao(),
        db.annotationDao(),
        sessionRepo,
        SavedStateHandle(mapOf("bookId" to bookId)),
    )

    @Test
    fun `聚合 book 进度 书签数 笔记数`() = runTest {
        seedBook("b1")
        db.readingProgressDao().upsert(ReadingProgressEntity("b1", "loc", 0.42, 5000L, null))
        db.bookmarkDao().upsert(BookmarkEntity("m1", "b1", """{"href":"x"}""", "ex1", 0L))
        db.bookmarkDao().upsert(BookmarkEntity("m2", "b1", """{"href":"y"}""", "ex2", 0L))
        db.annotationDao().upsert(
            AnnotationEntity(
                id = "a1", bookId = "b1", locatorJson = """{"href":"x"}""",
                selectedText = "sel", note = "n", color = HighlightColor.YELLOW,
                createdAt = 0L, updatedAt = 0L, deletedAt = null,
            ),
        )

        val state = vm("b1").uiState.first { !it.loading }

        assertEquals("测试书-b1", state.book?.title)
        assertEquals(0.42, state.progression!!, 0.0001)
        assertEquals(5000L, state.progressUpdatedAt)
        assertEquals(2, state.bookmarkCount)
        assertEquals(1, state.annotationCount)
    }

    @Test
    fun `书不存在时 book 为 null`() = runTest {
        val state = vm("不存在").uiState.first { !it.loading }
        assertNull(state.book)
    }

    @Test
    fun `未读无进度 progression 为 null 计数为 0`() = runTest {
        seedBook("b1") // 不写 progress / bookmark / annotation
        val state = vm("b1").uiState.first { !it.loading }
        assertEquals("测试书-b1", state.book?.title)
        assertNull(state.progression)
        assertEquals(0, state.bookmarkCount)
        assertEquals(0, state.annotationCount)
    }

    @Test
    fun `软删批注不计入笔记数`() = runTest {
        seedBook("b1")
        db.annotationDao().upsert(
            AnnotationEntity(
                id = "a1", bookId = "b1", locatorJson = """{"href":"x"}""",
                selectedText = "sel", note = null, color = HighlightColor.YELLOW,
                createdAt = 0L, updatedAt = 0L, deletedAt = null,
            ),
        )
        db.annotationDao().softDelete("a1", 9999L) // 软删 → observe 不返回

        val state = vm("b1").uiState.first { !it.loading }
        assertEquals(0, state.annotationCount)
    }

    @Test
    fun `聚合本书阅读时长 DATA-04`() = runTest {
        seedBook("b1")
        db.readingProgressDao().upsert(ReadingProgressEntity("b1", "loc", 0.1, 5000L, null))
        // 两条会话：30s + 90s = 120s
        db.readingSessionDao().upsert(
            com.xuziyue.ebook.data.db.ReadingSessionEntity("s1", "b1", 0L, System.currentTimeMillis(), 30L),
        )
        db.readingSessionDao().upsert(
            com.xuziyue.ebook.data.db.ReadingSessionEntity("s2", "b1", 0L, System.currentTimeMillis(), 90L),
        )

        val state = vm("b1").uiState.first { !it.loading }
        assertEquals(120L, state.bookTotalSeconds)
    }
}
