package com.xuziyue.ebook.reader.readium

import com.xuziyue.ebook.reader.readium.txt.TxtEpubConverter
import com.xuziyue.ebook.reader.readium.txt.TxtParseOutcome
import com.xuziyue.ebook.reader.readium.txt.TxtParser
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.isRestricted
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.mediatype.MediaType

/**
 * 打开本地 TXT：解析 → 转 EPUB → 缓存 → Readium 打开（P0V-04 方案 A）。
 *
 * Readium 无原生 TXT 解析器，故把 TXT 章节生成标准 EPUB 后复用 EPUB 全链路。
 * 与 [OpenBookUseCase] 同 `Try<Publication, OpenBookError>` 风格、同 IO 调度，
 * **不并入** [OpenBookUseCase]——保持 EPUB 路径 100% 不动。
 *
 * EPUB 转换结果缓存到 [cacheDir] 下，以 [contentHash]（原 txt 的 SHA-256）为 key 复用：
 * 首次打开解析+转换（10MB 量级约秒级），后续命中缓存秒开；cacheDir 系统可回收。
 *
 * @param facade Readium 门面（提供 assetRetriever + publicationOpener）。
 * @param txtParser TXT 解析器（编码探测 + 章节切分）。
 * @param converter TXT→EPUB 转换器。
 * @param cacheDir EPUB 缓存根目录（DI 注入 `context.cacheDir/txt-converted`）。
 */
class OpenTxtPublicationUseCase(
    private val facade: ReadiumFacade,
    private val txtParser: TxtParser,
    private val converter: TxtEpubConverter,
    private val cacheDir: File,
) {

    /**
     * 打开本地 TXT 文件。
     *
     * @param contentHash 原文件的 SHA-256（由调用方 [ReaderViewModel] 持有），用作 EPUB 缓存 key
     *   与 dc:identifier，免在此重算哈希。
     * @param allowUserInteraction 是否允许弹窗（渲染场景传 true）。
     */
    suspend fun open(
        file: File,
        contentHash: String,
        allowUserInteraction: Boolean = true,
    ): Try<Publication, OpenBookError> = withContext(Dispatchers.IO) {

        if (!file.exists()) {
            return@withContext Try.failure(OpenBookError.FileNotFound(file.absolutePath))
        }

        // 1. TXT 解析（编码探测 + 章节切分，红线 #5：NeedsEncodingChoice 不静默乱码）
        val book = when (val r = txtParser.parse(file)) {
            is TxtParseOutcome.Success -> r.book
            is TxtParseOutcome.NeedsEncodingChoice ->
                return@withContext Try.failure(OpenBookError.EncodingChoiceNeeded(r.candidates))
            is TxtParseOutcome.Failure ->
                return@withContext Try.failure(OpenBookError.TxtFailed(r.error))
        }

        // 2. 缓存命中即复用；否则转换并落盘（失败 delete 不留半成品，红线 #4）
        val cacheFile = File(cacheDir, "$contentHash.epub").apply { parentFile?.mkdirs() }
        if (!cacheFile.exists() || cacheFile.length() == 0L) {
            // 书名优先取首章标题（front-matter 通常是书名），兜底文件名；dc:identifier 用 contentHash
            val title = book.chapters.firstOrNull()?.title?.takeIf { it.isNotBlank() }
                ?: file.nameWithoutExtension
            try {
                cacheFile.outputStream().use { converter.writeTo(book, title, contentHash, it) }
            } catch (e: Throwable) {
                cacheFile.delete()
                return@withContext Try.failure(OpenBookError.TxtConvertFailed(e))
            }
        }

        // 3. 走 Readium EPUB 全链路（显式 mediaType=EPUB，避免嗅探歧义）
        val asset = facade.assetRetriever
            .retrieve(cacheFile, MediaType.EPUB)
            .mapFailure { OpenBookError.RetrieveFailed(it) }
            .getOrElse { return@withContext Try.failure(it) }

        val publication = facade.publicationOpener
            .open(asset, allowUserInteraction = allowUserInteraction)
            .mapFailure { OpenBookError.OpenFailed(it) }
            .getOrElse { return@withContext Try.failure(it) }

        if (publication.isRestricted) {
            return@withContext Try.failure(OpenBookError.Restricted)
        }

        Try.success(publication)
    }
}
