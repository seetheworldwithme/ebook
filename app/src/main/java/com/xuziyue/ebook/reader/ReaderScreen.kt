package com.xuziyue.ebook.reader

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.AndroidFragment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuziyue.ebook.data.export.ExportBookDataUseCase
import com.xuziyue.ebook.model.HighlightColor
import com.xuziyue.ebook.model.ReaderScrollMode
import com.xuziyue.ebook.model.ReaderTextAlign
import com.xuziyue.ebook.model.ReaderTheme
import com.xuziyue.ebook.model.ReaderTypography
import com.xuziyue.ebook.ui.relativeTime
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

    LaunchedEffect(bookId) {
        viewModel.openBook(bookId)
    }
    // 跟随系统主题：系统暗色变化时推入 VM，解析 ReaderTheme.SYSTEM → DARK/LIGHT。
    val systemDark = isSystemInDarkTheme()
    LaunchedEffect(systemDark) {
        viewModel.setSystemDark(systemDark)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val progressText by viewModel.progressText.collectAsStateWithLifecycle()
    val progression by viewModel.progression.collectAsStateWithLifecycle()
    val tableOfContents by viewModel.tableOfContents.collectAsStateWithLifecycle()
    val canGoBack by viewModel.canGoBack.collectAsStateWithLifecycle()
    val decorations by viewModel.decorations.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val typography by viewModel.typography.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val annotations by viewModel.annotations.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()

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
    LaunchedEffect(Unit) {
        viewModel.exportEvents.collect { outcome ->
            val msg = when (outcome) {
                is ExportBookDataUseCase.Outcome.Success ->
                    "已导出 ${outcome.items} 条（${if (outcome.format == ExportBookDataUseCase.Format.JSON) "JSON" else "Markdown"}）"
                is ExportBookDataUseCase.Outcome.Failed -> outcome.message
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

    Box(modifier = Modifier.fillMaxSize()) {
        // 核心：Compose 托管 ReaderFragment（内部 childFragmentManager 托管 EpubNavigatorFragment）
        AndroidFragment<ReaderFragment>(modifier = Modifier.fillMaxSize())

        // READ-03：点击左右边缘翻页（仅分页模式；scroll 模式是上下滚动，点击无意义）。
        // 左右各 20% 宽，中间 60% 留给 WebView 文本选择 / 链接。顶栏 / 底栏在更高 z 层覆盖上下区。
        if (typography.scroll != ReaderScrollMode.SCROLL) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.2f)
                    .align(Alignment.TopStart)
                    .clickable { viewModel.goBackwardPaging() },
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.2f)
                    .align(Alignment.TopEnd)
                    .clickable { viewModel.goForwardPaging() },
            )
        }

        // 顶部控制条（READ-02：目录 / 返回上一位置 / 进度入口；READ-06：书签 toggle）
        ReaderTopBar(
            progressText = progressText,
            canGoBack = canGoBack,
            isBookmarked = isBookmarked,
            canBookmark = capabilities.canBookmark,
            canSearch = capabilities.canSearch,
            onBack = onBack,
            onOpenToc = { showToc = true },
            onOpenSearch = { showSearch = true },
            onGoBack = { viewModel.goBack() },
            onToggleBookmark = { viewModel.toggleBookmark() },
            onOpenProgress = { showProgress = true },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // 底部控制条
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
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // 排版面板（TYPE-01 字号/行高/段距/页边距/对齐/字体 + TYPE-02 主题含跟随系统）
        if (showTypography) {
            TypographySheet(
                typography = typography,
                onDismiss = { showTypography = false },
                onFontSize = { viewModel.setFontSize(it) },
                onLineHeight = { viewModel.setLineHeight(it) },
                onParagraphSpacing = { viewModel.setParagraphSpacing(it) },
                onPageMargins = { viewModel.setPageMargins(it) },
                onTextAlign = { viewModel.setTextAlign(it) },
                onTheme = { viewModel.setTheme(it) },
                onFontFamily = { viewModel.setFontFamily(it) },
                onScrollMode = { viewModel.setScrollMode(it) },
                onVolumeKeyPaging = { viewModel.setVolumeKeyPaging(it) },
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
                title = { Text("导出批注") },
                text = { Text("选择导出格式") },
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

        // Loading 遮罩（打开 Publication 中 / 进程重建重 open 中）
        if (uiState is ReaderUiState.Loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        // Error 遮罩
        (uiState as? ReaderUiState.Error)?.let { err ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(err.message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(16.dp))
                    OutlinedButton(onClick = onBack) { Text("返回") }
                }
            }
        }
    }
}

@Composable
private fun ReaderTopBar(
    progressText: String,
    canGoBack: Boolean,
    isBookmarked: Boolean,
    canBookmark: Boolean,
    canSearch: Boolean,
    onBack: () -> Unit,
    onOpenToc: () -> Unit,
    onOpenSearch: () -> Unit,
    onGoBack: () -> Unit,
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回书库")
                }
                IconButton(onClick = onOpenToc) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "目录")
                }
                // READ-05：书内搜索入口（canSearch gating，红线 #2；PDF 未验证则隐藏）。
                if (canSearch) {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索")
                    }
                }
                // READ-02：目录/进度跳转后可返回上一阅读位置（无历史时隐藏）。
                if (canGoBack) {
                    IconButton(onClick = onGoBack) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "返回上一阅读位置")
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // READ-06：当前位置加 / 取消书签（canBookmark gating 红线 #2；已加=实心，未加=空心）。
                IconButton(onClick = onToggleBookmark, enabled = canBookmark) {
                    Icon(
                        if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (isBookmarked) "取消书签" else "加书签",
                        tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                TextButton(onClick = onOpenProgress) {
                    Text("进度 $progressText", style = MaterialTheme.typography.bodyMedium)
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
                Icon(Icons.Default.TextDecrease, contentDescription = "字号减小")
            }
            IconButton(onClick = onFontIncrease) {
                Icon(Icons.Default.TextIncrease, contentDescription = "字号增大")
            }
            IconButton(onClick = onOpenTypography) {
                Icon(Icons.Default.Tune, contentDescription = "排版")
            }
            // READ-06：书签列表入口（canBookmark gating 红线 #2）。
            if (canBookmark) {
                TextButton(onClick = onOpenBookmarks) {
                    Text("书签 $bookmarkCount", style = MaterialTheme.typography.bodySmall)
                }
            }
            // READ-07：高亮 / 笔记列表入口（canHighlight gating 红线 #2；PDF V1 生效时隐藏）。
            if (canHighlight) {
                TextButton(onClick = onOpenAnnotations) {
                    Text("笔记 $annotationCount", style = MaterialTheme.typography.bodySmall)
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
            "目录",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (items.isEmpty()) {
            Text(
                "本书没有目录",
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
                            .clickable { onJump(toc.link) }
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
            Text("跳转进度", style = MaterialTheme.typography.titleMedium)
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
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "后退 1%")
                }
                Slider(
                    value = local,
                    onValueChange = { local = it },
                    onValueChangeFinished = { onJump(local.toDouble()) },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    local = (local + 0.01f).coerceAtMost(1f)
                    onJump(local.toDouble())
                }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "前进 1%")
                }
            }
            Text(
                "${(local * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            OutlinedButton(onClick = onDismiss, modifier = Modifier.padding(top = 12.dp)) {
                Text("关闭")
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
                placeholder = { Text("搜索本书内容…") },
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
            ) { Text("搜索") }
        }
        Spacer(Modifier.height(8.dp))
        when (state) {
            is SearchUiState.Idle -> Text(
                "输入关键词后按搜索键，在全书查找。",
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
                Text("搜索「${state.query}」中…")
            }
            is SearchUiState.Error -> Text(
                state.message,
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
) {
    val countText = when {
        state.resultCount != null -> "找到 ${state.resultCount} 条结果"
        state.exhausted -> "共 ${state.items.size} 条结果"
        else -> "已加载 ${state.items.size} 条结果"
    }
    Text(
        countText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
    )
    if (state.items.isEmpty()) {
        Text(
            "没有匹配「${state.query}」的内容",
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
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

/**
 * 排版偏好面板（design.md §4.4 TYPE-01/02）。
 *
 * 字号/行高/段距/页边距用 Slider（松手写一次，拖动用本地 state 跟手，避免高频写 DataStore）；
 * 对齐/字体/主题用按钮组（点即生效）。主题含「跟随系统」（[ReaderTheme.SYSTEM]）。
 * 所有改动经 VM → Repository 持久化，跨重启保位。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypographySheet(
    typography: ReaderTypography,
    onDismiss: () -> Unit,
    onFontSize: (Double) -> Unit,
    onLineHeight: (Double) -> Unit,
    onParagraphSpacing: (Double) -> Unit,
    onPageMargins: (Double) -> Unit,
    onTextAlign: (ReaderTextAlign) -> Unit,
    onTheme: (ReaderTheme) -> Unit,
    onFontFamily: (String?) -> Unit,
    onScrollMode: (ReaderScrollMode) -> Unit,
    onVolumeKeyPaging: (Boolean) -> Unit,
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
                "排版",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            TypographySlider(
                label = "字号",
                value = typography.fontSize ?: 1.0,
                range = 0.5..5.0,
                valueText = { "${(it * 100).toInt()}%" },
                onChange = onFontSize,
            )
            TypographySlider(
                label = "行高",
                value = typography.lineHeight ?: 1.0,
                range = 1.0..3.0,
                valueText = { "%.2f×".format(it) },
                onChange = onLineHeight,
            )
            TypographySlider(
                label = "段距",
                value = typography.paragraphSpacing ?: 0.0,
                range = 0.0..3.0,
                valueText = { "%.1f em".format(it) },
                onChange = onParagraphSpacing,
            )
            TypographySlider(
                label = "页边距",
                value = typography.pageMargins ?: 1.0,
                range = 0.5..4.0,
                valueText = { "%.1f×".format(it) },
                onChange = onPageMargins,
            )

            // 对齐（TYPE-01）
            OptionGroup(
                label = "对齐",
                options = listOf(
                    ReaderTextAlign.JUSTIFY to "两端对齐",
                    ReaderTextAlign.START to "左对齐",
                ),
                selected = typography.textAlign,
                onSelect = onTextAlign,
            )

            // 字体（TYPE-01；自定义字体导入是 P1 TYPE-05，这里只给通用字体族预设）
            OptionGroup(
                label = "字体",
                options = listOf(
                    null to "默认",
                    "serif" to "衬线",
                    "sans-serif" to "无衬线",
                ),
                selected = typography.fontFamily,
                onSelect = onFontFamily,
            )

            // 翻页方式（READ-04：分页 / 纵向滚动）
            OptionGroup(
                label = "翻页方式",
                options = listOf(
                    ReaderScrollMode.PAGINATED to "分页",
                    ReaderScrollMode.SCROLL to "滚动",
                ),
                // null = 分页（引擎默认），UI 显示 PAGINATED 选中。
                selected = typography.scroll ?: ReaderScrollMode.PAGINATED,
                onSelect = onScrollMode,
            )

            // 音量键翻页开关（READ-03：app 层 Fragment 拦截 KeyEvent，不传 Readium 引擎）。
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("音量键翻页", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = typography.volumeKeyPaging,
                    onCheckedChange = onVolumeKeyPaging,
                )
            }

            // 主题（TYPE-02，含跟随系统）
            OptionGroup(
                label = "主题",
                options = listOf(
                    ReaderTheme.SYSTEM to "跟随系统",
                    ReaderTheme.LIGHT to "日间",
                    ReaderTheme.SEPIA to "米黄",
                    ReaderTheme.DARK to "夜间",
                ),
                selected = typography.theme,
                onSelect = onTheme,
            )
        }
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
                if (value == selected) {
                    Button(onClick = { onSelect(value) }) { Text(text) }
                } else {
                    OutlinedButton(onClick = { onSelect(value) }) { Text(text) }
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
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("书签", style = MaterialTheme.typography.titleMedium)
            if (items.isNotEmpty()) {
                TextButton(onClick = onClearAll) { Text("清空") }
            }
        }
        if (items.isEmpty()) {
            Text(
                "本书没有书签",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                items(items, key = { it.id }) { bookmark ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onJump(bookmark) }
                            .padding(start = 24.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                bookmark.excerpt?.takeIf { it.isNotBlank() } ?: "无摘录",
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                relativeTime(bookmark.createdAt, now),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        IconButton(onClick = { onDelete(bookmark) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除书签")
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
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("笔记 / 高亮", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onExport) { Text("导出") }
                if (items.isNotEmpty()) {
                    TextButton(onClick = onClearAll) { Text("清空") }
                }
            }
        }
        if (items.isEmpty()) {
            Text(
                "没有高亮 / 笔记（长按正文选中文字后选「高亮」）",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                items(items, key = { it.id }) { annotation ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onJump(annotation) }
                            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 四色调色板（当前色加环高亮，点击切换高亮颜色）
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HighlightColor.values().forEach { color ->
                                val selected = color == annotation.color
                                Box(
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .size(if (selected) 16.dp else 12.dp)
                                        .background(color.toComposeColor(), CircleShape)
                                        .then(
                                            if (selected) {
                                                Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                            } else {
                                                Modifier.clickable { onColorChange(annotation, color) }
                                            },
                                        ),
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                annotation.selectedText.ifBlank { "（空选区）" },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            annotation.note?.takeIf { it.isNotBlank() }?.let { note ->
                                Text(
                                    "笔记：$note",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            Text(
                                relativeTime(annotation.createdAt, now),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        IconButton(onClick = { onEdit(annotation) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "编辑笔记")
                        }
                        IconButton(onClick = { onDelete(annotation) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除高亮")
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
        title = { Text("编辑笔记") },
        text = {
            Column {
                Text(
                    selectedText.ifBlank { "（空选区）" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("写点笔记…") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(note.ifBlank { null }) }) { Text("保存") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("删除") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
