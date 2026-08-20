package com.xuziyue.ebook.data

import java.io.File
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * EPUB ZIP 安全校验器（设计 §7 安全要求，CLAUDE.md 红线 #4）。
 *
 * EPUB 是 ZIP 压缩容器，属于不可信输入。Readium 内部已有 Zip Slip / 损坏文件防护，
 * 本类提供 app 层 defense-in-depth：在 Readium 打开前预检 ZIP 结构，
 * 拦截恶意压缩包（路径遍历 / 压缩炸弹 / 超限条目）。
 *
 * 纯 [java.util.zip]，零 Android / Readium 依赖，可 JVM 单测。
 */
class EpubSecurityValidator(
    private val config: EpubSecurityConfig = EpubSecurityConfig.DEFAULT,
) {

    /**
     * 校验已复制到私有目录的 EPUB 文件。
     *
     * 打开 ZIP 中央目录遍历 entries，检查：
     * 1. Zip Slip（路径遍历 / 绝对路径）
     * 2. 条目数超限
     * 3. 单条目解压大小（流式实测字节，不信声明值）
     * 4. 解压总大小（实测累计）
     * 5. 压缩比（实测总量 / 压缩文件大小）
     *
     * @return [EpubSecurityResult.Safe] 通过；[EpubSecurityResult.Unsafe] 拒绝并附原因。
     */
    fun validate(file: File): EpubSecurityResult {
        if (!file.exists()) {
            return EpubSecurityResult.Unsafe(EpubSecurityError.CorruptArchive("文件不存在"))
        }

        val compressedSize = file.length()
        if (compressedSize <= 0) {
            return EpubSecurityResult.Unsafe(EpubSecurityError.CorruptArchive("空文件"))
        }

        // 打开 ZIP 中央目录；非法 / 损坏 ZIP 抛 ZipException
        val zipFile = try {
            ZipFile(file)
        } catch (e: ZipException) {
            return EpubSecurityResult.Unsafe(EpubSecurityError.CorruptArchive("非法 ZIP 结构：${e.message}"))
        } catch (e: Exception) {
            return EpubSecurityResult.Unsafe(EpubSecurityError.CorruptArchive("无法读取：${e.message}"))
        }

        // 实测解压总字节数（见下方循环说明：声明值可伪造，一律按实际读取计数）。
        var totalUncompressed = 0L

        zipFile.use { zf ->
            val entries = zf.entries()
            var entryCount = 0

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                entryCount++

                // 1. Zip Slip 路径遍历
                if (isZipSlip(entry.name)) {
                    return EpubSecurityResult.Unsafe(EpubSecurityError.ZipSlip(entry.name))
                }

                // 2. 条目数超限
                if (entryCount > config.maxEntryCount) {
                    return EpubSecurityResult.Unsafe(
                        EpubSecurityError.TooManyEntries(entryCount, config.maxEntryCount),
                    )
                }

                if (!entry.isDirectory) {
                    // 3/4. 实测解压字节（修复审查严重问题 #9）：
                    // 中央目录的 entry.size 是攻击者可伪造的声明值（Java ZipFile 解压不强制
                    // 实际字节等于声明），且 size<=0（含 -1）的条目原逻辑被完全跳过——
                    // 恶意 EPUB 声明小尺寸实际解出超大内容可穿透。这里流式读 entry 实测计数，
                    // 单条目与累计总量都按实际字节判。
                    var entryActual = 0L
                    zf.getInputStream(entry).use { stream ->
                        val buf = ByteArray(32 * 1024)
                        while (true) {
                            val n = stream.read(buf)
                            if (n <= 0) break
                            entryActual += n
                            if (entryActual > config.maxSingleEntrySize) {
                                return EpubSecurityResult.Unsafe(
                                    EpubSecurityError.EntryTooLarge(entry.name, entryActual, config.maxSingleEntrySize),
                                )
                            }
                        }
                    }
                    totalUncompressed += entryActual
                    if (totalUncompressed > config.maxTotalUncompressedSize) {
                        return EpubSecurityResult.Unsafe(
                            EpubSecurityError.TotalSizeExceeded(totalUncompressed, config.maxTotalUncompressedSize),
                        )
                    }
                }
            }
        }

        // 5. 压缩比异常（基于实测解压字节数计算；声明值可伪造，实测才可信）
        if (totalUncompressed > 0) {
            val ratio = totalUncompressed.toDouble() / compressedSize.toDouble()
            if (ratio > config.maxCompressionRatio) {
                return EpubSecurityResult.Unsafe(
                    EpubSecurityError.CompressionBomb(ratio, config.maxCompressionRatio),
                )
            }
        }

        return EpubSecurityResult.Safe
    }

    companion object {
        /** 导入文件大小上限（200MB，与 OpenBookUseCase.MAX_EPUB_SIZE 对齐）。 */
        const val MAX_IMPORT_SIZE: Long = 200L * 1024 * 1024

        /**
         * 复制前预检：文件大小 + 磁盘空间。
         *
         * @param sourceSize 源文件大小（字节）；-1 表示未知（跳过大小预检）。
         * @param availableSpace 可用磁盘空间（字节）。
         * @return [EpubSecurityResult.Safe] 可继续；[EpubSecurityResult.Unsafe] 应中止导入。
         */
        fun checkImportPreconditions(
            sourceSize: Long,
            availableSpace: Long,
        ): EpubSecurityResult {
            if (sourceSize > MAX_IMPORT_SIZE) {
                return EpubSecurityResult.Unsafe(
                    EpubSecurityError.FileTooLarge(sourceSize, MAX_IMPORT_SIZE),
                )
            }
            if (sourceSize > 0 && sourceSize > availableSpace) {
                return EpubSecurityResult.Unsafe(
                    EpubSecurityError.InsufficientSpace(sourceSize, availableSpace),
                )
            }
            return EpubSecurityResult.Safe
        }

        /**
         * 检测 Zip Slip 路径遍历（设计 §7：规范化每个解压路径并验证仍位于目标目录内）。
         *
         * 检查项：
         * - 绝对路径（以 `/` 开头）
         * - `../` 路径遍历（深度降到 0 以下）
         * - 反斜杠归一化后同上（Windows 风格 `..\`）
         */
        internal fun isZipSlip(entryName: String): Boolean {
            val normalized = entryName.replace('\\', '/')
            if (normalized.startsWith("/")) return true
            var depth = 0
            for (part in normalized.split("/")) {
                when {
                    part == ".." -> {
                        depth--
                        if (depth < 0) return true
                    }
                    part == "." || part.isEmpty() -> { }
                    else -> depth++
                }
            }
            return depth < 0
        }
    }
}

/** EPUB 安全校验配置（阈值）。 */
data class EpubSecurityConfig(
    /** 最大条目数（合法 EPUB 最多数百条目）。 */
    val maxEntryCount: Int = 10_000,
    /** 解压后总大小上限（压缩炸弹防御；按实测字节计数）。 */
    val maxTotalUncompressedSize: Long = 500L * 1024 * 1024,
    /** 单条目解压大小上限（按实测字节计数）。 */
    val maxSingleEntrySize: Long = 100L * 1024 * 1024,
    /** 压缩比上限（经典 zip bomb ≫ 100:1；基于实测字节计算）。 */
    val maxCompressionRatio: Double = 103.0,
) {
    companion object {
        val DEFAULT = EpubSecurityConfig()
    }
}

/** EPUB 安全校验结果。 */
sealed interface EpubSecurityResult {
    /** 通过安全校验。 */
    data object Safe : EpubSecurityResult

    /** 未通过安全校验，附 [error]。 */
    data class Unsafe(val error: EpubSecurityError) : EpubSecurityResult
}

/**
 * EPUB 安全校验错误类型（对齐 CLAUDE.md：建立明确错误类型 + 可理解提示）。
 *
 * [message] 供日志使用；UI 层通过扩展映射到本地化资源。
 */
sealed class EpubSecurityError {
    abstract val message: String

    /** 源文件超过导入大小上限。 */
    class FileTooLarge(val actual: Long, val limit: Long) : EpubSecurityError() {
        override val message: String =
            "文件过大（${actual / 1024 / 1024}MB），上限 ${limit / 1024 / 1024}MB"
    }

    /** Zip Slip 路径遍历。 */
    class ZipSlip(val path: String) : EpubSecurityError() {
        override val message: String = "检测到路径遍历：$path"
    }

    /** 解压后总大小超限。 */
    class TotalSizeExceeded(val actual: Long, val limit: Long) : EpubSecurityError() {
        override val message: String =
            "解压后内容过大（${actual / 1024 / 1024}MB），上限 ${limit / 1024 / 1024}MB"
    }

    /** 单条目解压大小超限。 */
    class EntryTooLarge(val entryName: String, val actual: Long, val limit: Long) : EpubSecurityError() {
        override val message: String =
            "条目「$entryName」过大（${actual / 1024 / 1024}MB），上限 ${limit / 1024 / 1024}MB"
    }

    /** 条目数超限。 */
    class TooManyEntries(val actual: Int, val limit: Int) : EpubSecurityError() {
        override val message: String = "条目数过多（$actual），上限 $limit"
    }

    /** 压缩比异常（疑似压缩炸弹；ratio 基于实测解压字节）。 */
    class CompressionBomb(val ratio: Double, val limit: Double) : EpubSecurityError() {
        override val message: String =
            "压缩比异常（${"%.1f".format(ratio)}:1，上限 ${"%.1f".format(limit)}:1）"
    }

    /** 非法 / 损坏 ZIP 结构。 */
    class CorruptArchive(val detail: String) : EpubSecurityError() {
        override val message: String = "文件损坏：$detail"
    }

    /** 磁盘空间不足。 */
    class InsufficientSpace(val required: Long, val available: Long) : EpubSecurityError() {
        override val message: String =
            "空间不足（需要 ${required / 1024 / 1024}MB，可用 ${available / 1024 / 1024}MB）"
    }
}

/** 导入安全异常（包装 [EpubSecurityError]，走 runCatching 通道）。 */
class ImportSafetyException(val error: EpubSecurityError) : Exception(error.message)
