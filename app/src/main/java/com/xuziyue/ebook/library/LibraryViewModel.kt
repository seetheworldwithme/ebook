package com.xuziyue.ebook.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xuziyue.ebook.R
import com.xuziyue.ebook.data.BookRepository
import com.xuziyue.ebook.data.CollectionRepository
import com.xuziyue.ebook.data.ImportBookUseCase
import com.xuziyue.ebook.model.Book
import com.xuziyue.ebook.model.Collection
import com.xuziyue.ebook.model.LibraryItem
import com.xuziyue.ebook.model.SYSTEM_FAVORITE_ID
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
 * 书库筛选（LIB-02 三入口 + LIB-05 书架入口）：[key] 透传到 DAO 的 `CASE :filter`。
 *
 * - [RECENT]：打开过即算（`lastOpenedAt` 非空）。
 * - [ALL]：全量（默认；新书导入后必可见）。
 * - [FINISHED]：已读完（`status = FINISHED`）。
 * - [SHELVES]：书架入口（LIB-05），不参与 DAO 的 CASE 筛选——进入书架 Tab 后先看书架列表，
 *   再点书架看内部书籍（走独立查询 [BookDao.observeLibraryItemsInCollection]）。
 */
enum class LibraryFilter(val key: String) { RECENT("RECENT"), ALL("ALL"), FINISHED("FINISHED"), SHELVES("SHELVES") }

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
    private val collectionRepository: CollectionRepository,
    private val importBookUseCase: ImportBookUseCase,
) : ViewModel() {

    val query = MutableStateFlow("")
    val filter = MutableStateFlow(LibraryFilter.ALL)
    val sort = MutableStateFlow(LibrarySort.LAST_OPENED)
    val viewMode = MutableStateFlow(LibraryViewMode.LIST)

    // ===== 书架（LIB-05）=====

    /** 当前在浏览的书架 id（null = 书架列表页；非 null = 该书架内书籍）。 */
    val selectedCollectionId = MutableStateFlow<String?>(null)

    /** 全部书架（含书籍数，系统「收藏」排最前）。书架 Tab 列表用。 */
    val collections: StateFlow<List<Collection>> =
        collectionRepository.observeCollectionsWithCounts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // query/filter 变化触发 DAO 重查（响应式回推，满足 LIB-02「状态变化后列表实时刷新」）；sort 仅内存排序。
    // 书架内浏览（selectedCollectionId 非空）走独立查询，不走 CASE 筛选。
    val items: StateFlow<List<LibraryItem>> =
        combine(query, filter, selectedCollectionId) { q, f, cid -> Triple(q.trim(), f, cid) }
            .flatMapLatest { (q, f, cid) ->
                if (f == LibraryFilter.SHELVES && cid != null) {
                    collectionRepository.observeBooksInCollection(cid, q)
                } else {
                    repository.observeLibraryItems(q, f.key)
                }
            }
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

    // ===== 书架管理（LIB-05）=====

    /** 书架 CRUD / 批量操作结果事件（UI collect 后 Toast 反馈）。 */
    private val _shelfEvents = Channel<ShelfOutcome>(Channel.BUFFERED)
    val shelfEvents = _shelfEvents.receiveAsFlow()

    fun openCollection(id: String?) { selectedCollectionId.value = id }

    fun createCollection(name: String) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) {
                _shelfEvents.send(ShelfOutcome.Failed(UserMessage.Res(R.string.shelf_name_empty)))
                return@launch
            }
            try {
                collectionRepository.createCollection(trimmed)
                _shelfEvents.send(ShelfOutcome.Created(UserMessage.Res(R.string.shelf_created, listOf(trimmed))))
            } catch (e: Exception) {
                _shelfEvents.send(ShelfOutcome.Failed(UserMessage.Res(R.string.batch_failed, listOf(e.message ?: ""))))
            }
        }
    }

    fun renameCollection(id: String, newName: String) {
        viewModelScope.launch {
            try {
                collectionRepository.renameCollection(id, newName)
                _shelfEvents.send(ShelfOutcome.Renamed(UserMessage.Res(R.string.shelf_renamed, listOf(newName))))
            } catch (e: IllegalStateException) {
                _shelfEvents.send(ShelfOutcome.Failed(UserMessage.Res(R.string.shelf_system_no_delete)))
            } catch (e: Exception) {
                _shelfEvents.send(ShelfOutcome.Failed(UserMessage.Res(R.string.batch_failed, listOf(e.message ?: ""))))
            }
        }
    }

    fun deleteCollection(collection: Collection) {
        viewModelScope.launch {
            try {
                collectionRepository.deleteCollection(collection.id)
                if (selectedCollectionId.value == collection.id) selectedCollectionId.value = null
                _shelfEvents.send(ShelfOutcome.Deleted(UserMessage.Res(R.string.shelf_deleted, listOf(collection.name))))
            } catch (e: IllegalStateException) {
                _shelfEvents.send(ShelfOutcome.Failed(UserMessage.Res(R.string.shelf_system_no_delete)))
            } catch (e: Exception) {
                _shelfEvents.send(ShelfOutcome.Failed(UserMessage.Res(R.string.batch_failed, listOf(e.message ?: ""))))
            }
        }
    }

    // ===== 批量选择模式（LIB-06）=====

    /** 是否处于批量选择模式（true 时书卡点击切选中而非开书）。 */
    val selectionMode = MutableStateFlow(false)

    /** 当前选中的书籍 id 集合。 */
    val selectedIds = MutableStateFlow<Set<String>>(emptySet())

    fun enterSelectionMode(seedBookId: String) {
        selectionMode.value = true
        selectedIds.value = setOf(seedBookId)
    }

    fun toggleSelection(bookId: String) {
        selectedIds.value = selectedIds.value.toMutableSet().apply {
            if (!add(bookId)) remove(bookId)
        }
        // 全部取消时自动退出选择模式
        if (selectedIds.value.isEmpty()) selectionMode.value = false
    }

    fun selectAllVisible() {
        val ids = items.value.map { it.book.id }.toSet()
        selectedIds.value = selectedIds.value + ids
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
        selectionMode.value = false
    }

    /** 批量加入书架（LIB-06）。重复加入幂等（DAO IGNORE）。 */
    fun addSelectedToCollection(collectionId: String, collectionName: String) {
        val ids = selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                collectionRepository.addBooksToCollection(collectionId, ids)
                _shelfEvents.send(
                    ShelfOutcome.BatchAdded(UserMessage.Res(R.string.batch_added_success, listOf(ids.size, collectionName))),
                )
                clearSelection()
            } catch (e: Exception) {
                _shelfEvents.send(ShelfOutcome.Failed(UserMessage.Res(R.string.batch_failed, listOf(e.message ?: ""))))
            }
        }
    }

    /** 批量删除选中书籍（LIB-06，复用 [BookRepository.deleteBook]，CASCADE 清子表 + 书架关系）。 */
    fun deleteSelected() {
        val ids = selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            var ok = 0
            // 先取全部书籍用于 deleteBook（需要 Book 对象拿 filePath/coverPath）
            val toDelete = items.value.filter { it.book.id in ids }.map { it.book }
            toDelete.forEach { book ->
                runCatching { repository.deleteBook(book) }.onSuccess { ok++ }
            }
            _deleteEvents.send(
                DeleteOutcome.Deleted(UserMessage.Res(R.string.batch_delete_success, listOf(ok))),
            )
            clearSelection()
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
 * 书架操作结果事件（LIB-05/06，[LibraryViewModel.shelfEvents]）。各态均带 [UserMessage]，
 * UI 统一 `outcome.message.resolve(context)` 出 Toast（同 [DeleteOutcome] 范式）。
 */
sealed interface ShelfOutcome {
    val message: UserMessage

    data class Created(override val message: UserMessage) : ShelfOutcome
    data class Renamed(override val message: UserMessage) : ShelfOutcome
    data class Deleted(override val message: UserMessage) : ShelfOutcome
    /** 批量加入书架成功（文案含数量 + 书架名）。 */
    data class BatchAdded(override val message: UserMessage) : ShelfOutcome
    data class Failed(override val message: UserMessage) : ShelfOutcome
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
