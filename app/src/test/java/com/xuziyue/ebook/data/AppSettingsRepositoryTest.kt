package com.xuziyue.ebook.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [AppSettingsRepository] 目录导入相关 key 单测（IMP-06）：
 * import_tree_uri 往返 / null 移除 + import_auto_scan 默认与往返。
 *
 * 纯 JVM 临时文件 DataStore（与 ReaderDisplaySettingsRepositoryTest 同范式）。
 */
class AppSettingsRepositoryTest {

    private val file = File.createTempFile("app_settings_test", ".preferences_pb").apply { delete() }
    private val repo = AppSettingsRepository(
        PreferenceDataStoreFactory.create(produceFile = { file }),
    )

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `import_tree_uri 默认 null（未授权）`() = runBlocking {
        assertNull(repo.importTreeUri.first())
    }

    @Test
    fun `import_tree_uri 写入并往返`() = runBlocking {
        repo.setImportTreeUri("content://tree/primary:Books")
        assertEquals("content://tree/primary:Books", repo.importTreeUri.first())
    }

    @Test
    fun `import_tree_uri 设 null 移除字段（解除授权）`() = runBlocking {
        repo.setImportTreeUri("content://tree/primary:Books")
        repo.setImportTreeUri(null)
        assertNull(repo.importTreeUri.first())
    }

    @Test
    fun `import_auto_scan 默认 true`() = runBlocking {
        assertEquals(true, repo.importAutoScan.first())
    }

    @Test
    fun `import_auto_scan 关闭后往返`() = runBlocking {
        repo.setImportAutoScan(false)
        assertEquals(false, repo.importAutoScan.first())
        repo.setImportAutoScan(true)
        assertEquals(true, repo.importAutoScan.first())
    }
}
