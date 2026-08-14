package com.xuziyue.ebook.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xuziyue.ebook.data.db.BookDatabase
import com.xuziyue.ebook.data.db.BookEntity
import com.xuziyue.ebook.data.db.ReadingSessionDao
import com.xuziyue.ebook.data.db.ReadingSessionEntity
import com.xuziyue.ebook.model.ReadingStatus
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 阅读会话 DAO + Repository 测试（DATA-04，Robolectric in-memory Room）。
 *
 * 覆盖：聚合查询（SUM / 今日 / 本周 / 每日趋势）、删除清空、CASCADE 删书连带删会话、
 * 以及 Repository 的 startSession / endSession（含统计开关） / touchActive / clearAll。
 */
@RunWith(RobolectricTestRunner::class)
class ReadingSessionDaoTest {

    private lateinit var db: BookDatabase
    private lateinit var dao: ReadingSessionDao
    private lateinit var appSettings: AppSettingsRepository
    private lateinit var repo: ReadingSessionRepository
    private var dsFile: File? = null

    @Before
    fun setUp() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, BookDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.readingSessionDao()
        val file = File(ctx.cacheDir, "settings-test.preferences_pb").also { it.delete() }
        dsFile = file
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        // 默认开统计
        dataStore.edit { it[booleanPreferencesKey("app_reading_stats_enabled")] = true }
        appSettings = AppSettingsRepository(dataStore)
        repo = ReadingSessionRepository(dao, appSettings)
    }

    @After
    fun tearDown() {
        db.close()
        dsFile?.delete()
    }

    private suspend fun seedBook(id: String = "b1") {
        db.bookDao().insert(
            BookEntity(
                id = id, contentHash = "h$id", title = "书$id", authors = emptyList(),
                description = null, language = null, format = "EPUB",
                mediaType = "application/epub+zip", filePath = "/$id.epub", fileSize = 0,
                coverPath = null, importedAt = 0, lastOpenedAt = null, status = ReadingStatus.UNREAD,
            ),
        )
    }

    private fun session(id: String, bookId: String, startedAt: Long, endedAt: Long, activeSeconds: Long) =
        ReadingSessionEntity(id, bookId, startedAt, endedAt, activeSeconds)

    @Test
    fun `totalActiveSecondsForBook 累加同书多会话`() = runTest {
        seedBook()
        dao.upsert(session("s1", "b1", 0L, 1000L, 30L))
        dao.upsert(session("s2", "b1", 2000L, 3000L, 90L))
        assertEquals(120L, dao.totalActiveSecondsForBook("b1"))
        assertEquals(0L, dao.totalActiveSecondsForBook("nope"))
    }

    @Test
    fun `今日区间聚合按 endedAt 过滤`() = runTest {
        seedBook()
        dao.upsert(session("s1", "b1", 0L, 1000L, 30L))
        dao.upsert(session("s2", "b1", 1000L, 2000L, 40L))
        dao.upsert(session("s3", "b1", 0L, 200_000_000L, 999L)) // 次日（区间外）
        assertEquals(70L, dao.todayTotalSeconds(0L, 86_400_000L))
        assertEquals(70L, dao.todaySecondsForBook("b1", 0L, 86_400_000L))
    }

    @Test
    fun `weekTotalSeconds 取自起点起全部`() = runTest {
        seedBook()
        dao.upsert(session("s1", "b1", 0L, 1000L, 30L))
        dao.upsert(session("s2", "b1", 0L, 1_000_000L, 50L))
        assertEquals(80L, dao.weekTotalSeconds(0L))
    }

    @Test
    fun `dailyTotals 按本地日期分组`() = runTest {
        seedBook()
        dao.upsert(session("s1", "b1", 0L, 1_000L, 30L))
        dao.upsert(session("s2", "b1", 0L, 1_000L, 20L))
        val totals = dao.dailyTotals(0L)
        assertEquals(1, totals.size)
        assertEquals(50L, totals[0].s)
    }

    @Test
    fun `deleteAll 清空`() = runTest {
        seedBook()
        dao.upsert(session("s1", "b1", 0L, 1000L, 30L))
        dao.deleteAll()
        assertEquals(0L, dao.totalActiveSecondsForBook("b1"))
    }

    @Test
    fun `CASCADE 删书连带删会话`() = runTest {
        seedBook()
        dao.upsert(session("s1", "b1", 0L, 1000L, 30L))
        db.bookDao().deleteById("b1")
        assertEquals(0L, dao.totalActiveSecondsForBook("b1"))
    }

    @Test
    fun `Repository startSession 开关开时返回 id 并可 endSession 落盘`() = runTest {
        seedBook()
        var clock = 1_000L
        val r = ReadingSessionRepository(dao, appSettings, clock = { clock }, idGenerator = { "gen-id" })
        val id = r.startSession("b1")
        assertEquals("gen-id", id)
        clock = 61_000L // 60s 后结束
        r.endSession(id)
        assertEquals(60L, dao.totalActiveSecondsForBook("b1"))
    }

    @Test
    fun `Repository startSession 开关关时返回 null 不落盘`() = runTest {
        seedBook()
        appSettings.setReadingStatsEnabled(false)
        val id = repo.startSession("b1")
        assertNull(id)
        assertEquals(0L, dao.totalActiveSecondsForBook("b1"))
    }

    @Test
    fun `Repository endSession 静止超阈值裁剪`() = runTest {
        seedBook()
        var clock = 0L
        val r = ReadingSessionRepository(dao, appSettings, clock = { clock }, idGenerator = { "sid" })
        val id = r.startSession("b1")!!
        clock = 30_000L // 30s 时翻页
        r.touchActive(id)
        clock = 400_000L // 又过 370s 才退出（远超 5min 阈值）
        r.endSession(id)
        // 30s + 300s(阈值) = 330s
        assertEquals(330L, dao.totalActiveSecondsForBook("b1"))
    }

    @Test
    fun `Repository clearAll 清空会话`() = runTest {
        seedBook()
        dao.upsert(session("s1", "b1", 0L, 1000L, 30L))
        repo.clearAll()
        assertEquals(0L, dao.totalActiveSecondsForBook("b1"))
    }

    @Test
    fun `Repository endSession 无活跃会话时 no-op`() = runTest {
        repo.endSession(null) // 不应抛异常
        assertTrue(dao.snapshotAll().isEmpty())
    }

    @Test
    fun `snapshotAll 返回全表`() = runTest {
        seedBook("b1"); seedBook("b2")
        dao.upsert(session("s1", "b1", 0L, 1000L, 30L))
        dao.upsert(session("s2", "b2", 0L, 1000L, 40L))
        assertEquals(2, dao.snapshotAll().size)
    }

    @Test
    fun `distinctRecentDays 去重并倒序`() = runTest {
        seedBook()
        dao.upsert(session("s1", "b1", 0L, 1_000L, 30L))
        dao.upsert(session("s2", "b1", 0L, 1_000L, 20L)) // 同日
        val days = dao.distinctRecentDays(0L)
        assertEquals(1, days.size)
    }
}

