# 代码审查报告：ebook（Android 本地电子书阅读器）

> 审查日期：2026-08-20 11:38
> 审查者：code-review-agent（AI 辅助审查，结论已逐条与源码比对，仅供参考）

## 一、概览

| 项 | 内容 |
|---|---|
| 技术栈 | Kotlin + Jetpack Compose + Readium（r2 系列）+ Room + Hilt + DataStore；多模块 `:app` / `:core:model` / `:reader:readium` |
| 审查范围 | `app/src/main`、`core/model/src/main`、`reader/readium/src/main` 全部 117 个 Kotlin 主源码文件；**未覆盖**：测试代码（仅作佐证参考）、`scripts/`、`website/`、Gradle 配置、原生资源 |
| 代码规模 | 共 117 个源码文件，约 14,155 行 |
| 审查方式 | 6 个并行子代理分区审查 + 主代理逐条复核 |
| 发现统计 | 🔴 严重 10 条 ｜ 🟡 一般 24 条 ｜ 🔵 建议 20 条 |
| 误报剔除 | 复核阶段剔除 / 降级 2 条子代理误报（详见问题清单注记） |

## 二、问题清单

### 🔴 严重（会真实出错 / 存在安全隐患）

#### 1. 恢复备份的 MERGE 策略失效：备份恒覆盖本地（哪怕本地更新）
- **位置**：`app/src/main/java/com/xuziyue/ebook/data/backup/RestoreUseCase.kt:243-248、264-267`
- **证据**：
  ```kotlin
  private suspend fun upsertBook(entity: BookEntity) {
      if (bookDao.getById(entity.id) != null) {
          bookDao.deleteById(entity.id) // CASCADE 会连带删子表——仅 OVERWRITE/MERGE 走这里需谨慎
      }
      bookDao.insert(entity)
  }
  // ...
  Strategy.MERGE_KEEP_NEWER -> {
      val local = progressDao.get(targetBookId)   // 刚被 CASCADE 清空，必为 null
      (local?.updatedAt ?: 0L) <= backupProgress.updatedAt
  }
  ```
- **问题**：`upsertBook` 先 `deleteById`（触发外键 CASCADE，连带删除该书本地进度/书签/批注/排版），随后 MERGE 分支查 `progressDao.get()` 必返回 null → `0L <= backup.updatedAt` 恒成立，**备份无条件覆盖本地**，与「字段级取新」语义完全相反。用户在 MERGE 策略下会丢失本地更新的进度与批注。
- **修复建议**：不要 delete+insert。改为字段级 UPDATE（保留子表），或在删除前先把本地 `updatedAt` 等比较基准缓存到内存再决策；整个 per-book 恢复块包 `db.withTransaction`。
- **置信度**：高（已主代理复核源码确认）

#### 2. 恢复流程无整体事务：中途失败留下半恢复状态
- **位置**：`app/src/main/java/com/xuziyue/ebook/data/backup/RestoreUseCase.kt:153-239`
- **证据**：
  ```kotlin
  for (bookRow in dto.books) {
      // ... 写文件、写 DB 记录、restoreRelated ...
      if (totalBytes > MAX_RESTORE_TOTAL) {
          return@withContext Outcome.Failed(...)   // L174-176：前半部分已落盘
      }
  }
  ```
- **问题**：恢复循环中途超限（L174）或抛异常时，前面若干本书的 DB 记录、文件、settings 已部分写入，违反「任何失败不留半条记录」红线；`upsertBook` 的 delete+insert 两步也无事务，进程被杀时可能书被删掉但没插回。
- **修复建议**：DB 写入全部包 `db.withTransaction`；文件落盘先到 staging 目录，事务成功后统一 rename；超限检查移到解压阶段（extractFiles 已有）之后的预检步骤，避免循环中途 return。
- **置信度**：高（已复核）

#### 3. 设置备份/恢复的 DataStore key 类型错位：恢复后 TTS 定时、亮度等静默回默认值
- **位置**：`app/src/main/java/com/xuziyue/ebook/data/backup/RestoreUseCase.kt:349-353`
- **证据**：
  ```kotlin
  pv.isString -> prefs[stringKey(keyName)] = pv.content
  pv.content == "true" || pv.content == "false" -> prefs[boolKey(keyName)] = ...
  else -> prefs[doubleKey(keyName)] = ...
  ```
- **问题**：备份把所有 Number/bool 存为无类型标签 JsonPrimitive，恢复统一写成 doubleKey/boolKey；而读取方（各 Repository）用 `intPreferencesKey`（如 tts_timer_minutes）、`floatPreferencesKey`（display_brightness）。DataStore 的 key 是「类型+名称」复合键，类型不等 → 恢复后这些设置读不到，**静默回落默认值**。备份往返（backup→restore）即丢设置。
- **修复建议**：备份时记录类型标签（备份 DTO 加 `type: int|float|bool|string|long` 字段），恢复按原类型写回；短期兜底：恢复时对已知 int/float key 名单做显式映射。
- **置信度**：高（已复核 applySettings 源码；读取方 key 类型经子代理交叉核对）

#### 4. 批量「加入书架」多选只对第一个书架生效
- **位置**：`app/src/main/java/com/xuziyue/ebook/MainActivity.kt:577-584`
- **证据**：
  ```kotlin
  // 批量加入：对每个选中的书架执行 addSelectedToCollection
  val first = collections.firstOrNull { it.id in selected }
  if (first != null) {
      viewModel.addSelectedToCollection(first.id, name)
  ```
- **问题**：`CollectionPickerSheet` 是多选交互（Checkbox 回传整个勾选集合），注释声称「对每个选中的书架执行」，实现却只取第一个。用户勾 3 个书架确认后只有 1 个生效，其余静默丢失。
- **修复建议**：`collections.filter { it.id in selected }.forEach { viewModel.addSelectedToCollection(it.id, 名称) }`；或把 ViewModel 方法改为接受 id 集合。若产品上只想要单选，则应把 Sheet 改为单选交互。
- **置信度**：高（已复核源码）

#### 5. 书库长按删除确认对话框是死代码，单本删除入口不可达
- **位置**：`app/src/main/java/com/xuziyue/ebook/MainActivity.kt:283、543-560`
- **证据**：
  ```kotlin
  var pendingDelete by remember { mutableStateOf<LibraryItem?>(null) }  // L283
  pendingDelete?.let { item -> AlertDialog(...) viewModel.deleteBook(...) }  // L543-560
  ```
- **问题**：全文件核对 `pendingDelete` 只有初始 null 与三处置 null（L545/551/555），**没有任何赋非空值的路径**（长按走的是 `enterSelectionMode`）。整个 AlertDialog 永不显示，`viewModel.deleteBook` 经此路径不可达，IMP-07 单本删除实际只剩详情页入口；`library_delete_confirm` 文案成死资源。
- **修复建议**：删除这段死代码（含文案），或在书卡 `onLongClick` 里补 `pendingDelete = item` 的真实路径（与批量模式二选一）。
- **置信度**：高（已主代理 grep 复核全部赋值点）

#### 6. TTS 发音人列表与定时状态在 ViewModel 构造时被冻结为空流
- **位置**：`app/src/main/java/com/xuziyue/ebook/reader/ReaderViewModel.kt:899-904`
- **证据**：
  ```kotlin
  val ttsVoices: StateFlow<List<AndroidTtsEngine.Voice>> = ttsManager?.voices
      ?: MutableStateFlow(emptyList())
  val ttsTimerMinutes: StateFlow<Int> = ttsManager?.timerMinutes
      ?: MutableStateFlow(0)
  ```
- **问题**：`ttsManager` 是 `private var ... = null`（L819），仅在 TTS 会话启动时赋值（L842）。这两个 `val` 在 ViewModel 构造时求值一次，那时必为 null → UI 拿到的是**永不更新的空流**。TTS 面板发音人列表永远为空、定时 chips 永远显示 0 且不反映用户选择。
- **修复建议**：`private val _activeTtsManager = MutableStateFlow<ReaderTtsManager?>(null)`，`ttsVoices = _activeTtsManager.flatMapLatest { it?.voices ?: flowOf(emptyList()) }.stateIn(...)`；`ttsTimerMinutes` 同理。
- **置信度**：高（已复核 L819/842/909 与字段定义）

#### 7. 无 BOM 的 UTF-16 文本会被误判为 UTF-8，正文 NUL 乱码
- **位置**：`reader/readium/src/main/java/com/xuziyue/ebook/reader/readium/txt/TxtEncodingDetector.kt:73`
- **证据**：
  ```kotlin
  if (isCleanDecode(bytes, StandardCharsets.UTF_8)) {
      return TxtEncodingResult.Detected(StandardCharsets.UTF_8, hadBom = false)
  }
  ```
- **问题**：NUL（`0x00`）是合法 UTF-8 字节。无 BOM UTF-16LE 纯 ASCII 文本是 `41 00 42 00…`，必然通过严格 UTF-8 校验被判为 UTF-8，打开后每字符间夹 NUL；中文 UTF-16 字节对也大概率成对合法。违反红线 #5「不静默乱码打开」。Windows 记事本「Unicode」保存即无 BOM UTF-16LE，是真实场景。
- **修复建议**：无 BOM 时先检测 NUL 字节的奇偶交替分布（命中即判 UTF-16LE/BE）；或在 UTF-8 校验通过后检查解码结果 `\u0000` 占比，超阈值（如 5%）改判 `NeedsUserChoice`。
- **置信度**：高（已复核探测流程）

#### 8. GB18030 校验几乎恒通过，Big5 等编码被静默乱码打开
- **位置**：`reader/readium/src/main/java/com/xuziyue/ebook/reader/readium/txt/TxtEncodingDetector.kt:78`
- **证据**：
  ```kotlin
  if (isCleanDecode(bytes, GB18030)) {
      return TxtEncodingResult.Detected(GB18030, hadBom = false)
  }
  ```
- **问题**：GB18030 几乎能干净解码任意字节序列（lead 0x81–0xFE + trail 0x40–0xFE），Big5、EUC-KR 甚至随机二进制大概率通过 → 被静默判为 GB18030 以乱码打开，同样违反红线 #5。
- **修复建议**：GB18030 通过后做二次确认：统计 CJK 汉字占比、乱码特征字符（PUA 区 `\uE000-\uF8FF` 等）频率；置信度低时降级 `NeedsUserChoice` 并把 GB18030 放候选首位。
- **置信度**：高

#### 9. 压缩炸弹防护建立在可伪造的 ZIP 声明值上，可被绕过
- **位置**：`app/src/main/java/com/xuziyue/ebook/data/EpubSecurityValidator.kt:72-99`
- **证据**：
  ```kotlin
  val uncompressedSize = entry.size
  if (uncompressedSize > 0) { ... totalUncompressed += uncompressedSize }
  ```
- **问题**：单条目/总大小/压缩比三项检查全部基于中央目录里攻击者可控的 `entry.size` **声明值**；`size <= 0`（含 -1，streaming 写出的 ZIP 常见）的条目被完全跳过。Java `ZipFile` 解压并不强制实际字节等于声明值，恶意 EPUB 声明小尺寸实际解出超大内容可全部穿透。防护形式存在、实质可绕过。
- **修复建议**：改为解压时流式实测计数（读 entry 流累计字节，超限中止）；至少对 `size <= 0` 的条目强制实测；或确认 Readium 侧解压有真实限额并在此注明依赖。
- **置信度**：中（机制成立；实际穿透需 Readium 侧无兜底，建议实测验证）

#### 10. 导入复制阶段无字节上限，querySize 返回 -1 时 200MB 限制失效
- **位置**：`app/src/main/java/com/xuziyue/ebook/data/BookFileImporter.kt:59-71、114-124`
- **证据**：
  ```kotlin
  } catch (e: Exception) { -1L }   // querySize 失败返回 -1
  // checkImportPreconditions: if (sourceSize > 0 && sourceSize > availableSpace) —— -1 时跳过全部预检
  while (true) { val n = read(buf); ... output.write(buf, 0, n) }  // copyToWithHash 无上限
  ```
- **问题**：SAF provider（云盘、部分文件管理器）查不到 SIZE 时返回 -1，大小与空间预检双双跳过；复制循环无累计上限，任意大流可被完整拷入直到写满磁盘。
- **修复建议**：`copyToWithHash` 循环内累计字节数，超过 `MAX_IMPORT_SIZE`（200MB）抛 `ImportSafetyException(FileTooLarge)` 并删临时文件；-1 时按「无预检」走实测上限。
- **置信度**：高（已复核源码）

### 🟡 一般（特定条件下出错 / 明显不合理）

#### 11. 搜索失败与无结果混同；取消旧搜索时关闭 iterator 存在竞态
- **位置**：`app/src/main/java/com/xuziyue/ebook/reader/ReaderViewModel.kt:755-757、768-772`
- **证据**：
  ```kotlin
  searchJob?.cancel()
  searchIterator?.close()
  val collection = iterator.next().getOrNull()
  if (collection == null) { _searchState.value = SearchUiState.Error(...) }
  ```
- **问题**：`next()` 失败（真错误）与耗尽（无更多）都走 `getOrNull() == null`，无匹配可能显示「搜索失败」；且 `cancel()` 后立即同步 `close()` 同一 iterator，旧协程若仍挂起在 `next()` 上，关闭竞态可能抛未捕获异常。
- **修复建议**：`next()` 改 onSuccess/onFailure 分流（失败→Error，耗尽→空 Results）；`close()` 移入 `searchJob.invokeOnCompletion`。
- **置信度**：高（已复核）

#### 12. navCommands 用无缓冲策略的 Channel，后台期指令缓冲重放
- **位置**：`app/src/main/java/com/xuziyue/ebook/reader/ReaderViewModel.kt:235-236` + `ReaderFragment.kt:294-312`
- **问题**：普通 Channel 在收集方仅 STARTED 活跃时，后台期发出的跳转指令会被缓冲，恢复阅读器后集中重放（回阅读器突然跳页）。
- **修复建议**：`Channel(Channel.CONFLATED 或 DROP_OLDEST)` + 指令携带时间戳做过期判断。
- **置信度**：中

#### 13. TXT→EPUB 缓存非原子写入，且缓存命中仍全量解析
- **位置**：`reader/readium/src/main/java/com/xuziyue/ebook/reader/readium/OpenTxtPublicationUseCase.kt:55-75`
- **证据**：
  ```kotlin
  if (!cacheFile.exists() || cacheFile.length() == 0L) {
      cacheFile.outputStream().use { converter.writeTo(book, title, contentHash, it) }
  ```
- **问题**：直接写目标文件，进程被杀/磁盘满留下半截 `.epub` 且 `length() != 0` 导致**永远复用坏缓存**；且缓存命中路径也先跑完整 `txtParser.parse`（读全文+探测+切分），缓存没省解析开销。
- **修复建议**：写 `.tmp` 后 `renameTo` 原子替换；缓存存在性检查提到 parse 之前。
- **置信度**：高

#### 14. TXT 全链路多次全文驻留内存，50MB 上限下峰值约 300MB+，OOM 风险
- **位置**：`reader/readium/.../txt/TxtParser.kt:83`、`TxtChapterSplitter.kt:66`、`TxtEpubConverter.kt:157`
- **问题**：`readBytes()` + decode + `split(Regex)` + 每章 `joinToString`/`buildString` 多份拷贝叠加；注释仅以 10MB 实测作依据，上限却放 50MB，中低端机（heap 128–256MB）直接 OOM。
- **修复建议**：上限降到实测安全的 20MB，或按索引惰性切片逐章流式写 zip。
- **置信度**：中

#### 15. 目录式 TXT 会切出成百上千个空 body 章节
- **位置**：`reader/readium/.../txt/TxtChapterSplitter.kt:95-100`
- **问题**：连续两行都是章节标题时 `start == end`，空 body 仍成章，EPUB 条目暴增拖慢打开。
- **修复建议**：`body.isBlank()` 且下一边界紧邻时跳过/合并；章节数设上限（如 2000）超限回落单章。
- **置信度**：中

#### 16. `isRestricted` 失败分支 Publication 未 close
- **位置**：`reader/readium/.../OpenBookUseCase.kt:56-58`（`OpenTxtPublicationUseCase.kt:88-90` 同）
- **问题**：受限分支直接返回 failure 不 close 已打开的 Publication，长期反复打开累积句柄泄漏。
- **修复建议**：restricted 分支先 `publication.close()` 再返回。
- **置信度**：中

#### 17. isZipSlip 未覆盖盘符/NUL 变体，且无解压点二次校验
- **位置**：`app/src/main/java/com/xuziyue/ebook/data/EpubSecurityValidator.kt:141-156`
- **问题**：`C:\` 盘符、NUL 字节变体未拦截；防护只在预检阶段，实际解压点未做「规范化后仍位于目标目录内」二次校验（红线 #4 原文要求）。
- **修复建议**：补 `:` 与 `\u0000` 检查；解压侧统一走一个做 canonicalPath 校验的工具函数。
- **置信度**：中

#### 18. IOException message 携带完整源 Uri，违反日志隐私红线
- **位置**：`app/src/main/java/com/xuziyue/ebook/data/BookFileImporter.kt:42`（按子代理核对）
- **问题**：错误信息包含原始 SAF Uri（可能含文件名/路径），进日志违反「日志不得含完整文件路径」。
- **修复建议**：日志只记错误类别与 display name（必要时截断哈希化）。
- **置信度**：中

#### 19. 安全校验排在 DB 去重之后，`file.delete()` 的「无引用」前提无保护
- **位置**：`app/src/main/java/com/xuziyue/ebook/data/ImportBookUseCase.kt:56-73`
- **问题**：去重命中后删除刚复制的文件，前提是「此刻无其他 DB 引用」，但该前提无校验（并发导入同 hash 时可能删掉他人正在引用的文件副本——contentHash 命名下实为同文件，风险低但逻辑前提脆弱）。
- **修复建议**：删除前 `bookDao.countByContentHash` 复核，或依赖 hash 命名文件本身可共享、直接复用不删。
- **置信度**：中

#### 20. CrashLogger previous handler 为 null 时进程不终止
- **位置**：`app/src/main/java/com/xuziyue/ebook/log/CrashLogger.kt:61-68`
- **问题**：自定义 handler 处理完崩溃后若 `previous == null` 直接 return，进程可能停留在半死状态（僵尸化），系统无法正常收集/重启。
- **修复建议**：previous 为 null 时 `Runtime.getRuntime().exit(10)` 或调用 `Process.killProcess` 保证终止。
- **置信度**：中

#### 21. DocumentEnumerator 对 MIME 为 null 的虚拟文档误判为文件
- **位置**：`app/src/main/java/com/xuziyue/ebook/data/scan/DocumentEnumerator.kt:107`
- **问题**：虚拟文档（`Document.MIME_TYPE_DIR` 之外但无 MIME）被当普通文件计数，目录扫描报告失真。
- **修复建议**：MIME 为 null 时按扩展名白名单二次判定，无法判定则跳过并计数 unknown。
- **置信度**：中

#### 22. TTS 一次性事件用 StateFlow 承载：同值合并 + 配置变更后重放
- **位置**：`app/src/main/java/com/xuziyue/ebook/reader/tts/ReaderTtsManager.kt:59-60、144`
- **证据**：
  ```kotlin
  private val _events = MutableStateFlow<Event?>(null)
  ...
  TtsNavigator.State.Ended -> { timer?.cancel(); _events.value = Event.Ended }
  ```
- **问题**：第二次相同事件（如重播后再 Ended）不重新发射；旋转屏幕后新收集者立刻收到历史事件，重复弹 Toast。
- **修复建议**：改 `Channel<Event>(Channel.BUFFERED) + receiveAsFlow()`（与 LibraryViewModel 同范式）。
- **置信度**：高

#### 23. 目录导入扫描失败与 takePersistableUriPermission 失败均被静默吞掉
- **位置**：`app/src/main/java/com/xuziyue/ebook/folderimport/FolderImportViewModel.kt:58-64、86-89`
- **证据**：
  ```kotlin
  val report = runCatching { scanUseCase.scan(...) }.getOrNull()   // 异常完全吞掉
  ```
- **问题**：扫描失败无任何 UI 提示（进度条一闪而过）；take 授权失败仍持久化 Uri，之后冷启动自动扫描反复失败且无线索，用户看到「自动扫描从来没工作过」。
- **修复建议**：onFailure 发事件 Toast 失败原因；take 失败不持久化并提示重新选择；扫描失败区分 `SecurityException` 引导重新授权。
- **置信度**：高

#### 24. 统计页数据只在开关变化时刷新，不随 Room 写入回推、跨天过期
- **位置**：`app/src/main/java/com/xuziyue/ebook/statistics/StatisticsViewModel.kt:53-55`
- **证据**：
  ```kotlin
  init { viewModelScope.launch { appSettings.readingStatsEnabled.collect { refresh(it) } } }
  ```
- **问题**：`refresh` 唯一触发器是设置开关 Flow；读完书返回统计页显示旧数据；跨午夜 today/week/daily 全部过期。
- **修复建议**：改 `WhileSubscribed` + 会话表响应式 Flow 驱动，或页面进入时 refresh。
- **置信度**：高

#### 25. 书籍详情页重复订阅同一 Flow，「今日时长」触发时机受制于进度更新
- **位置**：`app/src/main/java/com/xuziyue/ebook/library/BookDetailViewModel.kt:75、79-84`
- **问题**：`progressDao.observe(bookId)` 订阅两次，进度每变一次双路发射 + 顺带两条 suspend 查询；停留跨午夜「今日」口径不刷新。
- **修复建议**：合并为一次 observe，map 内同时取进度与时长。
- **置信度**：中

#### 26. LicensesScreen 许可证缓存是行级 remember 且主线程读 asset
- **位置**：`app/src/main/java/com/xuziyue/ebook/settings/LicensesScreen.kt:88-89、121-124`
- **问题**：缓存 map 在每个 `LicenseRow` 内部 remember，15+ 个 Apache 条目各自重读、滚动丢失重读，且组合期主线程 IO；与注释「同一许可证只读一次」不符。
- **修复建议**：缓存提升到 Screen 级或顶层伴生对象；读取放 LaunchedEffect。
- **置信度**：高

#### 27. SAF 导入 mime 白名单含 `*/*`，具体类型形同虚设
- **位置**：`app/src/main/java/com/xuziyue/ebook/MainActivity.kt:371-380`
- **证据**：
  ```kotlin
  launcher.launch(arrayOf("application/epub+zip", "text/plain", ..., "*/*"))
  ```
- **问题**：`*/*` 使系统选择器展示所有文件，白名单完全失效，用户可选 4GB 视频再等导入失败，削弱第一道输入过滤。
- **修复建议**：去掉 `*/*`；若为兼容 mime 探测不准的文件管理器而保留，写明注释理由。
- **置信度**：中（已复核数组内容）

#### 28. 书签 toggle 检查-写入非原子，快速双击产生重复书签
- **位置**：`app/src/main/java/com/xuziyue/ebook/data/BookmarkRepository.kt:37-43`
- **问题**：读快照与写入之间无事务，并发协程双判「不存在」，违反 READ-06「重复位置不生成重复书签」。
- **修复建议**：包 `db.withTransaction` 或提供原子 toggle SQL。
- **置信度**：高

#### 29. ReadingSessionRepository 内存态竞态，touchActive 不校验 sessionId
- **位置**：`app/src/main/java/com/xuziyue/ebook/data/ReadingSessionRepository.kt:40、54-58`
- **问题**：`active` 普通 var 无同步；touch 收到 sessionId 却不比对归属，快速切书时旧 session 迟到 touch 会拉长新会话时长（最长多计 5 分钟）。
- **修复建议**：Mutex 保护；`active` 记录 sessionId 并在 touch 比对。
- **置信度**：高

#### 30. LIKE 搜索未转义 `%`/`_`
- **位置**：`app/src/main/java/com/xuziyue/ebook/data/db/BookDao.kt:58`、`CollectionBookDao.kt:28`
- **问题**：书名含 `%` 的书在任何搜索词下都命中；两处 SQL 重复维护。
- **修复建议**：应用层转义 + `ESCAPE '\'`；SQL 收敛为常量。
- **置信度**：高

#### 31. DAO `OnConflictStrategy.REPLACE` 具备先 DELETE 后 INSERT 语义，为未来埋雷
- **位置**：`app/src/main/java/com/xuziyue/ebook/data/db/ReadingProgressDao.kt:17`；`BookmarkDao.kt:18`、`AnnotationDao.kt:21`、`BookTypographyDao.kt:17` 同
- **证据**：
  ```kotlin
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: ReadingProgressEntity)
  ```
- **问题**：SQLite `INSERT OR REPLACE` 是删旧插新。**经主代理复核：当前 FK 方向是这些子表 → books，REPLACE 自身行不会触发对子表的 CASCADE（只有删 books 行才会），故今日无实际数据丢失，降级为「一般」**（子代理原判「严重」）。但隐患真实存在：(1) annotations 的 REPLACE 会物理替换软删行，破坏回收站语义；(2) 一旦未来 schema 让 reading_progress 被其他表引用（如同步），每次高频 save 都会静默级联。
- **修复建议**：全部改 `@Upsert`（Room 2.5+，真 UPDATE-or-INSERT）。
- **置信度**：高（复核后由严重降级为一般）

#### 32. deleteBook 的 `runCatching` 吞掉一切文件删除异常且无日志
- **位置**：`app/src/main/java/com/xuziyue/ebook/data/BookRepository.kt:61-65`
- **问题**：文件删除失败（SecurityException 等）静默，孤儿文件无法排查，违反「错误不静默吞」约定。
- **修复建议**：失败分支记日志（不含完整路径）。
- **置信度**：中

#### 33. 恢复 ZIP 条目 bookId 未消毒，恶意条目可致整个恢复失败
- **位置**：`app/src/main/java/com/xuziyue/ebook/data/backup/RestoreUseCase.kt:382-394`
- **问题**：`books/x/y.epub` → bookId `x/y` → `File(tmpDir, "book-x/y.tmp")` 父目录不存在抛异常，不可信备份可 DoS 整个恢复。
- **修复建议**：bookId 做白名单消毒（`[A-Za-z0-9-]`），非法条目跳过。
- **置信度**：中

#### 34. 备份/导出写 SAF 非 "wt" 模式、无 fsync
- **位置**：`app/src/main/java/com/xuziyue/ebook/data/backup/BackupUseCase.kt:131-136`、`ExportBookDataUseCase.kt:132-142`
- **问题**：中途失败留半截文件且旧备份已丢，红线 #6「临时文件+原子替换」打折。
- **修复建议**：先写本地 tmp + fsync，再 `openOutputStream("wt")` 复制。
- **置信度**：高

### 🔵 建议（质量与可维护性改进）

#### 35. TXT xmlEscape 逐字符拼接性能差，`trim()` 吃掉段首全角空格缩进
- **位置**：`reader/readium/.../txt/TxtEpubConverter.kt:164-183`
- **建议**：分段批量 append；段落化仅 trimEnd 或用 CSS `text-indent:2em`。置信度：高

#### 36. ReadiumFacade 的 DefaultHttpClient 未限制外部资源加载
- **位置**：`reader/readium/.../ReadiumFacade.kt:25`
- **建议**：加拦截器拦截非本地请求返回 404（红线 #4 默认离线）；并在 navigator 层确认外链配置。置信度：中

#### 37. ExtractPublicationMetadataUseCase 把结构化错误压成 IllegalStateException
- **位置**：`reader/readium/.../ExtractPublicationMetadataUseCase.kt:49-51`
- **建议**：定义 `OpenBookException(val error: OpenBookError)` 保留类型（EncodingChoiceNeeded 等需上层分流）。置信度：高

#### 38. 搜索加载更多的 LaunchedEffect 捕获陈旧 state
- **位置**：`app/src/main/java/com/xuziyue/ebook/reader/ReaderScreen.kt:905-914`
- **建议**：key 加 loadingMore 或改 snapshotFlow。置信度：中

#### 39. 目录 LazyColumn 用 index 做 key
- **位置**：`app/src/main/java/com/xuziyue/ebook/reader/ReaderScreen.kt:735`
- **建议**：用 href+title 稳定 key。置信度：中

#### 40. goForward 发送 `GoBack` 命令对象，命名误导
- **位置**：`app/src/main/java/com/xuziyue/ebook/reader/ReaderViewModel.kt:727`
- **建议**：改名 GoToLocator。置信度：高

#### 41. ReaderViewModel 迟到 bookId 校验重复死代码
- **位置**：`app/src/main/java/com/xuziyue/ebook/reader/ReaderViewModel.kt:373-376`
- **建议**：与 353-356 重复，删除。置信度：高

#### 42. PersistedLocator.fromJsonString 缺键返回空串对象而非 null
- **位置**：`app/src/main/java/com/xuziyue/ebook/data/PersistedLocator.kt:44-50`
- **建议**：缺关键键返回 null，调用方按无进度处理。置信度：中

#### 43. DirectoryScanner upsertRecord 每文档重复查 DAO（N+1）
- **位置**：`app/src/main/java/com/xuziyue/ebook/data/scan/DirectoryScanner.kt:35、71`
- **建议**：一次性快照建 hash 索引。置信度：高

#### 44. Hashing.kt 与 copyToWithHash 重复实现流式 SHA-256
- **位置**：`app/src/main/java/com/xuziyue/ebook/data/Hashing.kt:19-28` vs `BookFileImporter.kt:114-124`
- **建议**：收敛为一个工具函数。置信度：高

#### 45. 恢复 preview 与 restore 对损坏 ZIP 行为不一致
- **位置**：`app/src/main/java/com/xuziyue/ebook/data/backup/RestoreUseCase.kt:102-103`
- **建议**：preview 损坏也报错而非弹「0 本书」对话框。置信度：中

#### 46. 恢复缺书文件条目时仍写指向不存在文件的 DB 记录（幽灵书）
- **位置**：`app/src/main/java/com/xuziyue/ebook/data/backup/RestoreUseCase.kt:165-201`
- **建议**：本地无同 hash 且 ZIP 无文件时跳过该书记录并计入警告。置信度：中

#### 47. restore-extract 临时目录从不清理
- **位置**：`app/src/main/java/com/xuziyue/ebook/data/backup/RestoreUseCase.kt:370`
- **建议**：恢复结束（成败皆）删除 tmpDir。置信度：高

#### 48. MERGE 策略下 settings 仍 `prefs.clear()` 全清覆盖
- **位置**：`app/src/main/java/com/xuziyue/ebook/data/backup/RestoreUseCase.kt:342-356`
- **建议**：MERGE 只覆盖备份中存在的 key。置信度：高

#### 49. preview/restore 每书单独 getByContentHash（N+1）
- **位置**：`app/src/main/java/com/xuziyue/ebook/data/backup/RestoreUseCase.kt:154`
- **建议**：一次性快照建索引。置信度：高

#### 50. BackupViewModel pendingUri 普通字段 + 未 takePersistableUriPermission
- **位置**：`app/src/main/java/com/xuziyue/ebook/backup/BackupViewModel.kt:40`
- **建议**：进程重建丢失；跨阶段 URI 授权可能失效，进 ViewModel 时 take。置信度：中

#### 51. 崩溃日志文件读取在主线程（组合期遍历 + 点击时 readText）
- **位置**：`app/src/main/java/com/xuziyue/ebook/settings/SettingsScreen.kt:70、169`
- **建议**：挪 ViewModel + Dispatchers.IO + StateFlow。置信度：中

#### 52. formatFileSize 未固定 Locale，ar 等输出非 ASCII 数字
- **位置**：`app/src/main/java/com/xuziyue/ebook/library/BookDetailScreen.kt:397-399`
- **建议**：`String.format(Locale.US, ...)`（同文件 RelativeTime 已如此）。置信度：高

#### 53. formatDuration 的 now 参数从未使用
- **位置**：`app/src/main/java/com/xuziyue/ebook/ui/ReadingStats.kt:19`
- **建议**：删除死参数。置信度：高

#### 54. TTS 创建协程中途取消时已创建资源不 close
- **位置**：`app/src/main/java/com/xuziyue/ebook/reader/tts/ReaderTtsManager.kt:106-131、240-246`
- **建议**：startJob 内 try/finally，取消时 close 已 created 的 navigator。置信度：中

#### 55. EbookApp.onCreate 主线程清理临时文件
- **位置**：`app/src/main/java/com/xuziyue/ebook/EbookApp.kt:51`
- **建议**：挪入 applicationScope.launch(Dispatchers.IO)。置信度：中

#### 56. TtsTimer 字段无 @Volatile，remainingMillis 可能读到旧值
- **位置**：`app/src/main/java/com/xuziyue/ebook/reader/tts/TtsTimer.kt:25-39`
- **建议**：加 @Volatile；`job = null` 移到 onExpired 之后。置信度：中

#### 57. deleteSelected 部分失败仍恒报成功
- **位置**：`app/src/main/java/com/xuziyue/ebook/library/LibraryViewModel.kt:244-250`
- **建议**：`ok < size` 时发带失败计数的事件。置信度：中

#### 58. Color.kt 模板色板基本未被使用（dynamicColor 恒 true）
- **位置**：`app/src/main/java/com/xuziyue/ebook/ui/theme/Theme.kt:28`
- **建议**：删模板色或加「动态色彩」设置开关。置信度：中

#### 59. CollectionPickerSheet 快速新建后立即确认不包含新书架
- **位置**：`app/src/main/java/com/xuziyue/ebook/library/CollectionPickerSheet.kt:67-71`
- **建议**：onQuickCreate 成功后自动勾选新书架。置信度：中

#### 60. 跨日阅读会话整段计入 endedAt 那天，连续天数可能断签
- **位置**：`app/src/main/java/com/xuziyue/ebook/data/db/ReadingSessionDao.kt:26-30、44-53`
- **建议**：按日切分或在统计层注明口径。置信度：中

#### 61. markOpened 绕过注入 clock、createCollection 排序无更新通道等范式不一致
- **位置**：`app/src/main/java/com/xuziyue/ebook/data/BookRepository.kt:46-47`、`CollectionRepository.kt:58-59`
- **建议**：补 clock 注入；补 updateSortOrder DAO 方法。置信度：高

> 复核注记：子代理另报「BookRepository.deleteBook 注释与实际顺序相反」一条，经主代理读源码核实——代码与注释一致（先 DB 后文件），仅吞异常问题成立（已并入 #32），该条误报剔除；Room REPLACE 一条由「严重」降级为「一般」（#31）。

## 三、整改清单

按优先级排序，可直接勾选跟踪：

**第一优先级（数据丢失 / 确定性功能失效）**
- [ ] 🔴 1. MERGE 恢复策略失效，备份恒覆盖（`RestoreUseCase.kt:243`）
- [ ] 🔴 2. 恢复无整体事务，中途失败留半恢复状态（`RestoreUseCase.kt:153`）
- [ ] 🔴 3. DataStore key 类型错位，恢复丢设置（`RestoreUseCase.kt:349`）
- [ ] 🔴 4. 批量加入书架只加第一个（`MainActivity.kt:579`）
- [ ] 🔴 5. 书库删除确认对话框死代码（`MainActivity.kt:283`）
- [ ] 🔴 6. TTS 发音人/定时状态冻结为空流（`ReaderViewModel.kt:899`）
- [ ] 🔴 7. 无 BOM UTF-16 误判 UTF-8（`TxtEncodingDetector.kt:73`）
- [ ] 🔴 8. GB18030 恒通过致静默乱码（`TxtEncodingDetector.kt:78`）
- [ ] 🔴 9. 压缩炸弹防护依赖声明值可绕过（`EpubSecurityValidator.kt:72`）
- [ ] 🔴 10. 复制无字节上限，-1 时 200MB 限制失效（`BookFileImporter.kt:114`）

**第二优先级（特定条件出错）**
- [ ] 🟡 11. 搜索失败/耗尽混同 + iterator 关闭竞态（`ReaderViewModel.kt:755`）
- [ ] 🟡 22. TTS 事件 StateFlow 误用（`ReaderTtsManager.kt:59`）
- [ ] 🟡 23. 目录导入两处错误静默吞（`FolderImportViewModel.kt:58/86`）
- [ ] 🟡 13. TXT→EPUB 缓存非原子 + 命中仍全量解析（`OpenTxtPublicationUseCase.kt:55`）
- [ ] 🟡 28. 书签 toggle 非原子（`BookmarkRepository.kt:37`）
- [ ] 🟡 29. 阅读会话竞态 + touch 不校验归属（`ReadingSessionRepository.kt:54`）
- [ ] 🟡 31. DAO REPLACE 改 @Upsert（`ReadingProgressDao.kt:17` 等 4 处）
- [ ] 🟡 34. 备份写 SAF 非 wt 模式无 fsync（`BackupUseCase.kt:131`）
- [ ] 🟡 24. 统计页不随 Room 回推、跨天过期（`StatisticsViewModel.kt:53`）
- [ ] 🟡 27. mime 白名单含 `*/*`（`MainActivity.kt:371`）
- [ ] 🟡 30. LIKE 未转义 %/_（`BookDao.kt:58`）
- [ ] 🟡 14. TXT 内存峰值 OOM 风险（`TxtParser.kt:83`）
- [ ] 🟡 15. 目录式 TXT 空章节爆炸（`TxtChapterSplitter.kt:95`）
- [ ] 🟡 16. isRestricted 分支 Publication 泄漏（`OpenBookUseCase.kt:56`）
- [ ] 🟡 17. isZipSlip 变体 + 解压点二次校验（`EpubSecurityValidator.kt:141`）
- [ ] 🟡 18. 日志泄漏完整 Uri（`BookFileImporter.kt:42`）
- [ ] 🟡 19. 去重删除前提无保护（`ImportBookUseCase.kt:56`）
- [ ] 🟡 20. CrashLogger null handler 不终止（`CrashLogger.kt:61`）
- [ ] 🟡 21. 虚拟文档误判（`DocumentEnumerator.kt:107`）
- [ ] 🟡 12. navCommands 缓冲重放（`ReaderViewModel.kt:235`）
- [ ] 🟡 25. 详情页重复订阅 Flow（`BookDetailViewModel.kt:79`）
- [ ] 🟡 26. 许可证缓存行级 remember + 主线程 IO（`LicensesScreen.kt:88`）
- [ ] 🟡 32. deleteBook 吞异常无日志（`BookRepository.kt:61`）
- [ ] 🟡 33. 恢复 bookId 未消毒可 DoS（`RestoreUseCase.kt:382`）

**第三优先级（质量改进）**
- [ ] 🔵 35~61（详见问题清单：xmlEscape 性能与缩进、DefaultHttpClient 离线、错误类型压平、LazyColumn key、N+1 查询 ×3、临时目录清理、Locale、死参数、@Volatile、主线程 IO ×2 等）

## 四、总体评价

整体工程质量在同类个人项目中属上乘：分层清晰（model/data/readium/reader）、migration 与导出 schema 逐版对齐、红线意识强（能力矩阵 gating、Locator 主数据、隐私日志约束在多处被自觉遵守）、测试覆盖广（54+ 测试文件含迁移与导入安全用例）。最突出的问题集中在三条链路：**备份/恢复链路**（无事务 + CASCADE 破坏 MERGE + key 类型错位，是唯一会真实丢用户数据的路径）、**TXT 编码探测**（两条静默乱码路径直接违反红线 #5）、**导入限制建立在声明值而非实测值**（压缩炸弹与大小上限可绕过）。另有两个低成本高收益的确定性 UI bug（批量加书架、删除死代码）建议随第一优先级一并修复。
