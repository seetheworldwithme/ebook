package com.xuziyue.ebook.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xuziyue.ebook.R
import com.xuziyue.ebook.data.BookRepository
import com.xuziyue.ebook.data.ImportBookUseCase
import com.xuziyue.ebook.model.Book
import com.xuziyue.ebook.model.LibraryItem
import com.xuziyue.ebook.ui.UserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 书库排序（LIB-03）：最近阅读 / 导入时间 / 书名。 */
enum class LibrarySort { LAST_OPENED, IMPORTED, TITLE }

/** 书库视图模式（LIB-01）：列表（默认）/ 网格。 */
enum class LibraryViewMode { LIST, GRID }

/**
 * 书库三入口筛选（LIB-02）：[key] 透传到 DAO 的 `CASE :filter`。
 *
 * - [RECENT]：打开过即算（`lastOpenedAt` 非空）。
 * - [ALL]：全量（默认；新书导入后必可见）。
 * - [FINISHED]：已读完（`status = FINISHED`）。
 */
enum class LibraryFilter(val key: String) { RECENT("RECENT"), ALL("ALL"), FINISHED("FINISHED") }

/**
 * 书库页 ViewModel（LIB-01 / LIB-02 / LIB-03 + IMP-05 导入反馈）。
 *
 * - [query]：搜索关键词（书名 / 作者，DAO LIKE）。
 * - [filter]：三入口筛选（LIB-02）；[sort] / [viewMode]：排序与视图模式。
 *   三者均**本刀内存态**，未持久化；保位后续复用 DataStore 加 key。
 * - [items]：query + filter → DAO 过滤 → 内存排序（[sortItems]），5000 条内存排序 <5ms 无压力。
 * - [importing]：导入进行中标志（UI 显示 indeterminate 进度条，IMP-05）。
 * - [importEvents]：一次性导入结果事件（UI collect 后 Toast + 跳阅读器，IMP-05）。
 *   SAF 导入（IMP-01）和外部 Intent 导入（IMP-02 ACTION_VIEW/SEND）共用此通道。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: BookRepository,
    private val importBookUseCase: ImportBookUseCase,
) : ViewModel() {

    val query = MutableStateFlow("")
    val filter = MutableStateFlow(LibraryFilter.ALL)
    val sort = MutableStateFlow(LibrarySort.LAST_OPENED)
    val viewMode = MutableStateFlow(LibraryViewMode.LIST)

    // query/filter 变化触发 DAO 重查（响应式回推，满足 LIB-02「状态变化后列表实时刷新」）；sort 仅内存排序。
    val items: StateFlow<List<LibraryItem>> =
        combine(query, filter) { q, f -> q.trim() to f }
            .flatMapLatest { (q, f) -> repository.observeLibraryItems(q, f.key) }
            .combine(sort) { list, s -> sortItems(list, s) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ===== 导入（IMP-01 SAF + IMP-02 外部 Intent + IMP-05 进度/成功/失败反馈）=====

    /** 导入进行中标志（UI 显示 indeterminate 进度条）。 */
    val importing = MutableStateFlow(false)

    /** 一次性导入结果事件（UI collect 后 Toast 反馈 + 成功跳阅读器）。 */
    private val _importEvents = Channel<ImportBookUseCase.Outcome>(Channel.BUFFERED)
    val importEvents = _importEvents.receiveAsFlow()

    /**
     * 导入 SAF / 外部 Intent 的 Uri（IMP-01 + IMP-02 共用）。
     * 导入完成后 [importEvents] emit 结果（Imported / AlreadyExists / Failed）。
     */
    fun importUri(uri: Uri) {
        viewModelScope.launch {
            importing.value = true
            val outcome = importBookUseCase.importUri(uri)
            importing.value = false
            _importEvents.send(outcome)
        }
    }

    // ===== 删除（IMP-07 首刀：长按删书，DB + 文件副本一起删）=====

    /** 一次性删除结果事件（UI collect 后 Toast 反馈）。 */
    private val _deleteEvents = Channel<DeleteOutcome>(Channel.BUFFERED)
    val deleteEvents = _deleteEvents.receiveAsFlow()

    /**
     * 删除一本书（IMP-07）。完成后 [deleteEvents] emit 结果（[DeleteOutcome.Deleted] / [DeleteOutcome.Failed]）。
     * 文件清理失败已在 [BookRepository.deleteBook] 内吞掉，这里只捕获 DB 删除异常。
     */
    fun deleteBook(book: Book) {
        viewModelScope.launch {
            val outcome = try {
                repository.deleteBook(book)
                DeleteOutcome.Deleted(UserMessage.Res(R.string.library_delete_success, listOf(book.title)))
            } catch (e: Exception) {
                DeleteOutcome.Failed(UserMessage.Res(R.string.library_delete_failed, listOf(e.message ?: "")))
            }
            _deleteEvents.send(outcome)
        }
    }

    fun setQuery(value: String) { query.value = value }
    fun setFilter(value: LibraryFilter) { filter.value = value }
    fun setSort(value: LibrarySort) { sort.value = value }
    fun toggleViewMode() {
        viewMode.value =
            if (viewMode.value == LibraryViewMode.LIST) LibraryViewMode.GRID else LibraryViewMode.LIST
    }
}

/**
 * 删除结果事件（IMP-07，[LibraryViewModel.deleteEvents]）。两态均带 [UserMessage]，
 * UI 统一 `outcome.message.resolve(context)` 出 Toast（避免在 Composable 内直接 context.getString
 * 触发 lint `LocalContextGetResourceValueCall`，与 importEvents 的 Failed 分支同范式）。
 *
 * - [Deleted]：删除成功（文案含书名「已删除《{title}」」）。
 * - [Failed]：DB 删除抛异常（文件清理失败已在 [BookRepository.deleteBook] 吞掉，不会走到这里）。
 */
sealed interface DeleteOutcome {
    /** 统一消息出口，UI 直接 outcome.message.resolve(context) 出 Toast。 */
    val message: UserMessage

    data class Deleted(override val message: UserMessage) : DeleteOutcome
    data class Failed(override val message: UserMessage) : DeleteOutcome
}

/**
 * 书库排序纯函数（LIB-03，可单测）。
 *
 * - [LibrarySort.LAST_OPENED]：lastOpenedAt 降序，未读（null）排末尾。
 * - [LibrarySort.IMPORTED]：importedAt 降序。
 * - [LibrarySort.TITLE]：书名不区分大小写升序（中文 lowercase 无影响，按 Unicode 序）。
 */
fun sortItems(items: List<LibraryItem>, sort: LibrarySort): List<LibraryItem> = when (sort) {
    LibrarySort.LAST_OPENED -> items.sortedByDescending { it.book.lastOpenedAt ?: -1L }
    LibrarySort.IMPORTED -> items.sortedByDescending { it.book.importedAt }
    LibrarySort.TITLE -> items.sortedBy { it.book.title.lowercase() }
}
