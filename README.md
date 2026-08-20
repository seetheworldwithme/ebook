# 电子书阅读器（EbookReader）

一个 **Android 本地离线优先（local-first）** 的个人电子书阅读器：原生 Kotlin + Jetpack Compose + Readium + Room。书籍与阅读数据以本地为唯一事实来源，**无账号、无同步、无书城、不上传任何数据**；核心阅读在无网络权限下也能完成。

> 当前版本 0.1.0（开发中，私人自用，不上架）。
> 唯一需求规格：[docs/plans/2026-08-10-android-ebook-reader-design.md](docs/plans/2026-08-10-android-ebook-reader-design.md)；
> 实现进度逐项账本：[docs/PROGRESS.md](docs/PROGRESS.md)。

## 功能特性

### 导入与文件管理

- 系统文档选择器（SAF）单 / 多文件导入；接收文件管理器「打开方式」与分享（`ACTION_VIEW` / `ACTION_SEND`）
- 导入即复制到应用私有目录，原文件删除 / 移动后仍可阅读；SHA-256 内容去重，失败不留半条记录
- 目录授权 + 增量扫描：授权一个目录后自动递归发现新书，冷启动自动扫描
- 恶意文件防护：Zip Slip 路径校验、压缩炸弹限额（总大小 / 单文件 / 文件数 / 压缩比）、损坏文件可理解报错

### 书库

- 列表 / 封面网格双视图；最近阅读、全部、已读完三入口
- 按书名 / 作者搜索，按最近阅读 / 导入时间 / 书名排序
- 收藏与自定义书架（标签即书架）；批量选择、移动到书架、批量删除
- 书籍详情页：元数据、进度、文件信息、书签 / 笔记数量、阅读时长统计

### 阅读器

- 进度以 Readium Locator 为稳定主数据：旋转、后台被杀、强杀、重启后位置精确恢复
- 目录跳转、进度百分比与拖动、浏览器式前进 / 后退历史双栈
- 点击区域（左右边缘）、左右滑动、音量键翻页（可关闭）；分页 / 纵向滚动双模式
- 书签（去重）、高亮（四色）、笔记、复制、系统分享；批注先落库再渲染
- 书内搜索：上下文预览 + 命中词高亮 + 分批加载
- 脚注弹层（EPUB3 `epub:type`）、内链跳转、外链打开前确认
- TTS 朗读：播放 / 暂停 / 语速 / 发音人 / 定时停止，当前句高亮自动跟翻

### 排版

- 字号、字体、字重、行高、段距、页边距、对齐，实时生效并保位
- 日间 / 米黄 / 夜间 / 跟随系统主题，夜间无白屏闪烁
- 预置[霞鹜文楷·屏幕阅读版](https://github.com/lxgw/LxgwWenKai-Screen)（OFL-1.1）
- 按书保存排版偏好：覆盖压全局、未动字段跟全局，可一键恢复默认
- 屏幕亮度、常亮、方向锁定；正文跟随系统字体放大
- 中文 / 日文 / RTL / 竖排 / ruby 注音按 ReadiumCSS 正确渲染（有回归样本集）

### 数据、导出与统计

- 单本书签 / 高亮 / 笔记导出为 Markdown 或 JSON（含 schema 版本 + Locator + 时间戳）
- 全量备份 / 恢复：ZIP 打包数据库 + 设置 + 书籍文件，恢复前预览冲突，三策略（跳过 / 覆盖 / 合并）
- 阅读统计：今日 / 本周时长、7 天趋势、连续阅读天数，仅前台实际阅读计时
- Room 全链路 migration 测试，升级不丢书库 / 进度 / 批注

### 设置与无障碍

- 简体中文 + 英文双语，全部文案走资源文件
- 语义标签、48dp 触控目标、关键操作不单靠颜色 / 手势表达
- 隐私说明页、开源许可证页；崩溃日志默认关闭、本地脱敏存储
- 日志不包含正文、摘录、完整文件路径

## 格式支持与能力矩阵

UI 由能力矩阵（`ReaderCapabilities`，来自 Publication 实测）驱动，**不同格式只展示真实可用的入口**，不按扩展名承诺能力：

| 能力 | EPUB 2/3 | TXT | PDF（V1） | CBZ（V1） |
| --- | --- | --- | --- | --- |
| 打开 / 翻页 / 进度恢复 | ✓ | ✓ | ✓ | ✓ |
| 目录跳转 | ✓ | ✓（按长度分章） | ✓（outline） | — |
| 书签 | ✓ | ✓ | ✓ | ✓ |
| 搜索 | ✓ | ✓ | — | — |
| 高亮 / 笔记 / 分享 | ✓ | ✓ | —（Readium 不支持，见 issue #823） | — |
| TTS 朗读 | ✓ | ✓ | — | — |
| 排版定制 / 主题 | ✓ | ✓ | — | — |
| 缩放 | 重排自适应 | 重排自适应 | ✓ | ✓ |

- TXT 经「转标准 EPUB」复用 EPUB 全链路：UTF-8（含 BOM）优先，GB18030 自动探测失败时让用户手选编码，不静默乱码打开。
- MOBI / AZW3、FB2、DJVU、CBR 等仅为后续候选，当前不支持。
- 密码 / 加密 PDF、损坏文件给出明确错误类型与可理解提示。

## 隐私与离线红线

- **不申请 `INTERNET` 与「所有文件访问」（`MANAGE_EXTERNAL_STORAGE`）权限**，导入只走系统文档选择器与 SAF；有固化测试（`OfflineGuaranteeTest`）保证发布 Manifest 不含网络权限、源码无直接网络调用。
- 正文、文件名、批注、阅读记录一律不上传；崩溃报告仅在明确同意后启用并脱敏。
- EPUB / CBZ / PDF 按不可信文档输入处理：解压防护见上，解析与封面生成不在主线程，EPUB Web 内容默认离线（禁外部加载与危险 `file://`）。

## 技术栈与模块

| 项 | 选择 |
| --- | --- |
| 语言 / UI | Kotlin + Jetpack Compose（Material 3） |
| 阅读引擎 | [Readium kotlin-toolkit](https://github.com/readium/kotlin-toolkit) 3.3.0（EPUB Navigator + media-tts）+ pdfium（PDF） |
| 数据 | Room（schema 导出 + migration 测试）、DataStore、kotlinx.serialization |
| 依赖注入 | Hilt |
| 最低 / 目标 SDK | minSdk 23 / targetSdk 36（compileSdk 36，锁定 AGP 9.0.0 与 Readium 3.3.0 一致） |

```
:app             # 入口、导航、书库 / 阅读 / 设置各 Screen 与 ViewModel
:core:model      # 纯领域模型（Book、ReaderCapabilities、排版与显示设置等）
:reader:readium  # Readium 封装（Navigator 三分流、能力矩阵、偏好映射）
```

## 构建与测试

要求 JDK 17+、Android SDK 36。仓库已配国内 Maven 镜像（阿里云 + jitpack），无需额外代理。

```bash
./gradlew assembleDebug        # 编译
./gradlew testDebugUnitTest    # 单元测试（387 个，含格式样本 / 导入安全 / migration 链）
./gradlew lintDebug            # 静态检查
./gradlew connectedDebugAndroidTest   # 仪器测试（migration / 样本开书冒烟，需真机或模拟器）
```

发布 APK 产物在 `releases/android/`。每项功能以 **真机回归通过** 为完成门槛（标准机 vivo V2329A），单元测试全绿只是必要条件。

## 文档索引

- [需求规格（唯一口径）](docs/plans/2026-08-10-android-ebook-reader-design.md) — 42 项需求表、技术方案、发布门槛
- [实现计划](docs/plans/2026-08-10-implementation-plan.md) — MVP 范围、Phase 0 验证结论、Readium 调研
- [PROGRESS.md](docs/PROGRESS.md) — 逐项实现状态账本与变更记录
- [能力矩阵实测](docs/P0V-05-能力矩阵.md)、[许可证与隐私审核](docs/REL-07-许可证隐私数据安全审核.md)
- `website/` — 配套官网（Next.js），与 App 仓库同仓维护

## 依赖与许可证

第三方依赖许可证已在 App 内「设置 → 开源许可证」页与应用侧审核文档中列明。关键项：Readium（BSD-3-Clause）、pdfium 系（Apache-2.0）、霞鹜文楷（OFL-1.1）、kotlinx / Compose 系（Apache-2.0）、desugar（GPL + Classpath Exception）。本项目为个人自用项目，未声明开源许可证。
