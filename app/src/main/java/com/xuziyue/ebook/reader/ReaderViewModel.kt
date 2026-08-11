package com.xuziyue.ebook.reader

import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xuziyue.ebook.data.BookRepository
import com.xuziyue.ebook.data.ReaderTypographyRepository
import com.xuziyue.ebook.data.ReadingProgressRepository
import com.xuziyue.ebook.model.ReaderCapabilities
import com.xuziyue.ebook.model.ReaderTextAlign
import com.xuziyue.ebook.model.ReaderTheme
import com.xuziyue.ebook.model.ReaderTypography
import com.xuziyue.ebook.reader.readium.OpenBookUseCase
import com.xuziyue.ebook.reader.readium.OpenTxtPublicationUseCase
import com.xuziyue.ebook.reader.readium.toEpubPreferences
import com.xuziyue.ebook.reader.readium.toReaderCapabilities
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
@OptIn(ExperimentalReadiumApi::class)
@HiltViewModel
class ReaderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val openBookUseCase: OpenBookUseCase,
    private val openTxtUseCase: OpenTxtPublicationUseCase,
    private val bookRepository: BookRepository,
    private val progressRepository: ReadingProgressRepository,
    private val typographyRepository: ReaderTypographyRepository,
) : ViewModel(), EpubNavigatorFragment.Listener {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    /**
     * 引擎无关排版偏好（持久化驱动）。Default 初值避免首次空窗；Repository emit 后自动更新。
     */
    val typography: StateFlow<ReaderTypography> =
        typographyRepository.observe()
            .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderTypography.Default)

    /** 当前系统是否暗色（由 ReaderScreen 据 isSystemInDarkTheme 推入），用于解析 [ReaderTheme.SYSTEM]。 */
    private val _systemDark = MutableStateFlow(false)

    /**
     * 实际喂给 Readium 的排版偏好：[typography] 据系统暗色解析为 [EpubPreferences]
     * （[ReaderTheme.SYSTEM] → DARK/LIGHT）。ReaderFragment collect 本 flow → submitPreferences 实时生效。
     */
    val preferences: StateFlow<EpubPreferences> =
        combine(typography, _systemDark) { t, dark -> t.toEpubPreferences(dark) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderTypography.Default.toEpubPreferences(isSystemDark = false))

    /** 高亮 Decoration（Phase 0 内存验证渲染；批注先落盘留 Phase 1 READ-07）。 */
    private val _decorations = MutableStateFlow<List<Decoration>>(emptyList())
    val decorations: StateFlow<List<Decoration>> = _decorations.asStateFlow()

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

    /** 跳转历史栈（READ-02：目录/进度跳转后可返回上一位置；Navigator 无 history，应用层自管）。 */
    private val jumpHistory = ArrayDeque<Locator>()
    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    private var currentBookId: String? = null
    private var latestLocator: Locator? = null
    private var persistJob: Job? = null
    /** 高亮 id 序列（递增，避免快速连续加高亮时 id 冲突）。 */
    private var highlightSeq = 0L

    /**
     * 打开指定 [bookId] 的书。幂等：同 bookId 不重复 open（旋转重建时不会重复打开）。
     */
    fun openBook(bookId: String) {
        if (bookId == currentBookId) return
        // 切换书：close 上一本（若有）
        (_uiState.value as? ReaderUiState.Ready)?.publication?.close()
        currentBookId = bookId
        openPublication(bookId)
    }

    private fun openPublication(bookId: String) {
        viewModelScope.launch {
            _uiState.value = ReaderUiState.Loading
            // 切书重置跳转状态（目录 / 历史 / 进度）
            _tableOfContents.value = emptyList()
            jumpHistory.clear()
            _canGoBack.value = false

            val book = bookRepository.getById(bookId) ?: run {
                _uiState.value = ReaderUiState.Error("书籍不存在：$bookId")
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
                    // 能力来自打开后的 Publication（conformsTo + isSearchable），非扩展名（红线 #2）。
                    _capabilities.value = publication.toReaderCapabilities()
                    // 扁平化目录（含嵌套 children → depth 缩进）
                    _tableOfContents.value = flattenTableOfContents(publication.tableOfContents)
                    val savedLocator = progressRepository.getLocator(bookId) // Room 替代 LocatorStore
                    bookRepository.markOpened(bookId) // lastOpenedAt + status=READING
                    val factory = EpubNavigatorFactory(publication)
                    _uiState.value = ReaderUiState.Ready(
                        publication = publication,
                        navigatorFactory = factory,
                        initialLocator = savedLocator,
                        // 派生偏好的当前快照（含 SYSTEM 解析）；后续 Fragment 持续 collect preferences 覆盖。
                        preferences = preferences.value,
                    )
                }
                .onFailure { error ->
                    _uiState.value = ReaderUiState.Error(error.message)
                }
        }
    }

    // ===== currentLocator 落盘（防抖 1.5s，对齐 design.md §6.5）=====

    /** 由 ReaderFragment 订阅 navigator.currentLocator 后转发到此。 */
    fun onLocatorUpdated(locator: Locator) {
        latestLocator = locator
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

    /**
     * 写入持久化层；observe 自动回流 → [typography]/[preferences] 更新 → Fragment submitPreferences。
     * 单向数据流（不乐观更新内存），避免快速连点时内存与 DataStore 竞态回退。
     */
    private fun updateTypography(transform: (ReaderTypography) -> ReaderTypography) {
        viewModelScope.launch { typographyRepository.update(transform) }
    }

    // ===== 高亮（Phase 0 验证 Decoration 渲染）=====
    // locator 必须来自文本选择（selection.locator）——含精确 DOM 文本范围，Readium 才能渲染高亮。
    // 页级 currentLocator 无文本范围、渲染不出（见 P0V-02 真机回归记录）。

    fun addHighlight(locator: Locator) {
        val decoration = Decoration(
            id = "hl-${highlightSeq++}",
            locator = locator,
            style = Decoration.Style.Highlight(tint = Color.YELLOW),
        )
        _decorations.value = _decorations.value + decoration
    }

    fun clearHighlights() {
        _decorations.value = emptyList()
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
        (_uiState.value as? ReaderUiState.Ready)?.publication?.close()
    }

    private companion object {
        const val PERSIST_DEBOUNCE_MS = 1500L
        /** 跳转 history 最大深度（防内存膨胀）。 */
        const val HISTORY_MAX = 20
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
