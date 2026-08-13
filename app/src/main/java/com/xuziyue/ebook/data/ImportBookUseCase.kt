package com.xuziyue.ebook.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.xuziyue.ebook.R
import com.xuziyue.ebook.model.Book
import com.xuziyue.ebook.model.ReadingStatus
import com.xuziyue.ebook.reader.readium.ExtractPublicationMetadataUseCase
import com.xuziyue.ebook.ui.UserMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 导入编排用例（design.md §6.5 导入流程，CLAUDE.md 红线 #4：不留半条记录 / 孤儿文件）。
 *
 * 编排：[BookFileImporter]（落盘+哈希，原子 rename+去重+失败自清）→ contentHash 去重
 * → [ExtractPublicationMetadataUseCase]（元数据+封面）→ 封面落盘 → 单行写 Room。
 * 任一步失败清理已写文件，DB 不留半条记录（单行 insert 本身原子，无需 @Transaction）。
 *
 * format/mediaType 由扩展名派生（EPUB→`application/epub+zip`、TXT→`text/plain`），
 * 不穿透 Readium；V1 引入 PDF/CBZ 再改为从 AssetRetriever 嗅探。
 */
class ImportBookUseCase(
    private val importer: BookFileImporter,
    private val extractor: ExtractPublicationMetadataUseCase,
    private val bookRepository: BookRepository,
    private val securityValidator: EpubSecurityValidator,
    @ApplicationContext private val context: Context,
) {

    /** 导入结果三态（重复导入复用已有 bookId）。 */
    sealed class Outcome {
        data class Imported(val bookId: String) : Outcome()
        data class AlreadyExists(val bookId: String) : Outcome()
        data class Failed(val message: UserMessage) : Outcome()
    }

    suspend fun importUri(uri: Uri): Outcome = withContext(Dispatchers.IO) {
        val imported = importer.importFromUri(uri).getOrElse {
            return@withContext mapImportError(it, R.string.import_failed)
        }
        commit(imported.contentHash, imported.file)
    }

    private suspend fun commit(hash: String, file: File): Outcome {
        // 1. 去重：已入库则复用（文件层 importer 已按 hash 去重）。
        bookRepository.getByContentHash(hash)?.let {
            return Outcome.AlreadyExists(it.id)
        }

        val ext = file.extension.lowercase()

        // 2. EPUB 安全校验（REL-04 红线 #4）：ZIP 结构预检——Zip Slip / 压缩炸弹 / 损坏 / 超限。
        //    校验失败删书文件（无 DB 引用，安全），返回可理解错误。
        if (ext == "epub") {
            val result = securityValidator.validate(file)
            if (result is EpubSecurityResult.Unsafe) {
                file.delete()
                return Outcome.Failed(result.error.toUserMessage())
            }
        }

        // 3. 提取元数据 + 封面；open 失败则删书文件（无 DB 引用，安全）。
        val meta = extractor.extract(file, hash, ext).getOrElse {
            file.delete()
            val msg = it.message
            return Outcome.Failed(
                if (msg != null) UserMessage.Raw(msg) else UserMessage.Res(R.string.import_parse_failed),
            )
        }

        val bookId = UUID.randomUUID().toString()

        // 4. 封面落盘（temp + 原子 rename）；null 或失败降级 coverPath=null（封面缺失不影响阅读）。
        val coverPath = writeCoverAtomically(meta.cover, bookId)

        // 5. 构造 Book + 写库；insert 失败回滚书文件 + 封面，DB 不留半条记录。
        val now = System.currentTimeMillis()
        val book = Book(
            id = bookId,
            contentHash = hash,
            title = meta.title,
            authors = meta.authors,
            description = meta.description,
            language = meta.language,
            format = ext.uppercase(),
            mediaType = if (ext.equals("txt", ignoreCase = true)) "text/plain" else "application/epub+zip",
            filePath = file.absolutePath,
            fileSize = file.length(),
            coverPath = coverPath,
            importedAt = now,
            lastOpenedAt = null,
            status = ReadingStatus.UNREAD,
        )
        return try {
            bookRepository.insert(book)
            Outcome.Imported(bookId)
        } catch (e: Exception) {
            file.delete()
            coverPath?.let { File(it).delete() }
            Outcome.Failed(UserMessage.Res(R.string.import_db_failed, listOf(e.message ?: "")))
        }
    }

    /** 导入异常 → [Outcome.Failed]；安全异常映射本地化资源，其他走通用文案。 */
    private fun mapImportError(e: Throwable, defaultResId: Int): Outcome.Failed {
        val message = when (e) {
            is ImportSafetyException -> e.error.toUserMessage()
            else -> UserMessage.Res(defaultResId, listOf(e.message ?: ""))
        }
        return Outcome.Failed(message)
    }

    /** EPUB 安全校验错误 → 本地化用户提示（SET-01 i18n）。 */
    private fun EpubSecurityError.toUserMessage(): UserMessage = when (this) {
        is EpubSecurityError.FileTooLarge ->
            UserMessage.Res(R.string.import_error_file_too_large, listOf((actual / 1024 / 1024).toInt()))
        is EpubSecurityError.ZipSlip ->
            UserMessage.Res(R.string.import_error_zip_slip)
        is EpubSecurityError.TotalSizeExceeded ->
            UserMessage.Res(R.string.import_error_total_size)
        is EpubSecurityError.EntryTooLarge ->
            UserMessage.Res(R.string.import_error_entry_too_large)
        is EpubSecurityError.TooManyEntries ->
            UserMessage.Res(R.string.import_error_too_many_entries)
        is EpubSecurityError.CompressionBomb ->
            UserMessage.Res(R.string.import_error_compression_bomb)
        is EpubSecurityError.CorruptArchive ->
            UserMessage.Res(R.string.import_error_corrupt)
        is EpubSecurityError.InsufficientSpace ->
            UserMessage.Res(R.string.import_error_insufficient_space)
    }

    /** 封面写 `filesDir/covers/{bookId}.png`（先 tmp 再 rename，跨挂载点兜底 copyTo）。 */
    private fun writeCoverAtomically(cover: Bitmap?, bookId: String): String? {
        if (cover == null) return null
        val dir = File(context.filesDir, "covers").apply { mkdirs() }
        val tmp = File(dir, "$bookId.png.tmp")
        val target = File(dir, "$bookId.png")
        return try {
            tmp.outputStream().use { cover.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            target.absolutePath
        } catch (e: Exception) {
            tmp.delete() // 封面失败不致命：降级 coverPath=null
            null
        }
    }
}

/** 取 [ImportBookUseCase.Outcome] 的 bookId（Imported/AlreadyExists），Failed 返回 null。 */
fun ImportBookUseCase.Outcome.bookIdOrNull(): String? = when (this) {
    is ImportBookUseCase.Outcome.Imported -> bookId
    is ImportBookUseCase.Outcome.AlreadyExists -> bookId
    is ImportBookUseCase.Outcome.Failed -> null
}
