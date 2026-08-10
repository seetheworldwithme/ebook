package com.xuziyue.ebook.reader.readium

import android.graphics.Bitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.getOrElse

/**
 * 提取 Publication 元数据 + 封面（导入时调用，design.md §6.5 导入第 4 步）。
 *
 * Readium 知识内聚在本模块（:app 只消费 [Metadata]，不碰 Readium 类，分层同 PublicationCapabilities）。
 * `allowUserInteraction=false`（纯导入校验，无 LCP 弹窗）。
 */
@OptIn(ExperimentalReadiumApi::class)
class ExtractPublicationMetadataUseCase(
    private val openBookUseCase: OpenBookUseCase,
    private val openTxtUseCase: OpenTxtPublicationUseCase,
) {

    /** Readium 独有字段（format/mediaType 由调用方按扩展名派生，不在此穿透）。 */
    data class Metadata(
        val title: String,
        val authors: List<String>,
        val description: String?,
        val language: String?,
        val cover: Bitmap?,
    )

    /**
     * @param ext 文件扩展名（"epub"/"txt"），决定走哪条 open 路径。
     * @return open/parse 失败返回 [Result.failure]（打不开的书不入库）；元数据空仍成功（title 用文件名兜底）。
     */
    suspend fun extract(
        file: File,
        contentHash: String,
        ext: String,
    ): Result<Metadata> = withContext(Dispatchers.IO) {
        val tryPub: Try<Publication, OpenBookError> = if (ext.equals("txt", ignoreCase = true)) {
            openTxtUseCase.open(file, contentHash = contentHash, allowUserInteraction = false)
        } else {
            openBookUseCase.open(file, allowUserInteraction = false)
        }

        val publication = tryPub.getOrElse { error ->
            return@withContext Result.failure(IllegalStateException(error.message))
        }

        try {
            val m = publication.metadata
            Result.success(
                Metadata(
                    title = m.title?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
                    authors = m.authors.mapNotNull { it.name }.filter { it.isNotBlank() },
                    description = m.description,
                    language = m.languages.firstOrNull(),
                    cover = publication.cover(),
                ),
            )
        } finally {
            publication.close() // 必须释放，避免文件句柄泄漏
        }
    }
}
