package com.xuziyue.ebook.reader

import org.readium.r2.shared.publication.Locator

/**
 * 跳转历史双栈（READ-09：历史位置前进 / 后退）。
 *
 * Readium Navigator 无 history（goForward/goBackward 是翻页），READ-02 时曾用单向栈只能后退；
 * 本类升级为浏览器同款 back/forward 双栈语义：
 *
 * - [recordJump]：用户显式跳转（目录 / 进度 / 搜索 / 书签 / 批注 / 内链确认）发生前调用，
 *   把当前阅读位置压入后备栈，并**清空前进栈**——新跳转截断 redo 分支（与浏览器一致）。
 * - [back]：弹出后备栈顶作为返回目标；[current]（此刻位置）压入前进栈供 [forward] 重做。
 * - [forward]：弹出前进栈顶作为前进目标；[current] 压回后备栈。
 *
 * current 为 null（书刚打开尚无 Locator）时对应操作静默降级：不入栈，避免空位污染历史。
 * 脚注弹层、TTS 跟翻是「渲染跟随」不是用户跳转，不经本类（不污染历史，口径与 READ-10 一致）。
 */
internal class JumpHistory {

    private val backStack = ArrayDeque<Locator>()
    private val forwardStack = ArrayDeque<Locator>()

    val canGoBack: Boolean get() = backStack.isNotEmpty()
    val canGoForward: Boolean get() = forwardStack.isNotEmpty()

    /**
     * 记录一次显式跳转：[current]（跳转前的位置）压后备栈 + 清前进栈。
     */
    fun recordJump(current: Locator?) {
        if (current == null) return
        backStack.addLast(current)
        while (backStack.size > MAX_DEPTH) backStack.removeFirst()
        forwardStack.clear()
    }

    /**
     * 返回上一阅读位置；无历史返回 null。[current] 进前进栈。
     */
    fun back(current: Locator?): Locator? {
        val target = backStack.removeLastOrNull() ?: return null
        if (current != null) {
            forwardStack.addLast(current)
            while (forwardStack.size > MAX_DEPTH) forwardStack.removeFirst()
        }
        return target
    }

    /**
     * 重做一次前进（[back] 的逆操作）；无前进返回 null。[current] 回后备栈。
     */
    fun forward(current: Locator?): Locator? {
        val target = forwardStack.removeLastOrNull() ?: return null
        if (current != null) {
            backStack.addLast(current)
            while (backStack.size > MAX_DEPTH) backStack.removeFirst()
        }
        return target
    }

    /** 切书 / 重开时清空双栈。 */
    fun clear() {
        backStack.clear()
        forwardStack.clear()
    }

    companion object {
        /** 单栈最大深度（防内存膨胀；与 READ-02 单向栈口径一致）。 */
        const val MAX_DEPTH = 20
    }
}
