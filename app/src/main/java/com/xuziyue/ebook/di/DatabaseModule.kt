package com.xuziyue.ebook.di

import android.content.Context
import androidx.room.Room
import com.xuziyue.ebook.data.BookFileImporter
import com.xuziyue.ebook.data.BookRepository
import com.xuziyue.ebook.data.ImportBookUseCase
import com.xuziyue.ebook.data.ReadingProgressRepository
import com.xuziyue.ebook.data.db.BookDao
import com.xuziyue.ebook.data.db.BookDatabase
import com.xuziyue.ebook.data.db.ReadingProgressDao
import com.xuziyue.ebook.reader.readium.ExtractPublicationMetadataUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据层依赖注入（Room 数据库 + DAO + Repository + 导入编排）。
 *
 * BookFileImporter / ExtractPublicationMetadataUseCase 由 [ReaderModule] 提供，Hilt 全局图跨模块注入。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideBookDatabase(@ApplicationContext context: Context): BookDatabase =
        Room.databaseBuilder(context, BookDatabase::class.java, BookDatabase.DB_NAME).build()

    @Provides
    fun provideBookDao(db: BookDatabase): BookDao = db.bookDao()

    @Provides
    fun provideReadingProgressDao(db: BookDatabase): ReadingProgressDao = db.readingProgressDao()

    @Provides
    @Singleton
    fun provideBookRepository(dao: BookDao): BookRepository = BookRepository(dao)

    @Provides
    @Singleton
    fun provideReadingProgressRepository(dao: ReadingProgressDao): ReadingProgressRepository =
        ReadingProgressRepository(dao)

    @Provides
    @Singleton
    fun provideImportBookUseCase(
        importer: BookFileImporter,
        extractor: ExtractPublicationMetadataUseCase,
        repo: BookRepository,
        @ApplicationContext context: Context,
    ): ImportBookUseCase = ImportBookUseCase(importer, extractor, repo, context)
}
