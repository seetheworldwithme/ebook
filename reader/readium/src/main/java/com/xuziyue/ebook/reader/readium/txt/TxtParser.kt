package com.xuziyue.ebook.reader.readium.txt

import java.io.File
import java.io.IOException
import java.nio.charset.Charset

/**
 * TXT 解析门面：组合「编码探测 + 解码 + 章节切分」。
 *
 * 纯解析核心层，零 Android / Readium / Compose 依赖（红线 #4：解析不在主线程，
 * 由调用方 `withContext(Dispatchers.IO)` 保证）。本层无临时文件、无 DB 写入，
 * 失败不产生半成品。
 *
 * @param config 解析配置。
 * @param detector 编码探测器（可注入便于单测）。
 * @param splitter 章节切分器（可注入便于单测）。
 */
class TxtParser(
    private val config: TxtParserConfig = TxtParserConfig.DEFAULT,
    private val detector: TxtEncodingDetector = TxtEncodingDetector,
    private val splitter: TxtChapterSplitter = TxtChapterSplitter(),
) {

    /** 读取并校验文件的内部结果。 */
    private sealed class ReadResult {
        data class Ok(val bytes: ByteArray) : ReadResult()
        data class Err(val error: TxtParseError) : ReadResult()
    }

    /**
     * 解析文件：探测编码 + 切分章节。
     *
     * 编码无法判定时返回 [TxtParseOutcome.NeedsEncodingChoice]，
     * 由调用方弹手选后用 [parseWithEncoding] 重试（红线 #5）。
     */
    fun parse(file: File): TxtParseOutcome {
        return when (val r = readValidated(file)) {
            is ReadResult.Err -> TxtParseOutcome.Failure(r.error)
            is ReadResult.Ok -> {
                when (val d = detector.detect(r.bytes)) {
                    is TxtEncodingResult.Detected -> {
                        val text = try {
                            detector.decode(r.bytes, d.charset)
                        } catch (e: Throwable) {
                            // 理论上 decode 不抛（坏字节走 REPLACE），兜底防异常（红线 #4）
                            return TxtParseOutcome.Failure(TxtParseError.ReadFailed(e))
                        }
                        val chapters = splitter.split(text)
                        TxtParseOutcome.Success(
                            TxtBook(d.charset, d.hadBom, chapters, r.bytes.size.toLong()),
                        )
                    }
                    is TxtEncodingResult.NeedsUserChoice ->
                        TxtParseOutcome.NeedsEncodingChoice(d.candidates)
                }
            }
        }
    }

    /**
     * 用用户手选编码强制解析（[parse] 返回 [TxtParseOutcome.NeedsEncodingChoice] 后的回传路径）。
     *
     * 坏字节走 REPLACE（用户已明确选择，给 best-effort 解码）；结构错误（文件被删等）抛
     * [IllegalStateException]——正常流程下 [parse] 已先行校验，不应到达。
     */
    fun parseWithEncoding(file: File, charset: Charset): TxtBook {
        val bytes = (readValidated(file) as? ReadResult.Ok)?.bytes
            ?: error("parseWithEncoding 前置校验失败（应先用 parse() 通过校验）")
        val text = detector.decode(bytes, charset)
        val chapters = splitter.split(text)
        return TxtBook(charset, detector.hasBom(bytes, charset), chapters, bytes.size.toLong())
    }

    /** 读取文件字节并做大小 / 空文件校验。 */
    private fun readValidated(file: File): ReadResult {
        if (!file.exists()) {
            return ReadResult.Err(TxtParseError.FileNotFound(file.absolutePath))
        }
        if (file.length() > config.maxFileSizeBytes) {
            return ReadResult.Err(TxtParseError.FileTooLarge(file.length(), config.maxFileSizeBytes))
        }
        val bytes = try {
            file.readBytes()
        } catch (e: IOException) {
            return ReadResult.Err(TxtParseError.ReadFailed(e))
        }
        if (bytes.isEmpty()) {
            return ReadResult.Err(TxtParseError.EmptyFile)
        }
        return ReadResult.Ok(bytes)
    }
}

/**
 * TXT 解析配置。
 *
 * @param maxFileSizeBytes 单文件大小硬上限（红线 #4，默认 50MB）。
 *   章节切分需全文（非流式），无法对超大文件边读边切；10MB 实测可接受（《万相之王》
 *   10.25MB），50MB 留余量。超限直接报 [TxtParseError.FileTooLarge]，优于 OOM。
 */
data class TxtParserConfig(
    val maxFileSizeBytes: Long = 50L * 1024 * 1024,
) {
    companion object {
        val DEFAULT = TxtParserConfig()
    }
}

/** [TxtParser.parse] 的三态结果（成功 / 需手选编码 / 失败）。 */
sealed class TxtParseOutcome {
    /** 解析成功。 */
    data class Success(val book: TxtBook) : TxtParseOutcome()

    /** 编码无法自动判定，需用户手选（红线 #5）。 */
    data class NeedsEncodingChoice(
        val candidates: List<TxtEncodingResult.EncodingCandidate>,
    ) : TxtParseOutcome()

    /** 解析失败（文件问题）。 */
    data class Failure(val error: TxtParseError) : TxtParseOutcome()
}

/**
 * TXT 解析错误类型（对齐 CLAUDE.md：建立明确错误类型 + 可理解提示，值而非异常）。
 */
sealed class TxtParseError {
    abstract val message: String

    /** 文件不存在（路径无效 / 原文件被删除）。 */
    class FileNotFound(val path: String) : TxtParseError() {
        override val message: String = "文件不存在：$path"
    }

    /** 超过大小上限（红线 #4：防内存爆炸）。 */
    class FileTooLarge(val actual: Long, val limit: Long) : TxtParseError() {
        override val message: String =
            "文件过大（${actual / 1024 / 1024}MB），上限 ${limit / 1024 / 1024}MB"
    }

    /** 空文件（0 字节，无法解析）。 */
    data object EmptyFile : TxtParseError() {
        override val message: String = "文件为空"
    }

    /** 读取 / 解码异常（兜底防异常，红线 #4）。 */
    class ReadFailed(val cause: Throwable) : TxtParseError() {
        override val message: String = "读取失败：${cause.message}"
    }
}
