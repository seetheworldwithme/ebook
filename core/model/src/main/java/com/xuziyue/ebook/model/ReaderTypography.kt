package com.xuziyue.ebook.model

/**
 * 引擎无关的阅读正文主题（design.md §4.4 TYPE-02）。
 *
 * Readium 的 `Theme` 枚举只有 LIGHT / DARK / SEPIA，没有「跟随系统」——
 * [SYSTEM] 由上层据系统暗色配置解析为 LIGHT 或 DARK
 * （见 :reader:readium 的 `ReaderTypography.toEpubPreferences(isSystemDark)` 扩展）。
 *
 * 持久化时直接存 [name]，SYSTEM 是稳定值（不会随系统暗色变化而漂移），
 * 因此适合作为产品默认（首次打开即「跟随系统」）。
 */
enum class ReaderTheme { LIGHT, SEPIA, DARK, SYSTEM }

/**
 * 引擎无关的正文对齐（design.md §4.4 TYPE-01）。
 *
 * 中文阅读主要用 [JUSTIFY]（两端对齐）与 [START]（行首对齐：LTR 左对齐、RTL 自动右对齐）。
 * 其余对齐（CENTER/RIGHT/END）后续按需扩展。
 */
enum class ReaderTextAlign { START, JUSTIFY }

/**
 * 引擎无关的阅读翻页方式（design.md §4.3 READ-04）。
 *
 * - [PAGINATED]：分页模式（Readium `EpubPreferences.scroll = false`，引擎默认）。
 * - [SCROLL]：纵向连续滚动（`scroll = true`）。
 *
 * FXL（固定版式）EPUB 上 [SCROLL] 无效——Readium 对非 reflowable 强制 scroll=false；
 * MVP 不做能力 gating（FXL 非目标场景），详见 PROGRESS.md READ-04 注记。
 *
 * name 用作持久化 key，不可随意改名（会破坏已落盘偏好）。
 */
enum class ReaderScrollMode { PAGINATED, SCROLL }

/**
 * 引擎无关的阅读排版偏好（design.md §4.4 TYPE-01/02）。
 *
 * 全局偏好（跨书共享；**按书保存**是 P1 的 TYPE-05，本类型不含 bookId）。
 * 所有字段 nullable：null 表示「未设置 / 走引擎默认」，避免把任意默认值固化进持久化层
 * （字号、行高等的实际默认由 ReadiumCSS 提供，如 fontSize 默认 100%）。
 *
 * 与 Readium `EpubPreferences` 的字段一一对应，但本类型**不依赖 Readium**：
 * 映射在 :reader:readium 的 `ReaderTypography.toEpubPreferences()` 扩展里完成。
 *
 * 范围对照：
 * - TYPE-01：[fontSize] 字号 / [fontFamily] 字体 / [fontWeight] 字重 / [lineHeight] 行高
 *   / [paragraphSpacing] 段距 / [pageMargins] 页边距 / [textAlign] 对齐。
 * - TYPE-02：[theme] 主题（含 [ReaderTheme.SYSTEM] 跟随系统）。
 * - READ-04：[scroll] 翻页方式（分页 / 纵向滚动）。
 * - READ-03：[volumeKeyPaging] 音量键翻页开关（app 层按键拦截，**不映射给 Readium 引擎**）。
 *
 * 数值语义（与 Readium 字段对齐，UI 层负责范围约束）：
 * - [fontSize]：倍率，1.0 = 引擎默认（UI 范围 0.5–5.0）。
 * - [fontWeight]：0.0–2.5（Readium 归一化字重，非 CSS 100–900；1.0 ≈ normal）。
 * - [lineHeight]：倍率，1.0 = 引擎默认。
 * - [paragraphSpacing]：em。
 * - [pageMargins]：倍率。
 * - [fontFamily]：CSS font-family 字符串（如 `"serif"` / `"sans-serif"`）。
 * - [scroll]：翻页方式（[ReaderScrollMode]），null = 分页 = 引擎默认。
 */
data class ReaderTypography(
    val fontSize: Double? = null,
    val fontFamily: String? = null,
    val fontWeight: Double? = null,
    val lineHeight: Double? = null,
    val paragraphSpacing: Double? = null,
    val pageMargins: Double? = null,
    val textAlign: ReaderTextAlign? = null,
    val theme: ReaderTheme? = null,
    // READ-04：null = 分页（引擎默认，不强制持久化，避免漂移）。
    val scroll: ReaderScrollMode? = null,
    // READ-03：音量键翻页开关（app 层 Fragment 拦截 KeyEvent，不传给 Readium）。默认开（主流阅读器行为）。
    // 非 nullable：与其它 nullable 字段不同，它是布尔开关，未设取 true（产品默认开）。
    val volumeKeyPaging: Boolean = true,
) {
    companion object {
        /**
         * 首次默认：主题「跟随系统」（现代阅读器直觉），其余字段走引擎默认。
         *
         * Repository 在 DataStore 无记录时返回此值；用户改动后各项被显式覆盖并持久化。
         */
        val Default: ReaderTypography = ReaderTypography(theme = ReaderTheme.SYSTEM)
    }
}
