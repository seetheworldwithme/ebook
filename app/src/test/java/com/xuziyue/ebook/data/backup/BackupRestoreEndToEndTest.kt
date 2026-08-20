package com.xuziyue.ebook.data.backup

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xuziyue.ebook.data.db.BookDatabase
import com.xuziyue.ebook.data.db.BookEntity
import com.xuziyue.ebook.data.db.BookmarkEntity
import com.xuziyue.ebook.data.db.ReadingProgressEntity
import com.xuziyue.ebook.data.db.ReadingSessionEntity
import com.xuziyue.ebook.model.HighlightColor
import com.xuziyue.ebook.model.ReadingStatus
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 备份→恢复端到端测试（DATA-03，Robolectric in-memory Room + 真实文件 + 真实 ZIP）。
 *
 * 流程：造数据（书+进度+书签+会话+设置）→ [BackupUseCase.backup] 写 ZIP 到临时文件 →
 * [RestoreUseCase.preview] 验证分类 → [RestoreUseCase.restore] 验证数据回来。
 *
 * 验证核心：全量备份可逆还原（design.md「恢复」验收）。
 */
@RunWith(RobolectricTestRunner::class)
class BackupRestoreEndToEndTest {

    private lateinit var db: BookDatabase
    private lateinit var backupUseCase: BackupUseCase
    private lateinit var restoreUseCase: RestoreUseCase
    private lateinit var dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    private var zipFile: File? = null

    @Before
    fun setUp() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, BookDatabase::class.java)
            .allowMainThreadQueries().build()
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(ctx.cacheDir, "e2e-test.preferences_pb").also { it.delete() } },
        )
        backupUseCase = BackupUseCase(
            db.bookDao(), db.readingProgressDao(), db.bookmarkDao(),
            db.annotationDao(), db.readingSessionDao(),
            db.collectionDao(), db.collectionBookDao(),
            db.bookTypographyDao(),
            dataStore, ctx,
        )
        restoreUseCase = RestoreUseCase(
            db, db.bookDao(), db.readingProgressDao(), db.bookmarkDao(),
            db.annotationDao(), db.readingSessionDao(),
            db.collectionDao(), db.collectionBookDao(),
            db.bookTypographyDao(),
            dataStore, ctx,
        )
    }

    @After
    fun tearDown() {
        db.close()
        zipFile?.delete()
    }

    @Test
    fun `备份后恢复 全表数据回来`() = runTest {
        seedBook("b1", "hash1")
        dataStore.edit { it[booleanPreferencesKey("app_reading_stats_enabled")] = true }

        // 备份到临时 ZIP
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val zip = File(ctx.cacheDir, "e2e-backup.zip").also { it.delete() }
        zipFile = zip
        val out = backupUseCase.backup(android.net.Uri.fromFile(zip))
        assertTrue("备份应成功：$out", out is BackupUseCase.Outcome.Success)

        // 清空当前库（模拟换机）
        db.bookDao().deleteById("b1")

        // 恢复（OVERWRITE_ALL，本地已空无冲突）
        val result = restoreUseCase.restore(android.net.Uri.fromFile(zip), RestoreUseCase.Strategy.OVERWRITE_ALL)
        assertTrue("恢复应成功：$result", result is RestoreUseCase.Outcome.Restored)
        result as RestoreUseCase.Outcome.Restored
        assertEquals(1, result.newBooks)

        // 书 + 进度 + 书签 + 会话回来
        val restored = db.bookDao().getByContentHash("hash1")
        assertEquals("测试书", restored?.title)
        assertEquals("loc-json", db.readingProgressDao().get(restored!!.id)?.locatorJson)
        assertEquals(1, db.bookmarkDao().forBook(restored.id).size)
        assertEquals(60L, db.readingSessionDao().totalActiveSecondsForBook(restored.id))
        // settings 回来
        val prefs = dataStore.data.first()
        assertTrue(prefs[booleanPreferencesKey("app_reading_stats_enabled")] ?: false)
    }

    @Test
    fun `预览 正确分类新增书`() = runTest {
        seedBook("b1", "hash1")
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val zip = File(ctx.cacheDir, "preview-backup.zip").also { it.delete() }
        zipFile = zip
        backupUseCase.backup(android.net.Uri.fromFile(zip))

        // 本地已有同 hash（UNCHANGED），删一本造 NEW 场景——这里本地有，预览应判 UNCHANGED
        val preview = restoreUseCase.preview(android.net.Uri.fromFile(zip))
        assertEquals(1, preview.totalBooks)
        assertEquals(0, preview.newCount) // 本地已有
        // 同书进度 0 vs 0 → UNCHANGED
        assertEquals(RestoreUseCase.ConflictKind.UNCHANGED, preview.items[0].kind)
    }

    @Test
    fun `恶意 ZIP 路径遍历 恢复不落盘外部文件`() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // 构造含 ../evil 的 ZIP
        val zip = File(ctx.cacheDir, "evil-backup.zip").also { it.delete() }
        zipFile = zip
        java.util.zip.ZipOutputStream(zip.outputStream()).use { zos ->
            // backup.json 最小合法
            zos.putNextEntry(java.util.zip.ZipEntry("backup.json"))
            zos.write(
                """{"schemaVersion":1,"exportedAt":0,"books":[],"readingProgress":[],"bookmarks":[],"annotations":[],"readingSessions":[]}""".toByteArray(),
            )
            zos.closeEntry()
            // 恶意路径
            zos.putNextEntry(java.util.zip.ZipEntry("../evil-outside.txt"))
            zos.write("pwned".toByteArray())
            zos.closeEntry()
        }

        // 恢复不应抛异常、不应在 cacheDir 父目录创建 evil-outside.txt
        val result = restoreUseCase.restore(android.net.Uri.fromFile(zip), RestoreUseCase.Strategy.OVERWRITE_ALL)
        // 空库恢复，应为 Restored(0,0,0) 或失败——关键是外部文件未生成
        val evilFile = File(ctx.cacheDir.parentFile, "evil-outside.txt")
        assertTrue("Zip Slip 应被拦截：恶意文件不应落在目标目录外", !evilFile.exists())
    }

    private suspend fun seedBook(id: String, hash: String) {
        db.bookDao().insert(
            BookEntity(
                id = id, contentHash = hash, title = "测试书", authors = listOf("作者"),
                description = null, language = "zh", format = "EPUB",
                mediaType = "application/epub+zip",
                filePath = "/tmp/$hash.epub", // 测试无真实书源文件，备份时跳过文件条目
                fileSize = 0L, coverPath = null, importedAt = 0L,
                lastOpenedAt = null, status = ReadingStatus.READING,
            ),
        )
        db.readingProgressDao().upsert(ReadingProgressEntity(id, "loc-json", 0.1, 5000L, null))
        db.bookmarkDao().upsert(BookmarkEntity("m1", id, """{"href":"x"}""", "ex", 0L))
        db.readingSessionDao().upsert(ReadingSessionEntity("s1", id, 0L, 60000L, 60L))
    }
}
