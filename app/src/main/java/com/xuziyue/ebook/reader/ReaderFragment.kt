package com.xuziyue.ebook.reader

import android.os.Bundle
import android.view.LayoutInflater
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
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // READ-08：进入后台强制保存最新 locator
        viewModel.flushLocator()
    }

    private companion object {
        const val NAV_TAG = "epub_navigator"
        const val DECORATION_GROUP = "highlights"
    }
}
