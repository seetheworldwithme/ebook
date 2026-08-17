package com.xuziyue.ebook.reader

import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.adapter.pdfium.navigator.PdfiumPreferences
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

/**
 * Navigator 创建规约（V1 PDF/CBZ）：按 Publication 实际格式封装三种 Navigator 的差异化创建参数。
 *
 * 之前 [ReaderUiState.Ready] 直接携带 `EpubNavigatorFactory + EpubPreferences`，
 * PDF（PdfNavigatorFactory+PdfiumPreferences）/ CBZ（无工厂无偏好）插不进来。
 * 收敛为本 sealed 层后：VM 构造 spec、Fragment 按 spec 分支实例化，
 * 共同接口（currentLocator / go / navCommands / 音量键）不变。
 *
 * 各格式能力差异由 [com.xuziyue.ebook.model.ReaderCapabilities] gating（红线 #2），
 * 不在此重复判断。
 */
sealed interface NavigatorSpec {

    /** 恢复位置（红线 #1：Locator 为主数据）；null 从头读。三种格式共用。 */
    val initialLocator: Locator?

    /** EPUB（含 TXT 转 EPUB）：需要 EpubNavigatorFactory + 初始 EpubPreferences。 */
    data class Epub(
        val publication: Publication,
        val navigatorFactory: EpubNavigatorFactory,
        val initialPreferences: EpubPreferences,
        override val initialLocator: Locator?,
    ) : NavigatorSpec

    /**
     * PDF：PdfNavigatorFactory 由 PdfiumEngineProvider + publication 即时构造，偏好是 PdfiumPreferences。
     * 注意 [PdfiumEngineProvider] 本身非泛型（实现 PdfEngineProvider 时已固化类型参数），裸类型使用。
     */
    data class Pdf(
        val publication: Publication,
        val engineProvider: PdfiumEngineProvider,
        val initialPreferences: PdfiumPreferences,
        override val initialLocator: Locator?,
    ) : NavigatorSpec

    /** CBZ：ImageNavigatorFragment 只需 publication + listener，无工厂无偏好。 */
    data class Cbz(
        val publication: Publication,
        override val initialLocator: Locator?,
    ) : NavigatorSpec
}
