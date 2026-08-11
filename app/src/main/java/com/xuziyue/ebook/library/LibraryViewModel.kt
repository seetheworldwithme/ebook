package com.xuziyue.ebook.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xuziyue.ebook.data.BookRepository
import com.xuziyue.ebook.model.LibraryItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

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
 * 书库页 ViewModel（LIB-01 / LIB-02 / LIB-03）。
 *
 * - [query]：搜索关键词（书名 / 作者，DAO LIKE）。
 * - [filter]：三入口筛选（LIB-02）；[sort] / [viewMode]：排序与视图模式。
 *   三者均**本刀内存态**，未持久化；保位后续复用 DataStore 加 key。
 * - [items]：query + filter → DAO 过滤 → 内存排序（[sortItems]），5000 条内存排序 <5ms 无压力。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: BookRepository,
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

    fun setQuery(value: String) { query.value = value }
    fun setFilter(value: LibraryFilter) { filter.value = value }
    fun setSort(value: LibrarySort) { sort.value = value }
    fun toggleViewMode() {
        viewMode.value =
            if (viewMode.value == LibraryViewMode.LIST) LibraryViewMode.GRID else LibraryViewMode.LIST
    }
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
