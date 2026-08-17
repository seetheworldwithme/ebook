package com.xuziyue.ebook.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ReaderTtsPreferencesRepository 单测（READ-10，Robolectric 真实 DataStore 文件）。
 */
@RunWith(RobolectricTestRunner::class)
class ReaderTtsPreferencesRepositoryTest {

    private lateinit var dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    private lateinit var repository: ReaderTtsPreferencesRepository
    private var storeFile: File? = null

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(ctx.cacheDir, "tts-prefs-test.preferences_pb").also { it.delete() }
        storeFile = file
        dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        repository = ReaderTtsPreferencesRepository(dataStore)
    }

    @After
    fun tearDown() {
        storeFile?.delete()
    }

    @Test
    fun `默认值 速度1 倍无发音人不定时`() = runTest {
        val prefs = repository.observe().first()
        assertEquals(1.0, prefs.speed, 1e-9)
        assertNull(prefs.voiceId)
        assertEquals(0, prefs.timerMinutes)
    }

    @Test
    fun `setSpeed 持久化并夹取范围`() = runTest {
        repository.setSpeed(1.5)
        assertEquals(1.5, repository.observe().first().speed, 1e-9)
        repository.setSpeed(99.0) // 超上限夹到 2.0
        assertEquals(2.0, repository.observe().first().speed, 1e-9)
        repository.setSpeed(0.1) // 低于下限夹到 0.5
        assertEquals(0.5, repository.observe().first().speed, 1e-9)
    }

    @Test
    fun `setVoiceId 持久化 null 删除`() = runTest {
        repository.setVoiceId("zh-cn-x-ji-local")
        assertEquals("zh-cn-x-ji-local", repository.observe().first().voiceId)
        repository.setVoiceId(null)
        assertNull(repository.observe().first().voiceId)
    }

    @Test
    fun `setTimerMinutes 持久化`() = runTest {
        repository.setTimerMinutes(15)
        assertEquals(15, repository.observe().first().timerMinutes)
        repository.setTimerMinutes(0)
        assertEquals(0, repository.observe().first().timerMinutes)
    }
}
