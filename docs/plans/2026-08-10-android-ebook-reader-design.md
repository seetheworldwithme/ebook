# Android 电子书阅读器：产品需求与技术架构

> 状态：产品与技术方案初稿  
> 日期：2026-08-10  
> 默认产品定位：本地离线优先的个人电子书库，不含在线书城、账号付费和商业 DRM 体系。

## 1. 结论摘要

首版应做成一款“安静、可靠、离线可用”的 Android 个人阅读器，核心价值不是宣称支持最多格式，而是让用户导入一本书后，稳定地完成阅读、定位、搜索和批注。

建议采用以下方案：

- 原生 Android：Kotlin + Jetpack Compose + 单 Activity，阅读器内核通过 Fragment 容器接入。
- 阅读内核：Readium Kotlin Toolkit，负责 EPUB 的解析、导航、排版、定位、搜索、标注装饰和 TTS；PDF 通过 Readium 的 Pdfium 适配器接入。
- 数据层：Room 保存书籍元数据、阅读位置、书签、批注和阅读统计；Proto DataStore 保存全局偏好；书籍原文件、封面和缓存放在应用私有文件目录。
- 产品模式：local-first。书籍和阅读数据以本地为唯一事实来源，首版不依赖服务端。
- 正式支持格式：MVP = EPUB 2/3 + TXT（PDF 经 2026-08 调研降级到 V1，详见 `2026-08-10-implementation-plan.md`）；V1 加入 PDF 基础浏览 + CBZ。MOBI/AZW3、FB2、DJVU、CBR 等只作为后续候选，不承诺首版支持。
- DRM 边界：仅支持无 DRM 文件。Kindle/Adobe DRM 不在范围内；Readium LCP 需单独商务与合规评估。
- 最低系统版本：`minSdk 23`（Android 6.0），`compileSdk 36`；发布时的 `targetSdk` 跟随 Google Play 当期要求。

## 2. 调研结论

### 2.1 市场功能基线

| 产品 | 典型能力 | 对本项目的启示 |
| --- | --- | --- |
| Google Play Books | 上传 EPUB/PDF、离线阅读、跨设备书库、笔记与同步 | 简洁可靠比选项数量更重要；同步应建立在稳定本地模型之上 |
| Moon+ Reader | 大量格式、主题与排版选项、手势/按键映射、TTS、WebDAV/Dropbox 同步 | 高度自定义有价值，但设置过多会增加学习成本，适合作为 V1/V2 |
| Librera | 多格式、目录扫描、书签/批注、字典、TTS、云同步、列表/网格书库 | 文件发现、书库组织和阅读辅助功能是重度用户刚需 |
| Readium | EPUB/PDF/有声书统一 Publication/Locator 模型，支持排版、搜索、标注装饰、TTS | 可避免自研 EPUB 标准兼容层，并为未来跨端同步保留稳定定位模型 |

主流阅读器共同具备的功能可以归纳为六组：

1. 书籍进入：系统文件选择、分享打开、目录扫描、在线目录或云盘。
2. 书库管理：封面、列表/网格、最近阅读、排序、搜索、收藏、标签/书架。
3. 阅读导航：恢复进度、目录、页码/百分比、跳转、书内搜索、历史返回。
4. 个性化排版：字号、字体、行距、段距、页边距、主题、亮度、翻页/滚动。
5. 阅读沉淀：书签、高亮、笔记、摘录、分享、导出和统计。
6. 辅助与跨设备：TTS、字典/翻译、无障碍、备份、同步、平板/折叠屏适配。

本项目不应在 MVP 中复制全部功能。首版成功标准是：导入可靠、打开快速、位置不丢、EPUB 排版舒适、PDF 浏览稳定、核心数据可导出。

### 2.2 格式能力与优先级

| 格式 | MVP 支持级别 | 技术实现 | 首版能力边界 | 结论 |
| --- | --- | --- | --- | --- |
| EPUB 2/3（`.epub`） | 完整支持 | Readium Streamer + EPUB Navigator | 流式/固定版式、目录、搜索、书签、高亮、笔记、TTS；EPUB Media Overlays 暂不承诺 | 核心格式 |
| PDF（`.pdf`） | V1（MVP 不含） | Readium PDF 接口 + Pdfium adapter（`marain87:PdfiumAndroid:1.9.8`） | 缩放、单/连续页、页码跳转、进度与书签、全文搜索；**文字选择/高亮/批注不支持**（issue #823） | 2026-08 调研后降级 V1；能力不能与 EPUB 等同宣传 |
| TXT（`.txt`） | 完整阅读 | 导入时按章节切分并规范化为内部 XHTML/Publication | UTF-8、带 BOM 编码；GB18030 自动探测失败时让用户手选编码 | 核心格式，中文用户常用 |
| CBZ（`.cbz`） | V1 | 独立图片 Publication/Navigator，或待 Readium 支持成熟后接入 | 图片序列、LTR/RTL、单页/双页、缩放；无文本搜索 | 漫画优先格式 |
| HTML/Markdown | V1/V2 候选 | 安全清洗后转内部 EPUB/XHTML | 禁止任意脚本和外部资源默认执行 | 可低成本扩展，但不是首版刚需 |
| FB2 | V2 候选 | XML 转内部 Publication | 需处理嵌入图片、注释、样式 | 视用户数据再决定 |
| MOBI/PRC/AZW3 | 暂缓 | 独立解析器或导入前转换 | 格式变体多；加密 Kindle 文件不能打开 | 不要只按扩展名承诺兼容 |
| DJVU | 暂缓 | 第三方原生引擎 | 引擎维护、体积和授权成本高 | 有明确用户量后再做 |
| CBR | 暂缓 | RAR 解压 + 图片阅读器 | RAR 依赖与授权需审查 | 先用 CBZ 覆盖漫画需求 |
| DOCX/RTF/CHM | 不支持 | 建议用户先转换为 EPUB/PDF | 排版还原与安全面过大 | 不是电子书阅读核心 |

格式支持必须按“能力矩阵”展示，不能只列扩展名。例如 PDF 的书签和页码可用，不代表 PDF 文字高亮也已可用。

### 2.3 EPUB 与 PDF 的技术事实

EPUB 3 是基于 ZIP 容器打包 XHTML、CSS、SVG 等资源的开放标准，同时包含导航、可重排/固定版式、全球文字排版与媒体叠加等机制。因此 EPUB 阅读并不是简单解压后用一个普通 WebView 打开 HTML；需要正确处理 spine、资源解析、位置映射、脚注、竖排/RTL、安全策略和用户样式覆盖。

Readium 当前正式支持 EPUB 2、EPUB 3 和 PDF；EPUB 可用分页/滚动、搜索、高亮和 TTS，而其公开能力表仍把 PDF 搜索、高亮与 TTS 标为未完整实现。Readium 的 PDF 层也不自行渲染 PDF，而是通过 Pdfium 或商业引擎适配。因此本方案把“PDF 文字选择、搜索、批注”列为验证后再排期的能力，不在 MVP 中做虚假同等承诺。

## 3. 产品定义

### 3.1 目标用户

- 主要用户：有本地 EPUB/PDF/TXT 文件，希望在 Android 手机、平板或墨水屏设备上长期阅读的人。
- 次要用户：需要高亮、做笔记、导出摘录或使用 TTS 的学习型读者。
- 暂不服务：购买商业版权电子书、管理机构借阅 DRM、专业 PDF 编辑和手写批注的用户。

### 3.2 核心使用场景

1. 用户从下载目录、网盘文档提供器或其他 App 的“打开方式”导入一本书。
2. App 提取书名、作者和封面，去重后加入书库。
3. 用户从最近阅读继续，恢复到精确章节/位置。
4. 用户调整主题和排版，使用目录、进度条或搜索定位内容。
5. 用户添加书签、高亮或笔记，并可按书查看、跳回原文及导出。
6. App 被杀进程、升级或重启后，阅读位置和数据仍然可靠。

### 3.3 不在首版范围

- 在线书城、支付、会员、广告和推荐算法。
- 账号系统、跨端云同步和多人协作。
- Kindle、Adobe ACS 或其他商业 DRM 破解/兼容。
- PDF 编辑、签名、表单、OCR 和手写墨迹。
- 有声书、播客、EPUB Media Overlays。
- OPDS 在线书库、WebDAV、Calibre Content Server。

## 4. 功能需求

优先级定义：P0 为 MVP 必须完成；P1 为首个增强版本；P2 为长期候选。

### 4.1 导入与文件管理

| ID | 优先级 | 需求 | 验收口径 |
| --- | --- | --- | --- |
| IMP-01 | P0 | 通过系统文档选择器导入单个或多个受支持文件 | 不申请“所有文件访问”权限；云盘等 DocumentsProvider 中的文件可被选择 |
| IMP-02 | P0 | 接收系统 `ACTION_VIEW` / `ACTION_SEND` 打开的电子书 | 从文件管理器或聊天工具分享文件后能进入导入确认页 |
| IMP-03 | P0 | 导入时复制到应用私有目录 | 删除或移动原文件后仍能阅读；导入失败不留下半成品 |
| IMP-04 | P0 | 提取标题、作者、封面、格式、文件大小和唯一哈希 | 元数据缺失时使用文件名和默认封面；SHA-256 相同文件提示已存在 |
| IMP-05 | P0 | 展示导入进度、成功和可理解的失败原因 | 加密、损坏、格式不支持、空间不足分别提示，不只显示“打开失败” |
| IMP-06 | P1 | 用户授权指定目录并增量扫描 | 使用 Storage Access Framework 的目录授权；按 URI、大小、修改时间和哈希增量处理 |
| IMP-07 | P1 | 删除书籍时选择“仅从书库移除”或“同时删除 App 内副本” | 操作前明确影响；删除原始外部文件不属于 App 默认行为 |

### 4.2 书库

| ID | 优先级 | 需求 | 验收口径 |
| --- | --- | --- | --- |
| LIB-01 | P0 | 网格/列表展示封面、书名、作者、进度和最近阅读时间 | 书库包含 5,000 条记录时仍可流畅滚动和搜索 |
| LIB-02 | P0 | 最近阅读、全部、已读完三个入口 | 阅读和完成状态变化后列表实时更新 |
| LIB-03 | P0 | 按书名/作者搜索，按最近阅读/导入时间/书名排序 | 搜索忽略大小写；中文书名可直接匹配 |
| LIB-04 | P0 | 书籍详情页 | 展示元数据、阅读进度、文件信息、书签/笔记数量及继续阅读入口 |
| LIB-05 | P1 | 收藏、标签和自定义书架 | 一本书可属于多个书架；删除书架不删除书籍 |
| LIB-06 | P1 | 批量选择、移动到书架、删除和重新提取元数据 | 批处理有进度和失败汇总 |
| LIB-07 | P2 | 手动编辑元数据与封面 | 修改只影响本地书库，不默认改写原文件 |

### 4.3 阅读器通用能力

| ID | 优先级 | 需求 | 验收口径 |
| --- | --- | --- | --- |
| READ-01 | P0 | 打开时恢复最近可靠位置 | 正常退出、切后台、进程被杀和设备重启后均能恢复；EPUB 使用 Locator，不以易漂移的“页码”作为主键 |
| READ-02 | P0 | 目录、章节跳转、当前位置百分比和进度拖动 | 跳转后可返回上一个阅读位置；固定版式/PDF 显示实际页索引 |
| READ-03 | P0 | 点击区域、左右滑动和音量键翻页 | 音量键功能可关闭；不会与 TTS 音量控制冲突 |
| READ-04 | P0 | 分页和纵向滚动两种模式 | EPUB 切换模式后保持语义位置；PDF 至少提供连续滚动与单页模式 |
| READ-05 | P0 | 书内搜索 | EPUB/TXT 显示上下文、结果数并可跳转；PDF 若引擎未通过验证则明确不显示入口 |
| READ-06 | P0 | 书签 | 可添加、取消、列表查看和跳回原文；重复位置不生成重复书签 |
| READ-07 | P0 | 高亮、笔记、复制和系统分享 | EPUB/TXT 完整支持；PDF 仅在文字选择能力验证通过后启用 |
| READ-08 | P0 | 退出阅读时自动保存位置 | 翻页后防抖保存，进入后台和页面销毁前强制保存，不阻塞 UI |
| READ-09 | P1 | 历史位置前进/后退、脚注弹层、外部链接确认 | 目录、搜索结果、脚注跳转均可返回；外链不会静默打开 |
| READ-10 | P1 | TTS 朗读 | 可播放/暂停、调速、选声音、定时停止；来电/其他媒体抢占音频焦点时正确暂停 |

### 4.4 EPUB/TXT 排版

| ID | 优先级 | 需求 | 验收口径 |
| --- | --- | --- | --- |
| TYPE-01 | P0 | 字号、字体、字重、行高、段距、页边距和对齐 | 调整实时预览，并保持当前语义位置 |
| TYPE-02 | P0 | 日间、米黄、夜间主题与跟随系统 | 状态栏、导航栏、菜单与正文主题一致；夜间无白屏闪烁 |
| TYPE-03 | P0 | 屏幕亮度、常亮和方向设置 | “跟随系统”是默认值；仅阅读页可覆盖系统亮度 |
| TYPE-04 | P0 | 正确显示中文、日文、RTL、竖排和 ruby 注音 | 建立包含这些排版特征的 EPUB 回归样本集 |
| TYPE-05 | P1 | 自定义字体导入和按书保存排版偏好 | 无效字体不导致书籍无法打开；可恢复全局默认 |
| TYPE-06 | P2 | 用户 CSS、高级断词和首行缩进规则 | 作为专家设置，提供一键复位 |

### 4.5 数据、导出与统计

| ID | 优先级 | 需求 | 验收口径 |
| --- | --- | --- | --- |
| DATA-01 | P0 | 导出单本书的书签、高亮和笔记为 Markdown/JSON | JSON 包含稳定 schema 版本、书籍 ID、Locator 和时间戳；Markdown 适合人阅读 |
| DATA-02 | P0 | 本地数据库自动迁移 | 升级 App 不丢书库、进度和批注；迁移有自动化测试 |
| DATA-03 | P1 | 全量备份/恢复 | 备份含数据库、设置和可选书籍文件；恢复前预览冲突处理结果 |
| DATA-04 | P1 | 阅读时长、日/周趋势、连续阅读天数 | 仅前台实际阅读且无长时间静止时计时；统计可关闭和清空 |
| DATA-05 | P2 | 跨设备同步 | 本地先写、Outbox 后同步；冲突按字段及更新时间合并，而非覆盖整本书数据 |

### 4.6 设置、无障碍与隐私

- P0：简体中文和英文；所有用户可见文案进入资源文件。
- P0：支持 TalkBack、合理的语义标签、焦点顺序、48dp 最小触控目标和高对比度。
- P0：正文可随用户字体设置放大；关键操作不能只靠颜色或手势表达。
- P0：本地书籍内容和阅读数据默认不上传；首版可在无网络权限的情况下完成核心阅读。
- P0：提供隐私说明、开源许可证页、崩溃日志开关；日志不得包含正文、摘录和完整文件路径。
- P1：平板、横屏、折叠屏采用自适应布局；书库可用列表-详情双栏，阅读页可并排显示目录/笔记。
- P1：墨水屏模式关闭大面积动画、阴影和渐变，提供高对比刷新友好的主题。

## 5. 技术方案比较

### 方案 A：原生 Kotlin + Readium + Pdfium（推荐）

优点：遵循 EPUB 标准、已有稳定 Locator/Publication 抽象、支持 EPUB 排版与 TTS、BSD-3-Clause 许可友好、能与 Android 生命周期和本地数据层自然集成。缺点：阅读 Navigator 仍以 Fragment 为主，Compose 需要桥接；PDF 高级能力弱于商业 SDK；CBZ 等格式尚未成熟。

适用：希望先做可靠的 EPUB/PDF 阅读，并保留长期产品化空间的项目。

### 方案 B：Compose + WebView + epub.js，自研 PDF/文本层

优点：前端开发上手快、CSS 定制灵活、EPUB 原型速度快。缺点：Android WebView 版本差异、分页和精确位置映射、文本选择、无障碍、竖排/RTL、安全隔离以及进程恢复都要自己补齐；PDF 又是另一套模型，长期容易形成两个不一致的阅读器。

适用：仅做短期 EPUB 演示或已有成熟 Web 阅读内核的团队。不建议作为本项目主路线。

### 方案 C：多原生引擎聚合（MuPDF/自研解析器/各格式插件）

优点：可覆盖 MOBI、DJVU、CBR、复杂 PDF 等更多格式，高级 PDF 功能上限高。缺点：APK 体积、JNI 稳定性、格式间行为一致性、许可证和商业授权成本最高；每个引擎都需要独立的定位、搜索、批注适配。

适用：已经验证重度多格式需求且有专门内核团队的成熟产品。不适合首版。

## 6. 推荐技术架构

### 6.1 总体结构

```text
UI（Compose）
├── 书库 / 详情 / 搜索 / 设置 / 批注 / 统计
└── Reader Host
    ├── Readium EPUB Navigator Fragment
    ├── Readium PDF Navigator + Pdfium Adapter
    └── TXT Normalizer -> internal Publication
              │
ViewModel + Use Cases（Flow / StateFlow）
              │
Repositories（本地数据唯一事实来源）
├── LibraryRepository
├── PublicationRepository
├── ProgressRepository
├── AnnotationRepository
└── SettingsRepository
              │
Local Data Sources
├── Room（元数据、Locator、书签、批注、统计）
├── Proto DataStore（全局偏好）
├── App-private files（书籍、封面、字体、缓存）
└── Storage Access Framework（仅负责用户授权的导入入口）
```

### 6.2 工程模块

首版采用适度模块化，避免一开始拆成几十个 Gradle 模块：

```text
:app
:core:model
:core:database
:core:files
:core:designsystem
:core:reader-api
:reader:readium
:reader:pdf
:reader:text
:feature:library
:feature:reader
:feature:annotations
:feature:settings
```

`core:reader-api` 定义与具体引擎无关的 `ReaderSession`、`ReaderLocation`、`ReaderCapabilities` 和 `ReaderPreferences`。UI 必须根据 `ReaderCapabilities` 决定是否展示搜索、高亮或 TTS，防止格式能力不一致时出现无效按钮。

### 6.3 推荐技术栈

| 领域 | 选择 |
| --- | --- |
| 语言/UI | Kotlin、Jetpack Compose、Material 3、Material 3 Adaptive |
| 导航/状态 | Navigation Compose、ViewModel、Coroutines、Flow/StateFlow |
| 依赖注入 | Hilt |
| EPUB | Readium Kotlin Toolkit `shared`、`streamer`、`navigator` |
| PDF | Readium PDF abstraction + Pdfium adapter；商业化前复核所有传递依赖许可证 |
| 数据库 | Room + schema export + migration tests |
| 设置 | Proto DataStore |
| 后台任务 | WorkManager（目录增量扫描、封面生成、未来备份/同步） |
| 文件访问 | Storage Access Framework，不申请 MANAGE_EXTERNAL_STORAGE |
| 序列化 | Kotlinx Serialization；原样保存 Readium Locator JSON 并附 schema version |
| 图片 | Coil（封面加载、磁盘/内存缓存） |
| 测试 | JUnit、Turbine、Room in-memory/migration、Compose UI test、Macrobenchmark |

### 6.4 核心数据模型

```text
Book
- id: UUID
- contentHash: SHA-256 (unique)
- title / authors / description / language
- format / mediaType / filePath / fileSize
- coverPath / importedAt / lastOpenedAt
- status: unread | reading | finished

ReadingProgress
- bookId (unique)
- locatorJson
- progression
- updatedAt
- deviceId (为未来同步预留)

Bookmark
- id / bookId / locatorJson / excerpt / createdAt

Annotation
- id / bookId / locatorJson
- selectedText / note / color
- createdAt / updatedAt / deletedAt

ReadingSession
- id / bookId / startedAt / endedAt / activeSeconds

Collection / CollectionBook
- id / name / sortOrder
- collectionId / bookId
```

重排版 EPUB 的页数会随屏幕、字体和边距变化，因此进度主数据必须保存 Readium Locator（`href`、位置片段、progression 等），展示百分比只是派生值。PDF 可在 Locator 中保存页索引和页内位置。

### 6.5 关键数据流

#### 导入

1. 系统文件选择器返回 `content://` URI。
2. ImportUseCase 在 IO 线程流式读取，检查 MIME、扩展名、文件头、大小和可用空间。
3. 写入临时文件，同时计算 SHA-256；解析器验证成功后原子移动到应用私有目录。
4. 提取元数据和封面，事务写入 Room；异步生成缩略图。
5. 若哈希重复，复用已有书籍并提示，不重复占用空间。
6. 任何步骤失败都删除临时文件，数据库不产生半条记录。

#### 阅读与进度

1. UI 只传 `bookId`；PublicationRepository 解析本地文件并创建 ReaderSession。
2. ProgressRepository 从 Room 取 Locator，Navigator 恢复位置。
3. 位置变化经 1–2 秒防抖后写入 Room；进入后台、关闭阅读页和进程生命周期停止时立即刷新。
4. 批注先本地事务落盘，再交给 Navigator Decoration API 呈现。

## 7. 安全、稳定性与异常处理

电子书属于不可信输入，尤其 EPUB/CBZ 是压缩容器，必须按文档解析器而不是可信网页处理。

- 防止 Zip Slip：规范化每个解压路径并验证仍位于目标目录内。
- 防止压缩炸弹：限制压缩包总展开大小、单文件大小、文件数量和压缩比；超过限制中止并提示。
- EPUB Web 内容默认离线：禁用任意外部网络加载、危险 `file://` 访问和不必要的 JavaScript bridge；外链由系统浏览器在用户确认后打开。
- 解析、封面生成和全文提取不在主线程；大文件流式读取，不能一次性载入内存。
- 对损坏目录、缺失 spine、异常字体、超大图片和密码保护 PDF 建立明确错误类型。
- App 启动时清理过期临时文件；崩溃后不自动把未完成导入加入书库。
- 数据库定期做一致性检查；导出和备份使用临时文件 + 原子替换。

## 8. 非功能需求

以下指标以一台 8GB RAM、近三年中端 Android 真机作为基线设备，均应在发布前固化具体型号和测试样本：

- 性能：包含 1,000 本书的本地书库冷启动至可交互 P95 ≤ 2 秒。
- 打开速度：10MB 常规 EPUB 恢复阅读 P95 ≤ 1.5 秒；100MB PDF 首屏 P95 ≤ 3 秒。
- 流畅度：书库滚动与常规翻页不出现持续卡顿；以 Macrobenchmark 记录帧时间。
- 内存：导入和阅读大文件时不按文件大小线性占用内存；500MB PDF/CBZ 不因整体载入导致 OOM。
- 可靠性：自动化破坏性样本不会崩溃；升级迁移、强杀恢复和空间不足是发布阻断用例。
- 数据安全：已确认保存的进度、书签和笔记在强杀、重启、版本升级后零丢失。
- 兼容性：覆盖 API 23、当前主流版本和最新版本；至少测试手机、平板、横屏和一种折叠/大屏模拟器。
- 可访问性：核心导入、书库和阅读流程可由 TalkBack 完成；动态字体和高对比主题通过检查。
- 隐私：MVP 不上传正文、文件名、批注或阅读记录；崩溃报告仅在明确同意后启用并脱敏。

## 9. 测试与质量策略

### 9.1 格式回归书库

建立可合法纳入仓库或 CI 下载的测试样本矩阵：

- EPUB 2、EPUB 3 reflow、EPUB 3 fixed-layout。
- 简体/繁体中文、日文 ruby/竖排、阿拉伯文 RTL、混合字体和 emoji。
- 脚注、内部/外部链接、表格、SVG、MathML、音视频、超大图片、嵌入字体。
- 缺封面、缺元数据、轻微不规范、完全损坏、Zip Slip 和压缩炸弹样本。
- 文本型/扫描型/密码 PDF，长页、横页、旋转页和 500MB 压力样本。
- UTF-8/UTF-16/GB18030 TXT，CRLF/LF、超长行和章节规则异常样本。

### 9.2 自动化层次

- 单元测试：编码探测、章节切分、哈希去重、Locator 序列化、能力矩阵、统计计时。
- 数据测试：Room DAO、事务、所有版本 migration、备份导入冲突。
- 解析集成测试：每本样本提取元数据、目录、首个可读位置和错误类型。
- UI 测试：导入、继续阅读、改字号仍保位、搜索跳转、书签/批注、深色模式。
- 生命周期测试：旋转、分屏、切后台、强杀恢复、低存储、权限/URI 失效。
- 性能测试：冷启动、书库滚动、EPUB 首开/复开、PDF 首屏、大文件导入和内存峰值。
- 人工真机：TalkBack、TTS 音频焦点、音量键、平板双栏和墨水屏刷新体验。

## 10. 交付阶段

### Phase 0：技术验证（建议 1–2 周）

- 接入 Readium，打开代表性的中英文 EPUB 2/3、固定版式和 PDF。
- 验证 Compose + Navigator Fragment 桥接、Locator 恢复、排版偏好和 Decoration API。
- 验证 Pdfium 的首屏性能、许可证、ABI 体积以及 PDF 文字选择/搜索的真实边界。
- 完成 TXT 编码探测与内部 Publication 原型。
- 输出能力矩阵实测结果；任何未通过能力从 MVP UI 中隐藏。

> 判定标准与任务清单详见 `2026-08-10-implementation-plan.md` 第 3 节。Phase 0 不全过不进 MVP；核心必过项是 Compose 桥接（`AndroidFragment` + `FragmentFactory`）+ Locator 进程重建恢复。

> 2026-08 调研补充：Readium 3.3.0 与本方案技术栈逐项对齐（minSdk 23 / compileSdk 36）；Compose 无官方支持，需 `AndroidFragment` + 宿主 Activity `FragmentFactory` 桥接；PDF 文字批注确认不支持（issue #823），且有 off-by-one 进度 bug（issue #811）需实测；实际 Pdfium 依赖为 `marain87:PdfiumAndroid:1.9.8`（Apache 2.0），非 Readium README 所写的 barteksc。

### Phase 1：MVP

> 2026-08 调研后范围收窄：MVP = EPUB + TXT（PDF 降 V1）。第一切片与完整 P0 顺序见 `2026-08-10-implementation-plan.md` 第 2 节。

- EPUB/TXT 导入、去重、书库与书籍详情。
- 阅读恢复、目录、进度、分页/滚动、主题和 EPUB/TXT 排版。
- EPUB/TXT 搜索、书签、高亮、笔记和 Markdown/JSON 导出。
- 数据迁移、安全导入、异常提示、无障碍基础与性能基线。

### Phase 2：V1

- CBZ、目录扫描、标签/书架、批量操作。
- TTS、阅读统计、全量备份恢复、平板/折叠屏和墨水屏优化。
- 根据 Phase 0 实测决定是否加入 PDF 搜索、选择和批注。

### Phase 3：V2

- 由真实用户格式分布决定 FB2、MOBI/AZW3 或 DJVU 的优先级。
- OPDS/WebDAV/Calibre 接入、可选账号和端到端同步。
- 若存在正版内容分发业务，再评估 Readium LCP；不得把 DRM 作为普通格式解析需求处理。

## 11. 发布门槛

MVP 只有同时满足以下条件才可称为完成：

1. 格式回归矩阵中的 P0 样本均能打开，或返回准确、可理解的错误。
2. EPUB/TXT 和 PDF 的能力矩阵与 UI 完全一致，不出现不可用按钮。
3. 强杀、重启和升级迁移后，进度、书签与笔记不丢失。
4. 导入恶意压缩包、损坏文件、超大文件和空间不足场景不崩溃、不产生半成品。
5. 达到已固化基线设备上的启动、首开和内存指标。
6. TalkBack 能完成“导入一本书—开始阅读—添加书签—回到书库”的核心路径。
7. 第三方依赖许可证、隐私清单和数据安全声明完成审核。

## 12. 待确认的产品决策（2026-08-10 grilling 后状态）

以下问题已于 2026-08-10 一场 grilling 中确认，结论与依据见 `2026-08-10-implementation-plan.md` 第 1 节：

1. ~~是否坚持“纯本地、无账号”，还是首版就要求跨设备同步？~~ → **确认：纯本地、无同步**（私人单设备；`DATA-05` 维持 P2/V2）。
2. ~~目标主要是普通手机/平板，还是电子墨水屏设备？~~ → **默认：手机/平板**（e-ink `SET-07` 维持 P1；若实际有墨水屏，开工前纠正）。
3. ~~PDF 是否必须在首版具备文字高亮/批注？~~ → **确认：首版不做**。Readium Pdfium 经调研确认不支持文字选择/高亮/批注（issue #823）；PDF 整体降级到 V1。
4. ~~是否必须支持 MOBI/AZW3？~~ → **确认：暂缓**（默认书库以 EPUB/PDF/TXT 为主）。
5. ~~产品是否计划上架？~~ → **确认：不上架**（私人自用，渠道决策消解）。

## 13. 主要参考资料

- [W3C EPUB 3.3 Recommendation](https://www.w3.org/TR/epub-33/)：EPUB 容器、内容、导航、固定/流式版式、媒体叠加与安全模型。
- [W3C EPUB 3 Overview](https://www.w3.org/TR/epub-overview-33/)：流式/固定布局、全球文字与媒体能力概览。
- [Readium Kotlin Toolkit](https://readium.org/kotlin-toolkit/latest/)：当前格式和功能支持矩阵、最低 Android 要求及模块接入方式。
- [Readium PDF support](https://readium.org/kotlin-toolkit/latest/guides/pdf/)：PDF 必须通过 Pdfium 或商业引擎适配的边界。
- [Readium Mobile GitHub](https://github.com/readium/mobile)：功能概览与 BSD-3-Clause 许可。
- [Android offline-first architecture](https://developer.android.com/topic/architecture/data-layer/offline-first)：本地事实来源、Repository、Flow 和 WorkManager 同步队列。
- [Android data layer](https://developer.android.com/topic/architecture/data-layer)：Repository/data source 分层和本地事实来源建议。
- [Android Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files)：文件/目录授权、持久 URI 权限及 Android 11+ 限制。
- [Android adaptive apps](https://developer.android.com/develop/ui/compose/build-adaptive-apps)：平板、折叠屏、分屏与动态窗口适配。
- [Android TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech)：TTS 生命周期及 Android 11+ manifest 查询要求。
- [Google Play Books](https://play.google.com/store/apps/details?id=com.google.android.apps.books)、[Moon+ Reader](https://play.google.com/store/apps/details?id=com.flyersoft.moonreader)、[Librera](https://librera.mobi/)：竞品功能与格式基线。

---

本方案中的第三方能力基于 2026-08-10 可查到的公开文档；依赖版本、许可证传递关系、Google Play 要求和格式能力应在 Phase 0 锁版本后再次验证。
