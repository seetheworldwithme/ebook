package com.xuziyue.ebook.data.scan

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * 目录枚举器接口（IMP-06）：给定授权目录 tree Uri，递归列出全部支持格式的文档。
 *
 * 抽成接口是为了让 [DirectoryScanner] 的编排逻辑可在 Robolectric / 纯 JVM 单测里
 * 用假实现驱动（SAF ContentResolver 查询不好伪造）。
 */
interface DocumentEnumerator {
    /**
     * 递归枚举 [treeUri] 下的文档。
     *
     * @param config 过滤配置（扩展名 / 条目上限）
     * @return 枚举结果；授权失效等底层异常由实现决定抛出或返回空（见 [SafDocumentEnumerator]）。
     */
    suspend fun enumerate(treeUri: Uri, config: ScanConfig): EnumerationResult
}

/** 枚举结果：文档列表 + 是否因 [ScanConfig.maxEntries] 截断。 */
data class EnumerationResult(
    val documents: List<ScannedDocument>,
    val truncated: Boolean,
)

/**
 * 基于 SAF DocumentsContract 的生产实现（IMP-06）。
 *
 * - 子文档查询用 [DocumentsContract.buildChildDocumentsUriUsingTree]，一次查询同时取
 *   DISPLAY_NAME / SIZE / LAST_MODIFIED / MIME_TYPE，避免逐文件 N+1。
 * - 目录判定走 MIME_TYPE（`Document.MIME_TYPE_DIR`），不递归进隐藏目录（名字以 `.` 开头）。
 * - 文件按 [ScanConfig.isSupported] 过滤；同时跳过隐藏文件。
 * - 累计条目（文件+目录）超过 [ScanConfig.maxEntries] 即停止（截断标记），防病态目录树。
 * - 授权失效（SecurityException）或单目录查询失败：跳过该目录（扫描尽力而为），
 *   根目录失败抛出（调用方提示用户重新授权）。
 */
class SafDocumentEnumerator(private val context: Context) : DocumentEnumerator {

    override suspend fun enumerate(treeUri: Uri, config: ScanConfig): EnumerationResult {
        val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val out = mutableListOf<ScannedDocument>()
        var truncated = false
        var entries = 0
        val queue = ArrayDeque<Uri>()
        queue.add(rootDocUri)

        while (queue.isNotEmpty()) {
            val dirUri = queue.removeFirst()
            val children = try {
                queryChildren(dirUri)
            } catch (e: SecurityException) {
                if (dirUri == rootDocUri) throw e // 根授权失效，整体失败
                continue // 子目录异常：跳过（尽力而为）
            } ?: continue

            for (child in children) {
                if (++entries > config.maxEntries) {
                    truncated = true
                    return EnumerationResult(out.toList(), truncated)
                }
                val name = child.first ?: continue
                if (name.startsWith(".")) continue // 隐藏文件 / 目录
                if (child.second) {
                    queue.add(child.third) // 目录：入队继续递归
                } else if (config.isSupported(name)) {
                    out.add(ScannedDocument(child.third.toString(), name, child.fourth, child.fifth))
                }
            }
        }
        return EnumerationResult(out.toList(), truncated)
    }

    /**
     * 查询目录的直接子项；返回 (displayName, isDirectory, documentUri, size, lastModified) 元组列表。
     * 查询失败返回 null（调用方跳过该目录）。
     */
    private fun queryChildren(dirUri: Uri): List<Tuple5>? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            dirUri,
            DocumentsContract.getDocumentId(dirUri),
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        return try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
                val nameIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val docIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val sizeIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val mtimeIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                buildList {
                    while (c.moveToNext()) {
                        add(
                            Tuple5(
                                if (c.isNull(nameIdx)) null else c.getString(nameIdx),
                                c.getString(mimeIdx) == DocumentsContract.Document.MIME_TYPE_DIR,
                                DocumentsContract.buildDocumentUriUsingTree(dirUri, c.getString(docIdx)),
                                if (c.isNull(sizeIdx)) -1L else c.getLong(sizeIdx),
                                if (c.isNull(mtimeIdx)) -1L else c.getLong(mtimeIdx),
                            ),
                        )
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 五元组（Kotlin 无内置 Tuple5，局部私有）。 */
    private data class Tuple5(
        val first: String?,
        val second: Boolean,
        val third: Uri,
        val fourth: Long,
        val fifth: Long,
    )
}
