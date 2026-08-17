package com.xuziyue.ebook.reader.readium

import android.content.Context
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/**
 * Readium 应用级门面。
 *
 * 聚合打开 Publication 所需的全部依赖（httpClient / assetRetriever / publicationOpener）。
 * 作为 Hilt @Singleton，由 :app 的 [com.xuziyue.ebook.di.ReaderModule] 构造。
 *
 * 对齐 Readium 3.3.0 实际 API（注意旧名已不存在）：
 * - 无 `Streamer`、无 `DefaultPublicationOpener`。
 * - 打开走 [PublicationOpener]（publicationParser = [DefaultPublicationParser]）。
 *
 * 参考：readium/kotlin-toolkit test-app `Readium.kt`。
 */
class ReadiumFacade(context: Context) {

    /** HTTP 客户端（Readium 用于远程资源/封面获取；本地 EPUB 打开不强制联网）。 */
    val httpClient = DefaultHttpClient()

    /** 资源嗅探器：把 File / Uri / Url 转成 Asset（识别格式）。 */
    val assetRetriever = AssetRetriever(context.contentResolver, httpClient)

    /**
     * Publication 打开器。
     *
     * - [DefaultPublicationParser] 负责把 Asset 解析成 Publication（EPUB / CBZ / PDF 等）；
     *   ImageParser（CBZ→图片序列）由其内部固定挂载，无需配置。
     * - V1 PDF：接入 Pdfium 适配（`marain87:PdfiumAndroid:1.9.8`，Apache-2.0 已在许可证清单）。
     * - contentProtections 留空（无 LCP/DRM）。
     */
    val publicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context,
            assetRetriever = assetRetriever,
            httpClient = httpClient,
            pdfFactory = PdfiumDocumentFactory(context),
        ),
        contentProtections = emptyList(),
    )
}
