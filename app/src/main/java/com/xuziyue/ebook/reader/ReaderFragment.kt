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
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.xuziyue.ebook.R
import com.xuziyue.ebook.model.ReaderTypography
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.image.ImageNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.adapter.pdfium.navigator.PdfiumPreferences
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.navigator.preferences.FontFamily
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

    /** 当前 Navigator（V1 三格式：EpubNavigatorFragment / PdfNavigatorFragment / ImageNavigatorFragment 共同实现 Navigator）。 */
    private var navigator: Navigator? = null
    /** 当前 Navigator 实际绑定的书；不同书绝不复用同一实例。 */
    private var navigatorBookId: String? = null
    /** Navigator 更换时取消旧 Locator/偏好/指令订阅，防止迟到回调串书。 */
    private var navigatorBindingJob: Job? = null

    // READ-03：音量键翻页开关（collect viewModel.volumeKeyPaging 后更新，默认开）。interceptor 读它决定是否消费。
    private var volumeKeyPaging = true

    // READ-10：TTS 播放中放行音量键（恢复系统媒体音量调节，验收「不与 TTS 音量控制冲突」）。
    private var ttsPlaying = false

    // READ-02：控制栏显隐切换——onTap 在 WebView 层触发（只对中央 60%：左右各 20% 被 Compose 翻页 overlay
    // 吃掉，到不了 WebView）。请求 VM 翻转 barsVisible。长按选词是 WebView 另一条路（onSelectionStart），
    // onTap 不挡——这是把切换从 Compose overlay 挪到这里的根因（Compose 兄弟挂 pointerInput 会吞长按）。
    @OptIn(ExperimentalReadiumApi::class)
    private val barsToggleInputListener = object : InputListener {
        override fun onTap(event: TapEvent): Boolean {
            viewModel.requestToggleBars()
            return false // 不消费：轻点正文无副作用，内部/外部链接由 HyperlinkNavigator 另行处理不受影响
        }
    }

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
                createRealFactory(state)
            }
            else -> createAllFormatsDummyFactory()
        }
        super.onCreate(savedInstanceState)
    }

    /** 按 [NavigatorSpec] 分支构造三种真实 Navigator 的 FragmentFactory（V1 PDF/CBZ）。 */
    @OptIn(ExperimentalReadiumApi::class)
    private fun createRealFactory(state: ReaderUiState.Ready): FragmentFactory {
        val restoreLocator = viewModel.restoreLocatorForNavigator(state)
        return when (val spec = state.navigatorSpec) {
            is NavigatorSpec.Epub -> spec.navigatorFactory.createFragmentFactory(
                initialLocator = restoreLocator ?: spec.initialLocator,
                initialPreferences = spec.initialPreferences,
                listener = viewModel,
                configuration = navigatorConfiguration(),
            )
            is NavigatorSpec.Pdf -> PdfNavigatorFragment.createFactory(
                publication = spec.publication,
                initialLocator = restoreLocator ?: spec.initialLocator,
                preferences = spec.initialPreferences,
                listener = viewModel,
                pdfEngineProvider = spec.engineProvider,
            )
            is NavigatorSpec.Cbz -> ImageNavigatorFragment.createFactory(
                publication = spec.publication,
                initialLocator = restoreLocator ?: spec.initialLocator,
                listener = viewModel,
            )
        }
    }

    /**
     * 三格式复合 dummy factory（V1 PDF/CBZ）：按恢复的 child class 名分发到对应格式的 dummy。
     *
     * 进程重建时 uiState 尚未 Ready，super.onCreate 恢复 child 的 class 由上次会话决定
     * （可能是三种 Navigator 之一）；单格式 dummy 会在格式不匹配时恢复失败崩溃。
     */
    @OptIn(ExperimentalReadiumApi::class)
    private fun createAllFormatsDummyFactory(): FragmentFactory = object : FragmentFactory() {
        override fun instantiate(classLoader: ClassLoader, className: String): Fragment =
            when (className) {
                PdfNavigatorFragment::class.java.name ->
                    PdfNavigatorFragment.createDummyFactory(viewModel.pdfEngineProviderForRestore())
                        .instantiate(classLoader, className)
                ImageNavigatorFragment::class.java.name ->
                    ImageNavigatorFragment.createDummyFactory().instantiate(classLoader, className)
                else -> EpubNavigatorFragment.createDummyFactory().instantiate(classLoader, className)
            }
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
        val existing = childFragmentManager.findFragmentByTag(NAV_TAG) as? Navigator
        if (existing != null) {
            navigator = existing
            navigatorBookId = state.bookId
            bindNavigatorObservers(state.bookId)
            return
        }

        // 首次 / 进程重建：dummy 已移除，按 spec 创建真实 navigator。
        childFragmentManager.fragmentFactory = createRealFactory(state)
        val navigatorClass = when (state.navigatorSpec) {
            is NavigatorSpec.Epub -> EpubNavigatorFragment::class.java
            is NavigatorSpec.Pdf -> PdfNavigatorFragment::class.java
            is NavigatorSpec.Cbz -> ImageNavigatorFragment::class.java
        }
        childFragmentManager.commitNow {
            add(R.id.navigator_container, navigatorClass, null, NAV_TAG)
        }
        navigator = childFragmentManager.findFragmentByTag(NAV_TAG) as? Navigator
        navigatorBookId = state.bookId
        bindNavigatorObservers(state.bookId)
    }

    @OptIn(ExperimentalReadiumApi::class)
    private fun removeExistingNavigator() {
        navigatorBindingJob?.cancel()
        navigatorBindingJob = null
        (navigator as? VisualNavigator)?.removeInputListener(barsToggleInputListener)
        childFragmentManager.findFragmentByTag(NAV_TAG)?.let {
            childFragmentManager.commitNow { remove(it) }
        }
        navigator = null
        navigatorBookId = null
    }

    @OptIn(ExperimentalReadiumApi::class)
    private fun bindNavigatorObservers(bookId: String) {
        val nav = navigator ?: return
        // READ-02：注册控制栏切换监听（先移除再加，旋转重绑同一 navigator 时幂等，不重复触发）。
        // 三种 Navigator 均实现 VisualNavigator（add/removeInputListener 公共接口）。
        (nav as? VisualNavigator)?.let { visualNav ->
            visualNav.removeInputListener(barsToggleInputListener)
            visualNav.addInputListener(barsToggleInputListener)
        }
        navigatorBindingJob?.cancel()
        navigatorBindingJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // currentLocator → VM（进度 + 防抖落盘）——三格式公共。
                launch {
                    var firstPage = true
                    nav.currentLocator.collect {
                        // REL-05 性能基线：首次定位 ≈ 首页已渲染（START→此处为首开总耗时）。
                        if (firstPage) { firstPage = false; Timber.i("PERF_READER_OPEN_FIRST_PAGE bookId=$bookId") }
                        viewModel.onLocatorUpdated(bookId, it)
                    }
                }
                // 偏好实时生效：EPUB 走 EpubPreferences，PDF 走 PdfiumPreferences（仅翻页方式），CBZ 无偏好。
                if (nav is EpubNavigatorFragment) {
                    launch { viewModel.preferences.collect { nav.submitPreferences(it) } }
                } else if (nav is PdfNavigatorFragment<*, *>) {
                    // star projection 上不能调 submitPreferences(P)，用 run 找回具体类型。
                    @Suppress("UNCHECKED_CAST")
                    val pdfNav = nav as PdfNavigatorFragment<*, PdfiumPreferences>
                    launch { viewModel.pdfPreferences.collect { pdfNav.submitPreferences(it) } }
                }
                // EPUB 专属：高亮 Decoration 渲染（红线 #9 DB 驱动）。
                (nav as? EpubNavigatorFragment)?.let { epubNav ->
                    launch { viewModel.decorations.collect { epubNav.applyDecorations(it, DECORATION_GROUP) } }
                }
                // READ-03：音量键翻页开关 → 更新本地拦截标志（interceptor 读它决定消费 / 放行）。
                launch { viewModel.volumeKeyPaging.collect { volumeKeyPaging = it } }
                // READ-10：TTS 播放态 → 音量键放行标志。
                launch { viewModel.ttsPlaying.collect { ttsPlaying = it } }
                // READ-10：当前朗读句 → tts Decoration 组（EPUB 专属；canTts gating 保证 PDF/CBZ 不产生朗读）。
                (nav as? EpubNavigatorFragment)?.let { epubNav ->
                    launch {
                        viewModel.ttsUtterance.collect { locator ->
                            if (locator == null) {
                                epubNav.applyDecorations(emptyList(), DECORATION_GROUP_TTS)
                            } else {
                                epubNav.applyDecorations(
                                    listOf(
                                        Decoration(
                                            id = "tts-utterance",
                                            locator = locator,
                                            style = Decoration.Style.Underline(tint = 0xFF2196F3.toInt()),
                                        ),
                                    ),
                                    DECORATION_GROUP_TTS,
                                )
                            }
                        }
                    }
                }
                // READ-10：自动跟翻——朗读句离开当前可视页时 go 到该句（animated=false 不闪）。
                // 不走 navCommands / jumpHistory：跟翻是渲染跟随，不是用户跳转，不污染返回历史。
                // 句级粒度（utteranceLocator）而非 token 级：token 每词更新会抖动，句级足够顺。
                launch {
                    viewModel.ttsUtterance.collect { locator ->
                        if (locator != null) navigator?.go(locator, animated = false)
                    }
                }
                // navCommands → 执行目录 / 进度 / 返回跳转（READ-02）——三格式公共（go/goForward/goBackward 是 Navigator 接口）。
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
                            // 翻页是 OverflowableNavigator 接口（三种 Navigator 均实现）。
                            is ReaderNavCommand.GoForward -> (target as? OverflowableNavigator)?.goForward(animated = false)
                            is ReaderNavCommand.GoBackward -> (target as? OverflowableNavigator)?.goBackward(animated = false)
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
                        val selection = (navigator as? EpubNavigatorFragment)?.currentSelection()
                        if (selection != null) {
                            viewModel.addHighlight(selection.locator)
                            (navigator as? EpubNavigatorFragment)?.clearSelection()
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
        val text = (navigator as? EpubNavigatorFragment)?.currentSelection()?.locator?.text?.highlight
        if (text.isNullOrBlank()) return
        val clipboard = requireContext().getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("ebook", text))
        Toast.makeText(requireContext(), getString(R.string.reader_copied), Toast.LENGTH_SHORT).show()
    }

    /** 系统分享选中文字（READ-07；空选区不操作；交系统 chooser 选择目标）。 */
    private suspend fun shareSelection() {
        val text = (navigator as? EpubNavigatorFragment)?.currentSelection()?.locator?.text?.highlight
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
        // TYPE-05：预置字体经 WebViewServer（https://readium/assets/）服务给 EPUB WebView，
        // 只白名单放行 fonts/ 前缀（红线 #4 最小开放面；Readium 自己再并入 readium/.*）。
        servedAssets += "fonts/.*"
        // 霞鹜文楷屏幕阅读版（OFL-1.1）：声明 @font-face，字体选项在 TypographySheet 可选。
        // 家族名必须与 TTF name 表一致（见 ReaderTypography.LXGW_FONT_FAMILY），否则 CSS 匹配不上字回退默认。
        addFontFamilyDeclaration(FontFamily(ReaderTypography.LXGW_FONT_FAMILY)) {
            addFontFace {
                addSource("fonts/LXGWWenKaiScreen-Regular.ttf")
            }
        }
    }

    /**
     * 音量键翻页拦截（READ-03）：开关开时消费音量键并翻页，关闭则放行（恢复系统音量调节）。
     *
     * 上键 = 上一页（goBackward），下键 = 下一页（goForward）。
     * 同时消费 DOWN + UP：阻止系统在 ACTION_DOWN 调音量（否则会先闪音量条再翻页），仅 ACTION_UP 翻页。
     * READ-10：TTS 播放中放行（用户此时调的是媒体音量，翻页拦截反而挡住音量条——验收
     * 「不与 TTS 音量控制冲突」）。
     */
    private fun handleVolumeKey(event: KeyEvent): Boolean {
        if (ttsPlaying) return false
        if (!volumeKeyPaging) return false
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
            event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> (navigator as? OverflowableNavigator)?.goBackward(false)
                KeyEvent.KEYCODE_VOLUME_DOWN -> (navigator as? OverflowableNavigator)?.goForward(false)
            }
        }
        return true // 消费 DOWN + UP，阻止系统调音量
    }

    override fun onResume() {
        super.onResume()
        installVolumeKeyInterception()
        // DATA-04：切通知栏回来继续读同一本时，会话可能已被 onPause 结束，此处续接。
        viewModel.onReaderResumed()
    }

    override fun onPause() {
        super.onPause()
        removeVolumeKeyInterception()
        // DATA-04：失去前台即结束会话计时并落盘（强杀时 onPause 几乎必然触发，比 onCleared 可靠）。
        viewModel.onReaderPaused()
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
        private const val DECORATION_GROUP_TTS = "tts"
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
