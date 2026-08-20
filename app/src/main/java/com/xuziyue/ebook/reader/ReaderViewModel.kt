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
import com.xuziyue.ebook.data.BookTypographyOverrides
import com.xuziyue.ebook.data.BookTypographyRepository
import com.xuziyue.ebook.data.ReaderDisplaySettingsRepository
import com.xuziyue.ebook.data.ReaderTtsPreferencesRepository
import com.xuziyue.ebook.data.ReaderTypographyRepository
import com.xuziyue.ebook.data.ReadingProgressRepository
import com.xuziyue.ebook.data.ReadingSessionRepository
import com.xuziyue.ebook.data.export.ExportBookDataUseCase
import com.xuziyue.ebook.data.mergeTypography
import com.xuziyue.ebook.model.HighlightColor
import com.xuziyue.ebook.model.ReaderCapabilities
import com.xuziyue.ebook.model.ReaderDisplaySettings
import com.xuziyue.ebook.model.ReaderFormat
import com.xuziyue.ebook.model.ReaderOrientation
import com.xuziyue.ebook.model.ReaderScrollMode
import com.xuziyue.ebook.model.ReaderTextAlign
import com.xuziyue.ebook.model.ReaderTheme
import com.xuziyue.ebook.model.ReaderTypography
import com.xuziyue.ebook.reader.readium.OpenBookUseCase
import com.xuziyue.ebook.ui.UserMessage
import com.xuziyue.ebook.reader.readium.OpenTxtPublicationUseCase
import com.xuziyue.ebook.reader.readium.toEpubPreferences
import com.xuziyue.ebook.reader.readium.toPdfiumPreferences
import com.xuziyue.ebook.reader.readium.toReaderCapabilities
import com.xuziyue.ebook.reader.readium.toReaderFormat
import com.xuziyue.ebook.reader.tts.ReaderTtsManager
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
import org.readium.r2.navigator.HyperlinkNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.image.ImageNavigatorFragment
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.adapter.pdfium.navigator.PdfiumPreferences
import org.readium.navigator.media.tts.android.AndroidTtsEngine
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
    private val bookTypographyRepository: BookTypographyRepository,
    private val displaySettingsRepository: ReaderDisplaySettingsRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val annotationRepository: AnnotationRepository,
    private val sessionRepository: ReadingSessionRepository,
    private val exportUseCase: ExportBookDataUseCase,
    private val ttsPreferencesRepository: ReaderTtsPreferencesRepository,
    private val pdfEngineProvider: PdfiumEngineProvider,
) : ViewModel(), EpubNavigatorFragment.Listener, PdfNavigatorFragment.Listener, ImageNavigatorFragment.Listener {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    /**
     * 全局排版偏好（TYPE-01/02，持久化驱动）。Default 初值避免首次空窗；Repository emit 后自动更新。
     *
     * 注意：UI / Fragment 消费的是合并后的 [typography]（TYPE-05：全局 + 本书覆盖），
     * 本 flow 只作合并输入，不直接暴露给排版面板。
     */
    private val globalTypography: StateFlow<ReaderTypography> =
        typographyRepository.observe()
            .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderTypography.Default)

    /** 当前打开的书 id（Flow 源，供 bookmarks/annotations/bookOverride flatMapLatest 切书时重订阅）。 */
    private val _activeBookId = MutableStateFlow<String?>(null)

    /**
     * 本书排版覆盖（TYPE-05，DB 驱动回流；无覆盖行 = Empty，全跟全局）。
     */
    private val bookOverride: StateFlow<BookTypographyOverrides> = _activeBookId
        .flatMapLatest { id ->
            if (id == null) flowOf(BookTypographyOverrides.Empty)
            else bookTypographyRepository.observe(id)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, BookTypographyOverrides.Empty)

    /**
     * 本书生效排版（TYPE-05）：全局偏好经本书覆盖合并（覆盖非 null 字段压全局）。
     * 切书时 flatMapLatest 重订阅，合并结果自动切到新书的覆盖。
     */
    val typography: StateFlow<ReaderTypography> = combine(globalTypography, bookOverride) { g, o ->
        mergeTypography(g, o.values)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReaderTypography.Default)

    /** 本书是否有覆盖行（排版面板「恢复全局默认」按钮显示态；快照全默认值也视为有覆盖）。 */
    val hasBookOverride: StateFlow<Boolean> = bookOverride
        .map { it.values != BookTypographyOverrides.Empty.values }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

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

    /**
     * PDF（V1）：全局排版偏好的「翻页方式」映射为 PdfiumPreferences（scroll→scrollAxis）。
     * ReaderFragment 在 navigator 是 PDF 时 collect 本 flow → submitPreferences；
     * 其余格式不消费。固定版式的字号/字体/主题等不映射（canAdjustTypography=false 已 gating UI）。
     */
    val pdfPreferences: StateFlow<PdfiumPreferences> = typography
        .map { it.toPdfiumPreferences() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderTypography.Default.toPdfiumPreferences())

    /** 最近 Locator（Flow 源，供 isBookmarked 响应式判定；[latestLocator] 取值直读 .value）。 */
    private val _latestLocator = MutableStateFlow<Locator?>(null)

    // ===== DATA-04 阅读计时（会话起止 + 静止刷新） =====
    /** 当前会话 id（startSession 返回；统计关时为 null，后续计时钩子全部 no-op）。 */
    private var sessionId: String? = null

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

    /**
     * 控制栏（顶/底栏）显隐切换信号（READ-02）。
     *
     * 方向：ReaderFragment 的 Readium [org.readium.r2.navigator.input.InputListener.onTap]（WebView 层）
     * → [requestToggleBars] → ReaderScreen collect → 翻转本地 barsVisible。
     * 为何走通道而非把 barsVisible 存进 VM：barsVisible 留在 Composable 本地（rememberSaveable）
     * 以保「每次进入先显示一次 + 跨重建保位」（Q7a）；onTap 必须在 WebView 层挂钩——Compose 兄弟节点
     * 只要挂 pointerInput 就独占手势、挡住 WebView 长按选词（READ-07），不能在 Compose 贴中央 overlay。
     */
    private val _barsToggleEvents = Channel<Unit>(Channel.BUFFERED)
    val barsToggleEvents: Flow<Unit> = _barsToggleEvents.receiveAsFlow()

    /** 由 Readium InputListener.onTap 调用，请求 ReaderScreen 翻转控制栏显隐。 */
    fun requestToggleBars() {
        _barsToggleEvents.trySend(Unit)
    }

    // ===== 书内搜索（READ-05：publication.search → SearchIterator 分批 → 上下文 + 跳转）=====

    /** 搜索 UI 状态（Idle / Loading / Results / Error）。 */
    private val _searchState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    /** 活跃搜索迭代器（分页拉取；切书 / 清空 / onCleared 时 close 释放）。 */
    private var searchIterator: SearchIterator? = null

    /** 当前搜索协程（新搜索取消旧的，避免并发竞态）。 */
    private var searchJob: Job? = null

    /** 跳转历史双栈（READ-09：前进/后退；READ-02 时单向栈只能后退的升级）。 */
    private val jumpHistory = JumpHistory()
    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()
    private val _canGoForward = MutableStateFlow(false)
    val canGoForward: StateFlow<Boolean> = _canGoForward.asStateFlow()

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
        // DATA-04：切书前结束旧会话计时并落盘。
        endSessionIfActive()
        // READ-10：切书关旧 TTS 会话（Publication 是会话的文本源，不能跨书复用）。
        closeTts()

        openJob?.cancel()
        (_uiState.value as? ReaderUiState.Ready)?.publication?.close()
        // 必须同步进入 Loading：ReaderFragment.onCreate 紧接着会选择恢复 factory，不能再看到旧 Ready。
        _uiState.value = ReaderUiState.Loading
        _activeBookId.value = bookId // 驱动 bookmarks/annotations/bookOverride 重新订阅新书
        _latestLocator.value = null
        _progressText.value = "0%"
        _progression.value = 0.0
        _perBookTypography.value = false // TYPE-05：按书开关是会话内状态，切书重置（重开书按已有覆盖行显示）
        openPublication(bookId)
    }

    /** 新建 ReaderFragment 时优先恢复当前会话的最新位置，而不是首次打开时的旧快照。 */
    fun restoreLocatorForNavigator(state: ReaderUiState.Ready): Locator? =
        selectNavigatorRestoreLocator(
            activeBookId = currentBookId,
            readyBookId = state.bookId,
            latestLocator = latestLocator,
            initialLocator = state.navigatorSpec.initialLocator,
        )

    /**
     * 进程重建时 ReaderFragment 复合 dummy factory 需要 PdfiumEngineProvider
     * （PdfNavigatorFragment.createDummyFactory 的入参）；直接透传注入的单例。
     */
    fun pdfEngineProviderForRestore() = pdfEngineProvider

    private fun openPublication(bookId: String) {
        openJob = viewModelScope.launch {
            // 切书重置跳转状态（目录 / 历史 / 进度）+ 链接弹层 + 清空搜索（释放旧 iterator）
            _tableOfContents.value = emptyList()
            jumpHistory.clear()
            _canGoBack.value = false
            _canGoForward.value = false
            _linkDialog.value = null
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
                    // 扁平化目录（含嵌套 children → depth 缩进）；PDF 取 outline（PdfiumDocument），
                    // CBZ 无目录语义（canToc=false 已 gating 入口）。
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
                    // DATA-04：会话起点（统计关时 startSession 返回 null，后续计时 no-op）。
                    viewModelScope.launch { sessionId = sessionRepository.startSession(bookId) }
                    if (bookId != currentBookId) {
                        publication.close()
                        return@onSuccess
                    }
                    // V1 PDF/CBZ：按 Publication 实际格式构造 NavigatorSpec（打开层分流，非能力层）。
                    val spec = when (publication.toReaderFormat()) {
                        ReaderFormat.PDF -> NavigatorSpec.Pdf(
                            publication = publication,
                            engineProvider = pdfEngineProvider,
                            // 派生偏好的当前快照；后续 Fragment 持续 collect pdfPreferences 覆盖。
                            initialPreferences = pdfPreferences.value,
                            initialLocator = initialLocator,
                        )
                        ReaderFormat.CBZ -> NavigatorSpec.Cbz(
                            publication = publication,
                            initialLocator = initialLocator,
                        )
                        // EPUB（含 TXT 转 EPUB）：能力层与打开层均等同 EPUB。
                        ReaderFormat.EPUB -> NavigatorSpec.Epub(
                            publication = publication,
                            navigatorFactory = EpubNavigatorFactory(publication),
                            // 派生偏好的当前快照（含 SYSTEM 解析）；后续 Fragment 持续 collect preferences 覆盖。
                            initialPreferences = preferences.value,
                            initialLocator = initialLocator,
                        )
                    }
                    _uiState.value = ReaderUiState.Ready(
                        bookId = bookId,
                        publication = publication,
                        navigatorSpec = spec,
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
        // DATA-04：翻页 / 滚动即活跃信号，刷新会话 lastActiveAt（仅内存）。
        sessionRepository.touchActive(sessionId)
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

    // ===== DATA-04 阅读计时：会话生命周期（onPause 结束 / onResume 续接） =====

    /** 阅读页失去前台（onPause）时结束会话并落盘。切后台 / 弹通知栏 / 退出都走这里。 */
    fun onReaderPaused() {
        endSessionIfActive()
    }

    /**
     * 阅读页恢复前台（onResume）时若仍有活跃书但会话已结束（sessionId==null），重新开始会话。
     * 避免 onPause 置空后「切通知栏回来继续读同一本」的时间丢失。
     */
    fun onReaderResumed() {
        if (sessionId == null && currentBookId != null) {
            viewModelScope.launch { sessionId = sessionRepository.startSession(currentBookId!!) }
        }
    }

    /** 结束当前会话并落盘（差值法结算 activeSeconds）；无活跃会话则 no-op。 */
    private fun endSessionIfActive() {
        if (sessionId == null) return
        val id = sessionId
        sessionId = null
        viewModelScope.launch { sessionRepository.endSession(id) }
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
    fun setFontWeight(value: Double) = updateTypography {
        it.copy(fontWeight = value.coerceIn(FONT_WEIGHT_MIN, FONT_WEIGHT_MAX))
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
     *
     * TYPE-05：[perBookTypography] 开时写入本书覆盖（BookTypographyRepository），
     * 关时写全局（ReaderTypographyRepository）——面板同一组 setter，路由由开关决定。
     */
    private fun updateTypography(transform: (ReaderTypography) -> ReaderTypography) {
        val bookId = currentBookId
        if (bookId != null && perBookTypography.value) {
            viewModelScope.launch {
                bookTypographyRepository.update(bookId) { overrides ->
                    overrides.copy(values = transform(overrides.values))
                }
            }
        } else {
            viewModelScope.launch { typographyRepository.update(transform) }
        }
    }

    // ===== 按书排版开关（TYPE-05）=====

    /** 排版改动是否只写本书（内存标志 + 持久化到本书覆盖行由首个 setter 落盘）。 */
    private val _perBookTypography = MutableStateFlow(false)
    val perBookTypography: StateFlow<Boolean> = _perBookTypography.asStateFlow()

    /**
     * 开「仅本书生效」：把**当前生效排版的快照**作为本书覆盖的起点。
     * 快照语义（非空覆盖）：此后本书改动只影响本书；全局改其它字段不影响本书（已全部覆盖）。
     * 这与 partial override 的「未动字段跟全局」在开关打开瞬间收敛为一致行为，避免
     * 「开了开关但只有部分字段覆盖、全局又变」的混乱中间态。
     */
    fun enablePerBookTypography() {
        val bookId = currentBookId ?: return
        val snapshot = typography.value
        _perBookTypography.value = true
        viewModelScope.launch {
            bookTypographyRepository.update(bookId) { it.copy(values = snapshot) }
        }
    }

    /** 关「仅本书生效」：后续改动回写全局；已落盘的本书覆盖保留（重开书仍生效）但不追加。 */
    fun disablePerBookTypography() {
        _perBookTypography.value = false
    }

    /** 「恢复全局默认」（TYPE-05 验收）：删本书覆盖行，本书立即回到纯全局排版。 */
    fun resetBookTypography() {
        val bookId = currentBookId ?: return
        viewModelScope.launch { bookTypographyRepository.clear(bookId) }
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
     * 跳到任意 [Locator]（书签 / 批注 / 搜索结果共用）：先记当前位置到 history（可返回），再发指令。
     */
    fun jumpToLocator(locator: Locator) {
        recordJumpHistory()
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

    // ===== 目录 / 进度跳转（READ-02）+ 历史前进/后退（READ-09）=====

    /** 跳到目录章节：先记当前位置到 history（可返回），再发指令。 */
    fun jumpToLink(link: Link) {
        recordJumpHistory()
        _navCommands.trySend(ReaderNavCommand.GoToLink(link))
    }

    /** 拖动到全书进度（0.0..1.0）：先记位置再发指令。 */
    fun jumpToProgression(progress: Double) {
        recordJumpHistory()
        _navCommands.trySend(ReaderNavCommand.GoToProgression(progress.coerceIn(0.0, 1.0)))
    }

    /** 返回上一阅读位置（READ-09：current 进前进栈，可再前进）。 */
    fun goBack() {
        val target = jumpHistory.back(current = latestLocator) ?: return
        syncHistoryFlags()
        _navCommands.trySend(ReaderNavCommand.GoBack(target))
    }

    /** 前进到被 back 撤销的位置（READ-09 新增；current 回压后备栈）。 */
    fun goForward() {
        val target = jumpHistory.forward(current = latestLocator) ?: return
        syncHistoryFlags()
        _navCommands.trySend(ReaderNavCommand.GoBack(target))
    }

    private fun recordJumpHistory() {
        jumpHistory.recordJump(current = latestLocator)
        syncHistoryFlags()
    }

    private fun syncHistoryFlags() {
        _canGoBack.value = jumpHistory.canGoBack
        _canGoForward.value = jumpHistory.canGoForward
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

    // ===== TTS 朗读（READ-10：ReaderTtsManager 封装 Readium TTS 细节，VM 只做路由与集成）=====

    private var ttsManager: ReaderTtsManager? = null

    /** 当前活跃的 TTS 会话管理器（null = 未创建）。UI 派生流（voices/timer）以此为源，随会话创建/关闭联动。 */
    private val _activeTtsManager = MutableStateFlow<ReaderTtsManager?>(null)

    /** TTS 播放态（UI 面板 + Fragment 音量键放行共用）。 */
    val ttsPlaying: StateFlow<Boolean> get() = _ttsPlaying
    private val _ttsPlaying = MutableStateFlow(false)

    /** 当前朗读句 Locator（null = 未播）。 */
    val ttsUtterance: StateFlow<Locator?> get() = _ttsUtterance
    private val _ttsUtterance = MutableStateFlow<Locator?>(null)

    /** TTS 一次性事件（Toast / 拉起语音下载）。 */
    val ttsEvents: StateFlow<ReaderTtsManager.Event?> get() = _ttsEvents
    private val _ttsEvents = MutableStateFlow<ReaderTtsManager.Event?>(null)

    private fun ensureTtsManager(): ReaderTtsManager? {
        val state = _uiState.value as? ReaderUiState.Ready ?: return null
        ttsManager?.let { return it }
        val manager = ReaderTtsManager(
            context = context,
            scope = viewModelScope,
            publication = state.publication,
            preferencesRepository = ttsPreferencesRepository,
        )
        ttsManager = manager
        _activeTtsManager.value = manager
        viewModelScope.launch {
            manager.isPlaying.collect { _ttsPlaying.value = it }
        }
        viewModelScope.launch {
            manager.utteranceLocator.collect { _ttsUtterance.value = it }
        }
        viewModelScope.launch {
            manager.events.collect { event -> if (event != null) _ttsEvents.value = event }
        }
        return manager
    }

    /** 开始朗读（从当前页首可见元素起）。READ-10 入口按 [ReaderCapabilities.canTts] gating（红线 #2）。 */
    fun startTts() {
        val manager = ensureTtsManager() ?: return
        manager.start(latestLocator)
    }

    fun pauseTts() {
        ttsManager?.pause()
    }

    fun skipPreviousTts() {
        ttsManager?.previousUtterance()
    }

    fun skipNextTts() {
        ttsManager?.nextUtterance()
    }

    fun setTtsSpeed(speed: Double) {
        ttsManager?.setSpeed(speed)
    }

    fun setTtsVoice(voiceId: String?) {
        ttsManager?.setVoice(voiceId)
    }

    fun setTtsTimer(minutes: Int) {
        ttsManager?.setTimer(minutes)
    }

    fun requestTtsInstallVoice() {
        ttsManager?.requestInstallVoice()
    }

    /** 消费一次性事件（UI collect 后调，防重复 Toast）。 */
    fun consumeTtsEvent() {
        _ttsEvents.value = null
    }

    /** TTS 偏好（语速/发音人/定时；面板初始化用）。 */
    val ttsPreferences: StateFlow<ReaderTtsPreferencesRepository.TtsPrefs?> = ttsPreferencesRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 可选发音人列表（TTS 会话就绪后非空；随会话创建/关闭动态派生，修复审查严重问题 #6）。 */
    val ttsVoices: StateFlow<List<AndroidTtsEngine.Voice>> =
        _activeTtsManager.flatMapLatest { it?.voices ?: flowOf(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** TTS 定时当前值（分钟；随会话动态派生）。 */
    val ttsTimerMinutes: StateFlow<Int> =
        _activeTtsManager.flatMapLatest { it?.timerMinutes ?: flowOf(0) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** 关闭 TTS 会话（切书 / onCleared；进程内页面播放口径，退出阅读页即停）。 */
    private fun closeTts() {
        ttsManager?.close()
        ttsManager = null
        _activeTtsManager.value = null
        _ttsPlaying.value = false
        _ttsUtterance.value = null
    }

    // ===== EpubNavigatorFragment.Listener（READ-09：脚注弹层 + 链接确认）=====

    /** 当前链接交互弹层（null = 无）；UI collect 渲染，dismiss 置 null。 */
    private val _linkDialog = MutableStateFlow<LinkDialog?>(null)
    val linkDialog: StateFlow<LinkDialog?> = _linkDialog.asStateFlow()

    /**
     * 内链询问（READ-09 脚注弹层核心）：返回 false 拦截库的默认跳转，改由 app 展示。
     * - [HyperlinkNavigator.FootnoteContext]（库已取好并清洗 aside 脚注内容）→ [LinkDialog.Footnote] 弹层；
     * - 无上下文的普通内链（含 EPUB2 无 epub:type 的旧式脚注）→ [LinkDialog.InternalLink] 确认后跳转。
     */
    override fun shouldFollowInternalLink(link: Link, context: HyperlinkNavigator.LinkContext?): Boolean {
        val footnote = (context as? HyperlinkNavigator.FootnoteContext)?.noteContent
        _linkDialog.value = internalLinkDialog(link, footnote)
        return false
    }

    /** 关闭链接弹层（脚注关闭 / 确认框取消共用）。 */
    fun dismissLinkDialog() {
        _linkDialog.value = null
    }

    /** 内链确认「跳转」：记历史后按普通目录跳转执行（READ-09 验收「脚注跳转可返回」）。 */
    fun confirmInternalLink(link: Link) {
        _linkDialog.value = null
        jumpToLink(link)
    }

    override fun onExternalLinkActivated(url: AbsoluteUrl) {
        // 红线 #4 + design.md §7：外链不经确认不得打开（Phase 0 直接 startActivity 是过渡实现）。
        // 确认后 [confirmExternalLink] 交系统浏览器，绝不在 WebView 内打开。
        _linkDialog.value = LinkDialog.ExternalLink(url)
    }

    /** 外链确认「继续打开」：交系统浏览器（READ-09 验收「外链不会静默打开」）。 */
    fun confirmExternalLink() {
        val url = (_linkDialog.value as? LinkDialog.ExternalLink)?.url
        _linkDialog.value = null
        if (url == null) return
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url.toString()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    override fun onCleared() {
        super.onCleared()
        clearSearch() // 释放搜索 iterator（Closeable）
        closeTts() // READ-10：释放 TTS 会话（TtsPlayer + MediaSession 适配器）
        endSessionIfActive() // DATA-04：VM 销毁时兜底结束会话（强杀时主要靠 onPause）
        (_uiState.value as? ReaderUiState.Ready)?.publication?.close()
    }

    private companion object {
        const val PERSIST_DEBOUNCE_MS = 1500L
        /** 书签 locator 等价判定的 progression 容差（与 BookmarkRepository 一致）。 */
        const val PROGRESSION_EPS = 1e-3
        /** 书签摘录最大长度（页级上下文，截 Locator.text.after）。 */
        const val EXCERPT_MAX = 80
        // 排版数值范围（UI 滑块同此；null = 引擎默认，coerce 仅约束显式设置的值）。
        const val FONT_SIZE_MIN = 0.5
        const val FONT_SIZE_MAX = 5.0
        // 字重（TYPE-05 补 TYPE-01 欠账）：Readium 归一化 0.0–2.5（非 CSS 100–900），UI 只露常用段。
        const val FONT_WEIGHT_MIN = 0.75
        const val FONT_WEIGHT_MAX = 1.75
        const val LINE_HEIGHT_MIN = 1.0
        const val LINE_HEIGHT_MAX = 3.0
        const val PAGE_MARGINS_MIN = 0.5
        const val PAGE_MARGINS_MAX = 4.0
        const val PARAGRAPH_SPACING_MAX = 3.0
    }
}
