package com.xuziyue.ebook.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xuziyue.ebook.data.BookRepository
import com.xuziyue.ebook.data.CollectionRepository
import com.xuziyue.ebook.data.ReadingSessionRepository
import com.xuziyue.ebook.data.db.AnnotationDao
import com.xuziyue.ebook.data.db.BookmarkDao
import com.xuziyue.ebook.data.db.ReadingProgressDao
import com.xuziyue.ebook.model.Book
import com.xuziyue.ebook.model.SYSTEM_FAVORITE_ID
import com.xuziyue.ebook.ui.dayBoundsFor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 书籍详情页 UiState（LIB-04）：聚合元数据 / 进度 / 书签数 / 笔记数。
 *
 * - [loading]：combine 首帧前的初始占位。
 * - [book] == null 且 !loading：书不存在（已删 / id 非法），UI 提示并返回。
 * - [progression] 0.0..1.0，null = 未读（展示 ×100）。
 */
data class BookDetailUiState(
    val loading: Boolean = true,
    val book: Book? = null,
    val progression: Double? = null,
    val progressUpdatedAt: Long? = null,
    val bookmarkCount: Int = 0,
    val annotationCount: Int = 0,
    /** 本书累计阅读秒数（DATA-04）。 */
    val bookTotalSeconds: Long = 0,
    /** 本书今日阅读秒数（DATA-04）。 */
    val bookTodaySeconds: Long = 0,
    /** 本书所属书架 id 集合（LIB-05，详情页 chips 展示）。 */
    val collectionIds: List<String> = emptyList(),
) {
    /** 是否在收藏书架（LIB-05 快捷态）。 */
    val isFavorite: Boolean get() = SYSTEM_FAVORITE_ID in collectionIds
}

/**
 * 书籍详情页 ViewModel（LIB-04）。
 *
 * bookId 取自 [SavedStateHandle]（Navigation Compose 把 `detail/{bookId}` route 参数注入）。
 *
 * 跨表聚合（仿 [com.xuziyue.ebook.data.export.ExportBookDataUseCase] 直接注 DAO）：
 * book（[BookRepository.observeBookById]）+ 进度 + 书签数 + 笔记数，四路 [combine] 响应式回推。
 * 不碰 DB schema；计数用 `observe(bookId).map { it.size }`（批注已过滤 `deletedAt IS NULL`）。
 */
@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val progressDao: ReadingProgressDao,
    private val bookmarkDao: BookmarkDao,
    private val annotationDao: AnnotationDao,
    private val sessionRepository: ReadingSessionRepository,
    private val collectionRepository: CollectionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val bookId: String = checkNotNull(savedStateHandle["bookId"])

    // 5 路 combine（book + 进度 + 书签数 + 笔记数 + 阅读时长）再与 collectionIds 嵌套合并（避开 combine 5 参数上限）。
    private val core: kotlinx.coroutines.flow.Flow<BookDetailUiState> =
        combine(
            bookRepository.observeBookById(bookId),
            progressDao.observe(bookId),
            bookmarkDao.observe(bookId).map { it.size },
            annotationDao.observe(bookId).map { it.size },
            // DATA-04：以进度更新为触发器重查本书阅读时长。
            progressDao.observe(bookId).map {
                val bounds = dayBoundsFor(System.currentTimeMillis())
                val total = sessionRepository.bookTotalSeconds(bookId)
                val today = sessionRepository.bookTodaySeconds(bookId, bounds.startMs, bounds.endMs)
                total to today
            },
        ) { book, progress, bookmarkN, annoN, sessionPair ->
            BookDetailUiState(
                loading = false,
                book = book,
                progression = progress?.progression,
                progressUpdatedAt = progress?.updatedAt,
                bookmarkCount = bookmarkN,
                annotationCount = annoN,
                bookTotalSeconds = sessionPair.first,
                bookTodaySeconds = sessionPair.second,
            )
        }

    val uiState: StateFlow<BookDetailUiState> =
        combine(core, collectionRepository.observeCollectionIdsForBook(bookId)) { state, ids ->
            state.copy(collectionIds = ids)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookDetailUiState())

    /** 全部书架（详情页「加入书架」选择 sheet 用，LIB-05）。 */
    val collections: StateFlow<List<com.xuziyue.ebook.model.Collection>> =
        collectionRepository.observeCollectionsWithCounts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 切换收藏（LIB-05，系统书架快捷操作）。 */
    fun toggleFavorite() {
        viewModelScope.launch { collectionRepository.toggleFavorite(bookId) }
    }

    /** 切换某书架归属（LIB-05，详情页 chip 移除 / 加入）。 */
    fun toggleBookInCollection(collectionId: String) {
        viewModelScope.launch { collectionRepository.toggleBookInCollection(collectionId, bookId) }
    }

    /** 删除本书（IMP-07 单本删除，入口迁移到详情页）。 */
    fun deleteBook(book: Book) {
        viewModelScope.launch { bookRepository.deleteBook(book) }
    }
}
