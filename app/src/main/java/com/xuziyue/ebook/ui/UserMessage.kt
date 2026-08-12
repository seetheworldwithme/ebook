package com.xuziyue.ebook.ui

import android.content.Context
import androidx.annotation.StringRes

/**
 * 可本地化的用户消息（SET-01 i18n）。
 *
 * 业务层（UseCase / ViewModel / 纯函数如 [relativeTime]）不持有 [Context]，
 * 通过本类型产出「可资源化」的消息；UI 层（Compose / Fragment）用 [resolve] 解析为字符串。
 *
 * - [Res]：引用字符串资源（`R.string.xxx`），可带格式参数（如 `listOf(count)`），随系统语言变化。
 * - [Raw]：真正不可译的动态内容（如异常 message、日期串），原样展示。
 *
 * 这样既满足「所有用户可见文案进入资源文件」，又不让 ViewModel / UseCase 依赖 Android Context，
 * 保持可单测（断言 [Res.resId] 而非脆性字面文案）。
 */
sealed interface UserMessage {

    /** 引用字符串资源；[args] 对应格式串里的 %1$s / %1$d 等占位符。 */
    data class Res(@StringRes val resId: Int, val args: List<Any> = emptyList()) : UserMessage

    /** 不可译的原始文本（动态内容兜底）。 */
    data class Raw(val text: String) : UserMessage
}

/** 把 [UserMessage] 解析为当前语言下的字符串。 */
fun UserMessage.resolve(context: Context): String = when (this) {
    is UserMessage.Res ->
        if (args.isEmpty()) context.getString(resId)
        else context.getString(resId, *args.toTypedArray())
    is UserMessage.Raw -> text
}
