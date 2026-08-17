package com.xuziyue.ebook.data.scan

import com.xuziyue.ebook.data.ImportBookUseCase
import com.xuziyue.ebook.data.db.ImportSourceEntity

/**
 * 目录扫描编排器（IMP-06）：枚举 → 增量判定 → 逐个导入 → 汇总报告。
 *
 * 纯编排逻辑，不直接依赖 SAF / android.net.Uri（[importUri] 接收 String，由调用方负责 parse），
 * 可在纯 JVM 单测里用假函数驱动（Uri.parse 在 JVM 单测是 android.jar stub，会抛 not mocked）。
 *
 * 增量判定（design.md：按 URI、大小、修改时间和哈希增量处理）：
 * 1. import_sources 有记录 且 size+mtime 均未变 → 跳过（不重读内容，二次扫描秒回）；
 *    size/mtime 任一为 -1（未知）时视为「可能变化」，重新导入（contentHash 去重兜底，不重复入库）。
 * 2. 无记录或已变化 → 走 [ImportBookUseCase]（内部 contentHash 去重），成功（Imported/AlreadyExists）
 *   后 upsert 记录刷新快照；Failed 不落记录（下次扫描可重试）。
 * 3. 单个文件失败不中断整体扫描（失败计入 [ScanReport.failed]）。
 */
class DirectoryScanner(
    private val sourceDaoGet: suspend (String) -> ImportSourceEntity?,
    private val sourceDaoUpsert: suspend (ImportSourceEntity) -> Unit,
    private val importUri: suspend (String) -> ImportBookUseCase.Outcome,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { java.util.UUID.randomUUID().toString() },
) {

    /** 对已枚举的文档列表做增量导入；返回汇总报告。 */
    suspend fun scanDocuments(documents: List<ScannedDocument>): ScanReport {
        var imported = 0
        var alreadyExists = 0
        var skippedUnchanged = 0
        var failed = 0

        for (doc in documents) {
            val existing = sourceDaoGet(doc.uri)
            if (existing != null && isUnchanged(existing, doc)) {
                skippedUnchanged++
                continue
            }
            when (val outcome = importUri(doc.uri)) {
                is ImportBookUseCase.Outcome.Imported -> {
                    imported++
                    upsertRecord(doc, outcome.bookId)
                }
                is ImportBookUseCase.Outcome.AlreadyExists -> {
                    alreadyExists++
                    // 已在书库（含手动导入过 / hash 重复）：也要落记录，让下次扫描跳过。
                    upsertRecord(doc, outcome.bookId)
                }
                is ImportBookUseCase.Outcome.Failed -> failed++
            }
        }
        return ScanReport(
            imported = imported,
            alreadyExists = alreadyExists,
            skippedUnchanged = skippedUnchanged,
            failed = failed,
            totalScanned = documents.size,
            truncated = false,
        )
    }

    /** size+mtime 快照比对；未知值（-1）视为变化（宁可重导，contentHash 去重兜底）。 */
    private fun isUnchanged(record: ImportSourceEntity, doc: ScannedDocument): Boolean {
        val sizeKnown = doc.size >= 0 && record.fileSize == doc.size
        val mtimeKnown = doc.lastModified >= 0 && record.lastModified == doc.lastModified
        return sizeKnown && mtimeKnown
    }

    private suspend fun upsertRecord(doc: ScannedDocument, bookId: String) {
        val existing = sourceDaoGet(doc.uri)
        sourceDaoUpsert(
            ImportSourceEntity(
                id = existing?.id ?: idGenerator(),
                sourceUri = doc.uri,
                bookId = bookId,
                fileSize = doc.size,
                lastModified = doc.lastModified,
                scannedAt = clock(),
            ),
        )
    }
}
