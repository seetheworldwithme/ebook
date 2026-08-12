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
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import com.xuziyue.ebook.library.LibraryFilter
import com.xuziyue.ebook.library.LibrarySort
import com.xuziyue.ebook.library.LibraryViewMode
import com.xuziyue.ebook.library.LibraryViewModel
import com.xuziyue.ebook.model.LibraryItem
import com.xuziyue.ebook.library.BookDetailScreen
import com.xuziyue.ebook.reader.ReaderScreen
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
) {
    val viewModel: LibraryViewModel = hiltViewModel()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle(initialValue = emptyList())
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 静态导入结果文案在 Composable 作用域解析（lint 要求 stringResource 而非 context.getString，
    // 这样随系统语言变化自动重组；Failed 的动态消息走 UserMessage.resolve(context)）。
    val importSuccessText = stringResource(R.string.import_success)
    val importAlreadyExistsText = stringResource(R.string.import_already_exists)
    val importProgressText = stringResource(R.string.reader_importing)

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

            OutlinedButton(
                onClick = { viewModel.importAsset(ALICE_ASSET) },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            ) { Text(stringResource(R.string.library_sample_alice)) }

            // 三入口（LIB-02）：最近阅读 / 全部 / 已读完
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
            }

            HorizontalDivider()

            when {
                items.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // 空态文案按筛选维度区分（LIB-02：已读完/最近阅读空 ≠ 没书）
                        val emptyText = when {
                            query.isNotBlank() -> stringResource(R.string.library_empty_search, query)
                            filter == LibraryFilter.FINISHED -> stringResource(R.string.library_empty_finished)
                            filter == LibraryFilter.RECENT -> stringResource(R.string.library_empty_recent)
                            else -> stringResource(R.string.library_empty_all)
                        }
                        Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                viewMode == LibraryViewMode.LIST -> {
                    LazyColumn {
                        items(items, key = { it.book.id }) { item ->
                            LibraryListRow(item, onClick = { onOpenBook(item.book.id) })
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
                            LibraryGridCard(item, onClick = { onOpenBook(item.book.id) })
                        }
                    }
                }
            }
        }
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
    DropdownMenuItem(
        text = { Text(if (current == value) "✓ $label" else label) },
        onClick = { onSelect(value) },
    )
}

/** 书库列表（默认，LIB-01）：横向卡——封面缩略 + 书名 + 作者 + 进度条 + 最近阅读时间。 */
@Composable
private fun LibraryListRow(item: LibraryItem, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
@Composable
private fun LibraryGridCard(item: LibraryItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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

/** assets 中的 Alice 样本路径。 */
private const val ALICE_ASSET = "samples/alice-in-wonderland.epub"
