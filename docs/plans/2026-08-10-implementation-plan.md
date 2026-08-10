# 实现计划与开发决策

> 日期：2026-08-10
> 身份：**开发执行权威文档**——开发时按本文件的范围、顺序、决策执行。
> 关系：
> - 需求规格与验收口径 → `2026-08-10-android-ebook-reader-design.md`（规格，保持干净）
> - 实现进度（打勾）→ `../PROGRESS.md`
> - 约束与工作流 → `../../CLAUDE.md`
>
> 本文件只记「做什么、什么顺序、为什么这么定、已知的坑」。
> 来源：2026-08-10 一场 grilling（拷问式规划）+ Readium 现状调研的产出。

---

## 1. 共享理解（grilling 锁定的决策）

| # | 决策 | 性质 |
| --- | --- | --- |
| 1 | 私人自用工具（你自己、你的设备）→ 不上架；第 12 节「上架渠道」决策消解 | ✅ |
| 2 | 无 deadline，长期推进，先到「自用可用」 | ✅ |
| 3 | 流程：定方向 → Phase 0 验证 → 才进 MVP；跳步 = 违反 CLAUDE.md | ✅ |
| 4 | 设备：手机/平板、单设备 → sync 不做（`DATA-05` 维持 P2）、e-ink 推后（`SET-07` 维持 P1） | ⚠️ 默认假设 |
| 5 | 书库：以 EPUB/PDF/TXT 为主，TXT 是核心 → MOBI/AZW3 推后、CBZ 按 V1 | ⚠️ 默认假设 |
| 6 | **PDF 降到 V1（P1）**：MVP 只做 EPUB + TXT；PDF 文字批注经调研确认不支持 | ✅ |
| 7 | Phase 0 直接立正式骨架，先 `:app` + `:core:model` + `:reader:readium` 三模块 | ✅ |
| 8 | MVP 第一切片 = `IMP-01/03/04` + `LIB-01/03` + `READ-01/02/04/08` + `TYPE-01/02`，无额外必须项 | ✅ |
| 9 | 样本：公有领域 + 自造可入库；版权书（含《万相之王》）仅本地、写进 `.gitignore` | ✅ |
| 10 | Phase 0 第一步：立骨架 + Compose 跑通 + Readium 打开一本 EPUB | ✅ |
| 11 | 技术事实：Readium 3.3.0 成熟；Compose 无官方支持需桥接；PDF 是最大坑（见 §4） | ✅ |

> **⚠️ 两条「最小默认假设」**（你未明确给事实）：若实际有多设备接力 / 墨水屏 / 书库大量 MOBI，**开工前一句话纠正即可**，不阻塞当前流程。

---

## 2. MVP 范围

**MVP = EPUB + TXT。**（PDF 降 V1，CBZ 维持 V1）

### 第一切片（自用最小闭环）

跑通「导入一本 EPUB → 读完 → 杀进程不丢进度」：

- `IMP-01/03/04`：文档选择器导入 + 复制到私有目录 + 提取元数据/封面/哈希
- `LIB-01/03`：书库列表 + 搜索/排序
- `READ-01/02/04/08`：恢复位置 + 目录/进度 + 分页/滚动 + 自动保存
- `TYPE-01/02`：字号字体 + 日夜主题

### 第一切片之后的 P0（主线跑稳后接）

`READ-05` 书内搜索、`READ-07` 高亮笔记（EPUB/TXT）、`DATA-01` 导出、`DATA-02` 迁移、`IMP-02/05`、`LIB-02/04`、`READ-03/06`、`TYPE-03/04`、`SET-01~05`。

### 明确推后（不在 MVP）

- PDF 全部 → V1
- CBZ → V1；MOBI/AZW3 → 暂缓
- sync（`DATA-05`）→ P2/V2
- e-ink（`SET-07`）→ P1
- 目录扫描（`IMP-06`）、标签书架（`LIB-05`）→ P1

---

## 3. Phase 0 技术验证

**目标**：在正式工程骨架里杀掉最可能翻车的技术未知，产出「能编译的骨架 + 能力矩阵」。**Phase 0 不全过，不进 MVP。**

### 判定标准

| 项 | 判定标准 | 风险 |
| --- | --- | --- |
| `P0V-01` EPUB 打开 | 中英文 EPUB 2/3 + 固定版式能打开并恢复 Locator | 🟢 低 |
| `P0V-02` Compose 桥接 | `AndroidFragment` + `FragmentFactory` 托管 `EpubNavigatorFragment`；旋转/后台/强杀后 Locator 正确恢复；字号/主题保位；Decoration 高亮能渲染 | 🔴 **核心必过** |
| `P0V-03` PDF（已降级） | 稳定翻页/搜索 + off-by-one 可绕 + 16KB 对齐 + 单 ABI ≤10MB；文字批注直接判不支持、UI 隐藏 | 🔴 高（V1 再做） |
| `P0V-04` TXT | UTF-8/GB18030 探测 + 章节切分（用《万相之王》压）→ 能在 Navigator 读 | 🟡 中 |
| `P0V-05` 能力矩阵 | EPUB=全能力、TXT=全能力、PDF=浏览/搜索/书签（无批注） | — |

### 任务清单

1. 立骨架：`:app` + `:core:model` + `:reader:readium`，Compose + Hilt + Room + Readium 3.3.0 依赖
2. Compose 桥接 Readium：`AndroidFragment` + 宿主 Activity `onCreate` 注册 `FragmentFactory`
3. 用 Readium 打开一本 Gutenberg EPUB，验 Locator 恢复（旋转/后台/强杀）
4. 排版偏好（字号/主题）+ Decoration 高亮验证
5. TXT 编码探测 + 章节切分原型（《万相之王》），转内部 Publication 在 Navigator 读
6. （V1 前置，可缓）PDF 打开/翻页/搜索，验 off-by-one + 16KB + 体积
7. 写能力矩阵，把未通过能力从 UI 隐藏

### 三个必验硬点（调研已暴露）

- (a) Pdfium `currentLocator` off-by-one（[issue #811](https://github.com/readium/kotlin-toolkit/issues/811)）是否影响 Locator 主进度
- (b) marain87 PdfiumAndroid 的 16KB 页对齐 + bundled Pdfium 的 CVE 版本暴露面
- (c) `AndroidFragment` + `FragmentFactory` 在进程重建后 Locator/批注恢复的可靠性

---

## 4. Readium 技术事实（2026-08 调研）

### 🟢 Readium Kotlin Toolkit 总体——成熟可用

- 版本 **3.3.0**，活跃维护（issue #811/#823/#824 都是 2026-07 开的），**BSD-3-Clause**。
- **minSdk 23 / compileSdk 36**、Kotlin 2.3.20、AGP 9.0.0、Compose 1.10.5（BOM 2026.06.01）、Room 2.8.4、KSP 2.3.4、Gradle 9.1.0。（2026-08-10 立骨架时按 Readium 3.3.0 的 `libs.versions.toml` 校准；此前误记的 Kotlin 2.2.20 / AGP 8.11 是 3.1.2 旧值，已纠正。）
- 来源：[readium/kotlin-toolkit](https://github.com/readium/kotlin-toolkit) · [Getting started 3.3.0](https://readium.org/kotlin-toolkit/3.3.0/guides/getting-started/) · [libs.versions.toml](https://github.com/readium/kotlin-toolkit/blob/master/gradle/libs.versions.toml)

### 🟡 Compose 集成——无官方支持，这是最大的工程坑

- 维护者 mickael-menu 原话（[discussion #564](https://github.com/readium/kotlin-toolkit/discussions/564)）：**"Jetpack Compose is not officially supported by Readium yet"**，甚至"不确定有没有人在生产环境用 Compose 跑过"。
- 三个 Visual Navigator（`EpubNavigatorFragment` / `PdfNavigatorFragment` / `ImageNavigatorFragment`）**全是 Fragment**，无 Compose API。
- 桥接方式（社区 + 维护者认可）：用 `AndroidFragment` composable（或 `AndroidView { FragmentContainerView }`）托管，且**必须在宿主 Activity `onCreate()` 注册 `FragmentFactory`**——因为 Navigator 没有空构造器，靠构造器参数注入依赖。
- 来源：[Navigator 指南 3.3.0](https://readium.org/kotlin-toolkit/3.3.0/guides/navigator/navigator/) · [discussion #564](https://github.com/readium/kotlin-toolkit/discussions/564) · [issue #824](https://github.com/readium/kotlin-toolkit/issues/824)

### 🔴 PDF——这套 stack 最大的坑

- **文字选择 / 高亮 / 标注一律不支持**（[issue #823](https://github.com/readium/kotlin-toolkit/issues/823) 原文："lacks native support for adding annotations, text selection handles, or highlights"）。→ 这正是决策 6 把 PDF 降 V1、砍 PDF 文字批注的依据。
- **off-by-one 进度 bug**（[issue #811](https://github.com/readium/kotlin-toolkit/issues/811)：`currentLocator is one page ahead`）→ 直接威胁 CLAUDE.md 红线 #1（Locator 主进度），Phase 0 必须实测能否绕过。
- **实际依赖是 `com.github.marain87:PdfiumAndroid:1.9.8`**（Apache 2.0，仍维护，1.9.8 = 2025-07-17），**不是** Readium adapter README 写的 barteksc（那个 2018 停更）——Readium 文档这块陈旧不准。
- ABI 体积：单 ABI ~8–10MB，4 ABI 全打 ~30–40MB；ABI split / App Bundle 后单设备增量 ~8–10MB。
- 两个发布硬点：**16KB 页对齐**（Android 15+ / Play 要求，[barteksc issue #95](https://github.com/barteksc/PdfiumAndroid/issues/95) 仍有 `libmodft2.so` 未对齐报告）和 **bundled Pdfium 快照 CVE 暴露面**（如 [CVE-2024-7973](https://nvd.nist.gov/vuln/detail/cve-2024-7973)）。
- 来源：[issue #823](https://github.com/readium/kotlin-toolkit/issues/823) · [issue #811](https://github.com/readium/kotlin-toolkit/issues/811) · [adapter-pdfium README](https://github.com/readium/kotlin-toolkit/blob/master/readium/adapters/pdfium/README.md) · [marain87/PdfiumAndroid](https://github.com/marain87/PdfiumAndroid)

### 一句话判断

EPUB/TXT 主线直接上 Readium 没问题；PDF 是最大坑；Compose 必须用 `AndroidFragment` + `FragmentFactory` 桥接。MVP 不承诺「PDF 能和 EPUB 一样批注」，能力矩阵照实标。

### 立骨架实测确认（2026-08-10）

`./gradlew assembleDebug` BUILD SUCCESSFUL，APK 45M。实测中确认/调整的点（接手必读）：

- **AGP 9.0 强制内置 Kotlin**：移除所有模块的 `org.jetbrains.kotlin.android` 插件，`gradle.properties` 加 `android.builtInKotlin=true`；parcelize / serialization / compose-compiler / ksp / hilt 作为独立插件仍正常工作，`kotlin { compilerOptions {} }` 仍可用。
- **Gradle 版本 9.1.0**（AGP 9.0.0 的最低要求；wrapper 已复用 calendar 项目的 jar 并指向 9.1.0）。
- **阿里云镜像**：`settings.gradle.kts` 的 repositories 前置 `maven.aliyun.com` 的 google / central / gradle-plugin / jitpack 镜像，规避国内 `dl.google.com` SSL 握手失败。
- **lifecycle 锁 2.10.0**：`app/build.gradle.kts` 用 `resolutionStrategy.eachDependency` 把 `androidx.lifecycle:*` 全族锁到 2.10.0，避免被传递依赖拉到 2.11.0（后者要求 compileSdk 37 / AGP 9.1.0，与本项目 compileSdk 36 / AGP 9.0.0 冲突）。
- **hilt-navigation-compose 降到 1.2.0**：1.4.0（改名 hilt-lifecycle-viewmodel-compose）要 compileSdk 37 / AGP 9.1.0，降到 1.2.0 兼容。

---

## 5. 样本策略

- **公有领域**书籍走 Project Gutenberg / 古登堡中文计划 → 可合法进仓库。
- **自造小样本**（超长行 TXT、损坏 EPUB、Zip Slip 样本、密码 PDF）→ 自己生成，进仓库。
- **版权书（含《万相之王》）仅本地测试**，绝不进 git 仓库；建工程时把这类路径写进 `.gitignore`（如 `samples/local/`）。
- 《万相之王》.txt 正好压 `P0V-04`：UTF-8/GB18030 探测、超长行、网文章节切分。

---

## 6. 待办的非阻塞确认项

开工 / Phase 0 过程中需要你补的，**不阻塞当前开工**：

1. **你的 Android 底子 + 测试设备**（影响 Phase 0 第一刀切多浅；零基础则第一步是「立工程 + Compose hello world」，不直接啃 Locator）。
2. **PDF off-by-one 实测失败后的兜底**（前置是 Phase 0 `P0V-03` 结果，跑完再定：页索引+容差 / PDF 进一步推后 / 评估商业 PSPDFKit）。
3. 两条 ⚠️ 默认假设的纠正确认（设备形态、书库格式构成）。

---

## 变更记录

> 实现过程中的关键变更累积于此（最新在上）。

> （2026-08-10）建立本文件，落盘 grilling 全部决策 + Readium 调研结论。PDF 从 MVP 降级到 V1；MVP 锁定为 EPUB + TXT；Phase 0 判定标准与任务清单固化。
