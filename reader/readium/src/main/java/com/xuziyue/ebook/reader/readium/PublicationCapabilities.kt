package com.xuziyue.ebook.reader.readium

import com.xuziyue.ebook.model.ReaderCapabilities
import com.xuziyue.ebook.model.ReaderFormat
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.search.isSearchable

/**
 * 把 Readium [Publication] 映射为引擎无关的 [ReaderFormat]（红线 #2：格式来自 Publication，非扩展名）。
 *
 * - 格式由 [Publication.conformsTo] 探测（Publication 无 format 字段，conformsTo 是规范 API）：
 *   TXT 经 OpenTxtPublicationUseCase 转 EPUB 后 conformsTo(EPUB)=true，能力天然等同 EPUB，
 *   能力层完全不需要知道原文件是 .txt。
 * - CBZ 经 streamer 内置 ImageParser 解析后 conformsTo(DIVINA)=true（DIVINA 与漫画图片序列
 *   共用 Readium 的 informal comic 规格族），映射为 [ReaderFormat.CBZ]。
 * - AUDIOBOOK/DIVINA 之外的未知格式兜底按 EPUB，避免未知格式被误判为 PDF 而错误关闭批注。
 */
@OptIn(ExperimentalReadiumApi::class)
fun Publication.toReaderFormat(): ReaderFormat = when {
    conformsTo(Publication.Profile.EPUB) -> ReaderFormat.EPUB
    conformsTo(Publication.Profile.PDF) -> ReaderFormat.PDF
    conformsTo(Publication.Profile.DIVINA) -> ReaderFormat.CBZ
    else -> ReaderFormat.EPUB
}

/**
 * 把 Readium [Publication] 映射为引擎无关的 [ReaderCapabilities]（红线 #2：能力来自 Publication，非扩展名）。
 *
 * 格式经 [toReaderFormat] 探测；搜索能力由 [Publication.isSearchable] 探针决定
 * （底层 findService(SearchService)），不按格式猜。
 *
 * Readium 知识内聚在本模块；:app 只调本扩展，不直接碰 conformsTo / isSearchable。
 */
@OptIn(ExperimentalReadiumApi::class)
fun Publication.toReaderCapabilities(): ReaderCapabilities {
    return ReaderCapabilities.from(format = toReaderFormat(), isSearchable = isSearchable)
}
