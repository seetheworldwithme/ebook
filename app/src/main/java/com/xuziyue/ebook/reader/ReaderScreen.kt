package com.xuziyue.ebook.reader

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.AndroidFragment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuziyue.ebook.model.ReaderTextAlign
import com.xuziyue.ebook.model.ReaderTheme
import com.xuziyue.ebook.model.ReaderTypography

/**
 * Reader 主界面（Compose）。
 *
 * - [AndroidFragment] 托管 [ReaderFragment]（Compose↔Readium 桥接的核心）。
 * - VM 绑 Activity scope（与 ReaderFragment 的 activityViewModels 共享同一实例）。
 * - 顶栏：返回 + 进度。
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
    val decorations by viewModel.decorations.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val typography by viewModel.typography.collectAsStateWithLifecycle()

    var showTypography by remember { mutableStateOf(false) }

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
            onOpenTypography = { showTypography = true },
            onClearHighlights = { viewModel.clearHighlights() },
            highlightCount = decorations.size,
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
    onOpenTypography: () -> Unit,
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
            IconButton(onClick = onOpenTypography) {
                Icon(Icons.Default.Tune, contentDescription = "排版")
            }
            // 能力矩阵 gating（红线 #2）：canHighlight=false 时隐藏高亮计数与清除（PDF V1 生效）。
            if (canHighlight) {
                Text("高亮 $highlightCount", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onClearHighlights) { Text("清") }
            }
        }
    }
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
