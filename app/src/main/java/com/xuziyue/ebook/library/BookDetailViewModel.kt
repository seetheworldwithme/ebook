package com.xuziyue.ebook.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xuziyue.ebook.data.BookRepository
import com.xuziyue.ebook.data.db.AnnotationDao
import com.xuziyue.ebook.data.db.BookmarkDao
import com.xuziyue.ebook.data.db.ReadingProgressDao
import com.xuziyue.ebook.model.Book
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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
)

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
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val bookId: String = checkNotNull(savedStateHandle["bookId"])

    val uiState: StateFlow<BookDetailUiState> =
        combine(
            bookRepository.observeBookById(bookId),
            progressDao.observe(bookId),
            bookmarkDao.observe(bookId).map { it.size },
            annotationDao.observe(bookId).map { it.size },
        ) { book, progress, bookmarkN, annoN ->
            BookDetailUiState(
                loading = false,
                book = book,
                progression = progress?.progression,
                progressUpdatedAt = progress?.updatedAt,
                bookmarkCount = bookmarkN,
                annotationCount = annoN,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookDetailUiState())
}
