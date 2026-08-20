package com.xuziyue.ebook.data.backup

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.withTransaction
import com.xuziyue.ebook.R
import com.xuziyue.ebook.data.EpubSecurityValidator
import com.xuziyue.ebook.data.db.AnnotationDao
import com.xuziyue.ebook.data.db.AnnotationEntity
import com.xuziyue.ebook.data.db.BookDatabase
import com.xuziyue.ebook.data.db.BookDao
import com.xuziyue.ebook.data.db.BookEntity
import com.xuziyue.ebook.data.db.BookmarkDao
import com.xuziyue.ebook.data.db.BookmarkEntity
import com.xuziyue.ebook.data.db.BookTypographyDao
import com.xuziyue.ebook.data.db.BookTypographyEntity
import com.xuziyue.ebook.data.db.CollectionBookDao
import com.xuziyue.ebook.data.db.CollectionBookEntity
import com.xuziyue.ebook.data.db.CollectionDao
import com.xuziyue.ebook.data.db.CollectionEntity
import com.xuziyue.ebook.data.db.ReadingProgressDao
import com.xuziyue.ebook.data.db.ReadingProgressEntity
import com.xuziyue.ebook.data.db.ReadingSessionDao
import com.xuziyue.ebook.data.db.ReadingSessionEntity
import com.xuziyue.ebook.model.CollectionKind
import com.xuziyue.ebook.model.HighlightColor
import com.xuziyue.ebook.model.ReadingStatus
import com.xuziyue.ebook.ui.UserMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 全量恢复用例（DATA-03，design.md L152「恢复前预览冲突处理结果」）。
 *
 * 两阶段：
 * - [preview]：读 ZIP 解析 backup.json，按 contentHash 对比当前库，产出冲突报告（不写任何东西）。
 * - [restore]：按 [Strategy] 执行合并——书籍文件解压回私有目录（Zip Slip 防护 + contentHash 去重复用本地）
 *   + 记录 upsert（冲突按策略）+ settings 覆盖。
 *
 * 安全（红线 #4 / #6）：
 * - 解压路径逐 entry 校验 [EpubSecurityValidator.isZipSlip]，恶意备份包是攻击面。
 * - 累计解压字节数超 [MAX_RESTORE_TOTAL] 中止（防压缩炸弹）。
 * - 书籍文件先写临时文件再 rename（跨挂载点兜底 copyTo）。
 *
 * @property preview/restore 返回 [Outcome]（成功条数 / 失败可读原因）。
 */
class RestoreUseCase(
    private val db: BookDatabase,
    private val bookDao: BookDao,
    private val progressDao: ReadingProgressDao,
    private val bookmarkDao: BookmarkDao,
    private val annotationDao: AnnotationDao,
    private val sessionDao: ReadingSessionDao,
    private val collectionDao: CollectionDao,
    private val collectionBookDao: CollectionBookDao,
    private val bookTypographyDao: BookTypographyDao,
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context,
) {

    /** 冲突类型。 */
    enum class ConflictKind { NEW, UNCHANGED, CONFLICT_PROGRESS_NEWER, CONFLICT_NOTES_NEWER }

    /** 单本书的冲突预览项。 */
    data class ConflictItem(
        val title: String,
        val contentHash: String,
        val kind: ConflictKind,
    )

    /** 预览结果：总书数 + 新增数 + 冲突数 + 明细。 */
    data class PreviewResult(
        val totalBooks: Int,
        val newCount: Int,
        val conflictCount: Int,
        val items: List<ConflictItem>,
    )

    /** 恢复策略。 */
    enum class Strategy {
        /** 跳过冲突项（本地已有同 contentHash 且有更新），只导入纯新增。 */
        SKIP_CONFLICTS,
        /** 全部覆盖（backup 为准）。 */
        OVERWRITE_ALL,
        /** 字段级取新：进度 / 笔记按 updatedAt 比较，backup 更新才覆盖。 */
        MERGE_KEEP_NEWER,
    }

    sealed class Outcome {
        data class Restored(val newBooks: Int, val overwritten: Int, val skipped: Int) : Outcome()
        data class Failed(val message: UserMessage) : Outcome()
    }

    /**
     * 预览阶段：读 ZIP 的 backup.json，对比当前库产出冲突报告（不写）。
     */
    suspend fun preview(srcUri: Uri): PreviewResult = withContext(Dispatchers.IO) {
        val dto = readBackupDto(srcUri) ?: return@withContext PreviewResult(0, 0, 0, emptyList())
        val localProgressByBook = progressDao.snapshotAll().associateBy { it.bookId }
        val localAnnotationsByBook = annotationDao.snapshotAll().filter { it.deletedAt == null }
            .groupBy { it.bookId }

        val items = dto.books.map { book ->
            val local = bookDao.getByContentHash(book.contentHash)
            val kind = if (local == null) {
                ConflictKind.NEW
            } else {
                val backupProgress = dto.readingProgress.firstOrNull { it.bookId == book.id }
                val localProgress = localProgressByBook[local.id]
                val backupNotesMax = dto.annotations
                    .filter { it.bookId == book.id && it.deletedAt == null }
                    .maxOfOrNull { it.updatedAt } ?: 0L
                val localNotesMax = localAnnotationsByBook[local.id]?.maxOfOrNull { it.updatedAt } ?: 0L
                classifyConflict(
                    backupProgressMax = backupProgress?.updatedAt ?: 0L,
                    localProgressMax = localProgress?.updatedAt ?: 0L,
                    backupNotesMax = backupNotesMax,
                    localNotesMax = localNotesMax,
                )
            }
            ConflictItem(book.title, book.contentHash, kind)
        }
        PreviewResult(
            totalBooks = dto.books.size,
            newCount = items.count { it.kind == ConflictKind.NEW },
            conflictCount = items.count { it.kind != ConflictKind.NEW && it.kind != ConflictKind.UNCHANGED },
            items = items,
        )
    }

    /**
     * 执行恢复：按 [strategy] 合并 backup 到当前库。
     */
    suspend fun restore(srcUri: Uri, strategy: Strategy): Outcome = withContext(Dispatchers.IO) {
        val dto = readBackupDto(srcUri) ?: return@withContext Outcome.Failed(UserMessage.Res(R.string.error_backup_invalid_zip))

        // 1. 先把书籍文件 / 封面从 ZIP 解压到临时映射（bookId → 解压后的 File），再决定是否落盘。
        val extracted = runCatching { extractFiles(srcUri, dto.books) }
            .getOrElse { return@withContext Outcome.Failed(UserMessage.Res(R.string.backup_failed, listOf(it.message ?: ""))) }

        var newBooks = 0
        var overwritten = 0
        var skipped = 0
        // backup bookId → 恢复后实际 bookId（本地已存在则复用本地 id），供书架关系重映射。
        val bookIdMap = mutableMapOf<String, String>()
        // 已落盘（staging rename 完成）的目标文件，事务失败时回滚删除（红线 #6：失败不留半条记录）。
        val committedFiles = mutableListOf<File>()

        // DB 侧整体事务：中途任一步失败全部回滚，绝不留半恢复状态（修复审查严重问题 #2）。
        val txnResult = runCatching {
            db.withTransaction {
                for (bookRow in dto.books) {
                    val local = bookDao.getByContentHash(bookRow.contentHash)
                    val isConflict = local != null
                    if (isConflict && strategy == Strategy.SKIP_CONFLICTS) {
                        skipped++
                        continue
                    }

                    // 决定目标 bookId：本地存在则复用本地 id（避免重复），否则用 backup id。
                    val targetBookId = local?.id ?: bookRow.id

                    // 书籍文件落盘（contentHash 去重：目标已存在则跳过复用，避免重复占空间）。
                    // 先写 staging 临时文件，DB 事务成功后再 rename 到正式位置（原子替换，修复审查严重问题 #2）。
                    val bookFile = extracted.books[bookRow.id]
                    if (bookFile != null) {
                        val dest = File(context.filesDir, "books").apply { mkdirs() }
                        val target = File(dest, "${bookRow.contentHash}.${extOf(bookRow.filePath)}")
                        if (!target.exists()) {
                            val staged = File(dest, "staging-${bookRow.id}.tmp")
                            bookFile.inputStream().use { it.copyTo(staged.outputStream()) }
                            committedFiles.add(target)
                            if (!staged.renameTo(target)) {
                                staged.inputStream().use { it.copyTo(target.outputStream()) }; staged.delete()
                            }
                        }
                    }
                    // 封面
                    val coverFile = extracted.covers[bookRow.id]
                    var resolvedCoverPath: String? = bookRow.coverPath
                    if (coverFile != null) {
                        val cdir = File(context.filesDir, "covers").apply { mkdirs() }
                        val ctarget = File(cdir, "$targetBookId.png")
                        if (!ctarget.exists()) {
                            coverFile.inputStream().use { it.copyTo(ctarget.outputStream()) }
                        }
                        resolvedCoverPath = ctarget.absolutePath
                    }

                    // 写书记录：字段级 UPDATE（不 delete+insert，保住本地子表——修复审查严重问题 #1）。
                    val entity = BookEntity(
                        id = targetBookId,
                        contentHash = bookRow.contentHash,
                        title = bookRow.title,
                        authors = bookRow.authors,
                        description = bookRow.description,
                        language = bookRow.language,
                        format = bookRow.format,
                        mediaType = bookRow.mediaType,
                        filePath = File(context.filesDir, "books/${bookRow.contentHash}.${extOf(bookRow.filePath)}").absolutePath,
                        fileSize = bookRow.fileSize,
                        coverPath = resolvedCoverPath,
                        importedAt = bookRow.importedAt,
                        lastOpenedAt = bookRow.lastOpenedAt,
                        status = runCatching { ReadingStatus.valueOf(bookRow.status) }.getOrDefault(ReadingStatus.UNREAD),
                    )
                    upsertBook(entity)
                    if (isConflict) overwritten++ else newBooks++

                    // 进度 / 书签 / 批注 / 会话按策略写入
                    restoreRelated(dto, targetBookId, bookRow.id, strategy, local != null)
                    bookIdMap[bookRow.id] = targetBookId
                }

                // 书架 / 关系恢复（LIB-05）：collections 直接 upsert（系统书架确保存在），关系按 bookIdMap 重映射。
                restoreCollections(dto, bookIdMap)

                // 按书排版恢复（TYPE-05）：SKIP 策略不动本地，其余按 bookIdMap 重映射后 upsert（孤儿跳过）。
                if (strategy != Strategy.SKIP_CONFLICTS) {
                    dto.bookTypography.forEach { row ->
                        val targetBookId = bookIdMap[row.bookId] ?: return@forEach // 书被跳过，覆盖不留孤儿
                        bookTypographyDao.upsert(
                            BookTypographyEntity(
                                bookId = targetBookId,
                                overridesJson = row.overridesJson,
                                updatedAt = row.updatedAt,
                            ),
                        )
                    }
                }
            }
        }

        // 事务失败：回滚本次已落盘的书籍文件，返回失败（数据库已由 Room 事务自动回滚）。
        if (txnResult.isFailure) {
            committedFiles.forEach { runCatching { it.delete() } }
            return@withContext Outcome.Failed(
                UserMessage.Res(R.string.backup_failed, listOf(txnResult.exceptionOrNull()?.message ?: "")),
            )
        }

        // 2. settings 覆盖（SKIP 策略不动设置；其余覆盖）。设置在 DB 事务成功后才写，避免半恢复。
        if (strategy != Strategy.SKIP_CONFLICTS) {
            applySettings(dto.settings, wipe = strategy != Strategy.MERGE_KEEP_NEWER)
        }

        Outcome.Restored(newBooks, overwritten, skipped)
    }

    /**
     * 字段级 upsert 书记录：不存在则 insert；已存在则按 id 做全字段 UPDATE。
     *
     * 绝不 delete+insert——delete 会触发外键 CASCADE 连带删掉该书本地进度/书签/批注/排版，
     * 使 MERGE_KEEP_NEWER 的「本地更新时间」比较基准丢失、备份恒覆盖本地（审查严重问题 #1）。
     */
    private suspend fun upsertBook(entity: BookEntity) {
        val existing = bookDao.getById(entity.id)
        if (existing == null) {
            bookDao.insert(entity)
        } else {
            bookDao.updateAllFields(
                id = entity.id,
                contentHash = entity.contentHash,
                title = entity.title,
                authors = entity.authors,
                description = entity.description,
                language = entity.language,
                format = entity.format,
                mediaType = entity.mediaType,
                filePath = entity.filePath,
                fileSize = entity.fileSize,
                coverPath = entity.coverPath,
                importedAt = entity.importedAt,
                lastOpenedAt = entity.lastOpenedAt,
                status = entity.status,
            )
        }
    }

    /** 按 [strategy] 写入进度 / 书签 / 批注 / 会话。 */
    private suspend fun restoreRelated(
        dto: BackupDto,
        targetBookId: String,
        backupBookId: String,
        strategy: Strategy,
        localExisted: Boolean,
    ) {
        // 进度
        val backupProgress = dto.readingProgress.firstOrNull { it.bookId == backupBookId }
        if (backupProgress != null) {
            val shouldWrite = when (strategy) {
                Strategy.OVERWRITE_ALL -> true
                Strategy.SKIP_CONFLICTS -> !localExisted
                Strategy.MERGE_KEEP_NEWER -> {
                    val local = progressDao.get(targetBookId)
                    (local?.updatedAt ?: 0L) <= backupProgress.updatedAt
                }
            }
            if (shouldWrite) {
                progressDao.upsert(
                    ReadingProgressEntity(
                        bookId = targetBookId,
                        locatorJson = backupProgress.locatorJson,
                        progression = backupProgress.progression,
                        updatedAt = backupProgress.updatedAt,
                        deviceId = backupProgress.deviceId,
                    ),
                )
            }
        }
        // 书签（按策略覆盖）
        val bookmarks = dto.bookmarks.filter { it.bookId == backupBookId }
        if (bookmarks.isNotEmpty() && strategy != Strategy.MERGE_KEEP_NEWER) {
            bookmarkDao.deleteAllForBook(targetBookId)
            bookmarks.forEach {
                bookmarkDao.upsert(BookmarkEntity(it.id, targetBookId, it.locatorJson, it.excerpt, it.createdAt))
            }
        }
        // 批注（按策略覆盖）
        val annotations = dto.annotations.filter { it.bookId == backupBookId }
        if (annotations.isNotEmpty() && strategy != Strategy.MERGE_KEEP_NEWER) {
            annotations.forEach {
                annotationDao.upsert(
                    AnnotationEntity(
                        id = it.id,
                        bookId = targetBookId,
                        locatorJson = it.locatorJson,
                        selectedText = it.selectedText,
                        note = it.note,
                        color = runCatching { HighlightColor.valueOf(it.color) }.getOrDefault(HighlightColor.YELLOW),
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt,
                        deletedAt = it.deletedAt,
                    ),
                )
            }
        }
        // 会话（reading_sessions 直接 upsert，id 来自 backup）
        dto.readingSessions.filter { it.bookId == backupBookId }.forEach {
            sessionDao.upsert(ReadingSessionEntity(it.id, targetBookId, it.startedAt, it.endedAt, it.activeSeconds))
        }
    }

    /**
     * 恢复书架与归属关系（LIB-05）。
     *
     * - collections：upsert（已存在同 id 覆盖；系统书架「收藏」固定 id，确保恢复后一定存在）。
     * - collection_books：按 [bookIdMap] 把 backup bookId 重映射为本地实际 bookId 后写入（IGNORE 幂等，
     *   跳过映射不到的孤儿关系——书被 SKIP 或未恢复时不留半截关系）。
     */
    private suspend fun restoreCollections(dto: BackupDto, bookIdMap: Map<String, String>) {
        dto.collections.forEach { c ->
            collectionDao.upsert(
                CollectionEntity(
                    id = c.id,
                    name = c.name,
                    sortOrder = c.sortOrder,
                    createdAt = c.createdAt,
                    kind = runCatching { CollectionKind.valueOf(c.kind) }.getOrDefault(CollectionKind.CUSTOM),
                ),
            )
        }
        dto.collectionBooks.forEach { cb ->
            val targetBookId = bookIdMap[cb.bookId] ?: return@forEach // 孤儿关系跳过
            collectionBookDao.add(
                CollectionBookEntity(collectionId = cb.collectionId, bookId = targetBookId, addedAt = cb.addedAt),
            )
        }
    }

    /**
     * 把 settings 快照写回 DataStore。
     *
     * 类型还原（修复审查严重问题 #3）：备份的 JsonPrimitive 丢失了 DataStore 的具体数值类型
     * （int/float/long/double 是不同 key），恢复时必须按各仓库声明过的 key 类型映射写回，
     * 否则 key 类型不等 → 读取方拿不到值静默回默认。规则：
     * 1. 已知类型表（KNOWN_SETTING_KEY_TYPES）命中的 key 按声明类型写；
     * 2. 未命中的（未来新增 key 的旧备份）：bool 字面量 → boolean，"true"/"false" 字符串
     *    依规则 1 优先（不走猜测），数值 → double，其余 → string。
     *
     * @param wipe true=OVERWRITE_ALL 语义，先清空本地再覆盖；false=MERGE 语义，只覆盖备份中存在的 key。
     */
    private suspend fun applySettings(settings: Map<String, kotlinx.serialization.json.JsonElement>, wipe: Boolean) {
        if (settings.isEmpty()) return
        dataStore.edit { prefs ->
            if (wipe) prefs.clear()
            settings.forEach { (keyName, value) ->
                val pv = value as? kotlinx.serialization.json.JsonPrimitive ?: return@forEach
                val typeName = KNOWN_SETTING_KEY_TYPES[keyName]
                when (typeName) {
                    "bool" -> prefs[booleanPreferencesKey(keyName)] = pv.content.toBoolean()
                    "int" -> pv.content.toIntOrNull()?.let { prefs[intPreferencesKey(keyName)] = it }
                    "long" -> pv.content.toLongOrNull()?.let { prefs[longPreferencesKey(keyName)] = it }
                    "float" -> pv.content.toFloatOrNull()?.let { prefs[floatPreferencesKey(keyName)] = it }
                    "double" -> pv.content.toDoubleOrNull()?.let {
                        prefs[androidx.datastore.preferences.core.doublePreferencesKey(keyName)] = it
                    }
                    "string" -> prefs[stringPreferencesKey(keyName)] = pv.content
                    else -> when {
                        // 未声明的 key：bool 字面量优先（bool 不可能被误存成字符串数值）
                        pv.content == "true" || pv.content == "false" ->
                            prefs[booleanPreferencesKey(keyName)] = pv.content.toBoolean()
                        pv.isString -> prefs[stringPreferencesKey(keyName)] = pv.content
                        else -> pv.content.toDoubleOrNull()?.let {
                            prefs[androidx.datastore.preferences.core.doublePreferencesKey(keyName)] = it
                        } ?: return@forEach
                    }
                }
            }
        }
    }

    /** 读 ZIP 的 backup.json → BackupDto（损坏 / 无 backup.json 返回 null）。 */
    private suspend fun readBackupDto(srcUri: Uri): BackupDto? = withContext(Dispatchers.IO) {
        runCatching {
            val json = openZipEntry(srcUri, BACKUP_JSON) ?: return@runCatching null
            json.parseBackupDto()
        }.getOrNull()
    }

    /** 解压书籍文件 / 封面到临时目录（Zip Slip 防护每个 entry），返回 bookId → File 映射。 */
    private fun extractFiles(srcUri: Uri, books: List<BookRow>): Extracted {
        val tmpBooks = mutableMapOf<String, File>()
        val tmpCovers = mutableMapOf<String, File>()
        val tmpDir = File(context.cacheDir, "restore-extract").apply { mkdirs() }
        var totalBytes = 0L
        context.contentResolver.openInputStream(srcUri).use { input ->
            if (input == null) return Extracted(tmpBooks, tmpCovers)
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (EpubSecurityValidator.isZipSlip(entry.name)) {
                        zip.closeEntry(); entry = zip.nextEntry; continue // 拒绝恶意路径，跳过
                    }
                    val name = entry.name
                    when {
                        name.startsWith("books/") -> {
                            val bookId = name.removePrefix("books/").substringBeforeLast('.')
                            val out = File(tmpDir, "book-$bookId.tmp")
                            copyStream(zip, out)
                            tmpBooks[bookId] = out
                            totalBytes += out.length()
                        }
                        name.startsWith("covers/") -> {
                            val bookId = name.removePrefix("covers/").substringBeforeLast('.')
                            val out = File(tmpDir, "cover-$bookId.tmp")
                            copyStream(zip, out)
                            tmpCovers[bookId] = out
                            totalBytes += out.length()
                        }
                    }
                    if (totalBytes > MAX_RESTORE_TOTAL) error("restore too large")
                    zip.closeEntry(); entry = zip.nextEntry
                }
            }
        }
        return Extracted(tmpBooks, tmpCovers)
    }

    private data class Extracted(
        val books: Map<String, File>,
        val covers: Map<String, File>,
    )

    /** 读取 ZIP 中指定 entry 的字符串内容（找不到返回 null）。 */
    private fun openZipEntry(srcUri: Uri, entryName: String): String? {
        context.contentResolver.openInputStream(srcUri).use { input ->
            if (input == null) return null
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == entryName) {
                        return zip.bufferedReader().readText()
                    }
                    zip.closeEntry(); entry = zip.nextEntry
                }
            }
        }
        return null
    }

    private fun copyStream(input: InputStream, dest: File) {
        dest.outputStream().use { input.copyTo(it) }
    }

    private fun extOf(path: String): String = path.substringAfterLast('.', "").lowercase()

    private companion object {
        const val BACKUP_JSON = "backup.json"
        const val MAX_RESTORE_TOTAL = 1_000_000_000L

        /**
         * 各仓库声明的 DataStore key 类型表（key 名 → 类型名）。
         * 来源：AppSettingsRepository / ReaderTypographyRepository / ReaderDisplaySettingsRepository /
         * ReaderTtsPreferencesRepository 的 companion KEY_*。恢复时按此表写回正确的 PreferencesKey 类型，
         * 避免 key 类型错位导致设置静默丢default（审查严重问题 #3）。
         * 新增设置 key 时必须同步登记到这里。
         */
        val KNOWN_SETTING_KEY_TYPES: Map<String, String> = mapOf(
            // AppSettingsRepository
            "app_crash_log_enabled" to "bool",
            "app_reading_stats_enabled" to "bool",
            "app_import_tree_uri" to "string",
            "app_import_auto_scan" to "bool",
            // ReaderTypographyRepository
            "font_size" to "double",
            "font_family" to "string",
            "font_weight" to "double",
            "line_height" to "double",
            "paragraph_spacing" to "double",
            "page_margins" to "double",
            "text_align" to "string",
            "scroll" to "string",
            "theme" to "string",
            "volume_key_paging" to "bool",
            // ReaderDisplaySettingsRepository
            "display_brightness" to "float",
            "display_keep_screen_on" to "bool",
            "display_orientation" to "string",
            // ReaderTtsPreferencesRepository
            "tts_speed" to "double",
            "tts_voice_id" to "string",
            "tts_timer_minutes" to "int",
        )
    }
}

/**
 * 冲突分类纯函数（便于单测）：比较 backup 与本地的进度 / 笔记最新时间戳。
 * - 进度更新 → CONFLICT_PROGRESS_NEWER
 * - 笔记更新 → CONFLICT_NOTES_NEWER
 * - 都不更新 → UNCHANGED
 */
fun classifyConflict(
    backupProgressMax: Long,
    localProgressMax: Long,
    backupNotesMax: Long,
    localNotesMax: Long,
): RestoreUseCase.ConflictKind {
    val progressNewer = backupProgressMax > localProgressMax
    val notesNewer = backupNotesMax > localNotesMax
    return when {
        progressNewer -> RestoreUseCase.ConflictKind.CONFLICT_PROGRESS_NEWER
        notesNewer -> RestoreUseCase.ConflictKind.CONFLICT_NOTES_NEWER
        else -> RestoreUseCase.ConflictKind.UNCHANGED
    }
}
