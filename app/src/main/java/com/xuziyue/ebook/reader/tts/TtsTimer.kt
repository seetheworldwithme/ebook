package com.xuziyue.ebook.reader.tts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TTS 定时停止（READ-10「定时停止」）。
 *
 * 独立纯类（不进 ReaderTtsManager 构造）：协程倒计时，到期回调 [onExpired]（由 Manager pause）。
 * - [start] 重设时长（重复调用重启计时）；[cancel] 停表（暂停 / 手动停 / 换时长时调）。
 * - 时长 0 = 不定时（[start] 直接 no-op 等价关）。
 * - scope 由调用方注入（Manager 的 viewModelScope），可测性好（虚拟时间）。
 *
 * 定时选项见 [MINUTES_OPTIONS]（UI chips 与持久化共用此表）。
 */
class TtsTimer(
    private val scope: CoroutineScope,
    private val onExpired: () -> Unit,
) {

    /** 当前剩余毫秒（UI 显示倒计时用）；未计时时为 null。 */
    val remainingMillis: Long?
        get() = job?.let { deadlineMillis - System.currentTimeMillis() }?.coerceAtLeast(0)

    private var job: Job? = null
    private var deadlineMillis: Long = 0

    /** 启动 / 重启 [minutes] 分钟倒计时；0 或负值视为不定时（取消现有计时）。 */
    fun start(minutes: Int) {
        cancel()
        if (minutes <= 0) return
        deadlineMillis = System.currentTimeMillis() + minutes * 60_000L
        job = scope.launch {
            delay(minutes * 60_000L)
            job = null
            onExpired()
        }
    }

    /** 停表（不触发回调）。暂停、手动停止、切换时长、close 时调。 */
    fun cancel() {
        job?.cancel()
        job = null
    }

    companion object {
        /** 定时选项（分钟）：0 = 不定时。UI chips 与默认值共用。 */
        val MINUTES_OPTIONS = listOf(0, 5, 15, 30)
    }
}
