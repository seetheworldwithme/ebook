package com.xuziyue.ebook.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.AndroidFragment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.readium.r2.navigator.preferences.Theme as ReadiumTheme
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * Reader 主界面（Compose）。
 *
 * - [AndroidFragment] 托管 [ReaderFragment]（Compose↔Readium 桥接的核心）。
 * - VM 绑 Activity scope（与 ReaderFragment 的 activityViewModels 共享同一实例）。
 * - 顶/底控制条 overlay：返回 + 进度、字号±、日/夜/护眼主题、高亮计数/清。
 *   高亮本身由「长按选中正文文字 → 系统 ActionMode「高亮」」触发（见 ReaderFragment），不在底栏。
 */
@OptIn(ExperimentalReadiumApi::class)
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

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val progressText by viewModel.progressText.collectAsStateWithLifecycle()
    val decorations by viewModel.decorations.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        // 核心：Compose 托管 ReaderFragment（内部 childFragmentManager 托管 EpubNavigatorFragment）
        AndroidFragment<ReaderFragment>(modifier = Modifier.fillMaxSize())

        // 顶部控制条
        ReaderTopBar(
            progress = progressText,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // 底部控制条
        ReaderBottomBar(
            onFontDecrease = { viewModel.changeFontSize(-0.1) },
            onFontIncrease = { viewModel.changeFontSize(0.1) },
            onLight = { viewModel.changeTheme(ReadiumTheme.LIGHT) },
            onDark = { viewModel.changeTheme(ReadiumTheme.DARK) },
            onSepia = { viewModel.changeTheme(ReadiumTheme.SEPIA) },
            onClearHighlights = { viewModel.clearHighlights() },
            highlightCount = decorations.size,
            canHighlight = capabilities.canHighlight,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

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
private fun ReaderTopBar(progress: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
            Spacer(Modifier.width(8.dp))
            Text("进度 $progress", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ReaderBottomBar(
    onFontDecrease: () -> Unit,
    onFontIncrease: () -> Unit,
    onLight: () -> Unit,
    onDark: () -> Unit,
    onSepia: () -> Unit,
    onClearHighlights: () -> Unit,
    highlightCount: Int,
    canHighlight: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
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
            OutlinedButton(onClick = onLight) { Text("日") }
            OutlinedButton(onClick = onSepia) { Text("黄") }
            OutlinedButton(onClick = onDark) { Text("夜") }
            // 能力矩阵 gating（红线 #2）：canHighlight=false 时隐藏高亮计数与清除（PDF V1 生效）。
            if (canHighlight) {
                Text("高亮 $highlightCount", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onClearHighlights) { Text("清") }
            }
        }
    }
}
