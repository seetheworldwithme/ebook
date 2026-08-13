package com.xuziyue.ebook.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.AttributeSet
import android.view.ActionMode
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.Toast
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.xuziyue.ebook.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.services.locateProgression
import timber.log.Timber

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
    /** 当前 Navigator 实际绑定的书；不同书绝不复用同一实例。 */
    private var navigatorBookId: String? = null
    /** Navigator 更换时取消旧 Locator/偏好/指令订阅，防止迟到回调串书。 */
    private var navigatorBindingJob: Job? = null

    // READ-03：音量键翻页开关（collect viewModel.volumeKeyPaging 后更新，默认开）。interceptor 读它决定是否消费。
    private var volumeKeyPaging = true

    // READ-03：Window.Callback 原始引用（onResume 包装拦截音量键，onPause 还原）。
    private var originalWindowCallback: Window.Callback? = null

    @OptIn(ExperimentalReadiumApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // 参数在 Fragment 恢复 child 之前可用。先切换 VM 到目标书，避免用上一部书的 Ready
        // 创建/恢复 Navigator；ReaderScreen 不再依赖 composition 后才执行的 LaunchedEffect。
        arguments?.getString(ARG_BOOK_ID)?.let(viewModel::openBook)
        // 必须在 super.onCreate 前：进程重建时 super.onCreate 用此 factory 恢复 child fragment。
        childFragmentManager.fragmentFactory = when (val state = viewModel.uiState.value) {
            is ReaderUiState.Ready -> {
                navigatorBookId = state.bookId
                state.navigatorFactory.createFragmentFactory(
                    initialLocator = viewModel.restoreLocatorForNavigator(state),
                    initialPreferences = state.preferences,
                    listener = viewModel,
                    configuration = navigatorConfiguration(),
                )
            }
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

        // READ-03：绑定音量键拦截器（自定义容器在 dispatchKeyEvent 拦截音量键翻页）。
        view.findViewById<ReaderNavigatorContainer>(R.id.navigator_container)
            .volumeKeyInterceptor = ::handleVolumeKey

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
        when (navigatorUpdate(navigatorBookId, state.bookId)) {
            NavigatorUpdate.KEEP -> if (navigator != null) return
            NavigatorUpdate.REPLACE -> removeExistingNavigator()
            NavigatorUpdate.CREATE -> Unit
        }

        // 旋转恢复：super.onCreate 已用真实 factory 恢复了 navigator，直接复用。
        val existing = childFragmentManager.findFragmentByTag(NAV_TAG) as? EpubNavigatorFragment
        if (existing != null) {
            navigator = existing
            navigatorBookId = state.bookId
            bindNavigatorObservers(state.bookId)
            return
        }

        // 首次 / 进程重建：dummy 已移除，创建真实 navigator。
        childFragmentManager.fragmentFactory = state.navigatorFactory.createFragmentFactory(
            initialLocator = viewModel.restoreLocatorForNavigator(state),
            initialPreferences = state.preferences,
            listener = viewModel,
            configuration = navigatorConfiguration(),
        )
        childFragmentManager.commitNow {
            add(R.id.navigator_container, EpubNavigatorFragment::class.java, null, NAV_TAG)
        }
        navigator = childFragmentManager.findFragmentByTag(NAV_TAG) as? EpubNavigatorFragment
        navigatorBookId = state.bookId
        bindNavigatorObservers(state.bookId)
    }

    private fun removeExistingNavigator() {
        navigatorBindingJob?.cancel()
        navigatorBindingJob = null
        childFragmentManager.findFragmentByTag(NAV_TAG)?.let {
            childFragmentManager.commitNow { remove(it) }
        }
        navigator = null
        navigatorBookId = null
    }

    @OptIn(ExperimentalReadiumApi::class)
    private fun bindNavigatorObservers(bookId: String) {
        val nav = navigator ?: return
        navigatorBindingJob?.cancel()
        navigatorBindingJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // currentLocator → VM（进度 + 防抖落盘）
                launch {
                    var firstPage = true
                    nav.currentLocator.collect {
                        // REL-05 性能基线：首次定位 ≈ 首页已渲染（START→此处为首开总耗时）。
                        if (firstPage) { firstPage = false; Timber.i("PERF_READER_OPEN_FIRST_PAGE bookId=$bookId") }
                        viewModel.onLocatorUpdated(bookId, it)
                    }
                }
                // preferences → submitPreferences（字号/主题实时生效）
                launch { viewModel.preferences.collect { nav.submitPreferences(it) } }
                // decorations → applyDecorations（高亮渲染，声明整组完整状态）
                launch { viewModel.decorations.collect { nav.applyDecorations(it, DECORATION_GROUP) } }
                // READ-03：音量键翻页开关 → 更新本地拦截标志（interceptor 读它决定消费 / 放行）。
                launch { viewModel.volumeKeyPaging.collect { volumeKeyPaging = it } }
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
                            is ReaderNavCommand.GoForward -> target.goForward(animated = false)
                            is ReaderNavCommand.GoBackward -> target.goBackward(animated = false)
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
                menu.add(0, MENU_HIGHLIGHT_ID, 0, getString(R.string.reader_menu_highlight))
            }
            if (caps.canCopyShare) {
                menu.add(0, MENU_COPY_ID, 0, getString(R.string.reader_menu_copy))
                menu.add(0, MENU_SHARE_ID, 0, getString(R.string.reader_menu_share))
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

    /** 复制选中文字到系统剪贴板（READ-07；空选区不操作；Toast 反馈，部分 ROM 不弹系统复制提示）。 */
    private suspend fun copySelection() {
        val text = navigator?.currentSelection()?.locator?.text?.highlight
        if (text.isNullOrBlank()) return
        val clipboard = requireContext().getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("ebook", text))
        Toast.makeText(requireContext(), getString(R.string.reader_copied), Toast.LENGTH_SHORT).show()
    }

    /** 系统分享选中文字（READ-07；空选区不操作；交系统 chooser 选择目标）。 */
    private suspend fun shareSelection() {
        val text = navigator?.currentSelection()?.locator?.text?.highlight
        if (text.isNullOrBlank()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.reader_share_chooser)))
    }

    @OptIn(ExperimentalReadiumApi::class)
    private fun navigatorConfiguration() = EpubNavigatorFragment.Configuration {
        selectionActionModeCallback = this@ReaderFragment.selectionActionModeCallback
    }

    /**
     * 音量键翻页拦截（READ-03）：开关开时消费音量键并翻页，关闭则放行（恢复系统音量调节）。
     *
     * 上键 = 上一页（goBackward），下键 = 下一页（goForward）。
     * 同时消费 DOWN + UP：阻止系统在 ACTION_DOWN 调音量（否则会先闪音量条再翻页），仅 ACTION_UP 翻页。
     * MVP 无 TTS，不存在 TTS 音量冲突（design.md READ-03「不会与 TTS 音量控制冲突」）。
     */
    private fun handleVolumeKey(event: KeyEvent): Boolean {
        if (!volumeKeyPaging) return false
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
            event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> navigator?.goBackward(false)
                KeyEvent.KEYCODE_VOLUME_DOWN -> navigator?.goForward(false)
            }
        }
        return true // 消费 DOWN + UP，阻止系统调音量
    }

    override fun onResume() {
        super.onResume()
        installVolumeKeyInterception()
    }

    override fun onPause() {
        super.onPause()
        removeVolumeKeyInterception()
    }

    /**
     * READ-03：包装 Activity Window.Callback，在 [dispatchKeyEvent] 入口拦截音量键。
     *
     * 为何不用自定义 ViewGroup dispatchKeyEvent：Compose AndroidFragment 嵌套托管下，KeyEvent
     * 经 DecorView 沿焦点链 dispatch，container.dispatchKeyEvent 不在调用路径（真机实测未触发）。
     * Window.Callback 是 Activity.dispatchKeyEvent 的最上游入口，可靠捕获所有物理按键。
     * onPause 还原原 callback，避免泄漏 / 影响其他界面。
     */
    private fun installVolumeKeyInterception() {
        val window = requireActivity().window
        if (originalWindowCallback != null) return // 已安装（防重复）
        val original = window.callback ?: return
        originalWindowCallback = original
        window.callback = object : Window.Callback by original {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (handleVolumeKey(event)) return true
                return original.dispatchKeyEvent(event)
            }
        }
    }

    private fun removeVolumeKeyInterception() {
        originalWindowCallback?.let {
            requireActivity().window.callback = it
            originalWindowCallback = null
        }
    }

    override fun onStop() {
        super.onStop()
        // READ-08：进入后台强制保存最新 locator
        viewModel.flushLocator()
    }

    companion object {
        const val ARG_BOOK_ID = "book_id"
        private const val NAV_TAG = "epub_navigator"
        private const val DECORATION_GROUP = "highlights"
        private const val MENU_HIGHLIGHT_ID = 1
        private const val MENU_COPY_ID = 2
        private const val MENU_SHARE_ID = 3
    }
}

/**
 * 阅读器导航容器（READ-03）：自定义 FrameLayout，重写 dispatchKeyEvent 拦截音量键翻页。
 *
 * 为何不直接 setOnKeyListener：EpubNavigatorFragment 内部的 WebView 会抢占焦点，普通
 * OnKeyListener 仅在宿主 view 获焦时触发，音量键事件被 WebView 分发后不会回流到容器。
 * 重写 dispatchKeyEvent 在事件分发给 WebView 之前拦截并消费，可靠阻止系统调音量。
 * 由 XML（fragment_reader.xml）inflate 实例化，承载 EpubNavigatorFragment。
 */
class ReaderNavigatorContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    /** 音量键拦截器：返回 true 消费（翻页 + 阻止系统音量），false 放行。 */
    var volumeKeyInterceptor: ((KeyEvent) -> Boolean)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (volumeKeyInterceptor?.invoke(event) == true) return true
        return super.dispatchKeyEvent(event)
    }
}
