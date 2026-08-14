package com.xuziyue.ebook.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuziyue.ebook.R
import com.xuziyue.ebook.model.Book
import com.xuziyue.ebook.ui.BookCover
import com.xuziyue.ebook.ui.formatDuration
import com.xuziyue.ebook.ui.relativeTime
import com.xuziyue.ebook.ui.resolve

/**
 * 书籍详情页（LIB-04）：元数据 / 进度 / 文件信息 / 书签·笔记数 / 继续阅读入口。
 *
 * 卡片点击进此；「继续阅读」→ reader（ReaderViewModel 自取 locator 恢复，详情页不传 locator）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    onBack: () -> Unit,
    onRead: () -> Unit,
    viewModel: BookDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.book?.title ?: stringResource(R.string.detail_title_default)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.detail_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> CenterText(padding, stringResource(R.string.detail_loading))
            state.book == null -> CenterText(padding, stringResource(R.string.detail_not_found))
            else -> DetailContent(state, padding, onRead)
        }
    }
}

@Composable
private fun CenterText(padding: PaddingValues, text: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DetailContent(state: BookDetailUiState, padding: PaddingValues, onRead: () -> Unit) {
    val book: Book = state.book!!
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // ① 元数据
        Row {
            BookCover(
                coverPath = book.coverPath,
                title = book.title,
                modifier = Modifier.size(width = 120.dp, height = 168.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                book.authors.takeIf { it.isNotEmpty() }?.joinToString("，")?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                book.language?.let {
                    Text(
                        stringResource(R.string.detail_language, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        book.description?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(16.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }

        SectionDivider()

        // ② 阅读进度
        SectionTitle(stringResource(R.string.detail_section_progress))
        Spacer(Modifier.height(8.dp))
        val p = state.progression
        if (p != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { p.toFloat() },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text("${(p * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
            }
            book.lastOpenedAt?.let {
                Text(
                    stringResource(
                        R.string.detail_last_opened,
                        relativeTime(it, System.currentTimeMillis()).resolve(context),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            Text(
                stringResource(R.string.detail_not_started),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionDivider()

        // ③ 文件信息（不展示路径 / contentHash——红线 #8：UI 不含完整路径；hash 对用户无意义）
        SectionTitle(stringResource(R.string.detail_section_file))
        Spacer(Modifier.height(8.dp))
        FileInfoRow(stringResource(R.string.detail_file_format_label), "${book.format}（${book.mediaType}）")
        FileInfoRow(stringResource(R.string.detail_file_size_label), formatFileSize(book.fileSize))
        FileInfoRow(stringResource(R.string.detail_file_imported_label), relativeTime(book.importedAt, System.currentTimeMillis()).resolve(context))

        SectionDivider()

        // ④ 书签 · 笔记数
        SectionTitle(stringResource(R.string.detail_section_annotations))
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.detail_counts, state.bookmarkCount, state.annotationCount),
            style = MaterialTheme.typography.bodyMedium,
        )

        SectionDivider()

        // ⑤ 阅读统计（DATA-04：本书总时长 + 今日时长）
        SectionTitle(stringResource(R.string.detail_section_stats))
        Spacer(Modifier.height(8.dp))
        StatsRow(
            label = stringResource(R.string.detail_stats_total),
            value = formatDuration(state.bookTotalSeconds).resolve(context),
        )
        StatsRow(
            label = stringResource(R.string.detail_stats_today),
            value = formatDuration(state.bookTodaySeconds).resolve(context),
        )

        Spacer(Modifier.height(24.dp))

        // ⑤ 继续阅读入口（有进度=继续，未读=开始）
        Button(onClick = onRead, modifier = Modifier.fillMaxWidth()) {
            Text(if (p != null) stringResource(R.string.detail_continue) else stringResource(R.string.detail_start))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    // SET-02：章节标题标 heading()，TalkBack 可按标题跳转（阅读进度 / 文件信息 / 书签·笔记）。
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun SectionDivider() {
    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun FileInfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

/** 阅读统计行（DATA-04）：label 左、值右，值用 bodyMedium 突出。 */
@Composable
private fun StatsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** 文件大小格式化（KB / MB）。 */
private fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    return if (kb < 1024.0) "%.1f KB".format(kb) else "%.1f MB".format(kb / 1024.0)
}
