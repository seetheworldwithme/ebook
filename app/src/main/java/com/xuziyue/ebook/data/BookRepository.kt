package com.xuziyue.ebook.data

import com.xuziyue.ebook.data.db.BookDao
import com.xuziyue.ebook.data.db.toDomain
import com.xuziyue.ebook.data.db.toEntity
import com.xuziyue.ebook.model.Book
import com.xuziyue.ebook.model.LibraryItem
import com.xuziyue.ebook.model.ReadingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 书籍仓库（封装 [BookDao]，design.md §6.5）。
 *
 * 对外暴露 domain [Book] / [LibraryItem]，DAO/Entity 细节内聚在 data 层。
 */
class BookRepository(private val dao: BookDao) {

    /** 书库列表（按最近阅读 / 导入时间排序，未读排末尾）。 */
    fun observeBooks(): Flow<List<Book>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    /**
     * 书库列表（带进度 + 搜索，LIB-01 / LIB-03）。
     *
     * @param query 空串 = 全量；否则按书名 / 作者子串过滤（DAO LIKE）。
     * 排序与视图模式在 [com.xuziyue.ebook.library.LibraryViewModel] 应用层处理。
     */
    fun observeLibraryItems(query: String): Flow<List<LibraryItem>> =
        dao.observeLibraryItems(query).map { entities -> entities.map { it.toDomain() } }

    suspend fun getById(id: String): Book? = dao.getById(id)?.toDomain()

    /** 导入去重：按 contentHash 查已有书（design.md §6.5 导入去重）。 */
    suspend fun getByContentHash(hash: String): Book? = dao.getByContentHash(hash)?.toDomain()

    suspend fun insert(book: Book) = dao.insert(book.toEntity())

    /** 打开时更新最近阅读时间 + 状态为 READING（READ-01/READ-08）。 */
    suspend fun markOpened(bookId: String) =
        dao.touchOpened(bookId, System.currentTimeMillis(), ReadingStatus.READING)

    suspend fun updateStatus(bookId: String, status: ReadingStatus) =
        dao.updateStatus(bookId, status)
}
