package com.xuziyue.ebook

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.xuziyue.ebook.data.BookRepository
import com.xuziyue.ebook.data.ImportBookUseCase
import com.xuziyue.ebook.data.bookIdOrNull
import com.xuziyue.ebook.model.Book
import com.xuziyue.ebook.reader.ReaderScreen
import com.xuziyue.ebook.ui.theme.EbookReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * 单 Activity 入口。
 *
 * Navigation Compose：
 * - `library`：书库列表 + 导入入口（刀1 最简：书名列表 + 点击打开；完整网格/搜索/排序留刀2）。
 * - `reader/{bookId}`：阅读界面。bookId 作 route 参数，进程重建后 Navigation 自动恢复（design.md §6.5：UI 只传 bookId）。
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var importBookUseCase: ImportBookUseCase
    @Inject lateinit var bookRepository: BookRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EbookReaderTheme {
                AppRoot(importBookUseCase, bookRepository)
            }
        }
    }
}

@Composable
private fun AppRoot(
    importBookUseCase: ImportBookUseCase,
    bookRepository: BookRepository,
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    NavHost(navController = navController, startDestination = "library") {
        composable("library") {
            LibraryScreen(
                bookRepository = bookRepository,
                onImportUri = { uri ->
                    scope.launch {
                        importBookUseCase.importUri(uri).bookIdOrNull()
                            ?.let { navController.navigate("reader/$it") }
                    }
                },
                onImportAsset = {
                    scope.launch {
                        importBookUseCase.importAsset(ALICE_ASSET).bookIdOrNull()
                            ?.let { navController.navigate("reader/$it") }
                    }
                },
                onOpenBook = { bookId -> navController.navigate("reader/$bookId") },
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

@Composable
private fun LibraryScreen(
    bookRepository: BookRepository,
    onImportUri: (Uri) -> Unit,
    onImportAsset: () -> Unit,
    onOpenBook: (String) -> Unit,
) {
    // SAF 文件选择器（红线 #3：不申请 MANAGE_EXTERNAL_STORAGE，只用 ACTION_OPEN_DOCUMENT）。
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onImportUri) }

    val books by bookRepository.observeBooks().collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 顶栏：标题 + 导入
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("书库", style = MaterialTheme.typography.headlineSmall)
                OutlinedButton(onClick = {
                    launcher.launch(arrayOf("application/epub+zip", "text/plain", "*/*"))
                }) { Text("导入") }
            }
            OutlinedButton(
                onClick = onImportAsset,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            ) { Text("读内置样本 Alice（EPUB2）") }
            HorizontalDivider()

            if (books.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "还没有书，导入一本开始吧",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn {
                    items(books, key = { it.id }) { book ->
                        BookRow(book = book, onClick = { onOpenBook(book.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun BookRow(book: Book, onClick: () -> Unit) {
    val subtitle = book.authors.takeIf { it.isNotEmpty() }?.joinToString(", ")
        ?: book.format
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(book.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** assets 中的 Alice 样本路径。 */
private const val ALICE_ASSET = "samples/alice-in-wonderland.epub"
