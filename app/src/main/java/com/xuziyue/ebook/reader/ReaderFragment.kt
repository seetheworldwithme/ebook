package com.xuziyue.ebook.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.xuziyue.ebook.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.services.locateProgression

/**
 * Reader 的 Fragment 宿主（命门：Compose↔Readium 桥接）。
 *
 * 用 childFragmentManager 托管 [EpubNavigatorFragment]（test-app 实战模式）：
 * - [onCreate] 在 super.onCreate **之前**设 fragmentFactory（super.onCreate 会用它恢复 child）。
 *   - uiState Ready（旋转，VM 存活）→ 真实 factory。
 *   - 否则（进程重建 / 首次）→ [EpubNavigatorFragment.createDummyFactory]，防 super.onCreate 恢复 child 时崩。
 * - [onViewCreated]：若 uiState 非 Ready，移除 dummy（必须在 onResume 前移除，否则抛
 *   RestorationNotSupportedException）；订阅 uiState，Ready 后 [ensureNavigator] 创建/复用真实 navigator。
 *
 * 外层 Compose 用 AndroidFragment<ReaderFragment> 托管本 Fragment。
 */
@AndroidEntryPoint
class ReaderFragment : Fragment() {

    private val viewModel: ReaderViewModel by activityViewModels()

    private var navigator: EpubNavigatorFragment? = null

    @OptIn(ExperimentalReadiumApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // 必须在 super.onCreate 前：进程重建时 super.onCreate 用此 factory 恢复 child fragment。
        childFragmentManager.fragmentFactory = when (val state = viewModel.uiState.value) {
            is ReaderUiState.Ready -> state.navigatorFactory.createFragmentFactory(
                initialLocator = state.initialLocator,
                initialPreferences = state.preferences,
                listener = viewModel,
                configuration = navigatorConfiguration(),
            )
            else -> EpubNavigatorFragment.createDummyFactory()
        }
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_reader, container, false)

    @OptIn(ExperimentalReadiumApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 进程重建恢复：super.onCreate 用 dummy factory 恢复了 dummy navigator，
        // 必须在 onResume 前移除（dummy 在 onResume 抛 RestorationNotSupportedException）。
        // 旋转时 uiState 已 Ready（VM 存活），不进此分支，保留 super.onCreate 恢复的真实 navigator。
        if (viewModel.uiState.value !is ReaderUiState.Ready) {
            removeExistingNavigator()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state is ReaderUiState.Ready) ensureNavigator(state)
                }
            }
        }
    }

    @OptIn(ExperimentalReadiumApi::class)
    private fun ensureNavigator(state: ReaderUiState.Ready) {
        if (navigator != null) return

        // 旋转恢复：super.onCreate 已用真实 factory 恢复了 navigator，直接复用。
        val existing = childFragmentManager.findFragmentByTag(NAV_TAG) as? EpubNavigatorFragment
        if (existing != null) {
            navigator = existing
            bindNavigatorObservers()
            return
        }

        // 首次 / 进程重建：dummy 已移除，创建真实 navigator。
        childFragmentManager.fragmentFactory = state.navigatorFactory.createFragmentFactory(
            initialLocator = state.initialLocator,
            initialPreferences = state.preferences,
            listener = viewModel,
            configuration = navigatorConfiguration(),
        )
        childFragmentManager.commitNow {
            add(R.id.navigator_container, EpubNavigatorFragment::class.java, null, NAV_TAG)
        }
        navigator = childFragmentManager.findFragmentByTag(NAV_TAG) as? EpubNavigatorFragment
        bindNavigatorObservers()
    }

    private fun removeExistingNavigator() {
        childFragmentManager.findFragmentByTag(NAV_TAG)?.let {
            childFragmentManager.commitNow { remove(it) }
        }
        navigator = null
    }

    @OptIn(ExperimentalReadiumApi::class)
    private fun bindNavigatorObservers() {
        val nav = navigator ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // currentLocator → VM（进度 + 防抖落盘）
                launch { nav.currentLocator.collect { viewModel.onLocatorUpdated(it) } }
                // preferences → submitPreferences（字号/主题实时生效）
                launch { viewModel.preferences.collect { nav.submitPreferences(it) } }
                // decorations → applyDecorations（高亮渲染，声明整组完整状态）
                launch { viewModel.decorations.collect { nav.applyDecorations(it, DECORATION_GROUP) } }
                // navCommands → 执行目录 / 进度 / 返回跳转（READ-02）
                launch {
                    viewModel.navCommands.collect { cmd ->
                        val target = navigator ?: return@collect
                        when (cmd) {
                            is ReaderNavCommand.GoToLink -> target.go(cmd.link, animated = false)
                            is ReaderNavCommand.GoToProgression -> {
                                val pub = (viewModel.uiState.value as? ReaderUiState.Ready)?.publication
                                    ?: return@collect
                                val locator = pub.locateProgression(cmd.progress) ?: return@collect
                                target.go(locator, animated = false)
                            }
                            is ReaderNavCommand.GoBack -> target.go(cmd.locator, animated = false)
                            is ReaderNavCommand.GoToLocator -> target.go(cmd.locator, animated = false)
                        }
                    }
                }
            }
        }
    }

    // ===== 文本选择 → 加高亮（READ-07：先 Room 落盘 → observe 回流 → applyDecorations 渲染）=====
    // 长按选中正文文字 → 系统 ActionMode 菜单「高亮」→ currentSelection().locator（精确 DOM 文本范围，
    // 同步携带 Locator.text.highlight 作为批注 selectedText）→ addHighlight → Repository 落盘 →
    // decorations 派生回流 → applyDecorations 渲染。对齐 Readium test-app VisualReaderFragment。

    private val selectionActionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            // 能力矩阵 gating（红线 #2）：文字选择相关能力为 false 时隐藏对应菜单项（PDF V1 生效）。
            val caps = viewModel.capabilities.value
            if (caps.canHighlight) {
                menu.add(0, MENU_HIGHLIGHT_ID, 0, "高亮")
            }
            if (caps.canCopyShare) {
                menu.add(0, MENU_COPY_ID, 0, "复制")
                menu.add(0, MENU_SHARE_ID, 0, "分享")
            }
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                MENU_HIGHLIGHT_ID -> {
                    lifecycleScope.launch {
                        val selection = navigator?.currentSelection()
                        if (selection != null) {
                            viewModel.addHighlight(selection.locator)
                            navigator?.clearSelection()
                        }
                        mode.finish()
                    }
                    return true
                }
                MENU_COPY_ID -> {
                    lifecycleScope.launch {
                        copySelection()
                        mode.finish()
                    }
                    return true
                }
                MENU_SHARE_ID -> {
                    lifecycleScope.launch {
                        shareSelection()
                        mode.finish()
                    }
                    return true
                }
            }
            return false
        }

        override fun onDestroyActionMode(mode: ActionMode) {}
    }

    /** 复制选中文字到系统剪贴板（READ-07；空选区不操作）。 */
    private suspend fun copySelection() {
        val text = navigator?.currentSelection()?.locator?.text?.highlight
        if (text.isNullOrBlank()) return
        val clipboard = requireContext().getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("ebook", text))
    }

    /** 系统分享选中文字（READ-07；空选区不操作；交系统 chooser 选择目标）。 */
    private suspend fun shareSelection() {
        val text = navigator?.currentSelection()?.locator?.text?.highlight
        if (text.isNullOrBlank()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "分享选中文字"))
    }

    @OptIn(ExperimentalReadiumApi::class)
    private fun navigatorConfiguration() = EpubNavigatorFragment.Configuration {
        selectionActionModeCallback = this@ReaderFragment.selectionActionModeCallback
    }

    override fun onStop() {
        super.onStop()
        // READ-08：进入后台强制保存最新 locator
        viewModel.flushLocator()
    }

    private companion object {
        const val NAV_TAG = "epub_navigator"
        const val DECORATION_GROUP = "highlights"
        const val MENU_HIGHLIGHT_ID = 1
        const val MENU_COPY_ID = 2
        const val MENU_SHARE_ID = 3
    }
}
