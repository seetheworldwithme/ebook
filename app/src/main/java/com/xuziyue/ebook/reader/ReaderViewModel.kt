package com.xuziyue.ebook.reader

import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xuziyue.ebook.data.LocatorStore
import com.xuziyue.ebook.reader.readium.OpenBookUseCase
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
 * 2. 协调 [Locator] 恢复——从 [LocatorStore] 读最近位置作 initialLocator，currentLocator 变化防抖落盘。
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
    private val locatorStore: LocatorStore,
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

    private var currentHash: String? = null
    private var latestLocator: Locator? = null
    private var persistJob: Job? = null

    /**
     * 打开指定 [contentHash] 的书。幂等：同 hash 不重复 open（旋转重建时不会重复打开）。
     */
    fun openBook(contentHash: String) {
        if (contentHash == currentHash) return
        // 切换书：close 上一本（若有）
        (_uiState.value as? ReaderUiState.Ready)?.publication?.close()
        currentHash = contentHash
        openPublication(contentHash)
    }

    private fun openPublication(contentHash: String) {
        viewModelScope.launch {
            _uiState.value = ReaderUiState.Loading

            val filePath = locatorStore.readFilePath(contentHash)
                ?: File(context.filesDir, "books/$contentHash.epub").absolutePath

            openBookUseCase.open(File(filePath))
                .onSuccess { publication ->
                    val savedLocator = locatorStore.readLocator(contentHash)
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
            locatorStore.saveLocator(currentHash ?: return@launch, locator)
        }
    }

    /** 进入后台 / 退出时强制写入最新 locator（READ-08：后台/销毁前强制保存）。 */
    fun flushLocator() {
        persistJob?.cancel()
        val locator = latestLocator ?: return
        val hash = currentHash ?: return
        viewModelScope.launch { locatorStore.saveLocator(hash, locator) }
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

    fun addTestHighlight() {
        val locator = latestLocator ?: return
        val decoration = Decoration(
            id = "hl-${System.nanoTime()}",
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
