package com.xuziyue.ebook.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.xuziyue.ebook.model.ReaderDisplaySettings
import com.xuziyue.ebook.model.ReaderOrientation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [ReaderDisplaySettingsRepository] 单测：用临时文件 DataStore 验证读写往返、默认值、增量更新。
 *
 * 纯 JVM（PreferenceDataStoreFactory.create(produceFile) 不需 Android Context），runBlocking 等 IO。
 * 与 [ReaderTypographyRepositoryTest] 同范式。
 */
class ReaderDisplaySettingsRepositoryTest {

    private val file = File.createTempFile("display_settings_test", ".preferences_pb").apply { delete() }
    private val repo = ReaderDisplaySettingsRepository(
        PreferenceDataStoreFactory.create(produceFile = { file }),
    )

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `首次 observe 返回默认（全跟随系统）`() = runBlocking {
        val d = repo.observe().first()

        assertNull(d.brightness) // null = 跟随系统亮度
        assertFalse(d.keepScreenOn) // false = 不常亮（产品默认）
        assertNull(d.orientation) // null = 跟随系统方向
    }

    @Test
    fun `update 写入亮度并持久化往返`() = runBlocking {
        repo.update { it.copy(brightness = 0.75f) }

        assertEquals(0.75f, repo.observe().first().brightness!!)
    }

    @Test
    fun `update 设 brightness 为 null 移除字段（恢复跟随系统）`() = runBlocking {
        repo.update { it.copy(brightness = 0.5f) }
        repo.update { it.copy(brightness = null) }

        assertNull(repo.observe().first().brightness)
    }

    @Test
    fun `update 开启 keepScreenOn 后持久化为 true`() = runBlocking {
        repo.update { it.copy(keepScreenOn = true) }

        assertTrue(repo.observe().first().keepScreenOn)
    }

    @Test
    fun `update 关闭 keepScreenOn 持久化往返`() = runBlocking {
        repo.update { it.copy(keepScreenOn = true) }
        repo.update { it.copy(keepScreenOn = false) }

        assertFalse(repo.observe().first().keepScreenOn)
    }

    @Test
    fun `update 写入方向并持久化`() = runBlocking {
        repo.update { it.copy(orientation = ReaderOrientation.LANDSCAPE) }

        assertEquals(ReaderOrientation.LANDSCAPE, repo.observe().first().orientation)
    }

    @Test
    fun `update 切换方向持久化往返`() = runBlocking {
        repo.update { it.copy(orientation = ReaderOrientation.PORTRAIT) }
        assertEquals(ReaderOrientation.PORTRAIT, repo.observe().first().orientation)

        repo.update { it.copy(orientation = ReaderOrientation.LANDSCAPE) }
        assertEquals(ReaderOrientation.LANDSCAPE, repo.observe().first().orientation)
    }

    @Test
    fun `update 设 orientation 为 null 移除字段（恢复跟随系统）`() = runBlocking {
        repo.update { it.copy(orientation = ReaderOrientation.PORTRAIT) }
        repo.update { it.copy(orientation = null) }

        assertNull(repo.observe().first().orientation)
    }

    @Test
    fun `update 写入字段不影响其它默认值（不漂移）`() = runBlocking {
        repo.update { it.copy(brightness = 0.6f) }

        val d = repo.observe().first()
        assertEquals(0.6f, d.brightness!!)
        // 写 brightness 不应让 keepScreenOn / orientation 漂移
        assertFalse(d.keepScreenOn)
        assertNull(d.orientation)
    }

    @Test
    fun `update 基于当前值增量修改`() = runBlocking {
        repo.update { it.copy(brightness = 0.5f) }
        repo.update { it.copy(brightness = (it.brightness ?: 0f) + 0.1f) }

        assertEquals(0.6f, repo.observe().first().brightness!!, 1e-5f)
    }
}
