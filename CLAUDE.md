# CLAUDE.md

本文件给在本仓库工作的 AI（Claude）下达硬性约束。

## 项目简介

- 这是一个 **Android 本地离线优先（local-first）的个人电子书阅读器**：原生 Kotlin + Jetpack Compose + Readium + Room。
- **唯一规格说明**是 `docs/plans/2026-08-10-android-ebook-reader-design.md`。做任何功能前先对照它的需求表（IMP / LIB / READ / TYPE / DATA 系列 ID）和技术方案（第 5、6 节），不要自己另立设计。
- **开发执行计划与决策**见 `docs/plans/2026-08-10-implementation-plan.md`（MVP 范围、第一切片、Phase 0 任务清单与判定标准、Readium 调研结论）。**开工前先读它，按它的顺序执行**。
- **首版不含服务端、账号、在线书城、商业 DRM**；书籍与阅读数据以本地为唯一事实来源。
- 项目目前处于规划阶段（Android 工程尚未 `gradle init`，仓库里还没有 `app/`、`build.gradle.kts`、`gradlew` 等）。下方的测试命令在工程建立后生效；建工程时严格按设计文档 6.2 的模块划分、6.3 的技术栈，不要随手拆几十个 Gradle 模块。
- **实现进度跟踪见 `docs/PROGRESS.md`**（42 项需求 + Phase 0 验证 + 发布门槛的逐项 checklist）；需求规格与验收口径仍以设计文档为准，两者分离，不要把实现注记写进设计文档。
- 正式支持格式：MVP = **EPUB 2/3 + TXT**（PDF 经 2026-08 调研降级 V1，文字批注确认不支持）；V1 = PDF 基础浏览 + CBZ。MOBI/AZW3、FB2、DJVU、CBR 等仅作后续候选，**不要在首版承诺支持**。
- 最低系统：`minSdk 23`、`compileSdk 36`。

## 强制工作流：改完代码必须测试，报错就改到通过

**每完成一次代码修改（无论大小），在宣布完成 / 提交 / 推送之前，必须先运行对应范围的测试命令。** 这是硬性要求，不是建议。

- 测试**通过** → 才可以收尾、提交、推送。
- 测试**报错**（编译错误、类型错误、测试失败、lint 报错）→ **不得**把改动当作完成，必须立刻定位并修复，再次跑测试，直到全绿为止。绝不允许“留个报错下次再说”。
- 如果错误来自本次改动之外（既有代码、环境问题），先说明清楚再决定是否继续，不要静默忽略。
- 修复报错时只改必要的部分，不要顺手重构无关代码。

### 按改动范围选择命令

工程是 Android/Kotlin 的，按你动了什么就跑什么（项目工程尚未初始化，命令在 `gradlew` 存在后生效）：

| 改了什么 | 必跑命令 |
| --- | --- |
| Kotlin 业务逻辑（`:feature:*` / `:core:*` / ViewModel / UseCase / Repository） | `./gradlew testDebugUnitTest` 跑单元测试；改动较大时加 `./gradlew assembleDebug` |
| Compose UI | `./gradlew assembleDebug` 编译；`./gradlew lintDebug` 静态检查；涉及交互流时 `./gradlew connectedDebugAndroidTest`（需真机/模拟器） |
| Room / 数据库 / migration | `./gradlew testDebugUnitTest`（含 Room migration 测试）；改了 schema 必须重新导出 schema 文件并补 migration，不得丢迁移测试 |
| Readium / 解析 / 导入逻辑 | `./gradlew testDebugUnitTest`（格式样本集成测试）；**导入安全用例（Zip Slip / 压缩炸弹 / 损坏文件 / 空间不足）必须覆盖** |
| 全项目（改动较大 / 收尾 / 发布前） | `./gradlew assembleDebug && ./gradlew testDebugUnitTest && ./gradlew lintDebug` |
| 配置了 ktlint / detekt | 额外跑 `./gradlew ktlintCheck detekt` |

示例：

```bash
# 改了业务逻辑
./gradlew testDebugUnitTest

# 收尾 / 发布门槛
./gradlew assembleDebug && ./gradlew testDebugUnitTest && ./gradlew lintDebug
```

### 声明测试结果时必须给证据

- 宣布“完成 / 通过”时，附上**实际运行的命令和关键输出**（贴 `BUILD SUCCESSFUL`、`Tests: N passed` 等），不要只说“测过了”。
- 报错时如实贴出错误，并说明你做了什么修复、为什么这样修。
- 测试**未运行**（跳过了、超时了、嫌慢省略了）= 工作未完成，必须明说，不能伪装成已通过。
- 仪器测试（`connectedAndroidTest`）和真机验证在 CI 跑不了时，必须说明“仅做单元测试 + 编译，未做真机回归”，不得冒充已验证。

## 强制工作流：功能完成后必须在 PROGRESS.md 标注状态

**每实现 / 完成一个功能（导入、书库、阅读恢复、批注、导出、迁移……），在宣布“完成 / 通过”之前，必须同步在 `docs/PROGRESS.md` 对应需求行更新状态。** 这是硬性要求，不是建议，与上面“测试不通过不算完成”同级。

- `docs/PROGRESS.md` 只跟踪实现状态（⬜ / 🚧 / ✅ / ⏸ / ⚠️）；需求规格与验收口径仍以 `docs/plans/2026-08-10-android-ebook-reader-design.md` 为准，**不要把实现注记写进设计文档**，保持设计文档干净。
- 把对应需求行（`IMP-01` / `LIB-01` / `READ-01` / `TYPE-01` / `DATA-01` / `SET-01` …）状态改为 `✅`，并在 `docs/PROGRESS.md` 文末「变更记录」补一条 `> 实现状态（日期）：…` 注记（关键改动、踩坑、测试覆盖、能力边界）。
- 对照 PROGRESS.md 时发现某项其实已实现却没勾（无论是不是这次自己做的），顺手补勾并补注记，不让它继续漏标。
- **有些应该做但本次没做、或留到后续 Phase 的**，状态标 `⏸`，并在注记里写明推后到哪个 Phase（P1 / V1 / V2）及原因。
- Phase 0 技术验证项（`P0V-xx`）、MVP 发布门槛（`REL-xx`，对应设计文档第 11 节）未全部满足前，不得称 MVP 完成。

## 关键技术约束（违反即偏离方案，不要自作主张）

以下来自设计文档，是最容易被忽略、也最容易把项目带偏的点：

1. **阅读进度必须以 Readium Locator 为主数据，不要用“页码”做主键。** 重排版 EPUB 的页数会随屏幕、字号、边距变化；Locator（`href` + 位置片段 + `progression`）才是稳定主数据，百分比 / 页码只是派生展示。PDF 可在 Locator 里存页索引和页内位置。
2. **UI 必须由能力矩阵（`ReaderCapabilities`）驱动，不要对所有格式展示相同按钮。** PDF 的搜索 / 高亮 / TTS 在未通过 Phase 0 验证前，对应入口必须隐藏，不能“按扩展名承诺能力”。格式支持按能力矩阵展示，不只列扩展名——例如 PDF 能翻页加书签 ≠ PDF 能文字高亮。
3. **不申请 `MANAGE_EXTERNAL_STORAGE` / “所有文件访问”权限。** 导入只用系统文档选择器（`ACTION_OPEN_DOCUMENT`）和 Storage Access Framework；导入文件复制到应用私有目录，原文件被删除 / 移动后仍能阅读。
4. **EPUB / CBZ / PDF 是不可信输入，按文档解析器处理，不能当可信网页。** 必须：防 Zip Slip（规范化每个解压路径并校验仍位于目标目录内）、防压缩炸弹（限制展开总大小 / 单文件大小 / 文件数 / 压缩比，超限中止并提示）、EPUB Web 内容默认离线（禁外部网络加载、危险 `file://`、不必要的 JS bridge）、解析和封面生成不在主线程、大文件流式读取。任何导入步骤失败都删临时文件、数据库不留半条记录。
5. **TXT 中文编码**：UTF-8（含 BOM）优先，GB18030 自动探测失败时让用户手选，不要静默用错误编码乱码打开。
6. **Room 必须导出 schema 并写 migration 测试**，升级不丢书库 / 进度 / 书签 / 批注；导出和备份用临时文件 + 原子替换。
7. **依赖许可证**：商业化前复核所有传递依赖许可证（Pdfium / PdfiumAndroid 等），Readium 是 BSD-3-Clause。引入新依赖前在 PR / 注记里写明许可证。
8. **隐私**：MVP 不上传正文、文件名、批注、阅读记录；崩溃报告仅在明确同意后启用并脱敏；核心阅读必须在无网络权限下也能完成。日志不得包含正文、摘录、完整文件路径。
9. **批注先落盘再呈现**：批注先在 Room 事务落盘，再交给 Readium Decoration API 渲染，不要让内存态跑在数据库前面。

## 数据模型对齐（实现时以此为准）

来自设计文档 6.4，建表 / 写 DAO 时字段必须与之对齐，迁移时保持兼容：

- `Book`：id(UUID)、contentHash(SHA-256，唯一)、title/authors/description/language、format/mediaType/filePath/fileSize、coverPath/importedAt/lastOpenedAt、status(unread|reading|finished)。
- `ReadingProgress`：bookId(唯一)、locatorJson、progression、updatedAt、deviceId（为未来同步预留）。
- `Bookmark`：id / bookId / locatorJson / excerpt / createdAt。
- `Annotation`：id / bookId / locatorJson / selectedText / note / color / createdAt / updatedAt / deletedAt。
- `ReadingSession`：id / bookId / startedAt / endedAt / activeSeconds。
- `Collection` / `CollectionBook`：id/name/sortOrder；collectionId/bookId。

Locator JSON 一律原样保存 Readium Locator，并附带 schema version，便于未来迁移与同步。

## 待确认的产品决策（动手前先问我）

这些来自设计文档第 12 节，不会阻塞 Phase 0，但**涉及这些方向时必须先和徐先生确认，不要自己拍板实现**：

1. 纯本地无账号 vs 首版就做跨设备同步。
2. 目标主要是手机 / 平板，还是电子墨水屏（动画与刷新策略差异大）。
3. PDF 是否必须在首版具备文字高亮 / 批注（若必须，应优先比较商业 PDF SDK，而不是假设 Pdfium 已覆盖）。
4. 是否必须支持 MOBI / AZW3（若用户书库以此为主，Phase 0 需增加解析器与许可证专项评估）。
5. 是否上架中国大陆应用市场 / Google Play / 两者（影响 SDK、隐私清单与发布流程）。

## 其余约定

- **跨平台**：本项目是 Android 原生，不存在跨端代码；不要引入 Flutter / RN / 多平台抽象。
- **注释用中文**。代码注释、注记、commit message 全部中文。
- **错误处理**：对加密、损坏、格式不支持、空间不足、密码 PDF 等建立明确错误类型并给用户可理解的提示，不要只显示“打开失败”。
- **能力宣传口径**：对外 / 文案上不要把 PDF 能力和 EPUB 等同宣传；能力以 Phase 0 实测的能力矩阵为准。

## 检测失忆

- 所有的回答开头都以“好的，徐先生”开始。

## Git 规范

- 在 commit 的时候要使用中文，不能使用英文。
