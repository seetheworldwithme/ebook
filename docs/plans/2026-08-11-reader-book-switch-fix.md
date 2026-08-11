# 阅读器切书串书修复 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修复退出一本书后打开另一本书时正文空白或仍显示旧书、进度不随当前书变化的问题。

**Architecture:** `ReaderUiState.Ready` 明确携带 `bookId`，`ReaderFragment` 只复用同一本书的 Navigator；目标书变化时取消旧观察任务、移除旧 Navigator 并用新 Publication 重建。Locator 回调携带来源 `bookId`，ViewModel 丢弃旧 Navigator 的迟到回调，同时取消切书前的打开与防抖保存任务，避免跨书污染 Room 进度。

**Tech Stack:** Kotlin、Jetpack Compose、AndroidX Fragment Compose、Readium、Kotlin Coroutines、JUnit/Robolectric。

---

### Task 1: 用单元测试固定 Navigator 切书判定

**Files:**
- Create: `app/src/test/java/com/xuziyue/ebook/reader/ReaderSessionTest.kt`
- Create: `app/src/main/java/com/xuziyue/ebook/reader/ReaderSession.kt`

1. 写测试：无 Navigator 时创建、同 bookId 复用、不同 bookId 替换、旧 Locator 来源不匹配时拒绝。
2. 运行 `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :app:testDebugUnitTest --tests com.xuziyue.ebook.reader.ReaderSessionTest`，确认因缺少实现失败。
3. 添加最小的纯 Kotlin 判定实现。
4. 重跑同一命令，确认通过。

### Task 2: 把 bookId 贯穿 Ready、Navigator 与 Locator 回调

**Files:**
- Modify: `app/src/main/java/com/xuziyue/ebook/reader/ReaderUiState.kt`
- Modify: `app/src/main/java/com/xuziyue/ebook/reader/ReaderFragment.kt`
- Modify: `app/src/main/java/com/xuziyue/ebook/reader/ReaderViewModel.kt`

1. `Ready` 增加 `bookId`，创建状态时写入当前目标书。
2. Fragment 记录 `navigatorBookId`；Ready 的书变化时取消旧绑定、移除旧 Navigator、创建新 Navigator。
3. Locator collector 调用 `onLocatorUpdated(sourceBookId, locator)`；ViewModel 仅接受来源与 activeBookId 相同的事件。
4. `openBook` 同步进入 Loading，清理旧进度展示，并取消旧的打开任务、防抖保存任务；异步结果只允许目标仍为当前书时发布。
5. 运行 Task 1 测试和 `./gradlew testDebugUnitTest`。

### Task 3: 编译、lint 与真机回归

**Files:**
- Modify: `docs/PROGRESS.md`

1. 在 `PROGRESS.md` 变更记录补充本次 READ-01/02/08 回归修复与验证边界。
2. 运行 `./gradlew assembleDebug && ./gradlew testDebugUnitTest && ./gradlew lintDebug`。
3. 安装 debug APK 到 vivo V2329A。
4. 真机验证：山海經正文与自身进度匹配；退出后打开万相之王，正文无空白/残留；翻页后右上角进度按当前 Locator 变化；再切回山海經确认两书进度互不污染。
5. 检查 logcat 无 FATAL/ANR/Readium 打开错误。

### Task 4: 拒绝跨书污染的恢复 Locator

**Files:**
- Create: `app/src/main/java/com/xuziyue/ebook/reader/ReaderLocatorValidation.kt`
- Create: `app/src/test/java/com/xuziyue/ebook/reader/ReaderLocatorValidationTest.kt`
- Modify: `app/src/main/java/com/xuziyue/ebook/reader/ReaderViewModel.kt`
- Modify: `docs/PROGRESS.md`

1. 写失败测试：readingOrder 内 href 接受；带 fragment 的同资源 href 接受；另一部书的 href 拒绝；空 readingOrder 拒绝。
2. 运行 `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :app:testDebugUnitTest --tests com.xuziyue.ebook.reader.ReaderLocatorValidationTest`，确认因校验函数缺失红灯。
3. 实现最小的 href 规范化和 readingOrder 归属校验。
4. `ReaderViewModel` 读取 Room Locator 后只把有效值传给 Navigator；无效值从 `reading_progress` 删除，等待真实 `currentLocator` 重新落盘。
5. 重跑定向测试和全量测试，确认通过。
6. 覆盖安装真机，打开 Alice 连续翻页，验证正文变化时右上角百分比和 Room Locator 同步变化；再重开确认恢复位置有效。

### Task 5: 同书退出重进恢复本次会话最新位置

**Files:**
- Modify: `app/src/main/java/com/xuziyue/ebook/reader/ReaderSession.kt`
- Modify: `app/src/test/java/com/xuziyue/ebook/reader/ReaderSessionTest.kt`
- Modify: `app/src/main/java/com/xuziyue/ebook/reader/ReaderFragment.kt`
- Modify: `app/src/main/java/com/xuziyue/ebook/reader/ReaderViewModel.kt`

1. 写失败测试：同一 bookId 的 ReaderFragment 重建时优先使用本次会话最新 Locator；尚无最新 Locator 时回退首次打开位置。
2. 实现恢复位置选择逻辑，并让 Fragment 创建/恢复 Navigator 的两个入口都使用它。
3. 重跑定向测试和全量测试、编译、lint。
4. 真机将 Alice 翻到新进度，退出到详情后重进验证不归零；再 force-stop 冷启动验证 Room 位置仍可恢复。
