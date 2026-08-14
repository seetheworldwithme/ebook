package com.xuziyue.ebook.data.backup

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.xuziyue.ebook.R
import com.xuziyue.ebook.data.db.AnnotationDao
import com.xuziyue.ebook.data.db.AnnotationEntity
import com.xuziyue.ebook.data.db.BookDao
import com.xuziyue.ebook.data.db.BookEntity
import com.xuziyue.ebook.data.db.BookmarkDao
import com.xuziyue.ebook.data.db.BookmarkEntity
import com.xuziyue.ebook.data.db.ReadingProgressDao
import com.xuziyue.ebook.data.db.ReadingProgressEntity
import com.xuziyue.ebook.data.db.ReadingSessionDao
import com.xuziyue.ebook.data.db.ReadingSessionEntity
import com.xuziyue.ebook.ui.UserMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * 全量备份用例（DATA-03，design.md L152）。
 *
 * 备份范围：数据库全表（books/progress/bookmarks/annotations/reading_sessions）+ DataStore 设置快照
 * + 书籍文件（books/{bookId}.{ext}）+ 封面（covers/{bookId}.png），打包为 ZIP。
 *
 * - 范式照抄 [com.xuziyue.ebook.data.export.ExportBookDataUseCase]：注入 DAO + @ApplicationContext context + withContext(IO)。
 * - 流式 [ZipOutputStream] 写文件，不一次性载入内存（大 EPUB 安全）。
 * - 自身膨胀防护：累计写入字节数，超 [MAX_BACKUP_TOTAL] 中止（自己生成的 ZIP 无需防压缩比）。
 * - 原子写：先写 cacheDir 临时 ZIP，再 copy 到 SAF URI（SAF 目标不支持 rename，临时文件保证失败不污染目标）。
 *
 * ZIP 结构见 [BackupDto] KDoc / 实施方案 §2.1。
 */
class BackupUseCase(
    private val bookDao: BookDao,
    private val progressDao: ReadingProgressDao,
    private val bookmarkDao: BookmarkDao,
    private val annotationDao: AnnotationDao,
    private val sessionDao: ReadingSessionDao,
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context,
) {

    /** 备份结果（message 直接映射用户可见提示，CLAUDE.md：可理解错误）。 */
    sealed class Outcome {
        data class Success(val bookCount: Int, val fileCount: Int) : Outcome()
        data class Failed(val message: UserMessage) : Outcome()
    }

    /**
     * 流式打包全量数据到 ZIP，写入 SAF 目标 [destUri]。
     */
    suspend fun backup(destUri: Uri): Outcome = withContext(Dispatchers.IO) {
        // 1. 全表快照
        val books = bookDao.snapshotAll()
        val progress = progressDao.snapshotAll()
        val bookmarks = bookmarkDao.snapshotAll()
        val annotations = annotationDao.snapshotAll()
        val sessions = sessionDao.snapshotAll()

        // 2. 构造 BackupDto + settings 快照
        val dto = BackupDto(
            exportedAt = System.currentTimeMillis(),
            books = books.map(::toRow),
            readingProgress = progress.map(::toRow),
            bookmarks = bookmarks.map(::toRow),
            annotations = annotations.map(::toRow),
            readingSessions = sessions.map(::toRow),
            settings = snapshotSettings(),
        )

        // 3. 流式写 ZIP 到临时文件
        val tmp = File(context.cacheDir, "backup.tmp.zip")
        var fileCount = 0
        val written = runCatching {
            ZipOutputStream(tmp.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(BACKUP_JSON))
                zip.write(dto.toJson().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                // 书籍文件 + 封面
                var totalBytes = 0L
                for (book in books) {
                    val sourceFile = File(book.filePath)
                    if (sourceFile.exists()) {
                        addFileEntry(zip, "books/${book.id}.${extOf(book.filePath)}", sourceFile)
                        fileCount++
                        totalBytes += sourceFile.length()
                        if (totalBytes > MAX_BACKUP_TOTAL) {
                            return@runCatching Outcome.Failed(UserMessage.Res(R.string.backup_too_large))
                        }
                    }
                    book.coverPath?.let { cp ->
                        val cover = File(cp)
                        if (cover.exists()) {
                            addFileEntry(zip, "covers/${book.id}.${extOf(cp).ifEmpty { "png" }}", cover)
                        }
                    }
                }
            }
            null as Outcome?
        }.getOrElse {
            tmp.delete()
            return@withContext Outcome.Failed(UserMessage.Res(R.string.backup_failed, listOf(it.message ?: "")))
        }
        written?.let { tmp.delete(); return@withContext it }

        // 4. 原子 copy 到 SAF URI（失败不污染目标）
        val copyOk = runCatching {
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                tmp.inputStream().use { it.copyTo(out) }
            } ?: error("无法打开目标文件")
        }.isSuccess
        tmp.delete()

        if (copyOk) Outcome.Success(books.size, fileCount)
        else Outcome.Failed(UserMessage.Res(R.string.backup_failed_unknown))
    }

    /** 把文件以 [entryName] 加入 ZIP（流式 copy）。 */
    private fun addFileEntry(zip: ZipOutputStream, entryName: String, file: File) {
        zip.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    /** 从路径取扩展名（小写，无点）。 */
    private fun extOf(path: String): String =
        path.substringAfterLast('.', "").lowercase()

    /** DataStore preferences → Map<String, JsonElement>（bool/double/string/long 转 JsonPrimitive）。 */
    private suspend fun snapshotSettings(): Map<String, JsonElement> {
        val prefs = dataStore.data.first()
        return buildMap {
            prefs.asMap().forEach { (key, value) ->
                put(key.name, toJsonPrimitive(value))
            }
        }
    }

    private fun toJsonPrimitive(value: Any?): JsonElement = when (value) {
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }

    // ===== Entity → Row =====

    private fun toRow(b: BookEntity) = BookRow(
        id = b.id, contentHash = b.contentHash, title = b.title, authors = b.authors,
        description = b.description, language = b.language, format = b.format, mediaType = b.mediaType,
        filePath = b.filePath, fileSize = b.fileSize, coverPath = b.coverPath,
        importedAt = b.importedAt, lastOpenedAt = b.lastOpenedAt, status = b.status.name,
    )

    private fun toRow(p: ReadingProgressEntity) = ProgressRow(
        bookId = p.bookId, locatorJson = p.locatorJson, progression = p.progression,
        updatedAt = p.updatedAt, deviceId = p.deviceId,
    )

    private fun toRow(b: BookmarkEntity) = BookmarkRow(
        id = b.id, bookId = b.bookId, locatorJson = b.locatorJson, excerpt = b.excerpt, createdAt = b.createdAt,
    )

    private fun toRow(a: AnnotationEntity) = AnnotationRow(
        id = a.id, bookId = a.bookId, locatorJson = a.locatorJson, selectedText = a.selectedText,
        note = a.note, color = a.color.name, createdAt = a.createdAt, updatedAt = a.updatedAt, deletedAt = a.deletedAt,
    )

    private fun toRow(s: ReadingSessionEntity) = SessionRow(
        id = s.id, bookId = s.bookId, startedAt = s.startedAt, endedAt = s.endedAt, activeSeconds = s.activeSeconds,
    )

    private companion object {
        const val BACKUP_JSON = "backup.json"
        /** 自身膨胀上限（约 1GB），超此中止（防无界写）。 */
        const val MAX_BACKUP_TOTAL = 1_000_000_000L
    }
}
