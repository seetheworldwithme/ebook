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
| P0（MVP 必做） | 28 | 0 | 0 |
| P1（首个增强版） | 11 | 0 | 0 |
| P2（长期候选） | 3 | 0 | 0 |
| 合计 | 42 | 0 | 0 |

> 当前进度：0 / 42（Phase 0 进行中：P0V-01/02 代码完成待真机回归，详见文末变更记录）。

---

## Phase 0：技术验证（MVP 开工前的门槛）

| ID | 状态 | 验证项 |
| --- | --- | --- |
| P0V-01 | 🚧 | 接入 Readium，打开代表性中英文 EPUB 2/3、固定版式（PDF 降 V1，见 P0V-03）。代码完成（Alice EPUB2 已能打开），待真机验格式覆盖 |
| P0V-02 | 🚧 | Compose + Navigator Fragment 桥接、Locator 恢复、排版偏好、Decoration API。代码完成，待真机验旋转/后台/强杀恢复（核心必过） |
| P0V-03 | ⏸ V1 | PDF 验证整体推后到 V1（MVP 不含 PDF）。已知关键点：文字批注/选择不支持(issue #823)、off-by-one 进度 bug(#811)、16KB 对齐、实际依赖 marain87:1.9.8。详见 implementation-plan §3/§4 |
| P0V-04 | ⬜ | TXT 编码探测与内部 Publication 原型 |
| P0V-05 | ⬜ | 输出能力矩阵实测结果；未通过能力从 MVP UI 中隐藏 |

> Phase 0 未全部 ✅ 前，不要开始 Phase 1（MVP）功能开发。

---

## P0 — MVP 必做

### 导入与文件管理（IMP）

| ID | 状态 | 需求 |
| --- | --- | --- |
| IMP-01 | ⬜ | 系统文档选择器导入单个 / 多个受支持文件（不申请「所有文件访问」） |
| IMP-02 | ⬜ | 接收 `ACTION_VIEW` / `ACTION_SEND` 打开的电子书 |
| IMP-03 | ⬜ | 导入时复制到应用私有目录（删/移原文件后仍可读，失败不留半成品） |
| IMP-04 | ⬜ | 提取标题、作者、封面、格式、文件大小、SHA-256 唯一哈希 |
| IMP-05 | ⬜ | 展示导入进度 / 成功 / 可理解的失败原因 |

### 书库（LIB）

| ID | 状态 | 需求 |
| --- | --- | --- |
| LIB-01 | ⬜ | 网格 / 列表展示封面、书名、作者、进度、最近阅读时间（5000 条仍流畅） |
| LIB-02 | ⬜ | 最近阅读、全部、已读完三个入口 |
| LIB-03 | ⬜ | 按书名 / 作者搜索，按最近阅读 / 导入时间 / 书名排序 |
| LIB-04 | ⬜ | 书籍详情页（元数据、进度、文件信息、批注数量、继续阅读） |

### 阅读器通用能力（READ）

| ID | 状态 | 需求 |
| --- | --- | --- |
| READ-01 | ⬜ | 打开时恢复最近可靠位置（正常退出 / 后台 / 被杀 / 重启均恢复，用 Locator） |
| READ-02 | ⬜ | 目录、章节跳转、当前位置百分比、进度拖动 |
| READ-03 | ⬜ | 点击区域、左右滑动、音量键翻页（音量键可关闭） |
| READ-04 | ⬜ | 分页与纵向滚动两种模式 |
| READ-05 | ⬜ | 书内搜索（PDF 若未通过验证则明确不显示入口） |
| READ-06 | ⬜ | 书签（添加 / 取消 / 列表 / 跳回，重复位置不重复生成） |
| READ-07 | ⬜ | 高亮、笔记、复制、系统分享（PDF 仅在文字选择验证通过后启用） |
| READ-08 | ⬜ | 退出阅读时自动保存位置（防抖保存 + 后台/销毁前强制保存） |

### EPUB / TXT 排版（TYPE）

| ID | 状态 | 需求 |
| --- | --- | --- |
| TYPE-01 | ⬜ | 字号、字体、字重、行高、段距、页边距、对齐（实时预览 + 保位） |
| TYPE-02 | ⬜ | 日间、米黄、夜间主题与跟随系统（夜间无白屏闪烁） |
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

> 实现状态（2026-08-10）：**修复真机点「读内置样本 Alice」闪退（真机回归的前置阻塞）。** 根因：`MainActivity` 原继承 `ComponentActivity`，而 `ReaderScreen` 用 `AndroidFragment<ReaderFragment>`（androidx.fragment.compose）桥接 Readium 的 `EpubNavigatorFragment`；`AndroidFragment` 内部走 `FragmentManager.findFragmentManager()`，要求 Compose 宿主是 `FragmentActivity` 的子类，而 `ComponentActivity` 并非其子类（关系是 `FragmentActivity` → `ComponentActivity`）→ 进入 reader 瞬间抛 `IllegalStateException: View ... is not within a subclass of FragmentActivity` 闪退（welcome 页不碰 Fragment，故主界面正常）。修复：`MainActivity : FragmentActivity()`（`FragmentActivity` 继承自 `ComponentActivity`，`setContent`/`enableEdgeToEdge`/`@AndroidEntryPoint` 行为完全不变，依赖 `androidx.fragment.ktx` 已在）。**测试证据**：`./gradlew assembleDebug` BUILD SUCCESSFUL；`testDebugUnitTest` + `lintDebug` BUILD SUCCESSFUL；真机回归（vivo PD2329，Android）——点「读内置样本 Alice」已正常打开阅读界面，不再闪退。P0V-01/02 仍维持 🚧：闪退已解除，可继续按 `docs/P0V-02-真机验证清单.md` 跑旋转/后台/强杀 Locator 恢复三场景 + EPUB3/中文/FXL 格式覆盖。

> 实现状态（2026-08-10）：**P0V-01/02 代码实现完成，待徐先生真机回归。** Compose 桥接 Readium：`AndroidFragment<ReaderFragment>` + `childFragmentManager.fragmentFactory` 托管 `EpubNavigatorFragment`；进程重建用 `createDummyFactory()` 防 `super.onCreate` 崩 → uiState Ready 后换真实 factory；旋转时 EpubNavigatorFragment 自带 locator SavedState 恢复。Locator 恢复：DataStore + `PersistedLocator(schemaVersion=1)`，`currentLocator` 防抖 1.5s 落盘 + onStop flush + 进程重建重 open + 读 Locator 作 initialLocator。打开流程 `AssetRetriever.retrieve → PublicationOpener.open`（200MB 上限，红线 #4）。实测纠正多处 API：`PublicationOpener`（无 Streamer）、`Try.mapFailure/getOrElse`（getOrElse 是扩展需 import）、`DefaultPublicationParser(pdfFactory=null)`、`createFragmentFactory` 的 configuration 有默认值、`createDummyFactory()` public、`currentLocator` 是 StateFlow 属性（非回调）、`Listener` 只需 override `onExternalLinkActivated`。**测试**：`testDebugUnitTest` **9 passed**（PersistedLocator 往返 5 + SHA-256 4）；`assembleDebug` + `lintDebug` BUILD SUCCESSFUL（APK 49MB，含内置 Alice EPUB2）。**LocatorJsonTest 移除**（Locator.fromJSON 内部用 android.net.Uri，unit test stub 跑不了，真机集成验证）。**仅编译 + 单测，未真机回归**——P0V-01/02 标 🚧，待徐先生按 `docs/P0V-02-真机验证清单.md` 跑旋转/后台/强杀三场景后转 ✅。架构注记：VM 暂绑 Activity scope（与 ReaderFragment activityViewModels 共享），Phase 1 优化 per-book scope；样本 Alice(EPUB2) 内置，EPUB3/中文/FXL 经 SAF 自传。

> 实现状态（2026-08-10）：**Phase 0 第一步（立工程骨架）完成。** `./gradlew assembleDebug` BUILD SUCCESSFUL，APK 45M；`testDebugUnitTest` 通过（无测试类，NO-SOURCE）。三模块（`:app` / `:core:model` / `:reader:readium`）+ Readium 3.3.0 依赖链全通。实测纠正：AGP 9.0 强制内置 Kotlin（移除 kotlin-android 插件 + `builtInKotlin=true`）、Gradle 9.1.0、阿里云镜像、lifecycle 锁 2.10.0、hilt-navigation-compose 降 1.2.0；版本矩阵已回填 implementation-plan §4。下一步 `P0V-01/02`（Compose 桥接 + 打开 EPUB）。**仅编译 + 单测，未真机回归。**

> 实现状态（2026-08-10）：grilling 收尾 + Readium 调研落盘。MVP 范围收窄为 EPUB + TXT，**PDF 整体降级到 V1**（`P0V-03` 标 ⏸）；第 12 节 5 条待确认决策全部确认；开发执行计划与决策写入 `docs/plans/2026-08-10-implementation-plan.md`；design.md 第 1 / 2.2 / 10 / 12 节回写规格级更新。42 项需求 ID 计数不变（READ-05/07 的 PDF 部分随 PDF 推后，EPUB/TXT 部分仍 P0）。

> 实现状态（2026-08-10）：建立 `PROGRESS.md`，从设计文档第 4 / 10 / 11 / 12 节抽取全部 42 项需求 + 5 项 Phase 0 验证 + 7 项发布门槛，初始状态均为 ⬜。为 4.6 节的非表格项分配 SET-01~07 以便逐项跟踪。
