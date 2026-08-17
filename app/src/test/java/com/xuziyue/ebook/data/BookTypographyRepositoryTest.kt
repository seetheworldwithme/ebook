package com.xuziyue.ebook.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xuziyue.ebook.data.db.BookDatabase
import com.xuziyue.ebook.data.db.BookEntity
import com.xuziyue.ebook.model.ReaderTheme
import com.xuziyue.ebook.model.ReaderTypography
import com.xuziyue.ebook.model.ReadingStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BookTypographyRepository 单测（Robolectric in-memory Room，TYPE-05）。
 * 验证 observe/update 原子修改/clear（恢复全局默认）/ clock 注入。
 */
@RunWith(RobolectricTestRunner::class)
class BookTypographyRepositoryTest {

    private lateinit var db: BookDatabase
    private lateinit var repository: BookTypographyRepository
    private var fakeNow = 1000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BookDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = BookTypographyRepository(db.bookTypographyDao(), clock = { fakeNow })
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `observe 无覆盖返回 Empty`() = runTest {
        db.bookDao().insert(book("b1"))
        assertEquals(BookTypographyOverrides.Empty, repository.observe("b1").first())
    }

    @Test
    fun `update 基于当前值原子修改并可叠加`() = runTest {
        db.bookDao().insert(book("b1"))
        // 第一次：设字号（从 Empty 出发）
        repository.update("b1") { it.copy(values = it.values.copy(fontSize = 1.5)) }
        // 第二次：在已有覆盖上叠加主题（原子读改写，不清掉字号）
        repository.update("b1") { it.copy(values = it.values.copy(theme = ReaderTheme.DARK)) }

        val current = repository.observe("b1").first()
        assertEquals(1.5, current.values.fontSize!!, 1e-9)
        assertEquals(ReaderTheme.DARK, current.values.theme)
    }

    @Test
    fun `update 写入 clock 时间戳`() = runTest {
        db.bookDao().insert(book("b1"))
        fakeNow = 4242L
        repository.update("b1") { it.copy(values = it.values.copy(fontSize = 1.5)) }
        assertEquals(4242L, db.bookTypographyDao().get("b1")?.updatedAt)
    }

    @Test
    fun `clear 删行恢复全局默认`() = runTest {
        db.bookDao().insert(book("b1"))
        repository.update("b1") { it.copy(values = it.values.copy(fontSize = 1.5)) }
        assertTrue(repository.observe("b1").first().values != BookTypographyOverrides.Empty.values)
        repository.clear("b1")
        assertEquals(BookTypographyOverrides.Empty, repository.observe("b1").first())
        assertFalse(repository.observe("b1").first().values != BookTypographyOverrides.Empty.values)
    }

    @Test
    fun `snapshotAll 供备份全表快照`() = runTest {
        db.bookDao().insert(book("b1"))
        repository.update("b1") { it.copy(values = ReaderTypography(fontSize = 1.5)) }
        assertEquals(1, repository.snapshotAll().size)
    }

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
        status = ReadingStatus.UNREAD,
    )
}
