package com.xuziyue.ebook.reader.readium

import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.streamer.PublicationOpener

/**
 * 打开 EPUB 的错误类型（对齐 CLAUDE.md：建立明确错误类型 + 给用户可理解的提示）。
 *
 * 作为 [org.readium.r2.shared.util.Try] 的错误泛型使用，是值而非异常。
 */
sealed class OpenBookError {

    /** 用户可读的提示文案。 */
    abstract val message: String

    /** 文件不存在（路径无效 / 原文件被删除）。 */
    class FileNotFound(val path: String) : OpenBookError() {
        override val message: String = "文件不存在：$path"
    }

    /** 超过单文件大小上限（压缩炸弹防御，CLAUDE.md 红线 #4）。 */
    class FileTooLarge(val actual: Long, val limit: Long) : OpenBookError() {
        override val message: String =
            "文件过大（${actual / 1024 / 1024}MB），上限 ${limit / 1024 / 1024}MB"
    }

    /** AssetRetriever 嗅探 / 创建 Asset 失败（格式损坏 / 不支持）。 */
    class RetrieveFailed(val cause: AssetRetriever.RetrieveError) : OpenBookError() {
        override val message: String = "无法识别文件：$cause"
    }

    /** PublicationOpener 解析失败（spine 损坏 / 格式不支持）。 */
    class OpenFailed(val cause: PublicationOpener.OpenError) : OpenBookError() {
        override val message: String = "打开失败：$cause"
    }

    /** 受 DRM 保护且未解锁。 */
    data object Restricted : OpenBookError() {
        override val message: String = "该文件受保护，无法打开"
    }
}
