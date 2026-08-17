package com.xuziyue.ebook.reader

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.WindowManager
import android.webkit.WebView
import android.widget.Toast
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.compose.AndroidFragment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuziyue.ebook.R
import com.xuziyue.ebook.data.export.ExportBookDataUseCase
import com.xuziyue.ebook.model.HighlightColor
import com.xuziyue.ebook.model.ReaderDisplaySettings
import com.xuziyue.ebook.model.ReaderOrientation
import com.xuziyue.ebook.model.ReaderScrollMode
import com.xuziyue.ebook.model.ReaderTextAlign
import com.xuziyue.ebook.model.ReaderTheme
import com.xuziyue.ebook.model.ReaderTypography
import com.xuziyue.ebook.reader.tts.ReaderTtsManager
import com.xuziyue.ebook.ui.relativeTime
import com.xuziyue.ebook.ui.resolve
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

/**
 * Reader 主界面（Compose）。
 *
 * - [AndroidFragment] 托管 [ReaderFragment]（Compose↔Readium 桥接的核心）。
 * - VM 绑 Activity scope（与 ReaderFragment 的 activityViewModels 共享同一实例）。
 * - 顶栏：返回 + 目录 + 返回上一位置 + 进度（点开拖动浮层）。READ-02。
 * - 底栏：字号±、排版入口（开 [TypographySheet]）、高亮计数/清。主题与排版全维度在 sheet 内。
 * - [isSystemInDarkTheme] 推入 VM（[ReaderViewModel.setSystemDark]），用于解析 [ReaderTheme.SYSTEM]。
 *
 * 高亮由「长按选中正文文字 → 系统 ActionMode「高亮」」触发（见 ReaderFragment），不在底栏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onBack: () -> Unit,
) {
    // VM 绑 Activity scope，确保与 ReaderFragment 的 activityViewModels() 共享同一实例。
    val activity = LocalContext.current as ViewModelStoreOwner
    val viewModel: ReaderViewModel = hiltViewModel(viewModelStoreOwner = activity)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val progressText by viewModel.progressText.collectAsStateWithLifecycle()
    val progression by viewModel.progression.collectAsStateWithLifecycle()
    val tableOfContents by viewModel.tableOfContents.collectAsStateWithLifecycle()
    val canGoBack by viewModel.canGoBack.collectAsStateWithLifecycle()
    val canGoForward by viewModel.canGoForward.collectAsStateWithLifecycle()
    val linkDialog by viewModel.linkDialog.collectAsStateWithLifecycle()
    val decorations by viewModel.decorations.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val typography by viewModel.typography.collectAsStateWithLifecycle()
    val displaySettings by viewModel.displaySettings.collectAsStateWithLifecycle()
    val perBookTypography by viewModel.perBookTypography.collectAsStateWithLifecycle()
    val hasBookOverride by viewModel.hasBookOverride.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val annotations by viewModel.annotations.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val ttsPlaying by viewModel.ttsPlaying.collectAsStateWithLifecycle()
    val ttsPreferences by viewModel.ttsPreferences.collectAsStateWithLifecycle()
    val ttsVoices by viewModel.ttsVoices.collectAsStateWithLifecycle()
    val ttsTimerMinutes by viewModel.ttsTimerMinutes.collectAsStateWithLifecycle()
    val ttsEvent by viewModel.ttsEvents.collectAsStateWithLifecycle()

    // 跟随系统主题：系统暗色变化时推入 VM，解析 ReaderTheme.SYSTEM → DARK/LIGHT。
    val systemDark = isSystemInDarkTheme()
    LaunchedEffect(systemDark) {
        viewModel.setSystemDark(systemDark)
    }

    // SET-03：跟随系统字号——WebView 不继承系统 fontScale，需显式折算进 Readium fontSize。
    val systemFontScale = LocalDensity.current.fontScale
    LaunchedEffect(systemFontScale) {
        viewModel.setSystemFontScale(systemFontScale)
    }

    // TYPE-03：亮度 / 常亮 / 方向应用到 Window（仅在阅读器内生效，退出恢复系统）。
    // 用 findActivity 扩展避免 lint 报「Context 转 Activity 不安全」（Context 不一定是 Activity）。
    val windowActivity = LocalContext.current.findActivity()
    val window = windowActivity.window
    // 亮度：null = 跟随系统（screenBrightness = -1f），0–1 = 手动。
    LaunchedEffect(displaySettings.brightness) {
        val attrs = window.attributes
        attrs.screenBrightness = displaySettings.brightness ?: -1f
        window.attributes = attrs
    }
    // 常亮：addFlags / clearFlags（FLAG 随 window 生命周期，退出 Composable 时 onDispose 清除）。
    DisposableEffect(displaySettings.keepScreenOn) {
        if (displaySettings.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { }
    }
    // 方向：null / SYSTEM = UNSPECIFIED，PORTRAIT / LANDSCAPE 锁定。
    LaunchedEffect(displaySettings.orientation) {
        windowActivity.requestedOrientation = when (displaySettings.orientation) {
            ReaderOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ReaderOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            null, ReaderOrientation.SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    // 退出阅读器时恢复系统行为（亮度跟随系统 / 清常亮 / 方向跟随系统）。
    DisposableEffect(Unit) {
        onDispose {
            val attrs = window.attributes
            attrs.screenBrightness = -1f
            window.attributes = attrs
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            windowActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // DATA-01 导出：SAF CreateDocument（MD/JSON 各一 launcher）+ 结果 Toast 反馈。
    val context = LocalContext.current
    val defaultFileName: (String) -> String = { suffix ->
        val title = (uiState as? ReaderUiState.Ready)?.publication?.metadata?.title
        val base = title?.replace(Regex("[/\\\\:*?\"<>|]"), "_")?.take(60)?.ifBlank { bookId } ?: bookId
        "$base.$suffix"
    }
    val mdLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri -> uri?.let { viewModel.exportBook(ExportBookDataUseCase.Format.MARKDOWN, it) } }
    val jsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { viewModel.exportBook(ExportBookDataUseCase.Format.JSON, it) } }
    // 导出成功文案模板在 Composable 作用域解析（lint 要求 stringResource 而非 context.getString），
    // 具体数值（条数 / 格式名）在 collect 里 String.format 填充。
    val exportSuccessTemplate = stringResource(R.string.reader_export_success)
    LaunchedEffect(Unit) {
        viewModel.exportEvents.collect { outcome ->
            val msg = when (outcome) {
                is ExportBookDataUseCase.Outcome.Success ->
                    String.format(
                        exportSuccessTemplate,
                        outcome.items,
                        if (outcome.format == ExportBookDataUseCase.Format.JSON) "JSON" else "Markdown",
                    )
                is ExportBookDataUseCase.Outcome.Failed -> outcome.message.resolve(context)
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    var showTypography by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var showProgress by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showAnnotations by remember { mutableStateOf(false) }
    var editingAnnotation by remember { mutableStateOf<AnnotationItem?>(null) }
    var showExportFormat by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showTts by remember { mutableStateOf(false) }
    // READ-02：顶/底控制栏显隐——默认显示一次（用户进入即见，便于看标题/进度）。
    // 切换由 Readium InputListener.onTap（WebView 层，见 ReaderFragment）经 VM 桥接回这里翻转；
    // 滚动模式栏常驻（controlsVisible 恒 true）。rememberSaveable 跨横竖屏/暗色保位，导航重进重置默认。
    var barsVisible by rememberSaveable { mutableStateOf(true) }
    // READ-02：onTap 经 VM 桥接——必须挂 WebView 层，不能在 Compose 贴中央 overlay
    // （Compose 兄弟节点挂 pointerInput 会独占手势、挡住 WebView 长按选词 READ-07）。
    LaunchedEffect(Unit) {
        viewModel.barsToggleEvents.collect { barsVisible = !barsVisible }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 核心：Compose 托管 ReaderFragment（内部 childFragmentManager 托管 EpubNavigatorFragment）
        AndroidFragment<ReaderFragment>(
            modifier = Modifier.fillMaxSize(),
            arguments = Bundle().apply { putString(ReaderFragment.ARG_BOOK_ID, bookId) },
        )

        // READ-03：点击左右边缘翻页（仅分页模式；scroll 模式是上下滚动，点击无意义）。
        // 左右各 20% 宽，中间 60% 留给 WebView 文本选择 / 链接。顶栏 / 底栏在更高 z 层覆盖上下区。
        // SET-02：给不可见点击区加 contentDescription + Role.Button——既消除 TalkBack「无名可点」，
        // 又给 TalkBack 用户一个显式翻页入口（音量键 / 滑动对 TalkBack 不友好）。
        val pagePrevText = stringResource(R.string.reader_page_prev)
        val pageNextText = stringResource(R.string.reader_page_next)
        if (typography.scroll != ReaderScrollMode.SCROLL) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.2f)
                    .align(Alignment.TopStart)
                    .clickable { viewModel.goBackwardPaging() }
                    .semantics {
                        contentDescription = pagePrevText
                        role = Role.Button
                    },
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.2f)
                    .align(Alignment.TopEnd)
                    .clickable { viewModel.goForwardPaging() }
                    .semantics {
                        contentDescription = pageNextText
                        role = Role.Button
                    },
            )
        }

        // 滚动模式无中央切换区（中间留给 WebView 选词/链接），控制栏常驻；分页模式跟随 barsVisible。
        val controlsVisible = typography.scroll == ReaderScrollMode.SCROLL || barsVisible

        // 顶部控制条（READ-02：目录 / 返回上一位置 / 进度入口；READ-06：书签 toggle）
        AnimatedVisibility(
            visible = controlsVisible,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderTopBar(
                progressText = progressText,
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                isBookmarked = isBookmarked,
                canBookmark = capabilities.canBookmark,
                canSearch = capabilities.canSearch,
                canTts = capabilities.canTts,
                onBack = onBack,
                onOpenToc = { showToc = true },
                onOpenSearch = { showSearch = true },
                onOpenTts = { showTts = true },
                onGoBack = { viewModel.goBack() },
                onGoForward = { viewModel.goForward() },
                onToggleBookmark = { viewModel.toggleBookmark() },
                onOpenProgress = { showProgress = true },
            )
        }

        // 底部控制条
        AnimatedVisibility(
            visible = controlsVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ReaderBottomBar(
                onFontDecrease = { viewModel.changeFontSize(-0.1) },
                onFontIncrease = { viewModel.changeFontSize(0.1) },
                onOpenTypography = { showTypography = true },
                onOpenBookmarks = { showBookmarks = true },
                onOpenAnnotations = { showAnnotations = true },
                bookmarkCount = bookmarks.size,
                annotationCount = annotations.size,
                canBookmark = capabilities.canBookmark,
                canHighlight = capabilities.canHighlight,
            )
        }

        // 排版面板（TYPE-01 字号/行高/段距/页边距/对齐/字体 + TYPE-02 主题含跟随系统
        // + TYPE-03 亮度/常亮/方向显示设置）
        if (showTypography) {
            TypographySheet(
                typography = typography,
                displaySettings = displaySettings,
                perBookTypography = perBookTypography,
                hasBookOverride = hasBookOverride,
                onDismiss = { showTypography = false },
                onFontSize = { viewModel.setFontSize(it) },
                onFontWeight = { viewModel.setFontWeight(it) },
                onLineHeight = { viewModel.setLineHeight(it) },
                onParagraphSpacing = { viewModel.setParagraphSpacing(it) },
                onPageMargins = { viewModel.setPageMargins(it) },
                onTextAlign = { viewModel.setTextAlign(it) },
                onTheme = { viewModel.setTheme(it) },
                onFontFamily = { viewModel.setFontFamily(it) },
                onScrollMode = { viewModel.setScrollMode(it) },
                onVolumeKeyPaging = { viewModel.setVolumeKeyPaging(it) },
                onEnablePerBook = { viewModel.enablePerBookTypography() },
                onDisablePerBook = { viewModel.disablePerBookTypography() },
                onResetBookTypography = { viewModel.resetBookTypography() },
                onBrightness = { viewModel.setBrightness(it) },
                onKeepScreenOn = { viewModel.setKeepScreenOn(it) },
                onOrientation = { viewModel.setOrientation(it) },
            )
        }

        // 目录面板（READ-02：章节列表 + 跳转）
        if (showToc) {
            TocSheet(
                items = tableOfContents,
                onJump = { link -> viewModel.jumpToLink(link); showToc = false },
                onDismiss = { showToc = false },
            )
        }

        // 进度拖动面板（READ-02：点「进度 N%」展开，Slider + ◄ ► 微调）
        if (showProgress) {
            ProgressSheet(
                progression = progression,
                onJump = { viewModel.jumpToProgression(it) },
                onDismiss = { showProgress = false },
            )
        }

        // 书内搜索面板（READ-05：canSearch gating 的入口在顶栏）
        if (showSearch) {
            SearchSheet(
                state = searchState,
                onSearch = { viewModel.search(it) },
                onLoadMore = { viewModel.loadMoreResults() },
                onJump = { viewModel.jumpToLocator(it); showSearch = false },
                onDismiss = { showSearch = false },
            )
        }

        // TTS 朗读面板（READ-10：canTts gating；播放/暂停/上下句/语速/发音人/定时/错误提示）
        if (showTts) {
            TtsSheet(
                isPlaying = ttsPlaying,
                preferences = ttsPreferences,
                voices = ttsVoices,
                timerMinutes = ttsTimerMinutes,
                onPlay = { viewModel.startTts() },
                onPause = { viewModel.pauseTts() },
                onPrev = { viewModel.skipPreviousTts() },
                onNext = { viewModel.skipNextTts() },
                onSpeed = { viewModel.setTtsSpeed(it) },
                onVoice = { viewModel.setTtsVoice(it) },
                onTimer = { viewModel.setTtsTimer(it) },
                onDismiss = { showTts = false },
            )
        }

        // READ-10：TTS 一次性事件（错误 Toast / 缺语音数据引导下载 / 读完提示）
        val ttsEndedText = stringResource(R.string.tts_ended)
        val ttsInstallText = stringResource(R.string.tts_install_voice)
        val ttsInstallOk = stringResource(R.string.tts_install_voice_ok)
        LaunchedEffect(ttsEvent) {
            when (val ev = ttsEvent) {
                is ReaderTtsManager.Event.Error -> {
                    Toast.makeText(context, ev.message.resolve(context), Toast.LENGTH_LONG).show()
                    viewModel.consumeTtsEvent()
                }
                ReaderTtsManager.Event.MissingVoiceData -> {
                    Toast.makeText(context, ttsInstallText, Toast.LENGTH_LONG).show()
                    viewModel.requestTtsInstallVoice()
                    viewModel.consumeTtsEvent()
                }
                ReaderTtsManager.Event.Ended -> {
                    Toast.makeText(context, ttsEndedText, Toast.LENGTH_SHORT).show()
                    viewModel.consumeTtsEvent()
                }
                null -> Unit
            }
        }

        // 书签面板（READ-06：列表 + 跳回 + 删除）
        if (showBookmarks) {
            BookmarkSheet(
                items = bookmarks,
                onJump = { viewModel.jumpToBookmark(it); showBookmarks = false },
                onDelete = { viewModel.removeBookmark(it.id) },
                onClearAll = { viewModel.removeBookmarksForCurrent() },
                onDismiss = { showBookmarks = false },
            )
        }

        // 批注面板（READ-07：高亮列表 + 跳回 + 笔记编辑 + 删除 + 清空）
        if (showAnnotations) {
            AnnotationSheet(
                items = annotations,
                onJump = { viewModel.jumpToAnnotation(it); showAnnotations = false },
                onEdit = { editingAnnotation = it },
                onDelete = { viewModel.removeAnnotation(it.id) },
                onColorChange = { item, color -> viewModel.updateAnnotationColor(item.id, color) },
                onExport = { showExportFormat = true },
                onClearAll = { viewModel.clearHighlights(); showAnnotations = false },
                onDismiss = { showAnnotations = false },
            )
        }

        // 导出格式选择（DATA-01：Markdown / JSON）
        if (showExportFormat) {
            AlertDialog(
                onDismissRequest = { showExportFormat = false },
                title = { Text(stringResource(R.string.reader_export_title)) },
                text = { Text(stringResource(R.string.reader_export_choose)) },
                confirmButton = {
                    TextButton(onClick = {
                        showExportFormat = false
                        mdLauncher.launch(defaultFileName("md"))
                    }) { Text("Markdown") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showExportFormat = false
                        jsonLauncher.launch(defaultFileName("json"))
                    }) { Text("JSON") }
                },
            )
        }

        // 笔记编辑弹窗（READ-07：编辑 / 清空笔记）
        editingAnnotation?.let { item ->
            NoteEditDialog(
                initialNote = item.note,
                selectedText = item.selectedText,
                onConfirm = { note ->
                    viewModel.updateAnnotationNote(item.id, note)
                    editingAnnotation = null
                },
                onDelete = {
                    viewModel.removeAnnotation(item.id)
                    editingAnnotation = null
                },
                onDismiss = { editingAnnotation = null },
            )
        }

        // READ-09 链接交互：脚注弹层 / 内链确认 / 外链确认（VM 拦截后驱动，三态互斥）。
        when (val dialog = linkDialog) {
            is LinkDialog.Footnote -> FootnotePopup(
                contentHtml = dialog.contentHtml,
                onDismiss = { viewModel.dismissLinkDialog() },
            )
            is LinkDialog.InternalLink -> InternalLinkConfirmDialog(
                link = dialog.link,
                onConfirm = { viewModel.confirmInternalLink(dialog.link) },
                onDismiss = { viewModel.dismissLinkDialog() },
            )
            is LinkDialog.ExternalLink -> ExternalLinkConfirmDialog(
                url = dialog.url.toString(),
                onConfirm = { viewModel.confirmExternalLink() },
                onDismiss = { viewModel.dismissLinkDialog() },
            )
            null -> Unit
        }

        // Loading 遮罩（打开 Publication 中 / 进程重建重 open 中）
        val loadingText = stringResource(R.string.reader_loading)
        if (uiState is ReaderUiState.Loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = loadingText })
            }
        }

        // Error 遮罩
        (uiState as? ReaderUiState.Error)?.let { err ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(err.message.resolve(context), color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(16.dp))
                    OutlinedButton(onClick = onBack) { Text(stringResource(R.string.reader_back)) }
                }
            }
        }
    }
}

@Composable
private fun ReaderTopBar(
    progressText: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    isBookmarked: Boolean,
    canBookmark: Boolean,
    canSearch: Boolean,
    canTts: Boolean,
    onBack: () -> Unit,
    onOpenToc: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenTts: () -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenProgress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        // edge-to-edge 下避开 status bar 触摸拦截区（真机回归发现：未加 inset 时顶栏按钮
        // 上半进入 status bar 区被系统拦截，目录/进度/返回点不动——READ-02）。
        modifier = modifier.fillMaxWidth().statusBarsPadding(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_back_to_library))
                }
                IconButton(onClick = onOpenToc) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.reader_toc))
                }
                // READ-05：书内搜索入口（canSearch gating，红线 #2；PDF 未验证则隐藏）。
                if (canSearch) {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.reader_search))
                    }
                }
                // READ-10：TTS 朗读入口（canTts gating，红线 #2；PDF 恒 false 隐藏）。
                if (canTts) {
                    IconButton(onClick = onOpenTts) {
                        Icon(Icons.Filled.VolumeUp, contentDescription = stringResource(R.string.tts_title))
                    }
                }
                // READ-02：目录/进度跳转后可返回上一阅读位置（无历史时隐藏）。
                if (canGoBack) {
                    IconButton(onClick = onGoBack) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.reader_go_back))
                    }
                }
                // READ-09：back 撤销过的跳转可重做（无前进历史时隐藏；与上按钮成对）。
                if (canGoForward) {
                    IconButton(onClick = onGoForward) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.reader_go_forward))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // READ-06：当前位置加 / 取消书签（canBookmark gating 红线 #2；已加=实心，未加=空心）。
                IconButton(onClick = onToggleBookmark, enabled = canBookmark) {
                    Icon(
                        if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = stringResource(if (isBookmarked) R.string.reader_remove_bookmark else R.string.reader_add_bookmark),
                        tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                TextButton(onClick = onOpenProgress) {
                    Text(stringResource(R.string.reader_progress, progressText), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    onFontDecrease: () -> Unit,
    onFontIncrease: () -> Unit,
    onOpenTypography: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenAnnotations: () -> Unit,
    bookmarkCount: Int,
    annotationCount: Int,
    canBookmark: Boolean,
    canHighlight: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        // 对称避 navigation bar（手势条区），底栏按钮完全可点。
        modifier = modifier.fillMaxWidth().navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onFontDecrease) {
                Icon(Icons.Default.TextDecrease, contentDescription = stringResource(R.string.reader_font_decrease))
            }
            IconButton(onClick = onFontIncrease) {
                Icon(Icons.Default.TextIncrease, contentDescription = stringResource(R.string.reader_font_increase))
            }
            IconButton(onClick = onOpenTypography) {
                Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.reader_typography))
            }
            // READ-06：书签列表入口（canBookmark gating 红线 #2）。
            if (canBookmark) {
                TextButton(onClick = onOpenBookmarks) {
                    Text(stringResource(R.string.reader_bookmarks_count, bookmarkCount), style = MaterialTheme.typography.bodySmall)
                }
            }
            // READ-07：高亮 / 笔记列表入口（canHighlight gating 红线 #2；PDF V1 生效时隐藏）。
            if (canHighlight) {
                TextButton(onClick = onOpenAnnotations) {
                    Text(stringResource(R.string.reader_annotations_count, annotationCount), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * 目录面板（READ-02）。
 *
 * 扁平化后的 [TocItem] 列表，按 [TocItem.depth] 缩进；点击 [TocItem.link] 跳转并关闭。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TocSheet(
    items: List<TocItem>,
    onJump: (Link) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            stringResource(R.string.reader_toc),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .semantics { heading() },
        )
        if (items.isEmpty()) {
            Text(
                stringResource(R.string.reader_toc_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                itemsIndexed(items, key = { i, _ -> i }) { _, toc ->
                    Text(
                        toc.title,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onJump(toc.link) }
                            .padding(start = (24 + toc.depth * 16).dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
                    )
                }
            }
        }
    }
}

/**
 * 进度拖动面板（READ-02）。
 *
 * 点顶栏「进度 N%」展开。Slider 本地 state 跟手（`remember(progression)` 同步外部进度），
 * 松手（onValueChangeFinished）跳转一次；◄ ► 微调 ±1% 立即跳转。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressSheet(
    progression: Double,
    onJump: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
            val jumpTitle = stringResource(R.string.reader_progress_jump)
            Text(jumpTitle, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            var local by remember(progression) { mutableStateOf(progression.toFloat()) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    local = (local - 0.01f).coerceAtLeast(0f)
                    onJump(local.toDouble())
                }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.reader_progress_back))
                }
                Slider(
                    value = local,
                    onValueChange = { local = it },
                    onValueChangeFinished = { onJump(local.toDouble()) },
                    valueRange = 0f..1f,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "$jumpTitle ${(local * 100).toInt()}%" },
                )
                IconButton(onClick = {
                    local = (local + 0.01f).coerceAtMost(1f)
                    onJump(local.toDouble())
                }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.reader_progress_forward))
                }
            }
            Text(
                "${(local * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            OutlinedButton(onClick = onDismiss, modifier = Modifier.padding(top = 12.dp)) {
                Text(stringResource(R.string.reader_close))
            }
        }
    }
}

/**
 * 书内搜索面板（READ-05）。
 *
 * 顶部 [OutlinedTextField]（按搜索键触发 [onSearch]）；下方按 [SearchUiState] 分支渲染：
 * Idle→提示 / Loading→进度 / Results→结果数 + 列表（命中词主题色高亮，点跳转，滚到底自动加载更多）
 * / Error→消息。入口由顶栏搜索图标按 `canSearch` gating（红线 #2）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSheet(
    state: SearchUiState,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onJump: (Locator) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.reader_search_placeholder)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    onSearch(query)
                    keyboard?.hide()
                }),
                modifier = Modifier.weight(1f),
            )
            // 显式「搜索」按钮：软键盘搜索键的补充，便于 adb 点击 + 用户明确触发。
            // 放 TextField 外（Row 兄弟）而非 trailingIcon，避免点输入框右侧误触按钮 + 不干扰焦点。
            Button(
                onClick = { onSearch(query); keyboard?.hide() },
                modifier = Modifier.padding(start = 8.dp),
            ) { Text(stringResource(R.string.reader_search)) }
        }
        Spacer(Modifier.height(8.dp))
        when (state) {
            is SearchUiState.Idle -> Text(
                stringResource(R.string.reader_search_idle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
            )
            is SearchUiState.Loading -> Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.reader_search_loading, state.query))
            }
            is SearchUiState.Error -> Text(
                state.message.resolve(LocalContext.current),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
            )
            is SearchUiState.Results -> ResultsBody(state, onLoadMore, onJump)
        }
    }
}

/** 搜索结果列表（READ-05）：结果数 + 命中词高亮列表，滚到底自动加载更多。 */
@Composable
private fun ResultsBody(
    state: SearchUiState.Results,
    onLoadMore: () -> Unit,
    onJump: (Locator) -> Unit,
) {    val countText = when {
        state.resultCount != null -> stringResource(R.string.reader_search_results_found, state.resultCount)
        state.exhausted -> stringResource(R.string.reader_search_results_total, state.items.size)
        else -> stringResource(R.string.reader_search_results_loaded, state.items.size)
    }
    Text(
        countText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
    )
    if (state.items.isEmpty()) {
        Text(
            stringResource(R.string.reader_search_no_match, state.query),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        )
    } else {
        val listState = rememberLazyListState()
        // 滚动接近底部时自动加载下一批（分批避免大书卡死）。
        LaunchedEffect(state.items.size) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { lastVisible ->
                    if (lastVisible >= state.items.size - 3 && !state.loadingMore && !state.exhausted) {
                        onLoadMore()
                    }
                }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
        ) {
            items(state.items.size) { index ->
                SearchResultRow(state.items[index]) { onJump(state.items[index].locator) }
                HorizontalDivider(modifier = Modifier.padding(start = 24.dp))
            }
            if (state.loadingMore) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

/**
 * TTS 朗读面板（READ-10）。
 *
 * 控制行：上一句 / 播放-暂停 / 下一句；语速 Slider（0.5–2.0×，松手写）；
 * 发音人列表（会话未建时提示先播放；按书语言过滤近似——展示引擎全部声音由系统语言排序）；
 * 定时 chips（不定时/5/15/30 分钟，到期自动暂停）。
 * 偏好全部持久化（ReaderTtsPreferencesRepository），跨会话保位。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TtsSheet(
    isPlaying: Boolean,
    preferences: com.xuziyue.ebook.data.ReaderTtsPreferencesRepository.TtsPrefs?,
    voices: List<org.readium.navigator.media.tts.android.AndroidTtsEngine.Voice>,
    timerMinutes: Int,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSpeed: (Double) -> Unit,
    onVoice: (String?) -> Unit,
    onTimer: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.tts_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .semantics { heading() },
            )

            // 播放控制行
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrev) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.tts_prev_sentence))
                }
                IconButton(onClick = if (isPlaying) onPause else onPlay) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.tts_pause else R.string.tts_play),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.tts_next_sentence))
                }
            }

            // 语速（0.5–2.0×；本地跟手，松手写一次）
            val speedLabel = stringResource(R.string.tts_speed)
            var localSpeed by remember(preferences?.speed) { mutableStateOf((preferences?.speed ?: 1.0).toFloat()) }
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(speedLabel, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.tts_speed_value, localSpeed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = localSpeed,
                    onValueChange = { localSpeed = it },
                    onValueChangeFinished = { onSpeed(localSpeed.toDouble()) },
                    valueRange = 0.5f..2.0f,
                    modifier = Modifier.semantics { contentDescription = "$speedLabel $localSpeed" },
                )
            }

            // 发音人（会话未建 voices 空 → 提示点播放后选择；否则列声音按钮组）
            Text(
                stringResource(R.string.tts_voice),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            if (voices.isEmpty()) {
                Text(
                    stringResource(R.string.tts_voice_auto),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // 「自动」选项 + 引擎全部声音（名字含语言标识）
                    val autoSelected = preferences?.voiceId == null
                    val autoModifier = Modifier.semantics { role = Role.RadioButton; selected = autoSelected }
                    if (autoSelected) {
                        Button(onClick = { onVoice(null) }, modifier = autoModifier) {
                            Text(stringResource(R.string.tts_voice_auto))
                        }
                    } else {
                        OutlinedButton(onClick = { onVoice(null) }, modifier = autoModifier) {
                            Text(stringResource(R.string.tts_voice_auto))
                        }
                    }
                    voices.take(8).forEach { voice ->
                        val isSelected = preferences?.voiceId == voice.id.toString()
                        val vModifier = Modifier.semantics { role = Role.RadioButton; selected = isSelected }
                        val label = voice.id.toString().substringAfter(':').ifBlank { voice.language.toString() }
                        if (isSelected) {
                            Button(onClick = { onVoice(voice.id.toString()) }, modifier = vModifier) { Text(label, maxLines = 1) }
                        } else {
                            OutlinedButton(onClick = { onVoice(voice.id.toString()) }, modifier = vModifier) { Text(label, maxLines = 1) }
                        }
                    }
                }
            }

            // 定时停止 chips
            Text(
                stringResource(R.string.tts_timer),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                com.xuziyue.ebook.reader.tts.TtsTimer.MINUTES_OPTIONS.forEach { minutes ->
                    val isSelected = minutes == timerMinutes
                    val label = if (minutes == 0) {
                        stringResource(R.string.tts_timer_off)
                    } else {
                        stringResource(R.string.tts_timer_minutes, minutes)
                    }
                    val tModifier = Modifier.semantics { role = Role.RadioButton; selected = isSelected }
                    if (isSelected) {
                        Button(onClick = { onTimer(minutes) }, modifier = tModifier) { Text(label) }
                    } else {
                        OutlinedButton(onClick = { onTimer(minutes) }, modifier = tModifier) { Text(label) }
                    }
                }
            }
        }
    }
}

/** 单条搜索结果：上下文 + 命中词主题色高亮，点击跳转原文（READ-05）。 */
@Composable
private fun SearchResultRow(item: SearchResultItem, onClick: () -> Unit) {
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    val text = buildAnnotatedString {
        append(item.before)
        withStyle(SpanStyle(background = highlightColor)) { append(item.highlight) }
        append(item.after)
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

/**
 * 排版与显示偏好面板（design.md §4.4 TYPE-01/02 + TYPE-03）。
 *
 * 字号/行高/段距/页边距用 Slider（松手写一次，拖动用本地 state 跟手，避免高频写 DataStore）；
 * 对齐/字体/主题用按钮组（点即生效）。主题含「跟随系统」（[ReaderTheme.SYSTEM]）。
 * 显示设置（TYPE-03）：亮度 Slider + 常亮 Switch + 方向按钮组。
 * 所有改动经 VM → Repository 持久化，跨重启保位。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypographySheet(
    typography: ReaderTypography,
    displaySettings: ReaderDisplaySettings,
    perBookTypography: Boolean,
    hasBookOverride: Boolean,
    onDismiss: () -> Unit,
    onFontSize: (Double) -> Unit,
    onFontWeight: (Double) -> Unit,
    onLineHeight: (Double) -> Unit,
    onParagraphSpacing: (Double) -> Unit,
    onPageMargins: (Double) -> Unit,
    onTextAlign: (ReaderTextAlign) -> Unit,
    onTheme: (ReaderTheme) -> Unit,
    onFontFamily: (String?) -> Unit,
    onScrollMode: (ReaderScrollMode) -> Unit,
    onVolumeKeyPaging: (Boolean) -> Unit,
    onEnablePerBook: () -> Unit,
    onDisablePerBook: () -> Unit,
    onResetBookTypography: () -> Unit,
    onBrightness: (Float?) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onOrientation: (ReaderOrientation?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.reader_typography),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .semantics { heading() },
            )

            // 按书排版（TYPE-05）：开关「仅本书生效」+ 恢复全局默认。
            // 开=把当前排版快照落成本书覆盖（此后改动只写本书）；恢复=删覆盖行回到纯全局。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp)
                    .toggleable(
                        value = perBookTypography,
                        onValueChange = { enabled ->
                            if (enabled) onEnablePerBook() else onDisablePerBook()
                        },
                        role = Role.Switch,
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.typography_per_book), style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = perBookTypography,
                    onCheckedChange = null, // 点击由 Row 的 toggleable 统一处理，避免双重回调
                )
            }
            if (hasBookOverride) {
                TextButton(
                    onClick = onResetBookTypography,
                    modifier = Modifier.padding(start = 12.dp),
                ) { Text(stringResource(R.string.typography_reset_book)) }
            }

            TypographySlider(
                label = stringResource(R.string.typography_font_size),
                value = typography.fontSize ?: 1.0,
                range = 0.5..5.0,
                valueText = { "${(it * 100).toInt()}%" },
                onChange = onFontSize,
            )
            // 字重（TYPE-01 欠账，TYPE-05 补）：Readium 归一化 0.75–1.75，1.0=常规；null 显示 1.0。
            TypographySlider(
                label = stringResource(R.string.typography_font_weight),
                value = typography.fontWeight ?: 1.0,
                range = 0.75..1.75,
                valueText = { "%.2f".format(it) },
                onChange = onFontWeight,
            )
            TypographySlider(
                label = stringResource(R.string.typography_line_height),
                value = typography.lineHeight ?: 1.0,
                range = 1.0..3.0,
                valueText = { "%.2f×".format(it) },
                onChange = onLineHeight,
            )
            TypographySlider(
                label = stringResource(R.string.typography_paragraph_spacing),
                value = typography.paragraphSpacing ?: 0.0,
                range = 0.0..3.0,
                valueText = { "%.1f em".format(it) },
                onChange = onParagraphSpacing,
            )
            TypographySlider(
                label = stringResource(R.string.typography_page_margins),
                value = typography.pageMargins ?: 1.0,
                range = 0.5..4.0,
                valueText = { "%.1f×".format(it) },
                onChange = onPageMargins,
            )

            // 对齐（TYPE-01）
            OptionGroup(
                label = stringResource(R.string.typography_align),
                options = listOf(
                    ReaderTextAlign.JUSTIFY to stringResource(R.string.typography_align_justify),
                    ReaderTextAlign.START to stringResource(R.string.typography_align_start),
                ),
                selected = typography.textAlign,
                onSelect = onTextAlign,
            )

            // 字体（TYPE-01 + TYPE-05 预置霞鹜文楷；SAF 运行时导入在 Readium 3.3 无工程通道，推后）
            OptionGroup(
                label = stringResource(R.string.typography_font),
                options = listOf(
                    null to stringResource(R.string.typography_font_default),
                    "serif" to stringResource(R.string.typography_font_serif),
                    "sans-serif" to stringResource(R.string.typography_font_sans),
                    ReaderTypography.LXGW_FONT_FAMILY to stringResource(R.string.typography_font_lxgw),
                ),
                selected = typography.fontFamily,
                onSelect = onFontFamily,
            )

            // 翻页方式（READ-04：分页 / 纵向滚动）
            OptionGroup(
                label = stringResource(R.string.typography_paging),
                options = listOf(
                    ReaderScrollMode.PAGINATED to stringResource(R.string.typography_paging_paginated),
                    ReaderScrollMode.SCROLL to stringResource(R.string.typography_paging_scroll),
                ),
                // null = 分页（引擎默认），UI 显示 PAGINATED 选中。
                selected = typography.scroll ?: ReaderScrollMode.PAGINATED,
                onSelect = onScrollMode,
            )

            // 音量键翻页开关（READ-03：app 层 Fragment 拦截 KeyEvent，不传 Readium 引擎）。
            // SET-02：Row 用 toggleable 合并 label+switch 为一个语义节点（TalkBack 读「音量键翻页，开关，开/关」）。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp)
                    .toggleable(
                        value = typography.volumeKeyPaging,
                        onValueChange = onVolumeKeyPaging,
                        role = Role.Switch,
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.typography_volume_key), style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = typography.volumeKeyPaging,
                    onCheckedChange = null, // 点击由 Row 的 toggleable 统一处理，避免双重回调
                )
            }

            // 主题（TYPE-02，含跟随系统）
            OptionGroup(
                label = stringResource(R.string.typography_theme),
                options = listOf(
                    ReaderTheme.SYSTEM to stringResource(R.string.common_follow_system),
                    ReaderTheme.LIGHT to stringResource(R.string.typography_theme_light),
                    ReaderTheme.SEPIA to stringResource(R.string.typography_theme_sepia),
                    ReaderTheme.DARK to stringResource(R.string.typography_theme_dark),
                ),
                selected = typography.theme,
                onSelect = onTheme,
            )

            // ===== 显示设置（TYPE-03：亮度 / 常亮 / 方向，Window 层副作用）=====

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                stringResource(R.string.typography_display),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 4.dp)
                    .semantics { heading() },
            )

            // 亮度（TYPE-03）：null = 跟随系统（显示为最低档 + 「跟随系统」标注）。
            BrightnessSlider(
                brightness = displaySettings.brightness,
                onChange = onBrightness,
            )

            // 常亮开关（TYPE-03：阅读时保持屏幕常亮）。
            // SET-02：Row 用 toggleable 合并 label+switch（同音量键翻页）。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp)
                    .toggleable(
                        value = displaySettings.keepScreenOn,
                        onValueChange = onKeepScreenOn,
                        role = Role.Switch,
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.typography_keep_screen_on), style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = displaySettings.keepScreenOn,
                    onCheckedChange = null,
                )
            }

            // 方向（TYPE-03，含跟随系统）。
            OptionGroup(
                label = stringResource(R.string.typography_orientation),
                options = listOf(
                    ReaderOrientation.SYSTEM to stringResource(R.string.common_follow_system),
                    ReaderOrientation.PORTRAIT to stringResource(R.string.typography_orientation_portrait),
                    ReaderOrientation.LANDSCAPE to stringResource(R.string.typography_orientation_landscape),
                ),
                selected = displaySettings.orientation ?: ReaderOrientation.SYSTEM,
                onSelect = { onOrientation(it) },
            )
        }
    }
}

/**
 * 亮度滑块行（TYPE-03）。本地 state 跟手拖动，松手写一次。
 *
 * null = 跟随系统（Slider 显示在最左 0% 位置，文案标注「跟随系统」）。
 * 拖动到任意位置 → 设为 0.0–1.0；拖到最左（=0）→ 仍设为 0（最暗，非跟随系统）。
 * 「跟随系统」需点 OptionGroup 的 SYSTEM 按钮恢复（Slider 不设 null 入口，避免误触）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrightnessSlider(
    brightness: Float?,
    onChange: (Float?) -> Unit,
) {
    val brightnessLabel = stringResource(R.string.typography_brightness)
    val followSystemLabel = stringResource(R.string.common_follow_system)
    // null（跟随系统）用 0 作 Slider 显示位置；非 null 用实际值。
    var local by remember(brightness) { mutableStateOf(brightness ?: 0f) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(brightnessLabel, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (brightness == null) followSystemLabel else "${(brightness * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local) },
            valueRange = 0f..1f,
            modifier = Modifier.semantics {
                contentDescription = "$brightnessLabel " +
                    (if (brightness == null) followSystemLabel else "${(brightness * 100).toInt()}%")
            },
        )
    }
}

/**
 * 排版滑块行。本地 state 跟手拖动，[onChange] 在松手（onValueChangeFinished）时回调一次。
 * `remember(value)` 保证外部持久值变化时同步本地（如底栏 ± 改了字号，面板重开能反映）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypographySlider(
    label: String,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    valueText: (Double) -> String,
    onChange: (Double) -> Unit,
) {
    var local by remember(value) { mutableStateOf(value) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                valueText(local),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = local.toFloat(),
            onValueChange = { local = it.toDouble() },
            onValueChangeFinished = { onChange(local) },
            valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
            modifier = Modifier.semantics { contentDescription = "$label ${valueText(local)}" },
        )
    }
}

/** 选项按钮组（对齐 / 字体 / 主题）。选中态用 filled Button，未选用 OutlinedButton。
 * 用 [FlowRow] 自动换行——主题 4 选项（含「跟随系统」较宽）单行放不下时换行，
 * 避免「夜间」被挤出屏外不可见（真机回归发现）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> OptionGroup(
    label: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { (value, text) ->
                // SET-02：选中态原仅靠 filled/Outlined 样式区分，对 TalkBack 不可知；
                // 补 RadioButton 角色 + selected 语义，TalkBack 读「已选中/未选中」。
                val isSelected = value == selected
                val optModifier = Modifier.semantics {
                    role = Role.RadioButton
                    // 显式 this：避免与 OptionGroup 的 selected 形参混淆（形参是 T?）。
                    this.selected = isSelected
                }
                if (isSelected) {
                    Button(onClick = { onSelect(value) }, modifier = optModifier) { Text(text) }
                } else {
                    OutlinedButton(onClick = { onSelect(value) }, modifier = optModifier) { Text(text) }
                }
            }
        }
    }
}

/**
 * 书签面板（READ-06）。
 *
 * 列表项：摘录（无则"无摘录"）+ 相对时间；点击跳回原文并关面板；右侧删除。
 * 顶部「清空」一次清掉当前书全部书签（Repository deleteAllForBook，回流自动更新计数）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarkSheet(
    items: List<BookmarkItem>,
    onJump: (BookmarkItem) -> Unit,
    onDelete: (BookmarkItem) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val now = System.currentTimeMillis()
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.reader_bookmark_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            if (items.isNotEmpty()) {
                TextButton(onClick = onClearAll) { Text(stringResource(R.string.reader_clear)) }
            }
        }
        if (items.isEmpty()) {
            Text(
                stringResource(R.string.reader_bookmark_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                items(items, key = { it.id }) { bookmark ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onJump(bookmark) }
                            .padding(start = 24.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                bookmark.excerpt?.takeIf { it.isNotBlank() } ?: stringResource(R.string.bookmark_no_excerpt),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                relativeTime(bookmark.createdAt, now).resolve(context),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        IconButton(onClick = { onDelete(bookmark) }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.reader_bookmark_delete))
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 24.dp))
                }
            }
        }
    }
}

/**
 * 批注 / 高亮面板（READ-07）。
 *
 * 列表项：色点 + 选中文字（最多 2 行）+ 笔记预览 + 相对时间；点击跳回原文；右侧编辑 / 删除。
 * 顶部「清空」软删当前书全部批注（Repository softDeleteAllForBook，回流清 UI）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnnotationSheet(
    items: List<AnnotationItem>,
    onJump: (AnnotationItem) -> Unit,
    onEdit: (AnnotationItem) -> Unit,
    onDelete: (AnnotationItem) -> Unit,
    onColorChange: (AnnotationItem, HighlightColor) -> Unit,
    onExport: () -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val now = System.currentTimeMillis()
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.reader_annotation_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onExport) { Text(stringResource(R.string.reader_annotation_export)) }
                if (items.isNotEmpty()) {
                    TextButton(onClick = onClearAll) { Text(stringResource(R.string.reader_clear)) }
                }
            }
        }
        if (items.isEmpty()) {
            Text(
                stringResource(R.string.reader_annotation_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                items(items, key = { it.id }) { annotation ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onJump(annotation) }
                            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 四色调色板（当前色加环高亮，点击切换高亮颜色）
                        // SET-02：色点 12/16dp 远小于 48dp 触控目标，且无标签、仅靠颜色区分（违反「不只靠颜色」）。
                        // 外层 48dp 可点热区 + contentDescription（色名）+ RadioButton 角色 + selected 态，
                        // TalkBack 读「黄，单选按钮，已选中」；视觉圆点保留小尺寸不变。
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HighlightColor.values().forEach { color ->
                                val isSelected = color == annotation.color
                                val colorName = stringResource(
                                    when (color) {
                                        HighlightColor.YELLOW -> R.string.color_yellow
                                        HighlightColor.GREEN -> R.string.color_green
                                        HighlightColor.BLUE -> R.string.color_blue
                                        HighlightColor.PINK -> R.string.color_pink
                                    },
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .size(36.dp)
                                        .clickable(role = Role.RadioButton) { onColorChange(annotation, color) }
                                        .semantics {
                                            contentDescription = colorName
                                            selected = isSelected
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(if (isSelected) 16.dp else 12.dp)
                                            .background(color.toComposeColor(), CircleShape)
                                            .then(
                                                if (isSelected) {
                                                    Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                                } else {
                                                    Modifier
                                                },
                                            ),
                                    )
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                annotation.selectedText.ifBlank { stringResource(R.string.annotation_empty_selection) },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            annotation.note?.takeIf { it.isNotBlank() }?.let { note ->
                                Text(
                                    stringResource(R.string.note_label, note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            Text(
                                relativeTime(annotation.createdAt, now).resolve(context),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        IconButton(onClick = { onEdit(annotation) }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.reader_annotation_edit))
                        }
                        IconButton(onClick = { onDelete(annotation) }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.reader_annotation_delete))
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 24.dp))
                }
            }
        }
    }
}

/**
 * 笔记编辑弹窗（READ-07）。
 *
 * 顶部展示选中文字作上下文；[OutlinedTextField] 多行编辑笔记。
 * 「保存」→ onConfirm（空串转 null）；「删除」→ onDelete（软删）；「取消」→ onDismiss。
 */
@Composable
private fun NoteEditDialog(
    initialNote: String?,
    selectedText: String,
    onConfirm: (String?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var note by remember { mutableStateOf(initialNote ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_note_title)) },
        text = {
            Column {
                Text(
                    selectedText.ifBlank { stringResource(R.string.annotation_empty_selection) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text(stringResource(R.string.reader_note_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(note.ifBlank { null }) }) { Text(stringResource(R.string.reader_note_save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.reader_note_delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.reader_note_cancel)) }
            }
        },
    )
}

/**
 * 从 [Context] 向上查找 [Activity]（lint 安全：LocalContext 不一定是 Activity，
 * 但在 Compose Activity 宿主下 ContextWrapper 链最终到达 Activity）。
 */
private fun Context.findActivity(): Activity {
    var ctx: Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    error("无法从 Context 找到 Activity")
}

/**
 * 脚注弹层（READ-09）：WebView 渲染 Readium 清洗后的脚注 HTML 片段。
 *
 * 红线 #4 口径：noteContent 已经过库内 `Jsoup.clean(Safelist.relaxed())`（去脚本/事件处理器），
 * 本层再收紧——JS 关闭、外部网络加载一律拒绝（app 本就无 INTERNET 权限）、
 * `loadDataWithBaseURL(null,…)` 使相对链接不可解析。深浅色由主题包裹适配。
 */
@Composable
private fun FootnotePopup(
    contentHtml: String,
    onDismiss: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_footnote_title)) },
        text = {
            // WebView 在 AndroidView 内一次性加载内容；state 变化仅来自弹层重建。
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        // 深浅色：夜间用浅字深底，避免黑底黑字。
                        setBackgroundColor(
                            if (isDark) 0xFF1C1B1F.toInt() else android.graphics.Color.WHITE,
                        )
                        loadDataWithBaseURL(null, wrapFootnoteHtml(contentHtml, isDark), "text/html", "utf-8", null)
                    }
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.reader_close)) }
        },
    )
}

/** 脚注 HTML 片段包一层排版（字体大小 / 行高 / 夜间配色），避免默认 16px 正文过小。 */
private fun wrapFootnoteHtml(html: String, isDark: Boolean): String {
    val color = if (isDark) "#E6E1E5" else "#1C1B1F"
    return """
        <html><head><meta charset="utf-8"><style>
        body { margin: 0; padding: 4px; color: $color; font-size: 15px; line-height: 1.6;
               word-break: break-word; }
        </style></head><body>$html</body></html>
    """.trimIndent()
}

/** 内链确认弹窗（READ-09：普通内链 / EPUB2 旧式脚注跳转前确认；跳转后可返回）。 */
@Composable
private fun InternalLinkConfirmDialog(
    link: Link,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_internal_link_title)) },
        text = { Text(stringResource(R.string.reader_internal_link_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.reader_internal_link_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/** 外链确认弹窗（READ-09 验收：外链不会静默打开；确认后交系统浏览器）。 */
@Composable
private fun ExternalLinkConfirmDialog(
    url: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_external_link_title)) },
        text = {
            Column {
                Text(stringResource(R.string.reader_external_link_text))
                Spacer(Modifier.height(8.dp))
                Text(
                    url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.reader_external_link_open)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
