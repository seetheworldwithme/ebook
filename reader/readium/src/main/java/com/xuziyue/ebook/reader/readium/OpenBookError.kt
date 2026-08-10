package com.xuziyue.ebook.reader.readium

import com.xuziyue.ebook.reader.readium.txt.TxtEncodingResult
import com.xuziyue.ebook.reader.readium.txt.TxtParseError
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.streamer.PublicationOpener

/**
 * 打开 EPUB / TXT 的错误类型（对齐 CLAUDE.md：建立明确错误类型 + 给用户可理解的提示）。
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

    // ===== TXT 专用错误（P0V-04） =====

    /** TXT 编码无法自动判定，需用户手选（P0 暂以可理解错误返回，编码手选 UI 推后 P1）。 */
    class EncodingChoiceNeeded(
        val candidates: List<TxtEncodingResult.EncodingCandidate>,
    ) : OpenBookError() {
        override val message: String =
            "无法自动识别文本编码，请稍后手动选择（编码手选功能开发中）"
    }

    /** TXT 解析失败（包装 [TxtParseError]：文件问题 / 大小超限 / 空文件）。 */
    class TxtFailed(val cause: TxtParseError) : OpenBookError() {
        override val message: String = cause.message
    }

    /** TXT→EPUB 转换或缓存写入失败。 */
    class TxtConvertFailed(val cause: Throwable) : OpenBookError() {
        override val message: String = "TXT 转换失败：${cause.message}"
    }
}
