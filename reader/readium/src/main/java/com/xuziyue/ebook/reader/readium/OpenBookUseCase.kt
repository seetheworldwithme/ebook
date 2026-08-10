package com.xuziyue.ebook.reader.readium

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.isRestricted
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.getOrElse

/**
 * 打开本地 EPUB 的用例。
 *
 * 流程（对齐 Readium 3.3.0 test-app `ReaderRepository`）：
 *   [File] → [AssetRetriever][org.readium.r2.shared.util.asset.AssetRetriever].retrieve → Asset
 *         → [PublicationOpener][org.readium.r2.streamer.PublicationOpener].open → [Publication]
 *
 * 安全（CLAUDE.md 红线 #4）：Readium 内部已防 Zip Slip 与损坏文件，
 * 但压缩炸弹的总量限制需自行加上——此处限定单文件大小上限。
 */
class OpenBookUseCase(private val facade: ReadiumFacade) {

    companion object {
        /** 单文件大小上限：200MB（压缩炸弹防御）。 */
        const val MAX_EPUB_SIZE: Long = 200L * 1024 * 1024
    }

    /**
     * 打开本地 EPUB 文件。
     *
     * @param allowUserInteraction 是否允许弹窗（如 LCP 密码框）。渲染场景传 true；纯导入校验传 false。
     */
    suspend fun open(
        file: File,
        allowUserInteraction: Boolean = true,
    ): Try<Publication, OpenBookError> = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            return@withContext Try.failure(OpenBookError.FileNotFound(file.absolutePath))
        }
        if (file.length() > MAX_EPUB_SIZE) {
            return@withContext Try.failure(
                OpenBookError.FileTooLarge(file.length(), MAX_EPUB_SIZE)
            )
        }

        val asset = facade.assetRetriever
            .retrieve(file)
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
