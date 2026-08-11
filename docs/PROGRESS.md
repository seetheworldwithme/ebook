# 实现进度（PROGRESS）

> 本文件只跟踪**实现状态**；需求规格与验收口径以 `docs/plans/2026-08-10-android-ebook-reader-design.md` 为准；开发执行计划与决策见 `docs/plans/2026-08-10-implementation-plan.md`。
> 设计文档保持干净，**不要把实现注记写进设计文档**；注记统一累积在本文末「变更记录」。
>
> 状态符号：
> - ⬜ 未开始
> - 🚧 进行中
> - ✅ 完成（已达到验收口径）
> - ⏸ 推后（注明推到哪个 Phase：P1 / V1 / V2）
> - ⚠️ 阻塞或有问题（注记里写原因）
>
> 硬性规则：每完成一项，把状态改成 ✅，并在文末「变更记录」补 `> 实现状态（日期）：…` 注记。详见 `CLAUDE.md`。

---

## 总览

| 优先级 | 总数 | 已完成 ✅ | 进行中 🚧 |
| --- | --- | --- | --- |
| P0（MVP 必做） | 28 | 12 | 1 |
| P1（首个增强版） | 11 | 0 | 0 |
| P2（长期候选） | 3 | 0 | 0 |
| 合计 | 42 | 12 | 1 |

> 当前进度：12 ✅ / 1 🚧（第一切片 11 项全真机转 ✅ + READ-06 书签真机转 ✅；READ-07 高亮笔记落盘持久化部分真机验过，仅剩复制/分享 + 调色板，故 🚧）。详见文末变更记录。

---

## Phase 0：技术验证（MVP 开工前的门槛）

| ID | 状态 | 验证项 |
| --- | --- | --- |
| P0V-01 | ✅ | Readium 打开代表性 EPUB 全过。真机（vivo PD2329）SAF 自传：Alice(EPUB2,内置) + alice-epub3(EPUB3 英文) + 山海經(EPUB2 中文,正文古文不乱码) + cole(FXL 固定版式) 均打开/翻页/渲染；cole ERR_FAILED 定性为内存压力偶发（清内存后正常）。详见变更记录 |
| P0V-02 | ✅ | Compose 桥接+Locator 恢复+排版偏好+Decoration。真机（vivo PD2329）全过：Locator 恢复三场景（旋转/后台被杀/强杀，19%→19%）；主题日/黄/夜+字号实时生效；Decoration 高亮——长按选中→系统菜单「高亮」→selection.locator→黄色渲染，翻页往返+旋转后保留 |
| P0V-03 | ⏸ V1 | PDF 验证整体推后到 V1（MVP 不含 PDF）。已知关键点：文字批注/选择不支持(issue #823)、off-by-one 进度 bug(#811)、16KB 对齐、实际依赖 marain87:1.9.8。详见 implementation-plan §3/§4 |
| P0V-04 | ✅ | TXT→Readium 接线：TXT 生成标准 EPUB 复用 EPUB 链路。真机（vivo PD2329）《万相之王》能打开/翻页/中文不乱码/夜间主题/横屏旋转恢复；42 单测过 |
| P0V-05 | ✅ | 输出能力矩阵实测结果；未通过能力从 MVP UI 中隐藏。新增 `ReaderCapabilities`（`:core:model`）+ `Publication.toReaderCapabilities()`（`:reader:readium`，`conformsTo`+`isSearchable`）；UI gating 钩子落高亮入口；能力来自 Publication 非扩展名（红线 #2）；5 单测过。详见变更记录 |

> Phase 0 未全部 ✅ 前，不要开始 Phase 1（MVP）功能开发。

---

## P0 — MVP 必做

### 导入与文件管理（IMP）

| ID | 状态 | 需求 |
| --- | --- | --- |
| IMP-01 | ✅ | 系统文档选择器导入单个 / 多个受支持文件（不申请「所有文件访问」）。刀1：SAF 导入 + Room 落库；真机回归 SAF 导入外部 EPUB/TXT 入库通过 |
| IMP-02 | ⬜ | 接收 `ACTION_VIEW` / `ACTION_SEND` 打开的电子书 |
| IMP-03 | ✅ | 导入时复制到应用私有目录（删/移原文件后仍可读，失败不留半成品）。刀1：BookFileImporter 原子复制+SHA-256 去重 + ImportBookUseCase 事务清理；真机回归通过 |
| IMP-04 | ✅ | 提取标题、作者、封面、格式、文件大小、SHA-256 唯一哈希。刀1：ExtractPublicationMetadataUseCase + contentHash 唯一索引；真机回归导入入库通过 |
| IMP-05 | ⬜ | 展示导入进度 / 成功 / 可理解的失败原因 |

### 书库（LIB）

| ID | 状态 | 需求 |
| --- | --- | --- |
| LIB-01 | ✅ | 网格/列表展示封面、书名、作者、进度、最近阅读时间。刀2-C：完整列表 + 网格切换 + Coil 封面 + 进度条(LEFT JOIN) + 相对时间。真机：列表/封面/进度/相对时间 + 网格 2 列切换 全过 |
| LIB-02 | ⬜ | 最近阅读、全部、已读完三个入口 |
| LIB-03 | ✅ | 按书名/作者搜索，按最近阅读/导入时间/书名排序。真机：LIKE 搜索（"Alice"→只剩 Alice）+ 3 种排序（书名升序 Alice→万→山 / 导入时间 / 最近阅读）全过 |
| LIB-04 | ⬜ | 书籍详情页（元数据、进度、文件信息、批注数量、继续阅读） |

### 阅读器通用能力（READ）

| ID | 状态 | 需求 |
| --- | --- | --- |
| READ-01 | ✅ | 打开时恢复最近可靠位置（正常退出 / 后台 / 被杀 / 重启均恢复，用 Locator）。刀1：ReadingProgress(Room) 替代 LocatorStore；真机回归强杀+冷启 16%→16% 恢复通过 |
| READ-02 | ✅ | 目录、章节跳转、当前位置百分比、进度拖动。真机：目录 sheet 章节跳转 + 返回上一位置（按钮出现/消失 + 正文回退）+ 进度 ProgressSheet ◄►微调（0→5→3%）全过。修复顶栏 status bar 触摸 bug（见变更记录） |
| READ-03 | ⬜ | 点击区域、左右滑动、音量键翻页（音量键可关闭） |
| READ-04 | ✅ | 分页与纵向滚动两种模式。刀3：ReaderScrollMode 接 EpubPreferences.scroll + 排版面板「翻页方式」。真机：切模式实时生效 + 双向保位（3%↔3%）+ 滚动连续/分页翻页 + 高亮跨模式存活 + 杀重启保模式。FXL 上 scroll 无效（Readium 行为，已知边界留 V1） |
| READ-05 | ⬜ | 书内搜索（PDF 若未通过验证则明确不显示入口） |
| READ-06 | ✅ | 书签（添加 / 取消 / 列表 / 跳回，重复位置不重复生成）。刀 READ-06/07：BookmarkEntity 表 + toggle 去重（href+progression ε）+ 顶栏 toggle + BookmarkSheet。真机（vivo V2329A）全过：加/取消/去重/列表/跳回（进度回 3%）/isBookmarked 跟位置响应式/杀重启书签+进度不丢 |
| READ-07 | 🚧 | 高亮、笔记、复制、系统分享（PDF 仅在文字选择验证通过后启用）。刀 READ-06/07：高亮落 Annotation 表 + DB 驱动渲染（根治红线 #9 内存态）+ AnnotationSheet + 笔记编辑；**复制/系统分享 + 调色板留下一刀**，故 🚧 |
| READ-08 | ✅ | 退出阅读时自动保存位置（防抖保存 + 后台/销毁前强制保存）。刀1：防抖 1.5s + flushLocator 走 ReadingProgressRepository；真机回归翻页后强杀恢复通过 |

### EPUB / TXT 排版（TYPE）

| ID | 状态 | 需求 |
| --- | --- | --- |
| TYPE-01 | ✅ | 字号、字体、字重、行高、段距、页边距、对齐（实时预览 + 保位）。刀2-A：全维度接 DataStore 持久化 + 排版面板；真机回归字号 130% 杀重启保位 + slider 跟手通过。字重 UI 推后（数据层+映射就绪，留 TYPE-05 一并） |
| TYPE-02 | ✅ | 日间、米黄、夜间主题与跟随系统（夜间无白屏闪烁）。刀2-A：真机回归夜间实时+杀重启保位 + 跟随系统（系统暗色切换正文跟随）+ 夜间无白屏闪烁均通过 |
| TYPE-03 | ⬜ | 屏幕亮度、常亮、方向设置 |
| TYPE-04 | ⬜ | 正确显示中文、日文、RTL、竖排、ruby 注音（建回归样本集） |

### 数据、导出与统计（DATA）

| ID | 状态 | 需求 |
| --- | --- | --- |
| DATA-01 | ⬜ | 导出单本书书签 / 高亮 / 笔记为 Markdown / JSON（含 schema 版本 + Locator + 时间戳） |
| DATA-02 | ⬜ | 本地数据库自动迁移（升级不丢书库 / 进度 / 批注，有迁移测试） |

### 设置、无障碍与隐私（SET）

| ID | 状态 | 需求 |
| --- | --- | --- |
| SET-01 | ⬜ | 简体中文 + 英文；所有用户可见文案进入资源文件 |
| SET-02 | ⬜ | TalkBack、语义标签、焦点顺序、48dp 最小触控目标、高对比度 |
| SET-03 | ⬜ | 正文随用户字体放大；关键操作不只靠颜色 / 手势表达 |
| SET-04 | ⬜ | 本地内容默认不上传；无网络权限可完成核心阅读 |
| SET-05 | ⬜ | 隐私说明、开源许可证页、崩溃日志开关；日志不含正文 / 摘录 / 完整路径 |

---

## P1 — 首个增强版本（V1）

| ID | 状态 | 需求 |
| --- | --- | --- |
| IMP-06 | ⬜ | 用户授权指定目录并增量扫描（SAF 目录授权） |
| IMP-07 | ⬜ | 删除书籍时选「仅移除」或「同时删 App 内副本」 |
| LIB-05 | ⬜ | 收藏、标签、自定义书架 |
| LIB-06 | ⬜ | 批量选择、移动到书架、删除、重新提取元数据 |
| READ-09 | ⬜ | 历史位置前进 / 后退、脚注弹层、外链确认 |
| READ-10 | ⬜ | TTS 朗读（播放/暂停/调速/选声/定时，音频焦点抢占正确暂停） |
| TYPE-05 | ⬜ | 自定义字体导入、按书保存排版偏好 |
| DATA-03 | ⬜ | 全量备份 / 恢复（含数据库、设置、可选书籍文件，恢复前预览冲突） |
| DATA-04 | ⬜ | 阅读时长、日 / 周趋势、连续阅读天数（仅前台实际阅读计时） |
| SET-06 | ⬜ | 平板 / 横屏 / 折叠屏自适应布局（列表-详情双栏） |
| SET-07 | ⬜ | 墨水屏模式（关大面积动画 / 阴影 / 渐变，高对比刷新友好主题） |

---

## P2 — 长期候选（V2）

| ID | 状态 | 需求 |
| --- | --- | --- |
| LIB-07 | ⬜ | 手动编辑元数据与封面（仅影响本地，不改原文件） |
| TYPE-06 | ⬜ | 用户 CSS、高级断词、首行缩进规则（提供一键复位） |
| DATA-05 | ⬜ | 跨设备同步（本地先写 + Outbox，按字段 + 更新时间合并冲突） |

---

## MVP 发布门槛（设计文档第 11 节）

全部 ✅ 后才可称 MVP 完成：

| ID | 状态 | 门槛 |
| --- | --- | --- |
| REL-01 | ⬜ | 格式回归矩阵中的 P0 样本均能打开，或返回准确、可理解的错误 |
| REL-02 | ⬜ | EPUB/TXT 和 PDF 的能力矩阵与 UI 完全一致，不出现不可用按钮 |
| REL-03 | ⬜ | 强杀、重启、升级迁移后，进度 / 书签 / 笔记不丢失 |
| REL-04 | ⬜ | 导入恶意压缩包 / 损坏 / 超大 / 空间不足场景不崩溃、不产生半成品 |
| REL-05 | ⬜ | 达到已固化基线设备上的启动、首开、内存指标 |
| REL-06 | ⬜ | TalkBack 能完成「导入一本书 → 开始阅读 → 添加书签 → 回到书库」 |
| REL-07 | ⬜ | 第三方依赖许可证、隐私清单、数据安全声明完成审核 |

---

## 待确认的产品决策（设计文档第 12 节）

> 不阻塞 Phase 0，但涉及这些方向时**必须先和徐先生确认**再动手：

| 状态 | 决策点 | 结论（2026-08-10 grilling） |
| --- | --- | --- |
| ✅ | 纯本地无账号 vs 跨设备同步 | 纯本地、无同步（`DATA-05` 维持 P2） |
| ⚠️默认 | 手机 / 平板 vs 电子墨水屏 | 手机/平板；e-ink `SET-07` 维持 P1（若实际有墨水屏，开工前纠正） |
| ✅ | PDF 首版文字高亮 / 批注 | 不做；Readium 确认不支持(issue #823)，PDF 降 V1 |
| ✅ | 是否支持 MOBI / AZW3 | 暂缓（书库默认以 EPUB/PDF/TXT 为主） |
| ✅ | 上架渠道 | 不上架（私人自用，决策消解） |

> 5 条已于 2026-08-10 全部确认，结论与依据见 `docs/plans/2026-08-10-implementation-plan.md` 第 1 节。

---

## 变更记录

> 按 `> 实现状态（日期）：…` 风格累积，最新的在最上面。

> 实现状态（2026-08-11）：**真机回归（vivo V2329A）全过：READ-06 书签 转 ✅；READ-07 高亮笔记落盘 持久化部分真机验过（维持 🚧，仅剩复制/分享 + 调色板）。** 覆盖设备上**真实 v1 库**（books/progress 各 3 行，user_version=1）覆盖安装 v2：
> **① v1→v2 迁移保数据（REL-03）**：覆盖安装后启动，`user_version 1→2`、books/reading_progress 各 3 行不丢、bookmarks/annotations 表建好、无崩溃——`MIGRATION_1_2` 真实迁移成功（比单测更强：真实 v1 数据 + 真实 SQLite）。
> **② READ-06 书签全功能**：顶栏 toggle「加书签↔取消书签」+ 底栏「书签 N」计数；**加→DB 落盘 1 行；同位置再点→toggle off，DB 物理删回 0**（「重复位置不重复生成」去重真机验证）；BookmarkSheet 列表（excerpt 空时「无摘录」兜底 + relativeTime「刚刚/11分钟前」）；**点书签跳回→进度从第5章(0%)回到 3%（位置A）+ isBookmarked 重新 true**（响应式跟位置切换，徐先生跳到第5章时顶栏正确变「加书签」未加态）；force-stop 杀进程重开→书签 1 + 进度恢复 3%（持久化跨重启）。
> **③ READ-07 高亮落盘 + DB 驱动渲染（根治红线 #9）**：徐先生手指长按选中「鬼哭」→系统菜单「高亮」→DB annotations 落盘 `selectedText=鬼哭 / color=YELLOW / deletedAt=null`（**selectedText 取自 locator.text.highlight 坐实**）；底栏「笔记 1」；**force-stop 杀进程重开→DB 仍在（1|鬼哭|YELLOW）+ 底栏「笔记 1」+ 正文「鬼哭」黄底重新渲染**（徐先生肉眼确认）——DB 驱动 decorations 派生让高亮跨重启存活，根治了 Phase 0 内存态重启即丢的违规（红线 #9）。
> **④ 笔记编辑**：AnnotationSheet→编辑笔记→AlertDialog 输入→保存→DB `note=goodpassage` + sheet 显示「笔记：goodpassage」预览（updateNote 链路 + relativeTime「12分钟前」）。
> **logcat 干净**：全程无 FATAL/Room/Decoration/SQLite 异常（迁移、CASCADE、软删、Decoration 重新注入均静默正常）。
> **adb 边界**：阅读器 UI 控制（顶栏 toggle / 底栏 sheet 入口 / sheet 内点击 / 进度跳转）+ DB sqlite3 直查 + logcat 全程 adb 自动化；唯「长按选中文字加高亮」必须手指（Readium WebView 手势 + 系统 ActionMode 浮层，adb 驱动不了，与 P0V-02/刀3 记录一致），由徐先生手指完成。设备测试残留：万相之王有一条书签（第1章 3% 位置）+ 一条高亮（第52章「鬼哭」+ 笔记 goodpassage），属自用真实标注，保留。
> **测试证据**：`:app:testDebugUnitTest` 84 passed + `:app:assembleDebug` + `:app:lintDebug` BUILD SUCCESSFUL（编译/lint 详见上一条「代码完成」注记）+ 真机 adb 全链路 + sqlite3 直查 + logcat 干净。**READ-07 维持 🚧**：复制 / 系统分享（canCopyShare）+ 高亮调色板（字段已建 HighlightColor 枚举，UI 暂单黄）推后下一刀，届时一并转 ✅。

> 实现状态（2026-08-11）：**刀 READ-06/07 代码完成：书签（READ-06 全做完）+ 高亮笔记落盘（READ-07 持久化闭环已做，复制/系统分享 + 调色板留下一刀）均 🚧 待真机回归转 ✅。代码 + 单测 + 迁移测试 + 编译 + lint 全绿，未真机回归。** 两件事共用「Locator 派生数据」Room 表，合并一刀摊销共享地基（DAO/Repository/迁移/UI 范式同构）。
> **根治红线 #9（批注先落盘再呈现）**：Phase 0 高亮是纯内存态（`_decorations` + `highlightSeq` 计数 id），本刀改成 **DB 驱动渲染**——`decorations` StateFlow 由 `annotations`（Room observe 回流）派生，打开书时已存高亮自动重现；`addHighlight` 先 `annotationRepository.add`（Room 事务）再回流驱动 `applyDecorations`，不让内存态跑在数据库前面。
> **数据层（照搬 ReadingProgressEntity/Dao/Repository 范式）**：① `:core:model` 加 `HighlightColor{YELLOW,GREEN,BLUE,PINK}` 枚举（默认 YELLOW）；② `BookmarkEntity`(id/bookId FK CASCADE/locatorJson/excerpt/createdAt，无软删——物理删) + `AnnotationEntity`(id/bookId/locatorJson/selectedText/note/color/createdAt/updatedAt/deletedAt 软删) + `@Index(bookId)`；③ `BookTypeConverters` 加 HighlightColor↔name 转换对（兜底 Default）；④ `BookDatabase` v1→**v2** + `MIGRATION_1_2`（CREATE TABLE/INDEX 的 SQL **逐字取自 Room 生成的 2.json**，保证列序/约束名一致）+ DI `.addMigrations`（绝不 fallbackToDestructive）；⑤ `BookmarkDao`/`AnnotationDao`（observe 过滤 deletedAt IS NULL / softDelete / updateNote）+ `BookmarkRepository`/`AnnotationRepository`（注入 `clock`+`idGenerator` 便于单测，Locator 经 `PersistedLocator` 包装）。
> **READ-06 去重口径**：「重复位置不生成重复书签」在 Repository 层按 locator 等价判定——`href` 相同 + `totalProgression` 差 < 1e-3 视为同位置（toggle off），不靠 DB unique 索引（locator JSON 字符串精确相等无法覆盖微小 progression 差）。`isBookmarked` 顶栏图标态复用同一判定。
> **READ-07 selectedText 来源（实测 Readium 3.3.0 jar 坐实）**：`Selection` 类只有 `locator`+`rect` 无 `.text`；选中文本 = `selection.locator.text.highlight`（Locator.Text = before/highlight/after）→ `addHighlight(locator)` 签名不变，selectedText 在 Repository 内取，ReaderFragment 选中回调零改动。
> **ViewModel（DB 驱动）**：`_activeBookId` flatMapLatest 切书重订阅 `bookmarks`/`annotations`；删 `_decorations`/`highlightSeq`；新增 `toggleBookmark`/`removeBookmark`/`removeBookmarksForCurrent`/`jumpToBookmark`/`addHighlight`/`removeAnnotation`/`updateAnnotationNote`/`clearHighlights`(软删全部)/`jumpToAnnotation`/`jumpToLocator`（书签+批注跳转共用，push history 后发 `GoToLocator` 指令）。`ReaderNavCommand` 加 `GoToLocator`，ReaderFragment when 加分支。
> **UI（仿 TocSheet 范式）**：顶栏加书签 toggle IconButton（实心/空心，`canBookmark` gating——**首次消费 canBookmark**，红线 #2）；底栏「高亮 N/清」改为「书签 N」+「笔记 N」两个 TextButton 入口（canBookmark/canHighlight 分别 gating）；`BookmarkSheet`（摘录+相对时间+跳回+删除+清空）、`AnnotationSheet`（色点+选中文字+笔记预览+跳回+编辑+删除+清空）、`NoteEditDialog`（AlertDialog+OutlinedTextField，保存/删除/取消）。颜色 `HighlightColor` 枚举存储但 UI 本刀只出黄色（调色板留后）。
> **测试（30 个新/改，全绿）**：BookmarkDaoTest 6 / AnnotationDaoTest 6 / BookmarkRepositoryTest 8（toggle 去重全分支：首次/同位置撤销/不同 href/容差内/容差外/locator 往返/delete/损坏跳过）/ AnnotationRepositoryTest 7（selectedText 取自 locator.text.highlight/locator 往返/updateNote/softDelete/清空/color 往返/损坏跳过）/ **BookDatabaseMigrationTest 1**（文件级金标准：裸 SQLiteDatabase 造 v1 库+种数据 → Room 用 MIGRATION_1_2 打开触发迁移+**schema 校验** → 旧数据未丢 + 新表可写读 + CASCADE 生效；设备级 MigrationTestHelper 等价测试随 connectedAndroidTest 延后）/ SchemaExportedTest 改（v1 保留 + v2.json 4 表断言）。踩坑：① 测试 helper 误把 progression 塞进 `locations.progression`（代码读的是 `totalProgression`，Readium 里是两个不同字段）→ 改塞 `totalProgression`；② 迁移测试首次失败 `Migration didn't properly handle: books`——手建 v1 库漏了 `books.contentHash` 唯一索引，补 `index_books_contentHash` 后通过（Room 迁移后校验全部表含索引）。
> **顺带修 1 个既有 lint 问题（非本刀引入）**：`TxtEncodingDetector.kt:93` 注释里混进一个字面 BOM 字符（P0V-04 `fec480f` 引入），触发 `ByteOrderMark` lint error 卡住 `lintDebug` 门槛 → 换成文本转义 `﻿`（语义不变），git status 确认该文件非本刀改动。
> **测试证据**：`:app:testDebugUnitTest` **84 passed**（0 fail 0 error 0 skip，含本刀新增 28 + 改 SchemaExportedTest 2）+ `:app:assembleDebug` + `:app:lintDebug` BUILD SUCCESSFUL（仅既有 @ApplicationContext KT-73255 warning）。**仅单测 + 编译 + lint，未真机回归**——READ-06/07 持久化逻辑经迁移测试强校验，待徐先生真机连机验「① 选中文字→高亮→杀重启→高亮仍在 ② 加书签→列表→跳回→同位置再点取消 ③ 笔记编辑保存 ④ 顶栏书签 toggle 实心/空心态」后转 ✅。READ-07 复制/分享 + 调色板推后下一刀。

> 实现状态（2026-08-11）：**真机回归（vivo V2329A）全过：READ-02 / LIB-01 / LIB-03 / READ-04 转 ✅，第一切片（IMP-01/03/04 + LIB-01/03 + READ-01/02/04/08 + TYPE-01/02）11 项功能全闭环。**
> **READ-04（刀3）**：切滚动/分页（排版面板「翻页方式」按钮组）实时生效；**双向保位**（分页→滚动→分页，进度始终 3%——Readium scroll 切换框架内建保 Locator，字节码验证属实）；滚动模式连续滚动 + 分页模式左右翻页（手指验，adb swipe 驱动不了 Readium WebView 手势）；高亮跨模式存活（滚动模式加高亮→切分页→高亮仍在）；杀重启 scroll 持久化（DataStore）+ 进度恢复。
> **READ-02（刀2-B）**：目录 sheet 章节列表 + 点章节跳转（canGoBack 按钮出现）+ 返回上一位置（按钮消失 + 正文回退）+ 进度 ProgressSheet ◄►微调（0→5→3%）。
> **LIB-01/03（刀2-C）**：列表（封面占位/书名/作者/进度/相对时间）+ 网格切换（2 列窄卡）+ 搜索过滤（"Alice"→只剩 Alice）+ 3 种排序（书名升序 Alice→万→山 / 导入时间 / 最近阅读）。
> **修复 1 个真 bug（顶栏 status bar 触摸拦截）**：edge-to-edge 下 `ReaderTopBar` 未加 `statusBarsPadding`，顶栏按钮上半进入 status bar 触摸拦截区——目录/进度/返回按钮 adb tap 上半无效（目录 IconButton 较大、tap 下部勉强可点；进度 TextButton 较窄、几乎整个在拦截区、tap 完全打不开 ProgressSheet）。修复：`ReaderTopBar` 加 `Modifier.statusBarsPadding()`（按钮下移 114px = status bar 高度，完全可点）、`ReaderBottomBar` 加 `navigationBarsPadding()`（对称；vivo 手势导航 inset≈0 无位移，但底栏本就可点）。修复后进度按钮中心 tap 正常展开 sheet。**属刀2-B 引入顶栏的 inset 遗漏，刀2-B 当时仅单测+编译未真机，本次真机首验抓到**——再次印证「未真机不算完成」。
> **READ-01/08 回归**：force-stop 强杀冷启重开 → 进度 3% 恢复、crash 缓冲空。
> **测试证据**：110 单测全绿（ReaderScreen inset 修复不影响单测）+ `:app:assembleDebug` + `:app:lintDebug` BUILD SUCCESSFUL；真机 16 张截图留证 + logcat 无 FATAL/OOM/ANR。adb 自动化覆盖书库/目录/进度/切模式/保位/杀重启；scroll 滚动 + 分页翻页 + 高亮由徐先生手指验（adb 驱动不了 Readium WebView 手势，与 P0V-02 记录一致）。
> **第一切片（自用最小闭环）功能完成**：导入 EPUB/TXT → 书库（列表/网格/搜索/排序）→ 阅读（恢复位置/目录/进度拖动/分页滚动/排版保位/高亮/自动保存）→ 杀重启不丢。下一刀进第一切片之后的 P0（READ-03/05/06/07、TYPE-03/04、DATA-01/02、SET-01~05、IMP-02/05、LIB-02/04 等）。

> 实现状态（2026-08-11）：**刀3 READ-04 代码完成：分页 / 纵向滚动两种模式，🚧 待真机转 ✅。第一切片（IMP-01/03/04 + LIB-01/03 + READ-01/02/04/08 + TYPE-01/02）最后一块补齐。代码 + 单测 + 编译 + lint 全绿，未真机回归。**
> **方案**：把 Readium `EpubPreferences.scroll`（javap 坐实 `getScroll():Boolean`，true=纵向滚动 / null·false=分页）接到已验证的 ReaderTheme/ReaderTextAlign 管线——新增 `enum ReaderScrollMode { PAGINATED, SCROLL }`（:core:model）+ `ReaderTypography.scroll` 字段（null=分页=引擎默认，不强制持久化避免漂移）→ `toEpubPreferences` 映射（PAGINATED→false / SCROLL→true）→ `ReaderTypographyRepository` `KEY_SCROLL`（enum name 存取 + runCatching 防御改名）→ `ReaderViewModel.setScrollMode` → 排版面板「翻页方式」OptionGroup（徐先生选，复用泛型 OptionGroup，selected = typography.scroll ?: PAGINATED）。**单向数据流复用全套，零新机制**。
> **关键：切模式保 Locator 是 Readium 框架内建，ReaderFragment 不改**——Plan 代理反编译 3.3.0 字节码坐实：`scroll` 变化触发 `Event.InvalidateViewPager` → `invalidateResourcePager` 先抓 `currentLocator` → `resetResourcePager` 重建 → 自动 `Navigator.go(locator)` 回位；现有 `preferences.collect { submitPreferences }` 管线自动触发，**无需补 go()**。保位精度=资源内 progression 级（满足 READ-04「保持语义位置」口径）。columnCount 在 scroll=true 时自动 ineffective，不需手动设 1；TXT（转 EPUB）自动生效；Decorations 跨模式存活（pager 重建后重新注入 JS）。
> **已知边界（FXL）**：Readium `overflow` 对非 reflowable（FXL 固定版式，如 cole 样本）强制 `scroll=false`，切滚动无效但不崩。**决策（徐先生拍板）MVP 不做能力 gating**（FXL 非目标场景，网文/小说均 reflowable）；真正的 FXL 能力矩阵细化（`ReaderFormat` 拆 REFLOWABLE_EPUB / FXL_EPUB）留 V1 统一做——现在加 `supportsScrollMode` 布尔是半截子补丁、V1 仍要重构。
> **测试**：`:core:model` **13**（+ReaderTypography copy scroll 1）/ `:reader:readium` **42**（回归）/ `:app` **55**（+TypographyMappings scroll 映射 1 + Repository scroll 往返 2）= **110 单测全绿**（0 fail 0 error 0 skip）；`:app:assembleDebug` + `:app:lintDebug` BUILD SUCCESSFUL（仅既有 @ApplicationContext KT-73255 warning，非本次引入）。
> **待真机验证（🚧→✅ 条件）**：① reflowable EPUB/TXT 切 滚动↔分页 实时生效 ② **切模式后位置保持**（progression 级，READ-04 核心）③ 滚动模式下加高亮 → 切回分页 → 高亮仍在（Decoration 跨模式存活）④ 开滚动 → 杀进程 → 重开 → 仍滚动 + 恢复位置（DataStore + initialPreferences）⑤ TXT（万相之王）切滚动正常（TXT→EPUB 链路自动生效）⑥ scroll + 夜间主题组合不互相干扰 ⑦ cole(FXL) 切滚动无效但不崩（已知边界）。**仅单测 + 编译 + lint，未真机回归**——READ-04 维持 🚧。

> 实现状态（2026-08-11）：**刀2-B/C 代码完成：READ-02（目录+进度拖动）+ LIB-01 完整书库 + LIB-03 搜索排序，三项 🚧 待真机转 ✅。代码 + 单测 + 编译 + lint 全绿，未真机回归。**
> **刀2-B（READ-02）**：① **跳转指令流**——`ReaderNavCommand`（GoToLink/GoToProgression/GoBack sealed）+ VM `Channel(BUFFERED).receiveAsFlow()`，ReaderFragment `bindNavigatorObservers` 加 collect 执行（沿用 preferences/decorations「VM 出事件 → Fragment 执行」范式，navigator 与 publication 分居 Fragment/VM 解耦）。② **目录**——`publication.tableOfContents` 经 `flattenTableOfContents`（递归 children → depth）扁平化成 `TocItem`，TocSheet 按 depth 缩进，点击 `navigator.go(link)`。③ **进度拖动**——`publication.locateProgression(progress)`（suspend 扩展，LocatorServiceKt，javap 验证）→ `navigator.go(locator)`；ProgressSheet（点顶栏「进度 N%」展开——徐先生选的浮层交互）Slider 本地 state 跟手松手跳转 + ◄ ► 微调 ±1%；`progression` StateFlow 派生自 Locator.totalProgression。④ **返回上一位置**——Navigator 无 history（goForward/goBackward 是翻页），VM 自管 `ArrayDeque<Locator>` jumpHistory（深度封顶 20），jumpToLink/jumpToProgression 前 push latestLocator、goBack pop；`canGoBack` gating 顶栏「返回」按钮可见。
> **刀2-C（LIB-01/03）**：⑤ **数据层**——`ReadingProgressEntity.progression` 冗余列（刀1 已预留注释「书库进度条一次查询拿全」）正用上：`BookDao.observeLibraryItems(query)` LEFT JOIN reading_progress + LIKE 搜索（`title/authors LIKE '%kw%'`，authors JSON 子串命中，SQLite 大小写不敏感 + 中文直匹配）；`LibraryItemEntity(@Embedded book + progress)` + `LibraryItem(book, progression)` domain。⑥ **LibraryViewModel**——query/sort/viewMode StateFlow，`combine(query.flatMapLatest{repo.observeLibraryItems}, sort){ sortItems }`；排序抽纯函数 `sortItems`（LAST_OPENED 未读末尾 / IMPORTED / TITLE 大小写不敏感，5000 条内存排序 <5ms）；viewMode/sort 本刀内存态（保位留优化）。⑦ **UI**——LibraryScreen 重写：**列表（默认，徐先生选）**横向卡（Coil `AsyncImage(File(coverPath))` 加载 covers/{bookId}.png + LinearProgressIndicator + 相对时间）+ **网格**（LazyVerticalGrid 2 列封面墙）切换 + 搜索框 + 排序 DropdownMenu（当前项 ✓）；Coil 3.5.0 刀1 已自配（无新依赖）；封面缺失 → surfaceVariant 占位 + 书名首字；`relativeTime`（刚刚/N分钟前/.../yyyy/M/d）。
> **测试**：`:core:model` **12**（+LibraryItem 3）/ `:reader:readium` **42**（回归）/ `:app` **52**（+RelativeTime 8 + TocFlatten 4 + BookDao +5 + SortItems 5）= **106 单测全绿**（0 fail 0 error 0 skip）；`:app:assembleDebug` + `:app:lintDebug` BUILD SUCCESSFUL（ArrowBack / KeyboardArrow* 全改 AutoMirrored，icon deprecated warning 清零；仅剩既有 @ApplicationContext KT-73255）。
> **踩坑**：① **Readium Kotlin 绑定 companion invoke 返回平台可空**——`Url("x")`/`Href("x")` 返回 `Url?`/`Href?`，TocFlattenTest 构造 Link 需 `Href(href)!!`（Link/Href/Url 依赖 android.net.Uri，单测放 app 用 Robolectric sdk=34，同 TypographyMappingsTest 范式）；② **Modifier.padding 不能混用 start/end 与 vertical**（TocSheet 目录项首版 `padding(start=,end=,vertical=)` 编译错）→ 改显式 top/bottom；③ **RelativeTime 未来时间 bug**——单测抓到 `diff<0` 会先命中 `diff<MINUTE` 返回「刚刚」而非空串，加 `if(diff<0)return""` 前置修复；④ **@Embedded 套带 TypeConverter 的 BookEntity**——Room Database 级 converter 对 @Embedded 字段生效（authors List 往返），DAO JOIN 测试验证。**仅单测 + 编译 + lint，未真机回归**——READ-02 / LIB-01 / LIB-03 维持 🚧，待徐先生真机连机验「① 目录 sheet 章节跳转 + 返回上一位置 ② 点进度展开 Slider 拖动/微调跳转 ③ 书库列表封面/进度/相对时间 + 网格切换 + 搜索过滤 + 排序切换 ④ 杀重启进度不丢」后转 ✅。

> 实现状态（2026-08-11）：**真机回归（vivo V2329A）全过：刀1 IMP-01/03/04 + READ-01/08 + 刀2-A TYPE-01/02 转 ✅（LIB-01 维持 🚧 待完整网格/封面）。** 自动化验：冷启动不崩、导入 Alice+翻页+强杀恢复 16%（READ-01/08 Locator 闭环）、字号 130% 杀重启保位（TYPE-01 DataStore）、夜间主题实时+杀重启保位（TYPE-02）。徐先生手指验：SAF 导入外部 EPUB/TXT（中文不乱码）、跟随系统（系统暗色切换正文跟随）、夜间无白屏闪烁、高亮（长按选中→ActionMode→变黄+计数）、slider 拖动跟手。**抓到并修复 1 个真 bug**：排版面板主题 4 按钮（跟随系统/日间/米黄/夜间）单行 Row 放不下、「夜间」被挤出屏外不可见（uiautomator dump 缺「夜间」诊断）→ OptionGroup `Row`→`FlowRow` 自动换行修复，重编译装验夜间按钮可见 + 实时 + 保位。**logcat 干净**：crash buffer 空、无 FATAL、无 Readium Error、无 OOM/ANR；唯一 `hiddenapi AccessibilityNodeInfo.getSelection/setSelection denied`（E 级，Readium classes13.dex，targetSdk 36 + Android 16 文本选择可访问性 API 被拦）——徐先生实测高亮正常、Readium 降级不影响功能，记为库已知行为（REL-06 无障碍时复核 TalkBack 选区播报）。**字重 UI 推后**（数据层+映射就绪，TYPE-01 其余 6 维度 + 保位真机验过，字重 slider 留 TYPE-05 自定义字体一并）。**测试证据**：81 单测 + assembleDebug + lintDebug 全绿 + 真机 6 组截图 + logcat 无错。

> 实现状态（2026-08-11）：**刀2-A 完成：排版偏好持久化保位（TYPE-01/02），🚧 待真机回归转 ✅。** 打通「调排版 → DataStore 落盘 → 重启恢复 → Readium 实时生效」闭环，TYPE-01 七维度（字号/字体/字重/行高/段距/页边距/对齐）+ TYPE-02（日/黄/夜/跟随系统）全部接通。新增 4 文件 + 改 4：① **`:core:model` `ReaderTypography`**（引擎无关 data class，全 nullable；`ReaderTheme`(LIGHT/SEPIA/DARK,**SYSTEM**——Readium 无 SYSTEM 自加) + `ReaderTextAlign`(START/JUSTIFY)；`Default.theme=SYSTEM` 产品默认跟随系统；沿用 ReaderCapabilities 范式不拆 `:core:reader-api`）。② **`:reader:readium` `TypographyMappings.kt`**（`ReaderTypography.toEpubPreferences(isSystemDark)` 扩展，SYSTEM 据系统暗色解析 DARK/LIGHT，沿用 toReaderCapabilities 先例；FontFamily 是 Readium inline value class，String→`FontFamily(it)` 转换）。③ **`:app` `ReaderTypographyRepository`**（包装 DataStore<Preferences>，observe/update 双向映射；**theme 未设默认 SYSTEM**（产品默认集中在 Repository，不随其它字段写入漂移）；enum 用 name 存取 + runCatching 防御改名；纯 JVM 可单测）。④ **`ReaderViewModel` 改造**：注入 repository；`typography`+`_systemDark` 经 `combine`+`stateIn(Eagerly)` 派生 `preferences: StateFlow<EpubPreferences>`（删纯内存 `_preferences` + `syncReadyPreferences`）；各 setter（changeFontSize delta / setFontSize 绝对 / setLineHeight / setPageMargins / setParagraphSpacing / setTextAlign / setFontFamily / setTheme）全走 repository.update（**单向数据流，不乐观更新内存**——避免快速连点时 DataStore 与内存竞态回退）；`setSystemDark` 接收 isSystemInDarkTheme；openPublication 用 preferences.value 作 initialPreferences（首次渲染即正确主题，夜间防白屏链路）。⑤ **`ReaderScreen` 改造**：`isSystemInDarkTheme()` 经 LaunchedEffect 推 setSystemDark；底栏精简（字号±/排版入口/高亮组，主题移入面板）；新增 `TypographySheet`（ModalBottomSheet：字号/行高/段距/页边距 Slider + 对齐/字体/主题按钮组含「跟随系统」）；Slider `remember(value)` 本地 state 跟手 + onValueChangeFinished 松手写一次（避免拖动高频写 DataStore）。**DI**：ReaderModule 加 provideReaderTypographyRepository（复用既有 DataStore）。**测试**：`:core:model:testDebugUnitTest` **9 passed**（ReaderTypographyTest 4：Default=SYSTEM/copy 保字段/enum name 稳定/valueOf 还原 + ReaderCapabilities 5 回归）；`:reader:readium:testDebugUnitTest` **42 passed**（TypographyMappingsTest 移走后纯 JVM 全回归）；`:app:testDebugUnitTest` **30 passed**（TypographyMappingsTest 6 + ReaderTypographyRepositoryTest 5 + 刀1 19 回归）；`:app:assembleDebug` + `:app:lintDebug` BUILD SUCCESSFUL（仅 ArrowBack/@ApplicationContext 2 既有 warning，非本次引入）。合计 core 9 + reader 42 + app 30 = **81 单测全绿**。**踩坑**：① **`EpubPreferences.fontFamily` Kotlin 类型是 `FontFamily?`（Readium inline value class）非 `String?`**——javap 看字段是 String 但构造参数是 FontFamily，映射要 `FontFamily(it)`（`FontFamily("Serif")` 构造可见）。② **`EpubPreferences` init require**：`fontWeight ∈ 0.0..2.5`（**非 CSS 100–900**！Readium 归一化字重，1.0≈normal）、fontSize/pageMargins/paragraphSpacing/letterSpacing/typeScale/wordSpacing ≥0——修正 ReaderTypography 注释 + 测试值（原误记 100-900）。③ **`Theme` 枚举依赖 `android.graphics.Color.parseColor`**（每个 Theme 值带 contentColor/backgroundColor int），纯 JVM unit test 的 android.jar stub 抛 "not mocked"（与 P0V-01 Locator/Uri 同类）→ **TypographyMappingsTest 放 app 模块用 Robolectric**（app 已配 sdk=34），不放 :reader:readium（保持其纯 JVM 不引 Robolectric）。④ **DataStore key 类型是 `Preferences.Key<T>`** 非 `Preferences.DoubleKey/StringKey`（内部嵌套类不公开）。**待真机验证（🚧→✅ 条件）**：排版面板 UI 渲染 + Slider 拖动跟手；字号/行高/段距/页边距/对齐/字体/主题实时生效 + **重启保位**；跟随系统在系统暗色切换时正确解析（Activity 重建 + LaunchedEffect 双保险）；**夜间无白屏闪烁**（initialPreferences 已带 DARK，WebView 容器背景留真机观察）；调排版后**阅读位置保持**（Readium submitPreferences 保位，沿用 P0V-02 已验机制再确认）。**字重 UI 推后**（数据层就绪，UI 留 TYPE-05 自定义字体一并或单独刀）。**仅单测+编译+lint，未真机回归**——TYPE-01/02 维持 🚧。

> 实现状态（2026-08-10）：**Phase 1 刀1 完成：Room 数据层地基 + 导入 + 进度闭环 + bookId 寻址 + 书库列表雏形。单测+编译+lint 全绿，IMP-01/03/04、READ-01/08、LIB-01 标 🚧 待真机回归转 ✅。** 打通「导入 EPUB/TXT → Room 持久化 → 书库可见 → 阅读 → 杀进程不丢进度」闭环骨架。新增 11 文件 + 改 5 + 删 1（LocatorStore）。① **Room 数据层**（`:app/data/db/`）：`BookEntity`（PK=id，uniqueIndex=contentHash）+ `ReadingProgressEntity`（PK=bookId，ForeignKey CASCADE 删书连带删进度）+ `BookTypeConverters`（authors↔JSON、status↔name）+ `BookDatabase`(v1, exportSchema=true) + `BookDao`/`ReadingProgressDao` + `BookMappers`（Entity↔domain）。② **schema export**（红线 #6）：`app/build.gradle.kts` 加 `ksp { arg("room.schemaLocation","$projectDir/schemas"); arg("room.generateKotlin","true") }`，schema 落 `app/schemas/.../BookDatabase/1.json`（已生成确认）。③ **Repository**：`BookRepository`（observeBooks 排序 lastOpenedAt desc nulls last、getByContentHash 去重、markOpened）、`ReadingProgressRepository`（getLocator/save 走 PersistedLocator + totalProgression，clock 注入）。④ **导入事务**（红线 #4 不留半条记录）：`ExtractPublicationMetadataUseCase`（`:reader:readium`，复用 OpenBookUseCase/OpenTxtUseCase `allowUserInteraction=false`，读 `metadata`（title 空→文件名兜底、authors mapNotNull 去空）+ `publication.cover()`，finally close）+ `ImportBookUseCase`（Outcome 三态 Imported/AlreadyExists/Failed + bookIdOrNull 扩展；原子性矩阵：copyWithHash 失败自清→查重短路→extract 失败删书文件→封面失败降级 null→insert 失败回滚书文件+封面；format/mediaType 由扩展名派生不穿透 Readium）。⑤ **reader bookId 改造**：route `reader/{contentHash}`→`reader/{bookId}`；`ReaderViewModel` 注入 BookRepository/ReadingProgressRepository 替换 locatorStore，`openPublication` 从 Book 拿 filePath+contentHash（打开层仍按扩展名分流，P0V-05 论证非能力层），进度恢复 `progressRepository.getLocator` + `markOpened`，落盘 `progressRepository.save`（防抖 1.5s + flushLocator）；删 `LocatorStore`，保留 DataStore provider（刀2 TYPE 用）。⑥ **书库列表**（`MainActivity.LibraryScreen`）：observeBooks 书名列表（书名+作者/format）+ 点击打开 + 空提示 + 导入按钮（SAF）+ 读 Alice；最简，网格/封面/进度/搜索/排序留刀2。**DI**：新建 `DatabaseModule`（BookDatabase/Dao/Repository/ImportBookUseCase @Singleton），`ReaderModule` 加 ExtractPublicationMetadataUseCase provider、删 provideLocatorStore。**关键决策**：Entity 放 `:app/data/db` 不拆 `:core:database`（CLAUDE.md 不拆模块）；authors TypeConverter（domain 保持 List）；locatorJson 复用 `PersistedLocator.toJsonString`（含 schemaVersion）；ReadingProgress PK=bookId + CASCADE；单行 insert 无需 @Transaction（多行事务留刀3+批注）；DataStore→Room 数据迁移**不做**（私人项目测试数据可重建、schema v1 未稳定）。**测试**：`:app:testDebugUnitTest` **19 passed**（BookDaoTest 6：insert/getById/getByContentHash/contentHash 重复抛约束/observeAll 排序/touchOpened/status 往返；ReadingProgressDaoTest 3：upsert REPLACE 覆盖/get null/CASCADE；SchemaExportedTest 1：formatVersion+database.entities；+ 既有 ContentHash 4 + PersistedLocator 5）；`:app:assembleDebug` + `:app:lintDebug` BUILD SUCCESSFUL。合计 model 5 + readium 42 + app 19 = **66 单测全绿**。**踩坑**：① **Robolectric DefaultSdkPicker 失败**（compileSdk 36，Robolectric 4.14.1 最高 35）→ `app/src/test/resources/robolectric.properties` 固定 `sdk=34`（Room DAO 测 SQLite 行为与 SDK 无关）+ `testOptions.unitTests.isIncludeAndroidResources=true`；② **schema classpath 路径**：`sourceSets.test.resources.srcDir(schemas)` 把内容映射到 classpath 根，`SchemaExportedTest` 的 resource 路径要去 `schemas/` 前缀；③ **Room schema 结构**：entities 在 `database` 对象下非根（`root.getJSONObject("database").getJSONArray("entities")`）；④ **Readium metadata.title/name 是 String? 平台类型**（javap 报 getTitle():String 但 Kotlin 绑定可空）→ `m.title?.takeIf{}` + `mapNotNull{it.name}`。**migration 框架**：v1 无 migration；CI 跑 `SchemaExportedTest` 验 schema 导出；`MigrationTestHelper` 骨架 + androidTest assets srcDir 就位（需真机，CI 跳过，v2 加 migration 时复用）。**仅单测 + 编译 + lint，未真机回归**（阅读流程 ReaderViewModel/MainActivity 改动大，LocatorStore→Room 切换）——IMP-01/03/04、READ-01/08、LIB-01 维持 🚧，待徐先生真机连机验「导入 EPUB/TXT → 书库列表可见 → 打开阅读 → 翻页 → 杀进程/旋转 → 恢复进度」全链路后转 ✅。下一步：真机回归通过转 ✅ → 刀2（完整 LIB-01/03 + READ-02 目录 + READ-04 分页滚动 + TYPE-01/02 排版保位）。

> 实现状态（2026-08-10）：**P0V-05 能力矩阵完成，转 ✅；Phase 0 收官，可进 Phase 1（MVP）。** 方案：建立引擎无关的能力矩阵模型，UI 据此 gating（红线 #2），能力来自打开后的 Readium `Publication` 而非扩展名。新增 3 文件 + 改 6 文件：① **`ReaderCapabilities` + `ReaderFormat`**（`:core:model`，纯 JVM data class，与 `Book` 同层）：`from(format, isSearchable)` 唯一能力逻辑入口（EPUB→全能力；PDF→浏览/搜索/书签，canHighlight/canAnnotate/canCopyShare/canTts=false 对应 issue #823）+ `forEpub()`/`forPdf()` 预定义工厂（spec 意图矩阵 + 单测 + V1 兜底）。② **`Publication.toReaderCapabilities()`**（`:reader:readium` 扩展）：用 `conformsTo(Publication.Profile.EPUB/PDF)` 探格式 + `publication.isSearchable` 探搜索（`Publication` 无 format 字段，conformsTo/isSearchable 均标 `@ExperimentalReadiumApi` 已 `@OptIn`，已用 javap 读 readium-shared-3.3.0 classes.jar 坐实 API）；Readium 知识内聚 readium 模块，`:app` 只调扩展。③ 接入：`ReaderViewModel` 加 `capabilities: StateFlow<ReaderCapabilities>`（初值 `forEpub()` 安全默认，`openPublication` onSuccess 里 `_capabilities.value = publication.toReaderCapabilities()`）；`ReaderScreen` collect 后传 `canHighlight` 给 `ReaderBottomBar`，底栏高亮计数/清除组包 `if (canHighlight)`；`ReaderFragment.onCreateActionMode` 按 `viewModel.capabilities.value.canHighlight` 门禁「高亮」菜单项。④ Gradle 接线：`core/model` 加 `testImplementation(libs.junit)`（原无 dependencies 块），`reader/readium` 加 `implementation(project(":core:model"))`。⑤ 产出 `docs/P0V-05-能力矩阵.md`（能力×格式矩阵表 + gating 规则表 + 关键设计说明）。**关键设计点**：能力来自 `Publication` 非扩展名（`Book.format` 当前是未填充死字段，不用）；TXT 经 `OpenTxtPublicationUseCase` 转 EPUB 后 conformsTo(EPUB)=true → 能力天然等同 EPUB，**能力层无需也不应按 `.txt` 扩展名单独算**；`ReaderViewModel.kt:94` 的 `.txt` 分流是**打开层**（TXT 必须先转 EPUB 才能喂 Readium，物理前置），非能力层，不动、不违反红线 #2（红线针对「按扩展名给用户展示按钮」，打开层对用户不可见）；PDF `forPdf()`=浏览/搜索/书签无批注，但 MVP 运行时永不产生（`pdfFactory=null`），V1 真开 PDF 走运行时 `toReaderCapabilities()` 以 `isSearchable` 实测、UI 只看运行时结果（design.md:130「未验证则隐藏」双重保证）。**模块决策**：`ReaderCapabilities` 放 `:core:model`，**未新建 `:core:reader-api`**（design.md:234 说属该模块，但当前不存在且同模块的 ReaderSession/ReaderLocation/ReaderPreferences 一个都没有；CLAUDE.md「不要随手拆模块」，为单类型开模块是 YAGNI；Phase 1 出现第二引擎时连四类型一起搬）。**测试**：`:core:model:testDebugUnitTest` **5 passed / 0 failed**（`ReaderCapabilitiesTest`：forEpub 全能力 / forPdf 浏览+搜索+书签无批注 / isSearchable 探针 EPUB&PDF 生效 / TXT 经 EPUB 推导==forEpub / EPUB 与 PDF 批注类严格不同防回归）；`:reader:readium:testDebugUnitTest` **42 passed / 0 failed**（P0V-04 基线全回归，加 `:core:model` 依赖无破坏）；`:app:testDebugUnitTest` **9 passed / 0 failed**（ContentHash+PersistedLocator 回归）；`:app:assembleDebug` + `:app:lintDebug` **BUILD SUCCESSFUL**（DI 闭环、gating 接入、APK 打包均过）。仅 2 个既有 warning（ArrowBack deprecated、`@ApplicationContext` KT-73255，P0V-04 已记录，非本次引入）。**仅单测 + 编译 + lint，未真机回归**（P0V-05 是模型+文档+gating 钩子，MVP 运行时只产生 EPUB 能力、canHighlight 恒 true，EPUB/TXT 路径行为无变化；gating 钩子在 V1 打开 PDF 时才生效）。**Phase 0 收官**：P0V-01/02/04/05 全 ✅，P0V-03 PDF 按既定决策（implementation-plan §3）推后 V1（非「未通过」，是 MVP 刻意不含 PDF），Phase 0 的 MVP 前置验证项全过，可进 Phase 1。

> 实现状态（2026-08-10）：**P0V-01 EPUB 格式覆盖全过，转 ✅。** 为覆盖 EPUB3/中文/FXL 下 3 类公有领域样本入 `samples/public/`（附 `README.md` 记来源+许可证，红线 #7）：① `alice-epub3-images.epub`（EPUB3.0，[Gutenberg #11](https://www.gutenberg.org/ebooks/11)，公有领域，189KB）；② `chinese-shanhaijing.epub`（EPUB2.0 中文，[Gutenberg #25288](https://www.gutenberg.org/ebooks/25288)，公有领域古籍，112KB，`dc:language=zh`）；③ `fxl-cole-voyage-of-life.epub`（EPUB3.0 固定版式，`rendition:layout=pre-paginated`×4，[IDPF epub3-samples](https://github.com/IDPF/epub3-samples) release 20230704，Thomas Cole 画作公有领域，970KB）。push 到设备 `/sdcard/Download/`，**真机（vivo PD2329）SAF 自传三类全过**：EPUB3 英文——`CHAPTER I. Down the Rabbit-Hole` + 正文段落 + curly quotes（""）正常；中文——《山海經》正文「又東三百里，曰堂庭之山，多白猿，多水玉，多黃金」繁体+生僻字不乱码（`【木炎】` 系 Gutenberg 对超 Unicode 生僻合字的占位描述，非乱码）；FXL——cole 画作整页 + Youth 文字页渲染正常。内置 Alice(EPUB2) 沿用 P0V-02 基线。**发现并定性 cole ERR_FAILED（内存压力偶发，非稳定 bug）**：首次 cole 翻到 youth-text 页 `net::ERR_FAILED`「网页无法打开」+ 满屏 `tile memory limits exceeded`；彼时 `files/books` 累积 6 本（含《万相之王》10MB TXT+EPUB）。force-stop 清内存后单开 cole → ERR_FAILED 消失、画作+文字页均正常；cole zip `unzip -t` 完整性通过、`2a-youth-text.xhtml` 内容/资源引用正常 → **非 Readium 解析 bug，是 FXL 大图（painting 1024px×4）+ 多本累积在中端 vivo 触发 WebView 瓦片显存超限、偶发资源加载失败，清内存可恢复**。记为后续优化项（FXL 大图内存管理 / 多本缓存释放，对应 `REL-05` 内存指标），不阻塞 P0V-01。**踩坑**：① `github.com` 主站被墙（443 超时），`api.github.com` / `raw.githubusercontent.com` / `gutenberg.org` 通，FXL 走 `ghproxy.net` 反代下载；② **adb `input tap` / `swipe` 无法驱动 Readium `EpubNavigatorFragment` paginated 翻页**——合成触摸不满足其 JS 手势判断（tap 被链接页捕获、swipe 不达 fling 阈值），进度死锁 0% 且内容不变，真机翻页必须手指（与 P0V-02「adb 点不中系统浮层」同源）；③ 徐先生首报「山海經报错」实为 cole youth-text 的 ERR_FAILED（dump 的 `readium_package/EPUB/xhtml/2a-youth-text.xhtml` URL 是辨识当前 publication 的依据），山海經本身正常。**仅真机验证（三类格式覆盖打开达成，Locator 恢复沿用 P0V-02 已验机制），P0V-01 转 ✅。**

> 实现状态（2026-08-10）：**P0V-04 TXT→Readium 接线完成，真机回归通过，转 ✅。** 方案：Readium 无原生 TXT 解析器（`AssetRetriever` 嗅探 .txt→`ResourceAsset`，`EpubParser` 第一步要求 `ContainerAsset` 必失败，已核 `EpubParser.kt`/`AssetRetriever.kt` 源码），故把 TXT 章节生成标准 EPUB 3.0，复用 P0V-02 已验证的 EPUB 全链路（`EpubNavigatorFactory`/`EpubNavigatorFragment`/Locator/Decoration/排版偏好全不动）。新增 2 文件 + 改 5 文件：① **`TxtEpubConverter`**（纯 Kotlin `java.util.zip`，零 Readium/Android 依赖，可单测）：`TxtBook`→EPUB ZIP，mimetype STORED 首项 + `META-INF/container.xml` + `OEBPS/content.opf`(metadata/manifest/spine) + `nav.xhtml`(`epub:type=toc`) + `toc.ncx` + 每章 `chapter-{i}.xhtml`（按行段落化、XML 转义、统一 UTF-8 输出）；EPUB 最小结构逐文件核对 Readium `EpubParser`/`PackageDocument`/`NavigationDocumentParser`/`NcxParser` 源码硬性要求（OPF 三子元素、命名空间、nav epub:type、XHTML ns）。② **`OpenTxtPublicationUseCase`**（与 `OpenBookUseCase` 并列、不合并以保 EPUB 路径 100% 不动，`Try<Publication,OpenBookError>` + `Dispatchers.IO`）：parse→convert→缓存 `cacheDir/txt-converted/{contentHash}.epub`（命中即复用、失败删半成品红线#4）→`retrieve(file, MediaType.EPUB)`→`publicationOpener.open`。③ `OpenBookError` 加 `EncodingChoiceNeeded`/`TxtFailed`/`TxtConvertFailed`。④ 接线：`ReaderViewModel.openPublication` 按 `.txt` 后缀分流（EPUB 分支不变）；`BookFileImporter` 按来源扩展名存 `.txt`/`.epub`（SAF `DISPLAY_NAME`，contentHash 仍基于原字节）；`MainActivity` Welcome 加「选择 TXT 文件」按钮（mime `text/plain`）；`ReaderModule` DI 注入 `TxtParser`/`TxtEpubConverter`/`OpenTxtPublicationUseCase`。**范围决策**（徐先生确认）：编码手选 UI 推后 P1（`NeedsEncodingChoice`→可理解错误，留 candidates 接口，P1 加弹窗调 `parseWithEncoding`）；TXT→EPUB 转换**打开时**生成并缓存（磁盘存原 txt 语义干净，首开慢后续秒开，cacheDir 系统可回收）。**测试**：`:reader:readium:testDebugUnitTest` **42 passed / 0 failed / 0 skipped**（含 `TxtEpubConverterTest` 9 个结构断言：mimetype STORED/章节数/container.xml/content.opf manifest+spine+nav 标记/nav epub:type/ncx navPoint/中文 UTF-8 不乱码/XML 转义/单章兜底；`TxtEpubConverterWanxiangTest`《万相之王》10MB/1796 章端到端真跑：章节数一致、mimetype STORED、首章「大夏国」UTF-8 不乱码——本地样本存在故非 skip）；`:app:assembleDebug` + `:app:lintDebug` BUILD SUCCESSFUL（DI 闭环、`MediaType.EPUB` 正确、Hilt 注入无缺、APK 打包成功；仅 2 个既有 warning：`@ApplicationContext` target KT-73255、`ArrowBack` deprecated，均非本次引入）。**真机回归（vivo PD2329，徐先生连机）通过**：选《万相之王》.txt → 能打开（不崩）→ 翻页内容推进 → **中文 GB18030→UTF-8 不乱码** → 夜间主题黑底白字生效 → 横屏旋转 reader 正常重建（Locator 机制复用 P0V-02）。**踩坑**：首次真机白屏，根因 converter `content.opf` manifest item `href="chapter-{i}"` 漏 `.xhtml` 扩展名 → Readium readingOrder 指向无扩展 href 找不到资源 → 读空 → `ReadiumCss.injectHtml` 正则找不到 `<head>` 抛 `No <head> opening tag` → WebView chrome-error 白屏（单测断言只查 `<item id="chap-0"` 未查完整 href 故漏）。修复：href 补 `.xhtml` + 单测加 href 断言 + title 改用首章标题（原误用 hash）；修后删 `cacheDir/txt-converted` 旧缓存重新生成 EPUB（缓存命中会复用 bug 版），Readium 接受，正文正常显示。**P0V-04 转 ✅。** 设备旋转设置测试后已还原。

> 实现状态（2026-08-10）：**P0V-02 Decoration 高亮修复完成，P0V-02 转 ✅。** 根因（前次回归发现）：`addTestHighlight()` 用页级 `currentLocator` 做 Decoration locator，缺精确 DOM 文本范围，Readium 渲染不出。修复（对齐 Readium test-app `VisualReaderFragment`）：① `ReaderViewModel` 删 `addTestHighlight`，加 `addHighlight(locator: Locator)`（seq 计数生成 id）；② `ReaderFragment` 创建 navigator 时在 `EpubNavigatorFragment.Configuration` 设 `selectionActionModeCallback`（`android.view.ActionMode.Callback`，framework 类型，不需 AppCompatActivity——MainActivity 的 FragmentActivity 即可），`onCreateActionMode` 动态 add「高亮」菜单项，`onActionItemClicked` 时 `navigator.currentSelection()?.locator → viewModel.addHighlight → navigator.clearSelection()`；③ `ReaderScreen` 底栏去掉旧的「加高亮」按钮（改为长按选中触发），保留计数/清。装饰渲染经既有 `decorations.collect{ applyDecorations(it,"highlights") }`，selection locator 含精确 DOM 范围故能渲染。**真机回归（vivo PD2329，徐先生手动点高亮）**：长按选词→点「高亮」→该词变黄 + 计数 0→1 ✅；翻页再翻回黄色高亮仍在 + 计数保持 ✅；旋转横屏高亮保留 + 无崩溃 ✅。**P0V-02 全过**：Locator 三场景（核心必过）+ 主题/字号 + Decoration 高亮。踩坑：adb 自动化点不中系统 ActionMode 浮层（坐标不固定 + uiautomator 抓不到浮层），「点高亮」步骤由徐先生手指完成。隐私：selection locator 的正文摘录不入日志。剩余 Phase 0：P0V-01 格式覆盖（EPUB3/中文/FXL 经 SAF 自传）、P0V-04 TXT→Publication 接线（P0V-02 已通过，依赖满足）、P0V-05 能力矩阵。

> 实现状态（2026-08-10）：**P0V-02 真机回归（vivo PD2329，徐先生连机，adb 自动化执行）。** ① 点「读内置样本 Alice」**不再闪退**（FragmentActivity 修复生效），EPUB2 正常打开/左滑翻页/顶栏进度显示。② **Locator 恢复三场景全过**（基准 19%）：旋转横屏→19%✅（pid 9364 不变）；Home + am kill 进程重建（pid 9364→12652）→19%✅；force-stop 强杀冷启重开（pid→13576）→19%✅；全程无崩溃。③ 主题日/黄/夜即时生效（夜间黑底白字、护眼米黄底）；字号 A+/- 响应。④ ⚠️**Decoration 高亮未渲染**：点底部「加高亮」（uiautomator content-desc 确认命中，调 `applyDecorations(decorations,"highlights")`）后翻页再翻回，正文无任何黄色着色（三次截图 + logcat 无报错）。根因疑似 `ReaderViewModel.addTestHighlight()` 用 `latestLocator`（currentLocator 页级定位）作 Decoration locator，缺精确 DOM 文本范围（CFI/range），Readium 无法定位文本涂色 → 静默不渲染。**结论**：P0V-02 维持 🚧——核心必过（Locator 恢复）主体达成，Decoration 高亮渲染项未过，待改用文本选择产生的精确 locator（或定位到具体段落）重验。证据方式：uiautomator dump 抓顶栏「进度 N%」量化对比 + screencap 留证；设备旋转设置测试后已还原。

> 实现状态（2026-08-10）：**P0V-04 TXT 解析核心层完成（编码探测 + 章节切分 + 单测），🚧。** 新增 `:reader:readium` 模块 `txt/` 包 6 个源文件 + 4 个测试文件，纯 Kotlin / `java.nio.charset`，零 Android/Readium/Compose 依赖，可独立单测。**编码探测器**（`TxtEncodingDetector`）：BOM 优先（UTF-8/UTF-16）→ 无 BOM 走严格 UTF-8 整流校验（`CharsetDecoder`+`REPORT`）→ 失败再走 GB18030（GBK 超集）→ 都失败返回 `NeedsUserChoice`（候选 UTF-8/GB18030/GBK/Big5/UTF-16），对齐红线 #5 绝不静默乱码。理论依据并实测验证：GBK lead/trail 字节结构几乎必然违反 UTF-8 续字节规则（《万相之王》第 0 字节 `0xCD` 即失败）。**章节切分器**（`TxtChapterSplitter`）：行首锚定 + `matchEntire` 整行匹配 `第[NUM]+章`（兼容阿拉伯+中文小写/大写数字）+ 序章/楔子/番外等特殊标题；front-matter 单独成「简介」章；无标题纯文本兜底单章；行尾归一化 CRLF/CR/LF。**门面**（`TxtParser`）三态 `Success/NeedsEncodingChoice/Failure`，50MB 硬上限（`FileTooLarge`）+ 空文件（`EmptyFile`）+ 不存在（`FileNotFound`）+ 异常兜底（红线 #4）；`parse()` 只读一次字节复用给 detect/decode。**实测《万相之王》（10.25MB GB18030 无 BOM）**：编码探测→GB18030 正确；章节 1796/1796 召回、**零误切**（正文里的「第三章」全被行首锚定排除）；首章「第1章 我有三个相宫」、正文含「大夏国」、rawLength=10253493；16 个校对版「书名+章节粘连」合并行因行首非「第」漏匹配（已知下限，宁漏不误切）。**踩坑**：Gradle `Test` 任务 workingDir 默认是模块目录 `reader/readium/`，导致本地集成测试相对路径 `samples/local/...` 解析失败→测试永远跳过；已在 `reader/readium/build.gradle.kts` 用 `tasks.withType<Test>().configureEach { workingDir = rootProject.projectDir }` 修复。`file -I` 对 GBK 误报 `unknown-8bit` 已印证不可信，自研探测是必要的。**测试覆盖**：`:reader:readium:testDebugUnitTest` **32 passed / 0 failed / 0 skipped**（EncodingDetector 10 + Splitter 13 + Parser 7 + 《万相之王》本地集成 2）；`:app:assembleDebug` BUILD SUCCESSFUL（依赖图无破坏）。本地集成测试在样本存在时真跑（skipped=0）。**能力边界 / 推后**：TXT → Readium `Publication` 转换、Navigator/Compose 接线本次**不做**，推后到 P0V-02（Compose↔Fragment 桥接进程重建鲁棒性）真机验证通过后——核心层先独立完成并单测，接线依赖那条尚未真机放行的桥。P0V-04 维持 🚧，待接线完成转 ✅。**仅编译 + 单测 + 本地样本集成，未真机回归**（核心层纯 JVM 无 UI，无需真机）。

> 实现状态（2026-08-10）：**修复真机点「读内置样本 Alice」闪退（真机回归的前置阻塞）。** 根因：`MainActivity` 原继承 `ComponentActivity`，而 `ReaderScreen` 用 `AndroidFragment<ReaderFragment>`（androidx.fragment.compose）桥接 Readium 的 `EpubNavigatorFragment`；`AndroidFragment` 内部走 `FragmentManager.findFragmentManager()`，要求 Compose 宿主是 `FragmentActivity` 的子类，而 `ComponentActivity` 并非其子类（关系是 `FragmentActivity` → `ComponentActivity`）→ 进入 reader 瞬间抛 `IllegalStateException: View ... is not within a subclass of FragmentActivity` 闪退（welcome 页不碰 Fragment，故主界面正常）。修复：`MainActivity : FragmentActivity()`（`FragmentActivity` 继承自 `ComponentActivity`，`setContent`/`enableEdgeToEdge`/`@AndroidEntryPoint` 行为完全不变，依赖 `androidx.fragment.ktx` 已在）。**测试证据**：`./gradlew assembleDebug` BUILD SUCCESSFUL；`testDebugUnitTest` + `lintDebug` BUILD SUCCESSFUL；真机回归（vivo PD2329，Android）——点「读内置样本 Alice」已正常打开阅读界面，不再闪退。P0V-01/02 仍维持 🚧：闪退已解除，可继续按 `docs/P0V-02-真机验证清单.md` 跑旋转/后台/强杀 Locator 恢复三场景 + EPUB3/中文/FXL 格式覆盖。

> 实现状态（2026-08-10）：**P0V-01/02 代码实现完成，待徐先生真机回归。** Compose 桥接 Readium：`AndroidFragment<ReaderFragment>` + `childFragmentManager.fragmentFactory` 托管 `EpubNavigatorFragment`；进程重建用 `createDummyFactory()` 防 `super.onCreate` 崩 → uiState Ready 后换真实 factory；旋转时 EpubNavigatorFragment 自带 locator SavedState 恢复。Locator 恢复：DataStore + `PersistedLocator(schemaVersion=1)`，`currentLocator` 防抖 1.5s 落盘 + onStop flush + 进程重建重 open + 读 Locator 作 initialLocator。打开流程 `AssetRetriever.retrieve → PublicationOpener.open`（200MB 上限，红线 #4）。实测纠正多处 API：`PublicationOpener`（无 Streamer）、`Try.mapFailure/getOrElse`（getOrElse 是扩展需 import）、`DefaultPublicationParser(pdfFactory=null)`、`createFragmentFactory` 的 configuration 有默认值、`createDummyFactory()` public、`currentLocator` 是 StateFlow 属性（非回调）、`Listener` 只需 override `onExternalLinkActivated`。**测试**：`testDebugUnitTest` **9 passed**（PersistedLocator 往返 5 + SHA-256 4）；`assembleDebug` + `lintDebug` BUILD SUCCESSFUL（APK 49MB，含内置 Alice EPUB2）。**LocatorJsonTest 移除**（Locator.fromJSON 内部用 android.net.Uri，unit test stub 跑不了，真机集成验证）。**仅编译 + 单测，未真机回归**——P0V-01/02 标 🚧，待徐先生按 `docs/P0V-02-真机验证清单.md` 跑旋转/后台/强杀三场景后转 ✅。架构注记：VM 暂绑 Activity scope（与 ReaderFragment activityViewModels 共享），Phase 1 优化 per-book scope；样本 Alice(EPUB2) 内置，EPUB3/中文/FXL 经 SAF 自传。

> 实现状态（2026-08-10）：**Phase 0 第一步（立工程骨架）完成。** `./gradlew assembleDebug` BUILD SUCCESSFUL，APK 45M；`testDebugUnitTest` 通过（无测试类，NO-SOURCE）。三模块（`:app` / `:core:model` / `:reader:readium`）+ Readium 3.3.0 依赖链全通。实测纠正：AGP 9.0 强制内置 Kotlin（移除 kotlin-android 插件 + `builtInKotlin=true`）、Gradle 9.1.0、阿里云镜像、lifecycle 锁 2.10.0、hilt-navigation-compose 降 1.2.0；版本矩阵已回填 implementation-plan §4。下一步 `P0V-01/02`（Compose 桥接 + 打开 EPUB）。**仅编译 + 单测，未真机回归。**

> 实现状态（2026-08-10）：grilling 收尾 + Readium 调研落盘。MVP 范围收窄为 EPUB + TXT，**PDF 整体降级到 V1**（`P0V-03` 标 ⏸）；第 12 节 5 条待确认决策全部确认；开发执行计划与决策写入 `docs/plans/2026-08-10-implementation-plan.md`；design.md 第 1 / 2.2 / 10 / 12 节回写规格级更新。42 项需求 ID 计数不变（READ-05/07 的 PDF 部分随 PDF 推后，EPUB/TXT 部分仍 P0）。

> 实现状态（2026-08-10）：建立 `PROGRESS.md`，从设计文档第 4 / 10 / 11 / 12 节抽取全部 42 项需求 + 5 项 Phase 0 验证 + 7 项发布门槛，初始状态均为 ⬜。为 4.6 节的非表格项分配 SET-01~07 以便逐项跟踪。
