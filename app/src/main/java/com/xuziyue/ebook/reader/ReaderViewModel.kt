package com.xuziyue.ebook.reader

import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xuziyue.ebook.data.BookRepository
import com.xuziyue.ebook.data.ReadingProgressRepository
import com.xuziyue.ebook.model.ReaderCapabilities
import com.xuziyue.ebook.reader.readium.OpenBookUseCase
import com.xuziyue.ebook.reader.readium.OpenTxtPublicationUseCase
import com.xuziyue.ebook.reader.readium.toReaderCapabilities
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.AbsoluteUrl
import java.io.File

/**
 * Reader 的核心 ViewModel（命门）。
 *
 * 职责：
 * 1. 管理 [Publication][org.readium.r2.shared.publication.Publication] 生命周期（open / close / 进程重建重 open）。
 * 2. 协调 [Locator] 恢复——从 [ReadingProgressRepository]（Room）读最近位置作 initialLocator，currentLocator 变化防抖落盘。
 * 3. 持有排版偏好（[EpubPreferences]）与高亮（[Decoration]）StateFlow，供 Compose 控制条与 Fragment 订阅。
 * 4. 实现 [EpubNavigatorFragment.Listener]（外链交系统浏览器，红线 #4 + design.md §7）。
 *
 * **Scope 决策**：绑 Activity scope（[com.xuziyue.ebook.MainActivity]）。这样 Compose 的 ReaderScreen 与
 * 嵌入的 ReaderFragment 通过 activityViewModels() 共享同一实例。contentHash 不依赖 SavedStateHandle，
 * 而由 [openBook] 传入——进程重建后 Navigation 恢复 route，ReaderScreen 重建调 openBook 重 open。
 * Phase 0 简化：返回书库时 publication 不立即 close（onCleared 或下次 openBook 时 close）。
 */
@OptIn(ExperimentalReadiumApi::class)
@HiltViewModel
class ReaderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val openBookUseCase: OpenBookUseCase,
    private val openTxtUseCase: OpenTxtPublicationUseCase,
    private val bookRepository: BookRepository,
    private val progressRepository: ReadingProgressRepository,
) : ViewModel(), EpubNavigatorFragment.Listener {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    /** 排版偏好（Phase 0 内存态；完整跨书持久化留 Phase 1 TYPE）。 */
    private val _preferences = MutableStateFlow(EpubPreferences())
    val preferences: StateFlow<EpubPreferences> = _preferences.asStateFlow()

    /** 高亮 Decoration（Phase 0 内存验证渲染；批注先落盘留 Phase 1 READ-07）。 */
    private val _decorations = MutableStateFlow<List<Decoration>>(emptyList())
    val decorations: StateFlow<List<Decoration>> = _decorations.asStateFlow()

    /** 全书进度展示（派生自 Locator.totalProgression，红线 #1）。 */
    private val _progressText = MutableStateFlow("0%")
    val progressText: StateFlow<String> = _progressText.asStateFlow()

    /** 当前 Publication 的能力矩阵（红线 #2：UI 据此 gating，不按扩展名承诺能力）。 */
    private val _capabilities = MutableStateFlow(ReaderCapabilities.forEpub())
    val capabilities: StateFlow<ReaderCapabilities> = _capabilities.asStateFlow()

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
                    val savedLocator = progressRepository.getLocator(bookId) // Room 替代 LocatorStore
                    bookRepository.markOpened(bookId) // lastOpenedAt + status=READING
                    val factory = EpubNavigatorFactory(publication)
                    _uiState.value = ReaderUiState.Ready(
                        publication = publication,
                        navigatorFactory = factory,
                        initialLocator = savedLocator,
                        preferences = _preferences.value,
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
        val pct = ((locator.locations.totalProgression ?: 0.0) * 100).toInt()
        _progressText.value = "$pct%"
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

    // ===== 排版偏好（实时生效 + 旋转保位）=====

    fun changeFontSize(delta: Double) {
        val newSize = ((_preferences.value.fontSize ?: 1.0) + delta).coerceIn(0.5, 5.0)
        _preferences.value = _preferences.value.copy(fontSize = newSize)
        syncReadyPreferences()
    }

    fun changeTheme(theme: Theme) {
        _preferences.value = _preferences.value.copy(theme = theme)
        syncReadyPreferences()
    }

    private fun syncReadyPreferences() {
        val ready = _uiState.value as? ReaderUiState.Ready ?: return
        _uiState.value = ready.copy(preferences = _preferences.value)
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
    }
}
