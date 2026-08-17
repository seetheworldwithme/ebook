package com.xuziyue.ebook.reader.tts

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TtsTimer 定时停止单测（READ-10，虚拟时间驱动——真实 5 分钟等不了）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TtsTimerTest {

    @Test
    fun `到期触发回调`() = runTest {
        var fired = false
        val timer = TtsTimer(scope = backgroundScope) { fired = true }
        timer.start(5) // 5 分钟
        advanceTimeBy(5 * 60_000L - 1)
        runCurrent()
        assertFalse("差 1ms 不触发", fired)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(fired)
    }

    @Test
    fun `cancel 后不再触发`() = runTest {
        var fired = false
        val timer = TtsTimer(scope = backgroundScope) { fired = true }
        timer.start(5)
        advanceTimeBy(60_000L)
        timer.cancel()
        advanceTimeBy(10 * 60_000L)
        runCurrent()
        assertFalse(fired)
    }

    @Test
    fun `重复 start 重启计时`() = runTest {
        var fired = false
        val timer = TtsTimer(scope = backgroundScope) { fired = true }
        timer.start(5)
        advanceTimeBy(4 * 60_000L) // 快到期
        runCurrent()
        assertFalse(fired)
        timer.start(5) // 重设 5 分钟
        advanceTimeBy(4 * 60_000L)
        runCurrent()
        assertFalse("重设后旧计时作废", fired)
        advanceTimeBy(60_000L)
        runCurrent()
        assertTrue(fired)
    }

    @Test
    fun `0 分钟等同不定时`() = runTest {
        var fired = false
        val timer = TtsTimer(scope = backgroundScope) { fired = true }
        timer.start(0)
        advanceTimeBy(60 * 60_000L)
        runCurrent()
        assertFalse(fired)
        assertNull(timer.remainingMillis)
    }
}
