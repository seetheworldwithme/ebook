package com.xuziyue.ebook.reader

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.robolectric.RobolectricTestRunner

/**
 * JumpHistory 双栈单测（READ-09：历史位置前进/后退）。
 *
 * 覆盖口径（对照设计文档 4.3 READ-09 验收「目录、搜索结果、脚注跳转均可返回」）：
 * - recordJump：push 后备栈 + 清空前进栈（新跳转截断 redo 分支，浏览器同款语义）。
 * - back：返回上一位置，弹出者进前进栈；无历史返回 null。
 * - forward：重做前进栈顶；无前进返回 null。
 * - 容量封顶：超容量丢最旧。
 * - null current（书刚打开尚无 Locator）：不 push，back 返回 null。
 */
@RunWith(RobolectricTestRunner::class)
class JumpHistoryTest {

    private fun locator(href: String, progression: Double): Locator = Locator.fromJSON(
        JSONObject().apply {
            put("href", href)
            put("type", "application/xhtml+xml")
            put("locations", JSONObject().apply { put("totalProgression", progression) })
        },
    ) ?: error("测试 Locator 构造失败")

    @Test
    fun `recordJump 后 canGoBack 为真 前进栈被清空`() {
        val history = JumpHistory()
        assertFalse(history.canGoBack)
        assertFalse(history.canGoForward)

        val a = locator("ch1", 0.1)
        val b = locator("ch2", 0.2)
        history.recordJump(current = a)
        history.back(current = b) // a 进前进栈

        assertFalse(history.canGoBack) // 后备栈已空
        assertTrue(history.canGoForward)

        history.recordJump(current = b) // 新跳转：清前进栈
        assertTrue(history.canGoBack)
        assertFalse(history.canGoForward)
    }

    @Test
    fun `back 返回上一位置 交替往返可重做`() {
        val history = JumpHistory()
        val a = locator("ch1", 0.1)
        val b = locator("ch2", 0.2)
        val c = locator("ch3", 0.3)
        val d = locator("ch4", 0.4)

        // 真实流：在 a/b/c 各跳走一次，最终落在 d。
        history.recordJump(a) // 在 a 处跳走 → 后备栈 [a]
        history.recordJump(b) // 在 b 处跳走 → 后备栈 [a, b]
        history.recordJump(c) // 在 c 处跳走 → 后备栈 [a, b, c]

        assertEquals(c, history.back(current = d)) // d → c
        assertEquals(b, history.back(current = c)) // c → b
        assertEquals(a, history.back(current = b)) // b → a，退到底
        assertFalse(history.canGoBack)

        // 前进：从 a 依次重做回 d（每步 current 回压后备栈）
        assertEquals(b, history.forward(current = a))
        assertEquals(c, history.forward(current = b))
        assertEquals(d, history.forward(current = c))
        assertFalse(history.canGoForward)
    }

    @Test
    fun `空栈 back 与 forward 返回 null 且 canGo 均假`() {
        val history = JumpHistory()
        val current = locator("ch1", 0.1)
        assertNull(history.back(current))
        assertNull(history.forward(current))
        assertFalse(history.canGoBack)
        assertFalse(history.canGoForward)
    }

    @Test
    fun `recordJump 的 current 为 null 时不入栈`() {
        val history = JumpHistory()
        history.recordJump(current = null)
        assertFalse(history.canGoBack)
        assertNull(history.back(current = locator("ch1", 0.1)))
    }

    @Test
    fun `back 的 current 为 null 时不入前进栈但仍返回跳转目标`() {
        val history = JumpHistory()
        val a = locator("ch1", 0.1)
        history.recordJump(a)
        val target = history.back(current = null)
        assertEquals(a, target)
        assertFalse(history.canGoForward) // 无 current 可记，前进链断
    }

    @Test
    fun `容量封顶丢最旧`() {
        val history = JumpHistory()
        val first = locator("ch-first", 0.01)
        history.recordJump(first)
        repeat(JumpHistory.MAX_DEPTH) { history.recordJump(locator("ch-$it", it * 0.01)) }

        // 退 MAX_DEPTH 次后，最早的 first 已被挤出，继续 back 应返回 null。
        var target: Locator? = null
        repeat(JumpHistory.MAX_DEPTH + 1) {
            target = history.back(current = locator("now", 0.99))
        }
        assertNull(target)
    }

    @Test
    fun `clear 清双栈`() {
        val history = JumpHistory()
        history.recordJump(locator("ch1", 0.1))
        history.back(current = locator("ch2", 0.2))
        history.clear()
        assertFalse(history.canGoBack)
        assertFalse(history.canGoForward)
        assertNull(history.back(current = locator("ch1", 0.1)))
        assertNull(history.forward(current = locator("ch2", 0.2)))
    }
}
