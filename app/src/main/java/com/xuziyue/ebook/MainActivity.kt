package com.xuziyue.ebook

import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.xuziyue.ebook.data.BookFileImporter
import com.xuziyue.ebook.data.LocatorStore
import com.xuziyue.ebook.reader.ReaderScreen
import com.xuziyue.ebook.ui.theme.EbookReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * 单 Activity 入口。
 *
 * Navigation Compose：
 * - `welcome`：双入口（内置 Alice 样本 + SAF 文件选择器）。
 * - `reader/{contentHash}`：阅读界面。contentHash 作 route 参数，进程重建后 Navigation 自动恢复。
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var bookFileImporter: BookFileImporter
    @Inject lateinit var locatorStore: LocatorStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EbookReaderTheme {
                AppRoot(bookFileImporter, locatorStore)
            }
        }
    }
}

@Composable
private fun AppRoot(importer: BookFileImporter, locatorStore: LocatorStore) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    NavHost(navController = navController, startDestination = "welcome") {
        composable("welcome") {
            WelcomeScreen(
                onOpenAlice = {
                    scope.launch {
                        importer.copyAssetEpub(ALICE_ASSET)
                            .onSuccess { book ->
                                locatorStore.saveFilePath(book.contentHash, book.file.absolutePath)
                                navController.navigate("reader/${book.contentHash}")
                            }
                    }
                },
                onOpenUri = { uri ->
                    scope.launch {
                        importer.importFromUri(uri)
                            .onSuccess { book ->
                                locatorStore.saveFilePath(book.contentHash, book.file.absolutePath)
                                navController.navigate("reader/${book.contentHash}")
                            }
                    }
                },
            )
        }
        composable(
            route = "reader/{contentHash}",
            arguments = listOf(navArgument("contentHash") { type = NavType.StringType }),
        ) { backStackEntry ->
            val contentHash = backStackEntry.arguments?.getString("contentHash")
            if (contentHash == null) {
                navController.popBackStack()
                return@composable
            }
            ReaderScreen(contentHash = contentHash, onBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun WelcomeScreen(
    onOpenAlice: () -> Unit,
    onOpenUri: (Uri) -> Unit,
) {
    // SAF 文件选择器（红线 #3：不申请 MANAGE_EXTERNAL_STORAGE，只用 ACTION_OPEN_DOCUMENT）。
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onOpenUri) }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("电子书阅读器 · Phase 0", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(32.dp))
            Button(onClick = onOpenAlice) { Text("读内置样本 Alice（EPUB2）") }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { launcher.launch(arrayOf("application/epub+zip", "*/*")) }) {
                Text("选择 EPUB 文件")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { launcher.launch(arrayOf("text/plain")) }) {
                Text("选择 TXT 文件")
            }
        }
    }
}

/** assets 中的 Alice 样本路径（步骤 4 复制进 assets/samples/）。 */
private const val ALICE_ASSET = "samples/alice-in-wonderland.epub"
