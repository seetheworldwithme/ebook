# REL-07 第三方依赖许可证 / 隐私清单 / 数据安全声明审核

> 对应 `docs/plans/2026-08-10-android-ebook-reader-design.md` 第 11.7 节发布门槛：
> **「第三方依赖许可证、隐私清单和数据安全声明完成审核。」**
> 本文件是 REL-07 的**审核证据**；需求规格与验收口径仍以设计文档为准。
>
> 审核日期：2026-08-13　审核范围：MVP（EPUB + TXT，纯本地）　审核人：AI（Claude）+ 徐先生复核

---

## 0. 审核方法与基线

| 项 | 说明 |
| --- | --- |
| 依赖树来源 | `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`（随 APK 发布的运行时依赖，不计测试/编译期） |
| 去重粒度 | `group:name`（忽略版本解析噪音），release runtime 共 **234** 个去重构件 |
| 在 App 内披露载体 | `app/src/main/java/com/xuziyue/ebook/settings/LicenseData.kt`（手写清单，渲染于「设置 → 开源许可证」页） |
| 许可证全文 | `app/src/main/assets/legal/*.txt`（Apache-2.0 / BSD-3-Clause / GPL-CPE / MIT） |
| 防回退测试 | `app/src/test/java/com/xuziyue/ebook/LicenseDataAuditTest.kt`（5 项，固化「已识别库必须在清单 + 全文 asset 存在 + 4 族许可证齐全」） |
| 隐私事实来源 | 源码 Manifest + 合并后 Manifest（AGP 产物）+ `OfflineGuaranteeTest`（4 项）+ `CrashLogSanitizerTest`（8 项） |

> **为何手写而非用 play-services-oss-licenses**：该库会引入网络/Play 相关依赖，违反 SET-04「无 INTERNET 权限下完成核心阅读」红线。手写清单零网络、离线渲染。

---

## 1. 第三方依赖许可证审核

### 1.1 审核结果汇总

| 许可证族 | 代表库 | 数量级 | 与本工程兼容性 |
| --- | --- | --- | --- |
| **Apache License 2.0** | AndroidX 全家、Compose、Hilt/Dagger、Coil、Room、Coroutines、Kotlin stdlib、Timber、media3、Guava、Okio… | 绝大多数 | ✅ 宽松，允许商用 / 闭源 / 修改，仅需保留版权声明 |
| **BSD 3-Clause** | Readium Kotlin Toolkit | 1（核心） | ✅ 宽松，兼容 Apache 2.0 |
| **MIT** | jsoup（Readium 传递依赖） | 1 | ✅ 最宽松，兼容一切 |
| **GPL v2 + Classpath Exception** | desugar_jdk_libs（Java 8+ API 脱糖） | 1 | ✅ **Classpath 例外**明确允许作为库链接而不传染整个 App；Google 官方正是为此用途采用此许可 |

**结论：运行时依赖树不存在 LGPL / AGPL / GPL（无例外）等强 copyleft 许可**，全部为宽松许可或「带 Classpath 例外」的 GPL，**无许可证冲突**。即便未来上架应用市场，上述组合亦合规；本项目按决策 #5（2026-08-10）**暂不上架、私人自用**，许可合规裕度充足。

### 1.2 已披露库清单（LicenseData，26 条）

> 按名称排序；`传递` 列 = 非本工程直接声明，由 Readium / Coil 等带入。

| 库 | 版本 | 许可证 | 传递 | 来源 |
| --- | --- | --- | --- | --- |
| Accompanist DrawablePainter | 0.37.3 | Apache 2.0 | ✓ | Coil 传递 |
| AndroidPdfViewer (marain87) | 3.2.8 | Apache 2.0 | ✓ | Readium PDF adapter 传递 |
| AndroidX AppCompat | 1.7.1 | Apache 2.0 | ✓ | Readium navigator 传递 |
| AndroidX Core KTX | 1.18.0 | Apache 2.0 | | 直连 |
| AndroidX DataStore Preferences | 1.1.7 | Apache 2.0 | | 直连 |
| AndroidX Fragment | 1.8.9 | Apache 2.0 | | 直连 |
| AndroidX Lifecycle | 2.10.0 | Apache 2.0 | | 直连 |
| AndroidX Media3 (ExoPlayer) | 1.10.0 | Apache 2.0 | ✓ | Readium navigator 传递（EPUB 音视频） |
| AndroidX Navigation Compose | 2.9.7 | Apache 2.0 | | 直连 |
| AndroidX Room | 2.8.4 | Apache 2.0 | | 直连 |
| AndroidX WebKit | 1.15.0 | Apache 2.0 | | 直连 |
| Coil | 3.5.0 | Apache 2.0 | | 直连（封面加载） |
| desugar_jdk_libs | 2.1.5 | GPL v2 + CPE | | 直连（Java 8+ 脱糖） |
| Guava | 33.3.1-android | Apache 2.0 | ✓ | media3 传递 |
| Hilt / Dagger | 2.60.1 | Apache 2.0 | | 直连 |
| Hilt Navigation Compose | 1.2.0 | Apache 2.0 | | 直连 |
| Jetpack Compose | BOM 2026.06.01 | Apache 2.0 | | 直连 |
| **jsoup** | **1.22.2** | **MIT** | **✓** | **Readium 传递（HTML 解析）— 本次审核新增披露** |
| Kotlin Coroutines | 1.10.2 | Apache 2.0 | | 直连 |
| Kotlinx Datetime | 0.7.1 | Apache 2.0 | | 直连 |
| Kotlinx Serialization | 1.10.0 | Apache 2.0 | | 直连 |
| koi (mcxiaoke) | 0.5.5 | Apache 2.0 | ✓ | Readium streamer 传递 |
| Okio | 3.17.0 | Apache 2.0 | ✓ | Coil / DataStore 传递 |
| **PdfiumAndroid (marain87)** | **1.9.8** | Apache 2.0 | **✓** | **Readium PDF adapter 传递（Pdfium JNI 封装）— 本次审核单列** |
| Readium Kotlin Toolkit | 3.3.0 | BSD 3-Clause | | 直连（核心） |
| Timber | 5.0.1 | Apache 2.0 | | 直连（日志） |

### 1.3 本次审核修复的缺口（关键）

SET-05 首版手写清单 19 条，本次审核核对真实依赖树后发现并补齐：

1. **jsoup 1.22.2（MIT）漏披露** ← **最关键**。Readium `readium-shared` 传递依赖 `org.jsoup:jsoup`（HTML 解析）。MIT 是**新的许可证族**（既有的 Apache / BSD / GPL-CPE 三族不覆盖），必须独立披露全文。
   - 修复：新增 `License.MIT` 枚举 + `assets/legal/mit.txt`（jsoup 版权声明）+ LicenseData 条目。
2. **PdfiumAndroid 1.9.8 单列**。红线 #7 与设计文档 §10 均点名「实际依赖 `marain87:PdfiumAndroid:1.9.8`」，原清单仅有其上层 `AndroidPdfViewer 3.2.8`；本次把原生 JNI 封装库单列（marain87 fork，Apache 2.0）。
3. **实质性传递依赖补登**（均为 Apache 2.0，但为「覆盖传递依赖」红线 #7 充分披露）：Media3/ExoPlayer 1.10.0（EPUB 音视频，体积可观）、Guava 33.3.1、Okio 3.17.0、Accompanist DrawablePainter 0.37.3、AppCompat 1.7.1。

### 1.4 同族归并与 de-minimis（不入在 App 清单，在此登记）

下列构件随 APK 发布，但**按家族归并**或**体积极小**，不在 App 内许可证页逐条列出（避免噪音），在此透明登记：

- **AndroidX 同族传递子模块**（数十个，均 Apache 2.0）：`annotation` / `collection` / `core` / `core-viewtree` / `savedstate` / `versionedparcelable` / `concurrent-futures` / `interpolator` / `tracing` / `profileinstaller` / `startup` / `navigationevent` / `arch.core` / `cursoradapter` / `customview` / `drawerlayout` / `viewpager` / `swiperefreshlayout` / `coordinatorlayout` / `transition` / `vectordrawable` / `documentfile` / `sqlite` / `window` / `emoji2` / `exifinterface` / `constraintlayout` / `legacy-support-*` 等——已由「AndroidX Core/Fragment/Lifecycle/…」家族条目覆盖披露。
- **Kotlin 工具链**（Apache 2.0）：`kotlin-stdlib` / `kotlin-stdlib-common` / `kotlin-reflect` / `kotlin-parcelize-runtime`。
- **纯注解 / 桥接规范库**（体积极小，仅若干注解类）：
  - `com.google.code.findbugs:jsr305`（POM 标 BSD，实际为 JSR-305 注解草案，业界普遍视为 ~公有领域；不随附 BSD 全文以免误导）。
  - `org.jspecify:jspecify`（Apache 2.0，空注解规范）、`javax.inject` / `jakarta.inject`（Apache 2.0，依赖注入注解）。
  - `com.google.guava:listenablefuture:9999.0-empty`（空桩，仅规避与 Guava 冲突，无实质代码）。
  - `org.jetbrains:annotations`（Apache 2.0，@Nullable 等注解）。

> 如未来上架要求逐构件披露，可用 `oss-licenses-plugin`（仅**生成期**扫描 POM，运行期仍手写渲染，不引入网络库）补全；当前私人自用无需。

### 1.5 许可证审核结论

✅ **全部随 APK 发布的第三方库均已完成许可证识别与披露**；运行时无强 copyleft 冲突；MIT 新族已补全文；传递依赖已实质覆盖；防回退测试固化。**第一项（第三方依赖许可证）审核通过。**

---

## 2. 隐私清单

> 对齐红线 #8：**MVP 不上传正文、文件名、批注、阅读记录；崩溃报告仅在明确同意后启用并脱敏；核心阅读必须在无网络权限下也能完成。日志不得包含正文、摘录、完整文件路径。**

### 2.1 数据清单（采集 / 存储）

| 数据类别 | 是否采集 | 存储位置 | 是否上传 | 留存 / 删除 |
| --- | --- | --- | --- | --- |
| 书籍文件（EPUB/TXT 原文） | 用户主动导入 | 应用私有目录 `filesDir/books/` | 否（零网络） | 用户删除书即删；卸载随应用清除 |
| 书籍元数据（标题/作者/封面/SHA-256） | 导入时本地提取 | Room 数据库（`books` 表） | 否 | 同上 |
| 阅读进度（Readium Locator JSON） | 阅读时本地生成 | Room（`reading_progress` 表） | 否 | 同上 |
| 书签 / 高亮 / 笔记 | 用户主动创建 | Room（`bookmarks` / `annotations` 表） | 否 | 同上 |
| 排版 / 显示偏好 | 用户本地设置 | DataStore（`reader_settings.preferences_pb`） | 否 | 卸载清除 |
| 崩溃日志（脱敏 stack trace） | 仅开关开启时 | `filesDir/crash_logs/`（本地文件） | 否（可用户主动分享脱敏文本） | 保留最近 5 条自动滚动；卸载清除 |
| 设备标识 / 账号 / 通讯录 / 位置 | **不采集** | — | — | — |

> `ReadingProgressEntity` 含 `deviceId` 字段，为**未来跨设备同步预留**（`DATA-05`，P2），**当前 MVP 不生成、不读取、不上传**任何设备标识。

### 2.2 权限清单

| 权限 | 声明方 | 用途 | 能否联网 |
| --- | --- | --- | --- |
| （源码 Manifest） | 本工程 | **零权限声明** | — |
| `ACCESS_NETWORK_STATE` | 依赖带入 | 查询网络连接状态（WebView / 依赖内部判断） | 否（只读状态，不开套接字） |
| `WAKE_LOCK` | 依赖带入 | TYPE-03「保持常亮」保活 | 否 |
| **`INTERNET`** | **—** | **合并后发布 Manifest 不含此权限**（`OfflineGuaranteeTest` 断言固化） | **App 无法打开任何网络套接字** |

> **不申请 `MANAGE_EXTERNAL_STORAGE` / 「所有文件访问」**（红线 #3）；导入只用系统文档选择器（SAF）+ 复制到私有目录。

### 2.3 网络清单

- 业务源码（`app` / `reader:readium` / `core:model` 的 `src/main`）**零直接网络调用**（`OfflineGuaranteeTest` 扫描库前缀与平台原语）。
- 唯一白名单：`ReadiumFacade.kt` 引用 Readium `DefaultHttpClient`，但**发布 Manifest 无 INTERNET 权限，套接字打不开**；且本地 EPUB/TXT 打开路径（`OpenBookUseCase`）从不触发它。属「有 API 无网络能力」的惰性兜底。
- version catalog（`libs.versions.toml`）**无 okhttp / retrofit / firebase / crashlytics / sentry / 广告 SDK**。

### 2.4 日志与脱敏清单

- **崩溃日志**（`CrashLogger`）：仅开关开启时写本地；`sanitize()` 把应用私有目录前缀 → `<app-dir>`/`<app-cache>`、通用 `/data/data|user/`、`/storage/` 路径 → 占位符、`*.epub|txt|pdf|cbz` → `<file>`（`CrashLogSanitizerTest` 8 项固化）。stack trace 本身不含正文 / 摘录。
- **Timber**：仅 debug 构种种 `DebugTree`（落 logcat，开发用）；**release 无树 = no-op**，不产生任何日志输出。
- 日志**不含**正文、摘录、完整文件路径（红线 #8，已固化测试）。

### 2.5 用户控制

- 崩溃日志开关**默认关闭**（`AppSettingsRepository`，DataStore 持久化）；用户在「设置 → 崩溃日志」显式开启才记录。
- 「分享崩溃日志」按钮仅在本地有记录时出现；分享走 `ACTION_SEND` 纯文本，不经 FileProvider、不经网络。
- 全部数据可由用户删除（删书 / 卸载）。

---

## 3. 数据安全声明

> 本声明按 Google Play「Data safety」表式结构编写，供透明披露与未来上架复用。按决策 #5，MVP **暂不上架、私人自用**，本声明为内部审核产物。

### 3.1 Data safety 表

| 问题 | 回答 | 说明 |
| --- | --- | --- |
| 本 App 是否采集或共享数据？ | **否** | 不采集、不上传任何用户数据 |
| 是否处理用户上传 / 用户创建内容？ | 是（**仅本地处理**） | 用户导入的书籍文件、书签 / 高亮 / 笔记**仅存于设备本地**，不出设备 |
| 数据是否加密（传输中）？ | 不适用 | 无任何数据传输（无 INTERNET 权限） |
| 数据是否加密（静态）？ | 否 | 本地 Room / DataStore / 文件明文存储；由 Android 应用沙箱（私有目录）保护，未额外加密 |
| 用户能否请求数据删除？ | 是 | 删除书籍即删其文件与所有阅读数据；卸载应用清除全部数据 |
| 是否与第三方共享数据？ | **否** | 零网络、零 SDK 上报 |
| 是否启用崩溃报告？ | 默认**关** | 仅用户显式开启时记录**脱敏**本地日志，不上传 |
| 是否面向儿童？ | 否 | |

### 3.2 数据安全声明（正文）

**本应用是一款本地优先（local-first）的离线电子书阅读器。所有书籍内容与阅读数据（进度、书签、高亮、笔记、设置）仅存储于您设备的应用私有目录，不向任何服务器上传，不与任何第三方共享。应用不申请 INTERNET 权限，核心阅读在完全无网络的环境下亦可完成。崩溃日志默认关闭；若您主动开启，仅在本设备记录经脱敏（移除文件路径与正文片段）的堆栈信息，且不会自动上传。您可以随时通过删除书籍或卸载应用清除全部数据。**

---

## 4. 审核结论

| REL-07 子项 | 状态 | 依据 |
| --- | --- | --- |
| 第三方依赖许可证审核 | ✅ 通过 | §1：26 条已披露 + 同族归并 + de-minimis 登记；运行时无强 copyleft 冲突；MIT 新族补全文；`LicenseDataAuditTest` 5 项固化 |
| 隐私清单 | ✅ 通过 | §2：数据 / 权限 / 网络 / 日志脱敏 / 用户控制五项清单齐；`OfflineGuaranteeTest`（无 INTERNET）+ `CrashLogSanitizerTest`（脱敏）固化 |
| 数据安全声明 | ✅ 通过 | §3：Data safety 表 + 声明正文；零采集 / 零上传 / 可删除 |

**REL-07 三项审核均已完成并通过**。代码 + 文档 + 防回退测试齐备；`assembleDebug` + `testDebugUnitTest`（174 passed）+ `lintDebug` 均 BUILD SUCCESSFUL。

### 🚧 → ✅ 收尾条件

REL-07 标 🚧，转 ✅ 仅需真机确认「设置 → 开源许可证」页能渲染**扩展后的 26 条清单**（含 jsoup / MIT 展开 / 新增传递依赖项），可与 REL-06（TalkBack）真机回归同批完成。许可证页的渲染 / 展开逻辑已由 SET-05（2026-08-12）真机验证通过，本次仅是清单内容扩充，逻辑零改动。
