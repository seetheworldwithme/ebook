package com.xuziyue.ebook.data.scan

/**
 * 目录扫描域模型（IMP-06）。
 */

/** 枚举到的单个源文档（一个 epub/txt 文件）。 */
data class ScannedDocument(
    /** 源文件 Uri（document Uri，含 tree 授权），同时是 import_sources.sourceUri 的增量判定键。 */
    val uri: String,
    /** 显示名（含扩展名，用于判定支持格式）。 */
    val displayName: String,
    /** 文件大小（字节）；未知为 -1。 */
    val size: Long,
    /** 最后修改时间（毫秒）；未知为 -1。 */
    val lastModified: Long,
)

/** 单次扫描的汇总报告。 */
data class ScanReport(
    /** 新导入的书数。 */
    val imported: Int,
    /** 已在书库（contentHash 重复）数。 */
    val alreadyExists: Int,
    /** 未变化跳过数（import_sources 快照命中）。 */
    val skippedUnchanged: Int,
    /** 导入失败的文件数。 */
    val failed: Int,
    /** 枚举总文件数（含被过滤掉的不支持格式）。 */
    val totalScanned: Int,
    /** 是否因条目上限截断（病态目录树防护）。 */
    val truncated: Boolean,
) {
    /** 需要处理的文件数（排除跳过的）。 */
    val processed: Int get() = imported + alreadyExists + failed
}

/** 扫描配置（阈值集中，便于测试注入低限）。 */
data class ScanConfig(
    /** 支持的扩展名（小写，不含点）。V1 PDF/CBZ 起支持（此前 pdf 被扫描跳过，自此反转）。 */
    val supportedExtensions: Set<String> = setOf("epub", "txt", "pdf", "cbz"),
    /** 单次扫描条目上限（枚举层截断，防病态目录树）。 */
    val maxEntries: Int = 5_000,
) {
    companion object {
        val DEFAULT = ScanConfig()
    }
}

/** 判定文件名是否为支持的扩展名（大小写不敏感）。 */
fun ScanConfig.isSupported(displayName: String): Boolean {
    val dot = displayName.lastIndexOf('.')
    if (dot !in 0 until displayName.length - 1) return false
    return displayName.substring(dot + 1).lowercase() in supportedExtensions
}
