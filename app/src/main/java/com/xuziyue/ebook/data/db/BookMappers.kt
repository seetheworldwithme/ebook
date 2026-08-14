package com.xuziyue.ebook.data.db

import com.xuziyue.ebook.model.Book
import com.xuziyue.ebook.model.Collection
import com.xuziyue.ebook.model.CollectionKind
import com.xuziyue.ebook.model.LibraryItem

/**
 * Entity ↔ domain 映射（持久化关注点，放 :app/data/db，不污染 :core:model）。
 *
 * 字段一一对应、无歧义；未来加字段时两侧同步。
 */
fun BookEntity.toDomain(): Book = Book(
    id = id,
    contentHash = contentHash,
    title = title,
    authors = authors,
    description = description,
    language = language,
    format = format,
    mediaType = mediaType,
    filePath = filePath,
    fileSize = fileSize,
    coverPath = coverPath,
    importedAt = importedAt,
    lastOpenedAt = lastOpenedAt,
    status = status,
)

fun Book.toEntity(): BookEntity = BookEntity(
    id = id,
    contentHash = contentHash,
    title = title,
    authors = authors,
    description = description,
    language = language,
    format = format,
    mediaType = mediaType,
    filePath = filePath,
    fileSize = fileSize,
    coverPath = coverPath,
    importedAt = importedAt,
    lastOpenedAt = lastOpenedAt,
    status = status,
)

/** [LibraryItemEntity] → domain [LibraryItem]（书库列表查询结果，LIB-01）。 */
fun LibraryItemEntity.toDomain(): LibraryItem = LibraryItem(
    book = book.toDomain(),
    progression = progression,
)

/** [CollectionWithCountEntity] → domain [Collection]（书架 Tab 列表查询结果，LIB-05）。 */
fun CollectionWithCountEntity.toDomain(): Collection = Collection(
    id = id,
    name = name,
    sortOrder = sortOrder,
    kind = runCatching { CollectionKind.valueOf(kind) }.getOrDefault(CollectionKind.CUSTOM),
    bookCount = bookCount,
)
