package com.xuziyue.ebook.di

import android.content.Context
import androidx.room.Room
import com.xuziyue.ebook.data.AnnotationRepository
import com.xuziyue.ebook.data.AppSettingsRepository
import com.xuziyue.ebook.data.BookFileImporter
import com.xuziyue.ebook.data.BookRepository
import com.xuziyue.ebook.data.EpubSecurityValidator
import com.xuziyue.ebook.data.BookmarkRepository
import com.xuziyue.ebook.data.ImportBookUseCase
import com.xuziyue.ebook.data.ReadingProgressRepository
import com.xuziyue.ebook.data.ReadingSessionRepository
import com.xuziyue.ebook.data.export.ExportBookDataUseCase
import com.xuziyue.ebook.data.db.BookDao
import com.xuziyue.ebook.data.db.BookDatabase
import com.xuziyue.ebook.data.db.MIGRATION_1_2
import com.xuziyue.ebook.data.db.MIGRATION_2_3
import com.xuziyue.ebook.data.db.AnnotationDao
import com.xuziyue.ebook.data.db.BookmarkDao
import com.xuziyue.ebook.data.db.ReadingProgressDao
import com.xuziyue.ebook.data.db.ReadingSessionDao
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
        Room.databaseBuilder(context, BookDatabase::class.java, BookDatabase.DB_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3) // v1→v2→v3（红线 #6，不破坏性重建）
            .build()

    @Provides
    fun provideBookDao(db: BookDatabase): BookDao = db.bookDao()

    @Provides
    fun provideReadingProgressDao(db: BookDatabase): ReadingProgressDao = db.readingProgressDao()

    @Provides
    fun provideBookmarkDao(db: BookDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideAnnotationDao(db: BookDatabase): AnnotationDao = db.annotationDao()

    @Provides
    fun provideReadingSessionDao(db: BookDatabase): ReadingSessionDao = db.readingSessionDao()

    @Provides
    @Singleton
    fun provideBookRepository(dao: BookDao): BookRepository = BookRepository(dao)

    @Provides
    @Singleton
    fun provideReadingProgressRepository(dao: ReadingProgressDao): ReadingProgressRepository =
        ReadingProgressRepository(dao)

    @Provides
    @Singleton
    fun provideBookmarkRepository(dao: BookmarkDao): BookmarkRepository = BookmarkRepository(dao)

    @Provides
    @Singleton
    fun provideAnnotationRepository(dao: AnnotationDao): AnnotationRepository =
        AnnotationRepository(dao)

    @Provides
    @Singleton
    fun provideReadingSessionRepository(
        dao: ReadingSessionDao,
        appSettings: AppSettingsRepository,
    ): ReadingSessionRepository = ReadingSessionRepository(dao, appSettings)

    @Provides
    @Singleton
    fun provideEpubSecurityValidator(): EpubSecurityValidator = EpubSecurityValidator()

    @Provides
    @Singleton
    fun provideImportBookUseCase(
        importer: BookFileImporter,
        extractor: ExtractPublicationMetadataUseCase,
        repo: BookRepository,
        securityValidator: EpubSecurityValidator,
        @ApplicationContext context: Context,
    ): ImportBookUseCase = ImportBookUseCase(importer, extractor, repo, securityValidator, context)

    @Provides
    @Singleton
    fun provideExportBookDataUseCase(
        bookDao: BookDao,
        annotationDao: AnnotationDao,
        bookmarkDao: BookmarkDao,
        progressDao: ReadingProgressDao,
        @ApplicationContext context: Context,
    ): ExportBookDataUseCase = ExportBookDataUseCase(bookDao, annotationDao, bookmarkDao, progressDao, context)

    @Provides
    @Singleton
    fun provideBackupUseCase(
        bookDao: BookDao,
        progressDao: ReadingProgressDao,
        bookmarkDao: BookmarkDao,
        annotationDao: AnnotationDao,
        sessionDao: ReadingSessionDao,
        dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
        @ApplicationContext context: Context,
    ): com.xuziyue.ebook.data.backup.BackupUseCase =
        com.xuziyue.ebook.data.backup.BackupUseCase(
            bookDao, progressDao, bookmarkDao, annotationDao, sessionDao, dataStore, context,
        )

    @Provides
    @Singleton
    fun provideRestoreUseCase(
        bookDao: BookDao,
        progressDao: ReadingProgressDao,
        bookmarkDao: BookmarkDao,
        annotationDao: AnnotationDao,
        sessionDao: ReadingSessionDao,
        dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
        @ApplicationContext context: Context,
    ): com.xuziyue.ebook.data.backup.RestoreUseCase =
        com.xuziyue.ebook.data.backup.RestoreUseCase(
            bookDao, progressDao, bookmarkDao, annotationDao, sessionDao, dataStore, context,
        )
}
