package com.xuziyue.ebook.model

/**
 * 引擎无关的阅读格式分类（不暴露文件扩展名，红线 #2）。
 *
 * 能力判断不依据原始文件后缀，而依据打开后的 Publication conformsTo 探测结果：
 * TXT 经 P0V-04 转 EPUB 后属 [EPUB]，能力等同 EPUB。
 */
enum class ReaderFormat { EPUB, PDF, CBZ }

/**
 * 当前 Publication 的能力矩阵（CLAUDE.md 红线 #2：UI 必须由此驱动，不按扩展名承诺能力）。
 *
 * 每个 Boolean 对应一项 READ 需求；UI 入口按对应字段 gating，能力为 false 即隐藏入口，
 * 杜绝「不可用按钮」（REL-02：能力矩阵与 UI 完全一致）。
 *
 * 能力来源：[from] 由 [ReaderFormat] + 运行时搜索探针 isSearchable 推导；
 * 运行时经 `Publication.toReaderCapabilities()`（:reader:readium 模块）填充。
 */
data class ReaderCapabilities(
    val format: ReaderFormat,
    val canOpen: Boolean, // READ-03 已打开即为 true
    val canNavigate: Boolean, // READ-03 翻页/滚动
    val canToc: Boolean, // READ-02 目录/章节跳转
    val canSearch: Boolean, // READ-05 书内搜索（由 isSearchable 探针决定）
    val canBookmark: Boolean, // READ-06 书签（Locator 为根，格式无关）
    val canRestorePosition: Boolean, // READ-01 Locator 位置恢复
    val canHighlight: Boolean, // READ-07 高亮（依赖文字选择）
    val canAnnotate: Boolean, // READ-07 笔记（依赖文字选择）
    val canCopyShare: Boolean, // READ-07 复制/系统分享（依赖文字选择）
    val canTts: Boolean, // READ-10 TTS（P1）
    val canAdjustTypography: Boolean, // TYPE-01/02 排版控件（字号/字体/主题等；重排版 EPUB 专属）
    val canSwitchPagingMode: Boolean, // READ-04 分页/滚动开关（PDF scrollAxis 支持；CBZ 无此偏好）
) {
    companion object {
        /**
         * 唯一能力逻辑入口：格式 + 运行时搜索探针 → 能力矩阵。
         *
         * - EPUB：全能力（[canSearch] 取 [isSearchable] 探针值；EPUB 恒注册 StringSearchService，实测为 true）。
         * - PDF：浏览/搜索/书签可用；文字选择/高亮/笔记/复制分享/TTS 不支持
         *   （Readium issue #823，design.md:48/130），对应字段 false；排版控件不适用
         *   （PDF 页面是固定版式位图），但支持滚动/单页切换（scrollAxis）。
         * - CBZ：图片序列——浏览/书签/位置恢复可用；无目录/搜索/批注/排版语义
         *   （ImageNavigator 无 Configurable，翻页方式不可切）。
         */
        fun from(format: ReaderFormat, isSearchable: Boolean): ReaderCapabilities = when (format) {
            ReaderFormat.EPUB -> ReaderCapabilities(
                format = format,
                canOpen = true,
                canNavigate = true,
                canToc = true,
                canSearch = isSearchable,
                canBookmark = true,
                canRestorePosition = true,
                canHighlight = true,
                canAnnotate = true,
                canCopyShare = true,
                canTts = true,
                canAdjustTypography = true,
                canSwitchPagingMode = true,
            )
            ReaderFormat.PDF -> ReaderCapabilities(
                format = format,
                canOpen = true,
                canNavigate = true,
                canToc = true,
                canSearch = isSearchable,
                canBookmark = true,
                canRestorePosition = true,
                // issue #823：PDF 无原生文字选择/高亮/批注支持。
                canHighlight = false,
                canAnnotate = false,
                canCopyShare = false,
                canTts = false,
                // PDF 是固定版式：字号/字体/行高等重排版无意义；主题反色属图像处理推后。
                canAdjustTypography = false,
                // scrollAxis 表达连续滚动 / 单页，与 EPUB「翻页方式」共用同一开关。
                canSwitchPagingMode = true,
            )
            ReaderFormat.CBZ -> ReaderCapabilities(
                format = format,
                canOpen = true,
                canNavigate = true,
                // 漫画图片序列无 outline 目录（ImageParser 不产 tableOfContents）。
                canToc = false,
                canSearch = false,
                canBookmark = true,
                canRestorePosition = true,
                canHighlight = false,
                canAnnotate = false,
                canCopyShare = false,
                canTts = false,
                canAdjustTypography = false,
                // ImageNavigator 无 Configurable（无偏好接口），翻页方式不可切。
                canSwitchPagingMode = false,
            )
        }

        /** EPUB 预定义工厂（单测可读性 + VM 安全默认初值；[isSearchable] 默认 true）。 */
        fun forEpub(isSearchable: Boolean = true): ReaderCapabilities =
            from(ReaderFormat.EPUB, isSearchable)

        /**
         * PDF 预定义工厂（标 spec 意图矩阵：浏览/搜索/书签，无批注；供单测与 V1 兜底）。
         *
         * MVP 运行时不产生 PDF 能力（ReadiumFacade 的 pdfFactory=null，PDF 整体降级 V1）；
         * V1 真开 PDF 时走运行时 `Publication.toReaderCapabilities()` 以 [isSearchable] 实测，
         * UI 永远只看运行时结果（design.md:130「未验证则隐藏」由此双重保证）。
         */
        fun forPdf(isSearchable: Boolean = true): ReaderCapabilities =
            from(ReaderFormat.PDF, isSearchable)

        /**
         * CBZ 预定义工厂（V1：浏览/书签/恢复，无目录/搜索/批注/排版）。
         */
        fun forCbz(): ReaderCapabilities = from(ReaderFormat.CBZ, isSearchable = false)
    }
}
