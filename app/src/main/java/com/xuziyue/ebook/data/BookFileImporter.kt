package com.xuziyue.ebook.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * EPUB / TXT 文件导入器（Phase 0 极简版）。
 *
 * 两条来源，都复制到应用私有目录 `filesDir/books/{contentHash}.{ext}`，原文件被删除 / 移动后仍可读：
 * 1. [importFromUri]：SAF 文件选择器返回的 `content://` Uri（按来源扩展名存 `.txt` / `.epub`）。
 * 2. [copyAssetEpub]：内置 assets 样本（如 Alice），零摩擦验证。
 *
 * - 不申请 `MANAGE_EXTERNAL_STORAGE`（CLAUDE.md 红线 #3），只用 SAF / assets。
 * - contentHash 相同（hash 重复）则复用已有文件，不重复占用空间（design.md §6.5 导入去重）。
 * - 复制到临时文件 + 原子重命名；失败删临时文件，不留半成品（CLAUDE.md 红线 #4）。
 *
 * Phase 1 的 IMP（元数据 / 封面 / 书库）不在本切片；这里只做「复制 + 哈希」最小集。
 */
class BookFileImporter(private val context: Context) {

    /** 私有目录下的书籍文件夹。 */
    private val booksDir: File
        get() = File(context.filesDir, "books").apply { if (!exists()) mkdirs() }

    /** 从 SAF Uri 导入，按来源扩展名存储，返回 [ImportedBook]。 */
    suspend fun importFromUri(uri: Uri): Result<ImportedBook> = withContext(Dispatchers.IO) {
        runCatching {
            val ext = extensionOf(uri) ?: "epub"
            val (hash, file) = copyWithHash({
                context.contentResolver.openInputStream(uri)
                    ?: throw java.io.IOException("无法打开所选文件：$uri")
            }, ext)
            ImportedBook(hash, file)
        }
    }

    /** 从内置 assets 复制 EPUB 样本（如 Alice），返回 [ImportedBook]。 */
    suspend fun copyAssetEpub(assetName: String): Result<ImportedBook> = withContext(Dispatchers.IO) {
        runCatching {
            val (hash, file) = copyWithHash({ context.assets.open(assetName) }, "epub")
            ImportedBook(hash, file)
        }
    }

    /** 从 SAF Uri 解析文件扩展名（小写）；无扩展名或查询失败返回 null（调用方兜底 epub）。 */
    private fun extensionOf(uri: Uri): String? {
        val name = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } else null
        } ?: return null
        val dot = name.lastIndexOf('.')
        return if (dot in 0 until name.length - 1) name.substring(dot + 1).lowercase() else null
    }

    /**
     * 流式复制到临时文件并同时计算 SHA-256；完成后原子重命名到 `{hash}.{ext}`。
     * 若 `{hash}.{ext}` 已存在则直接复用（去重），临时文件删除。
     */
    private fun copyWithHash(openInput: () -> InputStream, ext: String): Pair<String, File> {
        val tmp = File(booksDir, "importing-${System.nanoTime()}.tmp")
        try {
            val hash = openInput().use { input ->
                tmp.outputStream().use { output ->
                    input.copyToWithHash(output)
                }
            }
            val target = File(booksDir, "$hash.$ext")
            if (target.exists()) {
                tmp.delete() // hash 重复，复用已有
            } else {
                if (!tmp.renameTo(target)) {
                    // renameTo 失败兜底（跨挂载点时 renameTo 可能失败）
                    tmp.copyTo(target, overwrite = false)
                    tmp.delete()
                }
            }
            return hash to target
        } catch (e: Exception) {
            tmp.delete() // 失败清理临时文件，不留半成品
            throw e
        }
    }

    /** 复制流的同时计算 SHA-256，返回十六进制哈希。 */
    private fun InputStream.copyToWithHash(output: java.io.OutputStream): String {
        val buf = ByteArray(64 * 1024)
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        while (true) {
            val n = read(buf)
            if (n <= 0) break
            digest.update(buf, 0, n)
            output.write(buf, 0, n)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/** 导入结果：contentHash + 私有目录下的目标文件。 */
data class ImportedBook(val contentHash: String, val file: File)
