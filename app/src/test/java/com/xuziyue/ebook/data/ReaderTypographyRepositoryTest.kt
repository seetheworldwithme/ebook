package com.xuziyue.ebook.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.xuziyue.ebook.model.ReaderTextAlign
import com.xuziyue.ebook.model.ReaderTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * [ReaderTypographyRepository] 单测：用临时文件 DataStore 验证读写往返、默认值、增量更新。
 *
 * 纯 JVM（PreferenceDataStoreFactory.create(produceFile) 不需 Android Context），runBlocking 等 IO。
 */
class ReaderTypographyRepositoryTest {

    private val file = File.createTempFile("reader_settings_test", ".preferences_pb").apply { delete() }
    private val repo = ReaderTypographyRepository(
        PreferenceDataStoreFactory.create(produceFile = { file }),
    )

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `首次 observe 返回默认（theme SYSTEM，其余 null）`() = runBlocking {
        val t = repo.observe().first()

        assertEquals(ReaderTheme.SYSTEM, t.theme)
        assertNull(t.fontSize)
        assertNull(t.lineHeight)
        assertNull(t.textAlign)
        assertNull(t.pageMargins)
    }

    @Test
    fun `update 写入字号与行高并持久化`() = runBlocking {
        repo.update { it.copy(fontSize = 1.4, lineHeight = 1.7) }

        val t = repo.observe().first()
        assertEquals(1.4, t.fontSize!!, 1e-9)
        assertEquals(1.7, t.lineHeight!!, 1e-9)
        // 默认 theme 不因写入其它字段而漂移。
        assertEquals(ReaderTheme.SYSTEM, t.theme)
    }

    @Test
    fun `update 写入对齐与主题`() = runBlocking {
        repo.update { it.copy(textAlign = ReaderTextAlign.JUSTIFY, theme = ReaderTheme.DARK) }

        val t = repo.observe().first()
        assertEquals(ReaderTextAlign.JUSTIFY, t.textAlign)
        assertEquals(ReaderTheme.DARK, t.theme)
    }

    @Test
    fun `update 设 null 移除字段（恢复引擎默认）`() = runBlocking {
        repo.update { it.copy(fontSize = 1.4) }
        repo.update { it.copy(fontSize = null) }

        assertNull(repo.observe().first().fontSize)
    }

    @Test
    fun `update 基于当前值增量修改`() = runBlocking {
        repo.update { it.copy(fontSize = 1.0) }
        repo.update { it.copy(fontSize = (it.fontSize ?: 1.0) + 0.2) }

        assertEquals(1.2, repo.observe().first().fontSize!!, 1e-9)
    }
}
