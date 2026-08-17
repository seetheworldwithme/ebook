package com.xuziyue.ebook.reader.tts

import android.content.Context
import com.xuziyue.ebook.R
import com.xuziyue.ebook.data.ReaderTtsPreferencesRepository
import com.xuziyue.ebook.ui.UserMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.readium.navigator.media.tts.AndroidTtsNavigator
import org.readium.navigator.media.tts.AndroidTtsNavigatorFactory
import org.readium.navigator.media.tts.TtsNavigator
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.navigator.media.tts.android.AndroidTtsPreferences
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.getOrElse

/**
 * TTS 会话管理器（READ-10，页面内播放口径——无前台服务/媒体通知，P2 再评估）。
 *
 * 职责（把 Readium TTS 细节挡在 ReaderViewModel 之外）：
 * 1. **懒创建**：[start] 首次调用时用 [AndroidTtsNavigatorFactory] 建会话（系统 TTS 引擎初始化在
 *    库内异步完成）；publication 无 ContentService 或引擎初始化失败 → [events] 发可理解错误。
 * 2. **状态暴露**：[isPlaying] / [utteranceLocator]（当前朗读句，高亮 + 跟翻用）/ [voices]。
 * 3. **偏好应用**：speed / voiceId / 定时经 [ReaderTtsPreferencesRepository] 持久化，
 *    变更后 submitPreferences 到引擎（AndroidTtsPreferences）。
 * 4. **定时停止**：[TtsTimer] 到期 pause（暂停不断会话，可续播）。
 * 5. **音频焦点**：库内 TtsSessionAdapter.AudioFocusManager 处理（来电/他媒体抢占 → 自动暂停），
 *    本类不重复实现，只在真机回归专项验证。
 *
 * 生命周期：绑 ViewModel scope；[close] 释放 navigator（onCleared 调）。
 * 结束（Ended）/失败状态 → 停表 + 发事件（UI 收起面板或提示）。
 */
@OptIn(ExperimentalReadiumApi::class)
class ReaderTtsManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val publication: Publication,
    private val preferencesRepository: ReaderTtsPreferencesRepository,
) {
    /** 一次性事件（UI collect 后 Toast / 收面板 / 拉起系统语音安装）。 */
    sealed class Event {
        /** 可理解错误（无引擎 / 初始化失败 / 引擎运行错误）。 */
        data class Error(val message: UserMessage) : Event()

        /** 语音数据缺失：UI collect 后调 [requestInstallVoice] 拉起系统下载。 */
        data object MissingVoiceData : Event()

        /** 朗读到书尾。 */
        data object Ended : Event()
    }

    private val _events = MutableStateFlow<Event?>(null)
    val events: StateFlow<Event?> = _events.asStateFlow()

    /** 会话是否就绪（factory+navigator 建成；面板发音人列表等就绪）。 */
    private val _ready = MutableStateFlow(false)

    /** 是否正在朗读（playWhenReady；音频焦点被抢后引擎自动置 false，UI 跟随）。 */
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /** 当前朗读句 Locator（null = 未在播）。高亮（tts Decoration 组）与跟翻共用。 */
    private val _utteranceLocator = MutableStateFlow<Locator?>(null)
    val utteranceLocator: StateFlow<Locator?> = _utteranceLocator.asStateFlow()

    /** 可选发音人（引擎就绪后填充；按语言过滤在 UI 侧做）。 */
    private val _voices = MutableStateFlow<List<AndroidTtsEngine.Voice>>(emptyList())
    val voices: StateFlow<List<AndroidTtsEngine.Voice>> = _voices.asStateFlow()

    /** 定时剩余分钟展示（null = 未定时）；到点自动暂停。 */
    private val _timerMinutes = MutableStateFlow(0)
    val timerMinutes: StateFlow<Int> = _timerMinutes.asStateFlow()

    private var navigator: AndroidTtsNavigator? = null
    private var startJob: Job? = null
    private var timer: TtsTimer? = null

    /**
     * 从 [initialLocator] 起朗读。已就绪时等价 resume。
     * 首次创建：factory（无 ContentService 返回 null → 错误事件）→ createNavigator（suspend）→ play。
     */
    fun start(initialLocator: Locator?) {
        if (startJob != null) return // 创建中防抖
        navigator?.let { nav ->
            nav.play()
            restartTimerIfActive()
            return
        }
        startJob = scope.launch {
            val factory = AndroidTtsNavigatorFactory(context.applicationContext as android.app.Application, publication)
            if (factory == null) {
                _events.value = Event.Error(UserMessage.Res(R.string.tts_error_unsupported))
                startJob = null
                return@launch
            }
            // 首个偏好快照作 initialPreferences（语速/发音人冷启动即生效）。
            val initial = preferencesRepository.observe().first() // DataStore 恒有值（map 默认）

            val result = factory.createNavigator(
                listener = object : TtsNavigator.Listener {
                    override fun onStopRequested() {
                        // 会话请求停止（如引擎错误后系统侧 stop）：等同用户暂停
                        pause()
                    }
                },
                initialLocator = initialLocator,
                initialPreferences = initial.toAndroidPreferences(),
            )
            val created = result.getOrNull()
            if (created == null) {
                _events.value = Event.Error(UserMessage.Res(R.string.tts_error_init))
                startJob = null
                return@launch
            }
            navigator = created
            bindNavigator()
            _ready.value = true
            _voices.value = created.voices
                .filterIsInstance<AndroidTtsEngine.Voice>()
                .sortedBy { it.language.toString() }
            startJob = null
            created.play()
            applyTimer(initial.timerMinutes)
        }
    }

    /** 订阅 navigator 状态流（播放态 / 当前句 / 结束与失败）。 */
    private fun bindNavigator() {
        val nav = navigator ?: return
        scope.launch {
            nav.playback.collect { playback ->
                _isPlaying.value = playback.playWhenReady
                val state = playback.state
                when (state) {
                    TtsNavigator.State.Ended -> {
                        timer?.cancel()
                        _events.value = Event.Ended
                    }
                    is TtsNavigator.State.Failure -> handleFailure(state.error)
                    else -> Unit
                }
            }
        }
        scope.launch {
            nav.location.collect { location ->
                _utteranceLocator.value = location.utteranceLocator
            }
        }
    }

    /** 引擎/内容失败分类：缺语音数据拉安装，其余给可理解文案。 */
    private fun handleFailure(error: TtsNavigator.Error) {
        timer?.cancel()
        _isPlaying.value = false
        val engineError = (error as? TtsNavigator.Error.EngineError<*>)?.cause
        _events.value = when (engineError) {
            is AndroidTtsEngine.Error.LanguageMissingData -> Event.MissingVoiceData
            is AndroidTtsEngine.Error.Network -> Event.Error(UserMessage.Res(R.string.tts_error_network))
            else -> Event.Error(UserMessage.Res(R.string.tts_error_engine))
        }
    }

    fun pause() {
        timer?.cancel()
        navigator?.pause()
    }

    fun resume() {
        navigator?.play()
        restartTimerIfActive()
    }

    /** 跳上一句 / 下一句。 */
    fun previousUtterance() {
        navigator?.skipToPreviousUtterance()
    }

    fun nextUtterance() {
        navigator?.skipToNextUtterance()
    }

    /** 语速（持久化 + 引擎实时生效）。 */
    fun setSpeed(speed: Double) {
        scope.launch {
            preferencesRepository.setSpeed(speed)
            submitCurrentPreferences()
        }
    }

    /** 发音人（持久化 + 引擎实时生效；null = 自动选）。 */
    fun setVoice(voiceId: String?) {
        scope.launch {
            preferencesRepository.setVoiceId(voiceId)
            submitCurrentPreferences()
        }
    }

    /** 定时停止（分钟；0 = 不定时）。播放中立即起表。 */
    fun setTimer(minutes: Int) {
        scope.launch { preferencesRepository.setTimerMinutes(minutes) }
        _timerMinutes.value = minutes
        applyTimer(minutes)
    }

    private fun applyTimer(minutes: Int) {
        if (timer == null) timer = TtsTimer(scope) { pause() }
        if (minutes > 0 && _isPlaying.value) {
            timer?.start(minutes)
        } else if (minutes <= 0) {
            timer?.cancel()
        }
    }

    /** 暂停后续播时恢复计时（剩余时长简化为重启整段——分钟级粒度足够）。 */
    private fun restartTimerIfActive() {
        val minutes = _timerMinutes.value
        if (minutes > 0) timer?.start(minutes)
    }

    /** 组装当前偏好 → submitPreferences（voiceId 映射到当前书语言的 voices map）。 */
    private suspend fun submitCurrentPreferences() {
        val nav = navigator ?: return
        val prefs = preferencesRepository.observe().first()
        nav.submitPreferences(prefs.toAndroidPreferences())
    }

    /** 拉起系统语音数据下载（MissingVoiceData 事件后 UI 调）。 */
    fun requestInstallVoice() {
        AndroidTtsEngine.Companion.requestInstallVoice(context)
    }

    /** 释放会话（VM onCleared 调；TtsPlayer + MediaSession 适配器一并释放）。 */
    fun close() {
        timer?.cancel()
        startJob?.cancel()
        navigator?.close()
        navigator = null
        _ready.value = false
    }

    /** 本地偏好 → Readium AndroidTtsPreferences（voiceId 挂到书语言键上）。 */
    private fun ReaderTtsPreferencesRepository.TtsPrefs.toAndroidPreferences(): AndroidTtsPreferences {
        val language = publication.metadata.language
        val voices = voiceId?.takeIf { language != null }
            ?.let { mapOf(language!! to AndroidTtsEngine.Voice.Id(it)) }
            ?: emptyMap()
        return AndroidTtsPreferences(
            language = null, // null：跟随书内声明语言（含 lang 属性逐元素语言）
            pitch = null,
            speed = speed,
            voices = voices,
        )
    }
}
