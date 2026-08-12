package com.xuziyue.ebook.data.export

import android.content.Context
import android.net.Uri
import com.xuziyue.ebook.R
import com.xuziyue.ebook.data.db.AnnotationDao
import com.xuziyue.ebook.data.db.AnnotationEntity
import com.xuziyue.ebook.data.db.BookDao
import com.xuziyue.ebook.data.db.BookEntity
import com.xuziyue.ebook.data.db.BookmarkDao
import com.xuziyue.ebook.data.db.BookmarkEntity
import com.xuziyue.ebook.data.db.ReadingProgressDao
import com.xuziyue.ebook.data.db.ReadingProgressEntity
import com.xuziyue.ebook.ui.UserMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * 单本书数据导出用例（DATA-01，design.md L150）。
 *
 * 聚合单本书的书签 + 高亮 + 笔记（+ 阅读进度）原始持久化态，序列化为 Markdown / JSON，
 * 经 SAF 写入用户指定 URI。
 *
 * - 直接注入 4 个 DAO（跨表聚合原始持久化态，类 [com.xuziyue.ebook.data.ImportBookUseCase] 跨数据源）：
 *   Repository 现有 observe 丢失原始 locatorJson（返回反序列化后的 Locator），导出需要原文 + 全时间戳。
 * - locator 原样保留 [com.xuziyue.ebook.data.PersistedLocator] 包装（含其内层 schemaVersion），
 *   损坏 locatorJson 的记录跳过（与 Repository.observe 一致）。
 * - 写文件用「临时文件 + copy 到 URI」（红线 #6 / design.md §7）：SAF 目标 URI 不支持 rename，
 *   先写完整临时文件再一次性 copy，生成失败不污染目标 URI。
 */
class ExportBookDataUseCase(
    private val bookDao: BookDao,
    private val annotationDao: AnnotationDao,
    private val bookmarkDao: BookmarkDao,
    private val progressDao: ReadingProgressDao,
    @ApplicationContext private val context: Context,
) {

    /** 导出格式。 */
    enum class Format { MARKDOWN, JSON }

    /** 导出结果（message 直接映射用户可见提示，CLAUDE.md：可理解错误）。 */
    sealed class Outcome {
        data class Success(val format: Format, val items: Int) : Outcome()
        data class Failed(val message: UserMessage) : Outcome()
    }

    /**
     * 导出 [bookId] 的书签 / 高亮 / 笔记（+进度）为 [format]，写入 [destUri]。
     * 三件套 + 进度全空时返回 Failed（无可导出）。
     */
    suspend fun export(bookId: String, format: Format, destUri: Uri): Outcome = withContext(Dispatchers.IO) {
        val book = bookDao.getById(bookId)
            ?: return@withContext Outcome.Failed(UserMessage.Res(R.string.error_book_not_found))
        val annotations = annotationDao.snapshotForBook(bookId)
        val bookmarks = bookmarkDao.forBook(bookId)
        val progress = progressDao.get(bookId)

        if (annotations.isEmpty() && bookmarks.isEmpty() && progress == null) {
            return@withContext Outcome.Failed(UserMessage.Res(R.string.error_export_empty))
        }

        val dto = buildExportDto(book, annotations, bookmarks, progress, System.currentTimeMillis())
        val content = when (format) {
            Format.JSON -> dto.toJson()
            Format.MARKDOWN -> dto.toMarkdown(context)
        }
        writeAtomically(destUri, content).fold(
            onSuccess = { Outcome.Success(format, annotations.size + bookmarks.size) },
            onFailure = {
                val msg = it.message
                Outcome.Failed(
                    if (msg != null) UserMessage.Res(R.string.error_export_write, listOf(msg))
                    else UserMessage.Res(R.string.error_export_write_unknown),
                )
            },
        )
    }

    /** 从 Entity 构造导出 DTO（损坏 locatorJson 的记录跳过）。 */
    private fun buildExportDto(
        book: BookEntity,
        annotations: List<AnnotationEntity>,
        bookmarks: List<BookmarkEntity>,
        progress: ReadingProgressEntity?,
        exportedAt: Long,
    ): ExportDto = ExportDto(
        schemaVersion = EXPORT_SCHEMA_VERSION,
        exportedAt = exportedAt,
        book = BookDto(
            id = book.id,
            title = book.title,
            authors = book.authors,
            format = book.format,
        ),
        progress = progress?.let { p ->
            parseLocator(p.locatorJson)?.let { loc ->
                ProgressDto(locator = loc, progression = p.progression, updatedAt = p.updatedAt)
            }
        },
        bookmarks = bookmarks.mapNotNull { b ->
            parseLocator(b.locatorJson)?.let { loc ->
                BookmarkDto(id = b.id, locator = loc, excerpt = b.excerpt, createdAt = b.createdAt)
            }
        },
        annotations = annotations.mapNotNull { a ->
            parseLocator(a.locatorJson)?.let { loc ->
                AnnotationDto(
                    id = a.id,
                    locator = loc,
                    selectedText = a.selectedText,
                    note = a.note,
                    color = a.color.name,
                    createdAt = a.createdAt,
                    updatedAt = a.updatedAt,
                )
            }
        },
    )

    /** 解析 PersistedLocator 包装 JSON 为 JsonObject（非对象 / 损坏返回 null，导出跳过）。 */
    private fun parseLocator(raw: String): JsonObject? =
        runCatching { Json.parseToJsonElement(raw) }.getOrNull()?.let {
            if (it is JsonObject) it else null
        }

    /** 临时文件 + copy 到目标 URI（原子写：失败不污染目标）。 */
    private fun writeAtomically(destUri: Uri, content: String): Result<Unit> = runCatching {
        val tmp = File(context.cacheDir, "export.tmp")
        try {
            tmp.writeText(content)
            val out = context.contentResolver.openOutputStream(destUri)
                ?: error(context.getString(R.string.error_open_dest))
            out.use { tmp.inputStream().copyTo(it) }
        } finally {
            tmp.delete()
        }
    }
}
