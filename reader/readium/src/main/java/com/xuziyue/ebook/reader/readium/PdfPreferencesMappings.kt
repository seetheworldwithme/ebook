package com.xuziyue.ebook.reader.readium

import com.xuziyue.ebook.model.ReaderScrollMode
import com.xuziyue.ebook.model.ReaderTypography
import org.readium.adapter.pdfium.navigator.PdfiumPreferences
import org.readium.r2.navigator.preferences.Axis

/**
 * 全局排版偏好 → PDF [PdfiumPreferences] 映射（V1 PDF 基础浏览）。
 *
 * PDF 是固定版式，[ReaderTypography] 的字号/字体/行高/主题等重排字段一概不适用；
 * 唯一映射 **翻页方式**（READ-04「PDF 至少提供连续滚动与单页模式」）：
 * - [ReaderScrollMode.SCROLL] → [Axis.VERTICAL]（连续滚动）
 * - [ReaderScrollMode.PAGINATED] → [Axis.HORIZONTAL]（单页横滑）
 *
 * 与 EPUB 的 `EpubPreferences.scroll` 共用同一个 TypographySheet「翻页方式」开关，
 * 交互一致零新 UI；其余偏好字段保持 null 走 Pdfium 引擎默认（Fit/间距等推后）。
 */
fun ReaderTypography.toPdfiumPreferences(): PdfiumPreferences {
    val axis = when (scroll) {
        ReaderScrollMode.SCROLL -> Axis.VERTICAL
        // null（未设置）与 PAGINATED 都按引擎默认的单页横滑。
        ReaderScrollMode.PAGINATED, null -> Axis.HORIZONTAL
    }
    return PdfiumPreferences(scrollAxis = axis)
}
