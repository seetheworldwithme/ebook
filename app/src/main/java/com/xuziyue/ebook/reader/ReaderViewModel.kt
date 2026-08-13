package com.xuziyue.ebook.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xuziyue.ebook.R
import com.xuziyue.ebook.data.AnnotationRepository
import com.xuziyue.ebook.data.BookRepository
import com.xuziyue.ebook.data.BookmarkRepository
import com.xuziyue.ebook.data.ReaderDisplaySettingsRepository
import com.xuziyue.ebook.data.ReaderTypographyRepository
import com.xuziyue.ebook.data.ReadingProgressRepository
import com.xuziyue.ebook.data.export.ExportBookDataUseCase
import com.xuziyue.ebook.model.HighlightColor
import com.xuziyue.ebook.model.ReaderCapabilities
import com.xuziyue.ebook.model.ReaderDisplaySettings
import com.xuziyue.ebook.model.ReaderOrientation
import com.xuziyue.ebook.model.ReaderScrollMode
import com.xuziyue.ebook.model.ReaderTextAlign
import com.xuziyue.ebook.model.ReaderTheme
import com.xuziyue.ebook.model.ReaderTypography
import com.xuziyue.ebook.reader.readium.OpenBookUseCase
import com.xuziyue.ebook.ui.UserMessage
import com.xuziyue.ebook.reader.readium.OpenTxtPublicationUseCase
import com.xuziyue.ebook.reader.readium.toEpubPreferences
import com.xuziyue.ebook.reader.readium.toReaderCapabilities
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.search.SearchIterator
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.AbsoluteUrl
import java.io.File

/**
 * Reader 的核心 ViewModel（命门）。
 *
 * 职责：
 * 1. 管理 [Publication][org.readium.r2.shared.publication.Publication] 生命周期（open / close / 进程重建重 open）。
 * 2. 协调 [Locator] 恢复——从 [ReadingProgressRepository]（Room）读最近位置作 initialLocator，currentLocator 变化防抖落盘。
 * 3. 持有排版偏好（[typography] 引擎无关 → [preferences] 解析为 [EpubPreferences]）与高亮（[Decoration]）StateFlow，
 *    供 Compose 控制条与 Fragment 订阅。
 * 4. 实现 [EpubNavigatorFragment.Listener]（外链交系统浏览器，红线 #4 + design.md §7）。
 *
 * **排版偏好（design.md §4.4 TYPE-01/02）**：[typography] 来自 [ReaderTypographyRepository]（DataStore 持久化，
 * 跨重启保位）；[preferences] 由 [typography] + 系统暗色（[setSystemDark]）经 `toEpubPreferences` 派生。
 * 单向数据流：setter 写 Repository → observe 回流 → [typography]/[preferences] 更新 → Fragment submitPreferences。
 *
 * **Scope 决策**：绑 Activity scope（[com.xuziyue.ebook.MainActivity]）。Compose 的 ReaderScreen 与嵌入的
 * ReaderFragment 通过 activityViewModels() 共享同一实例。bookId 由 [openBook] 传入——进程重建后 Navigation
 * 恢复 route，ReaderScreen 重建调 openBook 重 open。
 */
@OptIn(ExperimentalReadiumApi::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReaderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val openBookUseCase: OpenBookUseCase,
    private val openTxtUseCase: OpenTxtPublicationUseCase,
    private val bookRepository: BookRepository,
    private val progressRepository: ReadingProgressRepository,
    private val typographyRepository: ReaderTypographyRepository,
    private val displaySettingsRepository: ReaderDisplaySettingsRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val annotationRepository: AnnotationRepository,
    private val exportUseCase: ExportBookDataUseCase,
) : ViewModel(), EpubNavigatorFragment.Listener {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    /**
     * 引擎无关排版偏好（持久化驱动）。Default 初值避免首次空窗；Repository emit 后自动更新。
     */
    val typography: StateFlow<ReaderTypography> =
        typographyRepository.observe()
            .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderTypography.Default)

    /** READ-03：音量键翻页开关（派生自 typography；Fragment collect 后决定是否拦截音量键）。 */
    val volumeKeyPaging: StateFlow<Boolean> = typography
        .map { it.volumeKeyPaging }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /**
     * 引擎无关的显示/环境设置（TYPE-03 亮度/常亮/方向，持久化驱动）。
     * Default 初值避免首次空窗；ReaderScreen collect 后 apply 到 Window（退出 restore）。
     */
    val displaySettings: StateFlow<ReaderDisplaySettings> =
        displaySettingsRepository.observe()
            .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderDisplaySettings.Default)

    /** 当前系统是否暗色（由 ReaderScreen 据 isSystemInDarkTheme 推入），用于解析 [ReaderTheme.SYSTEM]。 */
    private val _systemDark = MutableStateFlow(false)

    /** SET-03：Android 系统字号倍率（由 ReaderScreen 据 LocalDensity.fontScale 推入），折算进 Readium fontSize。 */
    private val _systemFontScale = MutableStateFlow(1f)

    /**
     * 实际喂给 Readium 的排版偏好：[typography] 据系统暗色 + 系统字号倍率解析为 [EpubPreferences]
     * （[ReaderTheme.SYSTEM] → DARK/LIGHT；fontSize × systemFontScale → SET-03 跟随系统字号）。
     * ReaderFragment collect 本 flow → submitPreferences 实时生效。
     */
    val preferences: StateFlow<EpubPreferences> =
        combine(typography, _systemDark, _systemFontScale) { t, dark, scale -> t.toEpubPreferences(dark, scale) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderTypography.Default.toEpubPreferences(isSystemDark = false))

    /** 当前打开的书 id（Flow 源，供 bookmarks/annotations flatMapLatest 切书时重订阅）。 */
    private val _activeBookId = MutableStateFlow<String?>(null)
    /** 最近 Locator（Flow 源，供 isBookmarked 响应式判定；[latestLocator] 取值直读 .value）。 */
    private val _latestLocator = MutableStateFlow<Locator?>(null)

    /** 当前书书签列表（READ-06，DB 驱动回流）。 */
    val bookmarks: StateFlow<List<BookmarkItem>> = _activeBookId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else bookmarkRepository.observe(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 当前书活跃批注列表（READ-07，DB 驱动回流；红线 #9：渲染跑在落盘之后）。 */
    val annotations: StateFlow<List<AnnotationItem>> = _activeBookId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else annotationRepository.observe(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 高亮 Decoration：由 [annotations] 派生（id=批注 id，定位/颜色取自 DB）。打开书时已存高亮自动重现。 */
    val decorations: StateFlow<List<Decoration>> = annotations
        .map { list ->
            list.map { Decoration(id = it.id, locator = it.locator, style = Decoration.Style.Highlight(tint = it.color.toTintColor())) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 当前位置是否已加书签（TopBar toggle 图标用；与 toggleBookmark 同一套 locator 等价判定）。 */
    val isBookmarked: StateFlow<Boolean> = combine(bookmarks, _latestLocator) { list, loc ->
        if (loc == null) false else list.any { sameBookmarkPosition(it.locator, loc) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 全书进度展示（派生自 Locator.totalProgression，红线 #1）。 */
    private val _progressText = MutableStateFlow("0%")
    val progressText: StateFlow<String> = _progressText.asStateFlow()

    /** 当前 Publication 的能力矩阵（红线 #2：UI 据此 gating，不按扩展名承诺能力）。 */
    private val _capabilities = MutableStateFlow(ReaderCapabilities.forEpub())
    val capabilities: StateFlow<ReaderCapabilities> = _capabilities.asStateFlow()

    /** 目录（READ-02）：openPublication 成功时从 publication.tableOfContents 扁平化（含嵌套 depth）。 */
    private val _tableOfContents = MutableStateFlow<List<TocItem>>(emptyList())
    val tableOfContents: StateFlow<List<TocItem>> = _tableOfContents.asStateFlow()

    /** 全书进度浮点（0.0..1.0，供进度拖动 Slider，派生自 Locator.totalProgression）。 */
    private val _progression = MutableStateFlow(0.0)
    val progression: StateFlow<Double> = _progression.asStateFlow()

    /** 跳转指令流（VM 发出 → ReaderFragment 执行 navigator 调用，READ-02）。 */
    private val _navCommands = Channel<ReaderNavCommand>(Channel.BUFFERED)
    val navCommands: Flow<ReaderNavCommand> = _navCommands.receiveAsFlow()

    // ===== 书内搜索（READ-05：publication.search → SearchIterator 分批 → 上下文 + 跳转）=====

    /** 搜索 UI 状态（Idle / Loading / Results / Error）。 */
    private val _searchState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    /** 活跃搜索迭代器（分页拉取；切书 / 清空 / onCleared 时 close 释放）。 */
    private var searchIterator: SearchIterator? = null

    /** 当前搜索协程（新搜索取消旧的，避免并发竞态）。 */
    private var searchJob: Job? = null

    /** 跳转历史栈（READ-02：目录/进度跳转后可返回上一位置；Navigator 无 history，应用层自管）。 */
    private val jumpHistory = ArrayDeque<Locator>()
    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    private val currentBookId: String? get() = _activeBookId.value
    private val latestLocator: Locator? get() = _latestLocator.value
    private var persistJob: Job? = null
    private var openJob: Job? = null

    /**
     * 打开指定 [bookId] 的书。幂等：同 bookId 不重复 open（旋转重建时不会重复打开）。
     */
    fun openBook(bookId: String) {
        if (bookId == currentBookId) return

        // REL-05 性能基线：首开计时起点（debug 树落 logcat，release 无树即 no-op）。
        Timber.i("PERF_READER_OPEN_START bookId=$bookId")
        // 切书前用旧 bookId 保存旧 Locator；随后任何旧 Navigator 迟到回调都会被来源校验拒绝。
        val previousBookId = currentBookId
        val previousLocator = latestLocator
        persistJob?.cancel()
        persistJob = null
        if (previousBookId != null && previousLocator != null) {
            viewModelScope.launch { progressRepository.save(previousBookId, previousLocator) }
        }

        openJob?.cancel()
        (_uiState.value as? ReaderUiState.Ready)?.publication?.close()
        // 必须同步进入 Loading：ReaderFragment.onCreate 紧接着会选择恢复 factory，不能再看到旧 Ready。
        _uiState.value = ReaderUiState.Loading
        _activeBookId.value = bookId // 驱动 bookmarks/annotations 重新订阅新书
        _latestLocator.value = null
        _progressText.value = "0%"
        _progression.value = 0.0
        openPublication(bookId)
    }

    /** 新建 ReaderFragment 时优先恢复当前会话的最新位置，而不是首次打开时的旧快照。 */
    fun restoreLocatorForNavigator(state: ReaderUiState.Ready): Locator? =
        selectNavigatorRestoreLocator(
            activeBookId = currentBookId,
            readyBookId = state.bookId,
            latestLocator = latestLocator,
            initialLocator = state.initialLocator,
        )

    private fun openPublication(bookId: String) {
        openJob = viewModelScope.launch {
            // 切书重置跳转状态（目录 / 历史 / 进度）+ 清空搜索（释放旧 iterator）
            _tableOfContents.value = emptyList()
            jumpHistory.clear()
            _canGoBack.value = false
            clearSearch()

            val book = bookRepository.getById(bookId) ?: run {
                _uiState.value = ReaderUiState.Error(UserMessage.Res(R.string.error_book_missing, listOf(bookId)))
                return@launch
            }
            val file = File(book.filePath)

            // 打开层按 filePath 扩展名分流（P0V-05 论证：打开层非能力层）；.txt 先转 EPUB 缓存再打开。
            val result = if (book.filePath.endsWith(".txt", ignoreCase = true)) {
                openTxtUseCase.open(file, contentHash = book.contentHash)
            } else {
                openBookUseCase.open(file)
            }

            result
                .onSuccess { publication ->
                    // 快速切书时旧打开任务可能迟到；不发布旧状态，并主动释放 Publication。
                    if (bookId != currentBookId) {
                        publication.close()
                        return@onSuccess
                    }
                    // 能力来自打开后的 Publication（conformsTo + isSearchable），非扩展名（红线 #2）。
                    _capabilities.value = publication.toReaderCapabilities()
                    // 扁平化目录（含嵌套 children → depth 缩进）
                    _tableOfContents.value = flattenTableOfContents(publication.tableOfContents)
                    val savedLocator = progressRepository.getLocator(bookId) // Room 替代 LocatorStore
                    val initialLocator = savedLocator?.takeIf {
                        isLocatorInReadingOrder(it, publication.readingOrder)
                    }
                    // 历史版本可能把另一部书的 Locator 写进当前 bookId；不能再交给 Navigator 恢复。
                    if (savedLocator != null && initialLocator == null) {
                        progressRepository.delete(bookId)
                    }
                    bookRepository.markOpened(bookId) // lastOpenedAt + status=READING
                    if (bookId != currentBookId) {
                        publication.close()
                        return@onSuccess
                    }
                    val factory = EpubNavigatorFactory(publication)
                    _uiState.value = ReaderUiState.Ready(
                        bookId = bookId,
                        publication = publication,
                        navigatorFactory = factory,
                        initialLocator = initialLocator,
                        // 派生偏好的当前快照（含 SYSTEM 解析）；后续 Fragment 持续 collect preferences 覆盖。
                        preferences = preferences.value,
                    )
                    // REL-05 性能基线：Publication 已开 + 恢复 Locator 已就绪（渲染前）。
                    Timber.i("PERF_READER_OPEN_READY bookId=$bookId")
                }
                .onFailure { error ->
                    if (bookId == currentBookId) {
                        _uiState.value = ReaderUiState.Error(UserMessage.Raw(error.message))
                    }
                }
        }
    }

    // ===== currentLocator 落盘（防抖 1.5s，对齐 design.md §6.5）=====

    /** 由 ReaderFragment 订阅 navigator.currentLocator 后转发到此。 */
    fun onLocatorUpdated(sourceBookId: String, locator: Locator) {
        if (!acceptsLocator(currentBookId, sourceBookId)) return
        _latestLocator.value = locator
        val prog = locator.locations.totalProgression ?: 0.0
        _progressText.value = "${(prog * 100).toInt()}%"
        _progression.value = prog
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            progressRepository.save(currentBookId ?: return@launch, locator)
        }
    }

    /** 进入后台 / 退出时强制写入最新 locator（READ-08：后台/销毁前强制保存）。 */
    fun flushLocator() {
        persistJob?.cancel()
        val locator = latestLocator ?: return
        val bookId = currentBookId ?: return
        viewModelScope.launch { progressRepository.save(bookId, locator) }
    }

    // ===== 排版偏好（实时生效 + 持久化保位，design.md §4.4 TYPE-01/02）=====

    /** 推入系统暗色状态（ReaderScreen 据 isSystemInDarkTheme 调），用于解析 [ReaderTheme.SYSTEM]。 */
    fun setSystemDark(isDark: Boolean) {
        _systemDark.value = isDark
    }

    /** SET-03：推入系统字号倍率（ReaderScreen 据 LocalDensity.fontScale 调），折算进 Readium fontSize。 */
    fun setSystemFontScale(scale: Float) {
        _systemFontScale.value = scale
    }

    fun changeFontSize(delta: Double) = updateTypography {
        it.copy(fontSize = ((it.fontSize ?: 1.0) + delta).coerceIn(FONT_SIZE_MIN, FONT_SIZE_MAX))
    }

    fun changeLineHeight(delta: Double) = updateTypography {
        it.copy(lineHeight = ((it.lineHeight ?: 1.0) + delta).coerceIn(LINE_HEIGHT_MIN, LINE_HEIGHT_MAX))
    }

    fun changePageMargins(delta: Double) = updateTypography {
        it.copy(pageMargins = ((it.pageMargins ?: 1.0) + delta).coerceIn(PAGE_MARGINS_MIN, PAGE_MARGINS_MAX))
    }

    fun changeParagraphSpacing(delta: Double) = updateTypography {
        it.copy(paragraphSpacing = ((it.paragraphSpacing ?: 0.0) + delta).coerceIn(0.0, PARAGRAPH_SPACING_MAX))
    }

    // 绝对值 setter（排版面板 Slider 用，onValueChangeFinished 松手调一次，避免拖动高频写）。
    fun setFontSize(value: Double) = updateTypography {
        it.copy(fontSize = value.coerceIn(FONT_SIZE_MIN, FONT_SIZE_MAX))
    }
    fun setLineHeight(value: Double) = updateTypography {
        it.copy(lineHeight = value.coerceIn(LINE_HEIGHT_MIN, LINE_HEIGHT_MAX))
    }
    fun setPageMargins(value: Double) = updateTypography {
        it.copy(pageMargins = value.coerceIn(PAGE_MARGINS_MIN, PAGE_MARGINS_MAX))
    }
    fun setParagraphSpacing(value: Double) = updateTypography {
        it.copy(paragraphSpacing = value.coerceIn(0.0, PARAGRAPH_SPACING_MAX))
    }

    fun setTextAlign(align: ReaderTextAlign) = updateTypography { it.copy(textAlign = align) }

    fun setFontFamily(family: String?) = updateTypography { it.copy(fontFamily = family) }

    fun setTheme(theme: ReaderTheme) = updateTypography { it.copy(theme = theme) }

    /** READ-04：切换分页 / 纵向滚动（持久化 → submitPreferences 自动保 Locator 重排）。 */
    fun setScrollMode(mode: ReaderScrollMode) = updateTypography { it.copy(scroll = mode) }

    /** READ-03：开关音量键翻页（持久化 → Fragment collect 后决定是否拦截 KeyEvent）。 */
    fun setVolumeKeyPaging(enabled: Boolean) = updateTypography { it.copy(volumeKeyPaging = enabled) }

    /**
     * 写入持久化层；observe 自动回流 → [typography]/[preferences] 更新 → Fragment submitPreferences。
     * 单向数据流（不乐观更新内存），避免快速连点时内存与 DataStore 竞态回退。
     */
    private fun updateTypography(transform: (ReaderTypography) -> ReaderTypography) {
        viewModelScope.launch { typographyRepository.update(transform) }
    }

    // ===== 显示/环境设置（TYPE-03 亮度/常亮/方向，Window 层副作用，不传给 Readium 引擎）=====

    /** TYPE-03：设置亮度（null = 跟随系统）。 */
    fun setBrightness(brightness: Float?) = updateDisplaySettings {
        it.copy(brightness = brightness?.coerceIn(0f, 1f))
    }

    /** TYPE-03：开关屏幕常亮。 */
    fun setKeepScreenOn(enabled: Boolean) = updateDisplaySettings {
        it.copy(keepScreenOn = enabled)
    }

    /** TYPE-03：设置方向（null = 跟随系统）。 */
    fun setOrientation(orientation: ReaderOrientation?) = updateDisplaySettings {
        it.copy(orientation = orientation)
    }

    /**
     * 写入持久化层；observe 自动回流 → [displaySettings] 更新 → ReaderScreen apply 到 Window。
     * 单向数据流（与 [updateTypography] 同范式）。
     */
    private fun updateDisplaySettings(transform: (ReaderDisplaySettings) -> ReaderDisplaySettings) {
        viewModelScope.launch { displaySettingsRepository.update(transform) }
    }

    // ===== 高亮 / 笔记（READ-07，DB 驱动：先落盘 → observe 回流 → applyDecorations 渲染，红线 #9）=====
    // locator 必须来自文本选择（selection.locator）——含精确 DOM 文本范围，Readium 才能渲染高亮。
    // 页级 currentLocator 无文本范围、渲染不出（见 P0V-02 真机回归记录）。selectedText 在 Repository 取 locator.text.highlight。

    /** 新增高亮（先 Room 落盘；observe 回流自动驱动 applyDecorations，不乐观更新内存）。 */
    fun addHighlight(locator: Locator) {
        val bookId = currentBookId ?: return
        viewModelScope.launch { annotationRepository.add(bookId, locator) }
    }

    /** 软删除全部高亮（"清空"；observe 回流清 UI）。 */
    fun clearHighlights() {
        val bookId = currentBookId ?: return
        viewModelScope.launch { annotationRepository.softDeleteAllForBook(bookId) }
    }

    /** 软删除单条高亮。 */
    fun removeAnnotation(id: String) {
        viewModelScope.launch { annotationRepository.softDelete(id) }
    }

    /** 编辑笔记（覆盖 + 刷新 updatedAt；observe 回流更新列表）。 */
    fun updateAnnotationNote(id: String, note: String?) {
        viewModelScope.launch { annotationRepository.updateNote(id, note) }
    }

    /** 切换高亮颜色（落盘 → observe 回流 → decorations 派生自动重渲染高亮底色）。 */
    fun updateAnnotationColor(id: String, color: HighlightColor) {
        viewModelScope.launch { annotationRepository.updateColor(id, color) }
    }

    /** 跳到批注位置（先记当前位置到 history，再发指令）。 */
    fun jumpToAnnotation(item: AnnotationItem) = jumpToLocator(item.locator)

    // ===== 导出（DATA-01：单本书书签+高亮+笔记+进度 → Markdown/JSON，经 SAF 写 URI）=====

    /** 一次性导出结果事件（UI collect 后 Toast 反馈）。 */
    private val _exportEvents = Channel<ExportBookDataUseCase.Outcome>(Channel.BUFFERED)
    val exportEvents: Flow<ExportBookDataUseCase.Outcome> = _exportEvents.receiveAsFlow()

    /** 导出当前书为 [format]，写入 SAF 选定的 [uri]。 */
    fun exportBook(format: ExportBookDataUseCase.Format, uri: Uri) {
        val bookId = currentBookId ?: return
        viewModelScope.launch {
            _exportEvents.trySend(exportUseCase.export(bookId, format, uri))
        }
    }

    // ===== 书签（READ-06：添加 / 取消 / 列表 / 跳回，重复位置不重复生成）=====

    /** 在当前位置切换书签（Repository 按 locator 等价去重，重复位置 toggle off）。 */
    fun toggleBookmark() {
        val bookId = currentBookId ?: return
        val locator = latestLocator ?: return
        val excerpt = locator.text.after?.take(EXCERPT_MAX)?.trim()?.takeIf { it.isNotBlank() }
        viewModelScope.launch { bookmarkRepository.toggleBookmark(bookId, locator, excerpt) }
    }

    /** 删除指定书签。 */
    fun removeBookmark(id: String) {
        viewModelScope.launch { bookmarkRepository.delete(id) }
    }

    /** 删除当前书全部书签（书签面板"清空"）。 */
    fun removeBookmarksForCurrent() {
        val bookId = currentBookId ?: return
        viewModelScope.launch { bookmarkRepository.deleteAllForBook(bookId) }
    }

    /** 跳到书签位置（先记当前位置到 history，再发指令）。 */
    fun jumpToBookmark(item: BookmarkItem) = jumpToLocator(item.locator)

    /**
     * 跳到任意 [Locator]（书签 / 批注共用）：先 push 当前位置到 history（可返回），再发指令。
     */
    fun jumpToLocator(locator: Locator) {
        pushHistory()
        _navCommands.trySend(ReaderNavCommand.GoToLocator(locator))
    }

    /** 点击右边缘翻下一页（READ-03，分页模式；scroll 模式由 UI 不显示 tap 区）。 */
    fun goForwardPaging() {
        _navCommands.trySend(ReaderNavCommand.GoForward)
    }

    /** 点击左边缘翻上一页（READ-03，分页模式）。 */
    fun goBackwardPaging() {
        _navCommands.trySend(ReaderNavCommand.GoBackward)
    }

    /** locator 等价判定（书签去重 / isBookmarked 共用，对齐 BookmarkRepository.PROGRESSION_EPS）。 */
    private fun sameBookmarkPosition(a: Locator, b: Locator): Boolean {
        if (a.href != b.href) return false
        val ap = a.locations.totalProgression
        val bp = b.locations.totalProgression
        return when {
            ap == null && bp == null -> true
            ap != null && bp != null -> kotlin.math.abs(ap - bp) < PROGRESSION_EPS
            else -> false
        }
    }

    // ===== 目录 / 进度跳转（READ-02）=====

    /** 跳到目录章节：先记当前位置到 history（可返回），再发指令。 */
    fun jumpToLink(link: Link) {
        pushHistory()
        _navCommands.trySend(ReaderNavCommand.GoToLink(link))
    }

    /** 拖动到全书进度（0.0..1.0）：先记位置再发指令。 */
    fun jumpToProgression(progress: Double) {
        pushHistory()
        _navCommands.trySend(ReaderNavCommand.GoToProgression(progress.coerceIn(0.0, 1.0)))
    }

    /** 返回上一阅读位置（READ-02：跳转后可返回上一个阅读位置）。 */
    fun goBack() {
        val from = jumpHistory.removeLastOrNull() ?: return
        _canGoBack.value = jumpHistory.isNotEmpty()
        _navCommands.trySend(ReaderNavCommand.GoBack(from))
    }

    private fun pushHistory() {
        val current = latestLocator ?: return
        jumpHistory.addLast(current)
        while (jumpHistory.size > HISTORY_MAX) jumpHistory.removeFirst()
        _canGoBack.value = true
    }

    // ===== 书内搜索（READ-05：publication.search → SearchIterator 分批 → 上下文 + 跳转）=====

    /**
     * 搜索 [query]：空白清空；非空 → 取消旧搜索 + close 旧 iterator → [Publication][org.readium.r2.shared.publication.Publication].search
     * → 取首批结果（[mapLocators] 出上下文 + 命中词）。搜索选项用引擎默认（大小写不敏感，适配中英文混排）。
     * 失败给可理解错误（CLAUDE.md）。能搜索时入口由 [ReaderCapabilities.canSearch] gating（红线 #2）。
     */
    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            clearSearch()
            return
        }
        val publication = (uiState.value as? ReaderUiState.Ready)?.publication ?: return
        // 重置旧搜索：取消协程 + 释放旧 iterator（SearchIterator 是 Closeable，红线 #4 不留资源）。
        searchJob?.cancel()
        searchIterator?.close()
        searchIterator = null
        _searchState.value = SearchUiState.Loading(trimmed)
        searchJob = viewModelScope.launch {
            // Publication.search 返回 SearchIterator?（非 Try；失败返回 null，iterator.next() 才返回 Try<LocatorCollection, SearchError>）。
            // EPUB/TXT 经 isSearchable gating 已保证可搜；null 兜底给可理解提示。
            val iterator = publication.search(trimmed) ?: run {
                _searchState.value = SearchUiState.Error(UserMessage.Res(R.string.error_search))
                return@launch
            }
            searchIterator = iterator
            val count = iterator.resultCount
            val collection = iterator.next().getOrNull()
            if (collection == null) {
                _searchState.value = SearchUiState.Error(UserMessage.Res(R.string.error_search))
                return@launch
            }
            val items = mapLocators(collection.locators)
            _searchState.value = SearchUiState.Results(
                query = trimmed,
                resultCount = count,
                items = items,
                loadingMore = false,
                exhausted = items.isEmpty(),
            )
        }
    }

    /** 加载更多结果（READ-05 分批）：仅 Results 且未在加载 / 未耗尽时拉取 iterator.next()。 */
    fun loadMoreResults() {
        val iterator = searchIterator ?: return
        val current = _searchState.value as? SearchUiState.Results ?: return
        if (current.loadingMore || current.exhausted) return
        _searchState.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            val collection = iterator.next().getOrNull()
            val base = _searchState.value as? SearchUiState.Results ?: return@launch
            if (collection == null) {
                _searchState.value = base.copy(loadingMore = false)
                return@launch
            }
            val more = mapLocators(collection.locators)
            _searchState.value = base.copy(
                items = base.items + more,
                loadingMore = false,
                exhausted = more.isEmpty(),
                // iterator 遍历后 resultCount 可能更新为准确总数。
                resultCount = iterator.resultCount ?: base.resultCount,
            )
        }
    }

    /** 清空搜索（取消协程 + close iterator + 状态回 Idle）。切书 / onCleared 调。 */
    fun clearSearch() {
        searchJob?.cancel()
        searchJob = null
        searchIterator?.close()
        searchIterator = null
        _searchState.value = SearchUiState.Idle
    }

    // ===== EpubNavigatorFragment.Listener =====

    override fun onExternalLinkActivated(url: AbsoluteUrl) {
        // 红线 #4 + design.md §7：外链交系统浏览器，不在 WebView 内打开。
        // Phase 0 简化为直接打开；Phase 1 READ-09 加用户确认弹窗。
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url.toString()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    override fun onCleared() {
        super.onCleared()
        clearSearch() // 释放搜索 iterator（Closeable）
        (_uiState.value as? ReaderUiState.Ready)?.publication?.close()
    }

    private companion object {
        const val PERSIST_DEBOUNCE_MS = 1500L
        /** 跳转 history 最大深度（防内存膨胀）。 */
        const val HISTORY_MAX = 20
        /** 书签摘录最大长度（页级上下文，截 Locator.text.after）。 */
        const val EXCERPT_MAX = 80
        /** 书签 locator 等价判定的 progression 容差（与 BookmarkRepository 一致）。 */
        const val PROGRESSION_EPS = 1e-3
        // 排版数值范围（UI 滑块同此；null = 引擎默认，coerce 仅约束显式设置的值）。
        const val FONT_SIZE_MIN = 0.5
        const val FONT_SIZE_MAX = 5.0
        const val LINE_HEIGHT_MIN = 1.0
        const val LINE_HEIGHT_MAX = 3.0
        const val PAGE_MARGINS_MIN = 0.5
        const val PAGE_MARGINS_MAX = 4.0
        const val PARAGRAPH_SPACING_MAX = 3.0
    }
}
