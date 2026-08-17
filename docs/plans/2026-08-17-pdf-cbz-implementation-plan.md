# PDF 基础浏览 + CBZ 实施计划（V1，2026-08-17）

> 对应 `docs/plans/2026-08-10-android-ebook-reader-design.md` §2.2（V1 = PDF 基础浏览 + CBZ）与 P0V-03（⏸ V1，本刀兑现）。
> 调研方式：javap 反编译 Gradle 缓存内 Readium 3.3.0 jar（与 READ-09/10 同套路，非文档推断）。

## 0. 调研结论（已坐实）

| 事实 | 依据 |
| --- | --- |
| PDF 解析只差一个开关：`ReadiumFacade` 的 `pdfFactory=null` 改为 `PdfiumDocumentFactory(context)` | `DefaultPublicationParser` 字节码：pdfFactory 非空才挂 `PdfParser` |
| **CBZ 解析今天就已启用**：`DefaultPublicationParser` 内部自动挂 `ImageParser`（CBZ→图片序列 Publication） | 构造器字节码：CompositePublicationParser 固定含 Epub/Pdf(条件)/ReadiumWebPub/**Image**/Audio 五个 parser |
| PDF 渲染：`PdfNavigatorFactory(publication, PdfiumEngineProvider)` + `createFragmentFactory(locator, prefs, listener)`；`PdfNavigatorFragment` 实现 VisualNavigator + Overflowable + Configurable（**非** Selectable/Decorable/Hyperlink） | javap |
| `PdfNavigatorFragment.Companion.createDummyFactory(engineProvider)` 存在（进程重建方案可复用现有模式） | javap |
| `PdfiumPreferences(fit, pageSpacing, readingProgression, scrollAxis)`——滚动/单页由 scrollAxis 表达 | javap |
| CBZ 渲染：`ImageNavigatorFragment` + `createFactory(publication, locator, listener)` / `createDummyFactory()`；实现 Overflowable（翻页/LTR-RTL 跟随书籍）；**无 Configurable（无偏好）、无公开缩放/双页 API** | javap + 反编译无 zoom/scale 引用 |
| PDF/CBZ Listener 全部是 default 方法（`onResourceLoadFailed`/`onJumpToLocator`），VM 可同时实现三个 Listener 无冲突 | javap |
| pdfium 三件套依赖 + JitPack 仓库 + 许可证页两条目（AndroidPdfViewer 3.2.8 / PdfiumAndroid 1.9.8）**全部已就位**，零新增依赖 | libs.versions.toml L49/L115、settings.gradle L22、LicenseData L39/63 |
| PDF 页 Locator 用 `locations.page/position`（`LocatorKt.getPage`）；目录来自 PDF outline（`PdfiumDocument.getOutline`） | javap |

## 1. 范围

**做**：
- 导入链路支持 `.pdf` / `.cbz`（SAF / ACTION_VIEW intent / 目录扫描三条入口 + mediaType 派生 + ZIP 安全校验适用面）
- 阅读器打开层按 Publication 实际格式分流三种 Navigator（EPUB / PDF / CBZ）
- PDF：连续滚动/单页切换（复用现有「翻页方式」开关映射 scrollAxis）、pinch 缩放（PDFView 内置）、页进度/强杀恢复、书签、目录（outline）、搜索入口按 isSearchable 探针（预计 false 自动隐藏）
- CBZ：翻页、LTR/RTL 跟随书籍、进度/恢复、书签
- 能力矩阵扩展 `ReaderFormat.CBZ` + UI gating（字号±/主题/字体滑块/高亮/TTS 对 PDF/CBZ 隐藏）

**不做（注记边界，对齐红线 #2「能力以实测为准」）**：
- PDF 文字选择/高亮/批注/TTS（issue #823，能力恒 false）
- CBZ 双页/缩放偏好（Readium 3.3 ImageNavigator 无公开 API；双页推后，缩放真机实测后注记）
- PDF/CBZ 夜间主题（PDF 页面是白色位图，反色属图像处理，推后）
- PDF Fit 偏好 UI（pinch 缩放已内置，适配宽度/高度偏好推后）

## 2. 改动清单（文件级）

### :core:model
1. `ReaderFormat` + `CBZ`
2. `ReaderCapabilities.from` + CBZ 分支（canOpen/canNavigate/canBookmark/canRestorePosition=true；canToc/canSearch/canHighlight/canAnnotate/canCopyShare/canTts=false——CBZ 无目录语义）+ `forCbz()` 工厂
3. 新增两个字段（UI gating 用，避免 UI 直接看 format）：
   - `canAdjustTypography`（字号/字体/主题等排版控件；EPUB=true，PDF/CBZ=false）
   - `canSwitchPagingMode`（翻页方式开关；EPUB/PDF=true，CBZ=false）

### :reader:readium
4. `ReadiumFacade`：`pdfFactory = PdfiumDocumentFactory(context)`
5. `PublicationCapabilities.kt`：抽 `Publication.toReaderFormat()`（conformsTo EPUB/PDF/**DIVINA**→CBZ；兜底仍 EPUB），`toReaderCapabilities()` 复用
6. 新 `PdfPreferencesMappings.kt`：`ReaderTypography.toPdfiumPreferences()`（scroll=SCROLL→Axis.VERTICAL / PAGINATED→HORIZONTAL，其余 null 走引擎默认）——纯函数可 JVM 单测

### :app（reader）
7. 新 `NavigatorSpec.kt`（sealed）：`Epub(publication, factory, prefs)` / `Pdf(publication, engineProvider, factory, prefs)` / `Cbz(publication)`，统一携带 initialLocator
8. `ReaderUiState.Ready`：`navigatorFactory + preferences` 两字段收敛为 `navigatorSpec: NavigatorSpec`
9. `ReaderViewModel`：
   - 追加实现 `PdfNavigatorFragment.Listener` / `ImageNavigatorFragment.Listener`（全 default，零 override）
   - `openPublication` 成功后按 `toReaderFormat()` 分流构造三种 spec（PDF：`PdfNavigatorFactory(publication, pdfEngineProvider)`）
   - 新 `pdfPreferences: StateFlow<PdfiumPreferences>`（typography.map { toPdfiumPreferences() }）
   - 注入 `PdfiumEngineProvider`（DI 单例）
10. `ReaderFragment`（最大改动面）：
    - `navigator: Navigator?` + 分支 helper；dummy factory 改为**三格式复合工厂**（按 class 名匹配 Pdf/Image/Epub dummy，进程重建恢复任意格式 child 不炸）
    - `ensureNavigator` 按 spec 实例化三种 Fragment（PDF 传 viewModel 作 listener；无 Epub 的字体/selection Configuration）
    - `bindNavigatorObservers`：公共块（currentLocator / navCommands / volumeKey / ttsPlaying / InputListener）走 `Navigator`/`OverflowableNavigator`；EPUB 专属块（submitPreferences / decorations / tts decorations）包 `if (nav is EpubNavigatorFragment)`，PDF 块单独 submit pdfPreferences
11. `ReaderScreen`：字号± 按 `canAdjustTypography` 隐藏；`TypographySheet` 按 `canAdjustTypography`/`canSwitchPagingMode` 分区显隐（显示区亮度/常亮/方向恒显——Window 层与格式无关）

### :app（导入链路）
12. `ImportBookUseCase`：ZIP 安全校验适用面 `ext == epub || ext == cbz`（**PDF 非 ZIP 绝不能过 ZipFile 校验**）；mediaType 派生抽纯函数 `mediaTypeForExtension`（epub→application/epub+zip / txt→text/plain / pdf→application/pdf / cbz→application/vnd.comic+zip）
13. `MainActivity` SAF launcher mimes + `application/pdf` + `application/vnd.comic+zip` + `application/x-cbz`；Manifest intent-filter 同步
14. `ScanConfig.supportedExtensions` + `pdf`、`cbz`（IMP-06 真机注记里「pdf 跳过」行为自此反转）
15. DI `ReaderModule`：provide `PdfiumEngineProvider` @Singleton

### 样本与脚本
16. 新 `scripts/gen_format_fixtures.py`（纯 stdlib）：生成 `samples/public/formats/minimal.pdf`（手写 3 页文本型 PDF）+ `sample.cbz`（stdlib 造 PNG 打 ZIP）+ README（自生成、无版权问题）
17. JVM 结构测试（同 TypographySamplesTest 范式）：PDF magic/页数、CBZ zip 列表合法

## 3. 关键设计决策

1. **不动 Room**：format 本就是字符串列，Locator JSON 格式无关——**零 schema 变更、零迁移**。
2. **能力驱动 UI，不看扩展名**（红线 #2）：新增两个 canXxx 字段而非让 UI 分支 format；PDF 搜索入口由 isSearchable 探针决定（预计 false 隐藏，实测 true 则白得）。
3. **PDF 翻页方式复用现有开关**：TypographySheet「翻页方式」→ scrollAxis，与 EPUB 交互一致，零新 UI。
4. **进程重建**：沿用「Ready 用真实 factory / 否则 dummy + onViewCreated 移除」模式，dummy 升级为三格式复合工厂。
5. **TXT 路径零改动**：转 EPUB 后天然走 EPUB 分支。

## 4. 测试计划（CLAUDE.md 硬性工作流）

单测（新增预估 ~15）：
- `ReaderCapabilitiesTest` +CBZ 分支与两新字段
- `PdfPreferencesMappingsTest`（scroll 双向映射 + 默认值）
- `mediaTypeForExtensionTest`（4 格式）
- ZIP 校验适用面纯函数测试（pdf 不进 / cbz 进）
- `ScanConfig.isSupported` pdf/cbz
- 格式样本结构测试（PDF/CBZ）

命令：`:app:testDebugUnitTest` + `:core:model` + `:reader:readium` + `:app:assembleDebug` + `:app:lintDebug` 全绿；zh/en 无新增文案则 key-parity 测试自动维持。

真机回归清单（vivo V2329A，🚧→✅ 条件）：
1. PDF：SAF 导入（元数据/封面/书库展示 application/pdf）→ 打开翻页（连续+切单页）→ pinch 缩放 → 进度 % + 强杀恢复 → 书签 → 目录（outline 样本）→ **搜索/高亮/TTS 入口不出现** → 选中菜单不出现
2. CBZ：导入 → 打开翻页 → 进度/书签/恢复 → RTL 样本（若有）方向正确 → 缩放实测（不支持则注记边界）
3. PDF off-by-one（issue #811）观察；16KB so 加载（Android 16 真机天然验证）
4. 回归：EPUB/TXT 全链路（打开/排版/高亮/TTS/书签）不受影响；`fake.pdf` 目录扫描从跳过变为导入

## 5. PROGRESS 更新

- `P0V-03` ⏸→🚧（代码完成）→ 真机后 ✅；REL-01/02 补 PDF/CBZ 复验注记（不回退 ✅，MVP 口径不受影响）
- 变更记录补本刀注记（API 事实、边界、测试证据）
