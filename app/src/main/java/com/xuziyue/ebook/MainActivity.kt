package com.xuziyue.ebook

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.xuziyue.ebook.R
import com.xuziyue.ebook.data.ImportBookUseCase
import com.xuziyue.ebook.data.bookIdOrNull
import com.xuziyue.ebook.library.CollectionPickerSheet
import com.xuziyue.ebook.library.LibraryFilter
import com.xuziyue.ebook.library.LibrarySort
import com.xuziyue.ebook.library.LibraryViewMode
import com.xuziyue.ebook.library.LibraryViewModel
import com.xuziyue.ebook.model.Collection
import com.xuziyue.ebook.model.CollectionKind
import com.xuziyue.ebook.model.LibraryItem
import com.xuziyue.ebook.library.BookDetailScreen
import com.xuziyue.ebook.reader.ReaderScreen
import com.xuziyue.ebook.settings.LicensesScreen
import com.xuziyue.ebook.settings.PrivacyScreen
import com.xuziyue.ebook.settings.SettingsScreen
import com.xuziyue.ebook.ui.BookCover
import com.xuziyue.ebook.ui.relativeTime
import com.xuziyue.ebook.ui.resolve
import com.xuziyue.ebook.ui.theme.EbookReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 单 Activity 入口。
 *
 * Navigation Compose：
 * - `library`：书库列表 + 导入入口（LIB-01 完整：列表/网格 + 封面 + 进度 + 最近阅读；LIB-02 三入口；LIB-03 搜索/排序）。
 * - `detail/{bookId}`：书籍详情页（LIB-04）。卡片点击进此，"继续阅读"再进 reader。
 * - `reader/{bookId}`：阅读界面。bookId 作 route 参数，进程重建后 Navigation 自动恢复（design.md §6.5）。
 *
 * IMP-02：接收 ACTION_VIEW / ACTION_SEND（文件管理器 / 分享面板打开电子书）。
 * 冷启动 [onCreate] + 热启动 [onNewIntent] 均从 Intent 取 Uri → [pendingImport] → LibraryScreen 消费。
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    /** IMP-02：待导入的外部 Uri（从 ACTION_VIEW/SEND Intent 提取，LibraryScreen 消费）。 */
    private val pendingImport = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleImportIntent(intent)
        setContent {
            EbookReaderTheme {
                AppRoot(
                    pendingImport = pendingImport,
                    onConsumeImport = { pendingImport.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleImportIntent(intent)
    }

    /**
     * IMP-02：从 ACTION_VIEW / ACTION_SEND Intent 提取电子书 Uri。
     *
     * ACTION_VIEW → [Intent.getData]；ACTION_SEND → [Intent.EXTRA_STREAM]。
     * 提取后推入 [pendingImport]，清 action 防旋转重建重复触发。
     */
    private fun handleImportIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW && intent?.action != Intent.ACTION_SEND) return
        val uri = intent.data ?: intent.getStreamUri()
        if (uri != null) {
            pendingImport.value = uri
            intent.action = null
        }
    }

    /** 兼容 API 33+ 的 getParcelableExtra 泛型签名变化。 */
    @Suppress("DEPRECATION")
    private fun Intent.getStreamUri(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        }
}

@Composable
private fun AppRoot(
    pendingImport: StateFlow<Uri?>,
    onConsumeImport: () -> Unit,
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "library") {
        composable("library") {
            LibraryScreen(
                pendingImport = pendingImport,
                onConsumeImport = onConsumeImport,
                onOpenBook = { bookId -> navController.navigate("detail/$bookId") },
                onOpenReader = { bookId -> navController.navigate("reader/$bookId") },
                onOpenSettings = { navController.navigate("settings") },
            )
        }
        composable(
            route = "detail/{bookId}",
            arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId")
            if (bookId == null) {
                navController.popBackStack()
                return@composable
            }
            BookDetailScreen(
                onBack = { navController.popBackStack() },
                onRead = { navController.navigate("reader/$bookId") },
            )
        }
        composable(
            route = "reader/{bookId}",
            arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId")
            if (bookId == null) {
                navController.popBackStack()
                return@composable
            }
            ReaderScreen(bookId = bookId, onBack = { navController.popBackStack() })
        }
        // SET-05：设置 / 隐私说明 / 开源许可证
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenPrivacy = { navController.navigate("privacy") },
                onOpenLicenses = { navController.navigate("licenses") },
                onOpenStatistics = { navController.navigate("statistics") },
                onOpenBackup = { navController.navigate("backup") },
            )
        }
        composable("privacy") {
            PrivacyScreen(onBack = { navController.popBackStack() })
        }
        composable("licenses") {
            LicensesScreen(onBack = { navController.popBackStack() })
        }
        // DATA-04 阅读统计
        composable("statistics") {
            com.xuziyue.ebook.statistics.StatisticsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        // DATA-03 备份与恢复
        composable("backup") {
            com.xuziyue.ebook.backup.BackupScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/**
 * 书库页（LIB-01 / LIB-02 / LIB-03）。
 *
 * 顶栏：标题 + 排序（DropdownMenu：最近阅读/导入时间/书名）+ 视图切换（列表/网格）+ 导入。
 * 三入口 TabRow（LIB-02）：最近阅读（打开过即算）/ 全部（默认）/ 已读完。
 * 默认列表（横向卡：封面缩略 + 书名 + 作者 + 进度条 + 最近阅读时间）；可切网格（封面墙）。
 * 搜索：书名 / 作者（DAO LIKE，忽略大小写；中文直接匹配）。
 */
@Composable
private fun LibraryScreen(
    pendingImport: StateFlow<Uri?>,
    onConsumeImport: () -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenReader: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: LibraryViewModel = hiltViewModel()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle(initialValue = emptyList())
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    // LIB-05：书架
    val collections by viewModel.collections.collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedCollectionId by viewModel.selectedCollectionId.collectAsStateWithLifecycle()
    // LIB-06：批量选择
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 静态导入结果文案在 Composable 作用域解析（lint 要求 stringResource 而非 context.getString，
    // 这样随系统语言变化自动重组；Failed 的动态消息走 UserMessage.resolve(context)）。
    val importSuccessText = stringResource(R.string.import_success)
    val importAlreadyExistsText = stringResource(R.string.import_already_exists)
    val importProgressText = stringResource(R.string.reader_importing)
    val deleteLongClickLabel = stringResource(R.string.library_delete_long_click)
    var pendingDelete by remember { mutableStateOf<LibraryItem?>(null) }
    // LIB-05/06：书架选择 sheet + 书架重命名/删除对话框状态
    var showCollectionPicker by remember { mutableStateOf(false) }
    var pendingShelfAction by remember { mutableStateOf<ShelfDialog?>(null) }
    val favoriteName = stringResource(R.string.shelf_system_favorite)

    // IMP-02：外部 Intent 导入（ACTION_VIEW/SEND），与 SAF 导入共用 importEvents 反馈通道。
    LaunchedEffect(Unit) {
        pendingImport.collect { uri ->
            if (uri != null) {
                onConsumeImport()
                viewModel.importUri(uri)
            }
        }
    }

    // IMP-05：导入结果反馈（Toast）+ 成功跳阅读器。
    LaunchedEffect(Unit) {
        viewModel.importEvents.collect { outcome ->
            val msg = when (outcome) {
                is ImportBookUseCase.Outcome.Imported -> importSuccessText
                is ImportBookUseCase.Outcome.AlreadyExists -> importAlreadyExistsText
                is ImportBookUseCase.Outcome.Failed -> outcome.message.resolve(context)
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            // 成功（Imported / AlreadyExists）→ 跳阅读器（用户意图是看书）。
            outcome.bookIdOrNull()?.let { onOpenReader(it) }
        }
    }

    // IMP-07：删除结果反馈（Toast）。两态走 UserMessage.resolve（避免 Composable 内 context.getString 触发 lint）。
    LaunchedEffect(Unit) {
        viewModel.deleteEvents.collect { outcome ->
            Toast.makeText(context, outcome.message.resolve(context), Toast.LENGTH_SHORT).show()
        }
    }

    // LIB-05/06：书架操作结果反馈（Toast）。
    LaunchedEffect(Unit) {
        viewModel.shelfEvents.collect { outcome ->
            Toast.makeText(context, outcome.message.resolve(context), Toast.LENGTH_SHORT).show()
        }
    }

    // SAF 文件选择器（红线 #3：不申请 MANAGE_EXTERNAL_STORAGE，只用 ACTION_OPEN_DOCUMENT）。
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importUri(it) } }

    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 顶栏：标题 + 排序 + 视图切换 + 导入
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.library_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        TextButton(onClick = { showSortMenu = true }) { Text(stringResource(R.string.library_sort)) }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                        ) {
                            SortMenuItem(stringResource(R.string.library_sort_recent), sort, LibrarySort.LAST_OPENED) {
                                viewModel.setSort(it); showSortMenu = false
                            }
                            SortMenuItem(stringResource(R.string.library_sort_imported), sort, LibrarySort.IMPORTED) {
                                viewModel.setSort(it); showSortMenu = false
                            }
                            SortMenuItem(stringResource(R.string.library_sort_title), sort, LibrarySort.TITLE) {
                                viewModel.setSort(it); showSortMenu = false
                            }
                        }
                    }
                    TextButton(onClick = { viewModel.toggleViewMode() }) {
                        Text(if (viewMode == LibraryViewMode.LIST) stringResource(R.string.library_view_grid) else stringResource(R.string.library_view_list))
                    }
                    OutlinedButton(onClick = {
                        launcher.launch(arrayOf("application/epub+zip", "text/plain", "*/*"))
                    }) { Text(stringResource(R.string.library_import)) }
                    // SET-05：设置入口（隐私说明 / 开源许可证 / 崩溃日志）。
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.library_settings))
                    }
                }
            }

            // 搜索框（LIB-03）
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text(stringResource(R.string.library_search_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )

            // IMP-05：导入进行中 indeterminate 进度条。
            if (importing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().semantics { contentDescription = importProgressText })
            }

            // 四入口（LIB-02 三入口 + LIB-05 书架）：最近阅读 / 全部 / 已读完 / 书架
            PrimaryTabRow(selectedTabIndex = filter.ordinal) {
                Tab(
                    selected = filter == LibraryFilter.RECENT,
                    onClick = { viewModel.setFilter(LibraryFilter.RECENT) },
                    text = { Text(stringResource(R.string.library_tab_recent)) },
                )
                Tab(
                    selected = filter == LibraryFilter.ALL,
                    onClick = { viewModel.setFilter(LibraryFilter.ALL) },
                    text = { Text(stringResource(R.string.library_tab_all)) },
                )
                Tab(
                    selected = filter == LibraryFilter.FINISHED,
                    onClick = { viewModel.setFilter(LibraryFilter.FINISHED) },
                    text = { Text(stringResource(R.string.library_tab_finished)) },
                )
                Tab(
                    selected = filter == LibraryFilter.SHELVES,
                    onClick = {
                        viewModel.setFilter(LibraryFilter.SHELVES)
                        viewModel.openCollection(null)
                    },
                    text = { Text(stringResource(R.string.library_tab_shelves)) },
                )
            }

            HorizontalDivider()

            // LIB-06：批量选择模式下的上下文操作栏（替换排序/导入）
            if (selectionMode) {
                BatchActionBar(
                    selectedCount = selectedIds.size,
                    onSelectAll = viewModel::selectAllVisible,
                    onAddToShelf = { showCollectionPicker = true },
                    onDelete = { pendingShelfAction = ShelfDialog.BatchDelete(selectedIds.size) },
                    onCancel = viewModel::clearSelection,
                )
                HorizontalDivider()
            }

            // 书架 Tab 分两种视图：书架列表（selectedCollectionId == null）/ 书架内书籍
            if (filter == LibraryFilter.SHELVES && selectedCollectionId == null && !selectionMode) {
                ShelfListView(
                    collections = collections,
                    onOpenCollection = { viewModel.openCollection(it) },
                    onNewShelf = { pendingShelfAction = ShelfDialog.CreateShelf },
                    onRenameShelf = { pendingShelfAction = ShelfDialog.RenameShelf(it) },
                    onDeleteShelf = { pendingShelfAction = ShelfDialog.DeleteShelf(it) },
                )
            } else {
                // 书架内浏览时顶部加返回书架列表的条目
                if (filter == LibraryFilter.SHELVES && selectedCollectionId != null) {
                    val shelf = collections.firstOrNull { it.id == selectedCollectionId }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openCollection(null) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            shelf?.let {
                                if (it.kind == CollectionKind.SYSTEM_FAVORITE) stringResource(R.string.shelf_system_favorite)
                                else it.name
                            } ?: stringResource(R.string.shelf_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                when {
                    items.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            // 空态文案按筛选维度区分（LIB-02：已读完/最近阅读空 ≠ 没书）
                            val emptyText = when {
                                query.isNotBlank() -> stringResource(R.string.library_empty_search, query)
                                filter == LibraryFilter.FINISHED -> stringResource(R.string.library_empty_finished)
                                filter == LibraryFilter.RECENT -> stringResource(R.string.library_empty_recent)
                                filter == LibraryFilter.SHELVES -> stringResource(R.string.shelf_book_count_zero)
                                else -> stringResource(R.string.library_empty_all)
                            }
                            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    viewMode == LibraryViewMode.LIST -> {
                        LazyColumn {
                            items(items, key = { it.book.id }) { item ->
                                val isSelected = item.book.id in selectedIds
                                LibraryListRow(
                                    item = item,
                                    selected = selectionMode && isSelected,
                                    onClick = {
                                        if (selectionMode) viewModel.toggleSelection(item.book.id)
                                        else onOpenBook(item.book.id)
                                    },
                                    onLongClick = {
                                        if (selectionMode) viewModel.toggleSelection(item.book.id)
                                        else viewModel.enterSelectionMode(item.book.id)
                                    },
                                    onLongClickLabel = stringResource(R.string.batch_selected_count, selectedIds.size),
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            gridItems(items, key = { it.book.id }) { item ->
                                val isSelected = item.book.id in selectedIds
                                LibraryGridCard(
                                    item = item,
                                    selected = selectionMode && isSelected,
                                    onClick = {
                                        if (selectionMode) viewModel.toggleSelection(item.book.id)
                                        else onOpenBook(item.book.id)
                                    },
                                    onLongClick = {
                                        if (selectionMode) viewModel.toggleSelection(item.book.id)
                                        else viewModel.enterSelectionMode(item.book.id)
                                    },
                                    onLongClickLabel = stringResource(R.string.batch_selected_count, selectedIds.size),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // IMP-07：长按书卡 → 删除确认对话框（列表/网格统一）。
    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.library_delete)) },
            text = { Text(stringResource(R.string.library_delete_confirm, item.book.title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBook(item.book)
                    pendingDelete = null
                }) { Text(stringResource(R.string.library_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    // LIB-05/06：书架创建 / 重命名 / 删除 / 批量删除 对话框。
    ShelfDialogRenderer(
        dialog = pendingShelfAction,
        onDismiss = { pendingShelfAction = null },
        onCreate = { name -> viewModel.createCollection(name); pendingShelfAction = null },
        onRename = { id, name -> viewModel.renameCollection(id, name); pendingShelfAction = null },
        onDeleteShelf = { c -> viewModel.deleteCollection(c); pendingShelfAction = null },
        onBatchDelete = { viewModel.deleteSelected(); pendingShelfAction = null },
    )

    // LIB-06：批量加入书架选择 sheet。
    if (showCollectionPicker) {
        CollectionPickerSheet(
            collections = collections,
            initiallySelected = emptySet(),
            onConfirm = { selected ->
                // 批量加入：对每个选中的书架执行 addSelectedToCollection
                val first = collections.firstOrNull { it.id in selected }
                if (first != null) {
                    val name = if (first.kind == CollectionKind.SYSTEM_FAVORITE) favoriteName else first.name
                    viewModel.addSelectedToCollection(first.id, name)
                }
                showCollectionPicker = false
            },
            onQuickCreate = { viewModel.createCollection(it) },
            onDismiss = { showCollectionPicker = false },
        )
    }
}

/** 排序菜单项，当前选中项加「✓」。 */
@Composable
private fun SortMenuItem(
    label: String,
    current: LibrarySort,
    value: LibrarySort,
    onSelect: (LibrarySort) -> Unit,
) {
    val isSelected = current == value
    // SET-03：选中态不只靠「✓」视觉前缀，补 stateDescription 让 TalkBack 读「已选中」。
    val selectedDesc = stringResource(R.string.common_selected)
    DropdownMenuItem(
        text = { Text(if (isSelected) "✓ $label" else label) },
        onClick = { onSelect(value) },
        modifier = Modifier.semantics {
            if (isSelected) stateDescription = selectedDesc
        },
    )
}

/** 书库列表（默认，LIB-01）：横向卡——封面缩略 + 书名 + 作者 + 进度条 + 最近阅读时间。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryListRow(
    item: LibraryItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onLongClickLabel: String,
    selected: Boolean = false,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = onLongClickLabel,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(
            coverPath = item.book.coverPath,
            title = item.book.title,
            modifier = Modifier.size(width = 48.dp, height = 66.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.book.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.book.authors.takeIf { it.isNotEmpty() }?.joinToString("，")
                    ?: item.book.format,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val p = item.progression
                if (p != null) {
                    LinearProgressIndicator(
                        progress = { p.toFloat() },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${(p * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        stringResource(R.string.library_unread),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item.book.lastOpenedAt?.let {
                Text(
                    relativeTime(it, System.currentTimeMillis()).resolve(context),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** 书库网格卡（LIB-01）：封面大图 + 书名 + 作者 + 进度。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryGridCard(
    item: LibraryItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onLongClickLabel: String,
    selected: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = MaterialTheme.shapes.medium,
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = onLongClickLabel,
            ),
    ) {
        BookCover(
            coverPath = item.book.coverPath,
            title = item.book.title,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.66f),
        )
        Text(
            item.book.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        item.book.authors.takeIf { it.isNotEmpty() }?.joinToString("，")?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            if (item.progression != null) "${(item.progression!! * 100).toInt()}%" else stringResource(R.string.library_unread),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ===== LIB-05/06：书架对话框 + 书架列表视图 + 批量操作栏 =====

/** 书架相关对话框状态（创建 / 重命名 / 删书架 / 批量删除）。 */
private sealed interface ShelfDialog {
    data object CreateShelf : ShelfDialog
    data class RenameShelf(val collection: Collection) : ShelfDialog
    data class DeleteShelf(val collection: Collection) : ShelfDialog
    data class BatchDelete(val count: Int) : ShelfDialog
}

/** 渲染书架对话框（按 [ShelfDialog] 具体类型）。 */
@Composable
private fun ShelfDialogRenderer(
    dialog: ShelfDialog?,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDeleteShelf: (Collection) -> Unit,
    onBatchDelete: () -> Unit,
) {
    when (dialog) {
        null -> Unit
        is ShelfDialog.CreateShelf -> ShelfNameDialog(
            title = stringResource(R.string.shelf_new),
            hint = stringResource(R.string.shelf_new_hint),
            confirmLabel = stringResource(R.string.shelf_new),
            initialName = "",
            onConfirm = { onCreate(it) },
            onDismiss = onDismiss,
        )
        is ShelfDialog.RenameShelf -> ShelfNameDialog(
            title = stringResource(R.string.shelf_rename),
            hint = stringResource(R.string.shelf_rename_hint),
            confirmLabel = stringResource(R.string.shelf_rename),
            initialName = dialog.collection.name,
            onConfirm = { onRename(dialog.collection.id, it) },
            onDismiss = onDismiss,
        )
        is ShelfDialog.DeleteShelf -> {
            val name = if (dialog.collection.kind == CollectionKind.SYSTEM_FAVORITE) {
                stringResource(R.string.shelf_system_favorite)
            } else {
                dialog.collection.name
            }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.shelf_delete)) },
                text = { Text(stringResource(R.string.shelf_delete_confirm, name)) },
                confirmButton = {
                    TextButton(onClick = { onDeleteShelf(dialog.collection) }) {
                        Text(stringResource(R.string.shelf_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                },
            )
        }
        is ShelfDialog.BatchDelete -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.batch_delete)) },
            text = { Text(stringResource(R.string.batch_delete_confirm, dialog.count)) },
            confirmButton = {
                TextButton(onClick = onBatchDelete) { Text(stringResource(R.string.batch_delete)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

/** 书架命名对话框（新建 / 重命名共用，输入框 + 确认）。 */
@Composable
private fun ShelfNameDialog(
    title: String,
    hint: String,
    confirmLabel: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(hint) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim()) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/** 批量选择模式上下文操作栏（LIB-06）：已选数 + 全选 + 加入书架 + 删除 + 取消。 */
@Composable
private fun BatchActionBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onAddToShelf: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.batch_selected_count, selectedCount),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onSelectAll) { Text(stringResource(R.string.batch_select_all)) }
        TextButton(onClick = onAddToShelf) { Text(stringResource(R.string.batch_add_to_shelf)) }
        TextButton(onClick = onDelete) { Text(stringResource(R.string.batch_delete)) }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
    }
}

/**
 * 书架列表视图（LIB-05 书架 Tab 首页）：每行书架名 + 书数 + 进入；顶部新建书架。
 * 长按书架行弹重命名/删除（系统书架仅查看）。
 */
@Composable
private fun ShelfListView(
    collections: List<Collection>,
    onOpenCollection: (String) -> Unit,
    onNewShelf: () -> Unit,
    onRenameShelf: (Collection) -> Unit,
    onDeleteShelf: (Collection) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TextButton(
            onClick = onNewShelf,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.shelf_new))
        }
        if (collections.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.shelf_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(collections, key = { it.id }) { c ->
                    ShelfRow(c, onOpenCollection, onRenameShelf, onDeleteShelf)
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfRow(
    collection: Collection,
    onOpen: (String) -> Unit,
    onRename: (Collection) -> Unit,
    onDelete: (Collection) -> Unit,
) {
    val name = if (collection.kind == CollectionKind.SYSTEM_FAVORITE) {
        stringResource(R.string.shelf_system_favorite)
    } else {
        collection.name
    }
    val countText = if (collection.bookCount == 0) {
        stringResource(R.string.shelf_book_count_zero)
    } else {
        stringResource(R.string.shelf_book_count, collection.bookCount)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onOpen(collection.id) },
                onLongClick = {
                    // 系统书架「收藏」不可改名/删除，长按无操作
                    if (collection.kind != CollectionKind.SYSTEM_FAVORITE) onRename(collection)
                },
                onLongClickLabel = stringResource(R.string.shelf_rename),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (collection.kind == CollectionKind.SYSTEM_FAVORITE) {
            Icon(Icons.Filled.Star, contentDescription = null)
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(
                countText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (collection.kind != CollectionKind.SYSTEM_FAVORITE) {
            TextButton(onClick = { onRename(collection) }) {
                Text(stringResource(R.string.shelf_rename))
            }
            TextButton(onClick = { onDelete(collection) }) {
                Text(stringResource(R.string.shelf_delete))
            }
        }
    }
}
