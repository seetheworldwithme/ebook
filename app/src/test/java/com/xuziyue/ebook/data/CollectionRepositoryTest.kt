package com.xuziyue.ebook.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xuziyue.ebook.data.db.BookDatabase
import com.xuziyue.ebook.data.db.BookEntity
import com.xuziyue.ebook.data.db.CollectionBookEntity
import com.xuziyue.ebook.data.db.CollectionEntity
import com.xuziyue.ebook.model.CollectionKind
import com.xuziyue.ebook.model.ReadingStatus
import com.xuziyue.ebook.model.SYSTEM_FAVORITE_ID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * CollectionRepository 单测（Robolectric in-memory Room，LIB-05/06）。
 * 验证 toggleFavorite（系统书架）/ 系统书架拒删拒改名 / toggleBookInCollection / 批量加入 / clock+idGenerator 注入。
 */
@RunWith(RobolectricTestRunner::class)
class CollectionRepositoryTest {

    private lateinit var db: BookDatabase
    private lateinit var repo: CollectionRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BookDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = CollectionRepository(
            collectionDao = db.collectionDao(),
            collectionBookDao = db.collectionBookDao(),
            bookDao = db.bookDao(),
            clock = { 12345L },
            idGenerator = { "gen-id" },
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `createCollection 用注入的 idGenerator 和 clock`() = runTest {
        val id = repo.createCollection("我的书架")
        assertEquals("gen-id", id)
        val entity = db.collectionDao().getById(id)
        assertEquals("我的书架", entity?.name)
        assertEquals(12345L, entity?.sortOrder)
        assertEquals(CollectionKind.CUSTOM, entity?.kind)
    }

    @Test
    fun `toggleBookInCollection 加入后返回 true，再次调用移除返回 false`() = runTest {
        db.bookDao().insert(book("b1"))
        db.collectionDao().insert(collection("c1"))
        assertTrue(repo.toggleBookInCollection("c1", "b1"))
        assertFalse(repo.toggleBookInCollection("c1", "b1")) // 再调用移除
        assertTrue(repo.observeCollectionIdsForBook("b1").first().isEmpty())
    }

    @Test
    fun `toggleFavorite 操作系统书架`() = runTest {
        db.bookDao().insert(book("b1"))
        db.collectionDao().insert(systemFavorite())
        assertTrue(repo.toggleFavorite("b1"))
        assertTrue(repo.isFavorite("b1"))
        assertFalse(repo.toggleFavorite("b1")) // 取消收藏
        assertFalse(repo.isFavorite("b1"))
    }

    @Test
    fun `系统书架拒绝改名`() = runTest {
        db.collectionDao().insert(systemFavorite())
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { repo.renameCollection(SYSTEM_FAVORITE_ID, "新名") }
        }
    }

    @Test
    fun `系统书架拒绝删除`() = runTest {
        db.collectionDao().insert(systemFavorite())
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { repo.deleteCollection(SYSTEM_FAVORITE_ID) }
        }
    }

    @Test
    fun `addBooksToCollection 批量加入幂等`() = runTest {
        db.bookDao().insert(book("b1"))
        db.bookDao().insert(book("b2"))
        db.collectionDao().insert(collection("c1"))
        repo.addBooksToCollection("c1", listOf("b1", "b2", "b1")) // b1 重复
        val ids = repo.observeCollectionIdsForBook("b1").first()
        assertTrue("c1" in ids)
        val ids2 = repo.observeCollectionIdsForBook("b2").first()
        assertTrue("c1" in ids2)
    }

    @Test
    fun `deleteCollection 自定义书架可删，关系清`() = runTest {
        db.bookDao().insert(book("b1"))
        db.collectionDao().insert(collection("c1"))
        db.collectionBookDao().add(CollectionBookEntity("c1", "b1", 0L))
        repo.deleteCollection("c1")
        assertEquals(null, db.collectionDao().getById("c1"))
        // 书还在
        assertEquals("书b1", db.bookDao().getById("b1")?.title)
    }

    private fun collection(id: String, name: String = "书架") =
        CollectionEntity(id = id, name = name, sortOrder = 0L, createdAt = 0L, kind = CollectionKind.CUSTOM)

    private fun systemFavorite() = CollectionEntity(
        id = SYSTEM_FAVORITE_ID,
        name = "收藏",
        sortOrder = Long.MIN_VALUE,
        createdAt = 0L,
        kind = CollectionKind.SYSTEM_FAVORITE,
    )

    private fun book(id: String, title: String = "书$id") = BookEntity(
        id = id,
        contentHash = "hash-$id",
        title = title,
        authors = emptyList(),
        description = null,
        language = null,
        format = "EPUB",
        mediaType = "application/epub+zip",
        filePath = "/$id.epub",
        fileSize = 0L,
        coverPath = null,
        importedAt = 0L,
        lastOpenedAt = null,
        status = ReadingStatus.UNREAD,
    )
}
