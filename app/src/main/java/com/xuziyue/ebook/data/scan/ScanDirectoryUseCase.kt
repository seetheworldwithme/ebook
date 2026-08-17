package com.xuziyue.ebook.data.scan

import android.net.Uri
import com.xuziyue.ebook.data.ImportBookUseCase
import com.xuziyue.ebook.data.ImportSourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 目录扫描用例（IMP-06）：组装枚举器 + [DirectoryScanner]，对外提供扫描入口。
 *
 * - [Mutex] 串行化：手动「立即扫描」与冷启动自动扫描并发时只跑一个，后者直接返回已跑结果
 *   （幂等——增量判定会跳过已处理文件）。
 * - 授权失效（根目录 SecurityException）返回 null，由调用方提示用户重新授权。
 */
class ScanDirectoryUseCase(
    private val enumerator: DocumentEnumerator,
    private val sourceRepository: ImportSourceRepository,
    private val importBookUseCase: ImportBookUseCase,
) {
    private val scanMutex = Mutex()

    /** 扫描进度回调（当前处理到第几个文件 / 总数）。 */
    data class Progress(val current: Int, val total: Int)

    /**
     * 扫描 [treeUri] 目录并增量导入。
     *
     * @return 扫描报告；授权失效返回 null（调用方提示重新授权）。
     */
    suspend fun scan(treeUri: Uri, config: ScanConfig = ScanConfig.DEFAULT): ScanReport? =
        withContext(Dispatchers.IO) {
            scanMutex.withLock {
                val enumeration = try {
                    enumerator.enumerate(treeUri, config)
                } catch (e: SecurityException) {
                    return@withContext null
                }
                val scanner = DirectoryScanner(
                    sourceDaoGet = { sourceRepository.findBySourceUri(it) },
                    sourceDaoUpsert = { sourceRepository.upsert(it) },
                    importUri = { importBookUseCase.importUri(Uri.parse(it)) },
                )
                scanner.scanDocuments(enumeration.documents).let { report ->
                    if (enumeration.truncated) report.copy(truncated = true) else report
                }
            }
        }
}
