package com.xuziyue.ebook.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * TTS 偏好持久化仓库（READ-10，沿用 [ReaderTypographyRepository] 范式）。
 *
 * 复用全局 DataStore（reader_settings.preferences_pb）：
 * - [speed]：语速倍率（0.5–2.0，默认 1.0）。
 * - [voiceId]：发音人 id（null = 引擎按语言自动选）。
 * - [timerMinutes]：定时停止分钟数（0 = 不定时，默认 0）。
 */
class ReaderTtsPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) {

    data class TtsPrefs(
        val speed: Double = 1.0,
        val voiceId: String? = null,
        val timerMinutes: Int = 0,
    )

    fun observe(): Flow<TtsPrefs> = dataStore.data.map { prefs ->
        TtsPrefs(
            speed = (prefs[KEY_SPEED] ?: 1.0).coerceIn(SPEED_MIN, SPEED_MAX),
            voiceId = prefs[KEY_VOICE_ID],
            timerMinutes = prefs[KEY_TIMER_MINUTES] ?: 0,
        )
    }

    suspend fun setSpeed(speed: Double) {
        dataStore.edit { it[KEY_SPEED] = speed.coerceIn(SPEED_MIN, SPEED_MAX) }
    }

    suspend fun setVoiceId(voiceId: String?) {
        dataStore.edit { prefs ->
            if (voiceId == null) prefs.remove(KEY_VOICE_ID) else prefs[KEY_VOICE_ID] = voiceId
        }
    }

    suspend fun setTimerMinutes(minutes: Int) {
        dataStore.edit { it[KEY_TIMER_MINUTES] = minutes.coerceAtLeast(0) }
    }

    private companion object {
        val KEY_SPEED = doublePreferencesKey("tts_speed")
        val KEY_VOICE_ID = stringPreferencesKey("tts_voice_id")
        val KEY_TIMER_MINUTES = intPreferencesKey("tts_timer_minutes")
        const val SPEED_MIN = 0.5
        const val SPEED_MAX = 2.0
    }
}
